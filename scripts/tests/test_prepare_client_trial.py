import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("client_trial", Path(__file__).parents[1] / "prepare-client-trial.py")
trial = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trial)


class ClientTrialIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / "mod.jar"
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "fixture_mod"}))
        self.xml = self.root / "tests.xml"
        self.xml.write_text('<testsuite tests="2" failures="0"/>', encoding="utf-8")
        self.value = {"status": "passed", "mode": "packaged_jar", "sourceCurrentAtCompletion": True,
                      "processExitCode": 0, "failed": 0, "acceptanceExecuted": 2, "minimumTests": 2,
                      "environment": {"generatorId": "fabric-1.21.1"}, "artifactPath": str(self.jar),
                      "artifactSha256": trial.digest(self.jar), "reportPath": str(self.xml),
                      "reportSha256": trial.digest(self.xml), "sourceSnapshot": {"sha256": "a" * 64}}
        self.report = self.root / "verification.json"

    def write_report(self):
        self.report.write_text(json.dumps(self.value), encoding="utf-8")
        return self.report

    def test_accepts_verified_jar_and_final_jsonl_result(self):
        result = trial.read_verification(self.write_report())
        self.assertEqual(result["artifactSha256"], trial.digest(self.jar))
        self.report.write_text('{}\n' + json.dumps({"task": {"verification": self.value}}) + '\n', encoding="utf-8")
        self.assertEqual(trial.read_verification(self.report)["modId"], "fixture_mod")

    def test_rejects_failed_stale_or_workspace_mode(self):
        for key, invalid in (("status", "failed"), ("mode", "workspace"), ("sourceCurrentAtCompletion", False),
                             ("acceptanceExecuted", 0), ("failed", 1)):
            with self.subTest(key=key):
                original = self.value[key]
                self.value[key] = invalid
                with self.assertRaises(ValueError): trial.read_verification(self.write_report())
                self.value[key] = original

    def test_rejects_artifact_or_report_tampering(self):
        self.write_report()
        self.xml.write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "changed"): trial.read_verification(self.report)
        self.value["reportSha256"] = trial.digest(self.xml)
        self.write_report()
        self.jar.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "changed"): trial.read_verification(self.report)

    def test_mod_id_cannot_escape_client_mod_directory(self):
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "../../escape"}))
        self.value["artifactSha256"] = trial.digest(self.jar)
        with self.assertRaises(ValueError): trial.read_verification(self.write_report())

    def test_manifest_checks_frozen_set_and_does_not_claim_behavior(self):
        mods = self.root / "run/mods"
        mods.mkdir(parents=True)
        deployed = mods / "fixture.jar"
        deployed.write_bytes(self.jar.read_bytes())
        manifest = self.root / "client-trial.json"
        manifest.write_text(json.dumps({"artifacts": [{"deployedFile": "run/mods/fixture.jar",
                                                       "artifactSha256": trial.digest(deployed)}]}), encoding="utf-8")
        self.assertFalse(trial.check(manifest)["clientBehaviorVerified"])
        (mods / "extra.jar").write_bytes(b"extra")
        with self.assertRaises(ValueError): trial.check(manifest)


if __name__ == "__main__": unittest.main()
