"""Launch installed verification in the existing GNOME user's session."""
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path('/home/stage15/s16-cold-fixed')
ROOT.mkdir(mode=0o700, exist_ok=True)
os.umask(0o077)
mode = sys.argv[1]
assert os.getuid() == 1000
from cold_environment import configure
configure()
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

def launch(label, command):
    with (ROOT / (label + '.monitor.stdout')).open('x') as stdout, (ROOT / (label + '.monitor.stderr')).open('x') as stderr:
        proc = subprocess.Popen(['python3', str(ROOT / 'monitor-process.py'), label, 'gnome-session-inhibit', '--inhibit', 'idle', '--reason', 'Stage16 cold-cache validation', *command], env=env,
                                cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, start_new_session=True)
    print(json.dumps({'monitorPid':proc.pid, 'mode':label, 'sessionVariableNames':sorted(session)}))

config = json.loads((ROOT / 'candidate.json').read_text())
assert Path('/opt/copperbench/lib/copperbench.jar').is_file()
import hashlib
assert hashlib.sha256(Path('/opt/copperbench/lib/copperbench.jar').read_bytes()).hexdigest() == config['applicationJarSha256']

if mode == 'grant':
    launch('grant', ['/usr/bin/copperbench', 'bootstrap', 'authorize-task', '--root', str(ROOT),
                    '--label', 'Stage16 complete Ubuntu cold-cache validation', '--capabilities',
                    'create,edit,build,test,run_client', '--ttl-seconds', '14400'])
elif mode == 'create':
    grant = json.loads((ROOT / 'grant.stdout').read_text())
    assert grant['status'] == 'completed', grant
    data = grant['data']
    authorization_id = data.get('authorizationId') or data.get('id')
    assert authorization_id, sorted(data)
    launch('create', ['/usr/bin/copperbench', 'bootstrap', 'create-workspace',
                     '--generator-id', 'fabric-1.21.1', '--mod-name', 'Stage16 Cold Cache Acceptance',
                     '--mod-id', 'stage16_cold', '--workspace-folder', str(ROOT / 'workspace'),
                     '--task-authorization', authorization_id, '--no-prompt', 'true'])
elif mode in ('app', 'app-reopen'):
    creation = json.loads((ROOT / 'create.stdout').read_text())
    assert creation['status'] == 'committed', creation
    launch(mode, ['/usr/bin/copperbench', creation['data']['workspaceFile']])
elif mode == 'helper':
    creation = json.loads((ROOT / 'create.stdout').read_text())
    launch('helper-launcher', ['python3', str(ROOT / 'harness/run-installed-agent-desktop.py'),
                              '--helper', str(ROOT / 'harness/verify-stage16-installed-agent.py'),
                              '--workspace', creation['data']['workspaceFile'],
                              '--candidate-sha256', config['installerSha256'],
                              '--output-directory', str(ROOT / 'agent-attempt1')])
else:
    raise ValueError(mode)
