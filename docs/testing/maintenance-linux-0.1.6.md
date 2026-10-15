# Linux 0.1.6 exact-candidate maintenance publication

The user's request was “推送并发布”. The authorized scope is this verified main-push candidate, its exact bytes and source, protected promotion and public asset verification. Full installed acceptance remains `not-repeated`; `formalSupportClaim` remains `false`. Linux publication is pending.

## Exact source and successful CI

- Product/binary source A: `9e3dfdeab1bc19c099e739bcda14940d9a6fee30`.
- [Main-push candidate 38748273030](https://github.com/Lotulune/Copperbench/actions/runs/38748273030), branch `main`, event `push`, attempt `1`: `completed/success`, updated `2026-10-15T14:25:47Z`.
- [Package job 116467570918](https://github.com/Lotulune/Copperbench/actions/runs/38748273030/job/116467570918): `completed/success`, `2026-10-15T14:05:20Z` to `2026-10-15T14:25:46Z`.
- [Raw run receipt](../../evidence/maintenance/2026-10-15/linux-016/candidate-run.json): SHA-256 `4025ce2e28a292e82f0575103492f2e83d5e231b7446d3609586b9cc1b587feb`.
- [Raw jobs receipt](../../evidence/maintenance/2026-10-15/linux-016/candidate-jobs.json): SHA-256 `fe3b7bae488f4b136ffe5a4ce2eda8e3febe11ffa1ca21981221b6510fa09895`.

All thirteen maintenance-required steps completed successfully:

| CI step | Actual step name | Conclusion | Completed UTC |
| --- | --- | --- | --- |
| 8 | Run Linux platform and generator regressions | success | 2026-10-15T14:06:45Z |
| 9 | Run Linux candidate and installed-gate contract tests | success | 2026-10-15T14:06:53Z |
| 10 | Build portable and deb candidates | success | 2026-10-15T14:13:05Z |
| 14 | Verify portable candidate contents and executable modes | success | 2026-10-15T14:13:16Z |
| 15 | Launch packaged headless bootstrap without system Java Gradle or Git | success | 2026-10-15T14:13:22Z |
| 18 | Launch packaged JCEF product shell under Xvfb | success | 2026-10-15T14:14:43Z |
| 19 | Verify graphical installer package and render its bilingual wizard | success | 2026-10-15T14:14:56Z |
| 22 | Run packaged NeoForge 1.21.1 X11 render preflight under Xvfb | success | 2026-10-15T14:20:09Z |
| 24 | Run packaged Fabric 1.21.1 X11 render preflight under Xvfb | success | 2026-10-15T14:23:25Z |
| 27 | Verify deb candidate layout | success | 2026-10-15T14:23:30Z |
| 29 | Freeze Linux candidate metadata | success | 2026-10-15T14:23:52Z |
| 31 | Attest Linux candidate provenance | success | 2026-10-15T14:24:02Z |
| 32 | Upload Linux candidate artifacts | success | 2026-10-15T14:25:39Z |

The separate NeoForge 26.3 graph job and selected 26.3 installed-acceptance steps were skipped for this ordinary candidate. They are not new 26.3 qualification. JCEF/X11 render checks and bilingual wizard preview are automated CI observations, separate from an operator-driven GNOME installation or player gameplay session.

## Downloaded immutable candidate

Candidate ID: `sha256:ea64a9f7126e7cab62343780704837b62ffbd93010ae2603d0d2992444c508d1`. The downloaded artifact ZIP for artifact `11982220420` has SHA-256 `93065ad4c482733ab21e00d5c621070488e8870da3fb45d4b71372c41a199d11`, matching the GitHub API artifact digest. The original [artifact receipt](../../evidence/maintenance/2026-10-15/linux-016/candidate-artifacts.json), [candidate metadata](../../evidence/maintenance/2026-10-15/linux-016/LINUX-CANDIDATE-METADATA.json) and [checksum list](../../evidence/maintenance/2026-10-15/linux-016/candidate-hashes.txt) retain their original bytes.

| Frozen file | Bytes | SHA-256 |
| --- | --- | --- |
| `Copperbench 0.1.6 Linux x86_64 Setup.tar.gz` | 1112286754 | `428db541820dd3d5c638f4d496b31b1630961a21da96b8e2462952ee1a880f67` |
| `Copperbench 0.1.6 Linux x86_64 Setup.tar.gz.build.json` | 311 | `ca1ce92ae3bcadbdcc8f2e14960950525cb2e8711ed614b9cde2e9589fe7cd32` |
| `Copperbench 0.1.6 Linux x86_64.tar.gz` | 1110296788 | `bc90f7a83a8187eca7093e962fef526701867cfae650af471cb8bbd359ba7871` |
| `LINUX-CANDIDATE-METADATA.json` | 1991 | `9c7461ab3152285c947959471fa9d65452ce6bd44db77d9597c59ea2aefa2fd1` |
| `copperbench-linux.spdx.json` | 5670307 | `a6b9ba2113437b0e00dd2d2a307272fce7ff564495156245f268221708ae3265` |
| `copperbench_0.1.6_amd64.deb` | 1027441440 | `b7b9a0a0d8e298f2f570a21b5a391e23a57bf9ec97b3fb8074c709e69d8353fd` |
| `linux-candidate-manifest.json` | 6694 | `9a742b63bd3077a473da680eb2e7db1bd81214dcb43984010f73c1a54cc5a52d` |
| `linux-candidate-sha256.txt` | 806 | `1b3e99506d1684edbf467234f8ac538e61f09722f8cbac749ef6b21e17d0b818` |

The [download and provenance verification](../../evidence/maintenance/2026-10-15/linux-016/candidate-download-verification.json) passed at `2026-10-15T15:35:32.815030+00:00`: all eight files match their downloaded byte hashes, immutable metadata identity and successful GitHub attestation results. Certificate/statement checks bind source A, `refs/heads/main`, `.github/workflows/stage15-linux-candidate.yml`, run `38748273030`, attempt `1` and each subject digest. One unchanged [raw verified attestation bundle](../../evidence/maintenance/2026-10-15/linux-016/candidate-attestation-bundle.json), SHA-256 `b00f24f5ec18ae7b70780979db251302817e081e5245a7f878f66395233ff0ef`, covers all eight subjects.

Two initial local Sigstore-verifier initialization attempts failed for the portable archive and setup build sidecar; their successful isolated retries verify the same frozen bytes. The [first results](../../evidence/maintenance/2026-10-15/linux-016/attestation-first-results.json), [retry results](../../evidence/maintenance/2026-10-15/linux-016/attestation-retry-results.json) and [portable](../../evidence/maintenance/2026-10-15/linux-016/attestation-first-portable.stderr.log) / [setup-sidecar](../../evidence/maintenance/2026-10-15/linux-016/attestation-first-setup-build.stderr.log) errors retain the original failure scope.

The [portable archive inspection](../../evidence/maintenance/2026-10-15/linux-016/archive-entry-verification.json) found each of 23 selected documentation/SDK/schema entries exactly once and matched its size and SHA-256 to A. The [expectation list](../../evidence/maintenance/2026-10-15/linux-016/package-entry-expectations.json), SHA-256 `ca36bae5d74c86d9daefe1a709bb3f3e23fdac74f0e7baba4400a6a5ce964989`, was [independently checked against Git A blobs](../../evidence/maintenance/2026-10-15/linux-016/expectations-source-check.json). This covers the thirteen added offline Markdown/readiness routes, nine existing SDK entries and editor schema 1.1. It does not constitute full installed-product acceptance.

Original metadata remains `development-not-certified`, `formalSupportClaim=false` and `exactBinaryPromotionEligible=false`; the separate release authorization does not rewrite those fields.

## Retained CI failures

Initial PR #113 source `d8b01b32380e7a1457c6b35b2b3a7d1c3671d072` failed Linux [run 38739258605](https://github.com/Lotulune/Copperbench/actions/runs/38739258605) and Windows [job 116436498331](https://github.com/Lotulune/Copperbench/actions/runs/38739258590/job/116436498331): `sdk/typescript/copperbench.ts` reached one Copy destination through both broad and narrow SDK rules. The fix removed only the broad duplicate, retaining strict duplicate failure. The later conformance artifact-upload error followed package construction stopping; it was not a successful conformance result.

The [initial PR snapshot](../../evidence/maintenance/2026-10-15/linux-016/pr-113-initial.json) remains its original in-progress observation. The [Linux failure log](../../evidence/maintenance/2026-10-15/linux-016/pr-113-linux-failed.log), SHA-256 `a3d89032a3f0982a9a3735cadb3779333de2b60c3fdc4f63aac5326646dc2574`, and [Windows failure excerpt](../../evidence/maintenance/2026-10-15/linux-016/pr-113-windows-failed-excerpt.log), SHA-256 `faaed563a26c8eed8088e14ce85f40bd92c45200b2898822ffa7ccf2331e6ab7`, retain the failed outcomes. Successful PR candidate `38744646888` has a different PR identity and is not the promotion source.

## Historical acceptance boundary

[2026-10-13 joint VM acceptance](installer-joint-vm-2026-10-13.md) records Windows r14/Linux r12 on its original `f5dd87ba6cf26abb0c53814c1db20656dfc3b4ad` baseline plus its documented fixes. Its staged installation, upgrade, uninstall and GNOME Wayland/Xorg observations remain tied to those artifacts.

[Historical optimization Linux run3](optimization-2026-10-15.md#linux无界面跟进受限通过原失败保留) is a different, strictly headless Ubuntu 24.04.4 Hyper-V candidate: archive SHA-256 `dc664d968936f8967eff0770d9a339fecbd971dbe5494e34854fd8725d853d5a`, source snapshot `20a401b9931197d55aea145909b1f26bc1580c4fdf8c1219b887b543ec84e8b0`. It used a fixed 1.5 GiB guest, stopped gdm and explicit warm-cache/task-local heap conditions. Its edit/reopen/normal-close, Fabric build/two packaged-JAR cases/ordinary and exact exports, and NeoForge build smoke keep that scope. The original SDK-close, memory, source-conflict, heap and timeout failures and final normal poweroff remain recorded.

Run3 had no Linux GUI/GNOME, player-controlled gameplay or same-world reopening acceptance. It predates the later thirteen review fixes. Neither it nor the earlier VM work establishes final 0.1.6 installed acceptance, cold/default-resource certification or a new support claim.

No new full GNOME install/upgrade/uninstall, Blockbench round trip, preference migration, UI persistence, player-controlled Minecraft or same-world reopening acceptance was performed for these final Linux binaries. Automated CI package/render observations remain the scope listed above.

## Maintenance authorization and publication context

`maintenance-ci-0.1.6` permits only `v0.1.6-linux-stable`, source A and main run `38748273030`. It binds the exact candidate ID, Debian/portable hashes, successful CI receipts and this report hash, and rejects a substituted source/run/tag or skipped graphical-installer check. Historical `acceptedEvidence` and preference-migration receipts are not reused. The [prior 0.1.5 authorization](../../evidence/maintenance/2026-10-15/linux-016/previous-015-authorization.json) is preserved byte-for-byte.

Windows [run 38748647623](https://github.com/Lotulune/Copperbench/actions/runs/38748647623) attempt 1 failed only while fetching Sigstore Rekor provenance after tests, packaging, SBOM, hashes and payload checks passed. The unchanged attempt 2 completed successfully. [Windows public release](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.6), ID `413978638`, is stable and non-draft, published at `2026-10-15T15:35:41Z`, with ten assets and source A. Independent checks matched the nine checksum-list entries to public API digests, byte-checked SHA256SUMS itself and the other two downloaded metadata files, and verified all ten source-A/attempt-2 provenance subjects. Windows ZIP-entry inspection is separate and was not required for this Linux candidate approval.

Linux publication remains pending. Signed latest-main promotion tags, protected production review, permitted A-to-promotion source delta and exact downloaded draft-asset comparison remain the publication controls. Later public outcomes are recorded separately in [maintenance-release-0.1.6.md](maintenance-release-0.1.6.md); this candidate-bound report preserves its acceptance scope.
