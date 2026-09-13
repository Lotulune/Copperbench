"""Verify archived hashes, task lifecycles and readback logs; not screenshot recognition."""

import datetime as dt
import hashlib
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def lines(path):
    return [json.loads(line) for line in path.read_text(encoding='utf-8-sig').splitlines() if line.strip()]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


manifest = read(ROOT / 'archive-manifest.json')
paths = set()
for entry in manifest['files']:
    target = (ROOT / entry['path']).resolve()
    require(target.is_relative_to(ROOT), 'Archive path escapes evidence directory')
    require(entry['path'] not in paths, 'Duplicate archive path')
    paths.add(entry['path'])
    require(target.stat().st_size == entry['bytes'], f"Size mismatch: {entry['path']}")
    require(digest(target) == entry['sha256'], f"SHA-256 mismatch: {entry['path']}")
    require(entry['bytesChanged'] is False, 'Raw evidence must remain byte-identical')
require(len(paths) == manifest['rawEvidenceFiles'], 'Incorrect raw file count')

candidate = read(ROOT / 'candidate.json')
acceptance = read(ROOT / 'acceptance.json')
require(candidate['sourceCommit'] == acceptance['candidateSourceCommit'], 'Candidate mismatch')
require(acceptance['stage16Complete'] is False and acceptance['published'] is False, 'Invalid stage/release promotion')
identity = read(ROOT / 'artifact-identity-after.json')
require(identity['status'] == 'passed' and identity['artifacts'] == 3, 'Final artifact check failed')
require(digest(ROOT / 'mod-artifact-manifest.json') == identity['manifestSha256'], 'Mod manifest changed')

runs = []
screenshots = 0
for expected in acceptance['runs']:
    directory = ROOT / expected['directory']
    launch = read(directory / 'invocation.json')
    result = read(directory / 'result.json')
    exit_observation = read(directory / 'normal-exit-observation.json')
    messages = lines(directory / 'product.jsonl')
    events = lines(directory / 'ui-events.jsonl')
    command_results = [message for message in messages if message.get('messageType') == 'command_result']
    require([message['status'] for message in command_results] == ['accepted', 'succeeded'], 'Unexpected command result sequence')
    require(command_results[0]['task']['state'] == 'running', 'Initial command did not start a running task')
    finals = [message for message in command_results if message['status'] != 'accepted']
    require(len(finals) == 1 and messages[-1] == finals[0], 'Missing or ambiguous final result')
    final = finals[0]
    require(final['operation'] == 'run_client', 'Unexpected operation')
    require(final['status'] == final['task']['state'] == 'succeeded', 'Client task failed')
    require(final['task']['id'] == expected['taskId'], 'Task ID mismatch')
    require(final['exitCode'] == result['processExitCode'] == 0, 'Nonzero exit code')
    require(launch['candidateSourceCommit'] == candidate['sourceCommit'], 'Run candidate mismatch')
    for key in ('productExeSha256', 'applicationJarSha256'):
        require(launch[key] == candidate[key], f'Run identity mismatch: {key}')
    require(launch['callerRuntimeOptionsCleared'] is True, 'Caller runtime overrides were not cleared')
    require(exit_observation['gameProcessAbsent'] and exit_observation['launcherProcessAbsent'], 'Processes remain alive')
    require(exit_observation['forcedTerminationUsed'] is False, 'Forced exit is not a normal close')
    require(exit_observation['launcherPid'] == launch['launcherPid'], 'Wrong launcher observed at exit')
    require('HEADLESS_TASK_TIMEOUT' not in (directory / 'product.jsonl').read_text(encoding='utf-8'), 'Unexpected timeout')
    game_log = (directory / 'minecraft.log.txt').read_text(encoding='utf-8')
    for marker in ('Stage16Trial joined the game', "ServerLevel[Stage16 Agent Acceptance]", 'All dimensions are saved', 'Stopping!'):
        require(marker in game_log, f'Missing game lifecycle marker: {marker}')
    require('"resonance_token:active": 1b' in game_log, 'No readback of active token data')
    require('Mature wheat harvested: 1' in game_log, 'No harvest ledger readback')
    user = read(directory / 'usercache.json')
    require(len(user) == 1 and user[0]['name'] == acceptance['playerName'] and user[0]['uuid'] == acceptance['playerUuid'], 'Player identity changed')
    previous = None
    for event in events:
        require(event['taskId'] == expected['taskId'], 'UI event belongs to another task')
        timestamp = dt.datetime.fromisoformat(event['recordedAt'])
        require(previous is None or timestamp >= previous, 'UI events out of order')
        previous = timestamp
        screenshot = directory / event['screenshot']
        require(digest(screenshot) == event['sha256'], 'Screenshot bytes changed')
        require(screenshot.read_bytes().startswith(b'\xff\xd8\xff'), 'Screenshot is not JPEG')
        screenshots += 1
    require(len(events) == expected['nativeScreenshotCount'], 'Screenshot count mismatch')
    for entry in read(directory / 'saved-world-state/manifest.json'):
        target = directory / 'saved-world-state' / entry['path']
        require(digest(target) == entry['sha256'], 'Saved-world checkpoint changed')
    start = dt.datetime.fromisoformat(launch['startedAt'])
    finish = dt.datetime.fromisoformat(result['completedAt'])
    require(abs((finish - start).total_seconds() - expected['durationSeconds']) < 0.001, 'Duration mismatch')
    runs.append((start, finish, launch, messages))

require(len(runs) == 2, 'Expected two independent runs')
require(runs[1][0] > runs[0][1], 'Second invocation overlapped first process lifetime')
require(runs[0][2]['launcherPid'] != runs[1][2]['launcherPid'], 'Launcher PID reused')
deadline = runs[0][0] + dt.timedelta(minutes=45)
late_running = [message for message in runs[0][3]
                if message.get('task', {}).get('state') == 'running'
                and any(dt.datetime.fromisoformat(log['timestamp']) > deadline for log in message.get('logs', []))]
require(bool(late_running), 'No running observation beyond the old 45-minute deadline')
require('D3_RESET_RECOUNT_A3_B2' in (ROOT / 'client-run-6/minecraft.log.txt').read_text(encoding='utf-8'), 'No successful pre-exit counter readback')
query = '/execute if block 0 -60 1 tally_stone:tally_stone[count=3] if block 2 -60 2 tally_stone:tally_stone[count=2] run harvestledger'
require(any(event['input'].get('command') == query for event in lines(ROOT / 'client-run-7/ui-events.jsonl')), 'Missing conditional counter/ledger query after restart')
ledger = 'saved-world-state/data/harvest_ledger_totals.dat'
require(digest(ROOT / 'client-run-6' / ledger) == digest(ROOT / 'client-run-7' / ledger), 'Persisted harvest ledger changed during read-only rejoin')
for observation in acceptance['observations'].values():
    for relative in observation['evidence']:
        require((ROOT / relative).is_file(), f'Missing observation evidence: {relative}')
for name in ('windows', 'ubuntu'):
    readiness = read(ROOT / f'install-readiness/{name}.json')
    require(readiness['started'] is False and readiness['configurationChanged'] is False, 'Installation readiness state was promoted')

result = {
    'schemaVersion': '1.0', 'kind': 'stage16-client-evidence-integrity',
    'verifiedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
    'status': 'passed', 'rawEvidenceFiles': len(paths), 'screenshots': screenshots,
    'independentSuccessfulInvocations': len(runs), 'runningBeyond45MinutesObserved': True,
    'samePlayerUuid': acceptance['playerUuid'], 'ledgerBytesPreservedAcrossRestart': True,
    'machineLogAndArtifactChecksPassed': True, 'automatedVisualRecognitionPerformed': False,
    'visualEvidenceSource': 'Native screenshots directly inspected by the operating agent; see acceptance.json and observation-annotations.json.',
    'stage16Complete': False, 'remainingStage': 'C: installed Windows and Ubuntu regression',
}
(ROOT / 'verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result))
