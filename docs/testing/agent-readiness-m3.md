# M3 installed-product and user acceptance worksheet

Status: the Ubuntu candidate is installed and the fixed task has reached the
first build. That attempt exposed a managed-process offline-setting bug; the
source fix passes its focused regression, and replacement candidates are needed.
Player acceptance remains unexecuted. This worksheet implements the remaining
[PRD scope](../roadmap/agent-readiness-prd-2026-10-09.md); it is not a release
approval or a passed validation record. The M1/M2 local source evidence does
not certify a new installer.

## Candidate preparation observations

### Installed Ubuntu attempts and offline correction

The verified deb was installed with `dpkg` under `/opt/copperbench`. Its welcome
window and Gradle preferences were operated on the guest GNOME Xorg desktop;
the bundled JBR 25.0.2 and workspace JDK 21.0.12 ran successfully. The guest
product's real authorization dialog approved create/edit/build/test/run_client
under `/home/cbtest/workspaces/m3`; this did not accept a server EULA.

All five started scripted attempts are retained under guest
`/home/cbtest/output/m3/run-001` through `run-005`, with distinct workspaces.
They are installed-product regression attempts, not autonomous-agent trials.

| Attempt | Observed result | Elapsed time |
| --- | --- | --- |
| run-001 | Creation failed with exit 10; Maven Central was unreachable | 186.7 s |
| run-002 | Operator interrupted a stalled Fabric download; exit 143 and interruption receipt retained | 563.6 s |
| run-003 | Operator interrupted stalled Mojang server download after artifact-cache preparation; exit 143 retained | 559.1 s |
| run-004 | Creation failed with exit 10 on a Fabric API read timeout after original Minecraft downloads were seeded | 650.1 s |
| run-005 | Creation passed in 41.3 s; read-only doctor, field discovery, element creation and preflight passed; initial build failed on Linux LWJGL artifact resolution | Partial task; see raw receipts |

The last attempt used the guest's offline preference and imported Gradle
resolution metadata. Creation honored the setting, but Native SDK build still
attempted network requests: `Fabric1211ProcessRunner` did not add `--offline`.
It now reads the setting for each invocation and preserves explicitly supplied
`--offline` or `-o` without duplication. A real subprocess regression failed
against the old installed application with the missing flag, then all 12 tests
in `Fabric1211ProcessRunnerTest` passed with the corrected class on Linux.
This focused JUnit run compiled against the installed package; it is source-fix
evidence, not acceptance of a replacement installer. Local Gradle compilation
also passed. The earlier Windows test attempts stopped at `CreateProcess
error=5` when Java launched `cmd.exe`; those environment failures are retained.

Imported dependencies and original Minecraft downloads were checked against
their content hashes. Eight Linux LWJGL native JARs were independently checked
against Mojang metadata, but JAR files alone did not supply Gradle's missing
offline resolution records. A separate direct Gradle diagnostic confirmed that
limitation. Cache preparation is not cold-cache product evidence. No attempt
has yet reached compile-fault repair, packaged GameTest, verified export,
reconnection or player crafting/persistence. These failures are distinct from
the previously investigated Codex response-stream transport fault.

The candidate identities below describe the pre-fix bytes. Rebuild and freeze
both platforms from the corrected source before claiming installed acceptance.

The current Windows candidate was rebuilt from
`06e8398dbae01cd5a0bcad32ceb27025e57f6211`; its ZIP SHA-256 is
`77be435d1fa7b8175f9ca86e7e36372e9eafe653fd46de881b351baee2e20943`
and EXE installer SHA-256 is
`6e04ce13d89d6f3e1ead98c0dcd3571fdedebd5d81798b394f07965f3aac8952`.
The first new build hit the known Windows JDK loopback issue; the successful
replay used the existing process-local TCP fallback. Both attempts are retained.

The [matching Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/37915050468)
was built from PR merge `496c2aa817ee4b23a0d97ee936dae45eb4edce39`.
GitHub commit metadata confirms that both source trees are
`5f78e5be79f8b2ee928c4b753716e029f0fe809a`.
The artifact ZIP's full SHA-256 matches GitHub's digest; the extracted portable,
deb, SBOM and manifest hashes also match the frozen candidate metadata.
The portable SHA-256 is
`67b6d03909c52fae47a352c9af4d5e475a59c02b17feae74a0747466279813cd`;
the deb SHA-256 is
`c930ef6389ac23dbdf298236d0ed519f8eaba2afa2d810cc7af9d2f303112872`.
These private candidates retain the development version 0.1.4 and have not been
released. The metadata retains `formalSupportClaim=false`.

The guest now has Ubuntu 24.04 GNOME on Xorg on the Hyper-V virtual display,
with a reviewed 1280x800 desktop image and llvmpipe OpenGL 4.5. This guest already
contained Java and development caches, so it is not the clean-tooling Stage 15
gate. A separate Windows 11 VM has been created but its OS is not installed.
No host desktop focus was used. The user authorized direct guest control; the
guest Python Bridge API reports `linux-x11`, version 0.3.0, without depending on
this chat's Windows MCP registration.

The [fixed task card](agent-readiness-m3-task-card.md) and
[installed replay script](../../scripts/verify-agent-readiness-installed.py)
are prepared. Syntax and local Markdown links pass; the installed replay has
the partial results recorded above. Scripted replay does not supply the 20 agent-task measurements
or 5–8 unfamiliar-user results.

### Earlier preparation retained for provenance

Private Windows candidates built from `20527f2f3d4b93c4a18eba277fb222e0e6b133c0`
retain the development version string 0.1.4. They were not installed, signed
or published. The portable ZIP SHA-256 is
`f8d42c0f6cd8520cf4dcd0cf5c4a87ab22b746587010aefbfb25aca951b611e7`;
the EXE installer SHA-256 is
`511151e33fec0158cd76c5d3966854d9c6303e616fe796fd648abb09e6988a90`.
The first installer attempt failed fetching NSIS; the successful rerun used
an existing NSIS 3.12 cache after file-by-file hash comparison.

The real packaged `copperbench.exe headless ... doctor` returned exit 0 in
1.587 seconds. Its report passed the shared schema; the workspace, isolated
home/temp/log/user-data and package file-name set remained unchanged. This
was a background CLI invocation, not an installed GUI or game replay.

The [Linux candidate job](https://github.com/Lotulune/Copperbench/actions/runs/37902909910)
passed on PR merge `da711b2760a047e01ca131dd88caaf7cdd5a4b64`.
GitHub commit metadata confirms its Git tree and the Windows source tree are
both `f03c2c15e387e03fddc37cc370b92eaadfc25f84`. Linux package hashes were
observed in the job log; its package bytes were not downloaded and independently
hashed locally. Neither result certifies Ubuntu GNOME installed acceptance.

The source SDK README now links doctor to the shipped schema and identifies
the M1 fixture and preflight validation report as source-repository paths.
All nine remaining relative link targets exist in both source and the prepared
Windows package layout. Those README edits are not in the existing candidate
bytes, so this candidate has not been frozen for M3 acceptance.

Local evidence is retained under `build/m3-validation/`: artifact hashes,
packaged doctor/nonmutation receipts, cached NSIS provenance, cross-platform
source metadata and SDK README link checks. These observations leave every
unexecuted cell below open.

## Candidate and host prerequisites

Freeze one reviewed product source and record each platform package's SHA-256,
manifest and build provenance before running this matrix. Windows and Linux
packages must come from that same source; use the exact tested bytes for any
later publication. Keep the public 0.1.4 artifacts and historical gate records
unchanged. The version of a new release has not been selected.

| Environment | Current evidence | Remaining prerequisite |
| --- | --- | --- |
| Windows 11 x64 installed candidate | Current candidate built; isolated VM created | Install Windows and the frozen candidate, then execute the fixed task |
| Ubuntu 24.04 GNOME installed candidate | Verified deb installed; fixed task reached initial build and exposed an offline-setting defect | Install the corrected candidate and finish the fixed task; Wayland remains unverified |
| Ubuntu 24.04 Openbox/Xvfb VM | SDK and desktop bridge setup verified | Suitable only for explicitly scoped X11 gameplay checks; not a substitute for the GNOME row |
| Minecraft control connection | Direct guest Bridge doctor returned `platform=linux-x11`, version 0.3.0 | Attach only the intended guest client; the host MCP connection is not used |

The current Hyper-V VM is `Copperbench-Test`; its M1 workspace is
`/home/cbtest/workspaces/m1-delivery`. Reconfirm its source/artifact identity
and actual execution backend before using it. Read the
[bridge contract](../../tools/minecraft-control-bridge/README.md), then use
doctor, list windows and attach only the intended guest client. Direct Python
Bridge calls are authorized for this isolated guest. The launcher
must use Copperbench lifecycle/SDK and retain the workspace session until the
client's terminal task state is recorded. Configuration pointing to the VM
alone does not prove an existing MCP connection has changed backends.

## Fixed installed-product task

Use the [M1 fixed sample](../../examples/agent-readiness/m1-delivery/README.md)
and its independent packaged-JAR checks. Register OS, source/package hashes,
workspace path, user/agent configuration and cold/warm cache condition before
starting. Execute the complete task once before classifying its result.

1. Launch the installed package, run the read-only doctor, create a disposable
   Fabric 1.21.1 workspace and discover item/recipe metadata from the public API.
2. Create the sample, save and build. Record the task ID, terminal diagnostics,
   logs and actual JDK/loader/API/generator versions.
3. Inject the documented compile fault, locate it from the returned diagnostic,
   explicitly repair it and rebuild. Preserve the failed attempt.
4. On a separate disposable copy, externally change a protected source file,
   run preflight and attempt the affected operation. Require an actionable
   conflict and byte-identical preservation of the user's edit after rejection.
5. Run packaged-JAR business tests, inspect effective counts/failures/skips and
   export the same verified artifact. Reconnect and confirm the evidence still
   belongs to the current input. Keep protocol, build and business results
   separate.
6. Prepare the client using the
   [packaged-client procedure](../ai/client-acceptance.md). Perform actual player
   crafting and stacking through the guest bridge. Save nondefault inventory
   state, leave the world, close the process normally, relaunch through the
   same product and reenter the exact world. Record restored state, player
   identity, both process/task identities, screenshots and action/log hashes.

The game preparation commands are not player interactions. Do not infer
crafting or persistence from source strings, server tests, a menu receipt or
an artifact hash. Review the actual screenshots before recording each
passed/failed/unverified result. Use the inspected sample recipe and stack
limit as expectations; do not guess them while operating the UI.

## Preregistered task and unfamiliar-user records

Before the first measured run, freeze at least 20 task IDs with the same
sample/task instructions and declared agent/model/cache settings. Keep failed
and abandoned attempts in the denominator. Record whether a person provided
rescue, the exact step, the intervention, the final artifact identity and
elapsed time. The proposed >=80% target concerns completion without rescue;
it is not a current measured success rate or a comparison with direct coding.

For the 5–8 unfamiliar-user study, use anonymous participant IDs and the same
task card. Ask participants to attempt the task and explain confusing steps;
record completion, abandonment, rescue and concrete misunderstandings. No
participants have been recruited or contacted, and no results exist. External
messages require the user's authorization.

| Run ID | OS/package hash | Agent/model or participant ID | Cache condition | Completed/failed/abandoned | Rescue and step | Artifact/report identity | Evidence location |
| --- | --- | --- | --- | --- | --- | --- | --- |

Create one row for every registered attempt, including interrupted attempts.
Finalize the acceptance matrix only after the same candidate has evidence for
every claimed environment; leave unavailable or unexecuted cells explicit.
