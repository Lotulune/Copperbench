# P263-17 — NeoForge 26.3 有界 block

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 实现范围

NeoForge 26.3 开放与 [Fabric block](p263-ticket-16-fabric-block-2026-10-12.md)一致的九个输入名称、默认值、纹理和朝向范围。专用模板通过 `DeferredRegister.Blocks.registerBlock` 与 `Items.registerSimpleBlockItem` 注册方块和物品，并接入 mod event bus。已从锁定的 NeoForge 26.3.0.76 sources JAR 核对这些方法设置 ResourceKey 和方块物品名称前缀的行为，摘录与来源摘要保存在 `output/p263-17/api`。

[样例说明](../../examples/camp-kit-structured/neoforge-26.3-block/README.md)列出完整范围和重放命令。方块状态、模型、客户端 item 定义、语言与自身掉落随实际 JAR 打包；客户端类不进入专用服务器注册代码。既有 [item 能力](p263-ticket-15-neoforge-item-2026-10-12.md)保留，结构化 loot 元素仍未开放，supportStage 保持 registered。

## 实际验证

35 个不同 Java 行为、Javadoc、100 项 Python SDK、42 项 UI-Core、31 项 UI unit 和 UI build 通过。Java 覆盖双加载器 item/block、字段发现与空表单默认值、产品重开、未知字段保存和旧八轨道回归。首次有一项能力测试仍期待 NeoForge 仅开放 item；更新到新增 block 范围后该类六项通过，其余初次通过的检查保留，未重复累加。

16 个不同浏览器场景/布局通过，覆盖两加载器 × item/block × 中英文 × 1366/1920。已查看 NeoForge block 中文小布局和英文大布局截图，标签、选择项及保存栏完整。block fixtures 和专用发现结果逐字段匹配真实 Core；该专用 block 格式不适用 item/recipe 的通用 schema。完整 editor 既有 strict v1.0 差异另存，27 继续处理。

最终运行：`output/p263-17/native/delivery-16022836913666322404`。Native 阶段耗时 186.693 秒，工作区 `02589133-f25d-49f9-9321-5f2ed96f53f4`；三个产品 PID 18740、38956、40320 均由句柄确认 exit 0。生成、构建、T10/T11 独立宿主、首次导出和产品重开后再次导出五个受管任务成功。

- T10：真实方块掉落由自身物品经服务器资源重载变为木棍；Minecraft registry-aware loot codec 对 `missing_drop` 返回未知注册键错误。
- T11：实际放置、Y→X 轴向变化，在固定位置保存 Z 轴方块。
- 独立普通专用服务器：加载逐文件哈希一致的世界副本和同一 JAR；直接读取保存的 Z 轴方块及木棍掉落，再真实重载为纸，写出 passed 回执并正常关闭。日志显示 Gradle 退出 0，耗时 47 秒。
- 八项 Native 负向被明确拒绝且 revision 不变；未来未知字段经编辑与产品重开保留。

重開宿主使用用户已批准的本任务临时测试服务器 EULA。其检查回执是普通服务器的运行证据，没有伪造第二份 GameTest XML，也未增加业务用例数。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 / 世界重开 JAR | `adf8ba0d8d67b948e8d1a50dd95cfdce300f474b65d42b1a640254ee5a0bcabf` |
| 最终首次宿主 XML | `5e55dfdb5163111aba8cd48e1b07e492a7c3f2ef70e3132187e2df13cffbf28a` |
| T10/T11 清单 | `a5f1def54659cbe4feabf5e8ba6554e996689e9359e750a998fd8a106ac51e2b` |
| 验收输入 | `064f84461e52cdffe567fbdd4c312a9a1bca7ce0ea05c97508b74b241e719bf8` |

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-17`。包含两次运行、实际 XML/JAR、失败日志、两类宿主源码/配置、截图、世界复制哈希清单及精确源快照；不包含世界数据、缓存与授权私钥。

## 失败与修正

首轮 `delivery-12374599916187817278` 的 Native 闭环成功，但世界重开失败且没有 GameTest XML：NeoForge 的 `net.minecraft.gametest.framework.GameTestMainUtil.createOrResetDir` 每次启动都会删除 universe。已保存真实 patched 源码、FML 启动入口和失败日志，未把缺失报告算作通过。

改为普通专用服务器加载保存世界，使用独立的 [WorldReopenChecks](../../examples/camp-kit-structured/neoforge-26.3-block/src/gametest/java/copperbench/acceptance/WorldReopenChecks.java)记录状态读取和真实掉落重载；原 T10/T11、最低数量和被测 JAR 保持不变。随后从新隔离工程完整重跑通过。

本票证明开发宿主上的服务端行为；安装、玩家输入/渲染、Ubuntu 和 G2 十三项验收仍由后续票执行。使用资格化缓存，未声称冷缓存通过。
