# Linux 0.1.18 publication

[Linux v0.1.18-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.18-linux-stable) was published at **2026-10-23T14:32:49Z**. Its signed tag points to `d46e5daed141a1154a44cea4db3890fa69bd3c8e`; binary source remains `28b912e94b7bf271e91bafeaef7a02daf1abde98`.

The [main producer](https://github.com/Lotulune/Copperbench/actions/runs/39956381252) passed all thirteen required candidate steps. The [independent consumer](https://github.com/Lotulune/Copperbench/actions/runs/39959392189) verified every frozen file, exact source/run provenance and package contents including the new Three.js license. [Digest-bound authorization](maintenance-linux-0.1.18.md) permits only candidate `sha256:a2754654513b0c48717c31cccbd90742913a5cdb01cd6e072eee6cf4f03d571c`. The [promotion](https://github.com/Lotulune/Copperbench/actions/runs/39969163795) verified signed latest-main source, allowed source delta, every frozen file and complete downloaded-draft equality before publication.

## Public assets

All 18 public asset sizes/digests were checked against the frozen candidate and authorization. Candidate and draft payloads were independently downloaded by the consumer and promotion workflows. Public API and metadata receipts are retained under `output/release-0.1.18/public/linux/`; release assets and workflow records provide permanent retrieval points.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| [Copperbench.0.1.18.Linux.x86_64.Setup.tar.gz](https://github.com/Lotulune/Copperbench/releases/download/v0.1.18-linux-stable/Copperbench.0.1.18.Linux.x86_64.Setup.tar.gz) | 1117280899 | `4b24c5b8ba333ab396a4f0bc6e497828610bc5b56ccdb9e7d1e7fdfd48943081` |
| [copperbench_0.1.18_amd64.deb](https://github.com/Lotulune/Copperbench/releases/download/v0.1.18-linux-stable/copperbench_0.1.18_amd64.deb) | 1031348958 | `fca5a3706d279a02f78e3c12c78dde8dbceb3c9c61c810f720ebb4b5a90e16fa` |
| [Copperbench.0.1.18.Linux.x86_64.tar.gz](https://github.com/Lotulune/Copperbench/releases/download/v0.1.18-linux-stable/Copperbench.0.1.18.Linux.x86_64.tar.gz) | 1114295335 | `c1c1c0496b9257ab36ef8d986b665bde0157918ccb49f0778f088da468001874` |

Installation, upgrade, user-file retention and removal results are recorded separately in [public-package acceptance](installed-release-0.1.18.md). Full GNOME/Wayland desktop, real-account CLI and player-controlled gameplay certification are not claimed; `formalSupportClaim=false`. GitHub's space-to-dot filename normalization preserves the frozen bytes. Historical records retain their original source and environment.

[Windows publication](maintenance-release-0.1.18.md) and [current downloads](../releases/current.md) record the other platform and public entry points.
