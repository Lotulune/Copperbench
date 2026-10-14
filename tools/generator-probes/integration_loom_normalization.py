"""Real Gradle regressions for the exact Fabric 26.3 local-JAR boundary.

Run explicitly after Java/Gradle setup; ordinary probe unit tests stay offline and
do not start Gradle. Logs and fixture bytes remain in build/loom-normalization/.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import uuid
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'plugins/generator-fabric-26.3/fabric-26.3/workspacebase/gradle/normalize-local-minecraft.gradle'
MODULE = 'minecraft-merged-7e9a32a5b8'
ARTIFACT = MODULE + '-26.3.jar'
RELATIVE_JAR = Path('.gradle/loom-cache/minecraftMaven/net/minecraft') / MODULE / '26.3' / ARTIFACT
# Fixed ZIP-format golden: UTF-8, STORED, 1980-01-02, a.txt=alpha, z.txt=zeta.
# Written independently from the Gradle normalizer; no runtime golden generation.
CANONICAL = bytes.fromhex(
    '504b03040a0000080000000022006a39e0d0050000000500000005000000612e747874616c706861'
    '504b03040a000008000000002200137b3c1a0400000004000000050000007a2e7478747a657461'
    '504b01020a000a0000080000000022006a39e0d00500000005000000050000000000000000000000000000000000612e747874'
    '504b01020a000a000008000000002200137b3c1a04000000040000000500000000000000000000000000280000007a2e747874'
    '504b05060000000002000200660000004f0000000000')
EXPECTED = '1f40212dc0e72e1a44baad16206cb56a747b93d57e63e3740baf940565ac4a53'


class LoomNormalizationIntegration(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.output = ROOT / 'build/loom-normalization' / uuid.uuid4().hex[:8]
        cls.output.mkdir(parents=True)
        # The exact Maven coordinate is long; keep fixtures below Windows MAX_PATH.
        cls.fixtures = Path(tempfile.mkdtemp(prefix='cb-loom-'))
        (cls.output / 'fixture-root.txt').write_text(str(cls.fixtures), encoding='utf-8')
        print('Loom normalization evidence:', cls.output, flush=True)

    @classmethod
    def tearDownClass(cls):
        shutil.make_archive(str(cls.output / 'fixtures'), 'zip', cls.fixtures)

    def project(self, label, entries=None):
        target = self.fixtures / label
        target.mkdir()
        jar = target / RELATIVE_JAR
        jar.parent.mkdir(parents=True)
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(jar, 'w') as archive:
                for name, data in (entries if entries is not None else [('z.txt', b'zeta'), ('a.txt', b'alpha')]):
                    info = zipfile.ZipInfo(name, (2026, 10, 13, 12, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.comment = b'non-semantic container metadata'
                    archive.writestr(info, data)
        pom = jar.with_suffix('.pom')
        pom.write_text('<project><modelVersion>4.0.0</modelVersion><groupId>net.minecraft</groupId>'
                       f'<artifactId>{MODULE}</artifactId><version>26.3</version></project>', encoding='utf-8')
        (target / 'gradle').mkdir()
        shutil.copy2(SCRIPT, target / 'gradle/normalize-local-minecraft.gradle')
        (target / 'gradle/verification-metadata.xml').write_text(
            '<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">'
            '<configuration><verify-metadata>true</verify-metadata><verify-signatures>false</verify-signatures></configuration>'
            f'<components><component group="net.minecraft" name="{MODULE}" version="26.3">'
            f'<artifact name="{ARTIFACT}"><sha256 value="{EXPECTED}"/></artifact>'
            f'<artifact name="{pom.name}"><sha256 value="{hashlib.sha256(pom.read_bytes()).hexdigest()}"/></artifact>'
            '</component></components></verification-metadata>', encoding='utf-8')
        (target / 'settings.gradle').write_text("rootProject.name='local-jar-regression'\n", encoding='utf-8')
        (target / 'canonical.bin').write_bytes(CANONICAL)
        (target / 'build.gradle').write_text(
            "apply from: 'gradle/normalize-local-minecraft.gradle'\n"
            "repositories { maven { url = uri('.gradle/loom-cache/minecraftMaven') } }\n"
            "configurations { probe }\n"
            f"dependencies {{ probe 'net.minecraft:{MODULE}:26.3' }}\n"
            "tasks.register('verifyCanonical') { doLast {\n"
            f"  if (providers.gradleProperty('tamper').isPresent()) file('{RELATIVE_JAR.as_posix()}').append('tampered')\n"
            "  assert configurations.probe.singleFile.bytes == file('canonical.bin').bytes\n"
            "} }\n", encoding='utf-8')
        return target, jar

    def execute(self, project, label='run', *extra):
        args = ['-p', str(project), 'verifyCanonical', '--offline', '--no-daemon', '--console=plain', *extra]
        if os.name == 'nt':
            request = project / (label + '-arguments.json')
            request.write_text(json.dumps(args) + '\n', encoding='utf-8')
            command = ['pwsh', '-NoProfile', '-File', str(ROOT / 'scripts/ci/run_gradle_request.ps1'),
                       '-RequestFilePath', str(request)]
        else:
            command = [str(ROOT / 'gradlew'), *args]
        completed = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=300)
        output = (completed.stdout + completed.stderr).decode('utf-8', errors='replace')
        (project / (label + '.log')).write_text(output, encoding='utf-8')
        (project / (label + '-process.json')).write_text(
            json.dumps({'arguments': command, 'exitCode': completed.returncode}) + '\n', encoding='utf-8')
        shutil.copy2(project / (label + '.log'), self.output / (project.name + '-' + label + '.log'))
        return completed.returncode, output

    def reject(self, project, jar, marker):
        original = jar.read_bytes() if jar.exists() else None
        code, log = self.execute(project)
        self.assertNotEqual(0, code, log)
        self.assertIn(marker, log)
        self.assertEqual(original, jar.read_bytes() if jar.exists() else None)
        self.assertEqual([], list(jar.parent.glob('canonical-*.jar')))

    def test_container_variation_and_repeat_preserve_the_same_strictly_verified_bytes(self):
        self.assertEqual(EXPECTED, hashlib.sha256(CANONICAL).hexdigest())
        for label, entries in (('reverse-order', None), ('forward-order', [('a.txt', b'alpha'), ('z.txt', b'zeta')])):
            with self.subTest(order=label):
                project, jar = self.project(label, entries)
                zone = 'Asia/Tokyo' if label == 'reverse-order' else 'America/Los_Angeles'
                code, log = self.execute(project, 'run', '-Duser.timezone=' + zone)
                self.assertEqual(0, code, log)
                self.assertEqual(CANONICAL, jar.read_bytes())
        stamp = jar.stat().st_mtime_ns
        code, log = self.execute(project, 'repeat')
        self.assertEqual(0, code, log)
        self.assertEqual(stamp, jar.stat().st_mtime_ns, 'Already canonical bytes must not be rewritten')

    def test_content_change_addition_and_removal_fail_without_replacing_original(self):
        for label, entries in (
            ('changed', [('a.txt', b'ALPHA'), ('z.txt', b'zeta')]),
            ('added', [('a.txt', b'alpha'), ('z.txt', b'zeta'), ('extra.txt', b'new')]),
            ('removed', [('a.txt', b'alpha')]),
        ):
            with self.subTest(change=label):
                self.reject(*self.project(label, entries), 'COPPERBENCH_LOCAL_MINECRAFT_CONTENT_MISMATCH')

    def test_duplicate_entries_are_rejected(self):
        self.reject(*self.project('duplicate', [('a.txt', b'alpha'), ('a.txt', b'alpha'), ('z.txt', b'zeta')]),
                    'COPPERBENCH_LOCAL_MINECRAFT_DUPLICATE_ENTRY')

    def test_missing_artifact_is_rejected(self):
        project, jar = self.project('missing')
        jar.unlink()
        self.reject(project, jar, 'COPPERBENCH_LOCAL_MINECRAFT_MISSING')

    def test_missing_pinned_checksum_is_rejected(self):
        project, jar = self.project('missing-checksum')
        metadata = project / 'gradle/verification-metadata.xml'
        metadata.write_text(metadata.read_text(encoding='utf-8').replace(f'<sha256 value="{EXPECTED}"/>', ''), encoding='utf-8')
        self.reject(project, jar, 'COPPERBENCH_LOCAL_MINECRAFT_CHECKSUM_MISSING')

    def test_gradle_still_rejects_raw_byte_tampering_after_normalization(self):
        project, _ = self.project('later-tampering')
        code, log = self.execute(project, 'run', '-Ptamper=true')
        self.assertNotEqual(0, code, log)
        self.assertIn('Dependency verification failed', log)


if __name__ == '__main__':
    unittest.main(verbosity=2)
