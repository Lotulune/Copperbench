from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from copperbench import CopperbenchError, read_workspace_connection


class WorkspaceConnectionSecurityTest(unittest.TestCase):
    def write_connection(self, root: Path, url: str) -> None:
        metadata = root / ".copperbench" / "mcp-connection.json"
        metadata.parent.mkdir(parents=True, exist_ok=True)
        metadata.write_text(
            json.dumps(
                {
                    "schemaVersion": "1.0",
                    "status": "listening",
                    "url": url,
                    "workspaceId": "00000000-0000-4000-8000-000000000091",
                }
            ),
            encoding="utf-8",
        )

    def test_accepts_exact_loopback_endpoint(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_connection(root, "http://127.0.0.1:61999/mcp")
            connection = read_workspace_connection(root)
            self.assertEqual("http://127.0.0.1:61999/mcp", connection["url"])

    def test_rejects_urls_that_can_escape_or_reshape_the_loopback_endpoint(self) -> None:
        malicious = (
            "http://127.0.0.1:80@attacker.example/mcp",
            "http://user@127.0.0.1:61999/mcp",
            "https://127.0.0.1:61999/mcp",
            "http://127.0.0.1/mcp",
            "http://127.0.0.1:99999/mcp",
            "http://127.0.0.1:61999/mcp?redirect=attacker.example",
            "http://127.0.0.1:61999/mcp#fragment",
            "http://127.0.0.1:61999/mcp/extra",
        )
        for url in malicious:
            with self.subTest(url=url), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.write_connection(root, url)
                with self.assertRaises(CopperbenchError) as error:
                    read_workspace_connection(root)
                self.assertEqual("MCP_CONNECTION_FILE_INVALID", error.exception.code)


if __name__ == "__main__":
    unittest.main()
