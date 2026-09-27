# Copperbench 0.1.2 release status

**Windows and Linux 0.1.2 are public stable releases.** [Windows downloads](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.2) were published at `2026-09-27T22:10:48Z`; [Linux downloads](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.2-linux-stable) at `2026-09-27T21:47:22Z` (both September 28 in Asia/Tokyo).

The user authorized committing, pushing and publishing the product maintenance changes, explicitly excluding the local Minecraft-control bridge. Both releases waited for fresh Linux 0.1.2 installed acceptance. Public receipts and digest checks are retained in [publication evidence](../../evidence/maintenance/2026-09-28/publication-0.1.2/verification.json).

## Implementation baseline

- Maintenance source: `f5ca9fc6`; development checks and limitations are retained in [the maintenance record](maintenance-2026-09-27.md).
- Version identities are advanced together in the product configuration, UI, MCP client identity and canonical release fixture.
- Existing bridge changes, local MCP configuration and the associated AGENTS.md changes remain outside the product commit.
- Historical 0.1.1 tags, binaries, authorization and acceptance records remain unchanged.

## Publication gates

| Gate | Current state |
| --- | --- |
| Required implementation PR and merged-main CI | Passed: PR #83; main `9bc6d104`; run `36321809512` |
| New Linux candidate and provenance | Passed: run `36321809542`; all six frozen assets verified |
| Fresh digest-bound Linux installed acceptance | Passed: ten gates and both preference migrations; [immutable installed report](maintenance-012-linux-installed-2026-09-28.md) |
| Release-readiness declaration PR and latest-main CI | Passed: [PR #84](https://github.com/Lotulune/Copperbench/pull/84); main `410e11b9`; [run 36351384541](https://github.com/Lotulune/Copperbench/actions/runs/36351384541) |
| Signed latest-main Windows/Linux tags | Passed: both annotated SSH-signed tags point to `410e11b9bc2d3eff477215bbd0040f00a19abc43` |
| Exact 0.1.2 draft notes created and read back before production approval | Passed: both draft and public bodies exactly match [Windows notes](../releases/v0.1.2-windows.md) and [Linux notes](../releases/v0.1.2-linux.md) |
| Protected packaging/promotion and publication | Passed: [Windows run 36352304576](https://github.com/Lotulune/Copperbench/actions/runs/36352304576), [Linux run 36352554854](https://github.com/Lotulune/Copperbench/actions/runs/36352554854); both non-draft, non-prerelease |
| Full nightly regression | Passed on implementation main `9bc6d104`: [run 36345702831](https://github.com/Lotulune/Copperbench/actions/runs/36345702831), including full Chromium and all eight generator golden jobs |

Public Windows/Linux 0.1.2 are the current downloadable baseline. Historical 0.1.1 releases and their evidence remain unchanged.

The accepted 0.1.2 Linux binaries are bound to source `9bc6d104688c24512468880abdc2a96828fcaab8` from [PR #83](https://github.com/Lotulune/Copperbench/pull/83). Their original metadata remains unchanged. The signed release commit `410e11b9bc2d3eff477215bbd0040f00a19abc43` adds only allowed documentation, evidence and release-control declarations. Windows binaries were built from this release commit; Linux promotes the exact accepted candidate bytes without rebuilding.

## Delivered changes and verification

- Batch one preserves the modeling task panel, inputs and active task across same-workspace asset refresh, failure and empty states, while resetting on workspace change. Clipboard feedback now follows actual write success and remains bound to the selected asset. Current README, quickstart and user guidance reflect the implemented capabilities and published version.
- Batch two extracts internal `AssetProjectionService` for asset reads, references, health and diagnostics. The existing facade retains authorization, revisions, transactions, recovery, errors and events; public API, MCP/SDK contracts and storage formats are unchanged.
- Development acceptance passed 74 targeted Playwright cases, 196 full Chromium cases, 29 UI-Core tests, Java/Javadoc (928 passed and 59 conditional skips), MCP conformance (six scenarios/eight checks), and the real native JCEF import → bind → refresh → reopen flow. Details remain in [the maintenance record](maintenance-2026-09-27.md).
- The frozen Linux candidate passed ten installed gates and two preference-migration modes on Ubuntu 24.04 GNOME Wayland/Xorg, including Fabric/NeoForge 1.21.1 client lifecycle, Agent loops, real Blockbench operations, UI persistence and portable build. All evidence remains bound by [the immutable installed report](maintenance-012-linux-installed-2026-09-28.md) and the promotion authorization.
- Windows public uploaded-asset digests match the provenance-verified `SHA256SUMS.txt`; the downloaded metadata and checksum file pass GitHub attestation verification for release commit `410e11b9` and the Windows release workflow. The protected workflow separately compares uploaded digests to built payload hashes. This is not a new local download of all large Windows binaries.
- Linux public asset digests and sizes match all six provenance-verified candidate files plus the committed promotion authorization. The protected promotion workflow also downloads its draft assets and byte-compares them with the frozen payload.

The [current Linux support record](../../release-control/linux-platform-support.json) classifies the published 0.1.2 bytes. Frozen candidate metadata and uploaded authorization retain their historical pre-publication support flags. The previous product-status snapshot, prior failed nightly result and 0.1.1 support/authorization records remain retrievable.

## Verification limits

No external-user trial or Agent efficiency comparison was performed. Linux installed replay covers Ubuntu 24.04 x86_64 GNOME and Fabric/NeoForge 1.21.1 with warm caches; it does not certify other distributions, architectures, gameplay, cold-cache, default-network or offline behavior. Original Wayland was restored after testing, and no world was entered. Windows native JCEF maintenance replay passed, but a new clean-installed Windows 0.1.2 RC replay and physical accessibility audit are not claimed. Windows packages remain unsigned by Authenticode.

## Versioned release-note operation

The existing Windows workflow's automatic draft fallback reads `docs/releases/v0.1.1.md`; the Linux fallback reads the older general Linux notes. Those fallbacks must not create the 0.1.2 public text. Both workflows explicitly reuse an existing draft, including its body. This release therefore uses the following required operation without changing CI or historical release notes:

1. After protected PR/main checks pass, create and verify the two signed latest-main tags.
2. Before approving either production job, create the Windows and Linux drafts with their tracked version-specific bodies linked above. Use `--verify-tag --draft --prerelease=false` and the corresponding `--notes-file`; do not create or replace a tag through the release API.
3. Read both drafts back from GitHub. Require the expected tag, draft state and exact body equality with the tracked files. Preserve these receipts. A mismatch blocks production approval until corrected while still a draft.
4. Approve the existing protected workflows. Their draft-reuse branches preserve these bodies while validating/uploading the payload. Verify both public bodies again with final asset and workflow receipts.

For these tags, pre-created/read-back drafts are a publication requirement, not an optional manual note. A later release must choose its own correct notes or separately authorize a CI change before relying on the automatic fallback.
