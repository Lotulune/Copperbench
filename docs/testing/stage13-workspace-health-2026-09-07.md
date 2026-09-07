# Stage 13 Workspace Health closure evidence — 2026-09-07

## Scope

This page records closure evidence for `FR-PRODUCTIVITY-06 Workspace Health` on the Stage 13 development line.

The implementation deliberately does **not** compute a hidden health score and does not introduce remote telemetry, speculative ownership, an approval queue, or automatic repair. `get_workspace_health` is a read-only Core projection that aggregates facts already owned by existing Copperbench subsystems.

## Core projection

`GET_WORKSPACE_HEALTH` / MCP `get_workspace_health` reports:

- element counts: total / valid / invalid / draft / unsupported;
- diagnostic totals split into error / warning / info using existing element validation, structured-reference diagnostics and Asset Health diagnostics;
- structured reference edge count plus proven dangling references;
- Asset Health summary including missing references, unused/safe-unused assets, duplicates and invalid asset documents when the workspace asset index is available;
- current generator support status, reason code and `generatable` decision from `VersionTrackCatalog`;
- active tasks and up to five recent failed task snapshots from the existing bounded current-session task event history;
- Local History availability, recovery-point count and whether the current worktree exactly matches a recovery point;
- explainable high-risk-operation facts:
  - loader migration is copy-only, requires explicit user approval and lists only same-version first-party loader targets that `VersionTrackCatalog` says are migratable;
  - AI / batch changes reuse the existing WorkspacePlan review model, maximum 100 operations, and the existing high-impact threshold of five operations or five affected semantic objects.

Asset/history subsystems may report a stable local unavailability reason without making the entire health projection fail. This is a read-only aggregation behavior, not a fallback mutation path.

## UI and Agent parity

The Workspace Hub renders one `项目健康` panel from `WorkspaceHealthProjection` and does not recompute health conclusions in React. The panel links each fact family back to the existing specialist surface: Elements, Creator Data/references, Asset Center, Tracks, Task Drawer and Local History.

Desktop UI, MCP and both SDKs use the same Core query:

- UI: `WorkbenchContext.getWorkspaceHealth()`;
- MCP: `get_workspace_health`;
- Python SDK: `get_workspace_health()`;
- TypeScript SDK: `getWorkspaceHealth()`.

The SDK methods remain thin wrappers; no second health model is maintained outside Core.

## Executable evidence

- `Stage67ApplicationServiceTest.workspaceHealthAggregatesOnlyCoreOwnedFacts` with `--rerun-tasks` — passed. The fixture includes a real dangling structured reference and a real unused workspace asset, and checks diagnostic, generator, migration-risk and WorkspacePlan-risk facts.
- `McpHttpServerTest` — passed with authenticated loopback MCP exposing `get_workspace_health` in `tools/list`.
- Python SDK unittest discovery — `3/3` passed, including the new thin health wrapper.
- `scripts/verify-ai-evals.mjs` — passed: `10 cases, 10 coverage targets`; both SDK method inventories include Workspace Health.
- TypeScript SDK standalone compilation with the repository TypeScript compiler and existing Node type roots — passed with exit code `0`.
- `npm run build --prefix ui-shell` — passed; Chinese i18n gate `224/224`; Vite production build completed successfully.
- `npx playwright test e2e/stage13-workspace-health.spec.ts` — `2/2` passed across Chromium and compact-1366. The Hub renders Core facts, diagnostic totals, recovery state and high-risk-change guidance and routes into the existing Asset Center.
- `npm test --prefix ui-core` — final `20/20` passed with `get_workspace_health` present in the v1.0 query/result operation sets.
- `WorkspaceReferenceIndexScaleTest` with `-Dcopperbench.stage9.scale=true` and `--rerun-tasks` — passed for the existing 2,000-element / 10,000-reference baseline.
- `WorkspaceHealthScaleTest` with the same scale property and `--rerun-tasks` — passed over the actual `get_workspace_health` query with 2,000 elements and 10,000 structured references. Recorded result: initial aggregation `115 ms`, repeated aggregation `45 ms`, zero lost references and zero false dangling diagnostics.

The ordinary Vite chunk-size warning and missing non-Windows JDK-path warnings in this Windows worktree are pre-existing build warnings and did not fail the accepted gates.

## Closure

`FR-PRODUCTIVITY-06` is complete on this development line. The PRD requirements are represented by explainable local Core facts: diagnostic totals, dangling references, missing/unused assets, generator capability, recent failed tasks, recovery availability, and high-risk migration / AI batch-change guidance. No hidden score, remote telemetry, speculative ownership or new approval framework was added.
