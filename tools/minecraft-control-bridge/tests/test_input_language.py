import copy
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from test_engine import FakeBackend
from minecraft_control_bridge.engine import Engine
from minecraft_control_bridge.errors import BridgeError


class ImeBackend(FakeBackend):
    def __init__(self):
        super().__init__()
        self.layout = 0x8040804
        self.opened = True
        self.acknowledge = True
        self.requests = []
        self.layouts = [self.layout]  # Real failure case: no separate English layout.

    def keyboard_status(self, window_id):
        return {"pid": self.state["pid"], "process_started": self.state["process_started"],
                "thread_id": 1, "layout": self.layout, "ime_risk": self.layout != 0x4090409,
                "ime_open": self.opened}

    def keyboard_layouts(self): return self.layouts
    def layout_is_ime(self, h): return h != 0x4090409

    def request_ime_open(self, window_id, opened):
        self.requests.append(("ime", opened))
        if self.acknowledge:
            self.opened = opened

    def request_keyboard_layout(self, window_id, layout):
        self.requests.append(("layout", layout))
        if self.acknowledge:
            self.layout = layout


class InputLanguageTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.backend = ImeBackend()
        self.engine = Engine(self.backend)
        self.sid = self.engine.attach("100", self.tmp.name)["session_id"]
        self.engine.observe(self.sid)

    def tearDown(self):
        self.engine.close()
        self.tmp.cleanup()

    def key(self):
        return self.engine.step(self.sid, "key", [{"type":"hold", "keys":["e"], "duration_ms":10}])

    def test_chinese_ime_only_switches_to_english_mode_then_restores(self):
        result = self.key()
        self.assertEqual("completed", result["status"])
        self.assertEqual("ime_open", result["keyboard"]["method"])
        self.assertIn(("key","e",True), self.backend.events)
        self.assertFalse(self.backend.opened)
        self.engine.detach(self.sid)
        self.assertTrue(self.backend.opened)
        self.assertEqual([("ime",False),("ime",True)],self.backend.requests)

    def test_preserve_blocks_before_any_keyboard_input(self):
        self.engine.session["keyboard_policy"] = "preserve"
        with self.assertRaises(BridgeError) as caught: self.key()
        self.assertEqual("IME_ACTIVE",caught.exception.code)
        self.assertFalse(self.backend.events)
        self.assertFalse(self.backend.requests)

    def test_unacknowledged_switch_blocks_input_and_does_not_claim_success(self):
        self.backend.acknowledge = False
        with self.assertRaises(BridgeError) as caught: self.key()
        self.assertEqual("IME_SWITCH_UNCONFIRMED",caught.exception.code)
        self.assertFalse(self.backend.events)

    def test_closed_ime_does_not_toggle_user_back_to_chinese(self):
        self.backend.opened = False
        self.key()
        self.engine.detach(self.sid)
        self.assertFalse(self.backend.requests)

    def test_unknown_ime_status_uses_installed_english_layout(self):
        self.backend.opened = None
        self.backend.layouts.append(0x4090409)
        self.key()
        self.assertEqual(0x4090409,self.backend.layout)
        self.engine.detach(self.sid)
        self.assertEqual(0x8040804,self.backend.layout)

    def test_unknown_ime_without_english_layout_blocks(self):
        self.backend.opened = None
        with self.assertRaises(BridgeError) as caught: self.key()
        self.assertEqual("ENGLISH_LAYOUT_UNAVAILABLE",caught.exception.code)
        self.assertFalse(self.backend.events)

    def test_focus_loss_during_switch_blocks_input(self):
        original = self.backend.request_ime_open
        def switch(*args):
            original(*args)
            self.backend.state["focused"] = False
        self.backend.request_ime_open = switch
        with self.assertRaises(BridgeError) as caught: self.key()
        self.assertEqual("FOCUS_LOST",caught.exception.code)
        self.assertFalse(self.backend.events)

    def test_user_layout_change_and_reused_window_are_never_overwritten(self):
        self.key()
        self.backend.layout = 0x4110411
        self.engine.detach(self.sid)
        self.assertEqual([("ime",False)],self.backend.requests)

    def test_reused_process_is_not_restored(self):
        self.key()
        self.backend.state["process_started"] = "new"
        self.engine.detach(self.sid)
        self.assertEqual([("ime",False)],self.backend.requests)

    def test_duplicate_receipt_does_not_switch_or_send_keys_again(self):
        self.key()
        events,requests = list(self.backend.events),list(self.backend.requests)
        self.assertTrue(self.key()["replayed"])
        self.assertEqual(events,self.backend.events)
        self.assertEqual(requests,self.backend.requests)


if __name__ == '__main__': unittest.main()
