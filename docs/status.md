# 当前状态与发布能力

更新：2026-10-14。先用这页判断自己使用的是哪一层，再进入对应指南。公开下载记录来自[当前下载](releases/current.md)与 [product-status.json](../product-status.json)，源码修改不会自动进入已有安装包。

| 层次 | 当前可用范围 | 入口与证据 |
| --- | --- | --- |
| **下载版已有** | Windows `v0.1.5`、Linux `v0.1.5-linux-stable`；双语离线安装器、结构化输入诊断、首作教程、草稿与数字编辑修复、创建默认值和光照／船模板修复，以及既有工作台、MCP／SDK、构建与客户端入口 | [下载安装](releases/current.md) · [第一件作品](user/first-creation.md) · [发布记录](testing/maintenance-release-0.1.5.md) |
| **源码新增** | 本轮 bug 修复、首作优化和安装器改动已进入 0.1.5；发布后的主线变化需另看提交记录，已有下载不会自动更新 | [输入诊断](ai/input-diagnostics.md) · [整合检查](testing/first-success-integration-2026-10-14.md) · [依赖记录](../compliance/dependency-audit/2026-10-14.json) |
| **预览／有边界的能力** | Fabric / NeoForge 26.3 需要显式启用预览，操作、元素与字段范围依能力查询；资源包客户端准备只落地文件；Blockbench 自动建模按任务范围运行；datagen、迁移与计划需预览确认 | [26.3 候选范围](releases/copperbench-26-3-candidate-2026-10-14.md) · [资源包](user/README.md#blockbench-与资源包) · [建模任务](user/blockbench-modeling-tasks.md) |
| **未验证／未宣称** | 没有新的真人首作完成记录；最终 0.1.5 未重跑完整安装与全量玩法验收。既有 VM、缓存和开发客户端证据保留原始候选范围；已终止的 Agent／陌生用户研究不列为待完成门禁；额外系统／架构、物理屏幕阅读器认证不视为通过 | [既有范围](remaining-work.md) · [0.1.5 范围](releases/current.md#release-scope--版本验证范围) |

## 支持范围

桌面目标：Windows 11 x64、Ubuntu 24.04 LTS x86_64（GNOME Wayland／Xorg）。公开支持的 Java 生成器：Fabric 和 NeoForge，覆盖 **26.2、26.1.2、1.21.1、1.20.1**；另有需显式启用的 **26.3 预览**和独立的 `resourcepack-1.21.1`。每个工作区只有一个活动生成器，实际安装项查新建工作区列表或 `list_new_workspace_generators`，当前环境查 `get_workspace_environment`，矩阵查 `get_version_tracks`。

“支持轨道”表示该轨道已有相应生成、构建或运行记录，不表示所有元素组合、所有玩法或每个新安装包都重新验收过。完整历史保留在[测试索引](testing/README.md)，不随源码新增自动升级为当前产物证据。

## 改动后补哪些验收

| 改动影响 | 本次应补的检查 |
| --- | --- |
| 纯文档、About 和工具说明 | 内容、链接及相应帮助页；不重跑无关游戏场景 |
| 输入校验／保存边界 | 缺省、null、错类型、嵌套路径、修复重试；真实工作区失败后元素、文件、revision 不变；后期失败回滚 |
| 前端依赖 | 锁文件审计、实际依赖链、生产构建、受影响的界面／Blockly 路径 |
| JDK／安装／启动路径 | 对应平台的新候选安装与启动验收，绑定源码与包哈希 |
| MCP／Agent 路径 | 协议与权限、真实工作区修改／失败／修复链；涉及桌面配对时补桌面链路 |
| 客户端／玩法／持久化 | 用隔离工作区和新世界观察实际玩家行为；持久化还需保存并重进同一世界，确认正常退出、日志和任务终态 |

只重新打开实际受影响的门禁。既有通过记录保留原来的提交、包哈希、平台和场景；新结论写入对应检查记录。发布操作仍按原发布流程执行。
