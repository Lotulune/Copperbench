"""Fixed public API routes on an existing isolated workspace. No grant or EULA issuance.

Requires the shipped Python SDK on sys.path (or installed with pip).
Native/Core and MCP names are deliberately translated only at this example's
boundary. Receipts are actual responses; an ordinary build is never acceptance.
"""
from __future__ import annotations

import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import time
from typing import Any

from copperbench import ClientMetrics, CopperbenchClient, CopperbenchError, NativeApiError, Workspace


def file_sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def workspace_bytes(root: Path, descriptor: Path) -> dict[str, str]:
    # Bound the protection assertion to product content; audit/history and task
    # logs can legitimately change on a rejected call and are not source writes.
    files = [descriptor]
    for directory in (root / "elements", root / "src"):
        if directory.is_dir():
            files.extend(path for path in directory.rglob("*") if path.is_file())
    return {path.relative_to(root).as_posix(): file_sha256(path) for path in sorted(files)}


class Evidence:
    def __init__(self, root: Path, secrets: list[str]):
        root.mkdir(parents=True, exist_ok=False)
        self.root = root
        self.secrets = [value for value in secrets if value]
        self.log_sequences: dict[str, int] = {}
        self.log_entries = 0
        self.repeated_logs = 0

    def redact(self, value: Any) -> Any:
        if isinstance(value, dict):
            return {key: "[redacted]" if key.lower() in {"token", "authorization", "taskauthorizationid"}
                    else self.redact(item) for key, item in value.items()}
        if isinstance(value, list):
            return [self.redact(item) for item in value]
        if isinstance(value, str):
            for secret in self.secrets:
                value = value.replace(secret, "[redacted]")
        return value

    def save(self, name: str, value: Any) -> None:
        encoded = json.dumps(self.redact(value), ensure_ascii=False, indent=2) + "\n"
        temporary = self.root / (name + ".tmp")
        temporary.write_text(encoded, encoding="utf-8")
        temporary.replace(self.root / (name + ".json"))

    def poll(self, result: dict[str, Any]) -> None:
        data = result["data"]
        task_id = data["task"]["id"]
        last = self.log_sequences.get(task_id, 0)
        for entry in data["logs"]:
            self.log_entries += 1
            if entry["sequence"] <= last:
                self.repeated_logs += 1
            last = max(last, entry["sequence"])
        self.log_sequences[task_id] = last
        # Append the original increment before replacing the resumable cursor.
        with (self.root / "task-polls.jsonl").open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(self.redact(result), ensure_ascii=False) + "\n")
        if self.repeated_logs:
            raise RuntimeError("The observed task logs repeated or regressed; the raw poll was retained")
        self.save("checkpoint", {"workspaceId": result.get("workspaceId"), "taskId": task_id,
                                 "afterLogSequence": last, "state": data["task"]["state"]})


class Route:
    def __init__(self, args, metrics: ClientMetrics, token: str):
        self.args, self.metrics, self.token = args, metrics, token
        self.client = None

    def connect(self):
        if self.args.mode == "mcp":
            self.client = CopperbenchClient.from_workspace(self.args.workspace, self.token, metrics=self.metrics,
                task_authorization_id=os.environ.get("COPPERBENCH_TASK_AUTHORIZATION"))
            self.client.initialize()
            self.tools = {tool["name"] for tool in self.client.list_tools()}
        else:
            options = {"task_authorization_id": os.environ.get("COPPERBENCH_TASK_AUTHORIZATION"), "metrics": self.metrics}
            self.client = (Workspace.connect(self.args.workspace, **options) if self.args.mode == "desktop"
                           else Workspace.open(self.args.workspace, launcher=self.args.launcher, **options))
            self.client.require_schema()
            self.tools = set(self.client.operations)

    def require(self, *operations: str):
        missing = set(operations) - self.tools
        if missing:
            raise RuntimeError("Required operations are not exposed: " + ", ".join(sorted(missing)))

    def read_element(self, element_id: str):
        return (self.client.read_mod_element(element_id) if self.args.mode == "mcp"
                else self.client.query("get_mod_element_editor", elementId=element_id))

    def revision(self) -> int:
        return self.client.get_workspace()["revision"]

    def create(self, name: str, values: dict[str, Any], revision: int):
        revision_key = "expectedRevision" if self.args.mode == "mcp" else "expected_revision"
        return self.client.create_mod_element(elementType="block", name=name, initialValues=values,
                                              **{revision_key: revision})

    def update(self, element_id: str, value: str, revision: int):
        revision_key = "expectedRevision" if self.args.mode == "mcp" else "expected_revision"
        return self.client.update_mod_element(elementId=element_id, changes=[{"path": "/displayName", "value": value}],
                                              **{revision_key: revision})

    def build(self, revision: int):
        return (self.client.build_workspace(revision) if self.args.mode == "mcp"
                else self.client.build(expected_revision=revision))

    def export(self, task_id: str, revision: int):
        return (self.client.export_verified_artifact(task_id, revision) if self.args.mode == "mcp"
                else self.client.command("export_workspace", verifiedTaskId=task_id, allowHistorical=False,
                                         expected_revision=revision))

    def close(self):
        if self.client is not None:
            self.client.close()


def require_succeeded(result: dict[str, Any]) -> dict[str, Any]:
    task = result["data"]["task"]
    if task["state"] != "succeeded":
        raise RuntimeError(f"Task {task['id']} ended as {task['state']}; inspect its saved diagnostics")
    return task


def w1(route: Route, environment: dict[str, Any], root: Path, descriptor: Path, evidence: Evidence):
    route.require("create_mod_element", "list_mod_elements", "build_workspace", "get_task")
    contract = route.client.field_contract("block", environment=environment)
    field = next((field for field in contract.get("fields", []) if field.get("name") == "hardness"), None)
    if not field or field.get("writable") is not True or not isinstance(field.get("default"), (int, float)):
        raise RuntimeError("This generator does not expose the required writable numeric hardness contract")
    name = route.args.name
    if list(route.client.list_mod_elements(search=name, fields=["id", "name"])):
        raise RuntimeError("Choose a fresh W1 name; an existing element must not be recreated")
    revision, before = route.revision(), workspace_bytes(root, descriptor)
    try:
        route.create(name, {"displayName": name, "hardness": "not-a-number"}, revision)
    except (NativeApiError, CopperbenchError) as error:
        evidence.save("w1-rejected", {"code": error.code, "details": error.details})
        diagnostics = error.details.get("diagnostics", []) if isinstance(error.details, dict) else []
        if error.code != "FIELD_TYPE_INVALID" or not any("hardness" in str(item.get("path", "")) for item in diagnostics):
            raise RuntimeError("The intentional field failure was not located by the public diagnostic") from error
    else:
        raise RuntimeError("The intentional invalid input unexpectedly committed")
    if route.revision() != revision or workspace_bytes(root, descriptor) != before:
        raise RuntimeError("Rejected W1 input changed content or revision")
    if list(route.client.list_mod_elements(search=name, fields=["id", "name"])):
        raise RuntimeError("Rejected W1 input created an element")
    created = route.create(name, {"displayName": name, "hardness": field["default"]}, revision)
    evidence.save("w1-created", created)
    items = list(route.client.list_mod_elements(search=name, fields=["id", "name"]))
    if len(items) != 1 or items[0]["name"] != name:
        raise RuntimeError("The fixed create did not produce exactly one named element")
    accepted = route.build(route.revision())
    evidence.save("w1-build-start", accepted)
    finished = route.client.wait_task(accepted["task"]["id"], on_update=evidence.poll)
    evidence.save("w1-build-finished", finished)
    require_succeeded(finished)
    return {"status": "passed", "elementId": items[0]["id"], "buildStarts": 1,
            "acceptedCreates": 1, "intentionalRejections": 1, "fieldContractReads": 1}


def w2(route: Route, root: Path, descriptor: Path, evidence: Evidence):
    route.require("update_mod_element", "read_mod_element" if route.args.mode == "mcp" else "get_mod_element_editor")
    original = route.read_element(route.args.element_id)
    element = original["data"]["element"]
    if element.get("ownership") != "generated":
        raise RuntimeError("Choose a generated W1 block; W2 must not take ownership of manual elements")
    revision = original["revision"]
    external_value = "SPEC-04 controlled second edit"
    changed = route.update(route.args.element_id, external_value, revision)
    evidence.save("w2-second-edit", changed)
    before = workspace_bytes(root, descriptor)
    try:
        route.update(route.args.element_id, "SPEC-04 stale draft", revision)
    except (NativeApiError, CopperbenchError) as error:
        evidence.save("w2-revision-rejected", {"code": error.code, "details": error.details})
        conflict_code = "WORKSPACE_REVISION_CONFLICT" if route.args.mode == "mcp" else "REVISION_CONFLICT"
        if error.code != conflict_code or not isinstance(error.details, dict) or not error.details.get("conflict"):
            raise
    else:
        raise RuntimeError("The stale revision unexpectedly committed")
    if workspace_bytes(root, descriptor) != before:
        raise RuntimeError("The stale revision changed content")
    reread = route.read_element(route.args.element_id)
    evidence.save("w2-reread", reread)
    fields = [field for section in reread["data"]["sections"] for field in section.get("fields", [])]
    if (reread["data"]["element"].get("ownership") != "generated"
            or not any(field.get("path") == "/displayName" and field.get("value") == external_value for field in fields)):
        raise RuntimeError("The second edit or ownership was not preserved")
    if route.args.mode == "mcp":
        return {"status": "completed_with_limits", "revisionConflict": "passed",
                "sourceHashConflict": "not_exposed", "reason": "This MCP catalog has no workspace-file editing tools"}
    route.require("read_workspace_file", "update_workspace_file")
    source = route.client.query("read_workspace_file", relativePath=route.args.source_path)["data"]
    if source.get("ownership") != "manual" or source.get("editable") is not True:
        raise RuntimeError("Select an editable manual .java file in the isolated workspace")
    path = (root / route.args.source_path).resolve(strict=True)
    if not path.is_relative_to(root) or file_sha256(path) != source["sha256"]:
        raise RuntimeError("The local source path no longer matches the public source read")
    revised_bytes = path.read_bytes() + b"\n// SPEC-04 controlled external source edit\n"
    revision = route.revision()
    path.write_bytes(revised_bytes)  # Explicit controlled external edit, outside the player/UI evidence.
    try:
        route.client.command("update_workspace_file", expected_revision=revision,
            relativePath=route.args.source_path, content=source["content"], expectedSha256=source["sha256"])
    except NativeApiError as error:
        evidence.save("w2-source-rejected", {"code": error.code, "details": error.details})
        if error.code != "WORKSPACE_SOURCE_CONFLICT":
            raise
    else:
        raise RuntimeError("The stale source hash unexpectedly overwrote disk bytes")
    if path.read_bytes() != revised_bytes or route.revision() != revision:
        raise RuntimeError("The source conflict changed the external bytes or revision")
    refreshed = route.client.query("read_workspace_file", relativePath=route.args.source_path)["data"]
    if refreshed["ownership"] != "manual" or refreshed["sha256"] != file_sha256(path):
        raise RuntimeError("The source reread lost ownership or the updated disk identity")
    evidence.save("w2-source-reread", {key: value for key, value in refreshed.items() if key != "content"})
    return {"status": "passed", "revisionConflict": "passed", "sourceHashConflict": "passed"}


def verify_delivery(task: dict[str, Any], acceptance: dict[str, Any]) -> dict[str, Any]:
    delivery = task.get("verifiedExport")
    verification = acceptance["verification"]
    if (not isinstance(delivery, dict) or delivery.get("taskId") != acceptance["id"]
            or delivery.get("status") != "passed_current_input"
            or delivery.get("sourceSha256") != verification["sourceSnapshot"]["sha256"]):
        raise RuntimeError("Export did not return a current-input receipt for the selected acceptance task")
    directory = Path(delivery["exportDirectory"]).resolve(strict=True)
    for path_key, hash_key in (("artifactPath", "artifactSha256"), ("reportPath", "reportSha256")):
        path = (directory / delivery[path_key]).resolve(strict=True)
        if (not path.is_relative_to(directory) or delivery[hash_key] != verification[hash_key]
                or file_sha256(path) != verification[hash_key]):
            raise RuntimeError("Exported evidence bytes differ from the selected acceptance record")
    receipt = json.loads((directory / "verification.json").read_text(encoding="utf-8"))
    if receipt != {key: value for key, value in delivery.items() if key != "exportDirectory"}:
        raise RuntimeError("The written verification receipt differs from the task receipt")
    return delivery


def w3(route: Route, environment: dict[str, Any], evidence: Evidence):
    route.require("build_workspace", "get_task", "export_verified_artifact" if route.args.mode == "mcp" else "export_workspace")
    build_starts = 0
    task_id, sequence = route.args.task_id, route.args.after_log_sequence
    if task_id is not None:
        evidence.log_sequences[task_id] = sequence
    if task_id is None:
        started = route.build(route.revision())
        build_starts = 1
        evidence.save("w3-build-start", started)  # Keep taskId before any transport teardown.
        task_id = started["task"]["id"]
        evidence.log_sequences[task_id] = sequence
        first = route.client.get_task(task_id, sequence)
        if first["data"]["task"]["id"] != task_id:
            raise RuntimeError("The first poll returned another task identity")
        evidence.poll(first)
        sequence = evidence.log_sequences[task_id]
        route.close()
        route.connect()  # Same live desktop host; never starts a second build.
        environment = route.client.get_workspace_environment()
        if environment["data"]["workspaceId"] != first.get("workspaceId"):
            raise RuntimeError("The reconnected host belongs to another workspace session")
        evidence.save("w3-reconnected-environment", environment)
    result = route.client.wait_task(task_id, after_log_sequence=sequence, on_update=evidence.poll)
    evidence.save("w3-build-finished", result)
    if require_succeeded(result).get("kind") != "build":
        raise RuntimeError("W3 taskId must refer to the original build task")
    selected = route.client.get_task(route.args.acceptance_task)
    evidence.save("w3-selected-acceptance", selected)
    acceptance = require_succeeded(selected)
    verification = acceptance.get("verification")
    if (acceptance.get("kind") != "run_gametest" or not isinstance(verification, dict)
            or verification.get("status") != "passed" or verification.get("mode") != "packaged_jar"
            or verification.get("workspaceId") != environment["data"]["workspaceId"]
            or type(verification.get("acceptanceExecuted")) is not int or verification["acceptanceExecuted"] <= 0
            or not isinstance(verification.get("sourceSnapshot"), dict)
            or any(not isinstance(verification.get(key), str) for key in ("artifactSha256", "reportSha256"))):
        raise RuntimeError("W3 requires a real passed packaged-JAR acceptance task with positive acceptance counts and identities")
    exported = route.export(acceptance["id"], route.revision())
    evidence.save("w3-export-start", exported)
    finished = route.client.wait_task(exported["task"]["id"], on_update=evidence.poll)
    evidence.save("w3-export-finished", finished)
    delivery = verify_delivery(require_succeeded(finished), acceptance)
    evidence.save("w3-delivery", delivery)
    return {"status": "passed", "buildTaskId": task_id, "buildStarts": build_starts, "exportStarts": 1,
            "resumedAfterLogSequence": sequence, "deliveryStatus": delivery["status"]}


def main():
    parser = argparse.ArgumentParser(description="Execute fixed public API routes on an isolated workspace")
    parser.add_argument("route", choices=["discover", "w1", "w2", "w3"])
    parser.add_argument("workspace", type=Path, nargs="?", help="Existing isolated .mcreator file")
    parser.add_argument("--workspace", dest="workspace_option", type=Path, help="Existing isolated .mcreator file")
    parser.add_argument("--mode", "--transport", dest="mode", choices=["independent", "desktop", "mcp"], default="independent")
    parser.add_argument("--launcher", help="Copperbench executable for independent mode")
    parser.add_argument("--output", type=Path, required=True, help="New receipt directory; existing directories are preserved")
    parser.add_argument("--name", default="agent_workflow_block")
    parser.add_argument("--element-id", help="Generated W1 element for W2")
    parser.add_argument("--source-path", help="Editable manual .java file for Native W2")
    parser.add_argument("--task-id", help="Existing W3 build task; supplying it never starts a build")
    parser.add_argument("--after-log-sequence", type=int, default=0)
    parser.add_argument("--acceptance-task", help="Existing real passed packaged-JAR GameTest task for W3")
    args = parser.parse_args()
    if (args.workspace is None) == (args.workspace_option is None):
        parser.error("Provide exactly one workspace path, positionally or with --workspace")
    args.workspace = args.workspace if args.workspace is not None else args.workspace_option
    if args.mode == "independent" and not args.launcher:
        parser.error("independent mode requires --launcher")
    if args.route == "w2" and (not args.element_id or (args.mode != "mcp"
            and (not args.source_path or not args.source_path.endswith(".java")))):
        parser.error("W2 requires --element-id and Native modes also require an editable --source-path ending in .java")
    if args.route == "w3" and (args.mode == "independent" or not args.acceptance_task):
        parser.error("W3 reconnect uses desktop or MCP mode and requires --acceptance-task from real packaged-JAR acceptance")
    if args.after_log_sequence < 0:
        parser.error("--after-log-sequence must be nonnegative")
    descriptor = args.workspace.resolve(strict=True)
    root = descriptor.parent
    output = args.output.resolve()
    if output.is_relative_to(root):
        parser.error("Receipt output must be outside the isolated workspace")
    token = (os.environ.get("COPPERBENCH_MCP_TOKEN") or getpass.getpass("Copperbench pairing token: ")) if args.mode == "mcp" else ""
    evidence = Evidence(output, [token, os.environ.get("COPPERBENCH_TASK_AUTHORIZATION", "")])
    metrics = ClientMetrics()
    started = time.monotonic()
    route, summary = None, {"route": args.route, "mode": args.mode, "status": "failed"}
    try:
        route = Route(args, metrics, token)
        route.connect()
        route.require("get_workspace_environment")
        environment = route.client.get_workspace_environment()
        data = environment["data"]
        if Path(data["workspaceRoot"]).resolve() != root:
            raise RuntimeError("The host workspace identity differs from the selected descriptor")
        evidence.save("environment", environment)
        if args.route == "w1":
            summary.update(w1(route, environment, root, descriptor, evidence))
        elif args.route == "w2":
            summary.update(w2(route, root, descriptor, evidence))
        elif args.route == "w3":
            summary.update(w3(route, environment, evidence))
        else:
            summary.update(status="passed", scope="read-only discovery")
        summary["workspaceId"] = data["workspaceId"]
    except (NativeApiError, CopperbenchError) as error:
        evidence.save("error", {"code": error.code, "details": error.details})
        raise
    except Exception as error:
        evidence.save("error", {"type": type(error).__name__, "message": str(error)})
        raise
    finally:
        try:
            if route is not None:
                route.close()
            summary["sessionCleanup"] = "completed"
        except (NativeApiError, CopperbenchError) as error:
            summary.update(sessionCleanup="failed", status="failed")
            evidence.save("cleanup-error", {"code": error.code, "details": error.details})
            raise
        finally:
            summary.update(elapsedSeconds=time.monotonic() - started, requestMetrics=metrics.snapshot(),
                           receivedLogEntries=evidence.log_entries, repeatedLogEntries=evidence.repeated_logs)
            evidence.save("summary", summary)
            print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
