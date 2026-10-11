# REM-02/03/06 implementation and evidence index

Date: 2026-10-11 (Japan time). Development base: `99f95e1d7ea0e919342414572e31f6dd6e5c4429` (merged PR #102).
This record describes local source changes on `codex/remaining-delivery-20261011`; it is not a release or an installed-candidate acceptance.
The user selected the next small implementation batch plus candidate-acceptance preparation.

## Delivered scope

| Item | Result |
| --- | --- |
| REM-02 | New advertised, versioned `get_task_verification` read shared by Native/MCP/TypeScript and UI; precise bilingual reasons, required/executed/shortfall, unknown counts distinguished from zero, fresh input comparison |
| REM-03 | Atomic five-file delivery directory, observed application identity, byte manifest, unique testcase enforcement, bounded stdlib offline directory/ZIP verifier |
| REM-06 | Current entry points reflect merged PRs; successful raw Java XML retained for 30 days in the existing required job; evidence and failure classifications documented |
| REM-01 preparation | [Candidate acceptance worksheet](next-candidate-acceptance.md), freeze fields, platform matrix and evidence layout prepared; no new candidate/package claimed |
| REM-04/05 | Still demand-driven follow-ups, outside this selected batch |

The [protocol and delivery guide](../ai/task-verification-and-delivery.md) documents capability discovery,
version rejection, compatibility, historical input labels and verifier exit states.
Strict v1.0 task/verification/export objects retain their existing shape and error codes.
The new result contract is separate; UI language changes re-render retained raw messages without mutating task/revision or rerunning acceptance.

## Actual local checks

| Check | Observed result |
| --- | --- |
| Focused Java: result projection, doctor, task queries/events, XML/counts, workflow, exporter, Native session and real loopback MCP HTTP | 60 distinct cases passed across scoped runs, zero failures/errors/skips; Javadoc passed |
| Python SDK | 95 passed |
| TypeScript SDK | 17 passed |
| Shared schemas | 39 passed; strict legacy task and export boundaries checked |
| UI bridge/localization/result logic | 32 passed, including shared 16-scenario Core/legacy matrix |
| Production UI build | Passed, including both localization gates |
| Playwright task/authorization checks | 22 passed across 1920×1080 and 1366×768; 14 affected result cases rerun after the final count/layout refinement, all passed |
| Visual inspection | Chinese/English, both viewport sizes, light/dark result captures; no clipped result text |
| Offline verifier | 14 passed, including per-file tampering/nonzero exit, rehashed count/XML attacks, duplicate identities, unsafe paths, actual filesystem symlink, Unix/Windows link ZIP entries, truncated ZIP, expansion limits and legacy/unknown versions |
| Java → Python bundle handoff | Java exporter fixtures checked by the independent Python verifier; synthetic test artifacts, not Minecraft acceptance |
| MCP conformance 2025-11-25 | Six applicable scenarios, eight checks passed |
| Workflow and prose | actionlint, Markdown links and diff whitespace checks passed |

Local evidence is retained under `output/remaining-delivery-validation/`: original Java XML,
conformance summary/logs, browser captures, independently checked fixture bundles, a source-file digest manifest,
and candidate preparation metadata. Normal build outputs remain under `build/`.
The source-file manifest binds this uncommitted batch to its base commit and actual changed file bytes;
it does not substitute for the final reviewed commit/tree or package identity.
The exporter also passed a late-failure regression: an oversized receipt is rejected after staging,
the temporary directory is removed, and no delivery directory or success marker is published.

The first direct Gradle invocation hit the documented Windows loopback-creation failure.
The repository's hidden external Gradle worker subsequently completed the same scoped regression.
This is an infrastructure limitation, not a waived product failure.
The Java workflow tests parse real XML and operate on real temporary files with a synthetic Gradle runner.
The MCP HTTP and conformance tests run a real local server with fixture workspaces.
None of these tests substitutes for a real installed application or Minecraft gameplay run.

## Commit → CI → candidate → installed/player evidence

| Source identity | CI/evidence entry | Candidate/install/player scope |
| --- | --- | --- |
| Historical `298846dc` | [Installed cache provenance](installed-cache-provenance-2026-10-11.md) | Only the frozen Windows/Ubuntu cold/warm candidates and recorded network conditions |
| PR #101, merged at `70261a71` | [Integration record](pr-consolidation-2026-10-11.md) and [PR #101](https://github.com/Lotulune/Copperbench/pull/101) | Original source checks; historical installed/player observations retain their original candidate |
| PR #102, `99f95e1d` | Local development base | Repository media cleanup; no newly inferred runtime acceptance |
| This local REM-02/03/06 delta | Local checks above; new hosted CI not run here | Candidate not frozen; Windows/Ubuntu installation and real M1/player replay unverified |

For future runs, the existing Java required job uploads `java-test-results-<source SHA>`
with raw XML and clearly synthetic cross-verifier fixtures on success or failure.
Required check names and existing M1 routing are preserved.
The [candidate worksheet](next-candidate-acceptance.md#failure-classification) separates
`product`, `harness`, `infrastructure`, `user-stop`, and unresolved classifications.
Old failed runs remain evidence; replacing a candidate never overwrites their identity.

The final release candidate, external provenance/signing, actual installed replay and selected player/save-reenter checks remain unverified.
Both cancelled research work packages remain closed.
