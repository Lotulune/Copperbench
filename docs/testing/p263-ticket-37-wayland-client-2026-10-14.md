# P263-37 G1 installed Native client acceptance

Date: 2026-10-14 (Asia/Tokyo). Result: passed for the exact cells below, with the recorded limits. This is G1 native-preview evidence only.

The original player run 38437219227 saved normally with PID 12878. PID 17097 visually reopened the world but its control session later hit the 90-minute limit; the original run remains failed. Supplementary reopen 38451371385 (PID 13144, task `6e36fbbb-777b-4805-aa04-4fd46fa58a10`, `supplementary_reopen_only`) is bound to the archived first save and completed normally. Real focus rejection comes from separately bound run 38426026727 (`supplementary_focus_only`). All seven saved-state checks agree. This is explicitly a reviewed combination of bounded runs, not a successful original full lifecycle.

Actual player actions covered exact world-name text, keyboard movement, mouse look, a manual 16+1 stack-cap attempt, ordered crafting output 2 with real ingredient consumption, and placement reducing two Camp Posts to one. Preparation commands do not count as player actions. The saved inventory retains Trail Kit stacks 16, 1, 2 and one Camp Post. Real focus loss blocked further input; the controller detached before any sequential focus recovery. All sessions were detached at the end. G1 sample textures/models and localization remain incomplete; visual asset quality is not claimed.

## client:wayland:neoforge

| Binding | Value |
| --- | --- |
| Candidate | `sha256:65dbd37b779ff7259273c498f0830646809ef6506f9b2b30d296b0ccfacc24e6` |
| Candidate source commit | `f29b4e193ed72d7cc60ab199687ffada32adeb74` |
| Mod JAR SHA-256 | `59cb2a585035747188778325006a2fb91b1459e95da9cff64a74bcf4c6cd5e42` |
| Installation receipt SHA-256 | `212a7bfcdc20dda8e04c533d3dd6f01e84a4fae160625cc6cd067a73ee2b7536` |
| Client receipt SHA-256 | `2aa8054b22a346564f82cec476eadb4f09c710b91232ca99813cfafd931b2b8f` |
| World | `P263-neo-wayland-38437219227` |
| First / reopened client PIDs | `12878 / 13144` |
| World identity | `7271b60d11c6abf3c5a130423b03f3ae5b84cc30ffd7e33cce5512b482b9557c` |
| Original raw evidence directory | `wayland-neoforge-g1-client-cell` |

| Run component | Lifecycle seconds | Requests | Hashed files | Input + waits ms | Capture ms | Rejected batches | Jev calls |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| focus | 4114.663 | 21 | 17 | 7535.44 | 6340.49 | 1 | 0 |
| primary | 5400.466 | 34 | 32 | 23655.66 | 13385.3 | 0 | 0 |
| reopen | 2242.34 | 16 | 12 | 9173.22 | 4959.96 | 0 | 0 |

Input timing includes declared waits. Requests exclude transport polling/downloads. Supplementary runs are reported separately; no speedup is inferred. Rejected batches and malformed verification requests retain their original receipts.

The existing candidate evaluator revalidated each actual install/client pair, including all raw file hashes. Unselected cells remain `not_run`. Ubuntu G2 uses two independent installation receipts from the same frozen candidate and mod JAR: its desktop coverage is combined in a separate reviewed index, not by rebinding either client or weakening the evaluator. Windows G2 additionally has a combined evaluation with both Windows scopes eligible. The evaluator's overall matrix status is not a claim that unrelated cells ran.

See the [qualification index](../../evidence/p263/2026-10-14/qualification.json), [candidate scope and rollback](../releases/copperbench-26-3-candidate-2026-10-14.md), and [final ticket index](p263-closeout-2026-10-14.md). Raw evidence is retained under `D:/AICoding/Minecraft_ModCreator/output/p263-delivery/evidence/p263-remaining/`, preserving original failures, supplemental bindings, screenshots/actions, process logs, world archives and raw installation exports. The session did not have registered Minecraft MCP tools; it used the user's authorized task-local bounded bridge with the same focus/evidence checks, not a claimed MCP registration.
