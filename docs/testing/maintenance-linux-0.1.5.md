# Linux 0.1.5 exact-candidate publication

The current owner request is to combine the first-success improvements with the completed fixes, commit/push them and publish a new installer version. It follows the handoff that explicitly stated clean-Linux installed acceptance was still pending. This maintenance publication retains that disclosed scope: successful candidate CI, exact binary/source verification and no new formal-support claim.

## Frozen candidate

- Product source: `7c9bc2160efe3c1bf89522c0214558a42f51969d`, the merged [PR #107](https://github.com/Lotulune/Copperbench/pull/107) integration, [PR #109](https://github.com/Lotulune/Copperbench/pull/109) release fixes and [PR #110](https://github.com/Lotulune/Copperbench/pull/110) native readiness test, and the rebuilt Windows `v0.1.5` tag.
- Successful main-push candidate run: [38568121386](https://github.com/Lotulune/Copperbench/actions/runs/38568121386).
- Candidate ID: `sha256:ea18be795a6266fa5934e75722983ccabf93e62a634c3629494b96768f4e1fac`.
- Debian SHA-256: `1af8ffbd49361ef2815053c1d89c4b6b82cbd318a1aa13b9c6934a52b8bda2c4`.
- Portable SHA-256: `60eb139a5c48e101228378edc0e1d23ea882bc7d33aaa06572a4995839dc07b5`.
- Graphical installer SHA-256: `3f9b7573511d46af9f2828aee58fa8d8154d1444180340d4f67c6c7ce591e0d7`.
- Candidate metadata SHA-256: `2dd3d4bca262f2fbff127cb992d5775e446f8ee7b16ab1d32ff8a0d46ed54fbe`.

The [run receipt](../../evidence/maintenance/2026-10-14/linux-015/candidate-run.json), [jobs receipt](../../evidence/maintenance/2026-10-14/linux-015/candidate-jobs.json) and [hash log excerpt](../../evidence/maintenance/2026-10-14/linux-015/candidate-hashes.txt) bind these values to the completed CI. All thirteen required maintenance steps passed: platform/generator regressions, installed-gate contracts, package build/layout/modes, isolated bootstrap, JCEF, both Loader render preflights, the bilingual graphical installer check, metadata, provenance and artifact upload.

Hashes above were extracted from the completed build log. Independent downloaded-byte and provenance verification is recorded separately before promotion; it is not inferred from the log. The protected promotion also verifies every frozen asset and compares all draft downloads byte-for-byte before making the release public.

## Authorization and acceptance scope

The current user request was “和这个一起提交推送并发布新版本安装包”. The preceding handoff stated “尚未发布新安装包；Linux 干净安装正式验收仍待完成。” Publication is authorized in that known maintenance scope. This record does not turn the pending installed gate into a pass.

There are no registered Hyper-V VMs on the current host, and the earlier `D:/Hyper-V/UX-Joint-20261013` guest directory is absent. Full GNOME Wayland/Xorg installation, desktop Blockbench round trips, UI persistence, preference migration and full gameplay acceptance were not repeated on these 0.1.5 binaries. The [earlier installer VM results](installer-joint-vm-2026-10-13.md) and prior game observations retain their original source and artifact identities.

`maintenance-ci-0.1.5` permits only `v0.1.5-linux-stable`, source `7c9bc2160efe3c1bf89522c0214558a42f51969d` and run `38568121386`. It requires `formalSupportClaim: false`, `fullInstalledAcceptance: not-repeated`, the thirteen passed CI steps and exact candidate/package hashes. It rejects a different source/run/tag, skipped graphical installer checks, substituted binaries or reused historical installed evidence. This is not authorization for another future candidate.

The [previous 0.1.4 authorization](../../evidence/maintenance/2026-10-14/linux-015/previous-014-authorization.json) is archived unchanged. Signatures, normal production review, latest-main promotion tags, permitted source delta, provenance and draft comparison remain required. This report is immutable input to the authorization; publication outcomes are recorded in the separate [0.1.5 release record](maintenance-release-0.1.5.md).
