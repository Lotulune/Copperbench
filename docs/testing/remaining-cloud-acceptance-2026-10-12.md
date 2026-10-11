# REM-02/03/06 cloud installation acceptance

Recorded 2026-10-12 (Japan time). The user authorized committing the remaining batch and moved both Windows and Linux installation acceptance to cloud runners after local VM restore could not obtain enough memory.

The first passing replacement candidate source is `48cd994ac22b6a2bf6f0b30b13457250b0c59745`.
**Both cloud installation runs, real M1 and required CI passed for that candidate.** It includes the Windows history fix found by the previous frozen candidate; the old binaries and failed attempts remain separate evidence.

The later [Windows 11 native desktop acceptance](remaining-desktop-acceptance-2026-10-12.md) found and fixed GUI initialization and dark-menu defects. The initialization fix has a separately frozen passing cloud rerun below; the final CSS fix has native desktop and required-CI evidence. Cloud installed/API/build/delivery evidence remains distinct from desktop/player scope.

## Source and runs

| Evidence | Source | Run / result |
| --- | --- | --- |
| Original required checks | `e178ecd2` | [38111516667](https://github.com/Lotulune/Copperbench/actions/runs/38111516667): one stale Java assertion failed; other required jobs passed |
| Original real M1 and protocol | `e178ecd2` | [38111519854](https://github.com/Lotulune/Copperbench/actions/runs/38111519854): both jobs passed; five real business tests, seven negative exports and real reconnect independently checked |
| Updated assertion and fixture harness required checks | `93a34f7f` | [38112789129](https://github.com/Lotulune/Copperbench/actions/runs/38112789129): all required jobs passed; production files match `e178ecd2` |
| Replacement required checks, first attempt | `48cd994a` | [38175673497](https://github.com/Lotulune/Copperbench/actions/runs/38175673497): Windows new-history regression passed; old-history test fixture omitted its `info/` parent directory. Superseded after correcting the test input |
| Replacement required checks | `68bd891b` | [38175934941](https://github.com/Lotulune/Copperbench/actions/runs/38175934941): all required jobs passed; the only difference from candidate source is one test-directory preparation line |
| Replacement real M1 and protocol | `48cd994a` | [38175676593](https://github.com/Lotulune/Copperbench/actions/runs/38175676593): both jobs passed; raw JAR/XML, five business cases, seven negative export results and three process sessions independently checked |
| Replacement package/install acceptance | `48cd994a` | [38175670087](https://github.com/Lotulune/Copperbench/actions/runs/38175670087): Ubuntu 24.04 and Windows Server 2025 both passed |

The older Java assertion compared a portable receipt with the strict v1.0 API object without removing the new disk-only `applicationIdentitySha256` field. The corrected test independently checks that digest against the actual identity file and still rejects the field in the API object. The failed original run is not relabeled as passing.

The replacement M1 replay completed in 260.713 seconds; its protocol job passed 46 Java and 95 Python tests. Its final mod hash is `cbeb70e122aaa6d1a833b1e9776056c0f7b63abc7d16c5ac26fefe1eed08dd3e`. The all-skipped and zero-test negative gates use deliberately injected recovered records, as recorded by that harness; they are not claims that a real server produced those reports.

The final Windows filesystem job ran 50 cases: 49 passed, one skipped, zero failures/errors. Both new legacy-history regressions passed with the real writer lease held.
The full Java job ran 1,154 cases: 1,085 passed, 69 skipped, zero failures/errors, with Javadoc and independent Java-to-Python bundle verification passing. The full Chromium suite passed 285 cases; SDK, schema, production UI and MCP jobs also passed.

## Replacement candidate identity

Candidate source tree: `f1b5869ea5562f6462f6b5ffc6bc4dc437dfd68e`.
The [Ubuntu cloud manifest](candidate-48cd994a-ubuntu-cloud.json) and
[Windows cloud manifest](candidate-48cd994a-windows-cloud.json) record the package, application, launcher, SDK, schema, guide and verifier identities frozen before installation.
The [compact result record](candidate-48cd994a-cloud-results.json) retains actual task IDs, mod/XML/input hashes, application identities, timings and unverified scopes.

| Platform | Installed package SHA-256 | Application JAR SHA-256 | Result |
| --- | --- | --- | --- |
| Ubuntu 24.04 | `27b7d328893721a526f69d60ef1436890535efefa54e1a3e38413b2ba5f1e968` | `4d3d3f970141b7ea696c5a9797f5730945cc61d973aa33e1e0d135b7017d302b` | Passed; all 14 installed members matched; 78 independent evidence checks passed |
| Windows Server 2025 | `94d95ba6aa6ca0f500913139b0a7952db8b074ba0937844812f34f1586a7d6e7` | `5d5916aec08585a0992298decd834f6b8eadb8bcedae40c1d763a464b1f25f58` | Passed; all 14 installed members matched; 78 independent evidence checks passed |

The Ubuntu baseline completed in 217.066 seconds and the supplemental result/delivery checks in 62.184 seconds; Windows took 477.859 and 111.359 seconds respectively. Both platforms accepted mod hash `e08cacd0e7fea6464424a0ec9cef4ca84833fe521ec631202eacea8164836285`. Current and historical bundles preserve their distinct input-status labels and each platform's actual raw XML digest.

The exact accepted [Windows packages](https://github.com/Lotulune/Copperbench/actions/runs/38175670087/artifacts/11716226161) and
[Ubuntu packages](https://github.com/Lotulune/Copperbench/actions/runs/38175670087/artifacts/11715658632) are Actions artifacts retained for 14 days.
The [Windows evidence](https://github.com/Lotulune/Copperbench/actions/runs/38175670087/artifacts/11715369926) and
[Ubuntu evidence](https://github.com/Lotulune/Copperbench/actions/runs/38175670087/artifacts/11716162017) are retained for 30 days, and have also been downloaded into the task worktree. No public release is created.

## Desktop fix cloud follow-up

Native Windows 11 validation exposed a GUI initialization defect in `48cd994a`: base generation
rewrote pending Core inputs and caused a false source conflict. The fix is
`f1ee111234554462b4dcf75228374ab5aa313a03`, tree `de635456393759fdb359ea1bfff6a89f69a5188f`.
Its [required CI](https://github.com/Lotulune/Copperbench/actions/runs/38179860974),
[real M1/protocol](https://github.com/Lotulune/Copperbench/actions/runs/38179864313), and
[Windows/Ubuntu package installation](https://github.com/Lotulune/Copperbench/actions/runs/38179867767)
all passed. This is a new candidate; none of the older receipts is relabeled.

| Platform | Installer SHA-256 | Application JAR SHA-256 |
| --- | --- | --- |
| Windows Server 2025 | `8327975664b22967bf84656aefb2c460ba3abd1ec94e491635b43b4e36365457` | `1e114a93bf4ee63d18f5a86607f47946fd1df33cf3aa746c8bb9077aec84f685` |
| Ubuntu 24.04 | `d42516a5bef235b75ea0d64b5bb39e6104f8b7452abf6f916bd19e7280f573d2` | `27265c357d3859b0a965452f1293c0d3eccde3f5a6a964ef3727d4c064d62154` |

The [Windows manifest](candidate-f1ee1112-windows-cloud.json),
[Ubuntu manifest](candidate-f1ee1112-ubuntu-cloud.json), and
[compact results](candidate-f1ee1112-cloud-results.json) retain full identity, actual task IDs,
all 13 supplemental checks, current/historical JAR/XML/input hashes and timings.
Both installations matched all 14 frozen members. The downloaded evidence independently passed
78 checks per platform, including real report counts, offline bundle verification and rejection
of JAR/XML/count/path/manifest corruption. The audit was rerun on these new artifacts.

The exact [Windows packages](https://github.com/Lotulune/Copperbench/actions/runs/38179867767/artifacts/11717845688)
and [Ubuntu packages](https://github.com/Lotulune/Copperbench/actions/runs/38179867767/artifacts/11717546182)
are retained for 14 days; [Windows evidence](https://github.com/Lotulune/Copperbench/actions/runs/38179867767/artifacts/11717831198)
and [Ubuntu evidence](https://github.com/Lotulune/Copperbench/actions/runs/38179867767/artifacts/11717418729)
for 30 days. Evidence is also downloaded under `output/desktop-acceptance-20261012/cloud/`.
Caches and default runner networking match the recorded retained-cache conditions.

The final native desktop source `82d0dc34` adds only theme colors for native select options.
Its [required CI](https://github.com/Lotulune/Copperbench/actions/runs/38181079774) and final
Windows 11 GameTest/export passed; this record does not claim another cloud installation of
that CSS-only revision. Its local application hash differs from both cloud applications above.

## Preserved attempts and corrections

| Attempt | Observed result | Classification |
| --- | --- | --- |
| Local VM restore, 2026-10-11 | Both saved VMs could not allocate approximately 5 GiB; no guest installation ran | Infrastructure |
| [38111512321](https://github.com/Lotulune/Copperbench/actions/runs/38111512321), `e178ecd2` | Both packages built, installed and matched all 14 frozen member digests; asynchronous initial history writes invalidated the discovery comparison | Harness initialization boundary |
| [38112219341](https://github.com/Lotulune/Copperbench/actions/runs/38112219341), same package bytes | Ubuntu discovery passed, then initial build found a metadata-only fixture without Gradle base files; Windows history initialization timed out | Harness fixture; Windows cause unresolved at that attempt |
| [38112786549](https://github.com/Lotulune/Copperbench/actions/runs/38112786549), same package bytes | Ubuntu baseline and all 13 supplemental checks passed; 78 independent downloaded-evidence checks passed. Windows preparer exposed a real locked-file error in legacy history | Ubuntu passed; Windows product defect |

The first cloud candidate's installation hashes were:

| Platform | Installer SHA-256 | Application JAR SHA-256 |
| --- | --- | --- |
| Ubuntu 24.04 | `11d5d30b2d900ef7132feaeae9f36d085bfda8ce4b2fe66632f97784b2ee3d46` | `a8301bb6bd1f0febef203fbe2243504b5abc9e12d680127611e0bbdccc35f983` |
| Windows Server 2025 | `f44f29f8ee93d6a60dc1b78c871fbad900414e8b1df4a3ce31aeaf351c56b1bc` | `72c65f891b18d545a7764745558b509bd32c09fb30f870bc518cf3ba9810d346` |

The history defect was not waived: upstream legacy history attempted to stage `.copperbench/workspace.write.lock`, which Windows holds with a mandatory file lock. The fix excludes runtime metadata and the root Git pointer, preserves existing exclude rules, and removes previously tracked runtime entries from the index without deleting live bytes. The regression covers a new repository and an older index while the real writer lease is held, and runs in both Linux and Windows required checks.

The fixture correction uses the installed generator and its real workspace templates to prepare input. It neither creates a task grant nor simulates interactive approval. Initialization finishes before the strict discovery before/after comparison; no workspace files are omitted from that comparison except the established size/presence treatment of the live writer lease.

## Acceptance scope and retained evidence

The cloud runs execute real NSIS and Debian installation, use the installed launcher and bundled Python SDK, and compare running application identities to manifests frozen before installation. Builds and GameTests execute through the product; portable evidence is verified in another directory using the shipped stdlib verifier.

Both platforms passed normal build, controlled Java failure and repair, five packaged business tests, 5/6 refusal with shortfall one, process close/reopen, source-conflict protection, current/historical results and exports, application identity, and JAR/XML/count/path/manifest corruption rejection. Each platform passed 13 supplemental checks and 78 independent checks of the downloaded evidence, including byte digests and raw successful GameTest XML. These are check counts, not an agent success-rate metric.

The hosted OS versions are Ubuntu 24.04 and Windows Server 2025. Each run records bundled Java, cache preferences, effective Gradle home and default runner networking. These are retained-cache runs, not cold-cache measurements. No DNS or proxy workaround is applied.

This installed harness does not verify Windows 11 desktop interaction or installed UI language switching; those checks now have a separate [native desktop record](remaining-desktop-acceptance-2026-10-12.md). Separate installed MCP/TypeScript sessions, imported CUSTOM references with different internal/registry names, and Minecraft player/save-reenter behavior remain unverified. The same-source required checks cover the API/schema/SDK/UI result matrix separately. No bootstrap approval or autonomous-agent success rate is claimed.

Downloaded evidence is under `output/candidate-acceptance-20261011/cloud/` in the task worktree. Workflow artifacts retain installation stdout/stderr, fixture preparation, complete discovery inventories, task envelopes, process identities, failure diagnostics, portable JAR/XML/receipts, tampered copies and offline verifier output. Candidate packages are retained separately. The local independent audit recomputes evidence digests, checks task and application identity, reruns the verifier on the real bundles and rejects each altered copy.

At the last local restore attempt, both VMs were saved with no assigned memory; the cloud follow-up does not operate local VMs or close unrelated user applications. This work does not merge main or publish a release. REM-04/05 and the cancelled research studies remain outside the selected batch.
