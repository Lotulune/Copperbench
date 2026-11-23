# ADR-0020：接管现有 Fabric/NeoForge Gradle 工程的范围

- 状态：提议，待项目所有者确认（[待确认的问题](#待确认的问题)中的选择会写回本文）
- 日期：2026-11-24
- 细化：[ADR-0019](0019-agent-harness-and-asset-workbench.md) 决定 4；[ADR-0017](0017-native-first-agent-workbench.md) 当时把“接管任意第三方 Gradle 工程”留作单独决定
- 待办落点：[维护清单第 11 项](../remaining-work.md#下一阶段方向调整)

## 背景

ADR-0019 把产品的差异点定为环境与工具链、构建到游戏内的验证回路、资产治理、恢复点与权限边界，并要求这些能力也能用在不是由 Copperbench 创建的工程上。现在只能打开 `.mcreator` 工作区：桌面“打开工作区”和 `headless --workspace` 都要求 `.mcreator` 文件（`JcefWorkspaceOpenBridgeTransport`、`HeadlessProductLauncher`），Core 会话也必须附着在一个 MCreator `Workspace` 上（`MCreatorWorkspaceSession.attach`）。

按源码核对，现有能力分两类。

**只依赖工程根目录**，状态已经保存在根目录下的 `.copperbench/`：

| 能力 | 实现 | 状态位置 |
| --- | --- | --- |
| Gradle 构建、运行、导出与任务记录 | `GradleWorkspaceTaskGateway` | `.copperbench/task-runs`、`task-records`、`task-inputs`、`delivery-*` |
| GameTest 验收与已验证导出 | `GameTestSupport`、打包 JAR 宿主、`VerifiedArtifactExporter` | 根目录 `copperbench-tests.json`、`.copperbench/verified-exports` |
| 源码浏览与带哈希的编辑 | `WorkspaceSourceService(root, ownership, writable)` | 无 |
| 资产目录、引用图、预览、导入、移动、删除、Blockbench 任务 | `AssetWorkspaceService(root)` 及其服务 | `.copperbench/asset-leases`、`modeling-tasks`、`resource-index` |
| 本地历史与恢复点 | `JGitLocalHistoryService` | `.copperbench/local-history.git` |
| 任务授权、MCP 设置与审计、内置对话 | `TaskAuthorizationStore`（按目录授权）、`DesktopMcpRuntime`、`AgentChatStore` | 授权在用户配置目录；其余在 `.copperbench/mcp-*`、`automation-audit.jsonl`、`agent-chat` |

**依赖 `.mcreator` 或 MCreator 工作区模型**：

- 工作区身份与修订号。`ProductMetadataManager` 把 `workspaceId`、`revision` 和注册表投影写进 `.mcreator` 文件的 `dev.copperbench` 命名空间。
- 结构化元素、Procedure、变量、标签、语言键、声音事件、生成器模板，以及由生成器决定的文件归属（`workspaceSourceOwnership`）。
- 加载器迁移、版本副本、元素视觉预览。
- 生成器描述：加载器、Minecraft 版本、Java 版本，以及 GameTest 宿主固定的加载器坐标，都来自工作区设置里的生成器。

另有一处会误导用户：资产健康检查在没有自定义代码元素时，把工作区内的使用情况视为已完整评估（`AssetProjectionService.health`）。接管的工程里全是任意 Java 代码，不能沿用这条规则，否则资源会被错误地列为可清理候选。

## 决定（提议）

1. **范围。** 第一阶段只接管单模块的 Fabric 或 NeoForge Gradle 工程，提供上表“只依赖工程根目录”的能力：源码、构建、运行客户端或服务器、GameTest 验收与已验证导出、资产工具、恢复点、任务授权、MCP 与内置对话的原生路线。结构化元素、Procedure、注册表、生成器模板、加载器迁移和版本副本在接管的工程里不可用，界面与 MCP 直接说明原因，不提供降级替代。
2. **识别而不改写。** 打开时只读工程文件，识别加载器、Minecraft 版本和模组 ID：Fabric 读 `fabric.mod.json` 与 `gradle.properties`；NeoForge 读 `META-INF/neoforge.mods.toml`（1.20.1 为 `mods.toml`）与 `gradle.properties`。识别结果必须对应产品已支持的版本轨道，GameTest 宿主才会沿用该轨道固定的坐标。识别不出或版本不受支持时，工程仍可打开浏览和编辑，但构建与验收不可用，并说明缺什么。产品不修改 `build.gradle`、`settings.gradle` 或 wrapper；构建使用工程自己的 Gradle Wrapper。
3. **元数据放在 `.copperbench/`。** 新增 `.copperbench/workspace.json`，记录工作区 ID、修订号和识别结果，其余状态沿用现有的 `.copperbench/*` 位置。不在工程里写 `.mcreator` 文件：那会让 MCreator 生成器认为自己拥有文件并在生成时改写它们。
4. **所有文件都是手写文件。** 接管的工程没有生成器归属，`update_workspace_file` 可以编辑允许范围内的全部文本文件；生成器源码的保护规则不适用。
5. **使用情况按“未评估”处理。** 资产健康检查不为接管的工程计算可清理候选，删除资产总要确认受影响的引用（第 9 项的现有规则）。
6. **新的工作区类型。** Core 与 UI-Core 增加工作区类型 `gradle_project`，与 `mod`、`resource_pack`、`data_pack` 并列。客户端可以据此隐藏不可用的功能，旧客户端按未知类型处理。

## 待确认的问题

以下选择需要项目所有者确认，括号内是建议：

1. **`.copperbench/` 与版本控制。** 接管时是否询问用户，把 `.copperbench/` 加入工程的 `.gitignore`？（建议：询问；默认不改动工程文件。本地历史是一个独立的 Git 存储，不应进入工程仓库。）
2. **Gradle 用户目录与镜像。** 构建用工程环境默认的 Gradle 用户目录，还是产品的 `~/.copperbench/gradle`？后者可以复用产品的缓存、loopback 处理和国内镜像设置。（建议：默认产品目录，以保持“环境与工具链”这一差异点；可在设置中改用工程默认目录。）
3. **工程形态。** 是否支持多模块和多加载器工程，例如 Architectury？是否支持 Kotlin DSL 构建脚本？（建议：第一阶段只支持单模块；Kotlin DSL 只要识别信息来自 `gradle.properties` 和模组元数据就可以支持；多加载器工程留到第一阶段验证之后。）
4. **入口。** 桌面“打开工作区”是否同时接受文件夹？（建议：接受。选中的文件夹含 `.mcreator` 时按原有方式打开，否则按 `gradle_project` 识别。）

## 后果

- 需要新的会话实现，它不附着 MCreator `Workspace`，而是用 `.copperbench/workspace.json` 提供身份与修订号。现有 `WorkspaceApplicationService` 的任务、源码、资产与历史路径通过工作区根目录复用；元素与注册表操作对 `gradle_project` 返回“不支持”诊断。
- GameTest 宿主、任务授权、MCP 工具与 SDK 的调用方式不变，只是多了一种工作区类型。
- 识别规则只覆盖常见模板。超出模板的工程得到明确的“不支持”结果，而不是猜测出的版本。
- 现有 `.mcreator` 工作区、已发布版本的支持范围和兼容承诺不受影响。

## 未采纳的方案

- **为外部工程生成 `.mcreator` 文件。** 生成器会接管并改写文件，结构化层的限制会被带进原本不需要它的工程。
- **把元数据放在用户目录，按工程路径索引。** 工程移动或换机器后身份丢失，也无法随工程一起备份。
- **自动改写构建脚本，加入 Copperbench 插件或任务。** 这会改变用户工程的构建行为，而 GameTest 验收已经用独立的打包 JAR 宿主完成，不需要改写构建脚本。

## 实施顺序与完成标准

1. 识别与只读打开：源码、资产、本地历史。
2. 构建、运行与 GameTest 验收。
3. MCP、SDK 与内置对话的原生路线。
4. 桌面入口与界面。

完成标准沿用 ADR-0019 决定 7：用裸 Gradle 组在对照实验中产出的 Fabric 1.21.1 工程，原样由 Copperbench 打开，完成构建与 GameTest 验收，且不修改其构建脚本；同一任务的成本与验证深度和 `.mcreator` 路线一起记录。
