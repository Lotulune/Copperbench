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

from copperbench import Workspace, NativeApiError


HOST = r'''
import json, pathlib, sys, time
mode = sys.argv[1]
log = pathlib.Path(__file__).with_suffix('.requests')
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
    if operation == 'slow':
        time.sleep(30)
    if operation == 'crash':
        sys.exit(2)
    result = {'status': 'succeeded', 'revision': revision, 'data': {}}
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
    if operation == 'get_task':
        result['data'] = {'task': {'state': 'succeeded'}, 'logs': []}
        if mode == 'task_logs':
            polls += 1
            result['data'] = {'task': {'state': 'running' if polls == 1 else 'succeeded'},
                              'logs': [{'sequence': polls, 'text': 'first' if polls == 1 else 'last'}]}
    print(json.dumps({'id': 'wrong' if operation == 'bad_id' else request['id'], 'result': result}), flush=True)
    if mode == 'blocked_write' and operation == 'get_workbench':
        time.sleep(30)
'''


class NativeApiTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='copperbench python ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.workspace = self.root / '中文工作区.mcreator'
        self.workspace.write_text('{}', encoding='utf-8')
        self.host = self.root / 'fixture.py'
        self.host.write_text(HOST, encoding='utf-8')

    def open(self, mode='normal', **kwargs):
        client = Workspace.open(self.workspace, launcher=[sys.executable, str(self.host), mode], **kwargs)
        self.addCleanup(client.close)
        return client

    def requests(self):
        return [json.loads(line) for line in self.host.with_suffix('.requests').read_text(encoding='utf-8').splitlines()]

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
        client = self.open(request_timeout=0.1)
        with self.assertRaises(NativeApiError) as raised:
            client.command('slow')
        self.assertEqual('NATIVE_TIMEOUT', raised.exception.code)
        self.assertEqual(1, len([r for r in self.requests() if r['operation'] == 'slow']))
        self.assertIsNotNone(client._process.poll())

    def test_child_exit_reports_error_and_reaps_process(self):
        client = self.open()
        with self.assertRaises(NativeApiError) as raised:
            client.query('crash')
        self.assertEqual('NATIVE_PROCESS_EXITED', raised.exception.code)
        self.assertIsNotNone(client._process.poll())

    def test_timeout_includes_a_blocked_pipe_write(self):
        client = self.open('blocked_write', request_timeout=0.1)
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

    def test_wait_task_returns_actual_terminal_state(self):
        client = self.open()
        self.assertEqual('succeeded', client.wait_task('task')['data']['task']['state'])

    def test_wait_task_keeps_logs_received_before_the_terminal_poll(self):
        client = self.open('task_logs')
        result = client.wait_task('task', poll_interval=0.01)
        self.assertEqual(['first', 'last'], [entry['text'] for entry in result['data']['logs']])

    def test_nonfinite_timeouts_are_rejected_before_launch(self):
        for value in (float('nan'), float('inf'), 0, -1):
            with self.assertRaises(ValueError):
                self.open(request_timeout=value)

    def test_legacy_mcp_client_remains_usable_as_a_single_file(self):
        source = Path(__file__).with_name('copperbench.py')
        (self.root / 'copperbench.py').write_text(source.read_text(encoding='utf-8'), encoding='utf-8')
        result = subprocess.run([sys.executable, '-c',
                                 'from copperbench import CopperbenchClient; print(CopperbenchClient.__name__)'],
                                cwd=self.root, capture_output=True, text=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('CopperbenchClient', result.stdout.strip())


if __name__ == '__main__':
    unittest.main()
