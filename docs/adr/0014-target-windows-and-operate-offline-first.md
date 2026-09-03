# ADR-0014：首期仅支持 Windows 并坚持离线优先

- 状态：已接受（2026-09-04 修订：Linux 纳入 Stage 15 正式路线）
- 日期：2026-08-16

首期正式支持范围仅包含 Windows 11 x64（build 22000+）。Windows 10、Linux 与 macOS 在首期均不提供正式安装包、支持承诺或发布门禁。缩小平台范围是为了集中验证 JCEF 打包、无边框窗口、Blockbench 启动、Gradle/JDK 工具链和 Minecraft 客户端流程，而不是暗示 Java 核心无法跨平台。

2026-08-20：关闭 Windows 10 作为未完成复验项。产品合同、NSIS 安装器和启动器均拒绝 Windows 10。干净安装证据只走 Windows 11 Hyper-V 客户机。

2026-09-04：后续平台决策已经明确。**Linux x86_64 正式支持进入 `PRD-NEXT.md` 的 Stage 15**，覆盖 Linux 打包、bundled JDK/JCEF、桌面集成、Blockbench/外部工具、Gradle 与真实 `runClient`、Desktop MCP/external-Agent、clean Linux VM 和正式发布/provenance 闭环。在 Stage 15 Definition of Done 完成前，当前正式支持平台仍仅为 Windows 11 x64，不得提前宣传 Linux 已受支持。macOS 继续保持未承诺状态，未来若纳入路线需另行决策。

产品离线优先：工作区编辑、生成、构建、版本历史、资源包导出和本地测试不依赖账户或自有云服务；前端资源全部随安装包分发，不使用 CDN，默认不采集遥测。网络仅用于构建依赖、用户启用的更新检查、可选远程 Git，以及经过权限控制的 AI/MCP 或发布操作。
