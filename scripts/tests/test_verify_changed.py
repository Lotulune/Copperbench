import contextlib
import importlib.util
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "verify-changed.py"
spec = importlib.util.spec_from_file_location("verify_changed", SCRIPT)
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


def environment(**gaps):
    names = ("node", "npm", "pwsh", "python", "git", "jdk", "jcef", "display", "windows",
             "ui-core-dependencies", "ui-shell-dependencies", "playwright", "chromium")
    return {name: {"available": name not in gaps, "reason": gaps.get(name)} for name in names}


# Captured Node 24 output from the real product-release-history contributor run.
# Keep a self-contained excerpt so CI does not depend on local output/ evidence.
NODE24_SPEC_OUTPUT = """✔ a stable patch retains the original published Beta version (1.3833ms)
✔ active Beta releases still require the current product version (1.9431ms)
✔ unpublished, future, malformed and non-stable histories are rejected (0.2972ms)
ℹ tests 3
ℹ suites 0
ℹ pass 3
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 114.0467
"""


class TestCountParsingTest(unittest.TestCase):
    def test_node24_default_reporter_retains_the_actual_emitted_summary(self):
        self.assertEqual(verifier.test_counts(NODE24_SPEC_OUTPUT), {
            "tests": 3, "suites": 0, "passed": 3, "failed": 0, "cancelled": 0,
            "skipped": 0, "todo": 0, "source": "command output"})

    def test_node_spec_and_tap_preserve_fail_skip_cancel_and_todo_counts(self):
        for prefix in ("#", "ℹ", "ℹ️"):
            with self.subTest(prefix=prefix):
                summary = "\r\n".join(f"\x1b[36m{prefix} {name} {count}\x1b[39m" for name, count in (
                    ("tests", 8), ("suites", 1), ("pass", 3), ("fail", 2),
                    ("cancelled", 1), ("skipped", 1), ("todo", 1)))
                self.assertEqual(verifier.test_counts(summary), {
                    "tests": 8, "suites": 1, "passed": 3, "failed": 2, "cancelled": 1,
                    "skipped": 1, "todo": 1, "source": "command output"})

    def test_partial_and_ambiguous_node_summaries_do_not_manufacture_counts(self):
        self.assertEqual(verifier.test_counts("# tests 3\n# pass 2\n"), {
            "tests": 3, "passed": 2, "source": "command output"})
        self.assertIsNone(verifier.test_counts("ℹ pass 3\nℹ fail 0\n"))
        self.assertIsNone(verifier.test_counts("ℹ tests 3\nℹ pass 3\nℹ tests 2\nℹ pass 2\n"))
        self.assertIsNone(verifier.test_counts("Expected output: ℹ tests 3\n"))
        self.assertEqual(verifier.test_counts("Ran 7 tests in 0.1s\nOK\n"), {
            "tests": 7, "source": "command output"})
        self.assertEqual(verifier.test_counts("  3 passed (1.0s)\n  1 skipped\n"), {
            "passed": 3, "failed": 0, "skipped": 1, "source": "command output"})


class VerificationPlanTest(unittest.TestCase):
    def test_docs_sdk_ui_unknown_and_mixed_plans_use_the_exact_shared_selector(self):
        for paths in (["README.md"], ["sdk/python/copperbench.py"], ["sdk/typescript/copperbench.ts"],
                      ["ui-shell/e2e/scenarios.spec.ts"], ["unknown/new-layer.ts"],
                      ["README.md", "sdk/python/copperbench.py", "ui-shell/e2e/scenarios.spec.ts"], []):
            with self.subTest(paths=paths):
                with patch.object(verifier.selection, "select_checks", wraps=verifier.selection.select_checks) as route:
                    plan = verifier.make_plan(paths, environment(), windows=True)
                    route.assert_called_once_with(paths)
                self.assertEqual(plan["selection"], verifier.selection.select_checks(paths))
                self.assertEqual(plan["selectionSource"], "scripts/ci/select_checks.py")

    def test_planned_auxiliary_commands_match_existing_ci_commands_and_conditions(self):
        workflow = (SCRIPT.parents[1] / ".github/workflows/test.yml").read_text(encoding="utf-8")
        ci_steps = {
            "ci-selection": ("Test change selection and failure handling", None,
                [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_ci_selection.py"]),
            "agent-readiness-unit": ("Test installed-run provenance and study evidence accounting", None,
                [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_agent_readiness*.py"]),
            "product-release-history": ("Validate product status", None,
                ["node", "--test", "scripts/tests/product-release-history.tests.mjs"]),
            "performance-tooling": ("Test performance tooling and source identity without a browser",
                "needs.changes.outputs.ui == 'true' || needs.changes.outputs.contract == 'true'",
                ["node", "--experimental-strip-types", "--test", "scripts/tests/performance-statistics.tests.mjs",
                 "scripts/tests/performance-fixtures.tests.mjs", "scripts/tests/performance-runner.tests.mjs",
                 "scripts/tests/source-identity.tests.mjs", "scripts/tests/native-observation.tests.mjs",
                 "scripts/tests/task-evaluation.tests.mjs"]),
            "contributor-unit": ("Test contributor verification planning and reporting",
                "needs.changes.outputs.python == 'true'",
                [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_verify_changed.py"]),
            "acceptance-measurement-unit": ("Test acceptance measurement planning and collection",
                "needs.changes.outputs.python == 'true'",
                [sys.executable, "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_acceptance_measurement.py"]),
        }
        for identifier, (name, condition, argv) in ci_steps.items():
            match = re.search(r"(?ms)^      - name: " + re.escape(name) + r"\n(.*?)(?=^      - name: |\Z)", workflow)
            self.assertIsNotNone(match, f"CI step for {identifier} was removed or renamed")
            block = match.group(1)
            actual_condition = re.search(r"^        if: (.+)$", block, re.MULTILINE)
            self.assertEqual(condition, actual_condition.group(1) if actual_condition else None)
            normalized = " ".join(block.replace("'", "").split())
            ci_argv = argv[1:] if argv[0] == sys.executable else argv
            self.assertIn(" ".join(ci_argv), normalized, f"CI command for {identifier} changed")
        for paths in (["README.md"], ["sdk/python/copperbench.py"], ["sdk/typescript/copperbench.ts"],
                      ["ui-core/tests/schema.test.mjs"], ["ui-shell/e2e/scenarios.spec.ts"],
                      ["scripts/verify-changed.py"],
                      ["README.md", "sdk/python/copperbench.py", "ui-shell/e2e/scenarios.spec.ts"]):
            with self.subTest(paths=paths):
                plan = verifier.make_plan(paths, environment(), windows=True)
                checks = plan["selection"]["checks"]
                required = {"ci-selection", "agent-readiness-unit", "product-release-history"}
                if checks["ui"] or checks["contract"]:
                    required.add("performance-tooling")
                if checks["python"]:
                    required.update({"contributor-unit", "acceptance-measurement-unit"})
                commands = {item["id"]: item for item in plan["commands"]}
                self.assertEqual(set(ci_steps) & commands.keys(), required)
                for identifier in required:
                    self.assertEqual(commands[identifier]["argv"], ci_steps[identifier][2])
                    self.assertEqual(commands[identifier]["status"], "planned")

    def test_optional_native_environment_does_not_block_docs(self):
        plan = verifier.make_plan(["README.md"], environment(jdk="JDK absent", jcef="JCEF absent", display="Display absent"))
        self.assertEqual([item["id"] for item in plan["commands"]],
                         ["ci-selection", "agent-readiness-unit", "markdown-links", "product-release-history", "product-status"])
        self.assertTrue(all(item["status"] == "planned" for item in plan["commands"]))

    def test_main_evidence_and_full_browser_checks_are_retained_without_reimplementing_selection(self):
        workflow = (SCRIPT.parents[1] / ".github/workflows/test.yml").read_text(encoding="utf-8")
        for paths in (["README.md"], ["sdk/python/copperbench.py"], ["ui-shell/src/components/ElementInspector.tsx"],
                      ["ui-shell/e2e/scenarios.spec.ts"], ["unknown/shared-layer"]):
            with self.subTest(paths=paths):
                plan = verifier.make_plan(paths, environment(), allow_downloads=True, windows=True)
                commands = {item["id"]: item for item in plan["commands"]}
                self.assertEqual("ui-full-e2e" in commands, plan["selection"]["ui_full_e2e"])
                expected = {
                    "portable-evidence-unit": "tools/portable-evidence",
                    "candidate-evidence-unit": "tools/candidate-evidence",
                    "generator-probes-unit": "tools/generator-probes",
                    "ci-runner-unit": "scripts/ci",
                }
                for identifier, directory in expected.items():
                    self.assertEqual(identifier in commands, plan["selection"]["checks"]["python"])
                    argv = ["-m", "unittest", "discover", "-s", directory, "-p", "test_*.py"]
                    self.assertIn(" ".join(argv), workflow.replace("'", ""))
                    if identifier in commands:
                        self.assertEqual([sys.executable, *argv], commands[identifier]["argv"])
                self.assertEqual("loom-normalization" in commands, plan["selection"]["checks"]["java"])
                if "loom-normalization" in commands:
                    self.assertEqual([sys.executable, "tools/generator-probes/integration_loom_normalization.py"],
                                     commands["loom-normalization"]["argv"])
                    self.assertIn("tools/generator-probes/integration_loom_normalization.py", workflow)
                    self.assertEqual(["java"], commands["loom-normalization"]["dependsOn"])

    def test_windows_plan_retains_main_boundary_classes_and_separate_packaging_gates(self):
        workflow = (SCRIPT.parents[1] / ".github/workflows/test.yml").read_text(encoding="utf-8")
        block = re.search(r"(?ms)- name: Test filesystem boundaries and native accessibility on Windows\n(.*?)(?=^      - name:)", workflow)
        self.assertIsNotNone(block)
        expected_classes = re.findall(r"--tests\s+(\S+)", block.group(1))
        self.assertTrue(expected_classes)
        plan = verifier.make_plan(["unknown/shared-layer"], environment(display="Headless session"),
                                  allow_downloads=True, windows=True)
        commands = {item["id"]: item for item in plan["commands"]}
        boundary = commands["windows-filesystem"]
        actual_classes = [boundary["argv"][index + 1] for index, item in enumerate(boundary["argv"]) if item == "--tests"]
        self.assertEqual(expected_classes, actual_classes)
        self.assertEqual("blocked", boundary["status"])
        for identifier in ("windows-nsis-setup", "windows-package-identity-build"):
            self.assertEqual("blocked", commands[identifier]["status"])
            self.assertTrue(any("Manual/CI" in gap for gap in commands[identifier]["environmentGaps"]))
        self.assertEqual(["windows-package-identity-build"], commands["windows-application-identity"]["dependsOn"])
        self.assertIn("test_eval_contracts.py", commands["eval-contracts"]["argv"])

    def test_missing_auxiliary_prerequisites_are_blocked_instead_of_omitted(self):
        plan = verifier.make_plan(["ui-shell/e2e/scenarios.spec.ts"], environment(git="Git absent"))
        commands = {item["id"]: item for item in plan["commands"]}
        for identifier in ("ci-selection", "performance-tooling"):
            self.assertEqual(commands[identifier]["status"], "blocked")
            self.assertTrue(any("Git absent" in gap for gap in commands[identifier]["environmentGaps"]))
        self.assertEqual(commands["product-release-history"]["status"], "planned")

    def test_java_builds_ui_once_and_a_java_gap_keeps_available_ui_build_planned(self):
        paths = ["src/main/java/Feature.java"]
        plan = verifier.make_plan(paths, environment(), allow_downloads=True, windows=True)
        ids = [item["id"] for item in plan["commands"]]
        self.assertNotIn("ui-build", ids)
        self.assertNotIn("windows-filesystem", ids)
        self.assertEqual(next(item for item in plan["commands"] if item["id"] == "ui-smoke")["dependsOn"], ["java"])
        blocked = verifier.make_plan(paths, environment(display="No visible display"), allow_downloads=True, windows=True)
        self.assertEqual(next(item for item in blocked["commands"] if item["id"] == "java")["status"], "blocked")
        self.assertEqual(next(item for item in blocked["commands"] if item["id"] == "ui-build")["status"], "planned")

    def test_downloads_and_external_tool_installation_are_never_implicit(self):
        plan = verifier.make_plan(["unrecognized.py"], environment(), windows=True)
        java = next(item for item in plan["commands"] if item["id"] == "java")
        self.assertEqual(java["status"], "blocked")
        self.assertTrue(any("--allow-downloads" in reason for reason in java["environmentGaps"]))
        opted = verifier.make_plan(["unrecognized.py"], environment(), allow_downloads=True, windows=True)
        conformance = next(item for item in opted["commands"] if item["id"] == "mcp-conformance")
        self.assertEqual(conformance["status"], "blocked")
        self.assertTrue(any("npx --yes" in reason for reason in conformance["environmentGaps"]))
        self.assertFalse(any("ci" in item["argv"][1:] or "install" in item["argv"][1:] for item in opted["commands"]))

    def test_changed_path_collection_preserves_deletions_both_rename_sides_and_untracked_names(self):
        def fake_git(root, *args):
            if args[0] == "diff":
                return b"src/Old.java\0docs/Renamed.md\0sdk/python/copperbench.py\0"
            if args[0] == "ls-files":
                return "docs/space and 中文.md\0docs/line\nbreak.md\0".encode("utf-8")
            raise AssertionError(args)
        with patch.object(verifier, "git", side_effect=fake_git):
            paths = verifier.changed_paths(Path("fixture"))
        self.assertEqual(set(paths), {"src/Old.java", "docs/Renamed.md", "sdk/python/copperbench.py",
                                    "docs/space and 中文.md", "docs/line\nbreak.md"})

    def test_default_dry_run_never_executes_checks_or_creates_a_report(self):
        output = io.StringIO()
        with patch("sys.argv", [str(SCRIPT), "--paths", "README.md"]), \
                patch.object(verifier, "inspect_environment", return_value=(environment(), None)), \
                patch.object(verifier, "run_plan") as execute, contextlib.redirect_stdout(output):
            self.assertEqual(verifier.main(), 0)
        execute.assert_not_called()
        self.assertEqual(json.loads(output.getvalue())["status"], "planned")


class ExecutionReportTest(unittest.TestCase):
    def test_node_default_counts_are_persisted_without_overriding_command_failure(self):
        plan = verifier.make_plan(["README.md"], environment(), windows=False)
        identity = {"status": "recorded", "commit": "a" * 40, "sourceSha256": "b" * 64, "dirtyDeltaSha256": "c" * 64}
        def fake_run(argv, cwd, child_environment, timeout):
            if "scripts/tests/product-release-history.tests.mjs" in argv:
                failed_output = (NODE24_SPEC_OUTPUT.replace("ℹ pass 3", "ℹ pass 1")
                                 .replace("ℹ fail 0", "ℹ fail 1").replace("ℹ skipped 0", "ℹ skipped 1"))
                return subprocess.CompletedProcess(argv, 1, failed_output, "")
            return subprocess.CompletedProcess(argv, 0, "Check completed without a test summary.\n", "")
        with tempfile.TemporaryDirectory(prefix="copperbench-verify-node-summary-") as temporary, \
                patch.object(verifier, "source_identity", return_value=identity), \
                patch.object(verifier, "execution_argv", side_effect=lambda argv, root: argv), \
                patch.object(verifier, "run_bounded", side_effect=fake_run):
            self.assertEqual(verifier.run_plan(plan, Path(temporary), Path(temporary), None, 5), 1)
            persisted = json.loads((Path(temporary) / "report.json").read_text(encoding="utf-8"))
        command = next(item for item in persisted["commands"] if item["id"] == "product-release-history")
        self.assertEqual(command["status"], "failed")
        self.assertEqual(command["exitCode"], 1)
        self.assertEqual(command["testCounts"]["tests"], 3)
        self.assertEqual(command["testCounts"]["passed"], 1)
        self.assertEqual(command["testCounts"]["failed"], 1)
        self.assertEqual(command["testCounts"]["skipped"], 1)

    def test_run_keeps_each_selected_ci_auxiliary_command_and_its_actual_result(self):
        for paths in (["README.md"], ["sdk/python/copperbench.py"], ["ui-core/tests/schema.test.mjs"],
                      ["README.md", "sdk/python/copperbench.py", "ui-shell/e2e/scenarios.spec.ts"]):
            with self.subTest(paths=paths):
                plan = verifier.make_plan(paths, environment(), windows=False)
                identity = {"status": "recorded", "commit": "a" * 40, "sourceSha256": "b" * 64, "dirtyDeltaSha256": "c" * 64}
                executed = []
                def fake_run(argv, cwd, child_environment, timeout):
                    executed.append(argv)
                    return subprocess.CompletedProcess(argv, 0, "Ran 3 tests in 0.1s\nOK\n", "")
                with tempfile.TemporaryDirectory(prefix="copperbench-verify-ci-commands-") as temporary, \
                        patch.object(verifier, "source_identity", return_value=identity), \
                        patch.object(verifier, "execution_argv", side_effect=lambda argv, root: argv), \
                        patch.object(verifier, "run_bounded", side_effect=fake_run):
                    self.assertEqual(verifier.run_plan(plan, Path(temporary), Path(temporary), None, 5), 0)
                    report = json.loads((Path(temporary) / "report.json").read_text(encoding="utf-8"))
                self.assertEqual(executed, [command["argv"] for command in plan["commands"]])
                for command in report["commands"]:
                    if command["id"] in {"ci-selection", "product-release-history", "performance-tooling", "contributor-unit", "acceptance-measurement-unit"}:
                        self.assertEqual(command["status"], "passed")
                        self.assertEqual(command["exitCode"], 0)
                        self.assertEqual(command["testCounts"]["tests"], 3)

    def test_exit_counts_blocked_prerequisites_and_environment_gaps_cannot_become_passes(self):
        plan = verifier.make_plan(["ui-shell/e2e/scenarios.spec.ts"], environment(), windows=False)
        identity = {"status": "recorded", "commit": "a" * 40, "sourceSha256": "b" * 64, "dirtyDeltaSha256": "c" * 64}
        def fake_run(argv, cwd, child_environment, timeout):
            if "build" in argv:
                return subprocess.CompletedProcess(argv, 1, "build failed", "")
            return subprocess.CompletedProcess(argv, 0, "# tests 3\n# pass 3\n", "")
        with tempfile.TemporaryDirectory(prefix="copperbench-verify-report-") as temporary, \
                patch.object(verifier, "source_identity", return_value=identity), \
                patch.object(verifier, "execution_argv", side_effect=lambda argv, root: argv), \
                patch.object(verifier, "run_bounded", side_effect=fake_run):
            code = verifier.run_plan(plan, Path(temporary), Path(temporary), None, 5)
            persisted = json.loads((Path(temporary) / "report.json").read_text(encoding="utf-8"))
        self.assertEqual(code, 1)
        smoke = next(item for item in plan["commands"] if item["id"] == "ui-smoke")
        self.assertEqual(smoke["status"], "blocked")
        self.assertIsNone(smoke["exitCode"])
        self.assertEqual(next(item for item in persisted["commands"] if item["id"] == "ui-unit")["testCounts"]["tests"], 3)

    def test_timeout_and_unknown_counts_are_reported_explicitly(self):
        plan = verifier.make_plan(["README.md"], environment(), windows=False)
        identity = {"status": "recorded", "commit": "a" * 40, "sourceSha256": "b" * 64, "dirtyDeltaSha256": "c" * 64}
        with tempfile.TemporaryDirectory(prefix="copperbench-verify-timeout-") as temporary, \
                patch.object(verifier, "source_identity", return_value=identity), \
                patch.object(verifier, "execution_argv", side_effect=lambda argv, root: argv), \
                patch.object(verifier, "run_bounded", side_effect=subprocess.TimeoutExpired(["fixture"], 1, output="partial")):
            self.assertEqual(verifier.run_plan(plan, Path(temporary), Path(temporary), None, 1), 1)
        self.assertTrue(all(item["status"] == "timeout" for item in plan["commands"]))
        self.assertTrue(all(item["exitCode"] is None for item in plan["commands"]))
        self.assertIsNone(verifier.test_counts("No count was emitted."))
        self.assertEqual(verifier.test_counts("Ran 7 tests in 0.1s")["tests"], 7)


if __name__ == "__main__":
    unittest.main()
