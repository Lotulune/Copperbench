# P263-09 — Fabric 26.3 原生修复、六项验收与同 JAR 导出

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 固定业务范围

[原生 Camp Kit 样例](../../examples/camp-kit-native/README.md)在独立 Fabric 26.3 工程中注册 16 堆叠上限的 item、带轴状态的 block、一个有界合成配方和手写恢复代码。它不依赖尚未开放的结构化元素入口。

固定清单为 `camp-kit-native-v1`，摘要 `8c0ebf3b57be5fa982ef8cf8ea6675966996115754601cd82b3aeb8ac707bfe9`，最低业务数量为 6。T01/T02/T03/T04/T11/T12 分别验证注册与堆叠、组件序列化、正确原料与结果数量、错误原料无结果、真实 BlockItem 放置与状态、修复后的手写方法。预期独立写在宿主测试中，不读取被测实现常量来推导答案。

Fabric 26.3 注解默认组合宿主 ID、类名和方法名作为身份，因此样例使用原生 TEST_FUNCTION 注册与六个 `test_instance` 数据文件，直接产生冻结的 `copperbench_camp_kit:tXX` 身份。没有重命名 XML 或改动清单来适配报告。

域层真实探针为 `output/p263-09/backend-becf6a6d-f43c-4f51-843c-5ef8b2c1ed6c`。宿主编译目录只包含测试源码；被测源码编译成 JAR 后复制到独立宿主 `run/mods`。首次真实构建及六项业务验收通过。

## Native 闭环

[Java 启动入口](../../src/test/java/dev/copperbench/headless/Native263AcceptanceMain.java)与 [Python Native 编排](../../sdk/python/native_263_acceptance.py)运行目录为 `output/p263-09/native/delivery-3766931738999667473`。

主流程耗时 439.029 秒，包含 18 个受管任务步骤。产品进程 PID 40112、27920、11428 各自通过进程句柄确认 exit 0，后两次使用新进程和连接重开同一工作区 `591a9e6d-6d8a-473b-a1c8-c082e1432c87`。

真实步骤包括：初始构建；经 SDK 将手写方法改成未定义符号并获得带文件位置的 `JAVA_COMPILE_ERROR`；经受保护写入修复；从最终 JAR 执行六项业务验收；选择该任务导出；关闭产品、重开并读取持久化任务后再次导出。25 份预览扩展响应通过 schema。

最终当前输入验收任务 `324932fb-5b49-4b62-825d-433a677e8da8` 的原始 XML 有 7 项通过，其中 `minecraft:always_pass` 作为框架项排除，**业务执行数为 6**。导出任务 `1f8b2977-e944-4eef-aa62-4c518be25d14` 与新进程导出 `8ddb2299-6cb0-4c72-8179-8980646b5a37` 的被测 JAR 摘要相同：

| 身份 | SHA-256 |
| --- | --- |
| 最终受测及导出 JAR | `a6baceaaf937d2b05964323c80cdc550117d079e5558fd1207345b07b9b75081` |
| 原始 XML | `c48c412e6f041c5ce468b7d0ece116db676897abf45ef2eed1e1a84485d6fb98` |
| 验收输入 | `142a35d597f860f8d9e27b33e68b6ec5028807a4f12153ca1ef73ec7146f11d9` |

`final-verification.json` 是实际导出器写出的便携文件，包含固定清单、覆盖结果、任务/工作区/环境绑定。`final-verified-mod.jar` 与 `final-gametest-results.xml` 是从该导出目录复制的实际文件。

## 拒绝边界与保留的纠正

- 未选择预览、旧 revision、外部源文件 SHA 冲突均拒绝。手写归属和修复内容经过新进程重开检查。
- 实际 SDK 等待超时后，显式取消构建；任务终态为 `cancelled`。
- 修改隔离宿主 JAR 或原始 XML 后，即使允许历史导出，也因证据摘要变化被拒绝；精确原字节随后恢复。
- 外部负向输入准备暂时移出 T12 数据文件，真实宿主只执行 5 项业务测试。冻结清单保持 6 项，返回 `GAMETEST_REQUIRED_CASES_MISSING`，明确缺少 `copperbench_camp_kit:t12`；该失败任务不能导出。恢复后重新验收六项全部通过。
- 改变源输入后，普通导出拒绝；显式历史导出保留原来的受测 JAR 并标为 `passed_historical_input`。恢复内容后通过新的当前输入验收，未把历史结果冒充当前结果。
- 主流程最初的重复报告反例误选 XML 首项 `minecraft:always_pass`，实际只证明框架项不能凑业务数量。已将编排改为明确选择 `scope=acceptance`，并在新的产品进程中补验真正重复的业务身份。补验的六行 XML 都是同一个业务 ID，记录与文件摘要匹配，当前及允许历史导出均在原始报告重解析处拒绝。这是明确标识的损坏历史记录 fixture，不是正向运行证据。

补验原始记录、XML、拒绝回执与进程退出记录位于 `duplicate-business-recheck`。原有框架反例没有删除或改称业务重复通过。

## 产品和 SDK 变化

Fabric 26.3 在已有六项 Native 操作之外开放 `prepare_game_tests`、`run_gametest` 和 `export_workspace`；仍需会话选择、各操作权限及冻结清单。NeoForge 的相应操作尚未开放，客户端、数据生成和结构化字段仍未验证。公开 `supportStage` 保持 `registered`。

Python Native、Python MCP 与 TypeScript SDK 的 required-cases 调用现在只对运行测试自动附带 task authorization。已验证导出没有对应授权 capability；过去 SDK 自动附加运行授权会被 Core 以操作不匹配拒绝。调用者明确提供的 payload 不被静默改写，回归测试覆盖运行附带、读取和导出不附带。

原生样例的首次文件准备由本地 fixture 完成；Native API 编辑既有文件，没有虚构 create-file 接口。测试授权仅写入隔离配置目录并在 finally 撤销，未修改用户的生产授权；桌面授权 UI 未在本票运行。

## 验证及证据范围

7 类定向 Java 检查中 51 项通过、1 项真实宿主 opt-in 跳过；该项已在域层独立执行通过，合并为 **52 个不同 Java 用例通过**。补充业务重复反例使用新产品进程 PID 39472（exit 0），两项实际导出均拒绝，见 `duplicate-business-recheck/result.json`。

98 项 Python SDK、18 项 TypeScript SDK、41 项 UI-Core、Javadoc、Markdown 链接和保留 CRLF 的差异检查通过。所有实际运行和 opt-in 跳过分别保留在原始 XML 与 `java-summary.json`，没有把跳过计为执行。

稳定归档为 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-09`。包含失败/修复/最终结果、真实导出、原始 XML、JAR、环境和源文件摘要，排除依赖缓存、玩家世界及私有签名密钥。使用已有 Gradle 缓存，没有冷缓存或安装版声明。

本票确认服务端六项业务验收和可信导出；玩家输入、渲染、保存重进、Ubuntu 与安装候选由后续 tickets 独立验收。
