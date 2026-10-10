import contextlib
import io
import json
import shutil
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

import wrapper_integrity as integrity


class WrapperIntegrityTest(unittest.TestCase):
    def test_live_retains_distinct_payloads_for_wrappers_sharing_a_distribution(self):
        original = integrity.inventory()[0]
        rows = [original, {**original, 'jarSha256': 'another-wrapper', 'jarVersion': 'another'}]
        rejected_urls = []

        def execute(project, home, url, expected, log, *, cold, rejected=False):
            if rejected:
                rejected_urls.append(url)
            return {'status': 'passed'}

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            with patch.object(integrity, 'inventory', return_value=rows), \
                    patch.object(integrity, 'execute', side_effect=execute), \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, integrity.live(output))
            self.assertEqual(2, len(set(rejected_urls)), 'Each wrapper must retain its own payload')
            results_path = next(output.glob('cache-run-*/results.json'))
            results = json.loads(results_path.read_text(encoding='utf-8'))
            for result in results:
                if result['case'] == 'wrong-digest':
                    payload = results_path.parent/result['payloadPath']
                    self.assertEqual(result['payloadSha256'], integrity.digest(payload))

    def test_manifest_rejects_unreviewed_distribution_wrong_digest_and_changed_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root/'gradle/wrapper'
            target.mkdir(parents=True)
            original = integrity.ROOT/'gradle/wrapper'
            shutil.copy2(original/'gradle-wrapper.jar', target/'gradle-wrapper.jar')
            config = target/'gradle-wrapper.properties'
            text = (original/'gradle-wrapper.properties').read_text()
            config.write_text(text)
            self.assertEqual(1, len(integrity.inventory(root)))
            expected = integrity.properties(config)['distributionSha256Sum']
            config.write_text(text.replace(expected, '0'*64))
            with self.assertRaisesRegex(ValueError, 'incorrect distribution checksum'):
                integrity.inventory(root)
            config.write_text(text.replace('gradle-9.6.0-bin.zip', 'gradle-0.0-bin.zip'))
            with self.assertRaisesRegex(ValueError, 'Unreviewed distribution'):
                integrity.inventory(root)
            config.write_text(text)
            (target/'gradle-wrapper.jar').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'Unreviewed wrapper JAR'):
                integrity.inventory(root)


if __name__ == '__main__':
    unittest.main()
