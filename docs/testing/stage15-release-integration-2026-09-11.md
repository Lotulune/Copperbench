# Stage 15 final-candidate acceptance — 2026-09-11

Run37 passes all ten installed-candidate acceptance gates. Signed-tag/production release promotion remains a separate boundary; this report does not claim public publication or completed formal Linux support.

## Candidate identity and integration

- Source: `f811483935ed682177494b205337843090dc040b`; workflow run `34537005983`.
- Candidate: `sha256:3fc5d9370cd083a4e01d55246e7b5ac281bd4b937c702cfe495e4b0320fb7b02`.
- Debian package SHA-256: `3ca9873c0e59ef65a5626fbf304b4c1e1ad57f490e9eed947fcab6d6dc4d4d80`.
- Portable SHA-256: `cb48687a8ee787e49570c5b1a32d42b74e35cfefe19107ba47469b487fc257f4`.
- Installed and portable `lib/copperbench.jar`: `6a4164510525f7970371d07faa5ea358c364094e868d2ba9f742b1ef6da0928e`.

Main `a3b868ee` was integrated in `ad521757`, preserving Stage14 artifact-content replay checks, manual source persistence and wrapper copy attributes together with the Linux fixes. Run34 remains historical; none of its installed results certify this newer product source.

Run37 passed Linux packaging, clean-tooling bootstrap, JCEF smoke and Fabric/NeoForge Xvfb render checks. The downloaded Debian package, portable archive, SPDX SBOM, platform manifest, immutable metadata and checksum manifest all passed source-bound GitHub attestation verification. Both package digests matched again inside the guest. `verify-linux-candidate-metadata.mjs` verified all four declared asset sizes/hashes and the candidate identity.

## Installed acceptance

The certification guest is Ubuntu 24.04 LTS x86_64, GNOME Wayland and GNOME on Xorg. Each preflight recorded absence of system Java, Gradle and Git. Build dependencies used the existing guest caches and an explicit temporary loopback proxy to official repositories; this is not an offline-build claim.

| Gate | Result | Evidence directory under `evidence/stage15/2026-09-11/` |
| --- | --- | --- |
| Xorg Fabric 1.21.1 installed build/render | Passed | `run37-xorg-fabric` |
| Xorg NeoForge 1.21.1 installed build/render | Passed | `run37-xorg-neoforge` |
| Wayland Fabric 1.21.1 installed build/render | Passed | `run37-wayland-fabric` |
| Wayland NeoForge 1.21.1 installed build/render | Passed | `run37-wayland-neoforge` |
| Xorg external Agent and normal closes | Passed | `run37-xorg-agent` |
| Wayland external Agent and normal closes | Passed | `run37-wayland-agent` |
| Real managed Blockbench edit/save/close | Passed | `run37-wayland-blockbench` |
| Installed Asset Center button to real Blockbench | Passed | `run37-wayland-assets` |
| Installed UI create/save/close/reopen | Passed | `run37-wayland-ui` |
| Portable bundled-runtime build from unrelated CWD | Passed | `run37-portable` |

Both Agent runs passed plan preview, initial build, deliberate revision conflict and committed retry, final build, real Minecraft rendering with at least ten seconds stability, menu-driven normal game close and normal product-window close. Descriptor removal and old-connection rejection passed. One-time credentials came from the real UI, travelled through private stdin, and were not persisted; both published verifier and adapter exited zero.

Blockbench 5.1.6 opened through the installed production locator/process service, rejected a competing asset lease, detected a real saved mesh change, and exited zero after its window close button. The separate Asset Center observation binds Blockbench PID 11812 directly to installed Copperbench PID 11267; no diagnostic classpath override was used.

The newly created `stage15_run37_ui` Fabric workspace reopened through the recent-workspace selector at revision 2. Its `wayland_saved_probe` function visibly retained `say Stage15 Run37 Wayland persistence`; saved and reopened element bytes both hash to `1897c84d261b20bcc13fc330325302f309c22acab0dc52b3678ada5e940a89e0`. The reopened product also exited normally and removed its descriptor.

## Regression and evidence boundaries

The initial full PR regression found one host-sensitive fixture failure among 554 tests (43 skipped): Windows development-SBOM JDK paths differed from Linux paths. Commit `4f02c965` adjusts only those two expected test values before comparing the entire manifest. Four focused manifest/SBOM/platform tests pass locally; full Java/Javadoc, UI and MCP CI run `34539008140` passes. All 12 release authorization contracts pass. Earlier main-integration checks passed 56 focused Java tests, 20 UI-Core tests and the UI build with 234 translation keys.

The test-only file is explicitly permitted in the post-candidate source delta; other unreviewed Java test paths and all product/build changes remain rejected. No product code changed after Run37. The published installed harness includes the current-run log-freshness check. The first Wayland Fabric attempt rejected CRLF in a host-created expected-digest text before installation; a distinct second attempt with LF passed. Published verifier source and frozen binaries were not changed.

Automatic-login GNOME requires normal login-keyring authentication before Chromium can initialize. The supplied password was used only for authenticated guest access and was not recorded. Xorg replay used a workspace whose historical title contains “Wayland”; `loginctl` and the preflight environment record the actual `x11` session. After acceptance the guest was returned to the authorized Wayland configuration with backups retained; temporary proxy and idle-inhibitor sessions ended on reboot.

Reviewed installed logs contain no new `stack smashing` or `SIGABRT`; the existing Apport report remains 159161848 bytes with mtime epoch 1789026916. This is bounded runtime evidence, not exhaustive native-path certification. Automated preflight process cleanup is not counted as normal-close evidence; the independent Agent results supply that evidence. Repository transcripts normalize line endings/trailing whitespace with original hashes recorded; raw guest evidence and immutable metadata remain unchanged.

## Release boundary

The user authorized Linux release CI, main integration, signed-tag progression and production approval. The dedicated tag is `v0.1.0-linux-preview.1`, excluded from the Windows release workflow. The signed tag must point to latest main. Promotion downloads the exact Run37 payload, checks source drift, asset hashes and attestations, and validates the ten hash-bound records in the independent authorization file. It creates a draft, downloads every uploaded asset and compares bytes. Public publication requires explicit `publish` and production approval.

Original metadata retains `development-not-certified`, `formalSupportClaim=false` and `exactBinaryPromotionEligible=false`. The separate approved authorization grants release eligibility to these tested bytes; formal public support remains false until the release boundary is completed. This acceptance covers Ubuntu 24.04 GNOME x86_64, not arbitrary distributions, architectures or additional Linux game-version tracks. Blockbench is a separately installed tool.
