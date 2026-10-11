"""Additional checks against a frozen installed candidate after the baseline replay."""
import argparse, hashlib, json, shutil, subprocess, sys, time, traceback
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--product', type=Path, required=True)
p.add_argument('--baseline', type=Path, required=True)
p.add_argument('--manifest', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--task-authorization')
a = p.parse_args()
manifest = json.loads(a.manifest.read_text(encoding='utf-8-sig'))
baseline = json.loads((a.baseline / 'result.json').read_text(encoding='utf-8-sig'))
assert baseline['status'] == 'passed'
product = a.product.resolve()
out = a.output.resolve()
out.mkdir()
sys.path.insert(0, str(product / 'sdk/python'))
from copperbench_native import Workspace
launcher = product / ('copperbench.exe' if sys.platform == 'win32' else 'copperbench.sh')
root = Path(baseline['workspaceFile']).parent
expected_app = manifest['members']['lib/copperbench.jar']['sha256']
def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()
assert sha(product / 'lib/copperbench.jar') == expected_app
assert sha(product / 'scripts/verify-delivery-bundle.py') == manifest['members']['scripts/verify-delivery-bundle.py']['sha256']
record = {'status': 'running', 'candidateCommit': manifest['sourceCommit'], 'applicationSha256': expected_app,
          'checks': [], 'tasks': [], 'playerBehaviorVerified': False, 'fixtureInjectionUsed': False}
started = time.monotonic()
def save(name, value):
    (out / (name + '.json')).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
def terminal(w, label, command, expected='succeeded', code=None):
    assert command['status'] == 'accepted', command
    result = w.wait_task(command['task']['id'], timeout=1500, poll_interval=1)
    save(label, result)
    task = result['data']['task']
    record['tasks'].append({'label': label, 'id': task['id'], 'state': task['state']})
    assert task['state'] == expected, result
    if code:
        assert any(d['code'] == code for d in result['data']['diagnostics']), result
    return task
def reject(w, label, task, code, historical=False):
    t = terminal(w, label, w.export_verified_artifact(task['id'], allow_historical=historical), 'failed', code)
    assert 'verifiedExport' not in t
    record['checks'].append(label)
def verify_bundle(label, source, expected_status):
    destination = out / label
    shutil.copytree(source, destination)
    completed = subprocess.run([sys.executable, '-I', str(product / 'scripts/verify-delivery-bundle.py'), str(destination)],
                               cwd=out, text=True, encoding='utf-8', capture_output=True, check=False)
    save(label + '-offline', {'exitCode': completed.returncode, 'stdout': completed.stdout, 'stderr': completed.stderr})
    assert completed.returncode == 0, completed.stdout + completed.stderr
    identity = json.loads((destination / 'application-identity.json').read_text(encoding='utf-8'))
    receipt = json.loads((destination / 'verification.json').read_text(encoding='utf-8'))
    assert identity['source'] == 'acceptance_record' and identity['application']['applicationSha256'] == expected_app
    assert receipt['status'] == expected_status
    record['checks'].append(label + '-offline-and-application-identity')
    return destination
try:
    accepted = json.loads((a.baseline / 'packaged-acceptance.json').read_text(encoding='utf-8'))['data']['task']
    with Workspace.open(baseline['workspaceFile'], launcher=launcher, cwd=product,
                        task_authorization_id=a.task_authorization, startup_timeout=180, request_timeout=120) as w:
        assert 'get_task_verification' in w.operations
        env = w.query('get_workspace_environment')
        save('environment', env)
        assert env['data']['application']['applicationSha256'] == expected_app
        before = w.get_task(accepted['id'])['data']['task']
        revision = w.revision
        result = w.get_task_verification(accepted['id'])
        save('current-result', result)
        assert result['data']['resultVersion'] == '1' and result['data']['subreason'] == 'passed_current_input', result
        assert before == w.get_task(accepted['id'])['data']['task'] and w.revision == revision
        record['checks'].append('read-only-versioned-result-preserves-legacy-task')
        current = terminal(w, 'current-export', w.export_verified_artifact(accepted['id']))['verifiedExport']
        bundle = verify_bundle('current-delivery', current['exportDirectory'], 'passed_current_input')
        config_path = root / 'copperbench-tests.json'
        original_config = config_path.read_bytes()
        config = json.loads(original_config)
        config['minimumTests'] = 6
        config_path.write_text(json.dumps(config), encoding='utf-8')
        try:
            command = w.run_game_tests()
            running = w.get_task_verification(command['task']['id'])
            save('running-result', running)
            if running['data']['taskState'] in ('running', 'queued'):
                assert running['data']['subreason'] == 'running' and running['data']['coverage']['acceptanceExecuted'] is None
            failed = terminal(w, 'five-of-six', command, 'failed', 'GAMETEST_NO_TESTS')
            result = w.get_task_verification(failed['id'])
            save('five-of-six-result', result)
            assert result['data']['subreason'] == 'insufficient_business_tests', result
            assert result['data']['coverage'] == {'minimumTests': 6, 'acceptanceExecuted': 5, 'shortfall': 1}, result
            assert result['data']['message']['args']['shortfall'] == 1
            reject(w, 'five-of-six-export-refused', failed, 'VERIFIED_ARTIFACT_UNAVAILABLE')
            record['checks'].append('real-packaged-five-of-six-result')
        finally:
            config_path.write_bytes(original_config)
        for field, label in [('artifactPath', 'jar-tamper'), ('reportPath', 'xml-tamper')]:
            path = Path(accepted['verification'][field])
            original = path.read_bytes()
            try:
                path.write_bytes(original + b'\nControlled installed acceptance corruption\n')
                reject(w, label + '-export-refused', accepted, 'VERIFIED_EVIDENCE_CHANGED', True)
            finally:
                path.write_bytes(original)
        source = root / 'src/main/java/net/mcreator/m1_delivery/recovery_probe.java'
        original_source = source.read_bytes()
        try:
            source.write_bytes(original_source + b'\n// Controlled historical-input acceptance check.\n')
            historical_result = w.get_task_verification(accepted['id'])
            save('historical-result', historical_result)
            assert historical_result['data']['subreason'] == 'passed_historical_input'
            reject(w, 'stale-current-export-refused', accepted, 'VERIFICATION_INPUT_CHANGED')
            historical = terminal(w, 'historical-export', w.export_verified_artifact(accepted['id'], allow_historical=True))['verifiedExport']
            verify_bundle('historical-delivery', historical['exportDirectory'], 'passed_historical_input')
        finally:
            source.write_bytes(original_source)
    for kind in ('jar', 'xml', 'count', 'path', 'manifest'):
        modified = out / ('tampered-' + kind)
        shutil.copytree(bundle, modified)
        if kind in ('jar', 'xml'):
            file = modified / ('verified-mod.jar' if kind == 'jar' else 'gametest-results.xml')
            file.write_bytes(file.read_bytes() + b'changed')
        else:
            file = modified / 'manifest.json'
            value = json.loads(file.read_text(encoding='utf-8'))
            if kind == 'count':
                value['minimumTests'] = 6
            elif kind == 'path':
                value['files'][0]['path'] = '../verified-mod.jar'
            else:
                value['sourceSha256'] = '0' * 64
            file.write_text(json.dumps(value), encoding='utf-8')
        result = subprocess.run([sys.executable, '-I', str(product / 'scripts/verify-delivery-bundle.py'), str(modified)],
                                cwd=out, text=True, encoding='utf-8', capture_output=True, check=False)
        save('tampered-' + kind + '-offline', {'exitCode': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr})
        assert result.returncode == 1, result
        record['checks'].append('offline-' + kind + '-rejected')
    record['status'] = 'passed'
except BaseException as error:
    record.update(status='failed', error=str(error), errorType=type(error).__name__, traceback=traceback.format_exc())
finally:
    record['elapsedSeconds'] = time.monotonic() - started
    save('result', record)
    print(json.dumps(record, ensure_ascii=False))
raise SystemExit(0 if record['status'] == 'passed' else 1)
