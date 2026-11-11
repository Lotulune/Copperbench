# Linux 0.1.20 publication

[Linux v0.1.20-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.20-linux-stable) was published at **2026-11-11T07:55:17Z**. Its signed tag points to `bfb0375420965f133a10c66d5f002bfd4938ad07`; binary source remains `5e1ab97f0a10ebf2792e2622ad0d563f8b4ffc29`.

The [main producer](https://github.com/Lotulune/Copperbench/actions/runs/42973803995) passed all thirteen required candidate steps. The [independent consumer](https://github.com/Lotulune/Copperbench/actions/runs/42976562215) verified every frozen file, exact source/run provenance and package contents. [Digest-bound authorization](maintenance-linux-0.1.20.md) permits only candidate `sha256:2df78ecfa00b345423438a9c8c4164b96aa1895a0f7856eb259a733de82a597b`. The [promotion](https://github.com/Lotulune/Copperbench/actions/runs/42981929877), dispatched from the signed tag ref, verified signed latest-main source, allowed source delta, every frozen file and complete downloaded-draft equality before publication. Its production deployment was approved on the repository owner's instruction.

## Public assets

All 18 public asset digests were checked against the frozen candidate and authorization: the 16 files listed in `linux-candidate-sha256.txt` match it exactly, that list is itself a candidate file, and the remaining asset is `LINUX-RELEASE-AUTHORIZATION.json`. The check compared the GitHub release API digests with the consumer's checksum list; release assets and workflow records provide permanent retrieval points.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Copperbench.0.1.20.Linux.x86_64.Setup.tar.gz](https://github.com/Lotulune/Copperbench/releases/download/v0.1.20-linux-stable/Copperbench.0.1.20.Linux.x86_64.Setup.tar.gz) | 1116799780 | `735ff64e7033005a32e194182d148113df8818ae6b795bbca4b5c73288a2e2ed` |
| [copperbench_0.1.20_amd64.deb](https://github.com/Lotulune/Copperbench/releases/download/v0.1.20-linux-stable/copperbench_0.1.20_amd64.deb) | 1031308604 | `a474240e1978a9f0f610b0dbb1a57b616f67b4449fa8649e6e241e7f7c7e3af9` |
| [Copperbench.0.1.20.Linux.x86_64.tar.gz](https://github.com/Lotulune/Copperbench/releases/download/v0.1.20-linux-stable/Copperbench.0.1.20.Linux.x86_64.tar.gz) | 1113806701 | `52756c3736bb18c4c7089c7c1c203897a2f0e7afc52b9f4c1c0687358b15e889` |

Installation, upgrade, user-file retention and removal results are recorded separately in [public-package acceptance](installed-release-0.1.20.md). Full GNOME/Wayland desktop, real-account CLI and player-controlled gameplay certification are not claimed; `formalSupportClaim=false`. GitHub's space-to-dot filename normalization preserves the frozen bytes. Historical records retain their original source and environment.

[Windows publication](maintenance-release-0.1.20.md) and [current downloads](../releases/current.md) record the other platform and public entry points.
