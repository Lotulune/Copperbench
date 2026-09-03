# ADR-0016：Stage 15 启动 Linux 正式平台支持

- 状态：已接受
- 日期：2026-09-04
- 继承决策：[ADR-0014](./0014-target-windows-and-operate-offline-first.md)

## 背景

ADR-0014 将 Copperbench 首期正式平台收缩为 Windows 11 x64，并明确 Linux/macOS 在首个公开 GitHub 衍生版中不提供正式安装包、支持承诺或发布门禁；同时约定后续若重新启动这些平台范围，需要新的产品决策。

截至 `v0.1.0-beta.4`，Windows 安装产品的 bundled JDK、交互式 `runClient`、Desktop MCP、external-Agent 与发布供应链闭环已经完成。产品后续路线也已经从“先证明 Windows 产品可交付”转向复杂编辑、创作者生产力、高级 AI/IDE 工作流与平台扩展，因此可以为 Linux 建立独立的正式支持阶段，而不改变 ADR-0014 对首期发布的历史事实。

## 决策

1. **Linux 正式支持进入 Stage 15。** `PRD-NEXT.md` 11.5 是 Stage 15 的功能范围与 Definition of Done；Linux 不再属于无限期 deferred 项。
2. **Stage 15 完成前，当前正式支持平台仍仅为 Windows 11 x64。** 不因为路线已确定就提前在 README、Release Notes、`product-status.json` 或 Release 页面宣称 Linux 已受支持。
3. **首个正式 Linux 目标为 x86_64 桌面 Linux。** Stage 15 开工时必须冻结至少一个主流 LTS/稳定发行版及最低版本作为 clean-VM 认证基线；其它发行版只有在取得独立证据后才能提升为正式支持。
4. **Linux 必须走完整产品而非“Java 核心能运行”口径。** 正式支持至少覆盖发行打包、bundled JDK/JCEF、桌面集成、Blockbench/外部工具、Gradle generate/build、真实 `runClient`、Desktop MCP/headless/external-Agent、clean Linux VM 与发布资产/provenance。
5. **打包格式在 Stage 15 开工时另行冻结。** 初始要求是至少一个 portable 产物和一个具有桌面入口/清理语义的正式发行形态；AppImage、`.deb`、tarball 或其它格式的最终组合通过 Stage 15 打包 ADR/实施决策确定。
6. **Linux 适配不得分叉 Core 业务语义。** UI、MCP、headless、workspace schema、revision、reference、recovery 与 generator capability 继续共享同一 Core；平台差异通过 platform adapter、打包层和稳定诊断表达。
7. **macOS 不随本 ADR 自动进入路线。** macOS 正式支持仍保持未承诺状态，未来若重启必须独立决策。

## 完成与支持声明

Stage 15 只有在 `PRD-NEXT.md` 11.5.2 全部满足后才能把 Linux 标记为正式支持，至少包括：

- clean Linux x86_64 候选在无系统 Java 环境启动并完成工作区 create/open/save/reopen；
- bundled JDK/JCEF、真实 Gradle build 与图形化 `runClient` 有安装候选证据；
- Desktop MCP 与独立 external Agent 在同一 Linux 候选完成读写/plan/build/conflict 闭环；
- Linux 发行资产具备 SHA-256、SBOM、release metadata、provenance 与不可变候选记录；
- 文档明确认证发行版/桌面会话、已知限制与未支持范围；
- Windows 11 既有安装产品和 generator/CRUD 基线没有因平台抽象发生未验证回归。

## 影响

- ADR-0014 **不被改写或废弃**；它继续描述首期 Windows-only 决策。ADR-0016 只负责后续 Linux 范围重启。
- Stage 12～14 不因 Linux 尚未实现而被阻断；Linux 是 Stage 15 自身的交付目标。
- Stage 15 开始后，任何触及 JDK、Run Client、Desktop MCP、external-Agent 等既有产品路径的跨平台重构，都必须按当前 gate 规则重新验证 Windows 基线。
- 在 Stage 15 证据完成前，Linux 上的源码运行、开发者自测或非正式构建只能描述为开发/兼容实验，不构成正式平台支持。
