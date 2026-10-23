# Public installer lifecycle — 0.1.18

[Cloud replay 39974356263](https://github.com/Lotulune/Copperbench/actions/runs/39974356263) passed for the **published Windows EXE and Linux graphical Setup.tar.gz**, upgrading from the published 0.1.17 installers. Product packages were downloaded and verified against public digests and signed source/run provenance; no product binary was rebuilt or replaced by the harness.

Binary source: `28b912e94b7bf271e91bafeaef7a02daf1abde98`. Harness: `9d7b2a594988488c8640663d129da9262b35782d`, reviewed in [PR #164](https://github.com/Lotulune/Copperbench/pull/164). The published-package checks use `packs_only=true`; this is installation and selected Core/data-pack acceptance, not a full mod or desktop certification.

## Passed checks

- Public package/metadata provenance and extracted installer identity; bilingual language/theme component checks and offscreen previews.
- Fresh per-user installation, OS registration and installed application bytes; installed Core data-pack create/edit/export smoke, including the prepared legacy-wrapper migration.
- Fresh uninstall preserves user additions, preferences and the external workspace sentinel.
- Install 0.1.17, upgrade to 0.1.18, confirm version/registration, match the installed JAR to the new public payload and distinguish it from the old application hash. Temporary previous-version directories are cleaned by the installer itself.
- Upgrade retains the user-file, preference, workspace and export sentinels. Installed Core exports data-pack ZIPs for 1.20.1, 1.21.1 and 26.1.x; normal shutdown and upgraded uninstall complete with user data retained.

| Variant | Lifecycle checkpoints | Installed JAR SHA-256 |
| --- | --- | --- |
| windows-setup | 16 passed | `41a4ceaef8f3abd1b3372f8693246ad1fc867d4e453bba5d89a6d08707ca5629` |
| linux-setup | 16 passed | `911d0416ebbd9f0ce830c09b50ac6edf60c157d86deddf04282f87cc1c33c4f2` |

The installed JAR hashes also match independent inspections of the public Windows portable ZIP and frozen Linux portable tarball. Every downloaded evidence-file digest was checked locally: 163 Windows files and 148 Linux files. Raw results, previews, command logs, task receipts and hash manifests remain in the workflow artifacts and under `output/release-0.1.18/installed/` in the release worktree.

## Corrected acceptance-driver expectation

The [first replay](https://github.com/Lotulune/Copperbench/actions/runs/39970999358) completed fresh installation, Core smoke and uninstall, then stopped after the upgrade with `Unexpected upgrade backup`. The driver still expected a retained rollback directory. The current installer intentionally deletes that temporary copy after successful activation and registration; its existing installer unit tests require this behavior.

The corrected driver checks a null backup result and no newly retained previous-version directories. It records the old application hash before upgrading and verifies the new public payload after upgrading; it does not clean installer leftovers on the product's behalf. Thirteen driver unit tests and probe compilation passed before the complete public-package replay. The successful replay used the **same published package bytes** as the first attempt.

## Scope

Runners were Windows Server 2025 and Ubuntu 24.04 under Xvfb. Windows installation paths used ASCII with spaces; Linux used Chinese characters and spaces. This run does not certify a Windows 11 interactive desktop, UAC, MSIX or DEB installation, full GNOME Wayland/Xorg sessions, real-account CLI workflows, full Fabric/NeoForge installed acceptance, or player controls/world save-and-reopen. The source-level M1 and native authoring evidence retain their separate scope. Linux `formalSupportClaim=false` is unchanged.

Package download links, public checksums and source identities are in [current downloads](../releases/current.md), [Windows publication](maintenance-release-0.1.18.md) and [Linux publication](linux-publication-0.1.18.md).
