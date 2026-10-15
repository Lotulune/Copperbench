# 当前状态与发布能力

当前稳定版为 **0.1.6**，各平台公开链接与实际核验见[当前下载](releases/current.md)。二进制来源为 A `9e3dfdeab1bc19c099e739bcda14940d9a6fee30`；Linux 晋级来源另记，后续文档提交不替换这两种身份。

| 层次 | 范围 | 入口与证据 |
| --- | --- | --- |
| **0.1.6 已交付能力** | 首作入口和引导、普通／精确交付、草稿保护和外部修改恢复、编译诊断定位、SDK 增量恢复与计量、贡献者工具；保留 0.1.5 修复及双语离线安装器 | [首作](user/first-creation.md) · [构建与导出](user/build-and-export.md) |
| **发布验证** | A 的主线 CI 和 Linux 冻结候选通过；Windows 十项 API／来源及 ZIP／23 项内容核验通过；Linux 公开资产核验 passed | [发布记录](testing/maintenance-release-0.1.6.md) |
| **历史验收** | VM r14/r12、C1–C4、Linux run3 和十三项补修保留原来源、失败和复验，不转作新包的完整安装／玩法通过 | [历史优化报告](testing/optimization-2026-10-15.md) |
| **预览与协商边界** | 26.3 需显式预览；editor schema 1.1、字段发现、doctor、生成预检和版本复制仍按各自合同与授权范围使用 | [编辑器](ai/editor-schema.md) · [版本复制](ai/version-copy-upgrade.md) · [Agent](ai/getting-started.md) |
| **未重复的验收** | 最终包完整安装、GNOME Wayland/Xorg、Blockbench 往返、偏好迁移、玩家玩法和同世界重开未重跑；旧性能和暖缓存结论保留原范围 | [具体边界](testing/maintenance-linux-0.1.6.md) |

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
