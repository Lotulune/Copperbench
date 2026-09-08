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
- Ubuntu 24.04 package-smoke workflow for Linux platform/generator regressions, portable/deb layout, executable bits and bundled runtimes;
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

The tests were run with `-x buildUiShell` because the stacked Stage 14 branch still carries an independent UI i18n completeness gate; Java compilation, test compilation and the focused tests completed successfully. The Stage 15 Ubuntu workflow now runs the Desktop MCP runtime/HTTP/Agent-loop and headless contracts as Linux tests, so the POSIX descriptor permission assertions will execute on the real Linux runner rather than being skipped by the Windows development filesystem.

## Not yet proven

This checkpoint is not Linux runtime evidence. Still required before Stage 15 closure:

- run the package workflow on Ubuntu 24.04 and inspect the actual tar/deb artifacts;
- start bundled JBR/JCEF on a clean graphical Linux VM;
- create/open/save/reopen real workspaces without system Java/Gradle/Git;
- verify Fabric/NeoForge generate/build and real graphical `runClient`;
- verify the implemented Wayland/Xorg capability classification against real GNOME Wayland and GNOME on Xorg sessions, including actual JCEF/window behavior;
- replay Desktop MCP and an independent external Agent on the installed Linux candidate, including descriptor permissions and credential cleanup;
- run the new Ubuntu supply-chain path to obtain real SPDX/digest/metadata/provenance artifacts, then promote the attested development record into the existing formal exact-binary release-candidate chain only after the Linux product gates are satisfied;
- rerun affected Windows installed-product gates after the cross-platform JDK/MCP/product-path changes.
