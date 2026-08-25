# 剩余完善清单

本页是**状态索引**，不是需求基线。阶段 8 历史见 [阶段 8 路线](./roadmap/stage-8-windows-beta-ga.md)，阶段 9 历史边界见 [PRD-STAGE-9.md](../PRD-STAGE-9.md)，当前阶段 10 的唯一需求入口是 [PRD-NEXT.md](../PRD-NEXT.md)。排除项见 [open-decisions.md](./open-decisions.md) 与 [ADR-0015](./adr/0015-github-unsigned-gpl-fork.md)。

## 当前状态

阶段 8 收口需求 `FR-CLOSE-01`～`FR-CLOSE-08` 均已完成。G7 已通过 Hyper-V Win11 客机的断网启动、安装生命周期、数据保留和当前包验证。阶段 9 创作者核心闭环已经可运行，但 G9.2～G9.5 的性能、八生成器、全轨服务端和干净虚拟机门禁尚未全部关闭；这些缺口已纳入阶段 10。

## 阶段 9 状态摘要

| 项 | 当前状态 | 证据/备注 |
| --- | --- | --- |
| Procedure 领域模型与编辑器 | 核心路径完成 | 结构化 IR、未知块保留、Blockly 工作台、预览/保存与引用索引已有自动化覆盖 |
| 变量 / 标签 / 语言 | 基础闭环完成 | 稳定 ID、CRUD、重命名影响预览与引用计数已接 UI/MCP/headless；语言导入导出待补 |
| Function / Loot Table / Advancement | 第一方 CRUD 完成 | 可真实写回上游工作区；专用编辑器深度和 8 生成器黄金编译待补 |
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
- 在 Fabric/NeoForge 的 26.2、26.1.2、1.21.1、1.20.1 上执行阶段 9 黄金生成器编译与专用服务端 readiness/故障夹具。
- 在可用的真实 JCEF 宿主和 Windows 11 干净虚拟机完成 G9.0/G9.5；CI/发行入口已在仓库侧重建，仍需 GitHub `main` 与真实 Tag 独立跑绿。

## 首发范围外（除非新 ADR）

Linux / macOS、Windows 10、Authenticode、产品网站、账号云同步、远程 MCP、内置厂商聊天。VMware 不属于当前 G7 范围；这些项目仍是首发范围外，不影响本次收口。
