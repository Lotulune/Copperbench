# Next candidate acceptance worksheet

Prepared 2026-10-11 (Japan time). Scope: REM-01 after the REM-02/03/06 source batch.
**Status: not run; no final application JAR or Windows/Ubuntu package is frozen by this worksheet.**
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
`--product-root`, `--candidate-package`, `--workspace-folder` and `--task-authorization`.
Use `--help` for the remaining exact options. Its existing scenarios do not automatically cover every new row below.
Retain its actual receipts and add the new result/bundle checks in the same candidate run.

## Windows and Ubuntu matrix

Record each row independently on both platforms, retaining exact commands and raw results.

| Check | Required observation | Current status |
| --- | --- | --- |
| Launcher and doctor | Running application SHA matches the frozen application JAR; OS/cache/network conditions recorded | Unverified |
| Discovery | Single-type item/recipe contract reads and legitimate CUSTOM references with differing internal/registry names | Unverified |
| Build and protection | Normal build works; external/manual source conflicts are rejected and authored bytes preserved | Unverified |
| Acceptance counts | 0/1, all-skipped/framework-only, 5/6 reject; 6/6 and 7/6 still require valid reports and no failures | Unverified |
| Versioned results | Native, MCP and TypeScript expose the same resultVersion 1 semantics; legacy task responses retain v1.0 shape | Unverified |
| Language | Chinese/English show actual/required/missing; language changes preserve task/revision and issue no mutation | Unverified |
| Freshness | Missing, stale, damaged XML retain distinct reasons; failed cases outrank count success | Unverified |
| Delivery | Current export and reopen pass; stale input requires explicit historical export and retains that label | Unverified |
| Integrity | JAR/XML/count/manifest tampering rejected; bundle rechecks offline in another clean directory | Unverified |
| Identity | Application identity in the bundle matches the accepted candidate; mod and application hashes remain separate | Unverified |
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
