# Stage 17 收尾进展（2026-09-23）

> 本页保留 v19 结果；后续进展见 [v20 通用字段复验](stage-17-generic-fields-2026-09-23.md)和[37 类型字段映射清单](stage-17-field-inventory-2026-09-23.md)。

**仍未整体验收；G17-A/B/C/D 保持开放。** 本轮完成两处专用字段转换修复，将此前函数修复一并纳入 v19，并补齐两平台公开入口的字段与旧工程差异处理证据。此前 v18 Minecraft 放置、库存重进和正常退出证据保持原候选范围，见 [9 月 22 日记录](stage-17-network-and-runtime-2026-09-22.md)。

## 字段修复与源码回归

真实工作区复现了两处错误成功：`projectile.showParticles="false"` 被隐式转成布尔值；`achievement.rewardLoot="lost"` 被丢弃为默认列表；两者均返回 `committed`。原失败记录 `.tmp/stage17-specialized-red-runtime.log`、`.tmp/stage17-specialized-red.xml` 保留。

[SpecializedFieldContract](../../src/main/java/dev/copperbench/core/application/SpecializedFieldContract.java) 为投射物和进度的既有映射补充严格字符串、布尔、字符串数组、精确整数、数值范围和枚举校验。数值范围来自上游存储字段的 `Numeric` 声明；进度的 `frame` 对应 `achievementType` 枚举。共同写入边界供创建、更新和计划使用，公开环境接口提供 `fieldContracts.projectile/achievement`。单独修改 `fields` 兼容路径时清除旧副本，显式提交冲突的两份值仍拒绝。该修复没有新增资源解析或游戏行为能力。

八轨均检查两个类型的创建、非法更新和计划拒绝、合法更新、显式别名冲突、实际生成文件及关闭重开。投射物生成 Java 的击退值为 42；进度生成 JSON 的奖励经验为 42、frame 为 goal。非法操作保留原定义字节和 revision。

| 回归 | 结果 | 原始证据 |
|---|---|---|
| 专用字段、函数、掉落表、方块一致性、应用服务 | 83 通过，0 失败，0 跳过 | `.tmp/stage17-specialized-regression.log`、`-summary.json`、`-xml/` |
| Schema | 28 通过，0 跳过 | `.tmp/stage17-specialized-schema.log` |

测试准备中的本机 Gradle loopback 失败已改用项目既有外部运行脚本；另有测试局部变量遮蔽 Java 包名、测试预期文件名不符合 `@NAMEEntity.java` 模板的失败，均保留原日志并修正测试，不计为产品缺陷。没有放宽字段或生成内容断言。

## v19 固定候选

- 源码：`D:/Hyper-V/Stage17-Linux/source-candidate-v19`，27135 个冻结文件；源清单 `source-manifest-v19.json`。这是非干净工作树快照，未提交或发布。
- `exportWin64 prepareDebLinux64 --offline --max-workers=1` 成功，1m18s。构建日志 `.tmp/stage17-v19-build.log`。
- 两平台主 JAR SHA-256：`28360df4ce051db0f5468d35ef6acfcde4a43bba999631428aeb10611ee06672`。
- 22 份随包文件、24 条文档链接、640 个 Schema 引用通过，冻结源文件无变化；`D:/Hyper-V/Stage17-Linux/candidate-v19-proof.json`。
- Ubuntu 已安装本地开发 deb；包 SHA-256：`8b44aa7fd0fe4adb57ff388615b7fa66264c6b99dd87b0c3753e0eabb2153004`。原共鸣工坊模型、PNG、旧 Mod JAR 哈希未变。安装证明在 `D:/Hyper-V/Stage17-Linux/guest-evidence-v19/installation-proof.json`。

Windows 验证入口是导出包 EXE，不是 Windows 安装器认证；Ubuntu 使用 `/usr/bin/copperbench` 和安装后的随包 SDK。本轮未新增原生 GUI、Blockbench 往返或客户端玩法结果。

## 两平台公开字段合同复验

[复验脚本](../../scripts/stage17-installed-contract-probe.py) 仅使用公开 SDK，在随包 Wayfinder 示例的新副本运行，拒绝复用既有证据目录。

Windows EXE 与 Ubuntu 安装入口各通过 15 次非法创建／更新／计划拒绝，诊断代码和字段路径准确，revision 不变；三类合法更新关闭重开后检查真实定义：

| 类型 | 重开后的有效值 |
|---|---|
| function | 正文精确为 `say after\nsay verified\n` |
| projectile | knockback=42，showParticles=true |
| achievement | rewardXP=42，title=Verified title，frame=goal |

两平台主 JAR 哈希与 v19 候选一致。Windows 证明 `.tmp/stage17-v19-sdk-contract.json`；Ubuntu 原始证明 `/home/stage17/evidence/v19/sdk-contract-proof.json`，已归档到 `D:/Hyper-V/Stage17-Linux/guest-evidence-v19/`。本条关闭了函数修复此前仅有源码回归、尚未进入候选验证的缺口。

## 两平台旧工程差异与保护边界

通过公开 SDK 创建专用种子，关闭后将定义硬度改为 0、保留产品声明硬度 3，重建原缺陷的差异状态；附加第三方未知定义字段。它是**重建的旧缺陷夹具**，不是未经修改的历史用户工程。所有后续用例使用独立副本。

两平台均通过：

1. 首次读取报告 drift、声明 3、有效值 0；读取不改定义、不推进 revision。
2. `adopt_definition` 重开后两侧均为 0，定义字节不变；`reapply_declared` 重开后两侧均为 3。
3. 计划后外部修改定义，应用返回 `WORKSPACE_PLAN_SOURCE_CONFLICT`；重新读取并重新计划才可应用。
4. 两条处理路径均保留未知对象 `thirdPartyExtension`；重复应用原已完成计划仍为 revision 2。
5. 无效声明 hardness=64001 拒绝重新应用，定位 `FIELD_VALUE_OUT_OF_RANGE`，定义和 revision 不变。
6. 手写锁定方块不被标作生成配置漂移；结构化修改返回 `SOURCE_MANAGEMENT_DETACHED`，手写 Java 与定义字节不变。
7. 另一次合法创建推进 revision 后，旧差异处理计划返回 `WORKSPACE_PLAN_STALE`，原定义不变。

Windows 证明：`.tmp/stage17-v19-recovery-windows-verified/proof.json`、`.tmp/stage17-v19-recovery-boundaries-windows-verified/proof.json`。Ubuntu 证明在归档目录 `legacy-recovery/proof.json`、`recovery-boundaries-verified/proof.json`。复验脚本保留于 `.tmp/stage17-v19-recovery-probe.py`、`.tmp/stage17-v19-recovery-boundaries.py`。

初轮脚本把计划的冲突代码误写为单写入冲突代码，产品实际已正确拒绝；失败日志和副本保留。随后按实际计划合同修正精确代码，在新副本重新通过。没有将旧失败文件覆盖成成功。

这里证明安全选择、拒绝与重开，不代替候选包内的故障注入回滚或实际恢复点恢复；后两项仍须补证。

## 当前门禁余项

| 门禁 | 本轮推进 | 仍需完成 |
|---|---|---|
| G17-A | 函数进入候选；投射物／进度严格类型修复；两平台旧工程差异与保护反例 | 全部声明字段、通用嵌套类型及引用／上下文合同审核；候选包故障回滚／恢复点边界；受影响最终候选行为复验 |
| G17-B | 原资源与探测证据保持；未新增 UI 验收 | 六夹具定位、语言／缩放／JCEF 矩阵、真实社区插件兼容。当前 UI_LOCALE 仍固定中文，英文是产品缺口，不能仅标作待截图 |
| G17-C | v18 Windows 客户端闭环保持有效；v19 定义复验通过 | 同源最终候选两平台连续建模与客户端闭环；Code 模板真实输入及存档边界 |
| G17-D | 新候选双平台公开入口补证 | 最终候选受影响平台回归、独立建模试作与复评分；不能把主 Agent 复验计成新增独立评估 |

八轨本轮只断言字段、生成与重开；此前不同候选的 GameTest 成果仍按原记录归属，不合并为 v19 八轨玩法通过。Minecraft Control 的菜单效率结果单列在 [桥验收记录](minecraft-control-menu-flows-2026-09-23.md)，不扩大产品验收范围。
