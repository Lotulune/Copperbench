# 0.1.7 export regression review and release

The user requested a review of recent commits, bug fixes and a new release package. Review baseline: `91307e46121e200338b5b95a7bb1673e4df4b4c3`. The original dirty checkout is preserved; implementation uses a separate worktree based on `origin/main`.

## Confirmed findings

| Finding | Trigger and effect | Correction and regression |
| --- | --- | --- |
| P2 — Data-pack default export format | Native data-pack workspaces use `kind=mod` in the frozen contract. The new delivery UI selected `.jar` from that kind, but the data-pack backend requires `.zip`, so the default export failed. | Select the extension using the generator ID as well as workspace kind. Browser cases cover all three supported data-pack generator IDs, initial and renewed paths, and the actual submitted export payload. |
| P2 — Custom Gradle archive directory | A valid declared mod archive under `build/distributions` with a custom external delivery path was rejected solely because `build/libs` did not exist. | Mod export relies on its validated archive-task receipt. The existing shape check remains for ordinary builds and fixed-output pack backends. Java regression verifies bytes, task success and input identity without `build/libs`. |

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

Required PR/main CI and the existing signed-tag package workflows remain pending at this preparation stage. Publication outcomes will be recorded separately.

The [previous release state](../../evidence/maintenance/2026-10-16/publication-0.1.7/previous-release-state.json) preserves 0.1.6 publication and Linux maintenance identities. Historical acceptance gates remain tied to their original artifacts. No fresh final-package graphical installation or player-controlled Minecraft/same-world-reopen acceptance is claimed for this export-only patch.
