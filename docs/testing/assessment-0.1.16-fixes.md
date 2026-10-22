# 0.1.16 assessment regression fixes

Validated on Windows on 2026-10-22, starting from `59bc654be0ba9c5fac72a5f65dcd45e5bfbe33c6`.
This change addresses the four concrete defects in the 0.1.16 assessment: legacy conversation continuation, attachment error localization, compact mode labels, and attachment retention after message truncation.

## Behavior and regression coverage

- **Legacy history:** conversations with a native resume ID and no recorded model expose `usesSessionModel`. The UI displays “Keep session model” / “沿用会话模型”, sends the existing conversation ID, and omits a model override. Catalog and saved defaults do not replace the original model. Explicit model selection still creates a conversation and carries the text draft; a new conversation without a native session still requires selection.
- **Old-format fixture:** Java and browser tests share [history-0.1.15.json](../../src/test/resources/dev/copperbench/agent/history-0.1.15.json). The actual `AgentChatStore.java` from `v0.1.15` (commit `d99aa3f4af171954a575f82de3afd1ee5b34c9a6`, blob `c6547c8c9fbd89e3b66edd54100589189c23dd87`) was compiled separately and used to serialize its values. Its emitted JSON equals the fixture. The Java subprocess regression then loads it, reopens twice, and verifies the same `conversationId` / native thread ID, original messages and workspace, `thread/resume` without `thread/start`, and no model override in either resume or turn frames.
- **Attachment errors:** local validation and structured backend failures retain bilingual text until rendering. Browser cases switch English → Chinese → English while keeping the draft for empty files, oversized files, file count, total bytes, upload start, chunk acknowledgement, commit, native backend errors, cleanup, and native selection errors. Unknown diagnostic text remains verbatim.
- **Attachment lifecycle:** history stores `retainedAttachmentIds` for messages removed by the 1,000-message limit. The references survive reopening and protect files still needed by native resume. The regression exercises production truncation, deletion, shared references, unsent drafts, and a full 256 MiB storage budget; deletion frees room for another upload. Invalid index metadata preserves the original history file and reports it unreadable.
- **500 × 600 labels:** the mode slot retains its 206px preferred width; narrower titlebar column gaps leave sufficient room. Both full labels, equal button sizes, neighboring controls, keyboard/touch actions and reduced motion pass with the wider Verdana / DejaVu Sans fallback stack. Light and dark browser captures were visually inspected.

## Verification

| Check | Result |
| --- | --- |
| `test --tests 'dev.copperbench.agent.*Test' --tests dev.copperbench.bridge.JcefAgentBridgeTransportTest javadoc` | 72 passed, 0 failed/errors/skipped; Javadoc passed |
| `npm test --prefix ui-shell` | 76 passed; affected boundary file rerun after formatting: 3 passed |
| `npm test --prefix ui-core` | 50 passed |
| `npm run build --prefix ui-shell` | Passed; 424 Chinese keys and 1,746 English messages checked |
| `agent-composer-options`, `agent-experience`, `agent-models` Playwright specs | 70 passed across `chromium` and `compact-1366` |
| Final `workbench-mode-switch` and `adaptive-and-frameless` specs | 32 passed across both projects |
| Old serializer comparison, Markdown links and whitespace diff checks | Passed |

The first browser batch had 76 passes and 4 compact-label failures: retaining the larger preferred slot alone was insufficient because its grid column still shrank. After reducing the compact titlebar gaps, all 10 mode-switch executions and 22 adaptive-layout executions passed. The 70 unchanged conversation/attachment executions plus this final batch cover 102 distinct spec/project cases; reruns are not counted as additional coverage.

The direct Windows Gradle launch failed before compilation with `Unable to establish loopback connection`. The repository's documented `scripts/run-gradle-external.ps1` entry completed the checks using the same wrapper and JBR. No global networking or Java settings were changed. Environment: JBR/JCEF 25.0.3, Node 24.22.1, npm 11.19.1, Playwright 1.62.1 and its managed Chromium 151.0.7922.34 (revision 1234).

[Verification receipt and tested hashes](../../evidence/maintenance/2026-10-22/assessment-016-fixes/verification.json) accompany the [legacy continuation](../../evidence/maintenance/2026-10-22/assessment-016-fixes/legacy-continue.png), [attachment error](../../evidence/maintenance/2026-10-22/assessment-016-fixes/attachment-error.png), [compact light](../../evidence/maintenance/2026-10-22/assessment-016-fixes/compact-light.png) and [compact dark](../../evidence/maintenance/2026-10-22/assessment-016-fixes/compact-dark.png) captures. Full local logs, first-attempt traces and the old-writer comparison remain under `output/assessment-016/` in the attached worktree.

## Scope limits

The native tests use deterministic CLI subprocess fixtures. This is source-level upgrade/protocol and browser validation, not an installed-package upgrade with a real CLI account. No release package was published and no Minecraft behavior changed. Linux/JCEF rendering and real-account upgrades remain unverified here.

The attachment index protects future truncation and still-visible attachments in loaded histories. It cannot identify copies whose references were already discarded by an older version. Such pre-existing orphan files are not swept automatically because their native-session ownership is unknown.
