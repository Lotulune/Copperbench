import copy
import json
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from pathlib import Path

from minecraft_control_bridge.actions import validate_actions
from minecraft_control_bridge.engine import Engine
from minecraft_control_bridge.errors import BridgeError
from minecraft_control_bridge.evidence import sha256


class FakeBackend:
    platform = "test-only"

    def __init__(self):
        self.state = {"window_id": "100", "pid": 123, "process_started": "42", "focused": True,
                      "visible": True, "title": "Minecraft test fixture", "executable": "java",
                      "rect": {"left": -200, "top": 100, "width": 1600, "height": 900}}
        self.events = []
        self.keys, self.buttons = set(), set()
        self.emergency = False
        self.on_key = None
        self.capture_fails = False

    def window(self, _):
        return copy.deepcopy(self.state)

    def list_windows(self):
        return [self.window("100")]

    def arm(self):
        self.armed = True

    def disarm(self):
        self.armed = False

    def focus(self, _):
        return True

    def capture(self, _, max_width):
        if self.capture_fails:
            raise BridgeError("CAPTURE_FAILED", "fixture")
        # Real PNG bytes so transport tests can decode them.
        from PIL import Image
        import io
        w = min(1600, max_width)
        h = round(900 * w / 1600)
        out = io.BytesIO()
        Image.new("RGB", (w, h), "green").save(out, format="PNG")
        return out.getvalue(), w, h

    def emergency_pressed(self):
        return self.emergency

    def key(self, name, down):
        self.events.append(("key", name, down))
        (self.keys.add if down else self.keys.discard)(name)
        if down and self.on_key:
            self.on_key()

    def button(self, name, down):
        self.events.append(("button", name, down))
        (self.buttons.add if down else self.buttons.discard)(name)

    def move_relative(self, dx, dy):
        self.events.append(("relative", dx, dy))

    def move_absolute(self, x, y):
        self.events.append(("absolute", x, y))

    def scroll(self, direction):
        self.events.append(("scroll", direction))

    def type_character(self, character):
        self.events.append(("text", character))

    def release_all(self):
        for k in list(self.keys):
            self.key(k, False)
        for b in list(self.buttons):
            self.button(b, False)

    def close(self):
        self.release_all()


class EngineTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.backend = FakeBackend()
        self.engine = Engine(self.backend)
        self.sid = self.engine.attach("100", str(self.root), str(self.root))["session_id"]
        self.frame = self.engine.observe(self.sid)

    def tearDown(self):
        self.engine.close()
        self.tmp.cleanup()

    def step(self, actions, action_id="1"):
        return self.engine.step(self.sid, action_id, actions)

    def assert_code(self, code, callback):
        with self.assertRaises(BridgeError) as caught:
            callback()
        self.assertEqual(code, caught.exception.code)

    def test_all_actions_validated_before_any_input(self):
        self.assert_code("INVALID_ACTION", lambda: self.step([
            {"type": "hold", "keys": ["w"]}, {"type": "hold", "keys": ["f8"]}]))
        self.assertEqual([], self.backend.events)

    def test_duration_and_nonfinite_rejected(self):
        for actions in ([{"type": "wait", "duration_ms": 2001}],
                        [{"type": "wait", "duration_ms": 1100}] * 2,
                        [{"type": "look", "dx": float("nan")}],
                        [{"type": "look", "duration_ms": True}]):
            with self.subTest(actions=actions):
                self.assert_code("INVALID_ACTION", lambda: self.step(actions))

    def test_duplicate_receipt_no_reexecution(self):
        actions = [{"type": "hold", "keys": ["w"], "duration_ms": 10}]
        first = self.step(actions)
        count = len(self.backend.events)
        second = self.step(actions)
        self.assertEqual(first["observation"]["frame_id"], second["observation"]["frame_id"])
        self.assertTrue(second["replayed"])
        self.assertEqual(count, len(self.backend.events))
        self.assert_code("ACTION_ID_CONFLICT", lambda: self.step([{"type": "wait", "duration_ms": 10}]))

    def test_combination_and_relative_motion_release(self):
        result = self.step([{"type": "hold", "keys": ["w", "space"], "buttons": ["right"],
                             "dx": 31, "dy": -7, "duration_ms": 50}])
        self.assertEqual("completed", result["status"])
        motion = [e for e in self.backend.events if e[0] == "relative"]
        self.assertEqual(31, sum(e[1] for e in motion))
        self.assertEqual(-7, sum(e[2] for e in motion))
        self.assertEqual(set(), self.backend.keys | self.backend.buttons)

    def test_scaled_coordinates_on_negative_monitor(self):
        result = self.step([{"type": "click", "x": 640, "y": 360, "frame_id": self.frame["frame_id"], "duration_ms": 5}])
        self.assertEqual("completed", result["status"])
        self.assertIn(("absolute", 600, 550), self.backend.events)

    def test_stale_frame_after_resize_or_expiry(self):
        action = {"type": "click", "x": 10, "y": 10, "frame_id": self.frame["frame_id"]}
        self.backend.state["rect"]["width"] = 800
        self.assert_code("WINDOW_CHANGED", lambda: self.step([action]))
        self.engine.frame_time -= 121
        self.assert_code("STALE_FRAME", lambda: self.step([action]))
        self.assertEqual([], self.backend.events)

    def test_click_uses_advertised_120_second_window_not_30(self):
        self.assertEqual(120, self.engine.doctor()["max_frame_age_seconds"])
        self.engine.frame_time -= 60
        result = self.step([{"type": "click", "x": 10, "y": 10,
                             "frame_id": self.frame["frame_id"], "duration_ms": 5}])
        self.assertEqual("completed", result["status"])
        self.assertGreaterEqual(result["before_frame_age_ms"], 60000)
        self.assertEqual(1, self.backend.events.count(("button", "left", True)))

    def test_expired_frame_rejection_records_no_input_and_recovery_details(self):
        self.engine.frame_time -= 121
        with self.assertRaises(BridgeError) as caught:
            self.step([{"type": "click", "x": 10, "y": 10, "frame_id": self.frame["frame_id"]}])
        self.assertEqual("STALE_FRAME", caught.exception.code)
        self.assertEqual(120, caught.exception.details["max_frame_age_seconds"])
        self.assertGreaterEqual(caught.exception.details["frame_age_ms"], 121000)
        events = [json.loads(line) for line in (self.engine.evidence.root / "actions.jsonl").read_text().splitlines()]
        self.assertEqual("step_rejected", events[-1]["type"])
        self.assertFalse(events[-1]["input_executed"])
        self.assertEqual([], self.backend.events)

    def test_resize_rejects_keyboard_batch_before_input_too(self):
        self.backend.state["rect"]["left"] += 100
        self.assert_code("WINDOW_CHANGED", lambda: self.step([{"type": "hold", "keys": ["enter"]}]))
        self.assertEqual([], self.backend.events)

    def test_final_capture_waits_after_release_without_extra_observe_call(self):
        released = []
        original_key, original_capture = self.backend.key, self.backend.capture
        def key(name, down):
            original_key(name, down)
            if not down:
                released.append(time.monotonic())
        def capture(window, width):
            self.assertFalse(self.backend.keys | self.backend.buttons)
            self.assertGreaterEqual(time.monotonic() - released[-1], 0.13)
            return original_capture(window, width)
        self.backend.key, self.backend.capture = key, capture
        result = self.step([{"type": "hold", "keys": ["enter"], "duration_ms": 5}])
        self.assertEqual("completed", result["status"])
        self.assertEqual(150, result["post_action_settle_ms"])
        self.assertEqual(5, result["declared_duration_ms"])

    def test_post_action_settle_respects_total_budget_and_existing_wait(self):
        with patch.object(self.engine, "_wait") as wait:
            result = self.step([{"type": "hold", "keys": ["w"], "duration_ms": 1950}])
            self.assertEqual(50, result["post_action_settle_ms"])
            self.assertEqual(2000, sum(call.args[0] for call in wait.call_args_list))
            wait.reset_mock()
            result = self.step([{"type": "hold", "keys": ["w"], "duration_ms": 100},
                                {"type": "wait", "duration_ms": 200}], "2")
            self.assertEqual(0, result["post_action_settle_ms"])
            self.assertEqual(2, wait.call_count)

    def test_focus_loss_after_release_keeps_receipt_without_replaying(self):
        original_key = self.backend.key
        def key(name, down):
            original_key(name, down)
            if not down:
                self.backend.state["focused"] = False
        self.backend.key = key
        actions = [{"type": "hold", "keys": ["enter"], "duration_ms": 5}]
        result = self.step(actions)
        self.assertEqual("executed_unobserved", result["status"])
        self.assertEqual(1, result["completed_actions"])
        self.assertEqual("FOCUS_LOST", result["observation_error"]["code"])
        self.assertEqual(set(), self.backend.keys)
        self.assertTrue(self.step(actions)["replayed"])
        self.assertEqual(1, self.backend.events.count(("key", "enter", True)))

    def test_old_frame_id_and_out_of_bounds(self):
        action = {"type": "click", "x": 10, "y": 10, "frame_id": "old"}
        self.assert_code("STALE_FRAME", lambda: self.step([action]))
        action.update(frame_id=self.frame["frame_id"], x=1280)
        self.assert_code("INVALID_COORDINATE", lambda: self.step([action]))

    def test_click_waits_for_client_cursor_processing(self):
        moved_at = []
        original_move = self.backend.move_absolute
        original_button = self.backend.button
        def move(x, y):
            moved_at.append(time.monotonic())
            original_move(x, y)
        def button(name, down):
            if down:
                self.assertGreaterEqual(time.monotonic() - moved_at[-1], 0.075)
            original_button(name, down)
        self.backend.move_absolute = move
        self.backend.button = button
        result = self.step([{"type": "click", "x": 10, "y": 10, "frame_id": self.frame["frame_id"]}])
        self.assertEqual("completed", result["status"])

    def test_focus_lost_while_cursor_settles_does_not_click(self):
        self.backend.move_absolute = lambda x, y: self.backend.state.update(focused=False)
        result = self.step([{"type": "click", "x": 10, "y": 10, "frame_id": self.frame["frame_id"]}])
        self.assertEqual("interrupted", result["status"])
        self.assertFalse(any(e[0] == "button" for e in self.backend.events))

    def test_focus_loss_mid_hold_releases_and_no_success_claim(self):
        self.backend.on_key = lambda: self.backend.state.update(focused=False)
        result = self.step([{"type": "hold", "keys": ["w"], "duration_ms": 100}])
        self.assertEqual("interrupted", result["status"])
        self.assertEqual("FOCUS_LOST", result["error"]["code"])
        self.assertEqual(set(), self.backend.keys)
        self.assertIn("observation_error", result)

    def test_parent_disconnection_releases(self):
        self.backend.on_key = lambda: setattr(self.engine, "parent_alive", lambda: False)
        result = self.step([{"type": "hold", "keys": ["w"], "duration_ms": 100}])
        self.assertEqual("CONTROLLER_DISCONNECTED", result["error"]["code"])
        self.assertEqual(set(), self.backend.keys)

    def test_emergency_stop_latches(self):
        self.backend.on_key = lambda: setattr(self.backend, "emergency", True)
        result = self.step([{"type": "hold", "keys": ["w"], "duration_ms": 100}])
        self.assertEqual("STOPPED", result["error"]["code"])
        self.backend.emergency = False
        self.assert_code("STOPPED", lambda: self.step([{"type": "wait"}], "2"))
        self.assertEqual(set(), self.backend.keys)

    def test_stop_signal_interrupts_without_waiting_for_rpc(self):
        timer = threading.Timer(0.05, self.engine.stop_event.set)
        timer.start()
        result = self.step([{"type": "hold", "keys": ["w"], "duration_ms": 1900}])
        timer.join()
        self.assertEqual("STOPPED", result["error"]["code"])
        self.assertLess(result["input_elapsed_ms"], 500)
        self.assertEqual(set(), self.backend.keys)

    def test_process_id_reuse_rejected(self):
        self.backend.state["process_started"] = "different"
        self.assert_code("TARGET_CHANGED", lambda: self.step([{"type": "hold", "keys": ["w"]}]))
        self.assertEqual([], self.backend.events)

    def test_capture_failure_does_not_repeat_input(self):
        self.backend.capture_fails = True
        actions = [{"type": "hold", "keys": ["w"], "duration_ms": 5}]
        result = self.step(actions)
        self.assertEqual("executed_unobserved", result["status"])
        self.assertTrue(self.step(actions)["replayed"])
        self.assertEqual(1, self.backend.events.count(("key", "w", True)))

    def test_log_cursor_rotation_rewrite_and_invalid_cursor(self):
        (self.root / "logs").mkdir()
        path = self.root / "logs/latest.log"
        path.write_text("first\n", encoding="utf-8")
        first = self.engine.read_logs(self.sid)
        with path.open("a", encoding="utf-8") as stream:
            stream.write("second\n")
        second = self.engine.read_logs(self.sid, first["cursor"])
        self.assertEqual("second", second["text"].strip())
        path.write_text("replacement longer than previous content\n", encoding="utf-8")
        rotated = self.engine.read_logs(self.sid, second["cursor"])
        self.assertTrue(rotated["rotated"])
        self.assertIn("replacement", rotated["text"])
        self.assert_code("INVALID_CURSOR", lambda: self.engine.read_logs(self.sid, "bogus"))

    def test_verified_record_requires_evidence_and_is_external(self):
        record = self.engine.record_verification(self.sid, "Expected change", "passed", [])
        self.assertEqual("unverified", record["verdict"])
        record = self.engine.record_verification(self.sid, "Expected change", "passed",
                                                  ["actions.jsonl", self.frame["image"]["relative_path"]])
        self.assertEqual("passed", record["verdict"])
        self.assertFalse(record["bridge_independently_verified"])
        self.assert_code("INVALID_VERIFICATION", lambda: self.engine.record_verification(
            self.sid, "Expected", "passed", ["../../outside.png"]))

    def test_verification_equivalent_session_paths_have_identical_evidence(self):
        root = self.engine.evidence.root
        frame = self.frame["image"]["relative_path"]
        (root / "nested").mkdir()
        expected = [{"path": ref, "sha256_at_recording": sha256(root / ref)}
                    for ref in ("actions.jsonl", frame)]
        references = [
            ["actions.jsonl", frame],
            [str((root / "actions.jsonl").resolve()), str((root / frame).resolve())],
            ["./actions.jsonl", "./" + frame],
            ["nested/../actions.jsonl", "nested/../" + frame],
        ]
        for refs in references:
            with self.subTest(refs=refs):
                record = self.engine.record_verification(self.sid, "Expected change", "passed", refs)
                self.assertEqual("passed", record["verdict"])
                self.assertEqual(expected, record["evidence"])
                self.assertEqual("external_planner", record["assessment_source"])
                self.assertFalse(record["bridge_independently_verified"])

    def test_verification_nested_actions_file_is_not_the_session_log(self):
        root = self.engine.evidence.root
        nested = root / "nested"
        nested.mkdir()
        (nested / "actions.jsonl").write_text("{}\n", encoding="utf-8")
        record = self.engine.record_verification(self.sid, "Expected change", "passed",
            [str((nested / "actions.jsonl").resolve()), self.frame["image"]["relative_path"]])
        self.assertEqual("unverified", record["verdict"])
        self.assertEqual("passed", record["requested_verdict"])

    def test_verification_existing_other_session_files_are_rejected(self):
        outside = self.root / "other-session"
        outside.mkdir()
        (outside / "actions.jsonl").write_text("{}\n", encoding="utf-8")
        frame = self.frame["image"]["relative_path"]
        (outside / "frame.png").write_bytes((self.engine.evidence.root / frame).read_bytes())
        for refs in ([str(outside / "actions.jsonl"), str(outside / "frame.png")],
                     ["../other-session/actions.jsonl", "../other-session/frame.png"]):
            with self.subTest(refs=refs):
                self.assert_code("INVALID_VERIFICATION", lambda: self.engine.record_verification(
                    self.sid, "Expected change", "passed", refs))

    def test_verification_symlink_escape_is_rejected(self):
        root = self.engine.evidence.root
        outside = self.root / "outside.png"
        outside.write_bytes((root / self.frame["image"]["relative_path"]).read_bytes())
        link = root / "linked.png"
        try:
            link.symlink_to(outside)
        except (OSError, NotImplementedError) as error:
            self.skipTest(f"Symlinks unavailable: {error}")
        self.assert_code("INVALID_VERIFICATION", lambda: self.engine.record_verification(
            self.sid, "Expected change", "passed", ["actions.jsonl", "linked.png"]))

    def test_detach_manifest_and_artifact_change(self):
        (self.root / "mods").mkdir()
        (self.root / "mods/new.jar").write_bytes(b"changed")
        result = self.engine.detach(self.sid)
        root = Path(result["evidence_dir"])
        session = json.loads((root / "session.json").read_text())
        self.assertFalse(session["artifacts_unchanged"])
        hashes = json.loads((root / "hashes.json").read_text())
        self.assertTrue(hashes)
        for name, digest in hashes.items():
            self.assertEqual(digest, sha256(root / name))

    def test_cross_instance_desktop_lock(self):
        other = Engine(FakeBackend())
        try:
            self.assert_code("DESKTOP_BUSY", lambda: other.attach("100", str(self.root)))
        finally:
            other.close()


class ActionContractTests(unittest.TestCase):
    def test_unsupported_unicode_unknown_fields_and_types(self):
        for action in ({"type": "text", "text": "中文"}, {"type": "wait", "shell": "no"},
                       {"type": "scroll", "steps": 1.5}, {"type": "hold", "keys": ["w", "w"]},
                       {"type": []}, {"type": "click", "button": [], "frame_id": "x", "x": 0, "y": 0}):
            with self.subTest(action=action):
                with self.assertRaises(BridgeError):
                    validate_actions([action])


if __name__ == "__main__":
    unittest.main()
