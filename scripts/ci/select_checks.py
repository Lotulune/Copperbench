"""Select PR checks with a small allowlist; every uncertain change runs everything."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess


CHECKS = ("java", "windows", "ui", "contract", "python", "typescript")
PLAN_OUTPUTS = (*CHECKS, "ui_full_e2e")
ROOT_DOCS = {
    "AGENTS.md", "CHANGES-FROM-UPSTREAM.md", "CONTEXT.md", "CONTRIBUTING.md",
    "README.md", "README.zh-CN.md", "UPSTREAM.md",
}
SDK_DOCS = {"sdk/README.md", "sdk/python/README.md", "ui-shell/README.md", "ui-core/README.md"}
# These components render ordinary workbench views. New components, shell startup,
# native bridges, shared contracts, build inputs, and unknown files stay full.
UI_COMPONENTS = {
    "AdvancementWorkbench.tsx", "CreatorDataView.tsx", "ElementInspector.tsx",
    "FunctionWorkbench.tsx", "GuiWorkbench.tsx", "HistoryView.tsx",
    "LanguageImportModal.tsx", "LootTableWorkbench.tsx", "ModElementsWorkbench.tsx",
    "OverlayWorkbench.tsx", "ProcedureWorkbench.tsx",
    "TracksAndMigrationView.tsx", "blockbenchSetup.css", "pythonWorkbench.css",
    "taskAuthorization.css",
}
# Changes to browser configuration or shared mock fixtures can affect any spec.
UI_E2E_FILES = {
    *("ui-shell/src/mock/" + name for name in ("assetFixtures.ts", "mockBridge.ts", "scenarios.ts", "windowBridge.ts")),
    "ui-shell/playwright.config.ts", "ui-shell/playwright.stage17-matrix.config.ts",
}
UI_FILES = {
    *("ui-shell/src/components/" + name for name in UI_COMPONENTS),
    *("ui-shell/src/i18n/" + name for name in ("en.ts", "enUi.ts", "zh.ts", "blocklyZh.ts", "labels.ts")),
    *UI_E2E_FILES, "ui-shell/src/content/userGuide.ts",
}


def full_plan(reason):
    return {"checks": dict.fromkeys(CHECKS, True), "ui_full_e2e": True, "reason": reason}


def checks_for_path(path):
    if path in ROOT_DOCS or path in SDK_DOCS or (path.startswith("docs/") and path.endswith(".md")):
        return set()
    if path == "sdk/python/copperbench.py" or re.fullmatch(r"sdk/python/test_[^/]+\.py", path):
        return {"python"}
    if path.startswith("sdk/typescript/"):
        return {"typescript"}
    if path == "sdk/protocol.md" or path.startswith("sdk/tests/"):
        return {"python", "typescript"}
    if path in UI_FILES or path.startswith(("ui-shell/tests/", "ui-shell/e2e/")):
        return {"ui", "contract"}
    if path.startswith(("ui-core/tests/", "ui-core/scripts/")):
        return {"contract"}
    return set(CHECKS)


def select_checks(paths):
    if not paths:
        return full_plan("No complete changed-file list; run all checks.")
    selected = set().union(*(checks_for_path(path) for path in paths))
    reason = "A shared, runtime, build, CI, or unrecognized path requires all checks." if selected == set(CHECKS) else "PR checks selected from the changed layers."
    full_e2e = selected == set(CHECKS) or any(
        path in UI_E2E_FILES or path.startswith("ui-shell/e2e/") for path in paths
    )
    return {"checks": {name: name in selected for name in CHECKS}, "ui_full_e2e": full_e2e, "reason": reason}


def changed_paths(repository, event):
    pr = event["pull_request"]
    base, head = pr["base"]["sha"], pr["head"]["sha"]
    if not all(isinstance(sha, str) and re.fullmatch(r"[0-9a-f]{40}", sha) for sha in (base, head)):
        raise ValueError("Expected full commit SHAs")

    def git(*args):
        return subprocess.run(
            ["git", *args], cwd=repository, check=True, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, timeout=60,
        ).stdout

    merge_bases = git("merge-base", "--all", base, head).decode("ascii").splitlines()
    if len(merge_bases) != 1 or not re.fullmatch(r"[0-9a-f]{40}", merge_bases[0]):
        raise ValueError("Expected exactly one merge base")
    # --no-renames includes both sides of a move, including a move into docs/.
    raw = git("diff", "--no-ext-diff", "--no-textconv", "--ignore-submodules=none", "--no-renames", "--name-only", "-z", merge_bases[0], head, "--")
    if raw and not raw.endswith(b"\0"):
        raise ValueError("Incomplete changed-file list")
    return [path.decode("utf-8", errors="surrogateescape") for path in raw.split(b"\0")[:-1]]


def plan_for_event(event_name, event_path, repository):
    # Keep a complete integration run on main and an explicit manual escape hatch.
    if event_name != "pull_request":
        return full_plan("Main pushes, manual runs, and other events run all checks.")
    try:
        event = json.loads(Path(event_path).read_text(encoding="utf-8"))
        paths = changed_paths(repository, event)
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        return full_plan("The complete PR diff could not be resolved; run all checks.")
    plan = select_checks(paths)
    plan["changed_file_count"] = len(paths)
    return plan


def verify_plan(result, outputs):
    if result != "success" or not isinstance(outputs, dict) or set(outputs) != set(PLAN_OUTPUTS) or any(value not in ("true", "false") for value in outputs.values()):
        raise ValueError("CI change selection failed or returned missing/invalid outputs")
    if outputs["ui_full_e2e"] == "true" and outputs["ui"] != "true":
        raise ValueError("Full Chromium selection requires UI checks")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paths", nargs="+", help="Preview the checks for explicit repository-relative paths")
    parser.add_argument("--verify-plan", action="store_true", help="Reject a failed or malformed prerequisite plan")
    args = parser.parse_args()
    if args.verify_plan:
        verify_plan(os.environ.get("CI_PLAN_RESULT"), json.loads(os.environ.get("CI_PLAN_OUTPUTS", "{}")))
        return

    plan = select_checks(args.paths) if args.paths else plan_for_event(
        os.environ.get("GITHUB_EVENT_NAME"), os.environ.get("GITHUB_EVENT_PATH"), Path.cwd(),
    )
    print(json.dumps(plan, indent=2))
    if not args.paths and os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            for name, enabled in {**plan["checks"], "ui_full_e2e": plan["ui_full_e2e"]}.items():
                output.write(f"{name}={str(enabled).lower()}\n")
    if not args.paths and os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write("## Selected PR checks\n\n" + plan["reason"] + "\n\n")
            summary.write("Markdown links and product status always run.\n\n| Check | Run |\n| --- | --- |\n")
            for name, enabled in plan["checks"].items():
                summary.write(f"| {name} | {'yes' if enabled else 'skip'} |\n")
            if plan["checks"]["ui"]:
                summary.write("\nChromium coverage: " + ("full suite" if plan["ui_full_e2e"] else "five smoke specs") + ".\n")


if __name__ == "__main__":
    main()
