# Installed cache provenance phase

This development phase addresses the remaining reproducibility work in AR-06,
AR-07 and AR-08 of the [Agent readiness PRD](agent-readiness-prd-2026-10-09.md).
The owner requested implementation of a complete phase before its consolidated
verification. The completed AR-09 baseline is `fd382b78`.

## Required behavior

- Workspace setup, Core process execution and diagnostics resolve one effective
  Gradle user home. Explicit caller paths apply consistently; relative paths are
  anchored to the product process directory before launching workspace tasks.
- A per-process option disables borrowing Gradle distributions from the user's
  other caches and the package. It preserves the selected cache and the default
  reuse behavior when isolation is not requested.
- The installed replay can establish an empty cold cache and subsequently reuse
  its verified payloads for a warm run of the same candidate and fixture. It
  preserves failed runs and never clears existing directories.
- Each SDK connection confirms the running application hash and actual cache
  configuration. Final checks retain the identities of package, application,
  launcher, SDK and fixture; source-commit declarations remain distinguished from
  observed binary identity.
- A hand-written cache note cannot be promoted to measured cold-cache evidence.
  Cold/warm receipts reject mismatched inputs, changed payloads, overlapping run
  directories and external cache links. Python optimization cannot disable the
  existing delivery assertions.

## Implementation and acceptance

Implementation covers the shared resolver, distribution reuse control, backend
and doctor observations, installed replay, focused regressions, PR CI invocation
and [operator instructions](../testing/agent-readiness-m3-task-card.md#cold-and-warm-gradle-cache-runs).
The [consolidated verification record](../testing/installed-cache-provenance-2026-10-11.md)
preserves the local successes, Java child-start rejection and Windows-incompatible
CI filename fixtures. Hosted CI runs the affected Windows cases and the complete
Linux suites. The required, Linux-candidate and independent M1 workflows all
passed for `5e40d0b0` (the candidate/required merge tree is identical). The Linux
package and embedded application hashes are frozen in that record. Windows
packaging then exposed an unchecked NSIS directory rename; direct extraction,
partial-cache repair and four real Gradle fixture checks address it separately.
The refreshed `1dbaf930` workflows and Windows installer then passed. An installed
cold attempt exposed a second application copy embedded in the Windows launcher:
its runtime digest identified the EXE instead of the shipped JAR. Windows now
loads the external JAR, with an actual launcher/doctor regression in CI.

Phase acceptance is complete on frozen product `298846dc` (Linux merge tree
`6349bb78`). Required CI, independent M1 and Linux candidate gates passed. Actual
installed cold and warm runs passed on Windows and Ubuntu, with five raw business
cases, current-input export/reconnect and source protection in each run. The
verification record binds the package/application digests and empty/retained cache
manifests. Failed default-network attempts remain recorded; the successful pairs
used temporary guest resolvers that were restored before saving both guests.

At phase acceptance, run the installed-run provenance Python regressions together
with CI selection tests; the focused Java cache/pool/process/environment/doctor
checks and Javadoc; and Markdown links. PR CI then applies the existing required
checks. Real installed cold/warm replays remain a distinct acceptance layer and
must retain the actual cache manifests, product hashes and task results. Passing
the source probes does not fill those cells.

The owner-ended independent-agent study stays closed. Unfamiliar-user research
remains deferred without participants. Host focus was not used.
