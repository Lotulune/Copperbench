"""Copperbench Core scripting: independent process or live desktop, without MCP."""

from __future__ import annotations

import json
import hashlib
import math
import os
from pathlib import Path
import queue
import re
import shutil
import socket
import subprocess
import threading
import time
from typing import Any, Callable, Iterator, Sequence
import uuid

from copperbench import ClientMetrics, _cursor_page, _poll_task


_MAX_RESPONSE_CHARS = 32 * 1024 * 1024


class NativeApiError(RuntimeError):
    def __init__(self, message: str, code: str, details: Any = None):
        super().__init__(message)
        self.code = code
        self.details = details


def _render_message(message: Any, default: str) -> str:
    """Render Core's named placeholders without format evaluation or mutation.

    Unknown placeholders stay visible. Values are inserted once, literally;
    attribute/index access, format specifications and recursive expansion are
    intentionally unsupported. Raw keys and arguments remain in error.details.
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


class Workspace:
    """A Core session opened independently or attached to the live desktop.

    Use as a context manager. Closing an attached session only disconnects;
    closing an independent session stops its host. Calls are serialized and
    mutations are never retried.
    """

    @classmethod
    def open(cls, workspace: str | Path, *, launcher: str | Path | Sequence[str] | None = None,
             task_authorization_id: str | None = None, startup_timeout: float = 120,
             request_timeout: float = 30, cwd: str | Path | None = None,
             metrics: ClientMetrics | None = None) -> Workspace:
        """Open an existing .mcreator file without an MCP token or running desktop.

        launcher is the Copperbench executable, or an explicit argv sequence
        (for example a Java launcher). No shell is used. cwd may specify the
        distribution root for a development Java command.
        """
        path = Path(workspace).resolve(strict=True)
        if not path.is_file() or path.suffix != ".mcreator":
            raise ValueError("workspace must be an existing .mcreator file")
        cls._check_timeout(startup_timeout)
        cls._check_timeout(request_timeout)
        if launcher is None:
            launcher = os.environ.get("COPPERBENCH_EXECUTABLE") or shutil.which("copperbench")
            if not launcher:
                raise NativeApiError("Specify launcher= with the Copperbench executable", "NATIVE_LAUNCHER_NOT_FOUND")
        argv = [os.fspath(launcher)] if isinstance(launcher, (str, Path)) else list(launcher)
        if not argv or any(not isinstance(item, str) or not item for item in argv):
            raise ValueError("launcher must be a nonempty executable or argv sequence")
        self = cls.__new__(cls)
        self.task_authorization_id = task_authorization_id
        self.metrics = metrics
        self.request_timeout = request_timeout
        self.revision: int | None = None
        self._closed = False
        self._lock = threading.RLock()
        self._close_lock = threading.Lock()
        self._socket = None
        self._writer = None
        self._responses: queue.Queue[Any] = queue.Queue()
        self._process = subprocess.Popen(
            [*argv, "headless", "--workspace", str(path), "api"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=None,
            text=True, encoding="utf-8", errors="strict", bufsize=1, cwd=cwd,
        )
        self._input, self._output = self._process.stdin, self._process.stdout
        # Keep the original LF/CRLF bytes for metrics, with the same strict UTF-8
        # decoding and universal line recognition as the subprocess text stream.
        self._output.reconfigure(newline="")
        self._reader = threading.Thread(target=self._read_output, daemon=True)
        self._reader.start()
        try:
            ready = self._receive(startup_timeout)
            if ready.get("nativeApiVersion") != "1" or ready.get("status") != "ready":
                raise NativeApiError("Core could not open the workspace", ready.get("code", "NATIVE_START_FAILED"), ready)
            self.workspace_id = ready["workspaceId"]
            self._task_authorized_operations = frozenset(ready.get("taskAuthorizedOperations", []))
            self.operations = tuple(ready.get('operations', []))
            self.get_workspace()
            return self
        except BaseException:
            self._close(graceful=False)
            raise

    @staticmethod
    def _check_timeout(value: float) -> None:
        if not math.isfinite(value) or value <= 0:
            raise ValueError("timeouts must be positive and finite")

    @classmethod
    def connect(cls, workspace: str | Path, *, request_timeout: float = 30,
                task_authorization_id: str | None = None, metrics: ClientMetrics | None = None) -> Workspace:
        """Attach to the desktop's live Core. Closing disconnects only this client.

        Discovery and credentials are private to the local OS user, separate
        from MCP. No workspace is reopened and no desktop approval is forged.
        """
        cls._check_timeout(request_timeout)
        path = Path(workspace).resolve(strict=True)
        if not path.is_file() or path.suffix != '.mcreator':
            raise ValueError('workspace must be an existing .mcreator file')
        key = path.as_posix().lower() if os.name == 'nt' else path.as_posix()
        digest = hashlib.sha256(key.encode('utf-8')).hexdigest()
        connection_file = Path.home() / '.copperbench/python-sessions' / (digest + '.json')
        try:
            connection = json.loads(connection_file.read_text(encoding='utf-8'))
            if not isinstance(connection, dict):
                raise ValueError('Invalid native connection metadata')
            port = connection['port']
            if (connection.get('nativeApiVersion') != '1' or type(port) is not int
                    or not 1 <= port <= 65535 or not isinstance(connection.get('token'), str)):
                raise ValueError('Invalid native connection metadata')
        except (OSError, ValueError, KeyError, TypeError) as error:
            raise NativeApiError('No native Python session found; open the workspace in Copperbench',
                                 'NATIVE_CONNECTION_UNAVAILABLE') from error
        self = cls.__new__(cls)
        self.task_authorization_id = task_authorization_id
        self.metrics = metrics
        self.request_timeout = request_timeout
        self.revision = None
        self._closed = False
        self._lock = threading.RLock()
        self._close_lock = threading.Lock()
        self._responses = queue.Queue()
        self._process = None
        self._socket = None
        self._reader = self._writer = None
        self._input = self._output = None
        try:
            self._socket = socket.create_connection(('127.0.0.1', port), timeout=request_timeout)
            self._socket.settimeout(None)
            self._input = self._socket.makefile('w', encoding='utf-8', newline='\n')
            self._output = self._socket.makefile('r', encoding='utf-8', newline='')
            self._reader = threading.Thread(target=self._read_output, daemon=True)
            self._reader.start()
            self._send(json.dumps({'token': connection['token']}, ensure_ascii=True))
            ready = self._receive(request_timeout)
            if (ready.get('nativeApiVersion') != '1' or ready.get('status') != 'ready'
                    or ready.get('workspaceId') != connection.get('workspaceId')):
                raise NativeApiError('Desktop session authentication failed', 'NATIVE_CONNECTION_REJECTED')
            self.workspace_id = ready['workspaceId']
            self._task_authorized_operations = frozenset(ready.get('taskAuthorizedOperations', []))
            self.operations = tuple(ready.get('operations', []))
            self.get_workspace()
            return self
        except BaseException as error:
            self._close(graceful=False)
            if isinstance(error, OSError):
                raise NativeApiError('Desktop session is no longer available', 'NATIVE_CONNECTION_UNAVAILABLE') from error
            raise

    def _send(self, line: str) -> None:
        # The caller waits on the response queue, so its deadline covers a
        # blocked write as well as Core execution. close() can interrupt both.
        def write() -> None:
            try:
                self._input.write(line + '\n')
                self._input.flush()
            except (OSError, ValueError):
                self._responses.put(NativeApiError('Core pipe disconnected', 'NATIVE_PROCESS_EXITED'))
            finally:
                if self._closed:
                    try:
                        self._input.close()
                    except (OSError, ValueError):
                        pass
        with self._close_lock:
            if self._closed:
                raise NativeApiError('Workspace is closed', 'NATIVE_SESSION_CLOSED')
            self._writer = threading.Thread(target=write, daemon=True)
            self._writer.start()

    def _read_output(self) -> None:
        try:
            while True:
                line = self._output.readline(_MAX_RESPONSE_CHARS + 2)
                if not line:
                    break
                # CRLF previously occupied one character after newline
                # translation. Preserve that limit while counting both bytes.
                characters = len(line) - int(line.endswith('\r\n'))
                if characters > _MAX_RESPONSE_CHARS:
                    raise ValueError("Native response exceeds 32 Mi characters")
                value = json.loads(line)
                if not isinstance(value, dict):
                    raise ValueError("Native response must be an object")
                self._responses.put((value, len(line.encode("utf-8"))))
        except Exception as error:
            self._responses.put(NativeApiError(str(error), "NATIVE_INVALID_RESPONSE"))
        finally:
            self._responses.put(NativeApiError("Core process closed its output", "NATIVE_PROCESS_EXITED"))
            self._output.close()

    def _receive(self, timeout: float) -> dict[str, Any]:
        self._last_response_bytes = 0
        try:
            value = self._responses.get(timeout=timeout)
        except queue.Empty as error:
            raise NativeApiError("Native API timed out; the request is not retried", "NATIVE_TIMEOUT") from error
        if isinstance(value, Exception):
            raise value
        response, self._last_response_bytes = value
        return response

    def _call(self, kind: str, operation: str, payload: dict[str, Any],
              expected_revision: int | None = None, *, schema_version: str | None = None) -> dict[str, Any]:
        metrics = getattr(self, "metrics", None)
        if metrics is None:
            return self._call_once(kind, operation, payload, expected_revision, schema_version=schema_version)
        started = time.monotonic()
        if metrics is not None:
            metrics._add(operation, "logicalCalls")
        try:
            return self._call_once(kind, operation, payload, expected_revision, schema_version=schema_version)
        finally:
            if metrics is not None:
                metrics._add(operation, "elapsedSeconds", time.monotonic() - started)

    def _call_once(self, kind: str, operation: str, payload: dict[str, Any],
                   expected_revision: int | None = None, *, schema_version: str | None = None) -> dict[str, Any]:
        with self._lock:
            if self._closed:
                raise NativeApiError("Workspace is closed", "NATIVE_SESSION_CLOSED")
            request_id = str(uuid.uuid4())
            request = {"id": request_id, "kind": kind, "operation": operation, "payload": payload}
            if schema_version is not None:
                request["schemaVersion"] = schema_version
            if kind == "command":
                revision = self.revision if expected_revision is None else expected_revision
                if type(revision) is not int or revision < 0:
                    raise ValueError("expected_revision must be a nonnegative integer")
                request["expectedRevision"] = revision
            line = json.dumps(request, ensure_ascii=True, allow_nan=False)
            if len(line) > 4 * 1024 * 1024:
                raise ValueError("Native request exceeds 4 Mi characters")
            try:
                if getattr(self, "metrics", None) is not None:
                    self.metrics._add(operation, "attempts")
                self._send(line)
                response = self._receive(self.request_timeout)
                if getattr(self, "metrics", None) is not None:
                    self.metrics._add(operation, "responseBytes", self._last_response_bytes)
                if response.get("id") != request_id:
                    raise NativeApiError("Native response ID mismatch", "NATIVE_INVALID_RESPONSE", response)
            except (NativeApiError, OSError) as error:
                self._close(graceful=False)
                if isinstance(error, NativeApiError):
                    raise
                raise NativeApiError("Core pipe disconnected; request was not retried", "NATIVE_PROCESS_EXITED") from error
            if "error" in response:
                error = response["error"]
                if (not isinstance(error, dict) or not isinstance(error.get("message"), (str, dict))
                        or not isinstance(error.get("code"), str) or not error["code"]):
                    self._close(graceful=False)
                    raise NativeApiError("Malformed native error", "NATIVE_INVALID_RESPONSE", response)
                raise NativeApiError(_render_message(error["message"], error["code"]), error["code"], response)
            result = response.get("result")
            if not isinstance(result, dict):
                self._close(graceful=False)
                raise NativeApiError("Missing Core result", "NATIVE_INVALID_RESPONSE", response)
            allowed_statuses = {"compatible", "incompatible"} if kind == "handshake" else {
                "succeeded", "committed", "accepted", "completed", "cancelled"}
            if not isinstance(result.get("status"), str) or result["status"] not in allowed_statuses:
                if not isinstance(result.get("status"), str) or result["status"] not in {"rejected", "failed"}:
                    self._close(graceful=False)
                    raise NativeApiError("Unrecognized Core result status", "NATIVE_INVALID_RESPONSE", result)
                diagnostics = result.get("diagnostics") if isinstance(result.get("diagnostics"), list) else []
                diagnostic = next((item for item in diagnostics if isinstance(item, dict)
                                   and isinstance(item.get("code"), str) and item["code"]), {})
                message = diagnostic.get("message", {})
                code = result.get("code")
                if not isinstance(code, str) or not code:
                    code = diagnostic.get("code", "CORE_OPERATION_REJECTED")
                if result.get("conflict"):
                    code = "REVISION_CONFLICT"
                raise NativeApiError(_render_message(message, code), code, result)
            revision = result.get("newRevision", result.get("revision"))
            if type(revision) is int:
                self.revision = revision
            return result

    def query(self, operation: str, *, schema_version: str | None = None, **payload: Any) -> dict[str, Any]:
        """Query Core. Editors explicitly request 1.1; pass schema_version='1.0' for the frozen projection."""
        version = schema_version or ("1.1" if operation == "get_mod_element_editor" else "1.0")
        if version not in ("1.0", "1.1") or (version == "1.1" and operation != "get_mod_element_editor"):
            raise ValueError("Schema 1.1 is available only for get_mod_element_editor")
        result = self._call("query", operation, payload, schema_version=version)
        if operation == "get_mod_element_editor" and result.get("schemaVersion") != version:
            raise NativeApiError("Core did not return the requested query schema", "NATIVE_INVALID_RESPONSE", result)
        return result

    def negotiate_schema(self, supported_versions: Sequence[str], *, client_name: str = "python",
                         client_version: str = "1") -> dict[str, Any]:
        """Negotiate the UI-Core wire schema with the running Core, without mutation."""
        versions = list(supported_versions)
        if (not versions or any(not isinstance(value, str) or re.fullmatch(r"[0-9]+\.[0-9]+", value) is None
                                for value in versions) or len(set(versions)) != len(versions)):
            raise ValueError("supported_versions must contain major.minor schema versions")
        result = self._call("handshake", "handshake", {
            "supportedSchemaVersions": versions,
            "client": {"name": client_name, "version": client_version}})
        selected, offered = result.get("selectedSchemaVersion"), result.get("coreSchemaVersions")
        if (not isinstance(offered, list) or not offered
                or any(not isinstance(value, str) or re.fullmatch(r"[0-9]+\.[0-9]+", value) is None for value in offered)
                or not isinstance(result.get("diagnostics"), list)
                or (result["status"] == "compatible" and (selected not in versions or selected not in offered))
                or (result["status"] == "incompatible" and selected is not None)):
            raise NativeApiError("Malformed schema negotiation result", "NATIVE_INVALID_RESPONSE", result)
        return result

    def require_schema(self, supported_versions: Sequence[str] = ("1.0",)) -> dict[str, Any]:
        """Stop explicitly on incompatibility; never downgrade required semantics."""
        result = self.negotiate_schema(supported_versions)
        if result["status"] != "compatible":
            raise NativeApiError("The host has no compatible Core schema", "UI_CORE_SCHEMA_INCOMPATIBLE", result)
        return result

    def command(self, operation: str, *, expected_revision: int | None = None, **payload: Any) -> dict[str, Any]:
        """Execute once at the last observed revision (or the explicit revision).

        Payload names follow UI-Core. Conflicts are reported, never silently
        retried. User approval cannot be issued or forged through this API.
        """
        if self.task_authorization_id and operation in self._task_authorized_operations:
            payload.setdefault("taskAuthorizationId", self.task_authorization_id)
        return self._call("command", operation, payload, expected_revision)

    def get_workspace(self) -> dict[str, Any]:
        return self.query("get_workbench")

    def preview_generation(self) -> dict[str, Any]:
        """Read source conflicts and input identity without mutation or downloads.

        Inspect data.status (ready/conflicted/unknown); ready is not build or
        gameplay acceptance. Generation always rechecks the current inputs.
        """
        return self.query("preview_generation")

    def doctor(self) -> dict[str, Any]:
        """Inspect this session's backend without writes, downloads or implicit authorization.

        For checks before opening a writer session, use the product command
        ``headless --workspace <path.mcreator> doctor``.
        """
        return self.query("get_workspace_doctor")

    def _field_contract_environment(self, environment: dict[str, Any] | None = None) -> tuple[dict[str, Any], dict[str, Any]]:
        result = self.get_workspace_environment() if environment is None else environment
        data = result.get("data") if isinstance(result, dict) else None
        contracts = data.get("fieldContracts") if isinstance(data, dict) else None
        if (not isinstance(contracts, dict)
                or ("generator" in data and not isinstance(data["generator"], dict))
                or any(not isinstance(name, str) or not name.strip() or not isinstance(contract, dict)
                       for name, contract in contracts.items())):
            raise NativeApiError("Core returned invalid field contract metadata",
                                 "NATIVE_INVALID_RESPONSE", result)
        return result, contracts

    def available_field_contracts(self) -> tuple[str, ...]:
        """List contract names published by this Core, not all creatable types.

        This is one read-only query. Generic input contracts may be listed;
        they do not substitute for a missing type-specific creation contract.
        """
        _, contracts = self._field_contract_environment()
        return tuple(sorted(contracts))

    def field_contract(self, element_type: str = "block", *,
                       environment: dict[str, Any] | None = None) -> dict[str, Any]:
        """Read a published contract or raise NATIVE_FIELD_CONTRACT_UNAVAILABLE.

        Missing metadata is not proof that an element cannot be created. This
        method never creates a probe element, guesses fields or retries a write.
        Inspect an existing element with get_mod_element_editor when needed.
        """
        if not isinstance(element_type, str) or not element_type.strip():
            raise ValueError("element_type must be a nonempty string")
        result, contracts = self._field_contract_environment(environment)
        data = result["data"]
        if element_type not in contracts:
            available = sorted(contracts)
            next_action = ("Inspect get_workspace_environment.data.fieldContracts and choose an advertised type; "
                           "update Copperbench if this Core does not advertise the contract you need. "
                           "For an existing element, query get_mod_element_editor with its elementId.")
            raise NativeApiError(
                f"Core does not publish a field contract for {element_type!r}. "
                f"Available contracts: {', '.join(available) or '(none)'}. "
                "For an existing element, query get_mod_element_editor with its elementId. "
                "No probe element was created and no fields were guessed.",
                "NATIVE_FIELD_CONTRACT_UNAVAILABLE", {
                    "elementType": element_type,
                    "availableTypes": available,
                    "reason": "type_not_advertised",
                    "nextAction": next_action,
                    "environment": result,
                    "generator": data.get("generator"),
                    "inspection": {"operation": "get_mod_element_editor",
                                   "requires": ["elementId"], "scope": "existing_element"},
                })
        return contracts[element_type]

    def discover_field_contract(self, element_type: str) -> dict[str, Any]:
        """Read versioned creation metadata with available/not_exposed/unsupported states.

        Hosts advertising this operation use one query and no contract cache.
        Older Cores retain their original field_contract() format. Missing new
        discovery support is explicit; it never creates a probe or guesses fields.
        """
        if not isinstance(element_type, str) or not element_type.strip():
            raise ValueError("element_type must be a nonempty string")
        operation = "get_mod_element_field_contract"
        # Native readiness already advertises operation names. On capable hosts,
        # query only the requested type; do not fetch every contract first.
        # This catalog is session-local, not a cache of fields or mutable state.
        if operation not in getattr(self, "operations", ()):
            result, contracts = self._field_contract_environment()
            environment = result["data"]
            operation = environment.get("fieldContractDiscovery")
            if operation is None:
                return {"elementType": element_type,
                        "generatorId": environment.get("generator", {}).get("id", ""),
                        "contractVersion": "1", "availability": "not_exposed",
                        "complete": False, "fields": [],
                        "reasonCode": "FIELD_CONTRACT_DISCOVERY_UNAVAILABLE",
                        "availableContracts": sorted(contracts),
                        "alternatives": [{"operation": "get_mod_element_editor",
                                          "requires": ["elementId"]}]}
            if operation != "get_mod_element_field_contract":
                raise NativeApiError("Core returned invalid discovery metadata",
                                     "NATIVE_INVALID_RESPONSE", result)
        result = self.query(operation, elementType=element_type)
        data = result.get("data")
        if (not isinstance(data, dict) or data.get("elementType") != element_type
                or not isinstance(data.get("generatorId"), str)
                or data.get("contractVersion") != "1"
                or data.get("availability") not in ("available", "not_exposed", "unsupported")
                or not isinstance(data.get("complete"), bool)
                or data["complete"] != (data["availability"] == "available")
                or not isinstance(data.get("fields"), list)
                or not isinstance(data.get("alternatives"), list)
                or (data["complete"] and (not data["fields"]
                    or not isinstance(data.get("minimalExample"), dict)))
                or (not data["complete"] and data["fields"])
                or any(not isinstance(field, dict)
                       or not isinstance(field.get("path"), str)
                       or not field["path"].startswith("/")
                       or not isinstance(field.get("inputSchema"), dict)
                       for field in data["fields"])):
            raise NativeApiError("Core returned invalid field contract metadata",
                                 "NATIVE_INVALID_RESPONSE", result)
        return data

    def field_reference_options(self, element_type: str, mapping_source: str, *,
                                search: str = "", offset: int = 0, limit: int = 100) -> dict[str, Any]:
        """Page the installed generator's vanilla reference values."""
        return self.query("get_field_reference_options", elementType=element_type,
                          mappingSource=mapping_source, search=search, offset=offset, limit=limit)
    def get_workspace_environment(self) -> dict[str, Any]:
        return self.query("get_workspace_environment")

    def plan_workspace_changes(self, operations: list[dict[str, Any]], *, idempotency_key: str,
                               expected_revision: int | None = None,
                               require_recovery_point: bool = False) -> dict[str, Any]:
        """Plan queries carry expectedRevision in their payload, unlike ordinary queries."""
        return self.query("plan_workspace_changes", operations=operations, idempotencyKey=idempotency_key,
                          expectedRevision=self.revision if expected_revision is None else expected_revision,
                          requireRecoveryPoint=require_recovery_point)

    def preview_workspace_plan(self, plan: dict[str, Any]) -> dict[str, Any]:
        return self.query("preview_workspace_plan", plan=plan)

    def apply_workspace_plan(self, plan: dict[str, Any], *, expected_revision: int | None = None) -> dict[str, Any]:
        """Apply once; conflicts never trigger automatic replay."""
        return self.command("apply_workspace_plan", plan=plan,
                            expected_revision=plan["baseRevision"] if expected_revision is None else expected_revision)

    def export_verified_artifact(self, task_id: str, *, allow_historical: bool = False) -> dict[str, Any]:
        """Start a hash-verified export of the selected acceptance artifact; wait for its terminal task."""
        return self.command("export_workspace", verifiedTaskId=task_id, allowHistorical=allow_historical)

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
        result = self._call("version_copy_upgrade", operation, arguments)
        if result.get("contract") != "version_copy_upgrade" or result.get("selectedVersion") not in supported_versions:
            raise NativeApiError("Version copy upgrade contract was not negotiated", "INVALID_RESPONSE", result)
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
            revision = self.revision if expected_revision is None else expected_revision
            if type(revision) is not int or revision < 0:
                raise ValueError("expected_revision must be a nonnegative integer")
            arguments["expectedRevision"] = revision
        if operation == "command" and self.task_authorization_id and command in self._task_authorized_operations:
            arguments.setdefault("payload", {}).setdefault("taskAuthorizationId", self.task_authorization_id)
        result = self._call("preview", operation, arguments)
        if result.get("contract") != "preview_targets" or result.get("selectedVersion") not in supported_versions:
            raise NativeApiError("Preview contract was not negotiated", "INVALID_RESPONSE", result)
        return result

    def acceptance(self, operation: str, *, expected_revision: int | None = None,
                   supported_versions: Sequence[str] = ("1",), diagnostic_versions: Sequence[str] | None = None,
                   **payload: Any) -> dict[str, Any]:
        """Use the required_cases contract with explicit version negotiation on every request.

        run/export keep the existing permission, revision and authorization rules.
        A legacy task is returned as legacy_scope and cannot be exported by this contract.
        """
        request: dict[str, Any] = {"supportedVersions": list(supported_versions), "payload": payload}
        if diagnostic_versions is not None:
            request["diagnosticVersions"] = list(diagnostic_versions)
        if operation in {"run", "export"}:
            revision = self.revision if expected_revision is None else expected_revision
            if type(revision) is not int or revision < 0:
                raise ValueError("expected_revision must be a nonnegative integer")
            request["expectedRevision"] = revision
            if operation == "run" and self.task_authorization_id:
                payload.setdefault("taskAuthorizationId", self.task_authorization_id)
        result = self._call("acceptance", operation, request)
        if result.get("contract") != "required_cases" or result.get("selectedVersion") not in supported_versions:
            raise NativeApiError("Required-case contract was not negotiated", "NATIVE_INVALID_RESPONSE", result)
        diagnostics = result.get("coverageDiagnostics")
        if diagnostic_versions is not None and (not isinstance(diagnostics, dict) or diagnostics.get("schemaVersion") not in diagnostic_versions):
            raise NativeApiError("Acceptance diagnostic contract was not negotiated", "NATIVE_INVALID_RESPONSE", result)
        return result

    def get_acceptance_diagnostics(self, task_id: str) -> dict[str, Any]:
        """Read recorded acceptance reasons and parameters; no mutation or new task is started."""
        return self.acceptance("get", diagnostic_versions=("1",), taskId=task_id)["coverageDiagnostics"]

    def required_case_manifests(self) -> list[dict[str, Any]]:
        return self.acceptance("catalog")["manifests"]

    def run_required_cases(self, manifest: dict[str, Any], *, expected_revision: int | None = None) -> dict[str, Any]:
        return self.acceptance("run", expected_revision=expected_revision, requiredCases=manifest)

    def get_required_case_evidence(self, task_id: str) -> dict[str, Any]:
        return self.acceptance("get", taskId=task_id)

    def export_required_case_artifact(self, task_id: str, *, allow_historical: bool = False) -> dict[str, Any]:
        return self.acceptance("export", verifiedTaskId=task_id, allowHistorical=allow_historical)

    def get_context(self) -> dict[str, Any]:
        return self._call('context', 'get', {})['data']

    def select_element(self, element_id: str | None) -> None:
        self._call('context', 'select', {'elementId': element_id})

    @property
    def elements(self) -> ElementCollection:
        """Named element handles; reads and writes use the same live Core."""
        return ElementCollection(self)

    def list_mod_elements(self, **filters: Any) -> Iterator[dict[str, Any]]:
        cursor = filters.get("cursor")
        seen = {cursor} if isinstance(cursor, str) else set()
        while True:
            result = self.query("list_mod_elements", **{**filters, "limit": filters.get("limit", 200),
                                                        **({"cursor": cursor} if cursor else {})})
            items, cursor = _cursor_page(result, seen, NativeApiError, "NATIVE_INVALID_RESPONSE")
            yield from items
            if cursor is None:
                return

    def create_mod_element(self, **payload: Any) -> dict[str, Any]:
        return self.command("create_mod_element", **payload)

    def update_mod_element(self, **payload: Any) -> dict[str, Any]:
        return self.command("update_mod_element", **payload)

    def update_procedure(self, **payload: Any) -> dict[str, Any]:
        return self.command("update_procedure", **payload)

    def build(self, *, expected_revision: int | None = None) -> dict[str, Any]:
        return self.command("build_workspace", expected_revision=expected_revision, scope="workspace")

    def validate(self, *, expected_revision: int | None = None) -> dict[str, Any]:
        return self.command("validate_workspace", expected_revision=expected_revision, scope="workspace")

    def generate(self, *, expected_revision: int | None = None) -> dict[str, Any]:
        return self.command("generate_workspace", expected_revision=expected_revision, scope="workspace")

    def run_client(self, *, expected_revision: int | None = None) -> dict[str, Any]:
        return self.command("run_client", expected_revision=expected_revision, scope="workspace")

    def run_server(self, *, expected_revision: int | None = None) -> dict[str, Any]:
        return self.command("run_server", expected_revision=expected_revision, scope="workspace")

    def prepare_game_tests(self, *, expected_revision: int | None = None) -> dict[str, Any]:
        return self.command("prepare_game_tests", expected_revision=expected_revision)

    def run_game_tests(self, *, expected_revision: int | None = None) -> dict[str, Any]:
        return self.command("run_gametest", expected_revision=expected_revision, scope="workspace")

    def get_task(self, task_id: str, after_log_sequence: int = 0) -> dict[str, Any]:
        return self.query("get_task", taskId=task_id, afterLogSequence=after_log_sequence)

    def cancel_task(self, task_id: str) -> dict[str, Any]:
        return self.command("cancel_task", taskId=task_id)

    def wait_task(self, task_id: str, *, timeout: float = 2700, poll_interval: float = 0.2,
                  after_log_sequence: int = 0,
                  on_update: Callable[[dict[str, Any]], None] | None = None) -> dict[str, Any]:
        """Resume an existing task with incremental logs; timeout only ends waiting.

        Persist on_update responses before disconnecting. An independent host
        must remain alive until its task ends; connect() can reconnect to a
        still-running desktop host. Transport errors propagate without replay.
        """
        return _poll_task(self.get_task, task_id, timeout=timeout, poll_interval=poll_interval,
                          after_log_sequence=after_log_sequence, on_update=on_update,
                          error_type=NativeApiError, invalid_code="NATIVE_INVALID_RESPONSE",
                          timeout_code="NATIVE_TASK_TIMEOUT")

    def close(self) -> None:
        """Disconnect, allowing an idle owned host up to 30s to finish saving."""
        self._close(graceful=True)

    def _close(self, *, graceful: bool) -> None:
        # Never wait for the request lock: another thread may be waiting for
        # Core or filling a pipe. Shutdown must wake it, not depend on it.
        with self._close_lock:
            if self._closed:
                return
            idle = graceful and self._lock.acquire(blocking=False)
            try:
                self._closed = True
                self._responses.put(NativeApiError('Workspace is closed', 'NATIVE_SESSION_CLOSED'))
            finally:
                if idle:
                    self._lock.release()
            if self._socket is not None:
                try:
                    self._socket.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                # makefile() keeps socket I/O references. Its buffered streams
                # can still be held by a pending read/write, so socket.close()
                # alone may defer the OS close and leave that operation alive.
                descriptor = self._socket.detach()
                if descriptor != -1:
                    socket.close(descriptor)
            elif self._process is not None:
                deadline = time.monotonic() + (30 if idle else 2)
                if idle and self._writer is not None:
                    # An acknowledged request can leave its writer descheduled
                    # after flush. Liveness alone does not establish blocked I/O.
                    # Wait within the graceful budget before touching its stream.
                    self._writer.join(timeout=max(0, deadline - time.monotonic()))
                # In-flight/faulted writes retain the short abort path; after
                # the graceful budget, a writer still holding the stream must
                # also be interrupted before closing the buffered input.
                if self._writer is not None and self._writer.is_alive():
                    self._process.terminate()
                else:
                    try:
                        self._input.close()
                    except (OSError, ValueError):
                        pass
                try:
                    # The writer and normal EOF exit share the graceful budget.
                    self._process.wait(timeout=max(0, deadline - time.monotonic()))
                except subprocess.TimeoutExpired:
                    self._process.kill()
                    self._process.wait(timeout=2)
            # A descendant can inherit a pipe. Never close a buffered stream
            # from here while its worker holds the lock indefinitely.
            for thread, stream in ((self._writer, self._input), (self._reader, self._output)):
                if thread is not None:
                    thread.join(timeout=0.2)
                if stream is not None and (thread is None or not thread.is_alive()):
                    try:
                        stream.close()
                    except (OSError, ValueError):
                        pass

    def __enter__(self) -> Workspace:
        return self

    def __exit__(self, *exc: Any) -> None:
        self.close()


class ElementCollection:
    """Access elements by exact name or ID, without maintaining a second data model."""

    def __init__(self, workspace: Workspace):
        self._workspace = workspace

    def __iter__(self) -> Iterator[ModElement]:
        for item in self._workspace.list_mod_elements():
            yield ModElement(self._workspace, item, self._workspace.revision)

    def __getitem__(self, name_or_id: str) -> ModElement:
        for element in self:
            if name_or_id in (element.name, element.id):
                return element
        raise KeyError(name_or_id)

    def new(self, element_type: str, name: str, **values: Any) -> ModElement:
        result = self._workspace.create_mod_element(elementType=element_type, name=name, initialValues=values)
        return ModElement(self._workspace, result['data']['element'], result['newRevision'])

    def get(self, name_or_id: str, default=None):
        try:
            return self[name_or_id]
        except KeyError:
            return default

    def remove(self, element: ModElement) -> dict[str, Any]:
        if element._workspace.workspace_id != self._workspace.workspace_id:
            raise ValueError('The element belongs to another workspace')
        return self._workspace.command('delete_mod_element', elementId=element.id,
                                       expected_revision=element._revision)


class ModElement:
    """A stable ID and observed revision, not a detached copy of Java objects.

    Field paths are Core JSON pointers. Assignments commit immediately;
    update() commits multiple top-level fields together. Conflicts require an
    explicit refresh() and a user/script decision, never a hidden retry.
    """

    def __init__(self, workspace: Workspace, summary: dict[str, Any], revision: int):
        self._workspace = workspace
        self._summary = dict(summary)
        self._revision = revision

    @property
    def id(self) -> str:
        return self._summary['id']

    @property
    def name(self) -> str:
        return self._summary['name']

    def __repr__(self) -> str:
        return f"<ModElement {self.name!r} id={self.id}>"

    def __dir__(self) -> list[str]:
        # Introspection must not silently refresh the revision used for writes.
        projection = self._workspace.query('get_mod_element_editor', elementId=self.id)['data']
        names = {field['path'][1:] for section in projection['sections'] for field in section.get('fields', [])
                 if field['path'].startswith('/') and field['path'][1:].isidentifier()}
        return sorted(set(super().__dir__()) | names)

    def __getattr__(self, name: str) -> Any:
        if name.startswith('_'):
            raise AttributeError(name)
        try:
            return self['/' + name]
        except KeyError as error:
            raise AttributeError(name) from error

    def __setattr__(self, name: str, value: Any) -> None:
        if name.startswith('_') or isinstance(getattr(type(self), name, None), property):
            object.__setattr__(self, name, value)
            return
        projection = self._workspace.query('get_mod_element_editor', elementId=self.id)['data']
        field = next((field for section in projection['sections'] for field in section.get('fields', [])
                      if field['path'] == '/' + name), None)
        if field is None or field.get('readOnly'):
            raise AttributeError(f'Unknown or read-only element field: {name}')
        self._apply([{'path': field['path'], 'value': value}])

    @property
    def display_name(self) -> str:
        return self['/displayName']

    @display_name.setter
    def display_name(self, value: str) -> None:
        self['/displayName'] = value

    @property
    def fields(self) -> dict[str, Any]:
        return {field['path']: field.get('value') for section in self.refresh()['sections']
                for field in section.get('fields', [])}

    def refresh(self) -> dict[str, Any]:
        result = self._workspace.query('get_mod_element_editor', elementId=self.id)
        self._summary = dict(result['data']['element'])
        self._revision = result['revision']
        return result['data']

    def __getitem__(self, path: str) -> Any:
        for section in self.refresh()['sections']:
            for field in section.get('fields', []):
                if field['path'] == path:
                    return field.get('value')
        raise KeyError(path)

    def __setitem__(self, path: str, value: Any) -> None:
        self._apply([{'path': path, 'value': value}])

    def update(self, **values: Any) -> dict[str, Any]:
        return self._apply([{'path': '/' + key.replace('~', '~0').replace('/', '~1'), 'value': value}
                            for key, value in values.items()])

    def _apply(self, changes: list[dict[str, Any]]) -> dict[str, Any]:
        result = self._workspace.update_mod_element(elementId=self.id, changes=changes,
                                                    expected_revision=self._revision)
        self._summary = dict(result['data']['element'])
        self._revision = result['newRevision']
        return result
