"""Transport-level checks for one-query discovery; not product/gameplay acceptance."""
from __future__ import annotations

import copy
import json
import threading
import unittest
from unittest.mock import Mock

from copperbench_native import NativeApiError, Workspace

OPERATION = "get_mod_element_field_contract"


def contract(element_type="item", availability="available", generator="fabric-1.21.1"):
    available = availability == "available"
    return {"elementType": element_type, "generatorId": generator, "contractVersion": "1",
            "availability": availability, "complete": available,
            "fields": [{"path": "/stackSize", "inputSchema": {"type": "integer"}}] if available else [],
            "alternatives": [], "minimalExample": {"elementType": element_type, "name": "example_item"}}


def environment(discovery=True):
    data = {"generator": {"id": "fabric-1.21.1"}, "fieldContracts": {"item": contract()}}
    if discovery:
        data["fieldContractDiscovery"] = OPERATION
    return data


class DiscoveryEfficiencyTest(unittest.TestCase):
    def client(self, data=None, operations=(OPERATION,)):
        client = Workspace.__new__(Workspace)
        client._closed = False
        client._lock = threading.RLock()
        client.request_timeout = 1
        client.revision = 7
        client.task_authorization_id = None
        client._task_authorized_operations = frozenset()
        if operations is not None:
            client.operations = operations
        client.close = Mock()
        requests = []
        client._send = lambda line: requests.append(json.loads(line))
        client._receive = lambda timeout: {"id": requests[-1]["id"], "result": {
            "status": "succeeded", "revision": 8,
            "data": environment() if requests[-1]["operation"] == "get_workspace_environment" else
                    (contract() if data is None else data)}}
        return client, requests

    def test_advertised_discovery_is_exactly_one_query(self):
        for element_type in ("item", "recipe"):
            with self.subTest(element_type=element_type):
                data = contract(element_type)
                client, requests = self.client(data)
                self.assertEqual(data, client.discover_field_contract(element_type))
                self.assertEqual([(r["kind"], r["operation"], r["payload"]) for r in requests],
                                 [("query", OPERATION, {"elementType": element_type})])
                self.assertNotIn("expectedRevision", requests[0])
                self.assertEqual(8, client.revision)
                client.close.assert_not_called()

    def test_all_three_states_use_the_direct_query(self):
        for availability in ("available", "not_exposed", "unsupported"):
            with self.subTest(availability=availability):
                data = contract(availability=availability)
                client, requests = self.client(data)
                self.assertEqual(data, client.discover_field_contract("item"))
                self.assertEqual([OPERATION], [r["operation"] for r in requests])

    def test_direct_query_does_not_touch_full_environment(self):
        client, _ = self.client()
        client._field_contract_environment = Mock(side_effect=AssertionError("Expensive environment must not be fetched"))
        self.assertEqual("available", client.discover_field_contract("item")["availability"])
        client._field_contract_environment.assert_not_called()

    def test_new_values_and_generator_are_observed_without_contract_cache(self):
        data = contract()
        client, requests = self.client(data)
        first = copy.deepcopy(client.discover_field_contract("item"))
        data["generatorId"] = "neoforge-1.21.1"
        data["fields"][0]["inputSchema"]["maximum"] = 32
        current = client.discover_field_contract("item")
        self.assertNotEqual(first, current)
        self.assertEqual("neoforge-1.21.1", current["generatorId"])
        self.assertEqual(32, current["fields"][0]["inputSchema"]["maximum"])
        self.assertEqual([OPERATION, OPERATION], [r["operation"] for r in requests])

    def test_core_rejection_is_not_retried_or_hidden_by_environment_fallback(self):
        client, requests = self.client()
        result = {"status": "failed", "diagnostics": [{"code": "WORKSPACE_NOT_FOUND", "message": "Gone"}]}
        client._receive = lambda _: {"id": requests[-1]["id"], "result": result}
        with self.assertRaises(NativeApiError) as raised:
            client.discover_field_contract("item")
        self.assertEqual("WORKSPACE_NOT_FOUND", raised.exception.code)
        self.assertIs(result, raised.exception.details)
        self.assertEqual([OPERATION], [r["operation"] for r in requests])
        client.close.assert_not_called()

    def test_transport_failure_is_not_retried(self):
        client, requests = self.client()
        client._receive = Mock(side_effect=NativeApiError("timed out", "NATIVE_TIMEOUT"))
        with self.assertRaises(NativeApiError) as raised:
            client.discover_field_contract("item")
        self.assertEqual("NATIVE_TIMEOUT", raised.exception.code)
        self.assertEqual([OPERATION], [r["operation"] for r in requests])
        client.close.assert_called_once()

    def test_malformed_direct_contract_still_uses_existing_validation(self):
        bad = [[], {}, {**contract(), "elementType": "recipe"}, {**contract(), "complete": False},
               {**contract(), "generatorId": None}, {**contract(), "contractVersion": "999"},
               {**contract(), "fields": []}, {**contract(), "fields": [{"path": "not-a-pointer"}]}]
        for data in bad:
            with self.subTest(data=data):
                client, requests = self.client(data)
                with self.assertRaises(NativeApiError) as raised:
                    client.discover_field_contract("item")
                self.assertEqual("NATIVE_INVALID_RESPONSE", raised.exception.code)
                self.assertEqual([OPERATION], [r["operation"] for r in requests])
                client.close.assert_not_called()

    def test_absent_or_older_catalog_preserves_environment_negotiation(self):
        for operations in (None, (), ("get_workspace_environment",)):
            with self.subTest(operations=operations):
                client, requests = self.client(operations=operations)
                self.assertEqual(contract(), client.discover_field_contract("item"))
                self.assertEqual(["get_workspace_environment", OPERATION], [r["operation"] for r in requests])

    def test_old_core_not_exposed_is_explicit_without_probe(self):
        client, requests = self.client(operations=())
        client._receive = lambda _: {"id": requests[-1]["id"], "result": {
            "status": "succeeded", "revision": 7, "data": environment(discovery=False)}}
        data = client.discover_field_contract("item")
        self.assertFalse(data["complete"])
        self.assertEqual("FIELD_CONTRACT_DISCOVERY_UNAVAILABLE", data["reasonCode"])
        self.assertEqual(["get_workspace_environment"], [r["operation"] for r in requests])

    def test_invalid_legacy_operation_is_not_dispatched(self):
        client, requests = self.client(operations=())
        data = environment()
        data["fieldContractDiscovery"] = "untrusted_other_operation"
        result = {"status": "succeeded", "revision": 7, "data": data}
        client._receive = lambda _: {"id": requests[-1]["id"], "result": result}
        with self.assertRaises(NativeApiError) as raised:
            client.discover_field_contract("item")
        self.assertEqual("NATIVE_INVALID_RESPONSE", raised.exception.code)
        self.assertIs(result, raised.exception.details)
        self.assertEqual(["get_workspace_environment"], [r["operation"] for r in requests])
        client.close.assert_not_called()

    def test_legacy_field_contract_shape_and_query_are_unchanged(self):
        client, requests = self.client()
        self.assertEqual(contract(), client.field_contract("item"))
        self.assertEqual(["get_workspace_environment"], [r["operation"] for r in requests])

    def test_invalid_input_is_rejected_before_any_query(self):
        for element_type in (None, [], 1, "", " \t"):
            with self.subTest(element_type=element_type):
                client, requests = self.client()
                with self.assertRaises(ValueError):
                    client.discover_field_contract(element_type)
                self.assertEqual([], requests)

    def test_new_session_uses_its_own_catalog(self):
        modern, modern_requests = self.client()
        legacy, legacy_requests = self.client(operations=())
        modern.discover_field_contract("item")
        legacy.discover_field_contract("item")
        self.assertEqual([OPERATION], [r["operation"] for r in modern_requests])
        self.assertEqual(["get_workspace_environment", OPERATION], [r["operation"] for r in legacy_requests])

    def test_following_write_uses_latest_revision_without_replay(self):
        client, requests = self.client()
        client.discover_field_contract("item")
        client._receive = lambda _: {"id": requests[-1]["id"], "result": {"status": "committed", "newRevision": 9}}
        client.create_mod_element(elementType="item", name="new_item")
        self.assertEqual([OPERATION, "create_mod_element"], [r["operation"] for r in requests])
        self.assertEqual(8, requests[-1]["expectedRevision"])
        self.assertEqual(9, client.revision)


if __name__ == "__main__":
    unittest.main()
