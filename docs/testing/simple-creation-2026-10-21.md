# 简洁创作实现验证 · 2026-10-21

本次验证针对当前工作树中的简洁/专业模式切换、CLI 宿主、工作区连接及新建作品引导。现有未提交改动保留；这些源码改动不代表已发布安装包更新。

后续新增 DeepSeek Harness、五后端图标检查及 Claude `z-ai/glm-5.3` 重试结果见[补充验证记录](deepseek-harness-2026-10-21.md)。其中 Claude 的最新观察为 403，下面的 400 保留为旧源检查记录。

再后的[完整 Review 与 Claude 新模型重试](simple-creation-review-2026-10-21.md) 记录了 7 项待修复问题，以及 `deepseek-v4.1-flash` 的成功连接；上述 403 也是先前源的历史结果。

## 已完成

- Java 编译、前端 TypeScript/Vite 构建、JAR 打包通过。中英文检查通过：387 个引用键、1705 条消息。
- 19 项 Agent 协议、实际假 CLI 子进程、停止和历史存储测试通过。另有 9 项 JCEF Agent/选择器桥接测试在首次验证批次通过，后续未修改其实现。
- 5 项前端单元测试通过，覆盖命名空间生成及原生选择器能力、取消和错误回传。
- 5 项 Playwright 场景通过：默认模式与已有偏好迁移，聊天/编辑器草稿保留、IME、审批、澄清、停止及重开，四后端会话区分与权限拒绝，新建作品及原生选择器，明暗主题与窄窗口。
- 500×600 窗口原先存在输入框被裁切的问题，已修复并增加输入框、发送按钮位于视口内的断言。暗色截图改为在过渡结束后捕获；针对布局的检查再次通过。
- Impeccable 检测无发现。独立界面审查为 `ship`，范围限七张浏览器 fixture 截图与对应源码；未展示的已加载纹理、设置字段、安装流程不记为视觉验收通过。

## 本机 CLI 连接检查

| 后端 | 观察结果 |
| --- | --- |
| Codex CLI | 真实进程启动、结构化流、会话记录和准确回复通过。 |
| Claude Code | 能启动并回传模型服务错误；主机现有配置的上游返回 HTTP 400，因此成功对话未验证。未改动用户的模型或认证配置。 |
| Grok Build | 真实 ACP 会话、结构化流及准确回复通过。 |
| Antigravity CLI | 真实流式会话及准确回复通过。终态后驻留的进程使用受控清理，确认归属进程结束后才完成；权限拒绝仍覆盖成功退出码。 |

连接检查使用独立目录，只要求固定回复，不调用工具、不改工作区。真实授权回传、续聊与异常终止的自动化覆盖来自匹配原生协议的进程 fixture；不能把固定回复检查称作全部创作能力的实机验收。

## 桌面与工作区

完整应用使用独立工作区和 `COPPERBENCH_HOME` 启动，JCEF 已显示简洁界面，但首次生成器依赖初始化持续占用模态窗口，因此该次启动不记作完整交互通过，验收启动器已停止。

另外启动真实 Workspace 与生产 `CopperbenchProductShell` 的隔离宿主，省略启动器的依赖初始化步骤。界面与原生桥接正常加载；外部 Python SDK 的 `Workspace.connect`、schema 检查、环境查询和元素读取通过。随后前台窗口覆盖了验收窗口，按桌面输入规则停止了进一步 UI 操作。浏览器交互证据与原生宿主连接证据分别记录。

本轮未启动 Minecraft 游戏客户端，也未做新模组的玩法、世界保存或重新载入验收。

## 证据与复现

本机证据位于 `output/simple-creation-validation/`：界面截图、四个 CLI 的脱敏状态、`native-sdk.json`、桌面日志、检测结果及源码/产物身份。该目录忽略提交。

主要命令：

```powershell
.\gradlew.bat test --tests 'dev.copperbench.agent.*' --tests 'dev.copperbench.bridge.JcefAgentBridgeTransportTest' --tests 'dev.copperbench.bridge.JcefWorkspacePickerTest' jar
npm --prefix ui-shell run build
node --experimental-strip-types --test ui-shell/tests/simple-creation-defaults.test.mjs ui-shell/tests/workspace-pickers.test.mjs
```

在 `ui-shell` 中运行：

```powershell
npx playwright test e2e/simple-creation.spec.ts --project=chromium
```

本机 JBR 的 NIO 初始化受 socket 临时路径影响；验证命令仅为当前子进程设置 `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\AICoding\Minecraft_ModCreator\.tmp` 后通过，未修改全局配置。
