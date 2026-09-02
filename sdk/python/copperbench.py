"""Minimal dependency-free Copperbench MCP client for Python 3.11+."""

from __future__ import annotations

import json
from pathlib import Path
import urllib.error
import time
import urllib.request
from typing import Any, Iterator


class CopperbenchError(RuntimeError):
    def __init__(self, message: str, code: str | None = None, details: Any = None):
        super().__init__(message)
        self.code = code
        self.details = details


class CopperbenchClient:
    def __init__(self, endpoint: str, token: str, workspace_id: str, timeout: float = 30.0,
                 max_transport_retries: int = 2, retry_backoff_seconds: float = 0.1):
        self.endpoint = endpoint
        self.token = token
        self.workspace_id = workspace_id
        self.timeout = timeout
        self.max_transport_retries = max(0, min(2, max_transport_retries))
        self.retry_backoff_seconds = max(0.0, retry_backoff_seconds)
        self._request_id = 0
        self._session_id: str | None = None

    @classmethod
    def from_workspace(cls, workspace: str | Path, token: str, **kwargs: Any) -> "CopperbenchClient":
        connection = read_workspace_connection(workspace)
        return cls(connection["url"], token, connection["workspaceId"], **kwargs)

    def initialize(self, client_name: str = "copperbench-python-sdk", version: str = "0.1.0") -> dict[str, Any]:
        result = self._rpc("initialize", {
            "protocolVersion": "2025-11-25",
            "capabilities": {},
            "clientInfo": {"name": client_name, "version": version},
        })
        self._rpc("notifications/initialized", {}, notification=True)
        return result

    def get_workspace(self) -> dict[str, Any]:
        return self.call_tool("get_workspace", {})

    def list_mod_elements(self, **arguments: Any) -> Iterator[dict[str, Any]]:
        cursor: str | None = None
        while True:
            page = self.call_tool("list_mod_elements", {
                **arguments,
                "limit": arguments.get("limit", 200),
                **({"cursor": cursor} if cursor else {}),
            })
            data = page.get("data") if isinstance(page.get("data"), dict) else {}
            for item in data.get("items", []):
                if isinstance(item, dict):
                    yield item
            cursor = data.get("nextCursor") if isinstance(data.get("nextCursor"), str) else None
            if not cursor:
                return

    def create_mod_element(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("create_mod_element", arguments)

    def update_mod_element(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("update_mod_element", arguments)

    def update_procedure(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("update_procedure", arguments)

    def rename_registry_entry(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("rename_registry_entry", arguments)

    def create_registry_entry(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("create_registry_entry", arguments)

    def list_workspace_registries(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("list_workspace_registries", arguments)

    def plan_workspace_changes(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("plan_workspace_changes", arguments)

    def preview_workspace_plan(self, plan: dict[str, Any]) -> dict[str, Any]:
        return self.call_tool("preview_workspace_plan", {"plan": plan})

    def apply_workspace_plan(self, **arguments: Any) -> dict[str, Any]:
        return self.call_tool("apply_workspace_plan", arguments)

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

    def cancel_task(self, task_id: str, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("cancel_task", {"taskId": task_id, "expectedRevision": expected_revision})

    def create_recovery_point(self, label: str, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("create_recovery_point", {"label": label, "expectedRevision": expected_revision})

    def restore_recovery_point(self, recovery_point_id: str, expected_revision: int) -> dict[str, Any]:
        return self.call_tool("restore_recovery_point", {
            "recoveryPointId": recovery_point_id,
            "expectedRevision": expected_revision,
        })

    def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        result = self._rpc("tools/call", {"name": name, "arguments": arguments})
        content = result.get("content") if isinstance(result.get("content"), list) else []
        text = next((item.get("text") for item in content if isinstance(item, dict) and item.get("type") == "text"), None)
        if not isinstance(text, str):
            raise CopperbenchError(f"Tool {name} returned no JSON content")
        value = json.loads(text)
        if value.get("status") in {"rejected", "failed"}:
            diagnostics = value.get("diagnostics") if isinstance(value.get("diagnostics"), list) else []
            diagnostic = diagnostics[0] if diagnostics and isinstance(diagnostics[0], dict) else {}
            raise CopperbenchError(f"Tool {name} was rejected", diagnostic.get("code"), value)
        return value

    def _rpc(self, method: str, params: dict[str, Any], notification: bool = False) -> dict[str, Any]:
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
        for attempt in range(self.max_transport_retries + 1):
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    raw = response.read().decode()
                    self._session_id = self._session_id or response.headers.get("mcp-session-id")
                break
            except urllib.error.HTTPError as error:
                if error.code not in {502, 503, 504} or attempt >= self.max_transport_retries:
                    raise CopperbenchError(f"MCP HTTP {error.code}", f"HTTP_{error.code}", error.read().decode()) from error
                error.read()
                time.sleep(self.retry_backoff_seconds * 2 ** attempt)
            except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
                if attempt >= self.max_transport_retries:
                    raise CopperbenchError("MCP transport failed", "MCP_TRANSPORT_FAILED", str(error)) from error
                time.sleep(self.retry_backoff_seconds * 2 ** attempt)
        if notification:
            return {}
        envelope = _parse_rpc_envelope(raw)
        if envelope.get("error"):
            error = envelope["error"]
            raise CopperbenchError(str(error.get("message", "MCP request failed")), str(error.get("code", "MCP_ERROR")), error)
        return envelope.get("result", {})


def _parse_rpc_envelope(body: str) -> dict[str, Any]:
    body = body.strip()
    if body.startswith("{"):
        return json.loads(body)
    for line in body.splitlines():
        if line.startswith("data: "):
            return json.loads(line[6:])
    raise CopperbenchError("MCP response did not contain a JSON-RPC envelope", "MCP_RESPONSE_INVALID", body)


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
    if not isinstance(url, str) or not url.startswith("http://127.0.0.1:") or not url.endswith("/mcp"):
        raise CopperbenchError("MCP connection URL must be a loopback /mcp endpoint", "MCP_CONNECTION_FILE_INVALID", connection)
    if not isinstance(workspace_id, str) or not workspace_id:
        raise CopperbenchError("MCP connection metadata has no workspaceId", "MCP_CONNECTION_FILE_INVALID", connection)
    return {"url": url, "workspaceId": workspace_id}
