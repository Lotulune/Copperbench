"""Run the Stage 15 installed-product external-Agent gate against Desktop MCP."""

from __future__ import annotations

import argparse
import getpass
import json
import os
from pathlib import Path
import re
import sys
import time
import uuid
from typing import Any


HERE = Path(__file__).resolve().parent
for candidate in (HERE / "sdk" / "python", HERE.parent / "sdk" / "python"):
    if (candidate / "copperbench.py").is_file():
        sys.path.insert(0, str(candidate))
        break

from copperbench import CopperbenchClient, CopperbenchError, read_workspace_connection  # noqa: E402


TERMINAL_TASK_STATES = {"succeeded", "failed", "cancelled"}
RUN_CLIENT_MARKERS = (
    "Backend library: LWJGL version",
    "Reloading ResourceManager:",
    "minecraft:textures/atlas/blocks.png-atlas",
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def workspace_revision(result: dict[str, Any]) -> int:
    direct = result.get("revision")
    if isinstance(direct, int):
        return direct
    data = result.get("data") if isinstance(result.get("data"), dict) else {}
    workspace = data.get("workspace") if isinstance(data.get("workspace"), dict) else data
    revision = workspace.get("revision") if isinstance(workspace, dict) else None
    if not isinstance(revision, int):
        raise RuntimeError(f"get_workspace returned no integer revision: {result}")
    return revision


def workspace_id(result: dict[str, Any]) -> str:
    direct = result.get("workspaceId")
    if isinstance(direct, str) and direct:
        return direct
    data = result.get("data") if isinstance(result.get("data"), dict) else {}
    workspace = data.get("workspace") if isinstance(data.get("workspace"), dict) else data
    value = workspace.get("workspaceId") if isinstance(workspace, dict) else None
    if not isinstance(value, str) or not value:
        raise RuntimeError(f"get_workspace returned no workspaceId: {result}")
    return value


def wait_task(client: CopperbenchClient, task_id: str, timeout_seconds: int = 1500) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    after_sequence = 0
    collected_logs: list[dict[str, Any]] = []
    while time.monotonic() < deadline:
        result = client.get_task(task_id, after_sequence)
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        task = data.get("task") if isinstance(data.get("task"), dict) else {}
        logs = data.get("logs") if isinstance(data.get("logs"), list) else []
        for entry in logs:
            if isinstance(entry, dict):
                collected_logs.append(entry)
                sequence = entry.get("sequence")
                if isinstance(sequence, int):
                    after_sequence = max(after_sequence, sequence)
        state = task.get("state")
        if state in TERMINAL_TASK_STATES:
            return {"state": state, "logs": collected_logs, "result": result}
        time.sleep(0.5)
    raise RuntimeError(f"task {task_id} did not reach a terminal state within {timeout_seconds}s")


def wait_interactive_run_client(client: CopperbenchClient, revision: int,
                                readiness_timeout_seconds: int = 1800,
                                close_timeout_seconds: int = 1500) -> dict[str, Any]:
    accepted = client.call_tool("run_client", {"expectedRevision": revision})
    require(accepted.get("status") == "accepted", f"run_client was not accepted: {accepted}")
    run_task_id = task_id(accepted)
    after_sequence = 0
    markers = {marker: False for marker in RUN_CLIENT_MARKERS}
    readiness_deadline = time.monotonic() + readiness_timeout_seconds

    while time.monotonic() < readiness_deadline:
        result = client.get_task(run_task_id, after_sequence)
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        task = data.get("task") if isinstance(data.get("task"), dict) else {}
        logs = data.get("logs") if isinstance(data.get("logs"), list) else []
        for entry in logs:
            if not isinstance(entry, dict):
                continue
            sequence = entry.get("sequence")
            if isinstance(sequence, int):
                after_sequence = max(after_sequence, sequence)
            text = entry.get("text")
            if isinstance(text, str):
                for marker in markers:
                    if marker in text:
                        markers[marker] = True
        state = task.get("state")
        require(state not in TERMINAL_TASK_STATES,
                f"run_client terminated before render readiness: {state}: {result}")
        if all(markers.values()):
            require(state == "running", f"run_client reached render markers but is not running: {state}")
            break
        time.sleep(0.5)
    require(all(markers.values()), f"run_client did not reach render readiness: {markers}")

    stability_deadline = time.monotonic() + 10
    while time.monotonic() < stability_deadline:
        result = client.get_task(run_task_id, after_sequence)
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        task = data.get("task") if isinstance(data.get("task"), dict) else {}
        logs = data.get("logs") if isinstance(data.get("logs"), list) else []
        for entry in logs:
            if isinstance(entry, dict) and isinstance(entry.get("sequence"), int):
                after_sequence = max(after_sequence, int(entry["sequence"]))
        require(task.get("state") == "running",
                f"run_client did not remain alive through the 10-second stability window: {result}")
        time.sleep(0.5)

    print("Minecraft render readiness passed and run_client stayed running for 10 seconds.", flush=True)
    print("Confirm the Minecraft window is visibly usable, then close Minecraft normally; do not cancel the task.",
          flush=True)
    closed = wait_task(client, run_task_id, close_timeout_seconds)
    require(closed["state"] == "succeeded",
            f"run_client did not succeed after Minecraft was closed normally: {closed['result']}")
    return {
        "taskId": run_task_id,
        "renderMarkers": list(RUN_CLIENT_MARKERS),
        "renderReady": True,
        "stableBeforeUserCloseSeconds": 10,
        "terminalStateAfterUserClose": closed["state"],
    }


def task_id(result: dict[str, Any]) -> str:
    task = result.get("task") if isinstance(result.get("task"), dict) else {}
    value = task.get("id")
    if not isinstance(value, str) or not value:
        raise RuntimeError(f"accepted task has no id: {result}")
    return value


def expect_conflict(action: Any) -> str:
    try:
        action()
    except CopperbenchError as error:
        require(error.code == "WORKSPACE_REVISION_CONFLICT",
                f"expected WORKSPACE_REVISION_CONFLICT, got {error.code}: {error.details}")
        return error.code or "WORKSPACE_REVISION_CONFLICT"
    raise RuntimeError("stale revision write unexpectedly succeeded")


def run_agent_loop(client: CopperbenchClient, descriptor_workspace_id: str) -> dict[str, Any]:
    initialized = client.initialize("stage15-linux-installed-agent", "1.0")
    initial = client.get_workspace()
    require(workspace_id(initial) == descriptor_workspace_id, "Desktop MCP workspaceId does not match descriptor")
    revision = workspace_revision(initial)
    initial_revision = revision
    initial_elements = list(client.list_mod_elements(limit=50))
    suffix = uuid.uuid4().hex[:8]

    item = client.create_mod_element(
        elementType="item",
        name=f"stage15_agent_item_{suffix}",
        initialValues={"displayName": "Stage15 Agent Item"},
        expectedRevision=revision,
    )
    require(item.get("status") == "committed", f"item creation did not commit: {item}")
    revision = int(item["newRevision"])

    planned = client.plan_workspace_changes(
        expectedRevision=revision,
        idempotencyKey=f"stage15-linux-agent-plan-{suffix}",
        requireRecoveryPoint=True,
        operations=[{
            "operation": "create_mod_element",
            "payload": {
                "elementType": "projectile",
                "name": f"stage15_agent_planned_projectile_{suffix}",
                "initialValues": {},
            },
        }],
    )
    require(planned.get("status") == "succeeded", f"workspace plan did not succeed: {planned}")
    plan = planned.get("data")
    require(isinstance(plan, dict), f"workspace plan payload is missing: {planned}")
    preview = client.preview_workspace_plan(plan)
    preview_data = preview.get("data") if isinstance(preview.get("data"), dict) else {}
    require(preview_data.get("wouldApply") is True, f"workspace plan preview would not apply: {preview}")
    applied = client.apply_workspace_plan(plan=plan, expectedRevision=revision)
    require(applied.get("status") == "committed", f"workspace plan did not commit: {applied}")
    revision = int(applied["newRevision"])

    paged_elements = list(client.list_mod_elements(limit=1))
    require(len(paged_elements) >= len(initial_elements) + 2,
            "cursor traversal did not return the elements created by the Agent loop")

    build = client.build_workspace(revision)
    require(build.get("status") == "accepted", f"build_workspace was not accepted: {build}")
    build_result = wait_task(client, task_id(build))
    require(build_result["state"] == "succeeded", f"first installed-product build failed: {build_result['result']}")

    stale_revision = max(0, revision - 1)
    conflict_code = expect_conflict(lambda: client.create_mod_element(
        elementType="projectile",
        name=f"stage15_stale_projectile_{suffix}",
        initialValues={},
        expectedRevision=stale_revision,
    ))

    refreshed = client.get_workspace()
    revision = workspace_revision(refreshed)
    projectile = client.create_mod_element(
        elementType="projectile",
        name=f"stage15_agent_projectile_{suffix}",
        initialValues={},
        expectedRevision=revision,
    )
    require(projectile.get("status") == "committed", f"conflict retry did not commit: {projectile}")
    revision = int(projectile["newRevision"])

    final_build = client.build_workspace(revision)
    require(final_build.get("status") == "accepted", f"final build was not accepted: {final_build}")
    final_build_result = wait_task(client, task_id(final_build))
    require(final_build_result["state"] == "succeeded",
            f"final installed-product build failed: {final_build_result['result']}")

    final_workspace = client.get_workspace()
    final_revision = workspace_revision(final_workspace)
    final_elements = list(client.list_mod_elements(limit=50))
    require(final_revision == revision, "final workspace revision changed unexpectedly")
    require(len(final_elements) >= len(initial_elements) + 3,
            "final element readback is missing Agent-created elements")

    run_client_lifecycle = wait_interactive_run_client(client, final_revision)

    return {
        "initializeProtocolVersion": initialized.get("protocolVersion"),
        "workspaceId": descriptor_workspace_id,
        "initialRevision": initial_revision,
        "finalRevision": final_revision,
        "initialElementCount": len(initial_elements),
        "finalElementCount": len(final_elements),
        "planPreviewWouldApply": True,
        "firstBuildState": build_result["state"],
        "revisionConflictCode": conflict_code,
        "conflictRetryCommitted": True,
        "finalBuildState": final_build_result["state"],
        "runClientLifecycle": run_client_lifecycle,
    }


def wait_for_shutdown(client: CopperbenchClient, descriptor: Path, timeout_seconds: int) -> dict[str, Any]:
    print("Agent loop passed. Close the installed Copperbench workspace window normally now; shutdown verification is waiting.", flush=True)
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline and descriptor.exists():
        time.sleep(0.5)
    require(not descriptor.exists(), "Desktop MCP descriptor was not removed after Copperbench closed")

    last_error: CopperbenchError | None = None
    probe_deadline = time.monotonic() + 30
    while time.monotonic() < probe_deadline:
        try:
            client.get_workspace()
        except CopperbenchError as error:
            last_error = error
            break
        time.sleep(0.5)
    require(last_error is not None, "old Desktop MCP endpoint/token still worked after Copperbench closed")
    return {
        "descriptorRemoved": True,
        "oldConnectionRejected": True,
        "oldConnectionFailureCode": last_error.code,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("workspace", type=Path, help="Workspace root or .mcreator file opened by installed Copperbench")
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--shutdown-timeout-seconds", type=int, default=300)
    args = parser.parse_args()

    candidate_sha = args.candidate_sha256.lower()
    require(re.fullmatch(r"[0-9a-f]{64}", candidate_sha) is not None, "candidate SHA-256 must be 64 hex characters")
    workspace = args.workspace.expanduser().resolve()
    workspace_root = workspace.parent if workspace.suffix.lower() == ".mcreator" else workspace
    descriptor = workspace_root / ".copperbench" / "mcp-connection.json"
    require(descriptor.is_file(), f"Desktop MCP descriptor is missing: {descriptor}")
    if os.name == "posix":
        require((descriptor.stat().st_mode & 0o777) == 0o600, "Desktop MCP descriptor is not mode 0600")
        require((descriptor.parent.stat().st_mode & 0o777) == 0o700, "Desktop MCP directory is not mode 0700")

    descriptor_data = json.loads(descriptor.read_text(encoding="utf-8"))
    require(isinstance(descriptor_data, dict), "Desktop MCP descriptor is not a JSON object")
    require(descriptor_data.get("permissionProfile") == "workspace",
            "Desktop MCP descriptor does not expose the workspace permission profile")
    require("token" not in descriptor_data and "authorization" not in descriptor_data,
            "Desktop MCP descriptor contains credential material")

    connection = read_workspace_connection(workspace)
    token = getpass.getpass("Paste the one-time Desktop MCP token copied from Copperbench UI: ")
    require(bool(token.strip()), "Desktop MCP token is empty")
    client = CopperbenchClient(connection["url"], token, connection["workspaceId"], timeout=60)

    loop = run_agent_loop(client, connection["workspaceId"])
    audit_path = workspace_root / ".copperbench" / "automation-audit.jsonl"
    require(audit_path.is_file(), "Desktop MCP automation audit was not created")
    audit = audit_path.read_text(encoding="utf-8")
    require(token not in audit, "Desktop MCP credential leaked into the automation audit")
    shutdown = wait_for_shutdown(client, descriptor, max(30, args.shutdown_timeout_seconds))

    result = {
        "schemaVersion": "1.0",
        "status": "passed",
        "candidateSha256": candidate_sha,
        "endpoint": connection["url"],
        "permissionProfile": "workspace",
        "tokenPersisted": False,
        "automationAuditCredentialLeak": False,
        "agentLoop": loop,
        "shutdown": shutdown,
        "formalSupportClaim": False,
    }
    encoded = json.dumps(result, ensure_ascii=False, indent=2)
    require(token not in encoded, "Desktop MCP credential would be written to evidence")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)


if __name__ == "__main__":
    main()
