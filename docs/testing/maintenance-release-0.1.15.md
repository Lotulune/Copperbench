# Copperbench 0.1.15 release preparation

The user requested publication for both Windows and Linux. The 0.1.14 signed source failed one native diagnostics test before packaging; neither platform published 0.1.14 binaries. Its [failed-build record](maintenance-release-0.1.14.md), signed tag and frozen Linux evidence remain intact. The corrected release uses new 0.1.15 identities.

## Change and scope

The native diagnostics test now selects professional mode before using its professional Help entry. It retains all ZIP-content, redaction and exclusion assertions, and is added to the ordinary Windows PR conformance job so this failure is caught before signing a release tag. Product behavior is unchanged by this test correction.

Application configuration, UI handshake/help, package locks and the first-party release inventory move together to 0.1.15. The release includes the simple-creation and workbench continuity changes from PRs #140 and #138. Generator support classifications and historical evidence remain unchanged. The [previous public Windows release](../../evidence/maintenance/2026-10-21/release-0.1.15/previous-release.json) is still 0.1.13.

## Publication requirements

Fresh 0.1.15 branch/main checks, signed latest-main Windows packaging and a new independently verified Linux candidate are required. The successful 0.1.14 Linux candidate is not relabeled as 0.1.15. Published download declarations stay at the observed 0.1.13 releases until new assets are verified. No new complete installed lifecycle or player-controlled gameplay acceptance is claimed by preparation checks.

## Local preparation verification

Seven focused Java tests passed, including the corrected real Windows JCEF diagnostics export and the native accessibility flow, with no failures or skips. The production UI and JAR build, 50 UI-Core tests, 73 UI unit tests, 6 Chromium help/update/layout cases, 3 release-history tests, localization, product-status and Markdown link checks passed. Logs are retained under `output/release-0.1.15/` in the independent release worktree. Fresh cloud source and package gates remain required.
