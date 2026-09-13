"""Seal standard Gradle Linux outputs into a low-memory Debian package on Ubuntu."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parent
assert ROOT == Path('/home/stage15-operator/s16-c0178f6b-package')
os.umask(0o022)
config = json.loads((ROOT / 'linux-candidate-before-deb.json').read_text(encoding='utf-8-sig'))
def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

archive = ROOT / 'Copperbench-c0178f6b-linux.tar.gz'
scaffold = ROOT / 'linux-deb-scaffold.tar'
assert sha(archive) == config['archiveSha256']
assert sha(scaffold) == config['scaffoldSha256']
deb_root = ROOT / 'deb-root'
deb_root.mkdir(exist_ok=False)
with tarfile.open(scaffold) as source:
    assert all(not m.issym() and not m.islnk() for m in source.getmembers())
    source.extractall(deb_root, filter='data')
payload_root = ROOT / 'payload-extraction'
payload_root.mkdir(exist_ok=False)
with tarfile.open(archive) as source:
    for member in source:
        parts = PurePosixPath(member.name).parts
        assert parts and parts[0] == 'Copperbench010' and '..' not in parts
        assert not member.issym() and not member.islnk()
        source.extract(member, payload_root, filter='data')
(deb_root / 'opt').mkdir()
(payload_root / 'Copperbench010').rename(deb_root / 'opt/copperbench')
assert sha(deb_root / 'opt/copperbench/lib/copperbench.jar') == config['applicationJarSha256']
# Match prepareDebLinux64's Unix permission normalization. The standard TAR also
# carries executable Gradle launchers, as specified by tarLinux64.
commands = [['chmod','-R','u+rwX,go+rX',str(deb_root)]]
for relative in ('usr/bin/copperbench', 'opt/copperbench/copperbench.sh', 'opt/copperbench/gradlew'):
    commands.append(['chmod','0755',str(deb_root / relative)])
for runtime in ('jdk','jdk21'):
    commands.append(['chmod','-R','a+rx',str(deb_root / 'opt/copperbench' / runtime / 'bin')])
    for helper in ('jspawnhelper','jexec','jcef_helper','cef_server'):
        path = deb_root / 'opt/copperbench' / runtime / 'lib' / helper
        if path.exists():
            commands.append(['chmod','0755',str(path)])
for command in commands:
    subprocess.run(command, check=True)
manifest = []
for path in sorted(deb_root.rglob('*')):
    assert not path.is_symlink()
    if path.is_file() and 'DEBIAN' not in path.relative_to(deb_root).parts:
        manifest.append({'path':path.relative_to(deb_root).as_posix(), 'bytes':path.stat().st_size,
                         'mode':path.stat().st_mode & 0o777, 'sha256':sha(path)})
(ROOT / 'linux-payload-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
deb = ROOT / 'copperbench_0.1.0_amd64.deb'
assert not deb.exists()
start = datetime.now(timezone.utc)
command = ['dpkg-deb','--build','--root-owner-group','--threads-max=1','-Zgzip','-z6',str(deb_root),str(deb)]
with (ROOT / 'deb-build.stdout').open('x') as stdout, (ROOT / 'deb-build.stderr').open('x') as stderr:
    result = subprocess.run(command, stdout=stdout, stderr=stderr)
assert result.returncode == 0
config.update({'installerSha256':sha(deb), 'installerBytes':deb.stat().st_size,
    'installerPath':str(deb), 'archivePath':str(archive), 'debSealingPending':False,
    'debSealingHost':'Ubuntu', 'debCompression':'gzip -6', 'debBuildCommand':command,
    'debBuildStartedAt':start.isoformat(), 'debBuildCompletedAt':datetime.now(timezone.utc).isoformat(),
    'payloadFiles':len(manifest), 'payloadManifestSha256':sha(ROOT / 'linux-payload-manifest.json'),
    'environmentKind':'ubuntu-vm', 'formalSupportClaim':False})
(ROOT / 'candidate.json').write_text(json.dumps(config, indent=2) + '\n')
print(json.dumps({'status':'sealed', 'installerSha256':config['installerSha256'],
                  'applicationJarSha256':config['applicationJarSha256'], 'payloadFiles':len(manifest)}))
