# Stage 11 Beta candidate: Preview 7

Date: 2026-09-01

## Frozen candidate

- Candidate tag: `v0.1.0-preview.7`
- Candidate source: `f4b5806222b1224712cf33e827cc97241acdc45c`
- Candidate tree: `13887b4486fa3ab8a3f399a7e956301a9be6a51e`
- Merged-main CI: run `33497929171`, passed.
- Full Nightly: run `33503172036`, passed on the same source commit.
- Windows release: run `33506364499`, passed and published the signed Preview prerelease.

Nightly passed the full product regression plus all eight generator-golden jobs:

- `fabric-26.2`
- `neoforge-26.2`
- `fabric-26.1.2`
- `neoforge-26.1.2`
- `fabric-1.21.1`
- `neoforge-1.21.1`
- `fabric-1.20.1`
- `neoforge-1.20.1`

## Canonical assets

The public GitHub Release assets are the promotion source of truth.

| Asset | SHA-256 |
| --- | --- |
| `Copperbench.0.1.0.Windows.64bit.exe` | `716a93ea45278d71b2ef80eeee3bd4d0ec315891c349478cff50ed093db90d93` |
| `Copperbench.0.1.0.Windows.64bit.zip` | `1255a8528cd104d9fb26dfbb6228bfab415d77b84b071b636032c65a1e0282f5` |
| `Copperbench.0.1.0.Windows.64bit.msix` | `cafcaadf49cb0a59873d47d55ee0f0023e8c6f03c9b820a03994bb75b215faa3` |
| `copperbench.spdx.json` | `655daa5b9a7a96edba27726169f46146cda391e6e5bfee4fa350da5d4a94c078` |

The public EXE was downloaded again from the Preview 7 Release before machine qualification. Its local size was `439296643` bytes and its SHA-256 matched the Release digest exactly.

## Exact-candidate Windows 11 qualification

The installed product under test was the public Preview 7 EXE above, not a local rebuild.

### GUI workspace creation and CLI cold start

`Invoke-G9CleanWindowsGuiGate.ps1` passed against the installed Preview 7 product. The disposable workspace was `guigateeta`.

Observed contract:

- workspace selector observed;
- New Workspace dialog observed;
- derived fields settled;
- workspace file created;
- generator setup observed and closed naturally;
- created workspace main window observed;
- cold `-workspace` relaunch opened the exact workspace;
- the CLI argument was observed; and
- the cold launch did not fall back to the workspace selector.

Machine evidence:

- `evidence/stage-9/2026-09-01/clean-windows11-gui-new-workspace.json`
- `evidence/stage-9/2026-09-01/clean-windows11-gui-new-workspace.png`

### Workspace build/JAR and graphical runClient limitation

The historical lifecycle harness contains a fixture defect: it hardcodes `guigatedelta` in its generated-source path and log match even when `-WorkspaceFile` points to another workspace. Running the unmodified script against `guigateeta` therefore failed before the build because it looked for `GuigatedeltaMod.java`.

A read-only prerequisite probe confirmed that the actual Preview 7 workspace contained:

- generator `neoforge-1.21.1`;
- `src/main/java/net/mcreator/guigateeta/GuigateetaMod.java`;
- NeoForge metadata;
- the workspace Gradle wrapper;
- the Copperbench-managed JDK 21; and
- the Copperbench-managed Gradle home.

For diagnostic qualification, a temporary copy of the candidate-tag lifecycle harness was used with only the three historical fixture-name occurrences changed from `guigatedelta`/`Guigatedelta` to `guigateeta`/`Guigateeta`. No candidate product, source tag, build script, runtime, schema, generator or release payload was changed.

That run proves the workspace-generation/build portion:

- generated source present;
- Gradle build succeeded;
- JAR artifact present.

It does **not** prove graphical `runClient` success. The captured client log shows NeoForge exhausting OpenGL/GLFW profiles and reporting `Failed to initialize the mod loading system and display`. The current lifecycle harness accepts any surviving Java window for its stability predicate, so the NeoForge initialization-error window was incorrectly reported as `windowObserved=true` / `stable=true`. This is a second lifecycle-harness defect in addition to the hardcoded fixture name.

The Hyper-V guest used for this gate does not expose a usable OpenGL profile, so a successful graphics-capable `runClient` replay cannot be obtained from this environment. Beta 3 therefore does not claim this graphical replay as passed; `clean-windows-11-stage9` remains `not-applicable` / non-blocking for the Beta scope. Stage 11 candidate qualification instead relies on its explicit candidate requirements—fixed-commit CI/Nightly, Windows install/upgrade/uninstall, offline workspace launch/retention, provenance and candidate assets—which are independently evidenced below.

Machine evidence:

- `evidence/stage-9/2026-09-01/clean-windows11-workspace-lifecycle-harness-false-positive.json`

The lifecycle harness must be fixed after the exact-binary promotion boundary to derive fixture paths from `-WorkspaceFile` and to reject known client-initialization errors before declaring a stable `runClient`. Changing `scripts/**` before promotion would violate the evidence/status/docs-only candidate-to-Beta delta.

### Product-shell IPC scan false positive

The historical guest-smoke harness successfully installed and launched Preview 7 and captured its screenshot, but reported `ipcFailureDetected=true`. Investigation showed that the harness recursively scans every small file under `.copperbench` as text. Its matches came from Gradle/JDK binary dependencies, including socket strings in `dt_socket.dll`/`net.dll`, class names inside `.jmod`, and `DatabindException` text inside Jackson JARs.

A separate read-only scan restricted to real text/log extensions, excluding `.copperbench/gradle`, scanned 20 files with the same IPC failure patterns and found **0 hits**. The installed `copperbench` and `javaw` processes were present and the install path existed. This is classified as a test-harness false positive, not a reproduced Copperbench loopback/Unique4j failure.

The raw false-positive result is retained as:

- `evidence/stage-9/2026-09-01/clean-windows11-product-shell-harness-false-positive.json`

The IPC scanner should be restricted to known text logs after promotion; the frozen candidate itself is unchanged.

### Final RC upgrade/offline/uninstall retention

`Invoke-G9CleanWindowsUpgradeRetentionGate.ps1 -FinalRcReplay` passed with the source commit and public EXE SHA-256 pinned to Preview 7.

The machine result records:

- `passed=true`;
- `finalRcReplayRequested=true`;
- `finalRcReplayRequired=false`;
- `finalRcSourceCommit=f4b5806222b1224712cf33e827cc97241acdc45c`;
- `finalRcSourceWorktreeClean=true`;
- `currentInstallerSha256=716a93ea45278d71b2ef80eeee3bd4d0ec315891c349478cff50ed093db90d93`;
- historical public Preview 3 installed before the upgrade;
- old-to-current upgrade succeeded with a different payload;
- workspace and user data survived the upgrade;
- network was disconnected for the offline launch check;
- the offline process remained stable and opened the requested workspace;
- silent uninstall succeeded;
- workspace and user data survived uninstall;
- the current Preview 7 install was restored;
- test markers were removed; and
- `gatePromotionReady=true`.

Machine evidence:

- `evidence/stage-9/2026-09-01/clean-windows11-upgrade-offline-retention.json`

## Promotion decision

Preview 7 is qualified as the frozen Stage 11 exact-binary candidate for `v0.1.0-beta.3` for the scoped Beta contract. Its exact public binary passed the Stage 11 candidate-required Windows install/upgrade/uninstall and offline workspace/data-retention replay. A graphics-capable clean-Windows `runClient` replay remains outside the Beta 3 claim because the available Hyper-V guest cannot provide a usable OpenGL profile.

The Beta release-control delta must remain limited to the paths allowed by the exact-binary promotion contract: `product-status.json`, `PRD-NEXT.md`, `docs/remaining-work.md`, `docs/testing/**`, `docs/releases/**`, and `evidence/**`. Any product, runtime, generator, release-tooling, schema/tooling implementation, license, or other build-affecting change requires a new signed Preview and a new acceptance cycle.

The real JCEF accessibility audit, graphics-capable clean-Windows `runClient` replay, and five-external-tester trial remain outside this Beta scope and are not claimed as passed. Windows packages remain without Authenticode signing.
