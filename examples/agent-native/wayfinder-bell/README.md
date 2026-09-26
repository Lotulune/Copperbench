# 寻路铃 / Wayfinder Bell

Fabric 1.21.1，Java 21，需要 Fabric Loader 0.19.3 或更高版本及对应的 Fabric API。

发行包附带此示例的源码，不包含预编译 JAR、依赖缓存或游戏世界。先将整个 `wayfinder-bell` 目录复制到可写目录，用 Copperbench 打开 `wayfinder_bell.mcreator` 并执行构建，等待任务成功。

也可以在已配置 Java 21 的终端中进入该副本，Windows 运行 `./gradlew.bat build`，Linux/macOS 运行 `sh ./gradlew build`。首次构建需要下载声明的依赖；不要使用离线参数，除非已经准备完整缓存。

成功后将 `build/libs/wayfinder_bell-1.0.0.jar` 放入对应游戏实例的 `mods` 目录。构建仅证明编译打包成功；若修改示例，应重新执行适用的 GameTest 和客户端检查。

- 潜行右键：把当前位置和维度保存到这一个寻路铃。
- 普通右键：显示路标方向、水平距离和高度差。
- 成功使用后冷却 1 秒。跨维度时提示原路标所在维度。
- 无序合成：指南针 ×1、紫水晶碎片 ×1、铜锭 ×2。
- 创造模式：在工具与实用物品栏查找“寻路铃”。
- 测试命令：`/give @s wayfinder_bell:wayfinder_bell`。

这是 Copperbench Stage 15 审视过程中制作的原生源码试作。玩法源码在 `src/main/java/dev/wayfinder`，通过 `fabric.mod.json` 的独立入口注册；Copperbench 的生成源码同时保留。

已经通过 Copperbench 构建、13 项几何检查，以及独立宿主加载最终 JAR 的服务端 GameTest。真实鼠标交互、HUD/图标和声音尚未人工体验；只有 Fabric 1.21.1 是本次被测目标。
