# 任务授权与自动验收

本文描述 Stage 15 之后新增的源码能力；使用它们需要包含本次改动的 Copperbench 构建。已经发布的安装包不会自动获得这些接口。

## 一次授权，持续执行

在桌面 **AI 与 MCP → 任务授权** 中填写任务名称、绝对目录、允许的操作和有效期。审查范围后点击“确认授权”，把返回的授权 ID 提供给 agent。默认有效期为 2 小时，最长 24 小时；授权目录包括其子目录。

| 范围 | 允许的 Copperbench 操作 |
| --- | --- |
| `create` | 在授权目录中创建工作区 |
| `edit` | 内容、资产和注册表修改，应用工作区计划，创建恢复点，准备测试模板，发布数据生成结果 |
| `build` | 生成和构建 |
| `test` | 校验、数据生成、GameTest |
| `run_client` | 运行客户端 |
| `run_server` | 运行专用测试服务器，须另行接受 Minecraft EULA |
| `restore` | 恢复历史版本，仍保留恢复前安全恢复点和 revision 检查 |

默认选择 `create,edit,build,test,run_client`。恢复历史版本和专用服务器需要单独选择。首次没有可用桌面工作区时，也可在具有图形会话的本机终端签发授权：

```powershell
.\copperbench.exe bootstrap authorize-task `
  --root "D:\ModProjects\BellTrial" `
  --label "寻路铃开发与验收" `
  --capabilities create,edit,build,test,run_client `
  --ttl-seconds 7200
```

此步骤显示一次本机确认窗口。若终端启动的窗口不可见，可直接使用桌面“AI 与 MCP”中的任务授权表单。无图形会话时会明确拒绝签发；agent 不能用 `--approve`、`userApproved: true` 或工作区中的文件自行批准。

签发后，创建工作区不再逐次弹窗，也不再把建议工作区目录当成硬性根目录：

```powershell
$taskGrant = '<user-issued authorization ID>'
.\copperbench.exe bootstrap create-workspace `
  --generator-id fabric-1.21.1 --mod-name "Wayfinder Bell" --mod-id wayfinder_bell `
  --workspace-folder "D:\ModProjects\BellTrial\wayfinder_bell" `
  --task-authorization $taskGrant --no-prompt true
```

MCP 写请求使用可选字段 `taskAuthorizationId`。CLI 对应 `--task-authorization`。TypeScript SDK 的 `taskAuthorizationId` 选项和 Python SDK 的 `task_authorization_id` 选项会把 ID 附到相关写请求；查询、取消和撤销不依赖仍然有效的任务授权。

```typescript
const client = CopperbenchClient.fromWorkspace(workspacePath, mcpToken, {
  taskAuthorizationId: approvedTaskId
});
await client.initialize();
await client.prepareGameTests(currentRevision);
// Retain the returned task ID and poll getTask(taskId, lastLogSequence).
```

撤销可以在桌面完成，也可调用 `revoke_task_authorization`，或运行：

```powershell
.\copperbench.exe bootstrap list-authorizations
.\copperbench.exe bootstrap revoke-authorization --authorization-id $taskGrant
```

授权记录持久化于用户配置目录下的 `task-authorizations`，由工作区之外的私有密钥签名。每次提交相关操作都会重新检查签名、目录、操作范围、到期和撤销状态。MCP 的查询和撤销限于覆盖当前工作区的授权；本机 UI 可管理本用户签发的全部任务授权。MCP 审计保留请求中的授权 ID。

**授权是补充批准机制。** 它不会替换 MCP 认证、提升 Read Only 连接或取消 revision 检查，也不会开放外部发布、凭据导出和 Java 插件启用。原本由 Workspace 连接允许的基础编辑/构建权限仍然存在；撤销任务授权不会撤销该连接令牌。到期和撤销阻止后续使用此授权的请求，已启动任务需通过任务面板或 `cancel_task` 取消。目录检查约束 Copperbench 操作，Gradle 脚本仍以当前系统用户执行，并非操作系统沙箱。

## 从模板到玩法验收

1. MCP 调用 `prepare_game_tests({expectedRevision, taskAuthorizationId})`，或执行 `headless ... prepare-game-tests`。
2. 编辑 `src/gametest/java` 下的测试，并按需修改 `copperbench-tests.json`。
3. 调用 `run_gametest` 或执行 `headless ... run-gametest`。
4. 查询任务直到终态，再检查 `task.verification` 的结果、用例和被测内容。

准备命令会创建配置、README 和版本对应的加载检查用例；已有配置或测试文件不会被覆盖。具备本地历史服务时，准备前先创建恢复点。**初始用例只验证模组加载，玩法行为需要自己补充断言。** 桌面任务面板在测试失败时提供“准备测试模板”，准备完成后可直接“运行测试”。

默认配置：

```json
{
  "schemaVersion": "1.0",
  "mode": "packaged_jar",
  "sourceDirectory": "src/gametest/java",
  "resourceDirectory": "src/gametest/resources",
  "entrypoints": ["copperbench.acceptance.AcceptanceTests"],
  "minimumTests": 1
}
```

`packaged_jar` 模式使用当前生成器固定的 Minecraft、Loader、API 和 Gradle 插件坐标。产品自动构建真实工作区快照，选择唯一的模组 JAR，创建独立测试宿主，并复制该 JAR 到宿主的 `run/mods`。宿主包含测试源码与加载/报告辅助代码，不复制模组实现源码。测试可以引用被测 JAR 的类型；需要额外依赖或定制运行环境的项目可使用下面的 `workspace` 模式。

Fabric 测试入口由 `entrypoints` 注册。NeoForge 1.20.1/1.21.1 使用 GameTest 注解，26.x 使用事件注册；生成的模板已经包含相应入口和报告器。NeoForge 1.20.1 启动 GameTest 仍需要带有 EULA 接受记录的 `run_server` 授权；准备和编译模板不会接受 EULA。

已经自行配置 Gradle 测试任务的工程可以保留自己的宿主：

```json
{
  "schemaVersion": "1.0",
  "mode": "workspace",
  "task": "runGameTest",
  "reportPath": "build/test-results/gametest.xml",
  "minimumTests": 3
}
```

该模式在真实文件快照中运行指定任务，要求本次任务产生 JUnit/GameTest XML；它绑定源码快照，不宣称独立 JAR 验收。没有配置文件时兼容原有 `runGameTest` 任务，默认报告路径为 `run/gametest-results.xml`。原先只返回退出码、未产生测试报告的脚本现在会明确失败。

## 被测内容与报告

内部 `.mcreator/localHistory`、`.mcreator/workspaceBackups` 和 `.mcreator/userSettings` 不属于验收输入；历史服务的临时文件和用户设置更新不会干扰快照。根目录的 `*.mcreator` 工作区定义、真实源码以及 `.mcreator` 下其他自定义输入仍会进入指纹。真实输入在捕获期间消失或改变会返回 `WORKSPACE_SNAPSHOT_CHANGED`，需要从当前文件重试。

`run_server`、`run_datagen`、`run_gametest` 都复制当前磁盘上的源码、资源、入口、构建脚本、测试和 `.mcreator` 文件。快照排除根目录的 `build`、`out`、`run`、`runs`、日志、IDE 状态、`.copperbench` 和 `.env` / `.env.*`，以及 Git/Gradle/Node 缓存。用户原有运行世界不会随这些目录复制。路径中的符号链接和重定向会被拒绝；复制前后文件清单或内容变化会返回 `WORKSPACE_SNAPSHOT_CHANGED`，不会继续验证混合版本。

每次任务使用新的 `.copperbench/task-runs/<kind>/<taskId>/`，其中包含 `source-manifest.json`。GameTest 另写 `verification.json`，任务查询和 JSONL 事件也返回同一份结构化结果：

- `status`、`reasonCode`；运行中为 `pending`，完成后为 `passed` 或 `failed`。
- `discovered`、`executed`、`passed`、`failed`、`skipped` 及逐用例失败消息。
- `acceptanceExecuted` 和 `frameworkTests`：保留真实总数，同时区分用户验收用例与 Minecraft 的 `minecraft:always_pass` 内置自检；每个用例的 `scope` 标明归属。内置自检不能满足 `minimumTests`。
- `sourceSnapshot.sha256`、文件清单位置、工作区 ID 与 revision。
- `packaged_jar` 模式的 `artifactSha256`、被测 JAR 位置及运行环境。
- XML 的 `reportPath`、`reportSha256`、任务时间和进程退出码。
- `sourceCurrentAtCompletion`：完成时工作区 revision 和磁盘文件指纹是否仍与被测快照一致。

只有进程成功退出、报告是本次生成、实际执行的验收用例数量达到 `minimumTests` 且没有失败，任务才通过。缺少/过期/无效报告、零用例、仅跳过用例、失败用例和非零进程退出均不能通过。JAR 部署时和运行后都会核对内容哈希；测试期间工作区发生变化会返回 `GAMETEST_SOURCE_CHANGED`，需要重跑。数据生成预览也会因原生文件变化而失效，即使其 revision 未改变。XML 禁止外部实体和 DTD，并限制报告大小、嵌套深度与用例数。

可自动验证的服务端行为应写成断言。真实输入、客户端显示与完整存档重进可由具有桌面操作能力的 agent 按[客户端验收流程](./client-acceptance.md)留证；美术质量、声音听感和趣味性仍保留人工判断。一个加载检查通过不代表这些体验已验收。

## CLI 流式反馈

```powershell
.\copperbench.exe headless --workspace '<path.mcreator>' run-gametest `
  --task-authorization $taskGrant --stream true
```

标准输出按行返回 JSON：初始化状态、任务接受结果、含增量日志的 `task_update`、最终结果。默认不加 `--stream true` 时仍返回原有单个最终 JSON。MCP/SDK 继续使用 `get_task(taskId, afterLogSequence)`；不要反复从 0 请求整段日志，也不要把任务已接受当成验收完成。

`run-client` 和 `run-server` 持续观察实际运行任务，直到进程正常关闭、失败或取消；它们不套用有限构建／验收任务的 45 分钟等待上限。需要检查命令终态，不能用渲染就绪日志代替正常关闭证据。

## 无交互启动与 Windows 运行恢复

外部 agent 创建工作区时应传入 `--no-prompt true`。没有任务授权时，产品立即返回 `USER_APPROVAL_REQUIRED`、请求的目录/模组/生成器和所需 `create` 范围，供用户审查；不会进入等待不可见弹窗的状态。拿到用户签发的授权 ID 后，保留该参数重试即可。默认不传此参数的本机创建命令仍显示原有确认窗口。

创建命令目前在 Gradle 初始化完成后一次返回最终 JSON，没有构建任务那样的中间流式进度。请保留 stdout、stderr 和耗时，不要把短时间无输出直接判定为完成或失败。创建失败时先检查稳定 `code` 和可用的 `detail` 原因，再决定修复或重试；不要自行签发授权或绕过产品入口。

Windows 工作区 Gradle 启动前会用选定的 JDK 单独检查本地通信。如果 Unix-domain selector pipe 不可用、TCP 检查可用，产品只对该任务的子进程设置兼容选项，并记录 `GRADLE_IPC_TCP_FALLBACK`。两种方式均不可用时返回 `GRADLE_LOOPBACK_UNAVAILABLE`；该预检发生在用户构建任务之前，不会为了恢复连接而重复运行构建。它不修改系统环境变量，也不代表桌面 MCP 的安装回归已经完成。

桌面和创建命令的应用 JVM 也执行相应检查；使用 Gradle Tooling API 时，随包启动辅助 JAR 将已验证的兼容设置应用于守护进程启动前。这些处理仍只作用于产品进程，不修改全局配置。分发包中的 `lib/copperbench-local-ipc-agent.jar` 应与应用一起保留，agent 无需手动添加 Java 环境补丁。

GameTest 在 Windows 的隔离目录过深时，自动改用 Copperbench 用户缓存下的 `task-runs/<taskId>`。源码快照、被测 JAR 和验收报告保留在该目录，原工作区的 `.copperbench/task-runs/run_gametest/<taskId>/execution-location.json` 记录位置与源码指纹。任务日志记录 `GAMETEST_SHORT_PATH`；若缓存路径本身也过深则返回 `GAMETEST_PATH_TOO_LONG`。清理缓存会删除其中的验收文件，需要留档时应先复制报告与被测产物。

## 已知运行限制

Windows 的一般长路径支持仍有限制。GameTest 已提供上述短目录回退；原工作区构建、Server 和 Datagen 的深路径仍需要选用较短的工作区目录。该修复不构成 Windows 任意长路径支持的承诺。
