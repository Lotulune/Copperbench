# Changes from upstream

This distribution is based on MCreator `2026.2.33518` at commit `361429609b772039a3eb9ab81662c25b225f1d0d`. The built-in Fabric generator is based on `26.1.2-2026.2-2.8` at commit `abfe19329126b679a26baafe5cade5a75d455528`.

## Stage 0 product changes

- Added the Copperbench `0.1.0` distribution identity, `dev.copperbench.studio` application ID, `dev.copperbench` product namespace, and `.copperbench` user-data directory.
- Replaced launch, window, About, workspace-selector, installer, MSIX, icon, logo, and splash branding. The user-provided Copperbench icon master is retained in `assets/branding`, and its 23 deterministic platform derivatives are locked in `compliance/branding-assets.lock.json`.
- Removed `pylo.svg` from processed product resources while retaining textual upstream copyright and GPL attribution.
- Added an offline distribution Web API and disabled automatic news, update, analytics, and Discord connections. Explicit upstream documentation and source/license links remain available where attribution or compatibility requires them.
- Renamed the application JAR and Windows launcher to `copperbench.jar` and `copperbench.exe` and added product/source metadata to the JAR manifest.
- Bundled the audited Fabric generator as `generator-fabric-26.1.2` and verified that its 704 packaged entries match the reproducible source-built plugin content.
- Pinned the Windows runtime to JetBrains Runtime with JCEF `25.0.3+1-b329.124`; its 551-file content lock is stored with the stage 0 evidence.

## Build and test fixes

- Increased the JCEF cold-start preload wait from 5 to 30 seconds.
- Added the standard FlatLaf artifact to the test runtime so Windows native window resources are available to UI integration tests.
- Corrected the Windows CopySpec so `lib/copperbench.jar` is included in ZIP, installer, and MSIX payloads.
- Restricted MSIX assets to PNG files, made MSIX rebuilding overwrite-safe, and changed the Windows packaging shell to `pwsh`.
- Added reproducible branding generation, package scanning, clean-build reporting, runtime/content locks, and G0 evidence aggregation scripts.

## Compatibility identifiers retained

- The `net.mcreator` Java namespace, `.mcreator` workspace extension, core version `2026.2`, plugin IDs such as `mcreator-core`, and upstream file formats remain unchanged where plugin or workspace compatibility depends on them.
- MCreator, Pylo, Minecraft, Fabric, NeoForge, and Blockbench names are used only for source attribution or compatibility descriptions, not as the Copperbench product brand.

## Known follow-up work

- Windows 10 is out of first-release scope. The NSIS installer and `SupportedPlatform` refuse builds older than 22000. Clean-machine G7 evidence is Windows 11 Hyper-V only.
- Public Git history starts from this Copperbench tree. It is an independent GPL derivative of MCreator, not an official Pylo distribution.
- Legacy workspace conversion fixtures with the Fabric generator and the full generator Gradle matrix are tracked for G1/G2 rather than the G0 build gate.
- Public identity is Copperbench. First public distribution is unsigned GitHub Releases with no product website, store listing, or Authenticode (ADR-0015). Product ID `dev.copperbench.studio` is a reverse-DNS identifier, not a live website.
- G7 is closed as passed: the Windows 11 Hyper-V guest completed silent install/upgrade/uninstall with the NIC disconnected, preserved workspace/user data, and kept the guest GUI process alive through the 10-second check. Evidence: `evidence/stage-8/2026-08-23/hyperv-g7-guest-checks.json`.

## Stage 3 Fabric 1.21.1 vertical slice

- Added a Copperbench-owned Fabric 1.21.1 generator for the bounded block, item, shaped recipe, simple procedure, texture and localization slice.
- Based build layout and version pins on FabricMC's official `fabric-example-mod` branch `1.21.1` at commit `a4c6556aeab4eb100f9f0e3c11d44175384796e6`; no unavailable Goldorion 1.21.1 branch was claimed or copied.
- Added validation, generation, Gradle build, safe artifact export and managed `runClient` task gateways, with structured logs and diagnostics.
- Expanded first-party MCP tools to CRUD, validation, generation, build, export, run client and task reads through the shared application service.
- Froze UI-Core Schema `1.0`, retained the `0.1` schemas and fixtures, and added unbound Java/TypeScript JCEF bridge endpoints for stage 4 integration.

## Stage 4 product shell

- Added the offline React/JCEF product shell with versioned UI-Core, window-action and isolated legacy-plugin transports.
- Added recoverable JCEF browser hosting; a real renderer-process termination smoke test verifies browser/transport recreation while retaining the committed Java workspace session.
- Added Windows custom non-client handling for drag, eight-way resize, Snap Layout hit testing, system menu, minimize/maximize/restore, multi-monitor DPI changes and dynamic system-frame fallback.
- Added a real `JavaPlugin` C-level Swing fixture compiled and loaded through `PluginLoader`, while retaining Java plugins as an explicit full-trust opt-in.
- Added Playwright visual baselines for 1366×768 at 125%, 1920×1080 at 150%, 2560×1440 at 175% and 3840×2160 at 200%.

## Stage 6 remainder and stage 7 G slice

- Added a first-party four-track catalog: latest Minecraft 26.2 (preview generate), previous stable 26.1 (preview), and maintenance tracks 1.21.1 (supported) and 1.20.1 (preview generate).
- Added copy-only Fabric/NeoForge loader migration and upstream workspace import. Source trees are hashed and never written; unknown fields are preserved and reported.
- Added asset publish batches and resource-pack test-client preparation (`run/resourcepacks` + `options.txt`). `prepare_resource_pack_client` still does not launch Minecraft. A Fabric 1.21.1 `runClient` probe later listed `file/copper_ready_pack.zip` in ResourceManager.
- Exposed the new operations through UI-Core 1.0, MCP tools and headless commands.
- After a complete 1.21.1 loader copy, the destination generator validates and generates into the copy. Source trees stay unchanged. Gradle rebuild of the copy is gated by `copperbench.stage7.migrationBuild`.
- Added first-party Fabric/NeoForge 26.1.2 vertical-slice generation (Java 25 pins). 26.1 stays preview until a golden compile is claimed. Same-version 26.1 Fabric↔NeoForge copy and rebuild are enabled.
- Added first-party Fabric/NeoForge 1.20.1 maintenance vertical-slice generation (Java 17 pins, `ResourceLocation` ctor, NeoForge userdev/`mods.toml`/`RegistryObject`). Same-version 1.20.1 Fabric↔NeoForge copy and rebuild are enabled. Golden compile remains gated.
- Added first-party Fabric/NeoForge 26.2 latest-track vertical-slice generation (Java 25; Fabric Loader `0.19.3`, Fabric API `0.158.0+26.2`, NeoForge `26.2.0.63`). Same-version 26.2 Fabric↔NeoForge copy and rebuild are enabled. Golden compile remains gated.

## Stage 8 G7 automation slice

- Added a machine-readable release manifest (`get_release_notes` / `headless release`) with the four-track matrix, honest golden vs generate-ready claims, known limitations, and the final machine-verified G7 status.
- NSIS uninstall now defaults to keeping `$PROFILE\.copperbench` in both UI and silent/upgrade paths, and no longer deletes the upstream `.mcreator` folder. User-chosen workspace directories are never removed.
- Release notes now include the Windows export layout contract and the first-party plugin compatibility inventory. Existing `build/export/win64` is checked when present.
- Added a Windows 11 silent install/upgrade/uninstall rehearsal against an isolated directory. It refuses to run if a real Copperbench uninstall key exists, preserves a planted workspace and `.copperbench` entries, and writes evidence under `evidence/stage-8/`.
- Added Fabric and NeoForge 1.21.1 cache-warm then Gradle `--offline` build gates. This is software offline mode after dependencies are cached, not an OS network disconnect.
- Clean Windows 11 G7 install/upgrade/uninstall ran in a Hyper-V guest (`Copperbench-G7`) with the NIC disconnected. Workspace and `.copperbench` survived uninstall. The final 2026-08-23 rerun also kept `copperbench.exe` alive through the 10-second guest check. Evidence: `evidence/stage-8/2026-08-23/hyperv-g7-guest-checks.json`.
- `list_installed_plugins` (UI / MCP / `headless plugins`) returns the live first-party and user plugin inventory with A/B/C/X classification and does not load Java.
- First-party mod-element slice is machine-readable (`elementCoverage` / `get_element_coverage` / `headless elements`): block, item, recipe, procedure on all eight generators. Imported upstream types are listed read-only; updates are rejected.
- Upstream MCreator tools are mapped in `upstreamTools` / `get_upstream_tools` / `headless upstream-tools` onto new UI, legacy window, unsupported, or out of scope. The legacy window is not a visual promise.
- U0 visual direction is closed: Direction A (workshop / left rail + inspector) is the shipped shell. Packaged `copperbench.exe` and `runMCreator` default to `-Dcopperbench.productShell=true`. Opt out with `false`.
- Authenticode and public brand remain postponed (GA). JCEF product-shell Snap/DPI (`HTMAXBUTTON=9`, `WM_DPICHANGED` 144) and Fabric 1.21.1 resource-pack client load are claimed from 2026-08-20 evidence.
- Added a first-release feature-coverage audit and a development-stage component inventory (not a signed production SBOM). Added `docs/user/README.md`.
- First-party 26.1/26.2 Fabric uses unobfuscated Loom. Fabric 26.1 is pinned to Minecraft `26.1.2` and Fabric API `0.155.2+26.1.2`. NeoForge 26.1 is pinned to Minecraft `26.1.2` / NeoForge `26.1.2.95`. Compile and runClient probes succeeded for Fabric/NeoForge 26.2 and 26.1.2; those four generators are now `SUPPORTED`.
- Added an honest Fabric 1.20.1 compile probe (`scripts/verify-fabric-1201-compile.ps1`). The 1.20.1 generator now emits Gradle 8.8 (matching fabric-loom 1.7). Cache-warm and `--offline` produced a jar. runClient and golden status are not claimed.
- Fabric 1.20.1 `fabric.mod.json` now depends on `java >=` the profile release (17 for 1.20.1). The runClient probe saw `COPPERBENCH_STAGE7_FABRIC1201_READY`. Fabric 1.20.1 is now `SUPPORTED`.
- NeoForge 1.20.1 pin is NeoForged Forge `1.20.1-47.1.106` (`net.neoforged:forge` + userdev 7.0.165). Cache-warm compile produced a jar. The runClient probe saw `COPPERBENCH_STAGE7_NEOFORGE1201_READY`. NeoForge 1.20.1 is now `SUPPORTED`. Gradle `--offline` for this track is still not claimed.
- First launch asks whether the user is in mainland China. Choosing yes writes Huawei Cloud Gradle-distribution and Aliyun Maven/Plugin Portal mirrors into `%USERPROFILE%\.copperbench\gradle`, and rewrites workspace wrapper URLs away from `services.gradle.org`. The setting remains available under Preferences → Gradle.
- Workspace Gradle setup shows download progress (file name, bytes, percent). A distribution pool copies an already-extracted Gradle install into the hash folder for the current wrapper URL so official and China-mirror URLs do not re-download the same version.
- Remaining first-release work is specified in `PRD-NEXT.md` (status index: `docs/remaining-work.md`). New Workspace generator plugins cover Fabric and NeoForge 26.2, 26.1.2, 1.21.1, and 1.20.1. The product shell has a native New Workspace view (`list_new_workspace_generators` / `create_workspace`); the Swing dialog remains as fallback. Empty-workspace Gradle golden compiles for those plugin trees are not claimed. MCP and headless do not yet expose workspace creation. The asset browser in the React shell still reads mock fixtures.
- Mainland-China mirrors also rewrite `libraries.minecraft.net` to BMCLAPI. Fabric Maven and NeoForge specialised repositories stay official. Workspace generators for 26.x and 1.21.1 share Gradle 9.7.0; 1.20.1 stays on 8.8. Windows export copies ready Gradle installs from `~\.copperbench\gradle` or `~\.gradle` into `gradle-dists` when present, and startup seeds them into the shared Gradle user home.
- About dialog now opens the development-test user guide (`docs/user/README.md` or bundled `user/README.md`). The Windows export recipe copies that file into `user/`.
