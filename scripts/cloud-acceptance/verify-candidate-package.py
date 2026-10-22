"""Inspect frozen package bytes against their exact Git source; never rebuild them."""
import argparse
import hashlib
import io
import json
import re
import subprocess
import tarfile
import zipfile
from pathlib import Path


def source_bytes(source, path):
    return subprocess.check_output(['git', 'show', source + ':' + path])


def product_version(source):
    if not re.fullmatch(r'[0-9a-f]{40}', source):
        raise ValueError('Expected a full source commit SHA')
    conf = source_bytes(source, 'src/main/resources/mcreator.conf').decode('utf-8')
    versions = re.findall(r'^product\.version=(\d+\.\d+\.\d+)\s*$', conf, re.MULTILINE)
    if len(versions) != 1:
        raise ValueError('Source must declare exactly one product version')
    return versions[0]


def verify_package(archive_path, source):
    version = product_version(source)
    # These routes are shipped by platform/export.gradle, including the 0.1.13
    # frozen source. Version-specific 0.1.13 notes are not in its export include
    # list; requiring later promotion notes would reject unchanged valid bytes.
    documents = ['docs/releases/current.md', 'docs/user/linux-installation.md',
                 'docs/build/development-setup.md']
    if version == '0.1.9':
        # Preserve all document checks of the original 0.1.9 consumer.
        documents += ['docs/releases/v0.1.9.md', 'docs/releases/v0.1.9-linux.md',
                      'docs/testing/maintenance-release-0.1.9.md']
    source_paths = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', source]).decode('utf-8').splitlines()
    ui_licenses = 'license/Copperbench UI Dependency Licenses.txt'
    if ui_licenses in source_paths:
        documents.append(ui_licenses)
    originals = {path: source_bytes(source, path) for path in documents}
    targets = [*documents, 'lib/copperbench.jar', 'plugins/generator-1.20.1.zip']
    found = {}

    def inspect(name, read):
        normalized = name.replace('\\', '/')
        for target in targets:
            if normalized != target and not normalized.endswith('/' + target):
                continue
            assert target not in found, 'Duplicate package entry: ' + target
            data = read()
            item = {'entry': name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
            if target.endswith('.jar'):
                with zipfile.ZipFile(io.BytesIO(data)) as jar:
                    conf = jar.read('mcreator.conf').decode('utf-8')
                    versions = [line for line in conf.splitlines() if line.startswith('product.version=')]
                    assert versions == [f'product.version={version}'], 'Packaged version differs from source'
                    item['productVersion'] = version
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
                        original = source_bytes(source, 'plugins/generator-1.20.1/' + relative)
                        assert contents == original, 'Data-pack template differs from source: ' + relative
                        entries[relative] = hashlib.sha256(contents).hexdigest()
                    assert b'gradle-9.7.0-bin.zip' in contents
                    item['sourceEntries'] = entries
            else:
                assert data == originals[target], 'Package documentation differs from source: ' + target
            found[target] = item

    if archive_path.name.endswith('.zip'):
        with zipfile.ZipFile(archive_path) as archive:
            for item in archive.infolist():
                if not item.is_dir():
                    inspect(item.filename, lambda item=item: archive.read(item))
    else:
        with tarfile.open(archive_path, 'r|gz') as archive:
            for item in archive:
                if item.isfile():
                    inspect(item.name, lambda item=item: archive.extractfile(item).read())
    assert set(found) == set(targets), 'Package is missing expected source/version entries'
    return {'status': 'passed', 'archive': archive_path.name, 'sourceCommit': source,
            'productVersion': version, 'entries': found,
            'scope': 'Selected source-bound documents and UI license notices, packaged application version, legacy fix markers and template bytes; no installed or gameplay acceptance.'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--archive', type=Path)
    parser.add_argument('--source', required=True)
    parser.add_argument('--result', type=Path)
    parser.add_argument('--print-version', action='store_true')
    args = parser.parse_args()
    if args.print_version:
        print(product_version(args.source))
        return
    if args.archive is None or args.result is None:
        parser.error('--archive and --result are required for package verification')
    result = verify_package(args.archive, args.source)
    args.result.parent.mkdir(parents=True, exist_ok=True)
    args.result.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'passed', 'entries': len(result['entries']), 'productVersion': result['productVersion']}))


if __name__ == '__main__':
    main()
