"""Execute Copperbench AI evals against a real local HTTP MCP session."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from sdk.python.copperbench import CopperbenchClient, CopperbenchError


def expect_status(value: dict[str, Any], expected: str) -> None:
    actual = value.get("status")
    if actual != expected:
        raise AssertionError(f"expected status {expected}, got {actual}: {value}")


def workspace_revision(client: CopperbenchClient) -> int:
    workspace = client.get_workspace()
    return int(workspace.get("data", {}).get("workspace", {}).get("revision", 0))


def task_id(result: dict[str, Any]) -> str:
    task = result.get("task")
    if not isinstance(task, dict) or not isinstance(task.get("id"), str):
        raise AssertionError(f"missing task id: {result}")
    return task["id"]


def rejected(action: Callable[[], Any], code: str) -> None:
    try:
        action()
    except CopperbenchError as error:
        if error.code != code:
            raise AssertionError(f"expected {code}, got {error.code}: {error.details}") from error
        return
    raise AssertionError(f"expected rejection {code}")


def run_read_only(client: CopperbenchClient) -> list[dict[str, str]]:
    rejected(lambda: client.create_mod_element(
        elementType="item", name="readonly_probe", initialValues={}, expectedRevision=0
    ), "PERMISSION_DENIED")
    return [{"id": "readonly-denial", "status": "passed"}]


def run_workspace(client: CopperbenchClient) -> list[dict[str, str]]:
    results: list[dict[str, str]] = []
    revision = workspace_revision(client)

    created = client.create_mod_element(
        elementType="item", name="eval_marker", initialValues={"displayName": "Eval Marker"},
        expectedRevision=revision,
    )
    expect_status(created, "committed")
    revision = int(created["newRevision"])
    results.append({"id": "create-element", "status": "passed"})

    procedure = client.create_mod_element(
        elementType="procedure", name="eval_procedure", initialValues={}, expectedRevision=revision
    )
    expect_status(procedure, "committed")
    revision = int(procedure["newRevision"])
    procedure_id = procedure["data"]["element"]["id"]
    updated = client.update_procedure(
        elementId=procedure_id,
        edits=[{"operation": "set_trigger", "trigger": "on_block_right_clicked"}],
        expectedRevision=revision,
    )
    expect_status(updated, "committed")
    revision = int(updated["newRevision"])
    results.append({"id": "procedure-edit", "status": "passed"})

    registry = client.create_registry_entry(
        registry="variables",
        entry={"name": "eval_score", "dataType": "number", "scope": "global"},
        expectedRevision=revision,
    )
    expect_status(registry, "committed")
    revision = int(registry["newRevision"])
    entry_id = registry["data"]["entry"]["id"]
    renamed = client.rename_registry_entry(
        entryId=entry_id, newName="eval_score_renamed", expectedRevision=revision
    )
    expect_status(renamed, "committed")
    revision = int(renamed["newRevision"])
    results.append({"id": "rename-reference", "status": "passed"})

    build = client.build_workspace(revision)
    expect_status(build, "accepted")
    build_task = task_id(build)
    polled = client.get_task(build_task, 0)
    if polled.get("status") != "succeeded" or polled.get("data", {}).get("task", {}).get("state") != "running":
        raise AssertionError(f"build task was not queryable: {polled}")
    cancelled_build = client.cancel_task(build_task, revision)
    expect_status(cancelled_build, "cancelled")
    results.append({"id": "build-repair", "status": "passed"})

    rejected(lambda: client.create_mod_element(
        elementType="item", name="stale_writer", initialValues={}, expectedRevision=max(0, revision - 1)
    ), "WORKSPACE_REVISION_CONFLICT")
    results.append({"id": "revision-conflict", "status": "passed"})

    datagen_cancel = client.run_datagen(revision)
    expect_status(datagen_cancel, "accepted")
    cancelled = client.cancel_task(task_id(datagen_cancel), revision)
    expect_status(cancelled, "cancelled")
    results.append({"id": "datagen-cancel", "status": "passed"})

    datagen = client.run_datagen(revision)
    expect_status(datagen, "accepted")
    datagen_task = task_id(datagen)
    preview = client.preview_datagen_output(datagen_task)
    if preview.get("status") != "succeeded":
        raise AssertionError(f"datagen preview failed: {preview}")
    manifest_hash = preview.get("data", {}).get("manifestHash")
    if not isinstance(manifest_hash, str) or len(manifest_hash) != 64:
        raise AssertionError(f"invalid manifest hash: {preview}")
    published = client.publish_datagen_output(datagen_task, manifest_hash, revision)
    expect_status(published, "committed")
    revision = int(published["newRevision"])
    results.append({"id": "datagen-publish", "status": "passed"})

    point = client.create_recovery_point("AI eval restore probe", revision)
    expect_status(point, "committed")
    recovery_point_id = point.get("recoveryPointId")
    if not isinstance(recovery_point_id, str):
        raise AssertionError(f"recovery point id missing: {point}")
    rejected(lambda: client.restore_recovery_point(recovery_point_id, revision), "USER_APPROVAL_REQUIRED")
    results.append({"id": "recovery-restore", "status": "passed"})

    reconnect_task = client.build_workspace(revision)
    reconnect_id = task_id(reconnect_task)
    first = client.get_task(reconnect_id, 0)
    second = client.get_task(reconnect_id, 0)
    if first.get("status") != "succeeded" or second.get("status") != "succeeded":
        raise AssertionError("task polling reconnect compatibility failed")
    client.cancel_task(reconnect_id, revision)
    results.append({"id": "task-reconnect", "status": "passed"})
    return results


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("connection", type=Path)
    parser.add_argument("--mode", choices=["workspace", "read_only"], required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    connection = json.loads(args.connection.read_text(encoding="utf-8"))
    endpoint = f"http://127.0.0.1:{connection['port']}/mcp"
    client = CopperbenchClient(endpoint, connection["token"], connection["workspaceId"])
    client.initialize("copperbench-ai-live-eval", "0.1.0")
    results = run_workspace(client) if args.mode == "workspace" else run_read_only(client)
    summary = {"mode": args.mode, "passed": len(results), "failed": 0, "cases": results}
    encoded = json.dumps(summary, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)


if __name__ == "__main__":
    main()
