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
