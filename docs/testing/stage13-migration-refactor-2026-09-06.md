# Stage 13 Migration / Refactor Workbench evidence — 2026-09-06

## Scope

This page records closure evidence for `FR-PRODUCTIVITY-05 Migration / Refactor Workbench`. The implementation preserves the existing copy-only migration model and reuses shared WorkspacePlan, reference-index and recovery mechanisms instead of introducing a parallel refactor engine.

The implementation deliberately compares only facts Copperbench can prove from the two workspace trees:

- requested generator transition versus the generator actually written to the target copy;
- workspace metadata after removing only the generator-owned fields that migration intentionally rewrites;
- `elements/*.mod.json` definitions using parsed JSON equality when possible, so formatting/property-order changes are not reported as semantic changes;
- added, removed, changed and preserved Mod Element definitions.

Generated Java, Gradle state, build output and other derived files are not presented as domain-level semantic changes.

## Product behavior

- Migration preview remains a capability/disposition review and returns `semanticComparison=null`; Copperbench does not claim to compare a target workspace that has not been materialized yet.
- A successful real copy migration computes the comparison after generator metadata has been rewritten in the target and after the source-tree hash is rechecked.
- The result is persisted in `migration-report.json` and returned through the shared Core command result.
- The Migration view renders the comparison only for committed results, including:
  - whether the target generator was actually applied;
  - whether non-generator workspace metadata was preserved;
  - preserved / modified / added / removed element counts;
  - a bounded list of concrete semantic changes.
- The source workspace remains untouched; semantic comparison does not introduce any new mutation or fallback path.

## Verification

- `LoaderMigrationServiceTest` + `Stage67ApplicationServiceTest` with `--rerun-tasks` -> `BUILD SUCCESSFUL`; real tree migration preserves the source hash, writes the requested target generator, reports preserved workspace metadata/elements, and persists the semantic comparison.
- `npm test` in `ui-core` -> `20/20` passed; the existing v1.0 contract remains compatible with the optional migration comparison projection.
- `npm run build` in `ui-shell` -> passed TypeScript, Vite and Chinese localization (`224/224` referenced keys); only the existing chunk-size warning remains.
- `npx playwright test e2e/u3-tracks-migration.spec.ts` -> `12/12` passed across Chromium and compact-1366. Preview does not show a fabricated comparison; execute shows the real committed comparison.

## Unified Refactor Workbench slice

The broader FR05 audit found that Copperbench already had the required mutation mechanics, but they were spread across specialist screens. This slice adds one shared Refactor tab under Tracks / Migration without introducing a second refactor engine:

- Registry rename loads the existing registry projection, calls `preview_registry_rename`, and creates the existing recovery-protected `WorkspacePlan` for `rename_registry_entry`. The workbench displays impacted elements, reference edges, changed paths and plan safety before applying the reviewed plan through `apply_workspace_plan`.
- Asset rename / move loads the existing Asset Center projection and calls `preview_asset_move`. The workbench displays inbound-reference counts plus the exact JSON Pointer rewrites already computed by `AssetMoveService`, then applies the reviewed move through the existing recovery-protected `move_asset` command.
- Procedure extraction, batch call-target replacement and batch resource-target replacement remain in Procedure Workbench because that specialist editor already uses the shared protected `plan_procedure_refactor` / `WorkspacePlan` path. The unified page links the capability conceptually instead of duplicating a second Procedure editor.

Verification for this slice:

- `npm run build` in `ui-shell` -> passed TypeScript, Vite and Chinese localization (`224/224`); only the existing chunk-size warning remains.
- `npx playwright test e2e/stage13-refactor-workbench.spec.ts` -> `4/4` passed across Chromium and compact-1366. Registry preview -> WorkspacePlan -> apply and Asset exact-reference preview -> apply are both exercised.
- Existing `npx playwright test e2e/u3-tracks-migration.spec.ts` remains `12/12` across the same two viewport projects after the new fifth tab was added.

## Agent parity and closure

The final parity audit found that the MCP catalog already exposed the same Core capabilities used by the desktop workbench, but the dependency-free Python and TypeScript SDKs did not wrap all of them. Both SDKs now expose:

- `preview_registry_rename` / `previewRegistryRename`;
- `plan_procedure_refactor` / `planProcedureRefactor`;
- `list_assets` / `listAssets`;
- `preview_asset_move` / `previewAssetMove`;
- `move_asset` / `moveAsset`.

This remains a thin transport surface: Procedure and Registry plans still apply through the existing `apply_workspace_plan`, and Asset moves still apply through the existing reviewed plan token. No new SDK state machine or second refactor abstraction was added.

Parity verification:

- Python SDK `unittest` -> `3` tests passed, including direct forwarding checks for all five refactor methods.
- `node scripts/verify-ai-evals.mjs` -> passed; the static SDK contract now requires all five methods in both languages while retaining the existing ten-case live-eval manifest.
- standalone TypeScript SDK compile -> passed with Node type roots supplied from the repository UI toolchain.
- `McpHttpServerTest` + `DesktopMcpAgentLoopTest` -> `BUILD SUCCESSFUL`; real loopback MCP exposes and executes protected Procedure refactor planning plus reference-safe Asset preview/apply.

`FR-PRODUCTIVITY-05` is closed. The PRD requirements are covered by the combined workbench rather than one monolithic editor: capability/disposition migration preview, copy-only execution, source-to-target semantic comparison, Registry rename impact + protected WorkspacePlan, Asset move impact + exact structured reference rewrites, and Procedure batch replacement/extraction plans all use shared Core semantics and are available from UI and MCP/SDK surfaces.
