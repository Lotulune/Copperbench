# Linux 0.1.9 exact-candidate maintenance publication

The user requested review, fixes and a new release package, followed by cloud acceptance. Cloud acceptance and the Windows release gate exposed the three defects addressed by 0.1.9; no 0.1.8 binaries were published. This authorization covers only the exact 0.1.9 main-push candidate verified below. Full installed acceptance remains `not-repeated`; `formalSupportClaim` remains `false`.

## Source and CI

- Product/binary source A: `9e8fcda24e58d165cb2b767f3a0cb3092a7f527c`, merged through [PR #121](https://github.com/Lotulune/Copperbench/pull/121).
- [Main CI](https://github.com/Lotulune/Copperbench/actions/runs/38894781423) passed.
- [Main-push candidate 38894781422](https://github.com/Lotulune/Copperbench/actions/runs/38894781422), attempt 1, completed successfully on A.
- The [run receipt](../../evidence/maintenance/2026-10-16/linux-019/candidate-run.json) and [jobs receipt](../../evidence/maintenance/2026-10-16/linux-019/candidate-jobs.json) retain the exact source, event, branch and step results.

All thirteen maintenance-required steps passed: Linux platform/generator and candidate/installed-gate contracts; portable/deb packaging and layouts; packaged headless bootstrap; JCEF/X11 shell; bilingual graphical-installer rendering; NeoForge and Fabric 1.21.1 X11 render preflights; metadata freeze; provenance; and artifact upload. The separate opt-in NeoForge 26.3 dependency-graph job was skipped, not newly qualified.

## Frozen bytes and provenance

Candidate ID: `sha256:f6c6e5167dc044f7f3ab17e5e6648b5aa0dcc163f960cc08e109dfdc5beb792d`. The [unchanged metadata](../../evidence/maintenance/2026-10-16/linux-019/LINUX-CANDIDATE-METADATA.json) remains `development-not-certified`, `formalSupportClaim=false`, `exactBinaryPromotionEligible=false`.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.9 Linux x86_64 Setup.tar.gz` | 1112306976 | `7f611ae643f4e29a7519c64eba4c8398b350e7e9f161837f14d4929fd1f11de4` |
| `Copperbench 0.1.9 Linux x86_64 Setup.tar.gz.build.json` | 311 | `40701797ab5aedec8f01049c0cefe7c7c470e9e8fbf0222a49cbf4a8d1004540` |
| `Copperbench 0.1.9 Linux x86_64.tar.gz` | 1110301981 | `f44e1e4f1faf25f2006c06bd4b687a5167309aa248c69d1671d2dcb0a0e287b2` |
| `LINUX-CANDIDATE-METADATA.json` | 1991 | `ae4d132f52a14c88e56349c0e3ae83ed5572bf4cb96649c1cb73c31f0bcb1158` |
| `copperbench-linux.spdx.json` | 5670307 | `996b7441e674c5aa10eb625b4baa6d90d588686e42f007011eeb685b0b1e60e5` |
| `copperbench_0.1.9_amd64.deb` | 1027350004 | `f0930342fa8fbfd5a998f5c639991efd6296b3a9134a75b11354abe9a0bca8aa` |
| `linux-candidate-manifest.json` | 6694 | `75dbf2c918f2ae19a00723b7dc37606701e4fe9fa64bb7373f0d31170f1f6e3e` |
| `linux-candidate-sha256.txt` | 806 | `f6926eb4e8e8f2b4117442587f31843c881fc4706b9e4a6fcb09310344cdb68c` |

All eight files were independently downloaded and hashed by [cloud consumer run 38898348329](https://github.com/Lotulune/Copperbench/actions/runs/38898348329), then matched the verified SLSA statement. The consumer also checked the packaged fix classes and stock wrapper against source A. Its [run receipt](../../evidence/maintenance/2026-10-16/linux-019/candidate-consumer-run.json) and [result](../../evidence/maintenance/2026-10-16/linux-019/candidate-consumer-result.json) bind that verification to its harness commit. The small downloaded evidence bundle was hash-checked locally; this report does not claim a second local download of all package bytes. GitHub's attestation verifier validated the metadata subject with the exact repository, source digest, `refs/heads/main` and candidate workflow; the same signed statement covers every file listed above. Its invocation is run 38894781422, attempt 1. [Verification summary](../../evidence/maintenance/2026-10-16/linux-019/candidate-provenance-verification.json) and [raw verified bundle](../../evidence/maintenance/2026-10-16/linux-019/verified-attestation-bundle.json) retain those bindings. The separate metadata verifier checked the declared sizes, role hashes and candidate identity.

The [portable archive inspection](../../evidence/maintenance/2026-10-16/linux-019/archive-entry-verification.json) matched the three 0.1.9 release-document entries to Git A, confirmed application `product.version=0.1.9`, and recorded both fix-class hashes and matched the updated 1.20.1 data-pack template to source A. This is a package/version check, not an installed-product test.

## Acceptance and publication boundaries

These cloud-discovered export and legacy-runtime fixes have the affected local checks and full source CI recorded in [the 0.1.9 review](maintenance-release-0.1.9.md). No new full GNOME installation/upgrade/uninstall, player-controlled gameplay or same-world reopening acceptance was performed for these final binaries. Historical installed or gameplay evidence is not reused as fresh acceptance.

`maintenance-ci-0.1.9` permits only `v0.1.9-linux-stable`, A and main run 38894781422. It binds the candidate identity, package hashes, authentic successful CI receipts and this immutable report hash. It rejects a substituted source/run/tag, missing approval, an installed-support claim or a skipped graphical-installer check. The [previous authorization](../../evidence/maintenance/2026-10-16/linux-019/previous-017-authorization.json) is preserved.

Signed latest-main promotion, the existing source-delta allowlist, production review, and downloaded draft-asset comparisons remain required. Subsequent publication outcomes are recorded separately in [the release record](maintenance-release-0.1.9.md); this candidate-bound report stays unchanged.
