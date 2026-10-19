# Linux 0.1.13 release preparation

Status: **preparing; not published**. The user requested that Linux advance to
0.1.13 alongside Windows. The currently published Linux release remains
`v0.1.9-linux-stable`; its authorization and evidence retain their original scope.

## Candidate to verify

The [Windows 0.1.13 release record](maintenance-release-0.1.13.md) identifies:

- Frozen product source: `464b75a39c34252f61c7d859b8d267dfabba205c`.
- Main-push Linux candidate: [39264954357](https://github.com/Lotulune/Copperbench/actions/runs/39264954357).
- Target Linux tag: `v0.1.13-linux-stable`.

The GitHub API run and jobs receipts have now been retrieved independently:
[run](../../evidence/maintenance/2026-10-20/linux-0113/candidate-run.json) and
[jobs](../../evidence/maintenance/2026-10-20/linux-0113/candidate-jobs.json).
They identify a successful main push on that exact source, attempt 1; all thirteen
maintenance-required steps passed. The optional NeoForge 26.3 dependency-graph
job and opt-in 26.3 acceptance/upload steps were skipped and are not newly qualified.

The candidate artifact is still available (artifact ID `12193438857`, archive
size 4,370,901,122 bytes). The independent cloud consumer
[39320163547](https://github.com/Lotulune/Copperbench/actions/runs/39320163547)
completed successfully using harness commit
`ce27327d61f43d0cc050b37e272bd2124f98a60f`. Its
[run receipt](../../evidence/maintenance/2026-10-20/linux-0113/candidate-consumer-run.json)
and [jobs receipt](../../evidence/maintenance/2026-10-20/linux-0113/candidate-consumer-jobs.json)
record the passed download, metadata, signed provenance and package-inspection
step. This is independent cloud verification of the frozen bytes, not a local
package execution or installed/player acceptance.

The small output artifact `linux-candidate-verification-39264954357` contains the
exact metadata, manifest, checksums, verified provenance and per-entry results.
Downloading it into this checkout currently redirects to
`productionresultssa5.blob.core.windows.net` and is denied by the environment
proxy; the large original artifact uses `productionresultssa14.blob.core.windows.net`.
Both destinations have been saved to the environment network draft. The exact
candidate identity and package digests will be recorded from that output rather
than invented or copied from a previous release. The original consumer's
human-readable scope string inherited the words "All eight" from 0.1.9; its
provenance verifier actually iterates every downloaded file. The current workflow
removes that fixed count and retains the complete per-file results as evidence.

## Preparation changes

The cloud candidate consumer now derives the version from the selected source
commit rather than assuming 0.1.9. It compares the source version with metadata
and the packaged JAR, and retains document-byte, legacy fix-marker and generator
template checks. The frozen export configuration ships the download index, Linux
installation guide and development guide, but omits the version-specific 0.1.13
release documents. The consumer checks those three shipped routes against the
exact source, rather than requiring later promotion notes inside immutable bytes.
All three original version-specific document checks remain required for 0.1.9.

The maintenance policy recognizes only the exact source/run/tag tuple above.
This code support does not approve a release: the active authorization JSON still
describes 0.1.9. A new authorization must bind real metadata, package digests,
successful run/jobs receipts, an immutable validation report and the appropriate
approval/acceptance scope. Thirteen required CI steps, signed latest-main tags,
protected production review, provenance verification and exact downloaded draft
comparisons remain enforced.

Only named candidate-consumer files and the Chinese README were added to the
post-freeze source-delta allowlist. Product sources, packaging, producer workflows
and unrelated acceptance drivers remain rejected.

## Completion steps

1. Enable access to the observed GitHub artifact storage destinations and retrieve
   the successful consumer output. Preserve its actual metadata, digests and signed
   provenance, and verify the output evidence-hash manifest. The existing M1 dispatch inputs
   are `linux_candidate_run=39264954357` and
   `linux_candidate_source=464b75a39c34252f61c7d859b8d267dfabba205c`.
2. Record the resulting candidate identity, checksums and verification scope.
   Complete required acceptance or obtain an explicit, candidate-bound maintenance
   approval if full installed acceptance is not repeated. Preserve prior evidence.
3. Review and merge the preparation and evidence changes, verify that the source
   delta is allowed, and create the signed Linux tag on the resulting latest main
   using an authorized release signer. This cloud checkout currently has no
   configured release signing identity; do not manufacture or bypass a signature.
4. Run Linux release control with `publish=false`, verify its draft download
   comparisons, then complete protected publication. Update the public download
   index and product-status release fields only after publication is observed.
5. Run public package acceptance for the Ubuntu Setup and DEB artifacts, including
   an upgrade from 0.1.9. Bind results to the actual 0.1.13 bytes. Automated cloud
   checks do not certify GNOME/player acceptance.

The [Linux release notes](../releases/v0.1.13-linux.md) remain a preparation page
until these steps complete. This record is not yet a digest-bound authorization
report and may be updated as evidence arrives.

## Local validation

- 47 Linux release-authorization/candidate-metadata tests passed.
- 11 synthetic candidate-package tests passed, including version mismatch,
  changed/missing/duplicate documentation, legacy fix markers and template drift.
- 9 existing cloud acceptance boundary tests passed.
- Markdown links, product-status validation, workflow YAML parsing and diff
  whitespace checks passed.

Synthetic test fixtures are not candidate evidence. No application or packaging
source was changed in this preparation, and no new package build was claimed.
