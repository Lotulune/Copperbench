# Linux 0.1.16 exact-candidate release authorization

The user authorized committing/pushing the reviewed work, processing the open PRs, and publishing new installers. This follows the existing Windows/Linux release process and authorizes only the frozen Linux source/run/tag tuple below. Full installed and player-controlled acceptance was not repeated; `formalSupportClaim=false`. Publication still requires the signed latest-main tag, production review and exact downloaded-draft comparison.

This report is digest-bound by the release authorization. Record publication outcomes separately without amending this frozen report.

## Source and verified CI

- Frozen source: `c87d2e0f0e924adc6ad31575aa5b06fe6da9dcf0`.
- Target tag: `v0.1.16-linux-stable`.
- Main candidate: [run 39723695135](https://github.com/Lotulune/Copperbench/actions/runs/39723695135), attempt 1.
- Independent consumer: [run 39725586361](https://github.com/Lotulune/Copperbench/actions/runs/39725586361), harness at the same source.
- API receipts: [candidate run](../../evidence/maintenance/2026-10-22/linux-0116/candidate-run.json), [candidate jobs](../../evidence/maintenance/2026-10-22/linux-0116/candidate-jobs.json), [consumer run](../../evidence/maintenance/2026-10-22/linux-0116/candidate-consumer-run.json), [consumer jobs](../../evidence/maintenance/2026-10-22/linux-0116/candidate-consumer-jobs.json).

All thirteen required candidate steps passed: platform/generator and candidate/installed-gate contracts, portable/DEB packaging and layout, isolated headless bootstrap, JCEF X11 startup, graphical-installer rendering, NeoForge/Fabric 1.21.1 X11 render preflights, metadata freezing, provenance and artifact upload. Optional 26.3 and installed acceptance steps do not establish new support.

## Frozen bytes and independent verification

Candidate ID: `sha256:efae25788a134e82951fb8fea4eef612af79113322cacb5b2bff6bbeae54812f`.

The [unchanged metadata](../../evidence/maintenance/2026-10-22/linux-0116/consumer/LINUX-CANDIDATE-METADATA.json) retains `development-not-certified`, `formalSupportClaim=false` and `exactBinaryPromotionEligible=false`. The consumer downloaded and verified 17 files totaling 4383859263 bytes. The [provenance result](../../evidence/maintenance/2026-10-22/linux-0116/consumer/provenance/provenance-verification.json) and [verified bundle](../../evidence/maintenance/2026-10-22/linux-0116/consumer/provenance/verified-bundle.json) bind them to the exact source, producer workflow, run and attempt.

All 10 files in the downloaded consumer [hash manifest](../../evidence/maintenance/2026-10-22/linux-0116/consumer/evidence-hashes.json) were checked locally. This is not another local download or execution of every package. The [archive inspection](../../evidence/maintenance/2026-10-22/linux-0116/consumer/archive-entry-verification.json) confirms the application version, source-bound documents, legacy fix markers and templates. It also verifies the new UI notice file at `Copperbench0116/license/Copperbench UI Dependency Licenses.txt` byte-for-byte against source: 69123 bytes, SHA-256 `e0bf667519d677902d6aa3ccde353a79e6bfad80f7bd1c410f98fafa2b1312b5`.

The earlier successful `7a0317f4` candidate lacked the added UI notice file and is not used for this authorization. Runtime code is unchanged by the notice correction; these frozen bytes include it.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.16 Linux x86_64 Setup.tar.gz` | 1117006347 | `9d1d74dc066a11d3724912209bd7eb5c47a06a036405ca04dbca4c9713e325c4` |
| `Copperbench 0.1.16 Linux x86_64 Setup.tar.gz.build.json` | 312 | `28997feeacf31194465deab17ead6c67884dc7e58dc6510dd9315179214eba7f` |
| `Copperbench 0.1.16 Linux x86_64.tar.gz` | 1114023203 | `b27084b1a7875ba50a6c4e11306892a0e1f932624ba4d6b8b435b99dc5a1bf2b` |
| `LINUX-CANDIDATE-METADATA.json` | 4295 | `752ed9833cd7fbc6797e47ee2f5385010925e781d44bb29b25716c0dab16216e` |
| `copperbench-linux.spdx.json` | 5676497 | `3087905c0abc8dfa3e7f6c12d442cbbe40d371fa4febd3eb8cc24fb616fcf882` |
| `copperbench-update-linux-x64-application.zip` | 24347261 | `c719b49b7dbfaddca63f9ae95a99ce263fb02f4a33c4fea8f5f44984e7b41f95` |
| `copperbench-update-linux-x64-gradle-8.8-bin.zip` | 138068016 | `6c441ca22e52b1c7d1a7227b0644bb8edc2e31ff9c4e3d314ce89df51553f868` |
| `copperbench-update-linux-x64-gradle-9.6.0-bin.zip` | 140757737 | `69fb5b5bf8a76969b13e782ab1fd48a53dc108e9d2895b9799dff9a31e42339e` |
| `copperbench-update-linux-x64-gradle-9.6.1-bin.zip` | 140759308 | `1084357d04fa49bda034238242ed9f4f678b40f8fe53e374e97286d359214f24` |
| `copperbench-update-linux-x64-gradle-9.7.0-bin.zip` | 150386410 | `8df29973eb34ea207d3820d88349a774b92dbf89e6995fb38b56ab12bd78a7dd` |
| `copperbench-update-linux-x64-jdk.zip` | 325235938 | `3658eb3be4f0e0a9d4b7e1a8c3dc37795b6e24cffb36ba6b7238936f8af4efe3` |
| `copperbench-update-linux-x64-jdk21.zip` | 150325720 | `24009fdb64d0ee7c084556705eb5ade730423bac2d2ee64ed6a67f959d685d8d` |
| `copperbench-update-linux-x64-libraries.zip` | 45429171 | `3fa7d7b24f0f8058797a3ef3ebfd5c6039e4195a068b493b502627c280f18775` |
| `copperbench-update-linux-x64.properties` | 867405 | `e602e488d205c7cb166ed116fabd07d3f9d4fe199bba00b6e02fc6c40befdbb5` |
| `copperbench_0.1.16_amd64.deb` | 1030963012 | `96cd39fba71273981e6b8accea18ba73b67f5c2213077b3a0aba1bc9fcd08ebb` |
| `linux-candidate-manifest.json` | 6708 | `56bf7101d4e60e2cdb738f92427308c0efdea803e61c2aebb8d0d8222f2647b8` |
| `linux-candidate-sha256.txt` | 1923 | `eccedb333ff93fe93f2c75f50c948276c014da9d302fbd792a263ab8a7f0f282` |

## Authorization and publication constraints

`maintenance-ci-0.1.16` permits only this source, producing run and release tag, with exact package digests, successful CI receipts, explicit approval and this report's SHA-256. The [previous 0.1.15 authorization](../../evidence/maintenance/2026-10-22/linux-0116/previous-015-authorization.json) is retained.

Allowed-signer tags, latest-main checks, protected production review, source-delta restrictions, provenance and full downloaded-draft comparison remain required. No new GNOME installation, upgrade, uninstall, screen-reader, offline or Linux player-controlled gameplay certification is claimed. Windows + Codex local candidate gameplay is recorded [separately](simple-mode-final-package-2026-10-22.md). [Release notes](../releases/v0.1.16-linux.md) and [current downloads](../releases/current.md) distinguish preparation from publication.
