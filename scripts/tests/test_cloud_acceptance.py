"""Identity and cleanup boundaries of the public-package cloud runner."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

DIRECTORY = Path(__file__).resolve().parents[1] / "cloud-acceptance"
sys.path.insert(0, str(DIRECTORY))
spec = importlib.util.spec_from_file_location("cloud_acceptance", DIRECTORY / "run.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
from common import workspace_document, observed_gradle


class ProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.digest, self.source = "1" * 64, "2" * 40
        self.ref = "refs/tags/v0.1.7"
        self.workflow = f"https://github.com/{runner.REPOSITORY}/.github/workflows/deploy.yml@{self.ref}"
        self.invocation = f"https://github.com/{runner.REPOSITORY}/actions/runs/123/attempts/1"
        self.results = [{"verificationResult": {"signature": {"certificate": {
            "sourceRepositoryURI": f"https://github.com/{runner.REPOSITORY}", "sourceRepositoryDigest": self.source,
            "sourceRepositoryRef": self.ref, "subjectAlternativeName": self.workflow,
            "runnerEnvironment": "github-hosted", "runInvocationURI": self.invocation}},
            "statement": {"subject": [{"digest": {"sha256": self.digest}}]}}}]

    def verify(self, results):
        return runner.verify_provenance(results, self.digest, self.source, "deploy.yml", self.ref, "123", "1")

    def test_accepts_exact_signed_identity(self):
        self.assertEqual(self.verify(self.results), self.invocation)

    def test_rejects_other_source_workflow_ref_run_or_host(self):
        for key in ["sourceRepositoryURI", "sourceRepositoryDigest", "sourceRepositoryRef", "subjectAlternativeName",
                    "runnerEnvironment", "runInvocationURI"]:
            with self.subTest(key=key):
                wrong = copy.deepcopy(self.results)
                wrong[0]["verificationResult"]["signature"]["certificate"][key] = "other"
                with self.assertRaises(AssertionError):
                    self.verify(wrong)

    def test_unsigned_predicate_cannot_supply_certificate_identity(self):
        wrong = copy.deepcopy(self.results)
        value = wrong[0]["verificationResult"]
        value["statement"]["predicate"] = value.pop("signature")
        with self.assertRaises(AssertionError):
            self.verify(wrong)

    def test_rejects_another_artifact_even_with_expected_certificate(self):
        self.results[0]["verificationResult"]["statement"]["subject"][0]["digest"]["sha256"] = "3" * 64
        with self.assertRaises(AssertionError):
            self.verify(self.results)


class SafetyTest(unittest.TestCase):
    def test_only_known_data_pack_adapters_may_omit_gradle_projection(self):
        with tempfile.TemporaryDirectory() as folder:
            for generator in ["datapack-1.20.1", "datapack-1.21.1", "datapack-26.1.x"]:
                self.assertIsNone(observed_gradle({"generator": {"id": generator}, "execution": {"host": {}}}, folder))
            with self.assertRaises(AssertionError):
                observed_gradle({"generator": {"id": "fabric-1.21.1"}}, folder)
            gradle = {"userHome": folder, "userHomeSource": "COPPERBENCH_GRADLE_USER_HOME", "reuseExternalDistributions": False}
            self.assertEqual(observed_gradle({"execution": {"gradle": gradle}}, folder), gradle)
            with self.assertRaises(AssertionError):
                observed_gradle({"execution": {"gradle": {**gradle, "reuseExternalDistributions": True}}}, folder)

    def test_document_discovery_excludes_mcreator_cache_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / ".mcreator").mkdir()
            expected = root / "project.mcreator"
            expected.write_text("{}", encoding="utf-8")
            self.assertEqual(workspace_document(root), expected)
            (root / "ambiguous.mcreator").write_text("{}", encoding="utf-8")
            with self.assertRaises(AssertionError):
                workspace_document(root)

    def test_machine_json_is_independent_of_stderr_and_whitespace(self):
        with tempfile.TemporaryDirectory() as folder:
            log = Path(folder) / "command.log"
            for data in ['[{"verified":true}]', '[\n  {"verified": true}\n]']:
                script = "import sys; print(sys.argv[1]); print('verification progress', file=sys.stderr)"
                result = runner.command([sys.executable, "-c", script, data], log)
                self.assertEqual(json.loads(result), [{"verified": True}])
                self.assertIn("verification progress", log.with_suffix(".stderr.log").read_text(encoding="utf-8"))

    def test_requires_exact_release_tags(self):
        self.assertEqual(runner.release_version("v0.1.7"), "0.1.7")
        for value in ["main", "v0.1.7-linux-stable", "../v0.1.7", "v0.1.7\n", ""]:
            with self.subTest(value=value), self.assertRaises(AssertionError):
                runner.release_version(value)

    def test_cleanup_rejects_root_and_outside_without_deleting_data(self):
        with tempfile.TemporaryDirectory() as folder:
            outer = Path(folder)
            root, outside = outer / "run", outer / "unrelated"
            root.mkdir()
            outside.mkdir()
            protected = outside / "data.txt"
            protected.write_text("keep", encoding="utf-8")
            for path in [root, outside, outer]:
                with self.subTest(path=path), self.assertRaises(AssertionError):
                    runner.remove_owned_tree(path, root)
            self.assertEqual(protected.read_text(encoding="utf-8"), "keep")
            owned = root / "unpacked"
            owned.mkdir()
            runner.remove_owned_tree(owned, root)
            self.assertFalse(owned.exists())


class UpgradeCleanupTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        root = Path(self.folder.name)
        self.target, self.payload = root / "installed", root / "payload"
        for directory in [self.target, self.payload]:
            (directory / "lib").mkdir(parents=True)
        (self.target / "lib/copperbench.jar").write_bytes(b"old application")
        (self.payload / "lib/copperbench.jar").write_bytes(b"published new application")
        self.previous_sha = runner.sha256(self.target / "lib/copperbench.jar")
        self.receipt = {"backup": None, "backupCleanup": "confirmed"}
        self.checkpoints = []
        self.acceptance = runner.Acceptance.__new__(runner.Acceptance)
        self.acceptance.bundles = {"v0.1.18": {"payload": self.payload, "manifest": root / "manifest",
            "jar": root / "setup.jar", "registrar": root / "registrar"}}
        def install(*args):
            (self.target / "lib/copperbench.jar").write_bytes((self.payload / "lib/copperbench.jar").read_bytes())
            return dict(self.receipt)
        self.acceptance.probe = install
        self.acceptance.registration = lambda target, version: {"version": version}
        self.acceptance.checkpoint = lambda name, **data: self.checkpoints.append(data)

    def test_cleaned_upgrade_records_old_and_new_application_hashes(self):
        self.acceptance.install("v0.1.18", self.target, upgrade=True)
        receipt = self.checkpoints[0]
        self.assertEqual(receipt["result"]["previousApplicationSha256"], self.previous_sha)
        self.assertEqual(receipt["applicationSha256"], runner.sha256(self.payload / "lib/copperbench.jar"))
        self.assertNotEqual(receipt["applicationSha256"], self.previous_sha)

    def test_retained_backup_is_a_cleanup_failure(self):
        self.receipt["backup"] = str(self.target.parent / "Copperbench-previous-unremoved")
        with self.assertRaisesRegex(AssertionError, "cleanup was not confirmed"):
            self.acceptance.install("v0.1.18", self.target, upgrade=True)
        self.assertEqual(self.checkpoints, [])

    def test_missing_cleanup_receipt_cannot_pass(self):
        self.receipt.pop("backupCleanup")
        with self.assertRaisesRegex(AssertionError, "cleanup was not confirmed"):
            self.acceptance.install("v0.1.18", self.target, upgrade=True)

    def test_unchanged_application_cannot_pass_as_an_upgrade(self):
        (self.payload / "lib/copperbench.jar").write_bytes(b"old application")
        with self.assertRaisesRegex(AssertionError, "did not replace"):
            self.acceptance.install("v0.1.18", self.target, upgrade=True)


if __name__ == "__main__":
    unittest.main()
