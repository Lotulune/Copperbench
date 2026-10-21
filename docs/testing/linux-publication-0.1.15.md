# Linux 0.1.15 publication

[Copperbench v0.1.15-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.15-linux-stable) was published at **2026-10-21T09:54:22Z**. It is public and is not a prerelease.

The signed release tag points to `3c6be89bbfaafc01e39dac81c924a60fbcfdc46c` and promotes unchanged binaries from `d99aa3f4af171954a575f82de3afd1ee5b34c9a6`, produced by [candidate run 39570341571](https://github.com/Lotulune/Copperbench/actions/runs/39570341571). No product binaries were rebuilt during promotion. The original [digest-bound authorization report](maintenance-linux-0.1.15.md) remains unchanged.

## Verification

- All 13 candidate steps passed. Independent [consumer run 39572937254](https://github.com/Lotulune/Copperbench/actions/runs/39572937254) verified 17 files, signed source/workflow/run provenance and archive contents. All 10 downloaded evidence-manifest entries matched their hashes.
- Preparation passed 50 authorization/metadata tests, 11 package-verifier tests and actual source-delta/authorization validation. [PR #144](https://github.com/Lotulune/Copperbench/pull/144) passed its required checks before merge.
- [Publication run 39579528082](https://github.com/Lotulune/Copperbench/actions/runs/39579528082) passed signature, latest-main, source-delta, frozen-file, provenance, draft-upload and complete downloaded-draft byte comparisons before publication.
- The [public API receipt](../../evidence/maintenance/2026-10-21/publication-0.1.15/linux/release.json) and [verification result](../../evidence/maintenance/2026-10-21/publication-0.1.15/linux/verification.json) confirm all 18 names, sizes and SHA-256 digests match the 17 independently verified frozen files plus the exact tagged [authorization bytes](../../evidence/maintenance/2026-10-21/publication-0.1.15/linux/LINUX-RELEASE-AUTHORIZATION.json). All 8 update components match the public descriptor's names, sizes, hashes and version.

| Public package | SHA-256 |
| --- | --- |
| `Copperbench.0.1.15.Linux.x86_64.Setup.tar.gz` | `c304781269aed0b36882033ad396b7814bdedd0987b72b50d9a9ec3e118e431c` |
| `Copperbench.0.1.15.Linux.x86_64.tar.gz` | `bcf013b9e9661d2d6ef4de58e73c77419cd08ac70c7cab35c10b887c6a55f98f` |
| `copperbench_0.1.15_amd64.deb` | `ece4b83e12132307c82c51a875ae0eccfdc36458b5f1b7671b94b62e0c39ef3d` |

The local post-publication check downloaded the metadata, checksum, authorization and update descriptor; it did not download another local copy of every Linux package. The publication workflow performed the complete draft download and byte comparison. Use the checksum list from this same release.

Complete GNOME install, upgrade, uninstall, screen-reader and player-controlled gameplay acceptance were not repeated. `formalSupportClaim` remains `false`; historical installed evidence does not certify these new bytes. See the [release notes](../releases/v0.1.15-linux.md) and [current downloads](../releases/current.md).
