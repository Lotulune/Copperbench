"""Check the scoped installed replay evidence; this does not close Stage16 C."""
import hashlib
import json
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(value, message):
    if not value:
        raise ValueError(message)


manifest = read(ROOT / 'archive-manifest.json')
seen = set()
for entry in manifest['files']:
    path = (ROOT / entry['path']).resolve()
    require(path.is_relative_to(ROOT) and entry['path'] not in seen, 'Invalid archive path')
    seen.add(entry['path'])
    require(path.stat().st_size == entry['bytes'] and sha(path) == entry['sha256'], 'Raw evidence changed: ' + entry['path'])
require(len(seen) == manifest['rawFileCount'], 'Wrong raw file count')
summary = read(ROOT / 'summary.json')
require(summary['stage16Complete'] is False and summary['published'] is False, 'Unproven promotion')
require(all(vm['State'] == 'Off' and vm['MemoryAssigned'] == 0 for vm in summary['finalState']['vms']), 'VM memory not released')
profiles = read(ROOT / 'reduced-vm-memory.json')
require(len(profiles) == 2 and all(p['Maximum'] == 4 * 1024**3 and p['DynamicMemoryEnabled'] for p in profiles), 'Wrong memory cap')
require([p['Startup'] // 1024**3 for p in profiles] == [4, 3], 'Wrong startup memory')
checkpoints = read(ROOT / 'preserved-checkpoints-verified.json')
require(len(checkpoints) == 2 and all(c['State'] == 'Saved' and c['SnapshotType'] == 'Standard' for c in checkpoints), 'Original session checkpoint missing')
windows_candidate = read(ROOT / 'windows-candidate.json')
windows_install = read(ROOT / 'windows-install-result.json')
linux_candidate = read(ROOT / 'linux-candidate.json')
linux_install = read(ROOT / 'linux-installed-repair/installation.json')
require(windows_candidate['sourceCommit'] == linux_candidate['source']['commit'] == summary['sourceCommit'], 'Candidate sources differ')
require(windows_install['installerExitCode'] == 0 and windows_install['installerSha256'] == windows_candidate['installerSha256'], 'Windows installation not verified')
require(linux_install['candidateSha256'] == linux_candidate['assets']['deb']['sha256'], 'Linux installed candidate mismatch')
require(linux_install['status'] == 'passed' and linux_install['dpkgVerifyExitCode'] == 0 and not linux_install['dpkgVerifyOutput'], 'Linux package verification failed')
for key in ('productExeSha256', 'applicationJarSha256'):
    require(windows_install[key] == windows_candidate[key], 'Windows installed payload mismatch')

counts = {}
for platform, relative, report, probe in (
        ('windows', 'windows-installed-repair', 'gametest.xml', 'Stage16MemoryCompileProbe.java'),
        ('ubuntu', 'linux-installed-repair/repair-evidence', 'gametest-results.xml', 'CompileFailureProbe.java')):
    directory = ROOT / relative
    replay = read(directory / 'summary.json')
    verification = replay['verification']
    require(replay['status'] == 'passed' and replay['sourceUnchanged'], 'Replay/source preservation failed')
    require(verification['mode'] == 'packaged_jar' and verification['status'] == 'passed', 'Wrong verification mode/status')
    require(verification['acceptanceExecuted'] == 5 and verification['failed'] == 0 and verification['frameworkTests'] == 0, 'Wrong behavior counts')
    require(verification['sourceCurrentAtCompletion'] is True, 'Stale source result')
    require(sha(directory / 'tested-mod.jar') == verification['artifactSha256'], 'Tested artifact changed')
    require(sha(directory / report) == verification['reportSha256'], 'Report changed')
    snapshot = read(directory / 'source-manifest.json')
    require(snapshot['sha256'] == verification['sourceSnapshot']['sha256'], 'Source snapshot mismatch')
    tree = ET.parse(directory / report)
    cases = list(tree.iter('testcase'))
    require(len(cases) == 5 and not list(tree.iter('failure')) and not list(tree.iter('error')), 'XML assertions failed')
    require(len({(c.get('classname'), c.get('name')) for c in cases}) == 5, 'Duplicate XML cases')
    for name in ('02-no-authorization', '04-compile-failure', '05-repaired-build', '06-accepted'):
        messages = [json.loads(line) for line in (directory / (name + '.jsonl')).read_text(encoding='utf-8-sig').splitlines() if line.strip()]
        result = messages[-1]
        if name == '02-no-authorization':
            require(result['code'] == 'USER_APPROVAL_REQUIRED' and result['exitCode'] != 0, 'Unauthorized creation was not refused')
        elif name == '04-compile-failure':
            require(result['exitCode'] != 0 and any(d.get('code') == 'JAVA_COMPILE_ERROR' and probe in (d.get('path') or '') for d in result['diagnostics']), 'Failure did not identify the intended source file')
        else:
            require(result['exitCode'] == 0 and result['status'] == 'succeeded', 'Successful step not completed')
    expected_jar = windows_install['applicationJarSha256'] if platform == 'windows' else linux_install['installedApplicationJarSha256']
    require(replay['applicationJarSha256'] == expected_jar, 'Replay used another application JAR')
    counts[platform] = len(cases)

desktop = read(ROOT / 'windows-desktop-startup.json')
closed = read(ROOT / 'windows-desktop-close.json')
require(desktop['descriptor']['status'] == 'listening' and desktop['unauthenticatedInitializeHttpStatus'] == 401, 'Desktop MCP did not listen/reject unauthenticated access')
require(closed['descriptorRemoved'] and closed['endpointClosed'] and closed['desktopProcessExited'] and not closed['forcedTerminationUsed'], 'Desktop did not close normally')
trim = read(ROOT / 'host-working-set-reclaim.json')
require(trim['processesClosed'] == trim['processesSuspended'] == 0 and not trim['persistentHostSettingsChanged'], 'Unexpected host cleanup effect')
result = {'schemaVersion': '1.0', 'status': 'passed', 'scope': 'evidence integrity and stated installed CLI/desktop-startup checks',
          'rawFileCount': len(seen), 'behaviorCasesPerPlatform': counts, 'vmMaximumGiB': 4,
          'bothWorkingVmsOff': True, 'originalSavedCheckpointsPreserved': True,
          'stage16Complete': False, 'authenticatedDesktopMcpLoopVerified': False,
          'ubuntuDesktopVerified': False, 'published': False}
(ROOT / 'verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result))
