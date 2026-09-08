# Survey Pulse：原生 Agent 编写与 Copperbench 对照

日期：2026-09-07。产品源码基线：`eaf18606e683fcc37a395de83c53e538c947760e`，分支 `codex/stage13a-procedure-productivity`。

本轮任务：在当前 ChatGPT＋本地编码工具环境中，不使用 Copperbench 的创作接口制作与前一轮相同的模组，并据实修订后续 PRD。这里没有另行启动独立 Codex CLI 或 Claude Code，也没有进行不同模型的排名。

## 1. 结论与适用范围

原生 Fabric 版本已成功编译、重映射和打包，八项纯扫描逻辑检查通过，外部源码修改在更改显示名并重建后保持不变。它不需要 `.mcreator`、代码包元数据、Copperbench MCP、MCreator 生成器或 Copperbench 应用运行时。

在本例中，原生代码的直接性主要体现在入口注册、源码权威来源和普通文件迭代，不是显著更快的 javac/Gradle。两边预热后的 raw-Gradle 构建耗时接近。Copperbench 的可视化、元素引用、审阅和恢复不能被这个简单原生工程自动替代；但其结构化托管也不应成为自由实现玩法的前置条件。

**没有完成游戏内交互、多人同步、粒子视觉效果或完整玩法验收。** 编译产物、纯函数断言和启动探针是不同证据层级。启动探针的范围见第 6 节；不能把到达 EULA 边界或进程正常退出写成游戏行为通过。

## 2. 规格、实现与复用边界

两边规格：Fabric 1.21.1；普通扫描半径 5，潜行半径 8；包含边界的立方扫描；服务端只读取已加载位置；按注册名 `_ore` 后缀和 `ancient_debris` 识别；显示数量和最近相对坐标；60 tick 冷却；最近位置 12 个 END_ROD 粒子；紫水晶碎片＋铜锭合成一个勘探杖；原版紫水晶贴图。

同一套工具链：Java 21、Gradle 9.7.0、Loom 1.17.19、Fabric Loader 0.19.3、Fabric API 0.116.15+1.21.1、Mojang mappings。完整规格在 [fixture-spec.json](../../evidence/agent-native/2026-09-07/fixture-spec.json)。

原生版本重新编写普通 `ModInitializer`、物品注册、UseItemCallback、扫描类和资源 JSON。为了无需启动 Minecraft 就测试扫描策略，使用一个读取方块 ID 的小接口隔离游戏适配层；没有为此增加 DSL 或通用框架。

明确复用了已知需求、上一轮看到的代码/算法思路、已验证的依赖版本、现成 JDK、用户 Gradle 依赖缓存，以及标准 Gradle Wrapper 文件。没有复制 Copperbench 生成的 Java/Mixin 或 `.mcreator`。因此这不是零知识/冷机器的从零公平耗时比赛。没有清空用户缓存。

上轮 Copperbench 路径使用生产源码组件和真实 Desktop MCP HTTP，但由自定义测试入口启动，不是完整安装产品 GUI 流程。其补齐工作区初始化、Gradle 同步和依赖下载的历史耗时不能全部归到 Copperbench 用户体验。

## 3. 原生路径实际遇到的问题

首次 `clean build` 耗时 98.944 秒，整体失败。Java 编译、重映射以及八项自执行扫描检查已通过，但默认 Gradle `test` 任务发现 `src/test` 中有源文件却没有 JUnit 测试，报 `There are test sources present ... did not discover any tests`。

这是本轮测试接线错误，不是玩法 Java 编译错误。修复是把可执行断言移到独立 `contractTest` source set，并让 `check` 依赖 `scannerContractTest`，没有关闭 Gradle 的“未发现测试”失败检查来冒充成功。

修复后的 `clean build` 成功，耗时 23.968 秒；八项断言全部通过。原始失败与成功日志均保留在 [证据目录](../../evidence/agent-native/2026-09-07/)。这些进程耗时不包含全部思考、查阅、工具往返及环境准备，不能当成总创作时间。

服务端探针的第一次启动脚本还遇到 PowerShell 参数解析错误，发生在 Gradle/JVM 启动前，记录于 [probe-invocation-note.txt](../../evidence/agent-native/2026-09-07/probe-invocation-note.txt)。它与任一模组运行结果分开记录。

## 4. 源码共存与结构比较

原生实验：给 `OreScanner.java` 添加外部修改标记，记录 SHA-256，再修改语言资源中的物品显示名并执行 `build`。构建成功，源码哈希前后均为：

```text
F6BBBDF14663D18397CDF98DF452DF74D539E14CB7401D120EAE68C8292155FF
```

显示名随后恢复，外部修改标记保留。见 [native-preservation.json](../../evidence/agent-native/2026-09-07/native-preservation.json)。这只是普通文件工作流，不等于原生版本实现了托管元素的 preview、revision 或 recovery。

上轮已通过真实 MCP 复现 CB-AUDIT-01（两个受管代码元素认领同一文件）和 CB-AUDIT-02（仅修改显示名回灌旧代码）。本轮没有修改其产品实现；这些仍是 14A 待修复 P1。两次旧操作返回过恢复点，但对应恢复尚未验证。汇总取自保留的原始报告，不重新导出含计划凭据的原始 plan 载荷。

| 观察项 | 原生项目 | Copperbench 试作项目 |
| --- | --- | --- |
| 主源码 Java 文件 | 3 | 22 |
| JAR 内 class 文件 | 5 | 39 |
| 模组 JAR 字节数 | 10,378 | 45,772 |
| 原生初始化 | 标准 Fabric entrypoint 直接注册 | 上轮另在生成主类的用户代码块加入调用 |
| 源码内容来源 | 文件本身 | 代码包元数据＋磁盘存在同步约束 |
| 同规格配方、贴图、入口类打包检查 | 通过 | 通过 |

Copperbench 项目含通用事件/Mixin 支撑代码，以及上轮一个审计辅助类。文件数、体积并非严格相同实现的优化分数，也不是模组质量判定。关键是这些额外支撑代码是否带来有效能力和版本维护负担。

## 5. 控制后的构建观察

对两个已有项目使用同一 JDK、Wrapper、用户依赖缓存和 `--no-daemon --console=plain clean assemble`。顺序预先设为原生 → Copperbench → Copperbench → 原生，不并行执行。

| 项目 | 第一次 | 第二次 | 结果 |
| --- | ---: | ---: | --- |
| 原生 | 22.257 秒 | 23.826 秒 | 两次成功 |
| Copperbench 已生成工程 | 23.617 秒 | 23.852 秒 | 两次成功 |

**本例没有证明明显的构建速度差距。** 这只隔离观察 raw-Gradle 编译/打包，不包含 Copperbench 的 MCP、生成器或审阅开销，也不是总开发效率比较。每边只有两次样本，项目缓存和源码规模不同，不发布速度提升百分比。

原始计量与产物摘要见 [comparison-summary.json](../../evidence/agent-native/2026-09-07/comparison-summary.json)。原生纯逻辑测试不放进该 `assemble` 耗时对照，防止一边测试、一边不测试。

## 6. 编译之外的风险与启动探针

Copperbench 工程两次成功打包时都出现三个目标方法不能重映射的告警：`Commands.performCommand(...)I`、`RepairItemRecipe.assemble(...)ItemStack`、`ServerPlayer.drop(Z)V`。相关 Mixin 配置为 `required=true`、`defaultRequire=1`。原生示例不含这些生成器 Mixin。

随后补充实际探针，使用各自独立的 `build/audit-server-probe`，显式 `eula=false`，不接受协议、不创建用户世界。结果如下：

| 开发服务端 bootstrap | 原生 | Copperbench 生成工程 |
| --- | --- | --- |
| Loader 识别 survey_pulse | 是 | 是 |
| 自定义初始化标记 | `SURVEY_PULSE_INITIALIZED` 出现 | 未出现 |
| 到达未接受 EULA 的正常边界 | 是 | 否 |
| Gradle/进程结果 | 退出 0 | 退出 1 |
| 世界/玩法运行 | 未执行 | 未执行 |

Copperbench 侧实际错误为 `ServerPlayerMixin` 的 `drop` 回调注入失败：`failed injection check, (0/1) succeeded. Scanned 0 target(s)`，随后 `Mixin transformation of net.minecraft.server.level.ServerPlayer failed`。模板位置是 `plugins/generator-1.21.1/fabric-1.21.1/templates/mixin/serverplayer_mixin.java.ftl:25`，目标字符串为 `drop(Z)V`。

这作为 **CB-AUDIT-03：已复现、未修复的 P1 启动缺陷**进入维护/14A 优先队列。不能把它当作普通依赖网络问题，也不能因为 Java 编译成功而关闭。Commands 与 RepairItemRecipe 两处目前只有明确重映射告警，不冒充已经逐一运行验证其影响。

原始依据为 [runtime-probes.json](../../evidence/agent-native/2026-09-07/runtime-probes.json)、[原生启动日志](../../evidence/agent-native/2026-09-07/native-server-probe.stdout.log) 与 [Copperbench 启动日志](../../evidence/agent-native/2026-09-07/copperbench-server-probe.stdout.log)。两边 `eula.txt` 均仍为 false，均未产生 world 目录。

原生侧首次缺少 `server.properties` 的日志后到达协议提示，不据该日志认定玩法有缺陷，也不据退出 0 判为服务器就绪。探针使用开发类路径，不是对安装进游戏的打包 JAR 进行验收。此次只证明本例的启动差异，不扩展为全部生成器或已发布安装包都受影响。

## 7. 对 PRD 的实际调整

- `PRD-NEXT.md` 升为 v1.9：14A 先修源码归属与 metadata-only 回灌；14B 提供原生源码/IDE/CLI 从零创建闭环；14C 做运行证据和对照；14D 再深化审阅/模板/扩展。
- 保留 FR-ADV-01～06 的需求编号，并新增 FR-ADV-07（原生创建闭环）、08（分层运行证据）、09（同模型收益对照）。普通外部文件编辑不再被概念上等同于非法绕过，但未校验结果不得被报告成功。
- 新发现的 CB-AUDIT-03 与 14A 并行优先修复；增加默认运行钩子的版本回归要求，以及“退出 0 / 初始化 / 世界就绪 / 行为通过”不得互相冒充的验收条款。
- `PRD.md` 作为已关闭的历史基线只补当前路线入口，不重写旧完成事实；`docs/remaining-work.md` 明确两项 P1 未修复；新增 [ADR-0017](../adr/0017-native-first-agent-workbench.md)。

保留认证、真实文件归属冲突、恢复点和受保护操作边界；减少的是强制表达方式、重复源码搬运和无差别人工中断，不是把“原生优先”解释成任意覆盖。

## 8. 可复现材料与限制

测试工程和运行脚本位于当前开发 worktree 的 `.tmp/astra-native-comparison/`，其中 `native/` 是普通 Fabric 项目，`run-gradle.ps1`、`preservation.ps1`、`matched-builds.ps1`、`server-probes.ps1` 记录具体操作。旧项目位于 `.tmp/astra-audit/survey_pulse/`。

为避免只保存在临时目录，原生源码另逐文件校验后保留在 [examples/agent-native/survey-pulse](../../examples/agent-native/survey-pulse/README.md)。见 [源码清单](../../evidence/agent-native/2026-09-07/example-source-manifest.json)；被测的是临时目录，示例目录是字节校验副本，不冒充在新路径重新执行过构建。完整源码 ZIP 和独立命名的原生 JAR 也位于 `.tmp/astra-native-comparison/delivery/`。

没有测量 token/计费，也没有同等预算的完整创作时间数据。八项断言只验证原生扫描策略，不能声称已经对两边完成相同运行行为验收。没有修改产品 Java 实现、发布候选或平台支持声明；规划批准不代表上述缺陷或 Stage 14 已完成。

本轮支持的结论是：应让通用 Agent 可以直接使用原生代码，并让 Copperbench 在资源理解、准确上下文、运行诊断和可靠协作上证明增益。不能仅凭这一个案例推断所有玩法都应弃用可视化，或所有强模型都无需专用工具。
