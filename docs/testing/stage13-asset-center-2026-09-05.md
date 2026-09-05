# Stage 13 Asset Center evidence — 2026-09-05

## Scope

This page tracks `FR-PRODUCTIVITY-02 Asset Center` on the active Stage 13 development line. The requirement is not closed yet.

The first Stage 13 Asset Center slice builds on the Stage 6/8 asset foundation and adds Core-owned asset health and reverse-usage semantics:

- the existing real `AssetWorkspaceService` remains the source for workspace asset indexing, stable IDs, categories, hashes and reference parsing;
- `list_assets` now returns per-asset health metadata rather than requiring React to derive asset truth independently;
- health metadata includes inbound/outbound reference counts, stable issue codes and a conservative static-unused candidate flag;
- workspace-level health summarizes missing references, invalid documents, path escapes and static-unused candidates;
- exact duplicate content is detected by `mediaType + SHA-256`; matching assets expose peer paths and `DUPLICATE_ASSET_CONTENT`, and the workspace summary reports duplicate groups/assets;
- reverse usage is projected explicitly, so consumers can answer both “what does this asset use?” and “what uses this asset?”;
- desktop MCP `inspect_asset_references` exposes incoming references and the same Core-owned asset health;
- the Asset Center UI adds error/warning/static-unused filters and separates inbound usage from outbound dependencies in the detail view;
- single-file import/replace is preview-first: the desktop picker returns a short-lived source grant instead of exposing an external absolute path to browser code;
- the import preview reports target path, CREATE/REPLACE/IDENTICAL conflict, hashes, duplicate peers and issue codes before any write;
- import plan tokens are short-lived, server-owned and workspace-bound; apply revalidates the source/target snapshot and requires explicit confirmation for replacement;
- import writes create a pre-change recovery point and roll back through local history if the write fails;
- target validation rejects unknown asset roots, extension mismatch, workspace escape and symlink/junction ancestors that resolve outside the workspace;
- legacy `.lang` assets are now indexed consistently and use `text/plain`, matching import-preview media typing;
- asset rename/move is preview-first and reference-safe: the review lists every exact structured-document rewrite by source path + JSON Pointer before apply;
- move plan tokens are workspace-bound and short-lived; apply rescans source/reference hashes, blocks stale plans and target/category/resource-root conflicts, creates one recovery point and advances one workspace revision;
- post-move validation requires the target asset to be re-indexed under its previewed stable ID and rejects any old-path dangling reference; UI and MCP use the same Core move planner/apply path;
- static-unused candidates are intentionally not upgraded to warnings/errors yet, because asset-file references alone cannot prove that Mod Elements or generator metadata do not use a resource.

Existing Stage 6/8 behavior retained by this slice includes unified category browsing/search, asset preview metadata, missing/invalid/path-escape diagnostics, the managed Blockbench process boundary, JCEF Blockbench bridge and MCP asset queries.

## Verification

- `npm run build` / forced Gradle UI build — passed; TypeScript, Vite and the Chinese localization gate (`200/200`) completed successfully.
- `AssetWorkspaceServiceTest` — passed, including reverse usage, exact duplicate-content groups, health projection, missing references, invalid JSON, path escape and generated resource-root behavior.
- `AssetQueryProjectionTest` — passed, including per-asset health, duplicate summary/issue projection and workspace health projection through UI-Core.
- `McpHttpServerTest` — passed, including `assetHealth` and `incomingReferences` on `inspect_asset_references`.
- `AssetImportServiceTest` / `AssetImportApplicationServiceTest` / `JcefAssetImportBridgeTransportTest` — passed, including stale-plan rejection, workspace-scoped tokens, replacement confirmation, recovery restore, external-path non-disclosure and symlink/junction protection.
- `AssetMoveServiceTest` / `AssetMoveApplicationServiceTest` — passed, including exact JSON Pointer rewrites, stale-source rejection before recovery/write, target/category/resource-root blocking, workspace-bound move tokens, one-revision apply and recovery restore.
- `McpHttpServerTest.externalAgentCanPreviewAndApplyReferenceSafeAssetMove` — passed through the real loopback MCP server, including preview, apply, reference rewrite and recovery point return.
- `npx playwright test e2e/asset-browser.spec.ts` — `22 passed` across Chromium and compact-1366; the browser covers health/static-unused/duplicate filters, CREATE and explicitly reviewed REPLACE import, exact move-reference review, blocked unchanged move, and the >=32px move-review interaction target baseline.

## Remaining `FR-PRODUCTIVITY-02` work

- safe-unused classification that also accounts for Mod Element / workspace references before cleanup can be offered;
- drag/drop and multi-file/batch import planning; the single-file preview/replace/recovery path is implemented;
- Blockbench save/exit refresh that creates a recovery point and refreshes preview/reference state automatically;
- broader asset move coverage for non-JSON or generator-specific references if future supported asset kinds introduce such references; current structured JSON/resource-ID references are protected.

The existing static-unused candidate flag is discovery information only and must not be used as an automatic deletion decision.
