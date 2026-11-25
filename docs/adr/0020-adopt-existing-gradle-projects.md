# ADR-0020：接管现有 Fabric/NeoForge Gradle 工程的范围

- 状态：已采纳。决定 7 至 10 由项目所有者于 2026-11-24 确认，其余随 [#184](https://github.com/Lotulune/Copperbench/pull/184) 于同日合并采纳；实施进展见文末
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

## 决定

1. **范围。** 第一阶段只接管单模块的 Fabric 或 NeoForge Gradle 工程，提供上表“只依赖工程根目录”的能力：源码、构建、运行客户端或服务器、GameTest 验收与已验证导出、资产工具、恢复点、任务授权、MCP 与内置对话的原生路线。结构化元素、Procedure、注册表、生成器模板、加载器迁移和版本副本在接管的工程里不可用，界面与 MCP 直接说明原因，不提供降级替代。
2. **识别而不改写。** 打开时只读工程文件，识别加载器、Minecraft 版本和模组 ID：Fabric 读 `fabric.mod.json` 与 `gradle.properties`，`gradle.properties` 没有 `minecraft_version` 时接受构建脚本中唯一的字面坐标 `com.mojang:minecraft:<版本>`（Agent 手写的工程常这样声明，例如 `examples/agent-native/survey-pulse`）；NeoForge 读 `META-INF/neoforge.mods.toml`（1.20.1 为 `mods.toml`）与 `gradle.properties`。识别结果必须对应产品已支持的版本轨道，GameTest 宿主才会沿用该轨道固定的坐标；预览轨道（当前为 26.3）的验收证据只覆盖生成的工作区，因此不作为接管工程的目标，按版本不受支持处理。识别不出或版本不受支持时，工程仍可打开浏览和编辑，但构建与验收不可用，并说明缺什么。产品不修改 `build.gradle`、`settings.gradle` 或 wrapper；构建使用工程自己的 Gradle Wrapper。验收宿主是产品自己的工程，使用该轨道固定的 wrapper 与插件：固定的 Loom 1.17.19 声明的 Gradle 插件 API 是 9.5.0，实测用 Gradle 8.8 的 wrapper 无法解析它，而现有工程常用 Gradle 8 和更早的 Loom。
3. **元数据放在 `.copperbench/`。** 新增 `.copperbench/workspace.json`，记录工作区 ID、修订号和识别结果，其余状态沿用现有的 `.copperbench/*` 位置。不在工程里写 `.mcreator` 文件：那会让 MCreator 生成器认为自己拥有文件并在生成时改写它们。
4. **所有文件都是手写文件。** 接管的工程没有生成器归属，`update_workspace_file` 可以编辑允许范围内的全部文本文件；生成器源码的保护规则不适用。
5. **使用情况按“未评估”处理。** 资产健康检查不为接管的工程计算可清理候选，删除资产总要确认受影响的引用（第 9 项的现有规则）。
6. **新的工作区类型。** Core 与 UI-Core 增加工作区类型 `gradle_project`，与 `mod`、`resource_pack`、`data_pack` 并列。客户端可以据此隐藏不可用的功能，旧客户端按未知类型处理。
7. **版本控制（已确认）。** 接管时询问用户是否把 `.copperbench/` 加入工程的 `.gitignore`，默认不改动工程文件。本地历史是独立的 Git 存储（`.copperbench/local-history.git`），不应进入工程仓库。
8. **Gradle 用户目录与镜像（已确认）。** 构建默认使用产品的 `~/.copperbench/gradle`，以复用产品的缓存和网络处理；可在设置中改用工程环境默认的 Gradle 用户目录。国内镜像只通过产品 Gradle 用户目录中的 init 脚本生效：现有的 wrapper 改写（`ChinaMirrorService.applyToWorkspace`）不用于接管的工程，否则违反决定 2。
9. **工程形态（已确认）。** 第一阶段只支持单模块工程。识别信息来自 `gradle.properties` 和模组元数据时，支持 Kotlin DSL 构建脚本。多模块与多加载器工程（例如 Architectury）留到第一阶段验证之后。
10. **入口（已确认）。** 桌面“打开工作区”同时接受文件夹：文件夹含 `.mcreator` 时按原有方式打开，否则按 `gradle_project` 识别。

## 后果

- 需要新的会话实现，它不附着 MCreator `Workspace`，而是用 `.copperbench/workspace.json` 提供身份与修订号。现有 `WorkspaceApplicationService` 的任务、源码、资产与历史路径通过工作区根目录复用；元素与注册表操作对 `gradle_project` 返回“不支持”诊断。
- GameTest 宿主、任务授权、MCP 工具与 SDK 的调用方式不变，只是多了一种工作区类型。
- 识别规则只覆盖常见模板。超出模板的工程得到明确的“不支持”结果，而不是猜测出的版本。
- 本地历史不得在工作区根目录写 `.git` 链接：此前打开没有仓库的工作区会写入该链接，Git worktree 与子模块的链接会被替换（[#186](https://github.com/Lotulune/Copperbench/pull/186) 修复）。
- 现有 `.mcreator` 工作区、已发布版本的支持范围和兼容承诺不受影响。

## 未采纳的方案

- **为外部工程生成 `.mcreator` 文件。** 生成器会接管并改写文件，结构化层的限制会被带进原本不需要它的工程。
- **把元数据放在用户目录，按工程路径索引。** 工程移动或换机器后身份丢失，也无法随工程一起备份。
- **自动改写构建脚本，加入 Copperbench 插件或任务。** 这会改变用户工程的构建行为，而 GameTest 验收已经用独立的打包 JAR 宿主完成，不需要改写构建脚本。

## 实施顺序与完成标准

1. 识别与打开：源码、资产、本地历史。已实现：`GradleProjectDetector` 识别工程，`GradleProjectWorkspaceSession` 以 `.copperbench/workspace.json` 提供身份、修订号与单会话占用；源码编辑沿用带哈希的 `update_workspace_file` 与恢复点，因此没有另设只读模式。结构化操作返回 `GRADLE_PROJECT_OPERATION_UNAVAILABLE`，构建与运行返回 `GRADLE_PROJECT_TASKS_UNAVAILABLE`；资产健康检查不列清理候选。入口只有 `headless --workspace <文件夹>` 的单次命令。
2. 构建、运行与 GameTest 验收。已实现：`GradleProjectTaskGateway` 按识别出的轨道复用 `GradleWorkspaceTaskGateway`，不生成文件、不准备生成器；校验、构建、导出、运行客户端与服务器、准备与运行 GameTest、已验证导出可用，`headless` 单次命令等待任务结束。每个任务重新读取磁盘上的工程文件，与打开时的识别结果不一致时拒绝执行（`GRADLE_PROJECT_TARGET_CHANGED` 或具体问题）；识别不完整的工程返回 `GRADLE_PROJECT_UNSUPPORTED` 并列出原因。起步测试放在 `src/copperbenchTest`，避免与工程自己的 `gametest` 源集冲突；没有 `copperbench-tests.json` 时返回 `GAMETEST_NOT_CONFIGURED`。datagen 与发布批次不在第一阶段范围内，返回 `GRADLE_PROJECT_TASKS_UNAVAILABLE`。
3. MCP、SDK 与内置对话的原生路线，包括 `headless … api`、`serve` 与 `doctor`。已实现：三个 `headless` 入口接受文件夹，`doctor` 不接管也不写入工程，工程自带的 wrapper 未经产品审核只记为未知；Python SDK 的 `open`、`attach`、`connect` 接受工程文件夹，`native_edit_brief` 另给出测试目录与验收宿主版本；MCP 的 `tools/list` 不列出第一阶段不提供的工具，并在 `initialize` 的 `instructions` 说明原因，直接经 Core 调用这些操作仍返回原有诊断码；内置对话按文件夹识别接管的工程，使用单独的说明；环境查询不再为没有元素的工程返回字段契约。MCP 与内置对话由桌面启动，入口随第 4 步提供。
4. 桌面入口与界面，包括决定 7 的 `.gitignore` 询问与决定 8 的设置。

第 2 步的实机记录（2026-11-25，Windows）：

| 工程 | 自己的工具链 | 结果 |
| --- | --- | --- |
| 对照实验裸组产出的 Fabric 1.21.1 工程，原样 | Gradle 9.7.0、Loom 1.17.19、Mojang 映射 | 构建 31 秒，验收 3 项 44 秒，已验证导出与普通导出通过，客户端开始渲染后取消 |
| 同一工程改为 Yarn | Gradle 8.8、Loom 1.7.4、Fabric API 0.116.4 | 验收 3 项通过，首次下载依赖约 5 分钟 |
| 按 MDK 布局新建的 NeoForge 1.21.1 工程 | Gradle 9.6.1、ModDevGradle 2.0.141、NeoForge 21.1.209 | 加载检查通过，首次下载依赖约 7 分钟 |

三个工程的原有文件哈希都没有变化；把配方产量改错后验收失败并指出原因。第 3 步在裸组工程的另一份副本上用 Python SDK 走完 Agent 路线：取摘要、建恢复点、加一个右键解冻的物品及其纹理、构建、写行为测试、断开后重连等待验收、导出验证产物，全部通过，原有文件只改动了主类；后台宿主冷启动 3.4 秒，重连 0.1 秒。1.21.1 以外的轨道只有单元测试；没有启动专用服务器，因为那需要使用者接受 Minecraft EULA。

已知限制：验收宿主只带被测 JAR 与该轨道的 Fabric API 或 NeoForge，依赖其他模组的工程要用 `workspace` 模式运行自己的测试任务；构建依赖 Git 信息、符号链接或工程目录以外的包含构建时，冻结副本中的构建会失败并给出 Gradle 的错误。

尚未完成：由 Agent 在接管的工程里完成与对照实验相同的任务，并记录与 `.mcreator` 路线的成本对比。

完成标准沿用 ADR-0019 决定 7：用裸 Gradle 组在对照实验中产出的 Fabric 1.21.1 工程，原样由 Copperbench 打开，完成构建与 GameTest 验收，且不修改其构建脚本；同一任务的成本与验证深度和 `.mcreator` 路线一起记录。
