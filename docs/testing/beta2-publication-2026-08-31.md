# Public Beta publication verification: v0.1.0-beta.2

Date: 2026-08-31

## Published release

- Beta tag: `v0.1.0-beta.2`
- Release-control commit: `8b06328347bc45b9ccc8cb243bf913c3e28e3be6`
- Merged-main CI: `33334020837` (passed)
- Windows release workflow: `33334394466` (passed)
- GitHub release state: `draft=false`, `prerelease=true`
- Published at: `2026-08-30T21:08:25Z`
- Asset count: 10; all assets are `uploaded` and all expose SHA-256 digests

The workflow completed the signed source gate, release tests, package collection,
SBOM generation, payload verification, build provenance generation, draft upload,
remote draft-asset digest verification, and final publication successfully.

## Authoritative Beta status correction

The bundled `product-status.json` declares:

- `product.channel=beta`
- `product.betaEligible=true`
- `delivery.betaRelease.tag=v0.1.0-beta.2`

This corrects the metadata-only `product.channel=preview` mismatch observed in
`v0.1.0-beta.1`. The immutable Beta 1 tag and release remain historical evidence;
no tag was moved or rewritten.

## Exact-binary promotion verification

`RELEASE-METADATA.json` records:

- release tag `v0.1.0-beta.2`
- release-control commit `8b06328347bc45b9ccc8cb243bf913c3e28e3be6`
- workflow run `33334394466`
- `binarySource.mode=promoted-tested-candidate`
- candidate tag `v0.1.0-preview.6`
- candidate source commit `f677e481914efc2b0a2ceb084a5d9c2f6e7407c8`

The four canonical assets have identical GitHub size and SHA-256 values across
`v0.1.0-preview.6`, `v0.1.0-beta.1`, and `v0.1.0-beta.2`:

| Role | Asset | Size | SHA-256 |
| --- | --- | ---: | --- |
| EXE | `Copperbench.0.1.0.Windows.64bit.exe` | 439155078 | `fbe3bda67efb179d08b09b0e6153a77040e72f0e5c599ef08f7087847ce1354b` |
| ZIP | `Copperbench.0.1.0.Windows.64bit.zip` | 462285094 | `e6ee15a656127c521717b04fc60643c6e5f8b8c8e452d25c6bafc2b1e114bf13` |
| MSIX | `Copperbench.0.1.0.Windows.64bit.msix` | 477386331 | `2586b7c4e75782c7e6541fe40fa277f7d235df7abc9c4092f4e0d98e969ddd00` |
| SBOM | `copperbench.spdx.json` | 487666 | `f467cfe23bbdad1f51ccd55723bc3448b1811eda2e0c534ad47daa3d18473a97` |

Therefore Beta 2 fixes the authoritative channel metadata without changing the
tested candidate binaries.

## Scope statement

The real JCEF accessibility audit, final clean-Windows RC replay, and five-person
external tester trial remain outside the current Beta scope and are not claimed
as passed. Windows packages remain unsigned by Authenticode.
