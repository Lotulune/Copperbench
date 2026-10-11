import unittest
from unittest.mock import Mock
from copperbench import Workspace
from test_task_authorization import RecordingTransport


class TaskVerificationSdkTest(unittest.TestCase):
    def test_mcp_versioned_result_is_read_only(self):
        client = RecordingTransport()
        client.get_task_verification("task")
        self.assertEqual([{"name": "get_task_verification", "arguments": {"taskId": "task", "resultVersion": "1"}}], client.calls)

    def test_native_preserves_result_without_reinterpreting_coverage(self):
        workspace = Workspace.__new__(Workspace)
        envelope = {"status": "succeeded", "revision": 7, "data": {"resultVersion": "1", "reasonCode": "GAMETEST_NO_TESTS",
            "subreason": "insufficient_business_tests", "coverage": {"minimumTests": 6, "acceptanceExecuted": 5, "shortfall": 1}}}
        workspace.query = Mock(return_value=envelope)
        self.assertIs(envelope, workspace.get_task_verification("task"))
        workspace.query.assert_called_once_with("get_task_verification", taskId="task", resultVersion="1")


if __name__ == "__main__":
    unittest.main()
