"""Recorder regression fixtures are not autonomous or unfamiliar-user trials."""
import copy
from datetime import datetime, timedelta, timezone
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).parents[1] / "agent-readiness-study.py"
spec = importlib.util.spec_from_file_location("readiness_study", SCRIPT)
study = importlib.util.module_from_spec(spec)
spec.loader.exec_module(study)


class StudyRecorderTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.spec_path = self.root / "spec.json"
        self.journal = self.root / "study"
        inputs = {}
        for name in ("candidatePackage", "applicationJar", "taskCard", "recoveryProbe", "gameTests", "testManifest"):
            path = self.root / (name + ".txt")
            path.write_text("unit test fixture: " + name, encoding="utf-8")
            inputs[name] = {"path": path.name, "sha256": study.digest(path)}
        self.plan = {
            "schemaVersion": "1.0", "studyId": "unit-fixture", "kind": "autonomous-agent",
            "sourceCommit": "a" * 40, "operatingSystem": "isolated unit fixture",
            "cachePolicy": "not a real measured run",
            "agent": {"model": "test-fixture-only", "settings": {"tools": "none"}},
            "inputs": inputs,
            "attempts": [{"id": f"agent-{i:03}", "participantId": f"agent-{i:03}",
                          "workspaceRoot": f"/guest/workspaces/agent-{i:03}"} for i in range(1, 21)],
        }

    def write(self, path, value):
        path.write_bytes(study.encoded(value))
        return path

    def register(self):
        self.write(self.spec_path, self.plan)
        return study.register(self.spec_path, self.journal)

    def start(self, number=1):
        return study.start(self.journal, f"agent-{number:03}", f"independent-context-{number}")

    def result(self, number=1, outcome="completed"):
        attempt = f"agent-{number:03}"
        bundle = self.root / ("bundle-" + attempt)
        bundle.mkdir()
        (bundle / "transcript.txt").write_text("Unit fixture, not actual agent evidence", encoding="utf-8")
        (bundle / "observation.txt").write_text("Unit-only stage evidence", encoding="utf-8")
        record = {
            "kind": self.plan["kind"], "executionId": f"independent-context-{number}",
            "sourceCommit": self.plan["sourceCommit"],
            "candidateSha256": self.plan["inputs"]["candidatePackage"]["sha256"],
            "workspaceRoot": self.plan["attempts"][number - 1]["workspaceRoot"],
            "outcome": outcome, "reviewer": "unit-test", "note": "Synthetic recorder regression only",
            "evidence": ["transcript.txt"],
        }
        if outcome == "completed":
            record["transcript"] = "transcript.txt"
            record["stages"] = {s: {"verdict": "passed", "evidence": ["observation.txt"]} for s in study.STAGES}
            jar = bundle / "tested.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr("fabric.mod.json", json.dumps({"id": "m1_delivery"}))
            (bundle / "exported.jar").write_bytes(jar.read_bytes())
            xml = bundle / "report.xml"
            xml.write_text("<testsuite>" + "".join(f'<testcase name="{name}"/>' for name in sorted(study.TESTS))
                           + "</testsuite>", encoding="utf-8")
            stamp = study.now()
            verification = {
                "status": "passed", "mode": "packaged_jar", "sourceCurrentAtCompletion": True,
                "minimumTests": 5, "acceptanceExecuted": 5, "processExitCode": 0, "failed": 0, "skipped": 0,
                "startedAt": stamp, "completedAt": stamp, "environment": {"generatorId": "fabric-1.21.1"},
                "artifactSha256": study.digest(jar), "reportSha256": study.digest(xml),
                "artifactPath": record["workspaceRoot"] + "/.copperbench/tasks/tested.jar",
                "sourceSnapshot": {"sha256": "b" * 64}, "taskId": "acceptance-task", "workspaceId": "workspace",
                "cases": [{"name": name, "scope": "acceptance", "status": "passed"} for name in sorted(study.TESTS)],
            }
            self.write(bundle / "verification.json", verification)
            self.write(bundle / "export.json", {
                "status": "succeeded", "workspaceId": "workspace",
                "data": {"task": {"state": "succeeded", "verifiedExport": {
                    "status": "passed_current_input", "taskId": "acceptance-task", "sourceSha256": "b" * 64,
                    "artifactSha256": verification["artifactSha256"], "reportSha256": verification["reportSha256"]}}},
            })
            record["delivery"] = {"verification": "verification.json", "report": "report.xml",
                                  "testedJar": "tested.jar", "exportedJar": "exported.jar", "exportReceipt": "export.json"}
        return self.write(bundle / "result.json", record)

    def test_preregistration_rejects_small_duplicate_and_overlapping_samples(self):
        original = copy.deepcopy(self.plan)
        variants = [
            lambda p: p.update(attempts=p["attempts"][:19]),
            lambda p: p["attempts"][1].update(id=p["attempts"][0]["id"]),
            lambda p: p["attempts"][1].update(workspaceRoot="/guest/workspaces/agent-001/nested"),
            lambda p: p["inputs"]["candidatePackage"].update(sha256="0" * 64),
        ]
        for edit in variants:
            self.plan = copy.deepcopy(original)
            edit(self.plan)
            with self.assertRaises(ValueError):
                self.register()
            self.assertFalse(self.journal.exists())

    def test_user_study_requires_five_to_eight_distinct_participants(self):
        self.plan["kind"] = "unfamiliar-user"
        self.plan["attempts"] = self.plan["attempts"][:5]
        self.plan["attempts"][1]["participantId"] = self.plan["attempts"][0]["participantId"]
        with self.assertRaisesRegex(ValueError, "distinct"):
            self.register()
        self.plan["attempts"][1]["participantId"] = "person-002"
        self.register()
        self.assertIsNone(study.summary(self.journal)["agentTargetMet"])

    def test_plan_and_frozen_inputs_cannot_change_after_registration(self):
        self.register()
        package = self.root / "candidatePackage.txt"
        package.write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Frozen input changed"):
            self.start()
        plan = study.read_json(self.journal / "plan.json")
        plan["attempts"].pop()
        self.write(self.journal / "plan.json", plan)
        with self.assertRaisesRegex(ValueError, "plan changed"):
            study.summary(self.journal)

    def test_context_reuse_and_invalid_lifecycle_never_rewrite_history(self):
        self.register()
        self.start()
        before = (self.journal / "events.jsonl").read_bytes()
        with self.assertRaises(ValueError):
            self.start()
        with self.assertRaisesRegex(ValueError, "reused"):
            study.start(self.journal, "agent-002", "independent-context-1")
        with self.assertRaises(ValueError):
            study.rescue(self.journal, "agent-002", "reviewer", "before task start")
        self.assertEqual(before, (self.journal / "events.jsonl").read_bytes())

    def test_failed_abandoned_rescued_and_running_attempts_stay_in_denominator(self):
        self.register()
        for number, outcome in ((1, "completed"), (2, "completed"), (3, "failed"), (4, "abandoned")):
            self.start(number)
            if number == 2:
                study.rescue(self.journal, "agent-002", "lead", "Explained a missing public API field")
            study.finish(self.journal, f"agent-{number:03}", self.result(number, outcome))
        self.start(5)
        summary = study.summary(self.journal)
        self.assertEqual((summary["registered"], summary["denominator"], summary["unassistedCompletions"]), (20, 5, 1))
        self.assertEqual(summary["unassistedCompletionRate"], .2)
        self.assertEqual(summary["states"], {"completed": 2, "failed": 1, "abandoned": 1, "running": 1, "registered": 15})
        self.assertIsNone(summary["agentTargetMet"])
        with self.assertRaises(ValueError):
            study.finish(self.journal, "agent-001", self.root / "bundle-agent-001/result.json")

    def test_summary_only_closes_after_all_preregistered_attempts_finish(self):
        self.register()
        for number in range(1, 21):
            self.start(number)
            study.finish(self.journal, f"agent-{number:03}", self.result(number, "abandoned"))
        summary = study.summary(self.journal)
        self.assertTrue(summary["allRegisteredAttemptsFinished"])
        self.assertEqual(summary["denominator"], 20)
        self.assertFalse(summary["agentTargetMet"])

    def test_scripted_replay_wrong_context_and_candidate_are_rejected(self):
        self.register()
        self.start()
        path = self.result(outcome="failed")
        record = study.read_json(path)
        for field, value in (("kind", "m3-scripted-installed-replay"), ("candidateSha256", "0" * 64),
                             ("executionId", "another-agent"), ("workspaceRoot", "/another-workspace")):
            modified = {**record, field: value}
            with self.subTest(field=field), self.assertRaises(ValueError):
                study.finish(self.journal, "agent-001", self.write(path, modified))
        self.assertEqual(study.summary(self.journal)["states"]["running"], 1)

    def test_zero_skipped_old_or_other_workspace_acceptance_is_rejected(self):
        self.register()
        self.start()
        result = self.result()
        path = result.parent / "verification.json"
        original = study.read_json(path)
        changes = (
            {"minimumTests": 0, "acceptanceExecuted": 0},
            {"acceptanceExecuted": True}, {"skipped": 1}, {"failed": 1},
            {"artifactPath": "/different/workspace/tested.jar"},
            {"startedAt": "2000-01-01T00:00:00Z"},
            {"sourceSnapshot": {}},
            {"completedAt": (datetime.now(timezone.utc) + timedelta(days=1)).isoformat()},
        )
        for values in changes:
            self.write(path, {**original, **values})
            with self.subTest(values=values), self.assertRaises(ValueError):
                study.finish(self.journal, "agent-001", result)
        self.assertEqual(study.summary(self.journal)["denominator"], 1)

    def test_xml_case_validation_is_independent_of_declared_passed_counts(self):
        self.register()
        self.start()
        path = self.result()
        bundle = path.parent
        xml = bundle / "report.xml"
        xml.write_text("<testsuite><testcase name='fake'/></testsuite>", encoding="utf-8")
        verification = study.read_json(bundle / "verification.json")
        verification["reportSha256"] = study.digest(xml)
        self.write(bundle / "verification.json", verification)
        exported = study.read_json(bundle / "export.json")
        exported["data"]["task"]["verifiedExport"]["reportSha256"] = study.digest(xml)
        self.write(bundle / "export.json", exported)
        with self.assertRaisesRegex(ValueError, "business test cases"):
            study.finish(self.journal, "agent-001", path)

    def test_export_tampering_and_historical_export_do_not_count(self):
        self.register()
        self.start()
        path = self.result()
        exported = path.parent / "exported.jar"
        original = exported.read_bytes()
        exported.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "JAR bytes differ"):
            study.finish(self.journal, "agent-001", path)
        exported.write_bytes(original)
        receipt = path.parent / "export.json"
        value = study.read_json(receipt)
        value["data"]["task"]["verifiedExport"]["status"] = "historical"
        self.write(receipt, value)
        with self.assertRaisesRegex(ValueError, "current-input export"):
            study.finish(self.journal, "agent-001", path)

    def test_completed_trial_requires_every_stage_and_preserves_evidence(self):
        self.register()
        self.start()
        path = self.result()
        record = study.read_json(path)
        record["stages"].pop("player")
        with self.assertRaisesRegex(ValueError, "every fixed task stage"):
            study.finish(self.journal, "agent-001", self.write(path, record))
        record["stages"]["player"] = {"verdict": "passed", "evidence": ["observation.txt"]}
        study.finish(self.journal, "agent-001", self.write(path, record))
        (path.parent / "observation.txt").unlink()
        self.assertEqual(study.summary(self.journal)["unassistedCompletions"], 1)
        captured = next((self.journal / "evidence/agent-001").glob("*-observation.txt"))
        captured.write_text("tampered after recording", encoding="utf-8")
        summary = study.summary(self.journal)
        self.assertEqual((summary["denominator"], summary["unassistedCompletions"]), (1, 0))
        self.assertTrue(summary["attempts"][0]["evidenceIssues"])

    def test_evidence_paths_cannot_escape_and_lock_conflict_is_explicit(self):
        self.register()
        self.start()
        path = self.result(outcome="failed")
        record = study.read_json(path)
        record["evidence"] = ["../candidatePackage.txt"]
        with self.assertRaisesRegex(ValueError, "escapes"):
            study.finish(self.journal, "agent-001", self.write(path, record))
        with study.locked(self.journal):
            with self.assertRaisesRegex(ValueError, "writer is active"):
                study.rescue(self.journal, "agent-001", "lead", "cannot concurrently modify")
        self.assertEqual(study.summary(self.journal)["states"]["running"], 1)

    def test_cli_registration_and_empty_summary_do_not_claim_trials(self):
        self.write(self.spec_path, self.plan)
        run = subprocess.run([sys.executable, str(SCRIPT), "register", "--spec", str(self.spec_path),
                              "--output", str(self.journal)], text=True, capture_output=True, check=True)
        self.assertEqual(json.loads(run.stdout)["started"], 0)
        run = subprocess.run([sys.executable, str(SCRIPT), "summary", "--study", str(self.journal)],
                             text=True, capture_output=True, check=True)
        summary = json.loads(run.stdout)
        self.assertEqual(summary["denominator"], 0)
        self.assertIsNone(summary["unassistedCompletionRate"])
        self.assertFalse(summary["independentlyVerifiedAgentOrUserBehavior"])

    def test_environment_change_can_be_retained_as_failed_attempt(self):
        self.register()
        self.start()
        (self.root / "candidatePackage.txt").write_text("changed during trial", encoding="utf-8")
        record = self.result(outcome="failed")
        study.finish(self.journal, "agent-001", record)
        summary = study.summary(self.journal)
        self.assertEqual((summary["denominator"], summary["states"]["failed"]), (1, 1))


if __name__ == "__main__":
    unittest.main()
