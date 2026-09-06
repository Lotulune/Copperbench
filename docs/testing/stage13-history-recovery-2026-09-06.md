# Stage 13 History / Recovery UX evidence — 2026-09-06

## Scope

This page tracks `FR-PRODUCTIVITY-04 Local History / Recovery UX` on the active Stage 13 development line. The local implementation is functionally complete; formal closure still waits for one installed-product replay from the current HEAD.

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

An earlier Windows G7 replay against the packaged `cfbe18fc` candidate proved that the installed desktop UI could open History and create a real recovery-point commit; the history HEAD changed from the pre-action commit to the new UI-created commit. That package is no longer the current development HEAD and therefore cannot close FR04.

While extending the replay to external-edit -> restore, the long-lived G7 guest exposed two acceptance-environment problems:

- repeated JCEF launches could reach the real 1024x768 Swing/JCEF window while the renderer remained the solid `#232323` placeholder for the entire bounded render wait;
- after rebooting the dedicated guest to clear that renderer state, Hyper-V Integration Services reported healthy but PowerShell Direct did not become usable within the bounded probe window, so the guest automation could not be continued reliably.

The gate itself was also corrected during this work: Chromium input now targets the renderer HWND rather than assuming global `mouse_event` delivery, and the acceptance mutation is required to use a LocalHistory-tracked workspace file instead of an arbitrary generated file that may be ignored by the workspace.

No current-HEAD installed-product pass is claimed from these interrupted runs.

## Remaining `FR-PRODUCTIVITY-04` work

The implementation-side work is now closed. Formal FR04 closure requires one remaining product gate:

1. rebuild the Windows installer from the current Stage 13 HEAD (not `cfbe18fc`);
2. run the clean installed-product flow on a responsive G7/G9 guest: UI create recovery point -> external tracked JSON edit -> reopen History -> verify the newest point is not labelled current -> preview impact -> desktop-confirm restore -> verify restored bytes -> installed headless validate succeeds;
3. archive that run's candidate commit, installer SHA-256, screenshots and structured result as the final FR04 evidence.
