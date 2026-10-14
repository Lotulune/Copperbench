# P263-16 — Fabric 26.3 有界 block

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 实现范围

Fabric 26.3 通过协商预览开放简单 block 创建和修改，范围为九个输入名称。既有 `get_mod_element_editor` 的 block 字段契约提供字段发现、默认例、枚举和约束；没有另造一份契约。具体默认值和范围见[样例说明](../../examples/camp-kit-structured/fabric-26.3-block/README.md)。已验证的 [item 能力](p263-ticket-14-fabric-item-2026-10-12.md)继续可用，公开 supportStage 保持 registered。

模板生成普通方块或柱形方块、对应物品、方块状态/模型、客户端 item 定义、语言键和自身掉落。方块物品固定存在；容器、世界生成和其他活跃未开放属性明确拒绝。NeoForge 的验证入口先检查自己的能力，避免共用后端时误继承 Fabric 新能力。未开放结构化 loot 元素。

## 实际验证

32 个不同 Java 行为、Javadoc、100 项 Python SDK、42 项 UI-Core、31 项 UI unit 和 UI build 通过。Java 覆盖既有 block 契约、UI/Core 创建编辑、模板生成、新会话保存重开、未知字段保留及旧八轨道回归。最初 32 项中一项 fixture 缺少工作区版本导致 metadata 生成失败；修正后该类全部三项通过，未把重跑重复计数。

12 个不同浏览器场景/布局通过：Fabric block 中英文 × 两种宽度，以及双加载器 item 回归。真实 Core 提供空表单默认值，浏览器实际编辑、撤销/重新选择预览、保存并发出生成命令；语言切换保留草稿。字段标签与柱形朝向选项补齐中英文后再次检查四个 block 流程。fixture 与归一化 Core 输出一致；完整 editor 的既有 strict v1.0 差异仍登记在 27，不声称它通过。

最终 Native 运行：`output/p263-16/native/delivery-16446317325826582770`，阶段耗时 209.923 秒。工作区 `2b10712d-e6f1-4743-8f47-cb5f1df91c69`，产品进程 PID 5588、26852、9732 均由句柄确认 exit 0。生成、构建、两项业务验收、首次导出与新进程重开后的导出五个受管任务全部成功。

- T10：真实 `Block.getDrops` 返回方块物品；受控 datapack 经 `server.reloadResources` 后返回一个木棍。真实 registry-aware loot codec 对 `missing_drop` 返回未知注册键错误；该负向没有增加业务数量。
- T11：真实放置与轴向状态 Y→X；在测试世界固定位置保存 Z 轴方块。第二个服务器使用同一 JAR，加载逐文件哈希一致的已保存世界副本，不重新放置该方块，直接读取预期 Z 轴状态。
- 新服务器中 T10/T11 再次通过；掉落先从保存世界读到木棍，再真实 reload 为纸。保留原宿主世界、XML、受测 JAR，第二宿主使用独立回执路径。
- 八项 Native 负向明确拒绝且 revision 不变：未选择预览、重开后未选择、未开放类型/字段、非法纹理、硬度、朝向和堆叠数。未知字段经新进程修改和重开后保持原值。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 / 重开宿主 JAR | `0893467da05e535a98e0f661b50d61c5da998e06076b717a242e74f0572bbf34` |
| 首次 XML | `a57733de922ae24b98ed1d868bbc6da088dd1a5280d47f137933df75bed301c2` |
| 世界重开 XML | `dc7f7c474f413322b4173c7c8f1955fa37d84f8bbbdd8f6ce50ddd35805cad81` |
| `camp-kit-block-v1` T10/T11 清单 | `a5f1def54659cbe4feabf5e8ba6554e996689e9359e750a998fd8a106ac51e2b` |

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-16`。保存原始回执、XML/JAR、宿主源码与配置、测试结果、截图、世界复制哈希清单和精确源快照；不归档依赖缓存、世界数据和授权私钥。

## 失败与边界

开发期间修复了 Java lambda 捕获、fixture 存储 API/版本错误、block 编辑器漏接 inputSchema 和预览只读范围。失败日志与修正后的报告保留。实际独立服务器验收及保存重开一次通过。

本票证明开发宿主上的服务端方块行为；安装候选、玩家控制/渲染、Ubuntu 和 G2 十三项全范围验收仍由后续票完成。使用预先资格化依赖缓存，未声称冷缓存通过。
