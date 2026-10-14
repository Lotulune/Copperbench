# P263-15 — NeoForge 26.3 有界 item

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 实现范围

NeoForge 26.3 通过协商预览开放 item 创建和修改，采用与 Fabric item 相同的十个输入名称和有界语义。字段、默认值、数值范围与枚举由 Core 提供；其他结构化类型仍被拒绝，公开 supportStage 保持 registered。详见[样例字段表](../../examples/camp-kit-structured/neoforge-26.3-item/README.md)。

专用模板使用 NeoForge `DeferredRegister.Items.registerItem` 和 mod event bus。已从锁定的 NeoForge 26.3.0.76 sources JAR 核对该方法自动设置 ResourceKey 的行为，源摘要和摘录在 `output/p263-15/api`。物品构造只使用已核对的 26.3 原生组件：稀有度、防火、固定速度 1 的 COOKING_FUEL，以及固定层数的 COMPOSTABLE。模型、客户端 item 定义与 en_us 显示名称随生成 JAR 打包。

[Native 编排](../../sdk/python/structured_263_item_delivery.py)与[运行入口](../../src/test/java/dev/copperbench/headless/Structured263ItemDeliveryMain.java)可按精确加载器选择独立工程；只复制测试宿主代码和数据。实际 mod 内容由结构化创建/更新生成，未复制手写 item 实现。

## 验证

25 项定向 Java、Javadoc、100 项 Python SDK、42 项 UI-Core、31 项 UI unit 和 UI build 已通过。Java 包括双加载器真实 UI/Core 适配器、字段发现、默认值、模板生成、未知字段保存重开，以及八个旧轨道字段回归。已有活跃未开放属性导致拒绝，失败不改变存储定义。

8 个浏览器流程通过，覆盖 Fabric/NeoForge、中英文与 1366/1920 两种宽度。NeoForge fixture 来自自己的真实 Core 输出，空表单默认堆叠 64、燃料 0、堆肥 0；实际填写 16、RARE、防火、燃料 400、堆肥 2 后经预览扩展保存。撤销预览保留草稿、禁用保存，重载不继承选择。已查看 NeoForge 1366 中文与 1920 英文截图，底部字段可见且不被保存栏遮挡。

fixture 与归一化 Core 输出逐字段一致，field-contract schema 通过；完整 editor 的既有 strict v1.0 差异继续记录在 27，未声称它通过。

最终运行目录：`output/p263-15/native/delivery-2743220678473256372`。Native 阶段耗时 187.033 秒，工作区 `b0fa2ede-5a35-42be-b016-612068ccb162`；三个产品进程 PID 5448, 8660, 44540 都由进程句柄确认 exit 0。五个受管任务完成：生成、构建、四项独立宿主验收、首次导出和第三进程重开后的导出。未知字段在新进程修改和重开后原值保留。

`camp-kit-item-v1` 事先固定 T01/T02/T05/T06，minimumTests=4，SHA-256 `b4963b6ad6f001d6f421383af5f352da9c6555f227b7b651f1c297117c8e2ff5`。四项全部通过；框架自检不计入业务数量。

- T01：注册身份与 16 堆叠上限。
- T02：稀有度、防火、燃料和堆肥组件及自定义名称补丁经 ItemStack CODEC 往返；被测 JAR 内显示名称资源正确。
- T05：真实 ServerLevel 中受控调用炉实体 ticker，消耗一件燃料、200 tick、固定速度 1，沙子变成一个玻璃，预算耗尽后熄灭且不额外消耗燃料。
- T06：真实堆肥插入逻辑每件增加一层，世界层数 0→7，每层消耗一件，满后不消耗。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 JAR | `1c01260e90b97c78cf8f2b6218b349240a3ae435b6a99383a6ad0b4b200a08ef` |
| 原始 XML | `50e77d8bd4b5519de9b7be32728991637e4d1e560f83e702a7058d5ccf93cc86` |
| 验收输入 | `a7b1cfd0a589a3f8809aedd3ea25623020809454f5ad170988d61e51b529def3` |

七项 Native 负向全部明确拒绝且 revision 不变：未 opt-in、重开后未 opt-in、未开放类型/字段、非法纹理、负燃料时长、超范围堆肥。成功与失败运行共 31 份预览扩展响应通过 schema 核对（其中成功流程 17 份）。

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-15`。包含原始回执、XML/JAR、两次宿主输入、失败日志、截图、测试报告及精确源快照；不含依赖缓存、私有签名密钥和玩家世界。

## 失败与修正

- 首次 PowerShell 调用的未引用生成器参数被拆分，Gradle 尝试选择不存在的 `.3` 任务；当时没有运行 Native 验收。引用整个参数后启动成功，样例命令同步修正。
- 首次真实运行 `delivery-16704383176294912732` 构建成功，但四项用例引用了 Fabric 提供的空结构，NeoForge 在 tick 0 放置测试结构失败，物品断言未执行。改为 NeoForge 原生样例已经验证的本地 `copperbench_camp_kit:empty` NBT；四个业务身份、最低数量与行为预期不变。失败任务、XML、JAR、宿主及日志保留。

本票不代替安装候选、玩家输入/渲染、保存重进、Ubuntu 或 G2 十三项全范围验收。所用依赖缓存已预先资格化，未声称冷缓存通过；临时授权最后撤销，未用本票替代授权签发 UI 的验证。
