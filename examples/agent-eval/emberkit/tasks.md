# Emberkit comparison tasks

These are the task texts given to every Agent run in the comparison that ADR-0019
relies on (first run 2026-11-16). Give them verbatim; the evaluation in
[README.md](README.md) checks the resulting mod JARs against them. Target:
Fabric 1.21.1, mod id `emberkit`, package `com.example.emberkit`, version 0.1.0.

## T1：结构化内容

1. 方块「余烬砖」（Ember Bricks），注册 ID `emberkit:ember_bricks`：石质，硬度 2.0，爆炸抗性 6.0；必须用镐才能挖掉并掉落（任何等级的镐均可）；挖掘后掉落自身；有方块物品，位于创造模式“建筑方块”标签页。
2. 物品「余烬粉」（Ember Dust），注册 ID `emberkit:ember_dust`：最大堆叠 64，位于“原材料”标签页。
3. 有序合成配方，配方 ID `emberkit:ember_bricks`，产出 4 个余烬砖，摆法：

   ```
   空     石砖    空
   石砖   余烬粉  石砖
   空     石砖    空
   ```

4. 方块和物品都要有可用的贴图与模型（16×16 占位贴图即可），以及 zh_cn 和 en_us 名称。

完成标准：能构建出模组 jar，以上内容都在 jar 里。

## T2：自定义行为

物品「余烬提灯」（Ember Lantern），注册 ID `emberkit:ember_lantern`：

- 不可堆叠，耐久 64，位于“工具与实用物品”标签页。
- 右键使用（逻辑只在服务端执行）：找出以玩家为中心、半径 8 格内除玩家自己以外的所有 LivingEntity。
  - 至少有一个目标：每个目标获得 200 tick 发光；物品损失 1 点耐久；物品进入 100 tick 冷却；在玩家位置播放任意原版音效。
  - 没有目标：不消耗耐久、不进入冷却，并在动作栏显示翻译键 `message.emberkit.no_targets` 的提示。
- 要有可用的贴图与物品模型，以及 zh_cn 和 en_us 的物品名与提示文本。

完成标准：能构建出模组 jar，以上内容都在 jar 里。
