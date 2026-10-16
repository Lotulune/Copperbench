# Linux 0.1.7 exact-candidate maintenance publication

The user requested review of recent commits, bug fixes and a new release package. This authorization covers only the exact 0.1.7 main-push candidate verified below. Full installed acceptance remains `not-repeated`; `formalSupportClaim` remains `false`.

## Source and CI

- Product/binary source A: `db8d08336c274cc37239b48e837f0bf3727c5fdc`, merged through [PR #116](https://github.com/Lotulune/Copperbench/pull/116).
- [Main CI](https://github.com/Lotulune/Copperbench/actions/runs/38850069890) passed.
- [Main-push candidate 38850069834](https://github.com/Lotulune/Copperbench/actions/runs/38850069834), attempt 1, completed successfully on A.
- The [run receipt](../../evidence/maintenance/2026-10-16/linux-017/candidate-run.json) and [jobs receipt](../../evidence/maintenance/2026-10-16/linux-017/candidate-jobs.json) retain the exact source, event, branch and step results.

All thirteen maintenance-required steps passed: Linux platform/generator and candidate/installed-gate contracts; portable/deb packaging and layouts; packaged headless bootstrap; JCEF/X11 shell; bilingual graphical-installer rendering; NeoForge and Fabric 1.21.1 X11 render preflights; metadata freeze; provenance; and artifact upload. The separate opt-in NeoForge 26.3 dependency-graph job was skipped, not newly qualified.

## Frozen bytes and provenance

Candidate ID: `sha256:fae758f09d295e48290761e452e5d22d0dcb4abe878f18577ef4e081d7d2c847`. The [unchanged metadata](../../evidence/maintenance/2026-10-16/linux-017/LINUX-CANDIDATE-METADATA.json) remains `development-not-certified`, `formalSupportClaim=false`, `exactBinaryPromotionEligible=false`.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.7 Linux x86_64 Setup.tar.gz` | 1112289948 | `685cd8c91c2508c1ce95a1d75380a3cca2f7ba0086ade05a33a6c6d51f11180b` |
| `Copperbench 0.1.7 Linux x86_64 Setup.tar.gz.build.json` | 311 | `a9f70a2a030b85fab2436e4fcdfcb3bbcaf3866f315e649eb6634899f4a7dfe3` |
| `Copperbench 0.1.7 Linux x86_64.tar.gz` | 1110295372 | `fdaa48f6a33f5387380fe8070c68a5a8639d1335aba3113ac26f18e2432a0598` |
| `copperbench-linux.spdx.json` | 5670307 | `60d58733dccf5ac8a69aaa22d2623119df2650991317fc89a865962f18d91d48` |
| `copperbench_0.1.7_amd64.deb` | 1027397234 | `7c632878bf8537638466435e79de8254230dbb941b81e0214c59e634f72d44a1` |
| `linux-candidate-manifest.json` | 6694 | `f0ff3b8dba5b1d785c1f434d85360195441dcac241e3f3f199f58ed20b49c4b6` |
| `LINUX-CANDIDATE-METADATA.json` | 1991 | `3b7f146d8e91a6c521d9cfc9ff83c84d5f42a73ad9cb563b7808775027309aa6` |
| `linux-candidate-sha256.txt` | 806 | `352dd0cac6a39215f2e1ee20f050d1dc61750ea3cc745303b4a9f18423fd8979` |

All eight downloaded files were hashed and matched the verified SLSA statement. GitHub's attestation verifier validated the metadata subject with the exact repository, source digest, `refs/heads/main` and candidate workflow; the same signed statement covers every file listed above. Its invocation is run 38850069834, attempt 1. [Verification summary](../../evidence/maintenance/2026-10-16/linux-017/candidate-provenance-verification.json) and [raw verified bundle](../../evidence/maintenance/2026-10-16/linux-017/verified-attestation-bundle.json) retain those bindings. The separate metadata verifier checked the declared sizes, role hashes and candidate identity.

The [portable archive inspection](../../evidence/maintenance/2026-10-16/linux-017/archive-entry-verification.json) matched the three 0.1.7 release-document entries to Git A, confirmed application `product.version=0.1.7`, and recorded the packaged task-gateway class hash. This is a package/version check, not an installed-product test.

## Acceptance and publication boundaries

This export-only maintenance patch has the affected local checks and full source CI recorded in [the 0.1.7 review](maintenance-release-0.1.7.md). No new full GNOME installation/upgrade/uninstall, player-controlled gameplay or same-world reopening acceptance was performed for these final binaries. Historical installed or gameplay evidence is not reused as fresh acceptance.

`maintenance-ci-0.1.7` permits only `v0.1.7-linux-stable`, A and main run 38850069834. It binds the candidate identity, package hashes, authentic successful CI receipts and this immutable report hash. It rejects a substituted source/run/tag, missing approval, an installed-support claim or a skipped graphical-installer check. The [previous authorization](../../evidence/maintenance/2026-10-16/linux-017/previous-016-authorization.json) is preserved.

Signed latest-main promotion, the existing source-delta allowlist, production review, and downloaded draft-asset comparisons remain required. Subsequent publication outcomes are recorded separately in [the release record](maintenance-release-0.1.7.md); this candidate-bound report stays unchanged.
