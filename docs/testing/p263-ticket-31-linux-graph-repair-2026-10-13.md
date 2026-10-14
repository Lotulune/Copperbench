# P263-31 Linux NeoForge dependency graph repair

The [installed cold run](https://github.com/Lotulune/Copperbench/actions/runs/38348098369)
failed during workspace Gradle synchronization. Linux resolves
`io.netty:netty-transport-native-epoll:4.2.16.Final`, absent from the Windows lock.
The original failure is retained; its Native host exited 0 and authority was
revoked, which does not turn the failed task into a pass.

The [dedicated Ubuntu qualification](https://github.com/Lotulune/Copperbench/actions/runs/38351212931)
successfully built the real probe and recorded the Linux graph. The separate
`gradle/linux.lockfile` preserves the Windows graph unchanged. The shared checksum
metadata adds 14 Linux artifacts without removing or changing existing pins.
All 14 original files were separately downloaded from Maven Central or
libraries.minecraft.net and matched the recorded SHA-256 values.

The probe requires both lock files on every platform, and generated NeoForge
workspaces carry the Linux lock and select it only on Linux. Python regression
checks reject a missing or changed Linux lock. The template regression checks
the generated lock, platform selection and epoll checksum. This repair awaits
a fresh installed cold/warm candidate run before ticket 31 can pass.

Evidence: original cold failure (`output/p263-remaining/cloud/neoforge-attempt-2/p263-installed/cold/installed-run.json`),
independent artifact checks (`output/p263-remaining/cloud/neoforge-linux-graph/independent-artifacts.json`),
probe regression log (`output/p263-remaining/linux-graph-tests.log`).
