# P263-19 — NeoForge 26.3 合成、烧炼与配方重载

日期：2026-10-13（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 实现范围

NeoForge 26.3 开放与 [Fabric 配方](p263-ticket-18-fabric-recipe-2026-10-12.md)相同的 16 个输入字段和四种配方类型，详见[样例说明](../../examples/camp-kit-structured/neoforge-26.3-recipe/README.md)。两加载器模板和八项原版引用映射逐字节一致；NeoForge 独立宿主通过 mod event bus 和 DeferredRegister 注册测试函数，并独立编译、加载和执行实际生成 JAR。supportStage 仍为 registered。

空创建可编辑，所选子类型的完整原料和结果是生成前提。CUSTOM 引用按内部名称查找元素，按真实 registryName 输出；未知引用、未支持子类型、namespace 覆盖及未开放活跃字段明确拒绝。未知字段编辑重开后保留。

## 实际验证

29 个不同 Java 行为最终通过，覆盖两加载器配方、NeoForge item、预览协商及原八精确生成器的字段发现/生成重开；重复执行按类与测试名去重。Javadoc、100 项 Python SDK、42 项 UI-Core、31 项 UI unit、UI build 通过。

两加载器 × 中英文 × 两种窗口尺寸的 8 个配方浏览器场景通过。实际创建空配方、填写原料/产物、切换炉类型并保留草稿、保持内部名称引用、保存、生成及撤销/重新选择预览均有回执。已检查 NeoForge 中文小布局和英文大布局截图。新 NeoForge fixture 与真实 Core 响应逐字段相同，field-contract schema 通过；完整 editor strict v1.0 基线差异仍归 27。

Native 运行 `output/p263-19/native/delivery-13649979294052427921`，耗时 206.157 秒。工作区 `cd44f330-7665-4cb0-9fbb-249fa327f902`；产品 PID 32480、5604、41744 均由进程句柄确认 exit 0。产品创建一个物品及五个配方，关闭后显式导入不同 registry_name/未知字段 fixture，重开编辑并生成；没有手写物品或配方实现进入被测 JAR。

冻结的 `camp-kit-recipe-v1` 固定 T03/T04/T07/T08/T09，minimumTests=5。生成、构建、独立宿主验收、首次导出和新产品进程重开后的再次导出全部成功。

- T03：有序/无序真实匹配分别产出 2 和 3 个实际注册物品。
- T04：错误合成无结果；错误炉原料执行 220 个实体 tick 不消耗原料或燃料。
- T07：普通炉第 199 tick 无产物，第 200 tick 消耗一个原料并产出两个物品；消耗一个燃料，剩余预算 201。
- T08：高炉/烟熏炉分别在 100 tick 完成，消耗一个原料、一个燃料并产出一个物品；剩余预算均为 301。
- T09：真实 datapack reload 后产物由 2 变为 4。实际 Recipe.DIRECT_CODEC 拒绝 missing_recipe_output，原始错误保留；该负向和框架 always_pass 均不增加业务用例数量。

原版 26.3 cooking API 依据沿用 18 的反汇编，并记录来源。NeoForge 上述时长与消耗来自本次实际运行：配方 cookingtime 为整数 tick，样例燃料速度为 1、预算为 400；高炉/烟熏炉的 100 tick 是配方显式数据，不假设额外二倍速度。

八项 Native 负向均拒绝且 revision/定义字节不变。五份 JAR 配方资源均使用 `copperbench_camp_kit:actual_trail_kit`。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 JAR | `c555edc5284779ef0a82cbbf8b4cfbaa34215c73d82f54b1cdaf6a85d7d931f6` |
| 原始 XML | `b2e125ffd2f08b930860404531aa7b82c045a6df8ebe8589fb4e872aec4d615e` |
| T03/T04/T07/T08/T09 清单 | `033c965b66c3e9948cc5a1e62283e68879275f7dc5645f2865ef73927266c918` |

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-19`。保留原始任务、XML/JAR、三份炉回执、无效引用错误、截图、测试日志和精确源快照；不包含依赖缓存、玩家世界或授权私钥。

首次 Java 运行在测试执行前遇到 Windows 本地套接字错误，新 Gradle 进程恢复。第二次运行揭示两个新测试仍引用 Fabric 专用辅助方法，改为 NeoForge 辅助方法后通过；失败日志和后续结果均保留。

本票不替代安装、玩家输入/渲染、Ubuntu 或完整 G2 验收。使用资格化依赖缓存，未声称冷缓存通过。
