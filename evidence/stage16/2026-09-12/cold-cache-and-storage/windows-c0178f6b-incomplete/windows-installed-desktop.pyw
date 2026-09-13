from cold_environment import configure
configure()
"""Run fixed-candidate native approval and workspace bootstrap in the real desktop session."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import traceback
import tkinter as tk
from tkinter import messagebox

ROOT = Path(__file__).resolve().parent
CONFIG = json.loads((ROOT / 'candidate.json').read_text(encoding='utf-8-sig'))
EXE = Path(CONFIG['guestProductExe'])
APP_JAR = EXE.parent / 'lib/copperbench.jar'
assert ROOT == Path(CONFIG['guestRoot'])
assert hashlib.sha256(APP_JAR.read_bytes()).hexdigest() == CONFIG['applicationJarSha256']

def run(label, arguments):
    started = datetime.now(timezone.utc)
    with (ROOT / (label + '.stdout')).open('xb') as stdout, (ROOT / (label + '.stderr')).open('xb') as stderr:
        process = subprocess.Popen([str(EXE), *arguments], cwd=EXE.parent,
            stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr,
            creationflags=subprocess.CREATE_NO_WINDOW)
        (ROOT / (label + '.launch.json')).write_text(json.dumps({
            'at': started.isoformat(), 'pid': process.pid, 'mode': label,
            'user': os.environ.get('USERNAME')}, indent=2) + '\n', encoding='utf-8')
        status = process.wait()
    (ROOT / (label + '.process.json')).write_text(json.dumps({
        'startedAt': started.isoformat(), 'completedAt': datetime.now(timezone.utc).isoformat(),
        'exitCode': status}, indent=2) + '\n', encoding='utf-8')
    subprocess.run([str(Path(os.sys.executable).with_name('python.exe')), str(ROOT / 'observe-cache.py'), 'after-' + label], check=True, creationflags=subprocess.CREATE_NO_WINDOW)
    if label == 'app':
        assert status == 0, status
        return {'exitCode': status}
    result = json.loads((ROOT / (label + '.stdout')).read_text(encoding='utf-8-sig'))
    assert status == 0 and result.get('exitCode') == 0, result
    return result

try:
    grant = run('grant', ['bootstrap', 'authorize-task', '--root', str(ROOT),
        '--label', 'Stage16 c0178f6b complete cold-cache validation', '--capabilities',
        'create,edit,build,test,run_client', '--ttl-seconds', '14400'])
    assert grant['status'] == 'completed'
    creation = run('create', ['bootstrap', 'create-workspace', '--generator-id', 'fabric-1.21.1',
        '--mod-name', 'Stage16 Cold Cache Acceptance', '--mod-id', 'stage16_cold',
        '--workspace-folder', str(ROOT / 'workspace'), '--task-authorization', grant['data']['id'],
        '--no-prompt', 'true'])
    assert creation['status'] == 'committed'
    run('app', [creation['data']['workspaceFile']])
except BaseException:
    (ROOT / 'desktop-bootstrap-failure.txt').write_text(traceback.format_exc(), encoding='utf-8')
    popup = tk.Tk()
    popup.withdraw()
    messagebox.showerror('Stage16 verification', 'The desktop bootstrap failed. Inspect the saved non-secret evidence log.', parent=popup)
    popup.destroy()
