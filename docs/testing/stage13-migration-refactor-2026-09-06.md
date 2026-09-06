# Stage 13 Migration / Refactor Workbench evidence — 2026-09-06

## Scope

This page tracks `FR-PRODUCTIVITY-05 Migration / Refactor Workbench`. The requirement is not closed yet; this slice closes the missing **post-migration semantic comparison** part of the PRD while preserving the existing copy-only migration model.

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

## Remaining `FR-PRODUCTIVITY-05` work

Post-migration semantic comparison and the shared Registry / Asset refactor entry are implemented. FR05 remains active only for the final acceptance audit: confirm that Procedure specialist refactors and the unified entry together satisfy the PRD's batch rename / move / reference-replacement workflow from both desktop and Agent surfaces, and close any concrete parity gap found there. No generic AST/refactor engine is planned unless an actual unsupported workflow requires it.
