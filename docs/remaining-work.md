# 剩余完善清单

本页是**状态索引**，不是需求基线。机器可读状态以 [`product-status.json`](../product-status.json) 为准；阶段 8 历史见 [阶段 8 路线](./roadmap/stage-8-windows-beta-ga.md)，阶段 9 历史边界见 [PRD-STAGE-9.md](../PRD-STAGE-9.md)，阶段 10 需求入口是 [PRD-NEXT.md](../PRD-NEXT.md)。

## 当前状态

阶段 8 收口需求 `FR-CLOSE-01`～`FR-CLOSE-08` 均已完成。`v0.1.0-preview.2` 已公开并验证完整 Windows 三包、SBOM、哈希、源元数据和生产审批；`v0.1.0-preview.3` 是历史冻结发布源，后续 main 提交已使其不再等于当前 HEAD。GitHub 已正确识别 GPLv3，Javadoc Pages 可访问，main 保护、真实受保护 PR 与 production 审批均已验证。PR #27 已合入 `main@92d1a8d0`；Nightly `33253594479` 在该固定提交上全绿：Java/Javadoc、完整 Playwright、MCP conformance、诊断包 native JCEF 验收与八生成器黄金编译均通过。FR-AI-03 Workspace Plan、FR-AI-04 Task Events/reconnect、FR-AI-05 SDK/evals、FR-BETA-01 诊断包和 FR-BETA-02 Issue 分流均已关闭。Stage 9 仍有真实 UIA/读屏、最终 RC 和外部试用阻断，外部试用为 0/5，因此 `betaEligible` 仍为 false。

## 当前交付阻断项

| 项 | 状态 | 下一证据 |
| --- | --- | --- |
| 机器状态源 | 已通过 | `product-status.json`、Schema 与漂移校验在 main/PR 持续通过 |
| main 分支保护 | 已验证 | 三项必需检查、严格更新和管理员保护已生效；PR #1～#5 留有全绿记录 |
| Javadoc Pages | 已验证 | <https://lotulune.github.io/Copperbench/> 返回 200 |
| production 审批 | 已验证 | Preview 2 在批准前保持等待；run `32909134939` 留下批准与部署记录后才公开 |
| Nightly | 已通过：`main@92d1a8d0` | [运行 33253594479](https://github.com/Lotulune/Copperbench/actions/runs/33253594479) 的完整 Windows 产品回归、Java/Javadoc/scale、Playwright、MCP、诊断包 native JCEF 验收与八生成器矩阵全部通过 |
| Dependency Submission | 已移除 | Dependency Graph 关闭时不保留必失败工作流 |
| Preview 2 | 已公开 | `v0.1.0-preview.2` 的 Tag、生产审批、三包、SBOM、哈希和资产验证已完成 |
| Preview 3 | 本次发布源 | 从本 PR 合并后的最新 `main` HEAD 创建签名 Tag；公开状态以 GitHub Tag API 和 Release 元数据为准 |
| 诊断包 | 本地候选完成，待固定提交 | 默认脱敏环境/日志/任务，复现文件显式授权；Java 服务 3/3、真实 JCEF 1/1、桥接与 UI 2/2 通过 |
| Issue 分流 | 本地候选完成，待固定提交 | Issue 表单已覆盖 FR-BETA-02 的区域、版本、commit、生成器、元素、预期/实际和诊断码 |
| 外部试用 | 0/5 有可审计记录 | 匿名 Schema、全任务协议和验证器已就绪；仍需五名非核心开发者对同一候选 commit/安装包提交结果 |

## 阶段 9 状态摘要

| 项 | 当前状态 | 证据/备注 |
| --- | --- | --- |
| Procedure 领域模型与编辑器 | 开发预览 | 结构化 IR、未知块保留、Blockly 工作台、预览/保存与引用索引已有自动化覆盖；500 节点真实 JCEF 打开/搜索/编辑/保存/重开门禁已通过 |
| 变量 / 标签 / 语言 | 开发预览 | 稳定 ID、CRUD、重命名影响预览与引用计数已接 UI/MCP/headless；语言 CSV/JSON 导入导出、merge/keep/replace、缺失/重复键统计已实现，剩余是纳入真实 JCEF/Windows 产品门禁 |
| Function / Loot Table / Advancement | UI 已补齐，仍为开发预览 | 专用编辑器、保存和代表性字段编辑在两个 viewport 的 16/16 E2E 中通过；八生成器黄金编译已 8/8 通过，真实 JCEF 主路径仍待补 |
| 服务端 / datagen / GameTest | 运行闭环已关闭 | 受管任务、日志和隔离目录已接入；datagen 支持暂存差异、确认发布与事务回滚；Fabric/NeoForge 26.2、26.1.2、1.21.1、1.20.1 的真实 dedicated-server readiness 8/8 通过 |
| 发布门禁 | 未关闭 | 真实 JCEF 高 DPI/屏幕阅读器、最终 Windows 11 RC 矩阵重放与外部测试者仍需证据；500 节点及 2,000 元素/10,000 引用 fixed-hardware JCEF 性能门禁已通过 |

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

- Function、Loot Table、Advancement 专用编辑器和语言 CSV/JSON 工具已实现；证据见 [Stage 9 UI 门禁记录](./testing/stage-9-ui-gates-2026-08-26.md)。
- 语言 CSV/JSON 导入导出、merge/keep/replace 冲突模式、缺失/重复键统计已经接入 Creator Data 与 Core `languageKeys`；剩余工作是把这一路径纳入真实 JCEF/Windows 产品门禁，而不是继续把它列为未实现功能。
- 500 节点 Procedure 与 2,000 元素/10,000 引用真实 JCEF P95 门禁已通过，见 [Stage 9 fixed-hardware native JCEF scale gate](./testing/stage-9-native-jcef-scale-2026-08-29.md)。Procedure 现有可键盘操作的检查面板和可读节点/端口大纲，真实 JCEF 已验证日志可选择、28 个 Procedure 控件名称与 32px 命中区；剩余是物理高 DPI、屏幕阅读器和完整人工键盘可访问性审计。
- 八套 Fabric/NeoForge 的 Stage 9 黄金生成器编译和同轨真实 dedicated-server readiness 已通过；服务端超时/非零退出也由 8 轨确定性合同验证 fail-closed。证据见 [Stage 9 real dedicated-server readiness](./testing/stage-9-server-readiness-real-2026-08-29.md)。后续运行类工作集中在持续回归 datagen/GameTest 适用矩阵，而不是继续阻断 server-readiness gate。
- 生产 JCEF 对话框焦点、Tab trap、Escape 焦点恢复、polite live region 与 32px 点击目标已有 Windows-native 自动验收；Windows 产品壳现使用 WR 与 Chromium `--force-renderer-accessibility=complete`，并在真实 `DPR=1.25` 下通过 32 个 shell/dialog 控件和 28 个 Procedure 控件审计。新装 Windows 11 客机的首次安装/首次启动，以及真实产品壳的 WorkspaceSelector → 新建工作区 → Generator setup → 原生启动器 `-workspace` 冷启动也已通过，见 [Stage 9 Windows GUI / CLI 产品路径验证](./testing/stage-9-clean-windows-gui-2026-08-28.md)。公开旧版 `v0.1.0-preview.3` → 当前候选升级、断网后目标工作区真实窗口启动、升级/卸载工作区与 `.copperbench` 数据保留也已通过，见 [G9.5 升级、断网与数据保留验证](./testing/stage-9-g95-upgrade-offline-retention-2026-08-29.md)。G9.5 现只剩最终 Public Beta/RC 安装包的完整矩阵重放；物理 150%/175%/200% DPI、Windows 屏幕阅读器互操作性、完整人工键盘审计，以及清洁 Hyper-V 客机 UIA provider 的实际控件暴露仍由独立的真实 JCEF/a11y 门禁阻断。

## 首发范围外（除非新 ADR）

Linux / macOS、Windows 10、Authenticode、产品网站、账号云同步、远程 MCP、内置厂商聊天。VMware 不属于当前 G7 范围；这些项目仍是首发范围外，不影响本次收口。
