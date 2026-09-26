import copy
import json
import unittest

import httpx

from minecraft_control_bridge.errors import BridgeError
from minecraft_control_bridge.jev import ENDPOINT, JevClient


class JevTests(unittest.TestCase):
    def setUp(self):
        self.response = {"model": "jev-test", "answers": {"action": {"type": "choice", "choice": "USE",
            "confidence": 0.9, "probabilities": {"USE": 0.95, "WAIT": 0.05}}}, "usage": {"input_tokens": 20}}
        self.status = 200
        self.requests = []
        def transport(request):
            self.requests.append(request)
            return httpx.Response(self.status, json=self.response)
        self.http = httpx.Client(transport=httpx.MockTransport(transport))
        self.client = JevClient("test-only-secret", _http_client=self.http)

    def tearDown(self):
        self.client.close()
        self.http.close()

    def choose(self):
        return self.client.choose({"focused": True, "source": "planner_visual_summary"},
                                  "Insert a copper ingot", {"USE": "Right-click", "WAIT": "Wait"})

    def test_valid_choice_is_not_execution(self):
        result = self.choose()
        self.assertEqual("USE", result["choice"])
        self.assertFalse(result["executed"])
        self.assertEqual(ENDPOINT, str(self.requests[0].url))
        self.assertEqual("Bearer test-only-secret", self.requests[0].headers["Authorization"])
        self.assertNotIn("test-only-secret", str(result))

    def test_unknown_choice_or_missing_probabilities_rejected(self):
        for answer in ({"type": "choice", "choice": "ARBITRARY", "confidence": 1, "probabilities": {"USE": 1}},
                       {"type": "choice", "choice": "USE", "confidence": 1, "probabilities": {"USE": 1}}):
            self.response["answers"]["action"] = answer
            with self.assertRaises(BridgeError) as caught:
                self.choose()
            self.assertEqual("JEV_INVALID_RESPONSE", caught.exception.code)

    def test_bool_unnormalized_and_nonwinning_answer_rejected(self):
        original = copy.deepcopy(self.response)
        for patch in ({"confidence": True},
                      {"probabilities": {"USE": 0.8, "WAIT": 0.8}}, {"choice": "WAIT"}):
            self.response = copy.deepcopy(original)
            self.response["answers"]["action"].update(patch)
            with self.assertRaises(BridgeError) as caught:
                self.choose()
            self.assertEqual("JEV_INVALID_RESPONSE", caught.exception.code)

    def test_nonfinite_provider_response_is_rejected(self):
        self.response["answers"]["action"]["confidence"] = float("nan")
        with httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(
                200, content=json.dumps(self.response)))) as http:
            with JevClient("test-only", _http_client=http) as client:
                with self.assertRaises(BridgeError) as caught:
                    client.choose("state", "goal", {"USE": "Use", "WAIT": "Wait"})
                self.assertEqual("JEV_INVALID_RESPONSE", caught.exception.code)

    def test_http_errors_do_not_leak_response_or_key(self):
        self.status = 401
        self.response = {"message": "test-only-secret"}
        with self.assertRaises(BridgeError) as caught:
            self.choose()
        self.assertEqual("JEV_HTTP_ERROR", caught.exception.code)
        self.assertNotIn("test-only-secret", str(caught.exception.as_dict()))
        self.assertEqual(1, len(self.requests))

    def test_redirect_not_followed(self):
        self.status = 307
        with self.assertRaises(BridgeError):
            self.choose()
        self.assertEqual(1, len(self.requests))

    def test_image_bytes_and_invalid_state_rejected_before_network(self):
        for state in (b"PNG", {"binary": b"PNG"}, {"number": float("nan")}):
            with self.assertRaises(BridgeError):
                self.client.choose(state, "Inspect", {"USE": "Use", "WAIT": "Wait"})
        self.assertEqual([], self.requests)


if __name__ == "__main__":
    unittest.main()
