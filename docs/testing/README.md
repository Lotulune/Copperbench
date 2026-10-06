# 测试与验收证据索引

## 当前交付基线

当前稳定版为 Windows `v0.1.4`、Linux `v0.1.4-linux-stable`，下载入口见[当前下载](../releases/current.md)。

| 内容 | 入口 |
| --- | --- |
| Windows 0.1.4 发布与验证 | [发布记录](product-shell-release-0.1.4.md) |
| Linux 0.1.4 发布与验证 | [发布记录](product-shell-linux-publication-0.1.4.md)、[本次验收范围](product-shell-linux-release-0.1.4.md) |
| 历史 0.1.2 维护修复与 Windows 原生回归 | [维护验收](maintenance-2026-09-27.md) |
| 历史 0.1.2 Linux 安装验收 | [0.1.2 Linux 安装验收](maintenance-012-linux-installed-2026-09-28.md) |
| 当前机器可读状态 | [product-status.json](../../product-status.json) |
| 门禁规则 | [发布门禁](release-gates.md) |
| 新工作台的源码、设置、资产和真实 Core 预览 | [Product Shell 工作流验收](product-shell-workflows.md) |
| 待跟进与未验证范围 | [持续维护清单](../remaining-work.md) |

这些入口记录已完成的验证范围；不能把历史通过结果扩展到其他二进制、平台或游戏版本。

## 测试源码与生成产物

| 路径 | 用途与清理边界 |
| --- | --- |
| [src/test](../../src/test/) | Java 回归测试和夹具，保留；名称含旧阶段不代表测试已失效 |
| [ui-shell/e2e](../../ui-shell/e2e/)、[ui-shell/tests](../../ui-shell/tests/)、[ui-core/tests](../../ui-core/tests/) | UI、契约与端到端测试源码，保留 |
| [scripts/tests](../../scripts/tests/)、[sdk/python](../../sdk/python/) | 脚本和 SDK 回归，保留 |
| [Minecraft 控制桥测试](../../tools/minecraft-control-bridge/tests/) | 独立控制桥测试，按该工具的说明执行 |
| [evidence](../../evidence/)、本目录的日期记录 | 归档验收证据；部分文件被发布授权按哈希绑定，保留原文与原路径 |
| `test-results/`、`ui-shell/test-results/`、`playwright-report/` | 测试运行输出；确认所需证据已留存且没有任务使用后可清理 |
| `build/`、`.tmp/`、`output/` | 同时包含生成物、测试工程、工作树或本地证据，必须逐项检查，禁止整目录按名字清空 |

## 历史记录

- Stage 17 的 0.1.1 基线：[发布记录](stage17-stable-publication-2026-09-27.md)、[安装验收](stage17-stable-installed-acceptance-2026-09-27.md)、[v38 收尾](stage-17-v38-closeout-2026-09-26.md)。
- 更早阶段：从 [历史路线图](../roadmap/README.md)和 [历史需求](../../PRD-NEXT.md)查找对应门禁。
- 日期报告中的“未发布”“仍开放”和失败重试描述对应当时状态，后续闭环另有记录。不要删除失败记录或改写为当前通过。
- 2026-09-28 本地清理退役了指定 Linux 测试 VM，并清理旧候选的部署包和部分生成目录。历史报告中的本机绝对路径可能已不存在；仓库内发布证据保持不变。范围见 [仓库维护说明](../maintenance/repository-maintenance.md)。
