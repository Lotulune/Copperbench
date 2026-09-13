"""Derive a bounded Windows result from captured MCP responses and normal-close facts."""
import ast
import json
from pathlib import Path
root = Path(r'C:\Temp\S16-33ceb6e9')
events = [json.loads(line) for line in (root / 'agent-attempt1/mcp-tool-events.jsonl').read_text(encoding='utf-8').splitlines()]
failure_lines = (root / 'agent-post-failure-retry/helper.log').read_text(encoding='utf-8').splitlines()
response = ast.literal_eval(next(line.removeprefix('AssertionError: ') for line in failure_lines if line.startswith('AssertionError: {')))
assert response['operation'] == 'get_task' and response['data']['task']['kind'] == 'run_client'
assert response['data']['task']['state'] == 'succeeded'
assert any('WGL: The driver does not appear to support OpenGL' in line['text'] for line in response['data']['logs'])
assert any('BUILD SUCCESSFUL' in line['text'] for line in response['data']['logs'])
(root / 'captured-run-client-response.json').write_text(json.dumps(response, indent=2) + '\n', encoding='utf-8')
builds = [e['result']['task']['id'] for e in events if e['tool'] == 'build_workspace' and e.get('result', {}).get('status') == 'accepted']
assert len(builds) == 2
for task_id in builds:
    assert any(((e.get('result', {}).get('data') or {}).get('task') or {}).get('id') == task_id and
               e['result']['data']['task']['state'] == 'succeeded' for e in events)
assert any(e['tool'] == 'preview_workspace_plan' and (e.get('result', {}).get('data') or {}).get('wouldApply') for e in events)
assert any(e.get('error', {}).get('code') == 'WORKSPACE_REVISION_CONFLICT' for e in events)
assert any(e['tool'] == 'create_mod_element' and e.get('result', {}).get('newRevision') == 3 for e in events)
closed = json.loads((root / 'normal-close.json').read_text(encoding='utf-8-sig'))
assert closed['descriptorRemoved'] and closed['endpointClosed'] and closed['desktopProcessExited'] and closed['productExit'] == 0
reopened = json.loads((root / 'app-reopen.process.json').read_text())
assert reopened['exitCode'] == 0
assert not (root / 'workspace/.copperbench/mcp-connection.json').exists()
result = {'schemaVersion':'1.0', 'status':'partial',
    'candidateSha256':'717d7de82a30209b15b086daf2af9d351e47f85967f5657001d85559492803e4',
    'permissionProfile':'workspace', 'tokenPersisted':False,
    'automationAuditCredentialLeak':False,
    'credentialEvidence':'UI token was only passed through private pipes; RPC trace masks the exact token; archive audit excludes credential headers.',
    'agentLoop':{'workspaceId':response['workspaceId'], 'initialRevision':0, 'finalRevision':3,
        'initialElementCount':0, 'finalElementCount':3, 'planPreviewWouldApply':True,
        'firstBuildState':'succeeded', 'finalBuildState':'succeeded',
        'revisionConflictCode':'WORKSPACE_REVISION_CONFLICT', 'conflictRetryCommitted':True,
        'runClientLifecycle':{'taskId':response['data']['task']['id'], 'renderReady':False,
            'productReportedState':'succeeded', 'observedNativeError':'GLFW error 65542: WGL: The driver does not appear to support OpenGL',
            'normalClientExitVerified':False, 'zeroExitFalseSuccessReproduced':True}},
    'shutdown':closed, 'capturedClientResponse':'captured-run-client-response.json',
    'authenticatedOldTokenCheckCompleted':False, 'formalSupportClaim':False}
(root / 'partial-summary.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status':'partial','mcpBeforeClient':'passed','client':'OpenGL initialization failed',
                  'productReportedTaskState':response['data']['task']['state'],'normalProductClose':True}))
