"""Real Python attachment to a writer-leased product Core, without MCP."""
from pathlib import Path
import sys
from copperbench import Workspace, NativeApiError

file = Path(sys.argv[1])
with Workspace.connect(file) as first, Workspace.connect(file) as second:
    bell = first.elements['python_bell']
    stale = second.elements['python_bell']
    bell.update(displayName='桌面铜铃')
    try:
        stale.update(displayName='stale overwrite')
        raise AssertionError('A stale object overwrote the live desktop edit')
    except NativeApiError as error:
        assert error.code == 'REVISION_CONFLICT', error.details
    assert stale['/displayName'] == '桌面铜铃'
    stale['/displayName'] = '共享铜铃'
    first.get_workspace()
    created = first.elements.new('item', 'python_live_item', displayName='实时物品')
    assert created['/displayName'] == '实时物品'
    try:
        first.command('create_task_authorization', userApproved=True)
        raise AssertionError('Attached Python impersonated the desktop user')
    except NativeApiError as error:
        assert error.code == 'TASK_AUTHORIZATION_USER_ONLY', error.details

with Workspace.connect(file) as reconnected:
    assert reconnected.elements['python_bell']['/displayName'] == '共享铜铃'
    assert reconnected.elements['python_live_item']['/displayName'] == '实时物品'
assert not (file.parent / '.copperbench/mcp-connection.json').exists()
print('PASS: live Python attachment, named objects, field assignment, revision conflict, reconnect; no MCP')
