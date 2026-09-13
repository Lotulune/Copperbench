from cold_environment import configure
configure()
"""Reopen the saved installed workspace and collect its ordinary process exit."""
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parent
config = json.loads((root / 'candidate.json').read_text(encoding='utf-8-sig'))
exe = Path(config['guestProductExe'])
creation = json.loads((root / 'create.stdout').read_text(encoding='utf-8-sig'))
start = datetime.now(timezone.utc)
with (root / 'app-reopen.stdout').open('xb') as stdout, (root / 'app-reopen.stderr').open('xb') as stderr:
    process = subprocess.Popen([str(exe), creation['data']['workspaceFile']], cwd=exe.parent,
        stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, creationflags=subprocess.CREATE_NO_WINDOW)
    (root / 'app-reopen.launch.json').write_text(json.dumps({'at':start.isoformat(),'pid':process.pid}) + '\n')
    status = process.wait()
(root / 'app-reopen.process.json').write_text(json.dumps({'startedAt':start.isoformat(),
    'completedAt':datetime.now(timezone.utc).isoformat(),'exitCode':status}) + '\n')
