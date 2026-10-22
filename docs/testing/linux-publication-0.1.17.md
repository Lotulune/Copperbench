# Linux 0.1.17 publication

[Linux v0.1.17-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.17-linux-stable) was published at **2026-10-22T11:58:13Z**. The signed release tag points to `7be632956b05e9d5a87e85ba527bd5e7a86dd300`; the unchanged binary source is `81563c9bc6ae4371b662a65b1b0bbf5ce9e5e4cb`.

The [main producer](https://github.com/Lotulune/Copperbench/actions/runs/39764178724) passed all 13 required candidate checks. The [independent consumer](https://github.com/Lotulune/Copperbench/actions/runs/39766634528) verified frozen files, exact source/run provenance and package content. [Digest-bound authorization](maintenance-linux-0.1.17.md) permits only candidate `sha256:bb277f99e8dc002eccdc427cad11d61f85ba2a29f1ac6f5b6af125c94fe40483`. The [promotion](https://github.com/Lotulune/Copperbench/actions/runs/39773079465) verified signed latest-main source, allowed source delta, every frozen file and complete downloaded-draft equality before publication.

## Public verification

All 18 public asset sizes/digests match candidate metadata/checksums and the signed authorization. All 8 update components match the downloaded 0.1.17 descriptor. Listed metadata downloads were independently byte-hashed locally; complete candidate/draft downloads were performed by the consumer and promotion workflows. Raw public API and downloaded-byte receipts remain under `output/release-0.1.17/public/linux/` in the release worktree. [Public checksums](https://github.com/Lotulune/Copperbench/releases/download/v0.1.17-linux-stable/linux-candidate-sha256.txt) and the workflow records provide permanent retrieval points.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Copperbench.0.1.17.Linux.x86_64.Setup.tar.gz](https://github.com/Lotulune/Copperbench/releases/download/v0.1.17-linux-stable/Copperbench.0.1.17.Linux.x86_64.Setup.tar.gz) | 1117017491 | `8170a6cb157f50a25a16873862d36c503de50b38343aa1b4f55ba3bd7f5880c8` |
| [copperbench_0.1.17_amd64.deb](https://github.com/Lotulune/Copperbench/releases/download/v0.1.17-linux-stable/copperbench_0.1.17_amd64.deb) | 1031042336 | `1ca009b4084868c12b79d7fd6d6341897ef696a62e49ca1d81b80978a01d117a` |
| [Copperbench.0.1.17.Linux.x86_64.tar.gz](https://github.com/Lotulune/Copperbench/releases/download/v0.1.17-linux-stable/Copperbench.0.1.17.Linux.x86_64.tar.gz) | 1114026945 | `9e4062ecdd57ddc7ff954befd60212d0ded123e20f84615e89c9ddec97fab3fc` |

Public checksum, candidate metadata, authorization and component descriptor are available on the same release. GitHub space-to-dot filename normalization preserves bytes. No new full GNOME/Wayland installation, upgrade, uninstall, real-account CLI or player-controlled gameplay certification is claimed; `formalSupportClaim=false`.

[Windows publication](maintenance-release-0.1.17.md) and [current downloads](../releases/current.md) record the other platform and public entry points. Earlier release records remain unchanged.
