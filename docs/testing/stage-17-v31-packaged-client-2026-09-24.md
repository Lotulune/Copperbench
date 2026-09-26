# Stage 17：v31 Windows Fabric 被测 JAR 客户端验收

已在 Windows Fabric 1.21.1 客户端验证场景 A 主方块的真实右键放置、模型显示、朝向、方块库存跨进程保存，以及两次正常退出。客户端使用服务端六项 GameTest 通过的同一 Mod JAR；这不是全部客户端资源或整体 PRD17 验收通过。

## 产物与入口

使用 v31 `copperbench.exe` 和随包 Python SDK，主 JAR SHA-256 为 `c3b822d6deef37ac553f41358ee51c8e21996d378355d9f88b77927130abf499`。由[公开准备脚本](../../scripts/prepare-client-trial.py)在已授权目录中新建 `D:/Hyper-V/Stage17-Linux/final-acceptance/client-scene-a-fabric`，空宿主的 Mod ID 为 `client_trial_host`；`run/mods` 只放入服务端已验证的 `stage17_structured` JAR，SHA-256 `a7d9ccd4732ce460ef3f282038683ba50e6aa5f43c4d1c8e69b24cea6f6849d5`。没有复制被测模组实现源码进宿主。

[会话脚本](../../scripts/stage17-packaged-client-session.py)通过公开 `run_client` 启动并持续保有工作区会话；运行前后校验部署集合和 JAR 哈希，正常退出后另开会话读回任务终态。观察采用实际调用成功的 Minecraft Control 0.2.0；每次先核对 PID、进程命令行和游戏目录，再绑定对应窗口。

| 运行 | 产品任务 | 游戏 PID | 结果 |
|---|---|---|---|
| 首轮 | `ed84ccd9-ae48-41f8-ac8f-77b653298f36` | 36232 | 真实放置、准备库存、保存退出；任务 succeeded |
| 重启 | `669943a4-e2fe-4980-8d88-fc7ae91e87ca` | 31500 | 同存档恢复、只读库存查询、保存退出；任务 succeeded |

## 实际观察

在客户端创建专用超平坦创造世界 `PRD17 v31 Scene A`。`/give` 领取方块和 `/tp` 定位是准备命令。随后通过真实鼠标右键放置，画面出现棕色导入模型，没有紫黑缺失贴图。F3 确认目标为 `stage17_structured:acceptance_forge`，坐标 `(0,-60,1)`，朝向 south；玩家面向 north。

`/item replace block 0 -60 1 container.0 with minecraft:diamond 7` 用于准备持久化数据，随后独立 `/data get block 0 -60 1 Items` 显示 `count: 7, Slot: 0b, id: "minecraft:diamond"`。这不是 GUI 库存操作。通过菜单保存回标题再正常退出，日志记录全部维度保存和 `Stopping!`，进程消失，产品任务 succeeded，重开读回一致。

新进程用已校准的菜单配置筛选并核对唯一名称，再进入同一世界。原模型仍在，F3 的坐标与 south 朝向不变。第二进程只执行库存读取，没有写入；画面与日志再次返回七个钻石。两轮用户名分别为 Player545、Player318，因此只证明世界方块库存恢复，不宣称同一玩家 UUID 或玩家库存恢复。

重启后的保存退出由一次 `mc_menu_flow(save_and_quit)` 执行。工具在最后关窗瞬间报告 `WINDOW_NOT_VISIBLE`，不能只凭此回执断言正常关闭；后续独立核对游戏日志的全部维度保存、`Stopping!`、PID 31500 消失和公开任务 succeeded 后才记录通过。两个任务的最终 JAR 身份检查相同，MCP 已 detach，最后窗口列表为空。

## 输入异常与校准

初次聊天键没有打开聊天框。遵照用户指示，先 detach，再用 Computer Use 对已观察的目标窗口点击一次，随后交回 MCP；仍没有预期响应。Computer Use 观察到输入法候选内容，第一次 Esc 消除候选，第二次 Esc 正常进入游戏菜单。切换本窗口英文输入状态后，MCP 的聊天、命令与真实右键正常工作。重启后同样通过取消候选及切换输入状态恢复。没有修改系统全局输入法配置，也不能据此推断所有历史失焦的根因。

菜单配置由已观察的标题、世界列表、暂停和游戏 HUD 四张图片校准，尺寸 854×480、英文菜单，精确世界名为 `PRD17 v31 Scene A`。重启进入世界的本地流程耗时 6.64 秒，仅证明菜单导航。另一次 F3 请求因帧龄 127.6 秒超过 120 秒而拒绝；重新观察后操作成功，拒绝记录保留，没有放宽守卫。

四个 MCP 控制会话共 35 个本地输入批次、60 张桥内观察图片、1 次过期帧拒绝、0 次 Jev 调用；输入耗时合计 20.58 秒，全部截图采集合计 3.05 秒，输入批次含截图合计 24.46 秒。37 次动作/菜单/显式观察请求包括菜单内产生的多张本地图像，其余生命周期、日志和验证调用另有工具记录。从第一次 attach 到最后 detach 共 1690.27 秒，包含模型调用间隔、输入法排查、校准和进程重启；不得用局部菜单耗时制造端到端提速结论。Computer Use 另有一次点击和两次 Esc，不混入 MCP 输入耗时。

## 保留问题与证据

- 游戏显示 `block.stage17_structured.acceptance_forge`，而被测 JAR 的 `en_us.json` 写入的是 `item.stage17_structured.acceptance_forge`。这是直接观察到的翻译键不一致，待定位并修复；不宣称方块名称显示通过。
- 辅助 `default_cube` 的 block/item 模型在被测 JAR 中缺失，客户端日志报告无法加载。它的服务端默认碰撞用例通过不证明视觉可用；本轮主方块已有独立导入模型。此问题仍待处理。
- 第一人称物品模型较大；本轮没有美术质量、声音、完整碰撞移动、真实库存 GUI、NeoForge 客户端或 Ubuntu 客户端的通过结论。

主交互图片在 `output/minecraft-validation/4a9279aa-b99b-4af5-ac29-f80130a6a18c/`：`frame-e6455b0a-936d-4973-99a5-a84871b7fc7c.png` 为真实放置，`frame-452c541b-e4bb-4ce4-a56a-7d8a745064ab.png` 为位置/朝向，`frame-a0261c67-8c3a-4798-9166-62ff0c02d676.png` 为保存前库存。

重启证据在 `90a26ef7-e14a-4f1a-9667-ec86b1776aeb/`：`frame-171be762-19ef-4208-b381-f7eaa148cf7d.png` 为只读七钻石结果，`frame-d01901f8-5bba-4e92-aecb-14b718a73864.png` 为位置/朝向。各会话保留原始 `actions.jsonl`、日志、主 LLM 验证判断及桥生成的哈希清单。产品任务与重开回执在 `stage17-v31-scene-a-client-first/`、`stage17-v31-scene-a-client-second/`；汇总清单为 `stage17-v31-scene-a-client-proof.json`。

G17-A/B/C/D 仍开放，本次证据不替代真实 Blockbench 往返、其他轨道、语言/JCEF 矩阵、场景 B/C/D 或独立试作。
