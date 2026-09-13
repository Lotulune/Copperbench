"""Preserve the failed official-source trial and start a separate empty profile."""
from pathlib import Path
from datetime import datetime, timezone
import json
import os
import shutil
import subprocess
import sys
import urllib.request

assert os.getuid() == 1000
old = Path('/home/stage15/s16-cold-fixed')
new = Path('/home/stage15/s16-cold-official2')
assert not new.exists()
assert not (old / 'workspace').exists(), 'Failed creation did not roll back its directory'
assert json.loads((old / 'create.process.json').read_text())['exitCode'] == 10
sys.path.insert(0, str(old))
from cold_environment import configure
configure()
out = old / 'evidence-incomplete'
out.mkdir()
grant = json.loads((old / 'grant.stdout').read_text())['data']['id']
result = subprocess.run(['/usr/bin/copperbench', 'bootstrap', 'revoke-authorization',
                         '--authorization-id', grant], capture_output=True, text=True)
(out / 'revoke-incomplete.stdout.txt').write_text(result.stdout)
(out / 'revoke-incomplete.stderr.txt').write_text(result.stderr)
assert result.returncode == 0
for path in old.iterdir():
    if path.is_file() and path.suffix in {'.json', '.stdout', '.stderr', '.py'}:
        shutil.copy2(path, out / (path.name + ('.txt' if path.suffix in {'.stdout', '.stderr'} else '')))
shutil.copytree(old / 'home/.copperbench/logs', out / 'application-logs')
shutil.copytree(old / 'home/.copperbench/gradle/daemon/9.7.0', out / 'gradle-daemon',
                ignore=shutil.ignore_patterns('registry.bin', 'registry.bin.lock'))
url = 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-lifecycle-events-v1/2.6.0+0865547519/fabric-lifecycle-events-v1-2.6.0+0865547519.pom'
probe = {'at': datetime.now(timezone.utc).isoformat(), 'url': url,
         'purpose': 'Independent network diagnostic; body discarded; no dependency-cache prefill'}
try:
    with urllib.request.urlopen(url, timeout=20) as response:
        probe.update(status=response.status, bytes=len(response.read()))
except Exception as error:
    probe['error'] = str(error)
(out / 'connectivity-probe.json').write_text(json.dumps(probe, indent=2) + '\n')
(out / 'failure-checkpoint.json').write_text(json.dumps({
    'at': datetime.now(timezone.utc).isoformat(), 'status': 'failed',
    'createExitCode': 10, 'code': 'WORKSPACE_GRADLE_SYNC_FAILED',
    'cause': 'Fabric Maven repository TLS handshake terminated by remote host',
    'workspaceRolledBack': True, 'grantRevoked': True, 'desktopMcpReached': False,
    'fullColdCachePassed': False, 'subsequentProbeProvesOriginalJavaRoute': False,
}, indent=2) + '\n')
new.mkdir(mode=0o700)
for path in old.glob('*.py'):
    (new / path.name).write_text(path.read_text().replace(str(old), str(new)))
shutil.copytree(old / 'harness', new / 'harness', ignore=shutil.ignore_patterns('__pycache__'))
for name in ['candidate.json', 'runtime-facts.json', 'installation.json']:
    text = (old / name).read_text()
    if name == 'candidate.json':
        text = text.replace(str(old), str(new))
    (new / name).write_text(text)
(new / 'retry-provenance.json').write_text(json.dumps({
    'at': datetime.now(timezone.utc).isoformat(), 'previousTrial': str(old),
    'copied': ['verification scripts', 'candidate metadata', 'installation metadata'],
    'sourceCacheCopied': False, 'userDataCopied': False, 'networkProfile': 'official repositories',
}, indent=2) + '\n')
subprocess.run(['python3', str(new / 'initialize-cold-profile.py')], check=True)
subprocess.run(['python3', str(new / 'observe-cache.py'), 'before-launch'], check=True)
print(json.dumps({'newRoot': str(new), 'previousFailureEvidence': str(out)}))
