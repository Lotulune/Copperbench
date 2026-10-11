"""Input boundary checks; these synthetic files are not installation evidence."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('cloud_installed', Path(__file__).parents[1] / 'verify-cloud-installed-delivery.py')
cloud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cloud)


class FrozenCandidateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.package = self.root / 'candidate.deb'
        self.package.write_bytes(b'synthetic archive bytes')
        self.path = self.root / 'manifest.json'
        self.manifest = {'manifestVersion': '1', 'sourceCommit': 'a' * 40, 'platform': 'ubuntu-cloud',
            'status': 'frozen_before_installation',
            'assets': {self.package.name: {'size': self.package.stat().st_size, 'sha256': cloud.digest(self.package)}},
            'members': {'lib/copperbench.jar': {'size': 1, 'sha256': 'b' * 64}}}
        self.write()

    def write(self):
        self.path.write_text(json.dumps(self.manifest), encoding='utf-8')

    def read(self):
        return cloud.reused_candidate(self.path, self.root, 'a' * 40, False)

    def test_reuse_checks_actual_bytes_and_retains_original_manifest(self):
        self.assertEqual(self.manifest, self.read())
        self.package.write_bytes(b'altered archive bytes')
        with self.assertRaisesRegex(ValueError, 'package changed'):
            self.read()

    def test_reuse_rejects_different_source_platform_and_escaping_members(self):
        for key, value in [('sourceCommit', 'c' * 40), ('platform', 'windows-cloud')]:
            original = self.manifest[key]
            self.manifest[key] = value
            self.write()
            with self.assertRaisesRegex(ValueError, 'differs'):
                self.read()
            self.manifest[key] = original
        self.manifest['members']['../outside.jar'] = {'size': 1, 'sha256': 'd' * 64}
        self.write()
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            self.read()


if __name__ == '__main__':
    unittest.main()
