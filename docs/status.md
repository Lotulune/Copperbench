# 当前状态与发布能力

Windows 与 Linux 当前稳定版均为 **0.1.13**，各平台公开链接与实际核验见[当前下载](releases/current.md)。两平台二进制源码为 `464b75a39c34252f61c7d859b8d267dfabba205c`；Linux 签名发布标签指向 `4110b253f5d3f99218caf817759154ff3e2d2613`，晋级过程未重建候选包。后续文档提交不替换这些身份。

| 层次 | 范围 | 入口与证据 |
| --- | --- | --- |
| **0.1.13 已交付能力** | 共享桌面、原生窗口、图标与应用内更新改进；Windows 更新保留已有快捷方式。保留首作引导、交付与恢复、Gradle 配置缓存导出、归档路径修复及双语离线安装器 | [Windows 变更](releases/v0.1.13.md) · [Linux 变更](releases/v0.1.13-linux.md) · [首作](user/first-creation.md) · [构建与导出](user/build-and-export.md) |
| **发布验证** | Windows 15 项公开资产摘要核验、正式 EXE 下载核验及 4 个更新组件校验通过；Linux 13 项候选门禁、独立消费者核验、签名来源、草稿下载字节比对及 18 项公开资产核验通过 | [Windows 发布记录](testing/maintenance-release-0.1.13.md) · [Linux 发布记录](testing/linux-publication-0.1.13.md) |
| **历史验收** | VM r14/r12、C1–C4、Linux run3 和十三项补修保留原来源、失败和复验，不转作新包的完整安装／玩法通过 | [历史优化报告](testing/optimization-2026-10-15.md) |
| **预览与协商边界** | 26.3 需显式预览；editor schema 1.1、字段发现、doctor、生成预检和版本复制仍按各自合同与授权范围使用 | [编辑器](ai/editor-schema.md) · [版本复制](ai/version-copy-upgrade.md) · [Agent](ai/getting-started.md) |
| **未重复的验收** | 0.1.13 最终包完整安装、升级、卸载及玩家操作验收未重复；Linux 完整 GNOME 会话未重复，保持 `formalSupportClaim=false`。旧性能、安装与暖缓存证据保留原范围 | [Windows 范围](testing/maintenance-release-0.1.13.md) · [Linux 范围](testing/maintenance-linux-0.1.13.md) |

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
