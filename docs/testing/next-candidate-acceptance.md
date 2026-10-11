# Next candidate acceptance worksheet

Prepared 2026-10-11 (Japan time). Scope: REM-01 after the REM-02/03/06 source batch.
**Status: Windows Server 2025 and Ubuntu 24.04 cloud installed/API/build/delivery acceptance passed.**
See the [current cloud results and scope](remaining-cloud-acceptance-2026-10-12.md) and the
[historical local candidates](remaining-candidate-2026-10-11.md). The broader matrix below is a release checklist; partial source or installed coverage does not close a whole row.
This preparation does not change the public 0.1.4 releases or the historical candidate results.

## Freeze before starting

Use a reviewed source commit and its own required CI, M1 and platform candidate runs.
Record full commit/tree identities, every shipped SDK file's SHA-256, application JAR size/SHA-256,
and each package/launcher size/SHA-256 from the selected build artifacts.
Do not obtain the expected digest by observing whichever installed application happens to run.
Keep one manifest and run ID per candidate/platform/cache/network combination; replacement packages get new IDs.

Use this structure in the run's evidence directory:

```text
candidate-manifest.json     source commit/tree, CI run URLs, SDK/application/package hashes
environment.json            OS/session, Java/toolchain, cache and network observations
doctor.json                 actual running application identity
tasks/                     raw final task records and action/log evidence
delivery/                  manifest, accepted mod, original XML, receipt, application identity
result.json                passed/failed/unverified per row, classification, evidence references
```

The existing [installed-run harness](../../scripts/verify-agent-readiness-installed.py)
requires explicit `--candidate-sha256`, `--application-sha256`, `--source-commit`,
`--product-root`, `--candidate-package` and `--workspace-folder`.
Interactive bootstrap coverage also requires `--task-authorization`; cloud runs use `--workspace-template` to prepare a generated import fixture and explicitly do not claim bootstrap approval coverage.
Use `--help` for the remaining exact options. Its existing scenarios do not automatically cover every new row below.
Retain its actual receipts and add the new result/bundle checks in the same candidate run.

## Windows and Ubuntu matrix

Record each row independently on both platforms, retaining exact commands and raw results.

| Check | Required observation | Current status |
| --- | --- | --- |
| Launcher and doctor | Running application SHA matches the frozen application JAR; OS/cache/network conditions recorded | Passed on both cloud platforms |
| Discovery | Single-type item/recipe contract reads and legitimate CUSTOM references with differing internal/registry names | Read-only item/recipe discovery and same-name CUSTOM construction passed; distinct internal/registry import has source tests only |
| Build and protection | Normal build works; external/manual source conflicts are rejected and authored bytes preserved | Passed on both cloud platforms |
| Acceptance counts | 0/1, all-skipped/framework-only, 5/6 reject; 6/6 and 7/6 still require valid reports and no failures | Real five-case success and 5/6 refusal passed on both; broader count matrix covered by source tests |
| Versioned results | Native, MCP and TypeScript expose the same resultVersion 1 semantics; legacy task responses retain v1.0 shape | Installed Native passed; MCP/TypeScript/schema agreement covered by required CI |
| Language | Chinese/English show actual/required/missing; language changes preserve task/revision and issue no mutation | Source/UI checks passed; installed desktop interaction unverified |
| Freshness | Missing, stale, damaged XML retain distinct reasons; failed cases outrank count success | Installed stale input and JAR/XML tampering passed; remaining reason matrix covered by source tests |
| Delivery | Current export and reopen pass; stale input requires explicit historical export and retains that label | Passed on both cloud platforms |
| Integrity | JAR/XML/count/manifest tampering rejected; bundle rechecks offline in another clean directory | Passed on both cloud platforms, including unsafe path rejection and independent downloaded-bundle checks |
| Identity | Application identity in the bundle matches the accepted candidate; mod and application hashes remain separate | Passed on both cloud platforms |
| Client/gameplay | Affected player behavior and save/reenter steps, if needed, have screenshot/action evidence | Unverified; scope to be selected |

For offline verification use the [delivery verifier](../../scripts/verify-delivery-bundle.py).
For Minecraft interaction use the configured bridge and record passed/failed/unverified against actual screenshots;
navigation completion or Java compilation is not player acceptance.
Cancelled autonomous-agent and unfamiliar-user studies remain closed.

## Failure classification

| Category | Evidence required |
| --- | --- |
| `product` | Product behavior fails its contract with a valid harness and candidate |
| `harness` | Bad fixture, stale assertion, wrong selector or recording defect; preserve the failed run before fixing it |
| `infrastructure` | Dependency/network/runner/desktop prerequisite fails before the product outcome can be established |
| `user-stop` | User explicitly stops the run; unfinished checks stay unverified |

Do not infer a category from a red exit code alone. If unresolved, record the failure with classification `unclassified`.
Temporary DNS/network conditions stay attached to their run; success there is not a default-network guarantee.
