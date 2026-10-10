"""Repeat the actual SDK operation-timeout tests, preserving initialization setup."""
import argparse
import json
from pathlib import Path
import platform
import sys
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
CASES = ('test_native_api.NativeApiTest.test_timeout_closes_process_without_retrying_mutation',
         'test_native_api.NativeApiTest.test_timeout_includes_a_blocked_pipe_write')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--count', type=int, default=100)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.count < 100:
        parser.error('The acceptance gate requires at least 100 repetitions per target case')
    sys.path.insert(0, str(ROOT/'sdk/python'))
    started = time.monotonic()
    rounds = []
    for iteration in range(args.count):
        print(f'Timeout repetition {iteration+1}/{args.count}', flush=True)
        suite = unittest.defaultTestLoader.loadTestsFromNames(CASES)
        result = unittest.TextTestRunner(stream=sys.stdout, verbosity=2).run(suite)
        rounds.append({'iteration': iteration+1, 'tests': result.testsRun,
                       'failures': len(result.failures), 'errors': len(result.errors), 'skipped': len(result.skipped)})
    passed = all(row['tests'] == len(CASES) and not any(row[key] for key in ('failures','errors','skipped')) for row in rounds)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({'schemaVersion':1, 'platform':platform.platform(),
        'python':sys.version, 'cases':CASES, 'repetitionsPerCase':args.count,
        'status':'passed' if passed else 'failed', 'elapsedSeconds':round(time.monotonic()-started,3),
        'rounds':rounds}, indent=2)+'\n', encoding='utf-8')
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
