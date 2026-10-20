# DeepSeek Harness 接入与 Claude GLM 重试 · 2026-10-21

DeepSeek Harness 已加入简洁创作模式，使用本机官方 `dsh`，名称前显示蓝色鲸鱼图标。新增后端、明暗主题、窄窗口及原生连接均完成下述验证。产物为 `build/libs/copperbench.jar`；未安装到用户现有安装版，也未提交或发布。

后续[完整 Review](simple-creation-review-2026-10-21.md) 确认了 7 项待修复问题；用户再次换源后，Claude Code 使用 `deepseek-v4.1-flash` 的原生与内置聊天检查均通过。下面保留当时 GLM 模型与旧源的历史结果。

## 实现

- 五个后端分别保存对话；DeepSeek 使用 `dsh --profile acp`，继承现有 Harness 配置。
- Grok 与 DeepSeek 共用 ACP 消息和审批处理，分别使用 `session/load` 与 `session/resume` 恢复。DeepSeek 恢复原会话 ID，不因不支持恢复而静默创建新会话。
- DeepSeek 审批请求只携带工具 ID，界面通过此前 `tool_call` 的名称和参数还原操作详情。缺失详情时取消请求。拒绝、工具失败、协议错误及关闭时的持久化失败均保留。
- 完成后等待已声明支持的 `session/close` 回应，再关闭输入并确认所属进程结束。用户停止通过 `session/cancel` 和既有进程生命周期处理。
- DeepSeek 提供“配置指南”，指向官方说明；模型配置沿用 `dsh web` → Settings → Models。
- 图标使用既有固定版本 LobeHub 包中的 `deepseek-color.svg`，保留 MIT 声明。实际构建资源中的图标、原始蓝色和版权声明已检查。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| Java 编译、TypeScript/Vite、JAR | 通过。 |
| 中英文检查 | 387 个引用键、1705 条消息，通过。 |
| Java Agent 与 JCEF Agent 桥接 | 31 项通过，0 失败、0 错误。 |
| Playwright 简洁创作场景 | 6 项通过，包括五后端切换、审批、停止、重开、图标与键盘选择。 |
| 界面目视检查 | 明暗菜单和 500×600 紧凑窗口中，五个品牌图标、名称、选中状态及操作可见。 |
| 本机 DeepSeek Harness `0.2.1-rc.2` | 官方 `dsh.cmd` 经实际 Java 服务启动，返回 `COPPERBENCH_CONNECTED`，状态 `idle`、无错误，进程收尾通过。 |
| 本机 Grok Build 回归 | 共用 ACP 代码后，真实连接与固定回复通过。 |
| Claude Code + `z-ai/glm-5.3` | 集成路径和直接原生 `claude -p` 均返回 `Failed to authenticate. API Error: 403 安全验证`，退出码 1；本次未连通。 |

Claude 重试使用仅本次进程的 `--model z-ai/glm-5.3`，没有更改用户的源、认证或全局模型配置。CLI 同时输出 `unrecognized_model` 诊断，但实测终态错误是上述 403。该结果替代此前旧源的 400 观察，不能记作成功对话。

本机 DeepSeek 首轮检查发现账号模型目录插件把 `console.info` 日志写到 stdout，导致严格 JSON 解析失败。安装版源码及隔离会话探针确认了具体日志格式；实现仅识别该模型列表成功日志，并保留为操作详情，其他非 JSON 输出仍报错。补入回归后重新构建及连接检查均通过，首次失败与最终成功证据分别保留。

固定回复检查使用独立目录，没有发起工具操作。真实审批、拒绝、取消和续聊的自动化覆盖来自原生协议进程 fixture。Harness 对某些 blocked/aborted 终态仍使用 `end_turn`，连接成功和协议结束均不等于模组创作或游戏验收。本轮没有启动 Minecraft。

## 证据

本机证据位于 `output/deepseek-harness-validation/`，该目录忽略提交：

- `build-and-tests-final.log`、`java-tests.json`、`ui-tests.log`。
- `menu-light.png`、`menu-dark.png`、`compact-menu.png`、`icon-license.json`。
- `deepseek-final/result.json`、`grok/result.json`、`claude-glm53/result.json` 和 `claude-glm53/claude-direct.json`。
- `deepseek/result.json` 保留首次协议失败；`session-probe/startup.json` 保留脱敏后的模型列表日志及创建/关闭会话回应。
- `identity.json` 记录本轮源码、图标、验证文件及 JAR 的 SHA-256。

最终 JAR SHA-256：`678A3F320B7DDAE57734A4E834104B45BB4D56AE160230A1B629DEF24AF5BACD`。

复现使用既有 Gradle 测试任务和 `ui-shell/e2e/simple-creation.spec.ts`，没有引入新测试框架。本机验证仅为当前进程设置短 socket 路径 `-Djdk.net.unixdomain.tmpdir=D:\AICoding\Minecraft_ModCreator\.tmp`。

## 协议依据

- [DeepSeek 官方 Harness](https://github.com/deepseek-ai/deepseek-harness) 与 [ACP 协议说明](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/acp/acp/README.md)。
- [ACP profile 与输入关闭生命周期](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/bundle/acp-app/README.md)。
- 本机 `@deepseek-ai/dsh` 及 `dsh-acp`、`dsh-acp-app`、`dsh-llm-deepseek-account` 的 `0.2.1-rc.2` 安装源码用于核对实际行为；未读取或复制用户凭据。
