# Public 0.1.9 cloud acceptance — passed

All three installed-package lanes passed in [run 38903643192](https://github.com/Lotulune/Copperbench/actions/runs/38903643192):
Windows Setup on Windows Server 2025, Linux Setup on Ubuntu 24.04, and Ubuntu DEB.
They downloaded the public 0.1.9 and 0.1.7 packages, verified their identities,
then exercised fresh installation, upgrade, installed Native Core and removal.
The [reviewed summary](../../evidence/maintenance/2026-10-16/cloud-acceptance-0.1.9/verified-summary.json)
binds each result to the released application JAR and harness commit.

| Acceptance | Windows Setup | Ubuntu Setup | Ubuntu DEB |
| --- | --- | --- | --- |
| Fresh install, OS registration and installed launch | Passed | Passed | Passed via apt |
| 0.1.7 → 0.1.9 upgrade | Passed | Passed | Passed |
| Fresh/upgraded uninstall and user-data preservation | Passed | Passed | Passed |
| Legacy 1.20.1 wrapper migration and first Gradle 9.7 download in a new cache | Passed | Passed | Passed |
| Fabric build, intentional compiler failure and repair | Passed | Passed | Not in lane |
| Independent packaged-JAR GameTests | 5 passed, 0 failed/skipped | 5 passed, 0 failed/skipped | Not in lane |
| Exact export; normal host exit/reopen; task and element persistence | Passed | Passed | Not in lane |
| Fabric/NeoForge custom archive export without build/libs | Passed | Passed | Not in lane |
| Data-pack ZIP exports: 1.20.1, 1.21.1, 26.1.x | Passed | Passed | 1.20.1 smoke |
| Published installer language/theme component checks | Passed | Passed | Not in lane |

All 16 Native host sessions exited with code 0. The first smoke logs explicitly
record the Gradle 9.7 download after preparation of the released 8.3 wrapper.
Exported mod hashes match the Gradle archive receipts; exact and reopened exports
match the five-case GameTest JAR. Uninstall checks verify removal of managed
files and OS registration while retaining test workspaces, element files, exports,
preferences sentinels and unmanaged notes. The Windows lane uses the released
EXE registrar/uninstaller; Linux Setup calls the published installer engine and
registration implementation; DEB uses the system package manager.

Binary source A: `9e8fcda24e58d165cb2b767f3a0cb3092a7f527c`.
Linux publication source B: `474b2df4c52ecf52e1f3724448c2d12c8af85396`.
The tested harness is `65bd2de4b324fead129b8f4ebef5666ab0896e1d`.
Only test drivers/workspace preparation fixtures were compiled; product and
installer implementations came from the public packages. Every session reported
`packaged_binary` and the same JAR SHA-256 independently inspected in the release.

The [evidence review](../../evidence/maintenance/2026-10-16/cloud-acceptance-0.1.9/review.json)
checked 571 downloaded evidence files against their run manifests. Selected
receipts, run/jobs metadata and two reviewed installer previews are committed
under [the evidence directory](../../evidence/maintenance/2026-10-16/cloud-acceptance-0.1.9/verified-summary.json).
Complete logs and incremental task records remain in the workflow artifacts and
local `output/cloud-release-runs/38903643192`. The preview review covered Windows
Chinese/light and Linux English/dark, with readable text and no missing controls.

These are automated installer/API/server-GameTest results. They do not certify
Windows 11 desktop interaction, non-ASCII Windows installation paths, MSIX, UAC,
GNOME Wayland/Xorg interaction or player controls and same-world save/reopen.
The installer screenshots are offscreen renders. Workspace persistence is not
player-world persistence. No removed Minecraft control bridge was started.

The [0.1.7 failed acceptance](../../evidence/maintenance/2026-10-16/cloud-acceptance-0.1.7/README.md)
and the [0.1.8 stopped release](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/maintenance-release-0.1.8.md) retain their original
results. The corrective publication and source checks are recorded in
[the 0.1.9 release report](maintenance-release-0.1.9.md).
