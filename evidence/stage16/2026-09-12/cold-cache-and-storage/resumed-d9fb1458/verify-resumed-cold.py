"""Check archived cold-cache evidence, source/artifact identity, and lifecycle results."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import re
import zipfile

ARCHIVE = Path(r'D:\AICoding\Minecraft_ModCreator\.tmp\stage15-linux\evidence\stage16\2026-09-12\cold-cache-and-storage\resumed-d9fb1458')
COMMIT = 'd9fb1458187110186c04e523d5044250637490bd'
JAR = '3d58f235c0609d4c18fd6d9b43680bbfd9c80c168dbccbe9eccc5b951b05ac19'

def read(path):
    return json.loads((ARCHIVE / path).read_text(encoding='utf-8-sig'))

def seconds(start, end):
    return round((datetime.fromisoformat(end.replace('Z', '+00:00')) -
                  datetime.fromisoformat(start.replace('Z', '+00:00'))).total_seconds(), 3)

association = read('source-association.json')
assert association['sourceCommit'] == COMMIT and association['committedSourceMatchesFrozenCandidateSource']
platforms = {}
for label, installer in [('windows-official', '2cb006da0f47564c385e3192dcd8aef565f06673db71e0f411527e5231bde11f'),
                         ('ubuntu-official', '977bf324cf9838539c0a5de655e51b22e2dd9e42e82f3cc78b0fb200c90f108a')]:
    folder = ARCHIVE / label
    config = read(f'{label}/candidate.json')
    assert config['sourceCommit'] == COMMIT and config['applicationJarSha256'] == JAR
    assert config['installerSha256'] == installer
    initial = read(f'{label}/cold-initial-state.json')
    assert all(initial[key] is False for key in ['workspaceExists', 'dependencyCacheExists',
        'wrapperCacheExists', 'minecraftAssetsExist', 'sourceCacheCopied'])
    cache = read(f'{label}/cache-before-launch.json')['folders']
    assert cache['gradle']['files'] == 1
    assert cache['gradle']['entries'][0]['path'] == 'gradle.properties'
    assert all(not cache[key]['exists'] for key in ['workspaceGradle', 'workspaceRun', 'isolatedDefaultGradle'])
    preferences = read(f'{label}/isolated-userpreferences.json')['core']
    assert preferences['gradle']['Xmx'] == 1024 and preferences['gradle']['useChinaMirrors'] is False
    properties = (folder / 'isolated-gradle.properties').read_text(encoding='utf-8-sig')
    assert 'org.gradle.workers.max=1' in properties and 'org.gradle.parallel=false' in properties
    visibility = read(f'{label}/first-use-visibility.json')
    assert visibility['installedFirstUseDialogVisible'] and visibility['nativeOfficialSourceChoice']
    assert visibility['regionPrompted'] and visibility['mirrorPreference'] is False
    for phase in ['grant', 'create', 'app', 'app-reopen', 'revoke']:
        assert read(f'{label}/{phase}.process.json')['exitCode'] == 0
    assert read(f'{label}/create.stdout.txt')['status'] == 'committed'
    assert read(f'{label}/revoked-create.process.json')['exitCode'] == 3
    assert read(f'{label}/revoked-create.stdout.txt')['code'] == 'TASK_AUTHORIZATION_REVOKED'
    result = read(f'{label}/agent-attempt1/external-agent-result.json')
    assert result['status'] == 'passed' and result['sourceCommit'] == COMMIT
    assert result['tokenPersisted'] is False and result['automationAuditCredentialLeak'] is False
    assert result['automationAuditExactTokenCheckCompleted'] and result['coldCacheLogCredentialCheck']['passed']
    loop = result['agentLoop']
    assert loop['initialRevision'] == 0 and loop['finalRevision'] == 3
    assert loop['initialElementCount'] == 0 and loop['finalElementCount'] == 3
    assert loop['planPreviewWouldApply'] and loop['conflictRetryCommitted']
    assert loop['revisionConflictCode'] == 'WORKSPACE_REVISION_CONFLICT'
    assert loop['firstBuildState'] == loop['finalBuildState'] == 'succeeded'
    lifecycle = loop['runClientLifecycle']
    assert lifecycle['renderReady'] and lifecycle['stableBeforeUserCloseSeconds'] >= 10
    assert lifecycle['terminalStateAfterUserClose'] == 'succeeded'
    assert result['shutdown']['descriptorRemoved'] and result['shutdown']['oldConnectionRejected']
    assert result['reopen']['workspaceIdPreserved'] and result['reopen']['descriptorRemovedAfterSecondClose']
    assert result['reopen']['oldTokenRejectedOnNewEndpoint']
    assert result['reopen']['oldTokenFailure']['code'] == 'HTTP_401'
    assert 'TOKEN_INVALID' in result['reopen']['oldTokenFailure']['details']
    assets = read(f'{label}/cold-download-verification.json')
    assert assets['status'] == 'passed' and assets['allAssetHashesVerified']
    assert assets['initialDependencyCacheEmpty'] and assets['initialAssetsEmpty']
    assert len(assets['indexes']) == 1
    index = assets['indexes'][0]
    assert index['uniqueObjects'] == 3888 and index['expectedBytes'] == 824678547
    assert not index['mismatches'] and not index['objectsPredatingTrial']
    facts = read(f'{label}/collection-facts.json')
    assert facts['descriptorAbsentAfterReopenClose'] and facts['freshAgent'] is False
    assert hashlib.sha256((folder / 'stage16_cold-1.0.jar').read_bytes()).hexdigest() == facts['artifactSha256']
    source_files = read(f'{label}/final-source-manifest.json')
    with zipfile.ZipFile(folder / 'final-workspace-source.zip') as archive:
        assert set(archive.namelist()) == {entry['path'] for entry in source_files}
        for entry in source_files:
            data = archive.read(entry['path'])
            assert len(data) == entry['bytes'] and hashlib.sha256(data).hexdigest() == entry['sha256']
    events = [json.loads(line) for line in (folder / 'agent-attempt1/mcp-tool-events.jsonl').read_text(encoding='utf-8').splitlines() if line]
    terminal = {}
    for event in events:
        if event['tool'] == 'get_task':
            task = event.get('result', {}).get('data', {}).get('task', {})
            if task.get('completedAt'):
                terminal[task['id']] = task
    task_metrics = []
    for event in events:
        if event['tool'] in ['build_workspace', 'run_client']:
            task = terminal[event['result']['task']['id']]
            assert task['state'] == 'succeeded'
            task_metrics.append({'kind': task['kind'], 'taskId': task['id'], 'startedAt': task['startedAt'],
                                 'completedAt': task['completedAt'], 'seconds': seconds(task['startedAt'], task['completedAt']),
                                 'firstResponseSeconds': event['elapsedSeconds']})
    assert [task['kind'] for task in task_metrics] == ['build', 'build', 'run_client']
    creation = read(f'{label}/create.process.json')
    platforms[label] = {'status': 'passed', 'createSeconds': seconds(creation['startedAt'], creation['completedAt']),
        'firstMcpResponseSeconds': events[0]['elapsedSeconds'], 'tasks': task_metrics,
        'assetObjects': index['uniqueObjects'], 'assetBytes': index['expectedBytes'],
        'screenshots': len(list((folder / 'screenshots').glob('*.jpg'))), 'freshAgent': False,
        'newGameTestCases': 0, 'nativeAppExitCodes': [0, 0], 'oldTokenStatus': 401}

assert read('windows-mirror-failure/create.process.json')['exitCode'] == 10
assert read('ubuntu-network-failure/create.process.json')['exitCode'] == 10
assert read('ubuntu-network-failure/failure-checkpoint.json')['fullColdCachePassed'] is False
samples = [json.loads(line) for line in (ARCHIVE / 'resource-samples.jsonl').read_text(encoding='utf-8-sig').splitlines() if line]
assert samples and all(sum(vm['state'] == 'Running' for vm in row['virtualMachines']) <= 1 for row in samples)
assert all(vm['assignedBytes'] <= 4 * 1024**3 and vm['maximumBytes'] <= 4 * 1024**3
           for row in samples for vm in row['virtualMachines'])
final = read('final-resource-state.json')
assert final['javaProcessCount'] == 0
assert all(vm['state'] == 'Off' and vm['assignedBytes'] == 0 for vm in final['virtualMachines'])

issues = []
checked = 0
manifest = []
excluded = {'final-verification.json', 'final-manifest.json', 'final-credential-scan.json'}
for path in sorted(ARCHIVE.rglob('*')):
    if not path.is_file() or path.name in excluded:
        continue
    data = path.read_bytes()
    relative = path.relative_to(ARCHIVE).as_posix()
    manifest.append({'path': relative, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    payloads = [(relative, data)]
    if path.suffix in {'.zip', '.jar'}:
        with zipfile.ZipFile(path) as archive:
            payloads += [(relative + '!' + name, archive.read(name)) for name in archive.namelist() if not name.endswith('/')]
    for name, content in payloads:
        if name.endswith(('.jpg', '.png', '.class')):
            continue
        checked += 1
        if re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----', content):
            issues.append(name + ': private key')
        if re.search(rb'Authorization:\s*Bearer\s+[A-Za-z0-9_-]{24,}', content):
            issues.append(name + ': full Bearer header')
        if re.search(rb'"token"\s*:\s*"[A-Za-z0-9_-]{24,}"', content):
            issues.append(name + ': literal token value')
assert not issues, issues
scan = {'status': 'passed', 'payloadsChecked': checked, 'matches': issues,
        'exactSessionTokenChecks': 'Both running helpers checked their exact token against application logs and automation audit before discarding it',
        'patterns': ['private key blocks', 'full Bearer headers', 'literal token values']}
summary = {'at': datetime.now(timezone.utc).isoformat(), 'status': 'passed', 'sourceCommit': COMMIT,
    'applicationJarSha256': JAR, 'fullColdCacheComplete': True, 'platforms': platforms,
    'preservedFailedCreationTrials': ['windows-mirror-failure', 'ubuntu-network-failure'],
    'resourceSamples': len(samples), 'maxRunningVirtualMachines': 1, 'virtualMachineCapGiB': 4,
    'minimumHostAvailableGiB': round(min(row['availableBytes'] for row in samples) / 1024**3, 3),
    'finalDiskDFreeGiB': round(final['diskDFreeBytes'] / 1024**3, 3),
    'archivedFiles': len(manifest), 'newGameTestCases': 0, 'newFreshAgentTrials': 0,
    'publicReleaseAuthorized': False}
for name, value in [('final-manifest.json', {'files': manifest}), ('final-credential-scan.json', scan),
                    ('final-verification.json', summary)]:
    (ARCHIVE / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False))
