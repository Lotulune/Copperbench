# 简洁创作：模型强度、附件和手动模型验收

日期：2026-10-22。工作分支 `codex/simple-mode-onboarding`，基线 `21d8fc0148c5decf48589236b5b57b131517d2b0`，均为未提交的本地改动。

## 已实现

- 模型右侧显示 `low / medium / high / xhigh / max`，同时提示模型、CLI 和源的支持范围。应用原样传递请求强度，原生 CLI 可能拒绝或调整。
- 模型菜单固定提供手动 ID 输入，加载中和失败时均可使用；移除了“使用 CLI 默认模型”入口。DSH 的 ACP 组合值在界面投影和原生回传间正确转换，保留提供方身份。
- 原生文件多选、拖入、粘贴图片、草稿移除、缩略图、文件名和大小。普通文件为工作区副本路径，图片使用实际的 native image/localImage 数据。复制和分块上传有大小、顺序、数量、路径及内容完整性校验。
- 默认权限及需确认的自动批准；只影响应用内会话，不改主机 CLI 全局配置。仍保留原生审批与问答路由。
- 切换模型携带未发送的文本与附件；首次发送创建会话时迁移草稿归属，初始化失败仍可编辑重试；删除旧对话不会误删仍由未发送草稿持有的附件。
- 历史保留附件元数据与会话选项；导出不包含文件本体；删除对话后清理无其他会话引用的附件。

## 自动检查

| 检查 | 结果 |
| --- | --- |
| Agent 及两个 JCEF bridge 的相关 Java 测试 | 69 个不同用例均有通过记录：首轮 68 项，原生选择器的 6 项 bridge 复测，以及草稿生命周期修复后的 21 项存储/服务复测均通过 |
| 前端单元测试 | 74 项通过 |
| UI-Core 合同测试 | 50 项通过 |
| 前端生产构建及中英文本检查 | 通过 |
| Javadoc | 通过 |
| 相关 Playwright | 共 62 个不同场景，覆盖 Chromium 1920×1080、1366×768 及部分 500×600 明暗布局；修复后只重跑受影响场景，均通过 |
| Markdown 本地链接、`git diff --check` | 通过 |

Playwright 覆盖手动模型失败回退、五档强度的顺序和位置、选择/粘贴/拖入附件、原生选择器入口、上传分块、草稿移除与转移、首次发送失败、自动批准确认、原有审批及教程。浏览器事件模拟不等于 Windows Explorer 实际拖放验收。

相关用例：[附件存储](../../src/test/java/dev/copperbench/agent/AgentAttachmentsTest.java)、[原生参数和图片](../../src/test/java/dev/copperbench/agent/AgentTurnOptionsTest.java)、[应用服务](../../src/test/java/dev/copperbench/agent/AgentChatServiceTest.java)、[浏览器交互](../../ui-shell/e2e/agent-composer-options.spec.ts)。

## 本机实际结果

| 工具及范围 | 观察结果 |
| --- | --- |
| Codex `gpt-6.1-sol`，`high`，默认权限 | 独立进程和桌面 JCEF 两条路径均正确读出文本文件 `NOTE-5832`、图片文字 `IMAGE-7241` 及棕橙色正方形，回合正常结束 |
| Grok `grok-4.7`，`high`，默认权限 | 普通文件读取成功，返回 `NOTE-5832` |
| Grok 当前 ACP 图片能力 | 握手未声明图片能力；图片请求在发送模型提示前明确失败。未把文件路径冒充视觉输入 |
| DSH 模型目录 | 本机真实读取成功，得到 5 个选项，包含提供方身份；原来拒绝 JSON 组合值的问题已修复 |
| Claude | 本轮仅验证原生参数与图片帧合同；依用户要求未重试当前不稳定的源 |
| Antigravity | 当前官方 stream-json 合同只接受文本；图片明确提示移除或切换助手；参数有单元覆盖，未进行新的上游请求 |

桌面验收通过 `AgentComposerDesktopMain` 启动真实 JCEF、附件 bridge 和 AgentChatService，使用生成的文本和图片以及真实安装的 Codex；Core 作品投影为隔离 fixture，所以右侧资产不可用提示不属于真实作品资产验收。实际点击原生文件选择器、确认两份文件、查看缩略图、选择 high、发送并观察真实回复。未在真实主机启用自动批准；其参数与确认交互由 Java 和浏览器测试验证。桌面发送通过后，后续的首次发送失败草稿迁移补丁另由 12 项相关 Playwright 复测覆盖，草稿文件生命周期补丁由 21 项存储/服务测试复测覆盖。

最初直接以源码 classpath 启动完整作品时遇到独立测试配置的 Gradle 缓存及 helper 布局问题，未将这些启动尝试计为通过；最终改用以上专门的聊天验收入口。没有重跑 Minecraft 游戏流程，也没有把上一轮 0.1.16 候选包的游戏证据算作本次改动验证。

## 本地证据

工作树 `output/agent-composer-20261022/` 保存：

- `codex-1-receipt.json`、`grok-files-1-receipt.json`：真实 CLI 结果。
- `deepseek-models.json`、`grok-models.json`：实际目录。
- `native-ui-receipt.json`、`native/`：原生选择、缩略图、强度列表与实际回复截图。
- `playwright/`、`playwright-recheck/`、`native-selector-recheck/`、`first-send-recheck/`：浏览器检查与修复后的复测。
- `source-manifest.json`：源码和构建内容摘要。

独立运行文件位于 `D:/AICoding/CopperbenchAcceptance/composer-20261022/`。证据不提交原生 resume ID、主机配置或凭据。此前的 0.1.16 候选 ZIP 保留，本轮未提交、推送或发布。
