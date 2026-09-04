# Stage 13 Procedure 2.0 foundation evidence — 2026-09-05

## Scope

This evidence covers the first two `FR-PRODUCTIVITY-01` implementation slices. It does not close Stage 13 or the full Procedure Workbench 2.0 requirement.

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
- preservation of the existing 500-node Procedure IR validation/serialization performance gate.

Still required before `FR-PRODUCTIVITY-01` can be called complete:

- richer element/reference relationship presentation beyond the existing workspace reference edge list;
- reusable-logic extraction and broader batch reference replacement beyond the now-covered workspace-variable rename;
- richer multi-operation refactor review UX for larger semantic plans;
- representative end-to-end failure → locate → fix → rebuild coverage using the installed-product path.

## Core / UI ownership

The new symbol relationships are emitted by `GET_PROCEDURE_EDITOR`; React does not derive workspace truth independently. Live validation calls the existing Core `PREVIEW_PROCEDURE_CHANGE` operation, so UI and MCP/headless consumers can share the same Procedure IR validation semantics. Variable refactoring reuses `PLAN_WORKSPACE_CHANGES` / `APPLY_WORKSPACE_PLAN` and `rename_registry_entry` instead of adding UI-private mutation logic. The plan token covers `requireRecoveryPoint`, and protected-plan execution recomputes recovery readiness in Core before mutation. The desktop MCP exposes the same option in its tool schema.

## Verification

- `npm run build` — passed, including TypeScript, Vite, and the Chinese localization gate (`182/182`).
- `WorkspaceApplicationServiceTest` — passed, including Registry-backed `stage13ProcedureEditorProjectsSharedVariableResourceAndCallSymbols`.
- `WorkspacePlanEngineTest` — passed, including protected-plan rejection without history and the end-to-end protected variable rename that verifies Procedure dependencies plus stable-ID reference-index resolution after the rename.
- `npx playwright test e2e/stage9-creator-core.spec.ts` — `16 passed` across Chromium and compact-1366 projects.
- `npx playwright test e2e/accessibility.spec.ts --grep "Procedure exposes"` — `2 passed` across Chromium and compact-1366 projects.
- `DesktopMcpAgentLoopTest`, `DesktopMcpRuntimeTest`, and `McpHttpServerTest` — passed; the Agent loop explicitly requests a protected plan and receives a recovery point, while `tools/list` exposes `requireRecoveryPoint`.
- `ProcedureIrScaleGateTest` with `-Dcopperbench.stage9.scale=true` — passed for the 500-node Procedure baseline.

The isolated Stage 13 worktree does not contain its own bundled-JDK directory, so Gradle verification used the repository-root bundled JBR 25 via `JAVA_HOME`. Gradle emitted warnings for worktree-local configured JDK paths that do not exist; the test executions themselves completed successfully.
