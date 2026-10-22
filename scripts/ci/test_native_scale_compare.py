"""Fail-closed evidence checks; these do not simulate Windows/JCEF performance."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import native_scale_compare as compare


class NativeScaleCompareTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sha = 'a' * 40
        self.data = {'environment': {'GITHUB_SHA': self.sha}, 'sampleCount': 20,
                     'p95TargetMillis': 300.0, 'references': 10000, 'passed': True}
        for metric in ('elementFilter', 'referenceRefresh'):
            self.data[metric + 'SamplesMillis'] = [100.0] * 20
            self.data[metric + 'P95Millis'] = 100.0
        self.xml = '<testsuite tests="1" skipped="0" failures="0" errors="0"/>'

    def validate(self, code=0, candidate=True):
        (self.root / 'scale.json').write_text(json.dumps(self.data))
        (self.root / 'test.xml').write_text(self.xml)
        return compare.validate_trial(self.root, self.sha, code, candidate)

    def test_complete_candidate_requires_all_native_assertions_to_pass(self):
        self.assertTrue(self.validate()['valid'])
        self.assertFalse(self.validate(code=1)['valid'])
        self.data['passed'] = False
        self.assertFalse(self.validate()['valid'])

    def test_baseline_measured_failure_is_retained_not_misreported_as_pass(self):
        self.data.update(passed=False, failure={'type': 'org.opentest4j.AssertionFailedError',
                                               'message': 'reference P95 exceeded 300 ms: 400.9'})
        self.data['referenceRefreshSamplesMillis'] = [400.9] * 20
        self.data['referenceRefreshP95Millis'] = 400.9
        self.xml = '<testsuite tests="1" skipped="0" failures="1" errors="0"/>'
        result = self.validate(code=1, candidate=False)
        self.assertTrue(result['valid'])
        self.assertFalse(result['gatePassed'])
        self.assertEqual(400.9, result['referenceRefreshP95Millis'])
        self.assertFalse(self.validate(code=1, candidate=True)['valid'])

    def test_baseline_infrastructure_failure_is_not_an_accepted_comparison(self):
        self.data.update(passed=False, failure={'type': 'java.io.IOException', 'message': 'browser failed'})
        self.assertFalse(self.validate(code=1, candidate=False)['valid'])

    def test_partial_nan_or_inconsistent_samples_are_rejected(self):
        for values in ([100] * 19, [float('nan')] * 20, [True] * 20, [-1] * 20, [200] * 20):
            with self.subTest(values=values):
                self.data['referenceRefreshSamplesMillis'] = values
                self.assertFalse(self.validate()['valid'])

    def test_changed_source_threshold_and_skipped_test_are_rejected(self):
        self.data['environment']['GITHUB_SHA'] = 'b' * 40
        self.assertFalse(self.validate()['valid'])
        self.data['environment']['GITHUB_SHA'] = self.sha
        self.data['p95TargetMillis'] = 500
        self.assertFalse(self.validate()['valid'])
        self.data['p95TargetMillis'] = 300
        self.xml = '<testsuite tests="1" skipped="1" failures="0" errors="0"/>'
        self.assertFalse(self.validate()['valid'])

    def test_missing_evidence_does_not_pass(self):
        self.assertFalse(compare.validate_trial(self.root, self.sha, 0, True)['valid'])

    def test_refs_are_full_shas_and_worktrees_must_match_and_be_clean(self):
        with self.assertRaises(ValueError):
            compare.verify_checkout(self.root, 'main')
        with patch.object(compare.subprocess, 'check_output', side_effect=[self.sha + '\n', '']):
            compare.verify_checkout(self.root, self.sha)
        for responses in ([('b' * 40) + '\n'], [self.sha + '\n', ' M source.java']):
            with patch.object(compare.subprocess, 'check_output', side_effect=responses):
                with self.assertRaises(ValueError):
                    compare.verify_checkout(self.root, self.sha)


if __name__ == '__main__':
    unittest.main()
