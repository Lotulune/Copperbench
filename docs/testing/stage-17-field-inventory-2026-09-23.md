# Stage 17 字段映射清单（2026-09-23）

> 本页和 JSON 保留 v20 的枚举快照。后续 [v21 验证](stage-17-custom-adapters-2026-09-23.md)已补充内置自定义适配器的输入类型检查；[v27 验证](stage-17-references-2026-09-23.md)补充已知节点与顶层过程字段的引用存在性／目标类别检查。返回类型、触发上下文、嵌套组件、第三方扩展及游戏行为仍需独立验证。

从本次已初始化的源码运行时枚举 37 个第一方类型，共 893 个类型内字段声明（包含产品元数据和只读内部名，不是去重字段数）。完整字段名、Java 类型、嵌套结构和排除项见 [JSON 清单](stage-17-field-inventory-2026-09-23.json)。

本清单用于界定审查范围，不把字段存在或通用转换器可用当作逐字段验收通过。反射分支的普通对象、数组、数值、布尔、字符串、枚举和映射引用输入已增加递归检查；资源存在性、跨字段条件及自定义序列化仍需独立审核。

| 类型 | 写入分支 | 字段声明数 | 排除的存储字段数 |
|---|---|---:|---:|
| block | specialized | 37 | 106 |
| item | reflective | 69 | 0 |
| recipe | reflective | 32 | 0 |
| procedure | specialized | 5 | 1 |
| function | specialized | 6 | 0 |
| loottable | specialized | 6 | 0 |
| achievement | specialized | 17 | 4 |
| armor | reflective | 77 | 0 |
| armortrim | reflective | 5 | 0 |
| tool | reflective | 37 | 0 |
| itemextension | reflective | 11 | 0 |
| attribute | reflective | 10 | 0 |
| bannerpattern | reflective | 6 | 0 |
| command | reflective | 7 | 0 |
| damagetype | reflective | 9 | 0 |
| enchantment | reflective | 15 | 0 |
| gamerule | reflective | 7 | 0 |
| keybind | reflective | 8 | 0 |
| painting | reflective | 8 | 0 |
| particle | reflective | 22 | 0 |
| potion | reflective | 8 | 0 |
| potioneffect | reflective | 20 | 0 |
| tab | reflective | 5 | 0 |
| villagerprofession | reflective | 8 | 0 |
| villagertrade | reflective | 5 | 0 |
| biome | reflective | 61 | 4 |
| dimension | reflective | 64 | 0 |
| feature | reflective | 8 | 0 |
| fluid | reflective | 47 | 0 |
| plant | reflective | 86 | 0 |
| structure | reflective | 19 | 0 |
| livingentity | reflective | 110 | 0 |
| specialentity | reflective | 10 | 0 |
| projectile | specialized | 18 | 2 |
| gui | reflective | 15 | 2 |
| overlay | reflective | 9 | 0 |
| code | specialized | 6 | 0 |

排除项保留在 JSON 清单：专用分支没有对应写入适配器的字段，以及 transient 临时字段。生物群系 genDepthMin/Max 已不再声明可写；已有导入值不因读取被清除。

自定义过程／返回值过程、GUI 组件和属性／状态映射不能按普通反射对象推断格式。JSON 中标作 custom_adapter_requires_separate_audit 的条目，以及 Map 中的值类型，需要结合实际适配器继续审查。

本轮行为反例与八轨合法更新／重开证据见 Stage17NestedFieldContractTest；完整 Core 回归为 194 通过、4 条件跳过。源码枚举程序及运行记录保留于 .tmp/Stage17FieldInventory.java、.tmp/stage17-field-inventory.init.gradle、.tmp/stage17-nested-core-final-runtime.log。清单不替代游戏验证，也不关闭整个 G17-A。
