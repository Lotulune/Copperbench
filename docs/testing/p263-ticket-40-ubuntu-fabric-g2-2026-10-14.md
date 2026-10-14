# P263-40 G2 installed Native client acceptance

Date: 2026-10-14 (Asia/Tokyo). Result: passed for the exact cells below, with the recorded limits. G2 scoped candidate qualification is a review conclusion; it is not a public support promotion.

Xorg run 38452185663 used PIDs 11864 and 14917. Native Wayland run 38463435714 used PIDs 12504 and 15519; the desktop receipt records `native_wayland=true`. Both runs completed two succeeded Native tasks and host exit zero.

In both runs the strict comparison of the two final saves failed only on player position; those `world-comparison.json` files remain failed. The original second-login log exactly matches the first-save position (Xorg line 192, Wayland line 193). `world-load-position.json` separately proves restoration at load; the other six state checks and function persistence agree. Xorg screenshots show a nearby horse after reentry. Wayland's later displacement has no established cause. This report accepts restoration at load, and does not claim equality of final saved positions. Fabric's development account UUID/appearance changed.

Wayland rejected some crafting batches with `POINTER_POSITION_UNCONFIRMED`. The planner inspected partial completion and continued only the remaining actions; the ordered output was visibly separated into stacks 1+2, and unordered crafting produced 3 with actual ingredient consumption. Two look requests after closing inventory caused no visible turn and do not count as passing input. Returning through the observed pause menu restored capture; subsequent actual turn and placement succeeded. Early distant-horizon rendering artifacts remain unexplained in this software-Mesa environment. Xorg's original action timeout and a transport submission failure are preserved.

The same final mod JAR passed the installed cold/warm Native workflow, frozen `camp-kit-scoped-v1` T01–T13 (`minimumTests=13`) in an independent packaged host, selected current/historical export, process restart, source/conflict/permission gates, duplicate/missing/shortfall rejection and tamper rejection. The client cell includes original installed raw evidence for item, block, recipe, function, code preservation and T13; screenshots do not replace those checks. A separate clean-directory isolated verifier accepted four valid bundles and rejected two tampered bundles for this platform/loader.

Actual player actions covered named-world text input, movement/look, attempting 16+1 stacking, ordered output 2 and unordered output 3 with ingredient consumption, placing one of two Camp Posts, and executing the prepared function. Give/setup commands only prepared the scene. Reopened inventories retain Trail Kit stacks 16, 1, 2, 3 and one Camp Post. Read-only NBT additionally preserves `camp_probe` / `camp_count` score 11 and a gold block at (12, 80, 12). Two normal Save and Quit / Quit Game lifecycles are corroborated by terminal task receipts and logs.

## client:xorg:fabric

| Binding | Value |
| --- | --- |
| Candidate | `sha256:5af68d4a7690efa0038675bab96987d888bb624f1b8550cfcb74a1a14006203c` |
| Candidate source commit | `12642d68c0f7c041d478eadbd6cf6853b7988044` |
| Mod JAR SHA-256 | `d77a9e64dad8913d72089ac3264675c0d21d26723f5f925ef149f8c09907fe6a` |
| Installation receipt SHA-256 | `5bf2eaf265f5ea74225c4a07e1d35dcf4f1363729868eb9a5f0732e520398551` |
| Client receipt SHA-256 | `acf614bc8f002ef57a42a305a83ed339705691e28c55fd23c0d8543d4bf787aa` |
| World | `P263-fabric-xorg-38452185663` |
| First / reopened client PIDs | `11864 / 14917` |
| World identity | `f681c47d5ab4c6bcfc47348c6de4c9432e9db9b8da15ae85e1b0cf243e8b82cb` |
| Original raw evidence directory | `xorg-fabric-g2-client-cell` |

| Run component | Lifecycle seconds | Requests | Hashed files | Input + waits ms | Capture ms | Rejected batches | Jev calls |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| primary | 4949.548 | 58 | 50 | 40842.31 | 4007.47 | 1 | 0 |

Input timing includes declared waits. Requests exclude transport polling/downloads. Supplementary runs are reported separately; no speedup is inferred. Rejected batches and malformed verification requests retain their original receipts.

## client:wayland:fabric

| Binding | Value |
| --- | --- |
| Candidate | `sha256:5af68d4a7690efa0038675bab96987d888bb624f1b8550cfcb74a1a14006203c` |
| Candidate source commit | `12642d68c0f7c041d478eadbd6cf6853b7988044` |
| Mod JAR SHA-256 | `d77a9e64dad8913d72089ac3264675c0d21d26723f5f925ef149f8c09907fe6a` |
| Installation receipt SHA-256 | `4cc796a7938195987ceef49f84d5b64ca0ce354d8d163bdf94f664ff1fcc855d` |
| Client receipt SHA-256 | `13e2fd0c1d3cb693faccac27155f19ac1aec514bc21cdc1e4d0b30a3038e7304` |
| World | `P263-fabric-wayland-38463435714` |
| First / reopened client PIDs | `12504 / 15519` |
| World identity | `34290eaf636a2f953550fe6ddb1367904a4a66d4556f6c5cc15f506ce35c3aca` |
| Original raw evidence directory | `wayland-fabric-g2-client-cell` |

| Run component | Lifecycle seconds | Requests | Hashed files | Input + waits ms | Capture ms | Rejected batches | Jev calls |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| primary | 4071.368 | 68 | 59 | 51172.92 | 26464.81 | 1 | 0 |

Input timing includes declared waits. Requests exclude transport polling/downloads. Supplementary runs are reported separately; no speedup is inferred. Rejected batches and malformed verification requests retain their original receipts.

The existing candidate evaluator revalidated each actual install/client pair, including all raw file hashes. Unselected cells remain `not_run`. Ubuntu G2 uses two independent installation receipts from the same frozen candidate and mod JAR: its desktop coverage is combined in a separate reviewed index, not by rebinding either client or weakening the evaluator. Windows G2 additionally has a combined evaluation with both Windows scopes eligible. The evaluator's overall matrix status is not a claim that unrelated cells ran.

See the [qualification index](../../evidence/p263/2026-10-14/qualification.json), [candidate scope and rollback](../releases/copperbench-26-3-candidate-2026-10-14.md), and [final ticket index](p263-closeout-2026-10-14.md). Raw evidence is retained under `D:/AICoding/Minecraft_ModCreator/output/p263-delivery/evidence/p263-remaining/`, preserving original failures, supplemental bindings, screenshots/actions, process logs, world archives and raw installation exports. The session did not have registered Minecraft MCP tools; it used the user's authorized task-local bounded bridge with the same focus/evidence checks, not a claimed MCP registration.
