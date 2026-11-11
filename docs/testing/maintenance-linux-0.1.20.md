# Linux 0.1.20 exact-candidate release authorization

The user requested merging the open pull requests and publishing new installation packages on 2026-11-11, after the Notebook refinements of PR #173 and the repository reductions of PR #174 and #175 were merged. This authorizes only the following frozen Linux 0.1.20 candidate after producer and independent consumer verification. Full installed/player acceptance was not repeated for this authorization; `formalSupportClaim=false`.

This report is digest-bound. Record subsequent publication and installation checks separately without changing these frozen bytes.

## Source and verified CI

- Binary source: `5e1ab97f0a10ebf2792e2622ad0d563f8b4ffc29`; target tag: `v0.1.20-linux-stable`.
- Main producer: [run 42973803995](https://github.com/Lotulune/Copperbench/actions/runs/42973803995).
- Independent consumer: [run 42976562215](https://github.com/Lotulune/Copperbench/actions/runs/42976562215).
- Receipts: [producer run](../../evidence/maintenance/2026-11-11/linux-0120/candidate-run.json), [producer jobs](../../evidence/maintenance/2026-11-11/linux-0120/candidate-jobs.json), [consumer run](../../evidence/maintenance/2026-11-11/linux-0120/candidate-consumer-run.json), [consumer jobs](../../evidence/maintenance/2026-11-11/linux-0120/candidate-consumer-jobs.json).

The thirteen required candidate steps passed: platform/generator and metadata contracts, portable/DEB packaging and layout, isolated bundled bootstrap, JCEF X11 startup, graphical installer rendering, Fabric/NeoForge 1.21.1 render preflights, metadata freezing, provenance and upload. Optional checks do not extend this classification.

## Frozen bytes

Candidate ID: `sha256:2df78ecfa00b345423438a9c8c4164b96aa1895a0f7856eb259a733de82a597b`. [Original metadata](../../evidence/maintenance/2026-11-11/linux-0120/consumer/LINUX-CANDIDATE-METADATA.json) retains its original development/not-certified flags. The separate exact-tuple authorization governs publication.

The independent consumer downloaded and verified every frozen file, source/run provenance, source-bound documents, application version, both UI dependency license notices and legacy template/class markers. [Package inspection](../../evidence/maintenance/2026-11-11/linux-0120/consumer/archive-entry-verification.json) and [provenance](../../evidence/maintenance/2026-11-11/linux-0120/consumer/provenance/provenance-verification.json) record those checks. Every entry in the consumer evidence hash manifest was independently checked locally; this does not claim another local download or execution of all Linux packages.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.20 Linux x86_64 Setup.tar.gz` | 1116799780 | `735ff64e7033005a32e194182d148113df8818ae6b795bbca4b5c73288a2e2ed` |
| `copperbench_0.1.20_amd64.deb` | 1031308604 | `a474240e1978a9f0f610b0dbb1a57b616f67b4449fa8649e6e241e7f7c7e3af9` |
| `Copperbench 0.1.20 Linux x86_64.tar.gz` | 1113806701 | `52756c3736bb18c4c7089c7c1c203897a2f0e7afc52b9f4c1c0687358b15e889` |

## Publication constraints

`maintenance-ci-0.1.20` permits only this source, producer run, tag and exact package digests with successful CI receipts, explicit user publication authorization and this report hash. The [previous authorization](../../evidence/maintenance/2026-11-11/linux-0120/previous-019-authorization.json) is retained.

Signed latest-main tags, the production environment, allowed source delta, provenance and complete downloaded-draft comparison remain required. Full GNOME/Wayland desktop, real-account CLI and player-controlled gameplay certification are not claimed. [Release notes](../releases/v0.1.20-linux.md) and [current downloads](../releases/current.md) report public state separately.
