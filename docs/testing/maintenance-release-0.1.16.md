# 0.1.16 integration and publication record

Date: 2026-10-22. Publication preparation is in progress; current public Windows and Linux downloads remain 0.1.15 until verified. The user authorized commits, pushes, processing both open PRs, and publishing new installers.

## Integrated changes

- Simple creation onboarding, bilingual runtime messages and guides, model catalog/manual selection, effort, attachments, session permissions, Markdown and history recovery: initial reviewed commit `11ceb6ae06441205c98fe96ce45672239b45c522`.
- [PR #146](https://github.com/Lotulune/Copperbench/pull/146): bilingual README badge rows; merged as `9b2e2e3e2d751c8d70f5bffa0b75b40ed3211418`.
- [PR #147](https://github.com/Lotulune/Copperbench/pull/147): preserve native scale failure evidence, omit inactive sidebar derivation, and prevent fast Stop from submitting another draft. Its exact candidate and diagnostic baseline passed the [full nightly and Windows A/B run](https://github.com/Lotulune/Copperbench/actions/runs/39704919193).
- Merge resolution preserves the newer model/effort/attachment/permission controls and the Stop default-action fix, adds the inactive-sidebar optimization, and makes the additional browser tests expand the current mode selector before choosing a mode.

## Evidence and scope

The [independent review](simple-mode-independent-review-2026-10-22.md) records five fixes and their relevant Java/UI/browser checks. The [final local candidate record](simple-mode-final-package-2026-10-22.md) covers a complete Windows + Codex workflow, two real Minecraft client processes, same-world persistence, ordinary export and application restart/continuation. It binds the pre-integration candidate ZIP/JAR and workspace artifact hashes; it is not an installed-acceptance claim for later official rebuilds.

Selected original [candidate evidence](../../evidence/maintenance/2026-10-22/release-0.1.16/simple-mode-candidate/archived-files.json), including task-input receipts, both normal-exit game logs, placement/reopen screenshots and artifact hashes, is retained in the repository. Complete local actions and observations remain under `output/minecraft-validation/sc04-final-review-20261022/`.

The [Windows scale comparison](../../evidence/maintenance/2026-10-22/release-0.1.16/pr147-native-scale/comparison.json) contains three alternating exact-source trials per variant on one runner. Candidate reference-refresh P95 was 130.9, 115.3 and 114.4 ms; baseline was 219.1, 222.8 and 220.4 ms. All six original JSON hashes, source identities, sample counts, P95 calculations and JUnit XML were checked. These are the recorded PR #147 measurements, not measurements of an untested rebuild.

## Release checks

The [local integration receipt](../../evidence/maintenance/2026-10-22/release-0.1.16/integration/verification.json) records 74 UI unit tests, 50 UI-Core tests, 36 Chromium/compact browser scenarios, seven comparison-harness tests, production UI build/localization, product-status and Markdown checks. Five evidence-writer JUnit tests and the actual Windows native scale gate passed with no skips. The integrated source measured reference-refresh P95 114.3 ms, element-filter P95 10.1 ms and Procedure search P95 8.5 ms. Browser layout screenshots were inspected. An initial local Gradle invocation failed during command-line path parsing before tests; the explicit argument-array invocation passed.

PR/main CI, documentation, full Windows preflight, signed tagging, official Windows packaging, and Linux exact-candidate authorization/publication will be recorded here as they finish. No final package hashes or public timestamps are asserted before the workflows and public assets are verified.

Full official installer upgrade/uninstall, 360 scanning, cold-cache installation, independent novice usability, Linux gameplay and other CLI full workflows are not newly claimed. Existing support gates retain their original scope; Linux maintenance publication must keep `formalSupportClaim=false` and exact frozen-byte provenance.
