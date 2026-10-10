# M1 verification record

Implementation is being validated against the
[M1 ledger](../roadmap/agent-readiness-m1.md) and
[PRD](../roadmap/agent-readiness-prd-2026-10-09.md).
The baseline is `436a41761d885fa5ad0d76500e2257438137d75f` plus the local
`codex/generation-preflight` delta. Results describe that source build, not a
released package. Prior AR-03 results are regression background.

## Implemented scope

- Shared item/recipe defaults, input shapes and custom adapter metadata.
- Versioned per-type discovery and paged installed-generator reference options.
- Environment compatibility and UI editor projection use the same Core data.
- Existing generation preflight/source protection remains in the phase checks.
- Verified export reparses raw acceptance reports and compares counts/cases,
  minimum tests, mode, process result and hashes before copying.
- [Fixed real-delivery fixture](../../examples/agent-readiness/m1-delivery/README.md)
  and Native SDK runner include compile failure/repair, counted packaged tests,
  export, genuine reconnect and negative gates.
- [Separate CI jobs](../../.github/workflows/agent-readiness-m1.yml) distinguish
  protocol/adapter evidence from real delivery. They run independently on manual
  dispatch or PR changes to the gate definition, runner or fixed fixture, and do
  not rename any existing required check. PR runs check out the exact head commit.

## CI entry follow-up

On 2026-10-11, manual dispatch against `codex/generation-preflight` returned HTTP
404 because this workflow was not registered on the default branch. The workflow
now also runs for PR changes to its own definition and dedicated gate inputs,
allowing it to be verified before merging. General product changes still use the
existing required/Nightly checks; the M1 manual entry remains available once
registered. Protocol and delivery are independent jobs, and their Gradle/Python
console logs are retained alongside structured evidence even on failure. Piped
logging uses `pipefail`, so a successful log write cannot hide a failed command.
This trigger correction does not itself establish a successful delivery run;
hosted results remain separately bound to their tested commit on
[draft PR #100](https://github.com/Lotulune/Copperbench/pull/100).

The first [PR execution](https://github.com/Lotulune/Copperbench/actions/runs/38075315759)
checked out `a07d8995` with an empty source delta. Protocol/adapter verification
passed 36 Java cases and all 72 Python SDK tests; schema evidence validation also
passed. Real delivery failed before its first session or task: the Native pipe
closed after 0.772 seconds. Its captured launcher omitted the Linux candidate
property required by `SupportedPlatform`, which the packaged Linux shell already
sets. The source harness now supplies that same property only on Linux x64. It
does not broaden platform support or enable the general unsupported-OS override.
The workflow also retains the product's XDG state logs, which the earlier
repository-local `logs/` artifact path missed. Original failure artifacts remain
available; a later run must establish the corrected real-delivery result.

## Current-source results

| Layer/check | Observed result |
| --- | --- |
| Focused Java checks, including source protection and adapter contracts | 127 distinct cases, latest results 127 passed / 0 failed / 0 skipped |
| Actual eight-track item/recipe envelopes, minimum examples and defaults | 16 envelopes and 832 values validated |
| Python SDK | 67 passed |
| TypeScript SDK | 14 passed |
| UI-Core schemas | 37 passed |
| UI logic/localization | 28 passed |
| UI production build and public API Javadoc | Passed |
| Real Fabric delivery | Passed: build → controlled failure → explicit repair → five packaged-JAR tests → export → genuine reconnect; all negative gates rejected |
| Final current-input acceptance/export/reopen | Passed again after the controlled mutations |
| Client observations | Unverified; user deferred desktop focus/input while using the computer |

The Java counts combine the latest result for each testcase across retained
focused runs, not a claim that the first selection passed. The first selection
had eight failures because the new examples used uppercase internal names.
The examples now use `discovery_item` and `discovery_recipe`, and publish the
same name pattern as creation. The adapter regressions consume those examples.
An initial AJV strict-mode error and missing Chinese diagnostic translation were
also corrected before the supporting checks passed.

The first real delivery exposed a product mismatch: native recipe definitions
use `recipeSlots`/`recipeReturnStack`, while the task generator's older validation
expected `pattern`/`result`. Native item/recipe validation now shares Core field
types, conditions, defaults and aliases; legacy projection-only workspaces keep
their existing behavior. Nine-slot and invalid-reference regression cases pass.
The next real build succeeded, then its runner stopped because it expected a
whole `/sourceFingerprints` field instead of the editor's per-file paths. The
runner now uses the returned `/sourceFingerprints/$primary` patch path.

Machine-readable local summaries are `build/reports/m1-java-verification.json`
and `build/reports/m1-supporting-results.json`. Original Java XML is retained in
`build/m1-first-java-results`, `build/m1-discovery-java-results` and
`build/m1-generation-java-results`. Failed real-delivery evidence is retained in
`build/reports/m1-delivery/delivery-4616973346773951438` and
`build/reports/m1-delivery/delivery-15060307772129535807`.

## Environment and reproduction

Local Windows 11 x64, product JBR 25.0.3 with JCEF, Python 3.13.14 and Node
24.19.0. Root Gradle is 9.6.0; the Fabric 1.21.1 workspace uses Gradle 9.7.0,
Loom 1.17.19, Loader 0.19.3, Fabric API `0.116.15+1.21.1` and generator
`0.116.15/5`. Minecraft uses Temurin `21.0.12+8`; this executable was queried
separately from the product JBR. Existing dependency caches are retained.
The delivery CI job explicitly locates Java 21 for Minecraft before setting up
JBR 25 for the product. Its Linux workflow has not been executed locally.

```powershell
python -m unittest discover -s sdk/python -p 'test_*.py'
npm run test:sdk --prefix ui-shell
npm test --prefix ui-core
npm test --prefix ui-shell
node scripts/verify-field-contract-evidence.mjs
pwsh -NoProfile -File scripts/run-gradle-external.ps1 --no-daemon runAgentReadinessDelivery
node scripts/verify-markdown-links.mjs
```

On Linux, the CI entry is `xvfb-run -a ./gradlew --no-daemon
runAgentReadinessDelivery -PnativePythonExecutable=python`. The
[workflow](../../.github/workflows/agent-readiness-m1.yml) gives its separate
protocol selection. Source-level and real-delivery results remain distinct.

## Evidence interpretation

The successful real-delivery run is
`build/reports/m1-delivery/delivery-12642896026605627169`, with complete external
log `.tmp/gradle-external/70cfec6aca454a6ebfc9d4901f95544f.log`. The Native runner
took 1,003.504 seconds; its enclosing Gradle task completed in 17m 19s.

| Observation | Task or receipt |
| --- | --- |
| Initial build | `c2d272f2-0b89-4602-a8b5-1311c8b841c6` |
| Intended compile failure, `recovery_probe.java` line 8 | `1cbdadae-8d65-42f3-b917-421410a03997`, `JAVA_COMPILE_ERROR` |
| Explicit repair and rebuild | `b973554e-51c6-44c4-b595-7a3685cef923` |
| Initial packaged acceptance, 5 passed / 0 failed / 0 skipped | `7d0355e4-b091-40f7-89b9-f569b129f386` |
| Initial verified export | `0ebe343e-1e09-4bf0-9ef1-b67f9e733fc2` |
| Real reconnect | PID 21672 exited 0; PID 35676 reopened revision 5 and restored the acceptance task |
| Export after reconnect | `a4b97d55-8dc0-432e-972a-88cf0d8c8c4d` |
| Final current acceptance, 5 passed / 0 failed / 0 skipped | `d6d749e2-dfbd-48ec-ade6-00ff5c18755e` |
| Final current export / export after another reopen | `145491a6-a935-4db4-bc26-452fb4afb0c2` / `9fa932c6-240f-480e-b07c-03c654ff9845` |

The last reopen used PID 12036 at revision 7. Final JAR SHA-256 is
`cbeb70e122aaa6d1a833b1e9776056c0f7b63abc7d16c5ac26fefe1eed08dd3e`;
final XML SHA-256 is
`5a50ac89e0d6d42380d05e4d450b87e7b034ab1e8fb3537d018b4d9ec698bb74`.
Both were recalculated from the copied delivery bytes. Final input SHA-256 is
`179224422422f6b8109e8925f8b2ddc8a85dcc2f7b95bc2e71865b5e6ba5fdde`.

Negative receipts retain distinct codes: actual five-case run with a six-case
minimum → `GAMETEST_NO_TESTS`; exporting that task →
`VERIFIED_ARTIFACT_UNAVAILABLE`; changed JAR/report →
`VERIFIED_EVIDENCE_CHANGED`; all-skipped/zero-case recovered records →
`VERIFIED_ACCEPTANCE_INVALID`; changed source/current export after historical
export → `VERIFICATION_INPUT_CHANGED`. Explicit historical export succeeds
only with `passed_historical_input`. None is counted as current acceptance.

`result.json` records every task ID, source identity, fixture hashes and resolved
environment. `source.patch` plus `product-source/untracked` preserves the
executed code delta; `source-and-artifact-audit.json` checks that runtime source
did not change while acceptance ran, records later reporting-only edits and
rechecks fixture and final artifact hashes. This is a local uncommitted source
identity; it is not a new product commit or package release.

Adapter/template tests consume the public minimum examples on all eight Java
tracks and check discovery does not alter files/revision. These are not eight
real Gradle builds. The product delivery gate runs Fabric 1.21.1 using the real
Core, Native SDK, Gradle and separate packaged-JAR host. A warmed cache is
explicitly recorded and cannot count as cold-cache verification.

Recovered-record negative injections are distinguished from actual server runs.
Installer, other OS delivery and unfamiliar-user validation belong to M2/M3.

## Client observation deferred by the user

The real Native `run_client` task `796348d9-3164-4494-bf88-b89cd0b15a8b`
started Minecraft 1.21.1 with Java 21, client PID 39592 and the exact delivery
workspace's `run` directory. Its Native lifecycle process (PID 31872) stayed
alive while the client ran. Local launch receipts are under
`output/minecraft-validation/m1-delivery-20261009/launch-1`.

Minecraft Control Bridge 0.3.0 reported a visible but unfocused target. Session
`e9b1ed2f-8a05-48be-922a-7ec5c35ca280` was detached before focus recovery; it
contains no gameplay verdict. The computer-use recovery could not supply a
reliable refreshed target screenshot. Its click was rejected with
`unknown screenshotId screenshot-0`; no Minecraft navigation, chat command,
crafting or inventory input was performed. Before any further recovery, the
user explicitly requested no control of desktop focus while using the computer.
Desktop work stopped immediately. A later background check observed the client
and Native launcher had exited: the game logged `Stopping!`, Gradle succeeded,
and `run_client` reached `succeeded` at 12:40:30 Japan time, with no diagnostics.
No agent quit input was sent. The lifecycle receipt elapsed time is 375.789 s.
This establishes observed normal shutdown, not player persistence.

The incomplete desktop attempt used five Minecraft MCP calls (doctor, two
window listings, attach and detach), zero `mc_step` batches, zero Minecraft
input/capture milliseconds and zero Jev calls. Computer-use focus recovery had
four calls, two returned captures and one rejected screenshot-ID click; no
reliable capture/input timing is supplied by those receipts. No speed comparison
is inferred. Machine-readable attempt status and copied bridge receipts are in
`output/minecraft-validation/m1-delivery-20261009`.

Actual crafting, observed stack limits and a complete client save/restart/reopen
remain **unverified**. Item serialization in the five server tests does not fill
this player-observation gap. This user-deferred extra client layer does not change
the completed M1 headless/packaged-server exit condition; M3 player and installed
acceptance remains open. No desktop retry or follow-up automation was scheduled.
