"""Collect fixed-candidate Windows installed evidence after normal desktop closes."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

root = Path(r'C:\Temp\S16-33ceb6e9')
exe = Path(r'C:\Copperbench-Stage16-33ceb6e9\copperbench.exe')
out = root / 'evidence-final'
assert not (out / 'collection-facts.json').exists(), 'Completed evidence is immutable'
out.mkdir(exist_ok=True)
workspace = root / 'workspace'
assert not (workspace / '.copperbench/mcp-connection.json').exists()
result = json.loads((root / 'partial-summary.json').read_text(encoding='utf-8-sig'))
assert result['status'] == 'partial'
assert result['agentLoop']['runClientLifecycle']['zeroExitFalseSuccessReproduced']
assert result['shutdown']['descriptorRemoved'] and result['shutdown']['endpointClosed']
grant = json.loads((root / 'grant.stdout').read_text(encoding='utf-8-sig'))
grant_id = grant['data']['id']
assert grant['data']['root'] == str(root)
assert set(grant['data']['capabilities']) == {'create', 'edit', 'build', 'test', 'run_client'}

def command(label, args):
    process_file = out / (label + '.process.json')
    stdout_file = out / (label + '.stdout.txt')
    stderr_file = out / (label + '.stderr.txt')
    if process_file.exists():
        previous = json.loads(process_file.read_text())
        return subprocess.CompletedProcess(args, previous['exitCode'], stdout_file.read_text(), stderr_file.read_text())
    start = datetime.now(timezone.utc)
    completed = subprocess.run([str(exe), *args], cwd=exe.parent, capture_output=True,
        text=True, encoding='utf-8', creationflags=subprocess.CREATE_NO_WINDOW)
    stdout_file.write_text(completed.stdout, encoding='utf-8')
    stderr_file.write_text(completed.stderr, encoding='utf-8')
    process_file.write_text(json.dumps({'startedAt': start.isoformat(),
        'completedAt': datetime.now(timezone.utc).isoformat(), 'exitCode': completed.returncode}, indent=2) + '\n')
    return completed

revoked = command('revoke', ['bootstrap', 'revoke-authorization', '--authorization-id', grant_id])
assert revoked.returncode == 0
denied = command('revoked-create', ['bootstrap', 'create-workspace', '--generator-id', 'fabric-1.21.1',
    '--mod-name', 'Revoked Denial', '--mod-id', 'revoked_denial', '--workspace-folder', str(root / 'revoked-denial'),
    '--task-authorization', grant_id, '--no-prompt', 'true'])
denial = json.loads(denied.stdout)
assert denied.returncode == 3 and denial.get('code') == 'TASK_AUTHORIZATION_REVOKED', denial
assert not (root / 'revoked-denial').exists()
for path in root.iterdir():
    if path.is_file() and path.suffix in {'.stdout', '.stderr', '.json', '.jsonl', '.py', '.pyw'}:
        shutil.copy2(path, out / (path.name + ('.txt' if path.suffix in {'.stdout', '.stderr'} else '')))
shutil.copytree(root / 'agent-attempt1', out / 'agent-attempt1')
shutil.copytree(root / 'agent-post-failure', out / 'agent-post-failure')
shutil.copytree(root / 'agent-post-failure-retry', out / 'agent-post-failure-retry')
shutil.copytree(root / 'harness', out / 'harness', ignore=shutil.ignore_patterns('__pycache__'))
shutil.copy2(workspace / '.copperbench/automation-audit.jsonl', out / 'automation-audit.jsonl')
shutil.copy2(workspace / 'run/logs/latest.log', out / 'minecraft-latest.log.txt')
artifacts = [p for p in (workspace / 'build/libs').glob('*.jar') if not p.name.endswith('-sources.jar')]
assert len(artifacts) == 1
shutil.copy2(artifacts[0], out / artifacts[0].name)
manifest = []
with zipfile.ZipFile(out / 'final-workspace-source.zip', 'x', zipfile.ZIP_DEFLATED) as archive:
    for folder, dirs, files in os.walk(workspace):
        dirs[:] = sorted(d for d in dirs if d not in {'.copperbench', '.gradle', 'build', 'run', '.idea', '.git',
            'localHistory', 'workspaceBackups', 'userSettings'})
        for name in sorted(files):
            path = Path(folder, name)
            relative = path.relative_to(workspace).as_posix()
            if relative.endswith('.lock') or relative == '.git':
                continue
            content = path.read_bytes()
            manifest.append({'path': relative, 'bytes': len(content), 'sha256': hashlib.sha256(content).hexdigest()})
            archive.writestr(relative, content)
(out / 'final-source-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
runtime = json.loads((root / 'runtime-facts.json').read_text(encoding='utf-8-sig'))
facts = {'at': datetime.now(timezone.utc).isoformat(),
    'candidateCommit': '33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5',
    'installedAppJarSha256': hashlib.sha256((exe.parent / 'lib/copperbench.jar').read_bytes()).hexdigest(),
    'artifactSha256': hashlib.sha256(artifacts[0].read_bytes()).hexdigest(),
    'runtimeEvidence': 'runtime-facts.json', 'desktopSessionId': 2,
    'descriptorAbsentAfterReopenClose': True, 'revokedCreateCode': denial['code'],
    'freshAgent': False, 'cacheState': 'existing warm dependency caches; new workspace',
    'tooling': {name: shutil.which(name) for name in ('java', 'javac', 'gradle', 'git', 'python3')},
    'externalHelperPython': str(root / 'python/python.exe'),
    'humanInteraction': 'User confirmed the displayed local task grant; agent operated native desktop and normal closes.'}
assert facts['installedAppJarSha256'] == '1f049a071cfe709fba9c98ce22defb9bbe6688fc334f421b64ce802315758c01'
(out / 'collection-facts.json').write_text(json.dumps(facts, indent=2) + '\n')
print(json.dumps({'output': str(out), 'result': result['status'], 'revokedCreateCode': denial['code'],
                  'rawFiles': sum(1 for p in out.rglob('*') if p.is_file())}))
