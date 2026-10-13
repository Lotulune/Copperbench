# Ticket 02：精确生成器注册表

日期：2026-10-12（日本时间）。状态：**done**。精确注册、旧工程重开、相关定向回归、Javadoc 与原八轨道真实构建均已通过。

共同实施工作树为 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`；尚未提交或合入。

## 实现

[VersionTrackCatalog](../../src/main/java/dev/copperbench/tracks/VersionTrackCatalog.java) 现在持有独立于展示轨道的八个精确登记项，每项绑定现有后端 profile。`findGenerator`、`firstPartyGenerator` 和 [环境描述](../../src/main/java/dev/copperbench/generator/WorkspaceExecutionEnvironment.java) 读取同一登记来源。展示分组可以轮换或重合，不删除登记、不提高支持等级。

同版本加载器迁移比较后端的精确 Minecraft 版本与加载器；不同版本、同加载器和未知 ID 仍拒绝。既有四轨道 v1.0 投影与 fixture 完全相同，保留旧展示字段含义；26.1.2 的执行环境继续报告精确 26.1.2。没有新增 26.3 产品登记、改变已有工程 generatorId 或放宽预览权限。

## 已验证

定向检查合并各用例的最后结果：**85 项通过、0 失败、1 项按条件跳过**，重复执行不累计数量。Javadoc 通过。

- [目录回归](../../src/test/java/dev/copperbench/tracks/VersionTrackCatalogTest.java)：移除 26.1.2 展示分组后仍能精确查询，全部八个 ID 的迁移组合保持边界；重合展示不重复登记或降级；未知 ID 拒绝；旧投影一致。
- [真实重开回归](../../src/test/java/dev/copperbench/tracks/ExactGeneratorWorkspaceTest.java)：八个 ID 分别创建、关闭并通过正常 `Workspace.readFromFS` 重开，保留身份和 Java 17/21/25 环境；未知查询不创建文件。
- 原环境、创建、路由、两加载器四版本生成器、插件模板与迁移保护回归通过。

首次与复跑均在一个 Windows junction 夹具处得到 `CreateProcess error=5`，尚未进入该用例的迁移断言。夹具改用项目已有 PowerShell 7 原生创建 junction，并核对真实目标；之后全部十项迁移测试通过，没有跳过保护断言或修改迁移产品逻辑。前两次失败记录保留。

唯一跳过项是需要显式开启的 `LoaderMigrationRebuildServiceTest.rebuiltNeoForgeCopyBuildsAJar`。它不计为通过。八个原生成器的真实构建另外运行，不能由单元测试或该跳过项推导。

## 八轨道真实构建

使用既有 `NewWorkspaceGeneratorGoldenBuildTest.emptyWorkspaceGeneratorsBuildIntoJars`，显式开启 `copperbench.stage8.workspaceGeneratorBuild=true`，逐个生成空工程并执行真实 Gradle 构建。使用既有网络/Gradle 分发缓存，以及本工作树独立的每轨道依赖缓存；不是冷机器验收。

首轮在 **1 小时 12 分 25 秒**后结束，**6 个通过、2 个失败**。通过的是 Fabric 26.2、NeoForge 26.2、Fabric 26.1.2、Fabric 1.21.1、Fabric 1.20.1、NeoForge 1.20.1。原始 XML、各轨道日志及机器摘要保留在 `output/p263-02/eight-golden-initial/`，已归档到稳定证据目录。

NeoForge 26.1.2 在下载 `lwjgl-freetype-3.4.1-natives-windows-arm64.jar` 时发生 TLS 握手中断；后续独立下载成功，并匹配已校验 Mojang 26.1.2 元数据提供的大小与 SHA-1。未降低 TLS 设置或修改 Gradle 缓存。

NeoForge 1.21.1 首轮在 Gradle 同步阶段没有发现已捆绑的 JDK 21，自动下载的工具链解包后不含有效 Java home，未进入最终模组构建。测试夹具将 1.21.1 的 Gradle Java 选择改为已有的 JDK 21，与 1.20.1 的显式选择方式一致；产品运行时和全局配置没有因此修改。

**三个定向复跑全部通过**：NeoForge 26.1.2、NeoForge 1.21.1，以及受夹具 Java 选择调整影响的 Fabric 1.21.1。按精确生成器 ID 取最后一次实际结果，原八个生成器全部构建通过。复跑没有重写首轮失败记录；逐次 XML 与完整日志在 `output/p263-02/retries/`，最终八项映射在 `eight-golden-final-summary.json`。

生成器的 Gradle 同步会准备实际 Minecraft 类库，不能用日志暂停判定任务已经终止。只在确认相应单元已结束且 Gradle 状态为 IDLE 后，关闭本工作树该单元自己的后台 daemon，避免多个已完成单元累积占用内存；当前构建没有因此重启。

证据位于 `output/p263-02/`：`initial/`、`final/`、`verified/` 保留各次 XML 与日志；`focused-summary.json` 按测试身份合并；`current-source.patch`、`source-snapshot/` 与 `source-receipt.json` 固定 Java 源码；`eight-golden-builds.log` 保存完整批次输出。实时日志为 `.tmp/gradle-external/a97ba75cb05f4557af7c0e5f8ad8235f.log`。

本票已完成代码自查并可由 06 在共同工作树中消费。`done` 表示本地实现与所需验证完成，不表示提交、主线合并或发布；结论不包含玩家客户端、安装包或 26.3 产品支持。
