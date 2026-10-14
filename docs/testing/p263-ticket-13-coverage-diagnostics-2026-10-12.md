# P263-13 — 中英文验收原因与覆盖参数

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，未提交或合入。

## 契约与兼容

[验收读取](../ai/task-authorization-and-acceptance.md)在 required_cases/1 请求中显式声明 `diagnosticVersions: ["1"]`，仅允许 get。新结果位于独立的 coverageDiagnostics 对象；未协商时不返回该字段，旧 v1.0 task、verification、command/query envelope 保持原结构。

[GameTestReport](../../src/main/java/dev/copperbench/generator/GameTestReport.java)在解析原始 XML 时产生机器原因。已有必要清单直接复用原 coverage；旧最低数量流程另存 acceptanceCoverage 内部记录，发往旧客户端前移除。没有从英文 detail、被截断名称或 UI 文案反推原因，旧报告原有可读 detail 也保留。

[Core 诊断投影](../../src/main/java/dev/copperbench/generator/AcceptanceDiagnostics.java)区分运行中、报告不可用、业务失败、无业务、重复、必要用例缺失、数量不足、当前输入记录通过和历史输入通过。required 是固定项数，minimumTests 是最低业务数量；运行中和不可用报告的未知执行数/差额为 null。导出读取跟随选定验收任务，因此历史导出不会借用另一轮当前结果。

Python Native、Python MCP、TypeScript 和 JCEF/UI 读取同一参数；新 SDK 若没有收到已协商版本的响应会明确拒绝。诊断没有命令执行路径，也不授予权限。旧记录若没保存精确重复投影，保留 report_unavailable 等宽分类，不用展示字段猜测身份。

inputScope 指明 current_at_completion、current_at_export 或 historical_at_export。这是记录时点的声明；后续导出继续核对当前输入和原始 JAR/XML，诊断不替代新的验收或导出门禁。

## 实际适配器与文件不变性

在 10 的成功 NeoForge 工作区上启动新的 Native 产品进程 PID 39620，读取最终验收、真实缺 T12、历史导出和当前导出四项。分别得到正确状态；六项通过为 6/6/0，缺项为 6/5/1，缺失身份是 copperbench_camp_kit:t12。产品 exit 0，修订保持 4，工作区文件和全部任务 JSON 的前后 SHA-256 相同。

MCP 使用真实本地 HTTP 服务和 READ_ONLY token 测试，运行中诊断与直接 Core 投影相同，读取后修订和任务数量保持不变。四份 Native 加一份 MCP 实际响应全部通过扩展与嵌套 v1.0 schema 检查。测试授权和临时审计文件仅用于隔离的测试目录。

## UI 与验证

沿用任务抽屉，由 [AcceptanceSummary](../../ui-shell/src/components/AcceptanceSummary.tsx)显示最低业务数、实际执行、差额，以及可展开的完整正常长度缺项/重复身份。大量或超长身份只在显示层截取，原始协议和报告不截断。摘要通过状态区域向辅助技术呈现，不依靠颜色说明失败。

1366×768 和 1920×1080 两种浏览器布局共 34 项 Playwright 检查通过。其中 26 项覆盖 13 类状态的中英文切换，另 8 项回归任务授权、EULA 选择和既有报告。切换前后参数、任务 JSON、修订、诊断读取次数相同，未发出生成、构建、验收、导出等产品命令。缺项和重复详情可键盘展开；水平溢出检查通过。

已人工查看 1366 的英文 5/6 差额、中文缺项和 1920 的英文历史导出截图：数字与身份清晰，未被遮挡，历史范围独立呈现。截图来自浏览器契约回放，使用 Java 解析合成 XML 后生成并由 Java 再比对的共享 fixtures，不称为安装版或 Minecraft 画面。

最初浏览器导航未展开项目健康区，未到达目标；纠正后暴露了新 JCEF 方法误用普通 envelope 顶层 workspaceId 检查的问题。实际验收扩展的工作区身份在嵌套 core 中，现已连同 requestId、taskId 一起检查，错误绑定有单独回归。早期失败日志和截图保留，最终 34 项全部通过。

验证包括：78 项不同 Java 原始解析/持久化/协商/导出与 MCP 检查、Javadoc、100 项 Python SDK、19 项 TypeScript SDK、42 项 UI-Core、31 项 UI 单元检查、UI 构建和 34 项 Playwright。Java 不同用例计数及各次 XML 见 java-summary.json；没有把重复运行累计为更多用例。Markdown 链接和 diff 检查通过。

稳定证据位于 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-13`，保留原始 Native/MCP 响应、合成模型/浏览器回放、成功与失败检查、截图及源码快照。本票不改变公开 supportStage，不替代安装、玩家操作、Ubuntu 或后续结构化元素门禁。
