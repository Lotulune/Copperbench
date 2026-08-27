# Stage 10 AI task events implementation - 2026-08-27

This record covers the current implementation slice of `FR-AI-04`. The gate
remains **blocked** until merged-main CI/Nightly and a real native JCEF
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
  `jdk/jbr25_win_64`, `WorkspaceTaskEventTest` compiled and passed **3/3**:
  retained task-event replay after reconnect, `afterLogSequence` incremental
  polling, and the asynchronous Gradle task fixture all passed.
- `McpHttpServerTest` passed after adding the MCP `cancel_task` and protected
  `restore_recovery_point` surfaces. The restore surface deliberately returns
  `USER_APPROVAL_REQUIRED` to MCP rather than allowing the client to
  self-assert desktop approval.
- Protected PR #16 CI is green on head `564b2ed4`: `Build and test` run
  `33068820470` completed successfully across Java/Javadoc, UI, MCP
  conformance, and the JUnit report job.
- `WorkspaceTaskEventTest` now also exercises the actual asynchronous
  `GradleWorkspaceTaskGateway` with a controlled process boundary, covering
  progress and log emission, failure diagnostics, cancellation while a task is
  running, and retained replay after disconnect/reconnect.
- That fixture exposed and closed a cancellation-noise bug: an interrupted
  external task no longer emits a second synthetic workspace-failure log after
  its task state has already committed to `cancelled`.

## Remaining closure work

- Merge only after review, then record exact merged-main CI/Nightly evidence.
- Review event retention and reconnect behavior on a real native JCEF host
  with a genuinely long-running managed task.
- Decide whether MCP needs a frozen push-subscription surface beyond the
  versioned `get_task(afterLogSequence)` reconnect contract; do not introduce a
  custom notification dialect without an explicit protocol/version boundary.
- Keep `ai-task-events` blocked in `product-status.json` until those records
  exist.
