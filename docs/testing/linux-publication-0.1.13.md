# Linux 0.1.13 publication

[Copperbench v0.1.13-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.13-linux-stable)
was published on 2026-10-20 at 06:45:26 Asia/Tokyo
(`2026-10-19T21:45:26Z`). It is public and is not a prerelease.

The signed release tag points to `4110b253f5d3f99218caf817759154ff3e2d2613`.
It promotes the unchanged candidate from `464b75a39c34252f61c7d859b8d267dfabba205c`,
produced by [candidate run 39264954357](https://github.com/Lotulune/Copperbench/actions/runs/39264954357).
No product binaries were rebuilt during publication. The original
[digest-bound authorization report](maintenance-linux-0.1.13.md) remains unchanged.

## Publication verification

- Local release authorization tests passed: 40 tests. The source-delta check and
  digest-bound authorization verification passed, including all 13 candidate CI gates.
- The tag signature verified against `.github/release-signers`. The production
  deployment was approved under the user's explicit Linux 0.1.13 publication request.
- [Publication run 39328695381](https://github.com/Lotulune/Copperbench/actions/runs/39328695381)
  completed successfully. Its contracts, latest-main and source-delta checks,
  frozen package verification, signed provenance checks, draft upload and full
  downloaded-draft byte comparisons all passed before publication.
- After publication, the GitHub Releases API reported 18 uploaded assets. Every
  name, size and SHA-256 digest matched the 17 frozen files in the
  [verified provenance record](../../evidence/maintenance/2026-10-20/linux-0113/consumer/provenance/provenance-verification.json)
  plus the exact tagged `LINUX-RELEASE-AUTHORIZATION.json` bytes. GitHub normalizes
  spaces in uploaded filenames to dots. This final API check did not download
  another local copy of all packages; the publication workflow performed the
  complete download and byte comparison.

| Public package | SHA-256 |
| --- | --- |
| `Copperbench.0.1.13.Linux.x86_64.Setup.tar.gz` | `d7103fa1267c6e791a7deba8397fc764d45e997dcd41f8db01dd2fa3ea5f464d` |
| `Copperbench.0.1.13.Linux.x86_64.tar.gz` | `a142f745943778dd541b2b522749942da138ff03090e8d9949be9f381cb171c6` |
| `copperbench_0.1.13_amd64.deb` | `ae4839c26d372e76e56bf55396993021cf0670b94ffdff639c9d63a4533a9b26` |

The release also includes Linux in-app update components, candidate metadata,
SPDX SBOM, checksum list and release authorization. Use the checksum list from
the same release.

## Acceptance scope

The [release notes](../releases/v0.1.13-linux.md) and original authorization define
the maintenance scope. Full GNOME install, upgrade, uninstall and player-controlled
gameplay acceptance were not repeated. `formalSupportClaim` remains `false`;
historical installed acceptance does not certify these newly published bytes.
