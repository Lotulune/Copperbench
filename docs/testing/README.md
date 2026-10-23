# 测试与发布验证 / Testing

按改动层选择 [CONTRIBUTING.md](../../CONTRIBUTING.md#choose-verification-for-the-change) 中的检查。各平台已发布版本、下载和包级验证统一见[当前下载](../releases/current.md)。

## 常用入口

| 目的 | 文档 |
| --- | --- |
| 当前版本的验证范围 | [发布记录入口](../releases/current.md#release-scope--版本验证范围) |
| 发布与生成器门禁 | [门禁规则](release-gates.md) · [机器可读状态](../../product-status.json) |
| Windows 安装验收 | [干净环境](clean-windows-hyperv-checklist.md) · [手工检查](clean-windows11-friend-checklist.md) |
| Linux 环境准备 | [Hyper-V 检查清单](stage15-linux-hyperv-checklist.md) |
| Agent 与首作 | [协议契约评测](protocol-contract-evals.md) · [简洁模式验收](simple-mode-onboarding.md) |
| Minecraft 运行时验收 | [项目验证要求](../../AGENTS.md#minecraft-runtime-validation) |

测试源码位于 [Java](../../src/test/)、[UI](../../ui-shell/e2e/)、[UI-Core](../../ui-core/tests/)、[脚本](../../scripts/tests/)和 [SDK](../../sdk/python/)。

0.1.18 的发布与验证见 [Windows 记录](maintenance-release-0.1.18.md)、[Linux 记录](linux-publication-0.1.18.md)和[图形安装器验收](installed-release-0.1.18.md)。

## 保留记录与历史

本目录保留当前发布记录、`product-status.json` 使用的门禁依据，以及发布授权绑定的报告和其直接引用。摘要绑定的报告保持原路径和原始字节；底层日志、截图和回执继续保存在 [evidence](../../evidence/)。

其他阶段、工单和临时验证记录可从[固定 Git 快照](https://github.com/Lotulune/Copperbench/tree/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing)查阅。报告中的通过、失败和未验证结论只对应其原有源码、二进制与环境。

新的一次性工作日志放在 Git 忽略的 `output/`，复用检查方法更新现有指南；需要长期保留的发布证据按[文档维护规则](../maintenance/repository-maintenance.md)入库。
