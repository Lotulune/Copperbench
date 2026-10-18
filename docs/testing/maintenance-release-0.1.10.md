# Windows 0.1.10 release preparation — 2026-10-18

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

Final-main CI, signed-tag verification, release packaging, SBOM/provenance generation and draft-asset verification are required before publication. Their run URLs, final source commit and public asset hashes are recorded after the workflow completes. Public-package installation and full player-world acceptance have not been repeated by this preparation record.

Only Windows 0.1.10 is being published here. Linux 0.1.9 and its earlier acceptance records retain their existing scope.
