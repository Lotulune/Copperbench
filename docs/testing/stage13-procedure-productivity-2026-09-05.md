# Stage 13 Procedure 2.0 foundation evidence — 2026-09-05

## Scope

This evidence covers the first four `FR-PRODUCTIVITY-01` implementation slices. It does not close Stage 13 or the full Procedure Workbench 2.0 requirement because installed-product failure-to-fix evidence is still pending.

Implemented in this slice:

- node search plus category filtering and session-local recently used nodes;
- current-graph search, previous/next result navigation, and selected-node location;
- Core-owned Procedure symbol projection for variable reads/writes, item-resource references, and Procedure calls;
- debounced `preview_procedure_change` validation while editing, with generated-source preview and `canGenerate` state;
- node-addressable diagnostics with jump-to-node behavior;
- accessible 32 px minimum targets for the new Procedure controls;
- Registry-backed stable identities for Procedure variable symbols, including variable type/scope metadata and the workspace variable catalog;
- a safe variable-rename workflow from the Procedure reference panel: impact preview, signed semantic workspace plan, one-revision atomic apply, and post-apply Procedure refresh;
- optional `requireRecoveryPoint` protection on workspace plans. Protected plans report recovery readiness during preview and are rejected before mutation when local-history recovery is unavailable;
- the same protected-plan capability in the desktop MCP schema and external-Agent loop;
- Procedure dependency recanonicalization after Registry-aware rewrites so renamed variables continue to resolve through the stable reference index;
- Core-owned stable Procedure identities in the symbol projection, including resolved call target IDs/names plus the workspace Procedure catalog;
- reusable-logic extraction for supported statement subgraphs: Core computes the extraction boundary, rejects unsafe/shared/control-flow graphs, creates a new Procedure, replaces the source root with a call while preserving its stable node identity and following statement, and commits both edits as one protected workspace plan;
- batch Procedure-call target replacement across multiple callers using stable Procedure identities, with one-revision atomic apply, one recovery point, and reference-index refresh;
- post-replacement call-graph validation that rejects newly introduced circular Procedure dependencies before a workspace plan reaches persistence;
- a new high-level `plan_procedure_refactor` MCP query so external Agents use the same extraction/batch-refactor planner as the UI instead of reconstructing low-level edits independently;
- preservation of the existing 500-node Procedure IR validation/serialization performance gate.

Fourth-slice additions:

- Core-owned inbound/outbound Procedure relationship projection with readable source/target names, types, and relationship-kind summaries;
- item-resource nodes now participate in the canonical dependency/reference index without false mandatory-identity diagnostics;
- protected batch item-resource replacement uses the same high-level planner and one-revision/recovery-point apply path;
- signed workspace-plan `review` metadata includes operation groups, affected objects, create/update/delete counts, changed paths, and per-element changed properties, and is rejected if tampered;
- the Procedure UI renders the same Core review for extraction, variable rename, call replacement, and resource replacement.

Still required before `FR-PRODUCTIVITY-01` can be called complete:

- representative end-to-end failure → locate → fix → rebuild coverage using the installed-product path.

## Core / UI ownership

The new symbol relationships are emitted by `GET_PROCEDURE_EDITOR`; React does not derive workspace truth independently. Live validation calls the existing Core `PREVIEW_PROCEDURE_CHANGE` operation, so UI and MCP/headless consumers can share the same Procedure IR validation semantics. Variable refactoring reuses `PLAN_WORKSPACE_CHANGES` / `APPLY_WORKSPACE_PLAN` and `rename_registry_entry` instead of adding UI-private mutation logic. Reusable-logic extraction plus batch call/resource replacement use the Core `PLAN_PROCEDURE_REFACTOR` planner, which emits ordinary signed workspace-plan operations and forces recovery protection. The plan identity/token covers `requireRecoveryPoint` and canonical `review` metadata, and preview/apply recompute both recovery readiness and semantic review before mutation. Desktop MCP exposes both the low-level protected-plan capability and the same high-level Procedure refactor planner.

## Verification

- `npm run build` — passed, including TypeScript, Vite, and the Chinese localization gate (`193/193`).
- `WorkspaceApplicationServiceTest` — passed, including Registry-backed symbols and Core-owned variable/resource/call relationship projection.
- `WorkspacePlanEngineTest` — passed, including protected variable rename, extraction, multi-caller call/resource replacement, review tamper rejection, stable reference resolution, and circular-call rejection.
- `WorkspaceReferenceIndexTest` — passed, including readable edge metadata and optional unresolved resource references without false dangling diagnostics.
- `ProcedureIrCodecTest` — passed, including semantic `replace_node` while preserving the stable node identity.
- `npx playwright test e2e/stage9-creator-core.spec.ts` — `20 passed` across Chromium and compact-1366, including relationship review and protected resource replacement with affected-object plan review.
- `npx playwright test e2e/accessibility.spec.ts --grep "Procedure exposes"` — `2 passed` across Chromium and compact-1366 projects.
- `DesktopMcpAgentLoopTest`, `DesktopMcpRuntimeTest`, and `McpHttpServerTest` — passed in the forced `--rerun-tasks` gate; the external Agent explicitly plans and applies reusable-logic extraction through `plan_procedure_refactor`, receives a recovery point, and `tools/list` exposes the high-level refactor schema.
- `ProcedureIrScaleGateTest` with `-Dcopperbench.stage9.scale=true` — passed for the 500-node Procedure baseline.
- `WorkspaceReferenceIndexScaleTest` with `-Dcopperbench.stage9.scale=true` — passed for the 2,000-element / 10,000-reference baseline after readable edge metadata was added.

The isolated Stage 13 worktree does not contain its own bundled-JDK directory, so Gradle verification used the repository-root bundled JBR 25 via `JAVA_HOME`. Gradle emitted warnings for worktree-local configured JDK paths that do not exist; the test executions themselves completed successfully.
