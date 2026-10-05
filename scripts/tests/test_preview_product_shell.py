"""Exercise the loopback boundary, including real HTTP origin/nonce checks."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from zipfile import ZipFile
from urllib.error import HTTPError
from urllib.request import Request, urlopen

SPEC = importlib.util.spec_from_file_location('preview_host', Path(__file__).parents[1] / 'preview-product-shell.py')
HOST = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HOST)


class Workspace:
    workspace_id = '11335577-0000-4000-8000-000000000000'
    def __init__(self):
        self.calls = []
    def command(self, operation, **payload):
        self.calls.append((operation, payload))
        raise HOST.NativeApiError('Revision changed', 'REVISION_CONFLICT', {
            'messageType': 'command_result', 'requestId': 'sdk-request',
            'workspaceId': self.workspace_id, 'operation': operation,
            'status': 'rejected', 'newRevision': 7, 'conflict': {'actualRevision': 7}})


class PreviewHostTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.dist = root / 'dist'
        self.dist.mkdir()
        (self.dist / 'index.html').write_text('<html><head></head><body></body></html>', encoding='utf-8')
        (root / 'private.txt').write_text('do not serve', encoding='utf-8')
        self.workspace = Workspace()
        self.server = HOST.PreviewServer(0, self.dist, self.workspace, root / 'audit.jsonl')
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.close_server)

    def close_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def post(self, payload, **headers):
        request = Request(self.server.origin + '/__preview/core', json.dumps(payload).encode(), headers={
            'Content-Type': 'application/json', 'X-Copperbench-Preview': self.server.token, **headers})
        with urlopen(request) as response:
            return json.load(response)

    def test_source_page_uses_bootstrap_and_does_not_expose_parent_files(self):
        with urlopen(self.server.origin) as response:
            self.assertIn(b'/__preview/bootstrap.js', response.read())
        with self.assertRaises(HTTPError) as error:
            urlopen(self.server.origin + '/%2e%2e/private.txt')
        self.assertEqual(404, error.exception.code)

    def test_cross_origin_bootstrap_and_commands_rejected(self):
        with self.assertRaises(HTTPError) as error:
            urlopen(Request(self.server.origin + '/__preview/bootstrap.js', headers={'Sec-Fetch-Site': 'cross-site'}))
        self.assertEqual(403, error.exception.code)
        with self.assertRaises(HTTPError) as error:
            self.post({}, Origin='https://unrelated.example')
        self.assertEqual(403, error.exception.code)
        with self.assertRaises(HTTPError) as error:
            self.post({}, **{'X-Copperbench-Preview': 'wrong'})
        self.assertEqual(403, error.exception.code)
        self.assertEqual([], self.workspace.calls)

    def test_revision_conflict_is_preserved_and_not_retried(self):
        result = self.post({'messageType': 'command', 'schemaVersion': '1.0',
            'requestId': '22446688-0000-4000-8000-000000000000', 'workspaceId': self.workspace.workspace_id,
            'expectedRevision': 6, 'operation': 'update_workspace_file', 'payload': {'content': 'private draft'}})
        self.assertEqual('rejected', result['status'])
        self.assertEqual(7, result['conflict']['actualRevision'])
        self.assertEqual('22446688-0000-4000-8000-000000000000', result['requestId'])
        self.assertEqual(1, len(self.workspace.calls))
        self.assertNotIn('private draft', self.server.audit.read_text())

    def test_running_classpath_isolated_from_next_compile(self):
        classes = Path(self.temp.name) / 'classes'
        classes.mkdir()
        (classes / 'Example.class').write_bytes(b'first build')
        dependency = str(Path(self.temp.name) / 'dependency.jar')
        frozen = HOST.freeze_classpath(str(classes) + os.pathsep + dependency, Path(self.temp.name) / 'runtime')
        archive, unchanged_dependency = frozen.split(os.pathsep)
        (classes / 'Example.class').write_bytes(b'next build')
        with ZipFile(archive) as jar:
            self.assertEqual(b'first build', jar.read('Example.class'))
        self.assertEqual(dependency, unchanged_dependency)


if __name__ == '__main__':
    unittest.main()
