# 剩余完善清单

本页是**状态索引**，不是需求基线。机器可读状态以 [`product-status.json`](../product-status.json) 为准；阶段 8 历史见 [阶段 8 路线](./roadmap/stage-8-windows-beta-ga.md)，阶段 9 历史边界见 [PRD-STAGE-9.md](../PRD-STAGE-9.md)，阶段 10～14 需求入口是 [PRD-NEXT.md](../PRD-NEXT.md)。

## 当前状态

### 2026-09-02 Stage 11 / Beta 3 发布收口

Stage 11 的 30 种原 `readOnly` / `legacyOnly` Java Mod Element 已全部进入 first-party `supported`，与既有 7 种核心类型合计形成 37 种第一方 Java Mod Element。固定 `main@f4b58062` 的 merged-main CI `33497929171` 与 Nightly `33503172036` 均已通过；Nightly 包含完整产品回归和 8/8 generator golden。

签名候选 `v0.1.0-preview.7` 已由 Windows release run `33506364499` 成功发布。四个 canonical promotion 资产冻结为：EXE `716a93ea45278d71b2ef80eeee3bd4d0ec315891c349478cff50ed093db90d93`、ZIP `1255a8528cd104d9fb26dfbb6228bfab415d77b84b071b636032c65a1e0282f5`、MSIX `cafcaadf49cb0a59873d47d55ee0f0023e8c6f03c9b820a03994bb75b215faa3`、SBOM `655daa5b9a7a96edba27726169f46146cda391e6e5bfee4fa350da5d4a94c078`。

公开 Preview 7 EXE 在 Windows 11 客机完成了 GUI 新建工作区/`-workspace` 冷启动，以及 `-FinalRcReplay` 的 Preview 3 → Preview 7 升级、断网工作区启动、静默卸载保留和恢复安装；最终 upgrade/offline/uninstall 机器结果为 `passed=true`、`finalRcReplayRequired=false`、`testMarkersRemoved=true`、`gatePromotionReady=true`。图形化 `runClient` 不计为通过：当前 Hyper-V 客机没有可用 OpenGL profile，NeoForge 进入初始化错误窗口。证据见 [Preview 7 candidate qualification](./testing/beta-candidate-preview7-2026-09-01.md)。

`v0.1.0-beta.3` 已由 release-control `7956dcb9930897357e2380ee413cdc4aa928f357` 和 release run `33515908561` 成功公开；GitHub Release 为 `draft=false` / `prerelease=true`，四个 canonical 资产与 Preview 7 在 size / SHA-256 上逐项一致，因此 Stage 11 exact-binary promotion 已完成。发布验证见 [Beta 3 publication verification](./testing/beta3-publication-2026-09-02.md)。

Beta 3 发布后，PR #52 / #53 只加固 Stage 9 测试基础设施：guest-smoke 不再把 Gradle/JDK 二进制缓存当文本扫描，workspace lifecycle 不再硬编码历史 fixture 且会拒绝已知 GLFW/OpenGL 初始化错误窗口，GUI gate 也修复了 generator-setup 观测竞态。当前 `main@af1b6ed9` 的 merged-main CI `33528964018` 已通过 Java/Javadoc、UI、Windows Stage 9 regression、MCP conformance 与 JUnit；这些 harness 变更不改变 Beta 3 产品二进制。

后续不再继续延长 Beta 3 发布门禁。产品开发转入 PRD 第 11 节的 Stage 12～14 深化路线：优先复杂元素专业编辑、Procedure/资产/诊断生产力和高级开发者/AI-native 工作流。图形化 clean-Windows `runClient` 专项认证、真实 JCEF/屏幕阅读器/物理 DPI 审计、五人形式化外部试用、Authenticode、Linux/macOS 与 Windows 10 均明确不作为 Stage 12～14 发布阻断项。

### 2026-08-31 发布控制更新（历史）

`v0.1.0-beta.2` 已从 `main@8b063283` 经 merged-main CI `33334020837` 与 Windows release run `33334394466` 成功公开。10 个 Release 资产全部 `uploaded` 且具有 digest；EXE/ZIP/MSIX/SBOM 与冻结候选 `v0.1.0-preview.6` 以及 Beta 1 在 GitHub size 与 SHA-256 上逐项完全一致。

Beta 1 随包 `product.channel=preview` 的元数据漂移已由不可变的新标签 Beta 2 修正。Beta 2 随包状态源为 `product.channel=beta`、`delivery.betaRelease.tag=v0.1.0-beta.2`，`RELEASE-METADATA.json` 指向 `binarySource.mode=promoted-tested-candidate` / `v0.1.0-preview.6`。证据见 [Beta 1 publication verification](./testing/beta1-publication-2026-08-31.md) 与 [Beta 2 publication verification](./testing/beta2-publication-2026-08-31.md)。

## 当前交付阻断项

| 项 | 状态 | 下一证据 |
| --- | --- | --- |
| 机器状态源 | 已通过 | `product-status.json`、Schema 与漂移校验在 main/PR 持续通过；当前 published baseline 为 Beta 3 |
| main 分支保护 | 已验证 | Java/Javadoc、UI、MCP 三项必需检查持续生效 |
| Javadoc Pages | 已验证 | <https://lotulune.github.io/Copperbench/> 返回 200 |
| production 审批 | 已验证 | Preview/Beta 发布继续经过 `production` Environment 审批；Beta 3 release run `33515908561` 已完成 |
| Nightly | 已通过：`main@f4b58062` | [运行 33503172036](https://github.com/Lotulune/Copperbench/actions/runs/33503172036) 的 Stage 11 固定提交产品回归与 8/8 generator golden 全部通过 |
| Dependency Submission | 已移除 | Dependency Graph 关闭时不保留必失败工作流 |
| Preview 2 | 已公开（历史） | `v0.1.0-preview.2` 的 Tag、生产审批、三包、SBOM、哈希和资产验证已完成 |
| Preview 3 | 历史公开基线 | `v0.1.0-preview.3` 指向 `main@cc15d57a`，release run `32923503840` 成功 |
| Preview 6 | 历史 Beta candidate | `v0.1.0-preview.6` 指向 `main@f677e481`，release run `33330520467` 成功 |
| Preview 7 | Stage 11 冻结候选 | `v0.1.0-preview.7` 指向 `main@f4b58062`，release run `33506364499` 成功；四个 canonical 摘要已冻结，candidate-required install/upgrade/uninstall/offline-retention RC `gatePromotionReady=true` |
| Beta 1 | 已公开（历史） | `v0.1.0-beta.1` release run `33332537616` 成功；二进制与 Preview 6 一致，但随包 channel 漂移 |
| Beta 2 | 已公开（历史） | `v0.1.0-beta.2` release run `33334394466` 成功；修正随包 `product.channel=beta` |
| Beta 3 | **已公开** | `v0.1.0-beta.3` release-control `7956dcb9`，release run `33515908561`；canonical EXE/ZIP/MSIX/SBOM 与 Preview 7 完全同 size/SHA-256 |
| 发布范围 | **Stage 11 已收口** | Beta 3 已发布；后续按 Stage 12～14 独立功能 DoD 迭代，不重新引入明确排除的环境/平台门禁 |
| Beta 二进制身份 | 已验证 | Beta 3 的四个 canonical 资产与 Preview 7 逐字节身份一致；见 Beta 3 publication evidence |
| 诊断包 | 已通过 | `main@92d1a8d0` + Nightly `33253594479` 已固定验证默认脱敏、显式复现授权、Java 服务、真实 JCEF、桥接与 UI 路径 |
| Issue 分流 | 已通过 | `main@92d1a8d0` + Nightly `33253594479` 已固定验证 FR-BETA-02 Issue 表单字段与分流入口 |
| 外部试用 | 后续路线不适用 | 五人形式化外测协议保留为历史工具，但不再作为 Stage 12～14 必做项或版本门禁 |

## Stage 12～14 后续开发摘要

机器状态源当前仍保持 `product.stage=11`，因为 Stage 12 只是规划而尚未完成；具体需求和 DoD 以 [PRD-NEXT.md](../PRD-NEXT.md) 第 11 节为准。

| 计划 | 优先级 | 主要范围 |
| --- | --- | --- |
| Stage 12A | P0 | `livingentity`、`biome`、`dimension`、`gui` 的专业化编辑深度 |
| Stage 12B | P0 | `projectile`、`specialentity`、`overlay`、worldgen 周边类型 |
| Stage 13A/B | P1 | Procedure 2.0、Asset Center、Diagnostics 2.0 |
| Stage 12C / 13C | P1 | 长尾类型专业化、本地历史、Migration/Refactor 工作台 |
| Stage 14 | P2 | manual/generated source 边界、IDE Bridge、AI Plan Review、高层 MCP、模板与扩展开发入口 |
| Continuous | 持续 | Minecraft/Loader、上游 MCreator、工具链、真实性缺陷回归和性能预算 |

## 阶段 9 状态摘要（历史基线）

| 项 | 当前状态 | 证据/备注 |
| --- | --- | --- |
| Procedure 领域模型与编辑器 | 已有产品能力；Stage 13 继续深化 | 结构化 IR、未知块保留、Blockly 工作台、预览/保存与引用索引已有自动化覆盖；500 节点真实 JCEF 打开/搜索/编辑/保存/重开门禁已通过 |
| 变量 / 标签 / 语言 | 已实现 | 稳定 ID、CRUD、重命名影响预览与引用计数已接 UI/MCP/headless；语言 CSV/JSON 导入导出、merge/keep/replace、缺失/重复键统计已实现 |
| Function / Loot Table / Advancement | 已实现 | 专用编辑器、保存和代表性字段编辑 E2E 已通过；八生成器黄金编译 8/8 通过 |
| 服务端 / datagen / GameTest | 运行闭环已关闭 | 受管任务、日志和隔离目录已接入；datagen 支持暂存差异、确认发布与事务回滚；8 条 Fabric/NeoForge 轨道 dedicated-server readiness 通过 |
| 发布门禁 | 当前范围已关闭 | Stage 12+ 不再把真实 JCEF/物理 DPI/屏幕阅读器、graphics clean-Windows runClient 或形式化外部试用作为版本阻断 |

当前历史证据见 [阶段 9 创作者核心验证记录](./testing/stage-9-creator-core-2026-08-25.md)。

## 阶段 8 状态摘要（历史基线）

| 项 | 当前状态 | 证据/备注 |
| --- | --- | --- |
| 产品外壳「新建工作区」 | 已完成 | 落盘、JCEF 宿主打开、MCP/headless 查询与审批测试通过 |
| 八套工作区生成器插件 | 已完成 | 8/8 空工程黄金编译通过 |
| 国内源 / Gradle 池 / 9.7.0 | 已完成（受缓存/网络条件约束） | 官方专用 Maven 源仍不承诺镜像化 |
| Gradle `--offline` 宣称 | 已完成 | 7 条轨道进入正式列表；NeoForge 1.20.1 明确排除 |
| 第一方纵向切片 | 已完成 | 八生成器 × block/item/recipe/procedure 的历史编译证据独立存在 |
| 资产 | 已完成 | `AssetWorkspaceService`、UI-Core、MCP 与产品路径已接真实工作区 |
| 独立资源包 | 已完成 | 创建、骨架、ZIP 导出和客户端准备均通过 |
| Windows 预览包 | 已完成 | 包、插件、README、安装/升级/卸载演练通过 |
| G7 | 已完成 (`passed`) | Hyper-V Win11 客机断网、安装/升级/卸载、工作区和用户数据保留均有历史证据 |

## 两套生成器（不要混用证据）

可视化「新建工作区」列出的是**工作区生成器插件**，不是版本轨道里的第一方纵向切片。

| 加载器 | 对话框已有插件 | 空工程黄金编译 |
| --- | --- | --- |
| Fabric | 26.2、26.1.2、1.21.1、1.20.1 | 4/4 通过 |
| NeoForge | 26.2、26.1.2、1.21.1、1.20.1 | 4/4 通过 |

Stage 11 已进一步把 37 种第一方 Java Mod Element 接入统一 supported 路径，并对适用组合执行完整 8-generator Gradle/JAR matrix。后续不再用“插件树里每一种类型是否能编译”作为模糊表述，具体 capability 以机器状态源和 generator capability/reason code 为准。

## 网络与 Gradle（限制，不是待办）

- Fabric Maven 与 NeoForge 专用仓库仍走官方地址。阿里云 / BMCLAPI / 华为云 Maven 不是 Fabric 的完整代理。
- 导出时仅当构建机本地已有 9.7.0 / 8.8 才会打进 `gradle-dists`。
- 产品自身构建仍用 Gradle 9.6.0；工作区发行包共用 `%USERPROFILE%\.copperbench\gradle`。
- 官方 Stage 8 `--offline` 宣称已对齐为 7 条通过轨道；NeoForge 1.20.1 保留未宣称限制，见 [`stage-8-offline-claims-2026-08-23.md`](testing/stage-8-offline-claims-2026-08-23.md)。

## 明确排除（不是待办）

以下项目不属于 Stage 12～14 的必做项或发布阻断：专门寻找 graphics-capable clean Windows 环境重做 `runClient` 认证；真实 JCEF + UIA/屏幕阅读器认证和物理 DPI 人工审计；形式化五人外部试用；Authenticode；Linux/macOS；Windows 10；Bedrock；账号、云同步、遥测、远程 MCP、内置厂商聊天或在线市场。若未来决定重新纳入其中任何一项，应通过新的 ADR/PRD 显式改变范围。

“排除”不表示删除已有产品路径。真实用户报告的可复现产品 bug 仍按正常优先级修复，并应转化为稳定回归测试。
