# Windows 0.1.17 publication

[Windows v0.1.17](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.17) was published at **2026-10-22T11:44:31Z** from signed source `81563c9bc6ae4371b662a65b1b0bbf5ce9e5e4cb`. The [release workflow](https://github.com/Lotulune/Copperbench/actions/runs/39767389827) passed its test/Javadoc, package, branding, SBOM, source provenance, draft verification and publication gates.

## Changes and source verification

[PR #152](https://github.com/Lotulune/Copperbench/pull/152) and [release integration PR #153](https://github.com/Lotulune/Copperbench/pull/153) preserve legacy CLI session/model identity, update attachment errors with the UI locale, retain cleanup references after message truncation, and keep compact mode labels complete. Product configuration, npm metadata, About/handshake and first-party release fixtures are 0.1.17.

[Source regression](assessment-0.1.16-fixes.md): 72 Java tests plus Javadoc, 76 UI unit, 50 UI-Core and 102 browser cases passed. Version alignment separately passed ReleaseManifestTest, production UI build, the same unit/contract suites, About/help, product status and Markdown checks. The [merged-source main CI](https://github.com/Lotulune/Copperbench/actions/runs/39764178576) passed, including 510 Chromium cases, and [documentation generation](https://github.com/Lotulune/Copperbench/actions/runs/39764178751) passed on the same source. Earlier narrow-label failures and the Windows direct-launch limitation are retained in the scoped source record.

## Published assets

All 15 public asset digests match the [checksum manifest](https://github.com/Lotulune/Copperbench/releases/download/v0.1.17/SHA256SUMS.txt). All 15 files were independently downloaded and hashed by the [cloud verifier](https://github.com/Lotulune/Copperbench/actions/runs/39775072961). Local large-file transfers stalled, so complete local package downloads are not claimed. All 4 update components match the downloaded 0.1.17 descriptor. Raw public API, byte-hash, provenance and archive receipts remain under `output/release-0.1.17/public/windows/` in the release worktree; official release assets and workflow logs are the permanent retrieval points.

The independent verification harness is fixed at [26c11bdcafe779772fddc93d57491da1bde713e4](https://github.com/Lotulune/Copperbench/blob/26c11bdcafe779772fddc93d57491da1bde713e4/.github/workflows/public-windows-verification.yml). Its archive inspection confirmed the new session-model state, retained-attachment index, bilingual attachment messages and compact titlebar CSS inside the public JAR.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Copperbench.0.1.17.Windows.64bit.exe](https://github.com/Lotulune/Copperbench/releases/download/v0.1.17/Copperbench.0.1.17.Windows.64bit.exe) | 503304115 | `434510cec9395a0a4405425d98cff05d32d372dbe5af4955a7a960ad41c12a78` |
| [Copperbench.0.1.17.Windows.64bit.zip](https://github.com/Lotulune/Copperbench/releases/download/v0.1.17/Copperbench.0.1.17.Windows.64bit.zip) | 606487936 | `5f8f65a9cca87a9a54e8c998935d2f6f321084c5201be945c73b52bd615408a9` |
| [Copperbench.0.1.17.Windows.64bit.msix](https://github.com/Lotulune/Copperbench/releases/download/v0.1.17/Copperbench.0.1.17.Windows.64bit.msix) | 627179213 | `e1a375723f4d330482d2f1e90d25528260b1d12cda3fe0ca3b34d629914232d3` |
| Product JAR inside the ZIP | 8538061 | `1c0e0983eb36197f57d27dddb71c25b52faad986da60b434bd67dd93cf6b3e7f` |

The downloaded metadata/provenance and ZIP were checked against the tagged source; package identity and the fixes' packaged code are recorded with the public receipts. Windows executables remain Authenticode unsigned. Full installer/real-account upgrade, uninstall, security-software rescanning and Minecraft player acceptance were not repeated. Historical first-creation results retain their own candidate hashes.

[Linux publication](linux-publication-0.1.17.md) and [current downloads](../releases/current.md) record the separate platform workflow. Tagged/shipped documentation retains its build-time preparation text; this publication record does not alter released bytes.
