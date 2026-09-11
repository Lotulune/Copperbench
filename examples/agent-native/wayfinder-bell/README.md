# 寻路铃 / Wayfinder Bell

Fabric 1.21.1，Java 21，需要 Fabric Loader 0.19.3 或更高版本及对应的 Fabric API。

将 `build/libs/wayfinder_bell-1.0.0.jar` 放入游戏实例的 `mods` 目录。

- 潜行右键：把当前位置和维度保存到这一个寻路铃。
- 普通右键：显示路标方向、水平距离和高度差。
- 成功使用后冷却 1 秒。跨维度时提示原路标所在维度。
- 无序合成：指南针 ×1、紫水晶碎片 ×1、铜锭 ×2。
- 创造模式：在工具与实用物品栏查找“寻路铃”。
- 测试命令：`/give @s wayfinder_bell:wayfinder_bell`。

这是 Copperbench Stage 15 审视过程中制作的原生源码试作。玩法源码在 `src/main/java/dev/wayfinder`，通过 `fabric.mod.json` 的独立入口注册；Copperbench 的生成源码同时保留。

已经通过 Copperbench 构建、13 项几何检查，以及独立宿主加载最终 JAR 的服务端 GameTest。真实鼠标交互、HUD/图标和声音尚未人工体验；只有 Fabric 1.21.1 是本次被测目标。
