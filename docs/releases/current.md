# Current downloads / 当前下载

Copperbench **0.1.6** 整合首作引导、草稿恢复、诊断定位、普通／精确交付和 SDK 改进，保留双语离线安装器及既有生成器修复。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.6](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.6), published 2026-10-15T15:35:41Z | 图形安装 EXE · Portable ZIP · MSIX | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.6](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.6-linux-stable), published 2026-10-15T16:17:00Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 单独安装。

## Release scope / 版本验证范围

- 二进制来源 A：`9e3dfdeab1bc19c099e739bcda14940d9a6fee30`；[主线 CI 38748273169](https://github.com/Lotulune/Copperbench/actions/runs/38748273169)通过。
- Windows：[运行 38748647623](https://github.com/Lotulune/Copperbench/actions/runs/38748647623) attempt 2 成功，[十项公开资产及来源核验](../../evidence/maintenance/2026-10-15/publication-0.1.6/windows/windows-public-verification.json)通过：九项清单条目对应 API 摘要，校验清单自身及另两个元数据文件另做字节核对，十项来源证明均通过。公开 ZIP 整包摘要和 [23 项归档内容](../../evidence/maintenance/2026-10-15/publication-0.1.6/windows/archive-entry-verification.json)也与 A 一致；EXE／MSIX 没有再做完整下载复验。
- Linux：冻结候选来自主线 [38748273030](https://github.com/Lotulune/Copperbench/actions/runs/38748273030)／A；晋级来源 b9383631ec6e800aa327f9f583fc48213a969648，发布运行 38766657314，结果 success。9 项[公开资产核验](../../evidence/maintenance/2026-10-15/publication-0.1.6/linux/linux-public-verification.json)结果 passed。

最终包未重跑完整安装与全量 Minecraft 玩家／存档验收；Linux 保持 `formalSupportClaim=false`、`fullInstalledAcceptance=not-repeated`。历史 VM、C4、Linux run3 和补修检查保留各自身份，详见[发布记录](../testing/maintenance-release-0.1.6.md)。

随包文档保留 A 构建时文本；本页和仓库指南在随后更新，不改变已发布包的来源或摘要。[0.1.5 记录](../testing/maintenance-release-0.1.5.md)与更早证据保持原样。
