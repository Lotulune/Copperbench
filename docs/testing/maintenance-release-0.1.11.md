# Windows 0.1.11 release — 2026-10-19

Windows [v0.1.11](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.11) was published as a public stable release at **2026-10-18T19:34:07Z** and confirmed as GitHub's latest release. [PR #127](https://github.com/Lotulune/Copperbench/pull/127) fixes the standalone update window and shares native prompt styling across the workbench, setup wizard and update worker.

The independent updater now uses the product icon and theme, preserves the launch language/theme, and distinguishes waiting from measured copy progress. Long messages and diagnostics remain reachable; compact setup forms retain their actions. The [native-window validation record](native-dialogs-2026-10-19.md) contains actual Windows screenshots, interaction results, source hashes and the local setup JAR identity. That fixture JAR and marker-only packaging fixture are distinct from the public packages below.

## Publication

| Check | Result / evidence |
| --- | --- |
| Signed source tag | `v0.1.11`, annotated tag object `d1e1ba37f79cbfd795188f7db41f073cd26ee429` |
| Binary source commit | [`ad62a523eaddde9b0a28850aaa88b81f1a29a752`](https://github.com/Lotulune/Copperbench/commit/ad62a523eaddde9b0a28850aaa88b81f1a29a752) |
| Final PR regression | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39155671116), including 431 Chromium cases |
| M1 protocol and actual delivery chain | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39155671171) |
| PR Linux compatibility candidate | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39155671105) |
| Main regression | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39157203043) |
| Main documentation | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39157203023) |
| Main Linux compatibility candidate | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39157203056); no new Linux release |
| Official Windows release workflow | [Passed](https://github.com/Lotulune/Copperbench/actions/runs/39157240306), including full Windows tests, package generation, actual EXE branding, SBOM/provenance and uploaded-asset verification |
| Public payload | 15 uploaded files, including 4 update components and their descriptor |

The published installer is **`Copperbench.0.1.11.Windows.64bit.exe`**, **503,043,094 bytes**, SHA-256 **`093eef5e9811ac4bb3886798d7c2e9100fdf2859ef0311b9d6199f9015fc6fd3`**.

After publication, the publicly downloaded [hash file](https://github.com/Lotulune/Copperbench/releases/download/v0.1.11/SHA256SUMS.txt), [source metadata](https://github.com/Lotulune/Copperbench/releases/download/v0.1.11/RELEASE-METADATA.json) and [updater descriptor](https://github.com/Lotulune/Copperbench/releases/download/v0.1.11/copperbench-update-windows-x64.properties) matched their GitHub upload digests. The hash file covers every other asset, all 15 asset digests agree, and the four update component sizes and hashes match the descriptor. The metadata identifies the signed source commit and release workflow above.

This public readback did not download or execute the full installer; the protected workflow verified uploaded assets against its built payload before publication. The presentation checks did not repeat real installation, upgrade, uninstallation or player-world acceptance against the public packages. OS-owned dialogs and the NSIS bootstrap/uninstaller retain their platform presentation.

The tagged `product-status.json` retains its pre-publication build declaration. This report and the [current download index](../releases/current.md) record the observed public delivery without changing released bytes. Linux remains at 0.1.9, and the [0.1.10](maintenance-release-0.1.10.md) and [0.1.9](maintenance-release-0.1.9.md) records retain their original acceptance scope.
