# Stage 17 引用存在性与目标类别验证

接续 [双平台恢复与回滚验证](stage-17-recovery-2026-09-23.md)。本轮 v27 修复工作区引用索引中的漏报、错误绑定和兼容副本重复扫描，Windows 导出 EXE 与 Ubuntu 安装产品的公开入口验证均通过。**本报告限定于下述引用语义，PRD17 尚未整体验收。**

## 实现与边界

[WorkspaceReferenceIndex](../../src/main/java/dev/copperbench/references/WorkspaceReferenceIndex.java) 现在从实际已识别的 Procedure 节点重新提取依赖，不信任调用者提供的 `dependencies` 摘要。省略摘要不会隐藏真实调用，伪造摘要不会生成虚构引用；`fields` 兼容副本合并后只扫描一次，节点内部 UUID 不作为工作区引用。

目标按内部名或稳定 UUID 及预期类别匹配。物品不能满足 Procedure 调用，同名注册表变量不能覆盖过程，显示名称不作为绑定依据。缺失、类别错误及多个同类目标分别返回 `WORKSPACE_REFERENCE_DANGLING`、`WORKSPACE_REFERENCE_TYPE_MISMATCH`、`WORKSPACE_REFERENCE_AMBIGUOUS`，未解析的 `targetId` 为 null；边新增 `resolution`。新增目标后，即使调用方没有修改也会重新解析。

元素存储类顶层声明为 Procedure 的字段也参与索引，例如物品的 `glowCondition` 和 `onRightClickedInAir`。具名对象／受支持的过程名称产生引用；布尔、文本、列表固定值、空名称及 null 不产生过程引用。

本轮没有加入保存时的全面语义拒绝，也没有验证返回值类型、触发上下文、嵌套 GUI 组件或不透明插件内部引用。资源引用仍交给独立资源检查处理；该索引中的 `external` 不表示资源存在。`stats.referenceScope` 和[公开工作流](../stage-17-agent-workflow.md)明示覆盖范围。索引无诊断不能单独证明可生成、可编译或玩法正确。

## 源码验证

[WorkspaceReferenceIndexTest](../../src/test/java/dev/copperbench/references/WorkspaceReferenceIndexTest.java) 共 6 项，覆盖空／伪造依赖摘要、别名去重、错误类型、动态解析、注册表重名、显示名不绑定、具名字段与固定值，以及导入数据的同名歧义。初次红灯运行的 4 项中有 2 项失败，证据 `.tmp/stage17-reference-semantics-red.log`、`.tmp/stage17-reference-semantics-red.xml`。

最终 `dev.copperbench.core.*` 与 `dev.copperbench.references.*` 回归 **249 通过、5 跳过、0 失败／错误**，用时 5 分 51 秒。日志 `.tmp/stage17-reference-core.log`，计数 `.tmp/stage17-reference-core-summary.json`，XML `.tmp/stage17-reference-core-xml/`。其中 3 项冷工作区探针需要显式夹具配置，2 项规模测试默认需显式开启。

随后开启 `copperbench.stage9.scale=true`，单独执行上述 2 项规模测试，均通过。每项使用 2,000 个元素、10,000 条引用；本机引用索引首次 84 ms、重复 18 ms、20 次采样 P95 为 25 ms，健康统计首次 206 ms、重复 57 ms。这是 Core 冒烟测量，不是固定硬件 UI 性能认证。日志 `.tmp/stage17-reference-scale.log`，XML `.tmp/stage17-reference-scale-xml/`，指标 `.tmp/stage17-reference-scale-metrics.json`、`.tmp/stage17-reference-health-scale-metrics.json`。两次运行合计 251 个不同测试通过，仍有 3 个冷工作区探针未在本轮执行。

## 候选与公开入口

- 冻结源码：`D:/Hyper-V/Stage17-Linux/source-candidate-v27`，27,163 个文件，来源提交与 dirty 状态见同级 `source-manifest-v27.json`。
- `exportWin64 prepareDebLinux64 --offline --max-workers=1` 构建成功，用时 1 分 25 秒；日志 `.tmp/stage17-v27-build.log`。
- Windows／Linux 主 JAR 相同：`a35b9e5f47ef3846313164628587e40f880a7b518020793e9aedddcd4aa430c9`。
- Ubuntu 开发候选 deb：`b7c699eed994f05c3473fd71757c7906a7c0cb0827ff7d41a04a99d09568ea19`。
- 包内容核对通过：22 个随包文件、24 条文档链接、640 次 schema 引用，两平台主 JAR／生成器 ZIP 一致，冻结源码未变化。证据 `D:/Hyper-V/Stage17-Linux/candidate-v27-proof.json`。

[公开探针](../../scripts/stage17-installed-reference-probe.py) 仅调用产品启动器和随包 SDK，在新建示例副本上分别执行 7 组检查：空摘要仍检出调用；物品不能满足过程调用；新增过程可修复未修改调用方且不受同名变量影响；具名字段与固定文本／显示名区分；健康计数与索引诊断一致且查询不改定义字节；改为 UUID 调用及固定布尔后正确更新；关闭重开保持结果。两平台均一次通过，最终 revision 均为 8。

Windows 使用导出包 `copperbench.exe`，不代表安装器认证；Ubuntu 使用实际安装的 `/usr/bin/copperbench` 与 `/opt/copperbench`。安装前后核对既有建模夹具的模型、贴图和 Mod JAR 哈希未变。探针与冻结脚本、两平台实际执行脚本 SHA-256 一致：`7b8796b0d13020be3db577c38d935bc2e8262f5d4fc811d698788698ca10941a`。

Windows 证明 `.tmp/stage17-v27-reference-proof.json`，日志 `.tmp/stage17-v27-reference-probe.log`；Ubuntu 原始证据 `/home/stage17/evidence/v27/` 已归档至 `D:/Hyper-V/Stage17-Linux/guest-evidence-v27/`，包含安装记录、探针证明及测试副本。汇总 `.tmp/stage17-v27-final-proof-summary.json`。结束时两平台无遗留产品 API 进程。

## 未关闭的门禁

本轮只推进 G17-A/B 的引用检查。返回类型／上下文及更完整引用覆盖、第三方扩展兼容、UI 六场景与英文／缩放／JCEF、同源最终候选双平台连续建模和游戏闭环、3 次独立试作及复评分仍需继续。此前恢复、游戏或建模证据保留其原候选范围，未改记为 v27 全面通过。本轮没有 Minecraft 输入或 Jev 调用。
