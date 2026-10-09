"""S18-04/05 regression: independent Nightly gates and honest eval evidence scope.

These unit tests execute the aggregate gate and the contract runner with controlled
responses. They do not replace a Windows Nightly run or the loopback MCP harness.
"""

from contextlib import redirect_stdout
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[2]
RUNNER_PATH = ROOT / "scripts/run-ai-live-evals.py"
spec = importlib.util.spec_from_file_location("ai_contract_evals", RUNNER_PATH)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

SUITES = {
    "JAVA": "java-regression",
    "SDK": "sdk-regression",
    "UI": "ui-regression",
    "MCP": "mcp-regression",
    "REPOSITORY": "repository-checks",
}
LEGACY_IDS = {
    "create-element", "procedure-edit", "rename-reference", "build-repair",
    "revision-conflict", "readonly-denial", "datagen-cancel", "datagen-publish",
    "recovery-restore", "task-reconnect",
}
UNSUPPORTED_COVERAGE = ("modelDriven", "realBuild", "repairLoop", "transportReconnect", "gameplay")


class NightlyWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/nightly.yml").read_text(encoding="utf-8")
        # Match only top-level job bodies; no optional YAML dependency is needed.
        cls.jobs = dict(re.findall(
            r"(?ms)^  ([\w-]+):\n(.*?)(?=^  [\w-]+:\n|\Z)",
            cls.workflow.split("jobs:\n", 1)[1],
        ))

    def step(self, job, name):
        match = re.search(
            rf"(?ms)^      - name: {re.escape(name)}\n(.*?)(?=^      - name: |\Z)",
            self.jobs[job],
        )
        self.assertIsNotNone(match, f"Missing {job} step: {name}")
        return match.group(1)

    def test_suites_have_no_failure_cascade_and_keep_bounded_permissions(self):
        for job in SUITES.values():
            with self.subTest(job=job):
                self.assertIn(job, self.jobs)
                self.assertNotRegex(self.jobs[job], r"(?m)^    (needs|if):")
                self.assertIn("    runs-on: windows-latest\n", self.jobs[job])
                self.assertRegex(self.jobs[job], r"(?m)^    timeout-minutes: \d+$")
                self.assertIn("uses: actions/upload-artifact@v7\n        if: always()", self.jobs[job])
                self.assertIn("retention-days: 30", self.jobs[job])
        self.assertNotIn("continue-on-error:", self.workflow)
        self.assertIn("permissions:\n  contents: read\n  checks: write\n", self.workflow)
        self.assertIn('cron: "30 16 * * *"', self.workflow)
        self.assertIn("cancel-in-progress: false", self.workflow)

    def test_aggregate_keeps_name_and_observes_every_independent_suite(self):
        aggregate = self.jobs["product-regression"]
        self.assertIn("name: Nightly product regression\n", aggregate)
        self.assertIn("    if: always()\n", aggregate)
        needs = re.search(r"(?m)^    needs: \[(.+)\]$", aggregate)
        self.assertIsNotNone(needs)
        self.assertEqual(set(SUITES.values()), set(needs.group(1).split(", ")))
        for name, job in SUITES.items():
            self.assertIn(f"{name}_RESULT: ${{{{ needs.{job}.result }}}}", aggregate)
        self.assertIn("name: nightly-product-regression\n", aggregate)

    def test_aggregate_executes_and_rejects_failure_skipped_cancelled_or_missing(self):
        body = self.step("product-regression", "Report independent suite results")
        script = textwrap.dedent(body.split("        run: |\n", 1)[1])
        scenarios = [(None, "success")]
        scenarios += [(suite, state) for suite in SUITES
                      for state in ("failure", "skipped", "cancelled", "missing", "")]
        for failing_suite, state in scenarios:
            with self.subTest(suite=failing_suite, state=state), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                environment = {**os.environ, **{f"{name}_RESULT": "success" for name in SUITES},
                               "GITHUB_STEP_SUMMARY": str(directory / "actions-summary.md")}
                if failing_suite:
                    if state == "missing":
                        environment.pop(f"{failing_suite}_RESULT")
                    else:
                        environment[f"{failing_suite}_RESULT"] = state
                completed = subprocess.run(
                    [sys.executable, "-c", script], cwd=directory, env=environment,
                    capture_output=True, text=True, timeout=15,
                )
                self.assertEqual(1 if failing_suite else 0, completed.returncode, completed.stderr)
                report = json.loads((directory / "build/nightly-results/summary.json").read_text())
                self.assertEqual(set(SUITES), set(report["suites"]))
                self.assertEqual(4 if failing_suite else 5, report["passed"])
                self.assertEqual(1 if failing_suite else 0, report["failed"])
                if failing_suite:
                    self.assertEqual(state, report["suites"][failing_suite])
                    self.assertIn(f"::error::{failing_suite}", completed.stdout)
                for suite in SUITES:
                    self.assertIn(f"| {suite} |", (directory / "actions-summary.md").read_text())

    def test_original_checks_and_evidence_remain_in_their_suites(self):
        java = self.step("java-regression", "Run full Java, Javadoc, and scale regression")
        for value in ("--no-daemon", "--no-build-cache", "--continue clean test javadoc",
                      "-Dcopperbench.stage9.scale=true", "-Dcopperbench.uiWindowTimeoutSeconds=20"):
            self.assertIn(value, java)
        self.assertIn("build/reports/tests/", self.jobs["java-regression"])
        self.assertIn("python -m unittest discover -s sdk/python -p 'test_*.py'", self.jobs["sdk-regression"])
        self.assertIn("npm test --prefix ui-shell", self.jobs["sdk-regression"])
        self.assertIn("npm run test:sdk --prefix ui-shell", self.jobs["sdk-regression"])
        self.assertIn("npm test --prefix ui-core", self.jobs["ui-regression"])
        self.assertIn("npm run build --prefix ui-shell", self.jobs["ui-regression"])
        self.assertIn("npx playwright test --project=chromium", self.jobs["ui-regression"])
        self.assertIn("ui-shell/test-results/", self.jobs["ui-regression"])
        self.assertIn("ui-shell/playwright-report/", self.jobs["ui-regression"])
        self.assertIn("verify-mcp-conformance.ps1 -OutputDirectory build/nightly-results/mcp", self.jobs["mcp-regression"])
        self.assertIn("verify-markdown-links.mjs", self.jobs["repository-checks"])
        self.assertIn("verify-product-status.mjs", self.jobs["repository-checks"])

    def test_clean_does_not_delete_or_lock_its_own_java_log(self):
        step = self.step("java-regression", "Run full Java, Javadoc, and scale regression")
        log = re.search(r"Tee-Object ([^\s]+)", step)
        self.assertIsNotNone(log)
        self.assertNotIn("build", Path(log.group(1)).parts)
        self.assertIn(str(Path(log.group(1)).parent).replace("\\", "/") + "/",
                      self.step("java-regression", "Upload Java regression evidence"))

    def test_tests_continue_only_after_their_own_dependencies_pass(self):
        dependency_gate = "if: ${{ !cancelled() && steps.dependencies.outcome == 'success' }}"
        for name in ("Run production bridge regression", "Run TypeScript SDK regression"):
            self.assertIn(dependency_gate, self.step("sdk-regression", name))
        self.assertIn("id: dependencies", self.step("sdk-regression", "Install UI shell dependencies"))
        self.assertIn(dependency_gate, self.step("ui-regression", "Build production UI"))
        self.assertIn("id: dependencies", self.step("ui-regression", "Install UI dependencies"))
        self.assertIn("id: build", self.step("ui-regression", "Build production UI"))
        self.assertIn("if: ${{ !cancelled() && steps.build.outcome == 'success' }}",
                      self.step("ui-regression", "Run full Playwright suite"))
        # A failed compiler must prevent the MCP host from being launched.
        self.assertNotIn("if:", self.step("mcp-regression", "Run MCP conformance"))
        self.assertLess(self.jobs["mcp-regression"].index("Compile MCP test host"),
                        self.jobs["mcp-regression"].index("Run MCP conformance"))
        self.assertIn("testClasses", self.step("mcp-regression", "Compile MCP test host"))

    def test_eight_generator_tracks_and_resource_limits_are_preserved(self):
        generator = self.jobs["stage9-generator-golden"]
        tracks = re.findall(r"(?m)^          - ((?:fabric|neoforge)-[^\r\n]+)$", generator)
        self.assertEqual([
            "fabric-26.2", "neoforge-26.2", "fabric-26.1.2", "neoforge-26.1.2",
            "fabric-1.21.1", "neoforge-1.21.1", "fabric-1.20.1", "neoforge-1.20.1",
        ], tracks)
        for setting in ("timeout-minutes: 90", "fail-fast: false", "max-parallel: 2",
                        "NewWorkspaceGeneratorGoldenBuildTest", "COPPERBENCH_JDK21_HOME",
                        "-Dcopperbench.stage9.workspaceGeneratorBuild=true",
                        "-Dcopperbench.stage9.workspaceGeneratorId=${{ matrix.generator }}",
                        "build/stage9-workspace-generator-logs/", "retention-days: 30"):
            self.assertIn(setting, generator)
        self.assertNotRegex(generator, r"(?m)^    needs:")


class EvalReportTest(unittest.TestCase):
    def workspace_client(self):
        client = Mock(spec=runner.CopperbenchClient)
        client.get_workspace.return_value = {"data": {"workspace": {"revision": 0}}}
        client.create_mod_element.side_effect = [
            {"status": "committed", "newRevision": 1},
            {"status": "committed", "newRevision": 2, "data": {"element": {"id": "procedure"}}},
            runner.CopperbenchError("stale", "WORKSPACE_REVISION_CONFLICT"),
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
