# Copperbench PRD：Blockbench 自动建模与可选工具接入

> 版本：1.6 · 日期：2026-09-14 · 状态：首轮范围完成，Windows / Ubuntu R7 端到端验收通过；第三方图形稳定性及展示限制已记录
> 用户已批准：独立安装、按需引导、复用社区 Blockbench MCP、Copperbench 管理建模任务及资产回导。
> 2026-09-14 后续授权：整理并提交、推送已完成工作；排除仍在处理的 `feature/ui-ux-redesign`，不发布安装包。见[提交前验证](./docs/testing/modeling-scripting-closeout-2026-09-14.md)。
> 基线：[PRD-NEXT.md](./PRD-NEXT.md)、[资产往返决策](./docs/adr/0007-reuse-resource-pack-tools-and-integrate-blockbench.md)。

## 1. 问题与目标

现有产品可以通过 Java 启动 Blockbench 打开已索引的 `.bbmodel`，在进程退出时检查文件变化并登记工作区版本。但 Copperbench MCP 不提供自动建模任务入口，也没有社区 MCP 的连接诊断。Agent 容易把“编辑器已安装”“MCP 可连接”和“建模结果已进入模组”混为一谈。

目标是让通用 Agent 完成“制作带贴图的自定义方块 → 用户继续编辑 → 回导 → 关联元素 → 构建 → 游戏中核验”，并让未使用自定义模型的用户无需安装额外软件。

## 2. 产品边界

- Blockbench 是独立第三方应用；Copperbench 负责工作区、资产、恢复点、冲突、构建和结果证据。
- 首期 Agent 分别连接 Copperbench MCP 与社区 Blockbench MCP。Copperbench 不复制模型、UV、纹理、动画编辑内核，不引入 Python 中转层。
- 保留用户手工编辑流程。社区 MCP 只在 AI 建模时需要。
- 首期不捆绑、镜像或静默安装 Blockbench 和社区插件；下载来自各项目官方来源，由用户主动触发。
- 不自动修改 Agent 配置、启用插件、接受第三方协议或接管用户独立安装的编辑器。
- 实现和测试在项目与临时测试目录中进行；提交、推送按上述后续授权执行，仍不改 CI、发布安装包或修改真实用户模组。

## 3. 需求

### BB-01 安装与连接就绪

- 在资产中心和 AI 设置中提供可折叠、可跳过的连接引导；无资产时也可访问。
- 检测已有安装，并支持重新检测。检测结果必须与实际启动使用同一配置来源。
- 分开报告编辑器安装/版本状态与 MCP 状态。未探测、不可达、协议错误、认证要求、工具清单可读取均有明确说明。
- 使用真实 `initialize → notifications/initialized → tools/list` 验证连接；仅端口开放不能报告成功。
- 提供 `get_blockbench_environment` Core query 和同名第一方 MCP 工具。默认只做本地安装检查，显式 `probeMcp: true` 才探测本机 MCP。
- 支持自定义本机端口和路径。仅接受 HTTP 的 `localhost`、`127.0.0.1`、`[::1]`，禁止远端、用户信息、查询参数、重定向；不得向社区服务转发 Copperbench token、工作区文件或模型内容。
- 成功仅表示 MCP 工具可发现，不代表身份可信、具体建模能力可用、Agent 已连接或回导闭环完成。
- 手动安装路径支持本机文件选择并持久化；检测和启动共用配置，启动 JVM 显式指定的位置优先。

### BB-02 可选安装体验

- 首次启动允许跳过；首次打开模型可进入安装引导，首次 AI 建模再提示插件。
- 提供官方下载和社区插件说明，说明第三方身份与许可证；复制地址必须有成功/失败反馈。
- 已有安装优先复用。未来一键下载须展示来源、版本、安装位置，验证下载完整性，并取得用户主动操作。
- 卸载 Copperbench 不卸载用户独立安装的 Blockbench，不清除其配置和模型。

### BB-03 建模任务生命周期

- 提供开始、查询、完成/取消任务入口。与 UI、MCP、原生 API 共用领域服务、权限和审计；只读身份不能开始写入任务。
- 任务记录 workspaceId、assetId/新资产目标、源哈希与 revision、编辑副本目录、恢复点、格式目标、任务状态和关联输出。
- 对已有资产创建编辑副本，记录源版本；新建资产允许无源文件。相对纹理依赖必须随副本正确迁移或嵌入，不能靠改变目录破坏引用。
- Agent 只编辑任务副本。现有 `.copperbench/asset-leases` 不构成对第三方插件的强制隔离；对外文档不得宣称其能阻止任意插件写文件。
- 完成任务以“保存后提交结果”为边界，不要求关闭 Blockbench。失败/取消保留可恢复的编辑结果，不登记成功。
- 进程退出、编辑器单实例转交、重开工作区和多任务必须具有明确行为；不能仅靠 launcher PID 判断模型是否已保存。

### BB-04 结果回导

- 回导前检查源哈希、目标占用、工作区版本和文件边界；源已变化时明确冲突并保留候选，不覆盖。
- `.bbmodel` 是编辑源；游戏模型 JSON、PNG 与必要 blockstate 是独立交付物。完成源文件保存不能冒充游戏格式导出完成。
- 验证 JSON/模型结构、纹理引用、资源命名与目标格式，再通过已有资产事务能力导入、刷新索引和引用。
- 多文件回导需具有失败恢复语义；重试不能重复导入或重复创建模组元素。
- Agent 使用现有元素/原生资源能力关联模型；构建和游戏验证分别报告，不能从构建成功推断视觉正确。

### BB-05 许可与来源

- Blockbench 与社区 MCP 插件分别记录来源、许可证、测试版本与安装方式；社区插件不得标记为“Blockbench 官方 MCP”。
- Copperbench 自身维持 GPL-3.0-only。当前只引导安装，不分发外部二进制。
- 将来分发 Blockbench 或插件，需保留许可证及版权、标记修改，并提供与分发版本对应的完整源码获取方式；不能只链接浮动仓库首页。
- 自研插件遵循 Blockbench 官方插件例外，但复制社区插件代码仍受该代码自身许可证约束。
- 使用兼容性表述，不暗示官方合作或背书。原创输出不会仅因使用 Blockbench 自动变为 GPL；外来模型/纹理许可另行遵守。

## 4. 交付顺序与状态

| 里程碑 | 范围 | 状态 |
| --- | --- | --- |
| M1 | BB-01 就绪查询、真实 MCP 握手诊断、资产中心/AI 设置按需引导、BB-05 来源说明 | 完成。两平台实际 UI/Core 探测社区插件 1.7.0 的 97 个工具；Windows 确认编辑器 5.1.6，Linux 如实显示版本未确认，见[实测记录](./docs/testing/blockbench-m4-2026-09-13.md) |
| M2 | BB-03 编辑副本、新建/已有资产任务、保存完成入口；BB-02 路径选择与重检 | 完成。两平台真实任务副本、原生几何修改与 Save Project 文件选择器保存通过，编辑器保持开启时可回导；Windows 另验便携版路径持久化 |
| M3 | BB-04 多文件回导、冲突恢复、元素关联、真实 MCP 模型试作 | 完成。R7 两平台实际社区插件试作、三文件回导、绑定、构建、JAR 字节核对、重开与幂等重试通过；最终图集和物品模型修复的 20 项定向回归通过，见[M4 实测记录](./docs/testing/blockbench-m4-2026-09-13.md) |
| M4 | 已安装 Windows/Ubuntu 产品端到端验收、首次启动入口与可选安装体验收口 | 完成。Windows 安装版与 Ubuntu TAR 解包安装版均取得实际游戏方块、纹理、物品证据；正常退出任务成功、卸载非干扰通过。Windows 临时 Mesa 已移除、启动内存恢复 4 GiB；Ubuntu 临时防休眠已结束，两 VM 正常停止 |
| 后续 | Copperbench 内部连接社区 MCP、对外统一入口；用户主动的一键安装 | 不属于首轮实现 |

里程碑状态必须区分代码与自动测试、真实插件试作、已安装产品及游戏内验收。M1 通过不关闭整个 PRD。

2026-09-14 收口：本轮限定 Java 方块导出回导与 Fabric 1.21.1 实际游戏验证，不扩展为所有格式、所有生成器或所有显卡的兼容性承诺。Ubuntu 独立 Blockbench 曾在长时间等待期间发生 GPU 进程崩溃，原方式重开后模型与纹理恢复；默认手持比例偏大、方块名称翻译键仍需后续展示修正。上述限制有独立证据，不影响本轮建模、保存、回导、构建与游戏资源加载闭环结论。未提交、推送或发布候选包。

2026-09-13 M1：新增 Core/MCP 的 `get_blockbench_environment`，采用现有 MCP Java SDK 进行显式探测；资产中心（含空资产状态）与 AI 设置提供可折叠引导和地址复制。桌面查询在后台完成，限制并发探测数量；打开模型时重新定位安装，支持产品启动后新增安装。

2026-09-13 M2：新增 `begin_blockbench_task`、`get_blockbench_task`、`list_blockbench_tasks`、`finish_blockbench_task`、`cancel_blockbench_task` 五个接口、持久任务记录与可移植编辑副本。现阶段限定 `java_block` 源模型，完成仅冻结候选，不写回原资产；新建目标不会预先占用为正式资源。安装位置选择保存在产品用户配置。环境查询按工作区根目录和恢复服务报告 `managedModelingTasksAvailable`，并返回 `modelingTaskScope=java_block_candidate_only`、`automaticModelImportAvailable=false`。[使用说明](./docs/user/blockbench-modeling-tasks.md)。

2026-09-13 M3：新增 `preview_blockbench_import`、`import_blockbench_task`、`recover_blockbench_import`、`bind_blockbench_model`。游戏导出仍由编辑器/社区插件完成；Copperbench 使用带有效期的预览、哈希复核和逐文件恢复日志进行回导。导入记录支持重开后幂等重试；绑定模型时生成器保留导入文件，并在上游插件工作区路径补写引用。环境能力范围更新为 `java_block_export_import`；是否可回导由工作区根目录和恢复服务决定，替代上述 M2 阶段的候选限定值。

## 5. 验收

- M1：未安装、已安装但未探测、连接拒绝、超时、HTTP 错误、非 MCP 服务、有效握手与工具清单；远端 URL 被拒绝；默认查询不联网、不改文件、不启动编辑器；UI 无假“已连接”。
- M1：UI/Core schema/MCP 契约一致，MCP 只读身份可查询；现有资产往返回归；窄屏和键盘可操作；失败有重试和可执行说明。
- M2/M3：正常完成、源冲突、坏 JSON/缺纹理、重复完成、取消、重开恢复、未保存、单实例交接、多文件中途失败。
- M4：同一个已安装候选中，Agent 制作自定义带贴图方块，用户保存修改，不关闭编辑器即可回导；JAR 包含正确导出文件，并取得游戏内视觉证据。Windows 11 与 Ubuntu 24.04 分别记录。

## 6. 依据（2026-09-13 核查）

- [Blockbench 官方仓库及插件例外](https://github.com/JannisX11/blockbench#plugins)
- [Blockbench GPLv3](https://github.com/JannisX11/blockbench/blob/master/LICENSE.MD)
- [社区 MCP 插件及安装说明](https://github.com/jasonjgardner/blockbench-mcp-plugin)
- [社区 MCP 插件 GPLv3](https://github.com/jasonjgardner/blockbench-mcp-plugin/blob/main/LICENSE)
- [GNU：独立程序与组合程序](https://www.gnu.org/licenses/gpl-faq.html#MereAggregation)
- [GPLv3：非源码形式分发](https://www.gnu.org/licenses/gpl-3.0.html#section6)

上述为当前实现路线的许可核查记录，不替代具体发行包的合规检查。
