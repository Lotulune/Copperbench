import os
import sys
import tempfile
import unittest
from contextlib import asynccontextmanager
from pathlib import Path

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


class JevMcpTests(unittest.IsolatedAsyncioTestCase):
    @asynccontextmanager
    async def connect(self, key=None):
        with tempfile.TemporaryDirectory() as directory:
            entry = Path(directory) / "jev_server.py"
            entry.write_text('''
from contextlib import contextmanager
import json
import httpx
import minecraft_control_bridge.server as server_module
from minecraft_control_bridge.jev import JevClient

def forbidden_desktop(*args, **kwargs):
    raise AssertionError("Jev must never initialize desktop control")

def respond(request):
    assert request.headers["Authorization"] == "Bearer test-only-secret"
    body = json.loads(request.content)
    if body["state"].get("force_http_error"):
        return httpx.Response(401, json={"message": "test-only-secret"})
    assert body["state"]["frame_id"] == "frame-123"
    assert set(body["questions"]["action"]["criteria"]) == {"USE", "WAIT"}
    return httpx.Response(200, json={"model": "jev-test", "answers": {"action": {
        "type": "choice", "choice": "USE", "confidence": 0.9,
        "probabilities": {"USE": 0.9, "WAIT": 0.1}}}})

@contextmanager
def mocked_jev():
    with httpx.Client(transport=httpx.MockTransport(respond)) as http:
        with JevClient(_http_client=http) as client:
            yield client

if __name__ == "__main__":
    server_module.Bridge = forbidden_desktop
    server_module.JevClient = mocked_jev
    server_module.create_server().run(transport="stdio")
''', encoding="utf-8")
            env = dict(os.environ)
            env.pop("TYPESAFE_API_KEY", None)
            if key:
                env["TYPESAFE_API_KEY"] = key
            params = StdioServerParameters(command=sys.executable, args=[str(entry)], env=env)
            async with stdio_client(params) as (reader, writer):
                async with ClientSession(reader, writer) as session:
                    await session.initialize()
                    yield session

    def arguments(self, **state):
        return {"state": {"frame_id": "frame-123", "source": "planner_visual_summary", **state},
                "goal": "Insert one copper ingot", "candidates": {"USE": "Right-click", "WAIT": "Wait"}}

    async def test_choice_provenance_and_errors_without_desktop(self):
        async with self.connect("test-only-secret") as session:
            result = await session.call_tool("mc_jev_choose", self.arguments())
            self.assertFalse(result.isError, result.content)
            self.assertEqual("USE", result.structuredContent["choice"])
            self.assertFalse(result.structuredContent["executed"])
            self.assertEqual("frame-123", result.structuredContent["frame_id"])
            self.assertEqual("caller_supplied_text", result.structuredContent["observation_source"])
            self.assertNotIn("test-only-secret", str(result))

            for arguments in (self.arguments(frame_id=""), self.arguments(frame_id=None),
                              {**self.arguments(), "candidates": {"USE": "Only one"}}):
                invalid = await session.call_tool("mc_jev_choose", arguments)
                self.assertTrue(invalid.isError)
                self.assertEqual("JEV_INVALID_REQUEST", invalid.structuredContent["error"]["code"])

            denied = await session.call_tool("mc_jev_choose", self.arguments(force_http_error=True))
            self.assertTrue(denied.isError)
            self.assertEqual("JEV_HTTP_ERROR", denied.structuredContent["error"]["code"])
            self.assertNotIn("test-only-secret", str(denied))

    async def test_missing_key_is_reported_through_mcp(self):
        async with self.connect() as session:
            result = await session.call_tool("mc_jev_choose", self.arguments())
            self.assertTrue(result.isError)
            self.assertEqual("JEV_KEY_REQUIRED", result.structuredContent["error"]["code"])


if __name__ == "__main__":
    unittest.main()
