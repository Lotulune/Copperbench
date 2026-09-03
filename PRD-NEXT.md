# Copperbench PRD：Public Beta 基线、Stage 11 全量 Mod Element、安装产品 Agent 闭环与后续深化路线

> 状态：Public Beta `v0.1.0-beta.4` 已发布；Stage 11 全量 Java Mod Element 与 2026-09-02 重新打开的 4 个安装产品 P0 门禁均已闭环，Stage 12～14 路线进入可执行状态
> 版本：v1.7
> 更新日期：2026-09-03
> 前置基线：[PRD.md](./PRD.md)、[PRD-STAGE-9.md](./PRD-STAGE-9.md)
> 当前公开 Beta：`v0.1.0-beta.4`（release-control `29ac9cf2`，release run `33751421385`）；canonical EXE/ZIP/MSIX/SBOM 与签名候选 `v0.1.0-preview.8` 完全同 size/SHA-256；安装产品 P0 行为证据来自 `d0d96877`，其到 Preview 8 的 tracked delta 不含产品/runtime/build/SDK 实现变更

## 2026-09-03 Beta 4 安装产品闭环与发布收口

2026-09-02 外部 Agent 实测重新打开的 bundled JDK、交互式 `runClient`、Desktop MCP、external-Agent 四个产品级 P0 已在后续 hardening 中全部关闭。最终安装产品候选 `d0d96877` 完成 clean Windows 11、真实桌面 Run Client、一次性 MCP 凭据、完整 Agent 读写/plan/build/conflict/code-compile 与正常关闭失效验证；相关 beta-blocking gates 已全部恢复为 `passed`。

签名候选 `v0.1.0-preview.8` 随后从 `main@69469b8f` 构建并通过正式 release tests、Windows 三件套打包、SBOM、payload/provenance 与资产摘要校验。`v0.1.0-beta.4` 由 release-control `29ac9cf2` 通过 release run `33751421385` 采用 `promote-tested-candidate` 路径公开，EXE、ZIP、MSIX 与 SBOM 均逐字节复用 Preview 8 的冻结资产，没有重新构建第二套 Beta 二进制。详见 [Beta 4 publication evidence](./docs/testing/beta4-publication-2026-09-03.md)。

因此 Stage 12～14 不再被 FR-PROD-01～04 阻断；但后续若修改对应的 JDK、Run Client、Desktop MCP 或 external-Agent 产品路径，或出现回归证据表明已验证行为发生变化，就必须重新打开并复验相应 gate。

## 2026-08-31 Public Beta publication and metadata correction

`v0.1.0-beta.1` 已从 release-control `main@f12823ab` 通过 release run `33332537616` 成功公开。`RELEASE-METADATA.json` 明确记录 `binarySource.mode=promoted-tested-candidate`，EXE/ZIP/MSIX/SBOM 与签名 `v0.1.0-preview.6` 的大小和 SHA-256 全部一致，因此 Beta 1 没有重新构建候选二进制。详见 [Beta 1 publication verification](./docs/testing/beta1-publication-2026-08-31.md)。

发布后验收发现 Beta 1 随包的权威 `product-status.json` 仍声明 `product.channel=preview`；该元数据问题已由不可变的新标签 `v0.1.0-beta.2` 修正。Beta 2 release run `33334394466` 成功，随包 `product.channel=beta`、`delivery.betaRelease.tag=v0.1.0-beta.2`，并继续原样晋升 Preview 6 的四个 canonical 资产。真实 JCEF 无障碍审计、最终 clean-Windows RC replay 和五人外部试用继续排除在当前 Beta scope 之外，不宣称其已通过。详见 [Beta 2 publication verification](./docs/testing/beta2-publication-2026-08-31.md)。

## 2026-09-02 Stage 11 与 Beta 3 收口

Stage 11 已把现有 30 种原 `readOnly` / `legacyOnly` Java Mod Element 全部提升为 first-party `supported`，与既有 7 种核心类型合计形成 37 种第一方 Java Mod Element 覆盖。全类型 CRUD、持久化与未知字段保留、导入/迁移、UI/MCP/headless 语义、诊断脱敏以及 8-generator Gradle/JAR matrix 均已取得固定实现证据。

签名候选 `v0.1.0-preview.7` 来自 `f4b58062`；其 exact-binary promotion `v0.1.0-beta.3` 已由 release-control `7956dcb9` 和 release run `33515908561` 成功公开。Beta 3 的 EXE、ZIP、MSIX 与 SBOM 与 Preview 7 canonical 资产逐项保持相同 size / SHA-256，因此 Stage 11 的“实现 → Nightly → 候选 → Windows 安装/升级/卸载 → exact-binary Beta”历史闭环已完成。

Beta 3 发布后又完成两组只影响测试基础设施的 Stage 9 harness 加固：IPC 扫描不再误读 Gradle/JDK 二进制缓存，workspace lifecycle 不再硬编码测试工作区且不会把 GLFW/OpenGL 初始化错误窗口判定为 `runClient` 成功；GUI gate 也修复了 workspace 主窗口先出现时可能漏采 generator setup dialog 的竞态。发布后 harness 验证基线 `main@af1b6ed9` 的 Java/Javadoc、UI、Windows Stage 9 regression、MCP 与 JUnit merged-main CI 全绿。这些后续 harness 变更不改变 Beta 3 产品二进制。

## 2026-09-02 外部 Agent 安装产品实测纠偏

外部 AI Agent 使用已安装 Copperbench 与真实 Fabric 26.1.2 工作区完成模组开发后，提交了 [AI Agent 安装产品实测改进任务书](./docs/handoffs/ai-agent-experience-fixes.md)。该实测不是“已完成”证据，而是对既有验收模型的反例：**协议测试、loopback MCP eval、源码布局和 Nightly 绿色，不能替代已安装产品上的真实入口验证。**

因此即日起将以下 4 项重新定义为下一候选的 P0 门禁：

1. **安装布局 JDK 解析**：发行包只有 `jdk/bin/java.exe` 时，Fabric/NeoForge 运行任务不得继续按源码树 `jdk/jbr25_win_64` / `jdk/jdk21_win_64` 路径设置无效 `JAVA_HOME`。
2. **交互式 `runClient` 生命周期**：产品“测试客户端”必须保持 Minecraft 运行直到用户主动关闭；readiness marker 只能用于 CI/冒烟探针，不能作为产品交互任务的成功条件，更不能触发杀进程。
3. **桌面产品 MCP 生命周期**：已打开工作区的桌面进程必须真实启动 loopback MCP、签发工作区凭据并呈现真实连接状态；“AI 与 MCP”页不得在没有客户端连接/监听事实时写死“已连接”。
4. **外部 Agent 最小闭环**：必须从同一个已安装候选、由桌面产品实际暴露的 MCP 地址完成 `initialize/get_workspace → list → create → preview/plan/apply → build/get_task → revision conflict 重读重试`，不得以测试 fixture 或专用 conformance 进程替代。

P1/P2 随后补齐 headless 产品入口、JDK/runClient 结构化诊断、generate 保留用户代码、`code` 元素与 `initialValues` Agent 文档、真实 MCP 连接文件/quickstart、真实 modid/导出名和客户端 API 一致性。所有状态提升必须在代码、自动测试和**安装候选复演**同时存在后才允许关闭。

## 0. 阅读与执行协议

- 本文件同时承担阶段 10～11 的历史产品基线、安装产品 Agent/运行闭环纠偏与 Stage 12～14 后续产品需求入口；`PRD.md` 和 `PRD-STAGE-9.md` 作为更早已实现能力与历史需求基线。
- 功能状态以源码、自动测试和固定提交产生的证据为准。README、Release Notes 或历史 evidence 不能单独证明能力已完成。
- 每个实现任务和 PR 必须引用本文件中的需求编号。
- 关闭需求必须同时满足实现、自动验证、用户文档和可追溯证据，不接受只修改状态文字。
- 涉及安装布局、桌面入口、MCP 生命周期或交互式运行任务时，测试 harness / loopback eval / 源码树验证只证明对应层级；**不得替代同一候选安装包上的端到端验证**。
- Stage 10～11 已建立“提交 → CI → 产物 → Release”的可信闭环。Stage 12 起不再把专门的图形环境认证、真实 JCEF 无障碍、五人外部试用、Authenticode、Linux/macOS 作为通用版本门禁；但 FR-PROD-01～04 是已确认影响 Windows 安装产品主路径的功能正确性 P0，不能按环境排除项绕过。

### 0.1 实施快照（2026-09-03）

机器可读事实入口为 [`product-status.json`](./product-status.json)，本节只解释执行状态。

| 项目 | 当前事实 | 结论 |
| --- | --- | --- |
| Fast CI | main 分支保护持续要求 Java/Javadoc、UI/Playwright 与 MCP；安装产品 hardening 合并后的 merged-main Build and test #176、Beta 4 release-control merged-main #179 均全绿 | 当前 Beta 4 产品与 release-control 基线已闭环；后续 Stage 12+ 继续按 protected CI 提升 |
| 公开 Release | `v0.1.0-beta.4` 已由 release-control `29ac9cf2` 公开，release run `33751421385` 成功 | Preview 8 → Beta 4 exact-binary promotion 已完成；四个 canonical 资产摘要与冻结候选一致 |
| 状态源 | `product-status.json`、Schema 和 CI 漂移校验已进入 main，并已修复首次远端运行暴露的 Release Schema 漏项 | 状态源门禁通过；后续状态提升仍必须带固定提交证据 |
| 重型门禁 | Stage 11 Nightly 保持 8/8 generator golden；post-review binary-source `d0d96877` 的 Build and test #173 与 Nightly #28 全绿 | Stage 11 与安装产品 P0 都有各自固定提交证据，Beta 4 可作为当前公开产品基线 |
| Stage 9 | 八生成器黄金编译、三个专用编辑器、语言工具、安装升级和离线工作区路径已有自动化/客机证据；Windows WR + Chromium 完整 AXMode 与 `DPR=1.25` 路径已通过 | 物理高 DPI、屏幕阅读器、最终 RC 矩阵和外部试用不纳入当前版本宣称，未来重新纳入时需新候选和新证据 |
| AI Developer Kit | Cursor、Workspace Plan、Task Events/SDK、Windows-native JCEF reconnect 与安装产品 external-Agent 闭环均已取得固定证据 | Core/协议层与安装产品入口均 `passed`；后续新增高层 AI 能力按 Stage 14 独立验收 |
| 安装产品创作 / Agent 闭环 | `d0d96877` 完成 bundled JDK、真实桌面 Run Client、Desktop MCP、外部 Agent 完整读写/plan/build/conflict/code-compile/关闭失效复演 | **P0 passed**；Beta 4 已代表该闭环，Stage 12～14 不再受 FR-PROD-01～04 阻断 |
| 仓库治理 | GitHub 已识别 GPLv3；Javadoc Pages 可访问；main 三项必需检查、受保护 PR 与 production 必需审阅者均已验证 | 仓库治理 P0 已闭环 |
| Dependency Submission | 仓库 Dependency Graph 未启用，原工作流无法成功 | 误导工作流已从 main 删除 |

## 1. 背景与问题

阶段 8 已完成八套生成器空工程构建、Windows 打包和 G7 演练；阶段 9 已实现 Procedure IR、Blockly、注册表、Function/Loot Table/Advancement CRUD、服务端、datagen 和 GameTest 的开发预览。

截至 2026-08-25，代码能力仍高于公共交付能力，主要差距是：

1. `v0.1.0-preview.2` 已证明完整发布链路，但发布后的状态同步提交使公开 Tag 不再等于最新 `main`；Preview 3 用一次冻结提交恢复严格一致性。
2. 快速 CI、分支保护、真实 PR、Nightly 和人工发布审批配置已闭环；下一签名 Preview 候选必须在最终合并 SHA 上保留检查、签名 Tag 和生产审批记录。
3. 机器状态源已建立；PRD、状态索引和发布说明仍必须在每次状态提升时同步更新。
4. Stage 9 的大型工作区、专用编辑器、语言工具和服务端 readiness 已有固定证据；真实 JCEF/a11y、最终 clean-Windows RC 与外部试用已明确移出当前 Beta 宣称范围，保留为未来重新纳入时的证据项。
5. MCP 的大型列表 Cursor、Workspace Plan、Task Events/reconnect 与 SDK/evals 在 Core/协议层已闭环；2026-09-02 安装产品实测证明桌面 MCP 启动/发现、发行布局和真实 Agent 入口此前未被该门禁覆盖，因此产品级 AI readiness 重新成为下一候选阻断项。

因此，阶段 10 的产品目标不是增加更多元素，而是把当时已有能力变成可下载、可验证、可解释、可反馈的 Public Beta 基线；阶段 10 的发布控制已经完成。后续产品范围同时由 7.4 的 Stage 11 全量元素计划与 5.7 的安装产品创作/Agent 闭环接管。

## 2. 阶段 10 目标与成功指标

### 2.1 产品目标

- 从干净 Tag 自动生成 Windows EXE、ZIP、MSIX、SHA-256、SBOM 和 provenance。
- GitHub Release 公开前自动确认三个二进制真实存在。
- `main` 和面向 `main` 的 PR 持续运行 Java、UI、MCP、Schema、Javadoc 和文档链接门禁。
- 建立单一机器可读状态源，并由它生成或校验 README、用户文档、MCP Release Notes 和发布说明。
- 关闭 Stage 9 中会阻止外部测试者使用现有创作闭环的验证缺口。
- 提供足以让第三方客户端完成“读取 → 预览 → 提交 → 构建 → 处理冲突”的 AI Developer Kit。

### 2.2 成功指标

| 指标 | 目标 |
| --- | --- |
| `main` CI | 连续 3 次独立运行全部通过，且至少 1 次来自 PR |
| Release 完整性 | 100% 同时包含 EXE、ZIP、MSIX、哈希、SBOM、元数据和许可证 |
| 源码可追溯性 | 100% 公开二进制可映射到唯一 Tag 和 commit SHA |
| 文档本地链接 | CI 中 0 个失效链接 |
| Stage 9 定向测试 | 0 失败；环境跳过必须有稳定原因码 |
| 大型元素遍历 | 2,000 元素可通过 Cursor 完整遍历，无遗漏或重复 |
| 安装布局 JDK | 同一 resolver 覆盖发行 `jdk/bin/java.exe` 与源码嵌套 JDK；无有效 JDK 时返回稳定诊断且不注入无效 `JAVA_HOME` |
| 交互式 `runClient` | 已安装候选启动 Minecraft 后保持 running 直到用户关闭；marker 不杀进程、不把缺 marker 当产品失败 |
| 桌面 MCP | 已安装候选打开工作区后真实监听 loopback MCP；UI/连接文件中的 URL、workspaceId、权限与实际服务一致 |
| 外部 Agent 最小闭环 | 外部客户端从桌面产品暴露的 MCP 完成读 → 写 → 构建 → `get_task(afterLogSequence)` → revision 冲突恢复，不依赖测试 harness |
| 外部试用 | 当前 Beta scope 为 N/A；未来重新纳入时至少 5 名非核心开发者完成新建/迁入、创建元素、构建和恢复 |
| P0 发布缺陷 | 发布后 7 天内 0 个“无法下载/资产缺失/版本不明”缺陷 |

## 3. 用户与核心场景

### 3.1 普通创作者

作为 Windows 11 用户，我能从 GitHub 下载与说明一致的包，验证哈希，创建 Fabric/NeoForge 工作区，完成基础创作和构建；遇到预览能力时能看到明确状态和回退入口。

### 3.2 模组开发者

作为已有 MCreator 工作区的开发者，我能在复制迁入后识别哪些元素正式支持、开发预览或只读，并在 Stage 9 数据元素与任务失败时获得可定位诊断。

### 3.3 AI 客户端开发者

作为本机 AI 集成开发者，我安装并打开 Copperbench 工作区后，不需要另起测试服务就能获得真实 MCP 地址/工作区信息；我能按版本化 Schema 遍历大型工作区，预览并提交多步修改、处理 revision 冲突、读取长任务增量日志并完成构建。UI 不会把“服务已监听”和“已有客户端连接”混为一谈。

### 3.4 发布维护者

作为维护者，我不能从脏工作树或无 Tag 的提交发布；不完整 Release 会停留在草稿状态，不能被工作流公开。

## 4. 范围与工作包

| 工作包 | 需求编号 | 用户可感知结果 |
| --- | --- | --- |
| CI 可信基线 | FR-CI-01～04 | 主分支和 PR 始终有公开验证结果 |
| Windows 可信发布 | FR-REL-01～06 | Release 有真实包、哈希、SBOM 与源码信息 |
| 单一状态源 | FR-STATUS-01～03 | README、产品和 Release 不再互相矛盾 |
| Stage 9 质量门禁 | FR-S9-01～05 | 现有预览能力具备规模与多轨证据 |
| AI Developer Kit | FR-AI-01～05 | 第三方客户端能稳定接入大型项目 |
| 安装产品创作 / Agent 闭环 | FR-PROD-01～06 | 已安装 Windows 产品上的 JDK、runClient、MCP 与外部 Agent 使用路径真实可用 |
| 试用与反馈闭环 | FR-BETA-01～03 | 外部问题可复现、可分流、可量化 |

## 5. 功能需求

### 5.1 CI 可信基线

#### FR-CI-01 主分支覆盖

- `push: main` 与 `pull_request: main` 必须触发 `Build and test`。
- feature/dev 分支可保留 push 验证，但不能替代 `main` 门禁。
- 所有 Gradle 命令只使用 Wrapper。

验收：从 `main` 提交和面向 `main` 的测试 PR 各保存一次成功运行 URL。

#### FR-CI-02 分层验证

每个 PR 至少运行：

- Java compile/test 与 Javadoc；
- UI-Core Schema 测试；
- UI Shell TypeScript/Vite 构建；
- 新建工作区与核心状态的快速 Playwright；
- MCP conformance；
- Markdown 本地链接；
- 产品身份、许可证与品牌相关测试。

生成器黄金构建进入 nightly；Minecraft 服务端、真实 JCEF、可访问性和干净 Windows 11 VM 进入具备对应环境的人工/自托管门禁，不阻塞每个轻量 PR，也不得因无法在托管 Runner 运行而标记通过。

#### FR-CI-03 可诊断失败

- Java JUnit、Playwright 和 MCP conformance 结果在失败时仍上传。
- 报告保留至少 14 天；MCP conformance 保留至少 30 天。
- 并发更新取消旧 CI，发布工作流不得被新运行取消。

#### FR-CI-04 Javadoc 发布

- Javadoc 使用 Wrapper 生成。
- 部署任务具备最小必要写权限。
- `main` Java 源码变化、每周定时和手动触发均可运行。

### 5.2 Windows 可信发布

#### FR-REL-01 发布来源

- 仅接受已存在的 `vX.Y.Z` 或 `vX.Y.Z-preview.N` Tag。
- Tag 必须指向检出的 `HEAD`，并与 `product.version` 一致。
- 工作树有 tracked/untracked 变化时失败。
- 发布元数据记录 commit SHA、Tag、UTC 构建时间、平台和签名状态。

#### FR-REL-02 发布产物

同一发布载荷必须包含：

- Windows NSIS EXE；
- Portable ZIP；
- MSIX；
- `SHA256SUMS.txt`；
- `RELEASE-METADATA.json`；
- SPDX JSON SBOM；
- `LICENSE.txt` 与第三方声明；
- GitHub Artifact Attestation build provenance。

#### FR-REL-03 发布前验证

- 先创建 draft prerelease，再逐个上传资产。
- 自动检查 EXE/ZIP/MSIX、哈希、元数据和 SBOM 名称存在。
- 检查失败时保持 draft，不得执行公开步骤。
- 公开后使用 GitHub API/CLI 再记录一次资产清单证据。

#### FR-REL-04 未签名透明度

下载页和 Release Notes 必须明确：Windows 11 x64、未签名、SmartScreen 风险、校验方法、对应 commit、正式/预览/只读能力和已知限制。

#### FR-REL-05 可复现性记录

本阶段不要求不同机器字节级完全一致，但必须记录 JDK/JCEF、Gradle Wrapper、runner image、依赖锁定信息和各资产 SHA-256。相同 Tag 的二次构建若哈希不同，必须能解释时间戳或打包器差异。

#### FR-REL-06 首个可信预览

不完整的 `v0.1.0` 草稿已删除。`v0.1.0-preview.1` 已证明基础链路，`v0.1.0-preview.2` 已证明三包、SBOM、生产审批和公开验证；`v0.1.0-preview.3` 已从当时最新全绿 `main@cc15d57a` 创建签名 Tag 并由 release run `32923503840` 公开。后续公开版本必须使用新 Tag，并继续要求 Tag、`RELEASE-METADATA.json` commit 与发布时目标 `main` HEAD 完全一致；不再通过发布后的追赶提交伪造一致性。

### 5.3 单一机器可读状态源

#### FR-STATUS-01 状态模型

版本化 `product-status.json` 是状态事实入口，至少包含：

- 产品版本、阶段和目标平台；
- 版本轨道与生成器支持状态；
- 元素的 `supported`、`preview`、`readOnly`、`legacyOnly`；
- G7/Stage 9 门禁状态与证据路径；
- MCP 协议/Schema 版本；
- 已知限制和最后验证提交。

#### FR-STATUS-02 生成与校验

README 功能表、用户帮助、`ReleaseManifest` 投影和 Release Notes 必须由状态源生成，或在 CI 中与状态源逐字段校验。禁止手工维护无法检测漂移的重复矩阵。

#### FR-STATUS-03 状态变更规则

状态只能在对应测试和证据同一 PR 中提升。`preview → supported` 必须引用门禁；证据失效或版本升级时自动回落为 `unverified`，不能沿用旧结论。

### 5.4 Stage 9 质量门禁

#### FR-S9-01 大型 Procedure

500 节点 Procedure 在目标 Windows 11 机器上完成打开、搜索、编辑、保存和重新打开。记录首次打开时间、交互 P95、内存峰值和载荷保真结果。

#### FR-S9-02 大型工作区

2,000 元素、10,000 引用的工作区完成列表、过滤、引用查询、重命名预览和恢复点创建。P95 目标在固定硬件证据中定义并稳定复测。

#### FR-S9-03 三种数据元素黄金编译

Function、Loot Table、Advancement 在八套 Fabric/NeoForge 生成器上运行黄金生成与 Gradle 编译。失败必须给出生成器/类型/版本组合和稳定诊断码。

#### FR-S9-04 受管任务矩阵

八套生成器验证专用服务端 readiness；适用轨道验证 datagen 和 GameTest。任务状态、取消、日志、失败回滚和暂存发布均有自动测试。

#### FR-S9-05 JCEF 与可访问性

在真实 JCEF 宿主运行 Stage 9 主路径；完成键盘焦点、对话框陷阱、状态播报、最小点击目标和高 DPI 检查。阻断级问题清零后才可进入公开 Beta。

### 5.5 AI Developer Kit

#### FR-AI-01 版本化资料

建立 `docs/ai/`、`schemas/`、`sdk/`、`examples/`，发布工具目录、权限、错误码、revision 冲突、重试/幂等和版本弃用规则。

#### FR-AI-02 Cursor 分页

所有大型列表统一支持 `cursor`、`limit`、`sort`、`filter`、`fields`、`nextCursor`。旧的 page/pageSize 在一个预览周期内兼容并标记弃用。

验收：2,000 元素完整遍历无遗漏、无重复；非法/过期 Cursor 返回稳定错误码。

状态（2026-08-27）：`passed`。PR #14 合入 `main@515c212c`；合并后 CI run `32997587858` 与 Nightly `32998281437` 均全绿，Nightly 已上传 2,000 元素 Cursor 结构化证据。该结论不替代独立的 `workspace-2000-10000` 固定硬件 P95 Gate。

#### FR-AI-03 原子工作区计划

提供 `plan_workspace_changes`、`preview_workspace_plan`、`apply_workspace_plan`，支持多操作顺序、统一 revision、幂等键、语义差异、权限评估、单恢复点和全量回滚。

实施状态（2026-08-28）：**passed**。首个内容计划切片由 PR #15 合入 `main@1f31b2b6`；固定提交 Windows Nightly `33098518016` 已在明确 source `main@e8caf018` 上全绿，`WorkspacePlanEngineTest` 5/5 通过，完整 Java/Javadoc/规模回归、Playwright 与 MCP conformance 均通过。支持边界复核确认计划仅覆盖元素 create/update/delete、Procedure 更新和 registry create/update/delete/rename；构建、运行、迁移、导入、datagen 发布和其他外部副作用仍留在任务/审批边界外，不并入内容原子计划。该 Nightly 是 PR #15 merge 的 main 后代，满足固定提交可追溯要求。

#### FR-AI-04 长任务事件

构建、客户端/服务端、datagen、GameTest 支持进度、日志、诊断、确认请求、取消和重连恢复；轮询接口继续兼容。

实施状态（2026-08-28）：**passed**。PR #16 的 Task Events 与 PR #17 的 Windows-native reconnect 验收均已进入 `main@e8caf018`。固定提交 Windows Nightly `33098518016` 的 Java 报告为 341 tests / 0 failures；`WorkspaceTaskEventTest` 3/3 通过，Windows-only `Stage10NativeJcefTaskReconnectTest` 1/1 真实执行并通过，标准输出明确初始化 JCEF `137.0.17.1142.68de80bc86de497c8d0632ad0f8fe33625b33bff`、CEF `137.0.17` 与 Chromium `137.0.7151.104`。同一 Nightly 的完整 Playwright 与 MCP conformance 也通过。Core 事件、JCEF retained replay、取消与序列缺口恢复因此取得固定提交 native 证据。MCP/Headless 的冻结恢复路径继续保持 `get_task(afterLogSequence)`；在没有明确版本边界前不引入自定义 push notification 方言。

#### FR-AI-05 SDK 与评测

提供 TypeScript/Python 最小客户端和至少 10 个 AI eval，覆盖创建元素、Procedure 修改、重命名引用、构建修复、revision 冲突、越权拒绝、datagen 取消/发布和恢复。

实施状态（2026-08-28）：**passed**。`sdk/typescript`、`sdk/python`、`sdk/evals/manifest.json` 和最小示例已随 PR #16 合入；10 项 manifest/覆盖校验与真实 loopback HTTP MCP live eval **10/10** 均已通过（含独立 read-only 权限会话）。固定提交 Windows Nightly `33098518016` 在包含 PR #16 的 `main@e8caf018` 上完成全量 Java/Javadoc、Playwright、MCP conformance 与 8/8 generator golden，提供了缺失的 merged implementation Nightly 证据。Nightly 不被表述为重新执行 live-eval harness；10/10 仍由专用 HTTP MCP 评测记录证明。

纠偏（2026-09-02）：上述 `passed` 继续证明 SDK、协议、权限和 loopback server 能力，不再被解释为“已安装桌面产品会自动启动 MCP，外部 Agent 可直接发现并连接”。产品级入口改由 FR-PROD-03/04 单独验收。

### 5.6 外部试用与反馈

#### FR-BETA-01 诊断包

提供脱敏环境摘要、日志、任务结果和可选最小复现导出。令牌、用户名和外部路径默认移除，用户确认后才附加文件。

实施状态（2026-08-29）：已通过。帮助页可导出本地 ZIP；默认仅包含环境摘要、脱敏日志、工作区安全摘要、活动任务和结构化诊断。附加最小复现文件必须显式勾选，并受目录、扩展名、文件数、单文件和总大小边界限制。Java 服务、真实 JCEF、桥接、UI 和 Nightly 固定提交证据均通过。

#### FR-BETA-02 Issue 分流

Issue 模板必须收集版本、commit、生成器、元素类型、复现步骤、预期/实际结果和诊断码，并区分安装、工作区、生成器、UI、MCP 与文档问题。

实施状态（2026-08-29）：已通过。Issue 表单已补齐区域、版本/完整 commit、生成器、元素类型、预期/实际结果、诊断码/错误 ID 和诊断包入口，并由合并后 Nightly 固定提交验证。

#### FR-BETA-03 外部试用任务

至少 5 名非核心开发者完成：下载安装、校验、创建或迁入工作区、创建元素、构建、制造一次失败、查看诊断、创建恢复点并恢复。结果形成匿名汇总，不以口头反馈替代。

实施状态（2026-08-30）：匿名证据 Schema、统一任务协议和机器验证器已就绪；根据当前收尾决策，外部试用不属于本版本 Preview-only 交付范围，因此不再作为本版本阻断项。未来重新开启 Beta 试用时，仍要求五名非核心开发者使用同一签名候选和同一 EXE SHA-256。

### 5.7 安装产品创作与 Agent 闭环（2026-09-02 重开）

本节专门覆盖“代码/协议存在，但发行包真实入口没有工作”的风险。FR-PROD-01～04 于 2026-09-02 被定义为下一候选 **P0**；不得通过缩小宣称范围将其排除，因为它们直接影响 Windows 11 x64 已安装产品的基础创作与本机 AI 集成主路径。

实施状态（2026-09-03）：FR-PROD-01～04 已由 `d0d96877` 安装候选复演关闭，并由 `v0.1.0-beta.4` 公开代表。以下需求与验收条款继续作为长期回归合同；只有相关产品路径发生变化或出现新的回归证据时才重新打开对应 gate。

#### FR-PROD-01 Bundled JDK 实际布局解析（P0）

建立共享 JDK resolver，由 `distributionRoot + javaRelease` 得到实际可执行的 Java home，而不是由 generator profile 直接拼发行相对路径：

1. 优先识别发行布局 `{root}/jdk/bin/java.exe`；
2. Java 25 源码布局可识别 `{root}/jdk/jbr25_win_64/bin/java.exe`；
3. Java 21 源码布局可识别 `{root}/jdk/jdk21_win_64/bin/java.exe`；
4. 最后可回退到仍含 `bin/java.exe` 的 `System.getProperty("java.home")`；
5. 全部不可用时返回稳定 `BUNDLED_JDK_MISSING`（或等价稳定代码），正文列出解析根与尝试路径，不得把不存在的目录写入 `JAVA_HOME`。

Fabric/NeoForge 的所有 Gradle 任务必须复用同一个 resolver；发行布局描述也必须明确“源码嵌套布局”和“安装后扁平布局”不是同一目录结构。

验收：单测模拟两类 JDK 布局和无 JDK 失败路径；随后用实际安装候选在 Fabric 26.1.2 至少完成一次 `build` + `runClient` 启动，日志不得再出现指向不存在 `jdk/jbr25_win_64` 的 `JAVA_HOME`。

#### FR-PROD-02 交互式 `runClient` 生命周期与诊断（P0）

产品外壳的 `RUN_CLIENT` 是创作者交互任务，不是 readiness smoke probe：

- Minecraft 启动后任务保持 `running`，直到用户关闭客户端；不得因看到 generator marker 主动销毁游戏进程。
- 缺少 marker 不能单独导致产品任务失败。需要 marker 的 CI 验证应使用独立 operation、显式 smoke flag 或专用验证脚本。
- Gradle 在客户端真正启动前非 0 退出应失败；JDK/Wrapper 等已知错误必须映射为结构化诊断，同时保留原始 Gradle 日志和解析后的 Java home。
- UI/任务说明明确这是 Fabric/NeoForge 开发客户端，不能以“看起来像原版”作为加载失败判断。

验收：安装候选打开真实工作区，启动客户端并保持到用户主动关闭；任务期间 UI 为 running；关闭后正确收口；无效 JDK 用 JDK 诊断码而不是 `readiness marker` 失败包装。

#### FR-PROD-03 桌面 MCP 生命周期、凭据与真实状态（P0）

工作区被桌面产品成功打开后，由持有 `WorkspaceFileLease` 的桌面进程负责本机 MCP 生命周期：

- 在 `127.0.0.1` 启动实际 `CopperbenchMcpServer`（动态或受控端口），并确保 `tools/list` 能正确反映工具能力；
- 为当前 workspaceId 签发默认 `workspace` 权限的凭据，令牌不得进入普通日志；
- 在工作区本地 `.copperbench/mcp-connection.json` 写入可供客户端发现的 URL、workspaceId、permissionProfile、expiresAt 等非秘密/受控连接信息；
- “AI 与 MCP”页显示真实监听状态、地址、工作区与权限，并提供 Grok/Cursor/Claude 等客户端配置片段；未监听时显示未启动，服务已监听但尚无客户端时不得写成“已连接”；
- 关闭/切换工作区时停止对应 MCP、吊销凭据并使连接文件失效。

验收：实际安装候选打开工作区后，独立外部进程可以对 UI 所示地址执行 MCP `initialize` 和 `get_workspace`，得到一致的 workspaceId/revision；工作区关闭后旧连接不可继续使用。

#### FR-PROD-04 外部 Agent 安装产品最小闭环（P0）

使用**同一个已安装候选**、桌面产品真实创建的 MCP 连接，外部 Agent 必须完成：

1. `initialize` / `get_workspace`；
2. `list_mod_elements` 按 cursor 遍历到 `nextCursor=null`；
3. `create_mod_element` 至少创建 `item`，并再覆盖 `code` 或 `projectile` 之一；
4. `preview_mod_element_change` 或 `plan_workspace_changes` → `apply_workspace_plan`；
5. `build_workspace` → `get_task(afterLogSequence)` 直到终态并保留诊断；
6. 人为制造一次旧 revision 写入，得到 `WORKSPACE_REVISION_CONFLICT`，重读后成功重试。

该门禁不得使用测试专用 `McpConformanceServerMain`、直接 `new HeadlessCli` 或人工修改 Java 作为替代。证据至少记录候选 SHA、workspace generator、MCP URL 的端口级信息（不含令牌）、操作序列、最终 revision、build 结果和脱敏任务日志。

#### FR-PROD-05 Agent/代码创作安全与 fallback（P1）

- 为安装的 `copperbench.exe` 提供 `headless --workspace <path> <command>` 入口，作为桌面 MCP 故障时的第二条受支持自动化路径。
- `create_mod_element type=code` 能提交 Java 源并返回编译诊断；复杂战斗/实体行为不应被迫全部表达成 Procedure。
- `docs/ai/` 或 Agent playbook 增加 `initialValues`、item/projectile/procedure 最小示例、推荐调用顺序、revision 冲突策略和令牌处理规则。
- generate / runClient 前置 generate 必须证明保留 `// Start of user code block` 及非 generator-owned 用户包；不得以重新生成为由静默删除用户代码。
- 示例和文档使用各受支持 Minecraft/Fabric/NeoForge 轨道的真实 API，不复制不兼容版本教程。

2026-09-02 本地实现状态：上述 P1 能力已完成到源码与 fresh `exportWin64` 预候选复验层。安装产品入口支持
`copperbench.exe headless --workspace <path> <command>`，headless 主进程固定 `java.awt.headless=true`、机器 stdout
固定 UTF-8 JSON，且不进入单实例 GUI/JCEF 生命周期；真实 `validate` 已 exit 0。MCP 直接
`create_mod_element(type=code)` 会在提交后自动启动真实 `build_workspace` compile-verification task，Agent 通过
现有 `get_task` 取得 Gradle/javac 诊断。编译诊断已在中文 Windows javac 输出下验证为去重后的
`JAVA_COMPILE_ERROR`，包含工作区相对源码路径与行号。用户代码块/独立用户包保留及 Agent playbook 也已有回归。
这些是 **pre-candidate evidence**；若发布候选继续把 headless/code 作为正式产品能力，仍需在冻结候选上重放并绑定候选 SHA。

#### FR-PROD-06 体验与发行一致性（P2）

- 客户端渲染等生成模板跟进目标版本已废弃 API；
- `AIControlView`、帮助页和状态栏共享真实 MCP runtime state；
- Python/TypeScript quickstart 默认优先读取工作区 `.copperbench/mcp-connection.json`，不得假设固定 `8787`；
- 新建工作区的 `actualmodid`、archive name、导出 JAR 与用户填写 modid 保持一致。

2026-09-02 hardening 的实施顺序为 **FR-PROD-01 → FR-PROD-02 → FR-PROD-03 → FR-PROD-04 → FR-PROD-05 → FR-PROD-06**。这些 P0 当前已在 Beta 4 基线关闭；未来若因产品变更或回归重新打开，仍必须同时具备代码、自动测试和安装候选证据，不能只凭单元测试或源码布局恢复绿色再次关闭。

## 6. 阶段 10 非目标（已完成的 Public Beta 基线）

- 阶段 10 Public Beta 不新增 Living Entity、GUI/Menu、Tool/Armor、Worldgen 等模组元素；该限制在阶段 10 收口后失效，Stage 11 下一功能版本将当前 30 种 read-only / legacy-only Java Mod Element 全部纳入 first-party 支持范围。
- Linux/macOS、Windows 10、商店、账号、云同步和远程 MCP。
- 购买 Authenticode 或宣称解决 SmartScreen。
- 完整替代 IDE、上游全部 Swing 编辑器或全部插件。
- 首个预览版实现差分更新器、自动更新或遥测。
- 在未完成 FR-S9 门禁前将 Stage 9 开发预览改称正式支持。

## 7. 发布阶段与退出门禁

### 7.1 Internal Alpha

- FR-CI-01～04 全部通过。
- FR-REL-01～05 在手动 `publish=false` 运行中生成完整 Actions artifact。
- 本地 Markdown 链接为 0 失败。

### 7.2 Public Preview

- FR-REL-06 完成，Release 真实可下载。
- 发布说明与源码状态一致。
- 至少 2 名外部试用者完成下载、安装、新建、构建和卸载复验。
- 无 P0/P1 发行缺陷。

### 7.3 Public Beta

- FR-STATUS、FR-S9、FR-AI、FR-BETA 全部完成。
- 至少 5 名外部试用者完成全任务。
- 所有高风险能力有固定提交证据；未验证项明确降级或移出宣称。
- 对 2026-09-02 之后建立的新候选，FR-PROD-01～04 也必须全部 `passed`；这四项属于已安装 Windows 主路径，不允许通过缩小宣称范围绕过。

当前公开版本为 `v0.1.0-beta.4`。Beta 4 由签名候选 `v0.1.0-preview.8` 通过 release-control `29ac9cf2` / release run `33751421385` 以 exact-binary 方式原样晋升；其 EXE/ZIP/MSIX/SBOM 与冻结 Preview 8 canonical assets 完全一致。后续产品或构建变更若要再次发布，仍必须重新建立候选与相应证据。

2026-09-02 历史纠偏：Beta 3 当时只证明 Stage 11 的发布控制事实，外部实测随后重新打开 FR-PROD-01～04，并禁止用 Beta 3 的历史 Nightly/MCP conformance 替代安装产品门禁。该阻断已在后续 hardening 与 `d0d96877` 安装候选复演中关闭，并由 Beta 4 公开代表；这段记录保留用于解释为何这些安装产品门禁存在，而不再表示当前 release blocked。

### 7.4 下一功能版本（Stage 11）：全量 Mod Element 一等支持

**产品决策：下一功能版本必须把当前 30 种 `readOnly` / `legacyOnly` Java Mod Element 全部升级为 Copperbench first-party `supported`。** 开发过程允许分批落地，但下一功能版本不得在仍有这些类型停留于 `readOnly`、`legacyOnly`、`unsupportedInNewUi` 或“只能导入不能修改”的情况下宣称完成。

实施状态：**Stage 11 已完成；2026-09-02 后续安装产品实测重新打开的 FR-PROD-01～04 也已在 Beta 4 前全部关闭。** 30 种目标 Java Mod Element 已全部进入 first-party `supported` 路径；全类型持久化/未知字段 round-trip、导入、迁移、UI/status contract 和核心 Java 回归通过。固定提交 `f4b58062` 的 Nightly `33503172036` 保留 Stage 11 产品回归与 8/8 generator golden 历史证据；`d0d96877` 及其安装产品复演补齐 FR-PROD-01～04；`v0.1.0-beta.4` 最终通过 Preview 8 exact-binary promotion 公开这些修复。未来候选若改变相关产品路径，必须重新绑定 CI/Nightly、安装产品复演、provenance 与候选资产。

本阶段覆盖以下 30 种类型：

- 装备与物品扩展：`armor`、`armortrim`、`tool`、`itemextension`。
- 注册表与玩法数据：`attribute`、`bannerpattern`、`command`、`damagetype`、`enchantment`、`gamerule`、`keybind`、`painting`、`particle`、`potion`、`potioneffect`、`tab`、`villagerprofession`、`villagertrade`。
- 世界与内容生成：`biome`、`dimension`、`feature`、`fluid`、`plant`、`structure`。
- 实体与界面：`livingentity`、`specialentity`、`projectile`、`gui`、`overlay`。
- 高级代码元素：`code`。

Bedrock Add-on 的 `bebiome`、`beblock`、`beentity`、`beitem`、`bescript` 属于不同平台产品范围，不计入上述 30 种 Java Mod Element 的完成度；若未来纳入 Bedrock，应单独建立平台 PRD 和生成器矩阵，不能用 Bedrock 的延期来降低本阶段 30 种类型的完成标准。

#### 7.4.1 “支持”定义

每一种类型只有同时满足以下条件，才能从 `readOnly` / `legacyOnly` 提升为 `supported`：

1. **完整 CRUD**：新 UI 可以创建、打开、编辑、保存和删除；应用服务不再以 `ELEMENT_TYPE_OUTSIDE_FIRST_PARTY_SLICE` 拒绝该类型的正常 create/update。
2. **类型化编辑体验**：建立可维护的字段模型、默认值、约束和专用或类型化 first-party 编辑器；不得以通用 JSON 文本框作为最终正式体验。
3. **无损兼容上游工作区**：导入包含该类型的 MCreator 工作区后，未修改字段和未知扩展字段必须 round-trip 保留；打开并保存不能静默丢字段、改语义或破坏 generator-owned 数据。
4. **引用与迁移正确**：凡涉及元素引用、资源引用、变量/标签、重命名或跨 loader/version 迁移的类型，都必须进入 reference index、影响预览和冲突诊断；不可产生悬空引用而无稳定诊断。
5. **生成与编译闭环**：每种类型至少有一个真实 golden fixture；对当前 Fabric/NeoForge 8-generator 矩阵中声明支持该类型的组合执行生成与 Gradle 编译。若某 generator/type 组合确实不适用，必须有显式 capability/reason code，不得静默跳过。
6. **MCP / headless 等价能力**：AI/自动化客户端可以查询 schema/capability，并通过与 UI 同一应用服务完成 create/read/update/delete、预览、验证和构建；不能形成“UI 能改、MCP 不能改”或反向的第二套产品语义。
7. **诊断和恢复**：非法字段、版本不兼容、资源缺失、引用冲突、生成失败必须返回稳定原因码；写操作继续进入 local history / recovery-point 保护。
8. **自动化证据**：每一种类型都必须有核心单测、UI/contract 覆盖、导入 round-trip 测试和 generator golden 证据；状态提升必须与测试和证据在同一 PR 中完成。

#### 7.4.2 实施分批

分批仅用于控制工程风险，**不代表下一版可以只交付其中一批**：

- Wave 1（高频创作核心）：`livingentity`、`gui`、`armor`、`tool`、`biome`、`dimension`、`fluid`、`projectile`。
- Wave 2（世界、表现与交互）：`feature`、`plant`、`structure`、`specialentity`、`overlay`、`particle`、`potion`、`potioneffect`、`enchantment`、`command`、`keybind`。
- Wave 3（长尾与高级类型）：`armortrim`、`attribute`、`bannerpattern`、`code`、`damagetype`、`gamerule`、`itemextension`、`painting`、`tab`、`villagerprofession`、`villagertrade`。

每个 Wave 合入后都应保持 `main` 可构建、可运行、可回退，并持续更新 `product-status.json`。Wave 1～3 是元素功能完成的必要条件；FR-PROD-01～04 已在 Beta 4 基线关闭，后续候选应持续证明这些 gate 未发生回归，而不是把它们当作尚未完成的前置任务。

## 8. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| Windows 包体积过大导致下载失败 | 同时提供 EXE/ZIP/MSIX、哈希，记录大小；后续评估 Standard/Offline 分包 |
| 未签名导致用户不信任 | 明示签名状态，提供 SBOM、provenance、commit、哈希和可复现说明 |
| 全量测试过慢 | PR 快速门禁、nightly 重矩阵、发布前全门禁分层 |
| 状态源与源码再次漂移 | CI 做结构化投影比对，状态提升必须携带测试证据 |
| MCP 协议演进破坏客户端 | 版本协商、弃用周期、兼容测试和 SDK fixture |
| 测试/loopback 绿色但安装产品入口未接线 | 对安装布局、桌面 MCP、交互式 runClient 和外部 Agent 主路径建立独立候选 Gate；低层测试只能证明自身层级 |
| generate / 运行任务破坏用户自定义代码 | 用户代码块与非 generator-owned 包加入回归 fixture；运行前自动 generate 也必须做无损验证 |
| 大型工作区性能目标不现实 | 先固定硬件/数据集测基线，再以 P95 回归阈值管理 |

## 9. 依赖与负责人边界

- GitHub 仓库管理员：配置 `main` 分支保护、Actions 权限和可选 `production` Environment 审阅者。
- 发布维护者：创建并推送干净 Tag，审核 Release Notes，执行外部安装复验。
- Core：状态源、分页、计划事务、任务事件和性能基线。
- UI：能力状态呈现、JCEF/可访问性、诊断导出、真实 MCP runtime state 与配置复制。
- Generator：八套黄金编译、服务端 readiness、Bundled JDK 解析与交互式 `runClient` 生命周期。
- MCP/Core integration：桌面工作区打开/关闭时的 MCP server、token、连接文件和 lease 生命周期。
- Docs/SDK：AI Developer Kit、安装产品 Agent playbook、连接发现、快速开始、故障排查和示例。

## 10. Definition of Done

当前 Beta 发布控制以以下条件为准：

1. 所有 FR 有对应代码、测试、文档和固定提交证据。
2. `main` 分支保护要求 Java、Frontend、MCP conformance 三个检查。
3. 公开 Release 有完整资产且能从 GitHub 匿名下载。
4. Release 的 commit、哈希、SBOM 和 provenance 可由非维护者验证。
5. README、产品帮助、MCP Release Notes 和 GitHub Release 的能力状态一致。
6. Stage 9 剩余门禁全部关闭，或未关闭能力从 Beta 范围明确移除。
7. 外部试用若未执行，必须明确标记为不适用并从当前版本宣称范围移除；不得把未执行试用写成通过。

### 10.1 Stage 11 下一功能版本 Definition of Done

1. 当前 30 种 `readOnly` / `legacyOnly` Java Mod Element 全部进入 first-party `supported`；`product-status.json` 对这些 Java 类型不再保留 `readOnly` / `legacyOnly` 状态。
2. `ElementCoverageCatalog` / 应用服务不再把这 30 种列入 `UNSUPPORTED_IN_NEW_UI`，正常 create/update 不再触发 `ELEMENT_TYPE_OUTSIDE_FIRST_PARTY_SLICE`。
3. 新 UI、MCP 和 headless 对全部类型共享同一 schema、验证、CRUD、诊断与持久化语义；不得以 legacy Swing 窗口作为完成这 30 种支持的替代方案。
4. 建立“全类型 golden workspace”：至少包含每种类型一个有效实例，并在 8-generator 矩阵的所有适用组合完成生成与 Gradle 编译；PR 可分片，Nightly/Release candidate 必须执行完整矩阵。
5. 建立上游兼容 fixture：覆盖这 30 种类型的 MCreator 工作区可以导入、浏览、编辑、保存、重新打开并构建，未编辑及未知字段无静默损失。
6. 全量元素支持不得回退现有 Block/Item/Recipe/Procedure/Function/Loot Table/Advancement、工作区生命周期、构建运行、诊断、MCP、历史恢复和发布链能力。
7. 下一功能版本候选必须从新的冻结 commit/tag 重新执行 CI、Nightly、Windows 安装/升级/卸载、Release provenance 与候选资产验证；不得直接复用 `v0.1.0-beta.2` 二进制。
8. FR-PROD-01 的发行/源码两种 Bundled JDK 布局均自动验证，并在实际安装候选上消除无效 `JAVA_HOME`；失败时提供稳定 JDK 诊断。
9. FR-PROD-02 的产品 `runClient` 在实际安装候选中保持长寿命，直到用户关闭客户端；CI readiness marker 与交互任务语义已经分离。
10. FR-PROD-03 的桌面产品真实启动并关闭 MCP；连接信息、workspaceId、权限和 UI 状态与实际 runtime 一致，不存在静态“已连接”假状态。
11. FR-PROD-04 在同一安装候选上由独立外部 Agent 完成读 → 写 → 构建 → 增量任务日志 → revision 冲突恢复；不得由测试专用 MCP server 代替。
12. generate/runClient 回归证明用户代码块与非 generator-owned 用户包不被删除；headless 必须从实际 Windows 导出/安装产品返回 UTF-8 机器 JSON，`code` 创建必须给出可继续轮询的 compile-verification task，真实 javac 失败必须产生带源码路径/行号的 `JAVA_COMPILE_ERROR`；若下一版本继续宣称 headless/code 为一等 AI fallback，则这些 FR-PROD-05 证据必须在冻结候选上重放。

阶段 10 与 Stage 11 均已完成；2026-09-02 安装产品实测证明原 Definition of Done 对发行布局与外部 Agent 入口覆盖不足，并由 FR-PROD-01～04 补齐。**当前正确状态是“Beta 4 已公开、安装产品闭环 passed、Stage 12～14 可继续开发”；后续候选若触及这些路径则必须重新证明相应 gate 未回归。** 具体历史门禁见 5.7、7.4 与 10.1；后续功能深化路线见第 11 节。

## 11. Stage 12～14 后续产品深化路线

### 11.0 路线决策与执行原则

`v0.1.0-beta.4` 之后，Copperbench 已具备“真实模组项目可用”且经过安装产品 Agent/运行闭环复演的基础基线。后续开发不再追求更多 `supported` 类型数量，而是把已经支持的 37 种 Java Mod Element 从“能够创建/保存/生成/构建”继续深化到“复杂项目中高效、可理解、可诊断、可维护”。FR-PROD-01～04 已关闭；新的 Preview/Beta/RC 仍需保持这些 gate 为 `passed`，若产品变更或回归证据重新打开 gate，则在候选建立前重新验证。

后续路线遵循以下原则：

1. **优先解决真实创作摩擦，而不是增加无关门禁数量。** 用户能否更快完成复杂实体、世界生成、GUI、Procedure 和资产工作流，比新增环境认证更重要；已确认的产品正确性 P0 不属于可排除的环境认证。
2. **每个阶段可以独立开发与验收。** Stage 12、13、14 不是必须一次完成的单一大版本；每个 Wave 达到自身 DoD 后即可进入候选准备，同时保持 Beta 4 已关闭的 FR-PROD-01～04 不回归。
3. **保持 Core 单一语义。** UI、MCP、headless、导入/迁移、历史恢复继续共享相同 schema、验证、引用和持久化模型，不为某个界面创建第二套业务规则。
4. **优先高频复杂类型。** `livingentity`、`biome`、`dimension`、`gui` 是 Stage 12 第一优先级；其它类型按对真实模组开发的价值继续分批深化。
5. **复杂项目必须可恢复。** 批量修改、迁移、AI 操作和高级编辑器仍必须进入 revision / recovery-point / semantic diff 保护。
6. **不为已明确排除的环境项重新制造发布阻断。** 第 11.6 节列出的专项认证不进入 Stage 12～14 DoD；但真实用户已经复现的安装产品功能缺陷仍按正常 P0/P1 管理。

### 11.1 总体拆分

| 阶段 | 优先级 | 核心目标 | 主要用户收益 | 建议交付方式 |
| --- | --- | --- | --- | --- |
| Stage 12：复杂元素编辑深度 | P0 | 把高价值复杂元素从“技术支持”提升为“专业创作体验” | 不依赖通用字段面板即可完成常见实体、世界生成和 GUI 场景 | 12A / 12B / 12C 三个可独立开发 Wave |
| Stage 13：创作者生产力 | P1 | 深化 Procedure、资产、诊断、历史和迁移工作流 | 大型项目更快定位、修改、构建和恢复 | 可按 Procedure / Asset / Diagnostics 三条线并行 |
| Stage 14：高级开发者与 AI-native 工作流 | P2 | 强化手写代码、IDE、批量重构、AI 计划审阅和扩展能力 | 高级作者能把 Copperbench 当作长期工程环境而非单次生成器 | API 稳定后逐项开放，不要求一次完成 |
| Continuous：版本与兼容维护 | 持续 | 保持现有能力随 Minecraft、Loader、JDK/JCEF/Gradle 与上游工作区演进 | 已有项目不因平台升级快速失效 | 与每个功能版本并行执行 |

---

### 11.2 Stage 12：复杂 Mod Element 编辑深度

#### 11.2.1 产品目标

Stage 11 已解决“37 种 Java Mod Element 是否能作为 first-party 类型被创建、编辑、保存和构建”的问题；Stage 12 解决“复杂类型是否足够适合真实日常创作”的问题。

Stage 12 不要求复制上游 MCreator 每一个 Swing 控件，而要求围绕常见创作任务建立 Copperbench 自己的类型化工作流：字段分组、资源/元素选择器、预览、引用提示、条件显示、诊断和生成器 capability 必须形成一致体验。

#### FR-DEPTH-01 Living Entity 与实体创作工作台

为 `livingentity` 建立第一优先级专用编辑体验，并复用公共实体组件到 `specialentity`、`projectile`：

- 基础身份、尺寸、属性、模型/纹理、声音和资源引用分区；
- 行为、事件/Procedure 引用、掉落、生成条件等按 generator capability 显示；
- 所有元素/资源引用使用统一选择器，显示缺失和跨版本不兼容诊断；
- 修改后可查看语义摘要与生成影响，不要求用户阅读原始 JSON；
- 上游未知字段继续无损保留，专用编辑器不得因“未显示字段”而删除数据。

#### FR-DEPTH-02 Biome / Dimension 世界环境编辑工作台

优先深化 `biome` 与 `dimension`，建立可复用的 World/Registry 编辑组件：

- 环境参数、视觉/声音、生成规则、结构/feature、资源引用按领域分组；
- registry/resource picker 必须显示来源、类型和版本 capability；
- 不适用字段必须显式解释原因，不得静默隐藏导致用户误以为已生效；
- 跨 Fabric/NeoForge 或版本迁移时给出字段级保留、转换、降级和待人工处理清单。

#### FR-DEPTH-03 GUI / Overlay 可视化布局工作台

深化 `gui` 与 `overlay`：

- 提供层级/组件树、属性检查器和基础布局预览；
- 控件事件可以直接绑定 Procedure / Function 等已有元素；
- 图片、纹理和字体等资源引用进入统一资产选择器；
- 对分辨率、锚点、尺寸和重叠问题提供即时诊断；
- 保存结果继续走 Core schema，不允许 UI 产生无法被 MCP/headless 读取的私有格式。

#### FR-DEPTH-04 Worldgen 与内容生成组件

在 FR-DEPTH-02 的公共组件基础上深化 `feature`、`structure`、`fluid`、`plant`：

- worldgen/resource 引用统一可视化；
- 结构、feature、放置规则和维度/生物群系关系可被引用索引追踪；
- 资源缺失、循环引用、generator 不支持和版本漂移给出稳定诊断码；
- 代表性 fixture 必须进入适用 generator 的真实生成/构建回归。

#### FR-DEPTH-05 装备、战斗与玩法数据编辑组件

深化 `armor`、`tool`、`enchantment`、`potion`、`potioneffect`、`damagetype`、`attribute`、`itemextension`：

- 建立可复用的数值、枚举、装备槽、效果、属性 modifier 与资源引用控件；
- 显示默认值、合法范围和 loader/version 差异；
- 常见错误在保存前以字段级诊断阻断，而不是等待 Gradle 编译失败。

#### FR-DEPTH-06 长尾数据与系统类型整理

对 `command`、`gamerule`、`keybind`、`painting`、`particle`、`tab`、`armortrim`、`bannerpattern`、`villagerprofession`、`villagertrade`、`code` 等长尾类型统一完成：

- 字段分组和类型化控件；
- capability / reason code 可见；
- 元素/资源引用可跳转；
- 创建模板与默认值可解释；
- UI、MCP、headless schema 一致。

#### 11.2.2 Stage 12 分批

| Wave | 范围 | 原因 | 完成后可独立进入功能验收 |
| --- | --- | --- | --- |
| 12A | `livingentity`、`biome`、`dimension`、`gui` | 最能决定复杂模组开发是否“顺手”的四类高价值元素 | 是 |
| 12B | `projectile`、`specialentity`、`overlay`、`feature`、`structure`、`fluid`、`plant` | 复用 12A 的实体、UI 和 worldgen 基础组件 | 是 |
| 12C | 装备/战斗/系统/长尾类型 | 统一剩余类型的专业化编辑与字段体验 | 是 |

#### 11.2.3 Stage 12 Definition of Done

每个 Wave 完成必须满足：

1. Wave 内高频常见场景可以仅使用类型化 UI 完成，不要求编辑原始 JSON 或手写生成器文件。
2. UI、MCP、headless 对新增字段模型保持同一 schema / validation / persistence 语义。
3. 上游工作区 open → edit → save → reopen 未编辑字段与未知字段无静默损失。
4. 代表性 fixture 在所有声明支持的 generator/type 组合生成并构建成功；不适用组合有明确 capability/reason code。
5. 引用、资源缺失、非法值和迁移差异具有字段级诊断和可跳转位置。
6. Wave 合入不得降低 Stage 11 既有 37 类型 CRUD 与 8-generator golden 基线。

---

### 11.3 Stage 13：创作者生产力与大型项目体验

#### 11.3.1 产品目标

Stage 13 不增加新的 Mod Element 类型，目标是降低真实项目在“找东西、改逻辑、管理资产、理解失败、恢复改动”上的时间成本。为避免与 5.7 已固定并被代码/证据引用的安装产品 `FR-PROD-*` 冲突，本节统一使用 `FR-PRODUCTIVITY-*` 编号。

#### FR-PRODUCTIVITY-01 Procedure 工作台 2.0

在现有 Blockly / Procedure IR 基础上继续深化：

- 节点/动作搜索、分类过滤和最近使用；
- 变量、元素引用、资源引用和调用关系侧栏；
- 节点级即时诊断与错误路径跳转；
- 大型 Procedure 的结构大纲、搜索结果导航和选中节点定位；
- 常见重构操作，例如提取可复用逻辑、批量替换引用或重命名影响预览，必须先生成 semantic diff / recovery point；
- 继续保持 500 节点基线为回归下限，不因增强 UI 退化已有性能与序列化保真。

#### FR-PRODUCTIVITY-02 Asset Center 统一资产中心

把纹理、模型、声音、语言和其它资源从“文件集合”提升为项目资产系统：

- 统一资产树、搜索、类型过滤和预览；
- 显示“被哪些元素使用”的反向引用；
- 检测缺失资产、失效路径、重复资产和可安全清理的未使用资产；
- 支持拖放/批量导入，并在写入前预览目标路径和冲突；
- Blockbench 往返继续使用受管外部进程，但保存后自动刷新引用、预览和 recovery point；
- 资产重命名/移动必须通过引用索引预览影响，禁止静默制造悬空引用。

#### FR-PRODUCTIVITY-03 Diagnostics 2.0：从日志到元素/字段

建立统一的“失败 → 原因 → 位置 → 修复建议”链：

- Gradle、generator、资源处理、迁移、MCP 和运行任务错误统一映射为稳定错误 ID；
- 尽可能映射到具体 Mod Element、字段路径、Procedure 节点或资产；
- UI 提供“打开元素”“跳到字段”“查看生成源码”“打开完整日志”等明确动作；
- 对可确定的常见问题提供修复建议或安全的一键修正计划；
- 自动修复必须经过 semantic diff / recovery point，不允许直接修改后无法回退。

#### FR-PRODUCTIVITY-04 本地历史与恢复体验

深化现有 local history / recovery point：

- 支持用户命名恢复点和自动恢复点来源标签；
- 展示元素、字段、资产层面的语义差异，而非仅文件 diff；
- 恢复前明确列出受影响对象；
- AI、迁移、批量重构和 datagen 发布统一使用同一恢复时间线；
- 恢复后自动运行引用和结构完整性检查。

#### FR-PRODUCTIVITY-05 Migration / Refactor 工作台

把 loader/version migration 从一次性转换提升为可审阅工程操作：

- 迁移前 capability matrix；
- 元素级“可直接转换 / 自动降级 / 需人工处理 / 不适用”分类；
- 批量重命名、移动、替换引用先生成影响图和计划；
- 默认复制到新工作区，除非未来 ADR 明确允许原地迁移；
- 迁移结果可与源工作区做语义对比。

#### FR-PRODUCTIVITY-06 Workspace Health 项目健康面板

提供一个不依赖构建失败才发现问题的项目视图：

- 诊断总数、悬空引用、缺失资产、未使用资产、generator capability 差异；
- 最近失败任务和恢复点；
- 高风险迁移/AI 批量变更提示；
- 只显示可解释的静态事实，不引入未经用户同意的远程遥测评分。

#### 11.3.2 Stage 13 Definition of Done

1. 代表性的“创建/修改 → 构建失败 → 定位 → 修复 → 重建”流程可以在 Copperbench 内完成，不要求用户手工翻找 Gradle 目录。
2. Procedure、资产、诊断和历史共享同一引用索引与 revision/recovery 机制。
3. 资产移动、批量重命名和迁移均有影响预览，不产生无诊断的悬空引用。
4. 500-node Procedure 与 2,000-element / 10,000-reference 大型工作区既有性能基线不得出现显著回归。
5. 关键生产力动作通过 UI 与 MCP 暴露同一 Core 能力；MCP 不直接操作 UI 私有状态。

---

### 11.4 Stage 14：高级开发者与 AI-native 工程工作流

#### 11.4.1 产品目标

Stage 14 面向希望长期维护复杂项目、愿意使用 Java/IDE 或外部 AI 的高级模组作者。目标不是把 Copperbench 变成完整 IDE，而是让可视化创作、生成代码、手写扩展和 AI 修改能够在一个可审阅、可恢复的工程模型中协作。

#### FR-ADV-01 Generated / Manual Source 工程边界

- 对 generated source、manual source、generator-owned resource 建立清晰 ownership；
- generated 文件只读展示并可查看“由哪个元素/字段生成”；
- manual source 提供独立目录和生命周期，重新生成不得覆盖；
- 当 manual code 引用被迁移/删除的元素时进入 reference/diagnostic 系统；
- 生成前后可以查看源码差异，但源码 diff 不替代元素 semantic diff。

#### FR-ADV-02 IDE Bridge

- 从工作区一键打开 IntelliJ IDEA / VS Code 等外部 IDE；
- 传递正确的工作区、Wrapper、JDK 与 Gradle 环境信息；
- 外部编辑后文件变化由 Copperbench 检测并更新诊断/历史；
- 不承诺成为 IDE 调试器，也不复制 IDE 的代码智能功能。

#### FR-ADV-03 AI Plan Review 工作台

在现有 Workspace Plan / MCP 上增加面向创作者的审阅界面：

- 按元素、字段、资产和 Procedure 节点展示 AI 计划；
- 高风险操作分组显示并要求用户确认；
- 应用前自动 recovery point；
- 应用后展示实际结果与计划差异；
- 长任务继续使用 Task Events / `get_task(afterLogSequence)` 可恢复模型，不创建第二套 AI 专用任务系统。

#### FR-ADV-04 高层 MCP / 批量工程操作

逐步把真实高频工程任务提升为高层工具，而不是要求 AI 连续调用大量低层 CRUD：

- 批量重命名与引用更新；
- 模板化创建一组相关元素；
- 项目健康检查与可修复问题计划；
- 构建失败后的诊断收集与修复计划；
- 大规模迁移/重构的 preview → apply → rollback。

所有高层工具必须落到同一 Core command/plan 模型，并保留权限、幂等、revision 和恢复语义。

#### FR-ADV-05 模板与可复用创作单元

- 支持把一组元素、Procedure 和资产打包为本地模板；
- 导入模板前检查名称、ID、资源和 generator capability 冲突；
- 模板实例化通过 Workspace Plan 完成，可预览、可回滚；
- 默认仅本地文件，不在本阶段引入账号、云市场或远程模板商店。

#### FR-ADV-06 扩展/生成器开发者入口

在不承诺立即稳定第三方 ABI 的前提下整理：

- generator capability manifest；
- schema/version compatibility 规则；
- 最小示例与 conformance fixture；
- 插件错误隔离与诊断边界。

任何“正式第三方插件 SDK”承诺必须先经过独立 ADR，明确兼容周期和破坏性变更策略；Stage 14 可以先改善内部/实验性开发入口，不因 SDK 尚未稳定阻塞其它高级功能。

#### 11.4.2 Stage 14 Definition of Done

1. 可视化元素、generated source、manual source、AI plan 和外部 IDE 修改之间的 ownership 清晰且不会互相静默覆盖。
2. AI 高层操作全部可以 preview、审批、应用、观察结果并恢复。
3. 批量操作保持 revision / idempotency / permission profile / recovery point 约束。
4. 外部 IDE 或 AI 不得绕过 Copperbench 的核心工作区完整性检查后直接宣称操作成功。
5. 扩展能力若仍为实验性，必须显式标记兼容级别，不使用“稳定 SDK”措辞。

---

### 11.5 Continuous：版本、兼容与回归维护轨道

以下工作与 Stage 12～14 并行，不单独定义为一个必须等完才能发布的阶段：

#### NFR-MAINT-01 Minecraft / Loader 轨道维护

- 当前 8 条 Fabric/NeoForge 轨道继续作为受支持基线；
- 新 Minecraft / Loader 版本引入时必须先建立 generator capability、workspace fixture 和真实 Gradle build；
- 废弃旧轨道必须有明确生命周期说明，不因新增版本静默删除已有项目支持。

#### NFR-MAINT-02 上游 MCreator 工作区兼容

- 定期用代表性上游 workspace fixture 检查导入、保存和未知字段保留；
- 上游字段/格式变化优先通过兼容层适配，不要求用户重建项目；
- 无法安全转换时保持只读保留并给出稳定诊断，禁止猜测转换。

#### NFR-MAINT-03 工具链升级

- JDK/JCEF、Gradle、Node、Playwright 与依赖升级走独立 PR；
- 升级必须保留 Java/UI/MCP 和 generator regression；
- 工具链升级不得与大型功能改造混成无法定位回归的单一提交。

#### NFR-MAINT-04 真实用户缺陷驱动回归

- Public Beta 用户报告的 P0/P1 问题优先转化为最小稳定回归测试；
- 修复必须先复现根因，再增加测试，避免只针对日志文本或一次性环境打补丁；
- 与某个 generator/version 相关的问题进入 capability matrix 或 fixture，避免其它轨道重复发生。

#### NFR-MAINT-05 性能预算

- 500-node Procedure、2,000 elements / 10,000 references、8-generator build 继续作为回归基线；
- Stage 12～14 的新 UI/索引功能不得引入明显的 O(n²) 扫描或无界列表；
- 性能失败优先通过算法/索引修复，不通过提高超时掩盖。

---

### 11.6 明确排除项

根据当前产品决策，下列项目**不属于 Stage 12～14 的通用版本门禁或独立专项认证要求**：

- 专门寻找带可用 OpenGL/GLFW 的 clean Windows 环境重做历史图形化 `runClient` 环境认证；这不豁免 FR-PROD-02 已确认的“已安装候选交互任务必须持续到用户关闭 Minecraft”功能正确性要求；
- 真实 JCEF + UIA/屏幕阅读器无障碍认证、物理 150%/175%/200% DPI 人工审计；
- “至少 5 名外部测试者”形式化试用门禁；
- Authenticode / SmartScreen 商业签名；
- Linux / macOS 安装包与正式支持；
- Windows 10 支持；
- Bedrock Add-on first-party 支持；若未来决定进入 Bedrock，必须单独建立平台 PRD；
- 账号、云同步、遥测、远程 MCP、内置厂商聊天或在线市场，除非未来独立 ADR/PRD 明确批准。

“排除”表示不主动投入专门认证或把它们作为 Stage 12～14 功能完成条件，并不表示删除已经存在的 `runClient`、JCEF UI 或相关代码路径，更不覆盖真实用户已复现的产品缺陷。若真实用户报告这些路径中的明确产品 bug，仍按正常缺陷优先级处理；FR-PROD-01～04 是 2026-09-02 纠偏建立的产品正确性 P0，并已在 Beta 4 基线关闭；若修改对应的 JDK、Run Client、Desktop MCP 或 external-Agent 产品路径，或出现新的回归证据，就必须重新打开并复验相应 gate。

---

### 11.7 推荐开发顺序

建议未来开发按以下顺序推进，但不绑定具体日历日期：

1. **Stage 12A**：`livingentity` + `biome` + `dimension` + `gui` 专用编辑深度。
2. **Stage 12B**：复用 12A 公共组件，完成实体/worldgen/UI 周边类型。
3. **Stage 13A**：Procedure 2.0 与引用/诊断联动。
4. **Stage 13B**：Asset Center 与 Diagnostics 2.0；这两项可以与 Stage 12C 部分并行。
5. **Stage 12C / Stage 13C**：长尾类型专业化、本地历史与 Migration/Refactor 工作台收口。
6. **Stage 14**：在 Core schema / plan / reference / recovery 模型稳定后推进 IDE、AI Plan Review、高层 MCP 和模板/扩展能力。

优先级改变可以基于真实用户高频痛点、P0/P1 缺陷、Minecraft/Loader 生态版本变化或已经测得的工程阻塞；不因为“某项专项环境认证尚未做”自动把第 11.6 节排除项重新提升为 P0，但已经复现的功能正确性缺陷不适用这条排除规则。

### 11.8 后续版本验收口径

从 Stage 12 开始，功能评审应回答四个问题；进入新的发布候选时还必须确认 5.7 的安装产品 P0 继续保持 `passed`，被相关变更或回归重新打开的 gate 必须先重新验证：

1. **用户价值是否真实增加？** 本版本是否让一个明确的模组创作任务更完整、更快或更容易理解。
2. **数据是否仍然安全？** 导入、编辑、迁移、AI/批量操作是否保持 unknown-field、revision、reference 和 recovery 语义。
3. **生成是否仍然可信？** 所有受影响的 generator/type 组合是否有适用 fixture 和真实构建回归。
4. **失败是否可解释？** 新功能失败时是否能给出稳定诊断、用户可定位对象和可恢复路径。

只有同时满足这四项，本阶段功能才可以标记为完成。第 11.6 节明确排除的专项环境/平台任务不参与该功能完成判定；新的 Preview/Beta/RC 仍不得绕过任何被重新打开的 FR-PROD-01～04 回归门禁。
