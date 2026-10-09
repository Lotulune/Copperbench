# Copperbench 下一阶段 PRD：Agent 就绪度与可信交付

版本：1.0｜日期：2026-10-09（日本时间）｜状态：M1 本地链路通过；M2 本地及远端门禁已闭合；2026-10-10 恢复 M3，双平台安装版 GameTest 与工作区界面通过，Ubuntu 实际合成及完整存档重进通过；其余验收仍逐项保留。

本阶段不预先指定发布版本，不覆盖历史 `PRD-NEXT.md`、Stage 14–17 结项记录或 0.1.4 发布结论。产品基线为 `2bfbb48e80dac267b2043ffcbfc795a0bdd931a3`。首批实现及验证边界见[实施记录](../testing/agent-readiness-2026-10-09.md)。

## 1. 产品决策

下一阶段优先解决“新 agent 能发现正确能力、失败后能安全恢复、交付的是已经验证的文件”。暂缓新增大类 Mod Element、扩大操作系统支持和中心服务的大规模重写。

上一轮评估在 `b1a69d2` 上得到 Agent 80/100、开发者 79/100。这是一次工程评估意见，不是标准化 benchmark；不把评分提升作为验收门槛。下一阶段使用可重复的任务成功率、诊断可操作性、源码不被误写及产物证据完整性作为指标。[S1][S2]

### 1.1 基线更新，避免重复开发

`main` 已合并 PR #97，覆盖短超时测试初始化、部分 UI 回归、完整 Chromium 的 CI 路由和 Windows Python 测试。本轮保留这些改动，不重复声称“修复了仍未处理的 100ms 启动问题”。本次未重新读取的 Nightly 状态不能沿用上一轮的失败结论。[S3]

仓库已有 37 类结构化编辑路径及多轨道完整性记录。缺少 `field_contract("item")` / `field_contract("recipe")` 的预创建发现元数据，不等于 item、recipe 不能创建，也不等于整个结构化系统失效。[S4][S5]

### 1.2 上一轮实测已证明与未证明的内容

新 mod“铜质昼夜仪”通过了真实 Native SDK 构建、受控编译故障定位和修复后重建、最终 JAR 的 12 个 GameTest、哈希绑定导出及重开查证；纯 Java 时间计算执行了 54 条断言。混合副本的生成被 `GENERATION_SOURCE_CONFLICT` 拒绝，手写源码保持原样。交付来自新的原生副本，未自动认领生成器所有权。[S1][S2]

这些证据仅适用于当时提交、相应缓存条件与服务端用例。没有证明新提交的安装包、真实客户端画面与输入、玩家实际合成、完整世界退出重进、其他轨道或陌生用户可用性。新版本必须重新执行受影响层级的验收；不得把旧 JAR 的通过记录复制成新版本通过。

## 2. 用户与核心任务

| 角色 | 需要完成的任务 | 成功的可观察结果 |
| --- | --- | --- |
| 首次接入的 coding agent | 发现环境和字段，创建或编辑 mod，诊断失败并交付 | 不靠阅读内部 Java 源码猜字段；得到当前输入对应的已验收 JAR |
| 有经验的模组开发者 | Java/JSON 与受管元素并用，保留 IDE 修改 | 写入冲突可定位；没有隐式接管、覆盖或重放 |
| Copperbench 维护者 | 修改一个模块后判断影响并发布候选 | 独立测试结果完整；能追溯提交、环境、测试宿主和产物 |

主任务流：读取环境 → 发现支持与限制 → 只读预检 → 获准修改 → 构建 → 查看终态诊断 → 显式修复 → packaged-JAR 验收 → 导出同一 JAR → 重开复核。

失败分支：缺少契约时停止猜测并展示替代读取入口；存在源码冲突时保留文件并要求明确决策；超时时返回可区分的任务状态，不自动重复写入或接受授权。

## 3. 范围与不可破坏的约束

本阶段范围：能力发现、诊断、生成预检、回归编排、真实交付门禁、环境检查和有限构建加固。优先覆盖 Fabric 1.21.1，再按现有支持矩阵扩展证据，不能把单轨道通过解释为八轨道通过。

不在首批范围：完整混合工程迁移向导、新增元素种类、模型美术、自动接受 EULA、自动颁发审批、绕过受管源码保护、升级所有依赖、自动合并主分支或发布安装包。外部试用和安装版验收是后续工作包，不虚构已完成。

约束：保持 revision 与逐文件指纹检查；保留 generated/manual 边界；冲突不自动重试；查询不创建探针元素；不因依赖错误改写源文件；导出必须校验被测 JAR 和当前输入；原始诊断、日志与验证记录不得被“可读化”覆盖。

## 4. 需求与优先级总表

P1 是本阶段核心工作；P2 是发布质量及扩大试用前的后续工作。本提案不修改既有 beta gate 的历史状态。

| 编号 | 优先级 | 需求 | 当前状态 / 首批边界 |
| --- | --- | --- | --- |
| AR-01 | P1 | 完整预创建字段契约发现 | M1 已实现：item/recipe 同源完整契约、三态发现、引用查询与最小示例；八轨道创建、保存、模板生成和重开通过，见[M1 记录](../testing/agent-readiness-m1.md) |
| AR-02 | P1 | 可读诊断与原始证据并存 | Native/MCP Python、TypeScript、UI 已对齐；保留原始错误与任务状态，切语言不重放保存/预览；[M2 本地回归通过](../testing/agent-readiness-m2.md) |
| AR-03 | P1 | 生成冲突定位与只读预检 | 开发分支已加入只读预检、结构化冲突与执行共用计划；验证范围见[后续实施记录](../testing/generation-preflight-2026-10-09.md)，其他轨道和安装版仍待验收 |
| AR-04 | P1 | 回归独立执行与可靠超时 | 正常 Nightly 的 14 个独立结果单元全部通过；SDK 故障注入仅使目标单元失败，其他 13 个仍执行通过；见 [M2 记录](../testing/agent-readiness-m2.md) |
| AR-05 | P1 | 真实 mod 交付门禁 | Fabric 1.21.1 真实正负链、5 个业务 GameTest、同 JAR 导出与真重连已在本地及 Nightly 执行；真实客户端与安装版证据由 [M3](../testing/agent-readiness-m3.md) 单独提供 |
| AR-06 | P2 | 只读环境检查与接入说明 | Core/CLI/MCP/SDK doctor 已实现；真实启动器无写入和报告 schema 验证通过，不自动安装或授权 |
| AR-07 | P2 | 构建校验与依赖风险处理 | 17 份现用 wrapper 已加官方摘要；远端 24 组下载用例通过；负向 ZIP 覆盖问题已修复并单独重放 6 个拒绝用例，历史证据限制见 [M2 记录](../testing/agent-readiness-m2.md) |
| AR-08 | P2 | 安装版与陌生用户任务验证 | Windows 新候选安装、发现、构建与失败修复通过；GameTest 因客机内存不足未执行到用例，剩余安装版、玩家及试用验收按用户要求延期，见 [M3 记录](../testing/agent-readiness-m3.md) |
| AR-09 | P2 | 有边界的维护性改进 | M1 已抽取字段契约与输入投影并完成八轨道适配器回归；未扩大到事务、授权或回滚重构 |

## 5. 详细功能规格

### AR-01：完整字段发现，不以猜测填补缺失

**问题。** 当前 `get_workspace_environment.data.fieldContracts` 只公开部分专门契约，SDK 直接索引会产生裸 `KeyError`。通用输入契约不能充当 item/recipe 的完整创建 Schema。[S5]

**首批实现。** 新增 `Workspace.available_field_contracts()`，只读取并返回当前 Core 发布的契约名称。`field_contract()` 对正常契约保持原返回结构；缺失时抛 `NativeApiError(code="FIELD_CONTRACT_UNAVAILABLE")`，提供 `elementType`、`availableContracts`、当前 generator 和需要现有 `elementId` 的 `get_mod_element_editor` 读取入口。畸形响应使用 `NATIVE_INVALID_RESPONSE`，不伪装成“功能不支持”。

这项修复只解决失败方式与恢复指引，不声称补齐了 item/recipe 创建契约。SDK 不创建临时元素、不回退成不完整的 generic 契约、不改变 type 名称后再试。

**完整需求。** Core 公开与实际创建/编辑验证器共享来源的按类型契约，优先 item、recipe。契约必须区分“当前生成器不支持”“支持但尚未公开元数据”“支持且元数据完整”。公开字段 JSON pointer、类型、必填项、默认值、枚举、引用解析入口、生成器限制与最小示例；可选元数据不能在 UI 和 SDK 各自复制一套。

M1 已提供按类型的只读查询 `get_mod_element_field_contract`，Native SDK 入口为
`discover_field_contract()`，原环境查询也增量公开完整的 item/recipe 元数据。
下例仅说明 `not_exposed` 状态的数据结构，不代表当前 Fabric item 的实测能力：

```json
{
  "elementType": "item",
  "generatorId": "fabric-1.21.1",
  "contractVersion": "1",
  "availability": "not_exposed",
  "complete": false,
  "fields": [],
  "alternatives": [{"operation": "get_mod_element_editor", "requires": ["elementId"]}]
}
```

示例故意不伪造字段、默认值或当前支持程度。只有实际生成器与验证器能够给出完整结果时，才能返回 `availability="available"`、`complete=true`。

**验收。** 支持轨道中的 item/recipe 发现结果能构造有效最小元素；保存、生成、重开结果一致。缺失元数据、未知类型、损坏响应各有稳定诊断。所有发现查询均不创建文件、不改变 revision、不启动依赖下载。契约示例由测试消费，不能只验证 JSON 形状。

### AR-02：人能读懂，机器仍能恢复

**首批实现。** SDK 将 `{field}: {reason}` 按 Core 的 `message.args` 渲染，例如 `/commands/0: Expected a non-null command string.`。保留 `NativeApiError.code` 和未经修改的 `details`；其中仍有原始 key、fallback、args。只有简单命名占位符被单次、字面替换，不执行 Python 格式表达式、属性/下标访问或递归替换。缺失参数保持可见，损坏的展示字段使用稳定 code 兜底。

**完整需求。** 对齐 Python Native、Python MCP、TypeScript 和 UI 的显示规则。错误至少回答失败位置、失败原因、允许的下一步、任务是否仍在运行；用户建议与机器 code 分开。中英文切换应基于原始消息重新渲染，不能只保存已经翻译的旧字符串。

**验收。** 覆盖中文、反斜杠、花括号、未知参数、0/false/null、复杂值和旧消息格式。诊断渲染不修改原始响应、不掩盖 `REVISION_CONFLICT`、不重放命令、不关闭仍健康的会话。错误显示测试不能代替实际失败任务测试。

### AR-03：生成冲突可定位，预检不接管文件

**首批实现。** 保留 `GENERATION_SOURCE_CONFLICT` 的已有原因与首个相关路径；显示详情有长度上限并清理控制字符，避免文件名伪装日志换行。原异常仍作为 cause 保留。所有权判定、指纹、锁、回滚和 pending-generation 逻辑不变。

**完整需求。** 引入只读生成预检，返回当前 revision/input fingerprint、受管路径、冲突路径、冲突原因、来源归属和下一步选项。预检与执行共享同一计划逻辑；执行仍需重新校验，不能将“预检通过”当作锁。未知状态必须明确返回 unknown，不能自动认定 native-only 或 managed。

建议结果包含 `conflicts[]`，每项提供 `relativePath`、`reasonCode`、`expectedOwnership`、`observedOwnership`；路径必须限定工作区并经过链接检查。冲突多于上限时分页或明确 truncated，不能静默遗漏。

允许的恢复选择包括保留原生路线、查看源文件、回到明确的迁移评审；只有另行获准的迁移事务才能接管。不得建议手改 ownership 元数据、删除手写文件或关闭保护来“让构建通过”。

**验收。** 未归属基础文件、未归属元素文件、生成前外部修改、用户代码边界损坏、符号链接和越界路径均有对应失败测试；拒绝后手写源码字节不变。预检不写文件。实际任务诊断保留可定位路径，并在依赖准备前拦截已经存在的冲突。

**后续实现。** `preview_generation` 作为空参数只读查询加入 UI-Core、MCP 和两种 Python SDK / TypeScript SDK；返回 revision、工作区输入摘要、受管路径、结构化冲突和明确的截断标志。预检和执行共用计划，已有冲突在补写 Gradle include 和依赖准备之前拒绝。无法完整观察时保留 `unknown` 或冲突加具体原因，不隐式接管源码。真实适配器、任务诊断和未验证层级分别记录在[AR-03 实施记录](../testing/generation-preflight-2026-10-09.md)，不将这一批等同于 M1 全部完成。

### AR-04：测试失败不等于其他领域没有结果

保留 #97 的初始化/目标超时分离，不扩大短超时阈值来掩盖行为问题。本轮将 `wait_task` 的 timeout、poll_interval 校验统一为正且有限；NaN、Infinity、非正数必须在轮询前拒绝。有效等待的终态和日志返回保持兼容；等待超时不等于取消任务。

Nightly 后续按独立能力拆分 SDK（Windows/Linux）、UI、Core/Javadoc/scale、MCP 和生成器矩阵；独立 job 不用 `needs` 串联。汇总 job 使用 always，并根据真实 job 结论汇报 passed/failed/skipped/cancelled。不能通过 `continue-on-error` 把失败改绿；保留主分支的三个 required check 名称或先完成审批后的保护规则迁移。

**验收。** 注入一个 SDK 故障时其他独立回归仍产生结果，汇总失败。每个失败套件都保留日志；没有 artifact 也应有明确原因。Windows/Linux 目标超时测试至少各执行 100 次，0 非预期失败；这只是确定性测试门槛，不宣称消除了所有负载相关问题。

### AR-05：协议测试与真实交付测试分别报告

保留既有 conformance 工具用于协议验证；把模拟宿主上的 build/repair/reconnect 场景清楚标为协议契约测试。新的产品级门禁必须使用真实 Core、可写临时工作区、真实 Gradle、独立 packaged-JAR 测试宿主和 verified export。[S6]

正向流程：固定来源的 mod 样本 → 构建 → 注入受控 Java 错误 → 诊断定位 → 显式修复 → 重建 → 运行有效用户用例 → 校验测试数/失败数/跳过数 → 导出被测 JAR → 重开查证。连接恢复用例必须真的断开再连接；不能用同一客户端两次查询代替。

负向流程：至少包含测试数不足、全部跳过、源码在验收后改变、JAR/报告篡改、历史结果与当前输入不一致。必须阻止误导性的“当前通过”导出；失败证据仍应可查看。实验性历史导出保持明确标记，不作为当前验收通过。

沿用昼夜仪时补独立预期值的 ticks-to-next-phase 断言，并补玩家实际合成及完整存档重进验收；不以复用实现函数作为所有预期值的唯一来源。

**证据要求。** 记录产品和样本提交、操作系统、JDK/loader/API/generator 版本、缓存策略、任务 ID、有效用例数、报告摘要、产物摘要和输入是否仍为当前。最终门禁判断业务断言，不能只看 workflow 绿色或任务 accepted。

### AR-06：接入前的只读环境检查

设计统一 doctor 输出：产品运行时与 workspace 的 Java 路径/版本、生成器、Gradle wrapper、工作目录、当前 Core/SDK 能力、网络与缓存条件、客户端渲染环境是否可用。区分 missing、unsupported、blocked、unknown，给出针对性说明。

不能把 Minecraft 1.21.1 的 Java 21 与产品 JDK 25 混为一项；不能把 Loom 本地 IPC 错误笼统标为下载失败。默认只读，不安装工具、不联网下载、不接受 EULA、不生成授权。需要网络的探测另行显式启动。

### AR-07：构建完整性与可复现性

先给当前根 wrapper 补经官方独立来源确认的 `distributionSha256Sum`，不同时升级 Gradle。对生成器模板、样本和历史证据目录分类处理：当前模板与新样本应覆盖；历史封存证据不能被静默改写。Gradle 的校验只在需要下载 distribution 时执行，已有缓存通过不算下载摘要验证。[S7]

建立审查过的依赖校验与锁定策略。前次 npm 日志出现 high severity 提示，但未做可达性分析；应重新取得当前 audit 明细，区分构建依赖和交付运行时，不直接宣称已被利用，也不盲目执行全量 audit fix。

验收至少含干净缓存默认来源、受支持的镜像配置、错误摘要必须拒绝、warm-cache 构建和版本升级后重跑。必须标明当前源码构建依赖 UI 的范围；Java 快速路径不得替代完整发布构建。

### AR-08 / AR-09：验证外部体验，控制维护范围

安装版验证先选 Windows 11 x64、Ubuntu 24.04 GNOME 现有支持范围，使用同一候选包完成打开、新建、构建、失败定位、源码保护、GameTest/导出和退出重开。真实 Minecraft 输入与图像按仓库控制桥的观察、聚焦、停止规则执行，不能用模拟截图代替。

招募建议为 5–8 位未参与项目的人，给相同任务说明，记录完成/放弃、人工救援、误解和最终产物。样本小，只能作为探索性可用性研究，不发布统计上未经支持的市场结论。

维护性先提取字段契约投影的只读职责，并添加等价测试；不能在同一批同时重构事务、授权和回滚。代码行数只是审查信号，不是缺陷证明；以修改影响面、测试可隔离性和回归结果决定是否继续拆分。

## 6. 指标与验收矩阵

以下数值是提议的目标，不是当前测量结果。正式排期前固定样本、agent/model 配置、任务集和缓存规则。

| 指标 | 定义 | 本阶段目标 |
| --- | --- | --- |
| 契约发现可用性 | item/recipe 支持轨道中，发现→构造→保存→生成→重开的通过比例 | 所声明覆盖矩阵 100%，不支持单元明确标记 |
| 写入安全 | 拒绝场景中受保护源码与关键元数据无非预期变化 | 100%；任一误写阻断发布 |
| 交付可信度 | 被测、导出 JAR 摘要与当前输入关联满足验证要求 | 100%；陈旧/篡改/零有效测试必须拒绝 |
| 受控修复链 | 固定编译故障能定位并显式修复后重建 | 100% 固定样本，不外推未知 bug 自动修复率 |
| 首次任务成功率 | 预先登记的至少 20 次任务中，无人工救援得到有效产物 | 目标 ≥80%；失败和中止保留在分母 |
| 外部用户探索 | 5–8 人固定任务的成功、救援与放弃记录 | 先建立基线，不预设统计显著性 |

至少分别记录 unit/contract、Core adapter、真实 headless、packaged server、installed desktop、真实客户端六层。低层通过不填充高层单元。分别报告 cold/warm cache；不与直接 coding agent 比较时长或 token，除非使用相同任务和对照条件。

## 7. 里程碑与交付责任

| 批次 | 交付物 | 建议责任角色 | 退出条件 |
| --- | --- | --- | --- |
| M0：本轮首批 | SDK 稳定错误与渲染、有限等待校验、冲突详情、回归测试、本文档 | SDK/Core 开发；维护者评审 | 对应测试完成；已实现与待开发明确分开 |
| M1：端到端发现与恢复 | item/recipe 完整契约、只读预检、结构化冲突、真实 mod 正负门禁 | Core/生成器/SDK；测试工程 | 发现→失败→修复→验收→导出真实链通过 |
| M2：持续验证与环境 | Nightly 隔离、doctor、wrapper 摘要和分层缓存验证 | CI/构建负责人 | 单套件故障不吞其他结果；冷缓存证据完整 |
| M3：候选与试用 | 安装版矩阵、真实玩家动作、陌生用户记录 | 发布/测试/产品 | 所声明发布范围对应证据齐全；未验证项显式保留 |

角色是工作归属建议，尚未指派个人，也没有承诺自动排期。每个合并请求尽量只跨一个责任边界；PRD 的全部需求不应一次性合并。

M1 的当前结项范围和逐项审计见[阶段清单](agent-readiness-m1.md)及
[实际验证记录](../testing/agent-readiness-m1.md)。结果对应基线
`436a41761d885fa5ad0d76500e2257438137d75f` 加本地未提交改动，并非新安装包。
宿主机客户端曾启动；用户要求不控制桌面焦点，后续已建立独立 Ubuntu 测试虚拟机。
当前聊天的 Minecraft MCP 未用于客机操作。经用户授权，直接通过客机 Bridge
完成 Ubuntu 已验证 JAR 的错误配方、实际合成 17 个物品、16+1 堆叠和完整
客户端退出重进；两次实际 UUID 一致。新结果见[M3 续测记录](../testing/agent-readiness-m3-resume-2026-10-10.md)。
M2 实现与逐项证据见[阶段清单](agent-readiness-m2.md)和
[集中验证记录](../testing/agent-readiness-m2.md)：同一 `d73b5651` 上正常 Nightly
14/14 通过，SDK 故障注入只有目标任务失败、其余 13 项通过；官方源冷/热缓存验证
也已通过。后续归档 ZIP 路径修复另有 10 项辅助测试和 6 个真实拒绝场景验证，
不冒用之前整轮运行的提交身份。M2 源码阶段已结项，早先本地网络失败仍保留。
M3 的安装版矩阵与外部试用未结项，准备条件和固定任务见
[M3 验收工作单](../testing/agent-readiness-m3.md)。

## 8. 兼容性、回滚与发布决策

`field_contract` 正常返回保持兼容；缺失时从偶然的 KeyError 改为明确的 NativeApiError 是错误处理行为变化，SDK release note 必须提示使用 `error.code`。保留原始 details。已有依赖 KeyError 的调用方需要调整，不称为完全无行为变化。

新 Core 元数据应增量添加、带契约版本；旧 Core/新 SDK、新 Core/旧 SDK 均有测试。不可用状态不能返回一份似乎完整的假契约。来源、授权与历史验证记录不迁移为更强结论。

本轮补丁无持久化格式变更，回滚可恢复到父提交；后续新增持久化数据必须先定义迁移与向后读取。发布前必须核对 actual head，不用已经过时的 CI 结果放行新提交。

Go 条件：对应批次所有强制验收通过、负向安全测试通过、证据可下载且摘要可复算、无新误写和假通过。No-Go 条件：缺失/跳过用例被计作通过、源码或 JAR 已变但仍标当前通过、权限保护被削弱、独立测试结果缺失而仍自动放行。

## 9. 验证命令与开发交接

```bash
python -W error::ResourceWarning -m unittest discover -s sdk/python -p 'test_*.py'
./gradlew test --tests '*GenerationPreparationDiagnosticsTest' --tests '*GenerationConflictDiagnosticTest' --tests '*MCreatorGenerationPreparationTest' javadoc
node scripts/verify-markdown-links.mjs
```

完整 PR CI 仍需按仓库原有规则执行。以上命令不是已执行成功的声明，也不代替 installed/gameplay 验收。新 Core API 实现时同步修改 UI-Core schema、SDK 测试、实际 adapter 测试和用户文档。

## 10. 证据与参考

- [S1：上一轮新 mod 实测 CI](https://github.com/Lotulune/Copperbench/actions/runs/37809815009)；产品基线 b1a69d2，样本 bf07090，含缓存范围限制。
- [S2：昼夜仪说明和实测边界](https://github.com/Lotulune/Copperbench/blob/2a9a57679db38f46815db57d861d17740eb598f9/examples/agent-native/copper-chronometer/README.md)。
- [S3：已合并的 PR #97](https://github.com/Lotulune/Copperbench/pull/97)。
- [S4：当前 remaining-work](https://github.com/Lotulune/Copperbench/blob/2bfbb48e80dac267b2043ffcbfc795a0bdd931a3/docs/remaining-work.md)；历史 gate 与支持范围保持原义。
- [S5：当前 Core 字段契约投影](https://github.com/Lotulune/Copperbench/blob/2bfbb48e80dac267b2043ffcbfc795a0bdd931a3/src/main/java/dev/copperbench/core/application/WorkspaceApplicationService.java#L497-L548)。
- [S6：协议评估脚本](https://github.com/Lotulune/Copperbench/blob/2bfbb48e80dac267b2043ffcbfc795a0bdd931a3/scripts/run-ai-live-evals.py)；[conformance 宿主](https://github.com/Lotulune/Copperbench/blob/2bfbb48e80dac267b2043ffcbfc795a0bdd931a3/src/test/java/dev/copperbench/mcp/McpConformanceServerMain.java)。
- [S7：Gradle distribution 摘要验证](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification-of-downloaded-gradle-distributions)。
