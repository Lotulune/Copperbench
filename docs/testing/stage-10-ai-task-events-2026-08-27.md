# Stage 10 AI task events implementation - 2026-08-27

This record covers the current implementation slice of `FR-AI-04`. The gate
remains **blocked** until the Windows-native reconnect proof is merged and a
fixed-commit Nightly records it on `main`.

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
- Protected PR #16 CI is green on final head `a1ed7204`: `Build and test` run
  `33071508349` completed successfully across Java/Javadoc, UI, MCP
  conformance, and the JUnit report job.
- PR #16 subsequently merged as `main@a7304fb6`; merged-main `Build and test`
  run `33071953778` passed Java/Javadoc, UI, MCP conformance, and the JUnit
  report job. Javadoc publish run `33071953877` also passed.
- `WorkspaceTaskEventTest` now also exercises the actual asynchronous
  `GradleWorkspaceTaskGateway` with a controlled process boundary, covering
  progress and log emission, failure diagnostics, cancellation while a task is
  running, and retained replay after disconnect/reconnect.
- That fixture exposed and closed a cancellation-noise bug: an interrupted
  external task no longer emits a second synthetic workspace-failure log after
  its task state has already committed to `cancelled`.
- `Stage10NativeJcefTaskReconnectTest` is a Windows-only native acceptance
  test with no opt-in system property. Using the repository-bundled JBR/JCEF it
  initializes real JCEF/Chromium, starts an actual asynchronous
  `GradleWorkspaceTaskGateway`, proves live task-log delivery, closes the first
  browser transport, emits a task log while disconnected, then proves retained
  replay and cancelled-task completion through a second native JCEF host. The
  final forced local run passed in 58 seconds. Because the test is enabled by OS rather than
  a private flag, the Windows Nightly full Java test suite will execute it once
  this test is merged.

## Remaining closure work

- Land the Windows-native reconnect acceptance test through protected review.
- Record a fixed-commit Windows Nightly on a `main` SHA that contains that test
  and the PR #16 task-event implementation.
- Decide whether MCP needs a frozen push-subscription surface beyond the
  versioned `get_task(afterLogSequence)` reconnect contract; do not introduce a
  custom notification dialect without an explicit protocol/version boundary.
- Keep `ai-task-events` blocked in `product-status.json` until those records
  exist.
