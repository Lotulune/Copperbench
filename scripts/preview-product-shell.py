"""Serve the built Product Shell against an actual isolated Core session.

This development host accepts any .mcreator workspace. It does not scan workspace
files or infer domain objects: all projections and writes go through the SDK.
Use a workspace copy for UI acceptance. The supplied workspace is writable.
Native window/file-picker/plugin integrations still require the desktop host.
"""
from __future__ import annotations

import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import sys
import threading
from urllib.parse import unquote, urlsplit
import uuid
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'sdk' / 'python'))
from copperbench_native import NativeApiError, Workspace


def freeze_classpath(classpath: str, destination: Path) -> str:
    """Keep a running preview independent of subsequent javac output changes."""
    snapshot = destination / str(uuid.uuid4())
    snapshot.mkdir(parents=True)
    entries = []
    for index, entry in enumerate(classpath.split(os.pathsep)):
        path = Path(entry)
        if path.is_dir():
            archive = snapshot / f'{index}-{path.name}.jar'
            with ZipFile(archive, 'w', ZIP_DEFLATED) as jar:
                for file in sorted(path.rglob('*')):
                    if file.is_file():
                        jar.write(file, file.relative_to(path).as_posix())
            entries.append(str(archive))
        else:
            entries.append(entry)
    return os.pathsep.join(entries)


def bootstrap(workspace_id: str, token: str) -> str:
    config = json.dumps({'workspaceId': workspace_id, 'token': token})
    return """(() => {
  const config = CONFIG;
  async function call(endpoint, payload) {
    const response = await fetch(endpoint, {
      method: 'POST', headers: {'Content-Type': 'application/json', 'X-Copperbench-Preview': config.token},
      body: JSON.stringify(payload), credentials: 'omit'
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || `Preview host returned ${response.status}`);
    return result;
  }
  window.copperbenchHost = {
    workspaceId: config.workspaceId,
    invoke: async raw => JSON.stringify(await call('/__preview/core', JSON.parse(raw))),
    onEvent: () => () => {}
  };
  window.__COPPERBENCH_WINDOW_HOST__ = {
    systemFrame: true, preferencesAvailable: false, preferencesSchemaVersion: '1.0',
    getPreferences: () => call('/__preview/preferences', {operation: 'get_preferences', payload: {}}),
    savePreferences: payload => call('/__preview/preferences', {operation: 'save_preferences', payload}),
    invoke: async () => { throw new Error('This action requires the desktop window host.'); }
  };
})();""".replace('CONFIG', config)


class PreviewServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, port: int, dist: Path, workspace: Workspace, audit: Path):
        self.workspace = workspace
        self.dist = dist.resolve(strict=True)
        self.token = secrets.token_urlsafe(32)
        self.audit = audit
        self.audit_lock = threading.Lock()
        super().__init__(('127.0.0.1', port), partial(PreviewHandler, directory=str(self.dist)))
        self.origin = f'http://127.0.0.1:{self.server_port}'

    def record(self, operation: str, result: dict) -> None:
        # Log receipts, never source contents, preference values or the session token.
        receipt = {key: result[key] for key in ('status', 'revision', 'newRevision', 'requestId') if key in result}
        receipt['operation'] = operation
        with self.audit_lock, self.audit.open('a', encoding='utf-8') as file:
            file.write(json.dumps(receipt) + '\n')


class PreviewHandler(SimpleHTTPRequestHandler):
    server: PreviewServer

    def log_message(self, format, *args):
        # The audit file records API outcomes; avoid dumping large source URLs/errors.
        pass

    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.send_header('Content-Security-Policy', "frame-ancestors 'none'")
        super().end_headers()

    def trusted_host(self) -> bool:
        return self.headers.get('Host') == f'127.0.0.1:{self.server.server_port}'

    def send_json(self, status: int, value: dict):
        body = json.dumps(value, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not self.trusted_host() or self.headers.get('Sec-Fetch-Site') not in (None, 'none', 'same-origin'):
            return self.send_json(403, {'error': 'Unexpected preview host'})
        route = unquote(urlsplit(self.path).path)
        if route == '/__preview/bootstrap.js':
            body = bootstrap(self.server.workspace.workspace_id, self.server.token).encode('utf-8')
            content_type = 'text/javascript; charset=utf-8'
        elif route in ('/', '/index.html'):
            html = (self.server.dist / 'index.html').read_text(encoding='utf-8')
            html = html.replace('<head>', '<head><script src="/__preview/bootstrap.js"></script>', 1)
            body = html.encode('utf-8')
            content_type = 'text/html; charset=utf-8'
        else:
            target = (self.server.dist / route.lstrip('/')).resolve()
            if not target.is_relative_to(self.server.dist) or not target.is_file():
                return self.send_error(404)
            return super().do_GET()
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if (not self.trusted_host()
                or self.headers.get('Origin') not in (None, self.server.origin)
                or not secrets.compare_digest(self.headers.get('X-Copperbench-Preview', ''), self.server.token)):
            return self.send_json(403, {'error': 'Preview session rejected'})
        if self.headers.get_content_type() != 'application/json':
            return self.send_json(415, {'error': 'Expected JSON'})
        try:
            length = int(self.headers.get('Content-Length', '-1'))
            if not 0 < length <= 8 * 1024 * 1024:
                return self.send_json(413, {'error': 'Invalid request length'})
            request = json.loads(self.rfile.read(length))
            if not isinstance(request, dict):
                raise ValueError('Expected a request object')
            if self.path == '/__preview/core':
                result = self.core_request(request)
            elif self.path == '/__preview/preferences':
                operation = request.get('operation')
                if operation not in ('get_preferences', 'save_preferences'):
                    raise ValueError('Unknown preferences operation')
                try:
                    result = self.server.workspace._call('context', operation, request.get('payload', {}))
                except NativeApiError as error:
                    details = error.details or {}
                    raise ValueError(details.get('error', {}).get('message') or str(error)) from error
                self.server.record(operation, result)
                result = result['data']
            elif self.path == '/__preview/shutdown':
                self.send_json(200, {'status': 'closing'})
                threading.Thread(target=self.server.shutdown, daemon=True).start()
                return
            else:
                return self.send_json(404, {'error': 'Unknown preview endpoint'})
            self.send_json(200, result)
        except (ValueError, KeyError, TypeError, NativeApiError) as error:
            if isinstance(error, NativeApiError):
                receipt = {'status': 'transport_failed', 'code': error.code, 'message': str(error),
                           'details': error.details}
                (self.server.audit.parent / 'transport-error.json').write_text(
                    json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
            self.send_json(400, {'error': str(error)})

    def core_request(self, request: dict) -> dict:
        request_id = str(uuid.UUID(request['requestId']))
        kind = request.get('messageType')
        operation = request.get('operation', 'handshake')
        workspace = self.server.workspace
        if kind == 'handshake':
            client = request.get('client', {})
            result = workspace.negotiate_schema(request['supportedSchemaVersions'],
                client_name=client.get('name', 'browser-preview'), client_version=client.get('version', '1'))
        else:
            if request.get('workspaceId') != workspace.workspace_id or request.get('schemaVersion') != '1.0':
                raise ValueError('Workspace or schema mismatch')
            if not isinstance(request.get('payload'), dict):
                raise ValueError('Expected a payload object')
            try:
                if kind == 'query':
                    result = workspace.query(operation, **request['payload'])
                elif kind == 'command':
                    result = workspace.command(operation, expected_revision=request['expectedRevision'], **request['payload'])
                else:
                    raise ValueError('Unsupported message type')
            except NativeApiError as error:
                # Preserve Core denials/conflicts exactly; do not turn them into success.
                if not isinstance(error.details, dict) or 'messageType' not in error.details:
                    raise
                result = error.details
        result['requestId'] = request_id
        self.server.record(operation, result)
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', required=True, type=Path)
    parser.add_argument('--product', required=True, type=Path, help='Installed distribution root (plugins and dependencies)')
    parser.add_argument('--runtime-home', required=True, type=Path, help='Isolated preferences/cache directory')
    parser.add_argument('--classpath-file', required=True, type=Path, help='Gradle main runtime classpath export')
    parser.add_argument('--port', type=int, default=5175)
    parser.add_argument('--dist', type=Path, default=ROOT / 'ui-shell' / 'dist')
    args = parser.parse_args()
    workspace_path = args.workspace.resolve(strict=True)
    product = args.product.resolve(strict=True)
    runtime_home = args.runtime_home.resolve()
    runtime_home.mkdir(parents=True, exist_ok=True)
    (runtime_home / 'ipc').mkdir(exist_ok=True)
    classpath = args.classpath_file.read_text(encoding='utf-8-sig').strip()
    if not classpath:
        raise ValueError('Runtime classpath is empty')
    classpath = freeze_classpath(classpath, runtime_home / 'classpath')
    os.environ['COPPERBENCH_HOME'] = str(runtime_home / 'copperbench')
    os.environ['GRADLE_USER_HOME'] = str(runtime_home / 'gradle')
    java = ROOT / 'jdk' / 'jbr25_win_64' / 'bin' / 'java.exe'
    if not java.exists():
        java = product / 'jdk' / 'bin' / 'java.exe'
    launcher = [str(java), '--add-opens=java.base/java.lang=ALL-UNNAMED',
        '--enable-native-access=ALL-UNNAMED,jcef', '-Dcopperbench.productShell=true',
        f'-Duser.home={runtime_home}', f'-Dcopperbench.gradle.user.home={runtime_home / "gradle"}',
        f'-Djdk.net.unixdomain.tmpdir={runtime_home / "ipc"}', '-cp', classpath, 'net.mcreator.Launcher']
    with Workspace.open(workspace_path, launcher=launcher, cwd=product,
                        startup_timeout=180, request_timeout=120) as workspace:
        required = {'list_workspace_files', 'read_workspace_file', 'get_workspace_source_index', 'update_workspace_file'}
        if not required.issubset(workspace.operations):
            raise RuntimeError('Build the current Core before launching this preview')
        with PreviewServer(args.port, args.dist, workspace, runtime_home / 'requests.jsonl') as server:
            print(json.dumps({'status': 'ready', 'url': server.origin, 'workspace': str(workspace_path),
                              'workspaceId': workspace.workspace_id, 'runtimeHome': str(runtime_home)}), flush=True)
            try:
                server.serve_forever(poll_interval=0.25)
            except KeyboardInterrupt:
                pass


if __name__ == '__main__':
    main()
