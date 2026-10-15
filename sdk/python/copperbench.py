"""Minimal dependency-free Copperbench MCP client for Python 3.11+."""

from __future__ import annotations

import json
import math
import re
from http.client import IncompleteRead
from pathlib import Path
import urllib.error
import urllib.parse
import time
import urllib.request
from typing import Any, Callable, Iterator, TYPE_CHECKING

if TYPE_CHECKING:
    from copperbench_native import Workspace, NativeApiError


def __getattr__(name: str) -> Any:
    # Preserve standalone copies of this MCP client used by existing harnesses.
    if name in {"Workspace", "NativeApiError"}:
        import copperbench_native
        return getattr(copperbench_native, name)
    if name in {'context', 'data', 'ops', 'utils', 'types', 'app', 'use_workspace', 'api_help'}:
        import copperbench_scripting
        return getattr(copperbench_scripting, name)
    raise AttributeError(name)


def __dir__():
    return sorted(set(globals()) | {'Workspace', 'NativeApiError', 'context', 'data', 'ops',
                                   'utils', 'types', 'app', 'use_workspace', 'api_help'})


def _render_message(message: Any, default: str) -> str:
    """Literal, single-pass Core formatting; parity is checked against shared fixtures.

    Kept in this file so the MCP client remains independently distributable.
    """
    if isinstance(message, str):
        return message or default
    if not isinstance(message, dict):
        return default
    fallback = message.get("fallback")
    if not isinstance(fallback, str) or not fallback:
        fallback = default
    arguments = message.get("args")
    if not isinstance(arguments, dict):
        return fallback

    def substitute(match: re.Match[str]) -> str:
        name = match.group(1)
        if name not in arguments:
            return match.group(0)
        value = arguments[name]
        if isinstance(value, str):
            return value
        if value is None or type(value) in (bool, int):
            return json.dumps(value)
        if type(value) is float and math.isfinite(value):
            return json.dumps(value)
        return match.group(0)

    return re.sub(r"(?<!\{)\{([A-Za-z_][A-Za-z0-9_]*)\}(?!\})", substitute, fallback)


class CopperbenchError(RuntimeError):
    def __init__(self, message: str, code: str | None = None, details: Any = None):
        super().__init__(message)
        self.code = code
        self.details = details


TASK_AUTHORIZED_TOOLS = {
    "create_workspace", "create_mod_element", "update_mod_element", "delete_mod_element", "update_procedure", "update_workspace_file",
    "set_mod_element_source_management", "create_registry_entry", "update_registry_entry", "delete_registry_entry",
    "rename_registry_entry", "apply_workspace_plan", "import_asset", "import_asset_batch", "move_asset",
    "create_recovery_point", "restore_recovery_point", "publish_datagen_output", "prepare_game_tests",
    "build_workspace", "generate_workspace", "validate_workspace", "run_gametest", "run_datagen", "run_client", "run_server",
}

# Audited reads only. A new tool, plan/preview issuance, or external probe must
# remain single-attempt until its effects have been reviewed. Keep the same
# allowlist in the TypeScript client; shared transport fixtures exercise both.
RETRY_SAFE_TOOLS = frozenset({
    "get_workspace", "get_workspace_environment", "get_workspace_doctor", "preview_generation", "get_mod_element_field_contract", "get_field_reference_options", "get_workspace_health", "get_task",
    "list_mod_elements", "read_mod_element", "get_procedure", "list_workspace_registries",
    "get_workspace_references", "list_recovery_points", "list_task_authorizations",
})

SUCCESS_STATUSES = frozenset({"succeeded", "committed", "accepted", "completed", "cancelled"})
MCP_PROTOCOL_VERSION = "2025-11-25"
TASK_STATES = frozenset({"queued", "running", "succeeded", "failed", "cancelled"})


class ClientMetrics:
    """Opt-in request counters. Never stores credentials, payloads, IDs or log text.

    responseBytes counts complete HTTP bodies or native UTF-8 response lines,
    including error bodies. Interrupted bodies and transport headers are excluded.
    """

    def __init__(self):
        self._operations: dict[str, dict[str, Any]] = {}

    def _add(self, operation: str, field: str, value: int | float = 1) -> None:
        row = self._operations.setdefault(operation, {
            "logicalCalls": 0, "attempts": 0, "responseBytes": 0,
            "elapsedSeconds": 0.0,
        })
        row[field] += value

    def snapshot(self) -> dict[str, Any]:
        return {"operations": {name: dict(row) for name, row in sorted(self._operations.items())},
                "byteScope": "complete response bodies or native lines; transport headers and interrupted bodies excluded"}


def _cursor_page(result: dict[str, Any], seen: set[str], error_type: type[RuntimeError],
                 invalid_code: str) -> tuple[list[dict[str, Any]], str | None]:
    data = result.get("data")
    if (not isinstance(data, dict) or not isinstance(data.get("items"), list)
            or any(not isinstance(item, dict) for item in data["items"])
            or "nextCursor" not in data):
        raise error_type("List response must contain object items and nextCursor", invalid_code, result)
    cursor = data["nextCursor"]
    if cursor is not None and (not isinstance(cursor, str) or not cursor or cursor in seen):
        raise error_type("List response has an invalid or repeated cursor", invalid_code, result)
    if cursor is not None:
        seen.add(cursor)
    return data["items"], cursor


def _field_contract(environment: dict[str, Any], element_type: str,
                    error_type: type[RuntimeError], invalid_code: str) -> dict[str, Any]:
    data = environment.get("data")
    if not isinstance(data, dict):
        raise error_type("Environment response must contain object data", invalid_code, environment)
    if "fieldContracts" not in data:
        raise error_type("This host does not expose field contracts", "FIELD_CONTRACT_NOT_EXPOSED", environment)
    contracts = data["fieldContracts"]
    if not isinstance(contracts, dict):
        raise error_type("Environment fieldContracts must be an object", invalid_code, environment)
    if element_type not in contracts:
        raise error_type(f"No field contract is exposed for {element_type}", "FIELD_CONTRACT_NOT_EXPOSED", environment)
    if not isinstance(contracts[element_type], dict):
        raise error_type("The requested field contract is malformed", invalid_code, environment)
    return contracts[element_type]


def _poll_task(get_task: Callable[[str, int], dict[str, Any]], task_id: str, *,
               timeout: float, poll_interval: float, after_log_sequence: int,
               on_update: Callable[[dict[str, Any]], None] | None,
               error_type: type[RuntimeError], invalid_code: str, timeout_code: str) -> dict[str, Any]:
    if not math.isfinite(timeout) or not math.isfinite(poll_interval) or timeout <= 0 or poll_interval <= 0:
        raise ValueError("timeout and poll_interval must be positive and finite")
    if type(after_log_sequence) is not int or after_log_sequence < 0:
        raise ValueError("after_log_sequence must be a nonnegative integer")
    deadline = time.monotonic() + timeout
    sequence = after_log_sequence
    logs: list[dict[str, Any]] = []
    while True:
        result = get_task(task_id, sequence)
        data = result.get("data")
        task = data.get("task") if isinstance(data, dict) else None
        if (not isinstance(task, dict) or task.get("id") != task_id
                or not isinstance(task.get("state"), str) or task["state"] not in TASK_STATES
                or not isinstance(data.get("logs"), list) or not isinstance(data.get("diagnostics"), list)):
            raise error_type("Task response has an invalid identity, state, logs or diagnostics", invalid_code, result)
        next_sequence = sequence
        for entry in data["logs"]:
            if (not isinstance(entry, dict) or type(entry.get("sequence")) is not int
                    or entry["sequence"] <= next_sequence):
                raise error_type("Task logs must have strictly increasing new sequences", invalid_code, result)
            next_sequence = entry["sequence"]
        sequence = next_sequence
        logs.extend(data["logs"])
        # The callback receives each original incremental response. Persist it
        # before the next read so a transport loss can resume from its sequence.
        if on_update is not None:
            on_update(result)
        if task["state"] in {"succeeded", "failed", "cancelled"}:
            return {**result, "data": {**data, "logs": logs}}
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise error_type(f"Task {task_id} is still running; resume after log sequence {sequence}",
                             timeout_code, {**result, "data": {**data, "logs": logs}})
        time.sleep(min(poll_interval, remaining))


class CopperbenchClient:
    def __init__(self, endpoint: str, token: str, workspace_id: str, timeout: float = 30.0,
                 max_transport_retries: int = 2, retry_backoff_seconds: float = 0.1,
                 task_authorization_id: str | None = None, metrics: ClientMetrics | None = None):
        self.endpoint = endpoint
        self.token = token
        self.workspace_id = workspace_id
        self.task_authorization_id = task_authorization_id
        self.timeout = timeout
        self.max_transport_retries = max(0, min(2, max_transport_retries))
        self.retry_backoff_seconds = max(0.0, retry_backoff_seconds)
        self._request_id = 0
        self._session_id: str | None = None
        self.metrics = metrics

    @classmethod
    def from_workspace(cls, workspace: str | Path, token: str, **kwargs: Any) -> "CopperbenchClient":
        connection = read_workspace_connection(workspace)
        return cls(connection["url"], token, connection["workspaceId"], **kwargs)

    def initialize(self, client_name: str = "copperbench-python-sdk", version: str = "0.1.0") -> dict[str, Any]:
        result = self._rpc("initialize", {
            "protocolVersion": MCP_PROTOCOL_VERSION,
            "capabilities": {},
            "clientInfo": {"name": client_name, "version": version},
        })
        if not isinstance(result.get("protocolVersion"), str):
            raise CopperbenchError("MCP initialization has no protocol version", "MCP_RESPONSE_INVALID", result)
        if result["protocolVersion"] != MCP_PROTOCOL_VERSION:
            raise CopperbenchError("The host selected an unsupported MCP protocol", "MCP_PROTOCOL_INCOMPATIBLE", result)
        self._rpc("notifications/initialized", {}, notification=True)
        return result

    def close(self) -> None:
        """Delete only this MCP session, once. Workspace tasks remain with the host."""
        if self._session_id is None:
            return
        started = time.monotonic()
        if self.metrics is not None:
            self.metrics._add("session/close", "logicalCalls")
            self.metrics._add("session/close", "attempts")
        request = urllib.request.Request(self.endpoint, headers={
            "Authorization": f"Bearer {self.token}", "X-Copperbench-Workspace": self.workspace_id,
            "mcp-session-id": self._session_id,
        }, method="DELETE")
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                received = response.read()
                if self.metrics is not None:
                    self.metrics._add("session/close", "responseBytes", len(received))
            self._session_id = None
        except urllib.error.HTTPError as error:
            try:
                received = error.read()
                if self.metrics is not None:
                    self.metrics._add("session/close", "responseBytes", len(received))
                detail = received.decode(errors="replace")
            except (OSError, IncompleteRead):
                detail = "HTTP error response body was unavailable"
            if error.code == 404:
                self._session_id = None  # The session has already expired.
                return
            raise CopperbenchError(f"MCP HTTP {error.code}", f"HTTP_{error.code}", detail) from error
        except (urllib.error.URLError, TimeoutError, ConnectionError, IncompleteRead) as error:
            raise CopperbenchError("MCP session close failed; request was not retried", "MCP_TRANSPORT_FAILED", str(error)) from error
        finally:
            if self.metrics is not None:
                self.metrics._add("session/close", "elapsedSeconds", time.monotonic() - started)

    def list_tools(self) -> Iterator[dict[str, Any]]:
        """Discover actual MCP names and input schemas, following MCP pagination."""
        cursor = None
        seen: set[str] = set()
        while True:
            result = self._rpc("tools/list", {"cursor": cursor} if cursor else {})
            tools = result.get("tools")
            if (not isinstance(tools, list) or any(not isinstance(tool, dict)
                    or not isinstance(tool.get("name"), str) or not tool["name"]
                    or not isinstance(tool.get("inputSchema"), dict) for tool in tools)):
                raise CopperbenchError("MCP tool catalog is malformed", "MCP_RESPONSE_INVALID", result)
            cursor = result.get("nextCursor")  # MCP allows omission on the final page.
            if cursor is not None and (not isinstance(cursor, str) or not cursor or cursor in seen):
                raise CopperbenchError("MCP tool catalog has an invalid or repeated cursor", "MCP_RESPONSE_INVALID", result)
            if cursor is not None:
                seen.add(cursor)
            yield from tools
            if cursor is None:
                return

    def get_workspace(self) -> dict[str, Any]:
        return self.call_tool("get_workspace", {})

    def get_workspace_health(self) -> dict[str, Any]:
        return self.call_tool("get_workspace_health", {})

    def read_mod_element(self, element_id: str, *, schema_version: str = "1.1") -> dict[str, Any]:
        """Explicit editor version; 1.0 preserves the frozen legacy projection."""
        if schema_version not in ("1.0", "1.1"):
            raise ValueError("Unsupported editor schema version")
        result = self.call_tool("read_mod_element", {"elementId": element_id, "schemaVersion": schema_version})
        if result.get("schemaVersion") != schema_version:
            raise CopperbenchError("Core did not return the requested editor schema", "MCP_TOOL_RESULT_INVALID", result)
        return result

    def preview_generation(self) -> dict[str, Any]:
        """Read source safety; ready is an observation, not build acceptance or a write permit."""
        return self.call_tool("preview_generation", {})

    def doctor(self) -> dict[str, Any]:
        """Read local environment findings; no download, task or authorization is started."""
        return self.call_tool("get_workspace_doctor", {})

    def get_mod_element_field_contract(self, element_type: str) -> dict[str, Any]:
        """Discover creation metadata without creating an element."""
        return self.call_tool("get_mod_element_field_contract", {"elementType": element_type})

    def get_field_reference_options(self, element_type: str, mapping_source: str, **options: Any) -> dict[str, Any]:
        return self.call_tool("get_field_reference_options",
                              {"elementType": element_type, "mappingSource": mapping_source, **options})

    def get_workspace_environment(self) -> dict[str, Any]:
        return self.call_tool("get_workspace_environment", {})

    def field_contract(self, element_type: str = "block", *,
                       environment: dict[str, Any] | None = None) -> dict[str, Any]:
        return _field_contract(self.get_workspace_environment() if environment is None else environment,
                               element_type, CopperbenchError, "MCP_TOOL_RESULT_INVALID")

    def list_mod_elements(self, **arguments: Any) -> Iterator[dict[str, Any]]:
        cursor = arguments.get("cursor")
        seen = {cursor} if isinstance(cursor, str) else set()
        while True:
            page = self.call_tool("list_mod_elements", {
                **arguments,
                "limit": arguments.get("limit", 200),
                **({"cursor": cursor} if cursor else {}),
            })
            items, cursor = _cursor_page(page, seen, CopperbenchError, "MCP_TOOL_RESULT_INVALID")
            yield from items
            if cursor is None:
                return

    def create_mod_element(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("create_mod_element", arguments)

    def update_mod_element(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("update_mod_element", arguments)

    def update_procedure(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("update_procedure", arguments)

    def rename_registry_entry(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("rename_registry_entry", arguments)

    def preview_registry_rename(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("preview_registry_rename", arguments)

    def create_registry_entry(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("create_registry_entry", arguments)

    def list_workspace_registries(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("list_workspace_registries", arguments)

    def plan_workspace_changes(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("plan_workspace_changes", arguments)

    def plan_procedure_refactor(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("plan_procedure_refactor", arguments)

    def preview_workspace_plan(self, plan: dict[str, Any]) -> dict[str, Any]:
        return self.call_tool("preview_workspace_plan", {"plan": plan})

    def apply_workspace_plan(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("apply_workspace_plan", arguments)

    def list_assets(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("list_assets", arguments)

    def preview_asset_move(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("preview_asset_move", arguments)

    def move_asset(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("move_asset", arguments)

    def build_workspace(self, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("build_workspace", {"expectedRevision": expected_revision})

    def run_datagen(self, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("run_datagen", {"expectedRevision": expected_revision})

    def preview_datagen_output(self, task_id: str) -> dict[str, Any]:
        return self.call_tool("preview_datagen_output", {"taskId": task_id})

    def publish_datagen_output(self, task_id: str, manifest_hash: str, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("publish_datagen_output", {
            "taskId": task_id,
            "manifestHash": manifest_hash,
            "expectedRevision": expected_revision,
        })

    def get_task(self, task_id: str, after_log_sequence: int = 0) -> dict[str, Any]:
        return self.call_tool("get_task", {"taskId": task_id, "afterLogSequence": after_log_sequence})

    def wait_task(self, task_id: str, *, timeout: float = 2700, poll_interval: float = 0.2,
                  after_log_sequence: int = 0,
                  on_update: Callable[[dict[str, Any]], None] | None = None) -> dict[str, Any]:
        """Query an existing task; timeout never cancels it or submits a new task.

        Returns the final Core envelope with logs received during this wait.
        Persist on_update responses to resume after a transport error; the
        original transport error and its details propagate unchanged.
        """
        return _poll_task(self.get_task, task_id, timeout=timeout, poll_interval=poll_interval,
                          after_log_sequence=after_log_sequence, on_update=on_update,
                          error_type=CopperbenchError, invalid_code="MCP_TOOL_RESULT_INVALID",
                          timeout_code="MCP_TASK_TIMEOUT")

    def export_verified_artifact(self, task_id: str, expected_revision: int, *,
                                 allow_historical: bool = False) -> dict[str, Any]:
        return self.call_tool("export_verified_artifact", {"verifiedTaskId": task_id,
                              "expectedRevision": expected_revision, "allowHistorical": allow_historical})

    def prepare_game_tests(self, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("prepare_game_tests", {"expectedRevision": expected_revision})

    def run_gametest(self, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("run_gametest", {"expectedRevision": expected_revision})

    def version_copy_upgrade(self, operation: str = "preview", *, target_generator_id: str | None = None,
                             supported_versions: tuple[str, ...] = ("1",), output_name: str | None = None,
                             plan_id: str | None = None, expected_revision: int | None = None,
                             task_authorization_id: str | None = None) -> dict[str, Any]:
        """Version 1 is read-only. Explicit version 2 executes a reviewed plan once, with target authority."""
        arguments: dict[str, Any] = {"supportedVersions": list(supported_versions)}
        for key, value in (("targetGeneratorId", target_generator_id), ("outputName", output_name),
                           ("planId", plan_id), ("expectedRevision", expected_revision),
                           ("taskAuthorizationId", task_authorization_id)):
            if value is not None:
                arguments[key] = value
        result = self.call_tool("version_copy_upgrade", {**arguments, "operation": operation})
        if result.get("contract") != "version_copy_upgrade" or result.get("selectedVersion") not in supported_versions:
            raise CopperbenchError("Version copy upgrade contract was not negotiated", "INVALID_RESPONSE", result)
        return result

    def preview(self, operation: str, *, generator_id: str | None = None,
                command: str | None = None, workspace_folder_path: str | None = None,
                expected_revision: int | None = None, supported_versions: tuple[str, ...] = ("1",),
                payload: dict[str, Any] | None = None) -> dict[str, Any]:
        """Choose or revoke a scoped preview; every command still rechecks Core authority and evidence."""
        arguments: dict[str, Any] = {"supportedVersions": list(supported_versions)}
        if generator_id is not None:
            arguments["generatorId"] = generator_id
        if command is not None:
            arguments["command"] = command
        if workspace_folder_path is not None:
            arguments["workspaceFolderPath"] = workspace_folder_path
        if payload is not None:
            arguments["payload"] = dict(payload)
        if operation in {"opt_in", "revoke", "command"}:
            revision = expected_revision
            if type(revision) is not int or revision < 0:
                raise ValueError("expected_revision must be a nonnegative integer")
            arguments["expectedRevision"] = revision
        if operation == "command" and self.task_authorization_id and command in TASK_AUTHORIZED_TOOLS:
            arguments.setdefault("payload", {}).setdefault("taskAuthorizationId", self.task_authorization_id)
        arguments["operation"] = operation
        result = self.call_tool("preview", arguments)
        if result.get("contract") != "preview_targets" or result.get("selectedVersion") not in supported_versions:
            raise CopperbenchError("Preview contract was not negotiated", "INVALID_RESPONSE", result)
        return result

    def acceptance(self, operation: str, *, expected_revision: int | None = None,
                   supported_versions: tuple[str, ...] = ("1",), diagnostic_versions: tuple[str, ...] | None = None,
                   **payload: Any) -> dict[str, Any]:
        """Negotiate required_cases for this request; mutations are never retried."""
        arguments: dict[str, Any] = {"operation": operation, "supportedVersions": list(supported_versions), "payload": payload}
        if diagnostic_versions is not None:
            arguments["diagnosticVersions"] = list(diagnostic_versions)
        if operation in {"run", "export"}:
            if type(expected_revision) is not int or expected_revision < 0:
                raise ValueError("expected_revision must be a nonnegative integer")
            arguments["expectedRevision"] = expected_revision
            if operation == "run" and self.task_authorization_id:
                payload.setdefault("taskAuthorizationId", self.task_authorization_id)
        result = self.call_tool("acceptance", arguments)
        if result.get("contract") != "required_cases" or result.get("selectedVersion") not in supported_versions:
            raise CopperbenchError("Required-case contract was not negotiated", "INVALID_RESPONSE", result)
        diagnostics = result.get("coverageDiagnostics")
        if diagnostic_versions is not None and (not isinstance(diagnostics, dict) or diagnostics.get("schemaVersion") not in diagnostic_versions):
            raise CopperbenchError("Acceptance diagnostic contract was not negotiated", "INVALID_RESPONSE", result)
        return result

    def get_acceptance_diagnostics(self, task_id: str) -> dict[str, Any]:
        """Read Core's recorded machine reasons and counts without executing a product command."""
        return self.acceptance("get", diagnostic_versions=("1",), taskId=task_id)["coverageDiagnostics"]

    def list_task_authorizations(self) -> dict[str, Any]:
        return self.call_tool("list_task_authorizations", {})

    def revoke_task_authorization(self, authorization_id: str, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("revoke_task_authorization", {"authorizationId": authorization_id, "expectedRevision": expected_revision})

    def cancel_task(self, task_id: str, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("cancel_task", {"taskId": task_id, "expectedRevision": expected_revision})

    def create_recovery_point(self, label: str, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("create_recovery_point", {"label": label, "expectedRevision": expected_revision})

    def preview_recovery_restore(self, recovery_point_id: str) -> dict[str, Any]:
        return self.call_tool("preview_recovery_restore", {"recoveryPointId": recovery_point_id})

    def restore_recovery_point(self, recovery_point_id: str, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("restore_recovery_point", {
            "recoveryPointId": recovery_point_id,
            "expectedRevision": expected_revision,
        })

    def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if self.task_authorization_id and name in TASK_AUTHORIZED_TOOLS:
            arguments = {"taskAuthorizationId": self.task_authorization_id, **arguments}
        result = self._rpc("tools/call", {"name": name, "arguments": arguments})
        if "isError" in result and type(result["isError"]) is not bool:
            raise CopperbenchError(f"Tool {name} returned an invalid isError flag", "MCP_TOOL_RESULT_INVALID", result)
        content = result.get("content") if isinstance(result.get("content"), list) else []
        text = next((item.get("text") for item in content if isinstance(item, dict) and item.get("type") == "text"), None)
        if not isinstance(text, str):
            raise CopperbenchError(f"Tool {name} returned no JSON text content", "MCP_TOOL_RESULT_INVALID", result)
        try:
            value = json.loads(text)
        except json.JSONDecodeError as error:
            if result.get("isError") is True:
                raise CopperbenchError(text or "MCP_TOOL_RESULT_INVALID", "MCP_TOOL_RESULT_INVALID", result) from error
            raise CopperbenchError(f"Tool {name} returned invalid JSON content", "MCP_TOOL_RESULT_INVALID", result) from error
        if not isinstance(value, dict):
            if result.get("isError") is True:
                raise CopperbenchError(text or "MCP_TOOL_RESULT_INVALID", "MCP_TOOL_RESULT_INVALID", result)
            raise CopperbenchError(f"Tool {name} returned a non-object result", "MCP_TOOL_RESULT_INVALID", result)
        status = value.get("status")
        if result.get("isError") is True or status in ("rejected", "failed") or (
            isinstance(value.get("code"), str) and value["code"]
            and (not isinstance(status, str) or status not in SUCCESS_STATUSES)
        ):
            diagnostics = value.get("diagnostics") if isinstance(value.get("diagnostics"), list) else []
            diagnostic = next((item for item in diagnostics if isinstance(item, dict)
                               and isinstance(item.get("code"), str) and item["code"]), {})
            code = value.get("code")
            if not isinstance(code, str) or not code:
                code = diagnostic.get("code", "MCP_TOOL_ERROR")
            if value.get("conflict"):
                code = "REVISION_CONFLICT"
            message = diagnostic.get("message", value.get("message"))
            raise CopperbenchError(_render_message(message, code), code, value)
        if not isinstance(status, str) or status not in SUCCESS_STATUSES:
            raise CopperbenchError(f"Tool {name} returned no recognized result status", "MCP_TOOL_RESULT_INVALID", value)
        return value

    def _rpc(self, method: str, params: dict[str, Any], notification: bool = False) -> dict[str, Any]:
        if self.metrics is None:
            return self._rpc_once(method, params, notification)
        operation = params["name"] if method == "tools/call" else method
        started = time.monotonic()
        if self.metrics is not None:
            self.metrics._add(operation, "logicalCalls")
        try:
            return self._rpc_once(method, params, notification)
        finally:
            if self.metrics is not None:
                self.metrics._add(operation, "elapsedSeconds", time.monotonic() - started)

    def _rpc_once(self, method: str, params: dict[str, Any], notification: bool = False) -> dict[str, Any]:
        self._request_id += 1
        body: dict[str, Any] = {"jsonrpc": "2.0", "method": method, "params": params}
        if not notification:
            body["id"] = self._request_id
        headers = {
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.token}",
            "X-Copperbench-Workspace": self.workspace_id,
        }
        if self._session_id:
            headers["mcp-session-id"] = self._session_id
        request = urllib.request.Request(self.endpoint, json.dumps(body).encode(), headers, method="POST")
        max_retries = self.max_transport_retries if (
            method == "tools/call" and params.get("name") in RETRY_SAFE_TOOLS
        ) else 0
        operation = params["name"] if method == "tools/call" else method
        for attempt in range(max_retries + 1):
            if self.metrics is not None:
                self.metrics._add(operation, "attempts")
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    received = response.read()
                    if self.metrics is not None:
                        self.metrics._add(operation, "responseBytes", len(received))
                    raw = received.decode()
                    self._session_id = self._session_id or response.headers.get("mcp-session-id")
                break
            except urllib.error.HTTPError as error:
                try:
                    received = error.read()
                    if self.metrics is not None:
                        self.metrics._add(operation, "responseBytes", len(received))
                    detail = received.decode(errors="replace")
                except (OSError, IncompleteRead):
                    detail = "HTTP error response body was unavailable"
                if error.code not in {502, 503, 504} or attempt >= max_retries:
                    raise CopperbenchError(f"MCP HTTP {error.code}", f"HTTP_{error.code}", detail) from error
                time.sleep(self.retry_backoff_seconds * 2 ** attempt)
            except (urllib.error.URLError, TimeoutError, ConnectionError, IncompleteRead) as error:
                if attempt >= max_retries:
                    raise CopperbenchError("MCP transport failed", "MCP_TRANSPORT_FAILED", str(error)) from error
                time.sleep(self.retry_backoff_seconds * 2 ** attempt)
            except UnicodeDecodeError as error:
                raise CopperbenchError("MCP response is not valid UTF-8", "MCP_RESPONSE_INVALID", str(error)) from error
        if notification:
            return {}
        envelope = _parse_rpc_envelope(raw)
        if envelope.get("jsonrpc") != "2.0" or type(envelope.get("id")) is not int or envelope["id"] != body["id"]:
            raise CopperbenchError("MCP response has an invalid version or request ID", "MCP_RESPONSE_INVALID", envelope)
        if ("error" in envelope) == ("result" in envelope):
            raise CopperbenchError("MCP response must contain exactly one result or error", "MCP_RESPONSE_INVALID", envelope)
        if "error" in envelope:
            error = envelope["error"]
            if (not isinstance(error, dict) or not isinstance(error.get("message"), str)
                or type(error.get("code")) not in (int, str) or error.get("code") == ""):
                raise CopperbenchError("MCP response has an invalid JSON-RPC error", "MCP_RESPONSE_INVALID", envelope)
            raise CopperbenchError(error["message"], str(error["code"]), error)
        if not isinstance(envelope["result"], dict):
            raise CopperbenchError("MCP response result must be an object", "MCP_RESPONSE_INVALID", envelope)
        return envelope["result"]


def _parse_rpc_envelope(body: str) -> dict[str, Any]:
    body = body.strip()
    encoded = body if body.startswith("{") else next(
        (line[5:].lstrip(" ") for line in body.splitlines() if line.startswith("data:")), None)
    if encoded is None:
        raise CopperbenchError("MCP response did not contain a JSON-RPC envelope", "MCP_RESPONSE_INVALID", body)
    try:
        envelope = json.loads(encoded)
    except json.JSONDecodeError as error:
        raise CopperbenchError("MCP response contained invalid JSON", "MCP_RESPONSE_INVALID", body) from error
    if not isinstance(envelope, dict):
        raise CopperbenchError("MCP response envelope must be an object", "MCP_RESPONSE_INVALID", body)
    return envelope


def read_workspace_connection(workspace: str | Path) -> dict[str, str]:
    """Read non-secret desktop MCP metadata from a workspace root or .mcreator path."""
    value = Path(workspace).expanduser().resolve()
    root = value.parent if value.suffix.lower() == ".mcreator" else value
    path = root / ".copperbench" / "mcp-connection.json"
    try:
        connection = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise CopperbenchError(
            f"Could not read Copperbench MCP connection metadata: {path}",
            "MCP_CONNECTION_FILE_UNAVAILABLE",
            str(error),
        ) from error
    if not isinstance(connection, dict):
        raise CopperbenchError("MCP connection metadata must be a JSON object", "MCP_CONNECTION_FILE_INVALID", connection)
    if "token" in connection or "authorization" in connection:
        raise CopperbenchError("MCP connection metadata must not contain credentials", "MCP_CONNECTION_FILE_INVALID")
    if connection.get("schemaVersion") != "1.0" or connection.get("status") != "listening":
        raise CopperbenchError("MCP connection metadata is not a listening v1.0 endpoint", "MCP_CONNECTION_FILE_INVALID", connection)
    url = connection.get("url")
    workspace_id = connection.get("workspaceId")
    try:
        parsed_url = urllib.parse.urlsplit(url) if isinstance(url, str) else None
        parsed_port = parsed_url.port if parsed_url is not None else None
    except ValueError:
        parsed_url = None
        parsed_port = None
    if (
        parsed_url is None
        or parsed_url.scheme != "http"
        or parsed_url.hostname != "127.0.0.1"
        or parsed_port is None
        or not 1 <= parsed_port <= 65535
        or parsed_url.username is not None
        or parsed_url.password is not None
        or parsed_url.path != "/mcp"
        or parsed_url.query
        or parsed_url.fragment
    ):
        raise CopperbenchError("MCP connection URL must be a loopback /mcp endpoint", "MCP_CONNECTION_FILE_INVALID", connection)
    if not isinstance(workspace_id, str) or not workspace_id:
        raise CopperbenchError("MCP connection metadata has no workspaceId", "MCP_CONNECTION_FILE_INVALID", connection)
    return {"url": url, "workspaceId": workspace_id}
