# Stage 15 closeout follow-up — 2026-09-10

This continues [installed validation](./stage15-installed-validation-2026-09-10.md).
Formal Linux support remains unclaimed until a new frozen candidate completes its remaining release gates.

## Linux JCEF compatibility correction

The installed Run 33 JBR helper previously aborted in `unzip.mojom.Unzipper` with signal 6 and
`--change-stack-guard-on-fork=enable`. CEF maintainer discussion on
[issue #3912](https://github.com/chromiumembedded/cef/issues/3912#issuecomment-2766842796), dated 2025-03-31,
documents a compatibility switch for this symptom. The subsequent
[native diagnosis](https://github.com/chromiumembedded/cef/issues/3912#issuecomment-2766859435) explains the required
annotations along the `CefExecuteProcess` call chain.

`CefUtils.addLinuxHelperCompatibilityArguments` now adds `--change-stack-guard-on-fork=disable` only on Linux,
normalizes conflicting copies of that argument, and leaves other platform arguments unchanged. This disables
the incompatible post-fork canary reseeding, not normal stack protection. Existing sandbox configuration is unchanged.
It is a compatibility workaround for the observed bundled runtime; a future runtime with verified native annotations
should re-evaluate whether the workaround is still needed.

Validation:

- Ten JUnit tests passed: `CefUtilsAccessibilityTest` (5), `LinuxDistributionLayoutTest` (3),
  `DesktopMcpRuntimeTest` (2). The new tests cover Linux argument normalization/idempotence and non-Linux preservation.
- A separately identified classpath-override diagnostic ran on real GNOME Xorg for 1059 seconds before normal close.
  JCEF main-frame readiness and Desktop MCP listening passed; normal close returned exit 0 and removed the descriptor.
- The product log has no new `stack smashing` or `SIGABRT`; the prior Apport report's size and modification time
  remained unchanged. Captured running helper arguments no longer enabled fork reseeding.
- The captured process snapshot did not include an Unzipper instance. The bounded no-crash soak is therefore useful
  diagnostic evidence, not proof that every helper path was exercised or a substitute for frozen-candidate replay.

Evidence: `evidence/stage15/2026-09-10/jcef-guard-fix-diagnostic/`. The diagnostic override SHA-256 is
`f466980b40bed0f8dafedd9ef61b3b1c5fdfb96269f172458debbe1f748a049a`; the base installed Run 33 `.deb` remains
`f897aadcfac5991114c2a89bf81e251ae58a25653d1a6b8f86fdd830aebee7c2`. Installed product files were not replaced.

## NeoForge official-repository transport

The guest's direct TLS request still fails, while the Windows host's existing local proxy can retrieve the same
official NeoForge POM with HTTP 200. A temporary SSH reverse forward bound only to guest `127.0.0.1:23067`
routes the verification process through that existing host proxy. HTTPS certificate verification remains enabled;
no repository URL, system proxy setting or global Gradle configuration is changed.

The prepared NeoForge fixture uses installed Run 33 classes and the existing published fixture launcher.
`stage15_neoforge_xorg_0910` initialized successfully with `status=committed`, `exitCode=0`, generator `neoforge-1.21.1`.
The real bundled Java 21 Gradle process completed NeoForm merge/rename/decompile and downloaded the required assets.
The subsequent unmodified installed-Core `build` returned `status=succeeded`, `exitCode=0`; its Gradle log reports BUILD SUCCESSFUL. Visible run-client replay is in progress. The temporary launcher selects the native bootstrap cache for run-client to reuse the already verified official assets.

Evidence: `evidence/stage15/2026-09-10/neoforge-proxy-probe.json` and subsequent loader replay results.

## Candidate promotion boundary

The source correction is not yet in a new immutable Linux candidate. The existing Stage 15 workflow already triggers
for `CefUtils.java` changes on `codex/stage15-linux-support`, so no CI configuration change is needed.
The user explicitly authorized committing/pushing the closeout work and continuing candidate verification. Candidate construction and final Xorg/Wayland replay
must bind to that new source commit before formal release promotion. Prior Run 33 product evidence stays historical;
the classpath diagnostic must not be relabelled as an exact-binary installed pass.
