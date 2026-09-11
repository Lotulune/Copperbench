# Stage15 Run42 final acceptance — 2026-09-11

Run42 passes all ten installed-candidate gates plus both legacy-preferences migration cases. It supersedes Run37 for release because PR review identified and fixed a real Linux upgrade-path omission. Signed-tag/production promotion and explicit public publication remain separate release steps.

## Frozen candidate

- Source: `61f69bf1f8dfdd3efbcf379afbaafbbfb11d3466`; workflow run `34547192490`.
- Candidate: `sha256:45e80ce44b0918215048e65fe8b2d45e45698d3f394453940918a1eee40eea09`.
- Debian SHA-256: `ff1d2e3c3e7120165aece357d9a0b4a1fba93ff37e31e44dd8f733b0ba7dc473` (888099122 bytes).
- Portable SHA-256: `746e8795b90689fe2d2672bb059f01f2bd8cca5c0e6689b89e5842baa4e38644` (968204286 bytes).
- Installed and portable product JAR: `d4bcaa2831415500bf99da16f7ab34aaf3e093de35497932502416f3853afcde`.

Both package hashes match on the host and guest. The six original assets (Debian, portable, SPDX SBOM, platform manifest, immutable metadata and checksum manifest) passed GitHub attestation verification against this source and the candidate workflow. The metadata verifier confirmed asset sizes/hashes and candidate identity. Original candidate flags remain `development-not-certified`, `formalSupportClaim=false`, `exactBinaryPromotionEligible=false`; a separate hash-bound authorization controls promotion without rebuilding.

## Installed acceptance

All evidence below is under `evidence/stage15/2026-09-11/`; exact JSON hashes are in `run42-candidate/accepted-evidence-index.json`.

| Gate | Result | Evidence |
| --- | --- | --- |
| Xorg Fabric 1.21.1 build/render | Passed | `run42-xorg-fabric` |
| Xorg NeoForge 1.21.1 build/render | Passed | `run42-xorg-neoforge` |
| Wayland Fabric 1.21.1 build/render | Passed | `run42-wayland-fabric` |
| Wayland NeoForge 1.21.1 build/render | Passed | `run42-wayland-neoforge` |
| Xorg Agent and normal closes | Passed | `run42-xorg-agent` |
| Wayland Agent and normal closes | Passed | `run42-wayland-agent` |
| Managed Blockbench edit/save/close | Passed | `run42-wayland-blockbench` |
| Installed Asset Center to native Blockbench | Passed | `run42-wayland-assets` |
| UI create/save/normal-close/reopen | Passed | `run42-wayland-ui` |
| Portable build from unrelated working directory | Passed | `run42-portable` |
| Installed old/modern preference migration | Both passed | `run42-preferences-migration` |

The guest is Ubuntu 24.04 LTS x86_64 with real GNOME Wayland and Xorg sessions. Each preflight verified no system Java, Gradle or Git. Build dependencies used warmed guest caches and a temporary loopback proxy to official repositories; this is not an offline-build claim. The published preflight requires Minecraft logs newer than the current launch marker. Automated cleanup is not counted as a normal close.

Both independent Agent runs used one-time credentials copied from the installed UI through private stdin. They passed plan preview, first build, deliberate revision conflict and committed retry, final build, real Minecraft rendering with at least ten seconds stability, menu-driven game close and normal product close. Both descriptor removal and old-connection rejection passed; verifier and adapter exit codes are zero and no credential was persisted. The disposable workspace title contains “Wayland” in both sessions; system environment records identify the actual session.

Blockbench 5.1.6 opened through the installed locator/process service, rejected a competing lease, detected a real saved mesh change and exited zero. The separate Asset Center button launched Blockbench PID 11272 directly from installed Copperbench PID 10777. No diagnostic classpath override was used.

The UI-created `stage15_run42_ui` Fabric workspace reopened through the recent-workspace selector at revision 2. The function visibly retained `say Stage15 Run42 Wayland persistence`. Saved/reopened bytes hash to `4ebb972e89dc6ceff7a178558526d5d239759d73fca611fc12c6eab095e4b2bc`; the reopened window also closed normally and removed its descriptor.

## Legacy preference fix and regression

`LegacyPreferencesMigration` runs before PreferencesManager's existing conversion/load step. It copies pre-XDG `~/.copperbench/userpreferences`, or the older `preferences` format, into XDG configuration only when neither active format exists. Old files remain unchanged, explicit user-home overrides remain isolated, and Windows retains its existing path behavior. A private temporary copy is moved without replacement; failed imports stop initialization rather than silently saving defaults. Four migration regressions and six path/integration checks pass.

The installed-JAR probe used two fresh isolated homes, loaded the seeded old value through real PreferencesManager initialization, preserved each original file and verified subsequent writes under XDG. Standalone probe JVM flags open AWT serialization packages, matching facilities provided by the normal launcher. These accepted runs have no source/classpath override. Earlier source-overlay diagnostics only validated the probe and are not candidate evidence.

Full Java/Javadoc, UI and MCP CI run `34547195549` passes. Fifteen release authorization contracts pass, including mandatory migration evidence and rejection of diagnostic overrides. The earlier main integration preserved Stage14 artifact replay/manual source fixes; Run34 and Run37 remain historical evidence. No product/build input changed after Run42; subsequent changes are release controls and evidence only.

Reviewed product logs contain no new `stack smashing` or `SIGABRT`. The old Apport file remains 159161848 bytes with mtime epoch 1789026916; this is bounded runtime observation, not exhaustive native-path proof. Guest session backups remain, Wayland was restored, and temporary proxy/idle inhibitors ended on reboot. Transcript normalization is recorded with original hashes; guest originals and frozen metadata are unchanged.

## Release scope

The user authorized Linux release CI, main integration, signed tags and production approval. The dedicated `v0.1.0-linux-preview.2` tag must be signed and point to latest main. Promotion rejects build-affecting source drift, re-verifies all frozen assets and the ten gates plus migration evidence, creates a draft by default, then downloads and compares all uploaded bytes. Public publication requires explicit `publish` and production approval. Formal public Linux support is not claimed by this report before that boundary is complete.

Validation is limited to Ubuntu 24.04 GNOME x86_64, with installed Fabric/NeoForge 1.21.1 runtime replay. Other distributions, architectures and additional Linux game-version tracks are not certified by these results. Blockbench remains external. Automatic-login GNOME may require normal login-keyring authentication before Chromium initializes; no keyring password or policy was changed.

The first signed tag `v0.1.0-linux-preview.1` and production-approved workflow `34554830164` created an unpublished draft. All seven GitHub asset API digests match the source payload, but final byte comparison failed because GitHub normalized portable-name spaces to dots. Release control now maps names consistently for resume and byte comparison, rejecting collisions and unsupported names. Tag/draft 1 remain historical; tag 2 will re-run protected promotion against the same Run42 bytes. No product code or frozen metadata was changed. See `run42-release-transfer` evidence.
