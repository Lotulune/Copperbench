# v37 Ubuntu 连续建模与重开复验

已安装 v37 经产品 GUI 完成：现有元素 → 选择原 36 部件源模型 → 新建编辑副本 → 打开真实 Blockbench → 保存 PNG 和源模型 → 导出游戏 JSON → 确认磁盘保存 → 自动识别和预览 → 确认替换并回导 → 保持关联 → 从引导入口构建 → 正常关闭 → 随包 SDK 重开读回。

所有操作位于 `/home/stage17/final-acceptance/v37/model-c-linux`，由原 `/home/stage17/fixtures/fabric-1.21.1` 复制。原工程的源模型、游戏 JSON、PNG、原 Mod JAR 四项哈希在本轮始终不变。产品入口 `/usr/bin/copperbench`，主 JAR `59da390ea54a68e8d925b0ca79a1d9de36dc68915310d994dcff62275b9f66e5`。这是已安装开发候选的验收，不是新公开发行。

## 实际任务和文件

建模任务 `5ffad452-29ba-4fbd-ac57-a35654e0a163` 从目标 `resonance_forge` 创建。begin、finish、import_begin、import_complete 来自 GUI；重开后为 `imported`，关联为 `bound / structured_forge:custom/resonance_forge`，修订为 6。

真实 Blockbench 保存的 `edit/model.bbmodel` 包含 36 部件，SHA-256 `29c3c40c532518b565756493e641ff648905e2291dd95dceac6944eddac43abc`。PNG 保存后 Folder 被编辑器清空，按实际属性界面恢复 `block`，namespace 保持 `structured_forge`；源模型随后保存。游戏导出文件命名为 `m.json`，不依赖导出文件名推测工作区目标。

产品自动建议 `models/custom/resonance_forge.json` 与 `textures/block/atlas_export.png`，均位于 `src/main/resources/assets/structured_forge/`。未手工填写工作区目标路径。预览显示源模型需替换、游戏 JSON/PNG 内容相同；勾选保留恢复点的替换确认后应用。

| 任务 | 结果 | 范围 |
|---|---|---|
| `6816f726-e31f-4d87-8f60-45887e3b2ba6` | failed | 首次构建，缺少 Mojang 清单且来宾下载失败 |
| `ab47ae20-c0b5-45e5-af36-decacdea3de7` | succeeded | 补齐缓存后的产品构建，资源索引恢复可用 |
| `252bdb5b-56ca-44f2-b698-eb2ffac4a2f4` | succeeded | 回导后从建模引导入口构建修订 6，22 秒完成 |

最终 `build/libs/structured_forge-1.0.jar` SHA-256 为 `cb05312ba4cee03d4e800c078962f71e4a241dcad227aed5c6153ba89b343c47`，与此前 Ubuntu 已完成真实模型显示、保存重进及正常退出的 v14 Mod JAR 完全相同。JAR 内游戏模型 36 部件，JSON SHA-256 `c939f0bba616d44a73b25f3cf8b4aa466be6f0809d2d4ac6a3e86a5dd05d53c3`，PNG SHA-256 `2193d992e0b19a20f34a09745431cf24c01e1b8e544543a60299ad4355a67d67`，分别与真实编辑器导出及原文件一致。

## 失败与恢复记录

首次回导预览拒绝 `MODEL_RESOURCE_UNVERIFIED`，具体来源为 `runtime_dependency_artifact_changed`；没有提前覆盖工作区。错误在预览区域保留，较小控制台窗口需要滚动查看。首次产品构建又因 Mojang 元数据下载失败退出；错误 ID `43b526f1-36e9-493f-a1c3-c9561667df2c` 与任务 ID 不同，首次按错误 ID 调用 `get_task` 得到不存在，随后从公开 recentTasks 取得正确 ID，未修改任务历史。

离线 `gradlew help --stacktrace` 定位缺失的 `mojang_versions_manifest.json`。宿主从 Mojang 官方端点获取清单和 1.21.1 元数据，校验版本元数据 SHA-1 `eb17d0d5933eec4a056c9f649db1e33c69622785`，现有 client/server JAR SHA-1 均匹配官方声明。新旧 downloads 与 libraries 定义相同；仅补齐元数据缓存，旧版本元数据另存备份。离线准备检查随后通过，再由产品正常构建成功。此恢复不证明 Ubuntu 直连 DNS/下载正常，不计作冷缓存验收；没有改网络、代理或产品实现。

VM 控制台 `type_text` 会贴入旧来宾剪贴板内容；首次插件选择改用可见文件夹导航，建模保存目录改用产品的复制编辑目录按钮和来宾粘贴，文件名/Folder 则用已观察字段中的按键输入。命名未确认前没有保存。另有滚动定位和截图滞后重观，多次桌面被用户使用时停止输入，收到新授权后继续。这些输入工具介入与产品结果分开保留。

## 正常关闭与复用边界

产品窗口于 2026-09-24 18:40:19 UTC 请求关闭，日志记录保存工作区、退出任务与 18:40:20 `Exiting Copperbench`；PID 91499 消失，Blockbench 进程也不在。之后通过已安装启动器与随包 SDK 新开会话，模型回导/绑定、两次成功构建及首次失败历史均保持，JAR 内容和哈希一致。成功构建后底部从 223 条部分警告回到 0，未把历史构建失败计入当前资源错误。

本轮没有新增 Linux Minecraft 输入；复用的是**同一 Mod JAR** 的既有客户端证据。v14 建模、导入、任务记录服务与 v36 的类身份复用依据见 [v36 工坊记录](stage-17-v36-workshop-2026-09-24.md)，v36→v37 的 2,178 个 Java class 全部相同；当前变化的前端引导与任务健康刷新已经在本轮实际检查。Windows 当前候选客户端退出及模型玩法另见 [v37 健康刷新](stage-17-v37-task-health-2026-09-24.md)和[v36 连续建模](stage-17-v36-model-c-2026-09-24.md)。这些都不改称独立 Agent 试作通过。

原始证据：`output/stage17-v37/model-c-launch.json`、`model-c-exported.json`、`model-c-preview-attempt.json`、`model-c-imported.json`、`model-c-built-proof.json`、`model-c-reopened-proof.json`、`metadata-cache-repair.json`、首次失败 task JSON、`model-c-native.log`、离线缓存准备日志，以及 `linux-*.jpg`。整体 G17-D 仍待独立建模客户端补验与最终报告归并。
