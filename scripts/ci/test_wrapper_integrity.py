import shutil
import tempfile
from pathlib import Path
import unittest

import wrapper_integrity as integrity


class WrapperIntegrityTest(unittest.TestCase):
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
