import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "ci" / "select_checks.py"
spec = importlib.util.spec_from_file_location("ci_selection", SCRIPT)
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)

CHECKS = {"java", "windows", "ui", "contract", "python", "typescript"}


class SelectionAssertions:
    def assert_checks(self, plan, expected):
        self.assertEqual({name: name in expected for name in CHECKS}, plan["checks"])
        self.assertTrue(all(type(value) is bool for value in plan["checks"].values()))


class PathSelectionTest(SelectionAssertions, unittest.TestCase):
    def test_linux_pr_ui_exclusions_match_the_shared_allowlist(self):
        workflow = (SCRIPT.parents[2] / ".github/workflows/stage15-linux-candidate.yml").read_text(encoding="utf-8")
        # This workflow intentionally lists one quoted path per line. Scope the
        # comparison to PR paths: main pushes have different integration coverage.
        pull_request = re.search(
            r"(?ms)^  pull_request:[ \t]*\n(.*?)(?=^ {0,2}[A-Za-z_][\w-]*:|\Z)", workflow,
        )
        self.assertIsNotNone(pull_request, "The Linux workflow must declare its PR path scope")
        paths = re.search(
            r"(?ms)^    paths:[ \t]*\n(.*?)(?=^ {0,4}[A-Za-z_][\w-]*:|\Z)", pull_request.group(1),
        )
        self.assertIsNotNone(paths, "The Linux PR workflow must declare its path filters")
        exclusions = {
            path for _, path in re.findall(
                r'''(?m)^      - (["'])!(ui-shell/[^"'\r\n]+)\1[ \t]*(?:#.*)?$''', paths.group(1),
            )
        }
        expected = set(selection.UI_FILES) | {
            "ui-shell/README.md", "ui-shell/e2e/**", "ui-shell/tests/**",
        }
        self.assertEqual(expected, exclusions)

    def test_known_layers_keep_shared_and_runtime_boundaries(self):
        cases = (
            ("docs/user/README.md", set()),
            ("README.zh-CN.md", set()),
            ("sdk/python/copperbench.py", {"python"}),
            ("sdk/python/test_mcp_client.py", {"python"}),
            ("sdk/typescript/copperbench.ts", {"typescript"}),
            ("sdk/tests/mcp-fixtures.json", {"python", "typescript"}),
            ("ui-shell/src/components/ProcedureWorkbench.tsx", {"ui", "contract"}),
            ("ui-shell/src/components/TaskDrawer.tsx", CHECKS),
            ("ui-shell/src/styles/tokens.css", CHECKS),
            ("ui-shell/src/hooks/useDialogA11y.ts", CHECKS),
            ("ui-shell/src/mock/newRuntimeFixture.ts", CHECKS),
            ("ui-shell/src/bridge/JcefCoreBridge.ts", CHECKS),
            ("ui-core/schemas/v1.0/command.schema.json", CHECKS),
            ("ui-core/fixtures/v1.0/release/release-notes.json", CHECKS),
            ("sdk/python/native_smoke.py", CHECKS),
            ("sdk/experimental-extension/manifest.json", CHECKS),
            (".github/workflows/test.yml", CHECKS),
            ("scripts/ci/select_checks.py", CHECKS),
            ("scripts/tests/test_ci_selection.py", CHECKS),
            ("new-runtime-layer/runtime.py", CHECKS),
        )
        for path, expected in cases:
            with self.subTest(path=path):
                self.assert_checks(selection.select_checks([path]), expected)

    def test_mixed_changes_union_layers_and_unknown_paths_force_all_checks(self):
        self.assert_checks(selection.select_checks([
            "README.md", "sdk/python/copperbench.py", "sdk/typescript/copperbench.ts",
        ]), {"python", "typescript"})
        self.assert_checks(selection.select_checks([
            "README.md", "sdk/python/copperbench.py", "new-runtime-layer/runtime.py",
        ]), CHECKS)
        self.assert_checks(selection.select_checks([]), CHECKS)

    def test_verify_plan_accepts_only_successful_complete_boolean_outputs(self):
        valid = dict.fromkeys(CHECKS, "false")
        selection.verify_plan("success", valid)
        selection.verify_plan("success", {**valid, "java": "true"})
        invalid = (
            ("failure", valid),
            ("cancelled", valid),
            ("skipped", valid),
            (None, valid),
            ("success", {}),
            ("success", {key: value for key, value in valid.items() if key != "windows"}),
            ("success", {**valid, "unexpected": "true"}),
            ("success", {**valid, "java": ""}),
            ("success", {**valid, "java": "TRUE"}),
            ("success", {**valid, "java": True}),
            ("success", list(CHECKS)),
        )
        for result, outputs in invalid:
            with self.subTest(result=result, outputs=outputs):
                with self.assertRaises(ValueError):
                    selection.verify_plan(result, outputs)


class GitSelectionTest(SelectionAssertions, unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="copperbench-ci-selection-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repository = self.root / "repository"
        self.repository.mkdir()
        # Global Git configuration must not change the fixture or execute hooks.
        environment = patch.dict(os.environ, {
            "GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_TERMINAL_PROMPT": "0",
        })
        environment.start()
        self.addCleanup(environment.stop)
        self.git("init", "--quiet", "--initial-branch=main")
        self.git("config", "user.name", "CI selection test")
        self.git("config", "user.email", "ci-selection@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("config", "core.autocrlf", "false")
        self.git("config", "core.filemode", "true")
        self.write("README.md", "Fixture documentation.\n")
        self.write("src/main/java/Feature.java", "class Feature {}\n")
        self.write("sdk/python/copperbench.py", "# MCP client fixture\n")
        self.base = self.commit("Initial fixture")

    def git(self, *arguments, input=None):
        return subprocess.run(
            ["git", *arguments], cwd=self.repository, input=input,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=15,
        ).stdout

    def write(self, relative, contents):
        path = self.repository / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(contents, encoding="utf-8")
        return path

    def commit(self, message, stage=True):
        if stage:
            self.git("add", "--all")
        self.git("commit", "--quiet", "-m", message)
        return self.git("rev-parse", "HEAD").decode("ascii").strip()

    def tree(self, filename=None):
        if filename is None:
            return self.git("mktree", input=b"").decode("ascii").strip()
        blob = self.git("hash-object", "-w", "--stdin", input=b"Fixture content.\n").decode("ascii").strip()
        entry = f"100644 blob {blob}\t{filename}\n".encode("ascii")
        return self.git("mktree", input=entry).decode("ascii").strip()

    def commit_tree(self, tree, *parents):
        arguments = ["commit-tree", tree]
        for parent in parents:
            arguments.extend(["-p", parent])
        return self.git(*arguments, input=b"Fixture commit.\n").decode("ascii").strip()

    def event(self, head, base=None):
        return {"pull_request": {"base": {"sha": base or self.base}, "head": {"sha": head}}}

    def event_file(self, event):
        path = self.root / "event.json"
        path.write_text(json.dumps(event), encoding="utf-8")
        return path

    def plan(self, event, event_name="pull_request", repository=None):
        return selection.plan_for_event(
            event_name, self.event_file(event), repository or self.repository,
        )

    def test_pr_merge_base_excludes_changes_made_only_on_the_base_branch(self):
        self.git("checkout", "--quiet", "-b", "feature")
        self.write("sdk/python/copperbench.py", "# Changed MCP client\n")
        head = self.commit("Change the Python MCP client")
        self.git("checkout", "--quiet", "main")
        self.write("src/main/java/BaseOnly.java", "class BaseOnly {}\n")
        advanced_base = self.commit("Base branch advances independently")

        event = self.event(head, advanced_base)
        self.assertEqual(["sdk/python/copperbench.py"], selection.changed_paths(self.repository, event))
        self.assert_checks(self.plan(event), {"python"})

    def test_deleted_java_source_still_requires_full_validation(self):
        (self.repository / "src/main/java/Feature.java").unlink()
        head = self.commit("Delete Java source")
        event = self.event(head)
        self.assertEqual(["src/main/java/Feature.java"], selection.changed_paths(self.repository, event))
        self.assert_checks(self.plan(event), CHECKS)

    def test_rename_from_java_into_docs_keeps_both_sides_of_the_change(self):
        destination = self.repository / "docs/Feature.md"
        destination.parent.mkdir()
        (self.repository / "src/main/java/Feature.java").rename(destination)
        head = self.commit("Move a source file into documentation")
        event = self.event(head)
        self.assertEqual({"src/main/java/Feature.java", "docs/Feature.md"},
                         set(selection.changed_paths(self.repository, event)))
        self.assert_checks(self.plan(event), CHECKS)

    def test_nul_delimited_names_and_mode_only_changes_are_preserved(self):
        names = ("docs/space and 中文.md", "docs/line\nbreak\t.md", "docs/literal\\backslash.md")
        for name in names:
            self.write(name, "Documentation.\n")
        self.git("add", "--all")
        # Change the Git executable bit, including on hosts without POSIX chmod.
        self.git("update-index", "--chmod=+x", "--", "sdk/python/copperbench.py")
        head = self.commit("Add unusual names and make the SDK executable", stage=False)
        event = self.event(head)
        self.assertEqual(set(names) | {"sdk/python/copperbench.py"},
                         set(selection.changed_paths(self.repository, event)))
        self.assert_checks(self.plan(event), {"python"})

    def test_unresolvable_pr_events_always_select_full_validation(self):
        events = (
            {},
            {"pull_request": None},
            {"pull_request": {"base": {}, "head": {"sha": self.base}}},
            self.event("refs/heads/main"),
            self.event("0" * 40),
        )
        for event in events:
            with self.subTest(event=event):
                self.assert_checks(self.plan(event), CHECKS)
        malformed = self.root / "malformed.json"
        malformed.write_text("{not valid JSON", encoding="utf-8")
        for path in (malformed, self.root / "missing.json"):
            with self.subTest(event_path=path.name):
                self.assert_checks(selection.plan_for_event("pull_request", path, self.repository), CHECKS)

    def test_git_failure_and_empty_diff_cannot_become_docs_only(self):
        self.assert_checks(self.plan(self.event(self.base), repository=self.root), CHECKS)
        empty = self.plan(self.event(self.base))
        self.assert_checks(empty, CHECKS)
        self.assertEqual(0, empty["changed_file_count"])

    def test_multiple_merge_bases_require_full_validation(self):
        common = self.commit_tree(self.tree())
        code_tree, docs_tree = self.tree("Feature.java"), self.tree("README.md")
        code_side = self.commit_tree(code_tree, common)
        docs_side = self.commit_tree(docs_tree, common)
        base = self.commit_tree(code_tree, code_side, docs_side)
        head = self.commit_tree(docs_tree, docs_side, code_side)
        self.assertEqual(2, len(self.git("merge-base", "--all", base, head).splitlines()))
        event = self.event(head, base)
        with self.assertRaisesRegex(ValueError, "exactly one merge base"):
            selection.changed_paths(self.repository, event)
        self.assert_checks(self.plan(event), CHECKS)

    def test_submodule_ignore_configuration_cannot_hide_runtime_changes_among_docs(self):
        sub_before = self.commit_tree(self.tree())
        sub_after = self.commit_tree(self.tree("runtime.py"), sub_before)
        self.write(".gitmodules", '[submodule "runtime"]\n'
                   '\tpath = vendor/runtime\n'
                   '\turl = https://example.invalid/runtime\n'
                   '\tignore = all\n')
        self.git("add", "--", ".gitmodules")
        self.git("update-index", "--add", "--cacheinfo", f"160000,{sub_before},vendor/runtime")
        # The gitlink exists only in the index; git add --all would remove it.
        base = self.commit("Add a runtime submodule ignored by default diff", stage=False)
        self.write("README.md", "Documentation changed along with the runtime.\n")
        self.git("add", "--", "README.md")
        self.git("update-index", "--cacheinfo", f"160000,{sub_after},vendor/runtime")
        head = self.commit("Update the runtime submodule and documentation", stage=False)

        # Establish that this fixture would actually be misclassified without the override.
        self.assertEqual(b"README.md\0", self.git("diff", "--name-only", "-z", base, head, "--"))
        event = self.event(head, base)
        self.assertEqual({"README.md", "vendor/runtime"},
                         set(selection.changed_paths(self.repository, event)))
        self.assert_checks(self.plan(event), CHECKS)

    def test_push_and_manual_events_are_full_even_when_the_pr_diff_would_be_docs_only(self):
        self.write("README.md", "Only documentation changed.\n")
        head = self.commit("Edit documentation")
        self.assert_checks(self.plan(self.event(head)), set())
        for event_name in ("push", "workflow_dispatch", "unknown_event"):
            with self.subTest(event_name=event_name):
                self.assert_checks(self.plan(self.event(head), event_name=event_name), CHECKS)

    def test_cli_emits_complete_boolean_outputs_without_copying_filenames(self):
        self.write("docs/name\nwindows=true.md", "Documentation.\n")
        head = self.commit("Edit documentation with a newline in its filename")
        output = self.root / "github-output.txt"
        summary = self.root / "github-summary.md"
        environment = {
            **os.environ,
            "GITHUB_EVENT_NAME": "pull_request",
            "GITHUB_EVENT_PATH": str(self.event_file(self.event(head))),
            "GITHUB_OUTPUT": str(output),
            "GITHUB_STEP_SUMMARY": str(summary),
        }
        process = subprocess.run(
            [sys.executable, str(SCRIPT)], cwd=self.repository, env=environment,
            capture_output=True, text=True, check=True, timeout=15,
        )
        self.assert_checks(json.loads(process.stdout), set())
        lines = output.read_text(encoding="utf-8").splitlines()
        self.assertEqual(len(CHECKS), len(lines))
        self.assertEqual({f"{name}=false" for name in CHECKS}, set(lines))
        self.assertIn("Markdown links and product status always run.", summary.read_text(encoding="utf-8"))
        # Exercise the workflow guard entry point, including a crashed classifier.
        for result, successful in (("success", True), ("failure", False), ("cancelled", False)):
            with self.subTest(result=result):
                guard = subprocess.run(
                    [sys.executable, str(SCRIPT), "--verify-plan"], cwd=self.repository,
                    env={**environment, "CI_PLAN_RESULT": result,
                         "CI_PLAN_OUTPUTS": json.dumps(dict.fromkeys(CHECKS, "false"))},
                    capture_output=True, text=True, timeout=15,
                )
                self.assertEqual(successful, guard.returncode == 0)


if __name__ == "__main__":
    unittest.main()
