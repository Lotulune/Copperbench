# Stage 13 Procedure Workbench 2.0 closure evidence — 2026-09-05

## Scope

This evidence closes `FR-PRODUCTIVITY-01 Procedure Workbench 2.0` on the current Stage 13 development line. It does not close Stage 13 as a whole; Asset Center, Diagnostics 2.0, history/recovery UX, migration/refactor workflows, and Workspace Health remain separate Stage 13 requirements.

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

## Installed-product failure → locate → fix → rebuild closure

The final Procedure-productivity gate uses two complementary paths so the acceptance does not depend on either a mock-only task gateway or direct filesystem repair alone:

- `DesktopMcpAgentLoopTest.externalAgentCanFailLocateRepairAndRebuildCodeThroughDesktopMcp` performs the complete Copperbench API flow: create a code element, receive automatic compile verification, inspect `JAVA_COMPILE_ERROR` through `get_task`, repair the same element through `update_mod_element` at `/code`, rebuild through `build_workspace`, and observe a successful terminal task with `BUILD SUCCESSFUL` and no diagnostics.
- `scripts/verify-stage13-productivity-repair-loop.ps1` performs the equivalent build-failure/location/rebuild check against a fresh exported Windows product and a disposable real MCreator workspace. It deliberately injects one invalid Java source, requires the exported product to return a workspace-relative `JAVA_COMPILE_ERROR` path and line number, resolves that exact diagnostic path back into the workspace, repairs the source, and rebuilds with the same exported executable.

The fresh export was produced with the canonical `exportWin64 -x test` task. The accepted replay is recorded in `evidence/stage13/2026-09-05/procedure-failure-repair-loop.json` and is bound to runtime source revision `24f7510d7bc8807b1c82f3ee4352eefdc8e78c89`:

- `copperbench.exe` SHA-256: `c94f8a487e2c8eb5927cbbf52715153686b772f843d843c384cbd422e628d220`;
- exported `lib/copperbench.jar` SHA-256: `fafcd1f396f9aca6666aaab87ff2b2d85d237b359243dc1444b275746ff1b8d9`;
- injected failure: process exit `10`, product/task state `failed`, stable diagnostic code `JAVA_COMPILE_ERROR`, path `/src/main/java/net/example/Stage13RepairProbe.java`, line `4`;
- repair: the source edited by resolving the diagnostic-provided workspace-relative path;
- rebuild: process exit `0`, product/task state `succeeded`, Gradle `BUILD SUCCESSFUL`, zero diagnostics.

This closes the Stage 13 representative create/modify → build failure → locate → fix → rebuild requirement for the Procedure/productivity path without requiring the user or Agent to manually search Gradle output directories.

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
- `DesktopMcpAgentLoopTest.externalAgentCanFailLocateRepairAndRebuildCodeThroughDesktopMcp` — passed; the Agent repairs `/code` through `update_mod_element` after locating `JAVA_COMPILE_ERROR`, then rebuilds successfully through the same desktop MCP session.
- `ProcedureIrScaleGateTest` with `-Dcopperbench.stage9.scale=true` — passed for the 500-node Procedure baseline.
- `WorkspaceReferenceIndexScaleTest` with `-Dcopperbench.stage9.scale=true` — passed for the 2,000-element / 10,000-reference baseline after readable edge metadata was added.
- fresh `exportWin64 -x test` — passed and produced the exported product used by the final repair-loop replay.
- `scripts/verify-stage13-productivity-repair-loop.ps1` against that fresh export — `pass=true`; failure/location/repair/rebuild evidence is stored at `evidence/stage13/2026-09-05/procedure-failure-repair-loop.json`.

The isolated Stage 13 worktree originally did not contain its own bundled JBR. The final fresh-export run therefore exercised the canonical `downloadJDKWin64` dependency and populated `jdk/jbr25_win_64` before packaging; subsequent local test runs used that worktree-local runtime. Missing non-Windows JDK path warnings are expected in this Windows-only export workspace and did not affect the accepted tests.

## Closure

`FR-PRODUCTIVITY-01` is complete on this development line: Procedure discovery/navigation, Core-owned symbol and relationship views, node-addressable validation, protected semantic refactors and impact review, 500-node performance coverage, shared UI/MCP Core semantics, and the representative product failure-repair-rebuild flow all have executable evidence. Stage 13 remains active for `FR-PRODUCTIVITY-02` through `FR-PRODUCTIVITY-06`.
