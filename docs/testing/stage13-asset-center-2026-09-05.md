# Stage 13 Asset Center evidence — 2026-09-05

## Scope

This page records closure evidence for `FR-PRODUCTIVITY-02 Asset Center` on the Stage 13 development line. The implementation and the final clean-installed Windows Explorer-to-Copperbench drag/drop replay are complete.

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
- multi-file import now uses the native multi-select picker to issue up to 64 short-lived source grants without exposing external absolute paths to browser code;
- the batch preview reviews every target together, reports CREATE/REPLACE/IDENTICAL counts, blocks duplicate target paths inside the same batch and requires explicit confirmation if any item will replace an existing asset;
- batch apply revalidates every item, snapshots all changed source bytes before the first workspace write, then commits the full batch with one recovery point, one workspace revision and one `assets_imported` event; this also prevents an earlier target from overwriting a later item’s source bytes;
- IDENTICAL batch items are explicit no-ops rather than failures, and any stale source/target or write failure rejects or restores the whole batch instead of leaving a partial import;
- the JCEF component now owns an OS file `DropTarget`; dropped files are converted to the same short-lived Core grants on the Java side and only grant metadata is emitted to React through `copperbench:asset-drop`, so browser code never receives the external absolute paths;
- dropped grants enter the exact same atomic batch preview/apply path as native multi-select, including editable targets, conflict review, replacement confirmation, staging, one recovery point and one revision;
- asset rename/move is preview-first and reference-safe: the review lists every exact structured-document rewrite by source path + JSON Pointer before apply;
- move plan tokens are workspace-bound and short-lived; apply rescans source/reference hashes, blocks stale plans and target/category/resource-root conflicts, creates one recovery point and advances one workspace revision;
- post-move validation requires the target asset to be re-indexed under its previewed stable ID and rejects any old-path dangling reference; UI and MCP use the same Core move planner/apply path;
- managed Blockbench editing now creates a Core-owned recovery point before the external process is launched; recovery creation, opened revision and event-sequence allocation occur under the same workspace revision lock;
- when Blockbench exits with a changed `.bbmodel`, Core re-indexes the edited asset, advances exactly one workspace revision, publishes `asset_external_edit_committed`, refreshes asset health/reference state, and returns the recovery/revision metadata through the narrow JCEF bridge;
- Asset Center polls Blockbench only while a managed session is active, stops on exit, surfaces the resulting recovery/revision state and automatically reloads the asset catalog after a committed save;
- safe-unused classification now layers the shared workspace reference index and conservative workspace text signals on top of asset-file references; embedded resource locations inside ordinary Mod Element strings count as usage;
- `safeUnused` is deliberately narrower than static `unused`: the first cleanup-assessed slice only covers model/texture assets, excludes assets with error diagnostics, and disables the entire cleanup assessment when raw code, non-first-party element types or oversized unexhausted text signals are present;
- generator/upstream/registry strings are included as conservative usage signals, so uncertain workspaces degrade to “static unreferenced / cleanup not assessed” instead of producing a false safe-delete claim;
- Asset Center exposes static-unused and safe-cleanup candidates as separate filters and does not perform automatic deletion.

Existing Stage 6/8 behavior retained by this slice includes unified category browsing/search, asset preview metadata, missing/invalid/path-escape diagnostics, the managed Blockbench process boundary, JCEF Blockbench bridge and MCP asset queries.

## Verification

- `npm run build` / forced Gradle UI build — passed; TypeScript, Vite and the Chinese localization gate (`200/200`) completed successfully.
- `AssetWorkspaceServiceTest` — passed, including reverse usage, exact duplicate-content groups, health projection, missing references, invalid JSON, path escape and generated resource-root behavior.
- `AssetQueryProjectionTest` — passed, including per-asset health, duplicate summary/issue projection, embedded Mod Element resource usage blocking safe cleanup, and raw-code workspaces disabling cleanup assessment.
- `McpHttpServerTest` — passed, including `assetHealth` and `incomingReferences` on `inspect_asset_references`.
- `AssetImportServiceTest` / `AssetImportApplicationServiceTest` / `JcefAssetImportBridgeTransportTest` — passed, including stale-plan rejection, workspace-scoped tokens, replacement confirmation, recovery restore, external-path non-disclosure and symlink/junction protection.
- `AssetImportBatchServiceTest` / `AssetImportApplicationServiceTest` / `JcefAssetImportBridgeTransportTest` — forced rerun passed, including mixed CREATE/REPLACE/IDENTICAL preview, duplicate-target blocking, whole-batch stale rejection, overlapping source/target staging, native multi-grants, batch-token workspace isolation, one revision and one recovery point.
- `JcefAssetImportBridgeTransportTest` — passed with a drop-event wire assertion that grant metadata is delivered without any `sourcePath`/external-path field; the bridge restores the prior JCEF component DropTarget when closed.
- `AssetMoveServiceTest` / `AssetMoveApplicationServiceTest` — passed, including exact JSON Pointer rewrites, stale-source rejection before recovery/write, target/category/resource-root blocking, workspace-bound move tokens, one-revision apply and recovery restore.
- `McpHttpServerTest.externalAgentCanPreviewAndApplyReferenceSafeAssetMove` — passed through the real loopback MCP server, including preview, apply, reference rewrite and recovery point return.
- `BlockbenchProcessServiceTest` / `BlockbenchEditApplicationServiceTest` / `JcefBlockbenchBridgeTransportTest` — forced rerun passed; recovery preparation precedes process start, completion runs once, the Core save registers one revision/event, recovery restores the pre-edit file, and the wire contract carries explicit recovery/revision metadata. The optional real-installed-Blockbench smoke remains skipped unless `copperbench.blockbench.executable` is supplied.
- `WorkspaceReferenceIndexScaleTest` with `copperbench.stage9.scale=true` — passed at 2,000 elements / 10,000 references (`initial=123ms`, `repeat=47ms`, `P95=28ms`) after embedded resource-location scanning was added.
- `npx playwright test e2e/asset-browser.spec.ts` — `34 passed` across Chromium and compact-1366; the browser covers health/static-unused/safe-cleanup/duplicate filters, single-file CREATE/REPLACE import, mixed CREATE/REPLACE batch review, intra-batch target collision blocking, native dropped-grant routing into the same batch preview, the >=32px batch/move review interaction target baseline, exact move-reference review, and managed Blockbench save-exit auto-refresh with recovery/revision feedback.

## Installed-product closure evidence

The remaining physical OS-drop boundary was closed on clean Windows 11 with `scripts/Invoke-Stage13AssetDropGuestGate.ps1` against exact installed candidate `09bda9c6d8c6a37bbcf15d7cf7c964c68abea2a4`, installer SHA-256 `bc59026c08c635b7b06c90e434fe3de80d639e4042288b0a20b2445e16ad6b1a`.

- the gate silently installs that candidate to a separate product directory and launches Copperbench at the same ordinary interactive integrity level as Explorer rather than elevating the product and invalidating Windows drag/drop behavior;
- before judging Copperbench, the gate selects the real `dropped_panel.png` Explorer item through UI Automation and proves the mouse input produces a genuine Windows Shell/OLE drag by transferring that same item Explorer-to-Explorer;
- the gate then opens Asset Center through the installed JCEF UI and drags the same real Explorer item into Copperbench; no synthetic `copperbench:asset-drop` browser event is used for the closure assertion;
- the reviewed batch plan opens and its real target input contains `assets/stage13_diagnostics/textures/imported/dropped_panel.png`, proving the native `DropTarget -> Core source grant -> copperbench:asset-drop -> atomic batch preview` path completed;
- evidence intentionally records the external file name and reviewed workspace target only. The external source path is not exposed as browser/product evidence, matching the grant-only security boundary;
- the final default **clean-install** run returned `passed=true` with all six steps green. Machine evidence is stored at `evidence/stage-13/2026-09-07/asset-drop-clean-windows11.json`; the companion screenshot is `asset-drop-clean-windows11.png`.

`FR-PRODUCTIVITY-02` is therefore closed. Broader asset-move coverage for future non-JSON or generator-specific reference kinds remains ordinary follow-up work rather than a Stage 13 blocker; current structured JSON/resource-ID references are protected.

Static-unused remains discovery information only. Only the narrower Core-owned `safeUnused` slice is presented as a cleanup candidate, and no automatic deletion action is implemented.
