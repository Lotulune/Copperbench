"""Run only through Gradle runNativePythonSmoke against an isolated generated fixture."""
import json
from pathlib import Path
import sys
from copperbench import Workspace, NativeApiError

config = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))


def open_workspace():
    return Workspace.open(config['workspace'], launcher=config['launcher'], cwd=config['cwd'])


with open_workspace() as workspace:
    assert workspace.revision == 0
    result = workspace.create_mod_element(elementType='item', name='python_bell', initialValues={'displayName': '铜铃'})
    assert result['status'] == 'committed', result
    elements = list(workspace.list_mod_elements())
    assert any(item['name'] == 'python_bell' for item in elements), elements
    try:
        workspace.build(expected_revision=0)
        raise AssertionError('Stale revision was accepted')
    except NativeApiError as error:
        assert error.code == 'REVISION_CONFLICT', error.details
    try:
        with open_workspace():
            raise AssertionError('Concurrent writer was allowed')
    except NativeApiError as error:
        assert error.code == 'WORKSPACE_WRITE_LOCKED', error.details
    try:
        workspace.command('create_task_authorization', userApproved=True)
        raise AssertionError('Python self-authorization was allowed')
    except NativeApiError as error:
        assert error.code == 'TASK_AUTHORIZATION_USER_ONLY', error.details
    accepted = workspace.validate()
    assert accepted['status'] == 'accepted', accepted
    validated = workspace.wait_task(accepted['task']['id'], timeout=120)
    assert validated['data']['task']['state'] == 'succeeded', validated
    final_revision = workspace.revision

with open_workspace() as reopened:
    assert reopened.revision == final_revision
    assert any(item['name'] == 'python_bell' for item in reopened.list_mod_elements())
    assert not (Path(config['workspace']).parent / '.copperbench/mcp-connection.json').exists()
print('PASS: Python -> product Core, persisted element, task completion, conflict, lock, authorization, reopen; no MCP')
