# 0.1.7 export regression review and release

The user requested a review of recent commits, bug fixes and a new release package. Review baseline: `91307e46121e200338b5b95a7bb1673e4df4b4c3`. The original dirty checkout is preserved; implementation uses a separate worktree based on `origin/main`.

## Confirmed findings

| Finding | Trigger and effect | Correction and regression |
| --- | --- | --- |
| P2 — Data-pack default export format | Native data-pack workspaces use `kind=mod` in the frozen contract. The new delivery UI selected `.jar` from that kind, but the data-pack backend requires `.zip`, so the default export failed. | Select the extension using the generator ID as well as workspace kind. Browser cases cover all three supported data-pack generator IDs, initial and renewed paths, and the actual submitted export payload. |
| P2 — Custom Gradle archive directory | A valid declared mod archive under `build/distributions` with a workspace-relative delivery path outside build was rejected solely because `build/libs` did not exist. | Mod export relies on its validated archive-task receipt. The existing shape check remains for ordinary builds and fixed-output pack backends. Java regression verifies bytes, task success and input identity without `build/libs`. |

Review covered the recent 0.1.6 export/input-identity changes, SDK transport lifecycle, editor recovery and the latest native-scale fixture update. Only confirmed defects were changed. The review is not a claim that every possible bug was eliminated.

## Verification and publication

Local affected checks passed after implementation was complete. The [validation receipt](../../evidence/maintenance/2026-10-16/publication-0.1.7/local-validation.json) binds tested source, implementation files, logs and the application JAR. Raw local logs and the reviewed browser screenshot are under `output/review-release-017`.

| Check | Result |
| --- | --- |
| Java delivery, input identity, data-pack and resource-pack suites | 26 passed, 1 existing opt-in real-build case skipped; 0 failures/errors |
| Production UI and application JAR | Passed through the Gradle build |
| UI unit / UI-Core contract | 68 / 50 passed |
| Affected Chromium delivery suite | 18 passed, including all three data-pack generator IDs |
| Markdown links and product status | Passed |

The first Java attempt stopped at the host's documented loopback-pipe restriction. The existing external-process launcher resolved it; its first invocation then exposed an argument-order mistake (`--tests` followed `jar`). The corrected invocation passed. Both initial logs are retained, and neither was treated as a product test pass. Browser tests use a controlled native transport and do not create real workspaces or run Minecraft.

The preparation-stage checks above retain their original source identity. Final publication outcomes are recorded below.

The [previous release state](../../evidence/maintenance/2026-10-16/publication-0.1.7/previous-release-state.json) preserves 0.1.6 publication and Linux maintenance identities. Historical acceptance gates remain tied to their original artifacts. No fresh final-package graphical installation or player-controlled Minecraft/same-world-reopen acceptance is claimed for this export-only patch.


## Verified public publication

- Source A: `db8d08336c274cc37239b48e837f0bf3727c5fdc`; [PR #116](https://github.com/Lotulune/Copperbench/pull/116), main CI 38850069890 and documentation run 38850069819 passed. The PR also passed the real Fabric build/failure/repair/accept/export/reconnect workflow and Linux candidate checks.
- Windows [v0.1.7](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.7), release ID `414559349`, published `2026-10-16T05:08:48Z` by [run 38851412919](https://github.com/Lotulune/Copperbench/actions/runs/38851412919), attempt 1, on A. All ten public files were downloaded and matched API digests and the checksum list; the verified SLSA statement covers all ten. Four selected ZIP entries establish release-document bytes and application version 0.1.7. [Public verification](../../evidence/maintenance/2026-10-16/publication-0.1.7/windows/windows-public-verification.json) · [provenance](../../evidence/maintenance/2026-10-16/publication-0.1.7/windows/provenance-verification.json) · [archive inspection](../../evidence/maintenance/2026-10-16/publication-0.1.7/windows/archive-entry-verification.json).
- Linux [v0.1.7-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.7-linux-stable), release ID `414571270`, published `2026-10-16T05:29:42Z` by [run 38855720737](https://github.com/Lotulune/Copperbench/actions/runs/38855720737), attempt 1, on promotion source `bd2b733e5709bfe952eef33f30028a8d70a928bf`. [PR #117](https://github.com/Lotulune/Copperbench/pull/117) passed its checks and merged after Windows publication. Binary source stays A; candidate run 38850069834 and [the immutable report](maintenance-linux-0.1.7.md) retain the exact candidate identity. All nine public sizes/digests match the eight frozen files and approved authorization; three public metadata files were also downloaded and byte-compared. The protected promotion verified provenance and all nine downloaded draft files before publishing. [Public verification](../../evidence/maintenance/2026-10-16/publication-0.1.7/linux/linux-public-verification.json).

The local Windows status watcher encountered a GitHub API EOF after publication. Fresh run and public-release receipts confirmed success, and independent downloads continued; no release workflow rerun or public asset overwrite was performed. Historical acceptance and the immutable Linux candidate report were not changed. These publication records do not add installed-product or player-controlled gameplay acceptance.
