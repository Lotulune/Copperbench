"""Real Native preview-contract verification; this does not claim a 26.3 build or gameplay pass."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import traceback

from copperbench_native import Workspace, NativeApiError

config = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
evidence = Path(config['evidence'])
started = time.monotonic()
record = {'status': 'running', 'scope': 'real_native_preview_negotiation_on_existing_m1', 'sessions': []}
receipts = []
root = Path(config['workspace']).parent
destination = root.parent / 'must-not-be-created'

def inputs():
    files = [Path(config['workspace']), *sorted((root / 'src').rglob('*'))]
    return {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in files if path.is_file()}

def open_workspace():
    workspace = Workspace.open(config['workspace'], launcher=config['launcher'], cwd=config['cwd'],
                               startup_timeout=180, request_timeout=120)
    record['sessions'].append({'pid': workspace._process.pid, 'workspaceId': workspace.workspace_id})
    return workspace

def denied(code, action):
    try:
        action()
    except NativeApiError as error:
        assert error.code == code, (error.code, error.details)
        if error.details.get('contract') == 'preview_targets': receipts.append(error.details)
        return error.details
    raise AssertionError('Operation unexpectedly succeeded: ' + code)

try:
    with open_workspace() as workspace:
        before = inputs()
        catalog = workspace.preview('catalog'); receipts.append(catalog)
        assert len(catalog['targets']) == 10
        preview = next(target for target in catalog['targets'] if target['generatorId'] == 'fabric-26.3')
        assert preview['capabilities']['supportStage'] == 'registered'
        assert preview['capabilities']['environmentQualified']
        create_ready = next(operation['ready'] for operation in preview['capabilities']['operations']
                            if operation['operation'] == 'create_workspace')
        selected = workspace.preview('opt_in', generator_id='fabric-26.3', workspace_folder_path=str(destination))
        receipts.append(selected)
        assert next(target for target in selected['targets'] if target['generatorId'] == 'fabric-26.3')['selected']
        form = dict(generatorId='fabric-26.3', workspaceFolderPath=str(destination), modName='Preview Probe',
                    modId='preview_probe', packageName='dev.preview.probe', userApproved=True)
        legacy = denied('PREVIEW_NEGOTIATION_REQUIRED', lambda: workspace.command('create_workspace', **form))
        (evidence / 'legacy-rejection.json').write_text(json.dumps(legacy, indent=2), encoding='utf-8')
        denied('USER_APPROVAL_REQUIRED' if create_ready else 'PREVIEW_NATIVE_OPERATION_UNVERIFIED', lambda: workspace.preview('command', generator_id='fabric-26.3',
                command='create_workspace', payload=form))
        denied('PREVIEW_OPT_IN_REQUIRED', lambda: workspace.preview('command', generator_id='neoforge-26.3',
                command='create_workspace', payload={**form, 'generatorId': 'neoforge-26.3'}))
        revoked = workspace.preview('revoke', generator_id='fabric-26.3', workspace_folder_path=str(destination))
        receipts.append(revoked)
        denied('PREVIEW_OPT_IN_REQUIRED', lambda: workspace.preview('command', generator_id='fabric-26.3',
                command='create_workspace', payload=form))
        denied('PREVIEW_VERSION_UNSUPPORTED', lambda: workspace.preview('catalog', supported_versions=('99',)))
        assert not destination.exists()
        assert before == inputs(), 'Read-only preview flow changed workspace inputs'
    with open_workspace() as reopened:
        catalog = reopened.preview('catalog'); receipts.append(catalog)
        assert not any(target['selected'] for target in catalog['targets'])
        assert before == inputs(), 'Reconnect changed workspace inputs'
    assert record['sessions'][0]['pid'] != record['sessions'][1]['pid']
    assert record['sessions'][0]['workspaceId'] == record['sessions'][1]['workspaceId']
    (evidence / 'responses.json').write_text(json.dumps(receipts, ensure_ascii=False, indent=2), encoding='utf-8')
    check = subprocess.run(['node', 'ui-core/scripts/validate-preview-evidence.mjs', str(evidence / 'responses.json')],
                           cwd=config['cwd'], capture_output=True, text=True, encoding='utf-8')
    (evidence / 'wire-validation.log').write_text(check.stdout + check.stderr, encoding='utf-8')
    assert check.returncode == 0, check.stderr
    record.update(status='passed', responseCount=len(receipts), inputFiles=len(before), inputsUnchanged=True,
                  destinationCreated=False, schema='preview_targets/1 + strict UI-Core/1.0')
except Exception:
    record.update(status='failed', error=traceback.format_exc())
    raise
finally:
    record['elapsedSeconds'] = round(time.monotonic() - started, 3)
    (evidence / 'result.json').write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(record, ensure_ascii=False), flush=True)
