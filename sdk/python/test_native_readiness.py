"""Agent-readiness regressions: discovery, literal diagnostics and bounded inputs.

These are SDK/protocol tests, not Minecraft or installed-product acceptance.
"""
from __future__ import annotations

import copy
import json
import threading
import unittest
from unittest.mock import Mock, patch

from copperbench_native import NativeApiError, Workspace


class NativeReadinessTest(unittest.TestCase):
    def client(self, result):
        """Exercise the actual _call path while replacing only its transport."""
        client = Workspace.__new__(Workspace)
        client._closed = False
        client._lock = threading.RLock()
        client.request_timeout = 1
        client.revision = 7
        client.task_authorization_id = None
        client.close = Mock()
        requests = []
        client._send = lambda line: requests.append(json.loads(line))
        client._receive = lambda timeout: {"id": requests[-1]["id"], "result": result}
        return client, requests

    def rejected(self, message, *, code="FIELD_TYPE_INVALID", conflict=None):
        return {"status": "failed", "revision": 7, "conflict": conflict,
                "diagnostics": [{"code": code, "message": message}]}

    def environment(self, contracts):
        return {"status": "succeeded", "revision": 7, "data": {
            "generator": {"id": "fabric-1.21.1"}, "fieldContracts": contracts}}

    def rejection(self, result):
        client, requests = self.client(result)
        before = copy.deepcopy(result)
        with self.assertRaises(NativeApiError) as raised:
            client.command("create_mod_element", elementType="function", name="invalid",
                           initialValues={"commands": [1]})
        self.assertEqual(len(requests), 1, "Rejected writes must not be replayed")
        self.assertEqual(client.revision, 7)
        client.close.assert_not_called()
        self.assertIs(raised.exception.details, result)
        self.assertEqual(result, before, "Message rendering must preserve raw evidence")
        return raised.exception

    def test_named_diagnostic_arguments_are_rendered_and_preserved(self):
        message = {"key": "diagnostic.field_type_invalid", "fallback": "{field}: {reason}",
                   "args": {"field": "/commands/0", "reason": "Expected a non-null command string."}}
        error = self.rejection(self.rejected(message))
        self.assertEqual(str(error), "/commands/0: Expected a non-null command string.")
        self.assertEqual(error.code, "FIELD_TYPE_INVALID")
        self.assertEqual(error.details["diagnostics"][0]["message"], message)

    def test_replacement_is_literal_single_pass_and_unicode_safe(self):
        error = self.rejection(self.rejected({
            "fallback": "{field}: {reason}; {field}",
            "args": {"field": "铜\\仪{reason}", "reason": "$1\\n"}}))
        self.assertEqual(str(error), "铜\\仪{reason}: $1\\n; 铜\\仪{reason}")

    def test_missing_argument_stays_visible(self):
        error = self.rejection(self.rejected({"fallback": "{field}: {reason}", "args": {"field": "/x"}}))
        self.assertEqual(str(error), "/x: {reason}")

    def test_format_expressions_and_attribute_access_are_not_evaluated(self):
        error = self.rejection(self.rejected({
            "fallback": "{field.__class__} {field[0]} {field!r} {field:>99} {field}",
            "args": {"field": "safe"}}))
        self.assertEqual(str(error), "{field.__class__} {field[0]} {field!r} {field:>99} safe")

    def test_json_scalar_arguments_have_stable_display(self):
        error = self.rejection(self.rejected({
            "fallback": "{count} {enabled} {missing} {ratio}",
            "args": {"count": 0, "enabled": False, "missing": None, "ratio": 1.5}}))
        self.assertEqual(str(error), "0 false null 1.5")

    def test_structured_arguments_are_not_misrepresented_as_strings(self):
        for value in ({"secret": "not-a-display-value"}, [1, 2]):
            with self.subTest(value=value):
                error = self.rejection(self.rejected({"fallback": "{field}", "args": {"field": value}}))
                self.assertEqual(str(error), "{field}")

    def test_legacy_string_message_remains_supported(self):
        self.assertEqual(str(self.rejection(self.rejected("Readable legacy message"))), "Readable legacy message")

    def test_missing_or_invalid_message_falls_back_to_diagnostic_code(self):
        for message in (None, [], {}, "", {"fallback": None}, {"fallback": 23}):
            with self.subTest(message=message):
                self.assertEqual(str(self.rejection(self.rejected(message))), "FIELD_TYPE_INVALID")

    def test_invalid_argument_map_keeps_fallback(self):
        for arguments in (None, "bad", ["bad"]):
            with self.subTest(arguments=arguments):
                error = self.rejection(self.rejected({"fallback": "{field}", "args": arguments}))
                self.assertEqual(str(error), "{field}")

    def test_missing_diagnostic_preserves_stable_error_and_conflict(self):
        for diagnostics in ([], None, [None], "bad", {"bad": True}):
            with self.subTest(diagnostics=diagnostics):
                result = {"status": "failed", "diagnostics": diagnostics}
                self.assertEqual(self.rejection(result).code, "CORE_OPERATION_REJECTED")
                result["conflict"] = {"expectedRevision": 6, "currentRevision": 7}
                self.assertEqual(self.rejection(result).code, "REVISION_CONFLICT")

    def test_conflict_code_is_not_replaced_by_message_formatting(self):
        error = self.rejection(self.rejected({"fallback": "Expected {expected}", "args": {"expected": 6}},
                                           conflict={"currentRevision": 7}))
        self.assertEqual(error.code, "REVISION_CONFLICT")
        self.assertEqual(str(error), "Expected 6")

    def test_transport_error_also_renders_message_without_losing_envelope(self):
        client, requests = self.client({})
        response = {"error": {"code": "INVALID_PAYLOAD", "message": {
            "fallback": "Missing {field}", "args": {"field": "name"}}}}
        client._receive = lambda timeout: {"id": requests[-1]["id"], **response}
        with self.assertRaises(NativeApiError) as raised:
            client.query("get_workbench")
        self.assertEqual(str(raised.exception), "Missing name")
        self.assertEqual(raised.exception.details["error"], response["error"])
        self.assertEqual(len(requests), 1)

    def test_existing_contract_is_returned_unchanged(self):
        contract = {"fields": [{"path": "/hardness", "type": "number"}]}
        client, requests = self.client(self.environment({"block": contract}))
        self.assertIs(client.field_contract(), contract)
        self.assertEqual([(x["kind"], x["operation"]) for x in requests], [("query", "get_workspace_environment")])

    def test_available_contracts_are_sorted_without_claiming_creatable_types(self):
        client, requests = self.client(self.environment({"generic": {}, "function": {}, "block": {}}))
        self.assertEqual(client.available_field_contracts(), ("block", "function", "generic"))
        self.assertEqual(len(requests), 1)

    def test_absent_item_recipe_and_unknown_contract_return_actionable_error(self):
        for kind in ("item", "recipe", "typo_type"):
            with self.subTest(kind=kind):
                client, requests = self.client(self.environment({"generic": {}, "block": {}}))
                with self.assertRaises(NativeApiError) as raised:
                    client.field_contract(kind)
                error = raised.exception
                self.assertEqual(error.code, "FIELD_CONTRACT_UNAVAILABLE")
                self.assertEqual(error.details["elementType"], kind)
                self.assertEqual(error.details["availableContracts"], ["block", "generic"])
                self.assertEqual(error.details["generator"]["id"], "fabric-1.21.1")
                self.assertEqual(error.details["inspection"]["requires"], ["elementId"])
                self.assertIn("get_mod_element_editor", str(error))
                self.assertEqual(len(requests), 1)
                self.assertEqual(requests[0]["kind"], "query")
                client.close.assert_not_called()

    def test_empty_contract_map_is_unavailable_not_invalid(self):
        client, _ = self.client(self.environment({}))
        self.assertEqual(client.available_field_contracts(), ())
        with self.assertRaises(NativeApiError) as raised:
            client.field_contract("item")
        self.assertEqual(raised.exception.code, "FIELD_CONTRACT_UNAVAILABLE")
        self.assertIn("(none)", str(raised.exception))

    def test_future_core_item_contract_is_used_without_sdk_special_cases(self):
        contract = {"schemaVersion": "future", "fields": []}
        client, _ = self.client(self.environment({"item": contract}))
        self.assertIs(client.field_contract("item"), contract)

    def test_invalid_type_is_rejected_before_any_request(self):
        for kind in (None, [], 1, "", " \t"):
            with self.subTest(kind=kind):
                client, requests = self.client(self.environment({}))
                with self.assertRaises(ValueError):
                    client.field_contract(kind)
                self.assertEqual(requests, [])

    def test_malformed_metadata_is_not_reported_as_unsupported_feature(self):
        for data in (None, [], {}, {"fieldContracts": None}, {"fieldContracts": []},
                     {"fieldContracts": {"item": None}}, {"fieldContracts": {"": {}}}):
            with self.subTest(data=data):
                result = {"status": "succeeded", "revision": 7, "data": data}
                client, requests = self.client(result)
                with self.assertRaises(NativeApiError) as raised:
                    client.field_contract("item")
                self.assertEqual(raised.exception.code, "NATIVE_INVALID_RESPONSE")
                self.assertIs(raised.exception.details, result)
                self.assertEqual(len(requests), 1)

    def test_core_rejection_is_not_hidden_by_discovery(self):
        client, requests = self.client(self.rejected("Workspace unavailable", code="WORKSPACE_NOT_FOUND"))
        with self.assertRaises(NativeApiError) as raised:
            client.field_contract("item")
        self.assertEqual(raised.exception.code, "WORKSPACE_NOT_FOUND")
        self.assertEqual(len(requests), 1)

    def test_wait_task_rejects_nonfinite_or_nonpositive_budgets_before_polling(self):
        for parameter in ("timeout", "poll_interval"):
            for value in (0, -1, float("nan"), float("inf"), float("-inf")):
                with self.subTest(parameter=parameter, value=value):
                    client, requests = self.client({})
                    with self.assertRaises(ValueError):
                        client.wait_task("test-task", **{parameter: value})
                    self.assertEqual(requests, [])

    def test_wait_task_keeps_terminal_logs_and_does_not_cancel(self):
        result = {"status": "succeeded", "revision": 7, "data": {
            "task": {"state": "succeeded"}, "logs": [{"sequence": 1, "message": "done"}]}}
        client, requests = self.client(result)
        self.assertIs(client.wait_task("test-task", timeout=1), result)
        self.assertEqual(result["data"]["logs"][0]["sequence"], 1)
        self.assertEqual(requests[0]["operation"], "get_task")
        self.assertEqual(len(requests), 1)

    def test_wait_timeout_leaves_task_running_without_cancel_or_replay(self):
        result = {"status": "succeeded", "revision": 7, "data": {"task": {"state": "running"}, "logs": []}}
        client, requests = self.client(result)
        with patch("copperbench_native.time.monotonic", side_effect=[0, 2]):
            with self.assertRaises(NativeApiError) as raised:
                client.wait_task("test-task", timeout=1)
        self.assertEqual(raised.exception.code, "NATIVE_TASK_TIMEOUT")
        self.assertEqual(len(requests), 1)
        self.assertEqual(requests[0]["operation"], "get_task")
        client.close.assert_not_called()


if __name__ == "__main__":
    unittest.main()
