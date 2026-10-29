# 简洁创作模式完整 Review · 2026-10-21

后续状态：以下 7 项问题已完成修复，见[修复与回归记录](simple-creation-fixes-2026-10-21.md)。本文保留修复前的发现和复现依据。

本轮检查整个简洁版选项：默认模式与切换、草稿保留、新建/打开作品、真实资产预览和参数编辑、恢复点、试玩/导出、五个 CLI 的聊天/审批/续聊/退出，以及进度窗口和按钮对比度修复。

结论：确认 7 项 P2 问题。产品源码未修改；新增的隔离复现程序和证据位于 `.tmp/` 与 `output/simple-creation-review/`。以下浏览器复现使用 Mock Core，不会改动真实作品或启动 Minecraft。

## Findings

### 1. [P2] 删除内容后，遗留参数草稿会持续阻止聊天和试玩

位置：`ui-shell/src/components/SimpleCreationView.tsx:83`。

触发：在简洁模式修改一个元素的设置而不应用，再从专业工作台删除该元素，或通过外部工作区更新移除它。`parameters` 仍保留旧元素的草稿，`hasParameterDrafts` 继续为真；该元素已不在列表中，右侧也不再提供“放弃输入并重读”。用户看到要求处理草稿的提示，却没有可用入口，发送和试玩一直禁用。

已用隔离 Core 删除事件复现：元素消失、发送按钮禁用、没有丢弃入口。专业编辑器的正常删除路径只清理自身的草稿缓存，不清理这里的缓存。

建议：在元素移除时处理对应草稿，或提供可访问的孤立草稿管理/丢弃入口。不要让不可见、已无法应用的草稿永久锁住操作。

证据：`removed-element-draft-blocked.png`。

### 2. [P2] 完成一次新建后，同一窗口无法再次新建作品

位置：`ui-shell/src/App.tsx:83`；关联 `NewWorkspaceView.tsx:178`、`:245`。

新建表单改为常驻组件后，成功的 `result.status === 'committed'` 会一直保留。完成创建、返回创作、再次点击“新建作品”时仍显示旧成功横幅；即使输入新名称，提交按钮也始终禁用。原来离开页面时的卸载重置已不存在，又没有“开始另一件作品”的重置动作。

已在完整浏览器界面复现。原生打开动作创建另一个工作区窗口，不负责重置原窗口的表单。

建议：区分“返回未完成草稿”和“开始新的创建请求”，后者重置成功回执、确认状态及默认目录/命名空间。

证据：`second-creation-blocked.png`。

### 3. [P2] 未发送的聊天输入关闭时会无提示丢失

位置：`ui-shell/src/components/SimpleCreationView.tsx:143`、`:147`。

聊天草稿只保存在 React 的 `drafts` 中，没有持久化，也没有计入 `setUnsavedDraftCount`。只有未发送文字、没有正在运行的 Agent 或其他编辑草稿时，原生 `confirmClose` 得到零草稿，直接允许关闭。

已复现长段聊天输入存在时 `hasUnsavedDrafts() === false`，并用参数编辑验证同一草稿守卫正常返回 true，排除了导入了另一份模块的测试误差。

建议：将非空聊天草稿计入退出保护，或按工作区/对话持久化并在重开时恢复。

证据：`chat-draft-guard.json`、`draft-probes-final.log`。

### 4. [P2] 简洁模式试玩没有提示专业编辑器中的未保存修改

位置：`ui-shell/src/components/SimpleCreationView.tsx:257`。

在专业工作台修改元素显示名称但不保存，切回简洁模式后，专业草稿被正确保留，但试玩按钮只检查简洁参数草稿。全局草稿守卫已经为 true，点击仍直接发出 `run_client`，没有说明这次试玩只使用旧的已保存内容。

已用 Mock Core 复现命令发出，未启动真实游戏。导出弹窗和已有首作指引已经使用全局草稿提示，可复用一致行为。

建议：检查共享草稿状态，在试玩前要求保存，或明确确认使用已保存内容。

证据：`professional-draft-playtest.json`。

### 5. [P2] 澄清问题被统一变成单选，丢失多选和自定义回答能力

位置：`src/main/java/dev/copperbench/agent/ClaudeAgentProtocol.java:116`；关联 `CodexAgentProtocol.java:93` 和 `SimpleCreationView.tsx:297`。

Claude 的 `AskUserQuestion.multiSelect` 没有传入界面，选中一个按钮就提交答案；有预设选项时也不会出现自由输入框。Codex 请求中的 `isOther` 同样被丢弃。比如模型允许同时选择“发光、飞行”，现在点“发光”就结束问题，无法提交两项或其他自定义内容。

已向实际适配器输入 `multiSelect: true` 复现：第一次点击后立即生成完整 control response，答案只有 `Glow`。字段合同已与 [Claude 官方用户输入文档](https://platform.claude.com/docs/en/agent-sdk/user-input) 和本机 Codex 生成的 JSON Schema 核对。

建议：在桥接合同中保留问题类型、多选和自定义回答能力；由显式提交按钮一次性返回完整答案。

证据：`clarification.json`；Codex Schema 位于本机 `C:/tmp/smart-search-evidence/20261021-codex-protocol-schema/ToolRequestUserInputParams.json`。

### 6. [P2] DeepSeek 成功纠正工具错误后，整轮仍会被误报为失败

位置：`src/main/java/dev/copperbench/agent/AcpAgentProtocol.java:75`。

`failedTools` 按单次工具调用 ID 记录失败，但重试会使用新的 ID。首次读取错误路径失败、随后读取正确路径成功、最后正常返回 `end_turn`，旧失败 ID 仍然存在，最终状态被改为 `failed`；界面随之显示“本次任务未完成”。

真实子进程 fixture 已复现这组合法 ACP 消息。失败日志应该保留，但它不能单独证明整个已修复的任务仍然失败。

建议：区分工具历史告警与最终任务状态。若要区分 Harness 的 blocked/aborted 终态，应使用明确的终态信息，不能用“本轮曾发生工具错误”替代。

证据：`reproduced-agent-cases/agent-reproductions.json` 的 `recovered-tool`。

### 7. [P2] CLI 在关闭会话时退出，界面仍会等待至 RPC 超时

位置：`src/main/java/dev/copperbench/agent/AgentRun.java:109`；关联 `:52`、`:164`。

新增 `session/close` 握手在本轮 outcome 完成之后运行。若 CLI 在回传 close 回应前退出，stdout EOF 分支因为 `outcome.isDone()` 而跳过失败处理，待完成的 close RPC 没有被唤醒。界面继续处于 running，阻止发送和切换对话，直到 60 秒超时，并错误提示“初始化或会话恢复超时”。

已用实际 fixture 子进程复现：正常返回本轮结果，收到 `session/close` 后退出；7 秒后子进程已不存在，但服务仍为 `running`、`busy: true`、无错误。

建议：EOF 时始终结束仍在等待的 RPC；分别记录对话完成和会话关闭状态，并给出对应的关闭错误。

证据：`reproduced-agent-cases/agent-reproductions.json` 的 `close-eof`。

## Claude Code 新源与模型重试

| 模型参数 | 检查结果 |
| --- | --- |
| `deepseek-ai/deepseek-v4.1-flash` | 应用集成路径持续出现 `server_error` 重试；180 秒内未完成，测试主动停止。 |
| `deepseek-v4.1-flash` | 原生 `claude -p` 成功返回 `COPPERBENCH_CONNECTED`；随后应用的实际 Java 流式集成也通过，最终 `idle`、无错误。 |
| `deepseek-ai/deepseek-v4-flash` | 按“前一个不行再试”的要求，无需继续使用备选；本轮未测试。 |

本轮使用本机 `C:/Users/Administrator/.local/bin/claude.exe`，临时包装器仅添加当前测试的 `--model`；没有修改源、认证或默认模型。`unrecognized_model` 诊断在成功调用中也存在，因此不能单独将它当作连接失败的依据。

证据：`claude-v41-no-prefix/direct-result.json`、`claude-v41-integrated/result.json`。成功范围是固定回复与流式生命周期；未用真实 Claude 工具修改作品。

## 覆盖与验证边界

| 范围 | 检查与结论 |
| --- | --- |
| 模式默认值、切换、编辑器保留 | 检查存储迁移与常驻组件；发现上述新建和草稿状态问题。 |
| 五个 CLI | 检查发现、启动参数、会话恢复、权限、停止、输出和持久化；新增 3 个协议/生命周期复现案例。 |
| 桥接边界 | 核对可信主框架 URL、请求限制、服务所有权和原生选择器；未发现新的越界执行问题。 |
| 资产与参数编辑 | 确认使用真实资产投影、字段合同、预览与 revision；发现被删除元素遗留草稿问题。 |
| 恢复点、试玩、导出 | 沿用现有 Core 操作；导出已有未保存输入提示，简洁试玩缺少跨模式草稿检查。 |
| 图标、主题、紧凑布局 | 检查本地 SVG、许可、键盘选择和既有 500×600 截图；本轮 Impeccable 源码检测结果为 `[]`。 |
| 原生进度和按钮 | 当前源码 SHA-256 与原先组件渲染/测试证据相同，重新查看了进度详情和浅色默认按钮截图，未发现新问题。未重新操作用户桌面。 |

上一轮 31 项 Java 和 6 项主场景检查通过；其源码和 JAR 身份在本轮再次核对一致，因此未重复完整构建。新增的 4 个浏览器复现用例与 3 个协议/生命周期案例用于确认缺陷，不能将这些“复现断言通过”误称为功能已修复。

本轮没有启动 Minecraft，也没有发布、提交代码或替换用户安装版。

## 界面技术审查摘要

界面视觉系统一致，使用既有主题、图标和真实内容。实现完整性暂不通过：草稿生命周期和创建状态的缺陷影响核心操作，不能仅凭样式检测无发现宣布可交付。

| 维度 | 分数 | 依据 |
| --- | --- | --- |
| 可访问性 | 3/4 | 标签、键盘选择和焦点处理已实现；复杂澄清输入语义缺失。 |
| 性能 | 3/4 | 有可见性感知轮询、输出限制和进程所有权；本轮未做压力基准。 |
| 主题 | 4/4 | 本地品牌图标与明暗令牌一致；原生按钮渲染证据匹配源码。 |
| 自适应 | 3/4 | 已有 500×600 截图和视口断言；本轮未测触屏。 |
| 实现完整性 | 2/4 | 7 项已确认问题，主要集中在状态边界。 |
| 合计 | 15/20 | 视觉基础良好，核心状态问题需要修复。 |

建议优先处理草稿、新建和输入合同，再处理 ACP 终态/断线；修复后用对应复现用例回归。界面收尾可按 `/impeccable harden`、`/impeccable polish` 顺序检查。

本机复现程序、截图、CLI 回执和源码身份记录统一保存于 `output/simple-creation-review/`。
