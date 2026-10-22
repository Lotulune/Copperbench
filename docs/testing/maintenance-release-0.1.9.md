# 0.1.9 Windows path-alias export correction

The 0.1.8 Windows release gate stopped before building or publishing packages:
[run 38886381793](https://github.com/Lotulune/Copperbench/actions/runs/38886381793)
had 1,445 tests, one failure and 64 skips. The real Gradle archive test
failed the workspace containment check. Windows supplied the temporary root
through an 8.3 alias, while Gradle's receipt used its canonical long path.
The [local diagnostic](../../evidence/maintenance/2026-10-16/export-path-019/short-path-reproduction.json)
reproduces that mismatch using the same canonicalization as the Gradle script;
it is an explicitly simulated receipt probe, not a gameplay test.

Archive validation now compares physical root/artifact paths after rejecting
links, then returns the artifact using the caller's workspace path spelling.
The existing delivery boundary therefore sees a consistent path. Files outside
the workspace build tree, symbolic links and changed archive bytes remain
rejected. Real Gradle regressions cover both ordinary and forced Windows short
paths, repeated exports and changed payload bytes; boundary regressions cover
outside and linked archives.

The configuration-cache and legacy data-pack fixes from [0.1.8](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/maintenance-release-0.1.8.md)
are retained. Its signed source tag and failed Windows run remain unchanged;
the independently verified Linux 0.1.8 candidate was not promoted. The corrective 0.1.9 packages are now public on both platforms.

Focused verification passed: 15 Java checks with no skips/failures, including both real-Gradle path variants and outside/linked archive rejection; 50 UI-Core contracts; three release-history checks; product status, Markdown links and the JAR/UI build. The [validation receipt](../../evidence/maintenance/2026-10-16/export-path-019/local-validation.json) binds results and source hashes. The [same short-path diagnostic after the fix](../../evidence/maintenance/2026-10-16/export-path-019/fixed-short-path-probe.json) now resolves the artifact using the caller's path spelling.

## Verified publication and final package acceptance

- Source A: `9e8fcda24e58d165cb2b767f3a0cb3092a7f527c`, merged through [PR #121](https://github.com/Lotulune/Copperbench/pull/121). [Exact-main CI](https://github.com/Lotulune/Copperbench/actions/runs/38894781423), [documentation](https://github.com/Lotulune/Copperbench/actions/runs/38894781518) and [Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/38894781422) passed. The [full Windows pre-tag test](https://github.com/Lotulune/Copperbench/actions/runs/38892934564) passed on the identical source tree; both real-Gradle path cases and all 13 delivery-boundary cases executed without skips.
- Windows [v0.1.9](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.9) was published at `2026-10-16T12:35:43Z` by [run 38897217090](https://github.com/Lotulune/Copperbench/actions/runs/38897217090). An [independent cloud consumer](https://github.com/Lotulune/Copperbench/actions/runs/38901865344) downloaded all ten public files, matched API/checksum/provenance identities and inspected package docs, version, fix classes and the data-pack template. [Public verification](../../evidence/maintenance/2026-10-16/publication-0.1.9/windows/windows-public-verification.json).
- Linux [v0.1.9-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.9-linux-stable) was published at `2026-10-16T12:49:41Z` by [run 38902713886](https://github.com/Lotulune/Copperbench/actions/runs/38902713886), using promotion source B `474b2df4c52ecf52e1f3724448c2d12c8af85396` from [PR #122](https://github.com/Lotulune/Copperbench/pull/122). Binary source stays A. The protected promotion reverified the eight frozen files and byte-compared all nine downloaded draft assets. Public API digests match the frozen bytes and signed authorization; three public metadata files were also independently downloaded and byte-compared. [Public verification](../../evidence/maintenance/2026-10-16/publication-0.1.9/linux/linux-public-verification.json) and [immutable candidate report](maintenance-linux-0.1.9.md).
- [Final public-package run 38903643192](https://github.com/Lotulune/Copperbench/actions/runs/38903643192) passed all three lanes: Windows Setup, Ubuntu Setup and DEB. It covers fresh installation, 0.1.7→0.1.9 upgrade, uninstall/data preservation, cold legacy-wrapper migration, Fabric build/fail/repair and five GameTests per Setup platform, exact/reopened/custom exports and three data-pack ZIP tracks. All 16 Native hosts exited normally. [Acceptance scope and receipts](cloud-acceptance-0.1.9.md).

These automated results do not add Windows 11/GNOME desktop or player-world save/reopen certification. Historical gates and the candidate-bound Linux report retain their original scope. Bundled documentation keeps source A's build-time text; subsequent repository records do not alter public package bytes.
