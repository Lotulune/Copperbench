"""Synthetic archive regression tests; these are not release acceptance receipts."""
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / 'cloud-acceptance' / 'verify-candidate-package.py'
spec = importlib.util.spec_from_file_location('candidate_package', SCRIPT)
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)
SOURCE = '1' * 40
PLUGIN = 'plugins/generator-1.20.1/'
GENERATOR = 'datapack-1.20.1/generator.yaml'
WRAPPER = 'datapack-1.20.1/workspacebase/gradle/wrapper/gradle-wrapper.properties'


def zip_bytes(entries):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name, contents in entries.items():
            archive.writestr(name, contents)
    return buffer.getvalue()


class CandidatePackageTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.prepare('0.1.13', linux_notes=False)
        mock = patch.object(verifier.subprocess, 'check_output', side_effect=self.git)
        mock.start()
        self.addCleanup(mock.stop)

    def prepare(self, version, linux_notes=True):
        self.version = version
        self.source = {
            'docs/releases/current.md': b'frozen download index',
            'docs/user/linux-installation.md': b'frozen Linux instructions',
            'docs/build/development-setup.md': b'frozen development instructions',
            'src/main/resources/mcreator.conf': f'product.version={version}\n'.encode(),
            f'docs/releases/v{version}.md': b'release notes from frozen source',
            f'docs/testing/maintenance-release-{version}.md': b'source release report',
            PLUGIN + GENERATOR: b'generator',
            PLUGIN + WRAPPER: b'distributionUrl=https://services.gradle.org/distributions/gradle-9.7.0-bin.zip',
        }
        if linux_notes:
            self.source[f'docs/releases/v{version}-linux.md'] = b'Linux notes in source'
        self.jar = {
            'mcreator.conf': self.source['src/main/resources/mcreator.conf'],
            'dev/copperbench/generator/GradleArchiveOutput$Request.class': b'--no-configuration-cache toRealPath',
            'dev/copperbench/generator/datapack/DataPackWorkspaceTaskGateway.class': b'gradle-9.7.0-bin.zip',
        }
        self.entries = {name: data for name, data in self.source.items() if name.startswith('docs/')}
        self.entries['lib/copperbench.jar'] = zip_bytes(self.jar)
        self.entries['plugins/generator-1.20.1.zip'] = zip_bytes({
            GENERATOR: self.source[PLUGIN + GENERATOR], WRAPPER: self.source[PLUGIN + WRAPPER]})

    def git(self, command):
        if command[:3] == ['git', 'ls-tree', '-r']:
            self.assertEqual(command[-1], SOURCE)
            return '\n'.join(self.source).encode()
        self.assertEqual(command[:2], ['git', 'show'])
        source, name = command[2].split(':', 1)
        self.assertEqual(source, SOURCE)
        return self.source[name]

    def archive(self, extra=(), kind='tar.gz'):
        path = self.root / ('candidate.' + kind)
        entries = list(self.entries.items()) + list(extra)
        if kind == 'zip':
            path.write_bytes(zip_bytes(dict(entries)))
        else:
            with tarfile.open(path, 'w:gz') as archive:
                for name, data in entries:
                    entry = tarfile.TarInfo('copperbench/' + name)
                    entry.size = len(data)
                    archive.addfile(entry, io.BytesIO(data))
        return path

    def test_current_version_without_later_linux_promotion_notes(self):
        result = verifier.verify_package(self.archive(), SOURCE)
        self.assertEqual(result['productVersion'], '0.1.13')
        self.assertEqual(len(result['entries']), 5)

    def test_historical_linux_notes_remain_required(self):
        self.prepare('0.1.9')
        result = verifier.verify_package(self.archive(), SOURCE)
        self.assertEqual(result['productVersion'], '0.1.9')
        del self.entries['docs/releases/v0.1.9-linux.md']
        with self.assertRaisesRegex(AssertionError, 'missing expected'):
            verifier.verify_package(self.archive(), SOURCE)

    def test_zip_archive_supported(self):
        self.assertEqual(verifier.verify_package(self.archive(kind='zip'), SOURCE)['status'], 'passed')

    def test_wrong_or_duplicate_packaged_version_rejected(self):
        for conf in [b'product.version=0.1.9', b'product.version=0.1.13\nproduct.version=0.1.9']:
            with self.subTest(conf=conf):
                self.jar['mcreator.conf'] = conf
                self.entries['lib/copperbench.jar'] = zip_bytes(self.jar)
                with self.assertRaisesRegex(AssertionError, 'version differs'):
                    verifier.verify_package(self.archive(), SOURCE)

    def test_altered_document_rejected(self):
        self.entries['docs/releases/current.md'] = b'changed after freezing'
        with self.assertRaisesRegex(AssertionError, 'documentation differs'):
            verifier.verify_package(self.archive(), SOURCE)

    def test_missing_document_rejected(self):
        del self.entries['docs/releases/current.md']
        with self.assertRaisesRegex(AssertionError, 'missing expected'):
            verifier.verify_package(self.archive(), SOURCE)

    def test_duplicate_entry_rejected(self):
        path = self.archive([('docs/releases/current.md', b'replacement')])
        with self.assertRaisesRegex(AssertionError, 'Duplicate package entry'):
            verifier.verify_package(path, SOURCE)

    def test_legacy_fix_markers_still_required(self):
        self.jar['dev/copperbench/generator/GradleArchiveOutput$Request.class'] = b'toRealPath'
        self.entries['lib/copperbench.jar'] = zip_bytes(self.jar)
        with self.assertRaises(AssertionError):
            verifier.verify_package(self.archive(), SOURCE)

    def test_template_must_match_source(self):
        self.entries['plugins/generator-1.20.1.zip'] = zip_bytes({GENERATOR: b'changed', WRAPPER: self.source[PLUGIN + WRAPPER]})
        with self.assertRaisesRegex(AssertionError, 'template differs'):
            verifier.verify_package(self.archive(), SOURCE)

    def test_source_ref_must_be_full_commit(self):
        for source in ['main', 'HEAD', SOURCE[:7], '--help']:
            with self.subTest(source=source), self.assertRaises(ValueError):
                verifier.product_version(source)

    def test_duplicate_source_version_rejected(self):
        self.source['src/main/resources/mcreator.conf'] += b'product.version=0.1.9\n'
        with self.assertRaises(ValueError):
            verifier.product_version(SOURCE)


if __name__ == '__main__':
    unittest.main()
