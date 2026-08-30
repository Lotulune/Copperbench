# Public Beta publication verification: v0.1.0-beta.1

Date: 2026-08-31

## Published release

- Beta tag: `v0.1.0-beta.1`
- Release-control commit: `f12823ab619581f5b595bbc1bef2942d4152c37f`
- Merged-main CI: `33332178127` (passed)
- Windows release workflow: `33332537616` (passed)
- GitHub release state: `draft=false`, `prerelease=true`
- Published at: `2026-08-30T20:20:22Z`

The workflow completed source verification, release tests, payload verification,
artifact provenance generation, draft upload, remote draft-asset digest
verification, and final publication successfully.

## Exact-binary promotion verification

`RELEASE-METADATA.json` records:

- `binarySource.mode=promoted-tested-candidate`
- candidate tag `v0.1.0-preview.6`
- candidate source commit `f677e481914efc2b0a2ceb084a5d9c2f6e7407c8`
- Beta release-control commit `f12823ab619581f5b595bbc1bef2942d4152c37f`

The four canonical assets have identical GitHub size and SHA-256 values in
`v0.1.0-preview.6` and `v0.1.0-beta.1`:

| Role | Asset | SHA-256 |
| --- | --- | --- |
| EXE | `Copperbench.0.1.0.Windows.64bit.exe` | `fbe3bda67efb179d08b09b0e6153a77040e72f0e5c599ef08f7087847ce1354b` |
| ZIP | `Copperbench.0.1.0.Windows.64bit.zip` | `e6ee15a656127c521717b04fc60643c6e5f8b8c8e452d25c6bafc2b1e114bf13` |
| MSIX | `Copperbench.0.1.0.Windows.64bit.msix` | `2586b7c4e75782c7e6541fe40fa277f7d235df7abc9c4092f4e0d98e969ddd00` |
| SBOM | `copperbench.spdx.json` | `f467cfe23bbdad1f51ccd55723bc3448b1811eda2e0c534ad47daa3d18473a97` |

This proves the Public Beta binaries were promoted from the signed Preview 6
candidate rather than rebuilt.

## Metadata correction required

The Beta 1 release-control commit left `product.channel` as `preview`. The
release template states that `product-status.json` is the authoritative channel
source, so Beta 1 has a metadata-level channel mismatch even though its binary
promotion is correct.

Do not move or replace `v0.1.0-beta.1`. The correction is a new signed
`v0.1.0-beta.2` release-control tag with `product.channel=beta`, using the same
`v0.1.0-preview.6` candidate and the same four canonical bytes. No product code,
build script, runtime, schema/tooling implementation, or license change is
needed for this correction.

The real JCEF accessibility audit, final clean-Windows RC replay, and five-person
external tester trial remain outside the current Beta scope and are not claimed
as passed.
