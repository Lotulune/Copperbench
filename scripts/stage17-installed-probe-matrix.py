"""Read-only S17-05 HTTP fault matrix through an installed candidate's public SDK.

Servers bind ephemeral IPv4 loopback ports, never call tools, and retain no credentials.
Constructor, cancellation and close fault injection belongs to the Java regression suite.
"""
import argparse
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import socket
import sys
import threading
import time


class Fixture:
    def __init__(self, mode):
        self.mode = mode
        self.methods = []
        self.authorization_seen = False
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_GET(self):
                self.send_response(405)
                self.end_headers()

            do_DELETE = do_GET

            def do_POST(self):
                fixture.authorization_seen |= 'Authorization' in self.headers
                request = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
                method = request['method']
                fixture.methods.append(method)
                if fixture.mode == 'slow':
                    time.sleep(5)
                status = {'unauthorized': 401, 'forbidden': 403, 'http_error': 503, 'redirect': 302}.get(fixture.mode)
                if status:
                    self.send_response(status)
                    if status == 302:
                        self.send_header('Location', 'http://127.0.0.1:1/must-not-follow')
                    self.end_headers()
                    return
                if method == 'notifications/initialized':
                    self.send_response(202)
                    self.end_headers()
                    return
                result = ({'protocolVersion': '2025-03-26', 'capabilities': {'tools': {}},
                           'serverInfo': {'name': 'stage17-fixture', 'version': '1'}}
                          if method == 'initialize' else {'tools': [] if fixture.mode == 'empty' else
                          [{'name': 'fixture_tool', 'inputSchema': {'type': 'object'}}]})
                response = {'jsonrpc': '2.0', 'id': request.get('id'), 'result': result}
                if fixture.mode == 'bad_protocol':
                    response = {'jsonrpc': '2.0', 'id': request.get('id'),
                                'error': {'code': -32600, 'message': 'stage17-private-sentinel'}}
                body = b'<html>stage17-private-sentinel</html>' if fixture.mode == 'html' else json.dumps(response).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                try:
                    self.wfile.write(body)
                except (BrokenPipeError, ConnectionResetError):
                    pass

        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.server.daemon_threads = True
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.endpoint = f'http://127.0.0.1:{self.server.server_port}/bb-mcp'

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, required=True)
    parser.add_argument('--distribution', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.sdk.resolve()))
    from copperbench import Workspace, NativeApiError

    distribution = args.distribution.resolve()
    java = distribution / ('jdk/bin/java.exe' if os.name == 'nt' else 'jdk/bin/java')
    launcher = [str(java), '--add-opens=java.base/java.lang=ALL-UNNAMED',
                '--enable-native-access=ALL-UNNAMED,jcef', '-Dcopperbench.productShell=true',
                *(['-Dcopperbench.stage15LinuxCandidate=true'] if os.name != 'nt' else []),
                '-cp', str(distribution / 'lib/*'), 'net.mcreator.Launcher']
    result = {'status': 'running', 'applicationSha256': hashlib.sha256(
        (distribution / 'lib/copperbench.jar').read_bytes()).hexdigest(), 'cases': []}
    try:
        with Workspace.open(args.workspace, launcher=launcher, cwd=distribution, startup_timeout=120) as workspace:
            for mode, expected in [('valid', 'tools_available'), ('empty', 'no_tools'),
                                   ('unauthorized', 'authentication_required'), ('forbidden', 'authentication_required'),
                                   ('html', 'protocol_error'), ('bad_protocol', 'protocol_error'),
                                   ('http_error', 'protocol_error'), ('redirect', 'protocol_error'), ('slow', 'timeout')]:
                fixture = Fixture(mode)
                try:
                    no_probe = workspace.query('get_blockbench_environment', endpoint=fixture.endpoint)['data']
                    assert no_probe['mcp']['state'] == 'not_checked' and not fixture.methods
                    started = time.monotonic()
                    data = workspace.query('get_blockbench_environment', probeMcp=True, endpoint=fixture.endpoint)['data']
                    elapsed = time.monotonic() - started
                    case = {'mode': mode, 'expected': expected, 'elapsedSeconds': elapsed, 'data': data,
                            'methods': list(fixture.methods), 'authorizationSeen': fixture.authorization_seen}
                    result['cases'].append(case)
                    assert elapsed < 10, case
                    assert data['mcp']['state'] == expected, case
                    assert data['editor'] == no_probe['editor'], case
                    assert data['application']['applicationSha256'] == result['applicationSha256'], case
                    assert data['application']['version'], case
                    assert not fixture.authorization_seen and 'tools/call' not in fixture.methods, case
                    assert 'stage17-private-sentinel' not in json.dumps(data), case
                finally:
                    fixture.close()
            with socket.socket() as port:
                port.bind(('127.0.0.1', 0))
                endpoint = f'http://127.0.0.1:{port.getsockname()[1]}/mcp'
            data = workspace.query('get_blockbench_environment', probeMcp=True, endpoint=endpoint)['data']
            result['cases'].append({'mode': 'closed_port', 'data': data})
            assert data['mcp']['state'] == 'unreachable'
            try:
                workspace.query('get_blockbench_environment', probeMcp=True, endpoint='http://example.invalid:3000/mcp')
                raise AssertionError('Remote endpoint was accepted')
            except NativeApiError as error:
                result['cases'].append({'mode': 'remote_rejected', 'code': error.code})
        result['status'] = 'passed'
    except Exception as error:
        result['status'] = 'failed'
        result['error'] = str(error)
        raise
    finally:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
