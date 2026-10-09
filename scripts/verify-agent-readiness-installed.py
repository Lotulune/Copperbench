#!/usr/bin/env python3
"""Replay the fixed M3 task through an installed product and its bundled SDK.

This is a scripted installed-product check, not an autonomous-agent or user-study
success-rate measurement. It does not issue task approvals or operate Minecraft.
"""
from __future__ import annotations

import argparse
import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import traceback


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def inventory(root):
    result = {}
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        # Windows exclusively locks the live writer lease. Track its presence and
        # size, as the Core preflight tests do; hash every other workspace file.
        result[relative] = ({"writerLeaseBytes": path.stat().st_size}
                            if relative == ".copperbench/workspace.write.lock" else digest(path))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--product-root", required=True, type=Path)
    parser.add_argument("--candidate-package", required=True, type=Path)
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--workspace-folder", required=True, type=Path)
    parser.add_argument("--task-authorization", required=True)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache-policy", default="Existing guest caches retained; new workspace and independent test host.")
    args = parser.parse_args()
    product = args.product_root.resolve(strict=True)
    package = args.candidate_package.resolve(strict=True)
    fixture = args.fixture.resolve(strict=True)
    root = args.workspace_folder.absolute()
    output = args.output.absolute()
    conflict_root = root.with_name(root.name + "-conflict")
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_commit):
        parser.error("--source-commit must be a full Git SHA")
    if digest(package) != args.candidate_sha256.lower():
        parser.error("Candidate package hash does not match")
    if root.exists() or conflict_root.exists() or output.exists():
        parser.error("Workspace, conflict-copy and output directories must all be new")
    launcher = product / ("copperbench.exe" if sys.platform == "win32" else "copperbench.sh")
    if not launcher.is_file():
        parser.error("Installed product launcher is missing")
    sys.path.insert(0, str(product / "sdk/python"))
    import copperbench_native
    if Path(copperbench_native.__file__).resolve().parent != product / "sdk/python":
        raise RuntimeError("The SDK must come from the installed candidate")
    output.mkdir(parents=True)
    shutil.copy2(__file__, output / "harness.py")
    started = time.monotonic()
    record = {
        "schemaVersion": "1.0", "status": "running", "kind": "m3-scripted-installed-replay",
        "startedAt": datetime.now(timezone.utc).isoformat(), "sourceCommit": args.source_commit,
        "candidatePackage": str(package), "candidateSha256": digest(package),
        "productRoot": str(product), "launcherSha256": digest(launcher),
        "applicationSha256": digest(product / "lib/copperbench.jar"),
        "sdkPath": copperbench_native.__file__, "sdkSha256": digest(copperbench_native.__file__),
        "harnessSha256": digest(output / "harness.py"),
        "workspace": str(root), "tasks": [], "sessions": [],
        "fixtureHashes": {name: digest(fixture / name) for name in
                          ("recovery_probe.java", "ContractGameTests.java", "copperbench-tests.json")},
        "autonomousAgentSuccessMeasured": False, "playerBehaviorVerified": False,
        "cachePolicy": args.cache_policy,
    }

    def save(name, value):
        (output / (name + ".json")).write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    def cli(label, arguments, timeout=1500):
        then = time.monotonic()
        with (output / (label + ".stdout")).open("w", encoding="utf-8") as stdout, \
                (output / (label + ".stderr")).open("w", encoding="utf-8") as stderr:
            completed = subprocess.run([str(launcher), *arguments], cwd=product, stdout=stdout,
                                       stderr=stderr, timeout=timeout, check=False)
        save(label + "-receipt", {"exitCode": completed.returncode, "elapsedSeconds": time.monotonic() - then})
        if completed.returncode:
            raise RuntimeError(f"{label} failed with exit {completed.returncode}; see preserved stdout/stderr")
        return json.loads((output / (label + ".stdout")).read_text(encoding="utf-8"))

    def open_workspace(path):
        workspace = copperbench_native.Workspace.open(path, launcher=launcher, cwd=product,
                    task_authorization_id=args.task_authorization, startup_timeout=180, request_timeout=120)
        record["sessions"].append({"pid": workspace._process.pid, "workspaceId": workspace.workspace_id,
                                   "path": str(path), "revision": workspace.revision})
        return workspace

    def terminal(workspace, label, accepted, expected="succeeded", code=None):
        assert accepted["status"] == "accepted", accepted
        task_id = accepted["task"]["id"]
        print(f"M3 {label}: {task_id}", flush=True)
        result = workspace.wait_task(task_id, timeout=1500, poll_interval=1)
        save(label, result)
        task = result["data"]["task"]
        record["tasks"].append({"label": label, "id": task_id, "state": task["state"]})
        save("result", record)
        assert task["state"] == expected, f"{label}: {task['state']} != {expected}"
        if code:
            assert any(d["code"] == code for d in result["data"]["diagnostics"]), result
        return result

    def update_code(workspace, element_id, text):
        editor = workspace.query("get_mod_element_editor", elementId=element_id)["data"]
        fingerprints = [{"path": field["path"], "value": field.get("value")}
                        for section in editor["sections"] for field in section.get("fields", [])
                        if field["path"].startswith("/sourceFingerprints/")]
        assert any(field["path"] == "/sourceFingerprints/$primary" for field in fingerprints)
        workspace.update_mod_element(elementId=element_id,
                                     changes=[*fingerprints, {"path": "/code", "value": text}])

    try:
        save("result", record)
        created = cli("create", ["bootstrap", "create-workspace", "--generator-id", "fabric-1.21.1",
            "--mod-name", "M1 Delivery", "--mod-id", "m1_delivery", "--workspace-folder", str(root),
            "--task-authorization", args.task_authorization, "--no-prompt", "true"])
        assert created["status"] == "committed", created
        workspace_file = Path(created["data"]["workspaceFile"]).resolve(strict=True)
        assert workspace_file.parent == root.resolve()
        before = inventory(root)
        save("doctor", cli("doctor", ["headless", "--workspace", str(workspace_file), "doctor"], timeout=180))
        assert inventory(root) == before, "Doctor modified the workspace"
        save("doctor-nonmutation", {"scope": "workspace file hashes; writer lease presence and size",
                                   "files": len(before), "unchanged": True})
        tests = root / "src/gametest/java/copperbench/acceptance/ContractGameTests.java"
        tests.parent.mkdir(parents=True)
        shutil.copy2(fixture / "ContractGameTests.java", tests)
        shutil.copy2(fixture / "copperbench-tests.json", root / "copperbench-tests.json")
        correct_code = (fixture / "recovery_probe.java").read_text(encoding="utf-8")
        assert correct_code.count("return (count + 15) / 16;") == 1
        with open_workspace(workspace_file) as workspace:
            before = inventory(root)
            revision = workspace.revision
            item = workspace.discover_field_contract("item")
            recipe = workspace.discover_field_contract("recipe")
            assert item["complete"] and recipe["complete"]
            assert item == workspace.field_contract("item") and recipe == workspace.field_contract("recipe")
            references = workspace.field_reference_options("recipe", "blocksitems", search="Items.STICK", limit=1)
            assert references["data"]["options"][0]["value"] == "Items.STICK"
            assert before == inventory(root) and workspace.revision == revision, "Discovery wrote workspace state"
            save("discovery", {"item": item, "recipe": recipe, "references": references})
            workspace.create_mod_element(**item["minimalExample"])
            payload = copy.deepcopy(recipe["minimalExample"])
            payload["initialValues"]["recipeReturnStack"] = "CUSTOM:discovery_item"
            workspace.create_mod_element(**payload)
            code = workspace.create_mod_element(elementType="code", name="recovery_probe", initialValues={"code": correct_code})
            code_id = code["data"]["element"]["id"]
            preflight = workspace.preview_generation()
            save("preflight", preflight)
            assert preflight["data"]["status"] == "ready", preflight
            save("environment", workspace.query("get_workspace_environment"))
            terminal(workspace, "initial-build", workspace.build())
            update_code(workspace, code_id, correct_code.replace("return (count + 15) / 16;", "return missing_m1_symbol;"))
            failed = terminal(workspace, "controlled-compile-failure", workspace.build(), "failed", "JAVA_COMPILE_ERROR")
            assert "missing_m1_symbol" in json.dumps(failed)
            assert any("recovery_probe.java" in str(d) and "Line " in str(d) for d in failed["data"]["diagnostics"])
            update_code(workspace, code_id, correct_code)
            terminal(workspace, "repaired-build", workspace.build())
            accepted = terminal(workspace, "packaged-acceptance", workspace.run_game_tests())["data"]["task"]
            verification = accepted["verification"]
            assert verification["status"] == "passed" and verification["mode"] == "packaged_jar"
            assert verification["acceptanceExecuted"] == 5 and verification["failed"] == 0 and verification["skipped"] == 0
            assert verification["sourceCurrentAtCompletion"] is True
            save("verification", verification)
            exported = terminal(workspace, "verified-export", workspace.export_verified_artifact(accepted["id"]))["data"]["task"]["verifiedExport"]
            assert exported["status"] == "passed_current_input"
            assert exported["artifactSha256"] == verification["artifactSha256"]
            assert digest(Path(exported["exportDirectory"]) / "verified-mod.jar") == verification["artifactSha256"]
            saved_revision = workspace.revision
            old_process = workspace._process
        assert old_process.poll() == 0, "Native launcher did not close normally"
        with open_workspace(workspace_file) as workspace:
            recovered = workspace.get_task(accepted["id"])["data"]["task"]
            assert workspace._process.pid != old_process.pid and workspace.revision == saved_revision
            assert recovered["restoredFromHistory"] is True and recovered["verification"] == verification
            assert {"discovery_item", "discovery_recipe", "recovery_probe"} <= {e["name"] for e in workspace.list_mod_elements()}
            terminal(workspace, "reopened-verified-export", workspace.export_verified_artifact(accepted["id"]))
            save("reconnect", {"oldPid": old_process.pid, "oldExitCode": old_process.returncode,
                 "newPid": workspace._process.pid, "revision": saved_revision, "restoredTaskId": accepted["id"]})
        shutil.copytree(root, conflict_root, ignore=shutil.ignore_patterns(".gradle", "build", "run", ".copperbench"))
        sources = list(conflict_root.glob("src/main/java/**/item/discovery_itemItem.java"))
        assert len(sources) == 1, "Expected one generated fixture item source"
        protected = sources[0]
        with open_workspace(conflict_root / workspace_file.name) as workspace:
            item_id = next(element["id"] for element in workspace.list_mod_elements()
                           if element["name"] == "discovery_item")
            # Queue a managed regeneration before the external edit. A native-only
            # build without a pending edit does not request source replacement.
            workspace.update_mod_element(elementId=item_id, changes=[{"path": "/stackSize", "value": 15}])
            ready = workspace.preview_generation()
            save("conflict-before-external-edit", ready)
            assert ready["data"]["status"] == "ready", ready
            protected.write_bytes(protected.read_bytes() + b"\n// M3 external edit: preserve these exact bytes.\n")
            external_hash = digest(protected)
            before = inventory(conflict_root)
            preflight = workspace.preview_generation()
            save("conflict-preflight", preflight)
            assert preflight["data"]["status"] == "conflicted", preflight
            assert inventory(conflict_root) == before, "Conflict preflight modified the copy"
            assert any(conflict["relativePath"] == protected.relative_to(conflict_root).as_posix()
                       and conflict["reasonCode"] == "SOURCE_CHANGED"
                       for conflict in preflight["data"]["conflicts"]), preflight
            terminal(workspace, "conflict-build", workspace.build(), "failed", "GENERATION_SOURCE_CONFLICT")
            assert digest(protected) == external_hash, "Rejected build changed the external edit"
        save("external-edit-preserved", {"path": str(protected), "sha256": external_hash, "unchanged": True})
        assert digest(package) == record["candidateSha256"]
        assert digest(product / "lib/copperbench.jar") == record["applicationSha256"]
        record.update(status="passed", workspaceFile=str(workspace_file), verificationFile=str(output / "verification.json"))
        return 0
    except Exception as error:
        record.update(status="failed", errorType=type(error).__name__, error=str(error))
        (output / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
        print(f"M3 failed; evidence: {output}", file=sys.stderr, flush=True)
        return 1
    finally:
        record.update(completedAt=datetime.now(timezone.utc).isoformat(), elapsedSeconds=time.monotonic() - started)
        save("result", record)
        save("evidence-hashes", {p.name: digest(p) for p in sorted(output.iterdir()) if p.is_file() and p.name != "evidence-hashes.json"})


if __name__ == "__main__":
    raise SystemExit(main())
