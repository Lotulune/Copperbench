# Stage 13 Asset Center evidence — 2026-09-05

## Scope

This page tracks `FR-PRODUCTIVITY-02 Asset Center` on the active Stage 13 development line. The requirement is not closed yet.

The first Stage 13 Asset Center slice builds on the Stage 6/8 asset foundation and adds Core-owned asset health and reverse-usage semantics:

- the existing real `AssetWorkspaceService` remains the source for workspace asset indexing, stable IDs, categories, hashes and reference parsing;
- `list_assets` now returns per-asset health metadata rather than requiring React to derive asset truth independently;
- health metadata includes inbound/outbound reference counts, stable issue codes and a conservative static-unused candidate flag;
- workspace-level health summarizes missing references, invalid documents, path escapes and static-unused candidates;
- reverse usage is projected explicitly, so consumers can answer both “what does this asset use?” and “what uses this asset?”;
- desktop MCP `inspect_asset_references` exposes incoming references and the same Core-owned asset health;
- the Asset Center UI adds error/warning/static-unused filters and separates inbound usage from outbound dependencies in the detail view;
- static-unused candidates are intentionally not upgraded to warnings/errors yet, because asset-file references alone cannot prove that Mod Elements or generator metadata do not use a resource.

Existing Stage 6/8 behavior retained by this slice includes unified category browsing/search, asset preview metadata, missing/invalid/path-escape diagnostics, the managed Blockbench process boundary, JCEF Blockbench bridge and MCP asset queries.

## Verification

- `npm run build` — passed; TypeScript, Vite and the Chinese localization gate (`193/193`) completed successfully.
- `AssetWorkspaceServiceTest` — passed, including reverse usage, health projection, missing references, invalid JSON, path escape and generated resource-root behavior.
- `AssetQueryProjectionTest` — passed, including per-asset health and workspace health projection through UI-Core.
- `McpHttpServerTest` — passed, including `assetHealth` and `incomingReferences` on `inspect_asset_references`.
- `npx playwright test e2e/asset-browser.spec.ts` — passed; the browser covers the Stage 13 health/static-unused filter in addition to the existing category/search/Blockbench/state tests.

## Remaining `FR-PRODUCTIVITY-02` work

- duplicate-asset detection with an explainable hash/path basis;
- safe-unused classification that also accounts for Mod Element / workspace references before cleanup can be offered;
- drag/drop and batch-import planning with target-path/conflict preview before writing;
- Blockbench save/exit refresh that creates a recovery point and refreshes preview/reference state automatically;
- reference-safe asset rename/move with impact preview and no silent dangling references.

The existing static-unused candidate flag is discovery information only and must not be used as an automatic deletion decision.
