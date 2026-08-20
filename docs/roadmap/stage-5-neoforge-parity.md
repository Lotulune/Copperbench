# 阶段 5：NeoForge 通用能力对齐

- 状态：已完成（G2）
- 依赖：阶段 3、阶段 4
- 对应门禁：G2（Fabric/NeoForge 1.21.1）

## 目标

证明产品不是只有 Fabric 的专用工具：阶段 3 的通用元素和工作流必须在 NeoForge 1.21.1 生成、编译、测试和通过新 UI 操作。

## 范围

- 为每个纵向元素标记通用字段、Fabric 扩展和 NeoForge 扩展。
- 对接上游 NeoForge 1.21.1 生成器。
- 新 UI 根据活动生成器展示能力差异和不可迁移字段。
- 建立 Fabric/NeoForge 双黄金工作区和差异报告。
- MCP 与 headless 使用同一工具名称，通过 Loader Target 参数或工作区上下文选择后端。
- 对不支持功能给出替代建议或明确阻断。

## 不在范围

- 同一工作区同时生成两个加载器。
- 自动加载器迁移。
- 所有加载器专属高级功能。
- 动态最新版本。

## 交付物

- Loader Capability Matrix v1。
- NeoForge 1.21.1 黄金工作区与编译/运行证据。
- 加载器扩展字段 Schema 与 UI 状态。
- 双加载器领域和 MCP 契约测试。
- Fabric 优先、NeoForge 落后状态的发布报告格式。

## 风险

- 把加载器差异强行塞入通用字段。缓解：允许明确命名空间扩展，不伪装通用。
- NeoForge 上游模板变化影响 Fabric 复用代码。缓解：模板来源和差异测试分开维护。
- UI 隐藏不支持字段导致数据丢失。缓解：切换生成器前显示能力报告，未知字段保留。

## 退出条件

- [x] 同一通用需求可分别在 Fabric 与 NeoForge 1.21.1 构建运行。
- [x] UI、MCP 与 headless 通过同一任务网关路由，并对无效加载器给出一致诊断。
- [x] NeoForge 通用能力没有超过一个里程碑的缺口；生成、构建、导出、日志、取消和客户端启动均有黄金工作区测试。
- [x] 加载器专属字段保存在生成器命名空间，打开、保存和路由时不会静默丢失。

## 已交付证据

- `NeoForge1211Generator` 生成 NeoForge ModDev 2.0.141 工作区，固定 Minecraft 1.21.1、NeoForge 21.1.232 和 Java 21。
- `LoaderRoutingWorkspaceTaskGateway` 按活动生成器选择 Fabric 或 NeoForge，并保持统一的 task、日志、诊断、取消和导出 API。
- `NeoForge1211GoldenBuildTest` 真实生成并构建 JAR；`NeoForge1211GoldenRunClientTest` 已在 Windows 11 x64 真实启动客户端并捕获 `COPPERBENCH_STAGE5_NEOFORGE_READY`。
- `scripts/verify-stage-5-neoforge.ps1 -RunClient` 是可复现门禁；NeoForge 资产缓存可使用 `C:\Users\<user>\.gradle\caches\neoformruntime\assets`，国内 BMCLAPI 仅用于预填充 Minecraft 对象，不改变官方哈希校验。
