"""Scene A through the shipped SDK; writes only test sources and modeling input fixtures directly.

Requires an already-created empty product workspace and an authorized task root.
No implementation Java is patched. This is not a real Blockbench UI trial.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
import sys
import uuid
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('product', 'launcher', 'workspace', 'output', 'texture', 'tests'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--authorization', required=True)
    parser.add_argument('--expected-application-sha256')
    parser.add_argument('--verify-block-visual-defaults', action='store_true')
    parser.add_argument('--resume-from', type=Path, help='Resume the existing modeling task after a rejected fixture, retaining the failed evidence.')
    args = parser.parse_args()
    product, root, output = args.product.resolve(), args.workspace.resolve().parent, args.output.resolve()
    assert not output.exists(), 'Use a new evidence directory'
    output.mkdir(parents=True)
    sys.path.insert(0, str(product / 'sdk/python'))
    from copperbench import Workspace, NativeApiError

    def sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def save(name, value):
        (output / (name + '.json')).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

    def opened():
        return Workspace.open(args.workspace, launcher=args.launcher, cwd=product,
            task_authorization_id=args.authorization, startup_timeout=180, request_timeout=120)

    def inputs():
        return {str(path.relative_to(root)): sha(path) for pattern in ('elements/*.mod.json', 'src/main/**/*')
                for path in root.glob(pattern) if path.is_file()}

    def completed(ws, name, accepted):
        save(name + '-accepted', accepted)
        print(json.dumps({'phase': name, 'taskId': accepted['task']['id']}), flush=True)
        result = ws.wait_task(accepted['task']['id'], timeout=2700, poll_interval=1)
        save(name + '-final', result)
        assert result['data']['task']['state'] == 'succeeded', result
        return result

    proof = {'scope': 'Scene A SDK configuration, model import/binding, reopen and isolated packaged JAR behavior',
             'productJarSha256': sha(product / 'lib/copperbench.jar'), 'workspace': str(args.workspace),
             'textureFixtureSha256': sha(args.texture), 'implementationSourcePatched': False, 'checks': []}
    try:
        if args.expected_application_sha256:
            assert proof['productJarSha256'] == args.expected_application_sha256, 'Unexpected product candidate'
        with opened() as ws:
            if args.resume_from:
                prior = args.resume_from.resolve()
                previous = json.loads((prior / 'proof.json').read_text(encoding='utf-8'))
                assert previous['workspace'] == str(args.workspace) and previous['productJarSha256'] == proof['productJarSha256']
                assert previous['status'] == 'failed' and 'BBMODEL_FACE_TEXTURE_UNRESOLVED' in previous['error']
                proof['checks'] = previous['checks']; proof['resumedFrom'] = str(prior)
                started = json.loads((prior / 'model-begun.json').read_text(encoding='utf-8'))
                task_id = started['data']['taskId']; element_id = started['data']['elementContext']['elementId']
                definition = root / 'elements/acceptance_forge.mod.json'
                stored = json.loads(definition.read_text(encoding='utf-8'))['definition']
                assert stored['hardness'] == 5 and stored['inventorySize'] == 3
                assert ws.query('get_blockbench_task', taskId=task_id)['data']['state'] == 'editing'
            else:
                assert list(ws.list_mod_elements()) == [], 'Scene A must start without elements'
                save('field-contract', ws.field_contract('block'))
                values = {'displayName': 'Structured Acceptance Forge', 'hardness': 3, 'resistance': 6,
                    'rotationMode': 1, 'hasInventory': True, 'inventorySize': 3, 'inventoryStackSize': 64,
                    'inventoryDropWhenDestroyed': True, 'openGUIOnRightClick': False, 'maxStackSize': 16,
                    'rarity': 'RARE', 'boundingBoxes': [{'mx': 0, 'my': 0, 'mz': 0, 'Mx': 16, 'My': 12, 'Mz': 16, 'subtract': False}]}
                created = ws.create_mod_element(elementType='block', name='acceptance_forge', initialValues=values)
                save('created-forge', created); element_id = created['data']['element']['id']
                save('created-default', ws.create_mod_element(elementType='block', name='default_cube',
                    initialValues={'displayName': 'Default Collision Cube', 'hardness': 1, 'resistance': 1}))
                definition = root / 'elements/acceptance_forge.mod.json'
                stored = json.loads(definition.read_text(encoding='utf-8'))['definition']
                assert stored['hardness'] == 3 and stored['inventorySize'] == 3 and stored['rotationMode'] == 1
                if args.verify_block_visual_defaults:
                    default_values = json.loads((root / 'elements/default_cube.mod.json').read_text(encoding='utf-8'))['definition']
                    assert default_values['renderType'] == 10 and default_values['texture'] == 'minecraft:stone', default_values
                    proof['checks'].append('default_block_definition_visuals')
                before, revision = inputs(), ws.revision
                try:
                    ws.update_mod_element(elementId=element_id, changes=[{'path': '/hardness', 'value': 'not a number'}])
                    raise AssertionError('Malformed numeric field was committed')
                except NativeApiError as error:
                    save('rejected-field', error.details)
                    assert any(item['code'] == 'FIELD_TYPE_INVALID' and item['path'].endswith('/hardness')
                               for item in error.details['diagnostics']), error.details
                assert before == inputs() and revision == ws.revision
                change = [{'path': '/hardness', 'value': 5}]
                save('hardness-preview', ws.query('preview_mod_element_change', elementId=element_id, changes=change))
                save('hardness-updated', ws.update_mod_element(elementId=element_id, changes=change))
                proof['checks'].extend(['empty_workspace', 'create_definition', 'invalid_field_atomic_rejection', 'hardness_update'])

                task_id = str(uuid.uuid4())
                started = ws.command('begin_blockbench_task', taskId=task_id, elementId=element_id)
                save('model-begun', started)
            task = ws.query('get_blockbench_task', taskId=task_id)['data']
            edit = Path(task['editPath']); context = task['elementContext']
            texture_resource = 'stage17_structured:block/acceptance_atlas'
            faces = {direction: {'uv': [0, 0, 16, 16], 'texture': '#all'}
                     for direction in ('north', 'south', 'east', 'west', 'up', 'down')}
            geometry = {'from': [0, 0, 0], 'to': [16, 12, 16], 'faces': faces}
            source_geometry = {**geometry, 'faces': {direction: {**face, 'texture': 0} for direction, face in faces.items()}}
            edit.write_text(json.dumps({'meta': {'model_format': 'java_block'}, 'elements': [source_geometry],
                'textures': [{'uuid': str(uuid.uuid4()), 'name': 'acceptance_atlas.png',
                    'source': 'data:image/png;base64,' + base64.b64encode(args.texture.read_bytes()).decode('ascii')}]}), encoding='utf-8')
            (edit.parent / 'acceptance_model.json').write_text(json.dumps({'textures': {'all': texture_resource}, 'elements': [geometry]}), encoding='utf-8')
            shutil.copy2(args.texture, edit.parent / 'acceptance_atlas.png')
            refreshed = ws.query('get_blockbench_task', taskId=task_id)['data']
            save('model-finished', ws.command('finish_blockbench_task', taskId=task_id, savedSha256=refreshed['editSha256']))
            preview = ws.query('preview_blockbench_import', taskId=task_id, elementId=element_id)
            save('model-preview', preview)
            save('model-imported', ws.command('import_blockbench_task', taskId=task_id, planToken=preview['data']['planToken'], confirmReplace=False))
            save('model-bound', ws.command('bind_blockbench_model', taskId=task_id, elementId=element_id, modelResource=context['modelResource']))
            proof.update(elementId=element_id, modelingTaskId=task_id, modelResource=context['modelResource'])
            proof['checks'].append('public_model_import_and_binding')
            completed(ws, 'prepare-tests', ws.prepare_game_tests())
            test_file = root / 'src/gametest/java/copperbench/acceptance/AcceptanceTests.java'
            assert test_file.is_file()
            test_file.write_bytes(args.tests.read_bytes())
            config_file = root / 'copperbench-tests.json'
            config = json.loads(config_file.read_text(encoding='utf-8'))
            config['minimumTests'] = 6
            config_file.write_text(json.dumps(config, indent=2), encoding='utf-8')
            proof['testSourceSha256'] = sha(test_file)
            before_reopen, revision = inputs(), ws.revision

        with opened() as ws:
            assert ws.revision == revision and inputs() == before_reopen
            stored = json.loads(definition.read_text(encoding='utf-8'))['definition']
            assert stored['hardness'] == 5 and stored['rotationMode'] == 1
            assert stored['hasInventory'] and stored['inventorySize'] == 3 and stored['inventoryStackSize'] == 64
            save('reopened-editor', ws.query('get_mod_element_editor', elementId=element_id))
            task = ws.query('get_blockbench_task', taskId=task_id)['data']; save('reopened-modeling-task', task)
            assert task['binding']['state'] == 'bound'
            proof['checks'].append('reopened_configuration_and_binding')
            completed(ws, 'generate', ws.generate())
            completed(ws, 'build', ws.build())
            result = completed(ws, 'gametest', ws.run_game_tests())
            verification = result['data']['task']['verification']
            assert verification['status'] == 'passed' and verification['acceptanceExecuted'] == 6, verification
            assert verification['sourceCurrentAtCompletion'], verification
            proof['verification'] = verification
            artifact = Path(verification['artifactPath'])
            assert sha(artifact) == verification['artifactSha256']
            with zipfile.ZipFile(artifact) as jar:
                wrapper = json.loads(jar.read('assets/stage17_structured/models/block/acceptance_forge.json'))
                assert wrapper['parent'] == context['modelResource']
                model = json.loads(jar.read('assets/stage17_structured/models/custom/acceptance_forge.json'))
                assert model['textures']['all'] == texture_resource and model['elements'][0]['to'] == [16, 12, 16]
                assert hashlib.sha256(jar.read('assets/stage17_structured/textures/block/acceptance_atlas.png')).hexdigest() == proof['textureFixtureSha256']
                if args.verify_block_visual_defaults:
                    default_model = json.loads(jar.read('assets/stage17_structured/models/block/default_cube.json'))
                    assert default_model['parent'] == 'block/cube', default_model
                    for face in ('down', 'up', 'north', 'east', 'south', 'west', 'particle'):
                        assert default_model['textures'][face] == 'minecraft:block/stone', default_model
                    language = json.loads(jar.read('assets/stage17_structured/lang/en_us.json'))
                    assert language['block.stage17_structured.acceptance_forge'] == 'Structured Acceptance Forge', language
                    assert language['block.stage17_structured.default_cube'] == 'Default Collision Cube', language
                    proof['checks'].append('verified_jar_default_stone_model_and_block_names')
            proof['checks'].extend(['generated_and_built', 'six_packaged_behavior_tests', 'exact_verified_jar_model_and_texture'])
            proof['revision'] = ws.revision
        proof['status'] = 'passed'
    except Exception as error:
        proof.update(status='failed', error=str(error), details=getattr(error, 'details', None))
        raise
    finally:
        save('proof', proof)
        print(json.dumps({'status': proof.get('status'), 'checks': proof['checks'], 'output': str(output)}), flush=True)


if __name__ == '__main__':
    main()
