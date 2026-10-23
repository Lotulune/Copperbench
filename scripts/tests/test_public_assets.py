"""Synthetic public-asset checks; these are not published-package evidence."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'cloud-acceptance' / 'verify-public-assets.py'


class PublicAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.payload = self.root / 'payload'
        self.payload.mkdir()
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)

    def prepare(self, version, updates=False):
        conf = self.root / 'src/main/resources/mcreator.conf'
        conf.parent.mkdir(parents=True, exist_ok=True)
        conf.write_text(f'product.version={version}\n', encoding='utf-8')
        subprocess.run(['git', 'add', '.'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        '-c', 'commit.gpgsign=false', 'commit', '-qm', 'fixture'], cwd=self.root, check=True)
        self.source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root).decode().strip()
        entries = {f'Copperbench.{version}.Windows.64bit.{ext}': b'package' for ext in ['exe', 'zip', 'msix']}
        entries.update({name: b'notice' for name in ['LICENSE.txt', 'LICENSE-ADDITIONAL-TERMS.md', 'THIRD_PARTY_NOTICES.md']})
        entries['copperbench.spdx.json'] = b'{}'
        entries['product-status.json'] = json.dumps({'product': {'version': version}}).encode()
        entries['RELEASE-METADATA.json'] = json.dumps({'tag': 'v'+version, 'commit': self.source,
            'binarySource': {'commit': self.source}, 'workflowRun': '123'}).encode()
        if updates:
            entries.update({f'copperbench-update-windows-x64-{name}.zip': b'component'
                for name in ['application', 'libraries', 'jdk', 'gradle']})
            entries['copperbench-update-windows-x64.properties'] = b'version='+version.encode()
        entries['SHA256SUMS.txt'] = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n'
            for name, data in entries.items()).encode()
        for name, data in entries.items():
            (self.payload / name).write_bytes(data)
        self.release = {'tag_name': 'v'+version, 'draft': False, 'prerelease': False,
            'id': 1, 'html_url': 'https://example.invalid/release', 'published_at': '2026-10-23T00:00:00Z',
            'assets': [{'name': name, 'size': len(data), 'digest': 'sha256:'+hashlib.sha256(data).hexdigest(),
                        'browser_download_url': 'https://example.invalid/'+name} for name, data in entries.items()]}

    def run_verifier(self):
        (self.root/'release.json').write_text(json.dumps(self.release), encoding='utf-8')
        return subprocess.run(['python', str(SCRIPT), '--platform', 'windows', '--source', self.source,
            '--release', str(self.root/'release.json'), '--payload', str(self.payload),
            '--result', str(self.root/'result.json')], cwd=self.root, capture_output=True, text=True)

    def test_current_version_with_update_components(self):
        self.prepare('0.1.18', updates=True)
        result = self.run_verifier()
        self.assertEqual(result.returncode, 0, result.stderr)
        receipt = json.loads((self.root/'result.json').read_text())
        self.assertEqual(receipt['releaseTag'], 'v0.1.18')
        self.assertEqual(receipt['assetCount'], 15)

    def test_historical_release(self):
        self.prepare('0.1.9')
        result = self.run_verifier()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_wrong_release_tag_rejected(self):
        self.prepare('0.1.18')
        self.release['tag_name'] = 'v0.1.17'
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_missing_local_asset_rejected(self):
        self.prepare('0.1.18', updates=True)
        (self.payload/'copperbench-update-windows-x64-jdk.zip').unlink()
        self.assertNotEqual(self.run_verifier().returncode, 0)

    def test_modified_download_rejected(self):
        self.prepare('0.1.18')
        (self.payload/'Copperbench.0.1.18.Windows.64bit.exe').write_bytes(b'changed')
        self.assertNotEqual(self.run_verifier().returncode, 0)


if __name__ == '__main__':
    unittest.main()
