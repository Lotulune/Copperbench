# 剩余完善清单

本页是**状态索引**，不是需求基线。机器可读状态以 [`product-status.json`](../product-status.json) 为准；阶段 8 历史见 [阶段 8 路线](./roadmap/stage-8-windows-beta-ga.md)，阶段 9 历史边界见 [PRD-STAGE-9.md](../PRD-STAGE-9.md)，阶段 10 需求入口是 [PRD-NEXT.md](../PRD-NEXT.md)。

## 当前状态

阶段 8 收口需求 `FR-CLOSE-01`～`FR-CLOSE-08` 均已完成。`v0.1.0-preview.2` 已公开并包含完整 Windows 三包、SBOM、哈希、源元数据和生产审批记录，发布源提交为 `c629315b`。GitHub 已正确识别 GPLv3，Javadoc Pages 可访问，main 保护、真实受保护 PR 与 production 审批均已验证。Nightly `32904372190` 在 `fe4b0f30` 的产品回归和 Stage 9 八生成器黄金编译 8/8 全绿；同一代码基线的快速 CI 已连续三次全绿。Stage 9 仍有多个 Beta 阻断门禁，外部试用为 0/5。

## 当前交付阻断项

| 项 | 状态 | 下一证据 |
| --- | --- | --- |
| 机器状态源 | 已通过 | `product-status.json`、Schema 与漂移校验在 main/PR 持续通过 |
| main 分支保护 | 已验证 | 三项必需检查、严格更新和管理员保护已生效；PR #1～#5 留有全绿记录 |
| Javadoc Pages | 已验证 | <https://lotulune.github.io/Copperbench/> 返回 200 |
| production 审批 | 已验证 | Preview 2 在批准前保持等待；run `32909134939` 留下批准与部署记录后才公开 |
| Nightly | 已取得最终候选全绿证据 | [运行 32904372190](https://github.com/Lotulune/Copperbench/actions/runs/32904372190) 在 `fe4b0f30` 的产品回归与八生成器矩阵全部通过 |
| Dependency Submission | 已移除 | Dependency Graph 关闭时不保留必失败工作流 |
| Preview 2 | 已公开 | `v0.1.0-preview.2` 的 Tag、生产审批、三包、SBOM、哈希和资产验证已完成 |
| 外部试用 | 0/5 有可审计记录 | 五名非核心开发者匿名任务结果 |

## 阶段 9 状态摘要

| 项 | 当前状态 | 证据/备注 |
| --- | --- | --- |
| Procedure 领域模型与编辑器 | 开发预览 | 结构化 IR、未知块保留、Blockly 工作台、预览/保存与引用索引已有自动化覆盖；500 节点交互门禁未关 |
| 变量 / 标签 / 语言 | 开发预览 | 稳定 ID、CRUD、重命名影响预览与引用计数已接 UI/MCP/headless；语言导入导出、回退和诊断待补 |
| Function / Loot Table / Advancement | 开发预览 | 可真实写回上游工作区；八生成器黄金编译已 8/8 通过，专用编辑器深度仍待补 |
| 服务端 / datagen / GameTest | 核心路径完成 | 受管任务、日志和隔离目录已接入；datagen 支持暂存差异、确认发布与事务回滚 |
| 发布门禁 | 未关闭 | 500 节点与 10,000 引用性能、全轨 readiness、真实 JCEF、Windows 11 干净 VM 仍需证据 |

当前证据见 [阶段 9 创作者核心验证记录](./testing/stage-9-creator-core-2026-08-25.md)。

## 阶段 8 状态摘要

| 项 | 当前状态 | 证据/备注 |
| --- | --- | --- |
| 产品外壳「新建工作区」 | 已完成 | 落盘、JCEF 宿主打开、MCP/headless 查询与审批测试通过；Playwright mock 仅作 UI 场景测试 |
| 八套工作区生成器插件 | 已完成 | 8/8 空工程黄金编译通过，当前 Windows 预览包已对齐 |
| 国内源 / Gradle 池 / 9.7.0 | 已完成（受缓存/网络条件约束） | 当前导出包布局和启动预填已通过；官方专用 Maven 源仍不承诺镜像化 |
| Gradle `--offline` 宣称 | 已完成 | 7 条轨道进入正式列表；NeoForge 1.20.1 明确排除 |
| 第一方纵向切片 | 已完成 | 八生成器 × block/item/recipe/procedure 的编译与 `runClient` 证据独立存在 |
| 资产 | 已完成 | `AssetWorkspaceService`、UI-Core、MCP 与产品路径已接真实工作区 |
| 独立资源包 | 已完成 | 创建、骨架、ZIP 导出和客户端准备均通过 |
| Windows 预览包 | 已完成 | 当前包、11 个插件、README、unsigned preview、安装/升级/卸载演练通过 |
| G7 | 已完成 (`passed`) | Hyper-V Win11 客机断网验证中 `processStartedWhileDisconnected=true`；安装/升级/卸载、工作区和用户数据保留均通过。VMware 非门禁 |

## 两套生成器（不要混用证据）

可视化「新建工作区」列出的是**工作区生成器插件**，不是版本轨道里的第一方纵向切片。

| 加载器 | 对话框已有插件 | 空工程黄金编译 |
| --- | --- | --- |
| Fabric | 26.2、26.1.2、1.21.1、1.20.1 | 4/4 通过 |
| NeoForge | 26.2、26.1.2、1.21.1、1.20.1 | 4/4 通过 |

这些插件是从相邻轨道模板改目标版本得到的。第一方切片与插件空工程现在都有独立证据，但这不宣称插件树里的每一种模组元素类型都能编译。

## 网络与 Gradle（限制，不是待办）

- Fabric Maven 与 NeoForge 专用仓库仍走官方地址。阿里云 / BMCLAPI / 华为云 Maven 不是 Fabric 的完整代理。
- 导出时仅当构建机本地已有 9.7.0 / 8.8 才会打进 `gradle-dists`。
- 产品自身构建仍用 Gradle 9.6.0；工作区发行包共用 `%USERPROFILE%\.copperbench\gradle`。
- 官方 Stage 8 `--offline` 宣称已对齐为 7 条通过轨道；NeoForge 1.20.1 保留未宣称限制，见 [`stage-8-offline-claims-2026-08-23.md`](testing/stage-8-offline-claims-2026-08-23.md)。

## 阶段 9 剩余实现与验证

- 为 Function 增加命令级诊断与函数标签编辑，为 Loot Table 增加池/条目/条件/函数的专用结构化编辑，为 Advancement 增加 criteria 与父级循环防护。
- 为语言注册表增加 CSV/JSON 导入导出、主/回退语言和缺失/重复键报告。
- 执行 500 节点 Procedure 与 2,000 元素/10,000 引用 P95 性能门禁，并完成键盘和可访问性审计。
- 八套 Fabric/NeoForge 的 Stage 9 黄金生成器编译已通过；下一步补齐同轨专用服务端 readiness、datagen/GameTest 适用矩阵与故障夹具。
- 在可用的真实 JCEF 宿主和 Windows 11 干净虚拟机完成 G9.0/G9.5；快速 CI 和 Preview 1 已真实跑绿，但它们不能替代 Stage 9 的产品门禁。

## 首发范围外（除非新 ADR）

Linux / macOS、Windows 10、Authenticode、产品网站、账号云同步、远程 MCP、内置厂商聊天。VMware 不属于当前 G7 范围；这些项目仍是首发范围外，不影响本次收口。
