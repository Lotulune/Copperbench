# P263-18 — Fabric 26.3 合成、烧炼与配方重载

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 实现范围

Fabric 26.3 通过既有 recipe 字段发现入口开放 16 个输入名称：有序/无序合成、普通炉、高炉与烟熏炉。字段、默认例、引用格式及数值约束来自 Core。具体范围见[样例说明](../../examples/camp-kit-structured/fabric-26.3-recipe/README.md)。未开放子类型、namespace 覆盖及其他活跃未支持属性明确拒绝；NeoForge recipe 仍未开放。

原生映射列表有八个已核对的条目。`CUSTOM:<internalName>` 通过真实元素的 registryName 生成资源 ID；未知引用在写入前拒绝，编辑器候选保留内部名称。旧式 CamelCase 内部名称和不同 registryName 的 fixture 通过生成/编辑/重开验证。原 [item](p263-ticket-14-fabric-item-2026-10-12.md)与 [block](p263-ticket-16-fabric-block-2026-10-12.md)能力保持可用，supportStage 仍为 registered。

## 实际验证

50 个不同 Java 行为、Javadoc、100 项 Python SDK、42 项 UI-Core、31 项 UI unit 与 UI build 通过。范围包括新 recipe、两加载器 item/block、权限/预览协商、旧八轨道方块及字段发现。重跑按类与测试名合并，未累计重复次数。

20 个不同浏览器场景/布局通过，包括四个 Fabric recipe 流程和既有 16 个 item/block 流程。中英文下实际创建空 recipe、填写合成格、切换烧炼类型、保留非当前类型草稿、使用旧式内部名称引用、保存、生成与重新选择预览。已查看中文小布局的实际原料/产物控件和英文大布局的类型、数量及时长区域；烧炼最小值从同一字段 schema 投影为 1。成功重跑和初次失败截图分别保留。

两个 recipe 编辑器 fixture 与真实 Core 响应逐字段一致，field-contract schema 通过。完整 editor 的既有 strict v1.0 差异继续登记在 27，未声称其通过。

最终 Native 运行：`output/p263-18/native/delivery-6537516851702909515`，耗时 187.221 秒。工作区 `d236ee1d-4a04-4f86-8155-72d7ab32b9b6`；产品 PID 8212、32364、19908 均由进程句柄确认 exit 0。一个物品和五个配方均经产品创建；关闭产品后显式导入 differing registry_name/未知字段 fixture，再在新进程中编辑并生成，未复制手写物品或配方实现。

事先冻结 `camp-kit-recipe-v1` 的 T03/T04/T07/T08/T09，minimumTests=5。生成、构建、独立宿主验收、首次导出与产品重开后再次导出五个受管任务全部成功。

- T03：真实有序与无序匹配分别产出 2、3 个实际注册物品。
- T04：错误合成原料没有匹配；错误炉原料运行 220 个真实实体 tick 后不消耗原料/燃料，无产物。
- T07：普通炉在第 199 tick 尚无产物，第 200 tick 消耗一个原料，得到两个产物；消耗一个燃料，剩余燃烧预算 201。
- T08：高炉与烟熏炉分别在第 100 tick 完成，各消耗一个原料和一个燃料、得到一个产物；剩余预算均为 301。
- T09：真实服务器 datapack reload 后，有序合成产物由 2 变为 4。实际 `Recipe.DIRECT_CODEC` 拒绝未知产物 `missing_recipe_output` 并保存原始诊断；该负向不增加业务数量。

26.3 的实际类和反汇编在 `output/p263-18/api`：`cookingtime` 是 tick 整数，炉实体按 `ceil(recipe.cookingTime / fuel.speedMultiplier)` 得到实际时长。本样例燃料组件速度为 1、时长为 400；高炉/烟熏炉的 100 tick 来自各自配方数据，没有臆设额外二倍速度。

八项 Native 负向明确拒绝且 revision 与定义文件不变：未选择预览、重开后未选择、未支持类型/字段、非法原版引用、缺失自定义引用、非法时长和未支持配方子类型。未知字段保存重开不丢失。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 JAR | `c9afc3ac068fdd74a740890d510ebb2ea2d48021b1a1dd62e95c8e9a4f5df41b` |
| 原始 XML | `805033ddd2a73544921e25f1040935926f790264c599cc1b782fec5f6cbdfd33` |
| T03/T04/T07/T08/T09 清单 | `033c965b66c3e9948cc5a1e62283e68879275f7dc5645f2865ef73927266c918` |
| 验收输入 | `80e09da911dceca76c09aded779d88ca12f267034e17a6e52be38d9edfca1bb3` |

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-18`。包含 Native 原始回执、XML/JAR、宿主与输入清单、三份实际炉消耗回执、非法引用诊断、浏览器截图、全部测试日志及精确源快照。排除依赖缓存、玩家世界和授权私钥。

## 开发期间发现的问题

- 映射列表中的煤使用既有条目 `Items.COAL#0`；最初 `Items.COAL` 未被发现入口列出，改为真实条目后八项一致。
- 旧物品负向仍断言 recipe 未开放，已改为持续未开放的 loottable，保留相同拒绝语义。
- 浏览器测试曾错误要求切换配方类型后隐藏合成格；按既有界面契约改为禁用并保留草稿。
- 截图复核发现时长范围提示仍显示旧下限 0；已让预览数字控件采用同一 inputSchema 的下限 1，并补 Core/浏览器检查。

本票未替代安装、玩家输入/渲染、Ubuntu 和完整 G2 验收。使用资格化依赖缓存，未声称冷缓存通过。
