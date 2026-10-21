# Linux 0.1.15 exact-candidate release authorization

The user requested a new public package release and explicitly selected both Windows and Linux. The corrected 0.1.15 release follows the unpublished 0.1.14 attempt. This authorization covers only the frozen source/run/tag tuple below. Complete installed and player-controlled acceptance was not repeated; `formalSupportClaim` remains `false`. Publication remains pending the signed latest-main tag and protected draft-download comparison.

This report is digest-bound by the release authorization. Record publication outcomes separately without amending this frozen report.

## Source and verified CI

- Frozen product source: `d99aa3f4af171954a575f82de3afd1ee5b34c9a6`.
- Target tag: `v0.1.15-linux-stable`.
- Main candidate: [run 39570341571](https://github.com/Lotulune/Copperbench/actions/runs/39570341571), attempt 1.
- Independent consumer: [run 39572937254](https://github.com/Lotulune/Copperbench/actions/runs/39572937254), harness `d99aa3f4af171954a575f82de3afd1ee5b34c9a6`.
- API receipts: [candidate run](../../evidence/maintenance/2026-10-21/linux-0115/candidate-run.json), [candidate jobs](../../evidence/maintenance/2026-10-21/linux-0115/candidate-jobs.json), [consumer run](../../evidence/maintenance/2026-10-21/linux-0115/candidate-consumer-run.json), [consumer jobs](../../evidence/maintenance/2026-10-21/linux-0115/candidate-consumer-jobs.json).

All thirteen required candidate steps passed: platform/generator and candidate/installed-gate contracts, portable/DEB packaging and layout, isolated headless bootstrap, JCEF X11 startup, bilingual graphical-installer rendering, NeoForge/Fabric 1.21.1 X11 render preflights, metadata freezing, provenance and artifact upload. Optional 26.3 and installed acceptance steps do not establish additional support.

## Frozen bytes and independent verification

Candidate identity: `sha256:034b7a5ec9ed25b55b35957f8f0dfd72e302857842c04a13f617bee33a460a81`.

The [unchanged metadata](../../evidence/maintenance/2026-10-21/linux-0115/consumer/LINUX-CANDIDATE-METADATA.json) retains `development-not-certified`, `formalSupportClaim=false` and `exactBinaryPromotionEligible=false`. The independent consumer downloaded and verified all 17 files, totaling 4383346208 bytes. The [provenance result](../../evidence/maintenance/2026-10-21/linux-0115/consumer/provenance/provenance-verification.json) and [verified bundle](../../evidence/maintenance/2026-10-21/linux-0115/consumer/provenance/verified-bundle.json) bind them to the exact main source, producing workflow, run and attempt.

The small consumer evidence artifact was downloaded locally and all 10 entries in its [hash manifest](../../evidence/maintenance/2026-10-21/linux-0115/consumer/evidence-hashes.json) were checked. This does not claim another local download or execution of every package. The [archive inspection](../../evidence/maintenance/2026-10-21/linux-0115/consumer/archive-entry-verification.json) confirms version 0.1.15 and compares packaged documents, application content and legacy template bytes with the frozen source.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.15 Linux x86_64 Setup.tar.gz` | 1116869190 | `c304781269aed0b36882033ad396b7814bdedd0987b72b50d9a9ec3e118e431c` |
| `Copperbench 0.1.15 Linux x86_64 Setup.tar.gz.build.json` | 312 | `35a60a78f75458301c5940e49fc99078716c0179b6873c5f94a4184038320471` |
| `Copperbench 0.1.15 Linux x86_64.tar.gz` | 1113890825 | `bcf013b9e9661d2d6ef4de58e73c77419cd08ac70c7cab35c10b887c6a55f98f` |
| `LINUX-CANDIDATE-METADATA.json` | 4295 | `5d1e74e5811316526bb35149c9a5d5a3568ba293db9a7f58cae13390b20e7256` |
| `copperbench-linux.spdx.json` | 5676497 | `d3bfe4b54c44a06ec010d7f39a8e89305f136bc6eba13f63c1377a55f2f1ef8f` |
| `copperbench-update-linux-x64-application.zip` | 24206589 | `21360978368e54b4618f1a31ab5d792dc6f37d7885c3afb8c75b1a8de606e421` |
| `copperbench-update-linux-x64-gradle-8.8-bin.zip` | 138068016 | `6c441ca22e52b1c7d1a7227b0644bb8edc2e31ff9c4e3d314ce89df51553f868` |
| `copperbench-update-linux-x64-gradle-9.6.0-bin.zip` | 140757737 | `69fb5b5bf8a76969b13e782ab1fd48a53dc108e9d2895b9799dff9a31e42339e` |
| `copperbench-update-linux-x64-gradle-9.6.1-bin.zip` | 140759308 | `1084357d04fa49bda034238242ed9f4f678b40f8fe53e374e97286d359214f24` |
| `copperbench-update-linux-x64-gradle-9.7.0-bin.zip` | 150386410 | `8df29973eb34ea207d3820d88349a774b92dbf89e6995fb38b56ab12bd78a7dd` |
| `copperbench-update-linux-x64-jdk.zip` | 325235938 | `3658eb3be4f0e0a9d4b7e1a8c3dc37795b6e24cffb36ba6b7238936f8af4efe3` |
| `copperbench-update-linux-x64-jdk21.zip` | 150325720 | `24009fdb64d0ee7c084556705eb5ade730423bac2d2ee64ed6a67f959d685d8d` |
| `copperbench-update-linux-x64-libraries.zip` | 45429171 | `3fa7d7b24f0f8058797a3ef3ebfd5c6039e4195a068b493b502627c280f18775` |
| `copperbench-update-linux-x64.properties` | 866891 | `70ec9710f863deb6d22fef12e975226d11691224e1dfb917e686a87865361bc2` |
| `copperbench_0.1.15_amd64.deb` | 1030860678 | `ece4b83e12132307c82c51a875ae0eccfdc36458b5f1b7671b94b62e0c39ef3d` |
| `linux-candidate-manifest.json` | 6708 | `3f5f21941a3531dba0ec99fa6edbac5a9bd7397a14023e17abf2c8759b45c98f` |
| `linux-candidate-sha256.txt` | 1923 | `0a7ba42529e6a13d43ddca59092463bf7a995a5555df23be53336f035b01f57a` |

## Authorization and publication constraints

`maintenance-ci-0.1.15` permits only this candidate, with exact package digests, successful CI receipts and this report's SHA-256. The user's release request and explicit platform selection authorize publication of this verified 0.1.15 candidate. The [previous 0.1.13 authorization](../../evidence/maintenance/2026-10-21/linux-0115/previous-013-authorization.json) is preserved.

Allowed-signer tags, latest-main validation, protected production approval, the source-delta allowlist, provenance and byte-for-byte downloaded-draft comparison remain required. No new complete GNOME installation, upgrade, uninstall, screen-reader, offline or player-controlled gameplay certification is claimed. Historical 0.1.13 and earlier records retain their own package scope. The [release notes](../releases/v0.1.15-linux.md) and [current downloads](../releases/current.md) distinguish prepared and published versions.
