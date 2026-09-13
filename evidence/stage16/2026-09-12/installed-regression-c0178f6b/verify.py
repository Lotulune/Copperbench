"""Verify archived installed regressions, their actual failures, and their stated limits."""
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parent
def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()
def require(value, message):
    if not value:
        raise AssertionError(message)

manifest = read(ROOT / 'archive-manifest.json')
seen = set()
for entry in manifest['files']:
    path = (ROOT / entry['path']).resolve()
    require(path.is_relative_to(ROOT.resolve()) and entry['path'] not in seen,'Invalid archive path')
    require(path.stat().st_size == entry['bytes'] and sha(path) == entry['sha256'],'Evidence changed: '+entry['path'])
    seen.add(entry['path'])
require(len(seen) == manifest['rawFileCount'],'Archive count mismatch')
summary = read(ROOT / 'summary.json')
commit = 'c0178f6b933825921cc3fb3f8f8ef8aa0874e688'
require(summary['sourceCommit'] == commit and not summary['published'] and not summary['freshAgent'],'Candidate scope mismatch')
for name, case in summary['cases'].items():
    directory = ROOT / name
    candidate = read(directory / 'candidate.json')
    result = read(directory / case['result'])
    require(result['status'] == 'passed' and result['sourceCommit'] == commit,'Incomplete result')
    require(result['candidateSha256'] == candidate['installerSha256'],'Wrong installer tested')
    require(result['permissionProfile'] == 'workspace' and not result['tokenPersisted'],'Credential profile mismatch')
    require(result['automationAuditExactTokenCheckCompleted'] and result['automationAuditCredentialLeak'] is False,'Token audit did not pass')
    loop = result['agentLoop']
    require((loop['initialRevision'],loop['finalRevision']) == (0,3),'Revision continuity failed')
    require((loop['initialElementCount'],loop['finalElementCount']) == (0,3),'Element readback failed')
    require(loop['planPreviewWouldApply'] and loop['conflictRetryCommitted'],'Plan or recovery failed')
    require(loop['revisionConflictCode'] == 'WORKSPACE_REVISION_CONFLICT','Wrong conflict code')
    require(loop['firstBuildState'] == loop['finalBuildState'] == 'succeeded','Build did not succeed')
    require(result['shutdown']['descriptorRemoved'] and result['shutdown']['oldConnectionRejected'],'First close failed')
    require(result['reopen']['workspaceIdPreserved'] and result['reopen']['oldTokenRejectedOnNewEndpoint'],'Reopened session accepted old authority')
    require(result['reopen']['oldTokenFailure']['code'] == 'HTTP_401' and 'TOKEN_INVALID' in result['reopen']['oldTokenFailure']['details'],'Old-token rejection was not authenticated HTTP 401')
    require(result['reopen']['descriptorRemovedAfterSecondClose'],'Second close left descriptor')
    for label in ('grant','create','app','app-reopen'):
        require(read(directory / (label+'.process.json'))['exitCode'] == 0,'Native process failed: '+label)
    require(read(directory / Path(case['result']).parent / 'desktop-launcher-result.json')['helperExitCode'] == 0,'Final helper failed')
    grant = read(directory / 'grant.stdout.txt')['data']
    require(grant['root'] == candidate['guestRoot'] and not grant['serverEulaAccepted'],'Unexpected task scope')
    require(set(grant['capabilities']) == {'create','edit','build','test','run_client'},'Unexpected capabilities')
    audit = [json.loads(line) for line in (directory / 'automation-audit.jsonl').read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    for tool in ('create_mod_element','apply_workspace_plan','build_workspace','run_client'):
        starts = [e for e in audit if e['tool'] == tool and e['result'] == 'started']
        require(starts,'Missing operation: '+tool)
        require(all((m := re.match(r'^\{\s*"taskAuthorizationId"\s*:\s*"([^"]+)"', e['parameterSummary'])) and
                    m.group(1) == grant['id'] for e in starts),'Operation omitted scoped authority')
    facts = read(directory / 'collection-facts.json')
    require(facts['installedAppJarSha256'] == candidate['applicationJarSha256'],'Wrong installed JAR')
    require(facts['descriptorAbsentAfterReopenClose'] and facts['revokedCreateCode'] == 'TASK_AUTHORIZATION_REVOKED','Revocation or teardown failed')
    require(read(directory / 'revoked-create.process.json')['exitCode'] == 3,'Revoked creation was not denied')
    source = read(directory / 'final-source-manifest.json')
    with zipfile.ZipFile(directory / 'final-workspace-source.zip') as archive:
        require(set(archive.namelist()) == {e['path'] for e in source},'Source archive inventory mismatch')
        require('.git' not in archive.namelist(),'Repository pointer was archived')
        for entry in source:
            data = archive.read(entry['path'])
            require(len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256'],'Source archive changed')
    artifacts = list(directory.glob('stage16_installed-*.jar'))
    require(len(artifacts) == 1 and sha(artifacts[0]) == facts['artifactSha256'],'Built mod bytes changed')
    lifecycle = loop['runClientLifecycle']
    minecraft = (directory / 'minecraft-latest.log.txt').read_text(encoding='utf-8-sig')
    if name == 'windows-vm':
        require(lifecycle['negativeGraphicsRegressionPassed'] and not lifecycle['renderReady'],'Negative graphics scope changed')
        require(lifecycle['terminalStateAfterFailure'] == 'failed' and lifecycle['processExitCode'] == 0,'Zero-exit failure regressed')
        complete = read(directory / 'agent-attempt1/run-client-complete-response.json')
        diagnostics = complete['result']['data']['diagnostics']
        require(any(d['code'] == 'FABRIC_RUN_CLIENT_WINDOWS_OPENGL_INITIALIZATION_FAILED' and
                    d['message']['args']['exitCode'] == 0 for d in diagnostics),'Stable diagnostic missing')
        require(any('BUILD SUCCESSFUL' in entry['text'] for entry in complete['logs']),'Actual Gradle success log missing')
        require('WGL: The driver does not appear to support OpenGL' in minecraft,'Native WGL failure missing')
    else:
        require(lifecycle['renderReady'] and lifecycle['stableBeforeUserCloseSeconds'] >= 10,'Real render readiness missing')
        require(lifecycle['terminalStateAfterUserClose'] == 'succeeded' and 'Stopping!' in minecraft,'Normal Minecraft close failed')
        require(all(marker in minecraft for marker in lifecycle['renderMarkers']),'Native render markers absent')
    if name != 'windows-host':
        require(all(not facts['tooling'].get(tool) for tool in ('java','javac','gradle','git')),'Unexpected system developer tools')
    if name == 'ubuntu':
        install = read(directory / 'installation.json')
        require(install['allPayloadHashesAndModesMatched'] and install['verifiedPayloadFiles'] == 2681,'Linux installed file verification missing')
        require(read(directory / 'helper-launcher.process.json')['exitCode'] == 2,'Original empty-input transport attempt was not preserved')
samples = [json.loads(line) for line in (ROOT / 'host-memory.jsonl').read_text(encoding='utf-8-sig').splitlines()]
require(samples and all(sum(v['state'] == 'Running' for v in s['vms']) <= 1 for s in samples),'VM workloads overlapped')
require(all(v['assignedBytes'] <= 4*1024**3 for s in samples for v in s['vms']),'VM memory cap exceeded')
require(all(v['state'] == 'Off' and v['assignedBytes'] == 0 for v in read(ROOT / 'final-vm-state.json')['vms']),'VM memory was not released')
require(summary['windowsInstalledClientComplete'] and summary['ubuntuInstalledClientComplete'],'Positive installed client result missing')
require(not summary['completeColdCacheVerified'] and not summary['stage16Complete'],'Unproven closure')
print(json.dumps({'status':'passed','scope':'candidate provenance, installed regressions, negative diagnostics, and final memory release',
    'cases':list(summary['cases']),'rawFiles':len(seen),'memorySamples':len(samples),'oneVmAtATime':True,'maximumVmGiB':4,
    'windowsInstalledClientComplete':True,'ubuntuInstalledClientComplete':True,'stage16Complete':False}))
