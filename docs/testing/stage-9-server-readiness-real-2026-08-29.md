# Stage 9 real dedicated-server readiness - 2026-08-29

The `server-readiness-matrix` beta gate is closed locally for all eight
first-party Java generator tracks. This record is based on real generated
workspaces and real Minecraft dedicated-server processes, not the injected
process fixture used by the deterministic contract test.

## Exit contract

Each track must satisfy all of the following:

- generate a fresh isolated workspace successfully;
- accept the Minecraft EULA only inside the isolated server run directory;
- start the track's real Gradle `runServer` task with the bundled JDK selected
  for that generator profile;
- emit the track-specific Copperbench readiness marker;
- emit Minecraft's native `Done (...)! For help, type "help"` readiness line;
- remain free of a fatal dedicated-server signal during the post-`Done`
  stabilization window;
- exit the probe with code `0` and record `passed=true`.

The production process runner now waits two seconds after the two readiness
signals before terminating the validation server. Immediate `/FATAL]`,
`DEDICATED_SERVER` client-class leakage, `Exception in server tick loop`, or
`Encountered an unexpected exception` lines fail closed instead of being
reported as a successful startup.

## Real matrix

| Generator | Bundled runtime | Marker | Minecraft `Done` | Fatal scan | Probe exit | Duration |
| --- | --- | --- | --- | --- | --- | ---: |
| Fabric 26.2 | JBR 25 | yes | yes | clean | 0 | 39.76 s |
| NeoForge 26.2 | JBR 25 | yes | yes | clean | 0 | 38.39 s |
| Fabric 26.1.2 | JBR 25 | yes | yes | clean | 0 | 36.12 s |
| NeoForge 26.1.2 | JBR 25 | yes | yes | clean | 0 | 37.30 s |
| Fabric 1.21.1 | JDK 21 | yes | yes | clean | 0 | 119.87 s |
| NeoForge 1.21.1 | JDK 21 | yes | yes | clean | 0 | 57.89 s |
| Fabric 1.20.1 | JDK 21 | yes | yes | clean | 0 | 142.27 s |
| NeoForge 1.20.1 | JDK 21 | yes | yes | clean | 0 | 932.97 s |

All eight final records were produced by the hardened probe with
`workspaceReused=false` and `serverFatalSeen=false`. The final NeoForge 1.20.1
log contains neither `LanServerPinger` nor a `FATAL` line.

Machine evidence:

- `evidence/stage-9/2026-08-29/server-readiness-fabric-26_2.json`
- `evidence/stage-9/2026-08-29/server-readiness-neoforge-26_2.json`
- `evidence/stage-9/2026-08-29/server-readiness-fabric-26_1_2.json`
- `evidence/stage-9/2026-08-29/server-readiness-neoforge-26_1_2.json`
- `evidence/stage-9/2026-08-29/server-readiness-fabric-1_21_1.json`
- `evidence/stage-9/2026-08-29/server-readiness-neoforge-1_21_1.json`
- `evidence/stage-9/2026-08-29/server-readiness-fabric-1_20_1.json`
- `evidence/stage-9/2026-08-29/server-readiness-neoforge-1_20_1.json`

After the fatal detector and post-`Done` stabilization window were introduced,
all eight tracks were rerun from fresh isolated workspaces and regenerated the
machine evidence above.

## Defects found by the real gate

The real-server matrix found three issues that the compile-only golden gate
could not detect:

1. NeoForge 26.x block/item registration bypassed the registry-aware factory
   helpers, causing `Block id not set` and unbound registry values during real
   FML registration. The generator now uses the ID-aware modern factories.
2. NeoForge 1.20.1 userdev runs the dedicated server from `runs/server`, while
   the managed task had assumed `run`. The backend now declares its actual
   server run directory, so EULA handling is correct per track.
3. NeoForged Forge `1.20.1-47.1.106` enables dedicated-server LAN advertising
   by default and its own patch then loads the client-only `LanServerPinger`,
   immediately crashing after Minecraft prints `Done`. Copperbench disables
   that option only in the isolated validation world's
   `world/serverconfig/forge-server.toml`; user workspace/game configuration is
   not modified.

The third issue also exposed a false-positive readiness condition. The runner
therefore gained the post-`Done` fatal-free stabilization window described
above.

## Deterministic failure contract

`dev.copperbench.generator.Stage9ServerReadinessContractTest` remains the fast
CI fixture. It covers all eight profiles and verifies both the successful
managed-task route and fail-closed behavior for timeout/non-zero process
results. It also verifies the legacy NeoForge EULA path and isolated Forge
server configuration preparation.

`dev.copperbench.generator.fabric.Fabric1211ProcessRunnerTest` covers the
native Minecraft readiness-line classifier, fatal-line classifier, and bundled
runtime mapping. The real probe is
`dev.copperbench.generator.Stage9RealServerReadinessProbeTest` and remains
opt-in because the maintenance NeoForge 1.20.1 NeoForm preparation can take
many minutes on a cold project directory.

This closes only the Stage 9 dedicated-server readiness gate. It does not
close the large-workspace performance, real JCEF/accessibility, final clean
Windows 11 RC replay, or external-tester beta blockers.
