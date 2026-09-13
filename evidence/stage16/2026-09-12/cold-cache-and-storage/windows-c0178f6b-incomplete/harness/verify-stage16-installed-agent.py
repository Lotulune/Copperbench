"""Reuse every installed-Agent assertion, attaching the UI-issued Stage16 grant."""
import importlib.util
import json
import os
from pathlib import Path
import sys
from datetime import datetime, timezone
import time

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('installed_agent', HERE / 'verify-stage15-linux-installed-agent.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
grant_file = HERE.parent / 'grant.stdout'
grant = json.loads(grant_file.read_text(encoding='utf-8-sig'))
assert grant['status'] == 'completed'
authorization_id = grant['data'].get('authorizationId') or grant['data'].get('id')
assert authorization_id
BaseClient = gate.CopperbenchClient

class ScopedClient(BaseClient):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, task_authorization_id=authorization_id, **kwargs)
        gate.active_scoped_client = self

    def call_tool(self, name, arguments):
        began = time.monotonic()
        event = {'at': datetime.now(timezone.utc).isoformat(), 'tool': name, 'arguments': arguments,
                 'taskAuthorizationId': self.task_authorization_id}
        try:
            result = super().call_tool(name, arguments)
            event['result'] = result
            return result
        except Exception as error:
            event['error'] = {'type': type(error).__name__, 'code': getattr(error, 'code', None),
                              'details': getattr(error, 'details', None)}
            raise
        finally:
            event['elapsedSeconds'] = round(time.monotonic() - began, 6)
            trace = Path(sys.argv[sys.argv.index('--output') + 1]).with_name('mcp-tool-events.jsonl')
            with trace.open('a', encoding='utf-8') as log:
                log.write(json.dumps(event, ensure_ascii=False).replace(self.token, '[REDACTED]') + '\n')

gate.CopperbenchClient = ScopedClient
if os.name == 'nt':
    # A GUI launcher already masked the entry; Windows getpass expects a console.
    gate.getpass.getpass = lambda prompt: sys.stdin.readline().rstrip('\r\n')

def expected_opengl_failure(client, revision, **kwargs):
    accepted = client.call_tool('run_client', {'expectedRevision': revision})
    gate.require(accepted.get('status') == 'accepted', 'run_client was not accepted')
    run_id = gate.task_id(accepted)
    finished = gate.wait_task(client, run_id, timeout_seconds=900)
    gate.require(finished['state'] == 'failed', 'OpenGL failure was not reported as failed')
    data = finished['result'].get('data') or {}
    diagnostics = data.get('diagnostics') or finished['result'].get('diagnostics') or []
    expected_code = 'FABRIC_RUN_CLIENT_WINDOWS_OPENGL_INITIALIZATION_FAILED'
    diagnostic = next((d for d in diagnostics if d.get('code') == expected_code), None)
    gate.require(diagnostic is not None, 'Stable Windows OpenGL diagnostic is missing')
    message = diagnostic.get('message') or {}
    gate.require(message.get('key') == 'diagnostic.task_client_opengl_initialization_failed',
                 'OpenGL diagnostic localization key is missing')
    gate.require((message.get('args') or {}).get('exitCode') == 0,
                 'This regression must reproduce the actual zero-exit failure')
    lines = [entry.get('text', '') for entry in finished['logs']]
    gate.require(any('WGL: The driver does not appear to support OpenGL' in line for line in lines),
                 'Actual native WGL failure is absent from task logs')
    gate.require(any('BUILD SUCCESSFUL' in line for line in lines), 'Gradle zero-exit success log is absent')
    gate.require(not any('minecraft:textures/atlas/blocks.png-atlas' in line for line in lines),
                 'Unexpected render readiness: this is a negative graphics gate')
    output = Path(sys.argv[sys.argv.index('--output') + 1])
    encoded = json.dumps(finished, indent=2, ensure_ascii=False).replace(client.token, '[REDACTED]')
    output.with_name('run-client-complete-response.json').write_text(encoded + '\n', encoding='utf-8')
    print('Expected native OpenGL failure correctly reported; exit zero did not produce success.', flush=True)
    return {'taskId': run_id, 'renderReady': False, 'terminalStateAfterFailure': 'failed',
            'diagnosticCode': expected_code, 'processExitCode': 0,
            'normalClientExitVerified': False, 'negativeGraphicsRegressionPassed': True}

# Use the original real-render readiness and normal-close assertions on the physical host.
gate.main()
output = Path(sys.argv[sys.argv.index('--output') + 1])
result = json.loads(output.read_text(encoding='utf-8'))
result['status'] = 'in_progress'
output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
old_client = gate.active_scoped_client
workspace_file = Path(sys.argv[1])
descriptor = workspace_file.parent / '.copperbench/mcp-connection.json'
print('Reopen installed Copperbench now to verify the old token is rejected by the new session.', flush=True)
deadline = time.monotonic() + 300
while not descriptor.exists() and time.monotonic() < deadline:
    time.sleep(0.5)
gate.require(descriptor.exists(), 'Workspace was not reopened before the deadline')
connection = gate.read_workspace_connection(workspace_file)
probe = BaseClient(connection['url'], old_client.token, connection['workspaceId'], timeout=30)
try:
    probe.initialize('stage16-old-token-after-reopen', '1.0')
except gate.CopperbenchError as error:
    old_token_error = {'code': error.code, 'details': error.details}
else:
    raise RuntimeError('New Desktop MCP session accepted the previous session token')
gate.require(old_token_error['code'] == 'HTTP_401', 'Old-token probe did not specifically return HTTP 401')
audit = (workspace_file.parent / '.copperbench/automation-audit.jsonl').read_text(encoding='utf-8')
gate.require(old_client.token not in audit, 'Old token leaked into the reopened audit')
result['reopen'] = {'descriptorCreated': True, 'workspaceIdPreserved': connection['workspaceId'] == old_client.workspace_id,
                    'oldTokenRejectedOnNewEndpoint': True, 'oldTokenFailure': old_token_error}
gate.require(result['reopen']['workspaceIdPreserved'], 'Reopened workspace identity changed')
print('Old token rejected by reopened session. Close installed Copperbench normally again.', flush=True)
deadline = time.monotonic() + 300
while descriptor.exists() and time.monotonic() < deadline:
    time.sleep(0.5)
gate.require(not descriptor.exists(), 'Reopened descriptor survived the second normal close')
result['reopen']['descriptorRemovedAfterSecondClose'] = True
checked_logs = []
for log_path in [*(HERE.parent / 'home/.copperbench/logs').glob('*.log'),
                 *(HERE.parent).glob('*.stdout'), *(HERE.parent).glob('*.stderr')]:
    if log_path.is_file():
        gate.require(old_client.token.encode() not in log_path.read_bytes(),
                     'Desktop log contains the session token')
        checked_logs.append(log_path.relative_to(HERE.parent).as_posix())
result['coldCacheLogCredentialCheck'] = {'passed': True, 'logsChecked': checked_logs}

result.update({'validationKind': 'installed-windows-real-graphics-positive-regression',
               'status': 'passed',
               'stage16Complete': False, 'windowsInstalledClientComplete': True,
               'automationAuditExactTokenCheckCompleted': True,
               'sourceCommit': json.loads((HERE.parent / 'candidate.json').read_text(encoding='utf-8-sig'))['sourceCommit']})
output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

