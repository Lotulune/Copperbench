# Fabric 1.21.1 纵向能力清单

## 已支持

| 能力 | 阶段 3 行为 | 自动化证据 |
| --- | --- | --- |
| 工作区描述 | 固定 `modId`、Java 包名和版本，校验路径与标识符 | `Fabric1211GeneratorTest` |
| 方块 | 强度、抗性、亮度、BlockItem、模型、方块状态、掉落、纹理和语言 | 黄金生成与真实 Gradle 构建 |
| 物品 | 最大堆叠数、创造栏、模型、纹理和语言 | 黄金生成与真实 Gradle 构建 |
| 有序配方 | 1-3 行 pattern、ingredient key、工作区内结果引用 | 生成器校验与快照断言 |
| 简单 Procedure | 初始化时执行固定日志消息 | `COPPERBENCH_STAGE3_READY` 标志 |
| 工作区任务 | 校验、生成、构建、安全导出、运行客户端、日志、诊断和取消 | `Fabric1211TaskGatewayTest` |
| AI/自动化 | MCP CRUD、校验、生成、构建、导出、运行和任务查询 | `McpHttpServerTest` |
| 恢复 | 恢复点、破坏性编辑、明确批准还原、再次构建与源码一致性 | `Fabric1211RestoreBuildTest` |

## 明确未支持

- 复杂 Procedure 图、触发器和依赖注入。
- 实体、GUI、世界生成、附魔、维度、标签和数据组件编辑器。
- 自定义模型、动画、音频和 Blockbench 往返。
- 1.20.1、动态最新版本和 NeoForge 生成。
- 与当前内置 Goldorion 26.1.x 生成器模板共享实现；阶段 3 生成器是独立的有界实现。

## 与 NeoForge 的当前差异

阶段 3 不声明 NeoForge 对等。当前领域合同保留加载器无关的 block/item/recipe/procedure 名称和字段路径，但注册 API、Gradle 插件、元数据和运行任务只实现 Fabric。NeoForge 对等属于阶段 5，届时必须对相同黄金元素增加差异映射和双加载器编译门禁。

