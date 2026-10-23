# Windows 0.1.18 publication

[Windows v0.1.18](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.18) was published at **2026-10-23T14:15:18Z** from signed source `28b912e94b7bf271e91bafeaef7a02daf1abde98`.

The user requested publishing new installation packages on 2026-10-23 after [PR #159](https://github.com/Lotulune/Copperbench/pull/159) was reviewed, fixed and merged as `6b248bc21315e3165b421b01b7e5400344ca32a6`. This release contains static model/element previews, the shared professional Agent workflow, and Blockbench recovery/exit synchronization fixes. Product identity, first-party release fixtures, About/help and the UI handshake advance to 0.1.18 together.

## Source verification

The reviewed PR head `368323142efdad004f12c3e1bdc3502b0562bcca` has the same tracked tree as the integration merge. [Build and test](https://github.com/Lotulune/Copperbench/actions/runs/39903242316), [Agent readiness](https://github.com/Lotulune/Copperbench/actions/runs/39903242322), and [Linux candidate checks](https://github.com/Lotulune/Copperbench/actions/runs/39903242387) passed on that head.

Local source verification passed 243 Java tests (one additional conditional skip), 81 UI unit tests, 50 UI-Core contracts, 130 affected Chromium cases, 12 compact-preview cases, Javadoc and the production UI build. The final navigation correction passed 72 targeted checks and a further native Windows JCEF replay. The [retained source summary](../../evidence/maintenance/2026-10-23/release-0.1.18/source-verification.json) distinguishes the earlier comprehensive pass from the navigation follow-up.

Windows source-candidate runtime validation exercised native JCEF previews, real Codex CLI 0.162.1 failure/repair/continuation/cancellation and Blockbench 5.1.6 open/edit/save/exit/reopen. For the isolated 1,303-file, 844 MB Blockbench fixture, first-history click-to-process creation took 30.002015 seconds; reopening with history took 4.565289 seconds. Workspace navigation remained available during preparation. These timings do not measure completion of editor loading.

## Release gate repair

The first Windows release run, [39946764142](https://github.com/Lotulune/Copperbench/actions/runs/39946764142), stopped before packaging: 18 of 1,626 tests failed (65 skipped). Agent fixtures passed Windows 8.3 paths into the history store, which serialized those aliases as `canonicalRoot` but compared them with real paths when loading. The repair canonicalized the store root before reading and writing, aligned CLI/export path assertions, and added dot-segment and real Windows short-path regressions. Windows PR checks now include the Agent suite so this boundary is checked before the release job. No installer from this failed attempt was published.

The first Linux consumer [39946437202](https://github.com/Lotulune/Copperbench/actions/runs/39946437202) timed out while `gh run download` was retrieving the frozen artifact, before byte verification. Its replacement was superseded by the Windows repair. The consumer and promotion now use the repository's existing `actions/download-artifact@v8` client in separate steps, preserving source identity, every-file hashes, provenance and package-content checks. Both platforms were rebuilt from the merged repair; the publication records below identify those final bytes.

The repair passed all 103 local Windows Agent tests with no skips, including actual Windows short paths, dot-segment aliases, history recovery, independent Core verification and native export. The twelve package-verifier tests, product-status and Markdown checks also passed. The cloud release and installed-package results below retain their separate artifact identities.

The repair was merged through [PR #162](https://github.com/Lotulune/Copperbench/pull/162). [Main regression](https://github.com/Lotulune/Copperbench/actions/runs/39956380982) and [documentation](https://github.com/Lotulune/Copperbench/actions/runs/39956380990) passed on the repaired release source `28b912e94b7bf271e91bafeaef7a02daf1abde98` before the signed release build.

## Public package verification

The [Windows release workflow](https://github.com/Lotulune/Copperbench/actions/runs/39962157466) passed release tests/Javadoc, packaging, installer branding, SBOM, provenance, payload and draft checks before publication. The [independent public verifier](https://github.com/Lotulune/Copperbench/actions/runs/39968464541) downloaded and hashed all 15 public assets, checked checksums/API digests and exact source/run provenance, and inspected packaged version, source documents, both UI dependency license notices and template/class markers.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Copperbench.0.1.18.Windows.64bit.exe](https://github.com/Lotulune/Copperbench/releases/download/v0.1.18/Copperbench.0.1.18.Windows.64bit.exe) | 503558587 | `1a1327e1ba239f3af54fd81daf803dce51db2aad3cfbde122b9b18bf50936342` |
| [Copperbench.0.1.18.Windows.64bit.zip](https://github.com/Lotulune/Copperbench/releases/download/v0.1.18/Copperbench.0.1.18.Windows.64bit.zip) | 606738657 | `f413160a19d8d858f881cd0e03ae41288d0bd76db9f2398a2af99f726363c109` |
| [Copperbench.0.1.18.Windows.64bit.msix](https://github.com/Lotulune/Copperbench/releases/download/v0.1.18/Copperbench.0.1.18.Windows.64bit.msix) | 627429652 | `4cab2227c3eec767e57b3a6f5c0585361558885f956f719417e5d1021b5a2ed6` |

Installation, upgrade, user-file retention and removal results are recorded separately in [public-package acceptance](installed-release-0.1.18.md). Source runtime checks above retain their source-candidate identity. Windows packages remain Authenticode unsigned; MSIX installation, UAC and full Windows 11 desktop/player acceptance are not inferred from cloud automation.

[Linux publication](linux-publication-0.1.18.md) and [current downloads](../releases/current.md) provide the other platform and public entry points. Tagged/shipped documentation retains its build-time preparation text; this record does not alter released bytes.
