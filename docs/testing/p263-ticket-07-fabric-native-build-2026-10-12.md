# Ticket 07：Fabric 26.3 原生工程

日期：2026-10-12。状态：**done（本地原生工程资格）**；尚未提交、合入或发布。

独立插件固定 Minecraft 26.3、Fabric Loader 0.19.5、Fabric API 0.162.0+26.3、Loom 1.17.21、Gradle 9.6.0 与 Java 25。仅提供原生基础工程，没有引入未验证的旧版元素模板。公开支持阶段仍为 registered。

域层真实创建及构建已通过：`output/p263-07/backend-79b519d4-fae0-4a52-b433-e0d9cee0c8d6`。JAR 内的 fabric.mod.json 精确声明 26.3。使用调用者指定的 `C:/Users/Administrator/.gradle` 既有缓存；不宣称冷缓存下载通过。

首轮失败记录保留于 `output/p263-07/backend-first-results`：默认产品缓存下载 Fabric API 超时，以及 failsafe YAML 字符串 `false` 未被新配置读取识别。后者已修正，并验证旧 Fabric 26.2 仍要求源码索引。

## 实现与保护

- [独立插件](../../plugins/generator-fabric-26.3/fabric-26.3/generator.yaml)使用 common-only ModInitializer；基础源文件保持生成归属，额外 Java/JSON 保持手写归属。
- [配置](../../src/main/java/net/mcreator/generator/GeneratorConfiguration.java)与 [Generator](../../src/main/java/net/mcreator/generator/Generator.java)在创建、重开和显式刷新时一致处理原生插件的源码索引退出。旧生成器默认仍启用索引。
- [预览能力](../../src/main/java/dev/copperbench/tracks/PreviewCapabilityCatalog.java)仅开放创建、已有源码编辑、校验、生成、构建和本地专用服务器；客户端、GameTest、导出和结构化字段继续关闭。选择不等于权限或 EULA 同意。旧 Swing 创建/切换列表不提供新的预览目标。
- [创建服务](../../src/main/java/dev/copperbench/core/workspace/WorkspaceCreationService.java)在目录写入前检查插件和工作区 Java 25；普通未协商入口继续拒绝预览。
- 手写源码保存归入既有 `edit` 授权范围。Python/TypeScript SDK 只向适用操作附加授权，撤回和取消仍走既有控制规则。
- Windows 深路径的专用服务器复用 [隔离执行目录选择](../../src/main/java/dev/copperbench/generator/GameTestRunDirectory.java)。工作区保留实际执行位置与源清单；过深或在工作区内部的候选缓存被拒绝。普通路径与 Linux 的布局不变。

## 最终真实 Native 结果

[运行入口](../../src/test/java/dev/copperbench/headless/Native263DeliveryMain.java)启动真实 `net.mcreator.Launcher`；[SDK 验收](../../sdk/python/native_263_delivery.py)没有模拟构建、任务或 Minecraft 进程。最终运行是 `delivery-14513263992078933578`，耗时 **150.821 秒**。

3 个产品进程 PID 为 **23588、41632、42276**，均以退出码 0 关闭。新工程两个会话的 workspaceId 均为 `a6e4ea59-60a0-4397-b25a-4c13ec25e96c`；新进程必须重新选择预览，旧构建任务可恢复。

| 实际步骤 | 结果 |
| --- | --- |
| Native/SDK 新建精确 Fabric 26.3 工程 | committed，真实写入独立 .mcreator |
| 编辑 Java 与 JSON | committed；JSON 未知嵌套字段保留 |
| 构建与重生成 | succeeded；JAR 含手写类、JSON 和精确 26.3 元数据 |
| SDK 等待超时与显式取消 | NATIVE_TASK_TIMEOUT 后任务仍在；取消终态为 cancelled |
| 专用服务器 | 初始化与 Minecraft 实际就绪均出现；回环地址、随机端口；检查后关闭 |
| 新进程重开与再次构建 | 原生文本、手写归属与任务记录保留；再次构建 succeeded |

拒绝门禁包括无选择、旧 API 绕过、伪造 `userApproved`、旧修订、外部源码摘要冲突、生成文件写入、无服务端授权，以及新进程未重新选择。旧修订响应保留真实 changedPaths。19 份实际 preview_targets/1 响应通过 schema 校验。

最终 JAR `native_sdk_probe-1.2.3.jar` 的 SHA-256 为 `8efd3e0bd3e8ab652b324f49928becd3a0bf370ec07b7b1404066a723a158a98`，已从最终磁盘文件复核。前一完整通过运行 `delivery-2779682930167974827` 产生相同摘要；最终重跑补齐逐进程退出码断言。

Java/JSON 的首次文件创建是明确记录的外部原生创作准备；后续编辑、冲突处理和持久化通过实际 SDK。当前 `update_workspace_file` 不提供新文件创建操作。

## 专用服务器与 EULA

用户已在本任务中明确接受 Minecraft EULA，仅用于本任务临时测试服务器。验收服务只绑定回环地址并在就绪检查后关闭。授权测试使用独立临时配置目录，不能作为生产界面发放授权的验收结果。

独立真实服务器探针先在没有 EULA 的工程运行，确认未就绪；随后在用户已同意的临时工程中运行，要求初始化标记和 `Done ... For help` 同时出现并稳定保持。实际进程超时返回 124。SDK 流程进一步验证：调用者自填 EULA/approval 字段仍被拒绝，只有相应的已确认测试授权才能启动服务。所有测试授权在结束后撤销，未更改用户原有授权。

## 回归与失败记录

21 个类的常规定向检查最终 **116 项通过**；2 个显式 opt-in 域层构建/服务器用例另行真实运行通过。按 class/name 合并最后一次实际执行，共 **118 个不同用例通过**；跳过标记不被计为执行，所有尝试保留在 `java-summary.json`。包含旧八轨道受管任务/服务器保护、源码归属/冲突、Native/MCP/JCEF、授权、旧创建界面、LF/执行权限辅助方法。POSIX 权限断言按文件系统能力执行；本 Windows 结果不代表 Linux 运行验收。

另有 **98 Python SDK、18 TypeScript SDK、41 UI-Core、30 UI 测试**通过。UI 构建、中英文词条检查、Javadoc、Markdown 链接及差异空白检查通过；adapter 的 Native/MCP 回执也通过 schema。

保留的失败及修复：

- 首次下载超时与 YAML 字符串读取问题，详见前文。
- 首次 Native 重开触发旧源码索引导入；创建后源码编辑因缺少 edit 授权映射失败。均已修复并通过真实流程。
- 第二次 Native 在旧修订处实际正确拒绝，验收脚本错误预期了另一层的错误码；已按原始 Core 响应纠正断言。
- 第三次 Native 深路径服务器无法访问 wrapper JAR；已修复执行位置并在同类深路径下重验通过。
- 一项旧离线偏好进程测试遇到 Windows CreateProcess error=5。相同源码、相同测试原样定向重跑通过；未降低保护或跳过断言。

稳定证据目录：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-07`。保存原始回执、失败与成功日志、XML、实际 JAR、服务器日志/位置清单及源码快照，排除依赖缓存、玩家世界和私有签名密钥。

本票只确认本地原生创建/构建/重开资格。公开安装包、Ubuntu 和玩家客户端验收由后续 tickets 单独执行；公开支持阶段没有提升为 native_preview。
