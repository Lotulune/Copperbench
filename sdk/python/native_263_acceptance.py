"""Real exact-target 26.3 G1/G2 Native gate. Negative record injection is explicitly marked as a fixture."""
from __future__ import annotations
import copy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time
import traceback
import uuid
import xml.etree.ElementTree as ET
from copperbench_native import Workspace, NativeApiError

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
evidence = Path(config["evidence"])
root = Path(config["workspace"]).parent
target = config.get("generatorId", "fabric-26.3")
level = config.get("level", "G1")
assert level in ("G1", "G2"), level
minimum = 6 if level == "G1" else 13
manifest_id = "camp-kit-native-v1" if level == "G1" else "camp-kit-scoped-v1"
case_label = "six-case-acceptance" if level == "G1" else "thirteen-case-acceptance"
source_path = "src/main/java/dev/copperbench/campkit/" + ("RecoveryProbe.java" if level == "G1" else "parts/BundleMath.java")
receipts = []
record = {"status": "running", "generatorId": target, "sessions": [], "tasks": [], "negativeGates": []}
started = time.monotonic()


def save(label, value):
    (evidence / (label + ".json")).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def open_workspace():
    workspace = Workspace.open(config["workspace"], launcher=config["launcher"], cwd=config["cwd"],
                               task_authorization_id=config["taskAuthorizationId"], startup_timeout=180, request_timeout=1200)
    record["sessions"].append({"pid": workspace._process.pid, "workspaceId": workspace.workspace_id})
    return workspace


def closed(workspace):
    record["sessions"][-1]["exitCode"] = workspace._process.poll()
    assert record["sessions"][-1]["exitCode"] == 0


def preview(workspace, operation, **payload):
    result = workspace.preview(operation, generator_id=target, **payload)
    receipts.append(result)
    return result


def command(workspace, operation, **payload):
    return preview(workspace, "command", command=operation, payload=payload)["core"]


def acceptance(workspace, operation, **payload):
    request = {"operation": operation, "supportedVersions": ["1"], "payload": payload}
    if operation in {"run", "export"}:
        request["expectedRevision"] = workspace.revision
    if operation == "run":
        payload["taskAuthorizationId"] = config["taskAuthorizationId"]
    return preview(workspace, "acceptance", payload=request)["acceptance"]["core"]


def terminal(workspace, label, accepted, expected="succeeded", code=None, detail=None):
    assert accepted["status"] == "accepted", accepted
    task_id = accepted["task"]["id"]
    print(f"Camp Kit {label}: {task_id}", flush=True)
    result = workspace.wait_task(task_id, timeout=1800, poll_interval=1)
    save(label, result)
    task = result["data"]["task"]
    record["tasks"].append({"label": label, "taskId": task_id, "state": task["state"]})
    assert task["state"] == expected, result
    if code:
        assert code in json.dumps(result), result
        record["negativeGates"].append({"label": label, "code": code})
    if detail:
        assert detail in json.dumps(result), result
    return task


def reject(label, code, action):
    try:
        action()
    except NativeApiError as error:
        save(label, error.details)
        assert error.code == code, (label, error.code, error.details)
        record["negativeGates"].append({"label": label, "code": code})
        return
    raise AssertionError(label + " unexpectedly succeeded")


def read(workspace, path=source_path):
    return workspace.query("read_workspace_file", relativePath=path)["data"]


def edit(workspace, content):
    before = read(workspace)
    assert before["ownership"] == "manual" and before["editable"]
    return command(workspace, "update_workspace_file", relativePath=source_path, expectedSha256=before["sha256"], content=content)


def run_cases(workspace, label):
    task = terminal(workspace, label, acceptance(workspace, "run", requiredCases=manifest))
    verification = task["verification"]
    assert verification["status"] == "passed" and verification["mode"] == "packaged_jar"
    assert verification["acceptanceExecuted"] == minimum and verification["minimumTests"] == minimum
    assert {case["name"] for case in verification["cases"] if case["scope"] == "acceptance"} == expected_ids
    proof = workspace.get_required_case_evidence(task["id"])
    save(label + "-required-cases", proof)
    assert proof["requiredCases"]["binding"]["manifest"] == manifest
    assert proof["requiredCases"]["coverage"]["missingCases"] == []
    return task


def export(workspace, label, accepted, historical=False, status="passed_current_input"):
    task = terminal(workspace, label, acceptance(workspace, "export", verifiedTaskId=accepted["id"], allowHistorical=historical))
    receipt = task["verifiedExport"]
    assert receipt["status"] == status
    destination = Path(receipt["exportDirectory"])
    portable = json.loads((destination / "verification.json").read_text(encoding="utf-8"))
    assert sha(destination / "verified-mod.jar") == receipt["artifactSha256"] == accepted["verification"]["artifactSha256"]
    assert sha(destination / "gametest-results.xml") == receipt["reportSha256"] == accepted["verification"]["reportSha256"]
    assert portable["requiredCases"]["manifest"] == manifest
    save(label + "-portable", portable)
    return receipt


def reject_export(workspace, label, task_id, code, historical=False, detail=None):
    task = terminal(workspace, label, acceptance(workspace, "export", verifiedTaskId=task_id, allowHistorical=historical), "failed", code, detail)
    assert "verifiedExport" not in task


def duplicate_record(accepted):
    """Negative recovery fixture with enough raw rows but only one independent identity."""
    original = json.loads((root / ".copperbench/task-records" / (accepted["id"] + ".json")).read_text(encoding="utf-8"))
    injected = copy.deepcopy(original)
    task_id = str(uuid.uuid4())
    injected["task"]["id"] = task_id
    case = copy.deepcopy(next(case for case in accepted["verification"]["cases"] if case["scope"] == "acceptance"))
    suite = ET.Element("testsuite")
    for _ in range(minimum):
        ET.SubElement(suite, "testcase", {"name": case["name"], "classname": case["className"]})
    report = root / ".copperbench/p263-negative/duplicate.xml"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_bytes(ET.tostring(suite))
    injected["task"]["verification"].update(reportPath=str(report), reportSha256=sha(report), discovered=minimum,
            executed=minimum, passed=minimum, failed=0, skipped=0, frameworkTests=0, acceptanceExecuted=minimum, cases=[case] * minimum)
    (root / ".copperbench/task-records" / (task_id + ".json")).write_text(json.dumps(injected), encoding="utf-8")
    save("duplicate-negative-record-fixture", injected)
    shutil.copy2(report, evidence / "duplicate-negative.xml")
    return task_id


try:
    if level == "G2":
        from scoped_263_setup import setup, verify_reopened
        with open_workspace() as workspace:
            reject("scoped-no-opt-in", "PREVIEW_OPT_IN_REQUIRED", lambda: command(workspace, "create_mod_element", elementType="item", name="trail_kit"))
            preview(workspace, "opt_in")
            scoped_elements = setup(workspace, root, command, terminal, save)
        closed(workspace)
    correct = (root / source_path).read_text(encoding="utf-8")
    expression = "return count / 16 + (count % 16 == 0 ? 0 : 1);"
    assert correct.count(expression) == 1
    with open_workspace() as workspace:
        manifest = next(m for m in workspace.required_case_manifests() if m["id"] == manifest_id)
        numbers = (1, 2, 3, 4, 11, 12) if level == "G1" else range(1, 14)
        expected_ids = {f"copperbench_camp_kit:t{number:02d}" for number in numbers}
        assert manifest["minimumTests"] == minimum and {case["name"] for case in manifest["cases"]} == expected_ids
        save("manifest", manifest)
        reject("no-opt-in", "PREVIEW_OPT_IN_REQUIRED", lambda: command(workspace, "build_workspace"))
        preview(workspace, "opt_in")
        terminal(workspace, "initial-build", command(workspace, "build_workspace"))
        old_revision = workspace.revision
        edit(workspace, correct.replace(expression, "return missing_p263_symbol;"))
        reject("old-revision", "WORKSPACE_REVISION_CONFLICT", lambda: preview(workspace, "command", command="build_workspace", expected_revision=old_revision))
        failed = terminal(workspace, "controlled-compile-failure", command(workspace, "build_workspace"), "failed", "JAVA_COMPILE_ERROR")
        failed_receipt = json.loads((evidence / "controlled-compile-failure.json").read_text(encoding="utf-8"))
        assert Path(source_path).name in json.dumps(failed_receipt) and "missing_p263_symbol" in json.dumps(failed_receipt)
        edit(workspace, correct)
        terminal(workspace, "repaired-build", command(workspace, "build_workspace"))
        accepted = run_cases(workspace, case_label)
        export(workspace, "verified-export", accepted)
    closed(workspace)

    with open_workspace() as workspace:
        preview(workspace, "opt_in")
        recovered = workspace.get_task(accepted["id"])["data"]["task"]
        assert recovered["restoredFromHistory"] and recovered["verification"] == accepted["verification"]
        export(workspace, "reopened-export", recovered)
        for key, label in (("artifactPath", "jar-tamper"), ("reportPath", "xml-tamper")):
            path = Path(accepted["verification"][key]); original = path.read_bytes()
            try:
                path.write_bytes(original + b"\ncontrolled negative tamper\n")
                reject_export(workspace, label, accepted["id"], "VERIFIED_EVIDENCE_CHANGED", historical=True)
            finally:
                path.write_bytes(original)
        duplicate = duplicate_record(accepted)
        reject_export(workspace, "duplicate-current", duplicate, "VERIFIED_ACCEPTANCE_INVALID", detail="GAMETEST_REPORT_INVALID")
        before = read(workspace)
        external = correct + "\n// Explicit external edit used by the conflict test.\n"
        (root / source_path).write_text(external, encoding="utf-8")
        reject("external-source-conflict", "WORKSPACE_SOURCE_CONFLICT", lambda: command(workspace, "update_workspace_file",
               relativePath=source_path, expectedSha256=before["sha256"], content=correct))
        edit(workspace, correct + "\n// New revision after verified input.\n")
        reject_export(workspace, "duplicate-historical", duplicate, "VERIFIED_ACCEPTANCE_INVALID", historical=True, detail="GAMETEST_REPORT_INVALID")
        reject_export(workspace, "changed-input", accepted["id"], "VERIFICATION_INPUT_CHANGED")
        export(workspace, "explicit-historical", accepted, historical=True, status="passed_historical_input")
        edit(workspace, correct)
        pending = command(workspace, "build_workspace")
        reject("sdk-timeout", "NATIVE_TASK_TIMEOUT", lambda: workspace.wait_task(pending["task"]["id"], timeout=0.01, poll_interval=0.001))
        save("cancel-request", workspace.cancel_task(pending["task"]["id"]))
        terminal(workspace, "cancelled-build", pending, "cancelled")
        # Explicit external omission fixture; this executes the remaining real host cases.
        required_file = root / "src/gametest/resources/data/copperbench_camp_kit/test_instance/t12.json"
        omitted = required_file.with_suffix(".disabled")
        required_file.rename(omitted)
        try:
            missing = terminal(workspace, "required-case-missing", acceptance(workspace, "run", requiredCases=manifest), "failed", "GAMETEST_REQUIRED_CASES_MISSING")
            proof = workspace.get_required_case_evidence(missing["id"])
            save("required-case-missing-proof", proof)
            assert proof["requiredCases"]["coverage"]["uniqueExecuted"] == minimum - 1
            assert proof["requiredCases"]["coverage"]["missingCases"] == [{"className": "", "name": "copperbench_camp_kit:t12"}]
            reject_export(workspace, "missing-export", missing["id"], "VERIFIED_ARTIFACT_UNAVAILABLE")
        finally:
            omitted.rename(required_file)
        assert read(workspace)["content"] == correct
        final_task = run_cases(workspace, "final-" + case_label)
        final_export = export(workspace, "final-export", final_task)
        for name in ("verified-mod.jar", "gametest-results.xml", "verification.json"):
            shutil.copy2(Path(final_export["exportDirectory"]) / name, evidence / ("final-" + name))
        record["finalVerification"] = final_task["verification"]
        record["finalExport"] = final_export
    closed(workspace)

    with open_workspace() as workspace:
        preview(workspace, "opt_in")
        if level == "G2":
            verify_reopened(workspace, root, scoped_elements, save)
        final_restored = workspace.get_task(final_task["id"])["data"]["task"]
        assert final_restored["restoredFromHistory"]
        record["reopenedExport"] = export(workspace, "final-reopened-export", final_restored)
    closed(workspace)
    assert len({s["pid"] for s in record["sessions"]}) == (3 if level == "G1" else 4)
    save("responses", receipts)
    checked = subprocess.run(["node", "ui-core/scripts/validate-preview-evidence.mjs", str(evidence / "responses.json")], cwd=config["cwd"], capture_output=True, text=True, encoding="utf-8")
    (evidence / "schema-validation.log").write_text(checked.stdout + checked.stderr, encoding="utf-8")
    assert checked.returncode == 0, checked.stderr
    record["status"] = "passed"
except BaseException as error:
    record.update(status="failed", error=str(error), traceback=traceback.format_exc())
    if isinstance(error, NativeApiError):
        record.update(errorCode=error.code, errorDetails=error.details)
    raise
finally:
    save("responses", receipts)
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result", record)
    print(f"Camp Kit Native {record['status']}: {evidence}", flush=True)
