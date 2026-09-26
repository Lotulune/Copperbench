"""Bounded menu navigation using planner-reviewed local image profiles, never a model loop."""
import hashlib
import json
import time
from pathlib import Path

from PIL import Image, ImageChops

from .errors import BridgeError

FLOWS = {"enter_world", "save_to_title", "save_and_quit"}
MAX_FLOW_MS = 25000  # below the existing 30 s worker RPC budget
POLL_MS = 250


def invalid(message):
    raise BridgeError("INVALID_MENU_PROFILE", message)


def ink(image):
    """Default-font white/gray glyphs; discard colored/animated menu backgrounds."""
    return Image.frombytes("L", image.size, bytes(
        255 if min(pixel) >= 220 and max(pixel) - min(pixel) <= 20 else 0
        for pixel in image.convert("RGB").getdata()))


class MenuProfile:
    def __init__(self, path):
        self.path = Path(path).resolve(strict=True)
        raw = self.path.read_bytes()
        if len(raw) > 65536:
            invalid("Profile exceeds 64 KiB")
        data = json.loads(raw)
        if data.get("schema_version") != 1:
            invalid("Expected menu profile schema_version 1")
        self.name = data.get("world_name")
        if not isinstance(self.name, str) or not 1 <= len(self.name) <= 64 or any(not 32 <= ord(c) <= 126 for c in self.name):
            invalid("world_name must be 1..64 printable ASCII characters for the search field")
        self.size = data.get("capture_size")
        if not isinstance(self.size, list) or len(self.size) != 2 or any(type(v) is not int or not 320 <= v <= 3840 for v in self.size):
            invalid("capture_size must contain the observed width and height (320..3840)")
        self.states = data.get("states", {})
        required = {"title": {"singleplayer", "quit"}, "world_list": {"heading", "create"},
                    "pause": {"heading", "save"}, "playing": {"hud_left", "hud_right"}}
        if set(self.states) != set(required):
            invalid("Provide title, world_list, pause and playing states")
        self.images = {}
        digest = hashlib.sha256(raw)
        for state, roles in required.items():
            spec = self.states[state]
            if not isinstance(spec, dict) or set(spec.get("markers", {})) != roles:
                invalid(f"Missing or unexpected markers for {state}")
            for marker in spec["markers"].values():
                self._load(marker, digest)
        if not isinstance(self.states["playing"].get("title_suffix"), str) or not self.states["playing"]["title_suffix"]:
            invalid("playing requires a nonempty window title_suffix")
        self.entry = data.get("world_entry", {})
        self._load(self.entry, digest)
        self.search = self._box(data.get("world_search_box"))
        self.empty_below = self._box(data.get("empty_below_entry"))
        ex, ey, ew, eh = self.entry["box"]
        bx, by, bw, bh = self.empty_below
        if (self.entry.get("mode", "ink") != "ink" or bx > ex or bx+bw < ex+ew
                or not ey+eh <= by <= ey+eh+64 or bh < 100 or by+bh < self.size[1]-140):
            invalid("World guard must cover the remaining visible result rows below the name template")
        self.fingerprint = digest.hexdigest()

    def _box(self, box):
        if not isinstance(box, list) or len(box) != 4 or any(type(v) is not int for v in box):
            invalid("Boxes use integer [x,y,width,height]")
        x, y, w, h = box
        if min(x, y) < 0 or min(w, h) <= 0 or x + w > self.size[0] or y + h > self.size[1]:
            invalid("Box is outside capture_size")
        return box

    def _load(self, marker, digest):
        if not isinstance(marker, dict):
            invalid("Each marker must be an object")
        self._box(marker.get("box"))
        mode = marker.get("mode", "ink")
        if mode not in {"ink", "rgb"}:
            invalid("Marker mode must be ink or rgb")
        name = marker.get("image")
        if not isinstance(name, str):
            invalid("Marker image must be a relative PNG path")
        path = (self.path.parent / name).resolve(strict=True)
        if not path.is_relative_to(self.path.parent) or path.suffix.lower() != ".png" or path.stat().st_size > 1048576:
            invalid("Marker must be a PNG within the profile directory, at most 1 MiB")
        payload = path.read_bytes()
        actual = hashlib.sha256(payload).hexdigest()
        if marker.get("sha256") != actual:
            invalid("Marker hash differs from the reviewed profile")
        digest.update(payload)
        with Image.open(path) as source:
            if list(source.size) != marker["box"][2:]:
                invalid("Marker dimensions differ from box")
            image = source.convert("RGB")
        template = ink(image) if mode == "ink" else image
        if mode == "ink" and (template.histogram()[255] < 12 or template.histogram()[0] < 12):
            invalid("Ink markers need both glyph and background pixels")
        if mode == "rgb" and len(set(image.getdata())) < 3:
            invalid("RGB markers must contain a nonuniform UI feature")
        self.images[(name, mode)] = template

    @staticmethod
    def crop(image, box):
        x, y, w, h = box
        return image.crop((x, y, x + w, y + h))

    def matches(self, image, marker):
        actual = self.crop(image, marker["box"]).convert("RGB")
        if marker.get("mode", "ink") == "ink":
            actual = ink(actual)
        # Deliberately exact after glyph normalization: unsupported scale/theme stops safely.
        return ImageChops.difference(actual, self.images[(marker["image"], marker.get("mode", "ink"))]).getbbox() is None

    def classify(self, observation):
        with Image.open(observation["image"]["path"]) as source:
            image = source.convert("RGB")
        if list(image.size) != self.size:
            raise BridgeError("MENU_PROFILE_GEOMETRY", "Window/capture size differs from the reviewed profile; recalibrate")
        hits = [state for state, spec in self.states.items()
                if (not spec.get("title_suffix") or observation["window"]["title"].endswith(spec["title_suffix"]))
                and all(self.matches(image, marker) for marker in spec["markers"].values())]
        if len(hits) > 1:
            raise BridgeError("MENU_STATE_AMBIGUOUS", "Multiple menu states matched; no further input")
        return hits[0] if hits else "unknown"

    def check_world(self, observation):
        with Image.open(observation["image"]["path"]) as source:
            image = source.convert("RGB")
        if not self.matches(image, self.entry):
            raise BridgeError("MENU_WORLD_NOT_MATCHED", "The first filtered row does not match the reviewed world name")
        # A profile covers exactly one filtered result. Any further visible row is ambiguous;
        # do not select the first item merely because its label is familiar.
        if ink(self.crop(image, self.empty_below)).histogram()[255] > 8:
            raise BridgeError("MENU_WORLD_AMBIGUOUS", "More than one filtered row or unexpected list content; inspect manually")

    def point(self, state, role):
        x, y, w, h = self.states[state]["markers"][role]["box"]
        return x + w // 2, y + h // 2


def run_menu_flow(engine, session_id, action_id, flow, profile_path, world_name=None, timeout_ms=20000):
    engine._require(session_id)
    if flow not in FLOWS or not isinstance(action_id, str) or not 1 <= len(action_id) <= 96:
        raise BridgeError("INVALID_MENU_FLOW", "Choose enter_world/save_to_title/save_and_quit and a 1..96 character action_id")
    if type(timeout_ms) is not int or not 1000 <= timeout_ms <= MAX_FLOW_MS:
        raise BridgeError("INVALID_MENU_FLOW", "timeout_ms must be an integer in 1000..25000")
    try:
        profile = MenuProfile(profile_path)
    except (OSError, ValueError, TypeError, KeyError) as error:
        raise BridgeError("INVALID_MENU_PROFILE", "Cannot load the reviewed local menu profile") from error
    if flow == "enter_world" and world_name != profile.name:
        raise BridgeError("MENU_WORLD_MISMATCH", "Explicit world_name must equal the reviewed profile world_name")
    signature = json.dumps([flow, str(profile.path), profile.fingerprint, world_name, timeout_ms])
    if action_id in engine.menu_completed:
        old, receipt = engine.menu_completed[action_id]
        if signature != old:
            raise BridgeError("ACTION_ID_CONFLICT", "Flow ID already belongs to another request/profile")
        return {**receipt, "replayed": True}
    if len(engine.menu_completed) >= 1000:
        raise BridgeError("SESSION_LIMIT", "Start a new session after 1000 menu flows")
    started = time.monotonic()
    deadline = started + timeout_ms / 1000
    initial_rect = engine._check()["rect"]
    result = {"session_id": session_id, "action_id": action_id, "flow": flow,
              "profile_sha256": profile.fingerprint, "status": "completed", "replayed": False,
              "navigation_only": True, "gameplay_verified": False, "steps": [], "states": []}
    engine.evidence.event({"type": "menu_flow_started", **result, "world_name": world_name,
                           "profile_path": str(profile.path), "timeout_ms": timeout_ms})
    stage = "initial"

    def checkpoint():
        if time.monotonic() >= deadline:
            raise BridgeError("MENU_FLOW_TIMEOUT", "Menu transition did not complete within the flow budget")
        window = engine._check()
        if window["rect"] != initial_rect:
            raise BridgeError("WINDOW_CHANGED", "Window changed during menu flow; no further input")

    def observe():
        checkpoint()
        observation = engine.observe(session_id, max_width=profile.size[0])
        result["observation"] = observation
        result["observation_is_last_known"] = False
        state = profile.classify(observation)
        event = {"state": state, "frame_id": observation["frame_id"], "stage": stage}
        result["states"].append(event)
        engine.evidence.event({"type": "menu_state", "action_id": action_id, **event})
        return state

    def await_state(wanted, previous):
        while True:
            state = observe()
            if state == wanted:
                return state
            if state not in {previous, "unknown"}:
                raise BridgeError("MENU_UNEXPECTED_STATE", f"Expected {wanted}, observed {state}; no further input")
            # Polling observes only. An input is never replayed to make a transition happen.
            engine._wait(min(POLL_MS, max(1, (deadline - time.monotonic()) * 1000)), deadline)

    def act(actions):
        checkpoint()
        if len(result["steps"]) >= 6:
            raise BridgeError("MENU_FLOW_LIMIT", "Menu flow exceeded six bounded input batches")
        if deadline - time.monotonic() < 2.5:
            raise BridgeError("MENU_FLOW_TIMEOUT", "Insufficient time for another bounded input batch")
        number = len(result["steps"])
        receipt = engine.step(session_id, f"menu:{action_id}:{number}", actions)
        result["steps"].append({key: receipt.get(key) for key in
                                ("action_id", "status", "completed_actions", "elapsed_ms", "input_elapsed_ms")})
        if "observation" in receipt:
            result["observation"] = receipt["observation"]
            result["observation_is_last_known"] = False
        else:
            result["observation_is_last_known"] = True
        if receipt["status"] != "completed":
            error = receipt.get("error") or receipt.get("observation_error") or {}
            raise BridgeError(error.get("code", "MENU_INPUT_UNCONFIRMED"), error.get("message", "Input result needs inspection"))

    def click(state, role):
        x, y = profile.point(state, role)
        act([{"type": "click", "x": x, "y": y, "frame_id": engine.frame["frame_id"]},
             {"type": "wait", "duration_ms": 200}])

    try:
        state = observe()
        if flow == "enter_world":
            if state == "title":
                stage = "open_world_list"
                click("title", "singleplayer")
                state = await_state("world_list", "title")
            if state != "world_list":
                raise BridgeError("MENU_UNEXPECTED_STATE", "Enter-world flow starts only at title or world list")
            stage = "filter_world"
            x, y, w, h = profile.search
            act([{"type": "click", "x": x+w//2, "y": y+h//2, "frame_id": engine.frame["frame_id"]},
                 {"type": "hold", "keys": ["ctrl", "a"], "duration_ms": 80},
                 {"type": "text", "text": world_name}, {"type": "wait", "duration_ms": 350}])
            if observe() != "world_list":
                raise BridgeError("MENU_UNEXPECTED_STATE", "World filter left the recognized world list")
            profile.check_world(result["observation"])
            stage = "open_exact_world"
            x, y, w, h = profile.entry["box"]
            act([{"type": "click", "x": x+w//2, "y": y+h//2, "frame_id": engine.frame["frame_id"]},
                 {"type": "wait", "duration_ms": 150}, {"type": "hold", "keys": ["enter"], "duration_ms": 120}])
            await_state("playing", "world_list")
            result["terminal"] = "world_visible"
        else:
            if state == "playing":
                stage = "open_pause"
                act([{"type": "hold", "keys": ["escape"], "duration_ms": 150}])
                state = await_state("pause", "playing")
            if state == "pause":
                stage = "save_to_title"
                click("pause", "save")
                state = await_state("title", "pause")
            if state != "title":
                raise BridgeError("MENU_UNEXPECTED_STATE", "Save flow requires playing, pause, or title")
            result["terminal"] = "title_visible"
            if flow == "save_and_quit":
                stage = "quit_game"
                try:
                    click("title", "quit")
                    while True:
                        engine._wait(POLL_MS, deadline)
                        observe()
                except BridgeError as error:
                    if error.code != "WINDOW_UNAVAILABLE":
                        raise
                result["terminal"] = "window_closed_unverified"
                result["observation_is_pre_close"] = True
    except BridgeError as error:
        result.update(status="blocked", error=error.as_dict(), stage=stage)
    except Exception:
        result.update(status="blocked", stage=stage,
                      error={"code": "MENU_FLOW_ERROR", "message": "Unexpected menu flow failure; inspect recorded evidence"})
    finally:
        try:
            engine.backend.release_all()
        except Exception:
            engine.stopped = True
            result.update(status="blocked", error={"code": "INPUT_RELEASE_FAILED", "message": "Input release failed"})
    result["elapsed_ms"] = round((time.monotonic() - started) * 1000, 2)
    engine.menu_completed[action_id] = signature, result
    engine.evidence.event({"type": "menu_flow_finished", **result})
    return result
