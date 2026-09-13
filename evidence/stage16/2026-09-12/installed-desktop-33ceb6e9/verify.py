"""Validate the archived installed Desktop MCP evidence without launching products."""
import hashlib
import json
import re
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parent
def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))
def require(condition, message):
    if not condition:
        raise ValueError(message)
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

manifest = read(ROOT / 'archive-manifest.json')
seen = set()
for item in manifest['files']:
    path = (ROOT / item['path']).resolve()
    require(path.is_relative_to(ROOT) and item['path'] not in seen, 'Unsafe or duplicate archive path')
    require(path.stat().st_size == item['bytes'] and sha(path) == item['sha256'], 'Changed evidence: ' + item['path'])
    seen.add(item['path'])
require(len(seen) == manifest['rawFileCount'], 'Raw file count differs')
summary = read(ROOT / 'summary.json')
require(summary['sourceCommit'] == '33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5', 'Wrong frozen candidate')
require(summary['published'] is False and summary['freshAgent'] is False, 'Unproven promotion')
passed = []
for platform in summary['verifiedPlatforms']:
    directory = ROOT / platform
    candidate = summary['candidates'][platform]
    result = read(directory / summary['resultFiles'][platform])
    require(result['status'] == summary['platformStatuses'][platform] and result['candidateSha256'] == candidate['installerSha256'], 'Installed result scope mismatch')
    require(result['permissionProfile'] == 'workspace' and not result['tokenPersisted'], 'Credential or permission problem')
    if platform == 'ubuntu':
        require(result['automationAuditCredentialLeak'] is False, 'Ubuntu token comparison failed')
    else:
        require(result['automationAuditExactTokenCheckCompleted'] is False and result['automationAuditCredentialLeak'] is None, 'Unproven Windows exact-token comparison')
    loop = result['agentLoop']
    require(loop['initialRevision'] == 0 and loop['finalRevision'] == 3, 'Revision continuity failed')
    require(loop['initialElementCount'] == 0 and loop['finalElementCount'] == 3, 'Element readback failed')
    require(loop['planPreviewWouldApply'] and loop['conflictRetryCommitted'], 'Plan/conflict recovery failed')
    require(loop['revisionConflictCode'] == 'WORKSPACE_REVISION_CONFLICT', 'Wrong conflict')
    require(loop['firstBuildState'] == loop['finalBuildState'] == 'succeeded', 'Build did not finish')
    lifecycle = loop['runClientLifecycle']
    if platform == 'ubuntu':
        require(lifecycle['renderReady'] and lifecycle['stableBeforeUserCloseSeconds'] >= 10, 'Client readiness or stability missing')
        require(lifecycle['terminalStateAfterUserClose'] == 'succeeded', 'Client normal close missing')
    else:
        require(result['status'] == 'partial' and not lifecycle['renderReady'] and not lifecycle['normalClientExitVerified'], 'Unproven Windows client pass')
        captured = read(directory / 'captured-run-client-response.json')
        require(lifecycle['productReportedState'] == 'succeeded' and captured['data']['task']['state'] == 'succeeded', 'Original false-success response was not preserved')
        require(lifecycle['zeroExitFalseSuccessReproduced'], 'False-success finding was dropped')
        require(read(directory / 'agent-attempt1/desktop-launcher-result.json')['helperExitCode'] == 1, 'Original failed verifier result changed')
    require(result['shutdown']['descriptorRemoved'], 'Descriptor survived shutdown')
    if platform == 'ubuntu':
        require(result['shutdown']['oldConnectionRejected'], 'Stale authenticated connection survived shutdown')
        require(read(directory / 'agent-attempt1/desktop-launcher-result.json')['helperExitCode'] == 0, 'Ubuntu verifier failed')
    else:
        require(result['shutdown']['endpointClosed'] and result['shutdown']['desktopProcessExited'], 'Windows normal close failed')
        require(result['authenticatedOldTokenCheckCompleted'] is False, 'Unproven Windows token rejection claim')
    grant = read(directory / 'grant.stdout.txt')['data']
    require(set(grant['capabilities']) == {'create', 'edit', 'build', 'test', 'run_client'}, 'Unexpected grant capabilities')
    require(grant['root'] == summary['roots'][platform] and not grant['serverEulaAccepted'], 'Unexpected grant scope')
    create = read(directory / 'create.stdout.txt')
    require(create['status'] == 'committed' and create['exitCode'] == 0 and create['data']['generatorId'] == 'fabric-1.21.1', 'Workspace creation failed')
    audit = [json.loads(line) for line in (directory / 'automation-audit.jsonl').read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    protected = {'create_mod_element', 'apply_workspace_plan', 'build_workspace', 'run_client'}
    for tool in protected:
        starts = [e for e in audit if e['tool'] == tool and e['result'] == 'started']
        require(bool(starts), 'Missing real MCP operation: ' + tool)
        # parameterSummary is intentionally bounded text and large plans may be truncated.
        matches = [re.match(r'^\{\s*"taskAuthorizationId"\s*:\s*"([^"]+)"', e['parameterSummary']) for e in starts]
        require(all(match and match.group(1) == grant['id'] for match in matches), 'MCP operation omitted the user grant')
    facts = read(directory / 'collection-facts.json')
    require(facts['installedAppJarSha256'].lower() == candidate['appJarSha256'], 'Wrong installed application bytes')
    require(facts['descriptorAbsentAfterReopenClose'], 'Reopen close did not clean connection')
    require(facts['revokedCreateCode'] == 'TASK_AUTHORIZATION_REVOKED', 'Revoked authority was accepted')
    for tool in ('java', 'javac', 'gradle', 'git'):
        require(not facts['tooling'].get(tool), 'Unexpected system developer tool: ' + tool)
    source_manifest = read(directory / 'final-source-manifest.json')
    with zipfile.ZipFile(directory / 'final-workspace-source.zip') as archive:
        require(set(archive.namelist()) == {f['path'] for f in source_manifest}, 'Source archive inventory changed')
        for entry in source_manifest:
            data = archive.read(entry['path'])
            require(len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256'], 'Source byte mismatch')
    artifact = list(directory.glob('stage16_installed-*.jar'))
    require(len(artifact) == 1 and sha(artifact[0]) == facts['artifactSha256'], 'Built artifact changed')
    minecraft = (directory / 'minecraft-latest.log.txt').read_text(encoding='utf-8-sig')
    if platform == 'ubuntu':
        require(all(marker in minecraft for marker in lifecycle['renderMarkers']) and 'Stopping!' in minecraft, 'Minecraft lifecycle log missing')
    else:
        require('WGL: The driver does not appear to support OpenGL' in minecraft, 'Native OpenGL failure missing')
    passed.append(platform)
samples = [json.loads(line) for line in (ROOT / 'host-memory.jsonl').read_text(encoding='utf-8-sig').splitlines() if line.strip()]
require(samples and all(sum(v['state'] == 'Running' for v in s['vms']) <= 1 for s in samples), 'VMs overlapped')
require(all(v['assignedBytes'] <= 4 * 1024**3 for s in samples for v in s['vms']), 'VM exceeded the 4 GiB limit')
require(all(v['State'] == 'Off' and v['MemoryAssigned'] == 0 for v in summary['finalState']['vms']), 'VM memory not released')
require(not summary['stage16Complete'] and not summary['windowsInstalledClientComplete'], 'Unproven Stage16/client closure')
print(json.dumps({'status': 'passed', 'scope':'evidence integrity and correctly bounded platform results',
                  'verifiedPlatforms': passed, 'platformStatuses':summary['platformStatuses'], 'rawFileCount': len(seen),
                  'maxVmGiB': 4, 'oneVmAtATime': True, 'stage16Complete':False, 'published': False}))
