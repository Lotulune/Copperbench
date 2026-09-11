# Stage 16：分发包中的 Agent 故障恢复

日期：2026-09-11。状态：A 段首个案例通过，Stage 16 整体进行中。

## 来源与范围

任务授权、真实文件隔离和产品 GameTest 闭环已提交为 `943fd582`。后续 Windows 启动恢复、GameTest 短目录与无交互创建改动提交为 `dabb36a4`。使用该提交执行 `exportWindowsZip -Psnapshot=true`，将 ZIP 解压到仓库之外，再直接调用其中的 `copperbench.exe`。没有使用仓库运行类路径或替换安装目录中的文件。

- ZIP SHA-256：`5f10dcf57a72670e6adfecf91529142d3acf84b4aa09812aa089d7f873bf4755`。
- 应用 JAR SHA-256：`e9b7431cebc354d3267c53ec6d05b101a90600e19272019e1dc1bbd040f8a8ae`。
- 环境：Windows x64 开发机、Fabric 1.21.1、包内 Java 21，使用现有依赖缓存。
- 输入：[寻路铃工作区](../../examples/agent-native/wayfinder-bell/wayfinder_bell.mcreator)。仓库示例保留原试作的三个原生实现文件和玩法断言，整理了示例许可/描述/作者元数据；不包含本机历史、授权和构建目录。

候选元数据见 [candidate.json](../../evidence/stage16/2026-09-11/candidate.json)。这是本地候选包，尚未公开发布。

## 发现与修复

最初从 `943fd582` 构建的分发包能完成 generator discovery 和环境查询，但在编译前出现 `Unable to establish loopback connection`，最终仅返回通用 `FABRIC_BUILD_FAILED`。[失败重放](../../evidence/stage16/2026-09-11/baseline-replay/summary.json)保留了这个结果。

本轮补齐三条恢复路径：

1. Windows 工作区 JDK 在独立进程中检查 selector 本地通信；失败时验证 TCP 回退可用，再只对该任务子进程应用兼容选项。检查发生在构建之前；两种方式都失败时返回 `GRADLE_LOOPBACK_UNAVAILABLE`。测试脚本主动移除调用方 socket 补丁，成功运行由产品自身的 `GRADLE_IPC_TCP_FALLBACK` 记录证明。
2. GameTest 的 Windows wrapper 路径达到 240 字符预算时，切换到用户缓存下的独立任务目录；工作区保留 `execution-location.json` 和源码指纹。首轮实际 wrapper 路径从预期的 244 字符降至 126 字符；这条首轮证据只证明回退被触发。
3. `bootstrap create-workspace --no-prompt true` 在没有授权时及时返回 `USER_APPROVAL_REQUIRED`、目录及所需范围。已有有效授权的创建保持可执行；授权签发仍由用户完成。

## 首轮重放结果

由[重放脚本](../../scripts/verify-stage16-packaged-agent.ps1)在新建工作区副本中执行，全部步骤达到预期：

| 步骤 | 产品结果 | 耗时 |
| --- | --- | --- |
| 生成器发现 | 成功 | 8.0 秒 |
| 无授权、无交互创建 | 拒绝，返回待批准的范围，目标目录未创建 | 4.6 秒 |
| 工作区环境 | 成功，解析到包内 Java 21 | 6.0 秒 |
| 故意注入编译错误 | 失败，`JAVA_COMPILE_ERROR` 定位到注入文件 | 109.8 秒 |
| 修复源码后构建 | 成功 | 36.0 秒 |
| 故意提高最低验收数量 | 实际执行 2 项，但不满足 10,000 项要求，正确拒绝 | 302.6 秒 |
| 恢复验收要求并重跑 | 2/2 通过，0 失败、0 跳过 | 306.5 秒 |

长任务首条 JSON 反馈在本次重放中为 1.59～2.05 秒。最终 `sourceCurrentAtCompletion=true`，真实 JAR 与 XML 的 SHA-256 均再次核对；原示例文件字节保持一致。两个验收用例分别为加载检查与包含多条断言的寻路铃行为场景，不能解读为两个独立玩法项目。

正向与数量不足两次运行加载了同一份模组 JAR：`80908fcd3ff291ec96c76a5d51deced464453f88d48f761901062006310e9df1`。该产物包含脚本修复后的无害编译探针类，因此不与 Stage 15 原始试作 JAR 混同。

完整结果见[重放摘要](../../evidence/stage16/2026-09-11/fixed-replay/summary.json)、[最终 JSONL](../../evidence/stage16/2026-09-11/fixed-replay/06-accepted.jsonl)、[GameTest XML](../../evidence/stage16/2026-09-11/fixed-replay/gametest.xml)和[机器汇总](../../evidence/stage16/2026-09-11/verification-summary.json)。

## 更深路径复测与历史缓存竞态

追加的 279 字符、含中文和空格的工作区首先暴露了另一处故障：快照读取 `.mcreator/localHistory/objects/*.tmp` 时，本地历史服务正在替换该临时文件，导致 `NoSuchFileException`。这个失败发生在 Gradle 启动前，见[失败摘要](../../evidence/stage16/2026-09-11/deep-path-before-history-fix/summary.json)及[对应异常](../../evidence/stage16/2026-09-11/deep-path-before-history-fix/application-failure.json)。

`eb34ad5f` 将本地历史、自动备份和用户设置排除出验收输入，同时保留根目录的工作区定义、原生源码和其他自定义元数据；真实输入消失仍会返回 `WORKSPACE_SNAPSHOT_CHANGED`。这项修复已有持续修改内部状态、保留自定义输入、真实源码消失的回归覆盖。

随后从新解压的 `eb34ad5f` 分发包重跑完整七步流程，全部达到预期。最终工作区的预期 wrapper 路径为 **285 字符**，产品自动使用 **126 字符**的实际路径；快照为 **29 个输入文件**，其中内部历史/备份/用户设置条目为 0。最终 2/2 通过、`sourceCurrentAtCompletion=true`，被测模组 JAR 仍为 `80908fcd…e9df1`，与首轮一致。[最终深路径重放](../../evidence/stage16/2026-09-11/final-deep-replay/summary.json)及[后续机器汇总](../../evidence/stage16/2026-09-11/followup-verification-summary.json)保留完整信息。

## 增量构建与最终候选

远端干净构建发现 `diagnostic.gradle_loopback_unavailable` 缺少中文词条。本地此前复用 UI 缓存，原因是 `buildUiShell` 没有声明词条检查实际读取的 Java 文件输入。`a3322c86` 补齐词条与输入声明，并通过“有效基线 → 只增加 Java 诊断键时检查失败 → 恢复原始 Java 字节后成功”的[增量回归](../../evidence/stage16/2026-09-11/build-input-regression.json)。没有修改 CI 工作流或降低检查要求。

最终又构建、解压并检查了 `a3322c86` 的 Windows ZIP，生成器发现和环境查询通过。其应用 JAR 中 **2,113 个 Java class 的内容与完成深路径玩法验收的 `eb34ad5f` 候选完全一致**；本次后续产品差异为中文词条与构建输入声明。完整玩法重放对应 `eb34ad5f`，最终候选的单独冒烟与 class 字节比对对应 `a3322c86`，二者分别记录，不宣称又执行了一次完整玩法重放。

最终 ZIP SHA-256：`3c4dded214d7ae36f347a34149753a72cdb9d69ba76996b3fe53ad8ea6c71837`；应用 JAR SHA-256：`8be5272520baf05d49366d5c41a2bea56a3f9804293a97a6427d819aa759947d`。见[最终候选元数据](../../evidence/stage16/2026-09-11/candidate-final.json)与[候选冒烟输出](../../evidence/stage16/2026-09-11/latest-candidate-smoke.json)。

## 验证边界与后续

实现提交 `a3322c86` 的 [Java/Javadoc、UI 和 MCP CI](https://github.com/Lotulune/Copperbench/actions/runs/34588551646)及 [Ubuntu 包候选检查](https://github.com/Lotulune/Copperbench/actions/runs/34588551765)全部通过。Ubuntu job 包含 portable/deb 构建、无系统 Java/Gradle/Git 的包内 bootstrap、Xvfb 下的 JCEF，以及 Fabric/NeoForge 1.21.1 渲染预检；这不替代 clean GNOME 安装认证。[CI 记录](../../evidence/stage16/2026-09-11/implementation-ci.json)固定了来源提交与运行链接。后续仅追加文档和证据，不改变这些受测实现。

- 首轮 38 个相关 Java 回归用例通过；加入后续检查后，按各 suite 最近通过结果合并为 [42 个不同用例](../../evidence/stage16/2026-09-11/followup-regression-summary.json)，无失败、无跳过，包括包内 Java 21/25 的实际通信预检。重复执行不叠加，数量也不与上一轮 58 项相加。
- 脚本重放期间人工介入 0 次；起点是已存在的示例工作区，未验证新用户首次签发授权并创建工作区。
- 执行者已经读过 Copperbench 实现，因此 `freshAgent=false`。这次不是陌生 agent 盲测，也不是新开发多个模组的证据。
- 每次完整 GameTest 任务约 5 分钟，大部分成本在冻结项目构建与宿主准备；实际游戏用例只占其中一部分。后续应测量并减少重复准备成本，同时保留源码与产物绑定。
- 未关闭陌生 agent 多模组试作、干净 Windows/Ubuntu 安装回归、真实客户端输入和存档重进验收。原工作区、Server 与 Datagen 的一般长路径支持仍有限制。

后续范围按 [Stage 16 路线](../roadmap/stage-16-agent-reliability.md)推进。现有公开版本状态不因分支推送或本地候选包而改变。
