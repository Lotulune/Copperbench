# 当前状态与发布能力

更新：2026-10-15。当前公开下载为 [Windows／Linux 0.1.5](releases/current.md)。源码已进入 **0.1.6 发布准备**，以最新主线 `bc2a512ca07b8c38fe1ff3140b3954563925bc6a` 为整合基线；最终主线 CI、发行候选、包哈希和发布结果尚待取得，见[准备记录](testing/maintenance-release-0.1.6.md)。

Current public packages are **0.1.5**. The **0.1.6** source preparation integrates the optimization work with the current main branch. Earlier checks retain their tested source and artifact identities; they do not establish validation of the final 0.1.6 packages.

| 层次 | 当前可用范围 | 入口与证据 |
| --- | --- | --- |
| **下载版已有 / Published 0.1.5** | Windows `v0.1.5`、Linux `v0.1.5-linux-stable`；双语离线安装器、结构化输入诊断、首作教程、草稿与数字输入修复、创建默认值、光照和 Fabric 模板修复，以及既有工作台和 Native／MCP 路线 | [下载安装](releases/current.md) · [0.1.5 发布记录](testing/maintenance-release-0.1.5.md) |
| **0.1.6 源码准备 / Unreleased source** | 首作快捷入口与任务引导、普通和精确交付、跨页面草稿保护、外部修改与未知保存状态恢复、编译诊断定位、SDK 增量任务恢复和传输计量、贡献者与测量工具；含后续独立复审的 13 项补修 | [0.1.6 准备记录](testing/maintenance-release-0.1.6.md) · [首作](user/first-creation.md) · [构建与导出](user/build-and-export.md) · [Agent 路线](../sdk/agent-workflows.md) |
| **历史优化候选与补修检查** | Windows C1–C4、Linux run3 和 13 项补修保留各自源码、包、通过、失败与复验记录。C4 的 Fabric 玩家操作／同世界重开及 NeoForge 加载 smoke、Linux run3 的受限无界面构建／交付和补修阶段自动化检查均只属于原身份 | [历史优化报告](testing/optimization-2026-10-15.md) · [实施计划](roadmap/optimization-plan-2026-10-15.md) |
| **预览／有边界的能力** | Fabric／NeoForge 26.3 需显式预览协商；editor schema 1.1、字段发现、doctor、生成预检及版本复制保持实际协商范围。资源包客户端准备只落地文件，Blockbench 建模、datagen 和迁移依现有预览与授权流程执行 | [26.3 候选](releases/copperbench-26-3-candidate-2026-10-14.md) · [编辑器合同](ai/editor-schema.md) · [版本复制](ai/version-copy-upgrade.md) · [Agent 接入](ai/getting-started.md) |
| **最终 0.1.6 尚待验证** | 合并后的必需 CI、Windows 包及 Linux 冻结候选／精确授权／来源证明／发布资产核验尚待执行。没有据旧候选新增最终包的完整安装、GNOME Wayland/Xorg、玩家玩法或同世界重开结论；历史性能、暖缓存和已知问题保留原范围 | [准备状态](testing/maintenance-release-0.1.6.md) · [历史边界](testing/optimization-2026-10-15.md) |

## 支持范围

桌面目标：Windows 11 x64、Ubuntu 24.04 LTS x86_64（GNOME Wayland／Xorg）。公开支持的 Java 生成器：Fabric 和 NeoForge，覆盖 **26.2、26.1.2、1.21.1、1.20.1**；另有需显式启用的 **26.3 预览**和独立的 `resourcepack-1.21.1`。每个工作区只有一个活动生成器，实际安装项查新建工作区列表或 `list_new_workspace_generators`，当前环境查 `get_workspace_environment`，矩阵查 `get_version_tracks`。

“支持轨道”表示该轨道已有相应生成、构建或运行记录，不表示所有元素组合、所有玩法或每个新安装包都重新验收过。完整历史保留在[测试索引](testing/README.md)，不随源码新增自动升级为当前产物证据。

## 改动后补哪些验收

| 改动影响 | 本次应补的检查 |
| --- | --- |
| 纯文档、About 和工具说明 | 内容、链接及相应帮助页 |
| 输入校验／保存边界 | 缺省、null、错类型、嵌套路径、修复重试；真实工作区失败后元素、文件、revision 不变；后期失败回滚 |
| 前端依赖 | 锁文件审计、实际依赖链、生产构建、受影响的界面／Blockly 路径 |
| JDK／安装／启动路径 | 对应平台的新候选安装与启动验收，绑定源码与包哈希 |
| MCP／Agent 路径 | 协议与权限、真实工作区修改／失败／修复链；涉及桌面配对时补桌面链路 |
| 客户端／玩法／持久化 | 用隔离工作区和新世界观察玩家行为；持久化须保存、正常退出客户端，再由新进程打开同一世界，并确认两次退出、日志和任务终态 |

只重新打开实际受影响的门禁。既有通过记录保留原来的提交、包哈希、平台和场景；新结论写入对应检查记录。
