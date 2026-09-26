import base64
import asyncio
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


class McpTests(unittest.IsolatedAsyncioTestCase):
    async def test_stdio_contract_images_errors_and_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            entry = Path(directory) / "fixture_server.py"
            # Exercise the real MCP server, real spawn worker and real Engine with only OS APIs replaced.
            entry.write_text('''import multiprocessing as mp
import minecraft_control_bridge.server as server_module
from minecraft_control_bridge.client import Bridge
from test_engine import FakeBackend
from functools import partial

if __name__ == "__main__":
    server_module.Bridge = partial(Bridge, _backend_factory=FakeBackend)
    server_module.create_server(__import__("sys").argv[1]).run(transport="stdio")
''', encoding="utf-8")
            env = dict(os.environ)
            env["PYTHONPATH"] = str(Path(__file__).parent.resolve())
            params = StdioServerParameters(command=sys.executable, args=[str(entry), directory], env=env)
            async with stdio_client(params) as (reader, writer):
                async with ClientSession(reader, writer) as session:
                    await session.initialize()
                    catalog = await session.list_tools()
                    names = {t.name for t in catalog.tools}
                    self.assertEqual({"mc_doctor", "mc_list_windows", "mc_attach", "mc_observe", "mc_step",
                                      "mc_read_logs", "mc_record_verification", "mc_stop", "mc_detach",
                                      "mc_jev_choose", "mc_menu_flow"}, names)
                    attached = await session.call_tool("mc_attach", {"window_id": "100"})
                    self.assertFalse(attached.isError, attached.content)
                    sid = attached.structuredContent["session_id"]
                    observed = await session.call_tool("mc_observe", {"session_id": sid})
                    images = [part for part in observed.content if part.type == "image"]
                    self.assertEqual(1, len(images))
                    self.assertTrue(base64.b64decode(images[0].data).startswith(b"\x89PNG\r\n\x1a\n"))
                    self.assertIn("frame_id", observed.structuredContent)
                    invalid = await session.call_tool("mc_step", {"session_id": sid, "action_id": "bad",
                                                                  "actions": [{"type": "hold", "keys": ["f8"]}]})
                    self.assertTrue(invalid.isError)
                    good = await session.call_tool("mc_step", {"session_id": sid, "action_id": "ok",
                                                               "actions": [{"type": "hold", "keys": ["w"], "duration_ms": 20}]})
                    self.assertEqual("completed", good.structuredContent["status"])
                    self.assertTrue(any(p.type == "image" for p in good.content))
                    running = asyncio.create_task(session.call_tool("mc_step", {"session_id": sid, "action_id": "long",
                        "actions": [{"type": "hold", "keys": ["w"], "duration_ms": 1900}]}))
                    await asyncio.sleep(0.15)
                    stopped = await session.call_tool("mc_stop", {"session_id": sid})
                    self.assertFalse(stopped.isError)
                    interrupted = await running
                    self.assertEqual("STOPPED", interrupted.structuredContent["error"]["code"])
                    self.assertLess(interrupted.structuredContent["input_elapsed_ms"], 1000)
                    detached = await session.call_tool("mc_detach", {"session_id": sid})
                    self.assertTrue(Path(detached.structuredContent["manifest"]).is_file())


if __name__ == "__main__":
    unittest.main()
