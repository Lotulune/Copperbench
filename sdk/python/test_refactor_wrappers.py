from __future__ import annotations

import unittest

from copperbench import CopperbenchClient


class RecordingClient(CopperbenchClient):
    def __init__(self) -> None:
        super().__init__("http://127.0.0.1:61999/mcp", "token", "workspace")
        self.calls: list[tuple[str, dict[str, object]]] = []

    def call_tool(self, name: str, arguments: dict[str, object]) -> dict[str, object]:
        self.calls.append((name, arguments))
        return {"tool": name, "arguments": arguments}


class RefactorWrapperTest(unittest.TestCase):
    def test_refactor_wrappers_forward_to_matching_mcp_tools(self) -> None:
        client = RecordingClient()

        client.preview_registry_rename(entryId="entry", newName="renamed")
        client.plan_procedure_refactor(kind="replace_resource_target", sourceResource="a", targetResource="b")
        client.list_assets()
        client.preview_asset_move(sourceAssetId="asset", targetRelativePath="assets/mod/moved.png")
        client.move_asset(planToken="plan", expectedRevision=7)

        self.assertEqual(
            [
                "preview_registry_rename",
                "plan_procedure_refactor",
                "list_assets",
                "preview_asset_move",
                "move_asset",
            ],
            [name for name, _ in client.calls],
        )
        self.assertEqual("renamed", client.calls[0][1]["newName"])
        self.assertEqual(7, client.calls[-1][1]["expectedRevision"])


if __name__ == "__main__":
    unittest.main()
