# Linux 0.1.18 exact-candidate release authorization

The user requested publishing new installation packages on 2026-10-23 after the three feature/fix tasks were reviewed and merged. This authorizes only the following frozen Linux 0.1.18 candidate after producer and independent consumer verification. Full installed/player acceptance was not repeated for this authorization; `formalSupportClaim=false`.

This report is digest-bound. Record subsequent publication and installation checks separately without changing these frozen bytes.

## Source and verified CI

- Binary source: `28b912e94b7bf271e91bafeaef7a02daf1abde98`; target tag: `v0.1.18-linux-stable`.
- Main producer: [run 39956381252](https://github.com/Lotulune/Copperbench/actions/runs/39956381252).
- Independent consumer: [run 39959392189](https://github.com/Lotulune/Copperbench/actions/runs/39959392189).
- Receipts: [producer run](../../evidence/maintenance/2026-10-23/linux-0118/candidate-run.json), [producer jobs](../../evidence/maintenance/2026-10-23/linux-0118/candidate-jobs.json), [consumer run](../../evidence/maintenance/2026-10-23/linux-0118/candidate-consumer-run.json), [consumer jobs](../../evidence/maintenance/2026-10-23/linux-0118/candidate-consumer-jobs.json).

The thirteen required candidate steps passed: platform/generator and metadata contracts, portable/DEB packaging and layout, isolated bundled bootstrap, JCEF X11 startup, graphical installer rendering, Fabric/NeoForge 1.21.1 render preflights, metadata freezing, provenance and upload. Optional checks do not extend this classification.

## Frozen bytes

Candidate ID: `sha256:a2754654513b0c48717c31cccbd90742913a5cdb01cd6e072eee6cf4f03d571c`. [Original metadata](../../evidence/maintenance/2026-10-23/linux-0118/consumer/LINUX-CANDIDATE-METADATA.json) retains its original development/not-certified flags. The separate exact-tuple authorization governs publication.

The independent consumer downloaded and verified every frozen file, source/run provenance, source-bound documents, application version, both UI dependency license notices and legacy template/class markers. [Package inspection](../../evidence/maintenance/2026-10-23/linux-0118/consumer/archive-entry-verification.json) and [provenance](../../evidence/maintenance/2026-10-23/linux-0118/consumer/provenance/provenance-verification.json) record those checks. Every entry in the consumer evidence hash manifest was independently checked locally; this does not claim another local download or execution of all Linux packages.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.18 Linux x86_64 Setup.tar.gz` | 1117280899 | `4b24c5b8ba333ab396a4f0bc6e497828610bc5b56ccdb9e7d1e7fdfd48943081` |
| `copperbench_0.1.18_amd64.deb` | 1031348958 | `fca5a3706d279a02f78e3c12c78dde8dbceb3c9c61c810f720ebb4b5a90e16fa` |
| `Copperbench 0.1.18 Linux x86_64.tar.gz` | 1114295335 | `c1c1c0496b9257ab36ef8d986b665bde0157918ccb49f0778f088da468001874` |

## Publication constraints

`maintenance-ci-0.1.18` permits only this source, producer run, tag and exact package digests with successful CI receipts, explicit user publication authorization and this report hash. The [previous authorization](../../evidence/maintenance/2026-10-23/linux-0118/previous-017-authorization.json) is retained.

Signed latest-main tags, the production environment, allowed source delta, provenance and complete downloaded-draft comparison remain required. Full GNOME/Wayland desktop, real-account CLI and player-controlled gameplay certification are not claimed. [Release notes](../releases/v0.1.18-linux.md) and [current downloads](../releases/current.md) report public state separately.
