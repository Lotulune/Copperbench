# Copperbench 下一步 PRD：阶段 10 可信预览发布与 Stage 9 收口

> 状态：实施中（可信 Preview 已建立，Beta 门禁未关闭）<br>
> 版本：v1.2<br>
> 更新日期：2026-08-26<br>
> 前置基线：[PRD.md](./PRD.md)、[PRD-STAGE-9.md](./PRD-STAGE-9.md)<br>
> 当前公开发布：`v0.1.0-preview.1`；目标发布：`v0.1.0-preview.2`

## 0. 阅读与执行协议

- 本文件是阶段 10 的唯一产品需求入口；`PRD.md` 和 `PRD-STAGE-9.md` 作为已实现能力与历史需求基线。
- 功能状态以源码、自动测试和固定提交产生的证据为准。README、Release Notes 或历史 evidence 不能单独证明能力已完成。
- 每个实现任务和 PR 必须引用本文件中的需求编号。
- 关闭需求必须同时满足实现、自动验证、用户文档和可追溯证据，不接受只修改状态文字。
- 本阶段冻结新模组元素类型，先建立“提交 → CI → 产物 → Release → 用户反馈”的可信闭环。

### 0.1 实施快照（2026-08-26）

机器可读事实入口为 [`product-status.json`](./product-status.json)，本节只解释执行状态。

| 项目 | 当前事实 | 结论 |
| --- | --- | --- |
| Fast CI | 受保护 PR #1～#5 均取得真实全绿记录；`main@fe4b0f30` 连续三次 Java/Javadoc、UI/Playwright、MCP 全绿 | PR 门禁已闭环；连续 main 门禁 3/3 |
| 公开 Release | `v0.1.0-preview.1` 已有 EXE/ZIP/MSIX/SBOM/哈希/元数据，源提交为 `b1225ec3` | 发布链路已证明，但公开包落后于当前 main |
| 状态源 | `product-status.json`、Schema 和 CI 漂移校验已进入 main，并已修复首次远端运行暴露的 Release Schema 漏项 | 状态源门禁通过；后续状态提升仍必须带固定提交证据 |
| 重型门禁 | Nightly `32904372190`（`main@fe4b0f30`）的全量回归、规模 smoke、完整 Playwright、MCP 与八生成器内容构建 8/8 全绿 | Nightly 门禁已建立并取得最终候选的完整远端证据 |
| Stage 9 | Function、Loot Table、Advancement 的八生成器黄金编译已关闭；专用编辑器、语言工具、500 节点、2,000/10,000 规模、服务端、真实 JCEF/a11y、干净 VM 仍未关闭 | `betaEligible=false`，不得称为 Beta |
| 仓库治理 | GitHub 已识别 GPLv3；Javadoc Pages 可访问；main 三项必需检查、受保护 PR 与 production 必需审阅者均已验证 | 仓库治理 P0 已闭环 |
| Dependency Submission | 仓库 Dependency Graph 未启用，原工作流无法成功 | 误导工作流已从 main 删除 |

## 1. 背景与问题

阶段 8 已完成八套生成器空工程构建、Windows 打包和 G7 演练；阶段 9 已实现 Procedure IR、Blockly、注册表、Function/Loot Table/Advancement CRUD、服务端、datagen 和 GameTest 的开发预览。

截至 2026-08-25，代码能力仍高于公共交付能力，主要差距是：

1. 公共 Release 已可下载，但 `v0.1.0-preview.1` 没有指向当前 `main`；Preview 2 仍需最终连续三绿、签名 Tag 和生产审批。
2. 快速 CI、分支保护、真实 PR、Nightly 和人工发布审批配置已闭环，但发布前仍需在最终候选 SHA 上保留完整记录。
3. 机器状态源已建立；PRD、状态索引和发布说明仍必须在每次状态提升时同步更新。
4. Stage 9 的大型工作区、专用编辑器、语言工具、服务端 readiness、真实 JCEF/a11y 和干净 Windows 11 门禁仍未关闭。
5. MCP 缺少大型项目分页、SDK 示例、批量原子计划和任务事件。

因此，本阶段的产品目标不是增加更多元素，而是把现有能力变成可下载、可验证、可解释、可反馈的高级 Alpha 预览版。

## 2. 目标与成功指标

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
| 外部试用 | 至少 5 名非核心开发者完成新建/迁入、创建元素、构建和恢复 |
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

删除不完整的 `v0.1.0` 草稿。`v0.1.0-preview.1` 已证明发布链路；下一次必须从最新、连续全绿且包含真实 PR 记录的 `main` 提交创建签名 Tag `v0.1.0-preview.2`。发布后由非维护者从 Release 页面完成下载、校验、安装、启动和卸载数据保留复验。

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

#### FR-AI-03 原子工作区计划

提供 `plan_workspace_changes`、`preview_workspace_plan`、`apply_workspace_plan`，支持多操作顺序、统一 revision、幂等键、语义差异、权限评估、单恢复点和全量回滚。

#### FR-AI-04 长任务事件

构建、客户端/服务端、datagen、GameTest 支持进度、日志、诊断、确认请求、取消和重连恢复；轮询接口继续兼容。

#### FR-AI-05 SDK 与评测

提供 TypeScript/Python 最小客户端和至少 10 个 AI eval，覆盖创建元素、Procedure 修改、重命名引用、构建修复、revision 冲突、越权拒绝、datagen 取消/发布和恢复。

### 5.6 外部试用与反馈

#### FR-BETA-01 诊断包

提供脱敏环境摘要、日志、任务结果和可选最小复现导出。令牌、用户名和外部路径默认移除，用户确认后才附加文件。

#### FR-BETA-02 Issue 分流

Issue 模板必须收集版本、commit、生成器、元素类型、复现步骤、预期/实际结果和诊断码，并区分安装、工作区、生成器、UI、MCP 与文档问题。

#### FR-BETA-03 外部试用任务

至少 5 名非核心开发者完成：下载安装、校验、创建或迁入工作区、创建元素、构建、制造一次失败、查看诊断、创建恢复点并恢复。结果形成匿名汇总，不以口头反馈替代。

## 6. 非目标

- 新增 Living Entity、GUI/Menu、Tool/Armor、Worldgen 等模组元素。
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

### 7.3 Public Beta 候选

- FR-STATUS、FR-S9、FR-AI、FR-BETA 全部完成。
- 至少 5 名外部试用者完成全任务。
- 所有高风险能力有固定提交证据；未验证项明确降级或移出宣称。

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

阶段 10 只有在以下条件同时满足时完成：

1. 所有 FR 有对应代码、测试、文档和固定提交证据。
2. `main` 分支保护要求 Java、Frontend、MCP conformance 三个检查。
3. 公开 Release 有完整资产且能从 GitHub 匿名下载。
4. Release 的 commit、哈希、SBOM 和 provenance 可由非维护者验证。
5. README、产品帮助、MCP Release Notes 和 GitHub Release 的能力状态一致。
6. Stage 9 剩余门禁全部关闭，或未关闭能力从 Beta 范围明确移除。
7. 外部试用达到指标且 P0/P1 问题清零。

阶段 10 完成后，阶段 11 才进入 Entity、GUI/Menu、Tool/Armor 等高价值元素扩展。
