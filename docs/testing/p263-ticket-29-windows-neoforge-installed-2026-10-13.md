# 29：Windows NeoForge 安装 API 验收

日期：2026-10-13（日本时间）。状态：**done；Windows NeoForge 安装 API 单元通过**。

最终 v5 已通过全新空缓存与绑定暖缓存闭环，包括六项必要业务、十二个拒绝门禁、每轮三次进程重连，以及当前/历史便携包的独立离线核验。测试授权均已撤销。本票只完成安装 API 单元；玩家客户端由 33 号验收，正式支持声明保持关闭。

## 最终 v5 结果

候选：`sha256:a90e1fd9e1381ddec64cecb5f75eeadf101a0adc33ecb5f76f1311722783fb4a`。Windows NSIS/ZIP 构建 7 分 18 秒，构建前后源码身份一致。源状态为 `dirty=true`，`formalSupportClaim=false`。解包后 4,634 个文件逐一核对，文件系统压缩前后摘要一致；运行使用该安装包的启动器、JDK、SDK 与生成器。

| 验收 | 冷缓存 | 暖缓存 |
| --- | --- | --- |
| 安装闭环 | passed，744.393 秒 | passed，280.211 秒 |
| 缓存条件 | 初始依赖缓存 0 文件 | 逐项核对冷轮保留的 8,286 个载荷 |
| 必要业务 | 6/6，框架项另计 | 6/6，框架项另计 |
| 拒绝门禁 | 12 项通过 | 12 项通过 |
| Native 验收进程 | 3 个不同 PID，均 exit 0 | 3 个不同 PID，均 exit 0 |
| 当前/历史导出离线核验 | 均通过，篡改包被拒绝 | 均通过，篡改包被拒绝 |
| 临时授权 | 已撤销 | 已撤销 |

两轮模组 JAR 均为 `59cb2a585035747188778325006a2fb91b1459e95da9cff64a74bcf4c6cd5e42`。最终 evaluator 将 `install:windows:neoforge` 判为 passed，单元回执摘要为 `0394f3a88c6a4a3b39fe4e3ee1e969b262be2bb231b40e9faab7efe01de0a0e1`。同候选客户端仍为 not_run，因此候选整体及正式支持资格不能标为通过。

原始运行在 `output/p263-29/installed-v5-048dda3a/`，冻结候选在 `output/p263-29/windows-candidate-v5/frozen/`。独立留存位置为 `C:/CB263/evidence/ticket-29/`，包含成功/失败原始记录、候选、校验清单和本报告。冻结包使用同卷硬链接保留，避免再占用约 6.8 GB；日志与回执独立复制，旧位置也保留，记录内容与候选 ID 均未改写。

网络条件是本任务显式 JVM 代理 `127.0.0.1:3067`，并非默认直连保证。授权签发采用安装代码内的隔离 fixture，不代表桌面签发 UI 验收。此次运行使用 portable ZIP 解包，未将其描述为 NSIS 安装流程验收。28 号 Fabric 的候选与归档保持原身份。

## 已修正的问题

NeoForge 与 Plugin Portal 为 `net.neoforged.moddev` 2.0.148 发布了内容等价、字节不同的 marker POM。独立下载并比对 GAV、唯一依赖后，在精确坐标上保留原 Portal SHA，增加 NeoForge Maven 的已核实 SHA。继续从空缓存运行时发现五个 BOM `.module` 缺少校验项；独立 Maven Central 下载与缓存字节一致，四个有公开 SHA-256，Spring BOM 5.3.39 的公开 SHA-256 返回 404，公开 SHA-1 匹配。新增项仍固定本地 SHA-256，未关闭严格验证或添加泛化信任规则。

两份 verification metadata 与 environment lock 已同步。原始 POM、module、公开校验结果与请求回执在 `output/p263-29/marker-artifacts`、`module-artifacts-reviewed`。修复后隔离探针真实构建成功；该运行复用了失败缓存，仅作为修复验证。

[WorkspaceCreationService](../../src/main/java/dev/copperbench/core/workspace/WorkspaceCreationService.java)仅对 Windows 下待回滚工作区内、非链接的普通文件清除 DOS readonly 后删除。原故障是 JGit loose objects 的只读属性阻止回滚。相关回归及错误详情脱敏测试 2 项通过，外部只读文件保留；本次诊断重放的创建失败也没有再次报告 `WORKSPACE_CLEANUP_FAILED`。

另一个可稳定解释的失败是 `downloadAssets` 默认请求 Java 21，但精确 26.3 创建流程只暴露 Java 25。检查锁定的 ModDev JAR 公共 task 属性后，[生成器模板](../../plugins/generator-neoforge-26.3/neoforge-26.3/workspacebase/build.gradle)与[环境探针](../../tools/generator-probes/neoforge-26.3/build.gradle)显式将该任务的 `javaExecutable` 绑定到工作区的 Java 25 toolchain。修改仅限 26.3 的资产下载任务；严格依赖校验保持开启。

真实 `downloadAssets` 验证已通过：**1 分 32 秒，5,147 个文件、461 MiB**。运行打印并校验实际 Java 路径和 release 25；日志为 `output/p263-29/assets-java25-probe.log`。两份 lock 的探针源码摘要和资产下载 Java 说明已更新，9 项探针边界测试通过。

`NeoForge263NativeWorkspaceTest` 本轮 3 项通过、2 项 opt-in 完整运行测试未执行，XML 保存在 `output/p263-29/native-template-tests-passed`。源码摘要、各自依赖图摘要与两份 verification metadata 字节一致性核对通过；插件 lock 原有 DevLaunch runtimeClasspath 差异按 08 号记录保留。未把两份职责不同的 lock 强行改成相同内容。

## v3 候选与失败记录

候选：`sha256:7897088ea8970de9e8f0ff94a2fdaf378c4c0b6f6ddbbb1605cf93cca2fa3bb9`。

该候选包含 POM/module 校验与回滚修正，**不包含后来新增的资产下载 Java 修正**。冻结目录为 `output/p263-29/windows-candidate-v3/frozen`，安装目录为 `C:/CB263/p263-29-v3-b673c0/Copperbench014`。候选为 `dirty=true`，`formalSupportClaim=false`。

| 运行 | 结果 | 耗时 |
| --- | --- | --- |
| `installed-cold-c0ab5798` | Native 创建期间输出流关闭 | 397.997 秒 |
| `installed-cold-dae1eb6d` | 同类退出再次出现；EXE 返回 0，但创建无成功回执 | 374.046 秒 |
| `installed-cold-26957a2a` | 明确标记 `diagnostic_replay`，复用失败缓存；返回资产下载 Java 21 不可用 | 157.895 秒 |

前两次的最终 Gradle 日志记录了客户端断连后取消构建，未定位到产品进程先退出的根因；不能把后续发现的 Java 21 问题当作其已证根因。第三次增加了子进程退出与内存采样，源码转换、7,301 个源码的重编译已完成，随后在 `downloadAssets` 的 toolchain 解析处正常报告失败。物理内存一度紧张，但没有证据把前两次退出归因于内存耗尽。

三次原始结果、最终 daemon 日志、进程诊断与失败回执均保留；任务授权全部撤销。没有把失败缓存记录为成功冷运行，也没有启动正式暖缓存验收。28 号的 Fabric 候选及归档保持原身份。

## v4 候选与外部脚本修正

v4 候选：`sha256:4310f1c265b3899bfa81baba7836e1bde5a9c3d2ce39bb102e809c45cd1f33fd`。Windows NSIS 与 ZIP 构建耗时 7 分 1 秒，构建前后源码身份一致。安装包已解包到 D 盘并通过资产摘要核对。冻结目录为 `output/p263-29/windows-candidate-v4/frozen`，仍为 `dirty=true`、`formalSupportClaim=false`。

首次运行 `installed-v4-8699fd87` 在 `bootstrap list-generators` 阶段失败：启动器返回 `3221225477 / 0xc0000005`，stdout/stderr 为空，尚未创建目标工作区。撤销 CLI 同样崩溃。原始 `installed-run.json` 保留该失败；随后通过同安装包的 JDK 与 `TaskAuthorizationStore.revoke` 明确撤销本次 grant，独立回执为 `cold/authority-recovery.json`，其中 `revoked=true`、`active=false`。未把原失败记录改成通过。

对同一安装目录和代理配置的对照表明，双引号包围的 Windows `JAVA_TOOL_OPTIONS` home 值会触发当前启动器崩溃；不带引号通过，JVM 单引号形式也通过。单引号形式同时通过带空格 home 的实际 Java 属性核对和安装启动器发现。对照日志与脚本保存在 `output/p263-29/launcher-diagnostic-v4/`。这定位了此次脚本传参故障，不说明 v3 创建期间退出的根因已经确定。

[外部安装脚本](../../tools/candidate-evidence/installed_acceptance.py)已修正 Windows 引号形式，保留非 JSON 输出时的真实退出码与原始流，并保证清理失败不覆盖主错误或跳过原始日志收集。若启动器撤销失败，安装 Java API 尝试撤销同一授权，但该轮仍为失败。发现阶段移到签发之前，减少启动器早期故障遗留的授权。新增独立缓存路径支持；暖运行仍必须消费同候选成功冷缓存。

脚本与候选工具 **37 项测试通过**，其中安装边界 15 项；日志为 `output/p263-29/harness-boundary-tests-v4.log`。Markdown 链接及差异空白检查通过。这些单测不代表安装闭环通过。

重跑 `installed-v4-revised-28b42ba4` 复用已核对的 v4 产品，缓存为全新的 `C:/CB263/cache-29-v4-28b42ba4`，工作区位于 D 盘项目 output。它记录冻结脚本摘要 `f0a776cd…deac` 与实际外部脚本摘要 `f3d20b74…034c`，保存脚本/校验器快照；候选 ID、包及产品资产未改变。旧失败缓存未复用。只有冷轮全部成功后，才执行绑定暖轮和单元 evaluator。玩家验收另由 33 号完成。

该冷轮在 **457.469 秒**后失败。Native 创建、只读检查、首次构建、受控编译失败及显式修复构建已通过；六项宿主任务在执行业务前因 `downloadAssets` 请求 Java 21 失败。授权经正常启动器撤销，`authorityRevoked=true`，没有清理错误；暖轮未执行。

此次进一步修正 [GameTestSupport](../../src/main/java/dev/copperbench/generator/GameTestSupport.java) 的独立宿主以及 [NeoForge 后端](../../src/main/java/dev/copperbench/generator/neoforge/NeoForge1211Generator.java) 的 26.3 生成路径，仅对 26.3 显式绑定资产任务到 Java 25。相关 Java 回归 **25 项通过，2 项 opt-in 未执行**，包含旧 NeoForge 轨道的生成回归；日志和 XML 为 `output/p263-29/host-java25-focused-tests.log` 与 `host-java25-focused-tests-passed/`。

使用当前编译的宿主生成器，从失败轮原始工作区快照创建了全新诊断宿主，复用原缓存并加载原模组 JAR。只提供安装包 Java 25、关闭 toolchain 自动发现，实际资产 Java 路径断言通过；**6 项必要业务全部通过**（另有 1 项框架项），31.483 秒结束，进程 exit 0，JAR 摘要 `59cb2a585035747188778325006a2fb91b1459e95da9cff64a74bcf4c6cd5e42` 保持一致。结果位于 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/host-java25-diagnostic-74b8a5ee/`，调用与源码摘要在 `output/p263-29/host-java25-diagnostic-invocation.json`。这是复用失败缓存的源码修复诊断，不是 v4 安装通过证据；产品修正需要新冻结候选再跑冷、暖闭环。

## 空间整理与执行条件

首次空间检查时 C、D 盘各剩约 2.9 GB，新候选约 2.29 GB、独立安装约 1.93 GB，还需空缓存与原始证据空间。用户随后明确授权清理本项目构建与虚拟机占用；再次删除四个可重建缓存仍被自动审批以 `blocked by policy` 拒绝，缓存未删除。

被拒绝的清理目标为 `C:/CB263/run-29-25ba3429/gradle-cache`、`run-29-1646fca0/gradle-cache`、`run-29-d04179fd/gradle-cache` 和 `p26329-probe-a1/gradle-cache`，后三者也均位于 `C:/CB263/`。此前 D 盘失败归档临时目录的清理也仍被自动审批阻断。未改用其他工具绕过拒绝。

按用户授权，对已关闭的两台项目虚拟机的当前差分盘进行了只读挂载下的 `Optimize-VHD -Mode Full`。Windows 盘从 30,621,564,928 bytes 缩至 29,010,952,192 bytes，回收 **1,610,612,736 bytes**；Linux 盘没有可回收块。两者的虚拟容量、磁盘标识、父盘路径保持一致，`Test-VHD` 通过并已卸载，虚拟机仍为 Off。检查点和分区未调整。操作依据为 [Microsoft Optimize-VHD 文档](https://learn.microsoft.com/en-us/powershell/module/hyper-v/optimize-vhd?view=windowsserver2025-ps)，原始操作回执与抓取内容保存在 `output/p263-29/storage-recovery/`。

首次压缩后 C 盘约 5.68 GB、D 盘约 4.44 GB 空闲，用于 v4 构建和安装。后续对两个 VM 的其余父盘、Linux 中间差分盘及本项目 data VHDX 也执行了只读挂载下的 Full 压缩，没有额外回收空间；所有磁盘已卸载，链验证通过，虚拟机保持 Off，检查点未删除。

已冻结候选的重复 `build/export` 临时产物也尝试清理，自动审批仍以 `blocked by policy` 拒绝，未删除。详细压缩回执为 `output/p263-29/storage-recovery/remaining-compaction.json`。重跑前 C 盘约 2.31 GB、D 盘约 2.51 GB 空闲；复用现有安装包，将依赖缓存与临时工作区分盘，避免新增一套安装副本。

Ubuntu 30–31 的可执行准备与验证范围见[云端验收准备报告](p263-tickets-30-31-ubuntu-installed-preparation-2026-10-13.md)。提交/推送授权和 Windows Minecraft MCP 连接仍待用户回复。

随后对六个本任务相关的旧安装副本或构建目录执行 Windows `compact /C /S /EXE:LZX`，所有文件压缩前后逐项核对字节数与 SHA-256，观察到额外释放 **3,171,561,472 bytes**。加上 VM 压缩为 **4,782,174,208 bytes（约 4.78 GB）**。原始回执和完整文件清单位于 `output/p263-29/storage-recovery/`。新 v5 安装目录另外通过同样的内容校验压缩节省约 0.54 GB；已完成的诊断与验收工作区也保留文件后压缩。此前被拒绝的删除操作未执行，未删除虚拟机检查点。
