"""Verify Procedure context, return diagnostics and trigger discovery through the shipped SDK.

Uses a new copy of a seed workspace. Optional generation/build is explicit;
this probe does not install the product or launch Minecraft.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('product', 'launcher', 'workspace', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--build', action='store_true')
    parser.add_argument('--seed', type=Path, help='Existing managed workspace to copy; required for generation/build.')
    args = parser.parse_args()
    product, target, output = args.product.resolve(), args.workspace.resolve(), args.output.resolve()
    if target.exists() or output.exists() or target == product or product in target.parents or target in product.parents:
        parser.error('Use new workspace and evidence directories separate from the product.')
    if args.build and not args.seed:
        parser.error('--build requires a managed --seed; the shipped Wayfinder source example is not a blank generated workspace.')
    seed = args.seed.resolve() if args.seed else product / 'examples/agent-native/wayfinder-bell'
    documents = [path for path in seed.glob('*.mcreator') if path.is_file()]
    if len(documents) != 1:
        parser.error('The seed must contain exactly one .mcreator document.')
    seed_inputs = {str(path.relative_to(seed)): hashlib.sha256(path.read_bytes()).hexdigest()
                   for pattern in ('*.mcreator', 'elements/*.mod.json', 'src/main/**/*')
                   for path in seed.glob(pattern) if path.is_file()}
    shutil.copytree(seed, target, ignore=shutil.ignore_patterns('.git', '.gradle', 'build', 'run', '.copperbench'))
    output.mkdir(parents=True)
    sys.path.insert(0, str(product / 'sdk/python'))
    from copperbench import Workspace, NativeApiError

    def sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def save(name, value):
        (output / (name + '.json')).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

    def opened():
        return Workspace.open(target / documents[0].name, launcher=args.launcher.resolve(), cwd=product,
                              startup_timeout=180, request_timeout=120)

    def body(value, trigger='no_ext_trigger'):
        return '<xml><block type="event_trigger"><field name="trigger">' + trigger + '</field><next>' \
            '<block type="return_number"><value name="VALUE">' + value + '</value></block></next></block></xml>'

    def trigger_edit(value):
        return [{'operation': 'set_trigger', 'trigger': value}]

    def assert_diagnostic(result, code):
        assert any(item['code'] == code for item in result.get('diagnostics', [])), result

    coordinate_id = str(uuid.uuid4())
    proof = {'scope': 'Public SDK Procedure context, return diagnostics, catalog, generation precheck and reopen',
             'applicationSha256': sha(product / 'lib/copperbench.jar'), 'product': str(product), 'checks': [],
             'seed': str(seed), 'seedInputs': seed_inputs}
    try:
        with opened() as ws:
            save('environment', ws.query('get_workspace_environment'))
            baseline = ws.query('get_workspace_health')['data']; save('baseline-health', baseline)
            assert baseline['diagnostics']['error'] == 0 and baseline['elements']['invalid'] == 0
            created = ws.create_mod_element(elementType='procedure', name='context_reader',
                initialValues={'procedurexml': body('<block type="coord_x" id="' + coordinate_id + '"/>')})
            element_id = created['data']['element']['id']; save('created', created)
            definition = target / 'elements/context_reader.mod.json'
            before = sha(definition); revision = ws.revision
            editor = ws.query('get_procedure_editor', elementId=element_id); save('editor', editor)
            catalog = editor['data']['triggerCatalog']
            ids = [item['id'] for item in catalog]
            assert len(ids) == len(set(ids)) and {'player_ticks', 'mod_serverload'} <= set(ids)
            assert not {'on_block_right_clicked', 'on_item_right_clicked', 'on_entity_tick_update'} & set(ids)
            player = next(item for item in catalog if item['id'] == 'player_ticks')
            assert {'name': 'x', 'type': 'number'} in player['dependencies']
            preview = ws.query('preview_procedure_change', elementId=element_id, edits=trigger_edit('mod_serverload'))
            save('invalid-context-preview', preview)
            assert preview['data']['canSaveDraft'] and not preview['data']['canGenerate']
            assert_diagnostic(preview['data'], 'PROCEDURE_CONTEXT_MISSING')
            assert coordinate_id in next(item['path'] for item in preview['data']['diagnostics'] if item['code'] == 'PROCEDURE_CONTEXT_MISSING')
            assert sha(definition) == before and ws.revision == revision
            invalid = ws.update_procedure(elementId=element_id, edits=trigger_edit('mod_serverload'))
            assert invalid['data']['element']['state'] == 'invalid'; save('invalid-context-saved', invalid)
            proof['checks'].extend(['active_trigger_catalog', 'preview_read_only', 'context_node_diagnostic', 'invalid_draft_saved'])
            invalid_revision = ws.revision

        with opened() as ws:
            assert ws.revision == invalid_revision
            health = ws.query('get_workspace_health'); save('invalid-reopened-health', health)
            assert health['data']['elements']['invalid'] == 1
            assert health['data']['diagnostics']['error'] == 1
            assert health['data']['diagnostics']['total'] == baseline['diagnostics']['total'] + 1
            before = sha(definition)
            try:
                accepted = ws.generate(); save('invalid-generation-accepted', accepted)
                rejected = ws.wait_task(accepted['task']['id'], timeout=180, poll_interval=0.5)
                assert rejected['data']['task']['state'] == 'failed', rejected
            except NativeApiError as error:
                rejected = error.details
            save('invalid-generation-result', rejected)
            assert 'PROCEDURE_CONTEXT_MISSING' in json.dumps(rejected), rejected
            assert sha(definition) == before and ws.revision == invalid_revision
            proof['checks'].extend(['invalid_state_reopened', 'exact_global_diagnostic_counts', 'generation_rejected_without_input_change'])

            bad_return = ws.create_mod_element(elementType='procedure', name='return_reader', initialValues={
                'procedurexml': body('<block type="logic_boolean"><field name="BOOL">TRUE</field></block>')})
            return_id = bad_return['data']['element']['id']; save('invalid-return-saved', bad_return)
            assert bad_return['data']['element']['state'] == 'invalid'
            editor = ws.query('get_procedure_editor', elementId=return_id); save('invalid-return-editor', editor)
            assert_diagnostic(editor, 'PROCEDURE_RETURN_TYPE_MISMATCH')
            ws.update_mod_element(elementId=return_id, changes=[{'path': '/procedurexml',
                'value': body('<block type="math_number"><field name="NUM">17</field></block>')}])
            repaired = ws.update_procedure(elementId=element_id, edits=trigger_edit('player_ticks'))
            assert repaired['data']['element']['state'] == 'valid'
            health = ws.query('get_workspace_health'); save('repaired-health', health)
            assert health['data']['diagnostics']['error'] == 0 and health['data']['elements']['invalid'] == 0
            proof['checks'].extend(['return_type_diagnostic', 'repairs_clear_current_errors'])
            if args.build:
                for name, command in [('generate', ws.generate), ('build', ws.build)]:
                    accepted = command(); save(name + '-accepted', accepted)
                    result = ws.wait_task(accepted['task']['id'], timeout=2400, poll_interval=1)
                    save(name + '-final', result)
                    assert result['data']['task']['state'] == 'succeeded', result
                proof['checks'].append('repaired_generate_and_build')
            proof['revision'] = ws.revision
            stable_inputs = {str(path.relative_to(target)): sha(path) for path in target.glob('elements/*.mod.json')}

        with opened() as ws:
            assert ws.revision == proof['revision']
            editor = ws.query('get_procedure_editor', elementId=element_id); save('reopened-editor', editor)
            assert editor['data']['ir']['trigger'] == 'player_ticks' and not editor['diagnostics']
            assert stable_inputs == {str(path.relative_to(target)): sha(path) for path in target.glob('elements/*.mod.json')}
            proof['checks'].append('repaired_reopen_preserves_definitions')
        proof['status'] = 'passed'
    except Exception as error:
        proof.update(status='failed', error=str(error), details=getattr(error, 'details', None))
        raise
    finally:
        proof['seedInputsUnchanged'] = seed_inputs == {str(path.relative_to(seed)): sha(path)
            for pattern in ('*.mcreator', 'elements/*.mod.json', 'src/main/**/*')
            for path in seed.glob(pattern) if path.is_file()}
        save('proof', proof)
        print(json.dumps({'status': proof.get('status'), 'checks': proof['checks'], 'output': str(output)}), flush=True)


if __name__ == '__main__':
    main()
