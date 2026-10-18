# Windows 0.1.12 release — 2026-10-19

Windows [v0.1.12](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.12) was published as a public stable release at **2026-10-18T21:15:41Z** and confirmed as GitHub's latest release. [PR #129](https://github.com/Lotulune/Copperbench/pull/129) removes the update dialog's fixed five-row status area and tightens its heading, message, progress and action spacing.

The [native presentation record](compact-update-dialog-2026-10-19.md) contains the source-bound Windows previews and local checks. Short messages use their actual height; longer text wraps and remains scrollable. The update/download/install control flow is unchanged.

## Publication

| Check | Result / evidence |
| --- | --- |
| Signed source tag | `v0.1.12`, annotated tag object `3faf6e5f1fafb9e8a6340989381dd3c4af04e929` |
| Binary source commit | [`2508bc2eb4afa2f537ab68920aae1ea95b6df70c`](https://github.com/Lotulune/Copperbench/commit/2508bc2eb4afa2f537ab68920aae1ea95b6df70c) |
| Final PR regression | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39163088318), including 431 Chromium cases |
| M1 protocol and actual delivery chain | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39163088343) |
| PR Linux compatibility candidate | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39163088302) |
| Main regression | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39164585513) |
| Main documentation | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39164585429) |
| Main Linux compatibility candidate | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39164585425); no new Linux release |
| Official Windows release workflow | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39164641587), including full Windows tests, package generation, actual EXE branding, SBOM/provenance and uploaded-asset verification |
| Public payload | 15 uploaded files, including 4 update components and their descriptor |

The published installer is **`Copperbench.0.1.12.Windows.64bit.exe`**, **503,043,619 bytes**, SHA-256 **`dd6d50174353c100371a59d7929896c2b46c4953283ea59d5fc93d10db7b221f`**.

After publication, the downloaded [hash file](https://github.com/Lotulune/Copperbench/releases/download/v0.1.12/SHA256SUMS.txt), [source metadata](https://github.com/Lotulune/Copperbench/releases/download/v0.1.12/RELEASE-METADATA.json) and [updater descriptor](https://github.com/Lotulune/Copperbench/releases/download/v0.1.12/copperbench-update-windows-x64.properties) matched their GitHub upload digests. The hash file covers every other asset, all 15 asset digests agree, and the four component sizes and hashes match the descriptor. Source metadata identifies the signed commit and release workflow above.

This public readback did not download or execute the full installer; the protected workflow verified uploaded assets against its built payload before publication. Real installation, upgrade, uninstallation and player-world acceptance were not repeated for this spacing correction. The new layout is active after installing 0.1.12 and restarting; an older running application continues to render its own update dialog while preparing the upgrade.

The tagged `product-status.json` retains its pre-publication build declaration. This report and the [current download index](../releases/current.md) record the public delivery without changing released bytes. Linux remains at 0.1.9, and the [0.1.11](maintenance-release-0.1.11.md) and [0.1.9](maintenance-release-0.1.9.md) records retain their original acceptance scope.
