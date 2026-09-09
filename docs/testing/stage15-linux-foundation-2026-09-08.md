# Stage 15 Linux platform foundation — 2026-09-08

## Scope

This checkpoint covers the first implementation slice of Stage 15. It does **not** claim Linux formal support or satisfy the Stage 15 Definition of Done.

Frozen target:

- certification baseline: Ubuntu 24.04 LTS x86_64;
- primary desktop session: GNOME Wayland;
- compatibility session to evaluate separately: GNOME on Xorg;
- portable format: `tar.gz`;
- desktop package format: `.deb`.

## Implemented

- runtime platform detection for Windows/Linux/macOS architecture-aware paths;
- Linux XDG data/config/cache/state/runtime paths while retaining the existing Windows user-root behavior;
- logs and JCEF logs use state storage, preferences use config storage, JCEF persistent cache uses cache storage, and the single-instance lock uses runtime storage;
- POSIX owner-only permissions for private runtime metadata where supported;
- Linux Blockbench discovery through explicit configuration, `PATH`, common user paths and system paths;
- bundled Java resolution that selects Linux JBR 25/JCEF and Java 21 sidecar paths without falling back to Windows source layouts;
- Java executable and Gradle process selection now route through the shared `RuntimePlatform` adapter instead of inline Windows-name checks;
- generated/restored Gradle wrappers explicitly regain owner-executable permission on POSIX filesystems;
- Stage 15-only Linux candidate admission using `copperbench.stage15LinuxCandidate=true`, without changing `currentHostSupported()` or the public support claim;
- Wayland/X11/headless/unknown desktop-session classification is exposed through the shared workspace execution environment with explicit primary/compatibility/unverified certification roles;
- `get_workspace_environment` gives headless/MCP/external-Agent clients the same host desktop capability facts without creating Linux-specific Core workspace semantics;
- Desktop MCP remains explicitly bound to `127.0.0.1`, keeps credentials out of the connection descriptor, revokes workspace tokens and deletes the descriptor on close; its POSIX contract now asserts descriptor mode `0600` and parent-directory mode `0700` whenever POSIX attributes are available;
- Linux `runClient` startup logs classify missing display, GLFW initialization and OpenGL initialization failures into stable task diagnostic codes before the readiness marker, while post-readiness exits remain generic runtime failures;
- candidate SBOM/inventory with installed `jdk`, `jdk21` and packaged Gradle distributions;
- Copperbench portable launcher, portable tar layout, Debian launcher/desktop entry and `.deb` build task;
- Linux Blockbench discovery now rejects non-executable Unix candidates; because Windows PE file-version metadata is not available on Linux/macOS, an executable Blockbench with unavailable version metadata is admitted as `READY_UNVERIFIED` for the existing managed-launch/lease/change-detection lifecycle instead of being incorrectly reported as unavailable;
- Ubuntu 24.04 package-smoke workflow for Linux platform/generator regressions, portable/deb layout, executable bits and bundled runtimes; after extracting the portable tar it also starts the packaged `bootstrap list-generators` entry with an isolated XDG home and a minimal `PATH` containing no system Java/Gradle/Git, then requires Fabric/NeoForge discovery and all three packaged Gradle runtimes to seed into the isolated cache;
- the packaged candidate also has an opt-in graphical probe and Xvfb compatibility smoke that creates a deterministic resource-pack workspace, starts the real packaged product shell with windowed Chromium/JCEF, verifies the JCEF main frame and Desktop MCP reach ready/listening state, verifies the X11 workspace window is visible, and preserves Linux formal-support status as pending;
- the same workflow now freezes an exact Linux development-candidate record binding the source commit to tar/.deb/SBOM/manifest SHA-256 values, emits an SPDX JSON SBOM, verifies the frozen record against the bytes, and requests GitHub build-provenance attestation.

The generated candidate manifest deliberately reports:

- `status=development-not-certified`;
- `formalSupportClaim=false`;
- `systemJavaRequired=false`;
- `systemGradleRequired=false`;
- `systemGitRequiredForBaseline=false`.

## Verification on the current Windows development host

Focused Gradle regressions passed for:

- `RuntimePlatformTest`;
- `UserDataPathsTest`;
- `PrivatePathPermissionsTest`;
- `BlockbenchExecutableLocatorTest`;
- `BundledJdkLocatorTest`;
- `SupportedPlatformTest`;
- `DevelopmentSbomTest`;
- `LinuxCandidateManifestTest`;
- `LinuxDistributionLayoutTest`;
- `GradleDistributionPoolTest`;
- `Fabric1211ProcessRunnerTest`;
- `Fabric1211TaskGatewayTest`;
- `NeoForge1211TaskGatewayTest`;
- `ResourcePackWorkspaceTaskGatewayTest`;
- `DesktopSessionCapabilitiesTest`;
- `ExecutableFilePermissionsTest`;
- `LinuxDesktopPathIntegrationTest`;
- `WorkspaceEnvironmentContextTest`;
- `DesktopMcpRuntimeTest`;
- `McpHttpServerTest`;
- `DesktopMcpAgentLoopTest`;
- `HeadlessProductLauncherTest`;
- representative Fabric/NeoForge generator regressions, including the installed Java 21 sidecar contract.

The real `writeLinuxCandidateManifest` Gradle task also completed successfully and materialized `build/reports/linux-candidate-manifest.json`. The cross-platform Linux candidate metadata contract has 5/5 Node tests passing, including post-freeze tamper rejection, premature-support-claim rejection, candidate-ID sensitivity to asset bytes, and workflow supply-chain wiring.

The focused local Java tests were run with `-x buildUiShell`; the real Ubuntu candidate workflow runs the complete UI production build as part of `exportLinuxCandidate`. The Stage 15 Ubuntu workflow also runs Desktop MCP runtime/HTTP/Agent-loop, headless, Blockbench and POSIX contracts as Linux tests, so the Linux-only permission paths execute on the real Linux filesystem rather than being inferred from the Windows development host.

## Real Ubuntu 24.04 candidate evidence

GitHub Actions run `34225972899` executed the Stage 15 candidate workflow on Ubuntu 24.04.4 against commit `c4dbd43719aa4065725c3d5b048d3599ad2f42d7` and completed every functional, packaging and supply-chain step successfully.

The Linux-specific Blockbench regressions passed on the Ubuntu runner, including:

- locating an executable `blockbench` through `PATH`;
- rejecting a regular file that lacks POSIX execute permission;
- rejecting a non-executable installation before version detection;
- admitting an executable Linux installation as `READY_UNVERIFIED` when Windows file-version metadata is unavailable;
- starting that `READY_UNVERIFIED` executable through the managed `BlockbenchProcessService` lifecycle.

The same run then proved the packaged candidate path rather than only source-tree behavior:

- full UI production build and `exportLinuxCandidate` succeeded;
- portable `tar.gz` and Debian `.deb` candidates were produced;
- portable launcher, `gradlew`, bundled JBR 25/JCEF helpers, Java 21 and the packaged Gradle 9.7.0 / 9.6.1 / 8.8 pools retained the required executable/layout contracts after extraction;
- the packaged headless bootstrap succeeded from an isolated XDG home with a minimal `PATH` containing no system Java, Gradle or Git and discovered both Fabric and NeoForge generators;
- `.deb` layout and the manifest identity shared with the portable candidate passed;
- SPDX generation, immutable candidate-metadata freeze/verification, SHA-256 generation, six-subject GitHub/Sigstore provenance attestation and artifact upload all succeeded.

Immutable Run 7 evidence:

- candidate identity: `sha256:ff529a31d5c7095652d3dea215e453e2bd69063987c1238b468dacc00215789d`;
- portable tar SHA-256: `4db79f533b56b5daf06e4989ac633235ac46197cefeb6f6ebc5b17009a4d5a5e`;
- Debian package SHA-256: `f3c2c5d9a3748f1b447bd8dee601f3dabb3fc727cc7e25255d4f580b159dda82`;
- SPDX SHA-256: `5f0091c69b05c91177e11d6ca2fa6284779cc4990314fbdb6e542d5ed436aae1`;
- frozen metadata SHA-256: `4000d88ae0832e31949aae86743fc313739bc4da924480c107d5581539509e51`;
- candidate manifest SHA-256: `1952b06e6d7bc593fcec5b1c5fd24a2558d481aca0f5f22ff57c2461b2b319b7`;
- uploaded artifact: `stage15-linux-candidate`, artifact ID `10055932678`, size `1854685435` bytes, artifact digest `sha256:5f15cb64de2550d19347adf1d26d1df135eb4eeb2674e76532b894f8c5046cbe`;
- GitHub provenance attestation ID `45961545`, Rekor transparency-log index `2757844528`.

This closes the previously missing real Ubuntu package/headless/SBOM/digest/metadata/provenance workflow evidence. It does **not** change the candidate manifest's `development-not-certified` status or make Linux a formally supported platform.

### Windowed X11/JCEF compatibility evidence

GitHub Actions run `34250193650` executed the updated Stage 15 candidate workflow on Ubuntu 24.04.5 against commit `9890cf6042223dbc708281f443bbb0ec197a1836` and completed the full candidate chain successfully. This run adds a stronger graphical compatibility layer than the earlier headless/package evidence:

- the Ubuntu regression set explicitly passed `GraphicalCiModeTest`, which keeps normal GitHub Actions Chromium headless behavior unchanged but permits the Stage 15 graphical smoke to opt into windowed Chromium;
- the packaged product was launched under Xvfb with `-Dcopperbench.graphicalCi=true`, so `CefUtils` did **not** add Chromium `--headless` for this smoke;
- the deterministic packaged workspace fixture committed successfully before product launch;
- the packaged JBR/JCEF product shell emitted the machine-readable graphical probe;
- the probe verifier confirmed `jcefMainFrameLoaded=true`, Linux x86_64/X11 classification, Desktop MCP `listening` state on loopback, and no credential material in the probe;
- the smoke then confirmed a matching X11 Copperbench workspace window remained visible/alive before normal test cleanup;
- the same exact run continued through `.deb` verification, SPDX generation, immutable candidate metadata, SHA-256 output, six-subject GitHub/Sigstore provenance and artifact upload.

Immutable Run 16 evidence:

- candidate identity: `sha256:a8031fcd446292871cdc8af6acaec435c6a1cdcba9836254d8b013179ee30d06`;
- portable tar SHA-256: `b54cf72ced513395493e0ea617168c60767f2ed624fae6e3e83de59e75f2c8cd`;
- Debian package SHA-256: `300efeeacb2757de2a3493b9a7b78c871abaedacfde26b2e23e37ae6d2b10e83`;
- SPDX SHA-256: `47376d861415934869826d8d51e5f60b6670c7a30742ecdfb4cc116109a01a77`;
- frozen metadata SHA-256: `5d952bc8f96d27c668b7a9fe1243643207439971bc983ec8504e0a1bb8cf2d18`;
- candidate manifest SHA-256: `1952b06e6d7bc593fcec5b1c5fd24a2558d481aca0f5f22ff57c2461b2b319b7`;
- uploaded artifact: `stage15-linux-candidate`, artifact ID `10065976853`, size `1854700291` bytes, artifact digest `sha256:04af0fea9a26949c97f52b9b119483a3096c8887c0cf1791aae5019567d7414c`;
- GitHub provenance attestation ID `46019397`, Rekor transparency-log index `2760160360`.

This is **windowed Chromium/JCEF X11 compatibility evidence on the GitHub-hosted Ubuntu/Xvfb environment**. It is not a substitute for the Stage 15 clean-installed GNOME Wayland/Xorg gates and does not promote Linux to formal support.

### Packaged Fabric 1.21.1 X11 render preflight evidence

GitHub Actions run `34270785109` executed the Stage 15 candidate workflow on Ubuntu 24.04.4 against commit `2b35a4ec14c323fb9de12b343ad94c51d35ee3e9` and completed the full candidate chain successfully. This run upgrades the earlier source-level/process-runner coverage into a real packaged Fabric client preflight:

- a deterministic `fabric-1.21.1` workspace was created against the packaged candidate;
- Copperbench's packaged headless Core performed the workspace `build`; the smoke did not bypass Copperbench by invoking `gradlew runClient` directly;
- the same packaged Core started `run-client` under Xvfb using the candidate's bundled Java/Gradle path;
- the real Minecraft client log reached Fabric Loader startup, the LWJGL Render thread/backend, resource-manager reload and `minecraft:textures/atlas/blocks.png-atlas` creation;
- after those render markers, the Copperbench/runClient process group remained alive for an additional ten-second stability window and no GLFW/OpenGL/display-init/Render-thread fatal signature was present;
- the same exact run then passed `.deb` verification, SPDX generation, immutable candidate-metadata verification, SHA-256 generation, six-subject GitHub/Sigstore provenance and artifact upload.

Immutable Run 21 evidence:

- candidate identity: `sha256:61d247d22b5df7c6fee09a350f44ffdd9ceb9cdfddbc75a1fe594a5fa6ac369d`;
- portable tar SHA-256: `7639c79afc2c554731ff78d2f9ea2e53577b2a1e3c0a6dcb14f3e060fb537062`;
- Debian package SHA-256: `18ec1462c950c240dc0d6a3906d6a7d1958019bcc6445f180b3a3cfb6f8232c9`;
- SPDX SHA-256: `1e6961480c8f4e62963c83eca9977097c59ea6385d15dd51bda3af5f20381b86`;
- frozen metadata SHA-256: `881632e2dd519d8f968af0dd042e4eb8dc15b9ee344a45385ace4a01d46b8b2c`;
- candidate manifest SHA-256: `1952b06e6d7bc593fcec5b1c5fd24a2558d481aca0f5f22ff57c2461b2b319b7`;
- uploaded artifact: `stage15-linux-candidate`, artifact ID `10074111529`, size `1854706014` bytes, artifact digest `sha256:26ac3471fb79789d311faa95f340a2aecab754516bee816f8ae5b3a357c68f42`;
- GitHub provenance attestation ID `46064240`, Rekor transparency-log index `2761720727`.

This is **real packaged Fabric/Minecraft render-path evidence under GitHub-hosted Ubuntu/Xvfb**. Xvfb did not expose a reliable user-visible child-window identity for Minecraft, so the gate intentionally uses application-level Render thread/LWJGL/resource/atlas evidence rather than window-manager metadata. It still does **not** prove that a user sees a stable Minecraft window in a clean GNOME Wayland/Xorg session; that remains an installed-desktop gate.

### Packaged Fabric + NeoForge 1.21.1 X11 render evidence

GitHub Actions run `34272333519` executed the same Stage 15 candidate workflow on Ubuntu 24.04.4 against commit `43fe1168cc6e2074e08d368052358047a88ebfa5` and completed the entire candidate chain successfully. This run adds NeoForge to the packaged render preflight while replaying Fabric against the exact same candidate bytes:

- the packaged Copperbench Core created, generated and built a deterministic `neoforge-1.21.1` workspace, then launched `run-client` under Xvfb without bypassing Copperbench through a direct `gradlew runClient` invocation;
- the NeoForge client reached `NeoForge 21.1.232 (neoforge)` discovery, the LWJGL Render thread/backend, resource-manager reload and `minecraft:textures/atlas/blocks.png-atlas` creation, then remained alive through the post-readiness stability window without a GLFW/OpenGL/display-init/Render-thread fatal signature;
- the same candidate replayed the Fabric 1.21.1 packaged render preflight successfully with the same Core build/run-client path and render/stability checks;
- portable contents, bundled runtimes, minimal-PATH headless bootstrap, windowed JCEF/X11 smoke, `.deb` layout, SPDX, immutable metadata, SHA-256 subjects, GitHub/Sigstore provenance and artifact upload all remained green in the same run.

Immutable Run 22 evidence:

- candidate identity: `sha256:b0da68ad63bdd3a2ec0b8c5ef5aea9e12becf0fc85aa3909aaaa45fc35816700`;
- portable tar SHA-256: `293736d1de6d35a2c6a3aa58518f0538f3c33b0faf1cd66b4c89fe2b62f38abf`;
- Debian package SHA-256: `93f02cdb312167e732d7f9e9e7df72e236b3e93cd71b18f52cddd3b20fd4fd9a`;
- SPDX SHA-256: `2bd6bc3323d1ba3e3636fd15467e6b69fe4b0292e8eac1ee2619ad00e92574c4`;
- frozen metadata SHA-256: `57d856787c7ec6a761062486eec47a2e89aa23dc21b222a432c41f1a1c3bc66c`;
- candidate manifest SHA-256: `1952b06e6d7bc593fcec5b1c5fd24a2558d481aca0f5f22ff57c2461b2b319b7`;
- uploaded artifact: `stage15-linux-candidate`, artifact ID `10074828705`, size `1854710910` bytes, artifact digest `sha256:452dc0f24d1c2f3b2d05f80e76c425827011d397a40308d281e03520bc6734f4`;
- GitHub provenance attestation ID `46068061`, Rekor transparency-log index `2761806013`.

This is **same-candidate packaged Fabric and NeoForge render-path evidence under GitHub-hosted Ubuntu/Xvfb**. It strengthens FR-LINUX-03 pre-certification evidence, but still does not substitute for a clean installed GNOME Wayland/Xorg session with a user-visible Minecraft window.

### Clean installed GNOME gate harness prepared

The current Stage 15 branch now also carries `scripts/verify-stage15-linux-installed-guest.sh` as a maintainer gate for the next real clean-desktop replay. This is **harness readiness, not certification evidence**. The gate is deliberately stricter than the hosted-runner smoke:

- it binds the replay to an exact `.deb` SHA-256 and verifies the installed candidate still declares `development-not-certified` / `formalSupportClaim=false`;
- it requires Ubuntu 24.04 x86_64 and a GNOME Wayland or GNOME X11 session, recording the session/display facts as evidence;
- before installation it requires system `java`, `gradle`, and `git` to be absent, so a passing replay cannot silently borrow the host toolchain;
- after installation it verifies the packaged desktop integration files themselves: `/usr/share/applications/copperbench.desktop`, its exact `Exec=/usr/bin/copperbench %F` launch contract, and the hicolor application icon. This proves package integration bytes only; an actual launch from the GNOME application menu remains a user-visible clean-desktop gate;
- the hosted `.deb` extraction smoke now enforces the same desktop-entry fields and icon presence before a candidate reaches the clean-guest step, so packaging drift is caught in ordinary Stage 15 CI while the real GNOME menu launch remains separately unproven;
- it launches the installed JBR/JCEF product, verifies the Stage 15 graphical probe and private Desktop MCP descriptor permissions, then uses `/usr/bin/copperbench` for installed-Core build and Fabric/NeoForge `run-client` render readiness;
- its machine result is intentionally `automated-preflight-passed-manual-gates-pending`. That base preflight alone does not close workspace create/save/reopen through the installed UI, actual user-visible Copperbench/Minecraft windows, UI-authorized external-Agent lifecycle, or real Blockbench integration. The bundled interactive helpers described below move the Agent, Run Client lifetime, and Blockbench process/lease/change portions into machine-checked evidence while deliberately leaving the inherently visual/user-authorized observations manual.

The harness also includes a dependency-free Python external-Agent helper. The tester still has to copy the one-time Desktop MCP token from the installed Copperbench UI; the helper deliberately reads that credential from a hidden prompt rather than a command-line argument. It then runs from an independent Python process through the published loopback MCP endpoint, verifies cursor traversal, direct element creation, Workspace Plan preview/apply, real build-to-terminal polling, a forced `WORKSPACE_REVISION_CONFLICT` plus reread/retry, final build/readback and audit-log credential redaction. The helper also starts public MCP `run_client`, waits for LWJGL/resource-manager/block-atlas render markers, requires the task to remain `running` through an additional ten-second stability window, then asks the tester to confirm the Minecraft window is visibly usable and close it normally. A pass requires the same task to reach `succeeded` only after that user close. Afterward it asks the tester to close Copperbench normally, waits for the descriptor to disappear, and verifies that the old endpoint/token can no longer read the workspace. The token remains process-memory-only and is excluded from the machine-readable evidence. This machine-checks Run Client lifetime; only the actual visual-window observation remains manual.

The harness now also carries `Stage15InstalledBlockbenchVerifier.java`. It is designed to run with the candidate's bundled JDK and production `BlockbenchExecutableLocator` / `BlockbenchProcessService`, not a test-only process launcher. On the clean guest it creates a unique disposable `.bbmodel`, launches the real discovered Linux Blockbench executable, requires a live managed process and cross-service `BLOCKBENCH_ASSET_LEASED` exclusion, then asks the tester to make one visible Blockbench edit, save and close normally. A pass requires exit code 0, a changed SHA-256 and the production `ASSET_CHANGED_EXTERNALLY` detection. This reduces the Blockbench process/lifecycle gate to the inherently visual edit/save action while retaining machine-readable process/lease/change evidence. It does **not** synthesize the installed JCEF Asset Center action, so one visible installed-UI “open in Blockbench” click remains required in addition to the helper. Both remain harness readiness until actually executed on the clean GNOME guest.

The verifier also accepts an explicit expected `wayland` or `x11` target and checks the corresponding `stage15-primary-target` / `stage15-compatibility-target` role while still requiring `stage15CertificationPending=true`. The hosted Stage 15 workflow runs static contracts for this installed-gate harness and is configured to upload a separate `stage15-linux-installed-gate-harness.tar.gz` bundle containing the gate script plus verifier, so the clean guest does not need Git merely to obtain the certification helper. The bundle is created with `tar` and explicitly checks that the gate script retains mode `0755`; this avoids relying on `upload-artifact` to preserve Unix file modes. Archive contents and verbose mode listings are materialized once before validation rather than repeatedly piping `tar` into early-exiting `grep`/`awk`, avoiding benign SIGPIPE/write-error noise in the candidate logs. The harness artifact is intentionally separate from the candidate binary/provenance subjects. The hosted workflow does not execute the installed gate on Xvfb or treat that environment as clean GNOME certification.

GitHub Actions run `34279938312` replayed the complete hosted Stage 15 chain against commit `3fd793af1254470c97b36652e014515997b1d39f` after the independent installed external-Agent helper and maintainer instructions were added. Linux platform/generator tests, the six installed-gate contracts, candidate export, minimal-PATH bootstrap, packaged JCEF/X11 smoke, NeoForge and Fabric 1.21.1 render preflights, `.deb` verification, SPDX, frozen metadata, SHA-256 output, provenance, candidate upload and harness packaging/upload all passed. The resulting harness archive contains `verify-stage15-linux-installed-guest.sh`, `verify-stage15-linux-installed-agent.py`, `Stage15GraphicalProbeVerifier.java`, `INSTALLED-GATE.md` and the dependency-free Python SDK; the workflow also verified executable modes for both helper scripts.

Immutable Run 26 evidence:

- candidate identity: `sha256:cb966b77a8761ec21312b949e731d55b500f4567c1ccb2d2160edc10c6eac564`;
- portable tar SHA-256: `f4dd9a84fd91646a9654f8df0bb210109008aae181b0067ef891d7c14e28e366`;
- Debian package SHA-256: `543d72c80a663831c6cece23d242953376d1eb53dabc5b4cb87567d6a594df2e`;
- SPDX SHA-256: `06e83b4af9f45cb96da731f8bb3fb8d90d342c44e3b535eb9e22e48c5c31843d`;
- frozen metadata SHA-256: `2275324204053d467464670424310be9e54e91b7c7dc1fec7b306908561178cc`;
- candidate manifest SHA-256: `1952b06e6d7bc593fcec5b1c5fd24a2558d481aca0f5f22ff57c2461b2b319b7`;
- candidate artifact ID `10077740513`, artifact digest `sha256:5f863f1d50531925cd5217082218e7df1fb8f714e9cc7a3ab3f6963e29b68850`;
- installed-gate harness artifact ID `10077741092`, artifact digest `sha256:a5c704f8b9c80a2cf6a1faf2537d2ce3fa88d17c95372aff304c3f771ca4d258`;
- GitHub provenance attestation ID `46085006`, Rekor transparency-log index `2762346133`.

Run 26 therefore closes **harness publication/readiness**, not the installed-desktop certification itself. The one-time token still has to be authorized from a real installed UI, and Wayland/Xorg visible-window, interactive Run Client and real Blockbench checks remain pending until they are replayed in the clean GNOME guest.

### Hosted desktop-package integration evidence

GitHub Actions run `34283184131` replayed the complete Stage 15 hosted candidate chain against commit `ff3b426a00a49592b9f849a96a1bd4db96ef4a97`. In addition to the previously proven JCEF/X11, Fabric and NeoForge render, supply-chain and harness checks, this run executed the tightened Debian extraction gate against the actual built package and verified:

- `/usr/share/applications/copperbench.desktop` exists in the candidate package;
- the desktop file carries exact `Type=Application`, `Name=Copperbench`, `Exec=/usr/bin/copperbench %F`, `Icon=copperbench`, and `Terminal=false` contracts;
- `/usr/share/icons/hicolor/256x256/apps/copperbench.png` exists;
- the portable and Debian candidates still carry identical `linux-candidate-manifest.json` bytes;
- the same run kept packaged JCEF, NeoForge 1.21.1 and Fabric 1.21.1 render preflights green before completing SPDX, metadata, hashes, provenance and uploads.

Immutable Run 27 evidence:

- candidate identity: `sha256:71c505be0f213fb695ce3b05bc7bd9d6536efef2efbed06e2940c782bf72afea`;
- portable tar SHA-256: `f4dd9a84fd91646a9654f8df0bb210109008aae181b0067ef891d7c14e28e366`;
- Debian package SHA-256: `3e838ebb91bf1d93b74402aefc55a7d9e1aff1ff5773f3e5a1a1f8b44422c4df`;
- SPDX SHA-256: `186a125c634fe3713fbcf55718f2d30c83c9b237f4ac66de12784c7fcf4768ef`;
- frozen metadata SHA-256: `a175e5da92b8a9dfaf9e47c65e6882e308045cf21b3c978d93b1f055c9df3c39`;
- candidate manifest SHA-256: `1952b06e6d7bc593fcec5b1c5fd24a2558d481aca0f5f22ff57c2461b2b319b7`;
- candidate artifact ID `10078925774`, artifact digest `sha256:52c13e5832efa518706c30c6381749e7147a208261617d1c1e51d3757cff62ed`;
- installed-gate harness artifact ID `10078926370`, artifact digest `sha256:b76636fe8882acbe09aef68d3fe25b363d620fdf39f4f659decea5d1385ef3b1`;
- GitHub provenance attestation ID `46091960`, Rekor transparency-log index `2762560329`.

Run 27 therefore closes the **hosted Debian desktop-integration byte contract**. It does not prove that GNOME actually indexes the desktop entry, that a user can launch Copperbench from the GNOME application menu, or that the resulting window behaves correctly on real Wayland/Xorg; those remain clean-desktop observations.

### Hosted installed external-tool lifecycle harness evidence

GitHub Actions run `34284855979` replayed the complete Stage 15 hosted candidate chain against commit
`98b24dfcc439c60465a67e3321fe918abc3ec82e` after the installed external-Agent helper gained the public MCP
`run_client` lifecycle gate and the clean-guest harness gained the production-path Blockbench verifier. All seven
installed-gate contracts passed. The same run also kept the packaged JCEF/X11 smoke, NeoForge 1.21.1 and Fabric 1.21.1
render preflights, Debian desktop-entry/icon contract, SPDX generation, frozen metadata, SHA-256 output, provenance and
both artifact uploads green. Harness packaging included `Stage15InstalledBlockbenchVerifier.java` and validated archive
contents/modes from materialized tar listings, eliminating the earlier benign early-pipe/SIGPIPE noise.

Immutable Run 28 evidence:

- candidate identity: `sha256:8def61dc1b8508bbdc47cc784067550e75c23c6dc1958ceff7b3f8007fdec113`;
- portable tar SHA-256: `10b213ea25495480562599fdc2a2c6a983e191d97bded682689cc8b89f37e88b`;
- Debian package SHA-256: `1abdbbe5481739b405d0f39fef247964384389284a2c4e571b3b3fcf775159e5`;
- SPDX SHA-256: `6086d8483b44d7e177a22b777175069247c469c17ca1e3907ea7c1f140cd2aa6`;
- frozen metadata SHA-256: `4a76cc572e2aa6ffac5d7402887eaf4e1bdc596a77069faf28e3dd29bb7e941d`;
- candidate manifest SHA-256: `1952b06e6d7bc593fcec5b1c5fd24a2558d481aca0f5f22ff57c2461b2b319b7`;
- candidate artifact ID `10079565234`, artifact digest `sha256:3c8023086f7bbd3cdc910082ec891573837723e06407a4692de688fcf3065125`;
- installed-gate harness artifact ID `10079565643`, artifact digest `sha256:fa5da01b4c39b3640784272498c88aba22f2188749e4977eedafc5047adae939`;
- GitHub provenance attestation ID `46095634`, Rekor transparency-log index `2762749332`.

Run 28 proves that the **final prepared helper bundle is reproducibly publishable with the same green candidate chain**.
It still does not claim that the external-Agent helper, its user-visible `run_client` close lifecycle, or the real
Blockbench verifier has executed inside a clean GNOME guest; those results only become evidence after the real guest
replay.

### Windows affected-source regression replay

After the Stage 15 platform/JDK/MCP/headless/external-tool changes, the affected Windows source-level regression set was replayed successfully on the Windows development host. The focused set covered bundled-JDK routing, workspace environment/layout recovery, Fabric and NeoForge task/runClient paths, Desktop MCP and external-Agent loops, headless product entry points, Blockbench discovery/lifecycle, XDG/legacy Windows path behavior and executable-permission portability. The Gradle run completed successfully with no source-level Windows regression.

### Windows installed-product regression revalidation

The affected Windows installed-product path has now also been replayed on the existing clean Windows 11 Hyper-V guest against the exact Stage 15 Windows candidate installer `sha256:a58a3d947c686d518439d15b09442ab8f1a136a5da8c67b4464e6681d548edd2`. The replay is bound to source HEAD `c7f45bd0f260030039a19e5f07b8abf0b3a36ae5`, source-delta SHA-256 `a75e37f37734f795bd261511546cf1126c0adfc870b3a1902970591de125f6b0`, and the already-passing Stage 14 source-integrity baseline in `evidence/stage14/2026-09-09/source-integrity-clean-windows11.json`.

The final gate completed successfully on 2026-09-09 and records `fullStage15WindowsRegressionPassed=true`. It respected the normal single-instance contract by binding to a newly created workspace HWND even when the installed JVM was reused. The real custom-titlebar Build and Test Client controls were resolved from the native window-chrome client-region snapshot, verified as `HTCLIENT`, and clicked with real mouse input rather than fixed historical coordinates. Build spawned `cmd.exe /c gradlew.bat --no-daemon build`, returned native exit code `0`, and produced two `build/libs` JARs. Test Client spawned `cmd.exe /c gradlew.bat --no-daemon runClient`; Minecraft ran from the installed bundled Java 25 runtime, stayed alive for the required 20-second stability window, then closed through `WM_CLOSE` without cancellation or process killing, after which Gradle returned native exit code `0`. Normal Copperbench close removed the workspace Desktop MCP descriptor.

The machine-readable result is `evidence/stage15/2026-09-09/windows-product-regression-clean-windows11.json`. Candidate-local accessibility probes also confirmed that this JCEF/Windows setup does not expose the titlebar buttons through UI Automation, so the final gate intentionally uses the product's native window-chrome region contract instead of claiming unavailable UIA semantics.

## Not yet proven

The headless/package/supply-chain path now has real Ubuntu evidence. Still required before Stage 15 closure:

- start the same bundled JBR/JCEF candidate on a clean installed GNOME Linux VM (the Xvfb/X11 CI compatibility path is now proven, but the clean-desktop gate is not);
- create/open/save/reopen real workspaces without system Java/Gradle/Git;
- verify Fabric and NeoForge generate/build on the clean GNOME candidate and complete at least one user-visible interactive graphical `runClient` lifecycle there, matching FR-LINUX-03/05 rather than inventing a two-loader visible-window requirement; both loaders already have same-candidate packaged Xvfb render-path evidence;
- verify the implemented Wayland/Xorg capability classification against real GNOME Wayland and GNOME on Xorg sessions, including actual JCEF/window behavior;
- execute the bundled external-Agent helper against the installed Linux candidate after authorizing its one-time token from the real UI; the helper is prepared to machine-check descriptor permissions, read/write/plan/build/conflict, interactive Run Client lifetime, credential redaction and normal-close endpoint cleanup, but none of those clean-guest results is claimed until the replay actually runs;
- execute the bundled real-Blockbench verifier on the clean guest and separately confirm the installed Asset Center launches that real Blockbench binary; the helper is prepared to machine-check production discovery, managed launch, lease exclusion, save/change detection and normal close, but those installed-tool results are not yet evidence;
- promote the attested development record into the existing formal exact-binary release-candidate chain only after the Linux graphical/product gates are satisfied.

### Clean-guest host preparation

The repository now also carries a host-side Hyper-V preparation path: `verify-stage15-linux-hyperv-ready.ps1` is a
read-only probe for Hyper-V, an unused VM/VHD name, the selected virtual switch, an Ubuntu 24.04 Desktop amd64 ISO and
an explicitly supplied SHA-256; `New-Stage15LinuxHyperVGuest.ps1` creates a Generation 2 guest only after re-hashing the
ISO, retains Linux-compatible Secure Boot, disables automatic checkpoints, preserves the default Hyper-V integration-
service state and refuses existing VM/VHD replacement.
`New-Stage15LinuxAutoinstallSeed.ps1` can attach a credential-free NoCloud CIDATA disk that selects the standard Desktop
source while leaving identity and the installer disk-write confirmation interactive; it disables optional driver,
codec, OEM and SSH additions and fails rather than removing packages if Java/Gradle/Git unexpectedly appear. The detailed
sequence is in `stage15-linux-hyperv-checklist.md`. These scripts intentionally stop at environment setup and guarded OS
installation: they do not seed developer tooling or credentials and do not convert VM creation into certification.
The read-only host probe was executed without installation media and recorded Hyper-V `Enabled`, the Hyper-V module and
`Default Switch` available, the configured `D:` VHD root drive available, and both the target VM and VHD name unused.
It returned `readyToCreateCleanVm=false` because no Ubuntu ISO or expected Canonical SHA-256 was supplied. No VM was
created or modified. The actual clean-GNOME replay therefore remains pending on verified installation media rather than
being simulated through SSH or Xvfb.

GitHub Actions run `34293294517` replayed the complete Stage 15 candidate chain against commit
`c7f45bd0f260030039a19e5f07b8abf0b3a36ae5` after the Hyper-V readiness/VM/NoCloud preparation scripts and their
contracts were added. The hosted Ubuntu 24.04 job explicitly passed `Stage15LinuxHyperVHarness.tests.ps1` alongside the
existing Linux platform, installed-gate and supply-chain contracts; packaged JCEF/X11, NeoForge 1.21.1 and Fabric 1.21.1
render preflights, Debian desktop integration, provenance and both artifact uploads also remained green.

Immutable Run 29 evidence:

- candidate identity: `sha256:4cbb68520a35c69310fe74b9800f21b86585748e572bbd49bb6dd71e56dadb96`;
- portable tar SHA-256: `a0a781cd5db90d8cd259bcbd860c141ede4c5c0bd20fec4c279c872547e073ca`;
- Debian package SHA-256: `633529b6401aa58e9aa3cd90aedd5c3d7ee0be57d7a209b15dad845f6eff7645`;
- SPDX SHA-256: `d741c89709daddd96df86260e45f8afb48ff69ffd1996b52ac26ac7607a926c8`;
- frozen metadata SHA-256: `da475b5ab1b69bac2fe8dddd3d3ec5a02a389c0ddbde4a51a9c1f6f52eb80391`;
- candidate manifest SHA-256: `1952b06e6d7bc593fcec5b1c5fd24a2558d481aca0f5f22ff57c2461b2b319b7`;
- candidate artifact ID `10082581836`, artifact digest `sha256:0ae5190d3c7ca69c37df1e032e9377189032f65149af2605d75d91565ce72e12`;
- installed-gate harness artifact ID `10082582456`, artifact digest `sha256:211bec0b15282ead8640a5d2e5d1ccb9318b27279e89bc12d1a98fa60e136604`;
- GitHub provenance attestation ID `46111831`, Rekor transparency-log index `2763684433`.

Run 29 proves the **host-preparation scripts and their refusal/safety contracts are continuously testable on the same
green candidate line**. It is not Linux installation or GNOME certification evidence; the local readiness probe still
refused VM creation because no verified Ubuntu ISO/hash was supplied.
