"""Public SDK boundary regressions; fixtures do not prove Core or GameTest acceptance."""
from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from copperbench import ClientMetrics, CopperbenchClient, CopperbenchError, NativeApiError, Workspace
from test_mcp_client import Response, reply, tool_result


def task_result(state='running', sequences=(), *, task_id='task-1'):
    return {'status': 'succeeded', 'revision': 4, 'data': {
        'task': {'id': task_id, 'kind': 'build', 'state': state},
        'logs': [{'sequence': sequence, 'text': f'line-{sequence}'} for sequence in sequences],
        'diagnostics': [{'code': 'TASK_DETAIL', 'severity': 'warning'}],
    }}


class AgentWorkflowSdkTest(unittest.TestCase):
    def client(self, **kwargs):
        return CopperbenchClient('http://127.0.0.1:61999/mcp', 'secret-token', 'workspace',
                                 retry_backoff_seconds=0, **kwargs)

    def test_initialize_rejects_incompatible_or_missing_protocol_before_notification(self):
        for result, code in (({'protocolVersion': '2024-11-05'}, 'MCP_PROTOCOL_INCOMPATIBLE'),
                             ({'capabilities': {}}, 'MCP_RESPONSE_INVALID')):
            sent = []
            def transport(request, **_):
                sent.append(json.loads(request.data))
                return reply(request, result)
            with patch('urllib.request.urlopen', transport), self.assertRaises(CopperbenchError) as captured:
                self.client().initialize()
            self.assertEqual(code, captured.exception.code)
            self.assertEqual(result, captured.exception.details)
            self.assertEqual(['initialize'], [request['method'] for request in sent])

    def test_tools_list_uses_actual_catalog_and_mcp_optional_final_cursor(self):
        pages = [{'tools': [{'name': 'get_workspace', 'inputSchema': {}}], 'nextCursor': 'catalog-2'},
                 {'tools': [{'name': 'get_task', 'inputSchema': {}}]}]
        sent = []
        def transport(request, **_):
            sent.append(json.loads(request.data))
            return reply(request, pages[len(sent) - 1])
        with patch('urllib.request.urlopen', transport):
            self.assertEqual(['get_workspace', 'get_task'], [tool['name'] for tool in self.client().list_tools()])
        self.assertEqual({'cursor': 'catalog-2'}, sent[-1]['params'])

    def test_paginated_elements_preserve_filters_and_continue_to_explicit_null(self):
        sent = []
        def transport(request, **_):
            sent.append(json.loads(request.data)['params']['arguments'])
            return reply(request, tool_result({'status': 'succeeded', 'data': {
                'items': [{'id': str(len(sent))}], 'nextCursor': 'page-2' if len(sent) == 1 else None}}))
        with patch('urllib.request.urlopen', transport):
            items = list(self.client().list_mod_elements(limit=1, fields=['id'], filter={'types': ['block']}))
        self.assertEqual(['1', '2'], [item['id'] for item in items])
        self.assertNotIn('cursor', sent[0])
        self.assertEqual({**sent[0], 'cursor': 'page-2'}, sent[1])

    def test_missing_malformed_or_repeated_cursor_does_not_return_a_complete_list(self):
        for data in ({'items': [{'id': '1'}]}, {'items': [], 'nextCursor': 5},
                     {'items': [], 'nextCursor': ''}, {'items': ['invalid'], 'nextCursor': None},
                     {'items': [], 'nextCursor': 'start'}):
            payload = {'status': 'succeeded', 'data': data}
            with patch('urllib.request.urlopen', lambda request, **_: reply(request, tool_result(payload))):
                with self.assertRaises(CopperbenchError) as captured:
                    list(self.client().list_mod_elements(cursor='start'))
            self.assertEqual('MCP_TOOL_RESULT_INVALID', captured.exception.code)
            self.assertEqual(payload, captured.exception.details)

    def test_stale_cursor_propagates_and_requires_a_fresh_complete_traversal(self):
        sent = []
        def transport(request, **_):
            args = json.loads(request.data)['params']['arguments']
            sent.append(args)
            payload = ({'status': 'rejected', 'diagnostics': [{'code': 'LIST_CURSOR_STALE', 'path': '/cursor'}]}
                       if args.get('cursor') else {'status': 'succeeded', 'data': {'items': [{'id': '1'}], 'nextCursor': 'stale'}})
            return reply(request, tool_result(payload))
        with patch('urllib.request.urlopen', transport), self.assertRaises(CopperbenchError) as captured:
            list(self.client().list_mod_elements(limit=1))
        self.assertEqual('LIST_CURSOR_STALE', captured.exception.code)
        self.assertEqual(2, len(sent))  # No hidden restart mixing revisions.

    def test_field_contract_reuses_explicit_environment_without_a_hidden_cache(self):
        client = self.client()
        client.get_workspace_environment = Mock(side_effect=AssertionError('No duplicate environment read'))
        for data, code in (({}, 'FIELD_CONTRACT_NOT_EXPOSED'), ({'fieldContracts': []}, 'MCP_TOOL_RESULT_INVALID'),
                           ({'fieldContracts': {}}, 'FIELD_CONTRACT_NOT_EXPOSED')):
            with self.assertRaises(CopperbenchError) as captured:
                client.field_contract('block', environment={'data': data})
            self.assertEqual(code, captured.exception.code)
        first = {'data': {'fieldContracts': {'block': {'contractVersion': 1}}}}
        second = {'data': {'fieldContracts': {'block': {'contractVersion': 2}}}}
        self.assertEqual(1, client.field_contract(environment=first)['contractVersion'])
        self.assertEqual(2, client.field_contract(environment=second)['contractVersion'])

    def test_both_wait_helpers_resume_without_restarting_and_keep_incremental_receipts(self):
        for client in (self.client(), Workspace.__new__(Workspace)):
            first, final = task_result(sequences=[38]), task_result('succeeded', [39])
            client.get_task = Mock(side_effect=[first, final])
            updates = []
            with patch('time.sleep'):
                result = client.wait_task('task-1', after_log_sequence=37, on_update=updates.append)
            self.assertEqual([38, 39], [entry['sequence'] for entry in result['data']['logs']])
            self.assertEqual([[38], [39]], [[entry['sequence'] for entry in update['data']['logs']] for update in updates])
            self.assertEqual([(('task-1', 37),), (('task-1', 38),)], [(call.args,) for call in client.get_task.call_args_list])
            self.assertEqual([39], [entry['sequence'] for entry in final['data']['logs']])  # Raw terminal receipt preserved.

    def test_task_timeout_keeps_all_received_logs_identity_and_known_state(self):
        for client, error_type, code in ((self.client(), CopperbenchError, 'MCP_TASK_TIMEOUT'),
                                         (Workspace.__new__(Workspace), NativeApiError, 'NATIVE_TASK_TIMEOUT')):
            client.get_task = Mock(side_effect=[task_result(sequences=[1]), task_result(sequences=[2])])
            with patch('time.monotonic', side_effect=[0, 0, 2]), patch('time.sleep'):
                with self.assertRaises(error_type) as captured:
                    client.wait_task('task-1', timeout=1)
            self.assertEqual(code, captured.exception.code)
            self.assertEqual('task-1', captured.exception.details['data']['task']['id'])
            self.assertEqual('running', captured.exception.details['data']['task']['state'])
            self.assertEqual([1, 2], [entry['sequence'] for entry in captured.exception.details['data']['logs']])

    def test_disconnection_preserves_original_error_and_last_successful_checkpoint(self):
        client = self.client()
        original = CopperbenchError('disconnected', 'MCP_TRANSPORT_FAILED', {'raw': 'transport receipt'})
        client.get_task = Mock(side_effect=[task_result(sequences=[38]), original])
        updates = []
        with patch('time.sleep'), self.assertRaises(CopperbenchError) as captured:
            client.wait_task('task-1', after_log_sequence=37, on_update=updates.append)
        self.assertIs(original, captured.exception)
        self.assertEqual(38, updates[-1]['data']['logs'][-1]['sequence'])
        self.assertEqual({'raw': 'transport receipt'}, captured.exception.details)

    def test_invalid_task_states_id_and_log_sequences_do_not_become_terminal_success(self):
        malformed = [task_result('unknown'), task_result(task_id='another-task'), task_result(sequences=[2, 1]),
                     task_result(sequences=[1, 1]), task_result(sequences=[0])]
        for response in malformed:
            client = self.client()
            client.get_task = Mock(return_value=response)
            with self.assertRaises(CopperbenchError) as captured:
                client.wait_task('task-1')
            self.assertEqual('MCP_TOOL_RESULT_INVALID', captured.exception.code)
            self.assertEqual(response, captured.exception.details)
        client.get_task = Mock(return_value=task_result('failed', [1]))
        self.assertEqual('failed', client.wait_task('task-1')['data']['task']['state'])

    def test_metrics_distinguish_logical_reads_from_http_retries_without_secrets(self):
        metrics = ClientMetrics()
        bodies = []
        def transport(request, **_):
            if not bodies:
                bodies.append(None)
                raise TimeoutError('temporary failure')
            response = reply(request, tool_result({'status': 'succeeded', 'data': {'name': '铜铃'}}))
            bodies.append(response.body)
            return response
        with patch('urllib.request.urlopen', transport):
            self.client(metrics=metrics).get_workspace()
        row = metrics.snapshot()['operations']['get_workspace']
        self.assertEqual(1, row['logicalCalls'])
        self.assertEqual(2, row['attempts'])
        self.assertEqual(len(bodies[-1].encode('utf-8')), row['responseBytes'])
        encoded = json.dumps(metrics.snapshot())
        self.assertNotIn('secret-token', encoded)
        self.assertNotIn('铜铃', encoded)

    def test_session_close_is_single_attempt_and_does_not_submit_a_task(self):
        client = self.client()
        client._session_id = 'session-1'
        requests = []
        def transport(request, **_):
            requests.append(request)
            return Response()
        with patch('urllib.request.urlopen', transport):
            client.close()
            client.close()
        self.assertEqual(1, len(requests))
        self.assertEqual('DELETE', requests[0].method)
        self.assertIsNone(requests[0].data)


class DeliveryExampleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(__file__).parent / 'examples' / 'agent_workflows.py'
        spec = importlib.util.spec_from_file_location('agent_workflow_example', path)
        cls.example = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.example)

    def test_local_delivery_consistency_validator_rejects_altered_bytes_or_historical_receipt(self):
        # This is a validator fixture, not a passed GameTest or real JAR receipt.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'verified-mod.jar').write_bytes(b'fixture-jar')
            (root / 'gametest-results.xml').write_text('<testsuite/>', encoding='utf-8')
            receipt = {'taskId': 'acceptance', 'status': 'passed_current_input', 'artifactPath': 'verified-mod.jar',
                       'reportPath': 'gametest-results.xml', 'sourceSha256': 'fixture-source',
                       'artifactSha256': self.example.file_sha256(root / 'verified-mod.jar'),
                       'reportSha256': self.example.file_sha256(root / 'gametest-results.xml')}
            (root / 'verification.json').write_text(json.dumps(receipt), encoding='utf-8')
            delivery = {**receipt, 'exportDirectory': str(root)}
            acceptance = {'id': 'acceptance', 'verification': {**receipt, 'sourceSnapshot': {'sha256': 'fixture-source'}}}
            self.assertEqual(delivery, self.example.verify_delivery({'verifiedExport': delivery}, acceptance))
            historical = copy.deepcopy(delivery)
            historical['status'] = 'passed_historical_input'
            with self.assertRaises(RuntimeError):
                self.example.verify_delivery({'verifiedExport': historical}, acceptance)
            (root / 'verified-mod.jar').write_bytes(b'altered')
            with self.assertRaises(RuntimeError):
                self.example.verify_delivery({'verifiedExport': delivery}, acceptance)

    def test_w3_never_exports_an_ordinary_build_as_acceptance(self):
        route = SimpleNamespace(args=SimpleNamespace(mode='desktop', task_id='task-1', after_log_sequence=0, acceptance_task='ordinary-build'),
            require=Mock(), client=SimpleNamespace(wait_task=Mock(return_value=task_result('succeeded')),
            get_task=Mock(return_value=task_result('succeeded', task_id='ordinary-build'))), export=Mock())
        evidence = SimpleNamespace(log_sequences={}, poll=Mock(), save=Mock())
        with self.assertRaisesRegex(RuntimeError, 'real passed packaged-JAR acceptance task'):
            self.example.w3(route, {'data': {'workspaceId': 'workspace'}}, evidence)
        route.export.assert_not_called()


if __name__ == '__main__':
    unittest.main()
