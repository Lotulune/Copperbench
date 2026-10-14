# P263-34 G1 installed Native client acceptance

Date: 2026-10-14 (Asia/Tokyo). Result: passed for the exact cells below, with the recorded limits. This is G1 native-preview evidence only.

The first player run was 38428889069 (PID 11530). Its first save and exit succeeded, but the second lifecycle exhausted the control budget. That run remains failed. Supplementary run 38445300688 (PID 11642, `supplementary_reopen_only`) restored the exact first-save world archive and completed a normal exit. The resume binding verifies the original session, installation and all world bytes. No claim is made that the original full run succeeded. The gameplay-state comparison passed all seven checks; Fabric's development player UUID changed.

Actual player actions covered exact world-name text, keyboard movement, mouse look, a manual 16+1 stack-cap attempt, ordered crafting output 2 with real ingredient consumption, and placement reducing two Camp Posts to one. Preparation commands do not count as player actions. The saved inventory retains Trail Kit stacks 16, 1, 2 and one Camp Post. Real focus loss blocked further input; the controller detached before any sequential focus recovery. All sessions were detached at the end. G1 sample textures/models and localization remain incomplete; visual asset quality is not claimed.

## client:xorg:fabric

| Binding | Value |
| --- | --- |
| Candidate | `sha256:d7baeec4304b79ed0900395a9a60be95f76e390a950dad61d1d4b090dc41e6cf` |
| Candidate source commit | `88cf8496613fa4d73365d860804488d556e3d39b` |
| Mod JAR SHA-256 | `85efba8002bf9ebf16a109a4b955a3a3bc34d43ddb103649761d01af442723d8` |
| Installation receipt SHA-256 | `51901eff0222f43b1bff3aeb6361b368758c0b6416b72adf7c80d4c1cc3c913e` |
| Client receipt SHA-256 | `c69adfa0eea0ee27984ee822bbe2e0f3bb26b8bd7ee7a4d4e98c0a0322cd54df` |
| World | `P263-fabric-xorg-38428889069` |
| First / reopened client PIDs | `11530 / 11642` |
| World identity | `bee055ab8f772a9f1ed52403427cab587d29d69402d2c9bcb3c87ec40f0bbc3e` |
| Original raw evidence directory | `xorg-fabric-g1-client-cell` |

| Run component | Lifecycle seconds | Requests | Hashed files | Input + waits ms | Capture ms | Rejected batches | Jev calls |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| primary | 5400.584 | 50 | 45 | 38038.25 | 3434.43 | 1 | 0 |
| reopen | 2837.598 | 16 | 12 | 8524.57 | 1082.07 | 0 | 0 |

Input timing includes declared waits. Requests exclude transport polling/downloads. Supplementary runs are reported separately; no speedup is inferred. Rejected batches and malformed verification requests retain their original receipts.

The existing candidate evaluator revalidated each actual install/client pair, including all raw file hashes. Unselected cells remain `not_run`. Ubuntu G2 uses two independent installation receipts from the same frozen candidate and mod JAR: its desktop coverage is combined in a separate reviewed index, not by rebinding either client or weakening the evaluator. Windows G2 additionally has a combined evaluation with both Windows scopes eligible. The evaluator's overall matrix status is not a claim that unrelated cells ran.

See the [qualification index](../../evidence/p263/2026-10-14/qualification.json), [candidate scope and rollback](../releases/copperbench-26-3-candidate-2026-10-14.md), and [final ticket index](p263-closeout-2026-10-14.md). Raw evidence is retained under `D:/AICoding/Minecraft_ModCreator/output/p263-delivery/evidence/p263-remaining/`, preserving original failures, supplemental bindings, screenshots/actions, process logs, world archives and raw installation exports. The session did not have registered Minecraft MCP tools; it used the user's authorized task-local bounded bridge with the same focus/evidence checks, not a claimed MCP registration.
