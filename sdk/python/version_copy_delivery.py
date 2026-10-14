"""Actual Native source creation -> reviewed copy -> target reopen/build/independent host/export."""
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
source_file = Path(config["source"])
source = source_file.parent
target = config["generatorId"]
record = {"status": "running", "sessions": [], "tasks": [], "negativeGates": []}
started = time.monotonic()


def save(label, value):
    (evidence / (label + ".json")).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sha(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""): h.update(block)
    return h.hexdigest()


def snapshot(root):
    return {p.relative_to(root).as_posix(): sha(p) for p in sorted(root.rglob("*")) if p.is_file()
            and ".git" not in p.relative_to(root).parts and p.relative_to(root).as_posix() != ".copperbench/workspace.write.lock"}


def open_workspace(file):
    ws = Workspace.open(str(file), launcher=config["launcher"], cwd=config["cwd"],
                        task_authorization_id=config["taskAuthorizationId"], startup_timeout=180, request_timeout=1200)
    record["sessions"].append({"pid": ws._process.pid, "workspaceId": ws.workspace_id, "workspaceFile": str(file)})
    return ws


def closed(ws):
    record["sessions"][-1]["exitCode"] = ws._process.poll()
    assert ws._process.poll() == 0


def terminal(ws, label, accepted):
    assert accepted["status"] == "accepted", accepted
    task_id = accepted["task"]["id"]
    print(f"Version copy {label}: {task_id}", flush=True)
    outcome = ws.wait_task(task_id, timeout=1800, poll_interval=1)
    save(label, outcome)
    task = outcome["data"]["task"]
    record["tasks"].append({"label": label, "taskId": task_id, "state": task["state"]})
    assert task["state"] == "succeeded", outcome
    return task


def reject(label, code, call):
    try:
        result = call()
    except NativeApiError as failure:
        result = failure.details
        assert failure.code == code, (label, failure.code, result)
    else:
        assert result["status"] == "rejected", result
        assert result["diagnostics"][0]["code"] == code, result
    save(label, result)
    record["negativeGates"].append({"label": label, "code": code})


def command(ws, operation, **payload):
    return ws.preview("command", generator_id=target, command=operation, payload=payload)["core"]


def acceptance(ws, operation, **payload):
    if operation == "run": payload["taskAuthorizationId"] = config["taskAuthorizationId"]
    request = {"operation": operation, "supportedVersions": ["1"], "payload": payload, "expectedRevision": ws.revision}
    return ws.preview("acceptance", generator_id=target, payload=request)["acceptance"]["core"]


try:
    code = "# Copy preserves function 中文\nscoreboard objectives add camp_count dummy\nscoreboard players set camp_probe camp_count 11\nsetblock 8 80 8 minecraft:gold_block\n"
    with open_workspace(source_file) as ws:
        source_id = ws.workspace_id
        created = ws.command("create_mod_element", elementType="function", name="camp_signal", initialValues={"namespace": "mod", "code": code})
        save("source-created-function", created)
        element_id = created["data"]["element"]["id"]
        terminal(ws, "source-generated", ws.command("generate_workspace"))
        terminal(ws, "source-built", ws.command("build_workspace"))
    closed(ws)
    raw_path = source / "elements/camp_signal.mod.json"
    raw = json.loads(raw_path.read_text(encoding="utf-8"))
    raw["unknownEnvelope"] = {"keep": "原值"}
    raw["definition"]["futureFunctionField"] = {"keep": [1, 2, 3]}
    raw_path.write_text(json.dumps(raw, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    document = json.loads(source_file.read_text(encoding="utf-8"))
    document["unknownCopyMetadata"] = {"keep": ["原值", 17]}
    source_file.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    world = source / "run/saves/Original World/level.dat"
    world.parent.mkdir(parents=True, exist_ok=True); world.write_bytes(b"P263 COPY BOUNDARY FIXTURE\x00\xff\x01")
    record["worldEvidence"] = "Controlled byte fixture for exclusion/preservation; no player-world runtime claim."
    with open_workspace(source_file) as ws:
        args = {"target_generator_id": target, "output_name": "upgraded-copy", "supported_versions": ("2",)}
        result = ws.version_copy_upgrade(**args); save("preview", result); plan = result["plan"]
        assert plan["executionAvailable"], [e for e in plan["entries"] if e["disposition"] != "convertible"]
        request = dict(args, plan_id=plan["planId"], expected_revision=ws.revision, task_authorization_id=config["taskAuthorizationId"])
        reject("missing-preview-selection", "PREVIEW_OPT_IN_REQUIRED", lambda: ws.version_copy_upgrade("execute", **request))
        ws.preview("opt_in", generator_id=target, workspace_folder_path=plan["targetDirectory"])
        reject("missing-build-authority", "TASK_AUTHORIZATION_OPERATION_DENIED", lambda: ws.version_copy_upgrade("execute", **{**request, "task_authorization_id": config["createOnlyAuthorizationId"]}))
        reject("wrong-target-authority", "TASK_AUTHORIZATION_SCOPE_MISMATCH", lambda: ws.version_copy_upgrade("execute", **{**request, "task_authorization_id": config["wrongScopeAuthorizationId"]}))
        reject("wrong-revision", "WORKSPACE_REVISION_CONFLICT", lambda: ws.version_copy_upgrade("execute", **{**request, "expected_revision": ws.revision - 1}))
        before = snapshot(source); save("source-before-execute", before)
        copied = ws.version_copy_upgrade("execute", **request); save("executed", copied)
        after = snapshot(source); save("source-after-execute", after)
        assert after == before, {k for k in before.keys() | after.keys() if before.get(k) != after.get(k)}
        receipt = copied["execution"]; assert receipt["conversionComplete"] and receipt["sourceUnchanged"]
        assert receipt["acceptance"] == "not_run" and not receipt["worldsCopied"]
        target_file = Path(receipt["targetWorkspaceFile"]); target_root = target_file.parent
        assert receipt["targetWorkspaceId"] != source_id
        target_run = target_root / "run"
        assert target_run.resolve() != (source / "run").resolve()
        # NeoForge's target Gradle setup creates an empty independent run directory.
        # No source settings, server list, or world content may be present in it.
        runtime_entries = sorted(p.relative_to(target_run).as_posix() for p in target_run.rglob("*"))
        assert not runtime_entries, runtime_entries
        profile_paths = sorted((target_root / ".eclipse/configurations").glob("*.launch"))
        for profile in profile_paths:
            profile_text = profile.read_text(encoding="utf-8")
            assert str(source) not in profile_text and str(source).replace("\\", "\\\\") not in profile_text
        save("runtime-boundary", {"sourceRuntimeFiles": snapshot(source / "run"),
                                  "targetRunDirectoryExists": target_run.exists(),
                                  "targetRuntimeEntries": runtime_entries,
                                  "targetLaunchProfiles": [p.relative_to(target_root).as_posix() for p in profile_paths],
                                  "sourcePathsInTargetLaunchProfiles": False})
        assert (target_root / "elements/camp_signal.mod.json").read_bytes() == raw_path.read_bytes()
        reject("existing-target", "UPGRADE_TARGET_EXISTS", lambda: ws.version_copy_upgrade("execute", **request))
        # A second explicit build demonstrates recovery using the original source after the byte comparison.
        terminal(ws, "source-still-builds", ws.command("build_workspace"))
    closed(ws)
    # Independent test source is installed only in the target; source never receives host or world data.
    fixture = Path(config["cwd"]) / f"examples/camp-kit-structured/{target}-function"
    for file in fixture.rglob("*"):
        if file.is_file():
            to = target_root / file.relative_to(fixture); to.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(file, to)
    manifest = json.loads((target_root / "required-cases.json").read_text(encoding="utf-8"))
    with open_workspace(target_file) as ws:
        assert ws.workspace_id == receipt["targetWorkspaceId"] and ws.revision == 0
        ws.preview("opt_in", generator_id=target)
        editor = ws.query("get_mod_element_editor", elementId=element_id); save("target-editor", editor)
        assert element_id in json.dumps(editor)
        terminal(ws, "target-built", command(ws, "build_workspace"))
        target_doc = json.loads(target_file.read_text(encoding="utf-8"))
        assert target_doc["unknownCopyMetadata"] == document["unknownCopyMetadata"]
        assert target_doc["mod_elements"][0]["name"] == document["mod_elements"][0]["name"]
        assert target_doc["mod_elements"][0]["registry_name"] == document["mod_elements"][0]["registry_name"]
        assert (target_root / "elements/camp_signal.mod.json").read_bytes() == raw_path.read_bytes()
        resource = target_root / "src/main/resources/data/copperbench_camp_kit/function/camp_signal.mcfunction"
        assert resource.read_bytes() == code.encode("utf-8")
        accepted = terminal(ws, "target-acceptance", acceptance(ws, "run", requiredCases=manifest))
        verification = accepted["verification"]
        assert verification["status"] == "passed" and verification["mode"] == "packaged_jar"
        assert verification["acceptanceExecuted"] == verification["minimumTests"] == 1
        host = Path(verification["reportPath"]).parent
        for name in ("function-effects.json", "invalid-command.json", "invalid-reference.json"):
            shutil.copy2(host / name, evidence / name)
        effects = json.loads((evidence / "function-effects.json").read_text(encoding="utf-8"))
        assert effects["score"] == 11 and effects["block"] == "minecraft:gold_block" and effects["position"] == "8,80,8"
        exported = terminal(ws, "target-export", acceptance(ws, "export", verifiedTaskId=accepted["id"], allowHistorical=False))
        export = exported["verifiedExport"]; assert export["status"] == "passed_current_input"
        for name in ("verification.json", "verified-mod.jar", "gametest-results.xml"):
            shutil.copy2(Path(export["exportDirectory"]) / name, evidence / name)
        assert sha(evidence / "verified-mod.jar") == verification["artifactSha256"]
    closed(ws)
    with open_workspace(target_file) as ws:
        ws.preview("opt_in", generator_id=target)
        assert ws.workspace_id == receipt["targetWorkspaceId"]
        restored = ws.get_task(accepted["id"])["data"]["task"]; assert restored["restoredFromHistory"]
        terminal(ws, "reopened-export", acceptance(ws, "export", verifiedTaskId=accepted["id"], allowHistorical=False))
        save("reopened-editor", ws.query("get_mod_element_editor", elementId=element_id))
    closed(ws)
    record.update(status="passed", sourceWorkspaceId=source_id, targetWorkspaceId=receipt["targetWorkspaceId"],
                  functionElementId=element_id, jarSha256=verification["artifactSha256"], verification=verification,
                  sourceByteComparison=True, targetWorldsCopied=False, requiredCases=manifest)
except BaseException:
    record["status"] = "failed"; (evidence / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8"); raise
finally:
    record["elapsedSeconds"] = round(time.monotonic() - started, 3); save("result", record)
