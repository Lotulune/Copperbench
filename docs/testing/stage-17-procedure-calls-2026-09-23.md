# Stage 17 过程调用生成链验证

接续 [v28 嵌套引用验证](stage-17-nested-references-2026-09-23.md)。本轮发现并修复两个真实生成阻断：UUID 引用虽然能被索引解析，却被上游生成器当作不存在的过程；任务预检仍要求已经不在公开过程合同中的旧 `message` 字段。**v30 已完成双平台候选的生成／构建／重开，以及 Windows Fabric 1.21.1 开发客户端的实际调用与正常退出；PRD17 四个总门禁保持开放。**

## 反例与修复

[Stage17ProcedureCallGenerationTest](../../src/test/java/dev/copperbench/core/workspace/mcreator/Stage17ProcedureCallGenerationTest.java) 在真实工作区分别创建按名称和 UUID 调用的过程，并使用实际上游 Blockly 生成器检查代码。修复前八轨均失败：索引无引用诊断，但编译备注为 `Procedure call block is calling nonexistent procedure <UUID>`，目标调用未生成。证据 `.tmp/stage17-procedure-call-red.log`、`.tmp/stage17-procedure-call-red.xml`。

[WorkspaceProcedureTargets](../../src/main/java/dev/copperbench/procedure/WorkspaceProcedureTargets.java) 为 `call_procedure` 提供只读身份解析，将稳定 UUID 转为模板要求的真实内部名称，同时支持导入元素的派生身份。目标必须唯一且为 Procedure；名称提示不能覆盖非空稳定身份，身份缺失时也不回退到提示名称。[ProcedureCallBlock](../../src/main/java/net/mcreator/blockly/java/blocks/ProcedureCallBlock.java) 不再凭 XML 字段顺序选择目标。[ProcedureIrCodec](../../src/main/java/dev/copperbench/procedure/ProcedureIrCodec.java) 的校验与依赖提取共用相同选择规则，旧 XML 的 `procedure` 名称形式以及空 `procedureId` 的兼容形式不再误报必填。

八轨修复后均断言：按名称和 UUID 生成相同的调用代码；过期名称提示不改变绑定；缺失 UUID 不能回退到一个真实但不同的名称；关闭重开结果一致；读取／编译不改调用方定义；导入身份可解析且显示标签不作绑定。该项不覆盖其他具名 Procedure 字段或返回值调用块的 UUID 生成，也不替代返回类型／触发上下文校验。

v29 导出包的公开探针随后暴露第二个问题：两个合法过程已提交，但 `generate_workspace` 在任务预检阶段返回 `FABRIC_PROCEDURE_MESSAGE_REQUIRED`。原失败工程保留于 `D:/Hyper-V/Stage17-Linux/procedure-call-v29/windows`，回执与原输入哈希位于 `output/minecraft-validation/stage17-v29-call-runtime/`，日志 `.tmp/stage17-v29-call-runtime.log`；原工程输入未变，该候选未进入游戏。

[Fabric1211Generator](../../src/main/java/dev/copperbench/generator/fabric/Fabric1211Generator.java) 现在对真实 XML/IR 正文执行过程图校验，不要求旧日志示例的 `message`。仅旧版无正文示例沿用原日志字段规则。无插件工作区的生成／迁移路径拒绝将真实正文替换成日志占位代码；真实插件工作区继续由其模板生成。NeoForge 的共用预检和备用生成路径也受此规则约束。[八轨任务层回归](../../src/test/java/dev/copperbench/generator/Stage17ProcedureTaskValidationTest.java)覆盖合法正文、不生成占位源码及既有插件源码保留。

## 源码回归

| 范围 | 结果 | 证据 |
|---|---|---|
| Core、引用、过程，UUID 修复后 | 274 通过、6 条件跳过、0 失败／错误；4 分 33 秒 | `.tmp/stage17-procedure-call-regression.log`、`-summary.json`、`-xml/` |
| 三个显式规模测试 | 3 通过：过程 500 节点、引用／健康各 2,000 元素及 10,000 引用 | `.tmp/stage17-procedure-call-scale.log` |
| 任务预检修复后：八轨调用生成、八轨任务校验及两加载器生成器回归 | 25 通过、0 跳过、0 失败／错误 | `.tmp/stage17-procedure-task-regression-verified.log`、`-summary.json`、`-xml/` |

这些是分步回归，部分用例重复，不相加冒称互不重叠的最终候选全量测试。三个冷工作区探针未在本轮执行。新增任务测试初版误把 `Profile` record 当成 enum 使用 `values()`，只在测试编译阶段失败；修正为实际静态 profile 列表后才执行成功，保留 `.tmp/stage17-procedure-task-regression.log`。没有通过修改产品断言来消除该测试代码错误。

## 固定候选与 Windows 公开入口

- v29 冻结 27,168 文件，主 JAR 为 `18330bff64dc8cbc9deb5c73666154471cac0367999d757baa8ea9c8158ad093`。保留其包内容核对和任务失败证明，不标为运行通过。
- 修正任务预检后冻结 v30：`D:/Hyper-V/Stage17-Linux/source-candidate-v30`，27,169 文件，来源提交与 dirty 状态见 `source-manifest-v30.json`。未提交或发布。
- `exportWin64 prepareDebLinux64 --offline --max-workers=1` 成功，1 分 11 秒；`.tmp/stage17-v30-build.log`。
- 两平台主 JAR SHA-256：`5384e1df86d3535d6d3a2da84f3857ab141f09170d003b413f08d9e7ea56e3af`。
- 22 个随包文件、24 条文档链接、640 次 Schema 引用通过，两平台主 JAR／生成器 ZIP 一致，冻结源码未变化；`D:/Hyper-V/Stage17-Linux/candidate-v30-proof.json`。

本地 Windows 探针 `output/minecraft-validation/stage17-v30-procedure-call.py`（原始验证文件未随源码发布）只调用导出 EXE 与随包 SDK，在原建模测试工程的新副本 `D:/Hyper-V/Stage17-Linux/procedure-call-v30/windows` 创建两个过程。目标通过 `text_print` 输出唯一标记 `PRD17_V30_UUID_CALL_REACHED`，没有外部触发器；调用方使用 `mod_serverload`，其调用块携带真实目标 UUID 和故意过期的名称提示。未手写 Java 替换被测调用。

公开生成任务 `0d1aba2a-7917-47e4-9c3c-ff44f1a285de` 成功，生成 37 个文件；构建任务 `fe14e12c-14f8-4996-aacc-8f722d3f3597` 成功，Gradle 用时 57 秒。探针核对实际调用源码指向 `stage17_targetProcedure.execute(...)`，不包含 UUID 或过期提示；目标源码包含唯一标记。关闭重开后引用与这两份源码哈希保持一致。证据 `output/minecraft-validation/stage17-v30-call-runtime/` 的 `generate-final.json`、`build-final.json`、`prepared.json`。

Windows 使用导出 EXE，不代表 Windows 安装器认证。当前测试是开发工程客户端，不是独立 JAR 宿主验收。

## Ubuntu 独立候选复验

使用同源 v30 的 `/home/stage17/candidate-root-v30/opt/copperbench/copperbench.sh` 与随包 SDK，在旧 Fabric 测试工程的新副本上执行同一创建／引用／生成／构建流程。实际生成通过，初次构建在 Loom 配置阶段下载 Minecraft 元数据失败，尚未进入 Java 编译。原始 `procedure-proof/build-final.json` 和 `proof.json` 保留失败状态。

检查发现产品缓存已有 Minecraft 1.21.1 的 JAR 和版本信息，但缺少全版本清单；另一既有 Gradle 缓存中有该清单。先验证清单中 1.21.1 的 SHA-1 与两份缓存的 `mojang_minecraft_info.json` 均一致，再只新增缺失的清单文件。未覆盖缓存文件，未改代理、镜像、环境变量或网络配置。复制路径及哈希保存在 `procedure-retry-proof/cache-copy.json`。

随后从公开 SDK 重跑构建成功，关闭重开仍为 revision 7，UUID 调用仍解析到原目标，定义及 Java 源码字节不变。重试证明单列于 `procedure-retry-proof/`，不覆盖首次失败。两批原始证据位于 `/home/stage17/evidence/v30/`，已归档至 `D:/Hyper-V/Stage17-Linux/guest-evidence-v30/`。

这是独立候选目录和已有缓存条件下的生成／构建／重开，不是安装入口或 Linux 玩法认证。已安装版本仍为获批的 v28，主 JAR 哈希 `ad62c7a4308a93824590478c0a8c0d7597f6967deda0dece6974e3fcf3b018e3`；v30 deb 已准备但未安装，SHA-256 为 `71765a75563e56a20a6cb8ab58a3e5de4b694bfa97374362d5b480425ebbed18`。复验结束时 Ubuntu 无遗留产品 API 进程。

## Windows 客户端实际调用与正常退出

公开 `run_client` 任务 `2a496b8d-6a11-442e-b6cf-21b1e3599c0e` 于 `2026-09-23T12:13:05Z` 启动；客户端 PID 32368、启动时间 `12:13:52Z`，命令行明确指向上述 v30 副本的 Loom 配置与随包 Java 21。产品首先到达 `running / task.run_client.rendering`，当时不计成功终态。

`mc_doctor` 实际返回桥 0.2.0。第一次绑定会话 `0e68d9af-36b5-4071-a863-fe420e48b84c` 无法取得前台焦点，立即 detach，没有游戏输入、没有取得新游戏截图，也没有 Jev 调用。等待期间保留客户端和 SDK 会话；准备阶段不计游戏通过。

用户于当前任务恢复焦点后，正式会话 `d86b9a8a-ca8b-42fd-9989-a04ea45bfe68` 的只读窗口检查确认前台、PID 和几何匹配，使用 `focus=false` 绑定。主 LLM 检查标题画面后，一次 `mc_menu_flow` 进入复制的 `PRD17 Scene C v7`：耗时 9.007 秒、3 个本地输入批次，终态画面为实际游戏 HUD。服务器于 `12:31:48Z` 输出一次 `PRD17_V30_UUID_CALL_REACHED`，随后玩家 Player838 进入世界。目标过程没有独立外部触发器，实际生成的服务器启动过程通过 `stage17_targetProcedure.execute()` 调用它；未发送产生该标记的聊天或控制台命令。

随后一次保存／退出流程完成世界保存并点击 Quit Game。窗口在退出点击期间消失，桥返回 `blocked / WINDOW_NOT_VISIBLE`，保留退出前标题截图，没有盲目重试。独立核对日志于 `12:32:57Z` 记录所有维度保存及 `Stopping!`，PID 32368 已消失，产品任务于 `12:33:00Z` 为 `succeeded`；SDK 辅助会话自行退出。两份被测生成源码的 SHA-256 与构建后记录一致，原工程输入哈希前后相同。

主 LLM 已通过 `mc_record_verification` 记录限定范围的 `passed`，随后 detach。正式会话的 39 个清单文件哈希全部独立复核，包含 32 张实际截图。关键证据：

- 游戏 HUD：`frame-061de3c9-b6f8-4010-bbdc-848f743a4fbd.png`。
- 退出前标题：`frame-95d5de3f-bc49-4e80-8187-f157ca88c204.png`。
- 执行标记日志：`log-bf10d9ab-e4f5-46ca-9bb8-570327df22d1.txt`；保存／退出日志：`log-a625855d-88ec-4148-9690-30131f61c92b.txt`。
- 会话内 `product-client-final.json`、`product-probe-proof.json`；试验目录 `runtime-summary.json`、`proof.json`、`client-final.log`。

正式会话从 attach 至 detach 为 223.144 秒，含模型检查和留证；8 次 MCP 调用，其中 2 次菜单流程、共 6 个本地输入批次，0 次输入前拒绝，1 次退出期间窗口消失导致的中断，0 次 Jev 调用。输入、步骤与截图计时单列在 `runtime-summary.json`，不从这些数值推导性能提升。

用户另已明确授权后续失焦时先用 computer-use 点击已核对的测试窗口，再交回 MCP 检查焦点；该顺序已记录到项目指令。本次恢复由用户实际点击完成，computer-use 仅初始化并成功查询了窗口能力，未执行聚焦点击，不将其算作已实测的恢复动作。

已审查并复用英文 854×480、精确世界名 `PRD17 Scene C v7` 的既有菜单配置。其整体指纹（配置加模板图片）为 `36439b3ceb76b0f613dd5b2cf9d2b7faa81952935cab50506f473f5affad864a`，与历史记录一致。配置 JSON 本身的哈希为 `c4b2f4048726da8df7365488d9ac83042670874990b42e9bb0bf26980e49161b`，两种口径不同，并非模板变化。

## 剩余门禁

本轮已为 `call_procedure` 的 UUID 生成补齐上述 Windows Fabric 1.21.1 开发客户端实际调用和正常退出证据；没有推广为八轨运行、独立 JAR 或所有过程形式通过。返回值／触发上下文、其他过程引用形式、第三方扩展兼容、英文／缩放／JCEF、同源最终候选建模与游戏闭环、独立试作及复评分继续开放。`product-status.json` 和发行状态未推进。
