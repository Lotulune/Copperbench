# Linux 0.1.5 exact-candidate publication

The current owner request is to combine the first-success improvements with the completed fixes, commit/push them and publish a new installer version. It follows the handoff that explicitly stated clean-Linux installed acceptance was still pending. This maintenance publication retains that disclosed scope: successful candidate CI, exact binary/source verification and no new formal-support claim.

## Frozen candidate

- Product source: `cb616b878da983864f7747cfa7b0ae09513ffef7`, the merged [PR #107](https://github.com/Lotulune/Copperbench/pull/107) source and Windows `v0.1.5` tag.
- Successful main-push candidate run: [38556240467](https://github.com/Lotulune/Copperbench/actions/runs/38556240467).
- Candidate ID: `sha256:30ec7ef885b3f7074efcbb831e5568ce5fc318f5a8cd83d9e0b82302df226f1d`.
- Debian SHA-256: `5d901fb88255a51e76a2731b88b086b4f1f62b98d74f3f08d8f59345fa57fb18`.
- Portable SHA-256: `29faf34ef00662a645d11c91a63f4fe943db3989bc811c1559faf35c2c991ae3`.
- Graphical installer SHA-256: `6d326d16b8efde222a932d6498d3cc8e261bcd5c6947cec3dd905457cff06d58`.
- Candidate metadata SHA-256: `3a33fb9ba40470a22d116324fa7a2bde3d06813be10da40e66fe210ac0b0c604`.

The [run receipt](../../evidence/maintenance/2026-10-14/linux-015/candidate-run.json), [jobs receipt](../../evidence/maintenance/2026-10-14/linux-015/candidate-jobs.json) and [hash log excerpt](../../evidence/maintenance/2026-10-14/linux-015/candidate-hashes.txt) bind these values to the completed CI. All thirteen required maintenance steps passed: platform/generator regressions, installed-gate contracts, package build/layout/modes, isolated bootstrap, JCEF, both Loader render preflights, the bilingual graphical installer check, metadata, provenance and artifact upload.

Hashes above were extracted from the completed build log. Independent downloaded-byte and provenance verification is recorded separately before promotion; it is not inferred from the log. The protected promotion also verifies every frozen asset and compares all draft downloads byte-for-byte before making the release public.

## Authorization and acceptance scope

The current user request was “和这个一起提交推送并发布新版本安装包”. The preceding handoff stated “尚未发布新安装包；Linux 干净安装正式验收仍待完成。” Publication is authorized in that known maintenance scope. This record does not turn the pending installed gate into a pass.

There are no registered Hyper-V VMs on the current host, and the earlier `D:/Hyper-V/UX-Joint-20261013` guest directory is absent. Full GNOME Wayland/Xorg installation, desktop Blockbench round trips, UI persistence, preference migration and full gameplay acceptance were not repeated on these 0.1.5 binaries. The [earlier installer VM results](installer-joint-vm-2026-10-13.md) and prior game observations retain their original source and artifact identities.

`maintenance-ci-0.1.5` permits only `v0.1.5-linux-stable`, source `cb616b878da983864f7747cfa7b0ae09513ffef7` and run `38556240467`. It requires `formalSupportClaim: false`, `fullInstalledAcceptance: not-repeated`, the thirteen passed CI steps and exact candidate/package hashes. It rejects a different source/run/tag, skipped graphical installer checks, substituted binaries or reused historical installed evidence. This is not authorization for another future candidate.

The [previous 0.1.4 authorization](../../evidence/maintenance/2026-10-14/linux-015/previous-014-authorization.json) is archived unchanged. Signatures, normal production review, latest-main promotion tags, permitted source delta, provenance and draft comparison remain required. This report is immutable input to the authorization; publication outcomes are recorded in the separate [0.1.5 release record](maintenance-release-0.1.5.md).
