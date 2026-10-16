"""Prepare two installed-product versions and an isolated Windows registration namespace.

Run after exportWin64 and testClasses. Launch the emitted Java argument file with
COPPERBENCH_HOME set to profile/ and exercise the real update dialog manually.
Only the network source and registration/shortcut namespace are fixtures. No
production update endpoint, Windows trust store, or existing install is changed.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import uuid
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--version', default='0.1.10')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = Path(args.output).resolve()
    if output.exists():
        raise SystemExit('Use a new acceptance output directory')
    if not re.fullmatch(r'\d+\.\d+\.\d+', args.version):
        raise SystemExit('Expected a numeric version')
    exported = root / 'build/export/win64'
    if not (exported / 'copperbench.exe').is_file():
        raise SystemExit('Run exportWin64 first')
    output.mkdir(parents=True)
    java = root / 'jdk/jbr25_win_64/bin'
    keytool = java / 'keytool.exe'
    if not keytool.is_file():
        keytool = root / 'jdk/jdk21_win_64/bin/keytool.exe'
    for tool in [java / 'java.exe', java / 'javac.exe', keytool]:
        if not tool.is_file():
            raise SystemExit(f'Required acceptance tool is missing: {tool}')
    classes = root / 'build/classes/java/test'
    registry = 'Software\\CopperbenchUpdateAcceptance\\' + uuid.uuid4().hex
    for name in ['shortcuts', 'desktop', 'profile', 'fixtures/classes', 'fixtures/src/dev/copperbench/installer']:
        (output / name).mkdir(parents=True)
    (output / 'registry-key.txt').write_text('HKCU\\' + registry, encoding='utf-8')

    def run(program, *argv):
        subprocess.run([str(program), *map(str, argv)], check=True, cwd=root,
                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)

    # Compile the existing registrar with only its key and shortcut destinations isolated.
    template = (root / 'platform/installer/bootstrap.nsi').read_text(encoding='utf-8')
    def registrar(version, destination):
        text = '!define UPDATE_HOST\n' + template
        text = text.replace('Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Copperbench', registry)
        text = text.replace('$SMPROGRAMS', str(output / 'shortcuts')).replace('$DESKTOP', str(output / 'desktop'))
        for key, value in {'VERSION': version, 'YEAR': '2026', 'OUTPUT': destination,
                           'ICON': root / 'src/main/resources/net/mcreator/ui/res/icon.ico'}.items():
            text = text.replace('%' + key + '%', str(value))
        script = output / ('registrar-' + version + '.nsi')
        script.write_text('\ufeff' + text, encoding='utf-8')
        run(root / 'build/tools/nsis/makensis.exe', '/V2', script)

    source = (root / 'platform/installer/src/dev/copperbench/installer/PlatformInstall.java').read_text(encoding='utf-8')
    source = source.replace('\\\\Software\\\\Microsoft\\\\Windows\\\\CurrentVersion\\\\Uninstall\\\\Copperbench',
                            '\\\\' + registry.replace('\\', '\\\\'))
    isolated = output / 'fixtures/src/dev/copperbench/installer/PlatformInstall.java'
    isolated.write_text(source, encoding='utf-8')
    setup = output / 'fixture-setup.jar'
    shutil.copy2(root / 'build/installer/copperbench-setup.jar', setup)
    run(java / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-cp', setup, '-d', output / 'fixtures/classes', isolated)
    replacements = {file.relative_to(output / 'fixtures/classes').as_posix(): file
                    for file in (output / 'fixtures/classes').rglob('*.class')}
    rebuilt = output / 'fixture-setup-new.jar'
    with zipfile.ZipFile(setup) as before, zipfile.ZipFile(rebuilt, 'w') as after:
        for item in before.infolist():
            replacement = replacements.pop(item.filename, None)
            after.writestr(item, replacement.read_bytes() if replacement else before.read(item.filename))
        for name, file in replacements.items():
            after.write(file, name)
    rebuilt.replace(setup)

    base = output / 'base-payload'
    shutil.copytree(exported, base)
    with zipfile.ZipFile(base / 'lib/copperbench.jar') as jar:
        config = jar.read('mcreator.conf').decode('utf-8')
    old_version = re.search(r'^product.version=(.+)$', config, re.M).group(1).strip()
    shutil.copy2(setup, base / 'copperbench-setup.jar')
    registrar(old_version, base / 'copperbench-update-host.exe')

    def inventory(payload):
        records = []
        for file in sorted(payload.rglob('*')):
            name = file.relative_to(payload).as_posix()
            if file.is_dir(): kind, digest, size, mode = 'D', '-', 0, 493
            else:
                digest = hashlib.file_digest(file.open('rb'), 'sha256').hexdigest()
                kind, size, mode = 'F', file.stat().st_size, 420
            records.append('\t'.join([kind, base64.b64encode(name.encode()).decode(), digest, str(size), str(mode)]))
        return '\n'.join(records) + '\n'

    manifest = output / 'base-payload.tsv'
    manifest.write_text(inventory(base), encoding='utf-8')
    target = output / 'installed'
    run(java / 'java.exe', '-cp', str(classes) + os.pathsep + str(setup),
        'dev.copperbench.installer.SeedUpdateAcceptance', base, manifest, setup, target, old_version, base / 'copperbench-update-host.exe')
    (target / 'personal-note.txt').write_text('Keep user additions during upgrade.\n', encoding='utf-8')
    (output / 'workspace').mkdir()
    (output / 'workspace/workspace.mcreator').write_text('Keep unrelated workspace bytes.\n', encoding='utf-8')
    profile = output / 'profile'
    (profile / 'first-run-tour.txt').write_text('seen\n', encoding='utf-8')
    (profile / 'ui-language.txt').write_text('en\n', encoding='utf-8')
    (profile / 'userpreferences').write_text(json.dumps({'core': {'hidden': {'chinaMirrorsPrompted': True, 'uiTheme': 'default_light'}}}), encoding='utf-8')

    candidate = output / 'candidate-payload'
    shutil.copytree(base, candidate)
    original = base / 'lib/copperbench.jar'
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(candidate / 'lib/copperbench.jar', 'w') as after:
        for item in before.infolist():
            data = before.read(item.filename)
            if item.filename == 'mcreator.conf':
                data = re.sub(rb'(?m)^product.version=[^\r\n]+', ('product.version=' + args.version).encode(), data)
            elif item.filename == 'META-INF/MANIFEST.MF':
                data = re.sub(rb'(?m)^Product-Version: [^\r\n]+', ('Product-Version: ' + args.version).encode(), data)
            after.writestr(item, data)
    registrar(args.version, candidate / 'copperbench-update-host.exe')
    assets = output / 'assets'
    run(java / 'java.exe', '-cp', setup, 'dev.copperbench.installer.UpdatePackageBuilder', candidate, args.version, 'windows-x64', assets)

    password_file = output / 'tls-password.txt'
    password_file.write_text(secrets.token_urlsafe(32), encoding='utf-8')
    run(keytool, '-genkeypair', '-alias', 'localhost', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3',
        '-dname', 'CN=localhost', '-ext', 'SAN=dns:localhost,ip:127.0.0.1', '-storetype', 'PKCS12',
        '-keystore', output / 'localhost.p12', '-storepass:file', password_file, '-keypass:file', password_file, '-noprompt')
    classpath = str(target / 'lib/*') + os.pathsep + str(classes)
    arguments = ['--add-opens=java.base/java.lang=ALL-UNNAMED', '--enable-native-access=ALL-UNNAMED,jcef', '-Dcopperbench.productShell=true',
                 '-cp', classpath, 'dev.copperbench.shell.InstalledUpdateAcceptance', str(assets), str(output), args.version]
    (output / 'launch.args').write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in arguments) + '\n', encoding='utf-8')
    receipt = {'initialVersion': old_version, 'targetVersion': args.version, 'target': str(target), 'profile': str(profile),
               'registryKey': 'HKCU\\' + registry, 'source': 'loopback HTTPS with a process-scoped trust store',
               'registration': 'Production registration logic compiled with isolated registry and shortcut destinations'}
    (output / 'preparation.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(receipt))


if __name__ == '__main__':
    main()
