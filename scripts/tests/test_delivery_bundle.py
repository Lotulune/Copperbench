import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "verify-delivery-bundle.py"
spec = importlib.util.spec_from_file_location("delivery_verifier", SCRIPT)
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class DeliveryBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "delivery"
        self.root.mkdir()
        (self.root / "verified-mod.jar").write_bytes(b"fixture mod bytes")
        (self.root / "gametest-results.xml").write_text(
            "<testsuite><testcase name='first'/><testcase name='second'/><testcase name='minecraft:always_pass'/>"
            "<testcase name='disabled'><skipped/></testcase></testsuite>", encoding="utf-8")
        self.identity = {"identityVersion": "1", "taskId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                         "source": "acceptance_record", "application": {"sourceState": "packaged_binary", "applicationSha256": "a" * 64}}
        self.write("application-identity.json", self.identity)
        self.receipt = {"schemaVersion": "1.0", "portableEvidenceVersion": "1", "taskId": self.identity["taskId"],
                        "status": "passed_current_input", "sourceSha256": "b" * 64, "mode": "packaged_jar",
                        "minimumTests": 2, "processExitCode": 0, "startedAt": "2026-10-11T00:00:00Z",
                        "environmentSource": "acceptance_record", "environment": {},
                        "artifactPath": "verified-mod.jar", "reportPath": "gametest-results.xml",
                        "artifactSha256": self.digest("verified-mod.jar"), "reportSha256": self.digest("gametest-results.xml"),
                        "applicationIdentitySha256": self.digest("application-identity.json"),
                        "passed": 3, "failed": 0, "skipped": 1, "acceptanceExecuted": 2,
                        "coverage": {"minimumTests": 2, "acceptanceExecuted": 2, "shortfall": 0, "status": "sufficient"}}
        self.reseal()

    def write(self, name, data):
        (self.root / name).write_text(json.dumps(data), encoding="utf-8")

    def digest(self, name):
        return hashlib.sha256((self.root / name).read_bytes()).hexdigest()

    def reseal(self):
        self.write("verification.json", self.receipt)
        self.manifest = {"bundleVersion": "1", **{key: self.receipt[key] for key in
            ("taskId", "status", "sourceSha256", "artifactSha256", "reportSha256", "mode", "minimumTests", "applicationIdentitySha256")},
            "files": [{"path": name, "size": (self.root / name).stat().st_size, "sha256": self.digest(name)}
                      for name in verifier.LIMITS if name != "manifest.json"]}
        self.write("manifest.json", self.manifest)

    def archive(self, name="delivery.zip", extra=None):
        target = Path(self.temp.name) / name
        with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for path in self.root.iterdir():
                archive.write(path, path.name)
            if extra:
                archive.writestr(*extra)
        return target

    def test_valid_directory_and_zip_in_clean_offline_process(self):
        clean = Path(self.temp.name) / "clean"; clean.mkdir()
        package = self.archive()
        result = subprocess.run([sys.executable, "-I", str(SCRIPT), str(package)], cwd=clean, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        observed = json.loads(result.stdout)
        self.assertEqual("consistent", observed["status"])
        self.assertEqual(2, observed["coverage"]["acceptanceExecuted"])
        self.assertEqual("not_authenticated", observed["provenance"])
        self.assertEqual([], list(clean.iterdir()), "Verification does not extract, execute or write")
        self.assertEqual(observed, verifier.verify(self.root))

    def test_historical_label_is_preserved(self):
        self.receipt["status"] = "passed_historical_input"; self.reseal()
        self.assertEqual("passed_historical_input", verifier.verify(self.root)["inputStatusAtExport"])

    def test_each_evidence_file_and_manifest_rejects_tampering(self):
        for name in verifier.LIMITS:
            with self.subTest(name=name):
                path = self.root / name; before = path.read_bytes()
                path.write_bytes(before + b"tampered")
                with self.assertRaises((ValueError, zipfile.BadZipFile)):
                    verifier.verify(self.root)
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(1, verifier.main([str(self.root)]))
                path.write_bytes(before)

    def test_resealed_counts_cannot_override_raw_report(self):
        for key, value in (("acceptanceExecuted", 9), ("passed", 9), ("skipped", 0), ("failed", 1), ("minimumTests", 3),
                           ("minimumTests", True), ("processExitCode", False), ("processExitCode", 1), ("mode", "workspace")):
            with self.subTest(key=key, value=value):
                before = self.receipt[key]; self.receipt[key] = value; self.reseal()
                with self.assertRaises(ValueError): verifier.verify(self.root)
                self.receipt[key] = before; self.reseal()

    def test_raw_failure_duplicate_skipped_framework_and_invalid_xml_rejected_after_rehash(self):
        reports = ["<testsuite/>", "<testsuite><testcase name='x'/><testcase name='x'/></testsuite>",
                   "<testsuite><testcase name='x'><failure/></testcase></testsuite>",
                   "<testsuite failures='1'><testcase name='x'/></testsuite>",
                   "<testsuite><testcase name='x'><skipped/></testcase></testsuite>",
                   "<testsuite><testcase name='minecraft:always_pass'/></testsuite>",
                   "<testsuite><testcase name='x'><testcase name='y'/></testcase></testsuite>", "<broken",
                   "<!DOCTYPE testsuite [<!ENTITY x 'x'>]><testsuite><testcase name='&x;'/></testsuite>"]
        for xml in reports:
            with self.subTest(xml=xml):
                (self.root / "gametest-results.xml").write_text(xml, encoding="utf-8")
                self.receipt["reportSha256"] = self.digest("gametest-results.xml"); self.reseal()
                with self.assertRaises((ValueError, verifier.expat.ExpatError)): verifier.verify(self.root)

    def test_unknown_versions_and_incomplete_legacy_are_explicit(self):
        for obj, key in ((self.receipt, "portableEvidenceVersion"), (self.receipt, "schemaVersion")):
            before = obj[key]; obj[key] = "99"; self.reseal()
            with self.assertRaises(ValueError): verifier.verify(self.root)
            obj[key] = before
        self.reseal()
        self.manifest["bundleVersion"] = "99"; self.write("manifest.json", self.manifest)
        with self.assertRaises(ValueError): verifier.verify(self.root)
        (self.root / "manifest.json").unlink()
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(2, verifier.main([str(self.root)]))
        self.assertEqual("legacy_incomplete", json.loads(output.getvalue())["status"])

    def test_unsafe_paths_duplicates_and_manifest_omissions_are_rejected(self):
        for path in ("/absolute.jar", "../verified-mod.jar", "nested/../verified-mod.jar",
                     "C:/verified-mod.jar", r"C:\verified-mod.jar", "./verified-mod.jar"):
            with self.subTest(path=path):
                self.reseal(); self.manifest["files"][0]["path"] = path; self.write("manifest.json", self.manifest)
                with self.assertRaises(ValueError): verifier.verify(self.root)
        self.reseal(); self.manifest["files"][0] = self.manifest["files"][1]; self.write("manifest.json", self.manifest)
        with self.assertRaises(ValueError): verifier.verify(self.root)

    def test_malicious_zip_members_and_truncation(self):
        for name in ("../escape", "/absolute", "C:/escape", "verified-mod.jar", "VERIFIED-MOD.JAR"):
            with self.subTest(name=name), warnings.catch_warnings():
                warnings.simplefilter("ignore")
                archive = self.archive(extra=(name, b"x"))
                with self.assertRaises(ValueError): verifier.verify(archive)
        archive = self.archive()
        archive.write_bytes(archive.read_bytes()[:-30])
        with self.assertRaises(zipfile.BadZipFile): verifier.verify(archive)

    def test_symlink_zip_and_expansion_budget_are_rejected(self):
        archive = Path(self.temp.name) / "link.zip"
        with zipfile.ZipFile(archive, "w") as handle:
            for path in self.root.iterdir():
                if path.name != "verified-mod.jar": handle.write(path, path.name)
            link = zipfile.ZipInfo("verified-mod.jar"); link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            handle.writestr(link, "../outside")
        with self.assertRaises(ValueError): verifier.verify(archive)
        with zipfile.ZipFile(archive, "w") as handle:
            for path in self.root.iterdir():
                if path.name != "verified-mod.jar": handle.write(path, path.name)
            link = zipfile.ZipInfo("verified-mod.jar"); link.create_system = 0
            link.external_attr = 0x400
            handle.writestr(link, "../outside")
        with self.assertRaises(ValueError): verifier.verify(archive)
        budget = verifier.LIMITS["verified-mod.jar"]
        try:
            verifier.LIMITS["verified-mod.jar"] = 2
            with self.assertRaises(ValueError): verifier.verify(self.root)
            with self.assertRaises(ValueError): verifier.verify(self.archive())
        finally: verifier.LIMITS["verified-mod.jar"] = budget

    def test_duplicate_json_key_is_rejected(self):
        path = self.root / "manifest.json"
        path.write_text('{"bundleVersion":"99",' + path.read_text(encoding="utf-8")[1:], encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Duplicate JSON"): verifier.verify(self.root)

    def test_filesystem_symlink_is_rejected(self):
        artifact = self.root / "verified-mod.jar"
        outside = Path(self.temp.name) / "original.jar"
        artifact.rename(outside)
        try:
            os.symlink(outside, artifact)
        except OSError as error:
            self.skipTest(f"Filesystem does not permit symlink creation: {error}")
        with self.assertRaisesRegex(ValueError, "links"): verifier.verify(self.root)

    def test_unknown_manifest_fields_are_rejected(self):
        self.manifest["success"] = True
        self.write("manifest.json", self.manifest)
        with self.assertRaises(ValueError): verifier.verify(self.root)

    def test_boolean_manifest_minimum_cannot_equal_integer_one(self):
        self.receipt["minimumTests"] = 1
        self.receipt["coverage"]["minimumTests"] = 1
        self.reseal()
        self.manifest["minimumTests"] = True
        self.write("manifest.json", self.manifest)
        with self.assertRaises(ValueError): verifier.verify(self.root)

    def test_application_identity_cannot_change_task_or_claim_unobserved_binary(self):
        for key, value in (("taskId", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), ("source", "unavailable"), ("identityVersion", "99")):
            before = self.identity[key]; self.identity[key] = value; self.write("application-identity.json", self.identity)
            self.receipt["applicationIdentitySha256"] = self.digest("application-identity.json"); self.reseal()
            with self.assertRaises(ValueError): verifier.verify(self.root)
            self.identity[key] = before


if __name__ == "__main__":
    unittest.main()
