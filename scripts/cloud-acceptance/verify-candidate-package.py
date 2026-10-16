import argparse
import hashlib
import io
import json
import subprocess
import tarfile
import zipfile
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--archive', type=Path, required=True)
p.add_argument('--source', required=True)
p.add_argument('--result', type=Path, required=True)
args = p.parse_args()
targets = ['docs/releases/v0.1.9.md', 'docs/releases/v0.1.9-linux.md',
           'docs/testing/maintenance-release-0.1.9.md', 'lib/copperbench.jar', 'plugins/generator-1.20.1.zip']
found = {}
def inspect(name, read):
    normalized = name.replace('\\', '/')
    for target in targets:
        if normalized == target or normalized.endswith('/' + target):
            assert target not in found, 'Duplicate package entry: ' + target
            data = read()
            item = {'entry': name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
            if target.endswith('.jar'):
                with zipfile.ZipFile(io.BytesIO(data)) as jar:
                    conf = jar.read('mcreator.conf').decode('utf-8')
                    assert 'product.version=0.1.9' in conf.splitlines()
                    item['productVersion'] = '0.1.9'
                    cls = jar.read('dev/copperbench/generator/GradleArchiveOutput$Request.class')
                    assert b'--no-configuration-cache' in cls and b'toRealPath' in cls
                    item['archiveRequestClassSha256'] = hashlib.sha256(cls).hexdigest()
                    data_pack = jar.read('dev/copperbench/generator/datapack/DataPackWorkspaceTaskGateway.class')
                    assert b'gradle-9.7.0-bin.zip' in data_pack
                    item['dataPackGatewayClassSha256'] = hashlib.sha256(data_pack).hexdigest()
            elif target == 'plugins/generator-1.20.1.zip':
                with zipfile.ZipFile(io.BytesIO(data)) as plugin:
                    entries = {}
                    for relative in ['datapack-1.20.1/generator.yaml', 'datapack-1.20.1/workspacebase/gradle/wrapper/gradle-wrapper.properties']:
                        contents = plugin.read(relative)
                        original = subprocess.check_output(['git', 'show', args.source + ':plugins/generator-1.20.1/' + relative])
                        assert contents == original, 'Data-pack template differs from tagged source: ' + relative
                        entries[relative] = hashlib.sha256(contents).hexdigest()
                    assert b'gradle-9.7.0-bin.zip' in contents
                    item['sourceEntries'] = entries
            else:
                original = subprocess.check_output(['git', 'show', args.source + ':' + target])
                assert data == original, 'Package documentation differs from source: ' + target
            found[target] = item

if args.archive.name.endswith('.zip'):
    with zipfile.ZipFile(args.archive) as archive:
        for item in archive.infolist():
            if not item.is_dir():
                inspect(item.filename, lambda item=item: archive.read(item))
else:
    with tarfile.open(args.archive, 'r|gz') as archive:
        for item in archive:
            if item.isfile():
                inspect(item.name, lambda item=item: archive.extractfile(item).read())
assert set(found) == set(targets), 'Package is missing expected source/version entries'
result = {'status': 'passed', 'archive': args.archive.name, 'sourceCommit': args.source, 'entries': found,
          'scope': 'Selected release-document bytes and packaged application version/class identity; no installed or gameplay acceptance.'}
args.result.parent.mkdir(parents=True, exist_ok=True)
args.result.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status': 'passed', 'entries': len(found), 'productVersion': '0.1.9'}))
