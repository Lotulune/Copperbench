"""Keep a public SDK client task alive while the lead agent validates gameplay.

Task completion and artifact identity are recorded separately from visual verdicts.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import sys
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--product', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--task-authorization', required=True)
    parser.add_argument('--expected-application-sha256', default='c3b822d6deef37ac553f41358ee51c8e21996d378355d9f88b77927130abf499')
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location('client_trial', Path(__file__).with_name('prepare-client-trial.py'))
    trial = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(trial)
    before = trial.check(args.manifest)
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    product = args.product.resolve()
    launcher = product / ('copperbench.exe' if sys.platform == 'win32' else 'copperbench.sh')
    assert trial.digest(launcher) == manifest['productExeSha256']
    app_hash = trial.digest(product / 'lib/copperbench.jar')
    assert app_hash == args.expected_application_sha256
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    def save(name, data):
        (output / (name + '.json')).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    save('identity-before', before)
    shutil.copy2(args.manifest, output / 'client-trial.json')
    sys.path.insert(0, str(product / 'sdk/python'))
    from copperbench import Workspace
    with Workspace.open(manifest['workspaceFile'], launcher=launcher, cwd=product,
                        task_authorization_id=args.task_authorization,
                        startup_timeout=180, request_timeout=120) as workspace:
        save('environment', workspace.query('get_workspace_environment'))
        accepted = workspace.run_client()
        save('accepted', accepted)
        task_id = accepted['task']['id']
        sequence = 0
        previous = None
        while True:
            result = workspace.get_task(task_id, after_log_sequence=sequence)
            save('latest', result)
            with (output / 'logs.jsonl').open('a', encoding='utf-8') as stream:
                for entry in result['data'].get('logs', []):
                    sequence = max(sequence, entry['sequence'])
                    stream.write(json.dumps(entry, ensure_ascii=False) + '\n')
            task = result['data']['task']
            status = (task['state'], task.get('stage', {}).get('key'))
            if status != previous:
                print(json.dumps({'taskId': task_id, 'state': status[0], 'stage': status[1]}), flush=True)
                previous = status
            if task['state'] not in ('queued', 'running'):
                save('final', result)
                break
            time.sleep(2)
    save('identity-after', trial.check(args.manifest))
    client_log = Path(manifest['workspaceFile']).parent / 'run/logs/latest.log'
    if client_log.is_file():
        shutil.copy2(client_log, output / 'client-final.log')
    with Workspace.open(manifest['workspaceFile'], launcher=launcher, cwd=product,
                        startup_timeout=180, request_timeout=120) as workspace:
        recovered = workspace.get_task(task_id)
        save('reopened-task', recovered)
        assert recovered['data']['task']['state'] == task['state']
    assert task['state'] == 'succeeded', task
    print('CLIENT_TASK_SUCCEEDED; visual verdicts are recorded by Minecraft Control separately', flush=True)


if __name__ == '__main__':
    main()
