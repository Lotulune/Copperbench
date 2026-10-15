# Copperbench 0.1.6 release preparation

The owner explicitly requested pushing and publishing the optimization work as a new installer version on 2026-10-15. This record describes source preparation. It does not assert that final main CI, release packaging, Linux candidate authorization or publication has completed.

## Source integration

- Integration base: latest main `bc2a512ca07b8c38fe1ff3140b3954563925bc6a`, which already includes published Windows/Linux 0.1.5 and the later editor-schema, field-discovery, doctor, preview and version-copy work.
- The optimization work originated from the older `b1a69d244ab7337608cc302826ed55d4a707f37f` checkout. Its C1–C4, Linux run3 and later review-fix evidence retain those exact source/artifact identities.
- Integration keeps the main branch's existing capabilities and applies the optimization's first-creation, draft-recovery, diagnostic, delivery, SDK and contributor-tool changes.
- Product configuration and release-owned manifest fixtures are prepared for 0.1.6. The approved `ready` declaration records the authorized release target; it is not a successful validation or public-release receipt.
- The [previous 0.1.5 declarations](../../evidence/maintenance/2026-10-15/publication-0.1.6/previous-release-state.json) preserve their source, timestamps, workflow results and proof paths. Historical gates, Beta provenance, the product snapshot and the Linux support record retain their original scope.

## Included review-fix scope

The [optimization report](optimization-2026-10-15.md#独立复审后的13项源码补修本轮) records thirteen confirmed findings and their earlier checks:

| Findings | Integrated behavior |
| --- | --- |
| ONB-REVIEW-01 / 02 / 03 | Declared Gradle archive selection, protected delivery destinations and late first-guide state |
| REC-F01 / 02 / 03 | Unknown save results, read-only/removed field drafts and revision-safe reread |
| REC-F04 / 05 | Persisted element identity and per-diagnostic source navigation |
| NATIVE-CLOSE-01 / AD-01 | Native writer/socket shutdown and actual CRLF/UTF-8 response accounting |
| PERF-F01 / 02 / AD-02 | Source identity filters, independent timing comparisons and real Node summary parsing |

The earlier affected checks, their skipped case, initial failures and necessary reruns remain in that report. They were run before this integration onto latest main. They do not become a fresh test pass for the merged implementation or final 0.1.6 binaries.

## Local integration preflight

The coordinated local preflight passed the affected checks after integration onto the main baseline. The [portable validation record](../../evidence/maintenance/2026-10-15/publication-0.1.6/local-integration-validation.json) retains each check's actual source snapshot, receipt/log hashes, the compiled application JAR identity and the original failed attempts.

| Check | Local result |
| --- | --- |
| Selected Java suites | 111 cases: 110 passed, 1 existing DataPack real-build opt-in case skipped; 0 failures/errors |
| Affected headless Chromium specs | 105 passed across ten specs |
| Python SDK | 141 passed |
| UI unit / UI-Core contract | 68 / 50 passed |
| TypeScript SDK | 33 passed |
| Node tooling / contributor planner | 32 / 17 passed |
| Production UI, application JAR and Javadoc | Passed; compiled JAR is a local 0.1.6 artifact, not a published distribution |
| Repository Markdown links and product status | Passed before this report update |
| Documentation packaging configuration | Gradle configuration passed after adding the missing offline Markdown routes; final package contents remain to be verified |

The first Java attempt stopped on an unused TypeScript binding; the first Python attempt exposed outdated merged fixtures. Their failures and hashes remain in the evidence. Successful checks retain their individual source identities rather than being described as one full rerun after every correction. This preflight did not add native/JCEF, graphical installer, player-controlled Minecraft or same-world reopening acceptance.

## Current evidence state

| Item | Preparation state |
| --- | --- |
| Final integration source commit | Not yet assigned; no invented commit or source digest |
| Local integration preflight | Passed within the scope above |
| Protected PR/main required CI | Pending |
| Windows signed tag, packaging and public assets | Pending |
| Linux successful main-push candidate and frozen identity | Pending |
| Linux exact-candidate hashes, provenance and authorization | Pending; existing 0.1.5 authorization is unchanged |
| Final 0.1.6 installed-product and gameplay acceptance | Not performed by this preparation task |
| Current public downloads | Windows `v0.1.5` and Linux `v0.1.5-linux-stable` |

Protected CI, immutable candidate/package checks and publication remain separate from the local preflight. No fresh final-package installed-product or gameplay acceptance is claimed; historical C4 and Linux run3 results retain their original identities.

## Publication sequence

1. Finish implementation and applicable verification, then merge the versioned source through the required PR checks. Retain the successful main CI and the Stage 15 candidate generated by that exact main push.
2. Sign and publish the Windows `v0.1.6` tag on latest main. Keep main unchanged through the workflow's source, draft and publication checks.
3. Download the exact Linux candidate, verify metadata, every frozen asset/hash and provenance, and retain authentic run/jobs receipts. Finalize a dedicated immutable candidate report before hashing it.
4. After Windows publication, merge the candidate-specific Linux authorization, exact maintenance tuple and tests. Only the existing release-control source-delta allowlist may differ from the candidate; no build-affecting changes may be hidden in promotion.
5. Sign `v0.1.6-linux-stable` on the new latest main and dispatch the protected Linux workflow with that tag as its ref. Verify exact draft downloads before publication.
6. Record actual public metadata, workflow conclusions and asset proof; update [current downloads](../releases/current.md) only after the releases are public.

Neither prior installed/gameplay evidence nor the old Linux maintenance exception authorizes a claim of fresh 0.1.6 acceptance. The requested release keeps exact source, candidate and publication verification separate.
