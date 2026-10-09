# M2: continuous verification and read-only environment discovery

This work follows the [2026-10-09 PRD](agent-readiness-prd-2026-10-09.md).
Status: M2 source acceptance closed on 2026-10-09. Normal Nightly passed all
14 shards; deliberate Windows SDK failure left the other 13 shards passed
and the overall result failed. Both runs use `d73b5651bc0a2c2675735f6cd387c0f085235c82`.
A later payload-retention correction is covered by 10 helper tests and six
real checksum-rejection replays, separately from those complete hosted runs.
This is not installed-package or player acceptance. See the
[validation record](../testing/agent-readiness-m2.md).

## Implementation scope

- [x] AR-04: independent Windows/Linux SDK, UI, Core/Javadoc/scale, MCP and
  generator jobs; no inter-suite `needs` chain and no failure-to-success masking.
- [x] Always-running summary preserves passed/failed/skipped/cancelled results,
  individual matrix results and an explicit reason when evidence is absent.
- [x] Reproducible SDK-failure injection and 100 repetitions of each target
  timeout regression on Windows and Linux; keep initialization and target
  operation timeouts separate.
- [x] AR-06: versioned, read-only doctor through Core, headless, MCP and both
  SDKs, using the actual execution backend. Separate product Java 25 from the
  workspace JDK; distinguish missing/unsupported/blocked/unknown findings.
- [x] Doctor reports generator, wrapper, working directory, capabilities,
  declared network/cache conditions and renderer evidence without downloading,
  installing, accepting EULA, issuing authorization or changing workspace files.
- [x] AR-07: independently sourced checksums for existing wrapper versions in
  the root, current templates and newly generated workspaces. Preserve historical
  evidence and dependency versions unless a current, assessed issue requires a fix.
- [x] Cold official-source and supported-mirror downloads, incorrect-checksum
  rejection and warm-cache build evidence have separate executable gates.
- [x] Current npm risk assessment distinguishes build-time and shipped runtime
  dependencies; record the reviewed dependency verification/locking strategy.
- [x] Finish carried-over AR-02 presentation consistency across Native Python,
  MCP Python, TypeScript and UI while preserving raw diagnostics and task state.

## Consolidated acceptance

- [x] Required layer checks from AGENTS.md, plus focused doctor, wrapper and
  independent Nightly summary regressions.
- [x] Real Windows/Linux timeout repetition receipts, with failures retained.
- [x] Read-only doctor against real Core/workspace, and negative environment
  states without mutations or implicit network probes.
- [x] Actual cold/default, cold/mirror, tampered-checksum and warm-build receipts.
  The final normal hosted run passes all 24 cases, six in each category. Raw
  log and retained final JAR hashes match. Earlier local official downloads
  remain failed; the later per-case ZIP retention fix has separate actual
  negative-case evidence and does not rewrite old artifacts.
- [x] One failed SDK suite leaves the other independent suites observable and
  the combined result failed. The same-source hosted fault-injection run
  fails only Windows SDK with exit 97; the other 13 gate shards pass. Actual
  jobs, current identity, required receipts and raw log hashes were verified.
- [x] Actual-head/source evidence inventory and requirement-by-requirement audit.
  The initial inventory preserves the original local dirty tree. Hosted
  evidence binds the two completed runs to `d73b5651`; the retention follow-up
  records its own source hashes and focused results.

Latest local SDK results are Python 72/72 and TypeScript 16/16 on both Windows
and the Ubuntu VM. Each OS also completed 100 repetitions of each of two
Native timeout cases, without changing the target timeout. The UI checks pass
38 contract, 30 unit and 29 distinct affected browser cases; the production
build and 107 distinct focused Java cases/Javadoc pass. These are scoped
local results. The hosted record adds complete Nightly evidence without
claiming an installed-product replay.

The three existing PR required-check names remain unchanged. The authorized
changes and runs are linked from [draft PR #100](https://github.com/Lotulune/Copperbench/pull/100).
No merge or publication is included. M1 player crafting/save-reopen remains unverified; M3 installed
package and unfamiliar-user work stays in the full PRD scope. Any game input
must use the isolated test VM, not the user's foreground desktop.
