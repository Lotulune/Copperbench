# M1 implementation and acceptance ledger

This stage follows [the 2026-10-09 PRD](agent-readiness-prd-2026-10-09.md).
Status: M1 completed locally on 2026-10-09. The whole discovery → controlled
failure → explicit repair → packaged-JAR acceptance → verified export → reopen
chain and its negative gates have current-source evidence in the
[verification record](../testing/agent-readiness-m1.md). Implementation preceded
the consolidated checks; failures and their focused reruns remain recorded.

## Required implementation

- [x] AR-01: versioned, read-only item/recipe discovery with available,
  not-exposed and unsupported states; unknown types and malformed responses
  retain stable errors.
- [x] Contracts share their field types, ranges, options, defaults and custom
  adapter rules with creation/edit validation. Publish JSON pointers,
  conditional requirements, reference discovery, generator field restrictions
  and consumable minimum examples. UI and SDK use the same Core projection.
- [x] Existing contracts and older Core/SDK combinations remain readable;
  discovery creates no element/file, advances no revision, and downloads nothing.
- [x] AR-03: retain shared planning/execution, bounded structured conflicts,
  link/path checks, input revalidation and byte preservation on rejection.
- [x] AR-05: real Core and Native SDK, temporary writable workspace, real Gradle,
  fixed sample sources, compile error → located diagnostic → explicit repair,
  counted independent packaged-JAR tests, exact verified export and reopen.
- [x] A reconnect check actually closes and replaces its transport/session.
- [x] Negative gates reject insufficient tests, all-skipped tests, changed
  source, modified JAR/report, and historical evidence for different inputs.
  Historical export stays explicitly historical and cannot waive test validity.
- [x] Protocol conformance and real delivery checks are separately named and
  runnable in CI without changing the three existing required check names.
- [x] Evidence records the product/source delta, fixture identity, OS, Java,
  loader/API/generator, cache policy, task IDs, effective test counts, report/JAR
  hashes and current-input checks. Preserve failure evidence.

## Required verification after implementation

- [x] Schema/SDK compatibility and negative input tests, including example
  consumption rather than JSON-shape-only assertions.
- [x] Eight supported Java tracks: item/recipe discovery, creation, persistence,
  generation and reopen; distinguish adapter/template results from real builds.
- [x] Fabric 1.21.1 real headless/packaged-server delivery and all negative gates.
- [x] Relevant source-protection regression, complete Python SDK checks,
  TypeScript SDK checks, UI-Core validation, UI build and public API Javadoc.
- [x] Documentation/link checks and requirement-by-requirement completion audit.

## User-deferred additional client layer

The test client launched, but no gameplay input occurred. The user explicitly
deferred desktop focus/input while using the computer. Actual crafting and
client save/restart/reopen are unverified and remain separate from the completed
M1 server gate. This exception records the user's direction, not a passing
client result. The protocol/delivery CI definitions have not been dispatched.

M2 environment/Nightly/wrapper work and M3 installed-package/external-user work
remain separate milestones. No EULA acceptance, authorization issuance, commit,
push, merge or release is implied by this ledger.
