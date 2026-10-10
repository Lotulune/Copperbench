# M3 installed-product acceptance record

Status: resumed at the user's request on **2026-10-10 JST**. Both installed
platforms now pass packaged GameTest, workspace UI checks, actual crafting and
full-client persistence. Windows gameplay used guest Mesa and an explicitly
authorized guest US keyboard. M3 is not complete. See the
[resumed results and repairs](agent-readiness-m3-resume-2026-10-10.md) for new
attempts, task IDs, candidate identity and retained failures. No host desktop
focus or VMConnect window was used; the VMs run serially.
The [fixed task card](agent-readiness-m3-task-card.md) and
[installed replay](../../scripts/verify-agent-readiness-installed.py) remain the
entry points for resuming this [PRD milestone](../roadmap/agent-readiness-prd-2026-10-09.md).

## Frozen corrected candidates

The Windows candidate was built from
`37fcb5b4ad228c3830cda3394eaf8708d57910a8`. The
[Linux candidate workflow](https://github.com/Lotulune/Copperbench/actions/runs/37929515247)
used PR merge `feee3cbb66996971c1a3e3f94566b51a759ecab1`.
GitHub commit metadata confirms the same Git tree for both:
`c71f922730c01cf54720e00d5626cd7bed098799`.
These private development candidates retain version 0.1.4; none was published.
Linux metadata retains `formalSupportClaim=false`.

| Asset | SHA-256 |
| --- | --- |
| Windows ZIP | `688c369625e82dd7bb3b77c119af54a74497aecaac7a47e7153c75d543934f31` |
| Windows EXE installer | `186bda57bde56c76950cad8950b9786a1d014c0f850b8587fa7cbaa295f8aecd` |
| Installed Windows application JAR | `611dc60fa3f43bedb2cee76292edc8fe2add84e74af37b5fc103ce7ec3a59d60` |
| Linux deb | `08093b3a3f5af27cfbcd04a8127aa2fa2fcef0bdfdfe1aac5717b4b2f86bd115` |
| Linux portable archive | `efff6b2e9c6f562bc4a98f4e897435976f3541a0275dc1b40d2fc554cbd8229a` |

The complete Linux artifact ZIP matches GitHub's SHA-256
`1b22b866f71b4b1d7557a4a43114ea52b96e026bdd2a12cdce544bb99752e579`.
Its deb, portable archive, SBOM and manifest passed the candidate metadata
validator. Interrupted transfers and bounded range retries are recorded; no
partial archive was installed. The corrected deb was installed successfully in
the resumed run; its application hash is recorded in the resumed results.
Later task-harness and documentation edits do not change these frozen binaries.

All three required PR checks passed on `37fcb5b4`: Java/Javadoc, UI
contract/build/smoke and MCP conformance
([run 37929515248](https://github.com/Lotulune/Copperbench/actions/runs/37929515248)).
The Windows packaging run also executed the full production UI build and NSIS
installer task. Hosted candidate smoke is separate from installed GNOME and
player acceptance.

## Offline-setting correction

The earlier installed Ubuntu attempt exposed a setting mismatch: workspace
creation honored the offline preference, while managed Native SDK builds still
attempted network requests. The shared
[process runner](../../src/main/java/dev/copperbench/generator/fabric/Fabric1211ProcessRunner.java)
now reads the setting on each invocation and supplies `--offline`, preserving
explicit `--offline` or `-o` without duplication. It retains the existing behavior
when preferences have not been initialized.

The [real subprocess regression](../../src/test/java/dev/copperbench/generator/fabric/Fabric1211ProcessRunnerTest.java)
failed against the old installed Linux application because the flag was absent.
All 12 runner tests then passed with the corrected source class on Linux and,
separately, against the corrected installed Windows application. No skips or
failures occurred in those passing runs. Local Gradle compilation passed.
Earlier host Windows attempts stopped at Java's `CreateProcess error=5` when
starting `cmd.exe`; this did not recur in the fresh Windows guest.

## Earlier Windows installed observations, 2026-10-09

A new Hyper-V guest runs Windows 11 Pro x64, build 26200. The verified installer
completed with exit 0 under `C:/Copperbench-M3`. The welcome window, mirror choice
and Gradle preferences were operated through guest-only Hyper-V input and
screenshots. Offline mode and mirrors were saved through the actual UI and then
read back. The product's authorization dialog approved create/edit/build/test/
run_client under `C:/M3/workspaces`, without accepting a dedicated-server EULA.
The welcome process closed normally with exit 0.

The harness uses the installed Python SDK. A copied local CPython 3.13.14 runtime
and standard library are test prerequisites; they are not part of the product.
Dependencies were seeded before the task, so this is not a cold-cache or clean-
tooling gate. Workspaces, outputs and harness hashes are distinct per attempt.

| Attempt | Observed result | Elapsed time |
| --- | --- | --- |
| Windows run-001 | Creation passed in 166.97 s and doctor passed; the harness failed reading the live exclusive writer lock | 186.82 s |
| Windows run-002 | Creation passed in 93.45 s; doctor, discovery, element creation, preflight, initial build, expected compile failure and explicit repair/build passed; packaged acceptance failed | 822.87 s |

The inventory helper now records the writer lease's presence and size, matching
the Core tests, while hashing every other workspace file. The second attempt
exercised that correction. Its harness SHA-256 is
`2cc0728c826a4343ccab3489005b89447527698d398bc162e7f6e31f68bc08e7`.
At that point the conflict fixture queued a managed edit before the external
source change, but neither earlier replay reached it. The resumed run exposed
and corrected its retained-cache prerequisite, as recorded separately.

Windows run-002 task identities:

| Task | ID | Terminal state |
| --- | --- | --- |
| Initial build | `1e6d5dd7-38b9-4472-be92-79a46cbb3637` | succeeded |
| Injected compile error | `aac307b6-d286-41a1-9fff-c0582ba55bac` | failed as expected; file, line and missing symbol verified |
| Repaired build | `89d83381-9f97-4a08-aeee-e02595c2dfb0` | succeeded |
| Packaged acceptance | `ba6c4940-7f5b-4bb8-9c2b-c83bbe3eb849` | failed; `GAMETEST_PROCESS_EXITED` |

### Memory failure and operator interruption

The independent test JVM loaded the declared mod and then logged
`OutOfMemoryError: Java heap space`. `jcmd VM.flags` measured
`MaxHeapSize=503316480` (480 MiB). The guest reported approximately 1.1 GiB of
visible physical memory, while the host had less than 0.5 GiB free. Hyper-V
reported a memory warning: this VM's dynamic configuration allowed a 1 GiB
minimum despite its 4 GiB startup and 5 GiB maximum.

The test JVM remained alive after its fatal main-thread exception. The operator
recorded the interruption and terminated that exact failed JVM; Gradle then
returned exit 1 and the product recorded failure. This was not normal client
shutdown. The verification reports **0 discovered / 0 acceptance executed**,
with a required minimum of 5. A zero failure counter does not make this a pass.
The unverified mod artifact SHA-256 is
`cbeb70e122aaa6d1a833b1e9776056c0f7b63abc7d16c5ac26fefe1eed08dd3e`.
No verified export or reconnect acceptance followed that run-002 failure.

No user application was closed or reconfigured to reclaim memory. The user
chose to defer further acceptance on 2026-10-09. On resumption the VM memory
minimum was raised to 4 GiB; the new successful runs are recorded separately.

## Earlier Ubuntu installed attempts

Ubuntu 24.04 GNOME on Xorg runs on the Hyper-V virtual display at 1280x800;
llvmpipe OpenGL 4.5 was observed. This is a real GNOME session, distinct from the
retained Openbox/Xvfb desktop. Wayland was not tested. The guest already contained
Java and caches and is not the clean-tooling Stage 15 gate.

The pre-fix deb was installed under `/opt/copperbench`; its welcome and preference
windows ran, as did bundled JBR 25.0.2 and workspace JDK 21.0.12. That deb's SHA-256
is `c930ef6389ac23dbdf298236d0ed519f8eaba2afa2d810cc7af9d2f303112872`.
It came from [run 37915050468](https://github.com/Lotulune/Copperbench/actions/runs/37915050468),
whose merge `496c2aa817ee4b23a0d97ee936dae45eb4edce39` has the same tree as
Windows source `06e8398dbae01cd5a0bcad32ceb27025e57f6211`.
Those earlier Windows ZIP/EXE candidates were built but not installed.

All five Ubuntu attempts remain under `/home/cbtest/output/m3/run-001` through
`run-005`, with separate workspaces and original harnesses:

| Attempt | Observed result | Elapsed time |
| --- | --- | --- |
| run-001 | Creation failed, exit 10; Maven Central unreachable | 186.7 s |
| run-002 | Stalled Fabric download interrupted by operator; exit 143 retained | 563.6 s |
| run-003 | Stalled Mojang server download interrupted after artifact-cache preparation; exit 143 retained | 559.1 s |
| run-004 | Creation failed, exit 10, on Fabric API read timeout after original Minecraft downloads were seeded | 650.1 s |
| run-005 | Creation passed in 41.3 s; doctor, discovery, element creation and preflight passed; initial build failed on Linux LWJGL resolution | Partial task |

The last attempt exposed the offline-setting mismatch described above. Neither
its partial success nor the standalone corrected-class tests certify the new
Linux installer. Earlier `20527f2f` package preparation and packaged-doctor
observations remain in the historical revision of this worksheet and archived
evidence; they are not substituted for the corrected candidates.

## Cache and control evidence

Preparation retained and validated 1,420 Gradle artifact files, 3,895 original
Minecraft downloads/assets and 1,829 resolution-cache files. It did not copy
merged/remapped Minecraft outputs. Eight Linux native JARs were checked against
Mojang metadata. Copying those JARs alone was insufficient: a separate real
Gradle resolution populated the missing repository records. The ensuing direct
offline build passed in 8 s; that was a cache diagnostic, not a product-task pass.
No guest network tunnel or host-wide proxy change was introduced in this work.
These dependency/download failures are separate observations from the previously
investigated Codex response-stream fault.

Before the pause, guest Python Bridge doctor reported `linux-x11`, bridge 0.3.0,
but no Minecraft attachment or client launch followed. Resumed gameplay now has
separate input and image evidence. The chat's Windows Minecraft MCP connection
was not used; direct guest control followed the
[bridge contract](../../tools/minecraft-control-bridge/README.md).

Local evidence is under `build/m3-validation/`, with the durable copy at
`output/agent-readiness/m3-20261009` in the primary checkout. The Windows archive
SHA-256 is `56894a095760324b0eb01975297d5bcbc97614bf4c27c80e6213e9662fba403e`.
It includes both attempts, installation and preference receipts, focused JUnit
results, the memory failure and the explicit interruption. Guest workspaces and
candidate bytes remain available. The local handoff report identifies their
exact locations without putting credentials in the repository.

## Current acceptance matrix and remaining studies

| Check on corrected candidates | Windows 11 guest | Ubuntu GNOME guest |
| --- | --- | --- |
| Install and launch product UI | passed | passed |
| Native creation, read-only discovery and doctor | passed | passed |
| Build, deliberate error diagnosis and repair | passed | passed |
| Workspace JCEF edit/save/build | passed, warm copy | passed, warm copy |
| Five packaged-JAR business tests | 5/5 passed after memory correction | 5/5 passed |
| Verified export, reconnect and external-edit conflict | passed; conflict tested in a separate corrected replay | passed in full run-006 |
| Player crafting, 16+1 stacking, save/close/rejoin | passed with guest Mesa, authorized guest US keyboard, two actual client processes and matching UUID | passed with two actual client processes and matching UUID |

Keep the guests serial and retain the explicit warm-cache boundary. New player
trials must use the same verified JAR and preserve preparation/input, player UUID,
normal exit and restored-inventory evidence. See the
[packaged-client procedure](../ai/client-acceptance.md) and
[resumed record](agent-readiness-m3-resume-2026-10-10.md).

The 20 preregistered autonomous-agent tasks and 5–8 unfamiliar-user trials have
not been executed. No participants were contacted. Scripted replay is not an
agent success-rate measurement or a user study. The task card records the
required denominator, rescue, abandonment and artifact evidence; arrange those
remaining trials separately from these installed-product regressions.
