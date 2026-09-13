"""Populate build-only Linux runtime caches from the already verified candidate archive."""
from pathlib import Path, PurePosixPath
import hashlib
import json
import shutil
import tarfile

RUN = Path(__file__).resolve().parent
REPO = RUN.parents[2]
ARCHIVE = RUN.parent / 'linux-33ceb6e9-explicit/payload/Copperbench 0.1.0 Linux x86_64.tar.gz'
expected = '5e1ae34cbc1734618d93837cb85c6487e8507d042ba967b79656af02832a1e05'
with ARCHIVE.open('rb') as source:
    assert hashlib.file_digest(source, 'sha256').hexdigest() == expected
targets = {'jdk': REPO / 'jdk/jbr25_linux_64', 'jdk21': REPO / 'jdk/jdk21_linux_64',
           'gradle-dists': REPO / 'build/linux-gradle-dists'}
for target in targets.values():
    assert target.resolve().is_relative_to(REPO.resolve())
    assert not target.exists(), target
    target.mkdir(parents=True)
manifest = []
with tarfile.open(ARCHIVE, 'r|gz') as archive:
    for member in archive:
        parts = PurePosixPath(member.name).parts
        if len(parts) < 2 or parts[0] != 'Copperbench010' or parts[1] not in targets:
            continue
        assert not member.issym() and not member.islnk(), 'Unexpected link in frozen runtime payload'
        assert '..' not in parts and not PurePosixPath(member.name).is_absolute()
        destination = targets[parts[1]].joinpath(*parts[2:])
        if member.isdir():
            destination.mkdir(parents=True, exist_ok=True)
        elif member.isfile():
            destination.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as source, destination.open('xb') as output:
                shutil.copyfileobj(source, output, length=1024 * 1024)
            with destination.open('rb') as copied:
                digest = hashlib.file_digest(copied, 'sha256').hexdigest()
            manifest.append({'sourceMember':member.name, 'path':str(destination.relative_to(REPO)),
                             'bytes':member.size, 'mode':member.mode, 'sha256':digest})
        else:
            raise RuntimeError('Unexpected archive member type')
assert (targets['jdk'] / 'bin/java').is_file()
assert (targets['jdk21'] / 'bin/java').is_file()
for version in ('9.7.0','9.6.1','8.8'):
    assert list(targets['gradle-dists'].glob(f'gradle-{version}-bin/**/bin/gradle'))
result = {'sourceArchiveSha256':expected, 'sourceCommit':'33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5',
          'purpose':'reuse unchanged build runtime dependencies only; no old application code',
          'files':manifest, 'applicationRebuiltSeparately':True, 'osConfigurationChanged':False}
(RUN / 'linux-runtime-cache-provenance.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'runtimeFilesReused':len(manifest), 'bytes':sum(f['bytes'] for f in manifest),
                  'scope':'build cache only', 'osConfigurationChanged':False}))
