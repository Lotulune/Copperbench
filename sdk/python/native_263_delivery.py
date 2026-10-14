"""Real exact-target 26.3 Native creation/edit/build/reopen gate; no gameplay or installed-package claim."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import traceback
import zipfile

from copperbench_native import NativeApiError, Workspace

config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
evidence = Path(config["evidence"])
destination = Path(config["destination"])
target = config.get("generatorId", "fabric-26.3")
fabric = target == "fabric-26.3"
metadata_path = "src/main/resources/fabric.mod.json" if fabric else "src/main/resources/META-INF/neoforge.mods.toml"
receipts = []
record = {"status": "running", "scope": target + "_native_build", "sessions": [], "tasks": []}
started = time.monotonic()


def save(label, value):
    (evidence / (label + ".json")).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def open_workspace(path):
    workspace = Workspace.open(path, launcher=config["launcher"], cwd=config["cwd"], startup_timeout=180,
                               request_timeout=1200, task_authorization_id=config["taskAuthorizationId"])
    record["sessions"].append({"pid": workspace._process.pid, "workspaceId": workspace.workspace_id, "path": str(path)})
    return workspace


def preview(workspace, operation, **kwargs):
    result = workspace.preview(operation, generator_id=target, **kwargs)
    receipts.append(result)
    return result


def command(workspace, operation, **payload):
    return preview(workspace, "command", command=operation, payload=payload)["core"]


def reject(label, expected, action):
    try:
        action()
    except NativeApiError as error:
        save(label, error.details)
        if error.details.get("contract") == "preview_targets": receipts.append(error.details)
        assert error.code == expected, (label, error.code, expected, error.details)
        return
    raise AssertionError(label + " unexpectedly succeeded")


def terminal(workspace, label, accepted, expected="succeeded"):
    assert accepted["status"] == "accepted", accepted
    task_id = accepted["task"]["id"]
    print(f"26.3 Native {label}: {task_id}", flush=True)
    outcome = workspace.wait_task(task_id, timeout=1800, poll_interval=1)
    save(label, outcome)
    task = outcome["data"]["task"]
    record["tasks"].append({"label": label, "taskId": task_id, "state": task["state"]})
    assert task["state"] == expected, outcome
    return outcome


def read(workspace, path):
    return workspace.query("read_workspace_file", relativePath=path)["data"]


def closed(workspace):
    record["sessions"][-1]["exitCode"] = workspace._process.poll()
    assert record["sessions"][-1]["exitCode"] == 0, record["sessions"][-1]


try:
    with open_workspace(config["workspace"]) as workspace:
        form = dict(generatorId=target, workspaceFolderPath=str(destination), modName="Native SDK Probe",
                    modId="native_sdk_probe", packageName="dev.nativesdkprobe", version="1.2.3")
        reject("no-choice", "PREVIEW_OPT_IN_REQUIRED", lambda: command(workspace, "create_workspace", **form))
        assert not destination.exists()
        preview(workspace, "opt_in", workspace_folder_path=str(destination))
        reject("legacy-create", "PREVIEW_NEGOTIATION_REQUIRED", lambda: workspace.command("create_workspace", **form))
        authority = workspace.task_authorization_id
        workspace.task_authorization_id = None
        reject("unapproved-create", "USER_APPROVAL_REQUIRED", lambda: command(workspace, "create_workspace", **form, userApproved=True))
        assert not destination.exists()
        workspace.task_authorization_id = authority
        created = command(workspace, "create_workspace", **form)
        save("created", created)
        assert created["status"] == "committed", created
        new_file = Path(created["data"]["workspaceFile"])
    closed(workspace)

    # Explicit local native-authoring preparation. Subsequent changes use the protected SDK writer.
    java_path = "src/main/java/dev/nativesdkprobe/ManualProbe.java"
    json_path = "src/main/resources/native-probe.json"
    java_text = "package dev.nativesdkprobe;\npublic final class ManualProbe { public static int value() { return 1; } }\n"
    json_text = '{"value":1,"unknownField":{"keep":["custom",26.3]}}\n'
    (destination / java_path).write_text(java_text, encoding="utf-8")
    (destination / json_path).write_text(json_text, encoding="utf-8")
    save("manual-file-preparation", {"java": java_path, "json": json_path, "via": "external native-authoring fixture"})

    with open_workspace(new_file) as workspace:
        reject("new-session-opt-in", "PREVIEW_OPT_IN_REQUIRED", lambda: command(workspace, "build_workspace"))
        preview(workspace, "opt_in")
        environment = workspace.query("get_workspace_environment")
        save("environment", environment)
        assert '"minecraftVersion": "26.3"' in json.dumps(environment)
        assert 'gradle-9.6.0-bin.zip' in json.dumps(environment)
        before = read(workspace, java_path)
        assert before["ownership"] == "manual" and before["editable"]
        old_revision = workspace.revision
        java_final = java_text.replace("return 1", "return 26")
        command(workspace, "update_workspace_file", relativePath=java_path, expectedSha256=before["sha256"], content=java_final)
        reject("old-revision", "WORKSPACE_REVISION_CONFLICT", lambda: preview(workspace, "command", command="build_workspace", expected_revision=old_revision))
        before_json = read(workspace, json_path)
        json_final = json_text.replace('"value":1', '"value":26')
        (destination / json_path).write_text(json_final, encoding="utf-8")
        reject("external-file-conflict", "WORKSPACE_SOURCE_CONFLICT", lambda: command(workspace, "update_workspace_file",
               relativePath=json_path, expectedSha256=before_json["sha256"], content=json_text))
        before_json = read(workspace, json_path)
        json_final = json_final.replace('"value":26', '"value":263')
        command(workspace, "update_workspace_file", relativePath=json_path, expectedSha256=before_json["sha256"], content=json_final)
        generated = read(workspace, metadata_path)
        assert generated["ownership"] == "generated" and not generated["editable"]
        reject("generated-file-read-only", "WORKSPACE_SOURCE_READ_ONLY", lambda: command(workspace, "update_workspace_file",
               relativePath=metadata_path, expectedSha256=generated["sha256"], content="{}"))
        built = terminal(workspace, "build", command(workspace, "build_workspace"))
        build_id = built["data"]["task"]["id"]
        jars = list((destination / "build/libs").glob("native_sdk_probe-1.2.3.jar"))
        assert len(jars) == 1, jars
        with zipfile.ZipFile(jars[0]) as jar:
            if fabric:
                assert json.loads(jar.read("fabric.mod.json"))["depends"]["minecraft"] == "26.3"
            else:
                assert 'versionRange="[26.3]"' in jar.read("META-INF/neoforge.mods.toml").decode("utf-8")
                assert "fabric.mod.json" not in jar.namelist()
            assert jar.read("native-probe.json").decode() == json_final
            assert "dev/nativesdkprobe/ManualProbe.class" in jar.namelist()
        record["jar"] = {"path": str(jars[0]), "sha256": hashlib.sha256(jars[0].read_bytes()).hexdigest()}
        terminal(workspace, "regenerate", command(workspace, "generate_workspace"))
        assert read(workspace, java_path)["content"] == java_final
        assert read(workspace, json_path)["content"] == json_final
        pending = command(workspace, "build_workspace")
        reject("sdk-wait-timeout", "NATIVE_TASK_TIMEOUT", lambda: workspace.wait_task(pending["task"]["id"], timeout=0.01, poll_interval=0.001))
        save("cancel-request", workspace.cancel_task(pending["task"]["id"]))
        terminal(workspace, "cancelled-build", pending, "cancelled")
        authority = workspace.task_authorization_id
        workspace.task_authorization_id = None
        reject("unapproved-server", "USER_APPROVAL_REQUIRED", lambda: command(workspace, "run_server", userApproved=True, serverEulaAccepted=True))
        assert not list(destination.glob(".copperbench/task-runs/run_server/*/workspace/run/eula.txt"))
        workspace.task_authorization_id = authority
        if config["serverEulaAccepted"]:
            server = terminal(workspace, "dedicated-server", command(workspace, "run_server"))
            logs = json.dumps(server["data"]["logs"])
            assert config.get("initializationMarker", "COPPERBENCH_P263_FABRIC_INITIALIZED") in logs and "For help, type" in logs
            assert "Starting Minecraft server on 127.0.0.1:0" in logs
            record["server"] = {"initialized": True, "ready": True, "userAcceptedEula": True}
    closed(workspace)

    with open_workspace(new_file) as reopened:
        reject("reopen-opt-in", "PREVIEW_OPT_IN_REQUIRED", lambda: command(reopened, "build_workspace"))
        restored = reopened.get_task(build_id)
        save("restored-build", restored)
        assert restored["data"]["task"]["state"] == "succeeded"
        assert read(reopened, java_path)["content"] == java_final
        assert read(reopened, json_path)["content"] == json_final
        assert read(reopened, json_path)["ownership"] == "manual"
        preview(reopened, "opt_in")
        terminal(reopened, "reopened-build", command(reopened, "build_workspace"))
    closed(reopened)
    assert record["sessions"][1]["pid"] != record["sessions"][2]["pid"]
    assert record["sessions"][1]["workspaceId"] == record["sessions"][2]["workspaceId"]
    save("responses", receipts)
    checked = subprocess.run(["node", "ui-core/scripts/validate-preview-evidence.mjs", str(evidence / "responses.json")],
                             cwd=config["cwd"], capture_output=True, text=True, encoding="utf-8")
    (evidence / "schema-validation.log").write_text(checked.stdout + checked.stderr, encoding="utf-8")
    assert checked.returncode == 0, checked.stderr
    record["status"] = "passed"
except BaseException as error:
    record.update(status="failed", error=str(error), traceback=traceback.format_exc())
    raise
finally:
    save("responses", receipts)
    record["elapsedSeconds"] = round(time.monotonic() - started, 3)
    save("result", record)
    print(f"26.3 Native gate {record['status']}: {evidence}", flush=True)
