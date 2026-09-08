# Stage 13 History / Recovery UX evidence — 2026-09-06

## Scope

This page records closure evidence for `FR-PRODUCTIVITY-04 Local History / Recovery UX` on the Stage 13 development line. The implementation and the final clean-installed Windows product replay are both complete.

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

## Semantic history and restore validation

- History file changes now project deterministic semantic objects only where Copperbench already owns the path convention: Mod Element JSON, workspace metadata and supported asset locations. Unknown files remain raw file changes instead of receiving guessed ownership.
- Modified `elements/*.mod.json` snapshots additionally expose JSON-Pointer field changes when both sides are valid JSON. Added/deleted elements remain object-level changes rather than exploding into artificial per-field deltas.
- Restore no longer relies on a broad post-checkout clean. The current non-ignored worktree is staged into Copperbench's isolated history index before checkout, so target-missing tracked files are removed precisely while ignored upstream `.mcreator/localHistory`, build and run state remain untouched.
- After restore, Copperbench reruns existing element-value validation and the workspace reference index. The restore remains committed even when the historical state contains problems, while proven invalid fields and dangling required references return actionable diagnostics and field/node location actions.
- The History timeline now supports text, source and actor filtering without changing which adjacent recovery points are compared for the selected point.

## Current-snapshot truth after external edits

- `JGitLocalHistoryService.currentRecoveryPointId()` compares the actual current worktree tree against recovery-point trees instead of assuming the newest recovery point is current.
- History UI now issues a fresh `get_history` query when the History view opens. This covers edits performed outside the running Copperbench event stream and replaces any stale cached `currentRecoveryPointId` through the existing JCEF bridge state projection.
- The bridge regression explicitly verifies `currentRecoveryPointId=null` from `get_history` replaces a stale non-null value and notifies state subscribers.
- JGit regressions cover external tracked-file edits across service reopen and content rewrites that deliberately preserve both file size and last-modified timestamp; content hashing must still invalidate the current recovery point.

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
- focused `LocalHistoryServiceTest` on the current line -> `5/5` passed, including external edit/reopen/restore and same-size+same-timestamp rewrite coverage.
- `node --test tests/bridge-logic.test.mjs` -> `5/5` passed, including stale-current replacement via `get_history`.
- current History/Recovery Playwright selection (`stage2-history-approval` + `stage13-history-filters`) -> `6/6` passed across Chromium and compact-1366.
- current `npm run build` in `ui-shell` -> passed TypeScript/Vite and Chinese localization at `224/224`; only the pre-existing Vite chunk-size warning remains.

## Installed-product acceptance status

`FR-PRODUCTIVITY-04` is closed by the final G7 Windows 11 replay from committed candidate `b4eddaff4861d9d4a26031d99536cac140717f12`.

- final installer: `Copperbench 0.1.0 Windows 64bit.exe`
- size: `722,725,347` bytes
- SHA-256: `1afe289a8c7b07b1736e0101090c02948c9056a9404e47b31810b2bd70659d3b`
- clean install/start smoke: `passed=true`, `cleanGuest=true`, `installPassed=true`, `productProcessStarted=true`, `ipcFailureDetected=false`, screenshot captured
- installed History gate: `passed=true`, recovery point created by desktop UI, external tracked sentinel mutation preserved across relaunch, restore confirmation reached, sentinel bytes restored exactly, installed headless validation returned `status=succeeded`
- structured evidence: `evidence/stage-13/2026-09-06/history-recovery-clean-windows11.json`
- screenshots: create dialog, restore confirmation and post-restore product state under the same evidence directory

The final replay also closed a product-shell defect discovered by this acceptance work. G7 exposes only `Microsoft Hyper-V Video`; Chromium WR repeatedly failed Vulkan/SwANGLE surface creation and left the product shell gray even though the Swing frame existed. Copperbench now keeps native WR on normal GPU-backed Windows desktops, but automatically selects the existing OSR path when every active adapter is a known software-only Hyper-V/Basic Display device. The software path uses ANGLE D3D11 WARP and disables Vulkan. The same fallback remains available when the user explicitly disables GPU acceleration.

The final clean-installed replay proves this happens without user intervention: `gpuAccelerationPreference=true`, `softwareDisplayFallbackObserved=true`, `jcefMode=OSR`, first render ready in `194 ms`, and the second launch ready in `36 ms`. On the normal development host, `Stage9NativeJcefAccessibilityTest` logs `Initializing JCEF in WR mode` and passes, so native Windows accessibility remains on the ordinary GPU-backed path.

The acceptance gate itself now uses a dedicated `stage13-history-sentinel.txt` in the workspace root. It is covered by LocalHistory but not rewritten by Copperbench during workspace open, making byte-for-byte restore verification deterministic. Product-managed `.mcreator` metadata is intentionally not used as a restore sentinel.

## `FR-PRODUCTIVITY-04` closure

No remaining FR04 implementation or installed-product acceptance item is open. Subsequent Stage 13 work can proceed to the next roadmap slice without carrying History/Recovery as a release blocker.
