"""Replay an existing Scene A input manifest through the installed public SDK.

This verifies a transferred workspace; it does not claim fresh creation or a
Blockbench UI workflow on the destination platform.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import time
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('product', 'launcher', 'workspace', 'output', 'transfer-proof'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--track', required=True)
    parser.add_argument('--expected-application-sha256', default='c3b822d6deef37ac553f41358ee51c8e21996d378355d9f88b77927130abf499')
    parser.add_argument('--operations', default='generate,build,gametest,export')
    args = parser.parse_args()
    output = args.output.resolve(); assert not output.exists(); output.mkdir(parents=True)
    root = args.workspace.resolve().parent
    transfer = json.loads(args.transfer_proof.read_text(encoding='utf-8'))
    manifest = next(item['manifest'] for item in transfer['tracks'] if item['track'] == args.track)
    operations = args.operations.split(',')
    assert set(operations) <= {'generate', 'build', 'gametest', 'export'}
    sys.path.insert(0, str(args.product.resolve() / 'sdk/python'))
    from copperbench import Workspace

    def sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def save(name, value):
        (output / (name + '.json')).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

    def wait(ws, name, accepted):
        save(name + '-accepted', accepted)
        task_id = accepted['task']['id']; sequence = 0; previous = None
        deadline = time.monotonic() + 2700
        while time.monotonic() < deadline:
            result = ws.get_task(task_id, after_log_sequence=sequence)
            save(name + '-latest', result)
            with (output / (name + '-logs.jsonl')).open('a', encoding='utf-8') as stream:
                for entry in result['data'].get('logs', []):
                    sequence = max(sequence, entry['sequence'])
                    stream.write(json.dumps(entry, ensure_ascii=False) + '\n')
            task = result['data']['task']; status = (task['state'], task.get('stage', {}).get('key'))
            if status != previous:
                print(json.dumps({'track': args.track, 'phase': name, 'taskId': task_id, 'status': status}), flush=True)
                previous = status
            if task['state'] not in ('queued', 'running'):
                save(name + '-final', result)
                assert task['state'] == 'succeeded', result
                return result
            time.sleep(1)
        raise TimeoutError('Task still running at the bounded acceptance deadline')

    proof = {'track': args.track, 'scope': 'Installed product replay of transferred Scene A inputs',
             'applicationSha256': sha(args.product / 'lib/copperbench.jar'), 'checks': []}
    try:
        changed = [item['path'] for item in manifest['files']
                   if not (root / item['path']).is_file() or sha(root / item['path']) != item['sha256']]
        proof['changedInputsBeforeOpen'] = changed
        if operations[0] == 'generate':
            assert not changed, changed
        assert proof['applicationSha256'] == args.expected_application_sha256
        definition = root / 'elements/acceptance_forge.mod.json'
        original_definition = sha(definition)
        with Workspace.open(args.workspace, launcher=args.launcher, cwd=args.product, startup_timeout=180, request_timeout=120) as ws:
            save('environment', ws.query('get_workspace_environment'))
            elements = list(ws.list_mod_elements()); save('elements', elements)
            assert len(elements) == 2 and all(item['state'] == 'valid' for item in elements), elements
            forge = next(item for item in elements if item['name'] == 'acceptance_forge')
            save('editor', ws.query('get_mod_element_editor', elementId=forge['id']))
            values = json.loads(definition.read_text(encoding='utf-8'))['definition']
            assert values['hardness'] == 5 and values['resistance'] == 6 and values['rotationMode'] == 1
            assert values['hasInventory'] and values['inventorySize'] == 3 and values['inventoryStackSize'] == 64
            assert sha(definition) == original_definition
            proof['checks'].append('transferred_definition_and_public_editor')
            verification = None
            for operation in operations:
                if operation in ('generate', 'build'):
                    wait(ws, operation, getattr(ws, operation)())
                elif operation == 'gametest':
                    result = wait(ws, operation, ws.run_game_tests())
                    verification = result['data']['task']['verification']
                    assert verification['status'] == 'passed' and verification['acceptanceExecuted'] == 6
                    assert verification['sourceCurrentAtCompletion']
                    proof['verification'] = verification
                    destination = output / 'verified'; destination.mkdir()
                    for key, filename in [('artifactPath', 'tested.jar'), ('reportPath', 'gametest.xml'), ('verificationPath', 'verification.json')]:
                        shutil.copy2(verification[key], destination / filename)
                    shutil.copy2(verification['sourceSnapshot']['manifestPath'], destination / 'source-manifest.json')
                    assert sha(destination / 'tested.jar') == verification['artifactSha256']
                    assert sha(destination / 'gametest.xml') == verification['reportSha256']
                    with zipfile.ZipFile(destination / 'tested.jar') as jar:
                        wrapper = json.loads(jar.read('assets/stage17_structured/models/block/acceptance_forge.json'))
                        assert wrapper['parent'] == 'stage17_structured:custom/acceptance_forge'
                        model = json.loads(jar.read('assets/stage17_structured/models/custom/acceptance_forge.json'))
                        assert model['elements'][0]['to'] == [16, 12, 16]
                        texture = 'assets/stage17_structured/textures/block/acceptance_atlas.png'
                        expected = next(item['sha256'] for item in manifest['files'] if item['path'] == 'src/main/resources/' + texture)
                        assert hashlib.sha256(jar.read(texture)).hexdigest() == expected
                    proof['checks'].append('six_behavior_tests_and_exact_jar_resources')
                else:
                    assert verification is not None, 'Run GameTest before exporting'
                    result = wait(ws, operation, ws.command('export_workspace', scope='workspace', verifiedTaskId=verification['taskId']))
                    receipt = result['data']['task']['verifiedExport']; save('export-receipt', receipt)
                    assert receipt['status'] == 'passed_current_input' and receipt['artifactSha256'] == verification['artifactSha256']
                    shutil.copytree(receipt['exportDirectory'], output / 'delivery')
                    proof['checks'].append('current_input_verified_export')
            proof['revision'] = ws.revision
        with Workspace.open(args.workspace, launcher=args.launcher, cwd=args.product, startup_timeout=180, request_timeout=120) as ws:
            assert ws.revision == proof['revision'] and sha(definition) == original_definition
            if verification:
                recovered = ws.get_task(verification['taskId']); save('reopened-task', recovered)
                assert recovered['data']['task']['verification']['artifactSha256'] == verification['artifactSha256']
            proof['checks'].append('reopened_definition_and_verification')
        proof['status'] = 'passed'
    except Exception as error:
        proof.update(status='failed', error=str(error), details=getattr(error, 'details', None))
        raise
    finally:
        save('proof', proof)
        print(json.dumps({'track': args.track, 'status': proof.get('status'), 'checks': proof['checks']}), flush=True)


if __name__ == '__main__':
    main()
