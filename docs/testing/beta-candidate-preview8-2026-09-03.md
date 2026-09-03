# Beta 4 candidate: Preview 8

Date: 2026-09-03

## Candidate identity

- Candidate tag: `v0.1.0-preview.8`
- Source commit: `69469b8f28464469e6d917335b49b775ddb343dd`
- Release workflow: `https://github.com/Lotulune/Copperbench/actions/runs/33740746411`
- GitHub Release: `https://github.com/Lotulune/Copperbench/releases/tag/v0.1.0-preview.8`
- Release state: public prerelease (`draft=false`, `prerelease=true`)
- `RELEASE-METADATA.json`: `binarySource.mode=built-from-release-commit`, with both `commit` and `binarySource.commit` equal to `69469b8f28464469e6d917335b49b775ddb343dd`.

The tag is an annotated SSH-signed tag. Repository `verify-release-source.ps1` passed against the exact current main commit before the tag was pushed, and the release workflow repeated the tag/clean-source check before building.

## Canonical candidate assets

| Role | Asset | SHA-256 |
| --- | --- | --- |
| EXE | `Copperbench.0.1.0.Windows.64bit.exe` | `660a01351e91f70286b632de896a5581545a3eef3b45d3b564227cfc962f7206` |
| ZIP | `Copperbench.0.1.0.Windows.64bit.zip` | `c0c8081a6f0b019914c9a28e0d5ab2c5ba74a347921a1cf591dd87fc6e228fcc` |
| MSIX | `Copperbench.0.1.0.Windows.64bit.msix` | `3aedbcbdd23a1c9cb171f10cb25ec42a61c64e314f0f0d46c30b76bb3b3ccf36` |
| SPDX SBOM | `copperbench.spdx.json` | `2149c06e5f7ed112ee8a40098ffad5b8a67b843354946594fbd9a6867f43442b` |

The public EXE was independently downloaded after publication: length `439370065` bytes and SHA-256 `660a01351e91f70286b632de896a5581545a3eef3b45d3b564227cfc962f7206`, matching the GitHub Release digest and `SHA256SUMS.txt`.

## Release-workflow verification

Build Windows release run `33740746411` completed successfully. Its single traceable-package job passed all required stages, including:

- tagged source checkout and release-source verification;
- Java tests and Javadoc plus UI release build/tests;
- Windows ZIP, NSIS installer, and MSIX package creation;
- SPDX SBOM generation;
- release metadata and SHA-256 generation;
- release payload verification;
- build provenance attestation;
- draft prerelease asset upload and digest verification;
- final prerelease publication.

## Installed-product evidence inheritance

The post-review installed-product candidate `d0d96877afdc00c98cdfeab20524f2f2551b73f9` previously passed the exact clean-Windows installed-product P0 replay documented in `p0-installed-product-candidate-d0d96877-2026-09-03.md`.

`git diff --name-status d0d96877...69469b8f` contains exactly two tracked paths:

1. `docs/testing/p0-installed-product-candidate-d0d96877-2026-09-03.md` (added evidence);
2. `product-status.json` (release-control/status update).

There are no product Java, UI, MCP, SDK, generator, build-script, packaging-script, or runtime source changes between the installed candidate and Preview 8 release source. Therefore the existing installed-product behavioral evidence remains applicable to the unchanged product code, while Preview 8 supplies the independently rebuilt and verified release artifacts/provenance.

An additional G7 replay of the public Preview 8 EXE is **not** claimed here: the current automation sandbox rejected Hyper-V guest write operations before any candidate installation was changed. This tooling limitation is kept explicit rather than converting an unexecuted replay into evidence.

## Beta promotion contract

`v0.1.0-beta.4` may be published only as an exact-binary promotion of the four canonical Preview 8 assets listed above. The Beta release-control commit may change release/status/evidence metadata, but the Beta workflow must download these Preview 8 assets and verify the frozen SHA-256 values instead of rebuilding them.
