# M3 installed-product and user acceptance worksheet

Status: candidate preparation and Windows packaged CLI doctor executed;
installed-product and player acceptance remain unexecuted. This worksheet implements the remaining
[PRD scope](../roadmap/agent-readiness-prd-2026-10-09.md); it is not a release
approval or a passed validation record. The M1/M2 local source evidence does
not certify a new installer.

## Candidate preparation observations

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
| Windows 11 x64 installed candidate | No current candidate installed replay | An isolated Windows guest and candidate; do not use the user's active foreground desktop |
| Ubuntu 24.04 GNOME installed candidate | No current candidate installed replay | A GNOME test session and matching-source Linux candidate |
| Ubuntu 24.04 Openbox/Xvfb VM | SDK and desktop bridge setup verified | Suitable only for explicitly scoped X11 gameplay checks; not a substitute for the GNOME row |
| Minecraft control connection | This chat's doctor returned `platform=windows`, version 0.3.0 | Reload the project MCP connection and require `platform=linux-x11` before input |

The current Hyper-V VM is `Copperbench-Test`; its M1 workspace is
`/home/cbtest/workspaces/m1-delivery`. Reconfirm its source/artifact identity
and actual execution backend before using it. Read the
[bridge contract](../../tools/minecraft-control-bridge/README.md), then use
doctor, list windows and attach only the intended guest client. The launcher
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
