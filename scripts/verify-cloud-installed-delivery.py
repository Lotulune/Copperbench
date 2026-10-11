#!/usr/bin/env python3
"""Install a frozen cloud candidate and run public installed SDK checks.

The workspace is an imported empty metadata fixture. No task grant is issued,
no interactive bootstrap approval is simulated, and no player actions are claimed.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import time
import traceback


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--harness', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--source-commit', required=True)
    args = parser.parse_args()
    source, harness, output = args.source.resolve(), args.harness.resolve(), args.output.resolve()
    actual_commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip()
    if actual_commit != args.source_commit or len(actual_commit) != 40:
        parser.error('The source checkout must match the full requested candidate commit')
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=source, text=True).strip():
        parser.error('Product source is not clean after packaging')
    if output.exists():
        parser.error('Preserve earlier runs; the output directory must be new')
    output.mkdir(parents=True)
    started = time.monotonic()
    windows = sys.platform == 'win32'
    export = source / 'build/export'
    layout = export / ('win64' if windows else 'linux64')
    package = next(export.glob('*Windows 64bit.exe' if windows else 'copperbench_*_amd64.deb'))
    product = Path('C:/Copperbench-Cloud-Candidate') if windows else Path('/opt/copperbench')
    if product.exists():
        parser.error('The dedicated runner must not contain an existing candidate installation')
    record = {'status': 'running', 'kind': 'cloud-installed-candidate-acceptance',
              'sourceCommit': actual_commit, 'os': platform.platform(),
              'startedAt': datetime.now(timezone.utc).isoformat(),
              'desktopAcceptance': 'unverified', 'playerBehaviorVerified': False,
              'bootstrapCreateVerified': False, 'authorityIssued': False}

    def save(name, value):
        (output / (name + '.json')).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

    def run(label, argv, *, env=None, timeout=3600):
        then = time.monotonic()
        with (output / (label + '.stdout')).open('wb') as stdout, (output / (label + '.stderr')).open('wb') as stderr:
            result = subprocess.run(argv, cwd=product if product.exists() else source, env=env,
                                    stdout=stdout, stderr=stderr, timeout=timeout, check=False)
        save(label + '-receipt', {'exitCode': result.returncode, 'elapsedSeconds': time.monotonic() - then})
        if result.returncode:
            raise RuntimeError(f'{label} failed with exit {result.returncode}; raw output is retained')

    try:
        members = {}
        selected = [layout / 'lib/copperbench.jar', layout / ('copperbench.exe' if windows else 'copperbench.sh'),
                    layout / 'scripts/verify-delivery-bundle.py',
                    layout / 'docs/ai/task-verification-and-delivery.md',
                    layout / 'ui-core/schemas/v1.0/task-verification.schema.json',
                    *sorted((layout / 'sdk').rglob('*'))]
        for path in selected:
            if path.is_file():
                members[path.relative_to(layout).as_posix()] = {'size': path.stat().st_size, 'sha256': digest(path)}
        for required in ['lib/copperbench.jar', 'sdk/python/copperbench_native.py',
                         'scripts/verify-delivery-bundle.py', 'ui-core/schemas/v1.0/task-verification.schema.json']:
            assert required in members, required
        assets = {path.name: {'size': path.stat().st_size, 'sha256': digest(path)}
                  for path in sorted(export.iterdir()) if path.is_file() and path.suffix in ('.exe', '.zip', '.deb', '.gz')}
        manifest = {'manifestVersion': '1', 'sourceCommit': actual_commit,
                    'sourceTree': subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=source, text=True).strip(),
                    'platform': 'windows-cloud' if windows else 'ubuntu-cloud',
                    'frozenAt': datetime.now(timezone.utc).isoformat(), 'assets': assets, 'members': members,
                    'status': 'frozen_before_installation', 'runUrl': os.environ.get('CANDIDATE_RUN_URL'),
                    'harnessCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=harness, text=True).strip()}
        save('candidate-manifest', manifest)
        app_sha = members['lib/copperbench.jar']['sha256']
        package_sha = assets[package.name]['sha256']
        if windows:
            run('install', [str(package), '/S', '/D=' + str(product).replace('/', '\\')], timeout=900)
        else:
            run('install', ['sudo', '-n', 'dpkg', '-i', str(package)], timeout=900)
        assert digest(product / 'lib/copperbench.jar') == app_sha, 'Installed application differs from frozen layout'
        for relative, identity in members.items():
            assert digest(product / relative) == identity['sha256'], relative
        assert digest(package) == package_sha
        save('installed-identity', {'applicationSha256': app_sha, 'candidateSha256': package_sha,
             'productRoot': str(product), 'checkedMemberCount': len(members), 'matchesFrozenCandidate': True})
        profile = output.parent / (output.name + '-profile')
        profile.mkdir()
        (profile / 'userpreferences').write_text(json.dumps({'core': {'gradle': {
            'Xmx': 1024, 'offline': False, 'useChinaMirrors': False}}}), encoding='utf-8')
        environment = os.environ.copy()
        environment['COPPERBENCH_HOME'] = str(profile)
        environment['COPPERBENCH_GRADLE_REUSE_EXTERNAL'] = 'true'
        environment['PYTHONUNBUFFERED'] = '1'
        save('environment', {'os': platform.platform(), 'python': platform.python_version(),
             'cacheCondition': 'Retained runner caches and bundled distributions; not a cold-cache claim',
             'gradleHomeOverride': environment.get('GRADLE_USER_HOME'), 'profile': str(profile),
             'gradleHeapMiB': 1024, 'mirrorsEnabled': False, 'offlinePreference': False,
             'networkCondition': 'Hosted runner defaults; no DNS or proxy changes',
             'display': environment.get('DISPLAY'), 'runUrl': environment.get('CANDIDATE_RUN_URL')})
        baseline = output / 'baseline'
        run('baseline', [sys.executable, str(harness / 'scripts/verify-agent-readiness-installed.py'),
            '--product-root', str(product), '--candidate-package', str(package),
            '--candidate-sha256', package_sha, '--application-sha256', app_sha,
            '--source-commit', actual_commit, '--workspace-folder', str(output.parent / (output.name + '-workspace')),
            '--workspace-template', str(product / 'examples/agent-native/wayfinder-bell/wayfinder_bell.mcreator'),
            '--fixture', str(source / 'examples/agent-readiness/m1-delivery'), '--output', str(baseline),
            '--cache-mode', 'retained', '--cache-policy', 'Hosted runner defaults; retained caches, isolated profile; imported empty metadata fixture'],
            env=environment)
        run('delivery', [sys.executable, str(harness / 'scripts/verify-installed-delivery.py'),
            '--product', str(product), '--baseline', str(baseline),
            '--manifest', str(output / 'candidate-manifest.json'), '--output', str(output / 'delivery')], env=environment)
        record['status'] = 'passed'
    except BaseException as error:
        record.update(status='failed', error=str(error), errorType=type(error).__name__, traceback=traceback.format_exc())
    finally:
        record.update(completedAt=datetime.now(timezone.utc).isoformat(), elapsedSeconds=time.monotonic() - started)
        save('result', record)
        print(json.dumps(record), flush=True)
    return 0 if record['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
