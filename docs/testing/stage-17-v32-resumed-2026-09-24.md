# PRD17 v32 续接验收

2026-09-24 恢复工作。沿用暂停交接的原 PRD 范围，复用有效历史证据，只补缺口和受影响验收。G17-A/B/C/D 尚未整体关闭。

## 候选审计

复用已完成的 v32 构建，没有重新打包产品源码。冻结源 27,196 文件逐项 SHA-256 核对无变化；22 个随包文件与冻结源一致、24 个相对文档链接有效、642 处 Schema 引用可解析。Windows 与 Linux 准备根主 JAR 均为 `4066f3acb35271579e27d02ea1360920f228d79d0a1413e129c54551267cb32e`。

原始证明：`D:/Hyper-V/Stage17-Linux/candidate-v32-proof.json`。Linux 传输归档 SHA-256 为 `bad911e888c5aed4493274134a548294efc836a0239b261bd6ad8b4819c6628b`，最终 deb 为 `/home/stage17/copperbench-stage17-v32-amd64.deb`，SHA-256 为 `fb2b249d1a0799659429ba34458d519ac9ac4ca63aa2ace725e4785e5d1c170c`。准备阶段核对已安装主 JAR 仍为 v31；安装许可待用户确认。

VM 的 IPv4 SSH 超时，已固定主机密钥的 IPv6 通道可用。本轮候选传输和 deb 准备走 IPv6；未调整 DHCP、持久代理或网络配置。Windows 公开 `list-authorizations` 确认已有测试授权仍 active，覆盖 `final-acceptance` 下的 create/edit/build/test/run_client。

## Windows 场景 A

通过 v32 启动器，在 `D:/Hyper-V/Stage17-Linux/final-acceptance/v32/` 下为 Fabric、NeoForge 1.21.1 分别从空目录新建工程。随包 SDK 完成纯结构化方块创建、非法类型的原子拒绝、硬度更新、API 模型回导与关联、关闭重开、生成、构建和隔离 JAR 验收。没有手写 Java 修复模组实现。

两个 Loader 各六项 GameTest 全通过，failed/skipped/frameworkTests 均为零，进程退出 0，`sourceCurrentAtCompletion=true`。用例沿用 v31 的硬度/碰撞、默认碰撞、放置/旋转、库存序列化、破坏掉落和模型资源断言。

新增定向断言验证：默认方块实际定义为 renderType=10、texture=minecraft:stone；被测 JAR 的默认模型以 `block/cube` 为父模型，六面及粒子均引用 `minecraft:block/stone`；两个方块的 en_us 名称均使用 `block.stage17_structured.*` 键。主模型 wrapper、几何和 PNG 哈希仍验证。

证据：`output/stage17-v32/scene-a-fabric/`、`scene-a-neoforge/`。本轮脚本为 `scripts/stage17-structured-acceptance.py`，新增候选主 JAR 限定及可选视觉默认值断言。v31 原脚本已归档到 `output/stage17-v31/harness/stage17-structured-acceptance.py`，原 SHA-256 `5be1147a96d1bb20465beef550df3b41fe0c13a82422baf5468bec0fde81a8f5` 保持；v31 清单只更新脚本定位。

两端关闭重开后均读回原验收任务，并通过 `export_workspace(verifiedTaskId=...)` 导出，结果均为 `passed_current_input`。Fabric 被测 JAR SHA-256 为 `18674c43e0356d90d4c60476afaeb5a1e80a9f4abd3a60dca3d14b96d7e92f1c`；NeoForge 为 `277a910a76cd997e65ee5ed5739aa7452c653172b21c7e91bd72a29630471ea8`。各自 `verified/` 与 `delivery/` 保留原件副本。

## Windows Fabric 客户端定向复验

公开准备脚本新建空客户端宿主，只部署上述 Fabric 已测 JAR。将旧测试世界的 27 个文件逐项核对后复制到新宿主，保留世界显示名 `PRD17 v31 Scene A`；这是 v32 的独立测试副本，名称不代表本次使用 v31 产品。源存档未被作为写入目标。

客户端任务 `49bc2670-3b1f-441b-ae5c-3578bae6f360`、PID 25704、玩家 Player265。主模型正常显示；`/time set day` 与 `/give` 仅作准备。选择默认方块后 HUD 显示 `Default Collision Cube`；实际潜行右键放置后，F3 确认 `(0,-60,2)` 为 `stage17_structured:default_cube`，显示石头贴图。切回主方块时 HUD 显示 `Structured Acceptance Forge`，不再显示原始翻译键。

截图位于 `output/minecraft-validation/9ac36917-c96e-4f7e-b3dd-14f19ba91b5c/`：`frame-98715590-7e98-4bfc-9128-75a3994b0579.png` 为放置后目标身份与模型，`frame-924425a9-9540-44be-bc5d-05221f000248.png` 为默认方块名称，`frame-769de6a8-4848-4f69-a0a9-5cd47e884f35.png` 为主方块名称。主 LLM 已使用 `mc_record_verification` 记录明确范围的通过判断。

旧菜单模板在初始标题识别失败且未发输入，改用观察过的屏幕导航。根据本轮实际查看的四张截图重新校准模板后，一次 `save_and_quit` 完成保存并关闭窗口。独立核对全部维度保存、`Stopping!`、PID 消失，以及公开 SDK 任务 succeeded 和重开读回；运行后的部署 JAR 哈希仍通过。证据在 `output/stage17-v32/client-visual/`，控制桥已 detach，最终窗口列表为空。

保留一次初始失焦、一次旧模板拒绝、一次过期帧拒绝。输入法取消候选并切换本窗口英文输入后聊天生效；不据此断定此前全部键盘问题的根因。主控制会话共 14 批输入（含菜单内批次）、18 次截图、1 次过期动作拒绝、0 次 Jev；输入 8.81 秒、截图 1.11 秒、批次含截图 9.93 秒，attach 至 detach 895.49 秒。另一次初始失焦会话 19.48 秒、无输入；Computer Use 的窗口激活与安全区域点击单列，不算 Minecraft 输入批次。模型/工具往返与准备等待包含在端到端时间中，不宣称加速。

本轮不重复宣称库存 GUI、同玩家重进或双平台客户端通过；存档恢复的历史范围仍见 v31 记录。汇总哈希与控制统计见 `output/stage17-v32/resume-proof.json`。

## 剩余范围

- v32 Windows Fabric 名称、默认模型及正常退出已通过；其余客户端缺口按原 PRD 范围补验。
- v32 Linux 安装入口及受影响双平台验证；已完成 deb 准备不计安装通过。
- 真实 JCEF 诊断、语言、缩放与定位矩阵；双平台连续 Blockbench 流程的缺口。
- 复核场景 B/D、Code 和独立试作的历史证据及源码影响，仅补未覆盖部分。已有独立字段与交付成功报告保留原候选归属，建模报告仍有客户端未闭环范围。

## 真实 JCEF 新反例与定向修复

v32 启动器打开新的 `final-acceptance/v32/jcef-scene-a` 普通输入副本。零诊断状态下，中文/英文总览、健康卡、底部与同一桌面的公开 SDK 查询一致。随后只把自定义模型的纹理 ID 改成 `acceptance_atlas_typo`：API 返回 9 条当前资源诊断（六个面、纹理声明及两个继承引用），资产页完成索引后显示 3 个有错误的资产、9 个缺失引用，但底部和返回总览后仍显示 0 错误。这是实际计数刷新缺陷；该单元记失败，不能用 mock 通过覆盖。

证据 `output/stage17-v32/jcef/`：`asset-error-api.json`、`asset-errors-stale-footer-en.jpg/json`、`asset-error-overview-stale-en.jpg/json`。注入前原字节保存在 `asset-original.json`，结束前已恢复，`asset-restored-api.json` 确认错误数回到零。界面语言恢复中文，产品经 Alt+F4 发起正常关闭。

源码修复限定三处前端文件：`WorkbenchContext.tsx` 在导航与资产重索引完成时更新全局健康快照，索引等待期间显示检查中，过期异步健康响应不能覆盖新响应；`AssetBrowserView.tsx` 将资产数量标为“有错误/有警告的资产”，与诊断条数分开；`global.css` 将这四个计数排为两列以保留英文可读性。

新增 `stage17-health-refresh.spec.ts` 用未改变 revision 的外部变化覆盖 0→9→0、延迟索引期间的检查中提示、底部键盘跳转和计数范围。最初两项行为用例通过；相关语言/资产/刷新回归共 36 项通过，生产构建通过。增加布局检查后发现较长英文在三列窄栏溢出，首次两项失败保留于 `health-refresh-layout/`；改成两列后两项通过，证据 `health-refresh-layout-fixed.log` 和对应截图。新增测试不是原生 JCEF 修复后通过证明。

修复已冻结为 `D:/Hyper-V/Stage17-Linux/source-candidate-v33`（27,199 文件），开始 `exportWin64 prepareDebLinux64 --offline --max-workers=1`；进度与结果在 `output/stage17-v33/build.log`。v33 比 v32 的产品改动仅上述三处前端，不重新计算 Java/游戏验证的已通过部分；最终仍须用新候选复验真实 JCEF 反例。v32 Linux 安装询问尚未收到答复，未替换 VM 安装。
