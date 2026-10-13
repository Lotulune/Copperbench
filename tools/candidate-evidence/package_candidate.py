"""Build and freeze one platform's G1 or G2 qualification inputs using existing packaging tasks."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import sys
import candidate_evidence as candidate

ROOT = Path(__file__).resolve().parents[2]


def sample_inventory(repository, product, loaders, level='G1'):
    output = repository / 'build/p263-samples'; output.mkdir(parents=True, exist_ok=True)
    for loader in loaders:
        generator = loader + '-26.3'; source = product / ('examples/camp-kit-native' if level == 'G1' else 'examples/camp-kit-scoped') / generator
        files = []
        for path in sorted(source.rglob('*')):
            if path.is_file():
                path = candidate.member(source, path.relative_to(source).as_posix())
                files.append({'path': path.relative_to(source).as_posix(), 'bytes': path.stat().st_size, 'sha256': candidate.sha(path)})
        candidate.require(files and (source / 'copperbench-tests.json').is_file(), 'Packaged Camp Kit sample is incomplete')
        candidate.write(output / (generator + '.json'), {'schemaVersion': '1', 'generatorId': generator,
                        'requiredCaseManifestId': 'camp-kit-native-v1' if level == 'G1' else 'camp-kit-scoped-v1', 'files': files})


def plan(repository, platform, loaders, level='G1'):
    candidate.require(level in ('G1', 'G2'), 'Unknown qualification level')
    conf = dict(line.split('=', 1) for line in (repository / 'src/main/resources/mcreator.conf').read_text(encoding='utf-8').splitlines() if '=' in line)
    version = conf['product.version']; name = conf['product.name']
    product = 'build/export/' + ('win64' if platform == 'windows' else 'linux64')
    launcher = 'copperbench.exe' if platform == 'windows' else 'copperbench.sh'
    package = f'{name} {version} Windows 64bit.exe' if platform == 'windows' else f'copperbench_{version.replace(" ", "~")}_amd64.deb'
    portable_package = f'{name} {version} Windows 64bit.zip' if platform == 'windows' else f'{name} {version} Linux x86_64.tar.gz'
    # Actions uses its configured JDK. Local Windows uses the established external wrapper.
    gradle = (['pwsh', '-NoProfile', '-File', 'scripts/run-gradle-external.ps1']
              if platform == 'windows' and os.environ.get('GITHUB_ACTIONS') != 'true'
              else (['cmd.exe', '/d', '/c', 'gradlew.bat'] if platform == 'windows' else ['bash', './gradlew']))
    tasks = ['buildInstallerWin64', 'exportWindowsZip'] if platform == 'windows' else ['exportLinuxCandidate']
    commands = [[*gradle, '--no-daemon', '--console=plain', *tasks],
                [sys.executable, 'tools/candidate-evidence/package_candidate.py', '--inventory-only', '--platform', platform, '--level', level,
                 *[arg for loader in loaders for arg in ['--loader', loader]]]]
    assets = [
        {'id': platform + '-application', 'role': 'application', 'platform': platform, 'path': product + '/lib/copperbench.jar'},
        {'id': platform + '-launcher', 'role': 'launcher', 'platform': platform, 'path': product + '/' + launcher},
        {'id': platform + '-package', 'role': 'package', 'platform': platform, 'path': 'build/export/' + package},
        {'id': platform + '-portable-package', 'role': 'portable_package', 'platform': platform, 'path': 'build/export/' + portable_package},
        {'id': 'python-native', 'role': 'python_native', 'path': product + '/sdk/python/copperbench_native.py'},
        {'id': 'python-mcp', 'role': 'python_mcp', 'path': product + '/sdk/python/copperbench.py'},
        {'id': 'typescript', 'role': 'typescript', 'path': product + '/sdk/typescript/copperbench.ts'},
    ]
    for loader in loaders:
        generator = loader + '-26.3'
        assets.extend([
            {'id': generator + '-generator', 'role': 'generator', 'generatorId': generator, 'path': product + '/plugins/generator-' + generator + '.zip'},
            {'id': generator + '-sample', 'role': 'sample', 'generatorId': generator, 'path': 'build/p263-samples/' + generator + '.json'},
        ])
    desktops = ['windows'] if platform == 'windows' else (['ubuntu-xorg', 'ubuntu-wayland'] if level == 'G1' else ['ubuntu'])
    scopes = [f'{loader}:{level}:{desktop}' for loader in loaders for desktop in desktops]
    availability = {f'{loader}:{level}:{desktop}': 'unavailable' for loader in candidate.LOADERS
                    for level, targets in [('G1', ['windows', 'ubuntu-xorg', 'ubuntu-wayland']), ('G2', ['windows', 'ubuntu'])]
                    for desktop in targets}
    availability.update(dict.fromkeys(scopes, 'ready'))
    return {'schemaVersion': '1', 'commands': commands, 'scopes': scopes, 'availability': availability, 'assets': assets}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', choices=['windows', 'ubuntu'], required=True)
    parser.add_argument('--loader', choices=candidate.LOADERS, action='append', required=True)
    parser.add_argument('--level', choices=['G1', 'G2'], default='G1')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--inventory-only', action='store_true')
    args = parser.parse_args(); loaders = sorted(set(args.loader))
    if args.inventory_only:
        product = ROOT / 'build/export' / ('win64' if args.platform == 'windows' else 'linux64')
        sample_inventory(ROOT, product, loaders, args.level); return 0
    candidate.require(args.output is not None, 'An output directory is required')
    candidate.require((os.name == 'nt') == (args.platform == 'windows'), 'Build on the selected target operating system')
    output = args.output.resolve(); candidate.detached_output(ROOT, output)
    candidate.require(not output.exists(), 'Choose a new candidate output directory'); output.mkdir(parents=True)
    candidate.write(output / 'build-plan.json', plan(ROOT, args.platform, loaders, args.level))
    candidate.build_candidate(ROOT, output / 'build-plan.json', output / 'build')
    result = candidate.freeze(ROOT, output / 'build/freeze-inputs.json', output / 'frozen')
    candidate.write(output / 'cells.json', candidate.evaluate(output / 'frozen', []))
    print(json.dumps({'candidateId': result['candidateId'], 'status': result['status'], 'path': str(output / 'frozen')}))
    return 0


if __name__ == '__main__': sys.exit(main())
