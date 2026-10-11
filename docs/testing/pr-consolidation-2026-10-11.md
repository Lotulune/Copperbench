# PR 98/99/101 consolidation

The integration starts from PR #101's `f1149915`, based on merged main
`da3157b5`, and merges the SDK/source-diagnostic fixes in `443cb90e`.
PRs [#98](https://github.com/Lotulune/Copperbench/pull/98) and
[#99](https://github.com/Lotulune/Copperbench/pull/99) have the same head,
`e408d0c3`; their older implementations conflict with the current main.
The combined implementation is reviewed and checked through
[PR #101](https://github.com/Lotulune/Copperbench/pull/101).

## Retained behavior and superseded proposals

| Earlier work | Current implementation |
| --- | --- |
| Partial item/recipe discovery | PR #100's complete versioned discovery and reference paging remain; #101 adds one-query discovery on advertised hosts |
| SDK missing-contract and invalid-response agreement | `NATIVE_FIELD_CONTRACT_UNAVAILABLE`, recovery details, raw envelopes and whitespace-name validation from the [alignment fix](sdk-contract-alignment-2026-10-11.md) |
| Literal diagnostic arguments | Python Native/MCP, TypeScript and UI share brace-preserving fixtures |
| Generation conflict location and source preview | Per-file bounded locations and safe captured Java previews, retaining ownership and source protection |
| Five-job Nightly proposal | The current [M2 independent gates](agent-readiness-m2.md) and strict aggregation remain |
| Chronometer-specific Native regression | The original branch and its evidence remain historical; the maintained product gate is [M1 real delivery](agent-readiness-m1.md), not an assertion that the two sample mods have identical gameplay |
| Protocol evaluation scope | Port the report/manifest clarification from `e408d0c3`, keeping legacy identifiers and declaring the actual [fixture-backed HTTP scope](protocol-contract-evals.md) |
| #101 delivery evidence | Keep minimum-count guidance, observed toolchain metadata and versioned portable JSON without extending the strict v1.0 API receipt |

The Native merge retains both paths: modern hosts query the requested type once;
legacy negotiation reads `result.data` and preserves the complete Core envelope
on error. Its regression now asserts that error-envelope identity explicitly.

An actual HTTP run found a stale assertion in the older protocol harness:
Python MCP reports `REVISION_CONFLICT` while preserving Core diagnostic
`WORKSPACE_REVISION_CONFLICT`. The harness now requires both independently;
regressions reject a wrong SDK code or missing/wrong Core diagnostic. It does
not weaken the rejection or change SDK error behavior.

## Observed local integration checks

| Check | Result |
| --- | --- |
| Complete Python SDK suite | 93 passed |
| Unchanged SDK tests extracted from `e408d0c3` | 45 passed against the integrated SDK |
| Java report, export and conflict-diagnostic classes | 27 passed across five classes, zero failures/errors/skips |
| Javadoc and production UI build/localization | Passed with the focused Gradle run |
| Protocol report/manifest regressions | 10 passed; includes the real-run revision-code regression |
| Protocol manifest verifier | 10 legacy cases, 16 explicitly named observed checks |
| Real loopback HTTP protocol harness | Workspace 9/9 and read-only 1/1; combined 10/10, zero failures |

Raw local evidence is under `build/pr101-integration/`. The first HTTP launch
hit this host's Gradle loopback limitation; running the same harness through a
hidden external PowerShell worker started the test host. That run then exposed
the stale revision-code assertion above. Both failures are retained separately
from the passing final run.

The CI-selection suite was also attempted locally: 20 cases passed and two
could not create newline-containing filenames on Windows. Its existing Linux
CI execution remains authoritative for those two filesystem cases. No tests
were skipped or rewritten to hide that platform limitation.

The earlier [alignment verification](sdk-contract-alignment-2026-10-11.md)
records the unchanged renderer/UI and source-protection checks. Final required
CI, Linux packaging and real M1 delivery must be read from the combined PR's
current head; earlier heads' green checks do not validate the merge.

No new release, installed-package acceptance or Minecraft player replay is
claimed. Both cancelled research work packages remain terminated.
