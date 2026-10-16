# Public 0.1.7 cloud acceptance — failed

The public 0.1.7 bytes did not pass the complete automated acceptance scope.
Two product defects were reproduced on Windows Server 2025 and Ubuntu 24.04:
NeoForge ordinary export fails when Gradle configuration caching is enabled;
1.20.1 data packs launch the old Gradle 8.3 distribution on bundled Java 25.
Both fixes are tracked in [PR #120](https://github.com/Lotulune/Copperbench/pull/120)
for 0.1.8. Its Windows release gate subsequently exposed a short-path alias issue; [PR #121](https://github.com/Lotulune/Copperbench/pull/121) completed 0.1.9. The independently executed [0.1.9 acceptance](../../../../docs/testing/cloud-acceptance-0.1.9.md) passed; this original 0.1.7 result remains failed.

Binary source: `db8d08336c274cc37239b48e837f0bf3727c5fdc`. The public packages
were downloaded afresh, matched GitHub asset digests and signed provenance,
then installed using the published installer implementation. Only test drivers
were compiled. Product sessions used the installed executable and bundled SDK;
their reported application SHA-256 matched the installed JAR.

| Check | Windows Setup | Ubuntu Setup | Ubuntu DEB |
| --- | --- | --- | --- |
| Fresh installation and OS registration | Passed | Passed | Passed via apt |
| Published installer language/theme components | Passed | Passed | Not in scope |
| Installed data-pack 1.21.1 create/edit/export | Passed | Passed | Passed, fresh and upgraded |
| 0.1.6 → 0.1.7 upgrade and data preservation | Passed | Passed | Passed |
| Fresh and upgraded uninstall; preserve external workspaces, exports and unmanaged files | Passed | Passed | Passed |
| Fabric build, controlled compile failure and repair | Passed | Passed | Not in scope |
| Five independent packaged-JAR GameTests | 5 passed, 0 failed/skipped | 5 passed, 0 failed/skipped | Not in scope |
| Exact export; normal host exit/reopen; task/element persistence | Passed | Passed | Not in scope |
| Fabric custom archive destination without build/libs | Passed | Passed | Not in scope |
| NeoForge build | Passed | Passed | Not in scope |
| NeoForge custom archive export | **Failed: configuration cache** | **Failed: configuration cache** | Not in scope |
| Data-pack 1.20.1 ZIP | **Failed: Gradle 8.3 / Java 25** | **Failed: Gradle 8.3 / Java 25** | Not in scope |
| Data-pack 26.1.x ZIP | Passed | Passed | Not in scope |

The [full run](https://github.com/Lotulune/Copperbench/actions/runs/38874596880)
passed the DEB lane and stopped both Setup lanes at the confirmed NeoForge
failure. The [supplemental pack run](https://github.com/Lotulune/Copperbench/actions/runs/38880082636)
continued independent data-pack cases and completed upgraded uninstall despite
the expected 1.20.1 failure. Its overall result remained failed. The table combines
independent observations on the same verified public bytes; it does not turn
either failed run into a full acceptance pass.

Selected receipts: [DEB lifecycle](passed/deb/result.json),
[Windows full Native result](runs/38874596880/windows-setup/upgraded-native/result.json),
[Linux full Native result](runs/38874596880/linux-setup/upgraded-native/result.json),
[Windows supplemental lifecycle](runs/38880082636/windows-setup/result.json),
[Linux supplemental lifecycle](runs/38880082636/linux-setup/result.json).
The per-run `evidence-hashes.json` files describe the complete uploaded artifact;
only selected receipts are committed here. Full downloaded evidence remains under
`output/cloud-release-runs/` and in the linked workflow artifacts.

[Prior attempts](prior-attempts.json) distinguish harness corrections from actual
product failures. The initial JSON parsing, fixture preparation, file discovery,
optional environment projection and request-shape mistakes are retained. Two
representative published-installer previews were visually inspected: Windows
Chinese/light and Linux English/dark, with no clipped text or missing controls.

Not established: Windows 11 desktop, non-ASCII Windows installation paths, MSIX,
UAC elevation, GNOME Wayland/Xorg interaction, player controls or saving and
reopening a player world. Swing component tests/offscreen previews, Native Core
sessions and server GameTests remain distinct from those claims. No removed
Minecraft control bridge was started.
