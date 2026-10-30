# Linux 0.1.19 exact-candidate release authorization

The user requested publishing new installation packages on 2026-10-30 after the Notebook interface redesign and Agent approval fixes were reviewed and merged. This authorizes only the following frozen Linux 0.1.19 candidate after producer and independent consumer verification. Full installed/player acceptance was not repeated for this authorization; `formalSupportClaim=false`.

This report is digest-bound. Record subsequent publication and installation checks separately without changing these frozen bytes.

## Source and verified CI

- Binary source: `a6851b3b16a071a922c833d66b3e7ae99008b58f`; target tag: `v0.1.19-linux-stable`.
- Main producer: [run 41025635804](https://github.com/Lotulune/Copperbench/actions/runs/41025635804).
- Independent consumer: [run 41029948787](https://github.com/Lotulune/Copperbench/actions/runs/41029948787).
- Receipts: [producer run](../../evidence/maintenance/2026-10-30/linux-0119/candidate-run.json), [producer jobs](../../evidence/maintenance/2026-10-30/linux-0119/candidate-jobs.json), [consumer run](../../evidence/maintenance/2026-10-30/linux-0119/candidate-consumer-run.json), [consumer jobs](../../evidence/maintenance/2026-10-30/linux-0119/candidate-consumer-jobs.json).

The thirteen required candidate steps passed: platform/generator and metadata contracts, portable/DEB packaging and layout, isolated bundled bootstrap, JCEF X11 startup, graphical installer rendering, Fabric/NeoForge 1.21.1 render preflights, metadata freezing, provenance and upload. Optional checks do not extend this classification.

## Frozen bytes

Candidate ID: `sha256:088650890e4ac7d1f5a42669f63b2b04a0c2cc1336da97f507825aa55235cd57`. [Original metadata](../../evidence/maintenance/2026-10-30/linux-0119/consumer/LINUX-CANDIDATE-METADATA.json) retains its original development/not-certified flags. The separate exact-tuple authorization governs publication.

The independent consumer downloaded and verified every frozen file, source/run provenance, source-bound documents, application version, both UI dependency license notices and legacy template/class markers. [Package inspection](../../evidence/maintenance/2026-10-30/linux-0119/consumer/archive-entry-verification.json) and [provenance](../../evidence/maintenance/2026-10-30/linux-0119/consumer/provenance/provenance-verification.json) record those checks. Every entry in the consumer evidence hash manifest was independently checked locally; this does not claim another local download or execution of all Linux packages.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.19 Linux x86_64 Setup.tar.gz` | 1117293947 | `d71214cf321ae72298ccf8513c28c232aff8951ca413f7bd924783c3372e6d19` |
| `copperbench_0.1.19_amd64.deb` | 1031363732 | `af40c52ada059be26e1b56f86c5cbedd8ce641ee7cf7552d4790c13ece53e93e` |
| `Copperbench 0.1.19 Linux x86_64.tar.gz` | 1114310353 | `936db85bcb361d75460aa95cf6e87f957a0aec8fb60076653a622fd8e2008ef5` |

## Publication constraints

`maintenance-ci-0.1.19` permits only this source, producer run, tag and exact package digests with successful CI receipts, explicit user publication authorization and this report hash. The [previous authorization](../../evidence/maintenance/2026-10-30/linux-0119/previous-018-authorization.json) is retained.

Signed latest-main tags, the production environment, allowed source delta, provenance and complete downloaded-draft comparison remain required. Full GNOME/Wayland desktop, real-account CLI and player-controlled gameplay certification are not claimed. [Release notes](../releases/v0.1.19-linux.md) and [current downloads](../releases/current.md) report public state separately.
