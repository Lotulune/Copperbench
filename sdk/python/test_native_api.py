"""Exercise the real Python pipe/process transport; Core behavior is tested in Java."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

from copperbench import ClientMetrics, Workspace, NativeApiError


HOST = r'''
import json, pathlib, sys, time
mode = sys.argv[1]
initial_response_delay = float(sys.argv[2])
log = pathlib.Path(__file__).with_suffix('.requests')
if mode == 'startup_hang':
    time.sleep(30)
if mode == 'startup_failure':
    print(json.dumps({'status': 'failed', 'code': 'WORKSPACE_WRITE_LOCKED'}), flush=True)
    sys.exit(1)
print(json.dumps({'status': 'ready', 'nativeApiVersion': '1', 'workspaceId': 'fixture',
                  'taskAuthorizedOperations': ['build_workspace']}), flush=True)
revision = 0
polls = 0
for line in sys.stdin:
    request = json.loads(line)
    with log.open('a', encoding='utf-8') as file:
        file.write(line)
    operation = request['operation']
    if operation == 'get_workbench' and initial_response_delay:
        time.sleep(initial_response_delay)
        initial_response_delay = 0
    if operation == 'slow':
        log.with_suffix('.blocked').write_text('ready', encoding='utf-8')
        time.sleep(30)
    if operation == 'crash':
        sys.exit(2)
    result = {'status': 'succeeded', 'revision': revision, 'data': {},
              'schemaVersion': '1.0' if mode == 'legacy_editor' else request.get('schemaVersion', '1.0')}
    if request['kind'] == 'handshake':
        compatible = '1.0' in request['payload']['supportedSchemaVersions']
        result = {'messageType': 'handshake_result', 'requestId': request['id'],
                  'status': 'compatible' if compatible else 'incompatible',
                  'selectedSchemaVersion': '1.0' if compatible else None,
                  'coreSchemaVersions': ['1.0'], 'diagnostics': []}
    if request['kind'] == 'version_copy_upgrade':
        result = {'status': 'succeeded', 'contract': 'version_copy_upgrade', 'selectedVersion': '1',
                  'revision': revision, 'received': request['payload']}
        if '2' in request['payload']['supportedVersions']: result['selectedVersion'] = '2'
    if request['kind'] == 'preview':
        result = {'status': 'succeeded', 'contract': 'preview_targets', 'selectedVersion': '1',
                  'revision': revision, 'received': request['payload']}
    if request['kind'] == 'acceptance':
        result = {'status': 'succeeded', 'contract': 'required_cases', 'selectedVersion': '1',
                  'revision': revision, 'received': request['payload'], 'manifests': []}
        if request['payload'].get('diagnosticVersions') == ['1']:
            result['coverageDiagnostics'] = {'schemaVersion': '1', 'required': 6, 'uniqueExecuted': 5,
                                             'shortfall': 1, 'duplicates': [], 'missingCases': []}
    if request['kind'] == 'command':
        if request['expectedRevision'] != revision:
            result = {'status': 'rejected', 'conflict': {'actualRevision': revision}, 'diagnostics': []}
        else:
            revision += 1
            result = {'status': 'committed', 'newRevision': revision, 'data': request['payload']}
            if operation == 'cancel_task':
                result['status'] = 'cancelled'
    if operation == 'list_mod_elements':
        cursor = request['payload'].get('cursor')
        result['data'] = {'items': [{'name': '第二个' if cursor else '第一个'}], 'nextCursor': None if cursor else 'page2'}
    if operation == 'get_workspace_environment':
        contracts = {
            'block': {'contractVersion': 1},
            'item': {'coverage': 'partial', 'fields': [{'name': 'stackSize', 'type': 'integer', 'min': 1, 'max': 99}]},
            'recipe': {'coverage': 'partial', 'fields': [{'name': 'recipeType', 'type': 'string', 'options': ['Crafting', 'Smelting']}]},
        }
        result['data'] = {'fieldContracts': contracts}
        if mode == 'legacy_contracts':
            result['data']['fieldContracts'] = {'block': contracts['block']}
        if mode == 'missing_contracts':
            result['data'] = {}
        if mode == 'invalid_contracts':
            result['data']['fieldContracts'] = []
        if mode == 'invalid_contract':
            contracts['item'] = None
    if operation == 'reject_diagnostic':
        result = {'status': 'rejected', 'revision': revision, 'data': {},
                  'diagnostics': [{'code': 'FIELD_TYPE_INVALID', 'path': '/commands/0',
                                   'message': request['payload']['message']}]}
    if operation == 'get_task':
        result['data'] = {'task': {'id': request['payload']['taskId'], 'state': 'succeeded'}, 'logs': [], 'diagnostics': []}
        if mode == 'task_logs':
            polls += 1
            result['data'] = {'task': {'id': request['payload']['taskId'], 'state': 'running' if polls == 1 else 'succeeded'},
                              'logs': [{'sequence': polls, 'text': 'first' if polls == 1 else 'last'}], 'diagnostics': []}
    print(json.dumps({'id': 'wrong' if operation == 'bad_id' else request['id'], 'result': result}), flush=True)
    if mode == 'blocked_write' and operation == 'get_workbench':
        time.sleep(30)
if mode == 'slow_clean_close':
    time.sleep(2.5)
    log.with_suffix('.closed').write_text('saved', encoding='utf-8')
'''


class NativeApiTest(unittest.TestCase):
    def test_editor_version_is_explicit_and_legacy_downgrade_is_rejected(self):
        metrics = ClientMetrics()
        with self.open(metrics=metrics) as workspace:
            self.assertEqual('1.1', workspace.query('get_mod_element_editor', elementId='element')['schemaVersion'])
            self.assertEqual('1.0', workspace.query('get_mod_element_editor', schema_version='1.0', elementId='element')['schemaVersion'])
            self.assertEqual(['1.1', '1.0'], [r['schemaVersion'] for r in self.requests() if r['operation'] == 'get_mod_element_editor'])
            self.assertEqual(2, metrics.snapshot()['operations']['get_mod_element_editor']['logicalCalls'])
            self.assertEqual(2, metrics.snapshot()['operations']['get_mod_element_editor']['attempts'])
        with self.open('legacy_editor') as workspace:
            with self.assertRaises(NativeApiError) as failure:
                workspace.query('get_mod_element_editor', elementId='element')
            self.assertEqual('NATIVE_INVALID_RESPONSE', failure.exception.code)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='copperbench python ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.workspace = self.root / '中文工作区.mcreator'
        self.workspace.write_text('{}', encoding='utf-8')
        self.host = self.root / 'fixture.py'
        self.host.write_text(HOST, encoding='utf-8')

    def open(self, mode='normal', *, initial_response_delay=0, **kwargs):
        client = Workspace.open(self.workspace,
                                launcher=[sys.executable, str(self.host), mode, str(initial_response_delay)], **kwargs)
        self.addCleanup(client.close)
        return client

    def requests(self):
        return [json.loads(line) for line in self.host.with_suffix('.requests').read_text(encoding='utf-8').splitlines()]

    def test_schema_negotiation_returns_incompatibility_without_closing_session(self):
        client = self.open()
        self.assertEqual('compatible', client.negotiate_schema(['1.0'])['status'])
        self.assertEqual('incompatible', client.negotiate_schema(['99.0'])['status'])
        self.assertEqual('succeeded', client.query('get_workbench')['status'])
        self.assertEqual(0, client.revision)

    def test_version_copy_upgrade_is_read_only_and_rejects_unnegotiated_results(self):
        client = self.open(task_authorization_id='authority')
        result = client.version_copy_upgrade(target_generator_id='fabric-26.3')
        self.assertEqual({'supportedVersions': ['1'], 'targetGeneratorId': 'fabric-26.3'}, result['received'])
        self.assertEqual(0, client.revision)
        with self.assertRaises(NativeApiError) as rejected:
            client.version_copy_upgrade(target_generator_id='fabric-26.3', supported_versions=('99',))
        self.assertEqual('INVALID_RESPONSE', rejected.exception.code)
        calls = [r for r in self.requests() if r['kind'] == 'version_copy_upgrade']
        self.assertEqual(2, len(calls))
        self.assertNotIn('expectedRevision', calls[0])

    def test_version_copy_execution_requires_explicit_version_and_exact_plan_parameters(self):
        client = self.open(task_authorization_id='ambient-authority')
        result = client.version_copy_upgrade('execute', target_generator_id='fabric-26.3', supported_versions=('2',),
            output_name='new-copy', plan_id='a' * 64, expected_revision=0, task_authorization_id='target-authority')
        self.assertEqual({'supportedVersions': ['2'], 'targetGeneratorId': 'fabric-26.3', 'outputName': 'new-copy',
            'planId': 'a' * 64, 'expectedRevision': 0, 'taskAuthorizationId': 'target-authority'}, result['received'])
        self.assertEqual(0, client.revision)
        self.assertEqual(1, len([r for r in self.requests() if r['kind'] == 'version_copy_upgrade']))

    def test_preview_negotiates_each_call_and_keeps_authority_nested(self):
        client = self.open(task_authorization_id='preview-authority')
        discovered = client.preview('catalog')['received']
        self.assertEqual({'supportedVersions': ['1']}, discovered)
        selected = client.preview('opt_in', generator_id='fabric-26.3')['received']
        self.assertEqual('fabric-26.3', selected['generatorId'])
        self.assertEqual(0, selected['expectedRevision'])
        self.assertNotIn('payload', selected)
        executed = client.preview('command', generator_id='fabric-26.3', command='build_workspace', payload={'scope': 'workspace'})['received']
        self.assertEqual('preview-authority', executed['payload']['taskAuthorizationId'])
        self.assertNotIn('permission', executed)
        cancelled = client.preview('command', generator_id='fabric-26.3', command='cancel_task', payload={'taskId': 'example'})['received']
        self.assertNotIn('taskAuthorizationId', cancelled['payload'])
        self.assertEqual(4, len([request for request in self.requests() if request['kind'] == 'preview']))
        with self.assertRaises(ValueError):
            client.preview('revoke', generator_id='fabric-26.3', expected_revision=True)

    def test_required_case_contract_preserves_manifest_revision_and_nested_authority(self):
        client = self.open(task_authorization_id='test-authority')
        manifest = {'id': 'raw-v1', 'sha256': 'a' * 64, 'cases': [{'className': '原始', 'name': 'exact#identity'}]}
        result = client.run_required_cases(manifest)
        received = result['received']
        self.assertEqual(['1'], received['supportedVersions'])
        self.assertEqual(0, received['expectedRevision'])
        self.assertEqual(manifest, received['payload']['requiredCases'])
        self.assertEqual('test-authority', received['payload']['taskAuthorizationId'])
        client.get_required_case_evidence('task')
        self.assertNotIn('taskAuthorizationId', self.requests()[-1]['payload']['payload'])
        client.export_required_case_artifact('task')
        self.assertNotIn('taskAuthorizationId', self.requests()[-1]['payload']['payload'])
        self.assertEqual(3, len([r for r in self.requests() if r['kind'] == 'acceptance']))

    def test_diagnostic_read_negotiates_without_revision_or_authority_mutation(self):
        with self.open() as client:
            revision = client.revision
            result = client.get_acceptance_diagnostics('task')
            self.assertEqual((6, 5, 1), (result['required'], result['uniqueExecuted'], result['shortfall']))
            self.assertEqual(revision, client.revision)
            request = self.requests()[-1]
            self.assertEqual('acceptance', request['kind'])
            self.assertEqual('get', request['operation'])
            self.assertEqual({'supportedVersions': ['1'], 'diagnosticVersions': ['1'], 'payload': {'taskId': 'task'}}, request['payload'])
            with self.assertRaises(NativeApiError) as raised:
                client.acceptance('get', diagnostic_versions=('99',), taskId='task')
            self.assertEqual('NATIVE_INVALID_RESPONSE', raised.exception.code)

    def test_native_calls_need_no_network_and_preserve_revision_and_unicode(self):
        with patch('socket.socket', side_effect=AssertionError('Network must not be used')):
            with self.open() as client:
                first = client.create_mod_element(elementType='item', name='test_item', initialValues={'displayName': '铜铃'})
                self.assertEqual('铜铃', first['data']['initialValues']['displayName'])
                client.update_mod_element(elementId='fixture', changes=[])
                self.assertEqual(2, client.revision)
                self.assertEqual(['第一个', '第二个'], [item['name'] for item in client.list_mod_elements()])
            self.assertIsNotNone(client._process.poll())
        self.assertEqual([0, 1], [r['expectedRevision'] for r in self.requests() if r['kind'] == 'command'])

    def test_revision_conflict_does_not_retry_or_silently_refresh(self):
        client = self.open()
        client.build()
        with self.assertRaises(NativeApiError) as raised:
            client.build(expected_revision=0)
        self.assertEqual('REVISION_CONFLICT', raised.exception.code)
        self.assertEqual(1, client.revision)
        self.assertEqual(3, len(self.requests()))
        self.assertEqual('succeeded', client.get_workspace()['status'])

    def test_item_and_recipe_field_discovery_returns_the_advertised_partial_contract(self):
        client = self.open()
        environment = client.query('get_workspace_environment')['data']['fieldContracts']
        for element_type in ('item', 'recipe'):
            with self.subTest(element_type=element_type):
                self.assertEqual(environment[element_type], client.field_contract(element_type))
                self.assertEqual('partial', client.field_contract(element_type)['coverage'])
        self.assertEqual(0, client.revision)
        self.assertTrue(all(request['kind'] == 'query' for request in self.requests()))

    def test_unknown_field_contract_is_actionable_and_does_not_close_or_mutate(self):
        client = self.open()
        with self.assertRaises(NativeApiError) as raised:
            client.field_contract('typo')
        error = raised.exception
        self.assertEqual('NATIVE_FIELD_CONTRACT_UNAVAILABLE', error.code)
        self.assertEqual('typo', error.details['elementType'])
        self.assertEqual(['block', 'item', 'recipe'], error.details['availableTypes'])
        self.assertEqual('type_not_advertised', error.details['reason'])
        self.assertIn('get_workspace_environment', error.details['nextAction'])
        self.assertIn('block, item, recipe', str(error))
        self.assertEqual('succeeded', client.get_workspace()['status'])
        self.assertEqual(0, client.revision)
        self.assertTrue(all(request['kind'] == 'query' for request in self.requests()))

    def test_older_service_missing_type_is_unavailable(self):
        with self.open('legacy_contracts') as client:
            with self.assertRaises(NativeApiError) as raised:
                client.field_contract('item')
            error = raised.exception
            self.assertEqual('NATIVE_FIELD_CONTRACT_UNAVAILABLE', error.code)
            self.assertEqual(['block'], error.details['availableTypes'])
            self.assertEqual('type_not_advertised', error.details['reason'])
            self.assertIn('update Copperbench', error.details['nextAction'])
            self.assertEqual('succeeded', client.get_workspace()['status'])

    def test_malformed_metadata_is_invalid_and_preserves_raw_envelope(self):
        for mode in ('missing_contracts', 'invalid_contracts', 'invalid_contract'):
            with self.subTest(mode=mode), self.open(mode) as client:
                envelope = client.query('get_workspace_environment')
                for discover in (lambda: client.field_contract('item'), client.available_field_contracts):
                    with self.assertRaises(NativeApiError) as raised:
                        discover()
                    self.assertEqual('NATIVE_INVALID_RESPONSE', raised.exception.code)
                    self.assertEqual(envelope, raised.exception.details)
                self.assertEqual('succeeded', client.get_workspace()['status'])

    def test_diagnostic_fallback_renders_args_and_preserves_the_core_receipt(self):
        client = self.open()
        message = {'key': 'diagnostic.field_invalid', 'fallback': '{field}: {reason}',
                   'args': {'field': '/commands/0', 'reason': 'Expected a non-null command string.'}}
        with self.assertRaises(NativeApiError) as raised:
            client.query('reject_diagnostic', message=message)
        error = raised.exception
        self.assertEqual('/commands/0: Expected a non-null command string.', str(error))
        self.assertEqual('FIELD_TYPE_INVALID', error.code)
        self.assertEqual({'status': 'rejected', 'revision': 0, 'data': {}, 'diagnostics': [
            {'code': 'FIELD_TYPE_INVALID', 'path': '/commands/0', 'message': message}]}, error.details)
        self.assertEqual(0, client.revision)
        self.assertEqual('succeeded', client.get_workspace()['status'])

    def test_diagnostic_substitution_is_single_pass_and_does_not_evaluate_format_expressions(self):
        client = self.open()
        message = {'fallback': '{field}: {reason}; {missing}; {field.__class__}; {field[0]}; '
                               '{field!r}; {field:>20}; {{field}}; {count}; {enabled}',
                   'args': {'field': '铜{reason}', 'reason': 'literal {count} and {"key": 1}',
                            'count': 2, 'enabled': False}}
        with self.assertRaises(NativeApiError) as raised:
            client.query('reject_diagnostic', message=message)
        self.assertEqual('铜{reason}: literal {count} and {"key": 1}; {missing}; {field.__class__}; '
                         '{field[0]}; {field!r}; {field:>20}; {{field}}; 2; false', str(raised.exception))
        self.assertEqual(message, raised.exception.details['diagnostics'][0]['message'])

    def test_diagnostic_missing_or_malformed_args_keep_literal_fallback(self):
        client = self.open()
        for message, expected in (
                ({'fallback': '{field}: {reason}'}, '{field}: {reason}'),
                ({'fallback': '{field}', 'args': None}, '{field}'),
                ({'fallback': '{field}', 'args': ['not', 'a', 'mapping']}, '{field}'),
                ({'fallback': '{field}', 'args': {'field': {'value': 'nested'}}}, '{field}'),
                ({'fallback': '{value}/{ratio}', 'args': {'value': None, 'ratio': 0.5}}, 'null/0.5'),
                ({'fallback': None, 'args': {'field': 'value'}}, 'FIELD_TYPE_INVALID'),
                ({'args': {'field': 'value'}}, 'FIELD_TYPE_INVALID'),
                ('plain diagnostic', 'plain diagnostic')):
            with self.subTest(message=message), self.assertRaises(NativeApiError) as raised:
                client.query('reject_diagnostic', message=message)
            self.assertEqual(expected, str(raised.exception))
            self.assertEqual('FIELD_TYPE_INVALID', raised.exception.code)

    def test_plan_query_revision_is_payload_only_and_apply_uses_plan_revision(self):
        client = self.open()
        client.build()
        client.plan_workspace_changes([], idempotency_key='stage17')
        client.get_workspace()
        plan = {'baseRevision': 0}
        with self.assertRaises(NativeApiError):
            client.apply_workspace_plan(plan)
        requests = self.requests()
        planning = next(r for r in requests if r['operation'] == 'plan_workspace_changes')
        self.assertEqual(1, planning['payload']['expectedRevision'])
        self.assertNotIn('expectedRevision', planning)
        self.assertTrue(all('expectedRevision' not in r['payload'] for r in requests if r['operation'] == 'get_workbench'))
        self.assertEqual(1, len([r for r in requests if r['operation'] == 'apply_workspace_plan']))

    def test_authorization_is_attached_to_commands_but_cancel_remains_available(self):
        client = self.open(task_authorization_id='approved-id')
        client.build()
        self.assertEqual('cancelled', client.cancel_task('task')['status'])
        requests = self.requests()
        self.assertNotIn('taskAuthorizationId', requests[0]['payload'])
        self.assertEqual('approved-id', requests[1]['payload']['taskAuthorizationId'])
        self.assertNotIn('taskAuthorizationId', requests[2]['payload'])

    def test_startup_failure_is_structured(self):
        with self.assertRaises(NativeApiError) as raised:
            self.open('startup_failure')
        self.assertEqual('WORKSPACE_WRITE_LOCKED', raised.exception.code)

    def test_startup_timeout_keeps_short_cleanup_and_reaps_host(self):
        processes = []
        popen = subprocess.Popen
        def launch(*args, **kwargs):
            process = popen(*args, **kwargs)
            processes.append(process)
            return process
        started = time.monotonic()
        with patch('copperbench_native.subprocess.Popen', side_effect=launch):
            with self.assertRaises(NativeApiError) as raised:
                self.open('startup_hang', startup_timeout=0.1)
        self.assertEqual('NATIVE_TIMEOUT', raised.exception.code)
        self.assertLess(time.monotonic() - started, 3)
        self.assertEqual(1, len(processes))
        self.assertIsNotNone(processes[0].poll())

    def test_idle_close_waits_for_slow_clean_eof_exit(self):
        client = self.open('slow_clean_close')
        client.close()
        self.assertEqual(0, client._process.returncode)
        self.assertEqual('saved', self.host.with_suffix('.closed').read_text(encoding='utf-8'))
        with self.assertRaises(NativeApiError) as closed:
            client.get_workspace()
        self.assertEqual('NATIVE_SESSION_CLOSED', closed.exception.code)

    def test_mismatched_response_closes_process(self):
        client = self.open()
        with self.assertRaises(NativeApiError) as raised:
            client.query('bad_id')
        self.assertEqual('NATIVE_INVALID_RESPONSE', raised.exception.code)
        self.assertIsNotNone(client._process.poll())
        with self.assertRaises(NativeApiError) as closed:
            client.get_workspace()
        self.assertEqual('NATIVE_SESSION_CLOSED', closed.exception.code)

    def test_timeout_closes_process_without_retrying_mutation(self):
        # Healthy initialization may exceed the deadline of the operation under test.
        client = self.open(initial_response_delay=0.2)
        client.request_timeout = 0.1
        started = time.monotonic()
        with self.assertRaises(NativeApiError) as raised:
            client.command('slow')
        self.assertEqual('NATIVE_TIMEOUT', raised.exception.code)
        self.assertLess(time.monotonic() - started, 3)
        self.assertEqual(1, len([r for r in self.requests() if r['operation'] == 'slow']))
        self.assertIsNotNone(client._process.poll())

    def test_child_exit_reports_error_and_reaps_process(self):
        client = self.open()
        with self.assertRaises(NativeApiError) as raised:
            client.query('crash')
        self.assertEqual('NATIVE_PROCESS_EXITED', raised.exception.code)
        self.assertIsNotNone(client._process.poll())

    def test_timeout_includes_a_blocked_pipe_write(self):
        # Finish the initial query before applying the short blocked-write deadline.
        client = self.open('blocked_write', initial_response_delay=0.2)
        client.request_timeout = 0.1
        started = time.monotonic()
        with self.assertRaises(NativeApiError) as raised:
            client.command('update_mod_element', text='x' * 1024 * 1024)
        self.assertEqual('NATIVE_TIMEOUT', raised.exception.code)
        self.assertLess(time.monotonic() - started, 3)
        self.assertIsNotNone(client._process.poll())

    def test_close_from_another_thread_interrupts_a_blocked_request(self):
        client = self.open('blocked_write', request_timeout=60)
        errors = []
        def call():
            try:
                client.command('update_mod_element', text='x' * 1024 * 1024)
            except NativeApiError as error:
                errors.append(error)
        worker = threading.Thread(target=call, daemon=True)
        worker.start()
        time.sleep(0.1)
        client.close()
        worker.join(timeout=3)
        self.assertFalse(worker.is_alive())
        self.assertEqual(1, len(errors))
        self.assertIsNotNone(client._process.poll())

    def test_close_from_another_thread_interrupts_a_blocked_read(self):
        client = self.open(request_timeout=60)
        errors = []
        def call():
            try:
                client.query('slow')
            except NativeApiError as error:
                errors.append(error)
        worker = threading.Thread(target=call, daemon=True)
        worker.start()
        deadline = time.monotonic() + 5
        while not self.host.with_suffix('.blocked').is_file():
            if time.monotonic() >= deadline:
                self.fail('Fixture never entered its blocking operation')
            time.sleep(0.01)
        started = time.monotonic()
        client.close()
        worker.join(timeout=3)
        self.assertLess(time.monotonic() - started, 3)
        self.assertFalse(worker.is_alive())
        self.assertEqual(1, len(errors))
        self.assertEqual('NATIVE_SESSION_CLOSED', errors[0].code)
        self.assertIsNotNone(client._process.poll())

    def test_wait_task_returns_actual_terminal_state(self):
        client = self.open()
        self.assertEqual('succeeded', client.wait_task('task')['data']['task']['state'])

    def test_wait_task_keeps_logs_received_before_the_terminal_poll(self):
        client = self.open('task_logs')
        result = client.wait_task('task', poll_interval=0.01)
        self.assertEqual(['first', 'last'], [entry['text'] for entry in result['data']['logs']])

    def test_native_metrics_count_actual_ipc_without_payloads(self):
        metrics = ClientMetrics()
        client = self.open(metrics=metrics, task_authorization_id='secret-grant')
        client.command('build_workspace', content='secret-content')
        rows = metrics.snapshot()['operations']
        self.assertEqual(1, rows['get_workbench']['logicalCalls'])
        self.assertEqual(1, rows['build_workspace']['attempts'])
        self.assertGreater(rows['build_workspace']['responseBytes'], 0)
        self.assertNotIn('secret-grant', json.dumps(metrics.snapshot()))
        self.assertNotIn('secret-content', json.dumps(metrics.snapshot()))

    def test_nonfinite_timeouts_are_rejected_before_launch(self):
        for value in (float('nan'), float('inf'), 0, -1):
            with self.assertRaises(ValueError):
                self.open(request_timeout=value)

    def test_current_mcp_client_remains_usable_as_a_single_file(self):
        source = Path(__file__).with_name('copperbench.py')
        (self.root / 'copperbench.py').write_text(source.read_text(encoding='utf-8'), encoding='utf-8')
        result = subprocess.run([sys.executable, '-c',
                                 'from copperbench import CopperbenchClient; print(CopperbenchClient.__name__)'],
                                cwd=self.root, capture_output=True, text=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('CopperbenchClient', result.stdout.strip())


if __name__ == '__main__':
    unittest.main()
