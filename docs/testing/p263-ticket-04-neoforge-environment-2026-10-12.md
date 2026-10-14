# Ticket 04：NeoForge 26.3 环境资格验证

日期：2026-10-12（日本时间）。状态：**done（本地依赖资格范围）**。代码位于 `codex/p263-unique-cases`，尚未提交或合入。

## 冻结与来源

[环境锁](../../tools/generator-probes/neoforge-26.3/environment.lock.json)固定 Minecraft 26.3、NeoForge **26.3.0.76**、ModDevGradle **2.0.148**、Gradle **9.6.0** 和工作区 Java **JetBrains Runtime 25.0.3+1-b329.124**。Mojang 原始元数据要求 Java 25；已校验客户端内的资源/数据包格式为 **97.1 / 121.0**。产品 JBR/JCEF 与工作区 Java 分别记录角色、发行版和摘要。

Mojang、NeoForge 和 ModDevGradle 的下载已逐项核对官方 SHA-1，并另算本地 SHA-256；Gradle 官方 SHA-256 与既有 wrapper 固定值一致。JBR 的归档 SHA-512 来自 JetBrains 官方 checksum，本轮使用已有安装完成构建，未声称重新安装该归档。

MCreator EAP 参考固定为 tag `2026.3.41718`、提交 `0a4c3c1ceffe202437e9ea143229fa3bf5e1a07e`、生成器 tree `4f0c34033e7df98233f4a0e3a3530d67eb0faba2`，实际目录为 `plugins/generator-26.3.x`。已读取该提交的许可证、生成器配置、构建模板及 mod 入口模板；其 GPL-3.0 和 section 7 条款记录在锁中。

与当前受维护的 26.2 构建模板比较，选择的构建差异为 ModDevGradle `2.0.141 → 2.0.148`；Java 25、`neoForge` DSL 和 `sourceSets.main` 接入方式一致。EAP 自带依赖是 `26.3.0.71-beta`，本探针明确选择官方 Maven 已发布的 `26.3.0.76`，没有把 EAP 的版本名冒充实际使用版本。

这一构建切片已在当前产品使用的 JBR、现有 wrapper 和外部 Gradle 入口中成功编译，不要求替换 MCreator 宿主。EAP 的结构化模板和模型字段没有整体导入；对应产品创建及元素适配仍由 08、15 等后续 tickets 完成。

## 实际运行

[独立工程](../../tools/generator-probes/neoforge-26.3/build.gradle)编译真实的 NeoForge `@Mod` 和 Minecraft Item/ItemStack 引用。第一次构建执行原始依赖下载、Minecraft 类库准备、Java 编译和打包，**7 分 12 秒通过**。本机已有 Gradle 缓存可以复用，新依赖实际下载；不是冷机器或玩家验收。

[依赖锁](../../tools/generator-probes/neoforge-26.3/gradle.lockfile)和[验证元数据](../../tools/generator-probes/neoforge-26.3/gradle/verification-metadata.xml)包含 **221 个组件、376 条 SHA-256 记录**；未发现 SNAPSHOT、latest 或动态版本。锁定后使用 `--offline --rerun-tasks`，**15 秒通过**，探针的 Java 编译、资源与 JAR 动作重新执行，Minecraft 基础类库的已校验缓存复用。两次 JAR 摘要完全相同。

| 证据 | SHA-256 |
| --- | --- |
| 最小模组 JAR | `9688aa6738bfa44b206d9176aad3deea3864ab3886086347ffcdda5f9401fa02` |
| 环境锁 | `60d12b9a50c5fc53f67ddb9691efeb25cb13656b6105967f484af45ee9505d3f` |
| 依赖锁 | `b3d77145b1b294691b6d48276d0f21e69239cdc83d49c83185214ea124afee69` |
| 验证元数据 | `e53849aae1e401319908c3f158ccab45bacabd3e59dfb6fe3342ec9068f9d3ba` |

## 拒绝边界与保留证据

共享探针的 9 项边界回归通过。NeoForge 本路线另外执行三个真实 CLI 反例：缺失依赖、摘要不符、动态版本分别返回退出码 1 和具体错误码，均未启动构建。不是用 Fabric 的构建结果替代本路线。

最终回执为 `output/p263-environment-discovery/neoforge-26.3-offline-rebuild/result.json`。原始文件、来源与逐次日志归档在 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/environment-probes`；执行方式和来源链接见[探针说明](../../tools/generator-probes/README.md)。

这张票只关闭当前开发主机上的依赖资格与构建切片。Native 产品全链、业务 GameTest、安装、Ubuntu 桌面会话与玩家操作仍需后续各票的实际结果。
