# Copperbench 0.1.6 integration and publication

The owner requested pushing and publishing the optimization release on 2026-10-15. This record separates local preflight, binary source A, exact Linux candidate, promotion source and public outcomes. Historical installed/gameplay evidence keeps its original identity.

## Source integration

- Integration base: main commit `bc2a512ca07b8c38fe1ff3140b3954563925bc6a` at the start of source integration, already including published Windows/Linux 0.1.5 and the later editor-schema, field-discovery, doctor, preview and version-copy work.
- The optimization work originated from the older `b1a69d244ab7337608cc302826ed55d4a707f37f` checkout. Its C1–C4, Linux run3 and later review-fix evidence retain those exact source/artifact identities.
- Integration keeps the main branch's existing capabilities and applies the optimization's first-creation, draft-recovery, diagnostic, delivery, SDK and contributor-tool changes.
- Product configuration and release-owned manifest fixtures were prepared for 0.1.6. The approved `ready` declaration at A recorded the authorized release target; the subsequent public receipts below establish publication.
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
| Documentation packaging | Configuration passed locally; actual Windows ZIP and Linux portable archives each passed all 23 A-bound entry checks |

The first Java attempt stopped on an unused TypeScript binding; the first Python attempt exposed outdated merged fixtures. Their failures and hashes remain in the evidence. Successful checks retain their individual source identities rather than being described as one full rerun after every correction. This preflight did not add native/JCEF, graphical installer, player-controlled Minecraft or same-world reopening acceptance.

## Current evidence state

| Item | Recorded state |
| --- | --- |
| Product/binary source A | `9e3dfdeab1bc19c099e739bcda14940d9a6fee30` |
| Local integration preflight | Passed within the scope above |
| Protected source CI | Main A run 38748273169 passed; PR #114 authorization contracts passed, and promotion follows its separately recorded source |
| Windows publication | [Public stable 0.1.6](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.6), 2026-10-15T15:35:41Z; ten assets and source A |
| Linux frozen candidate | Main push 38748273030 passed on A; candidate `sha256:ea64a9f7126e7cab62343780704837b62ffbd93010ae2603d0d2992444c508d1` |
| Linux candidate verification | Eight byte hashes/provenance subjects and 23 portable entries passed; [immutable report](maintenance-linux-0.1.6.md) and exact authorization are unchanged |
| Final 0.1.6 installed-product and gameplay acceptance | Not repeated; historical records retain their original scope |
| Linux publication | https://github.com/Lotulune/Copperbench/releases/tag/v0.1.6-linux-stable, published 2026-10-15T16:17:00Z; source b9383631ec6e800aa327f9f583fc48213a969648, run 38766657314, 9 assets |

Protected CI, immutable candidate/package checks and publication remain separate from the local preflight. No fresh final-package installed-product or gameplay acceptance is claimed; historical C4 and Linux run3 results retain their original identities.

## Verified public records

Windows `v0.1.6` was published by run `38748647623` attempt 2 at `2026-10-15T15:35:41Z`, release ID `413978638`, from A. Attempt 1 passed tests and packaging but failed fetching a Sigstore Rekor entry; its raw failure remains archived. The [public verification](../../evidence/maintenance/2026-10-15/publication-0.1.6/windows/windows-public-verification.json) matched nine checksum-list entries against API digests, separately byte-checked SHA256SUMS and the other two downloaded metadata files, and verified all ten source-A/attempt-2 provenance subjects. The public ZIP, `602441329` bytes / SHA-256 `87e6295c6eab9de371f1c8d6ac3a1c61aa4b3810ed26394045fbb87cef827b90`, was [downloaded and hashed](../../evidence/maintenance/2026-10-15/publication-0.1.6/windows/public-zip-download.json); [all 23 selected ZIP entries](../../evidence/maintenance/2026-10-15/publication-0.1.6/windows/archive-entry-verification.json) matched A. This does not claim complete re-downloads of EXE/MSIX or new installed/gameplay acceptance.

Linux `v0.1.6-linux-stable` public release ID `414016224` was published at `2026-10-15T16:17:00Z` by run `38766657314` on promotion source `b9383631ec6e800aa327f9f583fc48213a969648`; result `success`. [Public asset verification](../../evidence/maintenance/2026-10-15/publication-0.1.6/linux/linux-public-verification.json) matched all nine API sizes/digests to the eight frozen candidate files and exact approved authorization. The downloaded public metadata, checksum list and authorization were byte-identical to their originals; the protected promotion also verified frozen provenance and compared all nine downloaded draft assets byte-for-byte. Binary source stays A and main-push candidate run `38748273030`; the [immutable candidate report](maintenance-linux-0.1.6.md) and `maintenance-ci-0.1.6` approval are not rewritten. Full installed acceptance remains `not-repeated`, formal support claim `false`.

B's local promotion checks passed all five checks, including 30 authorization contracts and the exact metadata/13-step/source-delta/link checks, on implementation commit `2bb9544833ff87bf9e9d18f4e73f884b2d79c59f` / Git tree `248060a6eee0aff2c87baaacf583762b859ef047`. PR [#114](https://github.com/Lotulune/Copperbench/pull/114) and the final promotion source remain distinct identities. Later documentation C does not rebuild or alter A's packages.

## Publication procedure

1. Finish implementation and applicable verification, then merge the versioned source through the required PR checks. Retain the successful main CI and the Stage 15 candidate generated by that exact main push.
2. Sign and publish the Windows `v0.1.6` tag on latest main. Keep main unchanged through the workflow's source, draft and publication checks.
3. Download the exact Linux candidate, verify metadata, every frozen asset/hash and provenance, and retain authentic run/jobs receipts. Finalize a dedicated immutable candidate report before hashing it.
4. After Windows publication, merge the candidate-specific Linux authorization, exact maintenance tuple and tests. Only the existing release-control source-delta allowlist may differ from the candidate; no build-affecting changes may be hidden in promotion.
5. Sign `v0.1.6-linux-stable` on the new latest main and dispatch the protected Linux workflow with that tag as its ref. Verify exact draft downloads before publication.
6. Record actual public metadata, workflow conclusions and asset proof; update [current downloads](../releases/current.md) only after the releases are public.

Neither prior installed/gameplay evidence nor the old Linux maintenance exception authorizes a claim of fresh 0.1.6 acceptance. The requested release keeps exact source, candidate and publication verification separate.
