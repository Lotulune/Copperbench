# Stage 17 release integration — 2026-09-26

The user approved a non-prerelease stable release and the minimal release-workflow changes. Publication is pending validation; this report does not claim that release assets are already public.

## Source and local checks

- Stage 17 implementation: `398904c`.
- Integration with upstream `3e8714a`: `84fcc7bf30b9d28a825bc2eee19810e4cc56d7e3`.
- UI production build passed; 385 referenced contract keys and 1,669 authored English messages passed localization checks.
- 118 Playwright checks passed at 1920×1080 and 1366×768, covering native locale persistence and failure, draft retention, diagnostics, task history, modeled-asset workflows, Procedure return connections, and trigger catalogs.
- Windows signed-source gate tests passed, including approved stable declarations, mismatched tags, pending stable blockers, existing Beta exact-binary promotion, and draft asset digests.
- Linux authorization contract tests: 16 passed, retaining all ten installed acceptance gates and legacy-preference migration requirements for stable tags.
- Public issue tracker inspection returned no open issues at preparation time. This is not a claim of absence of unknown defects.

## Integrated-source CI

[PR #76](https://github.com/Lotulune/Copperbench/pull/76) passed the required checks in [run 36242472116](https://github.com/Lotulune/Copperbench/actions/runs/36242472116) on `6907855ca8670d65514f2ba3125f265592c760db` and merged to `341581261f847e5aee89e78896276f31b764532c`. The merge tree is identical to the tested PR head.

- Java/Javadoc: 985 tests, 919 passed, 66 explicitly skipped, zero failed. Duration: 9m22s including job setup.
- UI contract/build/smoke and repository Markdown links: passed (1m36s).
- MCP conformance and release-source regression checks: passed (3m25s).
- Linux release authorization contracts: passed (11s).
- Additional local native locale/bridge regressions: four passed against the merged product code.

This closes the integrated-source gate. Stable Windows readiness is approved for this code; publication still requires green main/documentation checks and the signed-tag production packaging workflow. Linux stable publication remains pending a fresh exact-candidate installed acceptance. The old Linux authorization stays bound to Preview 2 and cannot release these new bytes.

## Publication gates

Main-branch replay and documentation checks must pass before publishing the signed release tag. New packages must retain their own source commit, SHA-256, SBOM and provenance. Linux publication additionally requires fresh installed evidence bound to the exact CI candidate; previous run42 and local v38 receipts cannot authorize different bytes.

The [v38 development acceptance](stage-17-v38-closeout-2026-09-26.md) remains historical, scoped evidence: 924 Java passes, 59 conditional skips, installed/default probes and bounded Minecraft process-boundary persistence. Integration changes the UI and native locale bridge, so that acceptance does not certify the rebuilt release binaries. No fixed-player UUID inventory continuity, cold-cache/offline dependency coverage, new external trial, or new screen-reader certification is inferred.

## Packaging failure propagation

The Windows release workflow explicitly checks the exit code after each native dependency, Java test, UI test/build and packaging command. A local PowerShell probe confirmed that `ErrorActionPreference=Stop` alone continues after a native exit code of 7; a subsequent successful native command could otherwise hide that failure. All existing release checks remain enabled.
