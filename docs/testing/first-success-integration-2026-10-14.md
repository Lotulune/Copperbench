# 首次成功优化与最新主线整合（2026-10-14）

基线：`ccee11b8358aeee8da25e28741cefb3eb98c7048`（PR #106 合入后的 `origin/main`）。分支 `codex/first-success-integration-20261014` 只迁移本聊天拥有的优化；原目录的其他改动保持原样。

## 语义整合

- 输入预检复用主线 `StructuredElementDefaults` 与 `ElementFieldContract`。工具省略纹理、生物省略模型名、船省略物品纹理仍使用主线默认值；显式清空必需值或填写错误类型时返回结构化修复诊断。
- 环境查询保留 `FieldContractProjection`、字段发现、preview generation 和 26.3 预览限制，增加支持矩阵与输入预检元数据。
- 原有 `FailedElementMutationRegressionTest` 保持主线的故障注入及真实模板生成回滚测试；新增独立的预检持久化回归，避免用提前拒绝冒充后期回滚验收。
- 保留主线创建弹窗的 26.3 预览选择，只改善错误展示。About／README／状态页区分八条公开支持轨道与 26.3 预览。
- 主线已包含 `source-map-js 1.2.2`、`undici 7.30.0`；本分支迁移 `fast-uri 3.1.8` 锁项及原始告警来源记录，不重复降级或重升其他依赖。
- PR #106 已将原云端记录的三处本地输出链接改成带来源的文字记录；以本轮完整文档检查为准。

## 验证

| 检查 | 结果 |
| --- | --- |
| Java 定向回归 | **131 项覆盖通过**：11 个测试类，包含主线默认创建、字段合同、预览授权、计划、失败写入保护及故障注入回滚 |
| Javadoc 与前端生产构建 | **通过**；生产构建由 Gradle `buildUiShell` 执行 |
| 前端单元测试 | **36 通过** |
| UI-Core | **50 测试通过**；14 个 schema、18 个场景通过 |
| Playwright | **28 通过**：1920×1080／1366×768 下的创建诊断、帮助页、Blockly、26.3 预览选择与拒绝路径；使用隔离端口 5188 |
| MCP conformance | **6 场景、8 检查通过**，CLI 0.1.16；进程退出码 0 |
| npm audit | ui-shell、ui-core 均为 **0 个告警** |
| 文档及产品状态 | **全仓 Markdown 链接通过**；8 个公开生成器、27 个历史门禁状态校验通过，未提升历史门禁 |

Java 首轮 131 项中，4 项新断言把计划失败状态误写成 `rejected`；按既有 `WorkspacePlanEngine` 的 `failed` 合同修正了测试，未修改产品返回状态。其余 112 项在首轮通过；两个受影响类的 19 项随后全部通过，Javadoc 同轮通过。原始工作区的 [85 项检查记录](first-success-2026-10-14.md)只代表当时的源码，不替代此次整合验证。

源码／产物哈希、各轮报告和命令见[整合证据](../../evidence/maintenance/2026-10-14/first-success-integration.json)。完整日志和截图在本 worktree 的 `output/first-success-integration/`；帮助页 26.3 预览展示已检查。原目录和其中其他未提交改动未迁移或修改。

复现 Java 检查时，使用仓库 `scripts/run-gradle-external.ps1` 运行证据列出的 11 个测试类，并把 `javadoc` 放在所有 `--tests` 参数之后。UI 检查为 `npm test --prefix ui-shell`、`npm test --prefix ui-core`、`npm run validate --prefix ui-core`；浏览器选择 `input-preflight`、`adaptive-and-frameless` 的帮助用例、`stage17-procedure-rendering` 和 `preview-targets`。协议检查使用原有 `scripts/verify-mcp-conformance.ps1`。

本分支未推送、创建 PR 或发布安装包；交由联合发布聊天纳入 0.1.5。没有新增 Minecraft 实测或重新绑定主线已有游戏证据。
