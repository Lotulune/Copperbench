# Stage 10 AI task events implementation - 2026-08-27

This record covers the first implementation slice of `FR-AI-04`. The gate
remains **blocked** until protected PR CI, merged-main CI/Nightly, and a real
long-running task reconnect trial are complete.

## Implemented

- Workspace task gateways can publish asynchronous task events without making
  push delivery a requirement for legacy implementations.
- Gradle-backed tasks publish progress transitions, log entries, completion,
  cancellation, and failure diagnostics while preserving `get_task` polling.
- Core allocates task event sequences from the workspace state, retains a
  bounded per-workspace replay buffer, and exposes subscription/replay handles.
- JCEF transport subscribes to retained events and closes subscriptions with
  the browser transport; the TypeScript bridge reconciles projections when an
  event sequence gap is observed.
- Existing task event schemas and polling payloads remain unchanged.
- The production loader-routing gateway forwards events from all Fabric,
  NeoForge, and resource-pack task backends.
- `get_task` now honors the versioned `afterLogSequence` cursor and returns
  only newer log entries, so MCP/Headless polling can resume without duplicate
  output.

## Local checks

- `npm test --prefix ui-core`: 19/19 passed.
- `npm run build --prefix ui-shell`: passed; Chinese localization 125/125.
- `node --test ui-shell/tests/bridge-logic.test.mjs`: 4/4 passed.
- UI-Core schema suite: 20/20 passed, including the incremental task-log
  cursor contract.
- Product status and local Markdown link checks passed.
- With `JAVA_HOME` explicitly set to the repository-bundled
  `jdk/jbr25_win_64`, `WorkspaceTaskEventTest` compiled and passed **2/2**:
  retained task-event replay after reconnect and `afterLogSequence`
  incremental polling both passed.
- `McpHttpServerTest` passed after adding the MCP `cancel_task` and protected
  `restore_recovery_point` surfaces. The restore surface deliberately returns
  `USER_APPROVAL_REQUIRED` to MCP rather than allowing the client to
  self-assert desktop approval.

## Remaining closure work

- Add a real asynchronous task fixture covering progress, log replay,
  diagnostics, cancellation, and reconnect after a sequence gap.
- Run protected PR CI and merged-main Nightly evidence, then review event
  retention and reconnect behavior on a real JCEF host.
- Keep `ai-task-events` blocked in `product-status.json` until those records
  exist.
