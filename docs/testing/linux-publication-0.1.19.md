# Linux 0.1.19 publication

[Linux v0.1.19-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.19-linux-stable) was published at **2026-10-30T10:59:50Z**. Its signed tag points to `de5049224dfaa5b39438e059fd2fce2aa8b61555`; binary source remains `a6851b3b16a071a922c833d66b3e7ae99008b58f`.

The [main producer](https://github.com/Lotulune/Copperbench/actions/runs/41025635804) passed all thirteen required candidate steps. The [independent consumer](https://github.com/Lotulune/Copperbench/actions/runs/41029948787) verified every frozen file, exact source/run provenance and package contents. [Digest-bound authorization](maintenance-linux-0.1.19.md) permits only candidate `sha256:088650890e4ac7d1f5a42669f63b2b04a0c2cc1336da97f507825aa55235cd57`. The [promotion](https://github.com/Lotulune/Copperbench/actions/runs/41043720045) verified signed latest-main source, allowed source delta, every frozen file and complete downloaded-draft equality before publication. A first promotion dispatch, [41043526553](https://github.com/Lotulune/Copperbench/actions/runs/41043526553), was started from `main` instead of the tag ref; the production environment rejected it before any step ran, and nothing was created.

## Public assets

All 18 public asset digests were checked against the frozen candidate and authorization: the 17 candidate files match `linux-candidate-sha256.txt` exactly, and the remaining asset is `LINUX-RELEASE-AUTHORIZATION.json`. Public API receipts are retained under `output/release-0.1.19/public/linux/`; release assets and workflow records provide permanent retrieval points.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Copperbench.0.1.19.Linux.x86_64.Setup.tar.gz](https://github.com/Lotulune/Copperbench/releases/download/v0.1.19-linux-stable/Copperbench.0.1.19.Linux.x86_64.Setup.tar.gz) | 1117293947 | `d71214cf321ae72298ccf8513c28c232aff8951ca413f7bd924783c3372e6d19` |
| [copperbench_0.1.19_amd64.deb](https://github.com/Lotulune/Copperbench/releases/download/v0.1.19-linux-stable/copperbench_0.1.19_amd64.deb) | 1031363732 | `af40c52ada059be26e1b56f86c5cbedd8ce641ee7cf7552d4790c13ece53e93e` |
| [Copperbench.0.1.19.Linux.x86_64.tar.gz](https://github.com/Lotulune/Copperbench/releases/download/v0.1.19-linux-stable/Copperbench.0.1.19.Linux.x86_64.tar.gz) | 1114310353 | `936db85bcb361d75460aa95cf6e87f957a0aec8fb60076653a622fd8e2008ef5` |

Installation, upgrade, user-file retention and removal results are recorded separately in [public-package acceptance](installed-release-0.1.19.md). Full GNOME/Wayland desktop, real-account CLI and player-controlled gameplay certification are not claimed; `formalSupportClaim=false`. GitHub's space-to-dot filename normalization preserves the frozen bytes. Historical records retain their original source and environment.

[Windows publication](maintenance-release-0.1.19.md) and [current downloads](../releases/current.md) record the other platform and public entry points.
