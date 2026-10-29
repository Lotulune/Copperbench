# Agent onboarding

[简体中文](getting-started.md) · [The same Trail Block](trail-block.en.md) · [SDK compatibility](../../sdk/compatibility.md)

## Choose one entry

| Situation | Entry | Lifetime/authentication |
| --- | --- | --- |
| Workspace open in Copperbench | Python `Workspace.connect(...)` | Same OS user; shares the live desktop Core. Disconnecting the script keeps the desktop/tasks alive. |
| Independent Python automation | `Workspace.open(...)` | Existing `.mcreator`, installed launcher and exclusive write lock. Keep this host alive until its tasks/client exit. |
| General MCP client | Desktop **Tools → AI & MCP** | User pairs and supplies a workspace-scoped token. Discover the random loopback endpoint from `.copperbench/mcp-connection.json`; credentials are not in that file. |

Use the [minimal executable examples](../../sdk/agent-workflows.md). If the startup window offers **Connect external Agent**, it only explains setup and never issues a token. You can also open the workspace and use the AI/MCP page directly. A lost desktop session never automatically becomes an independent writing host.

## Discover before writing

Discover SDK helpers and operations from the actual host. [Current downloads](../releases/current.md) identifies published packages; [current status](../status.md) records this local source tree separately. Check MCP `2025-11-25`, or Native transport `1` plus Core `1.0`, and actual catalog/ready operations. Required semantics must not silently downgrade; see [contract floors](../../sdk/compatibility.md). For the in-app assistant, see [Simple creation（简体中文）](../user/simple-creation.md).

1. Negotiate/initialize; for MCP inspect `tools/list`, not guessed tool names.
2. Read `get_workspace_environment`: active generator, toolchain/execution, application identity and `data.fieldContracts`. There is no `get_mod_element_field_contract` in this catalog. Missing exposure, Core unavailability and malformed metadata are different results.
3. Use bounded selected-field lists, then read the target object. Traverse to `nextCursor=null`; discard an incomplete set on stale cursor before starting another traversal. Public project configuration is the source for unexposed mapping settings; do not infer mappings from “Fabric”.
4. Before each write, read the applicable revision/hash. Preserve generated/manual ownership and unknown fields. The [Trail Block example](trail-block.en.md) uses the actual contract and fresh revision.

Reuse one freshly read environment within its preparation step to avoid repeated large reads. There is no implicit TTL cache; re-read after reconnecting or changing workspace/generator/contract context. Native `get_workbench` and `get_mod_element_editor` map to MCP `get_workspace` and `read_mod_element`; their request wrappers differ.

## Repair, resume and deliver

Retain every diagnostic's stable code and original `details`. Correct fields from the exposed contract or follow the actual compiler source path. Revision/hash conflicts retain the newer disk bytes and your unsaved draft; inspect/reread before explicitly retrying a corrected action. Do not parse a stack trace to guess valid input.

Start a long task once and save `task.id`. Poll that original ID with the last persisted `afterLogSequence`. Python `wait_task(..., after_log_sequence=N, on_update=save_increment)` and TypeScript `waitTask(id, { afterLogSequence: N, onUpdate: saveIncrement })` checkpoint original increments. Timeout ends waiting, not the task; transport loss preserves raw errors and the last successful checkpoint. Failed/cancelled tasks remain terminal observations, not success. Do not automatically replay an uncertain write/start response.

The fixed public [W1/W2/W3 runners](../../sdk/agent-workflows.md) cover create/fail/fix, stale revision/source rejection and build/reconnect/exact export. MCP W2 explicitly lacks protected workspace-file editing; full source-hash coverage uses Native. W3 requires a real passed packaged-JAR acceptance task. Ordinary build/export is not acceptance; exact export compares the selected JAR/report/source identities, and historical input requires explicit selection. [GUI build/export](../user/build-and-export.en.md) describes the corresponding creator route.

## Permissions and records

Tokens stay in trusted client memory, not commands, repository, conversation, diagnostics or logs. Existing read-only/workspace/full-access profiles remain enforced. User-issued task grants can scope operations/root/lifetime and be revoked; the Agent cannot issue them, claim UI approval or accept EULA. Cancellation/revocation remain explicit operations. Protected recovery first uses its existing preview and user-authorized apply path.

The SDK [protocol](../../sdk/protocol.md) defines the audited read retry allowlist; writes, unknown tools and plan/service probes stay single-attempt. Optional metrics retain counts/bytes/time only, without payloads or credentials. Record the tested source/artifact identity and actual limitations; a unit fixture or loading smoke is not gameplay acceptance. Manual gameplay/persistence follows [the same first creation](../user/first-creation.en.md).
