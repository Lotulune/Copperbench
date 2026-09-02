# 剩余完善清单

本页是**状态索引**，不是需求基线。机器可读状态以 [`product-status.json`](../product-status.json) 为准；阶段 8 历史见 [阶段 8 路线](./roadmap/stage-8-windows-beta-ga.md)，阶段 9 历史边界见 [PRD-STAGE-9.md](../PRD-STAGE-9.md)，当前 Stage 10/11 与安装产品闭环需求入口是 [PRD-NEXT.md](../PRD-NEXT.md)。

## 当前状态

### 2026-09-02 外部 Agent 安装产品实测纠偏

外部 AI Agent 在已安装 Copperbench 上使用真实 Fabric 26.1.2 工作区完成一次复杂模组开发后，发现此前的自动化门禁没有覆盖“安装包桌面入口真实可用”这一层。原始问题清单见 [AI Agent 安装产品实测改进任务书](./handoffs/ai-agent-experience-fixes.md)。该文档是缺陷证据，不是完成证明。

`v0.1.0-beta.2` 的历史发布控制、资产摘要和 provenance 结论不变；但这次实测重新打开了下一候选的产品级 P0。当前不能再把 MCP conformance/loopback eval、源码布局 runClient 或测试内 `HeadlessCli` 直接解释成“安装后的外部 Agent 与测试客户端可用”。

| 项 | 当前状态 | 下一证据 |
| --- | --- | --- |
| P0 安装布局 Bundled JDK | **blocked（实现/本地发行复验已完成）** | shared resolver、任务级 `BUNDLED_JDK_MISSING`、真实 `exportWin64/jdk` + `testmod2` Gradle build 已通过；仍需把实现绑定到提交并在选定安装候选的桌面 `runClient` 重放后才能升 Gate |
| P0 交互式 `runClient` | **blocked（实现/本地发行树 runtime 复验已完成）** | fresh `exportWin64/jdk` 已真实启动 Fabric 26.1.2 + `testmod2`，readiness 后 Gradle 仍 running，正常关闭 Minecraft 后任务才结束；仍需选定安装候选从真实桌面按钮重放 |
| P0 桌面 MCP 生命周期 | **blocked（实现/发行树 runtime 复验已完成）** | fresh `exportWin64` 产品壳已真实监听随机 loopback 端口并写无 token 连接文件；仍需安装候选从 UI 取得一次性 token，再由外部进程连接 |
| P0 外部 Agent 最小闭环 | **blocked（runtime HTTP 回归已完成）** | 自动化已覆盖 list → create → plan/preview/apply → build/get_task → revision conflict 恢复；仍需同一安装候选 + UI 取得 token 的外部 Agent 重放 |
| P1 headless / code / 用户代码保护 / Agent 文档 | **实现/本地发行复验完成** | fresh `exportWin64` 的真实 EXE 已完成 `headless validate`/`build`（exit 0、单行 JSON），强制 `java.awt.headless=true` 也通过；独立错误 Java 的真实 EXE build 返回 exit 10 + `JAVA_COMPILE_ERROR`（路径+行号），`code` 源码 byte-exact 落盘/code-lock、MCP compile-verification/get_task、用户代码保留与 playbook 均已回归。若候选宣称这些能力，剩冻结候选绑定 SHA 后重放 |
| P2 MCP UI/quickstart/modid 一致性 | **实现/本地验证完成，待固定提交候选** | AIControlView 由 native MCP runtime state 驱动且 4/4 Playwright 通过；Python/TypeScript SDK 与 quickstart 读取 `.copperbench/mcp-connection.json`，不再假设固定 8787；8 条正式 Fabric/NeoForge 轨道的 workspace identity 测试均确认 `archivesName=${modid}`，Fabric 同时确认 `actualmodid=${modid}` |

本轮实现与 fresh Windows export 复验记录见 [P0 AI Agent / installed-layout product fixes](./testing/p0-ai-agent-product-fixes-2026-09-02.md)、[P1 Agent fallback / code diagnostics](./testing/p1-agent-fallback-code-diagnostics-2026-09-02.md) 与 [P2 MCP discovery / workspace identity](./testing/p2-mcp-discovery-workspace-identity-2026-09-02.md)。表中 P0 继续保持 `blocked` 是发布证据边界，不表示上述源码修复尚未实现。

### 2026-08-31 发布控制更新

`v0.1.0-beta.2` 已从 `main@8b063283` 经 merged-main CI `33334020837` 与 Windows release run `33334394466` 成功公开。10 个 Release 资产全部 `uploaded` 且具有 digest；EXE/ZIP/MSIX/SBOM 与冻结候选 `v0.1.0-preview.6` 以及 Beta 1 在 GitHub size 与 SHA-256 上逐项完全一致。

Beta 1 随包 `product.channel=preview` 的元数据漂移已由不可变的新标签 Beta 2 修正。Beta 2 随包状态源为 `product.channel=beta`、`delivery.betaRelease.tag=v0.1.0-beta.2`，`RELEASE-METADATA.json` 指向 `binarySource.mode=promoted-tested-candidate` / `v0.1.0-preview.6`。证据见 [Beta 1 publication verification](./testing/beta1-publication-2026-08-31.md) 与 [Beta 2 publication verification](./testing/beta2-publication-2026-08-31.md)。

阶段 8 收口需求 `FR-CLOSE-01`～`FR-CLOSE-08` 均已完成。当前发布链已完成 Preview 6 候选冻结、Beta 1 exact-binary promotion 与 Beta 2 metadata correction；`main@8b063283` 的 merged-main CI `33334020837` 全绿，Beta 2 release run `33334394466` 全绿。**这仍表示 Beta 2 当时的发布控制没有未完成阻断；2026-09-02 的安装产品实测已重新打开下一候选的产品 P0，不能用这段历史结论覆盖新缺陷。** 真实 JCEF 无障碍、最终 RC 回放和五人外部试用继续明确移出当前 Beta 宣称范围。

## 当前交付阻断项

| 项 | 状态 | 下一证据 |
| --- | --- | --- |
| 机器状态源 | 已通过 | `product-status.json`、Schema 与漂移校验在 main/PR 持续通过 |
| main 分支保护 | 已验证 | 三项必需检查、严格更新和管理员保护已生效；PR #1～#5 留有全绿记录 |
| Javadoc Pages | 已验证 | <https://lotulune.github.io/Copperbench/> 返回 200 |
| production 审批 | 已验证 | Preview 2 在批准前保持等待；run `32909134939` 留下批准与部署记录后才公开 |
| Nightly | 已通过：`main@92d1a8d0` | [运行 33253594479](https://github.com/Lotulune/Copperbench/actions/runs/33253594479) 的完整 Windows 产品回归、Java/Javadoc/scale、Playwright、MCP、诊断包 native JCEF 验收与八生成器矩阵全部通过 |
| Dependency Submission | 已移除 | Dependency Graph 关闭时不保留必失败工作流 |
| Preview 2 | 已公开（历史） | `v0.1.0-preview.2` 的 Tag、生产审批、三包、SBOM、哈希和资产验证已完成 |
| Preview 3 | 历史公开基线 | `v0.1.0-preview.3` 指向 `main@cc15d57a`，release run `32923503840` 成功；已被 Preview 6 取代为当前候选 |
| Preview 6 | 冻结 Beta candidate | `v0.1.0-preview.6` 指向 `main@f677e481`，release run `33330520467` 成功；EXE/ZIP/MSIX/SBOM canonical 摘要已冻结 |
| Beta 1 | 已公开；二进制验证通过、状态源 channel 漂移 | `v0.1.0-beta.1` 指向 `main@f12823ab`，release run `33332537616` 成功；四个 canonical 资产与 Preview 6 完全同 SHA-256，但随包 `product.channel=preview` |
| Beta 2 | 已公开，metadata correction 完成 | `v0.1.0-beta.2` 指向 `main@8b063283`，release run `33334394466` 成功；随包 `product.channel=beta`，四个 canonical 资产仍与 Preview 6/Beta 1 完全同 size/SHA-256 |
| 发布范围 | Public Beta 已发布 | Beta 2 状态源与 exact-binary promotion 已验证；无障碍、最终 RC 和外部试用继续从当前 Beta 合同移出且不宣称已通过 |
| Beta 二进制身份 | 已验证 | Preview 6、Beta 1、Beta 2 的 EXE/ZIP/MSIX/SBOM size/SHA-256 三方完全一致；Beta 2 `RELEASE-METADATA.json` 继续指向 `promoted-tested-candidate` / Preview 6 |
| 诊断包 | 已通过 | `main@92d1a8d0` + Nightly `33253594479` 已固定验证默认脱敏、显式复现授权、Java 服务、真实 JCEF、桥接与 UI 路径 |
| Issue 分流 | 已通过 | `main@92d1a8d0` + Nightly `33253594479` 已固定验证 FR-BETA-02 Issue 表单字段与分流入口 |
| 外部试用 | 当前 Beta 范围不适用 | 五人外测协议和验证器保留，但不再阻断当前 Beta 候选 |
| 安装布局 JDK 解析 | **阻断下一候选；修复已实现** | fresh `exportWin64` 为仅 `jdk/bin/java.exe` 的扁平布局，真实 `testmod2` 已用该 JDK `BUILD SUCCESSFUL`；剩安装候选桌面 `runClient` 重放与 commit/candidate 证据 |
| 交互式 `runClient` | **阻断下一候选；修复已实现** | 产品 task 已与 CI readiness probe 分离，自动化证明 marker 后仍保持 running；剩候选真实游戏窗口生命周期重放 |
| 桌面 MCP 产品接线 | **阻断下一候选；修复已实现** | fresh 导出树的真实产品壳已启动 `127.0.0.1` MCP 并写无密钥连接文件；当前工具宿主无法通过 JCEF UIA 安全点击一次性 token，剩候选人工/可交互桌面外部连接证据 |
| 外部 Agent 安装产品闭环 | **阻断下一候选；协议/runtime 回归已实现** | HTTP runtime 已覆盖完整读写构建/增量日志/冲突恢复；剩安装候选外部 Agent 使用 UI 取得 token 的同链路重放 |

## 阶段 9 状态摘要

| 项 | 当前状态 | 证据/备注 |
| --- | --- | --- |
| Procedure 领域模型与编辑器 | 开发预览 | 结构化 IR、未知块保留、Blockly 工作台、预览/保存与引用索引已有自动化覆盖；500 节点真实 JCEF 打开/搜索/编辑/保存/重开门禁已通过 |
| 变量 / 标签 / 语言 | 开发预览 | 稳定 ID、CRUD、重命名影响预览与引用计数已接 UI/MCP/headless；语言 CSV/JSON 导入导出、merge/keep/replace、缺失/重复键统计已实现，剩余是纳入真实 JCEF/Windows 产品门禁 |
| Function / Loot Table / Advancement | UI 已补齐，仍为开发预览 | 专用编辑器、保存和代表性字段编辑在两个 viewport 的 16/16 E2E 中通过；八生成器黄金编译已 8/8 通过，真实 JCEF 主路径仍待补 |
| 服务端 / datagen / GameTest | 运行闭环已关闭 | 受管任务、日志和隔离目录已接入；datagen 支持暂存差异、确认发布与事务回滚；Fabric/NeoForge 26.2、26.1.2、1.21.1、1.20.1 的真实 dedicated-server readiness 8/8 通过 |
| 发布门禁 | 当前范围已关闭 | 真实 JCEF 高 DPI/屏幕阅读器、最终 RC 矩阵和外部试用已移出当前版本宣称范围；500 节点及 2,000 元素/10,000 引用 fixed-hardware JCEF 性能门禁已有证据 |

当前证据见 [阶段 9 创作者核心验证记录](./testing/stage-9-creator-core-2026-08-25.md)。

## 阶段 8 状态摘要

| 项 | 当前状态 | 证据/备注 |
| --- | --- | --- |
| 产品外壳「新建工作区」 | 工作区生命周期已完成；安装产品 MCP 入口重开 | 落盘、JCEF 宿主打开和测试层 MCP/headless 查询已有证据；2026-09-02 证明这些测试不等于桌面产品会真实启动 MCP，产品入口由 FR-PROD-03/04 重新验收 |
| 八套工作区生成器插件 | 已完成 | 8/8 空工程黄金编译通过，当前 Windows 预览包已对齐 |
| 国内源 / Gradle 池 / 9.7.0 | 已完成（受缓存/网络条件约束） | 当前导出包布局和启动预填已通过；官方专用 Maven 源仍不承诺镜像化 |
| Gradle `--offline` 宣称 | 已完成 | 7 条轨道进入正式列表；NeoForge 1.20.1 明确排除 |
| 第一方纵向切片 | 生成/编译证据已完成；交互式 `runClient` 重开 | 八生成器 × block/item/recipe/procedure 的生成/编译与历史 smoke 证据独立存在；安装产品交互式 `runClient` 的生命周期/JDK 由 FR-PROD-01/02 重新验收 |
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
- 生产 JCEF 对话框焦点、Tab trap、Escape 焦点恢复、polite live region 与 32px 点击目标已有 Windows-native 自动验收；Windows 产品壳现使用 WR 与 Chromium `--force-renderer-accessibility=complete`，并在真实 `DPR=1.25` 下通过 32 个 shell/dialog 控件和 28 个 Procedure 控件审计。新装 Windows 11 客机的首次安装/首次启动，以及真实产品壳的 WorkspaceSelector → 新建工作区 → Generator setup → 原生启动器 `-workspace` 冷启动已通过；同一 GUI 创建的 `guigatedelta` 工作区随后也用客机自带 Wrapper、Copperbench 管理的 JDK/Gradle home 完成真实 `build`、jar 产出与交互式 `runClient`，见 [Stage 9 Windows GUI / CLI 产品路径验证](./testing/stage-9-clean-windows-gui-2026-08-28.md) 和 [Stage 9 clean-Windows workspace lifecycle](./testing/stage-9-clean-windows-workspace-lifecycle-2026-08-29.md)。公开旧版 `v0.1.0-preview.3` → 当前候选升级、断网后目标工作区真实窗口启动、升级/卸载工作区与 `.copperbench` 数据保留也已通过，见 [G9.5 升级、断网与数据保留验证](./testing/stage-9-g95-upgrade-offline-retention-2026-08-29.md)。G9.5 的普通候选路径已经完成；最终 Public Beta/RC 重放、物理高 DPI/屏幕阅读器和完整人工键盘审计已从当前 Beta 宣称范围移出，未来重新纳入时再绑定新候选和新证据。

## 首发范围外（除非新 ADR）

Linux / macOS、Windows 10、Authenticode、产品网站、账号云同步、远程 MCP、内置厂商聊天。VMware 不属于当前 G7 范围；这些项目仍是首发范围外，不影响本次收口。
