# Blockbench 建模任务：编辑、回导与元素关联

当前支持 `.bbmodel` 的 `java_block` 格式，即 Java 版方块/物品模型。任务是 Copperbench 的持久记录，与 Blockbench 进程独立；关闭或重开编辑器不会被视为保存完成。游戏模型和 PNG 由 Blockbench 手工导出或社区 MCP 导出，Copperbench 负责审核、回导和元素关联。

## 在资产中心使用

1. 展开“建模任务”。对选中的 `.bbmodel` 点击“从所选模型创建副本”；或填写新的工作区相对目标路径，点击“新建建模副本”。目标尚不会被创建或覆盖。
2. 复制编辑文件路径，在 Blockbench 中打开该文件，或让 Agent 通过社区 MCP 打开。请编辑副本，不要编辑原资产。
3. 在 Blockbench 中保存项目；回到 Copperbench 点击“刷新任务与保存状态”。
4. 点击“确认磁盘保存并生成候选”。系统验证已读到的文件哈希、源资产状态、模型结构与纹理，生成可独立读取的候选。
5. 候选位于任务目录，状态为“候选已保存，待回导”。这一步没有写入原模型、导出 Minecraft 模型或改变工作区内容版本。
6. 在 Blockbench 把游戏模型 JSON、PNG 及必要的 blockstate 导出到任务的 `edit` 目录。展开“回导导出的模型与贴图”，逐个填写编辑目录内相对路径和工作区目标路径；`.bbmodel` 候选自动加入。
7. 点击“预览回导”，检查新增、替换和内容相同的文件。需要覆盖已有文件时，明确勾选替换确认，再点击“应用回导”。系统保存恢复点，回导全部文件并刷新资产。无需关闭 Blockbench。
8. 选择已有方块/物品元素和导入的游戏模型，点击“关联所选模型”。模型应位于 `src/main/resources/assets/<模组标识>/models/custom/` 等独立路径，避免与生成器的 `models/block/<元素名>.json` 或 `models/item/<元素名>.json` 同名。
9. 构建后检查 JAR 中的模型与 PNG，再启动游戏确认形状和贴图。回导成功、构建成功、游戏视觉正确是三项独立结果。

可以取消编辑中或待回导任务。取消只更新任务状态，副本和候选均保留，编辑器不被关闭。相同目标存在未完成任务时，应先处理旧任务；已经导入或取消的任务不会阻止新任务。一个工作区当前最多保留 256 条任务记录，没有自动清理。

## Agent 调用顺序

以下均为 Copperbench MCP 工具。示例 UUID 用来说明同一次逻辑请求应保持同一 `taskId`；新任务应生成新 UUID，`expectedRevision` 使用当前工作区版本。

创建新模型：

```json
{"taskId":"d247c129-493b-4bba-b3f9-d42c5537c50e","targetRelativePath":"models/blockbench/lamp.bbmodel","expectedRevision":0}
```

把上述参数传给 `begin_blockbench_task`。编辑已有资产时用 `assetId` 替代 `targetRelativePath`，两者只能选一个。返回 `data.editPath`、源哈希、开始版本与恢复点；原模型的相对纹理会解析并嵌入副本。返回的 `editPath` 是任务专用文件路径，不是任意文件读写接口。

通过社区 Blockbench MCP 编辑并保存该副本，然后查询：

```json
{"taskId":"d247c129-493b-4bba-b3f9-d42c5537c50e"}
```

调用 `get_blockbench_task`，取实际返回的 `data.editSha256`，作为 `finish_blockbench_task` 的 `savedSha256`，并附相同 `taskId` 和最新 `expectedRevision`。不要自行假设哈希，也不要用未保存的内存模型代替磁盘文件。

`finish_blockbench_task` 返回 command 状态 `completed`，任务状态 `ready_to_import`，以及 `candidatePath`、`candidateSha256`。`imported=false`，不能据此报告模型已进入模组或游戏。相同文件的重复完成不会生成额外恢复点或活动记录；完成后文件再变化将拒绝重试，并在查询中标记 `candidateChanged`。

`list_blockbench_tasks` 接受空参数；`cancel_blockbench_task` 接受 `taskId` 和 `expectedRevision`。MCP 的所有操作进入既有审计；开始、完成和取消需要工作区写权限。如使用 task authority，其能力范围为 `edit`。Core 和原生 API 复用相同领域入口。

## Agent 回导与关联

调用 `preview_blockbench_import`，输入已完成任务和真实导出文件映射：

```json
{
  "taskId": "d247c129-493b-4bba-b3f9-d42c5537c50e",
  "outputs": [
    {"sourceRelativePath": "export/lamp.json", "targetRelativePath": "src/main/resources/assets/example/models/custom/lamp.json"},
    {"sourceRelativePath": "export/lamp.png", "targetRelativePath": "src/main/resources/assets/example/textures/block/lamp.png"}
  ]
}
```

模型中的纹理引用必须与目标一致，例如 `example:block/lamp`。不要把 `.bbmodel` 改扩展名冒充游戏导出，也不要只导出 JSON 却遗漏它引用的图片。预览验证导出模型、父模型及贴图引用；已有自定义父模型的继承和子模型贴图覆盖也会检查。`minecraft:` 资源仅检查名称语法，实际原版资源是否存在仍需相应版本的游戏验证。

当前流程要求自定义模型贴图放入 `textures/block/` 或 `textures/item/`，对应引用为 `namespace:block/name` 或 `namespace:item/name`。仅把 PNG 放入 `textures/` 根目录并不能保证游戏图集加载它；自定义图集定义不在本流程支持范围内，预览会以 `MODEL_TEXTURE_ATLAS_PATH` 拒绝这类引用。请在 Blockbench 调整纹理文件夹及引用后重新导出。

取预览实际返回的 `data.planToken`，向 `import_blockbench_task` 提交 `taskId`、`planToken`、最新 `expectedRevision`；预览包含替换时还必须提交 `confirmReplace: true`。预览有效期为 15 分钟，重启服务后需要重新预览。应用时重新检查候选、导出文件与目标哈希，陈旧预览不写入文件。

成功返回 `committed`、任务 `state=imported` 和工作区新版本。丢失响应时可以用相同 token 重试；只有已回导文件哈希仍一致才返回 `completed` 与 `idempotentReplay=true`，不会重复写入或递增版本。

向 `bind_blockbench_model` 提交 `taskId`、已有 `elementId`、`modelResource`（如 `example:custom/lamp`）与最新 `expectedRevision`。模型必须来自本任务的有效导入记录，元素必须是方块或物品。此操作不创建元素；需要新元素时先使用现有创建入口，记录返回 ID，重试时复用。生成器将元素模型指向自定义模型，并保留导入贴图。手工锁定元素不自动改写生成文件，应先检查其锁定状态。

回导与关联需要工作区写权限和 `edit` 能力，沿用现有权限、恢复与 MCP 审计。

## 文件与故障处理

任务记录位于 `.copperbench/modeling-tasks/<taskId>/task.json`，编辑文件为 `edit/model.bbmodel`，候选为 `candidate.bbmodel`。这些内部文件不会出现在资产索引，也不会作为普通模组资源打包；任务状态在重开工作区后仍可查询。

- `MODEL_SOURCE_CONFLICT`：原模型被修改/删除，或新目标被别人创建。保留当前副本，处理冲突后用新任务重新审核。
- `MODEL_EDIT_CHANGED`：文件在查询后又保存了。刷新保存状态再完成。
- `MODEL_TEXTURE_UNAVAILABLE`：把图片复制进任务 `edit` 目录，或在 Blockbench 中嵌入图片并保存。不会读取工作区外图片或下载外部 URL；在完成阶段只读取 `edit` 内图片。
- `MODEL_STRUCTURE_INVALID` / `MODEL_GEOMETRY_INVALID`：修复纹理绑定或立方体坐标。分层贴图等尚未支持的结构需要先处理，不会自动忽略。
- `MODEL_TASK_INCOMPLETE`：上一次准备在写入完整记录前中断，目录被保留；使用新的任务 ID，不覆盖其中的编辑内容。
- `MODEL_IMPORT_STALE` / `MODEL_PREVIEW_EXPIRED`：文件发生变化或预览已失效。重新预览并审核，不能复用旧的替换决定。
- `MODEL_IMPORT_FAILED`：本批写入失败，已恢复任务涉及的文件；编辑副本和候选仍保留。
- `MODEL_IMPORT_RECOVERY_REQUIRED`：恢复尚未完成。任务处于 `importing`，使用 UI“恢复中断的回导”或 `recover_blockbench_import`（`taskId`、最新 `expectedRevision`）。恢复只处理任务记录中的目标；出现其他人的后续修改时返回 `MODEL_RECOVERY_CONFLICT`，不会覆盖它们。
- `MODEL_IMPORTED_FILES_CHANGED`：导入后的正式文件已变化，旧成功记录不能证明当前文件仍相同。检查修改后使用新任务。
- `MODEL_BINDING_SELF_REFERENCE`：自定义模型与生成器包装模型同名。用独立资源名重新导出、审核并导入。
- 只接受已知资产目录下的小写 `.bbmodel` 路径，拒绝越界、符号链接与目录重定向。单文件及最终可移植模型上限为 32 MiB。
- 每批最多 63 个游戏导出文件（另加一个候选源），游戏导出总量上限 128 MiB。同一资源 ID 不能重复映射到多个资产根目录。

任务副本用于工作流隔离，不是对第三方插件的操作系统沙箱。已有资产租约也不会强制约束直接写磁盘的外部插件。
