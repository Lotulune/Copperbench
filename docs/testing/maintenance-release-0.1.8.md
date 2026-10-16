# 0.1.8 configuration-cache export fix

Public 0.1.7 cloud acceptance found the same ordinary NeoForge export defect on Windows Server 2025 and Ubuntu 24.04.
The project's `org.gradle.configuration-cache=true` setting made Gradle reject
the archive receipt action: its `:build` closure captures a `Project` and the
live task graph. The Java compilation and JAR task succeeded, but Gradle failed
while storing the configuration cache and Core correctly reported export failure.

The original [cloud run](https://github.com/Lotulune/Copperbench/actions/runs/38874596880)
and [NeoForge task receipt](../../evidence/maintenance/2026-10-16/export-cache-018/neoforge-custom-export.json)
retain the failure. Both Setup runs had passed fresh install/uninstall,
0.1.6 to 0.1.7 upgrade, Fabric build/fail/repair, all five packaged-JAR GameTests,
exact export, task persistence after a normal host restart, and Fabric's custom
archive destination. The separate public DEB lifecycle passed. Those facts apply
to the tested 0.1.7 binaries, not to the forthcoming 0.1.8 packages.

The fix passes `--no-configuration-cache` only when collecting the per-export
archive receipt. Project preferences and ordinary builds are unchanged. A real
offline Gradle regression exports twice from a cache-enabled project, changes
the input between exports, and checks the selected custom archive, fresh receipt
hash and payload bytes without a `build/libs` directory.

Local verification passed: 36 related Java tests (including the real Gradle regression), 50 UI-Core contracts, three release-history tests, product status, Markdown links and the application/UI build. The [validation receipt](../../evidence/maintenance/2026-10-16/export-cache-018/local-validation.json) binds source files, suite results, logs and the local application JAR. Initial host-IPC and new-test path-construction failures are retained separately.

Final source CI, signed package publication and installed 0.1.8 acceptance are pending. Public 0.1.7 assets will not be overwritten. Full
Windows 11/GNOME desktop and player-controlled world persistence are separate
from the cloud automation; no Minecraft control bridge is used.
