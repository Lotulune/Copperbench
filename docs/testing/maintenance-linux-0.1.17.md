# Linux 0.1.17 exact-candidate release authorization

The user explicitly requested merging the fixes, publishing new packages, and updating README/documentation on 2026-10-22. This authorizes the following fixed Linux maintenance candidate after its producer and independent consumer passed. Full installed/player acceptance was not repeated; `formalSupportClaim=false`.

This report is digest-bound by the authorization. Record publication results separately without amending this frozen report.

## Source and verified CI

- Binary source: `81563c9bc6ae4371b662a65b1b0bbf5ce9e5e4cb`; target tag: `v0.1.17-linux-stable`.
- Main producer: [run 39764178724](https://github.com/Lotulune/Copperbench/actions/runs/39764178724), attempt 1.
- Independent consumer: [run 39766634528](https://github.com/Lotulune/Copperbench/actions/runs/39766634528), harness on the same source.
- Receipts: [producer run](../../evidence/maintenance/2026-10-22/linux-0117/candidate-run.json), [producer jobs](../../evidence/maintenance/2026-10-22/linux-0117/candidate-jobs.json), [consumer run](../../evidence/maintenance/2026-10-22/linux-0117/candidate-consumer-run.json), [consumer jobs](../../evidence/maintenance/2026-10-22/linux-0117/candidate-consumer-jobs.json).

The thirteen required candidate checks passed, including platform/generator and metadata contracts, portable/DEB packaging and layout, isolated bundled bootstrap, JCEF X11 startup, graphical installer rendering, Fabric/NeoForge 1.21.1 render preflights, metadata freezing, provenance and upload. Optional 26.3 and installed checks do not extend this classification.

## Frozen bytes

Candidate ID: `sha256:bb277f99e8dc002eccdc427cad11d61f85ba2a29f1ac6f5b6af125c94fe40483`. The [original metadata](../../evidence/maintenance/2026-10-22/linux-0117/consumer/LINUX-CANDIDATE-METADATA.json) retains its development/not-certified classification and promotion-ineligible flag; this separate exact-tuple maintenance authorization governs publication.

The independent consumer downloaded and verified 17 files totaling 4383955272 bytes against the [signed source/run statement](../../evidence/maintenance/2026-10-22/linux-0117/consumer/provenance/provenance-verification.json), and checked [packaged version, source-bound documents, license notice and template/class markers](../../evidence/maintenance/2026-10-22/linux-0117/consumer/archive-entry-verification.json). Every entry in the consumer evidence hash manifest was independently checked locally; this is not a second local download or execution of all Linux packages.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.17 Linux x86_64 Setup.tar.gz` | 1117017491 | `8170a6cb157f50a25a16873862d36c503de50b38343aa1b4f55ba3bd7f5880c8` |
| `Copperbench 0.1.17 Linux x86_64 Setup.tar.gz.build.json` | 312 | `51a5d4500c3618a8ed4d5f47a0cee5842be778c84e68c6c5ee747cae035ccb28` |
| `Copperbench 0.1.17 Linux x86_64.tar.gz` | 1114026945 | `9e4062ecdd57ddc7ff954befd60212d0ded123e20f84615e89c9ddec97fab3fc` |
| `LINUX-CANDIDATE-METADATA.json` | 4295 | `bb4442f7f543666ccb1ac480603a81cca3c499097fbaf78a35e1dbf6886609e5` |
| `copperbench-linux.spdx.json` | 5676497 | `6088ec26dcb19a450197ea685b8885c016f83daad01f48077926d62ab65e55cf` |
| `copperbench-update-linux-x64-application.zip` | 24349060 | `86f0fcccadcb6aa6a74e753cd33131652d865a0366cc61d12fe03386f5f0f56a` |
| `copperbench-update-linux-x64-gradle-8.8-bin.zip` | 138068016 | `6c441ca22e52b1c7d1a7227b0644bb8edc2e31ff9c4e3d314ce89df51553f868` |
| `copperbench-update-linux-x64-gradle-9.6.0-bin.zip` | 140757737 | `69fb5b5bf8a76969b13e782ab1fd48a53dc108e9d2895b9799dff9a31e42339e` |
| `copperbench-update-linux-x64-gradle-9.6.1-bin.zip` | 140759308 | `1084357d04fa49bda034238242ed9f4f678b40f8fe53e374e97286d359214f24` |
| `copperbench-update-linux-x64-gradle-9.7.0-bin.zip` | 150386410 | `8df29973eb34ea207d3820d88349a774b92dbf89e6995fb38b56ab12bd78a7dd` |
| `copperbench-update-linux-x64-jdk.zip` | 325235938 | `3658eb3be4f0e0a9d4b7e1a8c3dc37795b6e24cffb36ba6b7238936f8af4efe3` |
| `copperbench-update-linux-x64-jdk21.zip` | 150325720 | `24009fdb64d0ee7c084556705eb5ade730423bac2d2ee64ed6a67f959d685d8d` |
| `copperbench-update-linux-x64-libraries.zip` | 45429171 | `3fa7d7b24f0f8058797a3ef3ebfd5c6039e4195a068b493b502627c280f18775` |
| `copperbench-update-linux-x64.properties` | 867405 | `b8ed3964d54a3a30990f5f18809467de9457ef35703fcdbe9668cb9b51d2041a` |
| `copperbench_0.1.17_amd64.deb` | 1031042336 | `1ca009b4084868c12b79d7fd6d6341897ef696a62e49ca1d81b80978a01d117a` |
| `linux-candidate-manifest.json` | 6708 | `f35eeb73781a58453e27ab10709c908a61dff9f10bc0090ff4303b7ff8d2f36c` |
| `linux-candidate-sha256.txt` | 1923 | `829e4fba09c4eeb5750dbaae038db8abe39e9489b104978fb86b3c12f2f60193` |

## Publication constraints

`maintenance-ci-0.1.17` permits only this source, producer run and target tag with exact package digests, successful CI receipts, explicit user approval and this report hash. The [0.1.16 authorization](../../evidence/maintenance/2026-10-22/linux-0117/previous-016-authorization.json) is preserved.

The signed latest-main tag, protected production approval, allowed source delta, provenance and complete downloaded-draft comparison remain required. No new full GNOME/Wayland installation, upgrade, uninstall, real-account CLI or player-controlled gameplay certification is claimed. [Release notes](../releases/v0.1.17-linux.md) and [current downloads](../releases/current.md) report public state separately.
