"""Opt-in real 26.3 structured code -> generated JAR -> independent loader host -> reopen/export."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import shutil
import sys
import time
import traceback
from copperbench_native import Workspace, NativeApiError

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
evidence = Path(config["evidence"])
root = Path(config["workspace"]).parent
target = config.get("generatorId", "fabric-26.3")
assert target in ("fabric-26.3", "neoforge-26.3"), target
record = {"status": "running", "sessions": [], "tasks": [], "negativeGates": []}
receipts = []
started = time.monotonic()


def save(label, value):
    (evidence / (label + ".json")).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def open_workspace():
    ws = Workspace.open(config["workspace"], launcher=config["launcher"], cwd=config["cwd"],
                        task_authorization_id=config["taskAuthorizationId"], startup_timeout=180, request_timeout=1200)
    record["sessions"].append({"pid": ws._process.pid, "workspaceId": ws.workspace_id})
    return ws


def closed(ws):
    record["sessions"][-1]["exitCode"] = ws._process.poll()
    assert record["sessions"][-1]["exitCode"] == 0


def preview(ws, operation, **payload):
    result = ws.preview(operation, generator_id=target, **payload)
    receipts.append(result)
    return result


def command(ws, operation, **payload):
    return preview(ws, "command", command=operation, payload=payload)["core"]


def acceptance(ws, operation, **payload):
    request = {"operation": operation, "supportedVersions": ["1"], "payload": payload, "expectedRevision": ws.revision}
    if operation == "run": payload["taskAuthorizationId"] = config["taskAuthorizationId"]
    return preview(ws, "acceptance", payload=request)["acceptance"]["core"]


def terminal(ws, label, accepted, expected="succeeded", diagnostic=None):
    assert accepted["status"] == "accepted", accepted
    task_id = accepted["task"]["id"]
    print(f"Structured code {label}: {task_id}", flush=True)
    result = ws.wait_task(task_id, timeout=1800, poll_interval=1)
    save(label, result)
    task = result["data"]["task"]
    record["tasks"].append({"label": label, "taskId": task_id, "state": task["state"]})
    assert task["state"] == expected, result
    if diagnostic:
        assert diagnostic in json.dumps(result), result
        record["negativeGates"].append({"label": label, "code": diagnostic})
    return task


def reject(ws, label, code, operation, **payload):
    before = ws.revision
    try:
        command(ws, operation, **payload)
    except NativeApiError as error:
        save(label, error.details)
        if error.details.get("contract") == "preview_targets": receipts.append(error.details)
        assert error.code == code, (label, error.code, error.details)
        assert ws.revision == before
        record["negativeGates"].append({"label": label, "code": code})
        return
    raise AssertionError(label + " unexpectedly succeeded")


def export(ws, label, accepted):
    task = terminal(ws, label, acceptance(ws, "export", verifiedTaskId=accepted["id"], allowHistorical=False))
    receipt = task["verifiedExport"]
    assert receipt["status"] == "passed_current_input", receipt
    destination = Path(receipt["exportDirectory"])
    portable = json.loads((destination / "verification.json").read_text(encoding="utf-8"))
    assert portable["requiredCases"]["manifest"] == manifest
    assert sha(destination / "verified-mod.jar") == accepted["verification"]["artifactSha256"]
    for name in ("verification.json", "verified-mod.jar", "gametest-results.xml"):
        shutil.copy2(destination / name, evidence / (label + "-" + name))
    return receipt


def editor_values(ws, element_id, label):
    response = ws.query("get_mod_element_editor", elementId=element_id)
    save(label, response)
    result = {}
    for section in response["data"]["sections"]:
        for field in section["fields"]:
            path = field["path"].removeprefix("/fields").removeprefix("/")
            if path.startswith("sourceFingerprints/"):
                key = path.removeprefix("sourceFingerprints/").replace("~1", "/").replace("~0", "~")
                result.setdefault("sourceFingerprints", {})[key] = field["value"]
            else:
                result[path] = field["value"]
    return result


def changes(element_id, values):
    return {"elementId": element_id, "changes": [{"path": "/" + key, "value": value} for key, value in values.items()]}


try:
    manifest = json.loads((root / "required-cases.json").read_text(encoding="utf-8"))
    save("frozen-required-cases", manifest)
    primary_body = "package dev.copperbench.campkit;\npublic final class recovery_probe {\n public static int bundles(int n) { return dev.copperbench.campkit.parts.BundleMath.count(n); }\n public static int externalStamp() { return dev.copperbench.campkit.parts.PreservationStamp.VALUE; }\n}\n"
    helper_body = "package dev.copperbench.campkit.parts;\npublic final class BundleMath {\n public static int count(int n) {\n  if (n < 0) throw new IllegalArgumentException(\"negative\");\n  return (int) ((n + 15L) / 16);\n }\n}\n"
    broken_body = helper_body.replace("(int) ((n + 15L) / 16)", "missing_p263_symbol")
    stamp_body = "package dev.copperbench.campkit.parts;\npublic final class PreservationStamp { public static final int VALUE = 7; }\n"
    files = [{"path": "parts/BundleMath.java", "code": helper_body}, {"path": "parts/PreservationStamp.java", "code": stamp_body}]
    primary = root / "src/main/java/dev/copperbench/campkit/recovery_probe.java"
    helper = primary.parent / "parts/BundleMath.java"
    stamp = primary.parent / "parts/PreservationStamp.java"
    with open_workspace() as ws:
        reject(ws, "no-opt-in", "PREVIEW_OPT_IN_REQUIRED", "create_mod_element", elementType="code", name="recovery_probe")
        preview(ws, "opt_in")
        contract = ws.query("get_workspace_environment")["data"]["fieldContracts"]["code"]
        save("code-contract", contract)
        assert contract["supported"] and contract["generatorId"] == target
        created = command(ws, "create_mod_element", elementType="code", name="recovery_probe", initialValues={"code": primary_body, "codeFiles": files})
        save("created-code", created)
        assert created["status"] == "committed"
        element_id = created["data"]["element"]["id"]
        assert primary.read_bytes().decode("utf-8") == primary_body
        assert helper.read_bytes().decode("utf-8") == helper_body
        initial = editor_values(ws, element_id, "initial-editor")
        old_fingerprints = initial["sourceFingerprints"]
        # Deliberate IDE-like edit inside this test workspace, with no simultaneous API write.
        external_stamp = stamp_body.replace("= 7", "= 41").replace("\n", "\r\n")
        stamp.write_bytes(external_stamp.encode("utf-8"))
        save("external-helper-edit", {"path": str(stamp), "sha256": sha(stamp), "content": external_stamp})
        metadata = command(ws, "update_mod_element", **changes(element_id, {"description": "Metadata only; preserve the IDE edit"}))
        save("metadata-only-update", metadata)
        assert metadata["status"] == "committed"
        assert stamp.read_bytes().decode("utf-8") == external_stamp and primary.read_bytes().decode("utf-8") == primary_body
        stale_files = [dict(file) for file in files]
        stale_files[1]["code"] = stamp_body.replace("= 7", "= 99")
        reject(ws, "stale-fingerprint", "SOURCE_CONTENT_CONFLICT", "update_mod_element", **changes(element_id,
            {"codeFiles": stale_files, "sourceFingerprints": old_fingerprints}))
        before_revision = ws.revision
        try:
            ws.preview("command", generator_id=target, command="update_mod_element", expected_revision=0,
                       payload=changes(element_id, {"description": "stale"}))
            raise AssertionError("stale revision unexpectedly committed")
        except NativeApiError as error:
            save("stale-revision", error.details)
            assert error.code == "WORKSPACE_REVISION_CONFLICT", (error.code, error.details)
            record["negativeGates"].append({"label": "stale-revision", "code": error.code})
            assert ws.revision == before_revision
        reject(ws, "path-ownership", "WORKSPACE_PERSISTENCE_FAILED", "create_mod_element", elementType="code", name="other_probe",
               initialValues={"code": "", "codeFiles": [{"path": "parts/BundleMath.java", "code": ""}]})
        assert not primary.with_name("other_probe.java").exists()
        for label, values, error in (
            ("unverified-field", {"automaticRegistration": True}, "PREVIEW_FIELD_UNVERIFIED"),
            ("invalid-code-type", {"code": True}, "FIELD_TYPE_INVALID"),
            ("escaping-path", {"codeFiles": [{"path": "../Escape.java", "code": ""}]}, "CODE_BUNDLE_INVALID"),
        ):
            reject(ws, label, error, "update_mod_element", **changes(element_id, values))
        reject(ws, "unverified-type", "PREVIEW_ELEMENT_UNVERIFIED", "create_mod_element", elementType="loottable", name="camp_loot")
        reject(ws, "no-takeover-extension", "PREVIEW_OPERATION_UNSUPPORTED", "set_mod_element_source_management", elementId=element_id,
               mode="generated", userApproved=False)
        assert stamp.read_bytes().decode("utf-8") == external_stamp and helper.read_bytes().decode("utf-8") == helper_body
        current = editor_values(ws, element_id, "before-fault-editor")
        faulty = [dict(file) for file in current["codeFiles"]]
        next(file for file in faulty if file["path"] == "parts/BundleMath.java")["code"] = broken_body
        injected = command(ws, "update_mod_element", **changes(element_id, {"codeFiles": faulty, "sourceFingerprints": current["sourceFingerprints"]}))
        save("injected-compile-fault", injected)
        assert injected["status"] == "committed"
        terminal(ws, "controlled-compile-failure", command(ws, "build_workspace"), "failed", "JAVA_COMPILE_ERROR")
        failure = json.loads((evidence / "controlled-compile-failure.json").read_text(encoding="utf-8"))
        assert "BundleMath.java" in json.dumps(failure) and "missing_p263_symbol" in json.dumps(failure)
    closed(ws)

    external_primary = (primary_body + "// External source kept after product reopen 中文\n").replace("\n", "\r\n")
    primary.write_bytes(external_primary.encode("utf-8"))
    save("external-primary-edit", {"path": str(primary), "sha256": sha(primary), "content": external_primary})
    with open_workspace() as ws:
        reject(ws, "reopen-needs-opt-in", "PREVIEW_OPT_IN_REQUIRED", "generate_workspace")
        preview(ws, "opt_in")
        current = editor_values(ws, element_id, "reopened-for-repair-editor")
        assert current["code"] == external_primary
        assert next(file for file in current["codeFiles"] if file["path"] == "parts/PreservationStamp.java")["code"] == external_stamp
        repaired = [dict(file) for file in current["codeFiles"]]
        next(file for file in repaired if file["path"] == "parts/BundleMath.java")["code"] = helper_body
        updated = command(ws, "update_mod_element", **changes(element_id, {"codeFiles": repaired, "sourceFingerprints": current["sourceFingerprints"]}))
        save("explicit-code-repair", updated)
        assert updated["status"] == "committed"
        terminal(ws, "generated", command(ws, "generate_workspace"))
        assert primary.read_bytes().decode("utf-8") == external_primary and stamp.read_bytes().decode("utf-8") == external_stamp
        assert helper.read_bytes().decode("utf-8") == helper_body
        terminal(ws, "repaired-build", command(ws, "build_workspace"))
        jar = root / "build/libs/copperbench_camp_kit-1.0.0.jar"
        built_sha = sha(jar)
        accepted = terminal(ws, "one-case-acceptance", acceptance(ws, "run", requiredCases=manifest))
        verification = accepted["verification"]
        assert verification["status"] == "passed" and verification["mode"] == "packaged_jar"
        assert verification["acceptanceExecuted"] == verification["minimumTests"] == 1
        assert {case["name"] for case in verification["cases"] if case["scope"] == "acceptance"} == {"copperbench_camp_kit:t12"}
        assert verification["artifactSha256"] == built_sha
        shutil.copy2(Path(verification["reportPath"]).parent / "code-effects.json", evidence / "code-effects.json")
        export(ws, "verified-export", accepted)
    closed(ws)
    with open_workspace() as ws:
        preview(ws, "opt_in")
        current = editor_values(ws, element_id, "reopened-editor")
        assert current["code"] == external_primary
        terminal(ws, "reopened-generated", command(ws, "generate_workspace"))
        assert primary.read_bytes().decode("utf-8") == external_primary and stamp.read_bytes().decode("utf-8") == external_stamp
        assert helper.read_bytes().decode("utf-8") == helper_body and sha(jar) == built_sha
        restored = ws.get_task(accepted["id"])["data"]["task"]
        assert restored["restoredFromHistory"] and restored["verification"] == verification
        export(ws, "reopened-export", restored)
    closed(ws)
    record.update(status="passed", jarSha256=built_sha, verification=verification, requiredCases=manifest,
                  codeElementId=element_id, scope="code_slice_only_not_g2")
except BaseException:
    record["status"] = "failed"
    (evidence / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
    raise
finally:
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result", record)
    save("preview-receipts", receipts)
