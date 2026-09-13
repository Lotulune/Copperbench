from cold_environment import configure
configure()
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parent
config=json.loads((ROOT/'candidate.json').read_text(encoding='utf-8-sig'))
exe=Path(config['guestProductExe'])
creation=json.loads((ROOT/'create.stdout').read_text(encoding='utf-8-sig'))
label='app-attempt2'
started=datetime.now(timezone.utc)
startup=subprocess.STARTUPINFO()
startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
startup.wShowWindow=1
with (ROOT/(label+'.stdout')).open('xb') as stdout,(ROOT/(label+'.stderr')).open('xb') as stderr:
    process=subprocess.Popen([str(exe),creation['data']['workspaceFile']],cwd=exe.parent,
        stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,startupinfo=startup)
    (ROOT/(label+'.launch.json')).write_text(json.dumps({'at':started.isoformat(),'pid':process.pid,'showState':'SW_SHOWNORMAL'})+'\n')
    status=process.wait()
(ROOT/(label+'.process.json')).write_text(json.dumps({'startedAt':started.isoformat(),'completedAt':datetime.now(timezone.utc).isoformat(),'exitCode':status})+'\n')
