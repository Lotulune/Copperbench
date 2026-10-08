# 铜质昼夜仪 / Copper Chronometer

本 mod 是 2026-10-08（UTC；日本时间 10 月 9 日）Copperbench 项目评估中新写的 Fabric 1.21.1 实测样本。
工程骨架及 wrapper 来自同仓库的 Wayfinder Bell，玩法实现、资源及断言独立编写。
运行需要 Java 21、Fabric Loader 0.19.3+ 及对应 Fabric API；使用 Mojang official mappings。

## 玩法

- 普通右键显示主世界天数、时分、时段及距下一时段的游戏刻数。
- 潜行右键在 12/24 小时制之间切换；格式偏好保存在每件物品的 CustomData 中。
- 成功使用冷却 20 游戏刻；旁观者不能使用。其他维度中读取失败且不触发冷却。
- 合成：3 铜锭、1 时钟、1 红石，布局见 recipe JSON。
- 物品 ID：`copper_chronometer:chronometer`，创造模式在工具与实用物品栏。
- 图标引用原版静态时钟贴图；本样本没有新增美术资源或自定义声音。

本仪器的时段约定：0–11999 白天，12000–12999 黄昏，13000–22999 夜晚，23000–23999 黎明。
这些范围服务于仪器显示，不是睡觉、刷怪或天气判断的承诺；下一时段数值按游戏刻计，暂停日夜循环时不会倒计时。

## 构建与验收

将整个目录复制到可写目录，用 Copperbench 打开 `copper_chronometer.mcreator`。
普通构建也可使用 Java 21 后执行 `sh ./gradlew build`。安装时需要另行提供 Fabric API。
`copperbench-tests.json` 声明独立 packaged-JAR GameTest 宿主，至少要求 12 个实际执行的用户用例。
测试成功后应使用 Copperbench 的 verified artifact export 交付与验收绑定的那一个 JAR。

评估驱动脚本位于仓库 `scripts/evaluation/run-chronometer-trial.py`。
它会创建结构化 function，探测字段发现与修订冲突，注入并修复编译错误，构建、GameTest 并导出。
最终验证状态以本次评估的运行日志及 `trial.json` 为准；源码文件的存在不代表构建或玩法通过。
没有实际客户端截图和输入证据时，画面、声音听感及保存退出重进体验均为未验证。

### 本次实测结果

[评估 CI 37809815009](https://github.com/Lotulune/Copperbench/actions/runs/37809815009) 使用产品基线
`b1a69d244ab7337608cc302826ed55d4a707f37f`，测试源码提交为
`bf07090d600a4298e93c5a60430c699a2d72ac48`。纯 Java 时间计算 54 条断言通过；真实 Native SDK
构建、受控编译错误的诊断与修复、最终 JAR 的 12 个 GameTest、已验证产物导出及重开均通过。
导出 JAR SHA-256：`540371ca504117ff10a25ed272ef373abc38a5aa7f685c54b9d0618dd6208b29`。

评估总状态为 `completed_with_findings`：`field_contract("item")` 和 `field_contract("recipe")`
实际抛出 `KeyError`；在另一份副本中加入结构化 function 后，生成任务正确拒绝了未归生成器所有的
现有基础文件，返回 `GENERATION_SOURCE_CONFLICT`。本示例采用原生 Java/JSON 路线，不能把空的
`mod_elements` 当作可直接重建所有基础文件的受管模板。全部手写源码在失败探针后保持原样；
最终交付来自新的原生副本，未修改或认领 ownership 元数据。该结构化 function 不在交付 JAR 中。

本次使用已有 CI 依赖缓存，未验证安装版桌面、真实客户端画面和输入、音效听感、玩家实际合成、
完整世界保存重进、其他 Minecraft/Loader 轨道或陌生用户首次使用。GameTest 的服务端用例通过
不扩展这些结论；受控编译错误探针也不等同于一般性 agent 自动修复成功率。

## 许可与来源

GPL-3.0-only，与上游工程骨架同许可；协议全文见仓库 LICENSE.txt。
不分发 Minecraft 二进制、原版纹理或缓存。GameTest 的虚拟连接辅助方法沿用仓库
Wayfinder Bell 示例的已记录 API 用法，断言覆盖的是本 mod 新写的昼夜仪行为。

## Stage 18 回归

[Stage 18](../../../PRD-STAGE-18.md)将该样本用于严格真实回归。54条纯Java断言已补入距下一时段的精确预期；接口契约或归属诊断探针失败也会使回归失败，不以原生交付成功覆盖它们。混合副本的归属拒绝属于明确的预期边界，交付始终来自另一份原生副本。

新的运行证据见[首批执行记录](../../../docs/testing/stage-18-initial-implementation-2026-10-09.md)。上面的2026-10-08实测哈希与结果保留为历史，修改后的输入需要重新验收。
