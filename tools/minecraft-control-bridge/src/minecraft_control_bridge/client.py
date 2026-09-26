"""Private pipe transport to the input owner; never inherit the MCP stdin handle."""
import json
import os
import queue
import subprocess
import sys
import threading
from pathlib import Path

from .errors import BridgeError

OPERATIONS = {"doctor", "list_windows", "attach", "observe", "step", "menu_flow", "read_logs", "record_verification", "stop", "detach"}


class Bridge:
    """One controller and one input-owning subprocess, with a separate stop channel.

    Context-manager exit closes the private pipe. EOF/crash requests input release.
    Python calls and MCP both use the same worker implementation.
    """

    def __init__(self, *, _backend_factory=None):
        self._mutex = threading.Lock()
        self._write_lock = threading.Lock()
        self._responses = queue.Queue()
        self._closed = False
        self._session = None
        args = [sys.executable, "-m", "minecraft_control_bridge.worker"]
        if _backend_factory:
            args.extend(["--backend-factory", f"{_backend_factory.__module__}:{_backend_factory.__qualname__}"])
        self._process = subprocess.Popen(args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=None,
                                         text=True, encoding="utf-8", bufsize=1,
                                         creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        self._reader = threading.Thread(target=self._read, daemon=True)
        self._reader.start()
        try:
            self._receive(30)
        except BaseException:
            self.close()
            raise

    def _read(self):
        try:
            for line in self._process.stdout:
                self._responses.put(json.loads(line))
        except (ValueError, OSError) as error:
            self._responses.put({"ok": False, "error": {"code": "WORKER_PROTOCOL_ERROR", "message": str(error)}})
        finally:
            self._responses.put(None)

    def _send(self, operation, arguments):
        try:
            with self._write_lock:
                self._process.stdin.write(json.dumps({"operation": operation, "arguments": arguments}) + "\n")
                self._process.stdin.flush()
        except (OSError, ValueError) as exc:
            raise BridgeError("WORKER_EXITED", "Input worker is unavailable; recreate Bridge") from exc

    def _receive(self, timeout):
        try:
            message = self._responses.get(timeout=timeout)
        except queue.Empty as exc:
            self.close()
            raise BridgeError("WORKER_TIMEOUT", "Worker timed out; connection closed and input stop requested") from exc
        if message is None:
            raise BridgeError("WORKER_EXITED", "Input worker exited; recreate Bridge")
        if not message["ok"]:
            error = message["error"]
            raise BridgeError(error["code"], error["message"], error.get("details"))
        return message.get("result")

    def call(self, operation, **arguments):
        if operation not in OPERATIONS:
            raise BridgeError("UNKNOWN_OPERATION", "Unknown operation")
        if not self._mutex.acquire(blocking=False):
            raise BridgeError("CONTROL_BUSY", "Another request is running; wait for its receipt")
        try:
            if self._closed:
                raise BridgeError("BRIDGE_CLOSED", "Bridge is closed")
            self._send(operation, arguments)
            result = self._receive(30)
            if operation == "attach":
                self._session = result["session_id"]
            elif operation == "detach":
                self._session = None
            return result
        finally:
            self._mutex.release()

    def doctor(self):
        return self.call("doctor")

    def list_windows(self):
        return self.call("list_windows")

    def attach(self, window_id, evidence_dir="./evidence", game_dir=None, focus=True):
        return self.call("attach", window_id=str(window_id), evidence_dir=str(Path(evidence_dir).resolve()),
                         game_dir=str(Path(game_dir).resolve()) if game_dir else None, focus=focus)

    def observe(self, session_id, max_width=1280):
        return self.call("observe", session_id=session_id, max_width=max_width)

    def step(self, session_id, action_id, actions):
        return self.call("step", session_id=session_id, action_id=action_id, actions=actions)

    def read_logs(self, session_id, cursor=None):
        return self.call("read_logs", session_id=session_id, cursor=cursor)

    def menu_flow(self, session_id, action_id, flow, profile_path, world_name=None, timeout_ms=20000):
        return self.call("menu_flow", session_id=session_id, action_id=action_id, flow=flow,
                         profile_path=str(Path(profile_path).resolve()), world_name=world_name, timeout_ms=timeout_ms)

    def record_verification(self, session_id, expectation, verdict, evidence_refs, author="planner"):
        return self.call("record_verification", session_id=session_id, expectation=expectation, verdict=verdict,
                         evidence_refs=evidence_refs, author=author)

    def stop(self, session_id):
        if session_id != self._session:
            raise BridgeError("SESSION_NOT_FOUND", "Cannot stop a different session")
        self._send("signal_stop", {})  # consumed by the worker's reader, not its action queue
        try:
            return self.call("stop", session_id=session_id)
        except BridgeError as error:
            if error.code == "CONTROL_BUSY":
                return {"session_id": session_id, "stop_requested": True, "await_running_step_receipt": True}
            raise

    def detach(self, session_id):
        return self.call("detach", session_id=session_id)

    def close(self):
        if not self._closed:
            self._closed = True
            with self._write_lock:
                try:
                    self._process.stdin.close()
                except OSError:
                    pass
        try:
            self._process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            return  # Do not kill the input owner before it can release held inputs.
        self._reader.join(timeout=1)
        self._process.stdout.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
