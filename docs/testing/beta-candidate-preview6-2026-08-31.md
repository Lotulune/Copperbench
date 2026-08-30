# Beta candidate freeze: v0.1.0-preview.6

Date: 2026-08-31

## Source and publication

- Candidate tag: `v0.1.0-preview.6`
- Source commit: `f677e481914efc2b0a2ceb084a5d9c2f6e7407c8`
- Merged-main CI: `33329612708` (`success`)
- Windows release workflow: `33330520467` (`success`)
- GitHub Release state: public prerelease (`draft=false`, `prerelease=true`)
- Release-source verification: signed annotated tag, allowed SSH signer, exact main commit, clean isolated worktree (`passed`)
- Draft asset verification and publication steps: `passed`

## Frozen canonical assets

| Role | Asset | SHA-256 |
| --- | --- | --- |
| EXE | `Copperbench.0.1.0.Windows.64bit.exe` | `fbe3bda67efb179d08b09b0e6153a77040e72f0e5c599ef08f7087847ce1354b` |
| ZIP | `Copperbench.0.1.0.Windows.64bit.zip` | `e6ee15a656127c521717b04fc60643c6e5f8b8c8e452d25c6bafc2b1e114bf13` |
| MSIX | `Copperbench.0.1.0.Windows.64bit.msix` | `2586b7c4e75782c7e6541fe40fa277f7d235df7abc9c4092f4e0d98e969ddd00` |
| SBOM | `copperbench.spdx.json` | `f467cfe23bbdad1f51ccd55723bc3448b1811eda2e0c534ad47daa3d18473a97` |

These names and digests are the candidate contract recorded under `delivery.betaRelease.candidateRelease` in `product-status.json`. The Beta workflow must promote these exact four candidate bytes instead of rebuilding them.

## Scope decision

The current Beta scope continues to exclude the real JCEF accessibility audit, final clean-Windows RC replay, and five-person external tester trial. Those exclusions are not claims that the omitted checks passed. All gates still marked `betaBlocking=true` are passed in the status source.

Any product-code, build-script, runtime, schema/tooling implementation, license, or other build-affecting change after this candidate freeze invalidates this candidate and requires a new signed Preview candidate.
