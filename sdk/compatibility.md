# Public client compatibility / 公共客户端兼容边界

The Python package version (`0.1.0`), product version and wire versions are
independent. This source tree configures product `0.1.4`; new helpers here are
source changes, not a claim that published 0.1.4 packages contain them. Historical
results retain their original source and artifact identities.

Python 包、产品和协议版本独立；本页以当前源码为准，不把历史验收移用于新字节。

| Common route / 常用路线 | Contract floor / 最低合同 | Discovery and incompatibility / 发现与不兼容 |
| --- | --- | --- |
| MCP transport | MCP `2025-11-25`; connection metadata `1.0` | `initialize()` checks the selected version before the initialized notification. Another version raises `MCP_PROTOCOL_INCOMPATIBLE`; absent/malformed version raises `MCP_RESPONSE_INVALID`. No silent downgrade. |
| Independent/desktop Python | Native transport `1`, UI-Core `1.0` | `Workspace.open/connect` check the ready transport identity. Call `require_schema()` before writes. `negotiate_schema()` retains a raw `incompatible` result; `require_schema()` raises `UI_CORE_SCHEMA_INCOMPATIBLE`. |
| Workspace/environment/one element | Core `1.0`; required MCP tools in `tools/list` | Core `get_workbench` maps to MCP `get_workspace`; `get_mod_element_editor` maps to `read_mod_element`. Environment is `get_workspace_environment` on both. |
| Field contracts | Environment's `fieldContracts`; entry's own `contractVersion` | The catalog has no `get_mod_element_field_contract`. Missing metadata/type uses `FIELD_CONTRACT_NOT_EXPOSED`; malformed metadata uses the entry's invalid-response code. Core's unavailable diagnostic propagates unchanged. |
| Element traversal | Core `1.0` cursor list; MCP `list_mod_elements` | Keep filter, fields, sort and limit fixed. Only `nextCursor=null` completes traversal; missing, malformed or repeated cursor fails explicitly. `LIST_CURSOR_STALE/INVALID` propagate without restarting. |
| Element/Procedure writes | Core `1.0`; named MCP tool's actual `inputSchema` | Preserve `expectedRevision`. Native Python accepts `expected_revision`; MCP tool arguments use `expectedRevision`. Raw Core conflict code is `WORKSPACE_REVISION_CONFLICT`; Native retains its existing `REVISION_CONFLICT` exception alias and all raw `details`. |
| Protected source writes | Core `read_workspace_file` / `update_workspace_file` | Native uses the read's `sha256` as `expectedSha256`, plus revision. This MCP catalog does not expose workspace-file tools. Do not substitute an unprotected write or take ownership of generated source. |
| Build/task recovery | Core `1.0`; MCP `build_workspace` / `get_task` | Start once and retain taskId. `wait_task` / `waitTask` accept a starting log sequence and checkpoint callback. Wrong task ID, unknown state or repeated/out-of-order logs are invalid responses. |
| Exact verified export | Passed packaged-JAR acceptance fields in Core `1.0`'s existing preview extension; MCP `export_verified_artifact` | Native maps to `export_workspace(verifiedTaskId=...)`. Require real artifact/report/source identities. Missing acceptance is unavailable; ordinary export is not a fallback. The backend rechecks evidence/input hashes. Historical export defaults to disabled. |

The table defines contract floors, not invented earliest product releases. The
available records do not establish every feature's first shipping version. For
an older installed host, inspect `serverInfo`, `data.application`, schema and
actual catalog/Native ready operations, and stop if required semantics are absent.
Tool presence does not itself grant permission or generator support.

上表给出最低协议／合同条件。各功能首次发布版本未全部由现有记录确定；旧宿主应按
实际身份、目录和能力检查，缺少必需语义时明确停止。

These changes add client helpers and local metrics, without extending frozen
wire schemas. Existing generic calls, successful envelopes and raw `details`
remain available. Additional checks reject responses that previously could look
like a complete list or a terminal task. The read retry allowlist is unchanged;
helpers/reconnection never replay writes. Generator/plugin extensions remain
[experimental](experimental-extension/README.md).

No SDK issues authority, supplies user approval, accepts EULA, or increases
transport permissions. Expired/revoked grants fail through Core. Cancellation
is a separate explicit command; ending a wait or MCP session is not cancellation.

SDK 不签发授权、代替用户批准或接受 EULA；授权过期／撤销后的敏感操作继续拒绝。
停止等待、断开连接与取消任务是不同操作。

## Fixed old-client regression / 固定旧客户端回归

The source-only Python fixture in a repository checkout's
`sdk/tests/legacy-clients/` contains the actual MCP client Git blob at
`b1a69d244ab7337608cc302826ed55d4a707f37f`, with immutable source/hash checks.
Its regression executes preserved public reads, pagination,
revision-bound writes, build/original-task polling and generic verified-export
calls against controlled current-protocol responses, alongside the current SDK.
Raw rejections and single-attempt uncertain writes are checked too. The old client
uses its original generic tools/manual polling; new helpers are not backported.
An incompatible-version fixture explicitly records the old implementation's
missing selected-version check and the current SDK's rejection before initialized
notification. It does not promise the new fail-closed behavior for old code.

This checks one fixed SDK's calling shapes and envelopes, not every old binary
host, real Core permission enforcement or accepted artifact bytes. New-client
incompatible negotiation, real Core authorization and packaged export keep their
separate checks and identity-bound evidence. Fixture source alone is not a passed
run; retain the actual unittest command/result when reporting compatibility.

固定旧版回归执行原始旧 SDK，验证保留的公开调用和原始错误；新便捷方法不据此声称
旧版支持。测试范围为受控传输与一个固定源码版本，真实 Core、权限和精确字节交付
仍分别验证；是否通过以实际执行回执为准。
