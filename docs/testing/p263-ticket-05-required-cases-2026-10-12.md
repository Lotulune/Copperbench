# Ticket 05：冻结必要用例清单

日期：2026-10-12（日本时间）。状态：**done**。公共入口、持久化、导出、源码回归与真实 Native 验收均通过。未提交或合入。

## 契约与身份

[独立协商入口](../../src/main/java/dev/copperbench/core/contract/AcceptanceExtension.java)使用 `required_cases` 契约、版本 `1`。每次请求携带 `supportedVersions`，Core 在执行前选择版本；不支持或未提供版本时拒绝。`negotiate` / `catalog` 只读，`run` / `get` / `export` 调用现有受权限、修订和授权约束的 Core 入口。Native 使用 `kind=acceptance`，MCP 使用 `acceptance` 工具，Python/TypeScript SDK 提供对应方法。

新请求定义在[独立 schema](../../ui-core/schemas/extensions/required-cases-v1.schema.json)。旧 UI-Core v1.0 的命令、任务、查询及导出对象不增加字段；未经协商向旧命令塞入 `requiredCases` 会拒绝。查询返回新扩展外层与其中原样的 `core` 对象。能力发现不授予权限、EULA 或预览资格。

[清单](../../src/main/java/dev/copperbench/generator/RequiredCaseManifest.java)固定版本、ID、身份类型、必要身份、门槛及 SHA-256。JUnit 使用原始 `(className, name)`，GameTest 使用报告中完整资源 ID；展示截断不参与身份比较。摘要按排序后的身份逐字段计算：UTF-8 字节前置四字节大端长度，字段顺序是 schemaVersion、id、identityKind、minimumTests、各用例的 className/name。JSON 空白、属性顺序和展示语言不影响摘要。

- `camp-kit-native-v1`：T01/T02/T03/T04/T11/T12，minimumTests=6。
- `camp-kit-scoped-v1`：T01–T13，minimumTests=13。

固定 GameTest 资源身份为 `copperbench_camp_kit:t01` 等小写编号，`className` 为空。宿主后续按这些事前固定的身份登记业务测试。已发布的两个清单不能改项、减项或复用其 ID；自定义开发子清单不会自动成为 G1/G2 门禁证据。

## 单次任务与导出

入口复制并验证请求，开始构建/验收进程前创建一次性的 `required-cases-frozen.json`，绑定任务、工作区、修订、输入快照、精确环境、独立宿主及报告路径。清单选项及结果保存在任务的内部记录中，v1.0 投影剥离该内部字段。扩展查询在重连后从同一工作区的记录恢复。

判定同时检查原始 XML、独立业务执行量和全部必要身份。额外通过用例不能补偿缺项；跳过不算通过；同一资源身份换模板不能增加覆盖；业务失败优先。运行结束时复核冻结文件，导出时再次核对文件摘要、原绑定、环境、单次宿主、原始报告和清单覆盖，不能用 persisted passed 或跨任务/加载器记录替代。

旧合法 M1 五项仍按原 minimumTests=5 使用。扩展读取旧任务时明确返回 `legacy_scope`，扩展导出拒绝无清单记录。26.3 不接受无清单验收；这条保护不表示 26.3 产品目标已经开放。便携 JSON 中的 `requiredCases.schemaVersion=1` 是独立扩展，旧基础回执与旧 wire 对象保持兼容；完整离线包核验由后续票提供。

## 当前验证

- Python SDK：96 项通过；TypeScript SDK：17 项通过。
- UI-Core：39 项通过，包含独立清单请求/响应与 strict v1.0 拒绝新字段。初次因依赖未安装无法加载 Ajv；安装锁定依赖后修正新 schema 的 `strictRequired` 定义，最终全部通过。该 schema 失败的原完整日志被同名最终输出覆盖，已按实际工具输出保存单独失败摘要；其余失败日志与最终日志分别保留。
- Java：按用例身份合并最后结果，**81 项通过，0 失败，0 跳过**；Javadoc 通过。包含[工作流回归](../../src/test/java/dev/copperbench/generator/RequiredCasesWorkflowTest.java)、真实 HTTP MCP 边界、Native 请求、JCEF 工作区隔离、持久化恢复及旧 GameTest/导出回归。工作流中的进程替身只用于可控原始报告与篡改边界，不计真实宿主证据。
- Java 首次编译发现新增局部变量与既有门槛变量重名；随后构建检查发现新诊断缺中文文案；均已修正。新增 Native 用例最初错误地预期旧 Core 返回 failed，并尝试断言旧通用错误入口未保留的 detail；现明确返回 rejected 与 `ACCEPTANCE_NEGOTIATION_REQUIRED`，定向复跑通过。全部原失败日志保留。
- UI：30 项检查通过；Gradle 执行的 UI 构建与本地化门禁通过。新增入口沿用原工作区绑定与权限检查，丰富状态展示仍由 13 处理。
- [真实 Native 验证入口](../../sdk/python/required_cases_delivery.py)已通过：复制原有已通过的 Fabric 1.21.1 M1 源码，保持五项独立预期，执行清单通过、同数量缺项失败、进程重连与导出。

## 真实会话与协议复验

最终运行 `acceptance-13939899035227743656` 使用两个真实产品进程、同一重开的工作区身份及四个受管任务。Python 流程耗时 **367.986 秒**；包含定向 Java 回归、Javadoc 和 Native 流程的外层 Gradle 在 **7 分 38 秒**后退出 0。

| 任务 | 结果 |
| --- | --- |
| `42f4b361-6c93-40b8-8f2a-2185dc479e32` | 五项固定必要身份通过；请求发出后修改调用者的清单对象不能替换冻结记录 |
| `7c9761b4-94e9-40db-9156-4865f52da5b3` | 关闭并重开产品进程后读取同一绑定并导出；原 JAR/XML 与导出字节摘要一致 |
| `06ed3a7c-a0c0-4bed-ae29-3e1714870059` | 真实宿主执行五项通过用例，门槛也是五，但其中一个必要身份缺失；明确返回 `GAMETEST_REQUIRED_CASES_MISSING`，不接受数量补足 |
| `0e7f277f-7fc6-4fb6-9ebe-26f71119e91b` | 失败清单的导出被真实 Native/Core 入口拒绝 |

未知协议版本、未经协商的旧命令和被篡改的摘要也从真实 Native 入口拒绝。最终九份真实响应通过[独立及旧 v1.0 schema 校验](../../ui-core/scripts/validate-required-cases-evidence.mjs)，该检查已纳入完整验收流程。

首轮功能流程通过后，逐条检查真实失败响应发现既有任务诊断在 Gson 树转换和持久化时丢失显式 `path: null`。修复限定在任务诊断的事件、查询和持久化边界；历史缺失位置投影为明确的未知位置，不改原历史文件，不放宽 v1.0 schema。相关恢复、查询与桥接回归及完整 Native 流程重新通过。首轮证据原样保留，并用 `wire-review.json` 明确标识协议失败，不能与最终整体验收混同。

最终从磁盘重新核对原 JAR、导出 JAR、原 XML、导出 XML 和冻结绑定的摘要；运行期间的产品与 SDK 源码摘要未变化。Java 用例按身份合并最新结果，重复执行不累加为新覆盖。

本票关闭必要清单契约，真实业务样例仍是既有 **Fabric 1.21.1 M1 五项**。两个 Camp Kit 清单固定了后续范围；26.3 两加载器的业务、安装及玩家验收由各后续票独立执行，本票不替代这些门禁。

工作证据目录为 `output/p263-05/`，稳定归档为 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-05`。本票已完成代码自查，可由后续票在共同工作树中消费；`done` 不表示主线合并或发布。
