# P263-38 G2 installed Native client acceptance

Date: 2026-10-14 (Asia/Tokyo). Result: passed for the exact cells below, with the recorded limits. G2 scoped candidate qualification is a review conclusion; it is not a public support promotion.

Windows PIDs 46680 and 13560 completed Native tasks `b2ca51a9-cee3-4b76-a2fe-76f6c8e44084` and `fac37ada-7538-41d2-a18f-a292ed11f231`. Both tasks succeeded and the host exited zero. Actual Alt+Tab focus loss caused `FOCUS_LOST` before further input. One verification request referenced the wrong session and was rejected; the corrected request and original rejection are retained. The prior derived `windows-fabric-g2-client-cell` lacked required G2 checks and was rejected by the evaluator; this report selects the independently sealed `-v2` receipt, retaining v1 unchanged.

The same final mod JAR passed the installed cold/warm Native workflow, frozen `camp-kit-scoped-v1` T01–T13 (`minimumTests=13`) in an independent packaged host, selected current/historical export, process restart, source/conflict/permission gates, duplicate/missing/shortfall rejection and tamper rejection. The client cell includes original installed raw evidence for item, block, recipe, function, code preservation and T13; screenshots do not replace those checks. A separate clean-directory isolated verifier accepted four valid bundles and rejected two tampered bundles for this platform/loader.

Actual player actions covered named-world text input, movement/look, attempting 16+1 stacking, ordered output 2 and unordered output 3 with ingredient consumption, placing one of two Camp Posts, and executing the prepared function. Give/setup commands only prepared the scene. Reopened inventories retain Trail Kit stacks 16, 1, 2, 3 and one Camp Post. Read-only NBT additionally preserves `camp_probe` / `camp_count` score 11 and a gold block at (12, 80, 12). Two normal Save and Quit / Quit Game lifecycles are corroborated by terminal task receipts and logs.

## client:windows:fabric

| Binding | Value |
| --- | --- |
| Candidate | `sha256:4cb9fecc20384c22a7e610302b53c1a9775a7952a6f00ceac92ef7af2baa682a` |
| Candidate source commit | `545a713382ec3155be4b51fa18042f2cefabc0b0` |
| Mod JAR SHA-256 | `d77a9e64dad8913d72089ac3264675c0d21d26723f5f925ef149f8c09907fe6a` |
| Installation receipt SHA-256 | `ff7e960fbafbfbca4f7e0e7a8108fd3d7b0ed78270230c38c442bc1e6165b541` |
| Client receipt SHA-256 | `7ab6bdf6ae67f3eff9160f43e2afe614ebd13d5f67941a6d756648051471d544` |
| World | `P263-fabric-win-G2-20261014` |
| First / reopened client PIDs | `46680 / 13560` |
| World identity | `83823c872177ee5a36627b1b5cfe69102fd28bdb091af3098f85aa93022603bb` |
| Original raw evidence directory | `windows-fabric-g2-client-cell-v2` |

| Run component | Lifecycle seconds | Requests | Hashed files | Input + waits ms | Capture ms | Rejected batches | Jev calls |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| primary | 1907.223 | 55 | 47 | 37143.93 | 1579.68 | 1 | 0 |

Input timing includes declared waits. Requests exclude transport polling/downloads. Supplementary runs are reported separately; no speedup is inferred. Rejected batches and malformed verification requests retain their original receipts.

The existing candidate evaluator revalidated each actual install/client pair, including all raw file hashes. Unselected cells remain `not_run`. Ubuntu G2 uses two independent installation receipts from the same frozen candidate and mod JAR: its desktop coverage is combined in a separate reviewed index, not by rebinding either client or weakening the evaluator. Windows G2 additionally has a combined evaluation with both Windows scopes eligible. The evaluator's overall matrix status is not a claim that unrelated cells ran.

See the [qualification index](../../evidence/p263/2026-10-14/qualification.json), [candidate scope and rollback](../releases/copperbench-26-3-candidate-2026-10-14.md), and [final ticket index](p263-closeout-2026-10-14.md). Raw evidence is retained under `D:/AICoding/Minecraft_ModCreator/output/p263-delivery/evidence/p263-remaining/`, preserving original failures, supplemental bindings, screenshots/actions, process logs, world archives and raw installation exports. The session did not have registered Minecraft MCP tools; it used the user's authorized task-local bounded bridge with the same focus/evidence checks, not a claimed MCP registration.
