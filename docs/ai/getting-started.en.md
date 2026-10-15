# Agent onboarding

[简体中文](getting-started.md) · [The same Trail Block](trail-block.en.md) · [SDK compatibility](../../sdk/compatibility.md)

## Choose one entry

| Situation | Entry | Lifetime/authentication |
| --- | --- | --- |
| Workspace open in Copperbench | Python `Workspace.connect(...)` | Same OS user; shares the live desktop Core. Disconnecting the script keeps the desktop/tasks alive. |
| Independent Python automation | `Workspace.open(...)` | Existing `.mcreator`, installed launcher and exclusive write lock. Keep this host alive until its tasks/client exit. |
| General MCP client | Desktop **Tools → AI & MCP** | User pairs and supplies a workspace-scoped token. Discover the random loopback endpoint from `.copperbench/mcp-connection.json`; credentials are not in that file. |

Use the [minimal executable examples](../../sdk/agent-workflows.md). Version 0.1.6 provides the startup **Connect external Agent** help; it only explains setup, never issues a token. Older packages can open the workspace and use the existing AI/MCP page. A lost desktop session never automatically becomes an independent writing host.

## Discover before writing

These examples describe the 0.1.6 client routes. Older hosts still require actual capability discovery; see [current status](../status.md). Check MCP `2025-11-25`, Native transport `1`, Core handshake/ordinary operations `1.0`, and actual catalog/ready operations. Editor queries separately select `1.1`; a successful `1.0` handshake does not establish editor support. Required semantics must not silently downgrade; see [contract floors](../../sdk/compatibility.md).

1. Negotiate/initialize; for MCP inspect `tools/list`, not guessed tool names.
2. Read `get_workspace_environment`: active generator, toolchain/execution, application identity and `data.fieldContracts`. Current main exposes `get_mod_element_field_contract` and `get_field_reference_options`. Native `discover_field_contract(...)` reports available/not_exposed/unsupported without creating probe elements. The Native legacy environment helper uses `NATIVE_FIELD_CONTRACT_UNAVAILABLE` for an absent type; MCP/TypeScript legacy helpers use `FIELD_CONTRACT_NOT_EXPOSED`. Core unavailability and malformed metadata retain their distinct errors.
3. Use bounded selected-field lists, then read the target object. Traverse to `nextCursor=null`; discard an incomplete set on stale cursor before starting another traversal. Public project configuration is the source for unexposed mapping settings; do not infer mappings from “Fabric”.
4. Before each write, read the applicable revision/hash. Preserve generated/manual ownership and unknown fields. The [Trail Block example](trail-block.en.md) uses the actual contract and fresh revision.

Reuse one freshly read environment within its preparation step to avoid repeated large reads. There is no implicit TTL cache; re-read after reconnecting or changing workspace/generator/contract context. Native `get_workbench` and `get_mod_element_editor` map to MCP `get_workspace` and `read_mod_element`; their request wrappers differ.

Current main also retains `get_workspace_doctor`/`workspace.doctor()`, `preview_generation`, explicit 26.3 preview and [version-copy negotiation](version-copy-upgrade.md). Doctor and generation preview do not download dependencies, start a build or grant write permission. Modern SDK editor reads select [editor schema 1.1](editor-schema.md); request `1.0` explicitly for legacy editor projections. Ordinary operations remain at `1.0`.

## Repair, resume and deliver

Retain every diagnostic's stable code and original `details`. Correct fields from the exposed contract or follow the actual compiler source path. Revision/hash conflicts retain the newer disk bytes and your unsaved draft; inspect/reread before explicitly retrying a corrected action. Do not parse a stack trace to guess valid input.

Start a long task once and save `task.id`. Poll that original ID with the last persisted `afterLogSequence`. Python `wait_task(..., after_log_sequence=N, on_update=save_increment)` and TypeScript `waitTask(id, { afterLogSequence: N, onUpdate: saveIncrement })` checkpoint original increments. Timeout ends waiting, not the task; transport loss preserves raw errors and the last successful checkpoint. Failed/cancelled tasks remain terminal observations, not success. Do not automatically replay an uncertain write/start response.

The fixed public [W1/W2/W3 runners](../../sdk/agent-workflows.md) cover create/fail/fix, stale revision/source rejection and build/reconnect/exact export. MCP W2 explicitly lacks protected workspace-file editing; full source-hash coverage uses Native. W3 requires a real passed packaged-JAR acceptance task. Ordinary build/export is not acceptance; exact export compares the selected JAR/report/source identities, and historical input requires explicit selection. [GUI build/export](../user/build-and-export.en.md) describes the corresponding creator route.

## Permissions and records

Tokens stay in trusted client memory, not commands, repository, conversation, diagnostics or logs. Existing read-only/workspace/full-access profiles remain enforced. User-issued task grants can scope operations/root/lifetime and be revoked; the Agent cannot issue them, claim UI approval or accept EULA. Cancellation/revocation remain explicit operations. Protected recovery first uses its existing preview and user-authorized apply path.

The SDK [protocol](../../sdk/protocol.md) defines the audited read retry allowlist; writes, unknown tools and plan/service probes stay single-attempt. Optional metrics retain counts/bytes/time only, without payloads or credentials. Record the tested source/artifact identity and actual limitations; a unit fixture or loading smoke is not gameplay acceptance. Manual gameplay/persistence follows [the same first creation](../user/first-creation.en.md).
