# Copperbench 0.1.5 integration and publication

The owner requested combining the first-success improvements with the completed bug fixes and offline installer, committing and pushing the integration, and publishing new installer packages on 2026-10-14. This record separates source verification, previously accepted runtime behavior, immutable candidate identity and publication.

## Source scope

- [PR #106](https://github.com/Lotulune/Copperbench/pull/106), merged as `ccee11b8358aeee8da25e28741cefb3eb98c7048`, includes delayed-refresh draft protection, numeric input preservation, POSIX receipt permissions, the complete bilingual offline installer, creator defaults and texture diagnostics, block lighting fields, and Fabric 1.21.1 spawn-egg/shearing/boat repairs.
- The first-success integration adds structured input preflight, the first-creation tutorial, dependency-lock fixes, consistent generator descriptions and a consolidated status entry. Its integration verification is recorded in [first-success-2026-10-14.md](first-success-2026-10-14.md).
- Product configuration, UI identity/About, package version and the release manifest fixture are synchronized to 0.1.5. [Previous release declarations](../../evidence/maintenance/2026-10-14/publication-0.1.5/previous-release-state.json) retain their original source and publication evidence. Historical snapshot, Beta candidate provenance and installed-product gates are unchanged.

## Existing evidence and its scope

[PR #106 CI](https://github.com/Lotulune/Copperbench/actions/runs/38546700428) passed Java/Javadoc (1,299 passed, 77 skipped), all 350 Chromium cases and MCP conformance. [M1](https://github.com/Lotulune/Copperbench/actions/runs/38546700677) passed protocol contracts and real Fabric delivery. Its [Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/38546700609) passed packaging, graphical installer, JCEF and both Loader render checks. These receipts identify the earlier integration, not the final 0.1.5 package.

Windows Fabric 1.21.1 development-client acceptance on `765dd28c` passed actual light-block placement, opacity-zero skylight, custom shearing, custom spawn eggs and ordinary boat placement/rendering. Saving, exiting and reopening the same isolated world retained blocks, entities and inventory; both client lifecycle tasks succeeded and the SDK session closed. The runtime source matched PR #106's integration. All four boat variants also compiled into a real SDK-produced 11-element JAR; the other three variants were not individually exercised in the client. Local hashes, screenshots and receipts are under the creator worktree's `output/minecraft-validation/summary.json`.

The [installer VM record](installer-joint-vm-2026-10-13.md) retains its original Windows r14/Linux r12 package identities, staged installation/upgrade/uninstallation checks and data-preservation scope. It is not a complete replay on the final 0.1.5 binaries.

## Publication procedure and current state

Publication is being prepared. Current public downloads remain listed in [current.md](../releases/current.md) until the new assets are verified and public.

Windows uses the protected workflow, a signed `v0.1.5` tag on the latest main commit, clean-source verification, packaging, SBOM, SHA-256 manifests, provenance and draft-asset checks. Linux uses the unchanged frozen candidate and its separately signed promotion tag. Candidate identity, exact hashes, completed CI and publication receipts will be recorded here when available.

The host currently has no registered Hyper-V VM and the earlier `D:/Hyper-V/UX-Joint-20261013` guest directory is absent. No fresh full clean-Linux installed acceptance is claimed. The prior 0.1.3/0.1.4 candidate-specific maintenance exceptions are not reused for 0.1.5.
