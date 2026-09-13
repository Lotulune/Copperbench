"""Collect immutable non-secret evidence from the installed Ubuntu gate."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

root = Path('/home/stage15/s16-33ceb6e9')
out = root / 'evidence-final'
assert not (out / 'collection-facts.json').exists(), 'Completed evidence is immutable'
out.mkdir(mode=0o700, exist_ok=True)
workspace = root / 'workspace'
assert not (workspace / '.copperbench/mcp-connection.json').exists()
result = json.loads((root / 'agent-attempt1/external-agent-result.json').read_text())
assert result['status'] == 'passed'
grant = json.loads((root / 'grant.stdout').read_text())
grant_id = grant['data']['id']
assert grant['data']['root'] == str(root)
assert set(grant['data']['capabilities']) == {'create', 'edit', 'build', 'test', 'run_client'}

def command(label, args):
    if (out / (label + '.process.json')).exists():
        previous = json.loads((out / (label + '.process.json')).read_text())
        return subprocess.CompletedProcess(args, previous['exitCode'],
            (out / (label + '.stdout.txt')).read_text(), (out / (label + '.stderr.txt')).read_text())
    start = datetime.now(timezone.utc)
    completed = subprocess.run(args, capture_output=True, text=True)
    (out / (label + '.stdout.txt')).write_text(completed.stdout)
    (out / (label + '.stderr.txt')).write_text(completed.stderr)
    (out / (label + '.process.json')).write_text(json.dumps({
        'startedAt': start.isoformat(), 'completedAt': datetime.now(timezone.utc).isoformat(),
        'exitCode': completed.returncode}, indent=2) + '\n')
    return completed

revoked = command('revoke', ['/usr/bin/copperbench', 'bootstrap', 'revoke-authorization', '--authorization-id', grant_id])
assert revoked.returncode == 0
denied = command('revoked-create', ['/usr/bin/copperbench', 'bootstrap', 'create-workspace',
    '--generator-id', 'fabric-1.21.1', '--mod-name', 'Revoked Denial', '--mod-id', 'revoked_denial',
    '--workspace-folder', str(root / 'revoked-denial'), '--task-authorization', grant_id, '--no-prompt', 'true'])
denial = json.loads(denied.stdout)
assert denied.returncode == 3 and denial['status'] in {'rejected', 'failed'} and denial['code'] == 'TASK_AUTHORIZATION_REVOKED', denial
assert not (root / 'revoked-denial').exists()
for path in root.iterdir():
    if path.is_file() and path.suffix in {'.stdout', '.stderr', '.json', '.py'}:
        shutil.copy2(path, out / (path.name + ('.txt' if path.suffix in {'.stdout', '.stderr'} else '')))
shutil.copytree(root / 'agent-attempt1', out / 'agent-attempt1')
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
            if relative.endswith('.lock'):
                continue
            content = path.read_bytes()
            manifest.append({'path': relative, 'bytes': len(content), 'sha256': hashlib.sha256(content).hexdigest()})
            archive.writestr(relative, content)
(out / 'final-source-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
verification = command('installed-integrity', ['dpkg', '-V', 'copperbench'])
assert verification.returncode == 0 and not verification.stdout.strip()
session_type = subprocess.check_output(['loginctl', 'show-session', '1', '-p', 'Type', '-p', 'Active', '-p', 'LockedHint'], text=True)
facts = {
    'at': datetime.now(timezone.utc).isoformat(), 'candidateCommit': '33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5',
    'installedAppJarSha256': hashlib.sha256(Path('/opt/copperbench/lib/copperbench.jar').read_bytes()).hexdigest(),
    'artifactSha256': hashlib.sha256(artifacts[0].read_bytes()).hexdigest(),
    'session': session_type.splitlines(), 'meminfo': Path('/proc/meminfo').read_text().splitlines()[:17],
    'descriptorAbsentAfterReopenClose': True, 'revokedCreateCode': denial.get('code'),
    'freshAgent': False, 'cacheState': 'existing warm dependency caches; new workspace',
    'tooling': {name: shutil.which(name) for name in ('java', 'javac', 'gradle', 'git', 'python3')},
    'humanInteraction': 'User confirmed the displayed local task grant; agent operated native desktop and normal closes.',
}
(out / 'collection-facts.json').write_text(json.dumps(facts, indent=2) + '\n')
print(json.dumps({'output': str(out), 'result': result['status'], 'revokedCreateCode': denial.get('code'),
                  'rawFiles': sum(1 for p in out.rglob('*') if p.is_file())}))
