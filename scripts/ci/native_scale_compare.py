"""Alternate exact-source native scale trials on one Windows runner; retain failures."""
from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from nightly import save, sha256

TEST = 'dev.copperbench.shell.Stage9NativeJcefScaleGateTest'
EVIDENCE = Path('build/nightly-results/stage9-native-jcef-scale.json')
XML = Path(f'build/test-results/test/TEST-{TEST}.xml')
BASELINE = '5d88dd2a2cc02c64df7c3320c9ed1fafbbe333c5'


def verify_checkout(root: Path, expected: str):
    if not re.fullmatch(r'[0-9a-f]{40}', expected):
        raise ValueError('Comparison refs must be full lowercase commit SHAs')
    actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if actual != expected:
        raise ValueError(f'Checkout mismatch: expected {expected}, observed {actual}')
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip():
        raise ValueError(f'Comparison checkout is dirty: {root}')


def command(root: Path, executable: str, args: list[str]):
    path = str(root / executable) if executable == 'gradlew.bat' else shutil.which(executable)
    if not path:
        raise ValueError(f'Executable not found: {executable}')
    # cmd /c quoting matches the existing nightly launcher, including paths with spaces.
    shell = subprocess.list2cmdline([os.environ.get('COMSPEC', 'cmd.exe')])
    return f'{shell} /d /s /c "{subprocess.list2cmdline([path, *args])}"'


def run(root: Path, source: str, directory: Path, name: str, executable: str, args: list[str], timeout: int):
    directory.mkdir(parents=True, exist_ok=True)
    log = directory / f'{name}.log'
    env = {**os.environ, 'GITHUB_SHA': source}
    started = time.monotonic()
    code = 127
    with log.open('wb') as stream:
        try:
            with subprocess.Popen(command(root, executable, args), cwd=root, env=env,
                                  stdout=stream, stderr=subprocess.STDOUT) as child:
                try:
                    code = child.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    stream.write(b'\nTrial timed out; terminating only this command process tree.\n')
                    subprocess.run(['taskkill', '/PID', str(child.pid), '/T', '/F'], stdout=stream,
                                   stderr=subprocess.STDOUT, timeout=30, check=False)
                    child.wait(timeout=30)
                    code = 124
        except (OSError, subprocess.SubprocessError) as error:
            stream.write(f'Command failure: {error}\n'.encode())
    receipt = {'source': source, 'command': [executable, *args], 'exitCode': code,
               'elapsedSeconds': round(time.monotonic() - started, 3), 'logSha256': sha256(log)}
    save(directory / f'{name}.json', receipt)
    print(json.dumps({'step': directory.name + '/' + name, **receipt}), flush=True)
    return code


def validate_trial(directory: Path, source: str, code: int, candidate: bool):
    errors = []
    try:
        data = json.loads((directory / 'scale.json').read_text(encoding='utf-8'))
        xml = ET.parse(directory / 'test.xml').getroot()
        if xml.get('tests') != '1' or xml.get('skipped', '0') != '0':
            errors.append('Native test did not execute exactly once without skipping')
        if data.get('environment', {}).get('GITHUB_SHA') != source:
            errors.append('Evidence source does not match checkout')
        if data.get('sampleCount') != 20 or data.get('p95TargetMillis') != 300.0 or data.get('references') != 10000:
            errors.append('Sample/threshold/fixture contract changed')
        for metric in ('elementFilter', 'referenceRefresh'):
            values = data.get(metric + 'SamplesMillis', [])
            if len(values) != 20 or not all(type(v) in (int, float) and math.isfinite(v) and v >= 0 for v in values):
                errors.append(f'Incomplete or invalid {metric} samples')
            elif not math.isclose(sorted(values)[18], data.get(metric + 'P95Millis', -1), abs_tol=1e-6):
                errors.append(f'Inconsistent {metric} P95')
        if candidate:
            if code != 0 or data.get('passed') is not True or xml.get('failures', '0') != '0' or xml.get('errors', '0') != '0':
                errors.append('Candidate native gate failed')
        elif code != 0 and (data.get('passed') is not False or xml.get('failures') != '1' or xml.get('errors', '0') != '0'
                            or data.get('failure', {}).get('type') != 'org.opentest4j.AssertionFailedError'
                            or 'exceeded' not in data.get('failure', {}).get('message', '')):
            errors.append('Baseline failed for a reason other than a measured gate assertion')
        elif code == 0 and (data.get('passed') is not True or xml.get('failures', '0') != '0' or xml.get('errors', '0') != '0'):
            errors.append('Baseline exit/evidence mismatch')
        return {'valid': not errors, 'errors': errors, 'exitCode': code,
                'referenceRefreshP95Millis': data.get('referenceRefreshP95Millis'),
                'elementFilterP95Millis': data.get('elementFilterP95Millis'),
                'gatePassed': data.get('passed'), 'evidenceSha256': sha256(directory / 'scale.json')}
    except (OSError, ValueError, TypeError, AttributeError, ET.ParseError) as error:
        return {'valid': False, 'errors': [f'Missing/invalid trial evidence: {error}'], 'exitCode': code}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--baseline-sha', default=BASELINE)
    parser.add_argument('--candidate-sha', required=True)
    args = parser.parse_args()
    if os.name != 'nt':
        parser.error('This comparison requires Windows with JBR/JCEF; Linux is not acceptance')
    output = args.output.resolve()
    if output.exists():
        parser.error('Use a fresh output directory so prior trials cannot be reused')
    variants = [('baseline', args.baseline.resolve(), args.baseline_sha),
                ('candidate', args.candidate.resolve(), args.candidate_sha)]
    for _, root, source in variants:
        verify_checkout(root, source)
        if output.is_relative_to(root):
            parser.error('Evidence must be outside both checkouts')
    summary = {'workflowSha': os.environ.get('GITHUB_SHA'), 'runId': os.environ.get('GITHUB_RUN_ID'),
               'runAttempt': os.environ.get('GITHUB_RUN_ATTEMPT'), 'runnerName': os.environ.get('RUNNER_NAME'),
               'baselineSha': args.baseline_sha, 'candidateSha': args.candidate_sha,
               'order': ['baseline', 'candidate'] * 3, 'trials': [], 'passed': False}
    save(output / 'comparison.json', summary)
    # Build both variants before measurement. Every trial starts a fresh test JVM;
    # --rerun affects only test, avoiding asymmetric recompilation during the A/B sequence.
    for label, root, source in variants:
        directory = output / f'prepare-{label}'
        if run(root, source, directory, 'npm', 'npm.cmd', ['ci', '--prefix', 'ui-shell'], 600) != 0:
            return 1
        if run(root, source, directory, 'compile', 'gradlew.bat',
               ['--no-daemon', '--no-build-cache', 'testClasses'], 1500) != 0:
            return 1
    for iteration in range(1, 4):
        for label, root, source in variants:
            directory = output / f'{iteration}-{label}'
            # Never accept a stale sample or test XML from another attempt.
            for relative in (EVIDENCE, XML):
                (root / relative).unlink(missing_ok=True)
            code = run(root, source, directory, 'test', 'gradlew.bat',
                       ['--no-daemon', '--no-build-cache', 'test', '--rerun', '--tests', TEST,
                        '-Dcopperbench.stage9.scale=true', '-Dcopperbench.uiWindowTimeoutSeconds=20'], 600)
            for relative, name in ((EVIDENCE, 'scale.json'), (XML, 'test.xml')):
                if (root / relative).is_file():
                    shutil.copy2(root / relative, directory / name)
            trial = {'iteration': iteration, 'variant': label, 'source': source,
                     **validate_trial(directory, source, code, label == 'candidate')}
            summary['trials'].append(trial)
            save(output / 'comparison.json', summary)
    summary['passed'] = len(summary['trials']) == 6 and all(trial['valid'] for trial in summary['trials'])
    save(output / 'comparison.json', summary)
    return 0 if summary['passed'] else 1


if __name__ == '__main__':
    sys.exit(main())
