# Windows and Linux 0.1.18 release preparation

The user requested publishing new installation packages on 2026-10-23 after [PR #159](https://github.com/Lotulune/Copperbench/pull/159) was reviewed, fixed and merged as `6b248bc21315e3165b421b01b7e5400344ca32a6`. This release contains static model/element previews, the shared professional Agent workflow, and Blockbench recovery/exit synchronization fixes. Product identity, first-party release fixtures, About/help and the UI handshake advance to 0.1.18 together.

## Source verification

The reviewed PR head `368323142efdad004f12c3e1bdc3502b0562bcca` has the same tracked tree as the integration merge. [Build and test](https://github.com/Lotulune/Copperbench/actions/runs/39903242316), [Agent readiness](https://github.com/Lotulune/Copperbench/actions/runs/39903242322), and [Linux candidate checks](https://github.com/Lotulune/Copperbench/actions/runs/39903242387) passed on that head.

Local source verification passed 243 Java tests (one additional conditional skip), 81 UI unit tests, 50 UI-Core contracts, 130 affected Chromium cases, 12 compact-preview cases, Javadoc and the production UI build. The final navigation correction passed 72 targeted checks and a further native Windows JCEF replay. The [retained source summary](../../evidence/maintenance/2026-10-23/release-0.1.18/source-verification.json) distinguishes the earlier comprehensive pass from the navigation follow-up.

Windows source-candidate runtime validation exercised native JCEF previews, real Codex CLI 0.162.1 failure/repair/continuation/cancellation and Blockbench 5.1.6 open/edit/save/exit/reopen. For the isolated 1,303-file, 844 MB Blockbench fixture, first-history click-to-process creation took 30.002015 seconds; reopening with history took 4.565289 seconds. Workspace navigation remained available during preparation. These timings do not measure completion of editor loading.

## Package and publication boundary

The first Windows release run, [39946764142](https://github.com/Lotulune/Copperbench/actions/runs/39946764142), stopped before packaging: 18 of 1,626 tests failed (65 skipped). Agent fixtures passed Windows 8.3 paths into the history store, which serialized those aliases as `canonicalRoot` but compared them with real paths when loading. Normalize the store root before both reading and writing, assert canonical CLI/export paths, and add dot-segment and real Windows short-path regressions. Windows PR checks now include the Agent suite so this boundary is checked before the release job. No installer from this failed attempt was published.

The first Linux consumer [39946437202](https://github.com/Lotulune/Copperbench/actions/runs/39946437202) timed out while `gh run download` was retrieving the frozen artifact, before byte verification. Its replacement was superseded by the Windows repair. The consumer and promotion now use the repository's existing `actions/download-artifact@v8` client in separate steps, preserving source identity, every-file hashes, provenance and package-content checks. Both platforms require new source-bound candidates after the repair.

The repair passed all 103 local Windows Agent tests with no skips, including actual Windows short paths, dot-segment aliases, history recovery, independent Core verification and native export. The twelve package-verifier tests, product-status and Markdown checks also passed. Cloud checks and the rebuilt release remain separate gates.

Release preparation passed ReleaseManifestTest, the production UI build, 81 UI unit tests, 50 UI-Core contracts, the Chromium workbench/help navigation case, product status and Markdown checks. Twelve package-verifier tests and five public-asset verifier tests cover source-bound license files, version selection, update components, missing downloads and altered bytes. The public Windows verifier now derives the release from the exact source instead of hardcoding 0.1.9.

Official 0.1.18 packages are not published at this source snapshot. The signed Windows latest-main build and the frozen Linux producer/consumer/promotion must pass before [current downloads](../releases/current.md) advances from 0.1.17. Package metadata, checksums, provenance and post-publication receipts will identify the actual bytes; earlier source runtime evidence does not establish installer acceptance.

Previews render static geometry/default poses and do not execute arbitrary Java or reproduce animation/full game lighting. Full installer upgrades, Linux GNOME/Wayland certification and player-controlled gameplay were not repeated in the source verification. Linux retains `formalSupportClaim=false`; historical support records keep their original scope. Windows packages remain Authenticode unsigned.
