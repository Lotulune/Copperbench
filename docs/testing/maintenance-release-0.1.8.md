# 0.1.8 cloud-discovered export fixes

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

A separate [data-pack run](https://github.com/Lotulune/Copperbench/actions/runs/38877992508) reproduced a second product defect on both platforms: the bundled 1.20.1 data-pack wrapper still launched Gradle 8.3 on Java 25, which failed with `Unsupported class file major version 69`. The [Linux receipt](../../evidence/maintenance/2026-10-16/export-cache-018/linux-datapack-1201-failure.json) retains the error. The template now uses the already-reviewed Gradle 9.7 distribution. Generation/build/export also upgrades an existing 1.20.1 wrapper only when its distribution URL and SHA-256 match the shipped 8.3 defaults (official or supported mirror); unknown/custom values, comments and unrelated settings are preserved. The [additional validation receipt](../../evidence/maintenance/2026-10-16/export-cache-018/datapack-local-validation.json) records 31 passing checks, including the real ZIP build after wrapper migration. A cold local distribution download timed out; the successful compatibility replay used an isolated copy of an existing completed Gradle 9.7 cache. Cold final-package cloud acceptance remains separate.

Initial cache-fix verification passed: 36 related Java tests (including the real Gradle regression), 50 UI-Core contracts, three release-history tests, product status, Markdown links and the application/UI build. The [validation receipt](../../evidence/maintenance/2026-10-16/export-cache-018/local-validation.json) binds source files, suite results, logs and the local application JAR. Initial host-IPC and new-test path-construction failures are retained separately.

Final source CI, signed package publication and installed 0.1.8 acceptance are pending. Public 0.1.7 assets will not be overwritten. Full
Windows 11/GNOME desktop and player-controlled world persistence are separate
from the cloud automation; no Minecraft control bridge is used.

## Publication stopped

The signed `v0.1.8` tag remains at source `9ece8e330e5ce7f3746a3e124691c68b5141a687`, but the [Windows publication run](https://github.com/Lotulune/Copperbench/actions/runs/38886381793) failed the new real-Gradle archive test before packaging/publication. The Linux candidate was independently verified but not promoted. [0.1.9](maintenance-release-0.1.9.md) adds the Windows short-path correction; no public 0.1.8 package or existing public asset was overwritten.
