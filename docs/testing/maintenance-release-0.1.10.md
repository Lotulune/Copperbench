# Windows 0.1.10 release — 2026-10-18

The requested release combines [PR #123](https://github.com/Lotulune/Copperbench/pull/123), which adds component updates and first-launch guidance, with the desktop presentation and branding changes reviewed in the browser. PR #123 was merged before preparing the release source. The Windows release workflow must build the signed latest-main tag; the local candidate below is not the public release artifact.

## Changes

- Locale-aware splash and native startup layout, themed and localized dialogs, and update checks in Help & About with an unobtrusive lower-left version notice.
- Tool, menu and page transitions with reduced-motion handling; native Windows outer-window rounding while retaining maximize and snap behavior.
- Shared copper workbench artwork across application, splash, installer, uninstaller, MSIX and macOS resources. NSIS MUI icon definitions prevent the default installer icon from replacing the product artwork.
- Independent Playwright server selection so validation does not reuse an unrelated preview process.

## Pre-release evidence

The presentation candidate is commit `4f6335e15875d5ba7b2dc5f67861b7a20352168f`, based on updater commit `c1167aa1a4f6bf97ddaada2c2734aad216f8c9be`.

| Check | Observed result |
| --- | --- |
| Focused Java checks | 60 passed, no failures or skips |
| UI unit tests | 68 passed |
| Affected Playwright cases | 25 passed against the candidate's own server |
| Product status and local Markdown links | Passed |
| Brand resource checks | 23 passed |
| Actual installer PE icon | Pixel comparison passed, maximum difference 0 |
| Complete-payload rehearsal | File install, upgrade, uninstall and user-file preservation passed |

The local complete installer candidate has SHA-256 `6711a5b639e231fee9819b7064b5e0c965a8da284f9b6265ff84666685af39fe`. The rehearsal did not perform OS registration or launch the application or a game. This candidate hash must not be presented as the hash of a later CI-built release.

The installer icon regression was first reproduced against the old generated EXE and then passed after setting the NSIS MUI installer and uninstaller icons. The same actual-EXE check is part of the release workflow.

- [Candidate verification receipt](../../evidence/maintenance/2026-10-18/display-brand-0110/verification.json)
- [Candidate source identity and file hashes](../../evidence/maintenance/2026-10-18/display-brand-0110/source-identity.json)
- [Previous public release state](../../evidence/maintenance/2026-10-18/display-brand-0110/previous-release-state.json)
- [Separate installed-updater and Chinese UI acceptance](in-app-update-2026-10-17.md), bound to the candidates and commits specified in that report.

## Publication

Windows [v0.1.10](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.10) was published as a public stable release at **2026-10-18T07:54:36Z** and confirmed as GitHub's latest release.

| Publication check | Result / evidence |
| --- | --- |
| Merged changes | [PR #123](https://github.com/Lotulune/Copperbench/pull/123) and [PR #124](https://github.com/Lotulune/Copperbench/pull/124) |
| Signed source tag | `v0.1.10`, annotated tag object `54b6070513be606b2ae84c31d1fd49c1c199dfe1` |
| Binary source commit | [`c3866a36163ca0ccd40d0029c0f2b5178d90c581`](https://github.com/Lotulune/Copperbench/commit/c3866a36163ca0ccd40d0029c0f2b5178d90c581) |
| Final PR regression | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39106032628), including 431 Chromium cases |
| Main regression | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39107530898) |
| Main documentation | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39107530808) |
| Main Linux compatibility candidate | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39107530827); no new Linux release |
| Official Windows release workflow | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39107596725), including full Windows tests, packaging, actual EXE branding, SBOM/provenance and draft-asset verification |
| Public payload | 15 uploaded files, including 4 update components and their descriptor |

The release follow-up aligned the shared version fixture and updater capability catalog; its 3 Java checks and 50 UI-Core checks passed. The remaining browser test now opens the updater from Help & About, and its host invocation passed before the final full PR regression.

The published installer is **`Copperbench.0.1.10.Windows.64bit.exe`**, **504,278,486 bytes**, SHA-256 **`aca4c2328691bc2a424bd3175b0b06499d5153390e98df25db77db0b553b8429`**.

After publication, the downloaded [hash file](https://github.com/Lotulune/Copperbench/releases/download/v0.1.10/SHA256SUMS.txt), [source metadata](https://github.com/Lotulune/Copperbench/releases/download/v0.1.10/RELEASE-METADATA.json) and [updater descriptor](https://github.com/Lotulune/Copperbench/releases/download/v0.1.10/copperbench-update-windows-x64.properties) matched their GitHub upload digests. The hash file covers every other public asset, all 15 asset digests agree, and the four component sizes and hashes match the descriptor. Source metadata identifies the signed source commit and release workflow above. This readback did not download or execute the installer; the workflow had already compared uploaded digests with its built payload before publication.

The tagged `product-status.json` is the frozen pre-publication build declaration. This report and the [current download index](../releases/current.md) record the observed public delivery state without changing the signed source or released bytes. Public-package installation and full player-world acceptance were not repeated for this release.

Only Windows 0.1.10 is being published here. Linux 0.1.9 and its earlier acceptance records retain their existing scope.
