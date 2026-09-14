"""Copperbench Core scripting: independent process or live desktop, without MCP."""

from __future__ import annotations

import json
import hashlib
import math
import os
from pathlib import Path
import queue
import shutil
import socket
import subprocess
import threading
import time
from typing import Any, Iterator, Sequence
import uuid


class NativeApiError(RuntimeError):
    def __init__(self, message: str, code: str, details: Any = None):
        super().__init__(message)
        self.code = code
        self.details = details


class Workspace:
    """A Core session opened independently or attached to the live desktop.

    Use as a context manager. Closing an attached session only disconnects;
    closing an independent session stops its host. Calls are serialized and
    mutations are never retried.
    """

    @classmethod
    def open(cls, workspace: str | Path, *, launcher: str | Path | Sequence[str] | None = None,
             task_authorization_id: str | None = None, startup_timeout: float = 120,
             request_timeout: float = 30, cwd: str | Path | None = None) -> Workspace:
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
            self.close()
            raise

    @staticmethod
    def _check_timeout(value: float) -> None:
        if not math.isfinite(value) or value <= 0:
            raise ValueError("timeouts must be positive and finite")

    @classmethod
    def connect(cls, workspace: str | Path, *, request_timeout: float = 30,
                task_authorization_id: str | None = None) -> Workspace:
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
            self._output = self._socket.makefile('r', encoding='utf-8')
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
            self.close()
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
                line = self._output.readline(32 * 1024 * 1024 + 1)
                if not line:
                    break
                if len(line) > 32 * 1024 * 1024:
                    raise ValueError("Native response exceeds 32 Mi characters")
                value = json.loads(line)
                if not isinstance(value, dict):
                    raise ValueError("Native response must be an object")
                self._responses.put(value)
        except Exception as error:
            self._responses.put(NativeApiError(str(error), "NATIVE_INVALID_RESPONSE"))
        finally:
            self._responses.put(NativeApiError("Core process closed its output", "NATIVE_PROCESS_EXITED"))
            self._output.close()

    def _receive(self, timeout: float) -> dict[str, Any]:
        try:
            value = self._responses.get(timeout=timeout)
        except queue.Empty as error:
            raise NativeApiError("Native API timed out; the request is not retried", "NATIVE_TIMEOUT") from error
        if isinstance(value, Exception):
            raise value
        return value

    def _call(self, kind: str, operation: str, payload: dict[str, Any],
              expected_revision: int | None = None) -> dict[str, Any]:
        with self._lock:
            if self._closed:
                raise NativeApiError("Workspace is closed", "NATIVE_SESSION_CLOSED")
            request_id = str(uuid.uuid4())
            request = {"id": request_id, "kind": kind, "operation": operation, "payload": payload}
            if kind == "command":
                revision = self.revision if expected_revision is None else expected_revision
                if type(revision) is not int or revision < 0:
                    raise ValueError("expected_revision must be a nonnegative integer")
                request["expectedRevision"] = revision
            line = json.dumps(request, ensure_ascii=True, allow_nan=False)
            if len(line) > 4 * 1024 * 1024:
                raise ValueError("Native request exceeds 4 Mi characters")
            try:
                self._send(line)
                response = self._receive(self.request_timeout)
                if response.get("id") != request_id:
                    raise NativeApiError("Native response ID mismatch", "NATIVE_INVALID_RESPONSE", response)
            except (NativeApiError, OSError) as error:
                self.close()
                if isinstance(error, NativeApiError):
                    raise
                raise NativeApiError("Core pipe disconnected; request was not retried", "NATIVE_PROCESS_EXITED") from error
            if "error" in response:
                error = response["error"]
                raise NativeApiError(error["message"], error["code"], response)
            result = response.get("result")
            if not isinstance(result, dict):
                self.close()
                raise NativeApiError("Missing Core result", "NATIVE_INVALID_RESPONSE", response)
            if result.get("status") not in {"succeeded", "committed", "accepted", "completed", "cancelled"}:
                diagnostics = result.get("diagnostics") or []
                diagnostic = diagnostics[0] if diagnostics else {}
                message = diagnostic.get("message", {})
                code = diagnostic.get("code", "CORE_OPERATION_REJECTED")
                if result.get("conflict"):
                    code = "REVISION_CONFLICT"
                raise NativeApiError(message.get("fallback", code) if isinstance(message, dict) else str(message), code, result)
            revision = result.get("newRevision", result.get("revision"))
            if type(revision) is int:
                self.revision = revision
            return result

    def query(self, operation: str, **payload: Any) -> dict[str, Any]:
        """Query Core using its operation name and payload fields; returns the Core envelope."""
        return self._call("query", operation, payload)

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

    def get_context(self) -> dict[str, Any]:
        return self._call('context', 'get', {})['data']

    def select_element(self, element_id: str | None) -> None:
        self._call('context', 'select', {'elementId': element_id})

    @property
    def elements(self) -> ElementCollection:
        """Named element handles; reads and writes use the same live Core."""
        return ElementCollection(self)

    def list_mod_elements(self, **filters: Any) -> Iterator[dict[str, Any]]:
        cursor = None
        seen = set()
        while True:
            result = self.query("list_mod_elements", **{**filters, "limit": filters.get("limit", 200),
                                                        **({"cursor": cursor} if cursor else {})})
            data = result["data"]
            yield from data.get("items", [])
            cursor = data.get("nextCursor")
            if not cursor:
                return
            if cursor in seen:
                raise NativeApiError("Repeated pagination cursor", "NATIVE_INVALID_RESPONSE", result)
            seen.add(cursor)

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

    def wait_task(self, task_id: str, *, timeout: float = 2700, poll_interval: float = 0.2) -> dict[str, Any]:
        """Wait for a terminal task result. Timeout leaves the task running in this session."""
        if timeout <= 0 or poll_interval <= 0:
            raise ValueError("timeout and poll_interval must be positive")
        deadline = time.monotonic() + timeout
        after_sequence = 0
        logs = []
        while True:
            result = self.get_task(task_id, after_sequence)
            data = result["data"]
            for entry in data.get("logs", []):
                after_sequence = max(after_sequence, entry["sequence"])
                logs.append(entry)
            if data["task"]["state"] not in {"queued", "running"}:
                data['logs'] = logs
                return result
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise NativeApiError("Task is still running; poll or cancel it explicitly", "NATIVE_TASK_TIMEOUT", result)
            time.sleep(min(poll_interval, remaining))

    def close(self) -> None:
        # Never wait for the request lock: another thread may be waiting for
        # Core or filling a pipe. Shutdown must wake it, not depend on it.
        with self._close_lock:
            if self._closed:
                return
            self._closed = True
            self._responses.put(NativeApiError('Workspace is closed', 'NATIVE_SESSION_CLOSED'))
            if self._socket is not None:
                try:
                    self._socket.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                self._socket.close()
            elif self._process is not None:
                # A blocked writer owns the buffered stream lock. Terminate
                # before touching that stream; otherwise close() also hangs.
                if self._writer is not None and self._writer.is_alive():
                    self._process.terminate()
                else:
                    try:
                        self._input.close()
                    except (OSError, ValueError):
                        pass
                try:
                    self._process.wait(timeout=2)
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
