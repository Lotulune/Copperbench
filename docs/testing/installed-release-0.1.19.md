# Public installer lifecycle — 0.1.19

[Cloud replay 41044985411](https://github.com/Lotulune/Copperbench/actions/runs/41044985411) passed for the **published Windows EXE and Linux graphical Setup.tar.gz**, upgrading from the published 0.1.18 installers. Product packages were downloaded and verified against public digests and signed source/run provenance; no product binary was rebuilt or replaced by the harness.

Binary source: `a6851b3b16a071a922c833d66b3e7ae99008b58f`. Harness: `de5049224dfaa5b39438e059fd2fce2aa8b61555` (latest `main`, the Linux authorization merge). The published-package checks use `packs_only=true`; this is installation and selected Core/data-pack acceptance, not a full mod or desktop certification.

## Passed checks

- Public package/metadata provenance and extracted installer identity for 0.1.19 and 0.1.18; bilingual language/theme component checks and offscreen previews.
- Fresh per-user installation, OS registration and installed application bytes; installed Core data-pack create/edit/export smoke, including the prepared legacy-wrapper migration.
- Fresh uninstall preserves user additions, preferences and the external workspace sentinel.
- Install 0.1.18, upgrade to 0.1.19, confirm version/registration, match the installed JAR to the new public payload and distinguish it from the old application hash.
- Upgrade retains the user-file, preference, workspace and export sentinels. Installed Core exports data-pack ZIPs; normal shutdown and upgraded uninstall complete with user data retained.

| Variant | Lifecycle checkpoints | Installed 0.1.19 JAR SHA-256 | Upgraded-from 0.1.18 JAR SHA-256 |
| --- | --- | --- | --- |
| windows-setup | 16 passed | `cf99dc88f49aabe62c519a7de40f595e716a1091ad69ec0b6c4189c69c0df839` | `41a4ceaef8f3abd1b3372f8693246ad1fc867d4e453bba5d89a6d08707ca5629` |
| linux-setup | 16 passed | `cfbdc1ce40bf416fb155a38e2735d22e985df9e978bce1e9f380a1aa3a0ca6c8` | `911d0416ebbd9f0ce830c09b50ac6edf60c157d86deddf04282f87cc1c33c4f2` |

The installed 0.1.19 JAR hashes also match the independent inspections of the public Windows portable ZIP ([verifier 41043377923](https://github.com/Lotulune/Copperbench/actions/runs/41043377923)) and the frozen Linux portable tarball ([consumer 41029948787](https://github.com/Lotulune/Copperbench/actions/runs/41029948787)). The 0.1.18 hashes equal those recorded in [the 0.1.18 acceptance](installed-release-0.1.18.md). Every downloaded evidence-file digest was checked locally: 163 Windows files and 148 Linux files. Raw results, previews, command logs, task receipts and hash manifests remain in the workflow artifacts and under `output/release-0.1.19/installed/` in the release worktree.

## Scope

Runners were Windows Server 2025 and Ubuntu 24.04 under Xvfb. The Windows result lists non-ASCII installation paths as unverified because of the external Java test-driver's argument encoding. This run does not certify a Windows 11 interactive desktop, UAC, MSIX or DEB installation, full GNOME Wayland/Xorg sessions, real-account CLI workflows, full Fabric/NeoForge installed acceptance, or player controls/world save-and-reopen. The Notebook interface was checked separately in browser previews and a native JCEF window on the release source; those checks keep their source identity. Linux `formalSupportClaim=false` is unchanged.
