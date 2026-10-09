"""M1 product delivery gate; execute only via runAgentReadinessDelivery.

The fixed fixture uses production Native/Core, real Gradle and an independent
packaged-JAR host. Negative recovered-record injections are explicitly labelled.
No authority is issued and no Minecraft EULA is accepted.
"""
from __future__ import annotations

import copy
import hashlib
import json
import platform
from pathlib import Path
import shutil
import subprocess
import sys
import time
import traceback
import uuid
from copperbench_native import Workspace

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
root = Path(config["workspace"]).parent
evidence = Path(config["evidence"])
fixture = Path(config["fixture"])
record = {"schemaVersion": "1.0", "status": "running", "layers": ["real_headless", "packaged_server"],
          "fixtureHashes": config["fixtureHashes"], "generatorVersion": config["generatorVersion"],
          "os": config["os"], "javaVersion": config["javaVersion"], "pythonVersion": platform.python_version(),
          "cachePolicy": config["cachePolicy"], "tasks": [], "negativeGates": [], "sessions": []}
started = time.monotonic()


def save(name, value):
    path = evidence / name
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_identity():
    cwd = config["cwd"]
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=cwd, text=True).strip()
    delta = subprocess.check_output(["git", "diff", "--binary", "HEAD"], cwd=cwd)
    (evidence / "source.patch").write_bytes(delta)
    names = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"], cwd=cwd, text=True).splitlines()
    hashes = {name: sha(Path(cwd) / name) for name in sorted(names) if (Path(cwd) / name).is_file()}
    return {"head": head, "trackedDeltaSha256": hashlib.sha256(delta).hexdigest(), "untrackedFiles": hashes}


def open_workspace():
    workspace = Workspace.open(config["workspace"], launcher=config["launcher"], cwd=config["cwd"],
                               startup_timeout=180, request_timeout=120)
    record["sessions"].append({"pid": workspace._process.pid, "workspaceId": workspace.workspace_id,
                               "openedRevision": workspace.revision})
    return workspace


def inventory():
    return {p.relative_to(root).as_posix(): ("active-lock:" + str(p.stat().st_size)
            if p.name == "workspace.write.lock" else sha(p))
            for p in sorted(root.rglob("*")) if p.is_file()}


def terminal(workspace, label, accepted, expected="succeeded", code=None):
    assert accepted["status"] == "accepted", accepted
    task_id = accepted["task"]["id"]
    print(f"M1 {label}: {task_id}", flush=True)
    result = workspace.wait_task(task_id, timeout=1500, poll_interval=1)
    save(label + ".json", result)
    task = result["data"]["task"]
    record["tasks"].append({"label": label, "taskId": task_id, "state": task["state"]})
    save("result.json", record)
    assert task["state"] == expected, f"{label}: {result}"
    if code is not None:
        assert any(d["code"] == code for d in result["data"]["diagnostics"]), result
    return result


def update_code(workspace, element_id, code):
    editor = workspace.query("get_mod_element_editor", elementId=element_id)["data"]
    fields = {f["path"]: f.get("value") for s in editor["sections"] for f in s.get("fields", [])}
    fingerprints = [{"path": path, "value": value} for path, value in fields.items()
                    if path.startswith("/sourceFingerprints/")]
    assert any(change["path"] == "/sourceFingerprints/$primary" for change in fingerprints), fields.keys()
    return workspace.update_mod_element(elementId=element_id, changes=[
        *fingerprints, {"path": "/code", "value": code}])


def reject_export(workspace, label, task_id, code, historical=False):
    result = terminal(workspace, label, workspace.export_verified_artifact(task_id, allow_historical=historical),
                      expected="failed", code=code)
    assert "verifiedExport" not in result["data"]["task"], result
    record["negativeGates"].append({"label": label, "code": code, "passed": True})
    return result


def inject_recovered_record(original_id, label, xml, minimum, actual_counts):
    """Negative fixture only: corrupt a copy, preserving the original positive task and report."""
    original = json.loads((root / ".copperbench/task-records" / (original_id + ".json")).read_text(encoding="utf-8"))
    injected = copy.deepcopy(original)
    task_id = str(uuid.uuid4())
    task = injected["task"]
    task["id"] = task_id
    report_path = root / ".copperbench/m1-negative" / (label + ".xml")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(xml, encoding="utf-8")
    verification = task["verification"]
    verification.update({"reportPath": str(report_path), "reportSha256": sha(report_path),
                         "minimumTests": minimum, "status": "passed", **actual_counts})
    (root / ".copperbench/task-records" / (task_id + ".json")).write_text(json.dumps(injected), encoding="utf-8")
    save(label + "-injected-record.json", injected)
    record["negativeGates"].append({"label": label, "layer": "negative_recovered_record_injection",
                                   "taskId": task_id, "rawReportSha256": sha(report_path)})
    return task_id


def export_current(workspace, label, acceptance):
    result = terminal(workspace, label, workspace.export_verified_artifact(acceptance["id"]))
    receipt = result["data"]["task"]["verifiedExport"]
    assert receipt["status"] == "passed_current_input", receipt
    verification = acceptance["verification"]
    assert receipt["artifactSha256"] == verification["artifactSha256"]
    assert receipt["reportSha256"] == verification["reportSha256"]
    assert receipt["sourceSha256"] == verification["sourceSnapshot"]["sha256"]
    destination = Path(receipt["exportDirectory"])
    assert sha(destination / "verified-mod.jar") == receipt["artifactSha256"]
    assert sha(destination / "gametest-results.xml") == receipt["reportSha256"]
    return receipt


try:
    record["productSource"] = source_identity()
    correct_code = (fixture / "recovery_probe.java").read_text(encoding="utf-8")
    assert correct_code.count("return (count + 15) / 16;") == 1
    broken_code = correct_code.replace("return (count + 15) / 16;", "return missing_m1_symbol;")
    with open_workspace() as workspace:
        before = inventory()
        item = workspace.discover_field_contract("item")
        recipe = workspace.discover_field_contract("recipe")
        assert item["complete"] and recipe["complete"]
        assert item == workspace.field_contract("item")
        options = workspace.field_reference_options("recipe", "blocksitems", search="Items.STICK", limit=1)
        assert options["data"]["options"][0]["value"] == "Items.STICK"
        save("discovery.json", {"item": item, "recipe": recipe, "references": options})
        assert before == inventory(), "Discovery modified the workspace"
        assert workspace.revision == 0
        workspace.create_mod_element(**item["minimalExample"])
        recipe_payload = copy.deepcopy(recipe["minimalExample"])
        recipe_payload["initialValues"]["recipeReturnStack"] = "CUSTOM:discovery_item"
        workspace.create_mod_element(**recipe_payload)
        code = workspace.create_mod_element(elementType="code", name="recovery_probe", initialValues={"code": correct_code})
        code_id = code["data"]["element"]["id"]
        preflight = workspace.preview_generation()
        save("preflight.json", preflight)
        assert preflight["data"]["status"] == "ready", preflight
        record["environment"] = workspace.query("get_workspace_environment")["data"]
        terminal(workspace, "initial-build", workspace.build())
        update_code(workspace, code_id, broken_code)
        failed = terminal(workspace, "controlled-compile-failure", workspace.build(), "failed")
        diagnostics = failed["data"]["diagnostics"]
        assert any(d["code"] == "JAVA_COMPILE_ERROR" and "recovery_probe.java" in str(d) and "Line " in str(d)
                   for d in diagnostics), diagnostics
        assert "missing_m1_symbol" in json.dumps(failed, ensure_ascii=False)
        update_code(workspace, code_id, correct_code)
        terminal(workspace, "repaired-build", workspace.build())
        accepted = terminal(workspace, "packaged-acceptance", workspace.run_game_tests())["data"]["task"]
        verify = accepted["verification"]
        assert verify["mode"] == "packaged_jar" and verify["status"] == "passed", verify
        assert verify["acceptanceExecuted"] == 5 and verify["failed"] == 0 and verify["skipped"] == 0, verify
        assert verify["sourceCurrentAtCompletion"] is True
        assert len(verify["cases"]) == 5
        first_export = export_current(workspace, "verified-export", accepted)
        for name, path in [("accepted-mod.jar", verify["artifactPath"]), ("accepted-report.xml", verify["reportPath"])]:
            shutil.copy2(path, evidence / name)
        saved_revision = workspace.revision
        old_process = workspace._process

    assert old_process.poll() == 0, "Native launcher did not close normally"
    with open_workspace() as workspace:
        assert workspace._process.pid != old_process.pid, "Reconnect did not replace the process/transport"
        assert workspace.revision == saved_revision
        recovered = workspace.get_task(accepted["id"])["data"]["task"]
        assert recovered["restoredFromHistory"] is True
        assert recovered["verification"] == accepted["verification"]
        assert {"discovery_item", "discovery_recipe", "recovery_probe"} <= {e["name"] for e in workspace.list_mod_elements()}
        export_current(workspace, "reopened-verified-export", recovered)
        save("reconnect.json", {"oldPid": old_process.pid, "oldExitCode": old_process.returncode,
                                "newPid": workspace._process.pid, "revision": workspace.revision,
                                "restoredTaskId": recovered["id"], "sourceSha256": verify["sourceSnapshot"]["sha256"]})

        # Real packaged-server insufficient-count failure: five business cases with a six-case minimum.
        tests_file = root / "copperbench-tests.json"
        original_config = tests_file.read_bytes()
        configured = json.loads(original_config)
        configured["minimumTests"] = 6
        tests_file.write_text(json.dumps(configured), encoding="utf-8")
        try:
            insufficient = terminal(workspace, "insufficient-tests", workspace.run_game_tests(), "failed", "GAMETEST_NO_TESTS")
            assert insufficient["data"]["task"]["verification"]["acceptanceExecuted"] == 5
            reject_export(workspace, "insufficient-export", insufficient["data"]["task"]["id"], "VERIFIED_ARTIFACT_UNAVAILABLE")
        finally:
            tests_file.write_bytes(original_config)

        # Tamper only the isolated evidence files, restore exact bytes and retain copies for inspection.
        for key, label in [("artifactPath", "jar-tamper"), ("reportPath", "report-tamper")]:
            path = Path(verify[key]); original_bytes = path.read_bytes()
            try:
                path.write_bytes(original_bytes + b"\nM1 controlled tamper\n")
                reject_export(workspace, label, accepted["id"], "VERIFIED_EVIDENCE_CHANGED", historical=True)
            finally:
                path.write_bytes(original_bytes)

        skipped_id = inject_recovered_record(accepted["id"], "all-skipped",
                    '<testsuite><testcase name="ignored"><skipped/></testcase></testsuite>', 1,
                    {"discovered": 1, "executed": 0, "passed": 0, "failed": 0, "skipped": 1,
                     "frameworkTests": 0, "acceptanceExecuted": 0,
                     "cases": [{"name": "ignored", "className": "", "status": "skipped", "scope": "acceptance"}]})
        reject_export(workspace, "all-skipped-export", skipped_id, "VERIFIED_ACCEPTANCE_INVALID", historical=True)
        empty_id = inject_recovered_record(accepted["id"], "zero-tests", "<testsuite/>", 1,
                    {"discovered": 0, "executed": 0, "passed": 0, "failed": 0, "skipped": 0,
                     "frameworkTests": 0, "acceptanceExecuted": 0, "cases": []})
        reject_export(workspace, "zero-tests-export", empty_id, "VERIFIED_ACCEPTANCE_INVALID")

        update_code(workspace, code_id, correct_code + "\n// Changed input after acceptance.\n")
        reject_export(workspace, "source-changed", accepted["id"], "VERIFICATION_INPUT_CHANGED")
        historical = terminal(workspace, "explicit-historical-export",
                              workspace.export_verified_artifact(accepted["id"], allow_historical=True))
        assert historical["data"]["task"]["verifiedExport"]["status"] == "passed_historical_input"
        reject_export(workspace, "historical-is-not-current", accepted["id"], "VERIFICATION_INPUT_CHANGED")
        update_code(workspace, code_id, correct_code)
        # A new acceptance binds the final current revision after all controlled source mutations.
        final_task = terminal(workspace, "final-current-acceptance", workspace.run_game_tests())["data"]["task"]
        final_export = export_current(workspace, "final-current-export", final_task)
        shutil.copy2(Path(final_export["exportDirectory"]) / "verified-mod.jar", evidence / "final-verified-mod.jar")
        shutil.copy2(Path(final_export["exportDirectory"]) / "gametest-results.xml", evidence / "final-gametest-results.xml")
        record["finalAcceptance"] = final_task["verification"]
        record["finalExport"] = final_export
        final_revision = workspace.revision

    with open_workspace() as workspace:
        assert workspace.revision == final_revision
        final_restored = workspace.get_task(final_task["id"])["data"]["task"]
        assert final_restored["restoredFromHistory"]
        export_current(workspace, "final-reopen-export", final_restored)
    record["status"] = "passed"
except BaseException as error:
    record["status"] = "failed"
    record["failure"] = {"type": type(error).__name__, "message": str(error), "traceback": traceback.format_exc()}
    raise
finally:
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result.json", record)
    print(f"M1 delivery {record['status']}: {evidence}", flush=True)
