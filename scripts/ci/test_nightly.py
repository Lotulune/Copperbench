"""Negative evidence accounting tests, independent of hosted Actions availability."""
import contextlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import nightly


class NightlyEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.identity = {'sha': 'source', 'runId': '100', 'runAttempt': '2'}
        self.env = patch.dict(os.environ, {'GITHUB_SHA': 'source', 'GITHUB_RUN_ID': '100', 'GITHUB_RUN_ATTEMPT': '2'})
        self.env.start()
        self.addCleanup(self.env.stop)

    def populate(self, failed=None):
        jobs = []
        for gate, (name, checks) in nightly.GATES.items():
            directory = self.root/gate
            for check in checks:
                log = directory/(check+'.log')
                directory.mkdir(exist_ok=True)
                log.write_bytes(b'actual log bytes\r\n')
                ok = gate != failed
                nightly.save(directory/(check+'.json'), {'schemaVersion': 1, **self.identity, 'check': check,
                    'status': 'passed' if ok else 'failed', 'exitCode': 0 if ok else 97,
                    'log': log.name, 'logBytes': log.stat().st_size, 'logSha256': nightly.sha256(log)})
            nightly.finalize(directory, gate, 'failure' if gate == failed else 'success')
            jobs.append({'name': name, 'conclusion': 'failure' if gate == failed else 'success'})
        return jobs

    def test_failed_sdk_keeps_every_independent_and_matrix_result(self):
        jobs = self.populate(failed='sdk-windows')
        result = nightly.summarize(jobs, self.root, self.identity)
        self.assertEqual('failed', result['status'])
        states = {row['gate']: row['status'] for row in result['gates']}
        self.assertEqual('failed', states.pop('sdk-windows'))
        self.assertEqual(13, len(states))
        self.assertEqual({'passed'}, set(states.values()))

    def test_success_requires_complete_current_artifacts(self):
        jobs = self.populate()
        self.assertEqual('passed', nightly.summarize(jobs, self.root, self.identity)['status'])
        (self.root/'ui'/'build.log').write_bytes(b'changed')
        result = nightly.summarize(jobs, self.root, self.identity)
        ui = next(row for row in result['gates'] if row['gate'] == 'ui')
        self.assertEqual('failed', ui['status'])
        self.assertIn('raw log missing or modified', ui['reason'])
        self.assertFalse(nightly.evidence_for(self.root, 'core', {**self.identity, 'runAttempt':'3'})[0])

    def test_missing_skipped_and_cancelled_have_explicit_reasons(self):
        jobs = self.populate()
        for gate, conclusion in [('sdk-windows','cancelled'),('sdk-linux','skipped')]:
            next(job for job in jobs if job['name'] == nightly.GATES[gate][0])['conclusion'] = conclusion
            (self.root/gate/'gate.json').unlink()
        result = nightly.summarize(jobs, self.root, self.identity)
        self.assertEqual(['cancelled', 'skipped'], [row['status'] for row in result['gates'][:2]])
        self.assertTrue(all('found 0' in row['reason'] for row in result['gates'][:2]))

    def test_invalid_receipt_identity_paths_and_types_are_not_success(self):
        self.populate()
        gate = self.root/'ui'/'gate.json'
        original = json.loads(gate.read_text())
        for altered in [
            {**original, 'checks':[{'check': []}]},
            {**original, 'checks':[{'check': key, 'report': 42} for key in nightly.GATES['ui'][1]]},
            {**original, 'checks':[{'check': key, 'report': '../'+key+'.json'} for key in nightly.GATES['ui'][1]]},
            {**original, 'sha':'another-source'}
        ]:
            nightly.save(gate, altered)
            self.assertFalse(nightly.evidence_for(self.root, 'ui', self.identity)[0])
        nightly.save(gate, original)
        report = self.root/'ui'/'build.json'
        value = json.loads(report.read_text())
        value['check'] = 'other'
        nightly.save(report, value)
        original['checks'][2]['reportSha256'] = nightly.sha256(report)
        nightly.save(gate, original)
        self.assertIn('check identifier mismatch', nightly.evidence_for(self.root, 'ui', self.identity)[1])

    def test_dispatch_fault_produces_a_real_failed_receipt_and_raw_log(self):
        result = subprocess.run([sys.executable, str(Path(nightly.__file__)), 'run', '--output', str(self.root),
                                 '--id', 'python', '--', sys.executable, '-c', 'raise RuntimeError("must not run")'],
                                env={**os.environ, 'NIGHTLY_INJECT_FAILURE':'true'}, capture_output=True)
        self.assertNotEqual(0, result.returncode)
        report = json.loads((self.root/'python.json').read_text())
        self.assertTrue(report['injectedFailure'])
        self.assertEqual(97, report['exitCode'])
        self.assertIn(b'Intentional SDK failure', (self.root/'python.log').read_bytes())

    @unittest.skipUnless(os.name == 'nt', 'Windows batch launch regression')
    def test_windows_launcher_with_spaces_retains_exit_code_and_quoted_argument(self):
        launcher = self.root/'launcher with spaces.cmd'
        launcher.write_text('@echo off\necho %~1\nexit /b 7\n', encoding='utf-8')
        self.assertEqual(1, nightly.run_check(self.root, 'batch', [str(launcher), 'argument with spaces']))
        report = json.loads((self.root/'batch.json').read_text())
        self.assertEqual(7, report['exitCode'])
        self.assertIn(b'argument with spaces', (self.root/'batch.log').read_bytes())

    def test_unavailable_hosted_job_api_still_writes_failure_summary(self):
        target = self.root/'summary.json'
        result = subprocess.run([sys.executable, str(Path(nightly.__file__)), 'summarize',
            '--jobs', str(self.root/'missing.json'), '--artifacts', str(self.root), '--output', str(target)],
            capture_output=True, env={**os.environ, 'GITHUB_STEP_SUMMARY': str(self.root/'summary.md')})
        self.assertNotEqual(0, result.returncode)
        summary = json.loads(target.read_text())
        self.assertEqual(14, len(summary['gates']))
        self.assertIn('unavailable', summary['jobsError'])
        self.assertTrue(all(row['status'] == 'failed' for row in summary['gates']))


if __name__ == '__main__':
    unittest.main()
