# 简洁模式接入与教程优化验证

基线：`21d8fc0148c5decf48589236b5b57b131517d2b0`。本记录对应 `codex/simple-mode-onboarding` 的源码修改，未发布新版本。

## 实现范围

- 运行时固定产品文案以稳定标识、双语模板和参数投影；用户输入、模型回复与历史自由文本保持原文。
- 五 CLI 安装帮助、版本/路径/错误检测，独立协议握手与实际模型请求的验证范围分开呈现。
- 中英文首作指南与帮助入口；简洁/专业遮罩教程，以及原生首启步骤动画。
- Markdown、复制和安全外部链接；历史容量、导出、确认删除、原始备份恢复。
- 原生模型目录与应用内模型选择；会话绑定模型，切换时保留未发送草稿。统一输入框焦点边框，长审批信息与输入框分开滚动。

安装帮助使用上游官方渠道；安装命令由用户在系统终端执行。Claude Code 直接运行主机安装，保留已有源与凭据；模型可在应用内选择，绑定当前对话。

## 检查结果

| 检查 | 结果与范围 |
| --- | --- |
| Java 编译 / Javadoc | 通过 |
| Agent、桥接、首启偏好定向测试 | 47 项通过，0 跳过；覆盖会话数量/字节容量恢复、原始备份、导出字段、活动删除保护、旧载荷及协议握手 |
| 原生 JCEF 可访问性 | `Stage9NativeJcefAccessibilityTest` 通过，实际 JCEF 137；覆盖既有对话框键盘和控件合同 |
| UI 单元测试 | 73 项通过 |
| UI-Core 契约 | 50 项通过 |
| 生产 UI 构建 / 双语门禁 | 通过；424 个词条、1746 条英文消息及源覆盖检查 |
| 既有简洁模式与首作浏览器回归 | Chromium 和 1366×768 共 64 项通过 |
| 新增浏览器回归 | 同两项目共 18 项通过；包含 500px 窄窗口、双语审批状态、Markdown/复制/安全链接、上滚阅读、历史管理、双模式教程及减少动画 |
| 窄窗口安装面板最终修整 | 对应 2 项定向复验通过；改用完整图标选择器，避免横排名称被裁切 |
| Markdown 相对链接 / Git diff whitespace | 通过 |
| 设计机械检查 | 无发现；已检查明暗主题和窄窗口截图 |

新增 `react-markdown` 使用默认安全解析，不启用原始 HTML；只允许 HTTP/HTTPS 链接。Vite 锁文件更新到兼容补丁 6.4.5，修复安装时发现的旧开发服务公告。最终 `npm audit` 重试通过，0 漏洞；首次单独复查遇到 TLS 网络错误，保留两次原始报告。

浏览器宿主为受控夹具。`agent-runtime.json` 来自 `AgentChatServiceTest.liveApprovalSnapshotHasLocalizedMetadataAndActiveHistoryCannotBeDeleted` 产生的真实服务 snapshot，只将 UUID、时间与主机路径规范化；前端测试覆盖该载荷经 `parseAgentSnapshot` 到 UI 的路径。此协议夹具不代表真实模型审批验收。

本地日志、截图、协议报告和来源清单位于 `output/simple-mode-onboarding/`。`source-manifest.json` 记录基线、分支、修改状态及相关源码 SHA-256；不是已发布包的身份。首启夹具首次启动缺少模块开放设置，已按其他既有夹具补齐初始化，之后正常显示。

### 主机 CLI 实测

| 助手 | 检测版本 | 实际验证 |
| --- | --- | --- |
| Codex CLI | 0.162.1 | 程序/版本检测、独立协议握手通过 |
| Claude Code | 2.1.304 | 程序/版本检测、独立协议握手通过；使用主机原安装 |
| Grok Build | 1.0.62 (1474f9cbdd91) | 程序/版本检测、独立协议握手通过 |
| Antigravity CLI | 1.4.2 | 程序/版本检测通过；无独立握手接口，未请求模型 |
| DeepSeek Harness | 0.2.1-rc.2 | 程序/版本检测、独立协议握手通过 |

使用显式验收入口 `dev.copperbench.agent.AgentConnectionProbeMain`，最终为 idle、0 个会话、0 条消息。没有模型创作请求；不据此声称登录、模型调用、作品修改或游戏交付成功。报告在 `installed-cli/protocol-checks.json`。

### 模型选择与输入框续作（2026-10-22）

输入框和工具栏的分割来自全局 textarea 焦点阴影叠加外层 composer。现在焦点边框和阴影统一放在外层，明暗主题及 500×600 窄窗口均已检查截图。

新增模型选择器，支持目录、搜索、刷新、手动模型 ID、键盘操作和历史模型恢复。切换模型新建对话并保留未发送草稿；活动请求期间禁切。Codex 的 thread/start、thread/resume、turn/start 显式传入 model；Claude/Grok/Antigravity 使用原生参数，DeepSeek 依据 ACP 返回的模型能力设置并核对确认。Claude 自定义源读取主机配置的模型端点，不把凭据投影到 UI，不改写主机配置。

| 续作检查 | 实际结果 |
| --- | --- |
| Java Agent / JCEF Agent bridge / Javadoc | 55 项通过，0 跳过；编译和 Javadoc 通过 |
| UI 单测 / UI-Core 契约 / 生产构建 | 73 / 50 项通过；构建与双语门禁通过 |
| 浏览器四组相关场景 | 首轮 92 项：91 通过、1 失败。发现刷新模型目录误关下拉框，修复后两种窗口的全部 8 个模型场景通过；不是首轮全绿 |
| 设计索引与文档 | 更新 composer 描述和 sidecar 示例；doctor findings 为空，Markdown 链接与 diff whitespace 通过 |
| 真实模型目录 | Codex 0.162.1 返回 8 个模型，默认 gpt-6.1-sol；Grok 1.0.62 返回 4 个模型，默认 grok-4.7。目录发现未发送用户提示或创建 Copperbench 对话 |
| Claude 自定义源 | 受控 HTTP 测试覆盖异品牌模型、分页、错误与重定向边界。当前真实源不可用，未继续真实 Claude 创作请求 |

续作原始日志、截图、已安装 CLI 目录和源码清单位于 `output/model-selection-validation/`。代码变更后的结果与上表之前的历史测试范围分开记录。

### 真实审批布局修复（2026-10-22）

候选 3 的真实 Codex 命令审批带有重复命令和策略元数据，整段 JSON 占满输入区域。修复为理由、位置与原始命令，完整操作信息仍可展开核对；审批放入对话滚动区域，避免挤走输入框。先限制详情高度时，1366 窗口仍能复现输入框不可见；调整区域后 8 个相关浏览器场景全部通过，包含长载荷、澄清回答、语言切换和模式/草稿/停止回归。

较长的实际命令还暴露了协议元数据重复导致的详情截断。Codex 投影现只移除重复命令解析及本界面不提供的持久策略提案，保留原始命令、工作目录、理由和额外访问范围；33 项 Agent 协议/服务定向复验通过，包含超过原载荷上限的重复数据样例。最终候选的大型保存/构建命令实机仍显示完整命令与可展开操作信息。

候选 3 实机确认 GPT-6.1-Sol 已响应并调用 SDK，且真实“允许这次”回传后成功读取了当前生成器字段。专业模式六步教程观察到位移与淡入淡出，稳定后的高亮与控件对应。运行中切换模式保留会话和未发送草稿。主动停止后会话 interrupted=false、模型仍为 gpt-6.1-sol；通过公开 SDK 另行核对 activeTasks=[]、recentTasks=[]、元素为空。随后正常关闭应用；没有强杀或修改权限设置。

## 安装包首作验收（SC-04）

**Windows + Codex 的真实首作、错误恢复、继续编辑、两次客户端持久化、普通导出和作品重开已通过。** 最终为本地 0.1.16 候选 5，未发布、未提交。复用主机 Gradle 缓存，不是冷缓存验收。原始截图、输入动作、SDK 回执、游戏日志、产物身份和源码清单位于 `output/minecraft-validation/sc04-20261021/`。

### 最终身份

| 项目 | 记录 |
| --- | --- |
| 构建标识 | local-windows-0.1.16-sc04-20261022-attempt-5 |
| 包来源 | 本工作树 `gradlew.bat exportWindowsZip`；`build/export/Copperbench 0.1.16 Windows 64bit.zip` |
| ZIP SHA-256 | b7094b18cb2d19030e3b0c89f3dc11e67b332059b8711cd8aa6e0802d720e26b |
| 产品 JAR SHA-256 | d7fd7dd778bad8c558afce55da6e27b7995cb45dc298028bd1d44b732bb1e46e |
| 基线与源码清单 | 21d8fc0148c5decf48589236b5b57b131517d2b0 + 未提交修改；`candidate-source-manifest.json` SHA-256 为 b1a802e1a7c2200a406b3ac30e7084f00b106ff111376ee44caef548febb525f |
| 环境 | Windows 11 x64；125% / 120 DPI；Copperbench 中文，Minecraft 英文 |
| 助手 | 主机 Codex CLI 0.162.1，原生 app-server；UI 明确选择 gpt-6.1-sol，沿用主机已有认证，不改写全局配置 |
| 独立工作区 | `D:/AICoding/CopperbenchAcceptance/sc04-20261021/workspace/sc_trail_block.mcreator`；SC04 Trail Block；Fabric 1.21.1 / Java 21 |
| 测试世界 | `CB_SC04_20261022_TrailBlock`；Creative / Superflat；位于该工作区 `run/saves/` |
| 元素 | 46f5febe-b0f7-4d82-b3e7-36e40ad386db；`sc_trail_block:trail_block` |

早期候选与最终包分开记录：候选 1 走通原生三步首启，并暴露旧手动首作指南误入简洁模式的问题；修复后 44 个相关浏览器场景通过。候选 2 走通简洁模式六步动画与 CLI 检测，Claude 源返回 503，没有作品修改。候选 3 验证模型选择、专业教程、模式/草稿连续性和停止；候选 4 完成真实方块创建、无效字段预览及修复构建。候选 5 消除了长审批载荷的重复命令数据，并完成重开后实际编辑、构建、两次试玩与导出。各候选身份保存在 `attempt-*/candidate.json`。

### 实际步骤与结果

| 步骤 | 结果与证据 |
| --- | --- |
| 模型选择与原生恢复 | UI 读取 Codex 实际目录并选择 gpt-6.1-sol；正常关闭重开后保留模型和原对话，显式发送才继续 |
| 创建与审批 | 真实 CLI 审批回传“允许这次”；公共 SDK 创建唯一 Trail Block。仅批准检查过的当前工作区操作，没有改写 CLI 权限配置 |
| 受控失败恢复 | 将 `/hardness` 的预览值设为字符串 `invalid-number`，返回 `FIELD_TYPE_INVALID` / `canApply=false`；修订号仍为 1，前后编辑投影摘要相同。随后正确保存数字 3/6、minecraft:stone、hasBlockItem=true |
| 首次构建 | `58736f16-47bb-4f54-b452-7577ab599d72` succeeded；`BUILD SUCCESSFUL in 22s`；回执在 `agent-first-creation/` |
| 最终候选继续编辑 | 重开后使用候选 5 附带的 SDK，只修改已有元素的描述；回读修订 3，未重复创建，其他字段保持。构建 `3daa1636-f0d6-43a9-a7fe-54aa63738c2a` succeeded；`BUILD SUCCESSFUL in 10s`；回执在 `agent-continued-edit/` |
| 模式/草稿/停止 | 运行中切换简洁/专业并完成专业六步教程，原对话及未发送草稿保留。停止后另行读取 Core，确认当时没有活动或已提交构建任务；没有把助手停止当作取消 Core 任务 |
| 第一次试玩 | 从简洁模式按钮启动任务 `b2a41ad2-ca00-4175-846c-1e4b3450c8d7`，客户端 PID 47840。游戏内 `/give` 获得 16 个 Trail Block，正常右键放置；F3 确认 ID 与坐标 `-5, -60, -3`，石头纹理正常 |
| 正常退出 | 保存世界、返回标题，再 Quit Game；PID 47840 消失，游戏日志确认玩家/所有维度保存和 Stopping，原 run_client 任务 succeeded |
| 新进程重开 | 同一应用按钮启动任务 `ee26d6c4-c3d8-4f94-b3a7-77f96c5f3a8b`，新 PID 27776；从世界列表打开同名存档，未重新给物品或放置。原方块 ID、位置、外观及快捷栏 16 个物品均保留 |
| 第二次退出 | 正常保存并 Quit Game，PID 27776 消失；游戏日志确认保存，第二个 run_client 任务 succeeded |
| 普通导出 | 界面指定 `build/libs/sc04-20261022/trail-block.jar`；导出任务 `1d62efd8-c730-4b10-ba6a-581713bd877e` succeeded；文件存在且与构建 JAR 摘要一致 |
| 导出后重开 | 正常关闭应用，日志确认保存与退出；再次打开同一工程，元素、描述、硬度/抗性、模型选择及已完成任务保留，活动任务为空。界面保留在简洁模式的方块设置页 |

两次试玩和导出任务的输入摘要一致：`95a1600f7c7a6001a1b0cf9edc3c7b828605d1cab47b5ca97aa45e407067217c`。导出 JAR 为 42,617 字节，SHA-256：`d3e8ed8767c6204cf3e30810ad5fb36b1b17aa1c37412923c46aff13d18ea6fd`。ZIP 内容确认模组 ID 为 `sc_trail_block`，包含方块 class 与石头纹理引用；身份记录在 `export-artifact.json`。

`/give` 用于取得物品，`/tp @s ~ ~ ~ 0 45` 只设置观察角度；方块由实际右键放置。重开阶段没有使用这些命令，也没有修改世界文件。动作记录与截图可区分准备命令和玩家行为。游戏名称因 Minecraft 文本框截断原计划长名称，创建前改为上表中的短名称；没有覆盖任何已有世界。

### 范围与工具限制

本轮证明主平台上的 CLI → SDK 保存/修复 → 构建 → 开发客户端试玩/跨进程保存 → 普通 JAR 导出路径。没有执行独立的“打包 JAR 验收”任务，不给普通导出附加该通过标识；界面上的引导确认也未被伪造。

其他四种 CLI 与 Linux 的完整首作未执行；它们的检测、协议握手、模型目录或受控协议测试不能外推为完整游戏交付。真实 Claude 源失败已单独记录，后续没有继续 Claude 创作请求。朋友体验与普通用户完成率没有测量。

Windows 的 JVM 属性附加查询被拒绝，因此未使用该查询作为运行依据；进程绑定来自唯一的目标启动配置、运行任务、新鲜 `run/logs` 和同名世界目录。独占全屏截图为黑色，恢复普通窗口并最大化后继续验收；窗口切换时混入无关后台画面的截图已排除。上述操作没有修改安全设置，也没有启动已移除的 Minecraft 控制桥。

## 官方安装资料

使用 `smart-search fetch` 于 2026-10-21 抓取了 [Codex README](https://github.com/openai/codex)、[Claude Code setup](https://code.claude.com/docs/en/setup)、[Grok Build overview](https://docs.x.ai/build/overview)、[Antigravity overview](https://antigravity.google/docs/cli/overview/) 和 [DeepSeek Harness README](https://github.com/deepseek-ai/deepseek-harness)。Antigravity 安装详情抓取失败，界面提供官方安装页入口，不推测安装命令。

DeepSeek README 确认官方 npm 包 `@deepseek-ai/dsh`；界面提供该包的 npm 全局安装方式以让宿主发现 `dsh`。连接检查只发送协议初始化，不发送模型提示词。Antigravity 不提供该检查，成功真实回复才能记录请求验证。


后续模型强度、附件和手动模型输入的独立验证见[聊天选项验收](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/agent-composer-options.md)，不沿用本页旧候选包的游戏结论。
