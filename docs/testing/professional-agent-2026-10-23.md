# 专业版 Agent 实施与验收记录

日期：2026-10-23（Asia/Tokyo）。基于用户提供的 `Copperbench-External-CLI-Agent-Implementation-Spec.md`，结合已有简洁版实现扩展专业工作台。基线提交为 `b1a69d244ab7337608cc302826ed55d4a707f37f`，工作区已有大量未提交改动；本次没有提交、推送或发布。

## 仓库调研与接入决策

| 调研项 | 实际实现与复用方式 |
| --- | --- |
| 客户端与执行位置 | Java/JCEF 桌面外壳托管 React；`AgentChatService` 在 Java 进程启动本机 CLI；浏览器预览没有宿主执行能力。 |
| 专业版入口 | `App.tsx` 和 `WorkbenchModeContext` 切换已有工作台；增加右侧面板，两个模式共用 `AgentContext` 的单一轮询控制器。 |
| 项目身份 | 每个 `CopperbenchProductShell` 持有一个 `MCreatorWorkspaceSession`；Agent 使用 `toRealPath` 规范目录和本机 OS 执行标识，运行记录校验项目身份。 |
| 进程 | 复用 `AgentProcess` 的 stdin 参数传递、行读取、退出检查及所属子进程跟踪；`AgentRun` 保留 PID、开始时间、自然退出码和清理证据。 |
| 会话与记录 | 复用 `AgentChatStore` 的私有原子历史文件；新增逐运行 journal，关键控制事件先写盘，UI 关闭不影响服务。 |
| 文件差异 | 未发现可直接满足任务前未提交内容基线、固定任务结束差异的通用现成接口；新增只读、有界快照，不修改 Git 索引或工作树。 |
| 配置与认证 | 复用 `AgentProvider` 和 `AgentDesktopActions` 的发现与官方登录流程；不读取或复制认证目录，不改全局配置。 |
| 桥接保护 | 复用 `JcefAgentBridgeTransport` 的预期浏览器、主 frame、受信 URL、操作白名单和请求大小检查。 |
| 验证任务 | `AgentVerification` 只映射 `build_workspace` / `validate_workspace` 到当前 Core 的 `WorkspaceEntryAdapter`；用户文本不转换成 shell 命令。 |
| 国际化与组件 | 使用 `uiText`、已有 CLI 选择器、批准组件、任务抽屉和工作台 tokens；未改变设计系统。 |
| 平台 | 当前可执行环境为 Windows；本次真实首适配器为 Codex CLI 0.162.1。其他供应方维持已有代码，本次不据此宣称完整验收。 |

首适配器采用现有 Codex app-server，不增加 SDK 或运行时依赖。CLI 自己负责认证；`thread/start` / `thread/resume` 绑定当前目录，`turn/start` 提交任务，结构化 `turn/completed` 判定结束，`turn/interrupt` 先尝试取消，再清理所属进程。权限采用已有 `workspace-write` / `on-request`，不默认扩大权限。官方协议核对来源：[Codex App Server](https://learn.chatgpt.com/docs/app-server)，核验日期 2026-10-23。

## 验证范围

开发前使用已编译的 `SimpleCreationCliSmokeMain` 在 `output/professional-agent/spike` 完成一次真实 Codex 连接，收到 `COPPERBENCH_CONNECTED` 并正常结束。这个结果仅证明接入可行，不代表本次新代码或完整专业版流程验收。

本次新增回归覆盖：请求去重及重连不重放、停止和迟到批准、精确工作树基线与历史固定、未跟踪和二进制/超大文件、秘密脱敏、同目录锁、服务重启的未确认状态、Core 验证任务独立取消、项目身份隔离、面板开合/模式切换、英中文案与窄窗口布局。

最终命令、退出码、截图及源码哈希记录在 `output/professional-agent/`。测试替身、真实 CLI 和浏览器界面检查分别记录，不将 Core 的内存模拟任务写成真实 Gradle 构建通过。

### 实际结果

| 检查 | 结果与证据 |
| --- | --- |
| 前端单元测试 | `npm test --prefix ui-shell`：73 项通过，`ui-unit.log`。 |
| Java Agent 与桥接 | `run-gradle-external.ps1 --no-daemon test --tests 'dev.copperbench.agent.*' --tests 'dev.copperbench.bridge.JcefAgentBridgeTransportTest' javadoc`，修复后按受影响类补跑；合计 46 个不同用例通过。完整日志为 `java-checks.log`、`java-final.log`、`journal-final.log`、`export-java-final.log`。 |
| 前端生产构建与 i18n | 由上述 Gradle 的 `buildUiShell` 执行，最终 TypeScript、Vite 和中英文门禁通过。 |
| 浏览器交互 | `professional-agent.spec.ts`、`simple-creation.spec.ts`、`source-workbench.spec.ts`：36 项通过，`browser-final.log`。原生导出接通后，专业版 8 项再次通过，`export-browser-final.log`。首次批次中的任务诊断源码 6 项亦通过。 |
| 真实 Codex | `ProfessionalAgentCliAcceptanceMain` 在全新 `CLI 验收 & space` 目录运行通过，`accepted-runtime.log`。输入路径含中文、空格和 `&`。 |
| 实际失败与修复 | 隔离项目的 `node check.mjs` 初始退出 1；Codex 修复后独立再次执行退出 0，输出 `ARITHMETIC_PASS`。既有注释和未跟踪笔记保留。 |
| 历史固定与取消 | 任务后编辑笔记不会改变已保存差异；继续同一 Codex 会话运行等待任务后取消，终态 `cancelled`、`cleanup=confirmed`，记录的所属进程均不再存活。 |
| 设计复核 | 1366×768 中文、900×700 英文界面截图有效；detector `[]`，独立复核为 `ship`，设计文档不需变更。 |

真实 CLI 证据位于 `output/professional-agent/CLI 验收 & space/.copperbench/acceptance/`，包含 `before.json`、`repair.json`、`after.json`、`cancel.json`。运行前的相关 Java 源码和 `.class` SHA-256 位于 `accepted-runtime-identity.json`；总体结果与前端产物身份位于 `acceptance-summary.json`。

直接启动 Gradle 曾遇到现有文档记录的 Windows loopback 问题，最终使用仓库已有独立进程入口通过。Javadoc 任务退出成功，仍报告已有 `ProductUpdateDialog.java:17` 的 `&` 实体警告，该文件未在本次修改。

真实 CLI 流程使用隔离的 Node 算术项目验证错误修复与取消，不等同于 Minecraft 模组构建、游戏内验收或打包后 JCEF 全流程。Core 验证任务关联与独立取消由真实 Core 应用层加内存任务替身检查；原生导出检查实际写文件，但文件管理器打开动作使用测试替身。

## 已知边界

- 文件覆盖是声明范围内的观察；不证明所有变化都来自 Agent，也不承诺整个目录独占。
- 应用崩溃后无法可靠核实资源时保守保留 `interrupted/unconfirmed`，不会按单个 PID 自动恢复或杀进程。
- 版本探测不证明登录成功，未知协议终态不标为成功；本次没有增加统一账号或凭据存储。
- 专业版复用已有适配器；本次真实流程优先验证 Codex，其他供应方与 macOS/Linux 不扩大声明。
- 本次改变桌面工作台，未改变 Minecraft 游戏行为；不将编译、浏览器替身或 CLI 回执当作游戏内验收。
