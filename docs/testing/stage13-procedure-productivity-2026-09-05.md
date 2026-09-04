# Stage 13 Procedure 2.0 foundation evidence — 2026-09-05

## Scope

This evidence covers the first `FR-PRODUCTIVITY-01` implementation slice. It does not close Stage 13 or the full Procedure Workbench 2.0 requirement.

Implemented in this slice:

- node search plus category filtering and session-local recently used nodes;
- current-graph search, previous/next result navigation, and selected-node location;
- Core-owned Procedure symbol projection for variable reads/writes, item-resource references, and Procedure calls;
- debounced `preview_procedure_change` validation while editing, with generated-source preview and `canGenerate` state;
- node-addressable diagnostics with jump-to-node behavior;
- accessible 32 px minimum targets for the new Procedure controls;
- preservation of the existing 500-node Procedure IR validation/serialization performance gate.

Still required before `FR-PRODUCTIVITY-01` can be called complete:

- richer element/reference relationship presentation beyond the existing workspace reference edge list;
- common semantic refactors such as reusable-logic extraction and batch reference replacement/rename;
- semantic impact preview and recovery-point flow for those refactors before mutation;
- representative end-to-end failure → locate → fix → rebuild coverage using the installed-product path.

## Core / UI ownership

The new symbol relationships are emitted by `GET_PROCEDURE_EDITOR`; React does not derive workspace truth independently. Live validation calls the existing Core `PREVIEW_PROCEDURE_CHANGE` operation, so UI and MCP/headless consumers can share the same Procedure IR validation semantics. Existing `UPDATE_PROCEDURE` structured edits and recovery-point behavior remain the write path.

## Verification

- `npm run build` — passed, including TypeScript, Vite, and the Chinese localization gate (`181/181`).
- `WorkspaceApplicationServiceTest` — passed, including `stage13ProcedureEditorProjectsSharedVariableResourceAndCallSymbols`.
- `npx playwright test e2e/stage9-creator-core.spec.ts` — `16 passed` across Chromium and compact-1366 projects.
- `npx playwright test e2e/accessibility.spec.ts --grep "Procedure exposes"` — `2 passed` across Chromium and compact-1366 projects.
- `ProcedureIrCodecTest` — passed.
- `ProcedureIrScaleGateTest` with `-Dcopperbench.stage9.scale=true` — passed for the 500-node Procedure baseline.

The isolated Stage 13 worktree does not contain its own bundled-JDK directory, so Gradle verification used the repository-root bundled JBR 25 via `JAVA_HOME`. Gradle emitted warnings for worktree-local configured JDK paths that do not exist; the test executions themselves completed successfully.
