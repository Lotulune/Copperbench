# Copperbench 0.1.17 release preparation

The user authorized merging the reviewed fixes, publishing new packages, and updating the README and documentation on 2026-10-22. This patch release follows the existing signed-tag Windows build and frozen-candidate Linux promotion workflows. Public downloads remain 0.1.16 until the respective release is verified and published.

## Included changes

[PR #152](https://github.com/Lotulune/Copperbench/pull/152) repairs legacy native-session continuation without model overrides, attachment error localization, attachment cleanup after visible-message truncation, and compact mode labels. The [source regression record](assessment-0.1.16-fixes.md) includes the exact source and artifact hashes, old-writer comparison and screenshots.

All current product identities are aligned to 0.1.17: native configuration, status declaration, npm metadata, UI handshake/About information and first-party release-note fixtures. Third-party dependency versions and historical support/acceptance records remain unchanged. The [prior release declaration](../../evidence/maintenance/2026-10-22/release-0.1.17/previous-release-state.json) is retained.

## Validation and release boundary

The fix passed 72 Java tests and Javadoc, 76 UI unit tests, 50 UI-Core tests, production build/localization, 102 distinct browser spec/project cases, Markdown and diff checks. Native CLI behavior was exercised with deterministic subprocess fixtures. The fixture JSON was also emitted by the actual v0.1.15 serializer and compared with the shared test input.

After aligning the version to 0.1.17, local verification passed the native `ReleaseManifestTest`, production UI build, 76 UI unit tests, 50 UI-Core tests, the About/help browser regression, product-status validation and Markdown links. The repository's existing external Gradle launcher was used on Windows.

The new version's merged-source CI, Windows package build, Linux candidate/consumer, signed publication and public-asset results will be recorded separately when they finish. This preparation record does not assert that 0.1.17 packages already exist. No new real-account installed upgrade, full installer lifecycle, player-controlled gameplay or independent novice study is claimed.

Release notes: [Windows](../releases/v0.1.17.md) · [Linux](../releases/v0.1.17-linux.md). Public platform state: [current downloads](../releases/current.md).
