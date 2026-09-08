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
- POSIX owner-only permissions for private runtime metadata where supported;
- Linux Blockbench discovery through explicit configuration, `PATH`, common user paths and system paths;
- bundled Java resolution that selects Linux JBR 25/JCEF and Java 21 sidecar paths without falling back to Windows source layouts;
- Stage 15-only Linux candidate admission using `copperbench.stage15LinuxCandidate=true`, without changing `currentHostSupported()` or the public support claim;
- candidate SBOM/inventory with installed `jdk`, `jdk21` and packaged Gradle distributions;
- Copperbench portable launcher, portable tar layout, Debian launcher/desktop entry and `.deb` build task;
- Ubuntu 24.04 package-smoke workflow for portable/deb layout, executable bits, bundled runtimes and SHA-256 artifact hashes.

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
- `LinuxDistributionLayoutTest`.

The real `writeLinuxCandidateManifest` Gradle task also completed successfully and materialized `build/reports/linux-candidate-manifest.json`.

The tests were run with `-x buildUiShell` because the stacked Stage 14 branch still carries an independent UI i18n completeness gate; Java compilation, test compilation and the focused tests completed successfully.

## Not yet proven

This checkpoint is not Linux runtime evidence. Still required before Stage 15 closure:

- run the package workflow on Ubuntu 24.04 and inspect the actual tar/deb artifacts;
- start bundled JBR/JCEF on a clean graphical Linux VM;
- create/open/save/reopen real workspaces without system Java/Gradle/Git;
- verify Fabric/NeoForge generate/build and real graphical `runClient`;
- verify Wayland and Xorg behavior/diagnostics;
- replay Desktop MCP and an independent external Agent on the installed Linux candidate, including descriptor permissions and credential cleanup;
- bind Linux SHA-256/SBOM/release metadata/provenance into the immutable release-candidate chain;
- rerun affected Windows installed-product gates after the cross-platform JDK/MCP/product-path changes.
