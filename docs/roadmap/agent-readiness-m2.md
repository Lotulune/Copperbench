# M2: continuous verification and read-only environment discovery

This work follows the [2026-10-09 PRD](agent-readiness-prd-2026-10-09.md).
Status: implementation complete; consolidated local validation executed on
2026-10-09, with two exit gates still open: official-source cold/warm wrapper
downloads and hosted Nightly fault injection. M2 is not closed. The results
apply to `436a41761d885fa5ad0d76500e2257438137d75f` plus this uncommitted working
tree, not a published package. See the [validation record](../testing/agent-readiness-m2.md).

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
- [ ] Actual cold/default, cold/mirror, tampered-checksum and warm-build receipts.
  Mirror cold, tampered-checksum rejection and mirror warm each pass 6/6.
  Official cold fails 6/6 on this network; its six dependent warm cases are
  explicitly not attempted and fail the combined gate.
- [ ] One failed SDK suite leaves the other independent suites observable and
  the combined result failed. Hosted workflow execution is reported separately
  from local orchestration checks. The local summary/receipt regressions pass;
  hosted execution still requires push/dispatch authorization.
- [x] Actual-head/source evidence inventory and requirement-by-requirement audit.
  The inventory binds this local dirty tree and retained receipts; it does not
  claim a new committed or hosted acceptance result.

Latest local SDK results are Python 72/72 and TypeScript 16/16 on both Windows
and the Ubuntu VM. Each OS also completed 100 repetitions of each of two
Native timeout cases, without changing the target timeout. The UI checks pass
38 contract, 30 unit and 29 distinct affected browser cases; the production
build and 107 distinct focused Java cases/Javadoc pass. These are scoped
results, not a full hosted Nightly or installed-product replay.

The three existing PR required-check names remain unchanged. Commits, pushes,
workflow dispatch, installer releases and external messages are not implied by
this ledger. M1 player crafting/save-reopen remains unverified; M3 installed
package and unfamiliar-user work stays in the full PRD scope. Any game input
must use the isolated test VM, not the user's foreground desktop.
