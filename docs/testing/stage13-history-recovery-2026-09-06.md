# Stage 13 History / Recovery UX evidence — 2026-09-06

## Scope

This page tracks `FR-PRODUCTIVITY-04 Local History / Recovery UX` on the active Stage 13 development line. The requirement is **not closed yet**.

The first slices deliberately repair existing product truthfulness before adding broader history abstractions.

## Native history comparison

- `HistoryView` no longer relies on a mock-preloaded `historyComparison`.
- Selecting a recovery point now sends the production Core `get_diff` query for the immediately preceding recovery point and the selected point.
- The native JCEF regression proves the exact recovery-point IDs are sent and the returned changed path is rendered.
- Empty comparisons render an explicit no-change state instead of silently looking unloaded.

## Truthful restore-impact preview

- Restore confirmation no longer reuses the selected point's historical delta as if it were the impact of restoring the current workspace.
- `LocalHistoryService.previewRestore` stages the current working tree into the isolated Copperbench history index, writes a temporary tree object without creating a recovery-point commit, and diffs that tree against the target recovery point.
- The regression includes files modified or created **after** the newest recovery point, proving the preview represents the current workspace rather than the latest checkpoint.
- Core exposes this through `preview_recovery_restore`; Legacy, MCP and Headless adapters project the same result.
- The desktop restore dialog shows the exact current-workspace-to-target file changes and does not enable restore until that preview is available.
- External Agents receive the same query as the MCP tool `preview_recovery_restore`; `restore_recovery_point` remains protected and MCP cannot self-approve the desktop confirmation.

## Recovery-point provenance

Recovery points now persist a stable `Copperbench-Source` metadata header and project it into the History timeline. Current sources are:

- `manual`
- `automation`
- `workspace_plan`
- `procedure`
- `asset`
- `datagen`
- `registry`
- `blockbench`
- `restore_safety`

Existing history commits without the new header remain readable and project as `manual`. Refactor operations that already execute through the shared atomic WorkspacePlan path use `workspace_plan`; copy-only loader migration does not manufacture a recovery point for the untouched source workspace.

## Verification

- `LocalHistoryServiceTest` — passed; JGit source metadata round-trips and restore preview includes uncommitted current-working-tree files.
- `HistoryContractTest` — passed; history and restore preview remain identical across Legacy, MCP and Headless entry adapters.
- `WorkspacePlanEngineTest` — passed after source tagging, preserving recovery-protected atomic plan behavior.
- combined history / Core / plan run — `BUILD SUCCESSFUL`.
- `npm test` in `ui-core` — `20/20` passed; v1.0 schemas compile and legacy v0.1 fixtures remain valid.
- `npm run build` in `ui-shell` — passed TypeScript/Vite and the Chinese localization gate at `224/224` referenced keys.
- targeted `stage2-history-approval` + native `jcef-bridge` Playwright run — `4/4` passed across Chromium and compact-1366. The native case proves `get_diff`, `preview_recovery_restore`, exact restore-impact rendering and source-label rendering.
- `McpHttpServerTest` + `HistoryContractTest` — `BUILD SUCCESSFUL`; authenticated loopback MCP advertises and executes `preview_recovery_restore` before the protected restore request.
- `git -c core.whitespace=cr-at-eol diff --check` — passed for both implementation slices before commit.

## Remaining `FR-PRODUCTIVITY-04` work

This does not close History / Recovery UX. Remaining work is intentionally functional:

1. add semantic Mod Element / field / asset differences where the old and new snapshots provide enough structured evidence; do not infer semantics from arbitrary file names or diagnostic text;
2. surface those semantic affected objects in the restore preview, not only raw file paths;
3. run structural/reference validation after restore and make any proven broken references actionable;
4. add broader timeline filtering/grouping only after the semantic payload is useful;
5. gather an installed-product restore → revalidate acceptance replay before formal closure.

