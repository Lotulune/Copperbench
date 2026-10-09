"""S18-05: a verified artifact must not hide a failed contract or ownership probe."""
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

DRIVER = Path(__file__).resolve().parents[1] / "evaluation/run-chronometer-trial.py"
SPEC = importlib.util.spec_from_file_location("chronometer_regression", DRIVER)
trial = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(trial)


class ChronometerRegressionGateTest(unittest.TestCase):
    @staticmethod
    def complete_checks():
        return [{"phase": phase, "name": name, "status": "passed"}
                for phase, name in sorted(trial.REQUIRED_CHECKS)]

    def test_complete_delivery_still_fails_on_a_contract_error(self):
        checks = self.complete_checks()
        checks.append({"phase": "delivery", "name": "unexpected_exception", "status": "failed"})
        self.assertFalse(trial.regression_succeeded(True, True, checks))

    def test_unverified_or_missing_evidence_cannot_pass(self):
        for index, original in enumerate(self.complete_checks()):
            for status in ("failed", "unverified", None):
                with self.subTest(probe=original, status=status):
                    checks = self.complete_checks()
                    checks[index]["status"] = status
                    self.assertFalse(trial.regression_succeeded(True, True, checks))
        self.assertFalse(trial.regression_succeeded(True, True, []))

    def test_every_required_phase_and_probe_must_be_observed(self):
        for index, original in enumerate(self.complete_checks()):
            with self.subTest(missing=original):
                checks = self.complete_checks()
                del checks[index]
                self.assertFalse(trial.regression_succeeded(True, True, checks))
            with self.subTest(wrong_phase=original):
                checks = self.complete_checks()
                checks[index]["phase"] = "another-workspace"
                self.assertFalse(trial.regression_succeeded(True, True, checks))
        self.assertFalse(trial.regression_succeeded(True, True, [
            {"phase": "mixed", "name": "generate_workspace", "status": "passed"}]))

    def test_both_real_delivery_and_ownership_boundary_are_required(self):
        checks = self.complete_checks()
        self.assertFalse(trial.regression_succeeded(False, True, checks))
        self.assertFalse(trial.regression_succeeded(True, False, checks))
        self.assertTrue(trial.regression_succeeded(True, True, checks))

    def test_fresh_query_is_required_even_when_the_sdk_cached_revision_matches(self):
        for operation, data in (
            ("get_workbench", {"workspace": {"id": "workspace-a", "revision": 1}}),
            ("get_workspace_environment", {"workspaceId": "workspace-a", "revision": 1}),
        ):
            response = {"status": "succeeded", "operation": operation, "workspaceId": "workspace-a",
                        "revision": 1, "data": data}
            with self.subTest(operation=operation):
                self.assertTrue(trial.query_at_revision(response, operation, "workspace-a", 1))
                for invalid in (None, {}, {**response, "status": "failed"},
                                {**response, "revision": 0}, {**response, "revision": True},
                                {**response, "workspaceId": "workspace-b"},
                                {**response, "data": {}}, {**response, "operation": "another_query"}):
                    self.assertFalse(trial.query_at_revision(invalid, operation, "workspace-a", 1))

    def test_required_evidence_collection_failure_revokes_completed_status(self):
        with tempfile.TemporaryDirectory(prefix="chronometer-collection-", dir=Path.cwd()) as directory:
            root = Path(directory)
            source, evidence = root / "source", root / "evidence"
            source.mkdir()
            evidence.mkdir()
            trial.write_json(evidence / "trial.json", {"status": "completed", "checks": [], "calls": [],
                                                       "deliveryWorkspace": str(source)})
            args = SimpleNamespace(evidence=evidence, source=source)
            with patch.object(trial, "source_archive", side_effect=OSError("required source ZIP failed")):
                report = trial.collect(args, root)
            self.assertEqual(report["status"], "incomplete")
            self.assertIn("required source ZIP failed", report["collectionErrors"])
            self.assertEqual(json.loads((evidence / "trial.json").read_text())["status"], "incomplete")

    def test_conflict_pointer_must_identify_an_existing_workspace_source(self):
        with tempfile.TemporaryDirectory(prefix="chronometer-gate-", dir=Path.cwd()) as directory:
            root = Path(directory)
            file = root / "src/main/resources/fabric.mod.json"
            file.parent.mkdir(parents=True)
            file.write_text("{}", encoding="utf-8")
            diagnostic = {"code": "GENERATION_SOURCE_CONFLICT", "path": "/src/main/resources/fabric.mod.json"}
            self.assertTrue(trial.locatable_source_conflict(diagnostic, root))
            for pointer in (None, "", "src/main/resources/fabric.mod.json", "/src/../fabric.mod.json",
                            "/src/missing.java", "/etc/passwd", "/src\\main\\resources\\fabric.mod.json"):
                with self.subTest(pointer=pointer):
                    self.assertFalse(trial.locatable_source_conflict({**diagnostic, "path": pointer}, root))
            self.assertFalse(trial.locatable_source_conflict({**diagnostic, "code": "OTHER"}, root))


if __name__ == "__main__":
    unittest.main()
