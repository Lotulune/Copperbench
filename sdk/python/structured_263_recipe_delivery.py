"""Opt-in real 26.3 structured recipe -> generated JAR -> independent loader host -> reopen/export."""
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
    print(f"Structured recipe {label}: {task_id}", flush=True)
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
    ids = {}
    shaped_slots = ["Items.STICK", "", "", "Items.STRING", "", "", "", "", ""]
    recipes = {
        "camp_shaped": {"recipeType": "Crafting", "recipeShapeless": False, "recipeSlots": shaped_slots, "recipeReturnStack": "CUSTOM:trail_kit"},
        "camp_shapeless": {"recipeType": "Crafting", "recipeShapeless": True, "recipeSlots": ["Items.FLINT", "Items.PAPER"] + [""] * 7, "recipeReturnStack": "CUSTOM:trail_kit"},
        "camp_smelting": {"recipeType": "Smelting", "smeltingInputStack": "Items.STRING", "smeltingReturnStack": "CUSTOM:trail_kit", "cookingTime": 200},
        "camp_blasting": {"recipeType": "Blasting", "blastingInputStack": "Items.FLINT", "blastingReturnStack": "CUSTOM:trail_kit", "cookingTime": 100},
        "camp_smoking": {"recipeType": "Smoking", "smokingInputStack": "Items.PAPER", "smokingReturnStack": "CUSTOM:trail_kit", "cookingTime": 100},
    }
    with open_workspace() as ws:
        reject(ws, "no-opt-in", "PREVIEW_OPT_IN_REQUIRED", "create_mod_element", elementType="recipe", name="camp_shaped")
        preview(ws, "opt_in")
        contract = ws.query("get_mod_element_field_contract", elementType="recipe")
        save("field-contract", contract)
        assert contract["data"]["complete"] and contract["data"]["generatorId"] == target
        created = command(ws, "create_mod_element", elementType="item", name="trail_kit", initialValues={
            "displayName": "Camp Kit", "stackSize": 16, "texture": "minecraft:paper", "fuelBurnTime": 400})
        save("created-item", created)
        assert created["status"] == "committed"
        for name, values in recipes.items():
            created = command(ws, "create_mod_element", elementType="recipe", name=name, initialValues=values)
            save("created-" + name, created)
            assert created["status"] == "committed", created
            ids[name] = created["data"]["element"]["id"]
        reject(ws, "unverified-type", "PREVIEW_ELEMENT_UNVERIFIED", "create_mod_element", elementType="loottable", name="camp_loot")
        for label, name, field, value, code in (
            ("unverified-field", "camp_shaped", "namespace", "minecraft", "PREVIEW_FIELD_UNVERIFIED"),
            ("invalid-reference", "camp_shaped", "recipeReturnStack", "Items.NOT_REAL", "FIELD_REFERENCE_INVALID"),
            ("missing-custom", "camp_shaped", "recipeReturnStack", "CUSTOM:MissingKit", "FIELD_REFERENCE_INVALID"),
            ("invalid-cooking-time", "camp_smelting", "cookingTime", 0, "FIELD_VALUE_OUT_OF_RANGE"),
            ("invalid-subtype", "camp_smoking", "recipeType", "Brewing", "FIELD_ENUM_INVALID"),
        ):
            before = (root / f"elements/{name}.mod.json").read_bytes()
            reject(ws, label, code, "update_mod_element", elementId=ids[name], changes=[{"path": "/" + field, "value": value}])
            assert (root / f"elements/{name}.mod.json").read_bytes() == before
    closed(ws)

    # Explicit imported identity/opaque-data fixtures, only while all product processes are closed.
    workspace_file = Path(config["workspace"])
    document = json.loads(workspace_file.read_text(encoding="utf-8"))
    identity = next(element for element in document["mod_elements"] if element["name"] == "trail_kit")
    identity["registry_name"] = "actual_trail_kit"
    workspace_file.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    save("imported-registry-identity", {"internalName": "trail_kit", "registryName": "actual_trail_kit", "fixture": "existing imported identity; no handwritten item implementation"})
    stored = root / "elements/camp_shaped.mod.json"
    raw = json.loads(stored.read_text(encoding="utf-8"))
    raw["definition"]["futureRecipeField"] = {"keep": [1, 2, 3], "label": "preserve"}
    stored.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    save("unknown-field-fixture", raw)

    with open_workspace() as ws:
        reject(ws, "reopen-needs-opt-in", "PREVIEW_OPT_IN_REQUIRED", "generate_workspace")
        preview(ws, "opt_in")
        for name, count in (("camp_shaped", 2), ("camp_shapeless", 3), ("camp_smelting", 2)):
            changed = command(ws, "update_mod_element", elementId=ids[name], changes=[{"path": "/recipeRetstackSize", "value": count}])
            save("updated-" + name, changed)
            assert changed["status"] == "committed"
        save("editor", ws.query("get_mod_element_editor", elementId=ids["camp_smelting"]))
        terminal(ws, "generated", command(ws, "generate_workspace"))
        for name in recipes:
            data = json.loads((root / f"src/main/resources/data/copperbench_camp_kit/recipe/{name}.json").read_text(encoding="utf-8"))
            assert data["result"]["id"] == "copperbench_camp_kit:actual_trail_kit", data
        assert json.loads(stored.read_text(encoding="utf-8"))["definition"]["futureRecipeField"] == raw["definition"]["futureRecipeField"]
        terminal(ws, "built", command(ws, "build_workspace"))
        jar = root / "build/libs/copperbench_camp_kit-1.0.0.jar"
        built_sha = sha(jar)
        accepted = terminal(ws, "five-case-acceptance", acceptance(ws, "run", requiredCases=manifest))
        verification = accepted["verification"]
        assert verification["status"] == "passed" and verification["mode"] == "packaged_jar"
        assert verification["acceptanceExecuted"] == verification["minimumTests"] == 5
        assert {case["name"] for case in verification["cases"] if case["scope"] == "acceptance"} == {case["name"] for case in manifest["cases"]}
        assert verification["artifactSha256"] == built_sha
        export(ws, "verified-export", accepted)
    closed(ws)

    with open_workspace() as ws:
        preview(ws, "opt_in")
        save("reopened-editor", ws.query("get_mod_element_editor", elementId=ids["camp_smelting"]))
        assert json.loads(stored.read_text(encoding="utf-8"))["definition"]["futureRecipeField"] == raw["definition"]["futureRecipeField"]
        assert sha(jar) == built_sha
        restored = ws.get_task(accepted["id"])["data"]["task"]
        assert restored["restoredFromHistory"] and restored["verification"] == verification
        export(ws, "reopened-export", restored)
    closed(ws)
    record.update(status="passed", jarSha256=built_sha, verification=verification, requiredCases=manifest,
                  recipeIds=ids, scope="recipe_slice_only_not_g2")
except BaseException:
    record["status"] = "failed"
    (evidence / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
    raise
finally:
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result", record)
    save("preview-receipts", receipts)
