# Stage 16：三个陌生 agent 模组试作

同一个独立 agent 仅使用分发包、公开文档和自己的新模组工程，在最终候选 `3c0aa090c3c4a53c0700d3bb173522e81ae91477` 上完成三个新案例，17 个不同的服务端行为用例通过。它未读 Copperbench 实现或旧试作。本文关闭 Stage16 B 段，不关闭安装回归或客户端门禁。

## 固定产物和真实结果

Windows ZIP SHA-256 为 `c62e96a26cbf3f5641012e4f1006933c64615fce5f67b6cca375bd8e483d9dc6`，EXE 为 `b4362ad2d744505a24964bc1b7948510f28a189242ccb7775df7403eac079db4`，应用 JAR 为 `2891c3c219c52b33b6194e75328d1cf7572978d57f15f1f508c3e66c5deb78e7`。启动辅助 JAR 为 `cb0d76e1495694112374b4203ce1a0be4de77ecee69cd5f5da22897435fcbc29`。

| 案例 | 最终用例 | 被测模组 JAR SHA-256 |
| --- | ---: | --- |
| `resonance_token` | 5/5 | `f3a1b4a3dd94f860eee2af0df034fcf556c380d2dbf5f423ea1a556f211c8939` |
| `tally_stone` | 5/5 | `76296fa2e85bbeb98a5634802cab7bc06cda6445930baec1b0bcf0bb25ddf35c` |
| `harvest_ledger` | 7/7 | `cd810e1121970525dd6e1d488849cc163fd49978bc6b5c1546c4fd4c8f95fc4f` |

三个最终任务均为 `packaged_jar`，进程退出码 0、`frameworkTests=0`、`sourceCurrentAtCompletion=true`。实际 JAR、原始 XML、产品 verification 和源快照清单一起归档。主任务复核了测试源码：物品覆盖实际物品入口与原版冷却管理器；方块和收获使用正常 ServerPlayer 的交互管理器；收获事件未被测试直接调用。

原版模拟玩家会将 `isCreative()` 固定为 true。agent 发现后改用正常 ServerPlayer，并断言夹具的实际模式，重跑了计数石。旧 5/5 保留为 superseded，不与最终 17 项重复计数。真实总执行数为 22。

## 成功率和使用成本

最初无授权创建及时返回正常拒绝，没有创建目录。用户从产品窗口实际签发一次授权后，`a78274ce` 的三个创建全部失败；agent 无法从当时的通用错误定位根因，主任务修复产品并交付新的固定候选。最终候选的三个案例全部成功。不能把这一过程写成最初版本无需维护者帮助便一次成功。

最终轮创建耗时为 69.6–73.8 秒，首条输出也在约 70 秒后；构建为 32.4–44.9 秒，首条流式反馈为 1.7–2.0 秒；最终 GameTest 为 151.3–178.6 秒，首条反馈为 1.6–1.9 秒。机器保留既有 Gradle/Minecraft 缓存，这些是单次观测，不能称为冷缓存基准或稳定性能分布。

一次合法授权覆盖三个工作区的创建、编辑、构建和测试，修复后沿用，没有逐操作审批。尚有两个明确体验缺口：bootstrap 无中间进度，`environment` 未直接给出映射方案，agent 需读真实 `build.gradle` 才发现应使用 Mojang 映射。独立 agent 的测试夹具修正属于模组开发工作；产品启动故障和当时不充分的诊断属于 Copperbench 的责任。

## 证据和验收边界

- [独立体验报告](../../evidence/stage16/2026-09-11/fresh-agent-three-mods/experience-log.md)，原始 SHA-256 `592ae9b4f6c00b04270702f00c24d1e1b56c0a019d08f8e6884e48efbc073738`。
- [需求先行的测试计划](../../evidence/stage16/2026-09-11/fresh-agent-three-mods/requirements-and-test-plan.md)、[逐用例结果](../../evidence/stage16/2026-09-11/fresh-agent-three-mods/case-results.json)、[各轮命令和介入统计](../../evidence/stage16/2026-09-11/fresh-agent-three-mods/trial-summary.json)。
- [106 个源码与构建输入文件](../../evidence/stage16/2026-09-11/fresh-agent-three-mods/source-delivery/README.md)，逐文件核对最终通过快照；干净导出本身尚未再次构建。
- [归档清单](../../evidence/stage16/2026-09-11/fresh-agent-three-mods/archive-manifest.json)记录原始及归档哈希；仅替换 16 个文件中的本机授权 ID，原始命令、失败与成功记录留在本机。记录中的绝对路径保留原执行语境，同名相对文件位于本归档。

actionbar 用例证明服务端调用参数；物品 codec、区块磁盘 NBT 与 SavedData 文件读回证明各自保存边界。它们不证明客户端可见反馈或整个 Minecraft 进程退出、重启及重进同一世界。首次客户端已运行到渲染阶段，但真实输入前 Windows 锁屏，随后为安装回归释放内存而结束该任务；该次不是客户端通过，也不是正常关闭证据。
