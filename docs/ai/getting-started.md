# MCP 接入快速开始

本文面向本机 AI 客户端和自动化集成开发者。Copperbench MCP 当前为开发预览协议，版本、Schema 和工具字段仍可能在预览版间变化。

当前源码新增了[任务授权与自动验收](./task-authorization-and-acceptance.md)：本机用户可以一次批准目录、操作和期限，agent 后续用授权 ID 连续开发；产品直接提供真实文件快照、GameTest 宿主和用例报告。使用这些接口需要包含改动的新构建。

## 安全边界

- 服务仅绑定 `127.0.0.1`，入口为 `/mcp`。
- 令牌按工作区隔离，不得提交到仓库或发送到远端日志。
- 权限档位为 Read Only、Workspace、Full Access。
- 写操作使用 `expectedRevision`；冲突必须重新读取，不能静默覆盖。
- 删除、外部发布、凭据导出和启用 Java 插件必须由用户确认。

完整规则见 [MCP 权限模型](../security/permission-model.md)。
需要让通用 Agent 实际完成“读取 → 修改 → 构建 → 冲突恢复”时，继续阅读
[外部 Agent 操作手册](./agent-playbook.md)。该手册同时给出 `create_mod_element`、Procedure IR、
Workspace Plan、增量任务日志和 `code` 使用边界的可复制示例。

## 推荐调用顺序

1. 初始化连接并读取服务能力。
2. 调用 `get_workspace` 获取工作区与当前 revision。
3. 使用 `list_mod_elements` 获取元素摘要。大型工作区使用 `cursor` / `limit` / `sort` / `filter` / `fields`，并持续使用响应里的 `nextCursor`，直到其为 `null`；旧 `page/pageSize` 仅保留一个预览周期兼容。
4. 对写操作先调用对应 preview 工具，检查诊断和引用影响。
5. 需要把多个内容写操作作为一个原子单元时，调用 `plan_workspace_changes`，再用 `preview_workspace_plan` 复核语义差异、权限和 revision；取得用户确认后调用 `apply_workspace_plan`。
6. 单项写操作仍以最新 `expectedRevision` 提交。
7. 运行校验或构建；长任务使用 `get_task` 查询状态和日志，并把最近收到的日志序号作为 `afterLogSequence` 传回以增量恢复。
8. 修订冲突时重新读取并重新生成计划，不要自动重试覆盖。

需要还原恢复点时，先调用 `preview_recovery_restore`。它比较当前工作树和目标恢复点，返回还原真正会涉及的文件；`restore_recovery_point` 接受用户签发的 `restore` 任务授权，或本机 UI 的明确批准，MCP 客户端不能自行声明桌面用户已经批准。

## 从空目录开始原生开发

Stage 14B 提供不依赖已有 `.mcreator` 文件的产品级 bootstrap 入口。外部 agent 先发现生成器，再使用用户签发的[任务授权](./task-authorization-and-acceptance.md)创建工作区。传入 `--no-prompt true` 后，缺少授权会立即返回可审查的 `USER_APPROVAL_REQUIRED`，便于自动化客户端处理；本机交互创建仍可省略该参数显示确认窗口。Agent 没有可自行批准的 `--approve` 参数：

```powershell
.\copperbench.exe bootstrap list-generators
.\copperbench.exe bootstrap create-workspace `
  --generator-id fabric-1.21.1 `
  --mod-name "Survey Pulse" `
  --mod-id survey_pulse `
  --workspace-folder "$env:USERPROFILE\MCreatorWorkspaces\survey_pulse" `
  --task-authorization '<user-issued authorization ID>' --no-prompt true
```

创建成功的 JSON 会返回新的 `.mcreator` 路径。随后使用正常 headless 或 Desktop MCP 入口读取真实工程环境；headless 形式为：

```powershell
.\copperbench.exe headless --workspace <path.mcreator> environment
```

`environment` / MCP `get_workspace_environment` 会返回当前 generator、Minecraft/Loader、Gradle、JDK、源码/资源根目录以及原生优先工作流提示。Windows 产品本身运行在随包 JBR 25 上；需要 Java 21 的 Minecraft/Gradle 轨道使用安装包内独立的 `jdk21` sidecar。外部 Agent 不应把应用 JVM 当成工作区 Java 版本。

编码前还应检查实际生成的 `build.gradle` 中的映射声明。目前 `environment` 未单独返回映射方案；本阶段 Fabric 1.21.1 工程使用 `loom.officialMojangMappings()`，不能仅凭 Fabric 加载器推断为 Yarn 符号。依赖文档应与工程声明的映射和版本对应。

之后可以直接用 IDE 或普通文件工具编辑工作区内 Java、资源和测试文件，再通过 `headless ... build`、Desktop MCP `build_workspace` / `get_task` 或原生 Gradle Wrapper 获取真实编译诊断。故意或意外产生的编译错误应按诊断定位、直接修复文件并重新构建；不需要把整段 Java 重新包装成结构化 JSON。外部文件修改仍受源码指纹、revision、归属冲突和 recovery point 保护，Copperbench 也不会仅因文件已经写入就把它报告成已编译或行为已验证。

这个 bootstrap 入口用于创建 Copperbench 工作区；任意第三方 Fabric/NeoForge 工程的完整导入仍不是 Stage 14B 的隐含承诺。原生 `gradlew` 继续可直接使用，Copperbench 提供的是上下文、诊断、审阅、引用和恢复层，而不是替代通用 Agent 或 IDE。


JCEF 客户端会接收任务的 `task_progressed`、`task_log_appended`、
`task_completed` 和失败诊断事件；页面重载时会重放 Core 保留的任务事件，
如果发现序列缺口则自动刷新工作区和任务投影。MCP/Headless 客户端继续使用
`get_task` 轮询作为兼容路径，直到任务事件订阅协议单独冻结。

无界工作区列表统一使用 `cursor` / `limit` / `sort` / `filter` / `fields` / `nextCursor`，单页最多 200 项。当前覆盖 `list_mod_elements`、`list_recovery_points`、`list_publish_batches`，以及指定 `registry` 后的 `list_workspace_registries`；空 payload 的桌面 UI 旧投影仍保留。Cursor 绑定数据集和查询条件，workspace revision 变化返回 `LIST_CURSOR_STALE`，数据集或查询条件变化返回 `LIST_CURSOR_INVALID`。大型工作区集成必须遍历到 `nextCursor=null`。

`plan_workspace_changes` 当前覆盖元素 create/update/delete、Procedure 更新，以及 registry create/update/delete/rename。计划记录统一 `baseRevision`、`idempotencyKey`、预分配对象 ID、语义差异、权限评估和目标状态摘要；`apply_workspace_plan` 成功时只推进一次 workspace revision，并只建立一个计划级恢复点。`planId` 是可审计的内容摘要，`planToken` 是当前 Copperbench 会话签发的 HMAC 认证令牌；客户端不得自行生成或修改两者。旧 revision 返回 `WORKSPACE_PLAN_STALE`；对已经达到同一目标状态、且持有当前会话有效 `planToken` 的同一计划重放不会再次推进 revision。Copperbench 重启后旧 `planToken` 会失效，应重新读取 workspace 并生成新计划。构建、运行、迁移、导入和外部发布等长任务/边界操作不进入这一批原子内容计划。

## 本地可复用模板

Stage 14D 的模板入口用于复用已经验证过的一组元素、Procedure 与资产，而不是替代原生文件编辑。模板默认只保存在当前用户的 `~/.copperbench/templates`，不上传云端，也没有远程模板商店。

推荐 MCP 流程：

1. 在源工作区调用 `create_local_template`，传入 `templateName`、需要打包的 `elementIds`、可选的工作区相对 `assetPaths`、`description` 与最新 `expectedRevision`。导出是本地模板操作，不推进 workspace revision。
2. `create_local_template` 只接受自包含的结构化依赖集合：选中的元素/Procedure 如果引用模板外元素或 Registry、存在悬空引用、异常 ownership，或资产路径越界/经过符号链接，创建会失败。资产内容与 SHA-256 会嵌入模板文件。
3. 在目标工作区调用 `list_local_templates`，再用 `preview_local_template_instantiation` 提交 `templateName`、目标 `expectedRevision` 和新的 `idempotencyKey`。目标 generator 必须与模板一致；实例化会为模板内元素分配新的稳定 ID，并重写模板内部 UUID 引用。
4. preview 返回的是标准 Workspace Plan。检查 `semanticDiff`、`changedPaths`、`review`、`permission`、`safety` 和 `templateAssets` 后，把**未经修改的** `plan` 交给现有 `apply_workspace_plan`。
5. 元素/Procedure 与资产在同一个 revision、同一个 recovery point 和同一个文件快照事务中提交。目标资产已经存在时 preview 返回 `WORKSPACE_PLAN_SOURCE_CONFLICT`；修改模板内容、Base64、路径、SHA、`planId` 或 `planToken` 会在写入前被完整性校验拒绝。

典型调用顺序为：

```text
create_local_template
list_local_templates
preview_local_template_instantiation
apply_workspace_plan
```


## 开发验证

仓库提供测试用 MCP 服务和一致性脚本：

```powershell
pwsh -NoProfile -File .\scripts\verify-mcp-conformance.ps1 `
  -OutputDirectory build\mcp-conformance-results
```

脚本要求 JDK 25、Node.js 和 npm。结果写入指定目录，CI 同样保存该报告。工具 Schema 的基线测试位于 `ui-core/tests/schema.test.mjs`。

FR-AI-02 已由 PR #14、合并后 main CI 和 `main@515c212c` 的 Nightly `32998281437` 正式关闭；`list_new_workspace_generators`、`list_installed_plugins` 等产品有界目录不进入这一分页门禁，`get_workspace_references` 属于图查询并由独立的 2,000/10,000 引用性能门禁约束。FR-AI-03 Workspace Plan、FR-AI-04 Task Events/reconnect 与 FR-AI-05 SDK/evals 已由固定提交 Windows Nightly `33098518016` 在明确 source `main@e8caf018` 上正式关闭：完整 Java/Javadoc/scale、Playwright、MCP conformance 与 8/8 generator golden 均通过，Windows-only `Stage10NativeJcefTaskReconnectTest` 在真实 JCEF/Chromium 中 1/1 执行通过；Workspace Plan 的支持边界复核继续把构建、运行、迁移、导入和外部发布留在原子内容计划之外。`sdk/typescript`、`sdk/python` 和 10 项 `sdk/evals` 已交付，专用真实 HTTP MCP live eval **10/10** 通过；Nightly 证明合并后的实现处于全绿产品基线，但不冒充再次执行 live-eval harness。MCP 继续以版本化 `get_task(afterLogSequence)` 作为恢复路径，不额外引入未冻结的自定义 push notification 方言。完整能力见 [下一阶段 PRD](../../PRD-NEXT.md)，客户端仍应把当前协议视为 `0.x` 预览。
