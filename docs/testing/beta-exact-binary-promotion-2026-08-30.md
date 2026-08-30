# Public Beta exact-binary promotion contract

## Why this contract exists

The Public Beta gates validate a specific Windows installer, not merely a source
tree. G9.5 binds its final replay to a source commit and EXE SHA-256, and the
five-tester completion gate binds all records to the same commit and installer
SHA-256.

The Windows release workflow also performs a fresh packaging build after a
release tag is created. That build cannot be treated as byte-identical to the
tested candidate: `project.builddate` is embedded in `copperbench.jar` as the
`Build-Date` manifest attribute and is also substituted into the NSIS installer
version. Rebuilding at Beta publication time would therefore break the evidence
chain even when the source code is otherwise unchanged.

Public Beta consequently uses **exact-binary promotion** instead of assuming a
rebuild is reproducible.

## Candidate freeze

Before the final Stage 9/Beta acceptance work starts, publish one signed public
Preview candidate from the frozen implementation source. The intended next
candidate is a new Preview tag; `v0.1.0-preview.3` remains only the historical
published baseline.

The candidate release provides four immutable promotion inputs:

- Windows EXE installer;
- Windows ZIP;
- Windows MSIX;
- `copperbench.spdx.json` SBOM.

`product-status.json` records the candidate tag, its exact source commit, and
the SHA-256/name pair for all four assets under
`delivery.betaRelease.candidateRelease` before `betaEligible` may become true.

The final accessibility acceptance, G9.5 replay, and all five external testers
must refer to this candidate. G9.5 and the 5/5 external-tester completion gate
specifically use the candidate EXE SHA-256.

## Evidence-only promotion

After the candidate is published, the candidate source is frozen. A Beta tag is
accepted only when `scripts/verify-release-source.ps1` proves all of the
following:

1. the Beta tag is the declared `delivery.betaRelease.tag` and is signed by an
   allowed release signer;
2. `betaEligible=true` and every beta-blocking gate is `passed`;
3. `candidateRelease.tag` is a signed Preview tag for the same product version;
4. the signed candidate tag resolves to the declared candidate source commit;
5. the candidate commit is an ancestor of the Beta release-control commit;
6. the candidate-to-Beta Git delta contains only `product-status.json`,
   `PRD-NEXT.md`, `docs/remaining-work.md`, `docs/testing/**`,
   `docs/releases/**`, or `evidence/**` changes; packaged documentation such as
   `docs/user/README.md` is deliberately excluded because it is copied into the
   Windows distribution; and
7. the four candidate asset descriptors contain valid, unique names and full
   lowercase SHA-256 values;
8. the canonical external-tester verifier still finds 5/5 valid records for
   the candidate commit and EXE SHA-256, with `packageType=exe`; and
9. the `clean-windows-11-stage9` evidence set contains a G9.5 final-RC machine
   result with `gatePromotionReady=true`, clean source, marker cleanup, and the
   exact same candidate commit/EXE SHA-256.

Any product code, build script, runtime, plugin, schema/tooling implementation,
license, or other build-affecting change after candidate testing requires a new
Preview candidate and a new acceptance cycle. It cannot ride the old evidence
into Beta.

## Publication behavior

The existing deploy workflow continues to compile, test, and build Windows
packages from the Beta release-control commit as a regression check. Before
release metadata and final payload hashes are produced, however,
`scripts/New-ReleaseMetadata.ps1` does the following for a Beta tag:

1. removes the freshly rebuilt EXE/ZIP/MSIX/SBOM from `build/release`;
2. downloads the four declared assets from the public candidate Release;
3. verifies each downloaded asset against its declared SHA-256; and
4. writes `RELEASE-METADATA.json` with the final release-control `commit` plus a
   separate `binarySource` object identifying the tested candidate tag, commit,
   and asset hashes.

`scripts/Test-ReleasePayload.ps1` then recomputes every entry in
`SHA256SUMS.txt` and, for Beta, independently requires the EXE/ZIP/MSIX/SBOM to
match the declared candidate hashes. A missing asset, changed byte, stale hash,
or candidate mismatch fails before draft publication.

The final workflow provenance therefore records the assembly/promotion run for
the Beta payload, while `binarySource` points to the signed Preview source and
its original release/build provenance. The Beta release does not claim that the
release-control commit independently rebuilt byte-identical Windows binaries.

## Acceptance sequence

The release sequence is intentionally one-way:

1. freeze implementation and release tooling;
2. publish the signed Preview candidate;
3. first prove the Windows accessibility environment with the Edge control,
   then run the Copperbench accessibility/DPI/keyboard acceptance on that
   candidate;
4. run G9.5 `-FinalRcReplay` against the candidate commit and exact EXE hash;
5. collect 5/5 non-core tester records against the same candidate commit and
   EXE hash;
6. add only the resulting evidence/status/documentation changes and set the
   beta-blocking gates to `passed` when their machine contracts allow it;
7. create the signed Beta tag on that evidence-only release-control commit; and
8. publish Beta by promoting the declared candidate bytes exactly.

Until steps 3-5 have real evidence, `betaEligible` remains `false` and no Beta
tag can pass the release-source gate.
