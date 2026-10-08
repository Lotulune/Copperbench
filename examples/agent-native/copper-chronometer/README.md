# 铜质昼夜仪 / Copper Chronometer

本 mod 是 2026-10-09（日本时间）Copperbench 项目评估中新写的 Fabric 1.21.1 实测样本。
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
最终验证状态以本次评估的运行日志及 trial-summary.json 为准；源码文件的存在不代表构建或玩法通过。
没有实际客户端截图和输入证据时，画面、声音听感及保存退出重进体验均为未验证。

## 许可与来源

GPL-3.0-only，与上游工程骨架同许可；协议全文见仓库 LICENSE.txt。
不分发 Minecraft 二进制、原版纹理或缓存。GameTest 的虚拟连接辅助方法沿用仓库
Wayfinder Bell 示例的已记录 API 用法，断言覆盖的是本 mod 新写的昼夜仪行为。
