# Public installer lifecycle — 0.1.20

[Cloud replay 42976929337](https://github.com/Lotulune/Copperbench/actions/runs/42976929337) passed for the **published Windows EXE and Linux graphical Setup.tar.gz**, upgrading from the published 0.1.19 installers. Product packages were downloaded and verified against public digests and signed source/run provenance; no product binary was rebuilt or replaced by the harness.

Binary source: `5e1ab97f0a10ebf2792e2622ad0d563f8b4ffc29`. Harness: the same commit (the release-preparation merge, dispatched from `main` before the Linux authorization merged). The published-package checks use `packs_only=true`; this is installation and selected Core/data-pack acceptance, not a full mod or desktop certification. The Windows job passed on the first attempt. The Linux job's first attempt ran before `v0.1.20-linux-stable` existed and stopped at the release lookup (HTTP 404); its re-run after publication passed.

## Passed checks

- Public package/metadata provenance and extracted installer identity for 0.1.20 and 0.1.19; bilingual language/theme component checks and offscreen previews.
- Fresh per-user installation, OS registration and installed application bytes; installed Core data-pack create/edit/export smoke, including the prepared legacy-wrapper migration.
- Fresh uninstall preserves user additions, preferences and the external workspace sentinel.
- Install 0.1.19, upgrade to 0.1.20, confirm version/registration, match the installed JAR to the new public payload and distinguish it from the old application hash.
- Upgrade retains the user-file, preference, workspace and export sentinels. Installed Core exports data-pack ZIPs; normal shutdown and upgraded uninstall complete with user data retained.

| Variant | Lifecycle checkpoints | Installed 0.1.20 JAR SHA-256 | Upgraded-from 0.1.19 JAR SHA-256 |
| --- | --- | --- | --- |
| windows-setup | 16 passed | `7a5dd1802f2c4320240e16efe8938f018d8f59ed5363b6bcac37c2c167082126` | `cf99dc88f49aabe62c519a7de40f595e716a1091ad69ec0b6c4189c69c0df839` |
| linux-setup | 16 passed | `11538ce1342669e87bb8ab08c721eaaa3610e92965da06d03e1887513e16e7ec` | `cfbdc1ce40bf416fb155a38e2735d22e985df9e978bce1e9f380a1aa3a0ca6c8` |

The installed 0.1.20 JAR hashes also match the independent inspections of the public Windows portable ZIP ([verifier 42976906932](https://github.com/Lotulune/Copperbench/actions/runs/42976906932)) and the frozen Linux portable tarball ([consumer 42976562215](https://github.com/Lotulune/Copperbench/actions/runs/42976562215)). The 0.1.19 hashes equal those recorded in [the 0.1.19 acceptance](installed-release-0.1.19.md). Every downloaded evidence-file digest was checked locally: 163 Windows files and 148 Linux files. Raw results, previews, command logs, task receipts and hash manifests remain in the workflow artifacts.

## Scope

Runners were Windows Server 2025 and Ubuntu 24.04 under Xvfb. The Windows result lists non-ASCII installation paths as unverified because of the external Java test-driver's argument encoding. This run does not certify a Windows 11 interactive desktop, UAC, MSIX or DEB installation, full GNOME Wayland/Xorg sessions, real-account CLI workflows, full Fabric/NeoForge installed acceptance, or player controls/world save-and-reopen. The interface changes of this release were checked in browser previews on the source; they were not re-checked in a native JCEF window. Linux `formalSupportClaim=false` is unchanged.
