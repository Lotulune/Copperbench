# Copperbench 下一步 PRD：阶段 10 Public Beta 收口与阶段 11 全量 Mod Element 支持

> 状态：Stage 11 功能、固定提交 Nightly、签名 Preview 7 与候选所需 Windows 安装/升级/卸载 RC 已完成；进入 `v0.1.0-beta.3` exact-binary promotion release-control；图形化 clean-Windows `runClient` 仍不在本 Beta 宣称范围<br>
> 版本：v1.4<br>
> 更新日期：2026-09-01<br>
> 前置基线：[PRD.md](./PRD.md)、[PRD-STAGE-9.md](./PRD-STAGE-9.md)<br>
> 当前公开 Beta：`v0.1.0-beta.2`（`main@8b063283`，release run `33334394466`）；下一 Beta 目标为 `v0.1.0-beta.3`，冻结候选为 `v0.1.0-preview.7`（`f4b58062`，release run `33506364499`）

## 2026-08-31 Public Beta publication and metadata correction

`v0.1.0-beta.1` 已从 release-control `main@f12823ab` 通过 release run `33332537616` 成功公开。`RELEASE-METADATA.json` 明确记录 `binarySource.mode=promoted-tested-candidate`，EXE/ZIP/MSIX/SBOM 与签名 `v0.1.0-preview.6` 的大小和 SHA-256 全部一致，因此 Beta 1 没有重新构建候选二进制。详见 [Beta 1 publication verification](./docs/testing/beta1-publication-2026-08-31.md)。

发布后验收发现 Beta 1 随包的权威 `product-status.json` 仍声明 `product.channel=preview`；该元数据问题已由不可变的新标签 `v0.1.0-beta.2` 修正。Beta 2 release run `33334394466` 成功，随包 `product.channel=beta`、`delivery.betaRelease.tag=v0.1.0-beta.2`，并继续原样晋升 Preview 6 的四个 canonical 资产。真实 JCEF 无障碍审计、最终 clean-Windows RC replay 和五人外部试用继续排除在当前 Beta scope 之外，不宣称其已通过。详见 [Beta 2 publication verification](./docs/testing/beta2-publication-2026-08-31.md)。

## 0. 阅读与执行协议

- 本文件是阶段 10 的唯一产品需求入口；`PRD.md` 和 `PRD-STAGE-9.md` 作为已实现能力与历史需求基线。
- 功能状态以源码、自动测试和固定提交产生的证据为准。README、Release Notes 或历史 evidence 不能单独证明能力已完成。
- 每个实现任务和 PR 必须引用本文件中的需求编号。
- 关闭需求必须同时满足实现、自动验证、用户文档和可追溯证据，不接受只修改状态文字。
- 本阶段冻结新模组元素类型，已建立“提交 → CI → 产物 → Release”可信闭环；无障碍、最终 RC 和外部试用不纳入当前 Beta 宣称，但候选包和签名来源仍是必需项。

### 0.1 实施快照（2026-08-31）

机器可读事实入口为 [`product-status.json`](./product-status.json)，本节只解释执行状态。

| 项目 | 当前事实 | 结论 |
| --- | --- | --- |
| Fast CI | main 分支保护持续要求 Java/Javadoc、UI/Playwright 与 MCP；Beta 2 release-control `main@8b063283` 的 merged-main run `33334020837` 全绿 | PR 门禁已闭环；Beta 2 已从 CI-green main 发布 |
| 公开 Release | `v0.1.0-beta.2` 已从 `main@8b063283` 公开，release run `33334394466` 成功；10 个资产全部 uploaded 且有 digest | 随包 `product.channel=beta`；EXE/ZIP/MSIX/SBOM 与 Preview 6/Beta 1 完全同 size/SHA-256，metadata correction 已完成 |
| 状态源 | `product-status.json`、Schema 和 CI 漂移校验已进入 main，并已修复首次远端运行暴露的 Release Schema 漏项 | 状态源门禁通过；后续状态提升仍必须带固定提交证据 |
| 重型门禁 | Nightly `33253594479`（`main@92d1a8d0`）的全量 Java/Javadoc/scale 回归、完整 Playwright、MCP、诊断包 native JCEF 验收与八生成器内容构建 8/8 全绿；后续发布合同变更也已通过主线 CI | FR-AI-02～05、FR-BETA-01～02 保持闭环；无障碍、最终 RC 和外部试用已按 Beta 合同移出当前范围 |
| Stage 9 | 八生成器黄金编译、三个专用编辑器、语言工具、安装升级和离线工作区路径已有自动化/客机证据；Windows WR + Chromium 完整 AXMode 与 `DPR=1.25` 路径已通过 | 物理高 DPI、屏幕阅读器、最终 RC 矩阵和外部试用不纳入当前版本宣称，未来重新纳入时需新候选和新证据 |
| AI Developer Kit | PR #14～#17 的 Cursor、Workspace Plan、Task Events/SDK 与 Windows-native JCEF reconnect 均已合入；当前 `main@f677e481` 的 merged-main CI `33329612708` 与固定产品基线 Nightly `33253594479` 全绿 | FR-AI-02～05 均为 `passed`；MCP reconnect 继续冻结为 `get_task(afterLogSequence)`，不引入未版本化的自定义 push 方言 |
| 仓库治理 | GitHub 已识别 GPLv3；Javadoc Pages 可访问；main 三项必需检查、受保护 PR 与 production 必需审阅者均已验证 | 仓库治理 P0 已闭环 |
| Dependency Submission | 仓库 Dependency Graph 未启用，原工作流无法成功 | 误导工作流已从 main 删除 |

## 1. 背景与问题

阶段 8 已完成八套生成器空工程构建、Windows 打包和 G7 演练；阶段 9 已实现 Procedure IR、Blockly、注册表、Function/Loot Table/Advancement CRUD、服务端、datagen 和 GameTest 的开发预览。

截至 2026-08-25，代码能力仍高于公共交付能力，主要差距是：

1. `v0.1.0-preview.2` 已证明完整发布链路，但发布后的状态同步提交使公开 Tag 不再等于最新 `main`；Preview 3 用一次冻结提交恢复严格一致性。
2. 快速 CI、分支保护、真实 PR、Nightly 和人工发布审批配置已闭环；下一签名 Preview 候选必须在最终合并 SHA 上保留检查、签名 Tag 和生产审批记录。
3. 机器状态源已建立；PRD、状态索引和发布说明仍必须在每次状态提升时同步更新。
4. Stage 9 的大型工作区、专用编辑器、语言工具和服务端 readiness 已有固定证据；真实 JCEF/a11y、最终 clean-Windows RC 与外部试用已明确移出当前 Beta 宣称范围，保留为未来重新纳入时的证据项。
5. MCP 的大型列表 Cursor、Workspace Plan、Task Events/reconnect 与 SDK/evals 均已闭环；当前不再以新增 AI 能力阻断 Beta 候选。

因此，阶段 10 的产品目标不是增加更多元素，而是把当时已有能力变成可下载、可验证、可解释、可反馈的 Public Beta 基线；阶段 10 已完成，后续产品范围由 7.4 的 Stage 11 全量元素计划接管。

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
| 外部试用 | 当前 Beta scope 为 N/A；未来重新纳入时至少 5 名非核心开发者完成新建/迁入、创建元素、构建和恢复 |
| P0 发布缺陷 | 发布后 7 天内 0 个“无法下载/资产缺失/版本不明”缺陷 |

## 3. 用户与核心场景

### 3.1 普通创作者

作为 Windows 11 用户，我能从 GitHub 下载与说明一致的包，验证哈希，创建 Fabric/NeoForge 工作区，完成基础创作和构建；遇到预览能力时能看到明确状态和回退入口。

### 3.2 模组开发者

作为已有 MCreator 工作区的开发者，我能在复制迁入后识别哪些元素正式支持、开发预览或只读，并在 Stage 9 数据元素与任务失败时获得可定位诊断。

### 3.3 AI 客户端开发者

作为本机 AI 集成开发者，我能按版本化 Schema 遍历大型工作区，预览多步修改、处理 revision 冲突、订阅长任务结果，并使用官方示例复现完整工作流。

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

当前公开版本仍为 `v0.1.0-beta.2`，其状态源 channel 修正与 exact-binary promotion 均已验证。Stage 11 后续产品变更已经建立新的签名候选 `v0.1.0-preview.7`：固定 `main@f4b58062` 的 merged-main CI 与 Nightly 已通过，Preview release/provenance 已通过，公开 EXE 的候选所需 Windows install/upgrade/uninstall、offline workspace launch 与 retention replay 也已得到 `gatePromotionReady=true`。因此下一步仅允许 evidence/status/docs release-control 变更并将 Preview 7 原字节提升为 `v0.1.0-beta.3`；任何产品、构建、运行时或工具实现变更都必须重新建立 Preview 候选。当前 Hyper-V 客机没有可用 OpenGL profile，图形化 clean-Windows `runClient` replay 无法形成通过证据，继续与真实 JCEF 无障碍和外部试用一起从本 Beta 宣称范围移除。

### 7.4 下一功能版本（Stage 11）：全量 Mod Element 一等支持

**产品决策：下一功能版本必须把当前 30 种 `readOnly` / `legacyOnly` Java Mod Element 全部升级为 Copperbench first-party `supported`。** 开发过程允许分批落地，但下一功能版本不得在仍有这些类型停留于 `readOnly`、`legacyOnly`、`unsupportedInNewUi` 或“只能导入不能修改”的情况下宣称完成。

实施状态（2026-09-01）：**Stage 11 功能与候选验收已完成，进入 Beta 3 release-control。** 30 种目标 Java Mod Element 已全部进入 first-party `supported` 路径；全类型持久化/未知字段 round-trip、导入、迁移、UI/status contract 和核心 Java 回归通过。`NewWorkspaceGeneratorGoldenBuildTest` 的 8-generator Stage 11 全量工作区为 8/8 PASS，所有轨道均完成真实 Gradle build 与 JAR 输出，JAR/mixin 一致性门禁也会验证 mixin JSON 中声明的每个 mixin 类真实存在于产物。固定提交 `f4b58062` 的 Nightly `33503172036` 已完成产品回归与 8/8 generator golden；签名 `v0.1.0-preview.7` 的 Windows release run `33506364499` 已发布；公开 EXE SHA-256 `716a93ea45278d71b2ef80eeee3bd4d0ec315891c349478cff50ed093db90d93` 已完成 GUI 新建工作区、真实 workspace Gradle build/JAR，以及 Stage 11 候选要求的 upgrade/offline/uninstall retention RC replay，机器结果为 `gatePromotionReady=true`。图形化 `runClient` 在当前 Hyper-V 客机上因无可用 OpenGL profile 进入 NeoForge 初始化错误窗口，原 lifecycle harness 将该窗口误判为 stable，因此该图形化 replay 不宣称通过并继续移出 Beta 3 scope。候选证据见 [`docs/testing/beta-candidate-preview7-2026-09-01.md`](./docs/testing/beta-candidate-preview7-2026-09-01.md)。下一步只允许 exact-binary promotion 所允许的 evidence/status/docs 变化，把 Preview 7 canonical bytes 原样提升为 Beta 3。

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

每个 Wave 合入后都应保持 `main` 可构建、可运行、可回退，并持续更新 `product-status.json`；只有 Wave 1～3 全部完成后，才允许建立下一功能版本的 Release candidate。

## 8. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| Windows 包体积过大导致下载失败 | 同时提供 EXE/ZIP/MSIX、哈希，记录大小；后续评估 Standard/Offline 分包 |
| 未签名导致用户不信任 | 明示签名状态，提供 SBOM、provenance、commit、哈希和可复现说明 |
| 全量测试过慢 | PR 快速门禁、nightly 重矩阵、发布前全门禁分层 |
| 状态源与源码再次漂移 | CI 做结构化投影比对，状态提升必须携带测试证据 |
| MCP 协议演进破坏客户端 | 版本协商、弃用周期、兼容测试和 SDK fixture |
| 大型工作区性能目标不现实 | 先固定硬件/数据集测基线，再以 P95 回归阈值管理 |

## 9. 依赖与负责人边界

- GitHub 仓库管理员：配置 `main` 分支保护、Actions 权限和可选 `production` Environment 审阅者。
- 发布维护者：创建并推送干净 Tag，审核 Release Notes，执行外部安装复验。
- Core：状态源、分页、计划事务、任务事件和性能基线。
- UI：能力状态呈现、JCEF/可访问性、诊断导出。
- Generator：八套黄金编译与服务端 readiness。
- Docs/SDK：AI Developer Kit、快速开始、故障排查和示例。

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

阶段 10 已完成。阶段 11 的下一功能版本不再只做少数高价值元素试点，而是以“当前 30 种 read-only / legacy-only Java Mod Element 全部转为 first-party supported”为完成条件；具体门禁见 7.4 与 10.1。
