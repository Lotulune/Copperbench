"""Collect installed regression facts without declaring positive graphics support."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parent
config = json.loads((root / 'candidate.json').read_text(encoding='utf-8-sig'))
exe = Path(config['guestProductExe'])
assert str(root) == config['guestRoot']
assert hashlib.sha256((exe.parent / 'lib/copperbench.jar').read_bytes()).hexdigest() == config['applicationJarSha256']
workspace = root / 'workspace'
assert not (workspace / '.copperbench/mcp-connection.json').exists()
result = json.loads((root / 'agent-attempt1/external-agent-result.json').read_text(encoding='utf-8'))
assert result['status'] == 'passed'
assert result['validationKind'] == 'installed-windows-opengl-zero-exit-negative-regression'
assert result['agentLoop']['runClientLifecycle']['negativeGraphicsRegressionPassed']
assert result['shutdown']['descriptorRemoved'] and result['shutdown']['oldConnectionRejected']
assert result['reopen']['oldTokenRejectedOnNewEndpoint']
assert result['reopen']['descriptorRemovedAfterSecondClose']
for label in ('app', 'app-reopen'):
    assert json.loads((root / (label + '.process.json')).read_text(encoding='utf-8'))['exitCode'] == 0
out = root / 'evidence-final'
out.mkdir(exist_ok=False)
grant = json.loads((root / 'grant.stdout').read_text(encoding='utf-8-sig'))
assert grant['data']['root'] == str(root)
grant_id = grant['data']['id']

def command(label, args):
    start = datetime.now(timezone.utc)
    completed = subprocess.run([str(exe), *args], cwd=exe.parent, capture_output=True,
        text=True, encoding='utf-8', creationflags=subprocess.CREATE_NO_WINDOW)
    (out / (label + '.stdout.txt')).write_text(completed.stdout, encoding='utf-8')
    (out / (label + '.stderr.txt')).write_text(completed.stderr, encoding='utf-8')
    (out / (label + '.process.json')).write_text(json.dumps({'startedAt': start.isoformat(),
        'completedAt': datetime.now(timezone.utc).isoformat(), 'exitCode': completed.returncode}, indent=2) + '\n', encoding='utf-8')
    return completed

revoked = command('revoke', ['bootstrap', 'revoke-authorization', '--authorization-id', grant_id])
assert revoked.returncode == 0
denied = command('revoked-create', ['bootstrap', 'create-workspace', '--generator-id', 'fabric-1.21.1',
    '--mod-name', 'Revoked Denial', '--mod-id', 'revoked_denial', '--workspace-folder', str(root / 'revoked-denial'),
    '--task-authorization', grant_id, '--no-prompt', 'true'])
denial = json.loads(denied.stdout)
assert denied.returncode == 3 and denial.get('code') == 'TASK_AUTHORIZATION_REVOKED'
assert not (root / 'revoked-denial').exists()
for path in root.iterdir():
    if path.is_file() and path.suffix in {'.stdout', '.stderr', '.json', '.jsonl', '.py', '.pyw'}:
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
            if relative.endswith('.lock') or relative == '.git' or name.startswith('.env'):
                continue
            content = path.read_bytes()
            manifest.append({'path': relative, 'bytes': len(content), 'sha256': hashlib.sha256(content).hexdigest()})
            archive.writestr(relative, content)
(out / 'final-source-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
facts = {'at': datetime.now(timezone.utc).isoformat(), 'candidateCommit': config['sourceCommit'],
    'installerSha256': config['installerSha256'], 'installedAppJarSha256': config['applicationJarSha256'],
    'artifactSha256': hashlib.sha256(artifacts[0].read_bytes()).hexdigest(),
    'descriptorAbsentAfterReopenClose': True, 'revokedCreateCode': denial['code'],
    'freshAgent': False, 'cacheState': 'existing warm dependency and asset caches; new workspace',
    'tooling': {name: shutil.which(name) for name in ('java', 'javac', 'gradle', 'git', 'python3')},
    'externalHelperPython': str(Path(os.sys.executable)),
    'validationKind': result['validationKind'], 'windowsInstalledClientComplete': False,
    'stage16Complete': False}
(out / 'collection-facts.json').write_text(json.dumps(facts, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'output': str(out), 'status': result['status'], 'rawFiles': sum(1 for p in out.rglob('*') if p.is_file())}))
