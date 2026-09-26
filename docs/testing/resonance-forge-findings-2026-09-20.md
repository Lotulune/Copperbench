# 共鸣工坊试作：Stage 17 需求依据

日期：2026-09-19—20。用途：保存下一阶段 PRD 所依赖的事实、复现步骤和证据边界。完整本地试作目录为 `output/resonance-forge-test/`；本文不依赖该目录被提交或随产品分发。

## 环境与结论

- 测试使用仓库已有编译输出，通过 `net.mcreator.Launcher`、产品 Python/Core 和真实桌面操作执行。
- 当时 HEAD：`06c372f760b449beeadb811567048438f5c5080e`；工作树非干净，未建立源码与全部已有编译类的一一对应证明。
- Windows x64；JBR 25；工作区 JDK 21；Fabric 1.21.1、Loader 0.19.3、Fabric API 0.116.15+1.21.1、Loom 1.17.19；Blockbench 5.1.6；已有依赖缓存。
- 交付 3 方块、3 物品、1 函数、1 多文件 Code 元素、6 配方、36 部件模型、2 种机器加工模式。
- 独立 JAR GameTest 14/14 通过，0 失败/跳过；客户端观察到模型贴图、空手/铜锭真实右键，重启同一存档后查询到保留的铜锭；第二次客户端正常退出任务成功。
- 评分：Agent 7.0/10，用户 6.5/10，均为五维等权评价；用户评分是 Agent 的真实界面评审，不是真人用户研究统计。

被测 JAR SHA-256：`5e71416a4dc707423fe1dde8cec18e1d11841aae94db0417f9fafd7187e88175`。

源码快照 SHA-256：`1addea9302eb76a747ec0dba5be1ca904f9dd02c210d5363816a5264aa57e765`。验收完成时 `sourceCurrentAtCompletion=true`；最后核对清单文件 `changedPaths=[]`。

## RF-01：方块声明值和实际定义分离

在空的 Fabric 1.21.1 工程中，以最新 revision 调用 `create_mod_element`，核心 payload：

```json
{
  "elementType": "block",
  "name": "resonance_forge",
  "initialValues": {
    "displayName": "Resonance Forge",
    "hasInventory": true,
    "inventorySize": 3,
    "inventoryStackSize": 64,
    "inventoryDropWhenDestroyed": true,
    "openGUIOnRightClick": false,
    "hardness": 3,
    "resistance": 6,
    "rotationMode": 1
  }
}
```

以上为 Core payload；Python SDK 的 `command` 通过 envelope 携带 revision，MCP 请求需按工具合同显式传入 `expectedRevision`。

结果为 `committed`，元素 `valid`。使用返回 elementId 查询编辑器：`hasInventory=true`、`inventorySize=3`、`hardness=3`、`rotationMode=1`。磁盘定义实际值分别为 `false`、`0`、`0`、`0`。初始方块 Java 只有 `super(properties.instabreak())`、`Shapes.empty()`，没有库存方块实体。

源码核对发现 [MCreatorWorkspaceMutationGateway](../../src/main/java/dev/copperbench/core/workspace/mcreator/MCreatorWorkspaceMutationGateway.java) 的 `newBlock` 初始化默认定义，`updateDefinition` 的 Block 分支只设置名称；通用转换分支还有保留 raw metadata、转换失败忽略的路径。需要在重建候选上检查完整映射链，不能仅依据一个分支推定全部类型都坏。

本地原始文件：`03-create.json`、`03-create.result.json`、`04-inspect.result.json`。最终机器已切换手写源码；其玩法测试通过不能证明原结构化字段路径通过。

## RF-02：合法原版资源被误报缺失

模型分别使用 `minecraft:block/cube_all`（2 处）、`minecraft:item/generated`（2 处）、`minecraft:item/handheld`（1 处）。`get_workspace_health` 返回 5 个 `MISSING_ASSET_REFERENCE`，目标被解析到当前工作区 `src/main/resources/assets/minecraft/...`。

对应 [AssetWorkspaceService](../../src/main/java/dev/copperbench/assets/AssetWorkspaceService.java) 的 `addReference` 在工作区索引找不到目标时直接生成缺失诊断。不能用“忽略所有 minecraft 引用”作为修复：合法引用与拼错引用必须分别验证。

本地证据：`10-health.result.json`、`jar-audit.json`、客户端模型截图。

## RF-03：错误数量口径不明确

总览卡片“诊断/错误”显示 0，项目健康显示 5 条/5 错误，资产侧也是 5 缺失。源码 [WorkspaceHub](../../ui-shell/src/components/WorkspaceHub.tsx) 的卡片使用 `elementCounts.invalid`，健康卡使用 `workspaceHealth.diagnostics.error`；[StatusFooter](../../ui-shell/src/components/StatusFooter.tsx) 从另一份诊断列表计数。

这是已观察到的不同数据口径被展示为同类“错误”问题，不应通过删掉健康诊断使数字凑齐。

本地证据：`screenshots/copperbench-workbench.png`、`10-health.result.json`。

## RF-04：探测错误分类待固定候选复核

`get_blockbench_environment({})` 成功识别 Blockbench 5.1.6。以下两个合法形状在本环境返回 `COMMAND_PAYLOAD_INVALID`：

```json
{"probeMcp": true}
```

```json
{"probeMcp": true, "endpoint": "http://127.0.0.1:3000/bb-mcp"}
```

当时该端口无社区服务。预期应是明确的连接状态。[BlockbenchEnvironmentService](../../src/main/java/dev/copperbench/assets/BlockbenchEnvironmentService.java) 已存在连接状态转换逻辑，传输构造位于后续 try/catch 之外；这只是排查入口，尚未证明具体抛错点。必须核对编译来源与 SDK/运行时差异后再确定根因。

本地证据：`01-console.log`、`02-create.result.json`、`04-inspect.result.json`。

## RF-05～07：体验与证明文件

- 模型副本、磁盘保存、候选、JSON/PNG 导出、路径映射、回导、绑定和构建都确实可用；当前引导要求用户理解它们的关系。`.bbmodel` 在资产详情是占位预览。
- 本次 Code 元素编译成功后，还需在主类 user-code 初始化区调用 `forge_runtime.init()`。这不是已证明的缺陷，而是原生工作流需要明确的示例和入口说明。
- 最终验收 JAR 在测试宿主 `run/mods`，早先 `build/libs` 曾是补齐资源前版本。本次人工按 verification 的 artifactPath/SHA-256 复制交付；建议产品提供直接导出所选已验证产物的入口。
- 首次客户端任务没有采集到终态，不能推断正常退出。第二次任务 `09d35b1a-a3bb-4718-979f-bba88bc5ec9d` 实际正常退出并最终 `succeeded`。
- 重启前后用户名不同；机器库存保留证明方块存档恢复，不证明玩家身份持久化。

## 用于开发的最小材料

| 目的 | 本地材料 |
|---|---|
| 原始问题复现 | `03-create.json`、`03-create.result.json`、`04-inspect.result.json`、当时元素定义 |
| 资源/诊断复现 | `10-health.result.json`、`screenshots/copperbench-workbench.png` |
| 模型往返 | `make_models.py`、`forge_export.json`、`atlas_export.png`、`model-*.json` |
| 原生复杂行为回归 | `deliverables/resonance-forge-source.zip`、`deliverables/verification.json`、`gametest-results.xml` |
| 客户端与存档 | `screenshots/minecraft-insert-copper.png`、`minecraft-reopened-inventory.png`、`minecraft-reopen.log`、`11-client-reopen.result.json` |

实施时整理脱敏、可复现的夹具到仓库既有测试/示例目录；原始日志与失败结果保留，不能只复制最后成功的手写工程。上述文件存在于本地试作目录，并非已提交的长期测试资产。

开发范围和验收条件见 [Stage 17 PRD](../../PRD-STAGE-17.md)。本记录不修改既有 Stage 16 或 Blockbench 专项的历史完成结论。
