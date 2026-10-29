# 首次成功与输入诊断优化检查（2026-10-14）

范围：输入预检、第一件作品教程、前端依赖告警、Agent／About 文案和状态入口。对应[状态总览](../status.md)。这些变化属于本地源码，未发布新安装包。

## 验收依据

- 创建请求缺失、null、错类型返回字段路径、期望类型、实际类型、修复提示及已知缺失字段列表。
- 已复现的工具／特殊实体／生物缺少纹理或模型名时，在创建前拒绝；计划路径共用规则。
- 真实 MCreator 工作区拒绝后不新增元素、不改变 `.mcreator` 和源文件、不递增 revision，重开后仍为空。
- 保留生成后的故障注入回滚检查，避免把前置检查当作事务安全的替代。
- 教程涵盖具体创建输入、构建、启动、取得／放置、保存重进和正常退出。字段与输入由本地实现及针对性测试核对；游戏内结果需要实际客户端观察。
- 支持范围由当前活动生成器、版本轨道查询、About 和公开指南表达；没有扩大历史玩法或发行包验收范围。

## 本轮结果

| 检查 | 结果 |
| --- | --- |
| Java 编译及定向测试 | **85 通过，0 失败**：输入预检、共享应用服务、Workspace Plan、真实 MCreator 失败创建、生成准备、方块一致性及 MCP HTTP |
| 前端生产构建 | **通过**：TypeScript、Vite 及中英词条检查；最终版本文案修改后重建通过 |
| 前端单元测试 | **28 通过** |
| UI-Core | **35 测试通过**；12 个 schema、18 个场景校验通过 |
| 浏览器交互 | **20 通过**：中英创建失败提示、输入保留、改选方块后创建、帮助／About、布局和 Blockly；覆盖 1920×1080、1366×768，帮助页另检查 1280／2560 宽度。最终精确版本文案修正后，4 项帮助页用例重跑通过 |
| MCP conformance | **6 个场景、8 项检查通过**，CLI `0.1.16`、协议 `2025-11-25` |
| npm 审计 | **3 high → 0**；ui-shell 全量／生产依赖、ui-core 全量审计均为 0 个告警 |
| 产品状态校验 | **通过**：8 条生成器、27 项门禁；未更改历史门禁状态 |
| Markdown 链接 | 本轮新增／修改文档无缺失链接。全仓检查仍报已有 `docs/testing/installer-cloud-2026-10-13.md` 中三个指向 `output/ux-implementation` 的链接缺失，未修改这份既有记录 |

三处依赖变更是 `source-map-js 1.2.1 → 1.2.2`、`undici 7.29.0 → 7.30.0`、`fast-uri 3.1.5 → 3.1.8`，只更新原有锁项，无新增依赖或强制 override。

机器可读检查结果、测试源码／产物哈希见[本轮证据](../../evidence/maintenance/2026-10-14/first-success.json)。完整日志及截图保存在本机 `output/first-success-2026-10-14/`；错误弹窗和帮助页截图已检查。测试基于当前含既有未提交修改的工作区，不声称是干净 HEAD 或发行包验证。

Java 检查使用仓库的 `scripts/run-gradle-external.ps1`，因为直接启动遇到了开发说明已记录的 Windows 宿主 loopback 限制。MCP 也通过隔离 PowerShell 进程运行现有检查脚本，正常返回退出码 0。

复现命令：

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 --no-daemon test --tests dev.copperbench.core.application.ElementInputPreflightTest --tests dev.copperbench.core.WorkspaceApplicationServiceTest --tests dev.copperbench.core.application.WorkspacePlanEngineTest --tests dev.copperbench.core.workspace.mcreator.FailedElementMutationRegressionTest --tests dev.copperbench.core.workspace.mcreator.MCreatorGenerationPreparationTest --tests dev.copperbench.core.workspace.mcreator.Stage17BlockConsistencyTest --tests dev.copperbench.mcp.McpHttpServerTest
npm test --prefix ui-shell
npm test --prefix ui-core
npm run validate --prefix ui-core
npm run build --prefix ui-shell
pwsh -NoProfile -File scripts/verify-mcp-conformance.ps1 -OutputDirectory output/mcp-conformance
node scripts/verify-markdown-links.mjs
node scripts/verify-product-status.mjs
```

在 `ui-shell` 目录运行对应的浏览器回归：

```powershell
npx playwright test e2e/input-preflight.spec.ts e2e/adaptive-and-frameless.spec.ts e2e/stage17-procedure-rendering.spec.ts --project=chromium --project=compact-1366 --grep 'creation preserves|help/about|help page|opening and unrelated edits|unavailable renderer'
```

本轮未发布新安装包，未重新执行 Minecraft 玩法或世界持久化验收。预检只改动输入拒绝路径；现有世界保存和游戏代码未作修改。教程中的游戏步骤是操作说明，不能视为本次已完成的客户端观测。

依赖来源、影响和升级依据见[结构化依赖记录](../../compliance/dependency-audit/2026-10-14.json)。审计仅覆盖两个 npm 锁文件，不等同于 Java、随包 JDK、系统组件或已发布安装包的全面安全扫描。
