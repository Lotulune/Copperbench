# Public routes / 公开调用路线

Install with `python -m pip install ./sdk/python`, then choose one entry. Fixed
routes use an existing isolated workspace. No example creates user grants,
accepts EULA or deletes a workspace.

先安装随包 Python SDK，再选择一个入口；固定回归使用独立工作区。

| Context / 情境 | Minimal example / 最小示例 | Prerequisite and exit / 前提与退出 |
| --- | --- | --- |
| Independent Python / 独立脚本 | `python sdk/python/examples/native_workspace.py <workspace.mcreator> --launcher <copperbench.exe>` | Closed in other hosts; `Workspace.open` holds its write lock. Leaving `with` stops its host. Keep it alive until long tasks/client exit. |
| Open desktop / 已打开桌面 | `python sdk/python/examples/live_workspace.py <workspace.mcreator> --element <name-or-id>` | Same OS user and open workspace. `Workspace.connect` shares live Core; leaving `with` disconnects only the script. |
| MCP client / 通用 MCP | `python sdk/python/examples/mcp_workspace.py <workspace.mcreator> --metrics` | Enable/pair in the desktop UI. Use its hidden token prompt or `COPPERBENCH_MCP_TOKEN`, never command arguments or connection metadata. `close()` deletes only this MCP session. |

MCP metadata is non-secret loopback discovery; Native's OS-private discovery is
separate. A lost desktop session never becomes an independent host automatically.
See [compatibility](compatibility.md) for actual names and contract floors.

连接文件仅包含回环端点等非秘密信息；原生连接另有发现机制。桌面失联不会自动切换宿主。

Read `get_workspace_environment` once for the current preparation step. Inspect
generator, execution/toolchain, application identity and `data.fieldContracts`
restrictions. Unexposed mapping configuration is unknown; use public project
configuration when needed rather than inferring it from a loader name. Ordinary
discovery does not create probes, change revision or download dependencies.

发现先读取真实生成器、工具链、合同与限制；未暴露的映射仍未知。故意错误输入只属于 W1。

Pass the fresh environment to `field_contract(environment=...)` /
`fieldContract(type, environment)` to avoid a duplicate full read. This is
explicit reuse, not a TTL cache. Re-read after reconnecting or changing workspace,
generator or contract context. Get applicable revision/hash before writes. Use
selected list fields and single-object reads. On `LIST_CURSOR_STALE`, discard
the partial set and explicitly traverse again to `nextCursor=null`.

显式复用本次 environment，避免重复读取；不缓存写入 revision/hash。
游标过期后丢弃不完整集合，不能混合两个 revision。

## W1: create, fail, fix / 创建、失败、修复

```powershell
python sdk/python/examples/agent_workflows.py w1 --transport desktop --workspace D:/Acceptance/sample/sample.mcreator --output D:/Acceptance/evidence/w1
```

The runner discovers writable numeric hardness, rejects one string value, saves
the original diagnostic/code, compares content hashes/revision, then submits
one corrected create and one build. Its summary contains W2's `elementId`. Use
a fresh `--name` when repeating W1; existing elements are refused, not recreated.
Independent/MCP modes execute shared steps with their actual envelopes.

定位 hardness 错误，确认失败未写入，修正后创建一次并构建；重复运行使用新的名称。

## W2: retain the second edit / 保留第二次修改

```powershell
python sdk/python/examples/agent_workflows.py w2 --transport desktop --workspace D:/Acceptance/sample/sample.mcreator --element-id <W1-element-id> --source-path src/main/java/example/AcceptanceFixture.java --output D:/Acceptance/evidence/w2
```

Provide an editable manual `.java` fixture in this isolated project. The runner
uses the public API for a controlled second edit, rejects the old revision and
rereads the field. It then appends a source comment outside Core and verifies
the stale hash cannot overwrite those bytes at the same revision. Both edits
remain for inspection; generated/manual ownership is retained and the stale
draft is not resubmitted. This is an engineering regression, not a human/Agent study.

传入可编辑手写 Java 文件；第二次字段修改和外部注释均保留。旧 revision 和同 revision
的旧 hash 均须拒绝。此为受控工程回归，不是人与 Agent 研究。

MCP W2 covers revision only and reports `sourceHashConflict=not_exposed`,
`status=completed_with_limits`; its catalog has no protected workspace-file tools.

MCP 明确报告源码接口未暴露，不将部分结果写成完整 W2 通过。

## W3: resume and export / 恢复与精确导出

First obtain a real passed `packaged_jar` GameTest task with positive acceptance
counts and artifact/report/source hashes. Preparation and acceptance execution
are explicit separate operations. Then:

```powershell
python sdk/python/examples/agent_workflows.py w3 --transport desktop --workspace D:/Acceptance/sample/sample.mcreator --acceptance-task <passed-packaged-jar-task-id> --output D:/Acceptance/evidence/w3
```

The runner starts one build, persists taskId/checkpoint, disconnects, reattaches
to the same live host, polls that original task and exports the selected real
acceptance artifact once. It compares copied JAR/XML bytes, `verification.json`
and acceptance/source identities. Ordinary builds cannot satisfy the route.
Current-input export is required; historical export remains a separate explicit
SDK choice.

W3 保存原 build taskId 再重连；精确导出绑定真实 JAR 验收和相同输入。
普通构建不能替代验收，默认不导出历史输入。

After interruption, use `checkpoint.json` or the saved start receipt:

```powershell
python sdk/python/examples/agent_workflows.py w3 --transport desktop --workspace D:/Acceptance/sample/sample.mcreator --task-id <original-build-task-id> --after-log-sequence <N> --acceptance-task <passed-packaged-jar-task-id> --output D:/Acceptance/evidence/w3-resume
```

`--task-id` starts no build. Keep earlier logs with the new evidence directory.
W3 teardown uses desktop/MCP: closing an independent `Workspace.open` stops its
host and cannot demonstrate running-task reconnection. A lost start response
without a task ID is an uncertain write; reconcile `recentTasks` before deciding
on an explicit new start. The SDK does not deduplicate/replay that request.

传入原 taskId 后不会重新启动构建；新旧日志一起保留。独立宿主关闭影响任务，不能当作
仅断开传输。启动响应丢失且没有 taskId 时，先核对任务，禁止自动重放。

`wait_task(..., after_log_sequence=N, on_update=save_increment)` and
`waitTask(id, { afterLogSequence: N, onUpdate: saveIncrement })` checkpoint each
original increment before the next read. Returned/timeout envelopes contain
logs received in that wait and the actual known task state. Transport errors
preserve raw `details`; resume from the last successful checkpoint. A failed or
cancelled task is terminal, not success. Restored running observations after
host loss are `unconfirmed_after_session_loss`, not proof the original process
failed. Historical records retain up to 10,000 entries and an 8 MiB journal
tail, so retained history may be incomplete.

超时只停止等待，保留本次日志与已知状态；传输错误保持原始 details。
宿主丢失后的观察仍有未确认范围，历史日志有容量上限。

## Receipts and counts / 回执与计数

Each run creates a new output directory outside the workspace. It saves real
environment identities, diagnostics/receipts, incremental polls, checkpoint
and summary, distinguishing failure, partial coverage and pass. Known pairing
tokens/grant IDs are redacted in evidence. Example code is not an already-passed run.

回执保存真实身份、原始失败、增量日志和结论；示例代码不代表已执行验收。

Opt in with `ClientMetrics()` and Python `metrics=collector` or TypeScript
`{ metrics: collector }`. `snapshot()` reports logical wire calls, actual
HTTP/IPC attempts, complete UTF-8 response bytes by operation and elapsed time.
Initialization/discovery are counted; retries increase attempts, not logical
calls. Headers/interrupted bodies and Native's startup ready line are excluded.
No payload, task ID or credential is retained in metrics. Workflow summaries
also record rejection reasons, build/export starts and received/repeated logs.
Compare the same fixed inputs, host, permissions and scope; bytes are not
unmeasured token estimates or proof of a performance improvement.

指标区分逻辑调用与传输尝试，记录字节，不换算未测量的 token 成本。
比较使用相同输入、宿主、授权和范围。
