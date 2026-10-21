# Linux 0.1.14 exact-candidate release authorization

The user requested a new public package release after the simple-creation merge and explicitly selected Windows and Linux 0.1.14. This authorization covers only the frozen source/run/tag tuple below. Complete installed and player-controlled acceptance was not repeated; `formalSupportClaim` remains `false`. Publication remains pending the signed latest-main tag and protected draft-download comparison.

This report is digest-bound by the release authorization. Record publication outcomes separately without amending this frozen report.

## Source and verified CI

- Frozen product source: `095874a66d9d347a6a5fd46befc1a621dd35e9d3`.
- Target tag: `v0.1.14-linux-stable`.
- Main candidate: [run 39558371689](https://github.com/Lotulune/Copperbench/actions/runs/39558371689), attempt 1.
- Independent consumer: [run 39560706171](https://github.com/Lotulune/Copperbench/actions/runs/39560706171), harness `095874a66d9d347a6a5fd46befc1a621dd35e9d3`.
- API receipts: [candidate run](../../evidence/maintenance/2026-10-21/linux-0114/candidate-run.json), [candidate jobs](../../evidence/maintenance/2026-10-21/linux-0114/candidate-jobs.json), [consumer run](../../evidence/maintenance/2026-10-21/linux-0114/candidate-consumer-run.json), [consumer jobs](../../evidence/maintenance/2026-10-21/linux-0114/candidate-consumer-jobs.json).

All thirteen required candidate steps passed: platform/generator and candidate/installed-gate contracts, portable/DEB packaging and layout, isolated headless bootstrap, JCEF X11 startup, bilingual graphical-installer rendering, NeoForge/Fabric 1.21.1 X11 render preflights, metadata freezing, provenance and artifact upload. Optional 26.3 and installed acceptance steps do not establish additional support.

## Frozen bytes and independent verification

Candidate identity: `sha256:3e91492eae5ec053137b443715b3f73a0e77550ab9b7662f0200af1495fd8e42`.

The [unchanged metadata](../../evidence/maintenance/2026-10-21/linux-0114/consumer/LINUX-CANDIDATE-METADATA.json) retains `development-not-certified`, `formalSupportClaim=false` and `exactBinaryPromotionEligible=false`. The independent consumer downloaded and verified all 17 files, totaling 4383345836 bytes. The [provenance result](../../evidence/maintenance/2026-10-21/linux-0114/consumer/provenance/provenance-verification.json) and [verified bundle](../../evidence/maintenance/2026-10-21/linux-0114/consumer/provenance/verified-bundle.json) bind them to the exact main source, producing workflow, run and attempt.

The small consumer evidence artifact was downloaded locally and all 10 entries in its [hash manifest](../../evidence/maintenance/2026-10-21/linux-0114/consumer/evidence-hashes.json) were checked. This does not claim another local download or execution of every package. The [archive inspection](../../evidence/maintenance/2026-10-21/linux-0114/consumer/archive-entry-verification.json) confirms version 0.1.14 and compares packaged documents, application content and legacy template bytes with the frozen source.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.14 Linux x86_64 Setup.tar.gz` | 1116868410 | `310b609bbc108650a3841705a1a28e4801dd45e11da00e254839c9ada8103ed5` |
| `Copperbench 0.1.14 Linux x86_64 Setup.tar.gz.build.json` | 312 | `b24ca3d0ffb0a112b713d979a2d4661053ba7b3ddc35e09520eacaa47af444dc` |
| `Copperbench 0.1.14 Linux x86_64.tar.gz` | 1113890946 | `067121af119532670a92ac02e21c8d6584790efb54c5206f434b211331e2f705` |
| `LINUX-CANDIDATE-METADATA.json` | 4295 | `dab91847dc8f48ca130753407a8a2b19807e124e85ba11b3aa257703949ba326` |
| `copperbench-linux.spdx.json` | 5676497 | `da6fae17e209a259a7bb510e46ba946ee30017b3bc98374a286958cdfffdaca7` |
| `copperbench-update-linux-x64-application.zip` | 24206566 | `49bc6165e1ca697e4239753656e551f70ef1d5e84fe90d330f976560bfcabcaa` |
| `copperbench-update-linux-x64-gradle-8.8-bin.zip` | 138068016 | `6c441ca22e52b1c7d1a7227b0644bb8edc2e31ff9c4e3d314ce89df51553f868` |
| `copperbench-update-linux-x64-gradle-9.6.0-bin.zip` | 140757737 | `69fb5b5bf8a76969b13e782ab1fd48a53dc108e9d2895b9799dff9a31e42339e` |
| `copperbench-update-linux-x64-gradle-9.6.1-bin.zip` | 140759308 | `1084357d04fa49bda034238242ed9f4f678b40f8fe53e374e97286d359214f24` |
| `copperbench-update-linux-x64-gradle-9.7.0-bin.zip` | 150386410 | `8df29973eb34ea207d3820d88349a774b92dbf89e6995fb38b56ab12bd78a7dd` |
| `copperbench-update-linux-x64-jdk.zip` | 325235938 | `3658eb3be4f0e0a9d4b7e1a8c3dc37795b6e24cffb36ba6b7238936f8af4efe3` |
| `copperbench-update-linux-x64-jdk21.zip` | 150325720 | `24009fdb64d0ee7c084556705eb5ade730423bac2d2ee64ed6a67f959d685d8d` |
| `copperbench-update-linux-x64-libraries.zip` | 45429171 | `3fa7d7b24f0f8058797a3ef3ebfd5c6039e4195a068b493b502627c280f18775` |
| `copperbench-update-linux-x64.properties` | 866891 | `5d73cacefbe9870d551f8099379940225e18f99e237e85b8df22fc0ce670fd97` |
| `copperbench_0.1.14_amd64.deb` | 1030860988 | `27a2e79beffcdd3717243b801d764f9dc26ed615965786dcb3ce77f9b5999856` |
| `linux-candidate-manifest.json` | 6708 | `ebd09b43465ff1b9837f920d8551ecb444895dcb3e04a7f1c4f084c7c5037f45` |
| `linux-candidate-sha256.txt` | 1923 | `a90ead942921f636547973bdcac4d6e8c97ed965a7db4a77ca9daa9c3b1fd53f` |

## Authorization and publication constraints

`maintenance-ci-0.1.14` permits only this candidate, with exact package digests, successful CI receipts and this report's SHA-256. The user's release request and explicit platform selection authorize publication of this verified 0.1.14 candidate. The [previous 0.1.13 authorization](../../evidence/maintenance/2026-10-21/linux-0114/previous-013-authorization.json) is preserved.

Allowed-signer tags, latest-main validation, protected production approval, the source-delta allowlist, provenance and byte-for-byte downloaded-draft comparison remain required. No new complete GNOME installation, upgrade, uninstall, screen-reader, offline or player-controlled gameplay certification is claimed. Historical 0.1.13 and earlier records retain their own package scope. The [release notes](../releases/v0.1.14-linux.md) and [current downloads](../releases/current.md) distinguish prepared and published versions.
