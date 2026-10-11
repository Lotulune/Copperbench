"""Protocol report and scope regressions preserved from PRs 98/99."""

from contextlib import redirect_stdout
import copy
import importlib.util
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[2]
RUNNER_PATH = ROOT / "scripts/run-ai-live-evals.py"
spec = importlib.util.spec_from_file_location("ai_contract_evals", RUNNER_PATH)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

LEGACY_IDS = {
    "create-element", "procedure-edit", "rename-reference", "build-repair",
    "revision-conflict", "readonly-denial", "datagen-cancel", "datagen-publish",
    "recovery-restore", "task-reconnect",
}
UNSUPPORTED_COVERAGE = ("modelDriven", "realBuild", "repairLoop", "transportReconnect", "gameplay")


class EvalReportTest(unittest.TestCase):
    def workspace_client(self):
        client = Mock(spec=runner.CopperbenchClient)
        client.get_workspace.return_value = {"data": {"workspace": {"revision": 0}}}
        client.create_mod_element.side_effect = [
            {"status": "committed", "newRevision": 1},
            {"status": "committed", "newRevision": 2, "data": {"element": {"id": "procedure"}}},
            runner.CopperbenchError("stale", "REVISION_CONFLICT", {
                "diagnostics": [{"code": "WORKSPACE_REVISION_CONFLICT"}]}),
        ]
        client.update_procedure.return_value = {"status": "committed", "newRevision": 3}
        client.create_registry_entry.return_value = {
            "status": "committed", "newRevision": 4, "data": {"entry": {"id": "variable"}},
        }
        client.rename_registry_entry.return_value = {"status": "committed", "newRevision": 5}
        client.build_workspace.side_effect = [
            {"status": "accepted", "task": {"id": "build-task"}},
            {"status": "accepted", "task": {"id": "polling-task"}},
        ]
        client.get_task.return_value = {"status": "succeeded", "data": {"task": {"state": "running"}}}
        client.cancel_task.return_value = {"status": "cancelled"}
        client.run_datagen.side_effect = [
            {"status": "accepted", "task": {"id": "cancel-datagen"}},
            {"status": "accepted", "task": {"id": "publish-datagen"}},
        ]
        client.preview_datagen_output.return_value = {"status": "succeeded", "data": {"manifestHash": "a" * 64}}
        client.publish_datagen_output.return_value = {"status": "committed", "newRevision": 6}
        client.create_recovery_point.return_value = {"status": "committed", "recoveryPointId": "point"}
        client.restore_recovery_point.side_effect = runner.CopperbenchError("approval", "USER_APPROVAL_REQUIRED")
        return client

    def test_runner_preserves_legacy_case_ids_and_labels_actual_observations(self):
        client = self.workspace_client()
        results = runner.run_workspace(client)
        report = runner.build_report("workspace", results)
        self.assertEqual(9, report["passed"])
        self.assertEqual(0, report["failed"])
        self.assertEqual(LEGACY_IDS - {"readonly-denial"}, {case["id"] for case in report["cases"]})
        self.assertEqual("protocol-contract", report["scope"]["kind"])
        self.assertEqual("InMemoryWorkspaceTaskGateway", report["scope"]["taskGateway"])
        for unsupported in UNSUPPORTED_COVERAGE:
            self.assertIs(False, report["scope"][unsupported])
        by_id = {case["id"]: case for case in report["cases"]}
        self.assertEqual(["build-task-acceptance", "running-task-query", "build-task-cancellation"],
                         by_id["build-repair"]["observedCoverage"])
        self.assertEqual(["same-client-task-polling"], by_id["task-reconnect"]["observedCoverage"])
        self.assertEqual(2, client.build_workspace.call_count)
        self.assertEqual(3, client.get_task.call_count)
        client.initialize.assert_not_called()
        self.assertEqual([("polling-task", 0), ("polling-task", 0)],
                         [call.args for call in client.get_task.call_args_list[-2:]])

    def test_polling_rejection_cannot_be_reported_as_passed(self):
        client = self.workspace_client()
        running = client.get_task.return_value
        client.get_task.side_effect = [running, running, {"status": "failed"}]
        with self.assertRaisesRegex(AssertionError, "same client session"):
            runner.run_workspace(client)

    def test_report_does_not_convert_failed_cases_into_success_counts(self):
        report = runner.build_report("workspace", [
            {"id": "build-repair", "status": "passed"},
            {"id": "task-reconnect", "status": "failed"},
        ])
        self.assertEqual((1, 1), (report["passed"], report["failed"]))

    def test_revision_rejection_requires_sdk_code_and_original_core_diagnostic(self):
        for code, details in (
            ("WORKSPACE_REVISION_CONFLICT", {"diagnostics": [{"code": "WORKSPACE_REVISION_CONFLICT"}]}),
            ("REVISION_CONFLICT", None),
            ("REVISION_CONFLICT", {"diagnostics": [{"code": "UNRELATED_ERROR"}]}),
        ):
            with self.subTest(code=code, details=details):
                action = Mock(side_effect=runner.CopperbenchError("rejected", code, details))
                with self.assertRaises(AssertionError):
                    runner.rejected(action, "REVISION_CONFLICT", diagnostic_code="WORKSPACE_REVISION_CONFLICT")

    def test_cli_report_is_compatible_scoped_and_contains_no_connection_secret(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            connection = directory / "connection.json"
            connection.write_text(json.dumps({"port": 12345, "token": "contract-test-secret", "workspaceId": "workspace"}))
            output = directory / "report.json"
            client = self.workspace_client()
            stdout = io.StringIO()
            with patch.object(runner, "CopperbenchClient", return_value=client), \
                    patch.object(sys, "argv", [str(RUNNER_PATH), str(connection), "--mode", "workspace", "--output", str(output)]), \
                    redirect_stdout(stdout):
                runner.main()
            report = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(report, json.loads(stdout.getvalue()))
            self.assertTrue({"mode", "passed", "failed", "cases"} <= report.keys())
            self.assertEqual("copperbench-ai-core", report["suite"])
            self.assertEqual("1.0", report["schemaVersion"])
            self.assertNotIn("contract-test-secret", stdout.getvalue())
            client.initialize.assert_called_once_with("copperbench-ai-live-eval", "0.1.0")

    def test_read_only_profile_still_contributes_the_tenth_case(self):
        client = Mock(spec=runner.CopperbenchClient)
        client.create_mod_element.side_effect = runner.CopperbenchError("denied", "PERMISSION_DENIED")
        report = runner.build_report("read_only", runner.run_read_only(client))
        self.assertEqual((1, 0), (report["passed"], report["failed"]))
        self.assertEqual("readonly-denial", report["cases"][0]["id"])
        self.assertEqual(["read-only-mutation-denial"], report["cases"][0]["observedCoverage"])


class EvalManifestValidationTest(unittest.TestCase):
    def setUp(self):
        self.node = shutil.which("node")
        self.assertIsNotNone(self.node, "Node.js is required to exercise the repository manifest verifier")
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        files = (
            "sdk/evals/manifest.json", "sdk/typescript/copperbench.ts", "sdk/python/copperbench.py",
            "examples/ai/quickstart.ts", "examples/ai/quickstart.py",
            "scripts/run-ai-live-evals.py", "scripts/verify-ai-live-evals.ps1",
        )
        for relative in files:
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)
        self.manifest_path = self.root / "sdk/evals/manifest.json"
        self.manifest = json.loads(self.manifest_path.read_text())

    def verify(self, manifest):
        self.manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        return subprocess.run(
            [self.node, str(ROOT / "scripts/verify-ai-evals.mjs")], cwd=self.root,
            capture_output=True, text=True, timeout=15,
        )

    def test_original_ten_ids_are_valid_with_additive_scope_metadata(self):
        self.assertEqual(LEGACY_IDS, {case["id"] for case in self.manifest["cases"]})
        self.assertEqual(10, self.manifest["minimumCases"])
        completed = self.verify(self.manifest)
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("10 cases, 16 observed contract checks (10 legacy coverage labels)", completed.stdout)

    def test_runtime_or_model_claims_are_rejected(self):
        for field in UNSUPPORTED_COVERAGE:
            with self.subTest(field=field):
                manifest = copy.deepcopy(self.manifest)
                manifest["scope"][field] = True
                completed = self.verify(manifest)
                self.assertNotEqual(0, completed.returncode)
                self.assertIn(f"scope.{field}", completed.stderr)
        manifest = copy.deepcopy(self.manifest)
        del manifest["scope"]
        self.assertNotEqual(0, self.verify(manifest).returncode)

    def test_legacy_labels_cannot_substitute_for_observed_contract_coverage(self):
        for case_id, false_coverage in (("build-repair", ["build repair"]),
                                        ("task-reconnect", ["task event reconnect"]),
                                        ("recovery-restore", ["recovery point restore"])):
            with self.subTest(case=case_id):
                manifest = copy.deepcopy(self.manifest)
                case = next(case for case in manifest["cases"] if case["id"] == case_id)
                case["observedCoverage"] = false_coverage
                completed = self.verify(manifest)
                self.assertNotEqual(0, completed.returncode)
                self.assertIn("observedCoverage", completed.stderr)

    def test_renaming_legacy_id_or_removing_observed_coverage_fails(self):
        manifest = copy.deepcopy(self.manifest)
        manifest["cases"][0]["id"] = "renamed-create-case"
        self.assertIn("legacy case IDs", self.verify(manifest).stderr)
        manifest = copy.deepcopy(self.manifest)
        del manifest["cases"][0]["observedCoverage"]
        self.assertNotEqual(0, self.verify(manifest).returncode)


if __name__ == "__main__":
    unittest.main()
