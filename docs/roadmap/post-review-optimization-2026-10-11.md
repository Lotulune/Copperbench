# Copperbench 复评后优化与剩余工作

当前进展：PR #101 已合并，后续 REM-02/03/06 的实现与实际验证范围见[本批证据索引](../testing/remaining-delivery-2026-10-11.md)。以下保留原规划的时间范围；发布前 REM-01 的[候选验收工作表](../testing/next-candidate-acceptance.md)单独维护。

版本：1.0；日期：2026-10-11（日本时间）。
基线：`da3157b580f560cfd0d184b29f23dff73eeec657`，即已合并 PR #100 的主分支。
本记录随首批优化代码提交评审；后续与 SDK 契约修复和旧 PR 的有效改进整合，见[整合验证记录](../testing/pr-consolidation-2026-10-11.md)。具体提交的 CI 与合并状态以关联 PR 为准，不更新历史安装版或玩家验收结论。

## 1. 本轮决策

优先降低现有 agent 工作流的重复读取、错误理解和交付复核成本，不再开启大规模架构重写。上一阶段已完成的字段发现、生成预检、源码保护、独立回归和真实交付能力继续作为基础，见[当前剩余工作入口](../remaining-work.md)及[前一阶段 PRD](agent-readiness-prd-2026-10-09.md)。

用户已经终止自主 agent 研究和陌生用户研究。本阶段不重新启动、不招募，也不将补满原计划次数设为验收条件。既有十四次启动的真实结果仍保留，不能换算成原二十次研究目标已达成。

## 2. 本次已实现，仍需按提交验收的优化

### OPT-01：支持新接口的 Native 会话只读取目标类型

`Workspace.discover_field_contract()` 使用 Native ready 消息已有的操作目录判断是否公开 `get_mod_element_field_contract`。公开时直接执行一次该只读查询，不再先取得含所有契约的完整环境。

未公开该操作或没有目录的旧会话保留原环境协商路径。原 `field_contract()` 和 `available_field_contracts()` 的格式与行为不变；查询被拒绝或超时不再回退重试。每次读取新契约，不缓存 generator、revision、字段内容、源码指纹或授权结论。

新增十四项 SDK 测试覆盖单次查询、三态发现、实时数据、旧 Core 兼容、畸形返回、错误传播、会话隔离和后续写入修订值。

保留的 `e612d007` 真实响应经新旧 SDK 传输替身回放，紧凑 UTF-8 JSON 的 response data 计量如下：

| 请求 | 修改前 | 修改后 |
| --- | --- | --- |
| item | 2 次查询；122,878 字节 | 1 次查询；38,673 字节 |
| recipe | 2 次查询；112,250 字节 | 1 次查询；28,045 字节 |

每次少传同一份 84,205 字节的环境数据。回放证明请求路径与响应数据量变化，不是新 Core 实测、端到端速度、token 或费用测量。样本来自 [M1 run 38094029219](https://github.com/Lotulune/Copperbench/actions/runs/38094029219)；原 ZIP SHA-256 为 `045adeedb2f84d1ba43bee0ee914ccb171d185a7564c56272172af0ff5af21cf`。

### OPT-02：最低测试数量不足给出准确说明

零项、全跳过或只有框架测试仍拒绝。实际执行五项但要求六项时，在既有 `detail` 字段中返回：

```text
GAMETEST_INSUFFICIENT_TESTS: At least 6 business tests must execute; observed 5. Skipped and framework cases do not count toward this minimum.
```

顶层 `reasonCode=GAMETEST_NO_TESTS` 保持兼容。既有 v1.0 `gameTestVerification` 禁止未知属性，不能直接增加结构化 `coverage` 字段。调用者仍以顶层状态和已有 `minimumTests`、`acceptanceExecuted` 处理结果；不应把英语文本作为新的机器协议。正式的机器细分原因与中英文呈现列入 REM-02。

实际测试失败、报告缺失、损坏、陈旧的原因优先级不变；最低数量和框架/跳过排除逻辑不变。新增八项 Java 测试检查这些边界。

### OPT-03：便携回执保留已观测工具链与验收条件

已验证导出继续先复核原始 XML、实际用例、数量、最低要求、进程结果、输入和文件摘要，再复制原先被验收的 JAR。公开的 `task.verifiedExport` 顶层 v1.0 结构不变。

现有 `environment` 中增量保留已观测的 `javaRelease`、`fabricApiVersion`、`pluginId`、`pluginVersion`，同时保留旧字段。只复制白名单中的标量；不复制其他环境字段、凭据或本机路径，不用导出进程的 JBR 版本推算工作区 Java。

磁盘上的 `verification.json` 作为便携文件额外提供：

- `portableEvidenceVersion="1"`；
- `environmentSource="acceptance_record"`；
- 原验收的 `mode`、`minimumTests`、`startedAt`、`processExitCode`；
- 从原始报告重新计算的数量摘要 `coverage`。

只有原始验收通过才生成可信便携回执，不能靠恢复记录中的伪造数量摘要提升结果。新增五项真实临时文件 Java 测试覆盖工具链保留、白名单、旧记录、摘要重算及不足数量拒绝。

**兼容提醒：**磁盘 `verification.json` 比 API 回执丰富，不能再以两者整对象相等作为必要条件。应比较 task、输入、JAR、XML 的共同身份字段，并按 `portableEvidenceVersion` 处理扩展。严格限制便携 JSON 字段的第三方消费者需更新；旧 Core 回执仍应可读。新的便携字段不等于完整候选签名或来源证明。

## 3. 验证状态与禁止扩大的结论

提交前已完成：基线源文件 Git blob SHA 校对；十四项新增 SDK 测试通过（ResourceWarning 按错误处理）；Python 语法编译通过；保留响应的新旧路径回放通过。

负向对照在旧 SDK 上执行同一十四个方法，产生十九个失败记录（含子测试），修改后全部通过。不是十九个独立产品缺陷。

初始提交 `f1149915` 的执行环境没有完整产品 JDK 25，因此当时未运行 Java。后续整合已在 Windows JDK 25 上通过 27 项相关 Java 测试、Javadoc、生产 UI 构建及完整 93 项 Python SDK 测试，详见[整合记录](../testing/pr-consolidation-2026-10-11.md)。完整 CI、Linux 候选与真实 M1 交付仍以整合提交自身的运行记录为准。

本次没有进行新的安装版、玩家输入/画面、完整世界保存重进或新的自主 agent 研究。此前 [298846dc 安装版冷暖结果](../testing/installed-cache-provenance-2026-10-11.md)及更早的玩家证据继续绑定原候选，不能复制为此补丁的已通过记录。

## 4. 剩余工作总表

以下优先级针对下一次开发和候选交付，不修改已发布 0.1.4 的历史结论。责任栏为建议职能，不代表已指派人员或承诺日期。

| ID | 优先级 / 批次 | 工作与交付物 | 建议责任 | 依赖 |
| --- | --- | --- | --- | --- |
| REM-01 | P1 / 发布前 | 冻结最终候选与针对受影响路径的安装版复核 | 发布、QA | 本补丁评审及提交级 CI |
| REM-02 | P2 / 下一小批 | 数量不足的中英文任务展示与版本化机器原因 | Core、SDK、UI | OPT-02；契约兼容设计 |
| REM-03 | P2 / 下一小批 | 小型便携证据包和离线核验器 | Core、工具、QA | OPT-03；来源与脱敏规范 |
| REM-04 | P2 / 按需 | 精简环境/能力目录，补跨 SDK 数据量基准 | Core、SDK | OPT-01 的真实路径回归 |
| REM-05 | P2 / 按使用频率 | 将完整预创建发现逐类扩展到高价值元素 | Core、生成器 | 共用验证器与实际任务样本 |
| REM-06 | P2 / 持续 | 当前状态入口、成功结果证据与失败分类整理 | 维护、CI | 保持既有门禁和历史记录 |

## 5. REM-01：收敛到能发布的同一份候选

目的：消除“最新源码通过”和“实际发布包通过”之间的身份空档。评审通过后冻结 source commit/tree、SDK 摘要、应用 JAR 摘要及安装包摘要，不以本机随手找到的已安装文件反向充当预期身份。

至少验证最终 Windows 与 Ubuntu 候选：真实启动器/doctor 身份、item/recipe 新发现路径、导入工程 internalName 与 registryName 不同时的合法 `CUSTOM:` 引用、正常构建、五项对六项的拒绝、当前输入导出与重开、外部源码修改和篡改证据的拒绝。

验收要求：运行记录包含候选摘要、工具链、缓存/网络条件、任务终态、原始 XML、JAR 及导出记录；验证中的应用身份与冻结值一致；未运行的轨道、玩家行为或默认网络条件单独标注。失败不能靠换包后沿用旧运行 ID 消除。

不要求重做已取消研究。未改变且无需补跑的玩家层级保留原身份/范围说明；受影响层级需重验。禁止将临时 DNS 条件下的通过描述为默认网络保证。

## 6. REM-02：让任务结果直接回答“缺什么”

UI 应分开显示：任务仍在运行、报告不可用、业务测试失败、没有执行业务测试、数量不足、已通过当前输入验收、仅历史输入通过。

数量不足示例：中文“要求至少 6 项业务测试，实际执行 5 项，还差 1 项”；英文保持相同参数。数据来自原始任务，不重新运行生成或测试来刷新文字。

机器细分原因需先设计版本化 Schema/能力协商；保持旧 code 或提供明确迁移期，不能在 strict v1.0 对象中直接塞入新属性。所有 Native/MCP/TypeScript 和 UI 消费者使用同一语义来源。

验收矩阵：0/1、5/6、6/6、7/6、全跳过、仅框架项、真实失败、陈旧/损坏 XML、运行中和历史导出。切换语言不改变任务状态、revision 或文件，不重发写操作。已有最低数量不降低，失败不能被“数量已足够”覆盖。

## 7. REM-03：证据包应可带走、可独立复核

交付一个明确版本的小型目录/ZIP，包括相对路径的 manifest、被测 mod JAR、原始 XML、便携回执及必要的应用/源码身份记录。默认不包含 Gradle/Minecraft 缓存、完整游戏目录、令牌或未经审阅的绝对路径日志。

独立核验器从实际文件重算摘要、大小和唯一业务用例，核对 mode、最低数量、失败/跳过、输入和 artifact 身份。历史导出必须仍明确标为历史；无法观测的应用/工具链字段不可猜测。文件完整性校验不等于外部签名或真实来源认证。

安全与失败验收：拒绝绝对路径、`..`、重复归一化成员、符号链接和超限解压；截断/损坏包返回非零状态；未知版本停止；在不支持新扩展的旧便携记录上给出明确兼容状态。构建临时目录后原子发布，失败不留下可误认成完整交付的成功标记。

完成判据：在另一干净目录中，不运行 Minecraft、Gradle 或访问网络即可复核一份有效包；逐个篡改 JAR/XML/数量/manifest 的负向用例全部被拒绝。外发证据范围需经过脱敏检查，不把安装版应用摘要混同为 mod JAR 摘要。

## 8. REM-04 至 REM-06：小步优化，避免再次扩大范围

**REM-04 精简目录。** 本批只解决 Native `discover_field_contract` 的额外环境读取；`available_field_contracts` 与旧完整环境仍可能很大。可增量提供不含字段体的能力目录/环境摘要，建议先把固定测试样本的摘要预算设为不超过 16 KiB，再依据实际字段调整。旧完整接口不变；字段、生成器、revision、权限和源码事实不能隐式长期缓存。分别计量 Core 序列化字节、SDK 请求次数与真实任务耗时，不能将字节节省直接换算成模型 token/费用。其他 SDK 按现有路径测量后再改，不重复实现已存在的直接查询。

**REM-05 逐类补齐。** 当前完整统一发现重点是 item/recipe，其他元素已有各自结构化/专门契约，不等于不支持。按真实失败频率选下一种元素，要求默认值、字段约束、引用、条件和最小示例来自实际验证器。示例必须被实际适配器消费，保留生成、重开和玩法验收的独立层级。单一 Fabric 样本通过不能扩大成八轨道玩法通过。

**REM-06 维护与证据。** 将当前入口中“draft PR #100”“local development branch”等合并前状态更新为真实状态，同时保留历史失败叙述的时间范围。下一步证据索引按 commit/tree → CI run → candidate → installed/player evidence 组织；成功的相关 Java XML 也应可取，不只在失败时上传。先保留现有必需检查名称与自动 M1 路由，不以增加重复 CI 次数作为质量目标。

## 9. 合并、回滚与完成标准

建议顺序：本批评审和提交级 CI → REM-02/03 中必要的消费兼容检查 → 冻结候选执行 REM-01 → 发布决策；REM-04/05 按需求分批，不作为无理由延迟维护发布的门槛。

本补丁不迁移工作区、不认领所有权、不修改授权、不接受 EULA，也不触碰系统网络配置。回滚以 revert 本批提交为主；旧 SDK 路径和旧宽泛错误码仍能工作，新便携 JSON 的扩展由消费者版本识别。

本批完成需：新增十四项 SDK 与十三项 Java 测试通过；现有相关保护回归不退步；严格 v1.0 响应无新顶层字段；同提交真实 M1 验收、负向拒绝、真重连和哈希绑定导出有新的证据。后续安装版范围单独关闭，不能由源代码 CI 自动完成。

## 10. 源码与证据入口

- [Native SDK](../../sdk/python/copperbench_native.py)；[新增单查询回归](../../sdk/python/test_discovery_efficiency.py)。
- [GameTest 报告](../../src/main/java/dev/copperbench/generator/GameTestReport.java)；[数量诊断回归](../../src/test/java/dev/copperbench/generator/GameTestCoverageTest.java)。
- [已验证导出](../../src/main/java/dev/copperbench/generator/VerifiedArtifactExporter.java)；[便携元数据回归](../../src/test/java/dev/copperbench/generator/ExportEvidenceMetadataTest.java)。
- [严格 v1.0 公共 Schema](../../ui-core/schemas/v1.0/common.schema.json)。
- [M1 工作流](../../.github/workflows/agent-readiness-m1.yml)；[已有 M1 验证记录](../testing/agent-readiness-m1.md)。
- [已关闭研究的真实范围](../testing/agent-readiness-m3-study-2026-10-10.md)；[安装版候选范围](../testing/installed-cache-provenance-2026-10-11.md)。
