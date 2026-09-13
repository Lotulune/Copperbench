"""Capture normal native process completion while its launcher returns immediately."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import sys
root = Path(__file__).resolve().parent
label, *command = sys.argv[1:]
start = datetime.now(timezone.utc)
with (root / (label + '.stdout')).open('xb') as stdout, (root / (label + '.stderr')).open('xb') as stderr:
    process = subprocess.Popen(command, cwd=root, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
    (root / (label + '.launch.json')).write_text(json.dumps({'at':start.isoformat(), 'pid':process.pid,
        'mode':label, 'uid':os.getuid()}, indent=2) + '\n')
    status = process.wait()
(root / (label + '.process.json')).write_text(json.dumps({'startedAt':start.isoformat(),
    'completedAt':datetime.now(timezone.utc).isoformat(), 'exitCode':status}, indent=2) + '\n')
subprocess.run(['python3', str(root / 'observe-cache.py'), 'after-' + label], check=True)
raise SystemExit(status)
