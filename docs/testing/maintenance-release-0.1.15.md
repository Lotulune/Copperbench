# Windows 0.1.15 release — 2026-10-21

[Windows v0.1.15](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.15) was published at **2026-10-21T09:42:08Z** from signed source `d99aa3f4af171954a575f82de3afd1ee5b34c9a6`. The [0.1.14 signed build](maintenance-release-0.1.14.md) failed before packaging; its tag and candidate evidence remain unchanged, and no 0.1.14 binary release was published.

## Source and validation

[PR #143](https://github.com/Lotulune/Copperbench/pull/143) makes the native diagnostics test explicitly select professional mode and adds it to the Windows PR gate and matching contributor plan. All diagnostic ZIP/redaction assertions remain. Product version, UI handshake/help, lockfiles and first-party inventory are 0.1.15. The release includes the Simple creation and continuity changes from PRs #140 and #138.

| Check | Result |
| --- | --- |
| Preparation | 7 Java/native tests, 50 UI-Core tests, 73 UI unit tests, 6 Chromium help/update cases and 17 contributor-plan tests passed; builds, localization, release-history, product-status and Markdown links passed. |
| Main source CI | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39570341587). |
| Main documentation | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39570350648). |
| Full Windows source preflight | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39568762239) on `4a1cd1aae6a6a52a7af10a288a512e126c6e949f`; its tree `e24085c033c4b8d17470f7bfb016dd690bc1745a` exactly matches the merged binary source. Native diagnostics executed; JUnit XML reports no failures/errors. |
| Windows release workflow | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39573527054), including full release tests, EXE/ZIP/MSIX/update packaging, branding, payload hashes, SBOM/provenance and draft asset verification. |
| Public files | 15 uploaded assets verified; 4 update components match their descriptor. |

The public installer is **`Copperbench.0.1.15.Windows.64bit.exe`**, **503162564 bytes**, SHA-256 **`2b66e3c6e56a33f40569d19410be7580563086296aee5a73c1b9215ede7b9085`**.

## Published-byte verification

The [public release receipt](../../evidence/maintenance/2026-10-21/publication-0.1.15/windows/release.json), [verification result](../../evidence/maintenance/2026-10-21/publication-0.1.15/windows/verification.json), [checksum manifest](../../evidence/maintenance/2026-10-21/publication-0.1.15/windows/SHA256SUMS.txt) and [source metadata](../../evidence/maintenance/2026-10-21/publication-0.1.15/windows/RELEASE-METADATA.json) bind the public assets to this release. Every uploaded digest matches the checksum list. The installer, metadata, checksum list and update descriptor were downloaded and byte-hashed; all component names, sizes, hashes and the 0.1.15 descriptor version matched the API assets.

The downloaded installer's [signed attestation verification](../../evidence/maintenance/2026-10-21/publication-0.1.15/windows/installer-attestation.json) matched the exact source commit, `refs/tags/v0.1.15` and `.github/workflows/deploy.yml`. The [source-preflight identity](../../evidence/maintenance/2026-10-21/publication-0.1.15/windows/source-preflight.json) and [tree/test verification](../../evidence/maintenance/2026-10-21/publication-0.1.15/windows/source-preflight-verification.json) retain the pre-tag test binding.

The installer was downloaded for verification and was not executed. No new complete installed upgrade/uninstall, 360 rescan or player-controlled gameplay acceptance is claimed. Windows executables remain Authenticode unsigned. Tagged product-status and shipped documents retain their build-time declarations. Linux publication is recorded [separately](linux-publication-0.1.15.md); [current downloads](../releases/current.md) lists both platforms.
