# Stage 13 Procedure 2.0 foundation evidence — 2026-09-05

## Scope

This evidence covers the first three `FR-PRODUCTIVITY-01` implementation slices. It does not close Stage 13 or the full Procedure Workbench 2.0 requirement.

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

Still required before `FR-PRODUCTIVITY-01` can be called complete:

- richer element/reference relationship presentation beyond the existing workspace reference edge list;
- richer reference refactors outside the now-covered workspace-variable rename and Procedure-call replacement domains;
- richer multi-operation refactor review UX for larger semantic plans;
- representative end-to-end failure → locate → fix → rebuild coverage using the installed-product path.

## Core / UI ownership

The new symbol relationships are emitted by `GET_PROCEDURE_EDITOR`; React does not derive workspace truth independently. Live validation calls the existing Core `PREVIEW_PROCEDURE_CHANGE` operation, so UI and MCP/headless consumers can share the same Procedure IR validation semantics. Variable refactoring reuses `PLAN_WORKSPACE_CHANGES` / `APPLY_WORKSPACE_PLAN` and `rename_registry_entry` instead of adding UI-private mutation logic. Reusable-logic extraction and batch call replacement use the new Core `PLAN_PROCEDURE_REFACTOR` planner, which emits ordinary signed workspace-plan operations and forces recovery protection. The plan token covers `requireRecoveryPoint`, and protected-plan execution recomputes recovery readiness in Core before mutation. Desktop MCP exposes both the low-level protected-plan capability and the same high-level Procedure refactor planner.

## Verification

- `npm run build` — passed, including TypeScript, Vite, and the Chinese localization gate (`193/193`).
- `WorkspaceApplicationServiceTest` — passed, including Registry-backed `stage13ProcedureEditorProjectsSharedVariableResourceAndCallSymbols`.
- `WorkspacePlanEngineTest` — passed, including protected-plan rejection without history, protected variable rename, reusable-logic extraction, multi-caller Procedure target replacement, stable-ID reference-index resolution, and pre-plan circular-call rejection.
- `ProcedureIrCodecTest` — passed, including semantic `replace_node` while preserving the stable node identity.
- `npx playwright test e2e/stage9-creator-core.spec.ts` — `18 passed` across Chromium and compact-1366 projects, including the protected reusable-logic extraction workflow.
- `npx playwright test e2e/accessibility.spec.ts --grep "Procedure exposes"` — `2 passed` across Chromium and compact-1366 projects.
- `DesktopMcpAgentLoopTest`, `DesktopMcpRuntimeTest`, and `McpHttpServerTest` — passed in the forced `--rerun-tasks` gate; the external Agent explicitly plans and applies reusable-logic extraction through `plan_procedure_refactor`, receives a recovery point, and `tools/list` exposes the high-level refactor schema.
- `ProcedureIrScaleGateTest` with `-Dcopperbench.stage9.scale=true` — passed for the 500-node Procedure baseline.

The isolated Stage 13 worktree does not contain its own bundled-JDK directory, so Gradle verification used the repository-root bundled JBR 25 via `JAVA_HOME`. Gradle emitted warnings for worktree-local configured JDK paths that do not exist; the test executions themselves completed successfully.
