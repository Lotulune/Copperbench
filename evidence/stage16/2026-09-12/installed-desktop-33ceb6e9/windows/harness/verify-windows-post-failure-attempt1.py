"""Verify MCP results and normal connection teardown while preserving a failed client gate."""
import argparse
import importlib.util
import json
from pathlib import Path
import sys
import time

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('installed_gate', HERE / 'verify-stage15-linux-installed-agent.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)
parser = argparse.ArgumentParser()
parser.add_argument('workspace', type=Path)
parser.add_argument('--candidate-sha256', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
root = HERE.parent
workspace = args.workspace.parent
events = [json.loads(line) for line in (root / 'agent-attempt1/mcp-tool-events.jsonl').read_text(encoding='utf-8').splitlines()]
accepted_builds = [e['result']['task']['id'] for e in events if e['tool'] == 'build_workspace' and e.get('result', {}).get('status') == 'accepted']
assert len(accepted_builds) == 2
for task_id in accepted_builds:
    assert any(e.get('result', {}).get('data', {}).get('task', {}).get('id') == task_id and
               e['result']['data']['task']['state'] == 'succeeded' for e in events)
assert any(e['tool'] == 'preview_workspace_plan' and e.get('result', {}).get('data', {}).get('wouldApply') for e in events)
assert any(e.get('error', {}).get('code') == 'WORKSPACE_REVISION_CONFLICT' for e in events)
run_id = next(e['result']['task']['id'] for e in events if e['tool'] == 'run_client' and e.get('result', {}).get('status') == 'accepted')
connection = gate.read_workspace_connection(args.workspace)
token = sys.stdin.readline().rstrip('\r\n')
assert token
client = gate.CopperbenchClient(connection['url'], token, connection['workspaceId'], timeout=60)
client.initialize('stage16-installed-post-failure', '1.0')
current = client.get_workspace()
assert gate.workspace_revision(current) == 3
assert len(list(client.list_mod_elements(limit=1))) == 3
failed = gate.wait_task(client, run_id, timeout_seconds=60)
assert failed['state'] == 'failed', failed['result']
latest = (workspace / 'run/logs/latest.log').read_text(encoding='utf-8')
assert 'WGL: The driver does not appear to support OpenGL' in latest
audit = (workspace / '.copperbench/automation-audit.jsonl').read_text(encoding='utf-8')
assert token not in audit
print('Close installed Copperbench normally to verify MCP teardown after the recorded OpenGL failure.', flush=True)
descriptor = workspace / '.copperbench/mcp-connection.json'
deadline = time.monotonic() + 300
while descriptor.exists() and time.monotonic() < deadline:
    time.sleep(0.5)
assert not descriptor.exists(), 'Descriptor survived normal workspace close'
try:
    client.get_workspace()
except gate.CopperbenchError as error:
    connection_failure = error.code
else:
    raise AssertionError('Old MCP endpoint/token still accepted requests')
result = {'schemaVersion':'1.0', 'status':'partial', 'candidateSha256':args.candidate_sha256,
    'permissionProfile':'workspace', 'tokenPersisted':False, 'automationAuditCredentialLeak':False,
    'agentLoop':{'workspaceId':connection['workspaceId'], 'initialRevision':0, 'finalRevision':3,
        'initialElementCount':0, 'finalElementCount':3, 'planPreviewWouldApply':True,
        'firstBuildState':'succeeded', 'finalBuildState':'succeeded',
        'revisionConflictCode':'WORKSPACE_REVISION_CONFLICT', 'conflictRetryCommitted':True,
        'runClientLifecycle':{'taskId':run_id, 'renderReady':False, 'terminalStateAfterFailure':'failed',
            'observedNativeError':'GLFW error 65542: WGL: The driver does not appear to support OpenGL',
            'normalClientExitVerified':False}},
    'failedClientTask':failed, 'shutdown':{'descriptorRemoved':True, 'oldConnectionRejected':True,
        'oldConnectionFailureCode':connection_failure}, 'formalSupportClaim':False}
encoded = json.dumps(result, indent=2)
assert token not in encoded
args.output.write_text(encoded + '\n', encoding='utf-8')
print(json.dumps({'status':'partial', 'mcpLoopVerified':True, 'shutdownVerified':True,
                  'clientBlockedBy':'OpenGL driver unavailable'}), flush=True)
