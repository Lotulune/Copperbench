# Windows 0.1.13 release — 2026-10-20

Windows [v0.1.13](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.13) was published at **2026-10-19T15:05:14Z** and confirmed as GitHub's latest release. [PR #131](https://github.com/Lotulune/Copperbench/pull/131) preserves existing shortcuts during in-app updates, avoids rewriting unchanged rollback files, and clarifies helper identity and localized installation guidance. [PR #132](https://github.com/Lotulune/Copperbench/pull/132) synchronizes 0.1.13 release metadata, help content and UI handshake identity.

The [native verification record](update-helper-2026-10-19.md) retains its source-bound previews, 20 Java checks, NSIS compiler invariant and real Windows Shell shortcut-preservation test. Release preparation additionally passed 4 Java checks and the production UI build, 50 UI-Core checks, 68 UI unit checks and 6 affected Chromium cases.

## Publication

| Check | Result |
| --- | --- |
| Signed source tag | `v0.1.13`, annotated tag object `22833ba648b6e41b2c3c180a75e3c137c41bb5b5` |
| Binary source commit | [`464b75a39c34252f61c7d859b8d267dfabba205c`](https://github.com/Lotulune/Copperbench/commit/464b75a39c34252f61c7d859b8d267dfabba205c) |
| Final PR regression | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39261567733), including 431 Chromium cases |
| M1 contracts and actual delivery chain | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39261567647) |
| PR Linux compatibility candidate | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39261567659) |
| Main regression | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39264954340) |
| Main documentation | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39265270715) |
| Main Linux compatibility candidate | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39264954357); no new Linux release |
| Windows release workflow | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39268386582), including full Windows tests, package generation, EXE branding, hashes, SBOM/provenance and uploaded-asset verification |
| Published payload | 15 uploaded files, including 4 update components and their descriptor |

The installer is **`Copperbench.0.1.13.Windows.64bit.exe`**, **503,045,963 bytes**, SHA-256 **`bbd4eed0995aac8ac723bee89234709c957980e7b85e90fed94f276be2e08da2`**.

The downloaded installer, [checksum manifest](https://github.com/Lotulune/Copperbench/releases/download/v0.1.13/SHA256SUMS.txt), [source metadata](https://github.com/Lotulune/Copperbench/releases/download/v0.1.13/RELEASE-METADATA.json) and [updater descriptor](https://github.com/Lotulune/Copperbench/releases/download/v0.1.13/copperbench-update-windows-x64.properties) were verified against GitHub upload digests. The checksum file covers every other uploaded asset, and all component sizes/hashes match the descriptor. Source metadata identifies the signed commit and release workflow above.

The installer was downloaded for byte verification and was not executed. No new 360 rescan, vendor review or full clean-account install/upgrade/uninstall was performed. Windows executables remain Authenticode unsigned. Reduced shortcut writes do not establish that all security alerts have disappeared. The running 0.1.12 application retains its old prompt until the upgrade and restart; the downloaded 0.1.13 update helper carries the new registration behavior.

The tagged product-status declaration retains its pre-publication wording. This record and the [download index](../releases/current.md) record delivery without modifying published bytes. Linux remains at 0.1.9, and the [0.1.12 record](maintenance-release-0.1.12.md) retains its original validation scope.
