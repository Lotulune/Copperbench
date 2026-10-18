"""Run one exact 26.3 backend/Native delivery and retain raw success or failure evidence."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import nightly

ROOT = Path(__file__).resolve().parents[2]
TARGETS = {'fabric-26.3': ('09', 'Fabric'), 'neoforge-26.3': ('10', 'NeoForge')}
sys.path.insert(0, str(ROOT / 'tools/candidate-evidence'))
from candidate_evidence import source_snapshot, source_identity


def new_run(parent, before, prefix):
    matches = [p for p in parent.glob(prefix + '*') if p.is_dir() and p not in before]
    if len(matches) != 1:
        raise ValueError(f'Expected one new {prefix} run, found {len(matches)}; old runs cannot be reused')
    return matches[0]


def bound_path(path, roots):
    path = Path(path).absolute()
    resolved = path.resolve(strict=True)
    if not any(resolved.is_relative_to(root.resolve()) for root in roots):
        raise ValueError(f'Evidence is outside this run: {path}')
    for part in [path, *path.parents]:
        if part.is_symlink() or getattr(part, 'is_junction', lambda: False)():
            raise ValueError(f'Linked evidence is not accepted: {path}')
        if part in roots:
            break
    return resolved


def collect_raw(runs, output):
    """Copy raw task reports without including Gradle caches, worlds or authority keys."""
    roots = list(runs)
    for run in runs:
        config = run / 'config.json'
        if config.is_file():
            home = json.loads(config.read_text(encoding='utf-8')).get('isolatedUserHome')
            if home:
                home = bound_path(home, [ROOT / 'build/p263-native-homes'])
                if not home.is_dir(): raise ValueError('Expected this run\'s isolated product home')
                roots.append(home / 'task-runs')
    rows = []
    def copy(path, destination):
        source = bound_path(path, roots)
        if not source.is_file():
            raise ValueError(f'Expected evidence file: {source}')
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            raise ValueError(f'Evidence destination already exists: {destination}')
        shutil.copy2(source, destination)
        rows.append({'path': destination.relative_to(output).as_posix(), 'bytes': destination.stat().st_size,
                     'sha256': nightly.sha256(destination)})
    for run in runs:
        target = output / 'raw' / run.name
        for path in sorted(run.iterdir()):
            if path.is_file() and path.suffix in {'.json', '.jsonl', '.xml', '.log', '.txt', '.jar'}:
                copy(path, target / path.name)
        workspace = run / 'workspace'
        for path in sorted((workspace / 'build/libs').glob('*.jar')):
            copy(path, target / 'workspace-jars' / path.name)
        for record in sorted((workspace / '.copperbench/task-records').glob('*.json')):
            copy(record, target / 'task-records' / record.name)
            task = json.loads(record.read_text(encoding='utf-8'))['task']
            verification = task.get('verification', {})
            for key in ('artifactPath', 'reportPath'):
                if verification.get(key) and Path(verification[key]).is_file():
                    file = Path(verification[key])
                    if file.suffix != ('.jar' if key == 'artifactPath' else '.xml'):
                        raise ValueError('Unexpected raw artifact/report type')
                    copy(file, target / 'tasks' / record.stem / file.name)
        for path in sorted((run / 'host/run').glob('gametest-results.xml')):
            copy(path, target / 'backend-host' / path.name)
        for path in sorted((run / 'host/run/mods').glob('*.jar')):
            copy(path, target / 'backend-host' / path.name)
    nightly.save(output / 'raw-inventory.json', {'schemaVersion': 1, **nightly.identity(), 'files': rows,
                                               'claim': 'raw_delivery_evidence_only'})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--generator', required=True, choices=TARGETS)
    parser.add_argument('--gradle-home', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if output.exists():
        raise ValueError('Choose a new output directory; a previous attempt cannot be overwritten')
    output.mkdir(parents=True)
    number, loader_class = TARGETS[args.generator]
    parent = ROOT / f'output/p263-{number}'
    native_parent = parent / 'native'
    old_backends = set(parent.glob('backend-*'))
    old_native = set(native_parent.glob('delivery-*'))
    def run_gradle(stage, arguments):
        arguments = ['--no-daemon', '--console=plain', *arguments]
        # A JSON argument vector avoids PowerShell -File splitting a -D/-P value
        # at a Windows drive colon. Actions uses the JDK supplied by setup-java.
        if os.name == 'nt' and os.environ.get('GITHUB_ACTIONS') != 'true':
            request = output / (stage + '-gradle-arguments.json'); nightly.save(request, arguments)
            command = ['pwsh', '-NoProfile', '-File', str(ROOT / 'scripts/ci/run_gradle_request.ps1'), '-RequestFilePath', str(request)]
        else:
            command = [str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')), *arguments]
        return nightly.run_check(output, stage, command)
    code = 1
    try:
        source = source_identity(source_snapshot(ROOT))
        if os.environ.get('GITHUB_SHA') and source['commit'] != os.environ['GITHUB_SHA']:
            raise ValueError('Checked-out source does not match this workflow run')
        if os.environ.get('GITHUB_ACTIONS') == 'true' and source['dirty']:
            raise ValueError('Cloud regression must start from the selected clean source commit')
        nightly.save(output / 'source.json', {'schemaVersion': 1, **nightly.identity(), 'source': source,
                                           'generatorId': args.generator, 'scope': 'source_native_regression'})
        # Each delivery needs a new workspace outside Gradle's declared test outputs.
        # Rerun only this task: neither cached nor up-to-date results recreate it.
        code = run_gradle('backend', ['test', '--rerun', '--tests',
            f'dev.copperbench.generator.workspace.{loader_class}263AcceptanceTest',
            f'-Dcopperbench.p263.{args.generator.split("-")[0]}NativeAcceptance=true',
            f'-Dcopperbench.gradle.user.home={args.gradle_home.resolve()}'])
        if code:
            return code
        backend = new_run(parent, old_backends, 'backend-')
        code = run_gradle('native', ['runNative263Acceptance',
            f'-PnativePythonExecutable={sys.executable}', f'-Pp263Generator={args.generator}',
            f'-Pp263GradleHome={args.gradle_home.resolve()}', f'-Pp263AcceptanceFixture={backend / "workspace"}'])
        if code:
            return code
        native = new_run(native_parent, old_native, 'delivery-')
        result = json.loads((native / 'result.json').read_text(encoding='utf-8'))
        if result['status'] != 'passed' or result['finalVerification']['environment']['generatorId'] != args.generator:
            raise ValueError('Native receipt did not pass for the selected exact generator')
        export = bound_path(result['finalExport']['exportDirectory'], [ROOT / 'build/p263-native-homes', native])
        collector = str(ROOT / 'tools/portable-evidence/copperbench_evidence.py')
        code = nightly.run_check(output, 'collect', [sys.executable, collector, 'collect', str(export), str(output / 'portable')])
        if code:
            return code
        code = nightly.run_check(output, 'offline', [sys.executable, collector, 'verify', str(output / 'portable')])
        return code
    finally:
        new_runs = sorted([p for p in parent.glob('backend-*') if p.is_dir() and p not in old_backends]
                          + [p for p in native_parent.glob('delivery-*') if p.is_dir() and p not in old_native])
        collect_raw(new_runs, output)


if __name__ == '__main__':
    sys.exit(main())
