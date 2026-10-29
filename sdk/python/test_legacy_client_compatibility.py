"""Run an actual fixed old SDK at its HTTP seam, not an import-only surrogate.

The controlled transport checks current public calling shapes and Core envelopes.
It does not execute Java Core, issue authority, build/export real artifacts, or
prove desktop/gameplay behavior. The fixture's exact Git blob is retained offline
so a shallow CI checkout does not fetch or substitute the current client.
"""
from __future__ import annotations

import builtins
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

import copperbench as current
from test_mcp_client import Response, reply, tool_result


LEGACY_COMMIT = "b1a69d244ab7337608cc302826ed55d4a707f37f"
LEGACY_BLOB = "af93d57f2f303e2ccdb38e9c7e19bd272cbfa25c"
LEGACY_SHA256 = "c6b59563c24dcc54d7fa3e3bf17ba353a5de5b7ac917fd32f221fe55ac170c38"
FIXTURE_ROOT = Path(__file__).parents[1] / "tests" / "legacy-clients"


ORIGINAL_IMPORT = builtins.__import__


def isolated_sdk_import(name, *args, **kwargs):
    if name in {"copperbench_native", "copperbench_scripting", "copperbench_console"}:
        raise AssertionError("The fixed MCP client must not borrow current Native or scripting helpers")
    return ORIGINAL_IMPORT(name, *args, **kwargs)


def core_read(revision, data):
    return {"messageType": "query_result", "schemaVersion": "1.0", "workspaceId": "workspace-fixture",
            "status": "succeeded", "revision": revision, "data": data, "diagnostics": []}


def task(identifier, kind, state):
    return {"id": identifier, "kind": kind, "state": state, "cancellable": state == "running", "progress": None}


class FixedLegacyClientCompatibilityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((FIXTURE_ROOT / "manifest.json").read_text(encoding="utf-8"))
        cls.fixture_path = FIXTURE_ROOT / "python-b1a69d244ab7.py"
        source = cls.fixture_path.read_bytes()
        if (cls.manifest["commit"] != LEGACY_COMMIT or cls.manifest["sourcePath"] != "sdk/python/copperbench.py"
                or cls.manifest["fixtureFile"] != cls.fixture_path.name
                or cls.manifest["sha256"] != LEGACY_SHA256
                or hashlib.sha256(source).hexdigest() != LEGACY_SHA256
                or cls.manifest["gitBlobSha1"] != LEGACY_BLOB
                or hashlib.sha1(b"blob " + str(len(source)).encode("ascii") + b"\0" + source).hexdigest() != LEGACY_BLOB
                or cls.manifest["bytes"] != len(source)):
            raise AssertionError("The legacy fixture no longer matches its fixed source Git blob")
        spec = importlib.util.spec_from_file_location("copperbench_legacy_b1a69d244ab7", cls.fixture_path)
        cls.legacy = importlib.util.module_from_spec(spec)
        with patch("builtins.__import__", isolated_sdk_import):
            spec.loader.exec_module(cls.legacy)

    def test_fixture_is_an_older_implementation_with_its_original_surface(self):
        self.assertEqual(self.legacy.CopperbenchClient.__module__, "copperbench_legacy_b1a69d244ab7")
        self.assertFalse(hasattr(self.legacy, "ClientMetrics"))
        self.assertFalse(hasattr(self.legacy.CopperbenchClient, "wait_task"))
        self.assertTrue(hasattr(current.CopperbenchClient, "wait_task"))
        self.assertNotEqual(self.fixture_path.read_bytes(), Path(current.__file__).read_bytes())

    def exercise_common_route(self, module):
        initial = core_read(7, {"name": "Legacy fixture"})
        environment = core_read(7, {"generator": {"id": "fabric-1.21.1"},
            "fieldContracts": {"block": {"contractVersion": 1, "supported": True}}})
        first_page = core_read(7, {"items": [{"id": "element-1", "name": "legacy_one"}], "nextCursor": "fixture-page-2"})
        second_page = core_read(7, {"items": [{"id": "element-2", "name": "legacy_two"}], "nextCursor": None})
        editor = core_read(7, {"element": {"id": "element-1", "name": "legacy_one", "ownership": "generated"},
            "sections": [{"fields": [{"path": "/displayName", "value": "Legacy 铜铃"}]}]})
        created = {"status": "committed", "newRevision": 8, "data": {"elementId": "created-1"}, "diagnostics": []}
        updated = {"status": "committed", "newRevision": 9, "data": {"elementId": "created-1"}, "diagnostics": []}
        build_start = {"status": "accepted", "newRevision": 9, "task": task("build-1", "build", "running"), "diagnostics": []}
        running = core_read(9, {"task": task("build-1", "build", "running"),
            "logs": [{"sequence": 38, "text": "first observed increment"}], "diagnostics": []})
        finished = core_read(9, {"task": task("build-1", "build", "succeeded"),
            "logs": [{"sequence": 39, "text": "BUILD SUCCESSFUL"}], "diagnostics": []})
        export_start = {"status": "accepted", "newRevision": 9, "task": task("export-1", "export", "running"), "diagnostics": []}
        export_task = task("export-1", "export", "succeeded")
        export_task["verifiedExport"] = {"taskId": "acceptance-fixture", "status": "passed_current_input",
            "artifactSha256": "a" * 64, "reportSha256": "b" * 64, "sourceSha256": "c" * 64,
            "scope": "Protocol fixture only; no accepted artifact bytes exist."}
        exported = core_read(9, {"task": export_task, "logs": [], "diagnostics": []})
        query = {"limit": 1, "search": "legacy", "fields": ["id", "name"]}
        create_args = {"elementType": "block", "name": "legacy_new", "initialValues": {"displayName": "Legacy 铜铃", "hardness": 3},
                       "expectedRevision": 7}
        update_args = {"elementId": "created-1", "changes": [{"path": "/displayName", "value": "Updated"}], "expectedRevision": 8}
        export_args = {"verifiedTaskId": "acceptance-fixture", "expectedRevision": 9, "allowHistorical": False}
        steps = [
            ("initialize", {"protocolVersion": "2025-11-25", "capabilities": {},
                "clientInfo": {"name": "copperbench-python-sdk", "version": "0.1.0"}},
                {"protocolVersion": "2025-11-25", "capabilities": {"tools": {}}, "serverInfo": {"name": "fixture", "version": "1"}}),
            ("notifications/initialized", {}, None),
        ]
        for operation, arguments, payload in (
            ("get_workspace", {}, initial), ("get_workspace_environment", {}, environment),
            ("list_mod_elements", query, first_page),
            ("list_mod_elements", {**query, "cursor": "fixture-page-2"}, second_page),
            ("read_mod_element", {"elementId": "element-1"}, editor),
            ("create_mod_element", {**create_args, "taskAuthorizationId": "fixture-grant"}, created),
            ("get_workspace", {}, core_read(8, {"name": "Legacy fixture"})),
            ("update_mod_element", {**update_args, "taskAuthorizationId": "fixture-grant"}, updated),
            ("build_workspace", {"expectedRevision": 9, "taskAuthorizationId": "fixture-grant"}, build_start),
            ("get_task", {"taskId": "build-1", "afterLogSequence": 37}, running),
            ("get_task", {"taskId": "build-1", "afterLogSequence": 38}, finished),
            ("export_verified_artifact", export_args, export_start),
            ("get_task", {"taskId": "export-1", "afterLogSequence": 0}, exported),
        ):
            steps.append(("tools/call", {"name": operation, "arguments": arguments}, tool_result(payload)))
        requests = []
        def transport(request, **_):
            body = json.loads(request.data)
            index = len(requests)
            self.assertLess(index, len(steps), "The client sent an unexpected extra operation")
            method, params, result = steps[index]
            self.assertEqual((body["method"], body["params"]), (method, params))
            self.assertEqual(request.get_header("Authorization"), "Bearer fixture-token")
            self.assertEqual(request.get_header("X-copperbench-workspace"), "workspace-fixture")
            self.assertEqual(request.get_header("Mcp-session-id"), None if index == 0 else "fixture-session")
            requests.append(body)
            return Response() if result is None else reply(request, result, headers={"mcp-session-id": "fixture-session"})
        client = module.CopperbenchClient("http://127.0.0.1:61999/mcp", "fixture-token", "workspace-fixture",
                                         task_authorization_id="fixture-grant", retry_backoff_seconds=0)
        with patch("urllib.request.urlopen", transport), patch("builtins.__import__", isolated_sdk_import):
            client.initialize()
            self.assertEqual(client.get_workspace(), initial)
            self.assertEqual(client.call_tool("get_workspace_environment", {}), environment)
            self.assertEqual(list(client.list_mod_elements(**query)), first_page["data"]["items"] + second_page["data"]["items"])
            self.assertEqual(client.call_tool("read_mod_element", {"elementId": "element-1"}), editor)
            self.assertEqual(client.create_mod_element(**create_args), created)
            self.assertEqual(client.get_workspace()["revision"], 8)
            self.assertEqual(client.update_mod_element(**update_args), updated)
            self.assertEqual(client.build_workspace(9), build_start)
            first_poll = client.get_task("build-1", 37)
            final_poll = client.get_task(first_poll["data"]["task"]["id"], first_poll["data"]["logs"][-1]["sequence"])
            self.assertEqual((first_poll, final_poll), (running, finished))
            self.assertEqual(client.call_tool("export_verified_artifact", export_args), export_start)
            self.assertEqual(client.get_task("export-1"), exported)
        self.assertEqual(len(requests), len(steps))
        operations = [request["params"]["name"] for request in requests if request["method"] == "tools/call"]
        self.assertEqual(operations.count("build_workspace"), 1)
        self.assertEqual(operations.count("export_verified_artifact"), 1)
        return [request["params"] for request in requests]

    def test_old_and_current_clients_preserve_common_public_calls_and_core_envelopes(self):
        self.assertEqual(self.exercise_common_route(self.legacy), self.exercise_common_route(current))

    def test_incompatible_protocol_outcome_retains_the_fixed_old_boundary(self):
        response = {"protocolVersion": "2024-11-05", "capabilities": {}}
        for module in (self.legacy, current):
            with self.subTest(client=module.__name__):
                requests = []
                def transport(request, **_):
                    body = json.loads(request.data)
                    requests.append(body)
                    return Response() if body["method"] == "notifications/initialized" else reply(request, response)
                client = module.CopperbenchClient("http://127.0.0.1:61999/mcp", "fixture-token", "workspace-fixture")
                with patch("urllib.request.urlopen", transport), patch("builtins.__import__", isolated_sdk_import):
                    if module is current:
                        with self.assertRaises(module.CopperbenchError) as captured:
                            client.initialize()
                        self.assertEqual(captured.exception.code, "MCP_PROTOCOL_INCOMPATIBLE")
                        self.assertEqual(captured.exception.details, response)
                        self.assertEqual([request["method"] for request in requests], ["initialize"])
                    else:
                        # The immutable old code did not validate the selected version.
                        # Record its boundary; do not credit it with the new fail-closed helper.
                        self.assertEqual(client.initialize(), response)
                        self.assertEqual([request["method"] for request in requests],
                                         ["initialize", "notifications/initialized"])

    def test_old_and_current_clients_retain_raw_current_host_rejections(self):
        for module in (self.legacy, current):
            for code in ("WORKSPACE_REVISION_CONFLICT", "TASK_AUTHORIZATION_REVOKED", "VERIFICATION_INPUT_CHANGED"):
                with self.subTest(client=module.__name__, code=code):
                    payload = {"status": "rejected", "diagnostics": [{"code": code, "path": "/fixture", "severity": "error"}],
                        "conflict": {"expectedRevision": 7, "actualRevision": 8} if code == "WORKSPACE_REVISION_CONFLICT" else None}
                    client = module.CopperbenchClient("http://127.0.0.1:61999/mcp", "fixture-token", "workspace-fixture")
                    with patch("urllib.request.urlopen", side_effect=lambda request, **_: reply(request, tool_result(payload), sse=True)) as transport:
                        with self.assertRaises(module.CopperbenchError) as captured:
                            client.call_tool("update_mod_element", {"expectedRevision": 7})
                    self.assertEqual(captured.exception.code, code)
                    self.assertEqual(captured.exception.details, payload)
                    self.assertEqual(transport.call_count, 1)

    def test_old_and_current_clients_do_not_replay_an_uncertain_build_start(self):
        for module in (self.legacy, current):
            with self.subTest(client=module.__name__):
                client = module.CopperbenchClient("http://127.0.0.1:61999/mcp", "fixture-token", "workspace-fixture",
                                                 max_transport_retries=2, retry_backoff_seconds=0)
                with patch("urllib.request.urlopen", side_effect=TimeoutError("Build response lost after submission")) as transport:
                    with self.assertRaises(module.CopperbenchError) as captured:
                        client.build_workspace(9)
                self.assertEqual(captured.exception.code, "MCP_TRANSPORT_FAILED")
                self.assertEqual(transport.call_count, 1)


if __name__ == "__main__":
    unittest.main()
