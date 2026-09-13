"""Launch installed verification in the existing GNOME user's session."""
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path('/home/stage15/s16-c0178f6b')
ROOT.mkdir(mode=0o700, exist_ok=True)
os.umask(0o077)
mode = sys.argv[1]
assert os.getuid() == 1000
env = dict(os.environ)
pids = subprocess.check_output(['pgrep', '-u', 'stage15', '-x', 'gnome-shell'], text=True).split()
assert len(pids) == 1, pids
session = {}
allowed = {'DISPLAY', 'WAYLAND_DISPLAY', 'XDG_RUNTIME_DIR', 'DBUS_SESSION_BUS_ADDRESS',
           'XAUTHORITY', 'XDG_SESSION_TYPE', 'XDG_CURRENT_DESKTOP', 'XDG_SESSION_DESKTOP'}
for entry in Path('/proc', pids[0], 'environ').read_bytes().split(b'\0'):
    name, _, value = entry.partition(b'=')
    if name.decode() in allowed:
        session[name.decode()] = value.decode()
env.update(session)
manager_environment = subprocess.check_output(['systemctl', '--user', 'show-environment'], env=env, text=True)
for entry in manager_environment.splitlines():
    name, _, value = entry.partition('=')
    if name in allowed:
        session[name] = value
del manager_environment
assert session.get('DISPLAY') and session.get('XDG_RUNTIME_DIR'), sorted(session)
env.update(session)

watch = ROOT / 'watch-verifier.py'
watch.write_text("import os,time\nfrom pathlib import Path\ndeadline=time.monotonic()+900\nwhile Path('/proc/5672').exists() and time.monotonic()<deadline: time.sleep(1)\n")
with (ROOT / 'idle-inhibitor.stdout').open('x') as stdout, (ROOT / 'idle-inhibitor.stderr').open('x') as stderr:
    proc = subprocess.Popen(['gnome-session-inhibit', '--inhibit', 'idle', '--reason',
        'Stage16 installed desktop verification', 'python3', str(watch)],
        env=env, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, start_new_session=True)
(ROOT / 'idle-inhibitor.json').write_text(json.dumps({'pid':proc.pid, 'at':datetime.now(timezone.utc).isoformat(),
    'watchedVerifierMonitorPid':5672, 'maximumSeconds':900, 'scope':'temporary GNOME idle inhibition while verifier is active',
    'persistentSettingsChanged':False}, indent=2)+'\n')
print(json.dumps({'inhibitorPid':proc.pid, 'maximumSeconds':900}))
