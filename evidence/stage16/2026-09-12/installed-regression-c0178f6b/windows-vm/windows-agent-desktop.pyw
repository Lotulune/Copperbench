"""Use a masked desktop prompt and private pipe for installed Windows MCP verification."""
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import simpledialog, messagebox

ROOT = Path(__file__).resolve().parent
CONFIG = json.loads((ROOT / 'candidate.json').read_text(encoding='utf-8-sig'))
output = ROOT / 'agent-attempt1'
output.mkdir(exist_ok=False)
creation = json.loads((ROOT / 'create.stdout').read_text(encoding='utf-8-sig'))
assert creation['status'] == 'committed'
window = tk.Tk()
window.withdraw()
pasted = simpledialog.askstring('Stage16 Desktop MCP',
    'Paste the token or configuration copied from installed Copperbench AI / MCP.\n'
    'It stays in this guest and is sent only through a private stdin pipe.', show='*', parent=window)
if not pasted:
    raise SystemExit(2)
match = re.search(r'Authorization:\s*Bearer\s+(\S+)', pasted)
token = match.group(1) if match else pasted
del pasted, match
window.clipboard_clear()
events = []

def notify(stage, message):
    events.append({'stage': stage, 'at': datetime.now(timezone.utc).isoformat()})
    window.after(0, lambda: messagebox.showinfo('Stage16 verification', message, parent=window))

def run():
    global token
    command = [str(Path(sys.executable).with_name('python.exe')), '-u',
        str(ROOT / 'harness/verify-stage16-installed-agent.py'), creation['data']['workspaceFile'],
        '--candidate-sha256', CONFIG['installerSha256'],
        '--output', str(output / 'external-agent-result.json')]
    with (output / 'helper.log').open('x', encoding='utf-8') as log:
        with subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                creationflags=subprocess.CREATE_NO_WINDOW) as process:
            process.stdin.write(token + '\n')
            process.stdin.close()
            for line in process.stdout:
                log.write(line.replace(token, '[REDACTED]'))
                log.flush()
                if line.startswith('Confirm the Minecraft window is visibly usable'):
                    notify('minecraft-close-requested',
                           'Render readiness passed and the task remained running. Observe Minecraft, then quit normally. Keep Copperbench open.')
                elif line.startswith('Agent loop passed. Close the installed Copperbench'):
                    notify('copperbench-close-requested',
                           'MCP operations, builds, conflict recovery and expected OpenGL failure passed. Close Copperbench normally within 5 minutes to verify rejection of the old authenticated connection.')
                elif line.startswith('Reopen installed Copperbench now'):
                    notify('copperbench-reopen-requested',
                           'Normal close and rejection of the old connection passed. Reopen the saved workspace with the Stage16 c0178f6b Reopen shortcut within 5 minutes.')
                elif line.startswith('Old token rejected by reopened session.'):
                    notify('copperbench-second-close-requested',
                           'The new session rejected the previous token with HTTP 401. Close Copperbench normally again.')
            status = process.wait()
    token = ''
    (output / 'desktop-launcher-result.json').write_text(json.dumps({
        'schemaVersion': '1.0', 'helperExitCode': status, 'notificationEvents': events,
        'workspacePath': creation['data']['workspaceFile'], 'tokenPersisted': False,
        'formalSupportClaim': False}, indent=2) + '\n', encoding='utf-8')
    notify('verifier-finished', f'Verifier finished with exit code {status}.')

threading.Thread(target=run, daemon=True).start()
window.mainloop()
