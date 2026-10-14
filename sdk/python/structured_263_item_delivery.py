"""Opt-in real 26.3 structured item -> generated JAR -> independent loader host -> reopen/export."""
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
    print(f"Structured item {label}: {task_id}", flush=True)
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
    with open_workspace() as ws:
        reject(ws, "no-opt-in", "PREVIEW_OPT_IN_REQUIRED", "create_mod_element", elementType="item", name="trail_kit")
        preview(ws, "opt_in")
        contract_response = ws.query("get_mod_element_field_contract", elementType="item")
        save("field-contract", contract_response)
        contract = contract_response["data"]
        assert contract["complete"] and contract["availability"] == "available"
        assert {f["path"] for f in contract["fields"]} == {"/" + f for f in (
            "name", "displayName", "description", "texture", "stackSize", "maxStackSize", "rarity", "immuneToFire", "fuelBurnTime", "compostingLayers")}
        create = dict(contract["minimalExample"])
        create["name"] = "trail_kit"
        created = command(ws, "create_mod_element", **create)
        save("created", created)
        assert created["status"] == "committed", created
        element_id = created["data"]["element"]["id"]
        reject(ws, "unverified-type", "PREVIEW_ELEMENT_UNVERIFIED", "create_mod_element", elementType="loottable", name="camp_loot")
        reject(ws, "unverified-field", "PREVIEW_FIELD_UNVERIFIED", "update_mod_element", elementId=element_id,
               changes=[{"path": "/enableRanged", "value": True}])
        for field, value, code in (("texture", "minecraft:missing", "FIELD_REFERENCE_INVALID"),
                                   ("fuelBurnTime", -1, "FIELD_VALUE_OUT_OF_RANGE"),
                                   ("compostingLayers", 8, "FIELD_VALUE_OUT_OF_RANGE")):
            reject(ws, "invalid-" + field, code, "update_mod_element", elementId=element_id,
                   changes=[{"path": "/" + field, "value": value}])
    closed(ws)

    # Explicit imported-future-data fixture, injected only with the product process closed.
    stored = root / "elements/trail_kit.mod.json"
    raw = json.loads(stored.read_text(encoding="utf-8"))
    raw["definition"]["futureField"] = {"keep": [1, 2, 3], "label": "preserve"}
    stored.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    save("unknown-field-fixture", raw)

    with open_workspace() as ws:
        reject(ws, "reopen-needs-opt-in", "PREVIEW_OPT_IN_REQUIRED", "generate_workspace")
        preview(ws, "opt_in")
        changed = command(ws, "update_mod_element", elementId=element_id, changes=[
            {"path": "/displayName", "value": "Copper Camp Kit"}, {"path": "/rarity", "value": "RARE"},
            {"path": "/texture", "value": "minecraft:stick"}, {"path": "/immuneToFire", "value": True},
            {"path": "/fuelBurnTime", "value": 200}, {"path": "/compostingLayers", "value": 1}])
        save("updated", changed)
        assert changed["status"] == "committed", changed
        save("editor", ws.query("get_mod_element_editor", elementId=element_id))
        terminal(ws, "generated", command(ws, "generate_workspace"))
        assert json.loads(stored.read_text(encoding="utf-8"))["definition"]["futureField"] == raw["definition"]["futureField"]
        terminal(ws, "built", command(ws, "build_workspace"))
        jar = root / "build/libs/copperbench_camp_kit-1.0.0.jar"
        built_sha = sha(jar)
        accepted = terminal(ws, "four-case-acceptance", acceptance(ws, "run", requiredCases=manifest))
        verification = accepted["verification"]
        assert verification["status"] == "passed" and verification["mode"] == "packaged_jar"
        assert verification["acceptanceExecuted"] == verification["minimumTests"] == 4
        assert {c["name"] for c in verification["cases"] if c["scope"] == "acceptance"} == {c["name"] for c in manifest["cases"]}
        assert verification["artifactSha256"] == built_sha, (verification["artifactSha256"], built_sha)
        export(ws, "verified-export", accepted)
    closed(ws)

    with open_workspace() as ws:
        preview(ws, "opt_in")
        save("reopened-editor", ws.query("get_mod_element_editor", elementId=element_id))
        final = json.loads(stored.read_text(encoding="utf-8"))["definition"]
        assert final["futureField"] == raw["definition"]["futureField"]
        assert (final["stackSize"], final["rarity"], final["fuelBurnTime"], final["compostingLayers"]) == (16, "RARE", 200, 1)
        assert sha(jar) == built_sha
        restored = ws.get_task(accepted["id"])["data"]["task"]
        assert restored["restoredFromHistory"] and restored["verification"] == verification
        export(ws, "reopened-export", restored)
    closed(ws)
    record.update(status="passed", jarSha256=built_sha, verification=verification,
                  requiredCases=manifest, elementId=element_id, scope="item_slice_only_not_g2")
except BaseException:
    record["status"] = "failed"
    (evidence / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
    raise
finally:
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result", record)
    save("preview-receipts", receipts)
