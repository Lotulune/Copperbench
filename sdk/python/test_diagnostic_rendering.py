"""Cross-entry presentation contract; transport and live task acceptance remain separate."""
import copy
import json
from pathlib import Path
import unittest

from copperbench import CopperbenchClient, CopperbenchError, _render_message as mcp_render
from copperbench_native import NativeApiError, _render_message as native_render
import test_native_readiness

FIXTURES = json.loads((Path(__file__).parents[1]/'tests/diagnostic-rendering.json').read_text(encoding='utf-8'))


class DiagnosticRenderingTest(unittest.TestCase):
    def test_plain_mcp_schema_error_is_actionable_without_replaying(self):
        for text in ('Input validation error: unexpected probeNetwork', '404', '"Legacy error"'):
            result = {'isError': True, 'content': [{'type': 'text', 'text': text}]}
            client = CopperbenchClient('http://127.0.0.1:61999/mcp', 'token', 'workspace')
            calls = []
            def rpc(*args):
                calls.append(args)
                return result
            client._rpc = rpc
            with self.assertRaises(CopperbenchError) as error:
                client.call_tool('get_workspace_doctor', {'probeNetwork': True})
            self.assertEqual('MCP_TOOL_RESULT_INVALID', error.exception.code)
            self.assertEqual(text, str(error.exception))
            self.assertIs(result, error.exception.details)
            self.assertEqual(1, len(calls))

    def test_all_shared_messages_preserve_raw_diagnostics_and_healthy_sessions(self):
        for case in FIXTURES['cases']:
            with self.subTest(case=case['name']):
                payload = {'status':'failed', 'revision':7, 'task':{'id':'active-task','state':'running'},
                           'diagnostics':[{'code':FIXTURES['code'], 'message':case['message']}]}
                original = copy.deepcopy(payload)
                native, sent = test_native_readiness.NativeReadinessTest().client(payload)
                with self.assertRaises(NativeApiError) as error:
                    native.command('create_mod_element', elementType='function', name='invalid')
                self.assertEqual(case['expected'], str(error.exception))
                self.assertEqual(FIXTURES['code'], error.exception.code)
                self.assertEqual(original, error.exception.details)
                self.assertEqual(1, len(sent))
                native.close.assert_not_called()
                self.assertEqual(original, payload)
                mcp = CopperbenchClient('http://127.0.0.1:61999/mcp', 'fixture-token', 'workspace')
                calls = []
                def rpc(method, params):
                    calls.append((method, params))
                    return {'isError':True, 'content':[{'type':'text','text':json.dumps(payload)}]}
                mcp._rpc = rpc
                with self.assertRaises(CopperbenchError) as error:
                    mcp.call_tool('create_mod_element', {'name':'invalid'})
                self.assertEqual(case['expected'], str(error.exception))
                self.assertEqual(original, error.exception.details)
                self.assertEqual(1, len(calls))
                mcp._rpc = lambda *_: {'content':[{'type':'text','text':'{"status":"succeeded","data":{}}'}]}
                self.assertEqual('succeeded', mcp.doctor()['status'])

    def test_conflict_code_and_nonfinite_values_are_preserved(self):
        for render in (native_render, mcp_render):
            self.assertEqual('{value}', render({'fallback':'{value}', 'args':{'value':float('inf')}}, 'CODE'))
        payload = {'status':'rejected','conflict':{'actualRevision':8},
                   'diagnostics':[{'code':'FIELD_TYPE_INVALID','message':{'fallback':'Conflict {revision}','args':{'revision':8}}}]}
        client = CopperbenchClient('http://127.0.0.1:61999/mcp', 'token', 'workspace')
        client._rpc = lambda *_: {'content':[{'type':'text','text':json.dumps(payload)}]}
        with self.assertRaises(CopperbenchError) as error:
            client.call_tool('create_mod_element', {})
        self.assertEqual('REVISION_CONFLICT', error.exception.code)
        self.assertEqual('Conflict 8', str(error.exception))
        self.assertEqual(payload, error.exception.details)


if __name__ == '__main__':
    unittest.main()
