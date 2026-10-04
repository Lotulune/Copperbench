"""Exercise the production MCP client at its HTTP seam, without a live workspace."""

from __future__ import annotations

import io
from http.client import IncompleteRead
import json
from pathlib import Path
import unittest
import urllib.error
from unittest.mock import patch

from copperbench import CopperbenchClient, CopperbenchError


FIXTURES = json.loads((Path(__file__).parents[1] / "tests" / "mcp-client-reliability.json").read_text())
TRANSIENT_FAILURES = ("timeout", "connection_reset", "body_reset", "incomplete_read", "http_502", "http_503", "http_504")


class Response:
    def __init__(self, body: str = "", *, read_error: Exception | None = None, headers=None):
        self.body = body
        self.read_error = read_error
        self.headers = headers or {}

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return None

    def read(self) -> bytes:
        if self.read_error is not None:
            raise self.read_error
        return self.body.encode()


def tool_result(payload, **metadata):
    return {"content": [{"type": "text", "text": json.dumps(payload)}], **metadata}


def reply(request, result, *, sse=False, headers=None):
    body = json.dumps({"jsonrpc": "2.0", "id": json.loads(request.data)["id"], "result": result})
    return Response(f"event: message\r\ndata:{body}\r\n\r\n" if sse else body, headers=headers)


def fail(kind):
    if kind.startswith("http_"):
        raise urllib.error.HTTPError("http://127.0.0.1:61999/mcp", int(kind[5:]), "gateway failure", {}, io.BytesIO(b"gateway failure"))
    if kind == "body_reset":
        return Response(read_error=ConnectionResetError("response body interrupted"))
    if kind == "incomplete_read":
        return Response(read_error=IncompleteRead(b"partial", 12))
    if kind == "timeout":
        raise TimeoutError("response lost after request reached server")
    raise urllib.error.URLError(ConnectionResetError("connection reset after request reached server"))


class McpClientReliabilityTest(unittest.TestCase):
    def client(self, **options):
        return CopperbenchClient("http://127.0.0.1:61999/mcp", "fixture-token", "workspace",
                                 retry_backoff_seconds=0, **options)

    def assert_code(self, code, action):
        with self.assertRaises(CopperbenchError) as captured:
            action()
        self.assertEqual(code, captured.exception.code)
        return captured.exception

    def test_audited_reads_retry_transient_failures_without_changing_request(self):
        for tool in FIXTURES["readTools"]:
            for failure in TRANSIENT_FAILURES:
                with self.subTest(tool=tool["name"], failure=failure):
                    sent = []

                    def transport(request, **_):
                        sent.append(json.loads(request.data))
                        if len(sent) == 1:
                            return fail(failure)
                        return reply(request, tool_result({"status": "succeeded", "data": {"revision": 7}}))

                    with patch("urllib.request.urlopen", transport):
                        result = self.client().call_tool(tool["name"], tool["arguments"])
                    self.assertEqual("succeeded", result["status"])
                    self.assertEqual(2, len(sent))
                    self.assertEqual(sent[0], sent[1])
                    self.assertEqual(tool["arguments"], sent[0]["params"]["arguments"])

    def test_mutations_and_unreviewed_tools_are_sent_once_even_with_retry_option(self):
        for tool in FIXTURES["singleAttemptTools"]:
            for failure in TRANSIENT_FAILURES:
                with self.subTest(tool=tool["name"], failure=failure):
                    sent = []

                    def transport(request, **_):
                        sent.append(json.loads(request.data))
                        if len(sent) == 1:
                            return fail(failure)
                        return reply(request, tool_result({"status": "accepted", "task": {"id": "duplicate-task"}}))

                    with patch("urllib.request.urlopen", transport):
                        self.assert_code(f"HTTP_{failure[5:]}" if failure.startswith("http_") else "MCP_TRANSPORT_FAILED",
                                         lambda: self.client(max_transport_retries=99).call_tool(tool["name"], tool["arguments"]))
                    self.assertEqual(1, len(sent), "An uncertain write must not start a second operation")
                    self.assertEqual(tool["arguments"], sent[0]["params"]["arguments"])

    def test_retry_limit_and_disabling_retries(self):
        for configured, attempts in ((99, 3), (0, 1), (-1, 1)):
            for failure in ("timeout", "http_502"):
                with self.subTest(configured=configured, failure=failure):
                    with patch("urllib.request.urlopen", side_effect=lambda *_, **__: fail(failure)) as transport:
                        self.assert_code("HTTP_502" if failure == "http_502" else "MCP_TRANSPORT_FAILED",
                                         lambda: self.client(max_transport_retries=configured).get_workspace())
                    self.assertEqual(attempts, transport.call_count)

    def test_nonretryable_http_statuses_are_sent_once(self):
        for status in (400, 401, 403, 404, 409, 500):
            with self.subTest(status=status):
                with patch("urllib.request.urlopen", side_effect=lambda *_, **__: fail(f"http_{status}")) as transport:
                    self.assert_code(f"HTTP_{status}", self.client().get_workspace)
                self.assertEqual(1, transport.call_count)

    def test_initialization_is_not_replayed_after_an_uncertain_response(self):
        for failure in ("timeout", "http_502"):
            with self.subTest(failure=failure):
                with patch("urllib.request.urlopen", side_effect=lambda *_, **__: fail(failure)) as transport:
                    self.assert_code("HTTP_502" if failure == "http_502" else "MCP_TRANSPORT_FAILED",
                                     self.client().initialize)
                self.assertEqual(1, transport.call_count)

    def test_interrupted_http_error_body_preserves_status_and_retry_policy(self):
        class BrokenBody(io.BytesIO):
            def read(self, *_):
                raise ConnectionResetError("error body interrupted")

        for status, tool, succeeds, attempts in ((401, "get_workspace", False, 1),
                                               (403, "get_workspace", False, 1),
                                               (502, "get_workspace", True, 2),
                                               (502, "build_workspace", False, 1)):
            with self.subTest(status=status, tool=tool):
                sent = []

                def transport(request, **_):
                    sent.append(request)
                    if len(sent) == 1:
                        raise urllib.error.HTTPError("http://127.0.0.1:61999/mcp", status, "error", {}, BrokenBody())
                    return reply(request, tool_result({"status": "succeeded"}))

                with patch("urllib.request.urlopen", transport):
                    action = lambda: self.client().call_tool(tool, {})
                    if succeeds:
                        self.assertEqual("succeeded", action()["status"])
                    else:
                        self.assert_code(f"HTTP_{status}", action)
                self.assertEqual(attempts, len(sent))

    def test_tool_error_codes_and_details_survive_without_retries(self):
        for case in FIXTURES["toolErrors"]:
            with self.subTest(case=case["label"]):
                metadata = {"isError": case["isError"]} if "isError" in case else {}
                result = tool_result(case["payload"], **metadata)
                with patch("urllib.request.urlopen", side_effect=lambda request, **_: reply(request, result)) as transport:
                    error = self.assert_code(case["code"], self.client().get_workspace)
                self.assertEqual(case["payload"], error.details)
                self.assertEqual(1, transport.call_count)

    def test_malformed_tool_results_fail_closed_without_retries(self):
        for result in FIXTURES["invalidToolResults"]:
            with self.subTest(result=result):
                with patch("urllib.request.urlopen", side_effect=lambda request, **_: reply(request, result)) as transport:
                    self.assert_code("MCP_TOOL_RESULT_INVALID", self.client().get_workspace)
                self.assertEqual(1, transport.call_count)

    def test_malformed_rpc_envelopes_fail_closed_without_retries(self):
        bodies = [json.dumps(value) for value in FIXTURES["invalidEnvelopes"]]
        bodies += ["", "[]", "null", "{", "data: [1]\n\n", "data: invalid\n\n"]
        for body in bodies:
            with self.subTest(body=body):
                with patch("urllib.request.urlopen", return_value=Response(body)) as transport:
                    self.assert_code("MCP_RESPONSE_INVALID", self.client().get_workspace)
                self.assertEqual(1, transport.call_count)

    def test_json_rpc_error_codes_are_preserved_without_retries(self):
        for code in (-32602, "MCP_AUTHORIZATION_REJECTED"):
            error = {"code": code, "message": "request rejected"}
            body = json.dumps({"jsonrpc": "2.0", "id": 1, "error": error})
            with self.subTest(code=code), patch("urllib.request.urlopen", return_value=Response(body)) as transport:
                captured = self.assert_code(str(code), self.client().get_workspace)
                self.assertEqual(error, captured.details)
                self.assertEqual(1, transport.call_count)

    def test_successful_payloads_are_unchanged_in_json_and_sse(self):
        for payload in FIXTURES["successfulPayloads"]:
            for sse in (False, True):
                with self.subTest(status=payload["status"], sse=sse):
                    with patch("urllib.request.urlopen", side_effect=lambda request, **_: reply(request, tool_result(payload, isError=False), sse=sse)):
                        self.assertEqual(payload, self.client().get_workspace())

    def test_session_and_incremental_log_cursor_survive_a_read_retry(self):
        requests = []

        def transport(request, **_):
            requests.append(request)
            sent = json.loads(request.data)
            if sent["method"] == "initialize":
                return reply(request, {"protocolVersion": "2025-11-25", "capabilities": {}}, headers={"mcp-session-id": "session-1"})
            if sent["method"] == "notifications/initialized":
                return Response()
            if len(requests) == 3:
                raise TimeoutError("first task poll timed out")
            return reply(request, tool_result({"status": "succeeded", "data": {"logs": [{"sequence": 38}]}}), sse=True)

        client = self.client()
        with patch("urllib.request.urlopen", transport):
            client.initialize()
            result = client.get_task("task-1", 37)
        self.assertEqual(4, len(requests))
        self.assertEqual("session-1", requests[-1].get_header("Mcp-session-id"))
        self.assertEqual(requests[-2].data, requests[-1].data)
        self.assertEqual({"taskId": "task-1", "afterLogSequence": 37}, json.loads(requests[-1].data)["params"]["arguments"])
        self.assertEqual(38, result["data"]["logs"][0]["sequence"])


if __name__ == "__main__":
    unittest.main()
