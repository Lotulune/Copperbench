# P263-35 G1 installed Native client acceptance

Date: 2026-10-14 (Asia/Tokyo). Result: passed for the exact cells below, with the recorded limits. This is G1 native-preview evidence only.

Run 38445309263 completed both lifecycles (PIDs 13129 and 16071) with succeeded Native tasks and host exit code zero. Both saves agree on world identity, player position and rotation, inventory, selected slot, game mode and the placed block. The source inventories and mod JAR remained unchanged.

Actual player actions covered exact world-name text, keyboard movement, mouse look, a manual 16+1 stack-cap attempt, ordered crafting output 2 with real ingredient consumption, and placement reducing two Camp Posts to one. Preparation commands do not count as player actions. The saved inventory retains Trail Kit stacks 16, 1, 2 and one Camp Post. Real focus loss blocked further input; the controller detached before any sequential focus recovery. All sessions were detached at the end. G1 sample textures/models and localization remain incomplete; visual asset quality is not claimed.

## client:xorg:neoforge

| Binding | Value |
| --- | --- |
| Candidate | `sha256:65dbd37b779ff7259273c498f0830646809ef6506f9b2b30d296b0ccfacc24e6` |
| Candidate source commit | `f29b4e193ed72d7cc60ab199687ffada32adeb74` |
| Mod JAR SHA-256 | `59cb2a585035747188778325006a2fb91b1459e95da9cff64a74bcf4c6cd5e42` |
| Installation receipt SHA-256 | `02287dd87329baab2db2b6b310dcb05e452f5f769a74a2883b6333fd1858a67b` |
| Client receipt SHA-256 | `f161d1fd4caa6f236e3180d9957accb0643b793f8b52ba83ceae6271fc0c5698` |
| World | `P263-neo-xorg-38445309263` |
| First / reopened client PIDs | `13129 / 16071` |
| World identity | `6d89740e73e5425fbe7705d2c55af1971941769617535294fbd4fafdd5b2d676` |
| Original raw evidence directory | `xorg-neoforge-g1-client-cell` |

| Run component | Lifecycle seconds | Requests | Hashed files | Input + waits ms | Capture ms | Rejected batches | Jev calls |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| primary | 4370.219 | 44 | 36 | 26153.67 | 2726.61 | 1 | 0 |

Input timing includes declared waits. Requests exclude transport polling/downloads. Supplementary runs are reported separately; no speedup is inferred. Rejected batches and malformed verification requests retain their original receipts.

The existing candidate evaluator revalidated each actual install/client pair, including all raw file hashes. Unselected cells remain `not_run`. Ubuntu G2 uses two independent installation receipts from the same frozen candidate and mod JAR: its desktop coverage is combined in a separate reviewed index, not by rebinding either client or weakening the evaluator. Windows G2 additionally has a combined evaluation with both Windows scopes eligible. The evaluator's overall matrix status is not a claim that unrelated cells ran.

See the [qualification index](../../evidence/p263/2026-10-14/qualification.json), [candidate scope and rollback](../releases/copperbench-26-3-candidate-2026-10-14.md), and [final ticket index](p263-closeout-2026-10-14.md). Raw evidence is retained under `D:/AICoding/Minecraft_ModCreator/output/p263-delivery/evidence/p263-remaining/`, preserving original failures, supplemental bindings, screenshots/actions, process logs, world archives and raw installation exports. The session did not have registered Minecraft MCP tools; it used the user's authorized task-local bounded bridge with the same focus/evidence checks, not a claimed MCP registration.
