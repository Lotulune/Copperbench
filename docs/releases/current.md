# Current downloads / 当前下载

Copperbench Windows 与 Linux **0.1.16** 均已正式发布。完善简洁首作与双语教程，新增实际模型目录、思考强度、文件/图片附件、会话权限、Markdown/复制和历史整理恢复，并修复停止、草稿与反馈问题，补齐随包 UI 依赖许可正文。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.16](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.16), published 2026-10-22T05:50:38Z | 图形安装 EXE · Portable ZIP · MSIX · 应用内更新组件 | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.16](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.16-linux-stable), published 2026-10-22T06:29:21Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz · 应用内更新组件 | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 与各个创作 CLI 单独安装和配置。

## Release scope / 版本验证范围

- 两平台二进制源码均为 `c87d2e0f0e924adc6ad31575aa5b06fe6da9dcf0`。Windows 使用签名标签 `v0.1.16`；Linux 签名发布标签指向 `ec9a70e1e18ff78cebf4c276211363baaca77371`，直接晋级验证后的固定候选文件。
- Windows 15 项公开资产摘要匹配校验清单，EXE、ZIP 等 10 项文件已实际下载并核对字节及来源证明，4 个更新组件已核对。公开 ZIP 中的完整 UI 许可正文与源码一致，见[Windows 发布记录](../testing/maintenance-release-0.1.16.md)。
- Linux 13 项候选门禁、独立消费者核验、来源证明及完整草稿下载字节比对通过，18 项公开资产和 8 个更新组件匹配固定候选及签名授权，见[Linux 发布记录](../testing/linux-publication-0.1.16.md)。
- [本地 Windows + Codex 候选首作](../testing/simple-mode-final-package-2026-10-22.md)完成了真实附件、审批、停止、游戏放置与世界重开、导出和应用恢复。该记录保留原包哈希；正式重建包的完整安装、升级、卸载、360 复扫和玩家操作未重新执行。Linux 保持 `formalSupportClaim=false`。

[Windows 0.1.15](../testing/maintenance-release-0.1.15.md)和[Linux 0.1.15](../testing/linux-publication-0.1.15.md)保留原记录。0.1.14 Windows 构建未公开二进制，见[失败记录](../testing/maintenance-release-0.1.14.md)；更早的 [Windows 0.1.13](../testing/maintenance-release-0.1.13.md)、[Linux 0.1.13](../testing/linux-publication-0.1.13.md)、[云端验收](../testing/cloud-acceptance-0.1.9.md)及[升级验收](../testing/in-app-update-2026-10-17.md)不转用于当前包。

随包文档与状态声明保留对应源码构建时的文本；后续发布记录不改变已发布字节。
