# PRD17 收尾复核（2026-09-26）

> 最终结论已更新至 [v38 收尾](stage-17-v38-closeout-2026-09-26.md)和[门禁结论](stage-17-final-gate-review-2026-09-25.md)：授权安装与客户端跨进程世界恢复/退出/清理完成，最终完整回归 924 通过、59 跳过、0 失败/错误，开发候选验收通过、未发布。下文保留早先时点的证据与待办。

> 后续推进见 [v38 候选与重启补验](stage-17-v38-closeout-2026-09-26.md)：双平台各八轨默认值验证通过，新 deb 已准备未安装；全量回归暴露的持久化夹具已补齐并增加显式上游校验，最终 24 项通过。桥恢复后新会话已记录 `unverified` 并清理，客户端仍等待前台焦点。下文保留此前证据范围。

主代理验收结论：v37 独立模型路线的同进程保存重进和正常退出通过；PRD17 整体仍未关闭。本轮新修正的源码与已测 v37 安装候选分开记录。

## 源码与回归

`WorkspaceApplicationService.defaultElementValues` 修正两项实际默认值错误：

- `structure` 不再自动加入顶层 `poolName`。存储类只在 `Structure.JigsawPool` 中声明该字段；旧默认值导致创建请求被严格适配器拒绝。
- `keybind` 分类默认值改为存储类的 `misc`，避免原 `key.categories.misc` 被现行枚举校验拒绝。

`WorkspacePersistenceCompatibilityTest` 的旧夹具曾通过公开创建/编辑入口写入 `source`、`customFlag`、`pluginFutureField` 等无适配器字段，与 Stage 17 的未知字段只读合同冲突。现改为合法创建/显示名编辑；未知字段通过磁盘上的旧元数据夹具注入后重建会话，逐一验证 37 类元素拒绝改写未知字段且不推进 revision、合法编辑后重开仍保留未知元数据和定义根字段。Blockly XML、按键、植物、流体、实体夹具补充其实际必要输入。

曾短暂尝试放宽创建时的未知字段限制，以及为植物自动补药效，复核需求后均撤回。最终未放宽未知字段校验，也未改植物缺少药效时的拒绝语义。

最终定向运行：

| 测试类 | 用例 | 失败/错误/跳过 |
|---|---:|---:|
| WorkspacePersistenceCompatibilityTest | 24 | 0/0/0 |
| Stage17MappedTypesTest | 19 | 0/0/0 |
| Stage17BlockConsistencyTest | 11 | 0/0/0 |
| Stage17ProcedureBodyContractTest | 9 | 0/0/0 |
| 合计 | 63 | 0/0/0 |

命令为 `pwsh -NoProfile -File scripts/run-gradle-external.ps1 test --no-daemon`，分别通过 `--tests` 选择上述四个类。完整日志：`output/stage17-closeout-tests-2026-09-26-final.log`；Gradle 原始日志：`.tmp/gradle-external/648e421f886d44e399c2e79a72f8c174.log`。XML 和 SHA-256 汇总已保存至 `output/stage17-closeout-review-2026-09-26/`。`git diff --check` 通过。本次不是全量测试运行，也不是新版安装候选验收。

## 6sol 桌面执行与主代理验收

独立报告位于 `D:/Hyper-V/Stage17-Linux/independent-eval/model/v37-client-closeout/resumed-2026-09-25/closeout-2026-09-26/report.md`，同目录保存公开任务、日志、进程回执、指标和候选哈希。主代理复核 12 项证据文件哈希全部一致；游戏主会话 13 张观察图的哈希均与原始回执相符。

固定 v37 Windows 产品，原 model 工程，世界 `PRD17 Scene C v7`，PID `3500`，玩家 `Player369`，本轮 task `89fb42f8-4f81-45a2-b05e-7f6cb67b69f9`。主代理亲自查看首入与保存后重进的图片：棕色、约四分之三高度的模型、位置/视角和快捷栏数量 3 保持一致；日志记录同玩家相同坐标再次登录及两次全维度保存。

- 游戏会话 `2bc493e6-ba78-4753-a3f3-5b926c018303`：10 个短批次 completed，已 detach；输入 10,185.5 ms，13 次截图合计 1,461.08 ms。
- 退出会话 `19f82258-1a75-4fbb-9978-3eb0d218a049`：标题截图确认点击位置在 Quit Game 内；回执原样保留 `interrupted / completed_actions=1 / WINDOW_NOT_VISIBLE`，输入 799.32 ms。主代理只读复核 PID 已退出，日志有 `Stopping!`，同一 task 的公开恢复记录为 `succeeded`，结束时间 `2026-09-25T20:26:41.597506100Z`，诊断为零，日志有 `BUILD SUCCESSFUL`。综合证据支持本轮正常退出。
- 客户端任务从受理至终态约 470 秒；一次超过 2,000 ms 的请求在输入前被拒绝，未计为真实输入。Jev 调用为零；不据此宣称速度提升。

本轮无新放置操作，复用了历史放置的世界状态；没有在本轮正常退出后启动新进程复验保存状态，不宣称跨进程玩家库存连续。旧 task `893118e2-cc0e-40fd-80f7-2fc2ac867428` 的公开终态仍未知。

主代理的文件审查结论不冒充 `mc_record_verification` 回执：Minecraft MCP 工具组在本轮从两个代理可调用列表中消失。项目 `.codex/config.toml` 仍为 enabled；退出会话未完成 `mc_detach` 和 MCP 验证记录。需要重载 Codex MCP 连接，恢复后核对旧 session 是否有效再处理，不能伪造清理成功，也没有另起输入控制器。

## 尚需完成

1. 恢复 Minecraft MCP，完成可执行的验证记录与会话清理，补同一世界的退出后新进程恢复检查；旧失败/失联记录保持不变。
2. 将两处默认值修正纳入新固定候选并完成相应候选验证。当前客户端证据只属于 v37，不能代表这些源码修正已安装通过。

没有提交、推送、创建工作树、安装或发布新候选；`product-status.json` 未推进。
