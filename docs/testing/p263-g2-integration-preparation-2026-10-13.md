# P263 G2 integration inputs

The new `examples/camp-kit-scoped` fixtures combine T01–T13 against a single
structured Camp Kit JAR for each loader. The installed Native harness creates
and edits all five element kinds, generates the workspace, introduces and repairs
a real compile error, checks independent packaged-JAR tests, reopens persisted
definitions and runs the existing export/tamper/missing-case protections.

`package_candidate.py --level G2` selects the scoped fixture and freezes a new
candidate. Its status remains pending acceptance. Ubuntu G2 requires both Xorg
and Wayland client cells from that same candidate. It does not inherit G1 passes.

The integrated tests share one 200-tick Trail Kit fuel contract. Recipe and loot
reloads are queued on the server executor; function effects use a separate world
position from the persisted pillar. The independent host imports no generated
mod constants or source. Raw host runtime verification is still required.

The [GNOME environment probe](https://github.com/Lotulune/Copperbench/actions/runs/38356323070)
successfully started real Xorg with GNOME Shell and the native GNOME Wayland
compositor on Ubuntu 24.04. Wayland reported a native renderer and a 1280x720
virtual monitor. Both exposed the Mutter RemoteDesktop and ScreenCast interfaces.
This was interface/environment qualification only: no Minecraft player input or
saved-world acceptance was performed by that probe.

Relevant checks: 38 candidate/installation boundary tests and 106 Python SDK
tests passed. The cancellation, preview and original-eight-generator regressions
and Javadoc passed; detailed XML is retained under
`output/p263-remaining/cancellation-and-legacy-test-results/`.

Tickets 32–41 remain open until their actual candidate-bound requirements pass.

## Integrated runtime results and follow-up

The [second Fabric G2 run](https://github.com/Lotulune/Copperbench/actions/runs/38360248775)
passed both installed Native executions: cold 311.923 seconds from an empty
dependency cache and warm 254.760 seconds. Both ran all thirteen required cases,
the intentional compile error and repair, cancellation, missing-case and tamper
rejections, reopening, and independent portable checks. The workflow failed in
the final cell aggregator because it still required three Native processes;
G2 correctly adds a fourth process for structured element creation. The original
run remains failed and its raw evidence is preserved under
`output/p263-remaining/cloud/fabric-g2-attempt-2/`.

The [second NeoForge G2 run](https://github.com/Lotulune/Copperbench/actions/runs/38360256142)
built and repaired the generated mod but failed compilation of the independent
host: the integrated block fixture had omitted `WorldReopenChecks`. The existing
independently exercised helper is now included. These failures do not establish
a G2 support claim.

The corrected aggregator validates the exact G1/G2 process count, distinct PIDs
and clean exits, retains the five structured contracts and element receipts,
and links the five element checks to actual thirteen-case evidence. All 39
candidate/installation boundary checks passed. Fresh candidate runs retain their
own source and artifact identities; earlier failures are not relabeled.

The [GNOME adapter run](https://github.com/Lotulune/Copperbench/actions/runs/38362118611)
passed on both real Xorg and native Wayland. It captured the desktop and reserved
and released F8 without sending input. A task-local bounded request queue now
keeps the installed Native lifecycle alive while returning each action receipt
and screenshot for planner inspection. It uses the existing bridge engine and
does not claim that an MCP connection was registered. Actual client qualification
remains pending.
