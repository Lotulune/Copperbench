"""Install the sealed candidate and verify every expected packaged file."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile

PACKAGE = Path('/home/stage15-operator/s16-c0178f6b-package')
ROOT = Path('/home/stage15/s16-c0178f6b')
assert os.getuid() == 0
assert not ROOT.exists(), 'New Ubuntu validation root already exists'
config = json.loads((PACKAGE / 'candidate.json').read_text())
def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

assert sha(Path('/opt/copperbench/lib/copperbench.jar')) == '473f9b06af5ee6e2fdb95c6d7eb92c1a426f1ffddfa4672091fe79b976c7c058'
deb = PACKAGE / 'copperbench_0.1.0_amd64.deb'
assert sha(deb) == config['installerSha256']
start = datetime.now(timezone.utc)
with (PACKAGE / 'install.stdout').open('x') as stdout, (PACKAGE / 'install.stderr').open('x') as stderr:
    result = subprocess.run(['dpkg','-i',str(deb)], stdout=stdout, stderr=stderr)
assert result.returncode == 0
manifest_file = PACKAGE / 'linux-payload-manifest.json'
assert sha(manifest_file) == config['payloadManifestSha256']
manifest = json.loads(manifest_file.read_text())
for item in manifest:
    path = Path('/') / item['path']
    assert path.is_file() and sha(path) == item['sha256'], item['path']
    assert path.stat().st_mode & 0o777 == item['mode'], ('mode',item['path'])
ROOT.mkdir(mode=0o700)
with tarfile.open(PACKAGE / 'linux-harness.tar') as source:
    assert all(not m.issym() and not m.islnk() for m in source.getmembers())
    source.extractall(ROOT, filter='data')
shutil.copy2(PACKAGE / 'candidate.json', ROOT / 'candidate.json')
facts = {'startedAt':start.isoformat(), 'completedAt':datetime.now(timezone.utc).isoformat(),
    'exitCode':result.returncode, 'sourceCommit':config['sourceCommit'],
    'installerSha256':config['installerSha256'], 'installedJarSha256':sha(Path('/opt/copperbench/lib/copperbench.jar')),
    'verifiedPayloadFiles':len(manifest), 'allPayloadHashesAndModesMatched':True}
(ROOT / 'installation.json').write_text(json.dumps(facts, indent=2) + '\n')
runtime = {'osRelease':Path('/etc/os-release').read_text(),
    'meminfo':Path('/proc/meminfo').read_text().splitlines()[:17],
    'sessions':subprocess.check_output(['loginctl','list-sessions','--no-legend'],text=True).splitlines(),
    'installedJarSha256':facts['installedJarSha256'], 'helperPython':'/usr/bin/python3',
    'environmentKind':'ubuntu-vm', 'tooling':{name:shutil.which(name) for name in ('java','javac','gradle','git','python3')}}
(ROOT / 'runtime-facts.json').write_text(json.dumps(runtime, indent=2) + '\n')
for path in [ROOT, *ROOT.rglob('*')]:
    assert not path.is_symlink()
    os.chown(path,1000,1000)
print(json.dumps(facts))
