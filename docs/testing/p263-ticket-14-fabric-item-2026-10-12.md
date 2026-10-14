# P263-14 — Fabric 26.3 有界 item

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 可用范围

Fabric 26.3 的预览扩展现在开放 `create_mod_element` 和 `update_mod_element`，限 item 的十个输入名称：`name`、`displayName`、`description`、`texture`、`stackSize`、`maxStackSize`、`rarity`、`immuneToFire`、`fuelBurnTime`、`compostingLayers`。其中 name 创建后只读，两个堆叠名称是同一属性的别名，description 是持久化创作者元数据。

堆叠范围 1–99；燃烧时长 0–128000 tick；每个物品增加 0–7 堆肥层。后两项为 0 时不生成相应组件。燃料速度固定为 1，堆肥为确定性常量层数。纹理仅开放已从锁定 26.3 JAR 核对的 barrier、stick、coal、paper 四个原版资源。[样例说明](../../examples/camp-kit-structured/fabric-26.3-item/README.md)列出默认值和重放命令。

字段发现、编辑器枚举、数值边界和实际写入校验一致。已有未知字段原值保留；未开放字段不能通过创建、更新或已有定义的隐式重新生成获得支持。NeoForge item 和其他四类结构化类型继续受各自证据门禁约束；公开 `supportStage` 仍为 `registered`。

旧轨道不公开新增的两个组件字段，也不改变原 itemextension 的语义。26.3 没有沿用旧 Fabric 燃料/堆肥注册表：生成器使用 `CookingFuel(ResolvableInt.Constant, ResolvableFloat.Constant)` 和 `Compostable(ResolvableInt.Constant)`；注册使用 `Registry.register` 与带 ResourceKey 的 `Item.Properties.setId`。物品模型、客户端 item 定义和 `en_us.json` 随同 JAR 生成。核对记录在 `output/p263-14/api`，包括实际类反汇编、版本及纹理字节摘要。

## 真实 Native 与独立宿主

[Native 入口](../../src/test/java/dev/copperbench/headless/Structured263ItemDeliveryMain.java)从生命周期创建独立空工程，只复制独立测试源与测试数据。物品由 [Python Native 编排](../../sdk/python/structured_263_item_delivery.py)通过字段发现、创建和修改产生；未复制手写 mod 实现。

最终运行目录：`output/p263-14/native/delivery-6079880694320060060`。Native 阶段耗时 165.680 秒，工作区 `40662e77-7899-49a1-ba25-293dd00e390a`；三个产品进程 PID 45916、36712、25584 均由进程句柄确认 exit 0。创建后关闭产品，向存储定义注入明确标记的未来字段 fixture；新进程修改、生成、构建、验收后，该字段保留。第三个进程重开，读取持久化验收并再次导出。

开发子清单 `camp-kit-item-v1` 固定 T01/T02/T05/T06，minimumTests=4，摘要 `b4963b6ad6f001d6f421383af5f352da9c6555f227b7b651f1c297117c8e2ff5`。此子清单不替代原生六项或 G2 十三项清单。

独立宿主测试只携带测试源码和数据，加载被测生成 JAR。预期值直接写在[宿主用例](../../examples/camp-kit-structured/fabric-26.3-item/src/gametest/java/copperbench/acceptance/AcceptanceTests.java)中，不读取被测实现常量来推导答案：

- T01：实际注册身份与 16 堆叠上限。
- T02：稀有度、防火、燃料、堆肥默认组件和自定义名称补丁经 ItemStack CODEC 往返；从被测 JAR 读取语言资源并检查显示名称。
- T05：在真实 ServerLevel 中调用真实炉实体 ticker，点燃时只消耗一个 item，燃烧总量为 200 tick、速度为 1，实际消耗沙子并产出一个玻璃，预算结束后熄灭且没有再消耗燃料。ticker 在同一宿主回合内受控调用，未把它描述为玩家手工等待。
- T06：真实 ComposterBlock 插入逻辑每次消耗一个 item，世界层数依次从 0 到 7；满后不再消耗。常量层数避免概率性成功。

验收任务 `7dabcd33-61b3-4601-a385-f0d372f77e5c` 的四项业务全部通过；框架 always_pass 不计入业务数量。构建、受测 JAR、首次导出和重开后的导出摘要一致：

| 对象 | SHA-256 |
| --- | --- |
| 最终生成 / 受测 / 导出 JAR | `93a425f9fda221a8f05963c5c6d8d43e2385b34479ef89c8be23d7943cfc4811` |
| 原始 XML | `25337da7ad1dac863395123d5cb3c29176b6171bfa1f7c029ca417c8d6b640bb` |
| 验收输入 | `78a895b6505441ce6caef88723c3cd900c35284530a9ac859126af8c8d7b2f38` |

七项实际 Native 拒绝检查覆盖未选择预览、重开后未重新选择、未开放类型、未开放字段、非法纹理、负燃料时长和超范围堆肥层数。失败不推进 revision。17 份真实扩展响应通过 schema。任务授权为本任务的隔离临时 fixture，最终撤销；未声称本票测试了桌面签发授权 UI。

## 界面和定向回归

工作区和元素编辑器都可显式选择本次会话预览。创建对话框只启用已验证类型；保存、生成、构建使用协商后的扩展。编辑器显示同一 Core 的字段、默认值、枚举和范围；撤销预览保留草稿并禁用保存，未开放的删除操作禁用。JCEF 扩展写入后刷新元素列表和 revision。

53 个不同 Java 测试通过，包含八个旧轨道 item/recipe 的字段发现、实际模板生成、持久化重开与验证器回归；新增用例还证明已有未开放食品属性不会在无关编辑中被静默忽略，失败后原始定义字节不变。另有一次既有真实资源探针 opt-in 跳过，未计入通过数。

Javadoc、100 项 Python SDK、42 项 UI-Core、31 项 UI unit 及 UI build 通过。浏览器检查共 18 个不同布局/场景通过：四个中英文 item 流程、八个已有预览目录/创建流程、六个旧工作区创建/构建/生成回归。重复运行保留原记录，不累加为不同用例。1366×768 中文和 1920×1080 英文截图已查看；底部字段可滚动至完整可见，不被保存区遮挡。浏览器使用真实 Core 生成的字段 fixture 加模拟传输；实际写入和运行结果由上面的 Native 和 Java UI 适配器提供，不将浏览器 fixture 当作 Minecraft 运行证据。

最后四个浏览器流程使用空 `initialValues` 的真实 Core 新建投影：堆叠 64、燃料 0、堆肥 0，再通过控件改成堆叠 16、稀有、防火、燃料 400 和堆肥 2。fixture 与归一化后的 Core 响应逐字段一致，独立 field-contract schema 通过。

## 保留的失败、修正与边界

- 首次脚本没有在生命周期初始化前设置指定 Gradle cache，创建过程因 annotations 26.0.2 POM 校验失败。补齐脚本缓存设置后，使用先前资格化的缓存完成全流程。本票没有证明默认缓存或冷缓存可用；此已知缺口交给候选/安装门禁继续核对。
- 首次模板沿用的 Items.registerItem 辅助方法在当前未放宽访问权限的 API 中为 private，真实构建失败。改用实际 26.3 原生样例已验证的 Registry / setId 路径后构建通过，失败回执保留。
- 初次四项通过后，JAR 内容自查发现语言文件未生成。补齐 language_file 配置并将资源断言加入 T02；前一 JAR 不作为最终候选，完整新验收和重开导出使用表中的最终摘要。
- 编辑器最初未识别 item 的反射字段，贴图枚举检查失败；统一反射和 schema 后通过。一次 Gradle 测试 worker 在接收用例前发生 Windows loopback/UnixDomainSocket 错误；保留日志，使用新 daemon 的后续测试通过，没有把那次启动失败算成产品断言结果。
- 额外完整编辑器 schema 核对发现已有契约差异：`configuration`、`sourceManagement`、`fieldContract`、`inputSchema` 等丰富编辑器属性不在 strict v1.0 的 editor shape 中，camel-case 消息键也超出其格式。已核对基线代码包含这些行为。完整 editor 响应未通过 strict v1.0，不能用字段契约或预览扩展通过来替代；明细保存于 `ui-fixture-schema.json`，27 的候选门禁继续处理。

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-14/frozen`。包含真实任务回执、最终和前序证据、宿主输入、原始 XML/JAR、源快照和摘要；不包含依赖缓存、私有签名密钥或玩家世界。父目录保留了首次归档碰到同名目录时的未完成复制，只有 `frozen/archive-index.json` 索引下的文件属于正式归档。

本票仅关闭 Fabric item 的有界开发验收。玩家 16+1 操作、渲染、世界保存重进、安装候选、Ubuntu 和 G2 全范围验收保持各自未验证状态。
