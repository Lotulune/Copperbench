import json
import unittest
from copperbench import CopperbenchClient


class RecordingTransport(CopperbenchClient):
    def __init__(self):
        super().__init__('http://127.0.0.1:61999/mcp', 'fixture', 'workspace', task_authorization_id='grant')
        self.calls = []

    def _rpc(self, method, params, *args, **kwargs):
        self.calls.append(params)
        return {'content': [{'type': 'text', 'text': json.dumps({'status': 'accepted'})}]}


class TaskAuthorizationSdkTest(unittest.TestCase):
    def test_grant_is_forwarded_to_mutations_without_changing_queries_or_revocation(self):
        client = RecordingTransport()
        client.prepare_game_tests(3)
        client.run_gametest(3)
        client.get_task('task', 8)
        client.list_task_authorizations()
        client.revoke_task_authorization('grant', 3)
        self.assertEqual(['prepare_game_tests', 'run_gametest', 'get_task', 'list_task_authorizations', 'revoke_task_authorization'],
                         [call['name'] for call in client.calls])
        self.assertEqual('grant', client.calls[0]['arguments']['taskAuthorizationId'])
        self.assertEqual('grant', client.calls[1]['arguments']['taskAuthorizationId'])
        self.assertEqual({'taskId': 'task', 'afterLogSequence': 8}, client.calls[2]['arguments'])
        self.assertEqual({}, client.calls[3]['arguments'])
        self.assertEqual({'authorizationId': 'grant', 'expectedRevision': 3}, client.calls[4]['arguments'])


if __name__ == '__main__':
    unittest.main()
