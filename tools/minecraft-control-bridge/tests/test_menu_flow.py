import asyncio
import copy
import hashlib
import io
import json
import os
import sys
import tempfile
import threading
import unittest
from pathlib import Path

from PIL import Image, ImageDraw
from test_engine import FakeBackend
from minecraft_control_bridge.engine import Engine
from minecraft_control_bridge.errors import BridgeError
from minecraft_control_bridge.menu_flow import MenuProfile
from minecraft_control_bridge.menu_profile import build_profile


BOXES = {
    "title": {"singleplayer": [100,80,180,25], "quit": [100,130,180,25]},
    "world_list": {"heading": [300,10,180,25], "create": [400,430,180,25]},
    "pause": {"heading": [300,60,180,25], "save": [300,150,180,25]},
    "playing": {"hud_left": [240,435,100,25], "hud_right": [440,435,100,25]},
}
ENTRY = [225,105,230,20]


def pictures():
    result = {}
    for state, roles in BOXES.items():
        image = Image.new("RGB", (854,480), (20,30,40))
        draw = ImageDraw.Draw(image)
        for name, (x,y,w,h) in roles.items():
            draw.text((x+3,y+4), state + " " + name, fill="white")
        if state == "world_list":
            draw.text((ENTRY[0]+3,ENTRY[1]+4), "PRD17 Scene C v7", fill="white")
        result[state] = image
    result["unknown"] = Image.new("RGB", (854,480), "black")
    return result


def fixture_profile(root):
    captures = root / "captures"
    captures.mkdir()
    states = {}
    for state, image in pictures().items():
        if state == "unknown":
            continue
        path = captures / (state + ".png")
        image.save(path)
        states[state] = {"source": str(path), "markers": {name: {"box": box} for name,box in BOXES[state].items()}}
        if state == "playing":
            states[state]["title_suffix"] = " - Singleplayer"
    spec = {"world_name":"PRD17 Scene C v7", "capture_size":[854,480], "states":states,
            "world_search_box":[225,45,400,35], "world_entry":{"box":ENTRY}, "empty_below_entry":[225,176,450,175]}
    return build_profile(spec, root / "profile")["profile_path"]


class MenuBackend(FakeBackend):
    def __init__(self):
        super().__init__()
        self.state["rect"].update(width=854,height=480)
        self.screen = "title"
        self.pictures = pictures()
        self.cursor = (0,0)
        self.selected = False
        self.search_text = ""
        self.frozen = False
        self.closed = False

    def window(self, window_id):
        if self.closed:
            raise BridgeError("WINDOW_UNAVAILABLE", "Fixture window closed")
        result = super().window(window_id)
        result["title"] = "Minecraft fixture" + (" - Singleplayer" if self.screen in {"playing","pause"} else "")
        return result

    def capture(self, window, max_width):
        output = io.BytesIO()
        self.pictures[self.screen].save(output, format="PNG")
        return output.getvalue(), 854, 480

    def move_absolute(self, x, y):
        super().move_absolute(x,y)
        self.cursor = (x-self.state["rect"]["left"], y-self.state["rect"]["top"])

    def button(self, name, down):
        super().button(name,down)
        if down or name != "left" or self.frozen:
            return
        x,y = self.cursor
        if self.screen == "title":
            if 80 <= y < 105:
                self.screen = "world_list"
            elif 130 <= y < 155:
                self.closed = True
        elif self.screen == "world_list" and 105 <= y < 125:
            self.selected = True
        elif self.screen == "pause" and 150 <= y < 175:
            self.screen = "title"

    def key(self, name, down):
        super().key(name,down)
        if not down or self.frozen:
            return
        if name == "enter" and self.screen == "world_list" and self.selected:
            self.screen = "playing"
        elif name == "escape" and self.screen == "playing":
            self.screen = "pause"

    def type_character(self, character):
        super().type_character(character)
        self.search_text += character


class MenuFlowTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.profile = fixture_profile(self.root)
        self.backend = MenuBackend()
        self.engine = Engine(self.backend)
        self.sid = self.engine.attach("100", self.root / "evidence")["session_id"]

    def tearDown(self):
        self.engine.close()
        self.tmp.cleanup()

    def flow(self, kind="enter_world", action_id="flow-1", **options):
        return self.engine.menu_flow(self.sid, action_id, kind, self.profile,
                                     world_name="PRD17 Scene C v7" if kind == "enter_world" else None, **options)

    def test_enter_and_save_quit_with_no_intermediate_planner_calls(self):
        entered = self.flow()
        self.assertEqual("completed", entered["status"], entered)
        self.assertEqual("world_visible", entered["terminal"])
        self.assertEqual(3, len(entered["steps"]))
        self.assertEqual("PRD17 Scene C v7", self.backend.search_text)
        self.assertFalse(entered["gameplay_verified"])
        closed = self.flow("save_and_quit", "close")
        self.assertEqual("completed", closed["status"], closed)
        self.assertEqual("window_closed_unverified", closed["terminal"])
        self.assertTrue(closed["observation_is_pre_close"])
        self.assertEqual(3, len(closed["steps"]))
        self.assertFalse(self.backend.keys | self.backend.buttons)

    def test_unknown_screen_never_receives_input(self):
        self.backend.screen = "unknown"
        result = self.flow()
        self.assertEqual("blocked", result["status"])
        self.assertEqual([], self.backend.events)
        self.assertIn("observation", result)

    def test_world_name_request_mismatch_rejected_before_capture_or_input(self):
        with self.assertRaises(BridgeError) as error:
            self.engine.menu_flow(self.sid,"wrong","enter_world",self.profile,world_name="Other")
        self.assertEqual("MENU_WORLD_MISMATCH", error.exception.code)
        self.assertEqual([], self.backend.events)

    def test_duplicate_result_does_not_enter_any_world(self):
        ImageDraw.Draw(self.backend.pictures["world_list"]).text((228,184), "PRD17 Scene C v7", fill="white")
        result = self.flow()
        self.assertEqual("MENU_WORLD_AMBIGUOUS", result["error"]["code"])
        self.assertFalse(self.backend.selected)
        self.assertEqual("world_list", self.backend.screen)

    def test_changed_world_name_does_not_enter(self):
        image = self.backend.pictures["world_list"]
        draw = ImageDraw.Draw(image)
        x,y,w,h = ENTRY
        draw.rectangle((x,y,x+w,y+h), fill=(20,30,40))
        draw.text((x+3,y+4), "PRD17 Scene C v8", fill="white")
        result = self.flow()
        self.assertEqual("MENU_WORLD_NOT_MATCHED", result["error"]["code"])
        self.assertFalse(self.backend.selected)

    def test_transition_timeout_does_not_repeat_click(self):
        self.backend.frozen = True
        result = self.flow(timeout_ms=3000)
        self.assertEqual("blocked", result["status"])
        self.assertIn(result["error"]["code"], {"MENU_FLOW_TIMEOUT", "ACTION_TIMEOUT"})
        self.assertEqual(1, self.backend.events.count(("button","left",True)))

    def test_duplicate_flow_returns_original_receipt_without_input(self):
        first = self.flow()
        count = len(self.backend.events)
        second = self.flow()
        self.assertTrue(second["replayed"])
        self.assertEqual(first["observation"]["frame_id"], second["observation"]["frame_id"])
        self.assertEqual(count, len(self.backend.events))
        with self.assertRaises(BridgeError) as error:
            self.flow("save_to_title")
        self.assertEqual("ACTION_ID_CONFLICT", error.exception.code)

    def test_focus_loss_aborts_without_next_transition(self):
        old = self.backend.button
        def button(name,down):
            old(name,down)
            if down:
                self.backend.state["focused"] = False
        self.backend.button = button
        result = self.flow()
        self.assertEqual("FOCUS_LOST", result["error"]["code"])
        self.assertFalse(self.backend.keys | self.backend.buttons)
        self.assertEqual(1,self.backend.events.count(("button","left",True)))

    def test_f8_interrupts_local_flow_and_releases(self):
        self.backend.on_key = lambda: setattr(self.backend,"emergency",True)
        self.backend.screen = "playing"
        result = self.flow("save_and_quit")
        self.assertEqual("STOPPED",result["error"]["code"])
        self.assertFalse(self.backend.keys | self.backend.buttons)
        self.assertFalse(self.backend.closed)

    def test_wrong_capture_size_stops_before_input(self):
        self.backend.pictures["title"] = self.backend.pictures["title"].resize((640,360))
        result = self.flow()
        self.assertEqual("MENU_PROFILE_GEOMETRY",result["error"]["code"])
        self.assertEqual([],self.backend.events)

    def test_changed_template_hash_is_rejected(self):
        path = Path(self.profile).parent / "title-singleplayer.png"
        path.write_bytes(path.read_bytes()+b"changed")
        with self.assertRaises(BridgeError) as error:
            self.flow()
        self.assertEqual("INVALID_MENU_PROFILE",error.exception.code)
        self.assertEqual([],self.backend.events)


class MenuMcpTests(unittest.IsolatedAsyncioTestCase):
    async def test_one_rpc_per_navigation_and_concurrent_stop(self):
        from mcp import ClientSession, StdioServerParameters
        from mcp.client.stdio import stdio_client
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            profile=fixture_profile(root)
            entry=root/'server.py'
            entry.write_text('''from functools import partial
import minecraft_control_bridge.server as server
from minecraft_control_bridge.client import Bridge
from test_menu_flow import MenuBackend
server.Bridge = partial(Bridge, _backend_factory=MenuBackend)
server.create_server(__import__('sys').argv[1]).run(transport='stdio')
''',encoding='utf-8')
            env=dict(os.environ,PYTHONPATH=str(Path(__file__).parent.resolve()))
            async with stdio_client(StdioServerParameters(command=sys.executable,args=[str(entry),directory],env=env)) as (reader,writer):
                async with ClientSession(reader,writer) as session:
                    await session.initialize()
                    a=await session.call_tool('mc_attach',{'window_id':'100'})
                    sid=a.structuredContent['session_id']
                    entered=await session.call_tool('mc_menu_flow',{'session_id':sid,'action_id':'enter','flow':'enter_world','profile_path':profile,'world_name':'PRD17 Scene C v7'})
                    self.assertEqual('world_visible',entered.structuredContent.get('terminal'),entered)
                    self.assertTrue(any(c.type=='image' for c in entered.content))
                    self.assertEqual(3,len(entered.structuredContent['steps']))
                    running=asyncio.create_task(session.call_tool('mc_menu_flow',{'session_id':sid,'action_id':'save','flow':'save_and_quit','profile_path':profile}))
                    await asyncio.sleep(.08)
                    await session.call_tool('mc_stop',{'session_id':sid})
                    interrupted=await running
                    self.assertEqual('STOPPED',interrupted.structuredContent['error']['code'])
                    await session.call_tool('mc_detach',{'session_id':sid})


if __name__ == '__main__':
    unittest.main()
