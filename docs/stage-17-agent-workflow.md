# Stage 17：结构化编辑与验收产物

本页说明已接入的增量接口。完整阶段验收仍见 `testing/stage-17-implementation-2026-09-20.md`。

Code 元素通过 `workspace.field_contract("code")` 发现输入合同：`code` 和文件包条目的 `code/path` 必须是字符串，不能用数字、布尔或 null 代替源码。`codeFiles` 是只含 path/code 的对象数组；路径限定为源包内的相对 `.java` 路径，反斜杠跨平台按正斜杠处理。`sourceFingerprints` 是路径到字符串的映射，应使用编辑器返回的值，不要自行猜测或覆盖并发保护。顶层与 `fields` 形式都可写，显式冲突会拒绝；外部源码读回会同步兼容副本。即使尚无源码目录，成功创建 Code 也必须写出主文件及 helper 并保持手写锁定，不要求先执行一次生成。待生成记录不取得手写文件的所有权；外部编辑后重开可更新说明，而基于旧正文的覆盖仍须通过源码指纹检查。合法文本不等于编译成功或生命周期已经接入。

安装包内的 Code 接入示例见[生命周期模板](../examples/agent-native/lifecycle-1.21.1/README.md)，包含可审查的渲染器和两个加载器模板。开发验收日志不随安装包分发；这些接口和示例不表示 Stage 17 全部门禁已经通过。

Procedure 元素通过 `workspace.field_contract("procedure")` 发现正文合同。`procedurexml` 必须是根元素为 `xml` 的合法 Blockly XML 字符串；`procedureIr` 必须是 1.0 IR 对象，节点、端口、依赖和坐标按实际 JSON 类型检查，不能用错误类型冒充空图。单独修改一种正文会刷新另一种及 `fields` 兼容副本；XML 输入保留原文，IR 输入导出为真实定义 XML。同时提交两种正文时，其导出的块结构必须一致，比较不依赖编辑器生成的 block id。未知节点须携带类型匹配的完整原始 block，不能用占位文本替代。循环、悬空连接等结构尚未完成的图仍可作为草稿保存，应另行读取过程诊断；正文保存成功不表示引用目标、触发上下文、生成结果或游戏行为已经通过验证。

如果 XML 含有 IR 无法完整保留的内容（例如根级变量、已知块的 mutation、带额外属性的字段或 shadow 连接），过程编辑器返回只读状态及 `PROCEDURE_XML_PRESERVATION_REQUIRED`。结构化更新、相关预览／计划及会重写该过程的注册表改名均会停止，保留原文件和 revision；说明字段修改及显式提交完整 XML 原文仍可用。明确替换为受支持的 XML 后，结构化编辑会恢复。未知 block 已作为完整原文保留的子树不因此禁用；这不表示任意第三方 Blockly 扩展已得到原生编辑支持。

## 发现与创建

`get_workspace_references` 从已识别的 Procedure IR 节点提取引用，不依赖请求附带的 `dependencies` 摘要；兼容 `fields` 副本不会重复计数。实际声明为 Procedure 的字段区分具名引用和固定值，包含顶层字段、嵌套对象／列表／映射，以及已注册 GUI 组件的过程字段（例如 `/components/0/data/onClick`）。固定文本、固定返回值和属性映射的键名不作为过程引用。目标按内部名或稳定 UUID 及类别解析，显示名称不作为绑定依据；缺失、类别不符和同名歧义分别报告 `WORKSPACE_REFERENCE_DANGLING`、`WORKSPACE_REFERENCE_TYPE_MISMATCH`、`WORKSPACE_REFERENCE_AMBIGUOUS`。新增目标后会重新解析已有调用。该索引还扫描约定引用键，但返回值／触发上下文及不透明自定义适配器仍需单独验证；无引用诊断不表示可以编译或玩法正确。

`call_procedure` 的 `fields.procedureId` 可以是过程内部名或稳定 UUID；旧 Blockly XML 的 `fields.procedure` 名称形式仍可用。非空 `procedureId` 优先于名称提示，生成器将其解析为实际过程名称；缺失身份不会回退到可能过期的提示名称。只有 `procedureId` 为空时才使用 `procedure`。图检查和生成使用相同的目标选择规则，显示名称不参与绑定。当前源码的 `procedure_retval_*` 生成器也使用该身份解析；已验证八轨数值返回调用的名称／UUID／过期提示／重开，尚未归档到新安装候选，也不等于所有返回形式、调用上下文或玩法通过。这些返回值调用块在 IR 中仍按不透明原文保留，不因此变成可原生编辑的已知节点。

当前源码会报告已知返回节点缺少值（`PROCEDURE_RETURN_VALUE_REQUIRED`）或连接了已知不兼容类型（`PROCEDURE_RETURN_TYPE_MISMATCH`），并给出节点与端口定位。预览将 `canGenerate` 设为 false，但可保留无效草稿；生成任务预检也会阻断。未知插件输出类型不作猜测，仍须由实际生成器验证；这个检查不证明被调用过程的实际返回类型、所有控制流路径或触发上下文兼容。

原生工作区还会使用当前加载的插件触发器目录和活动生成器检查直接上下文节点：`entity_from_deps`、`source_entity_from_deps`、`immediate_source_entity_from_deps`、`coord_x/y/z`。触发器未提供匹配名称和类型时报告 `PROCEDURE_CONTEXT_MISSING`；未知／不支持的触发器、不可用目录和未启用的必需 API 有各自代码。预览可保留无效草稿，生成准备会在依赖设置及文件生成前停止。通用创建／更新、重新打开、编辑器诊断和全局健康汇总重新计算这些已知错误；修复触发器后诊断消失。`no_ext_trigger` 的复用过程可由调用方提供上下文，不因缺少外部触发器被拒绝。传递调用、变量隐含依赖、不透明子树和局部依赖提供者尚未得到完整验证，不能把无直接上下文错误当作全部调用语义通过。

`get_procedure_editor` 的 `triggerCatalog` 列出当前加载且被活动生成器和已启用 API 支持的外部触发器，包含 `id`、`label` 和 `dependencies`（名称／类型）。选择触发器应使用返回的 ID，不猜测 `on_*` 名称；`no_ext_trigger` 表示由调用方提供上下文的复用过程。旧宿主可能没有目录，界面保留原值并限制新选择，不将目录缺失解释为所有触发器可用。菜单可选不等于该过程的依赖匹配，仍应读取修改预览和生成诊断。

原生 Python SDK 的具名参数采用 snake_case，`query`/`command` 内 Core payload 字段继续使用 camelCase。

```python
from copperbench import Workspace

with Workspace.connect(workspace_mcreator_file) as workspace:
    contract = workspace.field_contract("block")
    created = workspace.create_mod_element(
        elementType="block", name="resonance_forge",
        initialValues={"displayName": "Resonance Forge", "hardness": 3,
                       "resistance": 6, "rotationMode": 1,
                       "hasInventory": True, "inventorySize": 3,
                       "inventoryStackSize": 64, "inventoryDropWhenDestroyed": True})
```

等价 MCP 工具：先调用 `get_workspace_environment({})` 读取 `fieldContracts.block`，再调用 `create_mod_element`：

```json
{
  "expectedRevision": 0,
  "elementType": "block",
  "name": "resonance_forge",
  "initialValues": {
    "displayName": "Resonance Forge", "hardness": 3, "resistance": 6,
    "rotationMode": 1, "hasInventory": true, "inventorySize": 3,
    "inventoryStackSize": 64, "inventoryDropWhenDestroyed": true
  }
}
```

`0` 仅适用于仍为修订 0 的新工作区；始终使用最新查询返回的 revision。`fields` 兼容形式仍可用，同一请求的两份冲突值会被拒绝。错误的 `diagnostics[].path` 定位字段，`code` 和 `message.args.reason` 给出原因。内部名称是小写字母开头、最多 64 位的小写字母/数字/下划线。

整数参数按 JSON 原始十进制值检查：`1e2` 可表示整数 100；`1.0000000000000001` 不是整数，不能因浮点舍入而保存为 1。字段范围、Java 存储范围和库存槽索引均需满足，失败返回 `FIELD_VALUE_OUT_OF_RANGE`，内容 revision 不推进。

通用反射字段的递归规则可通过 `workspace.field_contract("generic")` 查询：普通嵌套对象、数组和集合同样检查数值类型／范围、布尔、字符串及枚举；例如交易的 `/trades/0/countOffer` 不会把小数舍入成整数，`repairItems` 不会把数字转成物品名称。新增未知嵌套键会被拒绝；旧定义含可能在重写时丢失的未知嵌套键，返回 `FIELD_PRESERVATION_REQUIRES_REVIEW` 并保留原字节。上游不持久化的 `transient` 字段（如生物群系的 `genDepthMin/Max`）不再声明可写；应使用真实定义字段，并检查其上下文要求。

`workspace.field_contract("custom")` 说明内置过程、GUI 组件和属性／状态映射的实际序列化格式。过程名可用字符串或 `{name: string}`，`null` 清空引用；返回值过程可用严格对应类型的常量，或 `{name, fixedValue}`，没有非空过程名时必须提供常量。GUI 组件使用 `{type, data}`，未知类型不能自动替换为空占位组件。属性值与 type（logic/integer/number/string）、min/max 或 arrayData 相符；状态映射数组不能重复声明同名属性。数值属性遗漏的边界按存储适配器默认为 0，需要其他范围时显式提交两端。颜色支持 ARGB 整数或 `{value: ARGB, falpha: 0}`，falpha 是旧存储常量，不接受用它设置透明度。上述合同不证明过程目标存在、返回类型符合调用上下文，也不证明 GUI 实际渲染或游戏行为正确。

投射物和进度分别通过 `workspace.field_contract("projectile")`、`workspace.field_contract("achievement")` 发现输入类型、数值范围、枚举和定义字段名；MCP 使用 `get_workspace_environment({})` 中的同名 `fieldContracts`。例如 `showParticles` 必须为布尔值，`knockback` 必须为 0～500 的整数，`rewardXP` 必须为 0～64000 的整数，`frame` 只接受 `task/goal/challenge`。`rewardLoot/rewardRecipes` 必须为字符串数组，不接受单个字符串或数字成员。引用使用名称字符串；投射物过程钩子及 `rewardFunction` 可用 `null` 清空。`fields` 兼容路径可单独更新，显式提交矛盾的两份值仍会拒绝。这些合同验证输入类型，不替代资源存在性、触发条件或游戏行为验收。

掉落表可通过 `workspace.field_contract("loottable")` 或 `get_workspace_environment` 的 `fieldContracts.loottable` 发现池、条目的字段类型、范围及路径模式。`pools` 和每个池的 `entries` 必须是对象数组；计数、权重、附魔等级是 0～64000 的整数，最大值不得小于最小值，省略最大值时使用该范围的最小值。`silkTouchMode` 为 0/1/2，条目 `type` 目前只支持 `item`。字符串数字、字符串布尔、未知新字段不会被静默转换或忽略；错误路径可定位到 `/pools/0/entries/0/weight`，`fields` 兼容形式也保留相应前缀。这是定义输入合同，资源引用、上下文条件与游戏行为仍须另外验证。

旧掉落表的 `minrolls/maxrolls` 等存储名在读取时投影为公开的 `minRolls/maxRolls`，读取不改文件。若原定义的池或条目含第三方未知字段，结构化编辑返回 `FIELD_PRESERVATION_REQUIRES_REVIEW` 并保留原文件；这些无稳定身份的数组对象不能安全自动合并，需先审查其含义。不要通过删除未知字段来规避该诊断。

普通植物的最小请求须选择实际存在的方块贴图及迷之炖菜效果。例如在具备相应资源的工作区中：

```python
workspace.create_mod_element(
    elementType="plant", name="sample_flower",
    initialValues={"texture": "minecraft:poppy", "suspiciousStewEffect": "SPEED"})
```

Core 默认采用普通植物、十字模型和植物脚步声。缺少效果返回 `FIELD_REQUIRED_BY_CONDITION`，缺少该模型需要的贴图返回 `FIELD_REQUIRED`；诊断路径指出待补字段，不以泛化持久化错误代替。双层植物还需底部贴图。保存及源码生成不替代资源可用性或游戏行为验收。

元素内部名称和游戏注册名分别读取。创建返回的 `data.element.identity`、`get_mod_element_editor` 的 `data.element.identity` 以及 `list_mod_elements` 的各项 `identity` 使用同一投影，创建后即可查询，无需先重开。例如内部名 `contract_probe_v6` 对应 `identity.internalName=contract_probe_v6`、`identity.registryName=contract_probe_v_6`；受管方块/物品还提供完整 `identity.resourceId=structured_forge:contract_probe_v_6`，编写命令或 GameTest 时使用该字段，不从 `name` 猜注册 ID。合法内部名称的 `element.name` 在重开后保持不变；旧上游工程中不符合公开名称格式的名称仍保留历史列表投影，实际内部名见 `identity.internalName`，不会改写原文件。`identity.source=generator_definition` 只说明生成器定义，不能证明游戏已注册或行为已通过。手写/锁定元素及其他类型不推断完整资源 ID；应依据其实际代码和行为验收。

```python
editor = workspace.query("get_mod_element_editor", elementId=element_id)["data"]
resource_id = editor["element"]["identity"].get("resourceId")
```

等价 MCP 请求为 `get_mod_element_editor({"elementId":"元素 UUID"})`；列表可投影 `fields=["id","name","identity"]`。这些都是只读查询，不携带 `expectedRevision`。

工作区缺少生成器依赖缓存时，字段修改仍会保存真实定义，但不会写出缺少导入的 Java。此时编辑器的 `configuration.generationState` 为 `pending`。运行公开的 `generate()` 或 `build()`，并等待返回的任务终态；任务会先准备依赖、建立类型索引，再生成受管源码。不带待生成标记的旧冷工程也会在直接构建时准备受管源码；代码锁定元素和 Code 元素保持原有所有权。普通字段查询和修改不会因此下载依赖。准备期间输入变化或目标源码被外部修改会拒绝生成，分别报告 `GENERATION_INPUT_CHANGED` / `GENERATION_SOURCE_CONFLICT`，应审查当前文件后重新操作；不要手工补导入掩盖准备失败。

准备失败应先查看稳定诊断与任务日志。`GENERATOR_DECOMPILATION_FAILED` 表示日志明确指出 Minecraft 源码反编译任务失败，应检查反编译器输出、系统内存与进程终止记录；仅此代码不能确定是 OOM，也不能确定是下载问题。`GENERATOR_LOCAL_IPC_UNAVAILABLE` 表示本地通信文件访问失败，应使用桌面产品或普通本地终端核对执行环境。只有日志中真实的下载/连接异常才能支持针对镜像或代理的排查。

## 函数命令正文

`workspace.field_contract("function")`（MCP：`get_workspace_environment({}).fieldContracts.function`）公开函数正文的输入规则。`commands` 必须是字符串数组，`code` 必须是字符串；数字、布尔值和 `null` 不会自动转换成命令。数组按 LF 连接并添加末尾 LF，空数组得到空文本。两种形式同时存在时必须生成完全相同的正文，否则返回 `FIELD_ALIAS_CONFLICT`，不会忽略其中一份。

```python
created = workspace.create_mod_element(
    elementType="function", name="announce_ready",
    initialValues={"commands": ["say Forge ready"]})
```

等价 MCP payload：`{"expectedRevision":0,"elementType":"function","name":"announce_ready","initialValues":{"commands":["say Forge ready"]}}`。也可只提交 `code: "say Forge ready\n"`，或使用 `fields.commands` / `fields.code`。更新时保留未修改字段；若既有工程同时保存了两种正文，须在同一请求中使两份内容一致。错误路径定位到具体数组项，拒绝后定义与 revision 保持原值。这里验证的是输入及生成文本，不判断 Minecraft 命令语法，也不证明函数已在游戏中执行。

## 计划与旧配置差异

```python
editor = workspace.query("get_mod_element_editor", elementId=element_id)["data"]
configuration = editor.get("configuration", {})
if configuration.get("status") == "drift":
    # 审查 differences 中的 declared/effective，再显式选择其中一种方式。
    operation = configuration["planOperations"]["adopt_definition"]
    planned = workspace.plan_workspace_changes(
        [operation], idempotency_key="reviewed-configuration-1",
        expected_revision=workspace.revision, require_recovery_point=True)
    plan = planned["data"]
    preview = workspace.preview_workspace_plan(plan)
    # 审查 preview；确认后仅执行一次。冲突时重新读取，不能盲目重放写请求。
    applied = workspace.apply_workspace_plan(plan)
```

另一选择是 `reapply_declared`。有效值或关联源码在预览后变化会拒绝旧计划；手写源码元素不能这样重新关联生成器。计划查询是例外：`expectedRevision` 在 query payload 中；普通只读查询不自动携带 revision。MCP 对应 `plan_workspace_changes`、`preview_workspace_plan`、`apply_workspace_plan`，使用相同 `operations`/`plan` 对象。

## 建模与交付

在任务编辑目录导出一个游戏 JSON 及其 PNG 后，可调用：

```json
{"taskId":"建模任务 UUID","elementId":"目标生成模式方块或物品 UUID"}
```

把上述 payload 传给 `preview_blockbench_import`，省略 `outputs` 即请求自动推导映射。多模型、缺图、命名空间冲突需要人工解决；返回预览不代表导入。也可继续显式提供 `outputs`。

创建任务使用 `begin_blockbench_task`。可只传稳定 `taskId` 和受管方块/物品的 `elementId`，Core 会选择 `models/blockbench/<元素名>.bbmodel`，并将目标元素、namespace、`custom/<元素名>` 模型建议与 `textures/block` 或 `textures/item` 目录存入 `elementContext`。可附带 `assetId` 从现有源模型创建副本，或附带 `targetRelativePath` 指定新源路径；后二者互斥。重试创建时，相同任务 ID 不能更换上下文中的目标元素；已有目标文件必须选择其资产 ID，不能覆盖建模源。

```python
import uuid

task_id = str(uuid.uuid4())  # 留存此 ID；只有确实创建新任务时才生成新的 ID。
started = workspace.command("begin_blockbench_task", taskId=task_id, elementId=element_id)
task = workspace.query("get_blockbench_task", taskId=task_id)["data"]
# 在 Blockbench 中打开 task["editPath"]，保存后，在同一编辑目录导出游戏 JSON 和 PNG。
# 保存后重新查询，避免使用编辑前的哈希；完成候选不等于回导。
saved = workspace.query("get_blockbench_task", taskId=task_id)["data"]
workspace.command("finish_blockbench_task", taskId=task_id, savedSha256=saved["editSha256"])
preview = workspace.query("preview_blockbench_import", taskId=task_id, elementId=element_id)
# 核对 preview 的文件、冲突与覆盖要求后，才显式应用回导和关联。
```

桌面元素检查器提供同一流程和只按任务 ID 打开编辑副本的入口，手工 Blockbench 不要求 MCP。编辑器退出既不完成候选，也不应用回导。`get_blockbench_task` / `list_blockbench_tasks` 保留上述上下文，并从当前元素定义与导入记录投影 `binding.state`（`unbound`、`bound`、`manual`、`element_missing`）；`bound` 只证明定义关联，不证明当前 JAR 或游戏效果。导入完成后仍需显式调用 `bind_blockbench_model`、构建和游戏验证；绑定失败可直接重试，已导入文件保留。

模型父级和贴图引用通过工作区及本地版本资源目录验证；`minecraft:` 也要验证。无外部目录时，健康检查显示未验证，回导预览返回 `MODEL_RESOURCE_UNVERIFIED`，请先完成依赖同步或构建后重试。资源 ID 使用 Minecraft 的 `namespace:path` 语义：例如 `example:block/lamp` 对应 `assets/example/textures/block/lamp.png`；不要额外加一层 `textures/`。父模型或贴图来源在预览后改变时，旧导入计划会被拒绝，需要重新预览。

构建/测试命令返回 `accepted` 时仅代表受理。用 `wait_task(task_id)` 读取真实终态、增量日志和诊断。通过独立 JAR 验收后：

```python
accepted = workspace.export_verified_artifact(acceptance_task_id)
finished = workspace.wait_task(accepted["task"]["id"])
delivery = finished["data"]["task"].get("verifiedExport")
```

MCP 提供 `export_verified_artifact`，参数为 `verifiedTaskId`、`expectedRevision` 及可选 `allowHistorical`。原生 Core 对应 `export_workspace` 的 `verifiedTaskId` 分支。若工作区输入已变，默认拒绝；明确要交付旧记录时才设置 `allow_historical=True` / `allowHistorical: true`。目录中的 JAR、XML 和 `verification.json` 通过相对路径关联；不要再按 `build/libs` 修改时间选择“最新文件”。

关闭重开后，使用原生 `workspace.query("get_workbench")["data"]["recentTasks"]`（MCP 对应 `get_workspace`）发现最近最多 100 条任务观察，再通过 `get_task` 读取所选任务的日志和证明。历史集合包含失败/中断任务；必须检查 `state == "succeeded"` 且 `verification.status == "passed"`，不能把任意任务当作验收成功。宿主外部生成的 XML/JAR 没有产品任务身份时，不能伪造记录导入为通过；应通过正式 `run_gametest` 产生可选任务。

## Blockbench 环境与故障结果

原生 `workspace.query("get_blockbench_environment")` 默认只检查本机安装；MCP 使用同名工具。显式联网探测传 `probeMcp=True, endpoint="http://127.0.0.1:3000/bb-mcp"`，MCP JSON 使用 `{"probeMcp":true,"endpoint":"http://127.0.0.1:3000/bb-mcp"}`。只接受明确端口和路径的 HTTP 回环地址，不接受凭据、查询串或远端主机。

读取 `data.editor` 和 `data.mcp` 两份状态：已安装不等于服务已启动，`tools_available` 只证明工具发现，不能据此宣称模型已创建。合法请求的连接故障仍是查询结果，检查 `mcp.state`、`diagnosticCode`、`failurePhase`；非法地址才是参数错误。常见状态为 `unreachable`、`timeout`、`authentication_required`、`protocol_error`、`initialization_error`、`no_tools`。`busy` 表示没有排队；`cancelled` 表示本次请求中断。

安装检测和 MCP 生命周期共享默认 9 秒预算。`inspectionState` 不是 `completed` 时，结果可能只有已完成的检测信息；`editor.state=unverified` 表示本次无法确认，不能当作未安装。`application` 给出实际 Java、MCP SDK、程序版本和打包 JAR 哈希；开发启动无法验证来源时明确为 `development_or_unverified`。诊断日志保留内部堆栈，任意服务端错误消息不会转发到结果。

受管构建默认复用产品初始化的 Gradle 缓存及其下载源设置；显式 `COPPERBENCH_GRADLE_USER_HOME` 优先，其次是 `GRADLE_USER_HOME`。使用自定义缓存目录时应自行核对其中的镜像配置，不能假定产品缓存中的 init 脚本已经复制过去。
