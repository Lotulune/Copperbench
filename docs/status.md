# 当前状态与发布能力

更新：**2026-10-24**。官方当前稳定版为 **Windows／Linux 0.1.18**，已核对 GitHub 的公开发布和资产列表。[当前下载](releases/current.md)提供安装包、校验文件和各平台发布范围。

0.1.18 新增静态模型与元素预览、专业工作台 Agent，以及 Blockbench 打开与保存同步修复。Windows 的源码实测和公开包核验见[发布记录](https://github.com/Lotulune/Copperbench/blob/6e4f6f47718d0c2f5cd7ef47ac6ace5e4f4ac5d2/docs/testing/maintenance-release-0.1.18.md)；Linux 的冻结候选与验收边界见 [Linux Release](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.18-linux-stable)。

本地工作树仍以 `b1a69d244ab7337608cc302826ed55d4a707f37f` 为基线，产品配置记录 0.1.4，并包含后续本地修改。这次文档更新未调整产品版本或发布产物。README 的 [2026-10-24 截图](../assets/screenshots/README.md)绑定实际前端输入摘要；不能用本地版本号或截图推断已发布包的内容。

**English:** Current public packages are 0.1.18 on both platforms. The local 0.1.4 configuration and dated frontend captures describe this working tree. Release records, local checks and the historical optimization evidence below retain separate source/artifact identities.

## 历史优化快照：2026-10-15

以下保留当时的 0.1.5 发布状态与本地候选、补修结果；其中“本轮”“当前源码”和未发布范围均指该次记录。

更新：2026-10-15。官方当前下载为 [Windows／Linux 0.1.5](releases/current.md)。本地检出基线为 `b1a69d244ab7337608cc302826ed55d4a707f37f`，产品配置和 [product-status.json](../product-status.json) 仍记录本地 0.1.4 基线，另有本批未提交优化与13项复审补修；这些本地文件不覆盖官方发布事实。未发布候选须以最终源码／产物 hash 标识。

独立复审确认的13项缺陷（P1 3项、P2 8项、P3 2项）已修复并通过受影响范围检查，覆盖普通导出、草稿保护、诊断定位、Native SDK关闭与计量、源码身份和性能／贡献者工具。统一检查：Java 67项通过、1项既有按需跳过；浏览器138项、Windows SDK 69项、Linux Native 28项通过；UI 60项、UI-Core 35项、Node 29项、贡献者15项及真实Gradle归档smoke 6项通过。最新源码身份及命令回执见[本轮补修记录](testing/optimization-2026-10-15.md#独立复审后的13项源码补修本轮)。这些源码不等于历史Windows C4／Linux run3包；本轮没有新增原生桌面／JCEF、Linux GUI、Minecraft玩家操作或同世界重开验收，也没有发布。

Official packages remain 0.1.5. The 13 confirmed post-review findings (3 P1, 8 P2, 2 P3) are fixed and verified within the affected headless scope, covering delivery, draft recovery, diagnostic navigation, Native SDK lifecycle/accounting, source identity and measurement/contributor tools. Unified validation: 67 Java cases passed with one existing opt-in case skipped; 138 browser, 69 Windows SDK, 28 Linux Native, 60 UI, 35 UI-Core, 29 Node, 15 contributor and six real Gradle archive cases passed. The [fix record](testing/optimization-2026-10-15.md#独立复审后的13项源码补修本轮) binds the tested implementation and command receipts. These changes are separate from the historical Windows C4 and Linux run3 packages. No new native desktop/JCEF, Linux GUI, player-controlled Minecraft or same-world reopening acceptance, or release, is claimed.

Historical Windows candidate4 and Linux run3 evidence: scoped Windows Fabric diagnostics, same-input tracking, two-process world persistence, exact delivery and same-path data retention have actual evidence. NeoForge1.21.1 build, rendered title/mod-list, loading and normal exit smoke also passed; no NeoForge world/gameplay claim is made. A strictly headless Ubuntu 24.04.4 Hyper-V follow-up has a frozen run3 package, confirmed edit/reopen/normal-close receipts, and 60 Windows SDK plus 19 Linux Native API regression passes. Fabric build, two packaged-jar acceptance cases, ordinary/exact exports, and NeoForge 1.21.1 headless build smoke passed with explicit warm-cache and task-local heap settings. Final host exits were normal, the SSH tunnel was stopped, and the VM was shut down. The original SDK close and later task failures remain recorded. Linux GUI, player-controlled gameplay and same-world reopening are not_run under the required headless constraint. The Linux runtimes and updated SDK are distinct from Windows C4 despite an unchanged application JAR; native measurements remain candidate3 low-sample observations. The existing synchronous cancel timeout and automatic initial-checkpoint file-lock warning remain documented limits. Final documentation was prepared after the Windows candidate4 and Linux run3 freezes and was not repackaged. [English quick start](user/getting-started.en.md) and [Agent onboarding](ai/getting-started.en.md) retain actual entry/contract discovery.

| 层次 | 当前可用范围 | 入口与证据 |
| --- | --- | --- |
| **官方发布已有 / Published 0.1.5** | Windows `v0.1.5`、Linux `v0.1.5-linux-stable`；包含输入诊断、首作教程、草稿／数字输入与创建默认值、编辑器／生成器修复、双语离线安装器；既有工作台与 Native／MCP 路线保留各自合同范围 | [下载安装](releases/current.md) · [官方 0.1.5 记录](https://github.com/Lotulune/Copperbench/blob/e5f91e58d7d3610ef2e3b4d66137022b6ba68f14/docs/testing/maintenance-release-0.1.5.md) · [首作](user/first-creation.md) |
| **当前未发布源码 / Current unreleased source** | 13项复审缺陷已修复并在受影响无界面范围验证；Java 67项通过、1项既有按需跳过；浏览器138项、Windows SDK 69项、Linux Native 28项通过；UI 60项、UI-Core 35项、Node 29项、贡献者15项及真实Gradle归档smoke 6项通过。源码与回执另记；C4／run3历史包不含本轮补修，无新增原生／JCEF／游戏验收或发布 | [本轮补修](testing/optimization-2026-10-15.md#独立复审后的13项源码补修本轮) · [交付规则](user/build-and-export.md) |
| **历史未发布候选 / Historical unreleased candidates** | 以下范围归属补修前Windows候选4／Linux run3；Fabric真实诊断修复、同revision输入失效、两进程同世界持久化、两类交付及旧同路径数据保留已验。NeoForge 1.21.1构建／渲染／加载／正常退出smoke通过，不扩为该加载器的世界玩法。Linux run3已冻结，编辑／重开／正常关闭及19项Native API回归通过；显式暖缓存与任务本地堆配置下，Fabric构建、2项packaged_jar验收／普通及精确导出、NeoForge1.21.1构建smoke通过，虚拟机已正常关闭。原关闭与后续任务失败均保留。C1退出失败、C2定向补验和C3测量分别保留原身份；仍0.1.4、未发布，后置文档身份另记 | [验收报告](testing/optimization-2026-10-15.md) · [首作](user/first-creation.md) · [交付](user/build-and-export.md) · [Agent路线](../sdk/agent-workflows.md) · [源码开发入口](build/development-setup.md) |
| **预览／有边界的能力** | 资源包客户端准备只落地文件；Blockbench 自动建模按当前支持的任务范围运行；datagen、迁移与 Workspace Plan 的预览需确认后才应用 | [资源包](user/README.md#blockbench-与资源包) · [建模任务](user/blockbench-modeling-tasks.md) · [Agent 手册](ai/agent-playbook.md) |
| **未验证／未宣称 / Not established** | 本轮13项补修未新增原生桌面／JCEF／Linux GUI／Minecraft玩家操作或同世界重开验收；Linux GUI／GNOME、玩家操作与世界重开按用户严格无界面要求not_run；Linux默认资源／冷缓存成功未建立；完整安装升级卸载、OS／Wrapper全冷与网络字节归因、辅助技术全矩阵、新手／自治研究未做。C3原生每项n1无p95；历史browser资产搜索243.4ms仍高于建议，无前后提速。既有取消同步超时、C4再次出现的自动初始检查点文件锁告警保留；官方0.1.5范围不变 | [本批范围](testing/optimization-2026-10-15.md) · [官方范围](releases/current.md#release-scope--版本验证范围) · [本地后续明细](remaining-work.md) |

## 支持范围

桌面目标：Windows 11 x64、Ubuntu 24.04 LTS x86_64（GNOME Wayland／Xorg）。Java 生成器：Fabric 和 NeoForge，覆盖 **26.2、26.1.2、1.21.1、1.20.1**；另有独立的 `resourcepack-1.21.1`。每个工作区只有一个活动生成器，实际安装项查新建工作区列表或 `list_new_workspace_generators`，当前环境查 `get_workspace_environment`，矩阵查 `get_version_tracks`。

“支持轨道”表示该轨道已有相应生成、构建或运行记录，不表示所有元素组合、所有玩法或每个新安装包都重新验收过。完整历史保留在[测试索引](testing/README.md)，不随源码新增自动升级为当前产物证据。

## 改动后补哪些验收

| 改动影响 | 本次应补的检查 |
| --- | --- |
| 纯文档、About 和工具说明 | 内容、链接及相应帮助页；不重跑无关游戏场景 |
| 输入校验／保存边界 | 缺省、null、错类型、嵌套路径、修复重试；真实工作区失败后元素、文件、revision 不变；后期失败回滚 |
| 前端依赖 | 锁文件审计、实际依赖链、生产构建、受影响的界面／Blockly 路径 |
| JDK／安装／启动路径 | 对应平台的新候选安装与启动验收，绑定源码与包哈希 |
| MCP／Agent 路径 | 协议与权限、真实工作区修改／失败／修复链；涉及桌面配对时补桌面链路 |
| 客户端／玩法／持久化 | 用隔离工作区和新世界观察玩家行为；持久化须保存、正常退出客户端，再由新客户端进程打开同一世界，并确认两次退出、日志和任务终态 |

只重新打开实际受影响的门禁。既有通过记录保留原来的提交、包哈希、平台和场景；新结论写入对应检查记录。发布操作仍按原发布流程执行。
