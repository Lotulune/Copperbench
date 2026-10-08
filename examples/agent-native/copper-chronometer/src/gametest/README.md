# Copper Chronometer：运行时验收

这些测试由 Copperbench `packaged_jar` 宿主加载最终模组 JAR 后执行。宿主只包含验收源码与测试依赖，不复制模组实现源码。入口：`dev.chronometer.acceptance.ChronometerGameTests`。

时间换算、相位边界、负数与极值、12/24 小时格式由 `src/contractTest/java/dev/chronometer/ClockReadingContractTest.java` 单独检查。这里的 12 个 `@GameTest` 聚焦真实 Minecraft 对象和服务端行为：

| 用例 | 断言范围 |
| --- | --- |
| `registeredItemAndRecipeLoadFromPackagedJar` | 最终 JAR 注册真实物品类、最大堆叠数为 1，配方 ID 已加载 |
| `preferencesBelongToIndividualItemStacks` | 默认 24 小时，独立物品与复制物品的偏好互不修改 |
| `preferenceUpdatesPreserveUnrelatedNbtAndComponents` | 保存偏好时保留第三方 NBT、同命名空间的无关字段及自定义名称 |
| `bothPreferencesSurviveItemSerialization` | 两种偏好经真实 ItemStack 保存、读取后仍有效，数据不共享 |
| `ordinaryUseReportsActualWorldTimeWithoutWritingPreference` | 真实 `Item.use` 回传当前世界时间、可翻译相位与剩余 ticks，普通读取不改 NBT |
| `sneakingUseTogglesModeAndChangesSubsequentReadout` | 潜行切换两种格式，后续普通读取采用已选格式，确认消息参数正确 |
| `itemItselfRejectsCooldownUntilExactlyTwentyTicks` | 直接调用物品时，0/19 ticks 拒绝，20 ticks 后允许；拒绝不修改偏好或发送成功反馈 |
| `spectatorsCannotReadOrMutateTheChronometer` | 物品自身拒绝旁观者读取与切换，不写 NBT、不启动冷却 |
| `offhandUseOnlyTogglesTheOffhandStack` | 副手操作只修改副手物品 |
| `playersHaveIndependentPreferencesAndCooldowns` | 两位玩家的冷却与物品偏好独立 |
| `netherReadingIsRejectedWithoutCooldownOrMutation` | 在真实 Nether Level 中拒绝报时，反馈明确，不写数据、不启动冷却 |
| `networkUseAndNaturalServerTicksCompleteTheCooldown` | 通过已连接 ServerPlayer 的 use 数据包触发行为，并等待真实服务端 ticks 自然结束冷却 |

轻量 `Player` fixture 和精确冷却推进方式参考仓库归档的 Stage 16 Resonance Token 验收；已连接玩家与数据包 fixture 参考 `examples/agent-native/wayfinder-bell/src/gametest/java/dev/wayfinder/acceptance/BellGameTest.java`。Chronometer 场景与断言为本次新写。测试不修改共享世界时间或系统 Locale，避免同批 GameTest 互相干扰。

Nether 用例要求宿主实际加载该维度；缺失时明确失败，不会将未执行的覆盖报告成通过。配方用例验证数据包配方加载，不表示实际玩家已完成工作台合成。

源码存在不代表运行成功。运行后检查 Copperbench 任务终态与 `verification`，确认 12 个有效验收用例均执行且通过、被测 JAR 哈希与当前输入一致。这些服务端测试不验收客户端图标、实际屏幕上的文字布局、声音听感、客户端预测分支或完整世界存档重进。
