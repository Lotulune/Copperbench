# Eight-track source-runtime audit harness

This is the exact Windows developer-worktree audit, not a product feature, installed-product gate, standalone Codex run, or portable end-user SDK.

Run from the tested `.tmp/stage12a-depth` worktree. The checkout's main resources/classes and plugins must already have been prepared; the harness derives `../../jdk/jbr25_win_64` and `../../jdk/jdk21_win_64` from that layout. Existing per-track Gradle caches are reused. No global caches are cleared.

To reconstruct the ignored audit directory, copy these harness files to `.tmp/stage14-track-audit` in that worktree. Run `compile.ps1`, then `run-track.ps1 -Track <generator-id>`. The initializer uses production Core persistence and a source-stage task stub; only the separate Gradle processes provide real compile/runtime evidence.

For modern NeoForge use `-SkipServerAssets` only for the explicitly limited headless `--initSettings` probe. It supplies real asset-index metadata from the cached Minecraft version manifest but does not certify the client asset payload. Default startup and these probes have different labels and acceptance scopes. Legacy NeoGradle has its own observed asset task name. `-SkipPrepare -BootstrapOnly` is a runtime-only retry on the already generated files.

Never use an unqualified `runServer` as a presumed EULA stop for NeoForge development environments. This audit observed one such run creating a disposable world despite `eula=false`; its termination and evidence are retained. Later NeoForge probes use `--initSettings`. Never point this harness at user worlds or reuse a user workspace.

`collect.ps1` collects phase receipts, logs, previous attempts and source hashes. `finalize-report.ps1` renders the summary table from that matrix. `verify-source-unchanged.ps1` verifies the current product delta against the captured one. A phase failure is not the same as orchestration failure; inspect the JSON state rather than the wrapper's final exit code.

Timeout/interrupted probes are not passed results. A zero exit plus an EULA/settings boundary without the fixture's initializer message is not proof that the mod initialized. World readiness, installed/deployed JAR checks and interactive gameplay are separate evidence layers.
