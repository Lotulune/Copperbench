# Stage 15 installed-candidate closeout — 2026-09-10

Run 34 installed-candidate validation is complete. Formal Linux support and public release promotion remain pending.
This report continues [the earlier installed validation](./stage15-installed-validation-2026-09-10.md).

## Frozen candidate

- [Workflow Run 34](https://github.com/Lotulune/Copperbench/actions/runs/34466884869), source `57897e74981156a32c071504fa5518585597dbfb`.
- `.deb`: 888099450 bytes, SHA-256 `349ad8be1488de0ce9f8dcf5c4aff06a0b5f56dbefa11c7c3164f7070475505e`.
- Portable: 968196540 bytes, SHA-256 `4d4ec753dc4928e5cf7e6f9b8491eb3f9f89ea84e691329e5a3c15ab82eebeb5`.
- Metadata, SBOM and manifest hashes matched; GitHub/Sigstore attestations verified the metadata, `.deb` and portable
  against this repository's Stage 15 workflow. Source commit and workflow invocation were checked in the signed statement.
- The installed product and portable binaries were not modified during accepted candidate replays.

Evidence root: `evidence/stage15/2026-09-10/`.

## Accepted Run 34 results

| Gate | Result | Evidence directory |
| --- | --- | --- |
| Ubuntu 24.04 GNOME Xorg, Fabric build/render preflight | Passed with current-run log freshness | `run34-xorg-fabric-fresh-log` |
| Ubuntu 24.04 GNOME Xorg, NeoForge build/render preflight | Passed with current-run log freshness | `run34-xorg-neoforge-fresh-log` |
| GNOME Wayland, Fabric build/render preflight | Passed after login keyring authentication | `run34-wayland-fabric-unlocked` |
| GNOME Wayland, NeoForge build/render preflight | Passed after login keyring authentication | `run34-wayland-neoforge-unlocked` |
| Xorg external Agent full lifecycle | Passed; real Minecraft and product normal close, descriptor removal, old connection rejection | `run34-xorg-desktop` |
| Wayland external Agent full lifecycle | Passed in replay 2, including both normal closes and credential lifecycle | `run34-wayland-agent-replay2` |
| Real managed Blockbench edit/save/normal close | Passed through installed production locator/process/lease/change detection | `run34-xorg-blockbench` |
| Installed Asset Center → real Blockbench | Passed through actual button; native Wayland model window observed | `run34-wayland-desktop` |
| UI create/save/close/reopen | Passed in a newly created Fabric workspace; recent-workspace UI reopen and byte-identical saved function | `run34-wayland-ui-persistence` |
| Portable extraction and bundled-runtime build | Passed from an unrelated working directory, with NeoForge and no system Java/Gradle/Git | `run34-portable` |

The new workspace is `/home/stage15/MCreatorWorkspaces/stage15_run34_ui/stage15_run34_ui.mcreator`.
Its `wayland_saved_probe` function retains `say Stage15 Run34 Wayland persistence` at revision 2 after normal close
and UI reopen. Saved/reopened element SHA-256: `3cd89b26c54a7a05ee3744464862ca67b75969f252b234a4f93ca38725ff6834`.

## JCEF helper correction and runtime observations

`CefUtils.addLinuxHelperCompatibilityArguments` normalizes `--change-stack-guard-on-fork=disable` on Linux only.
The [CEF maintainer's workaround](https://github.com/chromiumembedded/cef/issues/3912#issuecomment-2766842796), dated
2025-03-31, addresses the earlier helper abort symptom. It disables incompatible post-fork canary reseeding, not normal
stack protection. Sandbox configuration is unchanged; a future runtime with verified native annotations should re-evaluate it.

Ten focused JUnit tests passed (CefUtils 5, distribution layout 3, Desktop MCP 2). Before Run 34, a separately labelled
classpath diagnostic ran for 1059 seconds; it is not an installed exact-binary pass. On unchanged Run 34, the accepted
Xorg/Wayland sessions initialized JCEF and performed real UI/Agent/tool actions. One Wayland process was explicitly
observed alive at 977 seconds. Reviewed installed-product logs contain no `stack smashing` or `SIGABRT`, and the prior
Apport report remained 159161848 bytes with mtime epoch 1789026916. These are bounded observations, not proof that every
native helper path was exercised. See `run34-wayland-desktop/jcef-runtime-excerpt.txt` and `runtime-observation.json`.

## Verifier correction and rejected attempts

The published preflight could accept an existing Minecraft `latest.log` before the current run rendered. Its initial
Run 34 Xorg outputs are retained but rejected as current-run render evidence. The helper now creates a start marker
before launch and requires the log to be newer. Its actual Bash predicate passed three cases in one Linux regression:
stale complete log rejected, fresh complete log accepted, fresh partial startup rejected. Seven Node contracts passed.
Accepted preflights use a separately identified `harness-fresh-log` copy. The original published harness and candidate
binaries are retained unchanged. The helper script hash is in `run34-candidate/verification-harness.json`.

The first Wayland attempt had a blank window. Native `CrBrowserMain` was blocked in `secret_password_store_sync`,
while GNOME's login keyring was locked after automatic login. A concurrent GPU warning did not establish the cause.
Software-rendering, Ozone and GTK diagnostics did not resolve it; their unconfirmed source changes were withdrawn.
After normal password authentication, the active login keyring reported `Locked=false`, and the unchanged Run 34
candidate passed. A pending native GNOME authentication dialog was also completed with the user's supplied password.
No keyring password or storage policy was changed, and no password was persisted in evidence.

Some failed diagnostic JVMs required forced cleanup and are not normal-close evidence. The first Wayland Agent helper
also exceeded its shutdown deadline because the actual frontmost product close was not reached; its failure remains in
`run34-wayland-agent-timeout`. Replay 2 passed with native Hyper-V pointer input. XWayland-reported window origins did
not reliably match the actual desktop, so the successful Quit Game click used the observed native VM frame.
A temporary, bounded GNOME idle inhibitor was used during interactive validation; persistent lock settings were not changed.

## Networking and historical evidence

Guest direct TLS to the official NeoForge repository had failed. Validation used a temporary SSH reverse forward bound
to guest loopback `127.0.0.1:23067`, through the host's existing proxy. HTTPS verification and official repository URLs
were preserved; no system proxy or global Gradle settings were changed. The proxy is a validation environment prerequisite,
not an alternate repository or product code patch. Historical Run 33 NeoForge build and real Quit Game also passed;
those results remain separately bound to Run 33 in `run33-neoforge-xorg`.

## Release boundary

The target is Ubuntu 24.04 LTS x86_64, GNOME Wayland primary and GNOME on Xorg compatibility. Other distributions are
not certified by this evidence. Automatic-login users must respond to the normal GNOME login-keyring authentication prompt.

The repository's deployed release workflow is Windows-specific. Linux exact-binary promotion still needs an authorized
release-control workflow, signed tag, production approval and verified publication of these frozen assets. Neither
`product-status.json` nor a public support claim has been promoted. Candidate metadata remains
`development-not-certified`, `formalSupportClaim=false`, `exactBinaryPromotionEligible=false`; it must not be silently
rewritten to claim a released or certified binary. Stage 15 as a whole remains open at this release boundary.
