# Stage 14 八轨 Survey Pulse 复测 — 2026-09-07

## 范围与基线

此前原生 Agent 对照只实测 Fabric 1.21.1；这不等于 PRD 只支持该轨道。既有产品矩阵一直包含 Fabric/NeoForge 各四轨。本次将相同勘探杖需求扩展到八轨，并以当前 **Stage 14A 未提交实现**为基线，而非重用旧缺陷状态。

- 基线提交：`eaf18606e683fcc37a395de83c53e538c947760e`。
- 实际产品实现差异：[源码快照](../../evidence/stage14/2026-09-07/eight-track/source-snapshot.json)及[测试源码差异](../../evidence/stage14/2026-09-07/eight-track/tested-source-delta.patch)。单独的 HEAD 不能标识此次脏工作树实验。
- 机器结果：[八轨矩阵](../../evidence/stage14/2026-09-07/eight-track/matrix.json)。逐轨日志、失败尝试和源码摘要在同目录。下表按最终采集结果更新，不使用“脚本退出 0”推断全部子任务成功。
- 宿主：当前 ChatGPT 通过本地编码工具操作 Windows 工作区，不是另行启动独立 Codex CLI。
- 生产源码初始化与 Core 持久化用于元素/代码操作；源文件阶段采用任务 stub，不把它当编译证据。实际 Gradle `assemble` 与 `runServer` 是独立真实子进程。此轮不是安装版 GUI 或真实 HTTP MCP 的全流程重演。

## 测试方法

每轨创建新的隔离工作区，生成 Survey Wand 物品与多文件扫描逻辑。原生对照使用独立原生注册/事件代码与同一纯扫描实现，复用该轨已渲染的构建配置和标准 Wrapper 以匹配依赖，不依赖 Copperbench 运行时、元素元数据或代码包。它是兼容性/反馈对照，**不是零知识、冷缓存、从空目录独立创作竞速**。

所有轨道的意图相同：半径 5/潜行 8、已加载方块立方扫描、矿石数量与最近位置提示、60 tick 冷却、服务端计算、粒子和紫水晶+铜锭配方。Java 与资源格式按版本适配，公共 API 不假定生成器访问扩展存在。需求相同不代表产物已等价；字段持久化偏差会单列失败。

源码完整性最小用例为：主源码与辅助源码外部编辑后 `/displayName` 更新不重写；第二个代码元素占用已有主文件应被拒绝；拒绝后不留下元素/孤儿文件，已有主文件不变。**本轮没有据此声称恢复点回放、接管、指纹、所有 Workspace Plan 或安装产品链路已关闭。**

额外按公开手册使用 `initialValues.fields.maxStackSize=1` 创建、`/fields/maxStackSize=7` 更新，再读取真实上游 `Item.stackSize`。另有底层字段名 `/stackSize=1` 的补充探测，它不是已冻结公开 Core 协议的证明。早期原始结果中的 `canonicalStackSize` 是测试变量名，应按此说明理解；本报告对缺陷的判断以[公开字段重放](../../evidence/stage14/2026-09-07/eight-track/field-contract.json)为准。

启动始终写入 `eula=false`，使用独立 `build/track-bootstrap`，未执行接受协议的操作。原定只观察初始化与协议边界，但发现 **NeoForge 26.1.2 原生开发模式仍越过该文件并生成隔离测试世界**。该会话已被终止，另按唯一工作区路径识别并停止其 Java 子进程；这一异常及日志保留，不能再宣称所有测试都没有创建世界。用户已有世界未被操作。后续 NeoForge 改用 `--initSettings`，与 Fabric 的 EULA 边界探针分开记录。

NeoForge 默认任务可能被 `downloadAssets` 网络准备阻断；排除客户端资产任务的探针采用独立 `*-no-assets` 标签，不冒充默认启动已通过。上述开发环境“世界就绪”不是手动玩法断言，也不提升为 gameplay 或已安装产品通过。

## 结果矩阵

首轮汇总为八轨 × 原生/生成两种工程 **16/16 真实 assemble 通过**。2026-09-08 修复后又完成两组定向重放：FR-ADV-10 的完整 Stage 14A source-integrity 生命周期在 **8/8 轨道通过**；公开 `maxStackSize` 创建/更新也在 **8/8 轨道真实落盘并生成正确 `.stacksTo(...)`**。Fabric 1.20.1 的 Loader 合同随后通过真实启动复演。下面的 bootstrap 仍按各自实际层级记录，不能合并为“八轨 gameplay 通过”。

<!-- MATRIX-START -->
| Track | Primary/helper preservation + collision cleanup | Documented maxStackSize materialized | Generated compile | Native compile | Generated bootstrap | Native bootstrap |
| --- | --- | --- | --- | --- | --- | --- |
| fabric-1.20.1 | passed (full 14A lifecycle) | create 1 -> 1; update 7 -> 7; reopen 7; generated `.stacksTo(1/7)` | passed | passed | initialized (default; Loader 0.15.11; EULA boundary) | initialized (default) |
| fabric-1.21.1 | passed (full 14A lifecycle) | create 1 -> 1; update 7 -> 7; reopen 7; generated `.stacksTo(1/7)` | passed | passed | initialized (default) | initialized (default) |
| fabric-26.1.2 | passed (full 14A lifecycle) | create 1 -> 1; update 7 -> 7; reopen 7; generated `.stacksTo(1/7)` | passed | passed | initialized (default) | initialized (default) |
| fabric-26.2 | passed (full 14A lifecycle) | create 1 -> 1; update 7 -> 7; reopen 7; generated `.stacksTo(1/7)` | passed | passed | initialized (default) | initialized (default) |
| neoforge-1.20.1 | passed (full 14A lifecycle) | create 1 -> 1; update 7 -> 7; reopen 7; generated `.stacksTo(1/7)` | passed | passed | boundary only; initializer not observed (initSettings; client assets excluded) | boundary only; initializer not observed (initSettings; client assets excluded) |
| neoforge-1.21.1 | passed (full 14A lifecycle) | create 1 -> 1; update 7 -> 7; reopen 7; generated `.stacksTo(1/7)` | passed | passed | boundary only; initializer not observed (initSettings; client assets excluded) | boundary only; initializer not observed (initSettings; client assets excluded) |
| neoforge-26.1.2 | passed (full 14A lifecycle) | create 1 -> 1; update 7 -> 7; reopen 7; generated `.stacksTo(1/7)` | passed | passed | blocked_probe_incompatibility (initSettings; client assets excluded) | blocked_probe_incompatibility (initSettings; client assets excluded) |
| neoforge-26.2 | passed (full 14A lifecycle) | create 1 -> 1; update 7 -> 7; reopen 7; generated `.stacksTo(1/7)` | passed | passed | blocked_probe_incompatibility (initSettings; client assets excluded) | blocked_probe_incompatibility (initSettings; client assets excluded) |
<!-- MATRIX-END -->

Fabric 的 `initialized` 要求本例初始化标记与未接受 EULA 的边界都已出现。NeoForge 的 `--initSettings` 是不同的早期退出探针：1.20.1/1.21.1 到达协议或配置边界且退出 0，但未观察到本例初始化标记，标为 boundary only；26.1.2/26.2 两路均在写完配置后被启动器报 `Couldn't find Minecraft server thread` 并退出 1，标为 probe incompatibility/未通过，不据此宣称常规 NeoForge 模组不能运行。原始退出码、标记与异常均保留。

默认 NeoForge 客户端资源准备有下载阻断；单独的无客户端资产探针只提供真实 Minecraft 版本清单里的 asset-index 元数据，并显式排除下载任务，不证明完整默认准备流程通过。开发类路径启动与打包 JAR 部署分开；**八轨均未开展世界内右键/冷却/多人/视觉验收**。

## 已确认的问题与修正

### CB-AUDIT-05：标准 Item 字段提交与实际定义不一致（P1，已修复并八轨重放）

首轮八轨使用公开手册中的 `initialValues.fields.maxStackSize=1` 创建，再使用 `/fields/maxStackSize=7` 更新，虽均返回 `committed`，真实上游 Item 却保持 `stackSize=64`，据此定位到公开字段未映射进真实定义。修复后，2026-09-08 使用当前源码重跑同一八轨契约：8/8 均得到 create=1、update=7、生成 `.stacksTo(1)` / `.stacksTo(7)`，并在 reopen 后保持 7。该结果关闭字段落盘/生成缺陷，但没有执行世界内堆叠行为，因此不填充 Stage 14C gameplay cell。

原生代码显式设置 `stacksTo(1)`。修复后生成工程已在“公开输入 → 上游持久化定义 → 生成源码 → reopen”层恢复该参数意图；完整功能等价仍需 14C 的实际运行值/玩法断言后才能声明。

### CB-AUDIT-06：Fabric 1.20.1 Loader 合同冲突（P1，已修复并真实装载重放）

首轮该轨 `workspacebase/build.gradle` 固定 `fabric-loader:0.15.11`，而 `templates/modbase/fabric.mod.json.ftl` 声明 `fabricloader >=0.19.3`，导致实际生成模组虽编译成功、Loader 却在初始化前拒绝加载。修复后描述符要求与生成依赖对齐为 `fabricloader >=0.15.11`。

2026-09-08 重新由当前源码生成 Fabric 1.20.1 fixture，并真实执行 development `runServer`：Fabric Loader 0.15.11 接受描述符，模组执行 `SurveyPulseMod` 初始化并打印 `SURVEY_PULSE_INITIALIZED track=fabric-1.20.1`，随后到达预期的未接受 EULA 边界，Gradle `BUILD SUCCESSFUL`。因此该 Loader 合同缺陷按“initializer/loading”层关闭；不据此宣称 server-ready、packaged-JAR-loaded 或 gameplay passed。

### 已有修复与非产品失败

Stage 14A 的 CB-AUDIT-01/02 实现进度保留。Fabric 1.21.1 的 CB-AUDIT-03/04 已有上一轮定向修复，本次用当前模板重新生成工作区后再检验，不把旧源码镜像当最新模板证据。

测试过程中出现的驱动问题——异步复制的返回值污染退出码、重新附加工作区使用错误 ID、提前关闭 workspace-owned Gradle connection、未完成 Gradle artifacts/import 准备——均为测试驱动错误，修正后复测，历史失败保留，不计为 Copperbench 产品缺陷。

26.x 提示消息 API 改为 `ServerPlayer.sendSystemMessage`；原生注册不再调用依赖生成器访问扩展的私有 `Items.registerItem`，改用公共 Registry/Item.Properties 接口。这些属于本例版本适配错误，两路共用逻辑对称调整，不据此宣称任一 Agent 性能优劣。

## 对 PRD / Stage 14A 的影响

当前路线补充 [FR-ADV-10](../../PRD-NEXT.md#fr-adv-10-八轨原生与生成工程验证矩阵)，不是重新开始 Stage 14A。2026-09-08 的完整八轨 source-integrity 生命周期已经补齐主/辅助文件外改、metadata-only、冲突/失败清理、reopen/regenerate、恢复真实字节、接管和 stale-source 指纹；CB-AUDIT-05/06 也完成各自定向重放。再结合当时 Windows 候选的真实 installed-product Desktop MCP gate，Stage 14A 按自身 DoD 关闭。此处“14B/14C/14D 仍开放”属于该报告当时的快照；同日后续 14B/14D 已独立关闭，详见 [Stage 14B/14D closure evidence](./stage14-native-authoring-review-reuse-2026-09-08.md)。随后 14C 也通过独立 packaged-JAR/server-ready/gameplay 矩阵关闭，并完成 Stage 14 整体收口，详见 [Stage 14C runtime/gameplay closure evidence](./stage14-runtime-gameplay-closure-2026-09-08.md)。历史 Commands remap 语义检查保留为持续维护项，不再被用作 Stage 14C 运行结果的替代证据或关闭阻断。

不因本报告修改 `product-status.json` 中已发布 Beta 的历史事实。Stage 14A 的 installed-product 结论只绑定到 2026-09-08 G7 gate 所验证的当前 Stage 14A Windows 候选及其记录的 source-delta / installer SHA-256，不追溯改写 Beta 4 历史资产。

## 收尾检查

最初 2026-09-07 八轨审计结束时，`compileJava`、测试驱动编译、本地 Markdown 链接检查与 `git -c core.whitespace=cr-at-eol diff --check` 均通过；当时产品主源码/模板差异前后 SHA-256 相同（`86085C1C19D1E42AF9F397A8A6B88CF418217BF1144EB75DF540604A9F6CBDBE`），[最终一致性记录](../../evidence/stage14/2026-09-07/eight-track/source-unchanged.json)保留该历史检查时间。随后 2026-09-08 的 Stage 14A 修复与关闭重放属于后续产品实现，不再受“原审计未改产品源码”这条历史说明约束。

八个原始唯一测试目录的 Java 运行进程均已复查，最终没有残留匹配进程。2026-09-08 后续修复明确更新了 CB-AUDIT-05/06 与 Stage 14A 状态；它们的关闭依据是新的自动回归、真实 Loader 重放和 installed-product Desktop MCP 证据，而不是回写原始失败记录。
