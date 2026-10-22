# Current downloads / 当前下载

Copperbench Windows 与 Linux **0.1.17** 均已正式发布。修复旧会话原位续聊、附件错误语言切换、长会话附件回收及窄窗口模式名称截断。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.17](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.17), published 2026-10-22T11:44:31Z | 图形安装 EXE · Portable ZIP · MSIX · 应用内更新组件 | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.17](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.17-linux-stable), published 2026-10-22T11:58:13Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz · 应用内更新组件 | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 与创作 CLI 单独安装和配置。

## Release scope / 版本验证范围

- 两平台二进制源码为 `81563c9bc6ae4371b662a65b1b0bbf5ce9e5e4cb`。Windows 使用签名标签 `v0.1.17`；Linux 签名发布标签指向 `7be632956b05e9d5a87e85ba527bd5e7a86dd300`，直接晋级经过独立验证的固定候选文件。
- Windows 15 项公开资产摘要与校验清单一致，独立云端核验已下载并逐文件校验全部 15 个文件，4 个更新组件与描述文件一致，见[Windows 发布记录](../testing/maintenance-release-0.1.17.md)。
- Linux 候选的 13 项门禁、独立消费者和完整草稿下载比对通过；18 项公开资产及 8 个更新组件与固定候选及授权一致，见[Linux 发布记录](../testing/linux-publication-0.1.17.md)。
- [本轮修复回归](../testing/assessment-0.1.16-fixes.md)覆盖旧格式会话、可控 CLI 子进程、附件生命周期及浏览器交互。真实账号的完整安装升级、Minecraft 玩家流程及 Linux GNOME/Wayland 完整认证未重新执行；Linux 保持 `formalSupportClaim=false`。

[Windows 0.1.16](../testing/maintenance-release-0.1.16.md)、[Linux 0.1.16](../testing/linux-publication-0.1.16.md)及[当时的本地 Windows + Codex 首作](../testing/simple-mode-final-package-2026-10-22.md)保留各自源码、包哈希和范围，不转用于新包。

随包文档与状态声明保留对应源码构建时的文本；后续发布记录不改变已发布字节。
