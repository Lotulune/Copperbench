import hashlib
import json
import math
import platform
import threading
import time
import uuid
from pathlib import Path

from . import __version__
from .actions import MAX_DURATION_MS, action_duration_ms, validate_actions
from .errors import BridgeError
from .evidence import Evidence, sha256, utc
from .locking import DesktopLock

MAX_FRAME_AGE_SECONDS = 120
POST_ACTION_SETTLE_MS = 150


class Engine:
    """Runs only in the input-owning worker. Transport threads never send OS input."""

    def __init__(self, backend, stop_event=None, parent_alive=lambda: True):
        self.backend = backend
        self.stop_event = stop_event or threading.Event()
        self.parent_alive = parent_alive
        self.lock = DesktopLock(namespace=backend.platform)
        self.session = None
        self.frame = None
        self.evidence = None
        self.completed = {}
        self.menu_completed = {}
        self.stopped = False
        self.log_cursors = {}

    def doctor(self):
        return {"platform": self.backend.platform, "status": "available", "bridge_version": __version__,
                "capabilities": {"screenshots": True, "relative_mouse": True, "keyboard": True,
                                 "ascii_text": True, "game_telemetry": False, "background_control": False},
                "max_step_ms": MAX_DURATION_MS, "max_frame_age_seconds": MAX_FRAME_AGE_SECONDS,
                "post_action_settle_ms": POST_ACTION_SETTLE_MS, "emergency_key": "F8",
                "menu_flows": ["enter_world", "save_to_title", "save_and_quit"],
                "max_menu_flow_ms": 25000,
                "requirements": ["foreground unobscured game window", "same desktop and privilege level"],
                "note": "Backend initialized; actual capture/input/F8 are verified at attach and in live tests"}

    def list_windows(self):
        return {"windows": self.backend.list_windows()}

    def _require(self, session_id):
        if self.session is None or session_id != self.session["session_id"]:
            raise BridgeError("SESSION_NOT_FOUND", "Attach a window and use its session_id")

    def _window(self, require_focus=True):
        current = self.backend.window(self.session["window_id"])
        for field in ("pid", "process_started"):
            if current[field] != self.session["target"][field]:
                raise BridgeError("TARGET_CHANGED", "Window now belongs to a different process; reattach explicitly")
        rect = current["rect"]
        if not current["visible"] or rect["width"] <= 0 or rect["height"] <= 0:
            raise BridgeError("WINDOW_NOT_VISIBLE", "Restore the target window before observing or acting")
        if require_focus and not current["focused"]:
            raise BridgeError("FOCUS_LOST", "Target is not foreground; inputs were released")
        return current

    def _artifacts(self):
        if not self.session.get("game_dir"):
            return {"scope": "not_supplied", "jars": []}
        root = Path(self.session["game_dir"])
        mods = root / "mods"
        if mods.exists() and not mods.resolve().is_relative_to(root):
            raise BridgeError("PATH_OUTSIDE_GAME", "mods directory escapes game_dir")
        jars = []
        for path in sorted(mods.glob("*.jar")):
            if not path.resolve().is_relative_to(root):
                raise BridgeError("PATH_OUTSIDE_GAME", "A mod JAR escapes game_dir")
            jars.append({"path": path.relative_to(root).as_posix(), "sha256": sha256(path)})
        return {"scope": "game_dir_mods_only_not_loaded_classpath", "jars": jars}

    def attach(self, window_id, evidence_dir, game_dir=None, focus=True):
        if self.session:
            raise BridgeError("SESSION_EXISTS", "Detach the existing session first")
        if not isinstance(window_id, str) or not window_id.isdecimal():
            raise BridgeError("INVALID_ARGUMENT", "window_id must be a decimal string from list_windows")
        game = str(Path(game_dir).resolve(strict=True)) if game_dir else None
        if game and not Path(game).is_dir():
            raise BridgeError("INVALID_ARGUMENT", "game_dir must be a directory")
        self.lock.acquire()
        try:
            target = self.backend.window(window_id)
            self.backend.arm()
            if focus:
                self.backend.focus(window_id)
                target = self.backend.window(window_id)
            sid = str(uuid.uuid4())
            self.session = {"session_id": sid, "window_id": window_id, "target": target, "game_dir": game}
            self.completed, self.log_cursors = {}, {}
            self.menu_completed = {}
            self.frame, self.stopped = None, False
            self.stop_event.clear()
            metadata = {**self.session, "started_at": utc(), "platform": self.backend.platform,
                        "system": platform.platform(), "bridge_version": __version__, "input_kind": "os_keyboard_mouse",
                        "artifact_start": self._artifacts(), "client_behavior_verified": False}
            self.evidence = Evidence(Path(evidence_dir).resolve() / sid, metadata)
            self.evidence.event({"type": "attached", "target": target})
            return {**self.session, "evidence_dir": str(self.evidence.root), "emergency_key": "F8",
                    "next": "observe before step; keep the game foreground and unobscured"}
        except Exception:
            self.session = None
            self.backend.disarm()
            self.lock.release()
            raise

    def _check(self):
        if not self.parent_alive():
            raise BridgeError("CONTROLLER_DISCONNECTED", "Controller exited")
        if self.stopped or self.stop_event.is_set() or self.backend.emergency_pressed():
            self.stopped = True
            raise BridgeError("STOPPED", "Control stopped; detach and reattach to resume")
        return self._window()

    def poll_stop(self):
        if not self.session or self.stopped:
            return
        if self.stop_event.is_set() or self.backend.emergency_pressed():
            self.stopped = True
            self.backend.release_all()
            self.evidence.event({"type": "stopped", "reason": "emergency_or_requested"})

    def observe(self, session_id, max_width=1280):
        self._require(session_id)
        if isinstance(max_width, bool) or not isinstance(max_width, int) or not 320 <= max_width <= 3840:
            raise BridgeError("INVALID_ARGUMENT", "max_width must be an integer between 320 and 3840")
        started = time.monotonic()
        window = self._window()
        png, width, height = self.backend.capture(window, max_width)
        after = self._window()
        if after["rect"] != window["rect"]:
            raise BridgeError("WINDOW_CHANGED", "Window moved/resized during capture; observe again")
        fid = str(uuid.uuid4())
        path = self.evidence.root / f"frame-{fid}.png"
        path.write_bytes(png)
        self.frame = {"frame_id": fid, "captured_at": utc(), "window": window,
                      "max_frame_age_seconds": MAX_FRAME_AGE_SECONDS,
                      "image": {"path": str(path), "relative_path": path.name, "mime_type": "image/png",
                                "width": width, "height": height, "sha256": hashlib.sha256(png).hexdigest()},
                      "capture_ms": round((time.monotonic() - started) * 1000, 2)}
        self.frame_time = time.monotonic()
        self.evidence.event({"type": "observation", **self.frame})
        return {"session_id": session_id, "stopped": self.stopped, **self.frame}

    def _require_frame(self, frame_id=None):
        age = time.monotonic() - self.frame_time if self.frame else None
        if (age is None or age > MAX_FRAME_AGE_SECONDS
                or frame_id is not None and frame_id != self.frame["frame_id"]):
            raise BridgeError("STALE_FRAME", f"Use the latest observation within {MAX_FRAME_AGE_SECONDS} seconds; observe again before acting",
                              {"frame_age_ms": round(age * 1000, 2) if age is not None else None,
                               "max_frame_age_seconds": MAX_FRAME_AGE_SECONDS,
                               "latest_frame_id": self.frame["frame_id"] if self.frame else None,
                               "requested_frame_id": frame_id})
        return age

    def _click_point(self, action, window):
        self._require_frame(action["frame_id"])
        if self.frame["window"]["rect"] != window["rect"]:
            raise BridgeError("WINDOW_CHANGED", "Window geometry changed; observe before clicking")
        image, rect = self.frame["image"], window["rect"]
        if action["x"] >= image["width"] or action["y"] >= image["height"]:
            raise BridgeError("INVALID_COORDINATE", "Click lies outside the referenced screenshot")
        return (rect["left"] + min(rect["width"] - 1, math.floor(action["x"] * rect["width"] / image["width"])),
                rect["top"] + min(rect["height"] - 1, math.floor(action["y"] * rect["height"] / image["height"])))

    def _wait(self, duration_ms, deadline, dx=0, dy=0):
        start = time.monotonic()
        end = start + duration_ms / 1000
        moved_x = moved_y = 0
        while True:
            self._check()
            now = time.monotonic()
            if now > deadline:
                raise BridgeError("ACTION_TIMEOUT", "Action wall-clock deadline exceeded")
            ratio = min(1, (now - start) / (duration_ms / 1000))
            x, y = round(dx * ratio), round(dy * ratio)
            if x != moved_x or y != moved_y:
                self.backend.move_relative(x - moved_x, y - moved_y)
                moved_x, moved_y = x, y
            if now >= end:
                return
            time.sleep(min(0.02, end - now))

    def _execute(self, action, deadline):
        self._check()
        kind = action["type"]
        try:
            if kind == "hold":
                for name in action["keys"]:
                    self.backend.key(name, True)
                for name in action["buttons"]:
                    self.backend.button(name, True)
            elif kind == "click":
                self.backend.move_absolute(*self._click_point(action, self._window()))
                # XSync orders server requests, but GLFW can still consume the cursor event
                # on its next frame. Allow it to update cached GUI coordinates before clicking.
                if action["settle_ms"]:
                    self._wait(action["settle_ms"], deadline)
                self._check()
                self._click_point(action, self._window())
                self.backend.button(action["button"], True)
            if kind in {"hold", "look", "click", "wait"}:
                self._wait(action["duration_ms"], deadline, action.get("dx", 0), action.get("dy", 0))
            elif kind == "text":
                for character in action["text"]:
                    self._check()
                    self.backend.type_character(character)
                    self._wait(10, deadline)
            elif kind == "scroll":
                for _ in range(abs(action["steps"])):
                    self._check()
                    self.backend.scroll(1 if action["steps"] > 0 else -1)
                    self._wait(10, deadline)
        finally:
            self.backend.release_all()

    def step(self, session_id, action_id, actions):
        self._require(session_id)
        if not isinstance(action_id, str) or not 1 <= len(action_id) <= 128:
            raise BridgeError("INVALID_ARGUMENT", "action_id must have 1..128 characters")
        normalized = validate_actions(actions)
        signature = json.dumps(normalized, sort_keys=True)
        if action_id in self.completed:
            old_signature, result = self.completed[action_id]
            if signature != old_signature:
                raise BridgeError("ACTION_ID_CONFLICT", "action_id was already used for a different batch")
            return {**result, "replayed": True}
        # Never evict deduplication entries while the session lives.
        if len(self.completed) >= 10000:
            raise BridgeError("SESSION_LIMIT", "Detach and start a new session after 10000 batches")
        try:
            window = self._check()
            frame_age = self._require_frame()
            if self.frame["window"]["rect"] != window["rect"]:
                raise BridgeError("WINDOW_CHANGED", "Window geometry changed; observe before acting")
            for action in normalized:
                if action["type"] == "click":
                    self._click_point(action, window)
        except BridgeError as error:
            self.evidence.event({"type": "step_rejected", "action_id": action_id,
                                 "error": error.as_dict(), "input_executed": False})
            raise
        start = time.monotonic()
        before = self.frame["frame_id"]
        declared_ms = sum(action_duration_ms(action) for action in normalized)
        trailing_wait = normalized[-1]["duration_ms"] if normalized[-1]["type"] == "wait" else 0
        settle_ms = min(max(0, POST_ACTION_SETTLE_MS - trailing_wait), MAX_DURATION_MS - declared_ms)
        result = {"session_id": session_id, "action_id": action_id, "status": "completed",
                  "before_frame_id": before, "before_frame_age_ms": round(frame_age * 1000, 2),
                  "declared_duration_ms": declared_ms, "post_action_settle_ms": 0,
                  "completed_actions": 0, "replayed": False}
        self.evidence.event({"type": "step_started", "action_id": action_id, "actions": normalized, "before_frame_id": before})
        try:
            for index, action in enumerate(normalized):
                self.evidence.event({"type": "action_started", "action_id": action_id, "index": index, "action": action})
                self._execute(action, start + 2.5)  # 2 s input budget plus bounded OS scheduling overhead
                result["completed_actions"] += 1
                self.evidence.event({"type": "action_completed", "action_id": action_id, "index": index})
        except Exception as exc:
            error = exc if isinstance(exc, BridgeError) else BridgeError("BACKEND_ERROR", str(exc))
            result.update(status="interrupted", error=error.as_dict())
        finally:
            try:
                self.backend.release_all()
            except Exception as exc:
                self.stopped = True
                result.update(status="interrupted", release_error=str(exc))
        result["input_elapsed_ms"] = round((time.monotonic() - start) * 1000, 2)
        try:
            # Inputs have been released. Allow the game to consume the final release/menu event
            # without another LLM round-trip; retain the same focus/stop and two-second budget.
            if result["status"] == "completed" and settle_ms:
                result["post_action_settle_ms"] = settle_ms
                self._wait(settle_ms, start + 2.5)
            result["observation"] = self.observe(session_id)
        except Exception as exc:
            result["observation_error"] = (exc if isinstance(exc, BridgeError) else BridgeError("CAPTURE_FAILED", str(exc))).as_dict()
            if result["status"] == "completed":
                result["status"] = "executed_unobserved"
        result["elapsed_ms"] = round((time.monotonic() - start) * 1000, 2)
        self.completed[action_id] = signature, result
        self.evidence.event({"type": "step_finished", **result})
        return result

    def menu_flow(self, session_id, action_id, flow, profile_path, world_name=None, timeout_ms=20000):
        from .menu_flow import run_menu_flow
        return run_menu_flow(self, session_id, action_id, flow, profile_path, world_name, timeout_ms)

    def read_logs(self, session_id, cursor=None):
        self._require(session_id)
        if not self.session["game_dir"]:
            raise BridgeError("GAME_DIRECTORY_REQUIRED", "Attach with game_dir to read logs/latest.log")
        root = Path(self.session["game_dir"])
        path = root / "logs" / "latest.log"
        if not path.resolve().is_relative_to(root):
            raise BridgeError("PATH_OUTSIDE_GAME", "Log path escapes game_dir")
        if not path.is_file():
            raise BridgeError("LOG_UNAVAILABLE", "logs/latest.log does not exist")
        if cursor is not None and cursor not in self.log_cursors:
            raise BridgeError("INVALID_CURSOR", "Log cursor does not belong to this session")
        with path.open("rb") as stream:
            import os
            stat = os.fstat(stream.fileno())
            identity = f"{stat.st_dev}:{stat.st_ino}"
            previous = self.log_cursors.get(cursor)
            rotated = previous is not None and (previous[0] != identity or stat.st_size < previous[1])
            offset = previous[1] if previous and not rotated else max(0, stat.st_size - 65536)
            # Detect in-place rewrites even if the new file grew past the previous offset.
            if previous and not rotated and offset:
                stream.seek(max(0, offset - 64))
                if hashlib.sha256(stream.read(min(offset, 64))).hexdigest() != previous[2]:
                    rotated = True
                    offset = max(0, stat.st_size - 65536)
            stream.seek(offset)
            data = stream.read(65536)
            end = stream.tell()
            stream.seek(max(0, end - 64))
            anchor = hashlib.sha256(stream.read(min(end, 64))).hexdigest()
        token = str(uuid.uuid4())
        self.log_cursors[token] = identity, end, anchor
        text = data.decode("utf-8", "replace")
        name = f"log-{token}.txt"
        (self.evidence.root / name).write_text(text, encoding="utf-8")
        return {"cursor": token, "text": text, "rotated": rotated, "offset": offset, "next_offset": end,
                "truncated_prefix": offset > 0 and (previous is None or rotated),
                "has_more": end < stat.st_size, "evidence_path": name, "source": "game_log_not_live_telemetry"}

    def record_verification(self, session_id, expectation, verdict, evidence_refs, author="planner"):
        self._require(session_id)
        return self.evidence.record_verification(expectation, verdict, evidence_refs, author)

    def stop(self, session_id):
        self._require(session_id)
        self.stopped = True
        self.stop_event.set()
        self.backend.release_all()
        self.evidence.event({"type": "stopped", "reason": "requested"})
        return {"session_id": session_id, "stopped": True}

    def detach(self, session_id):
        self._require(session_id)
        try:
            self.backend.release_all()
            try:
                artifacts = self._artifacts()
                extra = {"artifact_end": artifacts, "artifacts_unchanged": artifacts == self.evidence.metadata["artifact_start"]}
            except Exception as exc:
                extra = {"artifact_check_error": str(exc), "artifacts_unchanged": False}
            self.evidence.event({"type": "detached"})
            return self.evidence.finish(extra)
        finally:
            self.backend.disarm()
            self.lock.release()
            self.session = None
            self.frame = None

    def close(self):
        try:
            if self.session:
                self.detach(self.session["session_id"])
        finally:
            self.backend.close()
            self.lock.release()
