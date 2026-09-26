# PRD17 原生健康计数与资产定位补验

本轮是 v32 游戏验收之后的前端定向收尾，不关闭整体 G17-B/D。

## 已观察结果

v32 在真实产品 JCEF 中，外部模型纹理拼写错误被 API/资产页识别，但全局底部与总览保持旧的零错误。失败及原文件恢复证据在 `output/stage17-v32/jcef/`；详见 [v32 续接记录](stage-17-v32-resumed-2026-09-24.md)。

v33 主 JAR SHA-256：`0aea4f02a512b0cc6c847a0f7e47cee68712d69833672cf556770621e045e8e0`。Windows 和 Linux 准备根包审计通过。与 v32 比较 2,178 个 Java 类及全部插件 ZIP 字节完全相同，产品改动仅前端三个文件，记录 `output/stage17-v33/impact-proof.json`。

v33 启动器在已恢复的 `final-acceptance/v32/jcef-scene-a` 测试副本重放：修订仍为 5，公开 API 错误数 0→9；进入资产页后底部显示 9，返回总览后当前错误卡/健康卡/底部均为 9，资产页区分 3 个有错误资产和 9 个缺失引用。`asset-error-api.json`、`asset-errors-zh.jpg/json`、`asset-error-overview-zh.jpg/json` 在 `output/stage17-v33/jcef/`。这部分原生刷新回归通过。

键盘 Tab 聚焦首个“定位到资产”，Enter 进入资产页，目标 block JSON 卡片获得焦点，但右侧仍显示默认 BBModel：`diagnostic-keyboard-focus-zh.jpg`、`diagnostic-asset-location-zh.jpg/json`。因此定位的详情一致性失败，不把焦点正确写成完整定位通过。

原资源已恢复，API 错误回到 0（`asset-restored-api.json`）。返回窗口后底部为 0，但资产列表还保留旧错误状态（`resource-restoration-current-state.jpg/json`），资产页缺少与全局健康一致的窗口 focus 刷新。一次导航动作被 computer-use 的用户输入守卫拒绝，未盲目重试；重新观察后记录当前状态。v33 产品最终经 Alt+F4 正常关闭，日志记录 `Exiting Copperbench`，PID 14308 已结束。

当前产品日志报告 Windows 120 DPI；工具截图为 1600×974。它们不能替代 PRD 指定的 100%/150%、1280×720/1920×1080 原生矩阵。

## 追加修复与验证

- `AssetBrowserView.tsx`：资产列表到达时，显式诊断目标优先于默认选中项，避免两处 effect 在同一轮把目标选中覆盖；窗口 focus 时重新索引，与全局健康刷新衔接。
- `stage17-language.spec.ts`：定位测试增加详情 stable ID 断言。原版本两项稳定失败（期待第二资产，实际第一资产），修复后两项通过，保留 `asset-location-before.log`、`asset-location-fixed.log` 与截图。
- `stage17-health-refresh.spec.ts`：覆盖仍在资产页时外部修复并返回窗口，资产计数和底部同时回到零，索引等待期间保留检查中提示。
- 相关语言、资产与刷新回归最终 **36/36 通过**，日志 `output/stage17-v33/selection-and-refresh-regression.log`。此前 v32 布局失败及修复证据继续保留。

上述追加修复晚于 v33 冻结，进入下述 v34；v33 的失败证据保留。

## v34 原生复验与双平台候选

冻结源 `D:/Hyper-V/Stage17-Linux/source-candidate-v34`，27,200 个文件；Windows/Linux 主 JAR SHA-256 均为 `5faa858c8c5fd548229c4395566f427085a8bdbeb95e23df10ed462b4d243f71`。随包 22 个文件、24 条链接、642 个 Schema 引用审计通过。2,178 个 Java 类及全部插件 ZIP 与 v32 字节相同，见 `output/stage17-v34/impact-proof.json`，因此复用未受前端修复影响的 v32 行为证明。

真实 Windows JCEF 已完成零错误→9 条资产诊断→修复回零：同一 revision 下总览、健康卡、底部数量一致；资产页明确显示 3 个有错误资产和 9 条缺失引用。Tab/Enter 定位后，右侧详情与目标 block JSON 的 stable ID 一致。外部修复并返回窗口后，总览与资产页均回零。证据为 `output/stage17-v34/jcef/` 中 `diagnostic-target-correct-zh`、`restored-overview-zh`、`restored-assets-zh` 等图片与原生可访问性快照；API 结果一并保留。

用户明确授权安装指定 v34 deb 后，于 `2026-09-24T06:34:20Z` 替换 Ubuntu v31；包 SHA-256 为 `c60d1409faa1ac16356d189b2915a80a9db2ce2a6cd4d06648ad4e0dca90c088`。安装前后受保护的旧模型、纹理、JAR 哈希相同。此前 v32 安装询问已作废。安装与完整运行证据在 `D:/Hyper-V/Stage17-Linux/guest-evidence-v34`。

已安装 v34 使用随包 SDK 重放 Windows 源快照，Fabric/NeoForge 1.21.1 各六项独立 JAR GameTest、可信导出、关闭重开均通过。正式任务分别为 `bbe1ef1f-baa5-4065-a5a1-db9d2a5f171b`、`499145fb-24e0-4cad-a4e1-c37b03be5c4f`。两份 JAR 分别与 v32 Windows 对应产物哈希相同，实际核对默认石头模型和两个名称键。见 `output/stage17-v34/linux-scene-a-proof.json`。本轮未用临时代理，但缓存已预热，不计冷网络验收。

## 真实显示缩放：已验范围与恢复

用户明确授权临时修改缩放并恢复。对原本 125%、2560×1440 的主显示器 2，分别启动新产品进程验证：

| 缩放 / 产品 DPI | 实际产品窗口 | 已观察范围 |
|---|---|---|
| 100% / 96 DPI | 1280×720 物理像素 | 中文零错误、资产错误与总览；英文总览 9 错误；计数和文字可辨 |
| 150% / 144 DPI | 1920×1080 物理像素（工具逻辑截图 1280×720） | 中文、英文 9 错误总览；计数和文字可辨 |

图片在上述 JCEF 目录，窗口物理尺寸另由 DPI-aware 只读进程查询记录于 `scale150-window-metrics.json`。既有进程跨 DPI 切换曾出现裁切，保存为 `scale100-live-dpi-transition-clipped`，重启产品后恢复；不把动态 DPI 切换记为通过。100% 下扩大窗口的拖动被工具边界检查拒绝，未执行，故不声称 1920×1080@100% 通过。这里也不是六夹具、两尺寸、两缩放全交叉矩阵。

测试结束已恢复 125%，设置界面截图确认，`output/stage17-v34/display-restored.json` 记录；所有本轮产品窗口正常退出且对应 PID 消失，显示设置窗口关闭。测试模型恢复到原始字节，API 错误数为 0。G17-A/B/C/D 继续开放；剩余范围按原 PRD 核对，不把主线程补验改称独立评估。
