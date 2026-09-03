# Beta 4 publication evidence — 2026-09-03

## Public release

- Tag: `v0.1.0-beta.4`
- Release-control source: `29ac9cf23f9f029f2c1b671b91e9ae59410df40a`
- Published at: `2026-09-03T16:15:42Z`
- Release workflow: `33751421385`
- GitHub Release id: `382164614`
- Public state: `draft=false`, `prerelease=true`

## Frozen tested candidate

- Candidate tag: `v0.1.0-preview.8`
- Candidate source: `69469b8f28464469e6d917335b49b775ddb343dd`
- Candidate release workflow: `33740746411`
- Candidate evidence: `docs/testing/beta-candidate-preview8-2026-09-03.md`
- Installed-product binary-source evidence: `docs/testing/p0-installed-product-candidate-d0d96877-2026-09-03.md`

The tracked delta from the installed-product binary source `d0d96877` to Preview 8 source `69469b8f` contains only release-control status/evidence changes. It contains no product runtime, generator, MCP, SDK, UI, or packaging implementation change.

## Canonical published asset baseline

The public Beta 4 assets have the same SHA-256 digests as the frozen Preview 8 assets:

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| `Copperbench.0.1.0.Windows.64bit.exe` | 439370065 | `660a01351e91f70286b632de896a5581545a3eef3b45d3b564227cfc962f7206` |
| `Copperbench.0.1.0.Windows.64bit.zip` | 462479650 | `c0c8081a6f0b019914c9a28e0d5ab2c5ba74a347921a1cf591dd87fc6e228fcc` |
| `Copperbench.0.1.0.Windows.64bit.msix` | 477593360 | `3aedbcbdd23a1c9cb171f10cb25ec42a61c64e314f0f0d46c30b76bb3b3ccf36` |
| `copperbench.spdx.json` | 489570 | `2149c06e5f7ed112ee8a40098ffad5b8a67b843354946594fbd9a6867f43442b` |

GitHub reports all four assets as uploaded with the digests above. These values are byte-for-byte identical to the frozen Preview 8 candidate baseline.

## Exact-binary promotion proof

The Beta 4 release job completed successfully and used the repository's `promote-tested-candidate` path:

- the signed Beta release source contract passed;
- package generation was skipped for the Beta payload because the already-tested candidate binaries were reused;
- the workflow explicitly promoted the tested candidate assets;
- draft-release asset digest verification passed before publication;
- the final GitHub Release publication step passed.

Therefore Beta 4 did not rebuild a second set of Windows binaries. Its EXE, ZIP, MSIX, and SBOM are the frozen Preview 8 bytes.

## Installed-product evidence boundary

`d0d96877` retains the clean Windows 11 installed-product evidence for bundled JDK resolution, real desktop Run Client lifecycle, desktop MCP authentication/lifecycle, and the full external-Agent read/write/plan/build/conflict/code-compile loop.

No additional G7 replay is claimed for the downloaded public Preview 8/Beta 4 EXE. During the public-candidate replay attempt, the automation sandbox rejected Hyper-V guest writes. Because the tracked product/runtime/build/SDK source did not change between the validated `d0d96877` tree and Preview 8, the existing installed-product evidence remains the behavioral evidence for the released product code; the release workflows independently verify the public packaging and exact-binary promotion chain.

## Publication closeout

After this evidence is merged, `product-status.json` promotes `v0.1.0-beta.4` to `publishedBaseline` and changes `delivery.betaRelease.status` from `candidate-frozen` to `public-prerelease`. No product binary or runtime implementation is changed by this closeout.
