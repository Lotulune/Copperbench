# Stage 17 嵌套过程引用验证

接续 [v27 引用存在性与目标类别验证](stage-17-references-2026-09-23.md)。v28 修复嵌套过程引用漏报，Windows 导出 EXE 与 Ubuntu 安装产品的公开入口复验均通过。**本轮推进 G17-A/B，PRD17 四个总门禁仍开放。**

## 修复范围

[WorkspaceReferenceIndex](../../src/main/java/dev/copperbench/references/WorkspaceReferenceIndex.java) 按实际存储类型递归检查公开字段、数组、集合和映射中的 Procedure，识别已注册 GUI 组件的 `type/data` 结构。物品 `customProperties`、动画 `condition`、按钮 `onClick`、槽位回调及具名条件不再因为位于嵌套结构而漏报。继承字段也参与检查，静态和 transient 字段跳过。

固定布尔、文本、列表和返回值不作为过程引用；已消费的类型化值只从私有扫描副本中移除，避免约定键扫描把属性映射键名误认成引用。原元素和 `fields` 兼容副本保持不变。诊断保留精确 JSON Pointer，含 `/`、`~` 的键名正确转义；定位动作与诊断路径一致。新增目标后，未修改的调用方会重新解析。

这是引用存在性／目标类别检查，不是保存时全面语义拒绝。返回类型、触发上下文、任意第三方自定义适配器、生成／编译和游戏行为仍需独立验证。未注册 GUI 组件不会被猜测成已知组件；本轮的注册组件证据来自第一方组件，不代表全部社区插件已通过兼容认证。[公开工作流](../stage-17-agent-workflow.md)同步说明覆盖范围。

## 源码回归

- [WorkspaceReferenceIndexTest](../../src/test/java/dev/copperbench/references/WorkspaceReferenceIndexTest.java) 新增两个反例，覆盖嵌套映射／动画、GUI 回调／继承字段、错误目标类别、固定文本、别名去重、查询不修改输入和路径定位。修复前 8 项中 2 项失败；原日志与 XML 为 `.tmp/stage17-nested-references-red.log`、`.tmp/stage17-nested-references-red.xml`。修复后 8 项全部通过。
- [Stage17NestedReferenceContractTest](../../src/test/java/dev/copperbench/core/workspace/mcreator/Stage17NestedReferenceContractTest.java) 在 Fabric／NeoForge 的 1.20.1、1.21.1、26.1.2、26.2 八轨创建真实物品与 GUI，核对引用诊断／健康计数、关闭重开、补建目标后消除诊断，以及查询和目标创建不重写调用方定义。八轨全部通过；不计为八轨玩法验收。
- 完整 `dev.copperbench.core.*` 与 `dev.copperbench.references.*` 回归：**259 通过、5 条件跳过、0 失败／错误**，4 分 35 秒。证据 `.tmp/stage17-nested-references-core.log`、`-summary.json`、`-xml/`。
- 随后显式开启规模门禁，补跑其中两项规模测试，均通过。两次运行共 **261 个不同测试通过，3 个冷工作区探针未执行**。各规模用例为 2,000 元素／10,000 引用；索引首次 92 ms、重复 48 ms、20 次采样 P95 为 33 ms；健康首次 213 ms、重复 75 ms。这是已有 Core 规模冒烟场景，不是嵌套组件专项性能或固定硬件 UI 认证。证据 `.tmp/stage17-nested-references-scale-verified.log`、`-scale-xml/`、`-scale-metrics.json`、`-health-scale-metrics.json`。

真实工作区测试初轮错误地将默认 `minecraft:barrier` 贴图也计入“两个过程引用”的预期总数；保留 `.tmp/stage17-nested-references-contract.log`，修正为总边数 3、其中明确断言默认资源边 1，未放宽过程诊断断言。规模测试首条命令因 PowerShell 拆分未加引号的 `-D` 参数而在选择任务阶段失败；`.tmp/stage17-nested-references-scale.log`保留原失败，正确引用参数后才完成测试。

## v28 固定候选

| 项目 | 结果 |
|---|---|
| 冻结源码 | `D:/Hyper-V/Stage17-Linux/source-candidate-v28`，27,165 文件；提交与 dirty 状态见 `source-manifest-v28.json` |
| 构建 | `exportWin64 prepareDebLinux64 --offline --max-workers=1` 成功，1 分 46 秒；`.tmp/stage17-v28-build.log` |
| 两平台主 JAR SHA-256 | `ad62c7a4308a93824590478c0a8c0d7597f6967deda0dece6974e3fcf3b018e3` |
| Ubuntu 开发 deb SHA-256 | `af81f75c01240428ad480af6dcbb7841fb3e0b54c886f529b03257a6211cff99` |
| 包内容核对 | 22 个随包文件、24 条文档链接、640 次 Schema 引用通过；两平台主 JAR／生成器 ZIP 一致，冻结源文件未变化 |
| 包核对证明 | `D:/Hyper-V/Stage17-Linux/candidate-v28-proof.json` |

冻结的是非干净工作树快照，未提交或发布。Windows 使用导出包 EXE，不代表 Windows 安装器认证。本文及 PRD 进展链接在候选验证后更新，属于验收记录；候选中的公开工作流已经包含本轮接口范围说明。

## 双平台公开入口

[公开探针](../../scripts/stage17-installed-reference-probe.py)只使用启动器、随包 SDK 和全新示例副本。保留 v27 的检查，并增加嵌套映射／GUI 的路径诊断、固定标签不误报、健康计数、定义字节不变、未解析引用重开后保持，以及新增目标后不重写调用方即可解析。每个平台 **9 组检查通过，最终 revision 11，再次重开保持结果**。

Windows 证明：`.tmp/stage17-v28-reference-proof.json`，日志 `.tmp/stage17-v28-reference-probe.log`。

Ubuntu 先通过独立候选目录完成同一探针，未更改已安装 v27；随后用户在当前任务明确授权替换该专用测试 VM 中的开发包。安装前重新核对获批 deb 哈希，通过 `/usr/bin/copperbench` 与 `/opt/copperbench/sdk/python` 在新副本再次通过探针。安装时间为 `2026-09-23T11:38:26Z`；原共鸣工坊模型、JSON、PNG 和 Mod JAR 四个文件哈希前后一致。独立目录结果与实际安装结果分别保存，不互相替代。

Ubuntu 原始证据 `/home/stage17/evidence/v28/` 已归档至 `D:/Hyper-V/Stage17-Linux/guest-evidence-v28/`，包含 `preparation-proof.json`、`portable-reference-proof.json`、`installation-proof.json`、`installed-reference-proof.json`、安装日志及两份测试副本。Ubuntu IPv4 连接超时，改用原有固定主机密钥校验的 IPv6 连接；未修改网络设置。

探针与冻结源码、Ubuntu 实际执行脚本 SHA-256 一致：`88519a0b85c3bf6f0761f509c010f2305614fcda40a7fabd76d81269dbf6c251`。汇总 `.tmp/stage17-v28-final-proof-summary.json`。结束时两平台无遗留产品 API 进程。本轮没有 Minecraft 输入或 Jev 调用。

## 仍需完成

返回值／触发上下文合同、第三方扩展兼容、UI 六场景及英文／缩放／真实 JCEF、同源最终候选连续建模／游戏闭环、3 次独立试作及复评分仍需继续。已有其他候选的游戏与建模证据保持原归属，本轮没有将其改记为 v28 全面通过；`product-status.json` 和发行状态未推进。
