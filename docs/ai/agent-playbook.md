# 外部 Agent 操作手册

本文给通过本机 MCP 操作 Copperbench 工作区的 AI Agent 使用。目标是让 Agent 在不绕过 revision、权限、恢复点和任务日志的前提下完成“读取 → 修改 → 构建 → 修复冲突”的闭环。

## 1. 连接规则

打开工作区后，桌面 Copperbench 会在工作区写入：

```text
.copperbench/mcp-connection.json
```

该文件只包含监听状态、loopback URL、`workspaceId`、权限档位和过期时间，**不包含令牌**。令牌只能由用户在“AI 与 MCP”页显式显示一次。

Agent 必须遵守以下规则：

- 只使用连接文件中的 `url`，不要假定固定端口（例如 `8787`）。
- `workspaceId` 必须与 `get_workspace` 返回值一致。
- 不要把令牌写入仓库、普通日志、诊断包、聊天内容或任务描述。
- 不要尝试从连接文件、进程命令行或日志中恢复令牌；令牌不可用时应要求用户重新配对。
- 所有写操作都使用最新 `expectedRevision`，出现冲突后重新读取，不得静默覆盖。

## 2. 推荐闭环

一次可靠的内容修改按以下顺序执行：

1. MCP `initialize`，然后发送 `notifications/initialized`。
2. `get_workspace`，记录 `workspaceId` 和 `revision`。
3. `list_mod_elements`，使用 `limit` 和响应中的 `nextCursor` 连续读取，直到响应明确给出 `nextCursor: null`。
4. 对单个元素先使用 preview 工具；多个内容修改优先使用 `plan_workspace_changes`。
5. `preview_workspace_plan` 检查语义差异、权限和 stale 状态。
6. `apply_workspace_plan`，并传入计划基于的 `expectedRevision`。
7. `build_workspace`。
8. 用 `get_task({taskId, afterLogSequence})` 增量读取日志；每次把已处理的最大 `sequence` 作为下一次 `afterLogSequence`，直到任务进入终态。
9. 若写操作返回 `WORKSPACE_REVISION_CONFLICT`，重新调用 `get_workspace`，重新检查目标状态，再以新 revision 生成操作或计划。
10. 若计划返回 `WORKSPACE_PLAN_STALE`，废弃旧计划并从最新 workspace 重新计划；不要修改或伪造 `planId` / `planToken`。

## 3. `create_mod_element` 的 `initialValues`

`create_mod_element` 必须同时提供 `elementType`、`name`、`initialValues` 和 `expectedRevision`。`initialValues` 可以很小；Copperbench 会补齐通用默认值。不要凭空发明当前 schema 没有定义的字段。

### Item：先创建物品，再实现蓄力逻辑

```json
{
  "elementType": "item",
  "name": "charged_blade",
  "initialValues": {
    "displayName": "Charged Blade",
    "fields": {
      "maxStackSize": 1
    }
  },
  "expectedRevision": 12
}
```

当前 MCP 创建协议没有一个可通用于各生成器的 `chargeTicks` 魔法字段。长按蓄力、按住期间持续更新、松开发射等行为应在 Procedure 或 `code` 元素中实现，而不是向 Item `initialValues` 填未经 Core/生成器定义的字段。

### Projectile：最小可编辑投射物

```json
{
  "elementType": "projectile",
  "name": "flying_sword",
  "initialValues": {
    "displayName": "Flying Sword",
    "disableGravity": true,
    "igniteFire": false
  },
  "expectedRevision": 13
}
```

其余字段应在创建后读取元素投影/编辑器能力再补充，不要跨 Minecraft/Loader 版本照抄教程字段。

### Procedure：空元素 + 结构化 IR 编辑

Procedure 可以先以空 `initialValues` 创建：

```json
{
  "elementType": "procedure",
  "name": "charged_blade_use",
  "initialValues": {},
  "expectedRevision": 14
}
```

随后调用 `get_procedure` 取得当前 IR，再用 `preview_procedure_change` / `update_procedure` 提交结构化 `edits`。最小触发器修改：

```json
{
  "elementId": "<procedure element UUID>",
  "edits": [
    {
      "operation": "set_trigger",
      "trigger": "on_block_right_clicked"
    }
  ],
  "expectedRevision": 15
}
```

需要增加节点时使用 IR 的 `add_node`，例如一个最小 statement 节点：

```json
{
  "operation": "add_node",
  "node": {
    "id": "<new UUID>",
    "type": "text_print",
    "kind": "statement",
    "x": 100,
    "y": 100,
    "fields": {},
    "inputs": {}
  }
}
```

添加节点后再使用 `connect` 等结构化编辑连接控制流，并始终先 preview。未知 Blockly/plugin 节点会作为 opaque payload 保存，Agent 不应重新序列化或“清理”其未知字段。

### Code：复杂实时行为的逃生舱

```json
{
  "elementType": "code",
  "name": "charged_blade_runtime",
  "initialValues": {
    "code": "public final class ChargedBladeRuntime { }"
  },
  "expectedRevision": 16
}
```

环绕实体持续碰撞、按 tick 蓄力决定数量、一次释放多枚实体并维持自定义状态等行为，在 Procedure 无法清晰表达时应使用 `code`。不要为了“纯 Blockly”而制造不可维护的图。

直接调用 `create_mod_element` 创建 `code` 时，成功响应的 `data.compileVerification` 会包含一个
`build_workspace` task。继续调用 `get_task(taskId, afterLogSequence)` 直到终态：真实 javac 编译错误会作为
`JAVA_COMPILE_ERROR` 返回，message 含行号，`path` 指向工作区内的 Java 源文件。不要只检查顶层 create
是否 `committed` 就假定 Java 可编译。通过 `plan_workspace_changes` 批量创建 `code` 时不会隐式启动构建，
apply 后应显式调用 `build_workspace`。

使用 `code` 时仍要遵守工作区所有权边界：

- 不覆盖 `// Start of user code block` / 对应结束标记中的用户内容。
- 不删除 Agent/用户已有的独立 Java 包。
- 修改已有代码前先读取目标文件/元素并确认 ownership；不要以“重新生成整个类”代替精确修改。

## 4. 原子计划示例

创建一个 `code` 元素可以放入计划：

```json
{
  "expectedRevision": 20,
  "idempotencyKey": "charged-blade-runtime-v1",
  "operations": [
    {
      "operation": "create_mod_element",
      "payload": {
        "elementType": "code",
        "name": "charged_blade_runtime",
        "initialValues": {}
      }
    }
  ]
}
```

把 `plan_workspace_changes` 返回的完整 plan 原样传给 `preview_workspace_plan`；确认 `wouldApply`、diagnostics 和 permission 后，再把同一 plan 原样传给 `apply_workspace_plan`。不要手改 `planId`、`planToken` 或预分配 ID。

构建、`run_client`、迁移、导入、发布等长任务不是 Workspace Plan 的内容步骤；内容计划提交后再单独启动这些任务。

## 5. 任务日志恢复

`build_workspace` 返回 `accepted` 和 `task.id` 后，从 `afterLogSequence: 0` 开始调用 `get_task`。若本次返回日志的最大 sequence 是 `37`，下一次请求使用：

```json
{
  "taskId": "<task UUID>",
  "afterLogSequence": 37
}
```

不要反复从 0 拉取全部日志。终态至少包括 `succeeded`、`failed`、`cancelled`；失败时应读取结构化 diagnostics 和原始任务日志，而不是只根据最后一行猜原因。

对于 JDK 解析失败，产品会返回 `BUNDLED_JDK_MISSING` 并列出尝试的 Java home。不要把这种错误重新解释成 “readiness marker 未出现”。

## 6. 冲突恢复

遇到：

```text
WORKSPACE_REVISION_CONFLICT
```

正确行为是：

1. `get_workspace` 获取新 revision。
2. 重新读取将被修改的元素/引用。
3. 判断目标变更是否仍适用。
4. 重新 preview/plan。
5. 用新 revision 提交。

禁止直接把旧请求里的 `expectedRevision` 数字改大后重放，因为这会跳过对其他写入的语义检查。

## 7. `run_client` 的语义

桌面“测试客户端”是长寿命的 Loom/开发客户端任务。Minecraft 运行期间任务保持 `running`；用户关闭客户端后正常退出才结束。`COPPERBENCH_*_READY` 字符串属于 smoke/readiness 验证信号，不是创作者桌面客户端的自动终止条件。

Agent 不应因为标题画面看起来接近原版就判断 Fabric 或模组未加载；应以任务日志和 Minecraft `run/logs/latest.log` 的 Loader/mod 初始化记录为准。

## 8. 最低安全清单

- 连接信息来自当前工作区，而不是固定端口。
- 令牌只在受信任客户端内存中使用，不落普通日志和聊天。
- 列表遍历到 `nextCursor: null`。
- 每次写入使用最新 revision。
- 复杂批量修改先 plan + preview。
- 冲突后重读，不静默覆盖。
- 任务日志按 sequence 增量恢复。
- 保留未知字段、用户代码块和非生成器所有的代码。
- 复杂实时行为允许使用 `code`，不强迫所有逻辑进入 Procedure。
