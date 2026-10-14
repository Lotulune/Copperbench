# P263-08 — NeoForge 26.3 原生创建、构建与重开

日期：2026-10-12（日本时间）。实施树：`codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`。改动尚未提交或合入。

## 交付边界

独立插件 [generator-neoforge-26.3](../../plugins/generator-neoforge-26.3/plugin.json) 使用 Minecraft 26.3、NeoForge 26.3.0.76、ModDevGradle 2.0.148、Gradle 9.6.0 与工作区 Java 25。模板只有 common 模组入口及 `META-INF/neoforge.mods.toml`，没有导入旧版全量元素模板，也不加载旧源码索引。

精确注册表、工作区创建、执行环境和任务路由使用同一 profile。Core 预览扩展仅开放 `create_workspace`、`update_workspace_file`、`validate_workspace`、`generate_workspace`、`build_workspace`、`run_server`；`supportStage` 仍为 `registered`。客户端、GameTest、数据生成、导出和结构化字段尚未开放。

UI 和 SDK 消费同一能力来源。传统 Swing 新建选择器排除两个 26.3 目标，不能绕过预览选择。目标目录写入前检查选择、权限、任务授权与工作区 Java；错误 Java 和缺失 JDK 的测试均确认目标目录没有创建。

## 依赖与服务端修复

[04 的依赖资格](p263-ticket-04-neoforge-environment-2026-10-12.md)保持独立。运行服务端时实际发现两项与纯构建不同的要求：

1. ModDevGradle 的 runs 配置把 `net.neoforged:DevLaunch:1.0.2` 加入 `runtimeClasspath`。初次服务端因锁中缺少该配置而失败。通过 Gradle `dependencies --configuration runtimeClasspath --write-locks` 解析，锁文件只有这一行配置成员变化；原有校验元数据未改动、未关闭验证。插件环境锁保留 04 的原始摘要并记录本次运行时增量。
2. NeoForge 开发运行器绕过原版 EULA 文件检查，第二次负向探针实际启动并就绪，不能记作拒绝通过。模板的 `runServer` 已补显式 `run/eula.txt` 检查。第三次真实探针确认未接受时退出码非零、未初始化、未就绪；按本任务用户的独立授权接受后才启动。

服务端配置 `--nogui`、`127.0.0.1`、随机端口和较小视距，关闭 NeoForge 局域网广播。已接受探针的日志同时包含 `COPPERBENCH_P263_NEOFORGE_INITIALIZED`、`Not loading early display in headless mode`、回环监听和 `Done … For help, type`。稳定观察后运行器关闭进程；这不是把自然退出码 0 当作就绪。另有真实 1 毫秒截止的 Gradle 任务返回 124。

初次构建和两次服务端失败均保留，最终域层结果见 `backend-and-server-third.log` 和 `xml-backend-server-third`。使用已有依赖缓存，未声称冷缓存安装。

## 真实 Native / Python SDK

[Native 验收入口](../../src/test/java/dev/copperbench/headless/Native263DeliveryMain.java)和 [Python 编排](../../sdk/python/native_263_delivery.py)按精确 loader 选择元数据与初始化标记。最终 NeoForge 运行目录：`output/p263-08/native/delivery-10162309592704468750`。

- 全流程 165.873 秒；3 个独立产品进程 PID 12076、32436、42816，均通过进程句柄确认 exit 0。
- 新工程工作区 ID：`6ebb1b6c-dc9e-4f72-93ce-a76b8d193c24`；新进程重开后保持一致。
- 构建 `fc0e461e-1595-438b-9dca-54698d363a6f`、重生成 `eb141f68-eca6-4972-b3b2-fffb1d3b5dc3`、服务端 `2352a5cf-f948-4e62-b142-4c04312865cf`、重开构建 `9494625b-afcf-4920-a621-ece4d0e94616` 均成功。
- 构建 `4b01fb15-74cd-446f-80c6-fe912f24bd38` 在 SDK 等待超时后明确取消，持久化终态为 `cancelled`。
- JAR SHA-256：`46bfe3f99ae6ec5676e79797aeb3f78446ee36b67bd093ffba31f467d53f8677`。包含精确 NeoForge/Minecraft TOML、手写 Java class 和未知嵌套 JSON 字段；不含 Fabric 元数据。
- 19 个扩展响应通过 schema。真实拒绝覆盖未选择、新会话重新选择、旧协议写入、缺少任务授权、旧修订、外部文件 SHA 冲突及生成文件只读。

Java/JSON 的首次创建是明确记录的外部原生 fixture 准备，后续修改使用 SDK；没有声称产品新增文件创建 API。手写归属、内容与未知字段经过重生成和新进程重开检查。授权测试使用隔离配置目录中的 signed grant fixture，只验证 Native 消费，不代表桌面授权 UI 已运行。服务端授权来自本任务用户确认，验收后撤销 grant。

## 验证与证据

12 类定向 Java 回归共 58 项通过，4 项真实运行用例按 opt-in 跳过。其中 NeoForge 的 2 项已在独立真实运行通过；按 class/name 合并，本票为 **60 个不同 Java 用例通过、2 个 Fabric 后端 opt-in 未在本票重跑**，所有尝试保留在 `java-summary.json`。原八轨的任务隔离、就绪规则和精确身份重开保护通过。Javadoc、98 项 Python SDK、41 项 UI-Core、Markdown 链接与保留 CRLF 的差异检查通过。

共享 Native 编排的默认 Fabric 分支也用真实进程复跑，通过创建、编辑、构建、取消和新进程重开；本次回归未重复 Fabric 服务端探针，证据保存在本票的 `fabric-native-regression`，不覆盖 07。

8 项 Playwright 检查通过，并检查 NeoForge 能力面板截图。界面测试使用明确的 Core 响应 fixture，覆盖两个加载器的选择、目录变更撤销、受保护命令和两种窗口尺寸；实际创建和构建由上面的 Native 进程完成。首次浏览器运行误连了已有的 5173 端口服务，未获得 Copperbench 页面，已保留失败并改用本任务独占端口。

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-08`。保留原始回执、日志、XML、JAR、执行位置和源文件摘要快照；排除依赖缓存、玩家世界和私有签名密钥。[07 的既有归档](p263-ticket-07-fabric-native-build-2026-10-12.md)未被覆盖。

这是本地原生创建/构建/重开资格。Linux wrapper 的 LF 与生成后的执行权限由现有方法及相关测试保护；本 Windows 运行不等同于 Ubuntu 执行验证。公开安装版、玩家客户端、六项业务 GameTest 和已验证导出由后续 tickets 独立验收。
