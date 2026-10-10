"""Run and account for independent Nightly gates without hiding failed checks."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
GENERATORS = ('fabric-26.2', 'neoforge-26.2', 'fabric-26.1.2', 'neoforge-26.1.2',
              'fabric-1.21.1', 'neoforge-1.21.1', 'fabric-1.20.1', 'neoforge-1.20.1')
GATES = {
    'sdk-windows': ('SDK regression (windows-latest)', ('harness', 'python', 'typescript', 'timeout-repeat')),
    'sdk-linux': ('SDK regression (ubuntu-24.04)', ('harness', 'python', 'typescript', 'timeout-repeat')),
    'ui': ('UI regression', ('contract', 'unit', 'build', 'playwright')),
    'core': ('Core, Javadoc and scale regression', ('java-javadoc-scale', 'doctor-schema')),
    'mcp': ('MCP regression', ('conformance', 'status', 'documentation')),
    'wrapper': ('Wrapper integrity and cache gates', ('manifest', 'cache-matrix')),
    **{f'generator-{name}': (f'Stage 9 generator golden ({name})', ('golden',)) for name in GENERATORS},
}
CONCLUSIONS = {'success': 'passed', 'failure': 'failed', 'cancelled': 'cancelled',
               'skipped': 'skipped', 'timed_out': 'failed', 'action_required': 'failed',
               'startup_failure': 'failed', 'stale': 'failed', 'neutral': 'failed'}


def identity():
    return {key: os.environ.get(env, 'local') for key, env in
            [('sha', 'GITHUB_SHA'), ('runId', 'GITHUB_RUN_ID'), ('runAttempt', 'GITHUB_RUN_ATTEMPT')]}


def save(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')


def sha256(path: Path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run_check(directory: Path, check: str, command: list[str], inject=False):
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]*', check):
        raise ValueError('Invalid check identifier')
    if command and command[0] == '--':
        command = command[1:]
    if not command:
        raise ValueError('A command is required')
    directory.mkdir(parents=True, exist_ok=True)
    log = directory/f'{check}.log'
    started = time.monotonic()
    result = {'schemaVersion': 1, **identity(), 'check': check, 'command': command,
              'startedAt': datetime.now(timezone.utc).isoformat(), 'injectedFailure': inject,
              'platform': sys.platform, 'log': log.name}
    with log.open('wb') as stream:
        if inject:
            stream.write(b'Intentional SDK failure for the Nightly isolation acceptance scenario.\n')
            code = 97
        else:
            argv = list(command)
            executable = shutil.which(argv[0]) or argv[0]
            if os.name == 'nt' and Path(executable).suffix.lower() in ('.cmd', '.bat'):
                # cmd treats an unquoted ./launcher.bat as the command '.'.
                executable = str((ROOT / executable).resolve())
                # Only repository-owned, explicit argv is accepted by this runner.
                # cmd parses the /c tail itself; passing it through Popen's argv
                # quoting would turn the executable's quotes into literal backslashes.
                shell = subprocess.list2cmdline([os.environ.get('COMSPEC', 'cmd.exe')])
                tail = subprocess.list2cmdline([executable, *argv[1:]])
                argv = f'{shell} /d /s /c "{tail}"'
            try:
                with subprocess.Popen(argv, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT) as child:
                    while chunk := child.stdout.read1(65536):
                        stream.write(chunk)
                        stream.flush()
                        sys.stdout.buffer.write(chunk)
                        sys.stdout.buffer.flush()
                    code = child.wait()
            except OSError as error:
                stream.write(f'Could not start command: {error}\n'.encode('utf-8'))
                code = 127
    result.update(exitCode=code, status='passed' if code == 0 else 'failed',
                  elapsedSeconds=round(time.monotonic()-started, 3),
                  logSha256=sha256(log), logBytes=log.stat().st_size)
    save(directory/f'{check}.json', result)
    return 0 if code == 0 else 1


def finalize(directory: Path, gate: str, job_status: str):
    _, checks = GATES[gate]
    observations = []
    for check in checks:
        path = directory/f'{check}.json'
        observations.append({'check': check, 'report': path.name if path.is_file() else None,
                             'reportSha256': sha256(path) if path.is_file() else None,
                             'reason': None if path.is_file() else
                             f'No check receipt: runner job status is {job_status}; inspect setup/earlier steps.'})
    save(directory/'gate.json', {'schemaVersion': 1, **identity(), 'gate': gate,
                               'jobStatus': job_status, 'checks': observations})


def evidence_for(directory: Path, gate: str, expected_identity: dict):
    matches = []
    for path in directory.rglob('gate.json'):
        try:
            receipt = json.loads(path.read_text(encoding='utf-8'))
            if isinstance(receipt, dict) and receipt.get('gate') == gate:
                matches.append((path, receipt))
        except (OSError, ValueError):
            continue
    if len(matches) != 1:
        return False, f'Expected one gate receipt, found {len(matches)}; inspect upload/download and job steps.'
    path, receipt = matches[0]
    if any(receipt.get(key) != value for key, value in expected_identity.items()):
        return False, 'Evidence belongs to another source/run/attempt.'
    rows = receipt.get('checks', [])
    expected_checks = GATES[gate][1]
    if (not isinstance(rows, list) or not all(isinstance(row, dict) and isinstance(row.get('check'), str) for row in rows)
            or len(rows) != len(expected_checks) or {row.get('check') for row in rows} != set(expected_checks)):
        return False, 'Required check receipts are incomplete or duplicated.'
    problems = []
    for row in rows:
        if not row.get('report'):
            problems.append(f"{row['check']}: {row.get('reason', 'receipt missing')}")
            continue
        try:
            report = path.parent/row['report']
            if report.name != f"{row['check']}.json" or report.resolve().parent != path.parent.resolve():
                raise ValueError('invalid check receipt path')
            if sha256(report) != row.get('reportSha256'):
                raise ValueError('check receipt hash mismatch')
            check = json.loads(report.read_text(encoding='utf-8'))
            if not isinstance(check, dict):
                raise ValueError('check receipt must be an object')
            if check.get('check') != row['check']:
                raise ValueError('check identifier mismatch')
            if any(check.get(key) != value for key, value in expected_identity.items()):
                raise ValueError('check source/run/attempt mismatch')
            log = path.parent/check['log']
            if log.name != f"{row['check']}.log" or log.resolve().parent != path.parent.resolve():
                raise ValueError('invalid log path')
            if log.stat().st_size != check['logBytes'] or sha256(log) != check['logSha256']:
                raise ValueError('raw log missing or modified')
            if check.get('status') != 'passed' or check.get('exitCode') != 0:
                problems.append(f"{row['check']}: failed (exit {check.get('exitCode')})")
        except (OSError, ValueError, KeyError, TypeError) as error:
            problems.append(f"{row['check']}: invalid evidence ({error})")
    return not problems, '; '.join(problems) if problems else 'All required receipts and raw-log hashes are present.'


def summarize(jobs: list[dict], directory: Path, expected_identity: dict):
    results = []
    for gate, (name, _) in GATES.items():
        matches = [job for job in jobs if isinstance(job, dict) and job.get('name') == name]
        job = matches[0] if len(matches) == 1 else {}
        conclusion = job.get('conclusion')
        status = CONCLUSIONS.get(conclusion, 'failed')
        evidence_ok, reason = evidence_for(directory, gate, expected_identity)
        if not job:
            reason = f'Expected one hosted job named {name}; found {len(matches)}. {reason}'
        if not evidence_ok and status == 'passed':
            status = 'failed'
        failed_steps = [step.get('name') for step in job.get('steps', [])
                        if step.get('conclusion') not in ('success', 'skipped', None)]
        results.append({'gate': gate, 'job': name, 'status': status,
                        'jobConclusion': conclusion, 'evidenceComplete': evidence_ok,
                        'reason': reason, 'failedSteps': failed_steps, 'jobUrl': job.get('html_url')})
    return {'schemaVersion': 1, **expected_identity,
            'status': 'passed' if all(row['status'] == 'passed' for row in results) else 'failed',
            'gates': results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest='action', required=True)
    run = actions.add_parser('run')
    run.add_argument('--output', type=Path, required=True)
    run.add_argument('--id', required=True)
    run.add_argument('--inject-failure', action='store_true')
    run.add_argument('command', nargs=argparse.REMAINDER)
    final = actions.add_parser('finalize')
    final.add_argument('--output', type=Path, required=True)
    final.add_argument('--gate', choices=GATES, required=True)
    final.add_argument('--job-status', choices=('success','failure','cancelled','skipped'), required=True)
    summary = actions.add_parser('summarize')
    summary.add_argument('--jobs', type=Path, required=True)
    summary.add_argument('--artifacts', type=Path, required=True)
    summary.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.action == 'run':
        return run_check(args.output, args.id, args.command,
                         args.inject_failure or os.environ.get('NIGHTLY_INJECT_FAILURE') == 'true')
    if args.action == 'finalize':
        finalize(args.output, args.gate, args.job_status)
        return 0
    try:
        jobs = json.loads(args.jobs.read_text(encoding='utf-8'))
        if not isinstance(jobs, list):
            raise ValueError('Hosted jobs response must be an array')
        jobs_error = None
    except (OSError, ValueError) as error:
        jobs = []
        jobs_error = f'Hosted job conclusions unavailable: {error}'
    result = summarize(jobs, args.artifacts, identity())
    if jobs_error:
        result['jobsError'] = jobs_error
        for row in result['gates']:
            row['reason'] = jobs_error + '; ' + row['reason']
    save(args.output, result)
    lines = ['| Gate | Result | Evidence / reason |', '| --- | --- | --- |']
    lines.extend(f"| {row['job']} | {row['status']} | {row['reason'].replace('|', '/')} |" for row in result['gates'])
    markdown = '\n'.join(lines)+'\n'
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a', encoding='utf-8') as stream:
            stream.write(markdown)
    print(markdown)
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
