"""Opt-in real 26.3 structured function -> generated JAR -> independent loader host -> reopen/export."""
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


def terminal(ws, label, accepted):
    assert accepted["status"] == "accepted", accepted
    task_id = accepted["task"]["id"]
    print(f"Structured function {label}: {task_id}", flush=True)
    result = ws.wait_task(task_id, timeout=1800, poll_interval=1)
    save(label, result)
    task = result["data"]["task"]
    record["tasks"].append({"label": label, "taskId": task_id, "state": task["state"]})
    assert task["state"] == "succeeded", result
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


try:
    manifest = json.loads((root / "required-cases.json").read_text(encoding="utf-8"))
    save("frozen-required-cases", manifest)
    code = "# Camp signal 中文\nscoreboard objectives add camp_count dummy\nscoreboard players set camp_probe camp_count 7\nsetblock 8 80 8 minecraft:gold_block\n"
    with open_workspace() as ws:
        reject(ws, "no-opt-in", "PREVIEW_OPT_IN_REQUIRED", "create_mod_element", elementType="function", name="camp_signal")
        preview(ws, "opt_in")
        environment = ws.query("get_workspace_environment")
        contract = environment["data"]["fieldContracts"]["function"]
        save("function-contract", contract)
        assert contract["supported"] and contract["generatorId"] == target
        created = command(ws, "create_mod_element", elementType="function", name="camp_signal", initialValues={"code": code})
        save("created-function", created)
        assert created["status"] == "committed"
        element_id = created["data"]["element"]["id"]
        reject(ws, "unverified-type", "PREVIEW_ELEMENT_UNVERIFIED", "create_mod_element", elementType="loottable", name="camp_loot")
        for label, field, value, error in (
            ("unverified-field", "tags", ["minecraft:tick"], "PREVIEW_FIELD_UNVERIFIED"),
            ("invalid-content", "code", "# line 1\n/say invalid", "FUNCTION_CONTENT_INVALID"),
            ("invalid-namespace", "namespace", "minecraft", "FIELD_ENUM_INVALID"),
            ("invalid-resource-name", "name", "../outside", "FIELD_VALUE_INVALID"),
            ("invalid-command-type", "commands", [42], "FIELD_TYPE_INVALID"),
            ("conflicting-body-aliases", "commands", ["say other"], "FIELD_ALIAS_CONFLICT"),
        ):
            before = (root / "elements/camp_signal.mod.json").read_bytes()
            reject(ws, label, error, "update_mod_element", elementId=element_id, changes=[{"path": "/" + field, "value": value}])
            assert (root / "elements/camp_signal.mod.json").read_bytes() == before
    closed(ws)

    # Opaque imported definition fixture, changed only while the product is closed.
    stored = root / "elements/camp_signal.mod.json"
    raw = json.loads(stored.read_text(encoding="utf-8"))
    raw["definition"]["futureFunctionField"] = {"keep": [1, 2, 3], "label": "preserve"}
    stored.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    save("unknown-field-fixture", raw)
    expected_code = code.replace("camp_count 7", "camp_count 11")
    with open_workspace() as ws:
        reject(ws, "reopen-needs-opt-in", "PREVIEW_OPT_IN_REQUIRED", "generate_workspace")
        preview(ws, "opt_in")
        changed = command(ws, "update_mod_element", elementId=element_id, changes=[{"path": "/code", "value": expected_code}])
        save("updated-function", changed)
        assert changed["status"] == "committed"
        save("editor", ws.query("get_mod_element_editor", elementId=element_id))
        terminal(ws, "generated", command(ws, "generate_workspace"))
        resource = root / "src/main/resources/data/copperbench_camp_kit/function/camp_signal.mcfunction"
        assert resource.read_text(encoding="utf-8") == expected_code
        assert json.loads(stored.read_text(encoding="utf-8"))["definition"]["futureFunctionField"] == raw["definition"]["futureFunctionField"]
        terminal(ws, "built", command(ws, "build_workspace"))
        jar = root / "build/libs/copperbench_camp_kit-1.0.0.jar"
        built_sha = sha(jar)
        accepted = terminal(ws, "one-case-acceptance", acceptance(ws, "run", requiredCases=manifest))
        verification = accepted["verification"]
        assert verification["status"] == "passed" and verification["mode"] == "packaged_jar"
        assert verification["acceptanceExecuted"] == verification["minimumTests"] == 1
        assert {case["name"] for case in verification["cases"] if case["scope"] == "acceptance"} == {"copperbench_camp_kit:t13"}
        assert verification["artifactSha256"] == built_sha
        host_run = Path(verification["reportPath"]).parent
        for name in ("function-effects.json", "invalid-command.json", "invalid-reference.json"):
            shutil.copy2(host_run / name, evidence / name)
        export(ws, "verified-export", accepted)
    closed(ws)

    with open_workspace() as ws:
        preview(ws, "opt_in")
        save("reopened-editor", ws.query("get_mod_element_editor", elementId=element_id))
        assert resource.read_text(encoding="utf-8") == expected_code
        assert json.loads(stored.read_text(encoding="utf-8"))["definition"]["futureFunctionField"] == raw["definition"]["futureFunctionField"]
        assert sha(jar) == built_sha
        restored = ws.get_task(accepted["id"])["data"]["task"]
        assert restored["restoredFromHistory"] and restored["verification"] == verification
        export(ws, "reopened-export", restored)
    closed(ws)
    record.update(status="passed", jarSha256=built_sha, verification=verification, requiredCases=manifest,
                  functionElementId=element_id, scope="function_slice_only_not_g2")
except BaseException:
    record["status"] = "failed"
    (evidence / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
    raise
finally:
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result", record)
    save("preview-receipts", receipts)
