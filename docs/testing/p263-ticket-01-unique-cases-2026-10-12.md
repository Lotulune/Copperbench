# Ticket 01：唯一业务身份与已验证导出

日期：2026-10-12（日本时间）。对应本地 26.3 计划的 ticket 01 / P263-00。

状态：实现、相关源码回归与完整真实 M1 均通过；代码与证据复查后，本地任务为 `done`，尚未提交或合入。

基线：`99f95e1d7ea0e919342414572e31f6dd6e5c4429`。工作分支：`codex/p263-unique-cases`。本记录对应未提交工作树中的代码，不提升 26.3 支持或公开安装版状态。

## 修复与边界

[GameTestReport](../../src/main/java/dev/copperbench/generator/GameTestReport.java) 使用未截断的原始 className/name 二元组识别用例，跨 suite 统一判断。缺失 className 保持空值语义；完整名称、参数变体和命名空间保留。展示截断与 suite 名称不参与身份判断。

`acceptanceExecuted` 只计实际执行的不同业务身份，跳过和框架项不计。重复报告即使独立用例已够最低数量，也返回 `GAMETEST_REPORT_INVALID`，在既有 detail 中说明 `GAMETEST_DUPLICATE_TESTS`。若同时存在真实失败，`GAMETEST_FAILED` 优先；原始行和失败信息保留用于诊断。当前无显式 attempt 结构的重复报告不作为成功重试接受，应另启验收任务。

其余原始计数保留逐行含义，strict v1.0 对象不增加字段。[VerifiedArtifactExporter](../../src/main/java/dev/copperbench/generator/VerifiedArtifactExporter.java) 原有的重新解析入口因此同时拒绝重复报告；本票没有另造一套导出计数规则。旧 persisted passed 标签、匹配的 XML 哈希和 allowHistorical 均不能绕过解析。

## 失败复现与回归

在未修改产品解析器的基线上运行三个反例，结果为 **3 项执行、3 项失败**：同身份重复六次仍 passed；跨 suite 重复仍 passed；完整保存并恢复的重复 passed 记录仍可导出。这些反例由本票重建，不冒充附件提及但本轮未读取的原始复测文件。

修复后，六个相关 Java 测试类共 **48 项通过**：

| 测试类 | 数量 | 验证范围 |
| --- | --- | --- |
| GameTestIdentityTest | 11 | 重复、跨 suite、合法不同类/参数、长身份、二元组边界、失败/跳过尝试及 v1 字段 |
| GameTestReportTest | 5 | 原有 XML、时间、框架项与失败保护 |
| GameTestCoverageTest | 8 | 0/1、5/6、足量/超量、跳过、框架、失败优先级 |
| VerifiedArtifactExporterTest | 6 | 恢复记录、当前/历史导出、篡改与记录一致性 |
| ExportEvidenceMetadataTest | 5 | 便携元数据、旧记录及摘要重算 |
| GameTestWorkflowTest | 13 | 受管任务持久化拒绝、来源变化、产物变化和原八轨道宿主准备 |

新增回归见 [GameTestIdentityTest](../../src/test/java/dev/copperbench/generator/GameTestIdentityTest.java)、[持久化导出反例](../../src/test/java/dev/copperbench/generator/VerifiedArtifactExporterTest.java)、[受管任务反例](../../src/test/java/dev/copperbench/generator/GameTestWorkflowTest.java)。Python SDK **93 项通过**；Javadoc 通过。Gradle 的正常资源依赖同时完成产品 UI 构建及中英文文本检查，没有运行玩家 UI 验收。

保存的本地证据分别位于 `output/p263-01/red/`、`output/p263-01/green/`、`output/p263-01/checks/`。首次直接 Gradle 启动受到 Windows 回环连接问题影响，尚未执行测试；实际红/绿回归使用项目既有的独立进程入口完成，原失败日志保留。

## 实测发现的必要前置修复

首次 M1 在创建测试工程时失败：旧本地历史把 `.copperbench/workspace.write.lock` 纳入索引，Windows 正在持有的锁使 JGit 无法读取，初始恢复点未形成。这发生在模组构建与本票新增拒绝步骤之前。

复用维护分支提交 `48cd994ac22b6a2bf6f0b30b13457250b0c59745` 的 [GitHistoryBackend 修复](../../src/main/java/net/mcreator/workspace/localhistory/GitHistoryBackend.java)、[历史回归](../../src/test/java/dev/copperbench/history/LegacyHistoryRuntimeFilesTest.java)及 Windows CI 测试选择，并补齐测试夹具创建 info 目录的步骤。

前置补丁把运行文件从旧历史索引排除，保留磁盘字节和原有自定义忽略项。新建/已有历史两种回归，以及 WorkspaceFilesTest 的两个文件保护用例共 **4 项通过**；真实租约保持锁定，普通 authored 文件继续进入恢复点。导入测试的第一次旧历史夹具因缺少 info 目录失败，修正夹具后四项通过；失败证据保留在 `output/p263-01/prerequisite/`，最终结果在其 `fixed/` 子目录。

因此本票的最终 Windows M1 结论依赖该前置补丁。不能把结果描述为只修改解析器的原始主线已通过 Windows 全流程。未提交或推送维护分支，也未改动其他工作树的产品代码。

## 真实 M1

运行：`delivery-16286686970414939950`。输入为既有 Fabric 1.21.1 M1 固定样例，minimumTests=5；使用已有本地缓存、新建工作区和独立 packaged-JAR 宿主。真实宿主日志确认 Java 21、Fabric Loader 0.19.3、Minecraft 1.21.1 和被测模组已加载。

最终 `result.json` 为 `passed`，Python M1 流程耗时 **969.693 秒**；外层 Gradle 在 **16 分 36 秒**后以 `BUILD SUCCESSFUL`、退出码 `0` 结束。全流程使用 **3 个真实产品进程、20 个受管任务步骤**。

已通过：只读发现、创建、初始构建、可定位编译故障、显式修复、五项独立业务验收、选定 JAR 导出、旧进程正常退出及新进程重连/重开导出、5/6 不足与导出拒绝、JAR/XML 篡改拒绝、零项/全跳过拒绝、重复记录的当前与历史导出拒绝、外部输入变化拒绝、合法历史导出，以及最终当前输入重新验收、导出和第三个进程中的重开导出。

九个拒绝门禁全部通过：`insufficient-export`、`jar-tamper`、`report-tamper`、`all-skipped-export`、`zero-tests-export`、`duplicate-current-export`、`duplicate-historical-export`、`source-changed`、`historical-is-not-current`。

首次重连证据：旧 PID `31504`，退出码 `0`，新 PID `8744`，恢复 revision `5`；第三个产品进程 PID `37576` 恢复 revision `7`，重开导出通过。最终原始 XML 为五行、五个不同身份，classname 为 `fabric-gametest-api-v1:empty`，name 分别为组件序列化、正确配方、错误原料、堆叠上限及修复代码的完整宿主方法名。

新增 [M1 负向步骤](../../sdk/python/agent_readiness_delivery.py) 使用真实产品导出 API，但重复 XML/恢复记录属于明确标注的 `negative_recovered_record_injection`，不是六个真实业务测试。它保留原始正常记录，并分别在当前输入和修改源码后的 allowHistorical 路径检查拒绝。

最终验收任务为 `468c544b-23fa-4859-901c-b38e37399055`，当前输入导出任务为 `dba15e54-2044-41a0-a7ef-f80d0564830a`，第三个进程的重开导出任务为 `b5d157c7-e4cb-4b8e-ab04-db8d6d4b46d1`。便携回执状态为 `passed_current_input`，`acceptanceExecuted=5`、`minimumTests=5`。

| 最终证据 | SHA-256 |
| --- | --- |
| 被测及导出的模组 JAR | `cbeb70e122aaa6d1a833b1e9776056c0f7b63abc7d16c5ac26fefe1eed08dd3e` |
| 原始及导出的 GameTest XML | `ffe0d9e3a6d21f3025b7394c97edea9aa7b5f06b52582a77b151665cc2d39c00` |
| 本次验收输入 | `d18f57937673a562dafacb2aed4982b8d586ddfb5fee97f7168327f7110488a4` |
| 实际磁盘 verification.json | `c8f28635589901ecb074453f9bf999165f71e76d755329a9f3ace82f4cd51329` |

已从磁盘核对被测文件、选定导出文件及回执摘要；M1 期间的产品变更与新增测试源也和启动时收据一致。本轮验证范围为 **Fabric 1.21.1 的既有 M1**，不代表 26.3、公开安装包或玩家键鼠操作已验收。

## 证据位置与交付状态

稳定归档目录：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-01`。

- `validation/`：原实现失败、新实现 48 项回归、93 项 SDK、前置修复的 4 项回归、Javadoc、两轮 M1 日志及机器摘要。失败尝试保留。
- `m1/`：完整真实 M1 原始报告、步骤回执和产物证据。
- `verified-export/`：选定最终导出的原始 JAR、XML 与 verification.json，未重新序列化回执。
- `new-test-sources/`：未跟踪的两个新增测试源；结合 `m1/source.patch` 的源码差异与 `validation/checks/source-receipt.json` 的摘要可复查本次变更。

实现工作树为 `C:/Users/Administrator/.codex/worktrees/p263-unique-cases/Minecraft_ModCreator`。52 项相关 Java 测试（48 项本票回归 + 4 项必要前置回归）、93 项 SDK、Javadoc 与完整 M1 通过。本票的验收项及代码自查已完成，后续 05 可在同一实施工作树中消费该实现；其他 tickets 的进度独立跟踪，不能沿用本票的 1.21.1 证据关闭 26.3 门禁。
