# 新手与自治 Agent 任务预登记

R04 的交付是预登记合同与只读校验／汇总入口。当前没有真实参与者或自治 Agent 研究结果；回归中的文件、包和结果均为合成 fixture。工具不会启动产品、运行模型、联系参与者、联网或写入结果文件。

两组分别使用 [新手模板](task-evaluation/novice-plan.template.json)和[自治 Agent 模板](task-evaluation/agent-plan.template.json)，各自登记、执行和汇总，不合并成功率。新手模板仅覆盖创建、构建、普通导出；自治模板覆盖发现、创建、真实构建失败、诊断修复、真实客户端观察和原验收 JAR 的精确交付。两者任务范围不同，不能据此直接比较群体能力。

## 先看计划，不执行任务

```sh
node scripts/evidence/task-evaluation.mjs --help
node scripts/evidence/task-evaluation.mjs docs/testing/task-evaluation/novice-plan.template.json --root .
node scripts/evidence/task-evaluation.mjs docs/testing/task-evaluation/agent-plan.template.json --root .
```

省略 `RESULTS.json` 就是只读计划检查。模板显式为 `state=planned`，包／源码／环境／模型待填项保持 `null`；输出 `status=planned`、`registrationReady=false`、零观察和固定的预计划分母，退出 `0` 仅表示允许查看计划。`registrationIssues` 列出登记前缺项。这不是已执行研究或通过证明。

把模板复制到新的 `output/task-evaluation/<study-id>/plan.json`，补齐后设置 `state=preregistered` 与真实 `registeredAt`。所有 JSON 内的文件引用都相对 `--root`，缺省根目录为计划所在目录；下面统一显式使用仓库根目录。

```sh
node scripts/evidence/task-evaluation.mjs output/task-evaluation/<study-id>/plan.json --root .
node scripts/evidence/task-evaluation.mjs output/task-evaluation/<study-id>/plan.json output/task-evaluation/<study-id>/results.json --root .
```

完整预登记但没有结果时，输出 `registrationReady=true`、`status=not_run`，退出 `2`。stdout 保持 JSON；调用方可将它保存到新的汇总文件，保留历史文件。

| 退出码 | 含义 |
| --- | --- |
| `0` | 显式计划查看，或记录齐备且校验有效；即使所有任务失败，记录也可以齐备。 |
| `1` | 输入、登记身份、证据或协议边界无效；保留 JSON 中可识别的记录与问题。JSON 解析／文件读取失败时错误写入 stderr。 |
| `2` | 完整登记后尚未运行、存在缺失任务、未结束尝试或研究尚未关闭。 |

没有任何退出码表示“产品／研究通过”。`recordsComplete` 只判断记录覆盖、结构与文件摘要；`researchComplete` 还要求 `dataKind=participant`。fixture 最多为 `status=fixture_only`，其真实参与槽位计数始终为零。

## 登记前固定什么

一次计划固定一个 cohort、版本／源码／包、环境、提示、权限、任务定义和参与槽位。每个 `subjectSlots` 槽位都须执行 `tasks` 中的所有任务，不能在观察失败后删槽位或删任务。槽位使用匿名 ID；新手槽位对应一个人，Agent 槽位对应一次独立的预登记研究会话。招募资格、独立性和槽位映射须由研究负责人保管，不记录姓名或凭据。

| 字段 | 固定内容 |
| --- | --- |
| `studyId / cohort / dataKind` | 新手 `novice` 或自治 `autonomous_agent`；真实运行用 `participant`，工具 fixture 用 `fixture`。 |
| `registeredAt` | 观察前的 UTC 时间，精度为秒或三位毫秒；例如 `2026-10-16T01:00:00Z`。 |
| `product.version / source / artifact` | 受测产品版本、完整 commit、实际源码及脏差量摘要、源清单路径／摘要、实际产品包相对路径／字节数／摘要。不能借用历史候选身份。 |
| `environment` | OS、机器／硬件、包启动方式、SDK／工具版本、网络仓库允许列表、依赖／缓存冷暖状态、任务起始状态、桌面控制能力和预先授权范围。 |
| `model / prompt` | Agent 的精确模型 ID、固定版本及设置（含推理、采样、工具／包装器）；完整实际生效的系统／开发者／用户提示和固定工具指令按顺序写入 `prompt`。滚动别名不能代替具体版本。新手 `model=null`，`prompt` 为完整统一说明。 |
| `permissions` | 明列允许及禁止的资源、写入范围、工具、网络和人工帮助。提示和证据中不放 token／密钥。 |
| `rules` | 总墙钟预算、Agent 总 token 预算、最大尝试数、重试触发与会话延续／重置规则、允许介入与否及具体边界。 |
| `tasks` | 固定描述、成功标准、所有终态尝试共有的证据种类，以及 `materials` 起始文件／故障／验收材料的路径与摘要。没有外部材料用 `[]`。 |

源码清单复用 [source-identity.mjs](../../scripts/evidence/source-identity.mjs) 的 `source-paths-v2`，包括实际脏工作树和非忽略新增源码；二进制另外绑定，不从 commit 推断包内容。将生成的清单保存到本次输出目录，再读取其中的身份字段填入计划。例如：

```powershell
$ErrorActionPreference = 'Stop'
node scripts/evidence/source-identity.mjs . > output/task-evaluation/<study-id>/source.json
(Get-FileHash -Algorithm SHA256 -LiteralPath output/task-evaluation/<study-id>/source.json).Hash.ToLowerInvariant()
(Get-FileHash -Algorithm SHA256 -LiteralPath output/task-evaluation/<study-id>/package.zip).Hash.ToLowerInvariant()
(Get-Item -LiteralPath output/task-evaluation/<study-id>/package.zip).Length
```

目录与受测包须已准备好，`<study-id>` 及包名替换为实际值；先检查源码命令真实退出码，再使用输出。工具会核对清单自身文件摘要、清单内的文件／脏差量摘要和非空包的实际字节。任务材料也须为存在的非空本地文件。绝对路径、URL、`..`、重复引用路径和经符号链接逃出根目录的文件会被拒绝。

运行计划检查取得 `planSha256` 和 `expectedAttemptIdentity` 后，在观察前保存这份**原始计划文件及检查回执**。结果须引用原始文件的 SHA-256，包括空格和换行；任何计划改动都需要新研究 ID、重新登记和新的观察。不得先观察、再修改标准或删除失败重做登记。

本地时间字段和文件摘要不能独立证明登记确实早于观察。研究负责人仍须保留有日期的只读登记副本／可信登记记录；本工具只检查声明的时间顺序，不提供时间戳公证、包与源码的构建溯源或模型提供方认证。

## 自治任务的准备与成功证据

准备由研究负责人在计时前完成，并写入 `environment` 及模板的三个 `materials` 文件：

1. `starting-state`：独立 Fabric 1.21.1 工作区的初始清单、空元素状态、实际目录、依赖／缓存条件、SDK 与桌面能力、已经签发的任务能力范围。只登记授权元数据，不保存授权秘密；Agent 不创建授权或接受 EULA。
2. `build-failure-fixture`：预定的**手写源码**文件范围、原始字节／摘要、要植入的精确编译错误、预期诊断及允许的修复范围。Agent 创建元素后才应用故障并启动真实构建；不得任意破坏生成源码或用字段校验拒绝代替构建失败。W1 的受控错误字段不是编译失败。
3. `acceptance-tests`：固定的正例用户断言、完整测试源码／配置及摘要、`packaged_jar` 模式、最小用例数与输入边界。若用清单文件引用多个材料，清单须包含每个实际文件的摘要，研究负责人须核对这些引用；工具只直接核验登记的文件，不递归认证清单语义。

Agent 在预算内执行发现→创建→植入故障并构建失败→诊断修复并重新构建→真实客户端观察及正常退出→原始打包 JAR 验收→精确交付。可用路线以[公开 SDK 工作流](../../sdk/agent-workflows.md)、[同一作品 SDK 示例](../ai/trail-block.md)、[任务授权与打包验收](../ai/task-authorization-and-acceptance.md)和[客户端验收](../ai/client-acceptance.md)的实际支持为准；W3 需要已经真实通过的原始 `packaged_jar` 任务，不能自己制造通过回执。普通导出不是精确验收交付。

成功须有：原失败任务和完整诊断、修复前后输入身份、新成功构建任务、真实客户端截图／动作记录／游戏日志／正常生命周期终态、正例验收计数、原验收的 JAR／报告／源码／输入摘要，以及精确导出目录中的 JAR、XML、`verification.json` 对账。历史导出保持关闭，不能重新构建另一个 JAR 冒充被验收文件。游戏输入遵循 [AGENTS.md](../../AGENTS.md) 的单控制器、截图观察和隔离世界规则；工具回执不替代玩家可见事实。发生持久化任务扩展时另行预登记同世界新进程重开，不把一次放置推断为持久化通过。

准备缺失时保留 `planned/not_run`；若真实尝试已经开始，则记录失败／超时／中止及阻塞证据。成功专属要求在 `successCriteria`，共有 `requiredEvidence=[actions,outcome]`：早期失败的 `outcome` 可以是实际阻塞／终态诊断，无需伪造后续游戏或导出证据。一次研究尝试中的**预期故障构建**是任务步骤；该步骤被正确诊断修复后，整体尝试仍可成功。整个任务未完成而另开研究尝试时才增加 `attempt`，两层失败都必须保留。

## 记录实际结果

结果根对象：`schemaVersion=task-evaluation-results-v1`、与计划相同的 `studyId/cohort/dataKind`、原始 `planSha256`、`closedAt` 和 `attempts`。`attempts=[]` 表示尚无观察；`closedAt=null` 表示仍开放。只有观察实际结束、记录完整后才填关闭时间，工具不生成结果或成功回执。

每次尝试都有以下字段；原记录只追加，不覆盖失败后补成成功：

| 字段 | 记录合同 |
| --- | --- |
| `subjectSlot / taskId / attempt` | 属于计划的槽位／任务；同一槽位任务的次数从 `1` 连续增加，不能重复、跳号、超过上限或在成功／未结束尝试后重试。 |
| `startedAt / endedAt / outcome` | 真实 UTC 时间；`outcome` 为 `success / failed / timeout / abandoned / running`。只有 `running` 的 `endedAt=null`，其他终态都要结束时间。重试时间不重叠；开始不早于登记，关闭不早于任何尝试终态。 |
| `identity` | 每次都复制计划检查给出的完整 `expectedAttemptIdentity` 并按实际核对：版本、源码、包、模型、提示与权限。不能只填模型别名或省掉某个 hash。 |
| `usageTokens` | 新手为 `null`；Agent 为提供方实测的非负输入／输出／推理合计，固定计费口径，跨重试累加。未知不能填 `0` 冒充。 |
| `failureCause` | `failed/timeout/abandoned` 必须是 `{category, details}`；成功／运行中为 `null`。分类为 `product / task_understanding / environment / tooling / time_budget / permission / intervention / participant_withdrawal / other`。 |
| `interventions` | 无帮助也填 `[]`。每次事件记录 `{at, kind, reason, evidence}`；时间位于本次尝试内，并有本地摘要证据。包括提示、接管与环境帮助。 |
| `evidence` | `{kind, path, sha256}` 引用数组，SHA-256 为小写十六进制；终态须覆盖任务固定的所有证据种类。动作轨迹与结果／阻塞分别留证，文件非空且字节摘要匹配。 |

时间预算是同一槽位／任务从首试开始到末试结束的墙钟时间，包含重试空档；token 也按同一单位跨重试累计。超预算成功、禁止介入后的成功、身份或证据不全均不能进入成功分子。失败／超时／中止仍保留；“研究尝试超时”只说明预算结束，不能把仍在运行的产品任务写成正常退出。

## 读汇总时使用的分母

- `summary.subjects` 分别报告计划、开始、所有任务终态有效的参与槽位；“全部任务首试／最终成功”以全部计划槽位为分母。fixture 的 `realParticipantSlotsStarted=0`。
- `firstAttemptSuccess / eventualSuccess` 的共同分母始终是**全部计划槽位 × 全部固定任务**，包括未执行／无效项。成功重试只能增加最终成功，不能抹掉首试失败。`unassisted*` 进一步要求无人工介入；允许帮助时的总成功率不能冒充自治成功率。
- `attempts.records` 为全部原始尝试记录数，另列无效、未归属、重复、未结束和各失败终态。重复记录使受影响任务失效，不会生成额外参与者。`retryCount` 按已登记槽位／任务的不同重试编号计算，原始重复数另列。
- `retryAssignments / intervenedAssignments` 以全部计划槽位任务为分母；`interventionEventsInRawRecords` 是原始已归属记录中的事件数，重复数据可能重复计事件，须结合无效／重复标记解释。
- `failuresByCause` 的 `denominator` 为全部原始 `failed/timeout/abandoned` 记录，`allAttemptDenominator` 同时给出全部尝试记录数；无分类失败列 `unclassified`。无效记录仍保留在这些原始失败统计中，不能据此单独作有效原因分析。
- 耗时只汇总终态且身份／证据有效的记录，包含失败。任务耗时含重试空档，尝试耗时逐次累计，均明确给出 `n`；未结束／无效项另列，不能当零秒样本。没有 p95 或小样本推广结论。

`assignments` 列出全部固定任务及 `not_run/incomplete/success/failed`；`records` 保留每条原始输入、有效性和问题。空计划／重复槽位／重复任务不能推导更小分母，`summary=null` 且失败记录仍保留。工具核验字节与可计算边界，不理解截图内容，也不能自动核准自然语言成功标准、招募资格或任意重试／介入细则；研究负责人必须逐条审查证据与偏差后才解释结果。

## 工具回归

完成所有实现后，通过现有 Node 测试运行器执行：

```sh
node --test scripts/tests/task-evaluation.tests.mjs
```

覆盖分母、缺任务／空结果、全部失败、重试及重复／跳号、未结束／未关闭、实际包／源码／材料摘要、每次身份、证据缺失／变更／越界、时间／token 预算、人工介入和 CLI 只读行为。测试仅证明工具逻辑，不计入真实参与者、自治 Agent 或 Minecraft 验收样本。
