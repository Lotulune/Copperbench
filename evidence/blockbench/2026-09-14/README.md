# Blockbench R7 acceptance evidence

These screenshots and hash checks come from the installed Windows candidate and the Ubuntu TAR candidate described in [the acceptance report](../../../docs/testing/blockbench-m4-2026-09-13.md). They predate the separate integration-branch UI extraction; the extraction has its own automated checks.

Both candidates use core SHA-256 `e67f1248ca7aa8f04c5f4f2dbf5c76a77ec580c715b819a4e8d59dc3d423d4dc`, independent Blockbench 5.1.6 and community MCP plugin 1.7.0. No external application binaries, credentials or full protocol transcripts are included here.

| Evidence | Windows | Ubuntu |
| --- | --- | --- |
| Game block geometry and texture | [Screenshot](windows/r7-game-block-visible.png) | [Screenshot](linux/r7-game-block-visible.png) |
| Game item rendering | [Screenshot](windows/r7-game-item-visible.png) | [Screenshot](linux/r7-game-item-visible.png) |
| Exact exported JSON/PNG bytes inside the built JAR | [Checks](windows/r7-jar-verification.json) | [Checks](linux/jar-verification.json) |
| Persisted world after restarting the game | — | [Screenshot](linux/r7-game-reopened-visible.png) |
| Editor recovery after its GPU-process crash | — | [Screenshot](linux/r7-editor-recovered.png) |
| Manual cap-height edit | — | [Screenshot](linux/r7-manual-cap-height.png) |
| Product UI MCP probe | — | [Screenshot](linux/r7-ui-mcp-ready.png) |
| Independent editor/workspace retention after TAR removal | — | [Checks](linux/uninstall-retention.json) |

The hand-held display uses the editor's default transform and is oversized. Ubuntu's block display name still resolves to a translation key. The recovered editor screenshot demonstrates saved-model recovery, not a fix for the third-party GPU crash. Runtime limitations are recorded in the acceptance report.
