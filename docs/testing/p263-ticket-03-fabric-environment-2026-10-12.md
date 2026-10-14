# Ticket 03：Fabric 26.3 环境资格验证

日期：2026-10-12（日本时间）。状态：**done（本地依赖资格范围）**。实现位于 `codex/p263-unique-cases`，尚未提交或合入。

## 冻结结果

[环境锁](../../tools/generator-probes/fabric-26.3/environment.lock.json)固定原始来源、获取时间、输入摘要、Java 两种角色、原有生成器源码提交及独立探针源码。使用 [共享探针](../../tools/generator-probes/probe_environment.py) 和 [独立构建工程](../../tools/generator-probes/fabric-26.3/build.gradle)，不更新现行产品支持状态。

| 输入 | 已核对的值 |
| --- | --- |
| Minecraft | 26.3，Mojang release |
| 工作区 Java | JetBrains Runtime 25.0.3+1-b329.124，Java 25 |
| 产品 JBR/JCEF | 独立记录同一安装的发行版、java.exe SHA-256 与 JCEF 模块 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.162.0+26.3 |
| Loom | 1.17.21 |
| Gradle | 9.6.0，现有 wrapper；官方分发 SHA-256 与 wrapper 固定值一致 |
| 资源 / 数据包格式 | 97.1 / 121.0，读取自已校验客户端内的 version.json |

Mojang 元数据及客户端通过官方 SHA-1 核对；Loader、API 和 Loom 下载通过各自官方 Maven SHA-1 核对，并另算本地 SHA-256。JBR 下载 SHA-512 取自 JetBrains 官方 checksum，本轮复用已有安装而未重新下载安装该归档；这与实测使用该安装完成编译分别记录。生成器基于当前 Copperbench 受维护源码，未引入未获适用许可的社区插件。

精确依赖与依据见 [探针说明和来源链接](../../tools/generator-probes/README.md)。官方正文通过 smart-search CLI fetch 取得；原始依赖字节通过独立下载校验。主搜索模型的 HTTP 503 未被用来推断任何依赖版本。

## 实际构建与复现

第一次有效构建使用新建探针工程、已有本机 Gradle 缓存和网络，真实执行 `compileJava`、资源处理、JAR 与 build；**3 分 9 秒通过**。产物含 `FabricEnvironmentProbe.class` 和精确 Fabric 元数据。源码编译引用实际 Minecraft Item、ItemStack、Items 以及 Fabric ModInitializer，未运行 Minecraft。

生成的 [依赖锁](../../tools/generator-probes/fabric-26.3/gradle.lockfile)与 [验证元数据](../../tools/generator-probes/fabric-26.3/gradle/verification-metadata.xml)锁定 174 个组件、375 条 SHA-256 记录。检查未发现 SNAPSHOT、latest 或动态版本。完整性校验不等同于生产者签名认证。

随后以相同锁执行 `--offline --rerun-tasks`，**17 秒通过**，四项构建动作实际重跑；初次构建与离线重新编译产生完全相同的 JAR：

- JAR SHA-256：`779a1b47233e6e3c2e36837332ad41469dc7feebe101c2edcb7a80c9496ae5da`
- 环境锁 SHA-256：`fce6a8eab04a8279d0b5026dded7bdb6e9647d51bca7e5dfb31ea980f5678836`
- 依赖锁 SHA-256：`6ceab4dd6522181598c897c8a6bfba1dbe8903436f07a7af777ac8712327b54b`
- 验证元数据 SHA-256：`f8a51f737a1ed855f72659fef76fc03006e5382ceb41ade6d1df72071305c0ac`

首次启动器参数尝试因 PowerShell 把 `-p` 识别为公共参数缩写而失败，尚未进入 Gradle；使用明确的 `--project-dir` 修正。另一次离线检查多数任务为 UP-TO-DATE，因此最终判定采用后来强制重新编译的记录，全部尝试分别保留。

## 拒绝边界与证据

[9 项边界回归](../../tools/generator-probes/test_probe_environment.py)通过，涵盖缺失依赖、同尺寸篡改、上游与本地摘要分别核对、动态版本、源码变化、危险路径、重复成员、未知版本和锁定依赖图变化。Fabric 的三个真实 CLI 负向探针分别以退出码 1 返回 `DEPENDENCY_MISSING`、`DEPENDENCY_DIGEST_MISMATCH`、`DYNAMIC_VERSION_FORBIDDEN`，均在启动构建前拒绝；NeoForge 同入口也执行了三项独立负向检查。

原始证据位于 `output/p263-environment-discovery/`，最终成功回执在 `fabric-26.3-offline-rebuild/result.json`，输入 artifact 与元数据原样保存。稳定归档位于 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/environment-probes`。

本票证明这套明确依赖能够在当前开发主机编译并离线重建最小模组。26.3 的产品创建/重开、业务 GameTest、可信导出、安装与玩家操作仍由后续对应 tickets 验收。
