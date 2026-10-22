# 仓库与文档维护

## 项目目录

| 目录 | 内容 |
| --- | --- |
| `src/`、`plugins/`、`vendor/`、`lib/`、`gradle/` | Java 产品、生成器、上游资源与构建输入 |
| `ui-shell/`、`ui-core/` | 产品界面与 UI/Core 契约 |
| `sdk/`、`scripts/`、`tools/` | SDK、构建与验证工具 |
| `docs/user/`、`docs/ai/`、`docs/build/` | 用户、Agent 与开发指南 |
| `docs/architecture/`、`docs/adr/`、`docs/security/` | 架构、决策与权限边界 |
| `docs/releases/`、`docs/testing/`、`evidence/` | 发布说明、必要验收记录与原始证据 |
| `product-status.json`、`release-control/` | 构建时状态、固定候选与发布授权 |
| `assets/`、`compliance/`、`license/` | 展示素材、来源与许可 |
| `output/`、`build/`、`.tmp/` | Git 忽略的本地产物和工作记录 |

## 文档保留规则

- 常用入口集中在[文档导航](../README.md)。同一主题优先更新现有指南；中英文指南成对维护。
- 当前版本与下载维护在[当前下载](../releases/current.md)，功能边界维护在[当前状态](../status.md)，待办只写入[维护清单](../remaining-work.md)。
- 阶段计划、Agent 交接稿、逐次调试日志和本机清理记录放在 `output/`；进入项目的结论写入对应指南、PR 或发布说明。
- 长期保留会影响使用、开发、兼容性或发布追溯的内容。新增独立文档时，在所属主题入口添加链接。
- 发布授权绑定的文件保留原路径与字节。整理历史文档前检查状态清单、脚本和证据引用，再把历史链接改为固定提交，避免悬空引用。

<a id="history"></a>

## 历史文档快照

清理前的完整文档保留在提交 [`07924ba11a00`](https://github.com/Lotulune/Copperbench/tree/07924ba11a00a8bab9ac1b720bbc01867f27a506)。旧内容仍可追溯，当前目录不再存放完整的阶段规划和过程日志。

- [旧 PRD](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/PRD.md) · [后续阶段需求](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/PRD-NEXT.md)
- [阶段路线](https://github.com/Lotulune/Copperbench/tree/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/roadmap) · [交接稿](https://github.com/Lotulune/Copperbench/tree/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/handoffs) · [调研](https://github.com/Lotulune/Copperbench/tree/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/research)
- [历史测试记录](https://github.com/Lotulune/Copperbench/tree/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing) · [旧维护记录](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/maintenance/repository-maintenance.md)

本地查看原文：

```sh
git show 07924ba11a00a8bab9ac1b720bbc01867f27a506:PRD-NEXT.md
```

## 本地产物

`output/`、`build/` 和 `.tmp/` 可能包含测试工程、工作树及尚未入库的证据。清理时逐项确认用途，保留用户工作区、进行中的任务与必要证据；源码测试不会因名称含旧阶段而自动退役。
