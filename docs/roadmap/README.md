# 分阶段开发路线图

本路线图不承诺日历日期。阶段按可验证依赖排序，每个阶段只有通过对应门禁才能退出；团队规模和实际吞吐确定后再估算时间。

## 阶段总览

| 阶段 | 名称 | 主要结果 | 依赖 |
| --- | --- | --- | --- |
| 0 | [分支、许可与可复现基线](./stage-0-fork-compliance-baseline.md) | 可构建、可追溯、已去品牌的 Windows 基线 | 无 |
| 1 | [领域边界与上游兼容基础](./stage-1-domain-compatibility-foundation.md) | UI/MCP/headless 共用的 Java 应用服务 | 0 |
| 2 | [版本历史、MCP 与 headless](./stage-2-history-mcp-headless.md) | 安全 AI 控制面、恢复点和自动化入口 | 1 |
| 3 | [Fabric 1.21.1 端到端链路](./stage-3-fabric-1211-vertical-slice.md) | 第一个可构建、可运行、可回滚的 Fabric 工作流 | 2 |
| 4 | [新产品外壳集成](./stage-4-product-shell.md) | 自适应无边框 JCEF/React 工作台 | 1，集成依赖 3 |
| 5 | [NeoForge 通用能力对齐](./stage-5-neoforge-parity.md) | 同一通用元素在两个加载器工作 | 3、4 |
| 6 | [资产、资源包与 Blockbench](./stage-6-assets-resource-pack-blockbench.md) | 资产往返、引用治理和资源包工作流 | 4、5 |
| 7 | [版本轨道与加载器迁移](./stage-7-version-tracks-and-migration.md) | 四条版本轨道和复制式迁移 | 5、6 |
| 8 | [Windows Beta 与正式发布](./stage-8-windows-beta-ga.md) | 阶段 8 已收口；后续可信预览见 [`PRD-NEXT.md`](../../PRD-NEXT.md) | 0-7 |
| 9 | [可视化逻辑与创作者核心](../../PRD-STAGE-9.md) | Procedure、工作区数据、数据驱动元素和服务端验证闭环 | 8 |

## UI 并行工作流

UI 不需要等待阶段 3 才开始，但不得提前绑定未稳定的核心内部类。

| UI 子阶段 | 与核心并行 | 工作内容 |
| --- | --- | --- |
| U0 信息架构 | 阶段 1 | 已关闭：选定方向 A「专注工坊流」；任务地图与 IA 已交付 |
| U1 合同夹具 | 阶段 2-3 | React 设计系统、模拟命令/查询/事件、窗口原型、Playwright 基线 |
| U2 正式集成 | 阶段 4 | 接入稳定 Schema bridge，完成 Fabric 纵向工作流 |
| U3 覆盖扩展 | 阶段 5-7 | NeoForge、资产、资源包、迁移、插件兼容界面 |
| U4 发行硬化 | 阶段 8 | 帮助/About 与 DPI/命中区 Playwright 100/100；JCEF 实机 Snap/DPI 已宣称（HTMAXBUTTON=9，WM_DPICHANGED 144） |

截至 2026-08-25：U0–U2 与 G4 自动化完成。阶段 7 G 切片已落地；U3 交接见 [`u3-stage-7-ui-brief.md`](../handoffs/u3-stage-7-ui-brief.md)。阶段 8 的新建工作区落盘/三入口、八插件空工程、资产页、资源包工作区、离线宣称、当前 Windows 预览包导出、授权后的安装演练和 Hyper-V G7 最终复验均有证据；发布记录见 [`stage-8-release-preview-2026-08-23.md`](../testing/stage-8-release-preview-2026-08-23.md)。G7 已通过，VMware 不属于门禁。阶段 9 的创作者核心闭环正在实施，当前证据见 [`stage-9-creator-core-2026-08-25.md`](../testing/stage-9-creator-core-2026-08-25.md)；未通过 G9.5 前不进入正式支持声明。

## 首个正式版本定义

首个正式版本是仅支持 Windows 11 x64、离线优先、公开 GPL 的 MCreator 分支产品。它内置 Fabric 与 NeoForge，维护最新稳定版、前一个稳定版、1.21.1 和 1.20.1，提供本地 Git 历史、第一方 MCP、headless 构建/校验/导出、资源包能力和 Blockbench 往返。

正式版不包含 Windows 10、自有账户、云同步、远程 MCP、内置模型厂商聊天、Linux 或 macOS 支持。未迁移到新产品外壳的上游高级工具必须列入兼容矩阵并提供明确入口或明确标记不支持，不能静默消失。

## 全局规则

- Fabric 优先不等于 NeoForge 可选；通用能力最多相差一个里程碑。
- 每个工作区只有一个活动生成器和一个写入者。
- 变更操作始终保留校验、日志和恢复点策略。
- UI、MCP 与 headless 只能调用同一套 Java 应用服务。
- 阶段证据遵循 [发布门禁](../testing/release-gates.md)。
- 架构变化先更新 ADR，再修改阶段范围。
