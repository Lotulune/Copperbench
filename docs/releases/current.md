# Current downloads / 当前下载

Copperbench Windows 与 Linux **0.1.15** 均已正式发布。新增简洁创作与专业工作台切换、五种本机 CLI 创作助手、原生文件选择和草稿保护，并改善任务日志、语言切换、弹窗焦点和原生进度显示。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.15](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.15), published 2026-10-21T09:42:08Z | 图形安装 EXE · Portable ZIP · MSIX · 应用内更新组件 | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.15](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.15-linux-stable), published 2026-10-21T09:54:22Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz · 应用内更新组件 | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 与各个创作 CLI 单独安装和配置。

## Release scope / 版本验证范围

- 两平台二进制源码为 `d99aa3f4af171954a575f82de3afd1ee5b34c9a6`。Windows 使用签名标签 `v0.1.15`；Linux 签名发布标签指向 `3c6be89bbfaafc01e39dac81c924a60fbcfdc46c`，直接晋级已验证的冻结候选文件。
- Windows 的 15 个公开文件与校验清单匹配；已下载正式 EXE，核对 SHA-256 并验证签名来源证明。4 个更新组件的大小、哈希和版本与公开描述文件一致。完整 Windows 源码预检查、正式发布测试、打包与上传验证通过，见[Windows 发布记录](../testing/maintenance-release-0.1.15.md)。
- Linux 的 13 项候选门禁、独立消费者核验、来源证明及完整草稿下载字节比对通过。18 项公开资产匹配冻结文件和标签内授权，8 个更新组件的大小、哈希与版本已核对，见[Linux 发布记录](../testing/linux-publication-0.1.15.md)。
- 本次没有重复最终包的完整安装、升级、卸载、360 复扫或玩家操作验收。Linux 保持 `formalSupportClaim=false`；历史安装与游戏验收只适用于对应的源码和包哈希。

0.1.14 的 Windows 签名源码在原生诊断测试阶段失败，没有公开二进制；修复后以 0.1.15 发布。[失败构建记录](../testing/maintenance-release-0.1.14.md)与冻结候选证据保留。此前 [Windows 0.1.13](../testing/maintenance-release-0.1.13.md)和 [Linux 0.1.13](../testing/linux-publication-0.1.13.md)记录保持原样。

随包文档与状态声明保留对应源码构建时的文本；后续发布记录不改变已发布字节。更早的[公开包云端验收](../testing/cloud-acceptance-0.1.9.md)及[独立升级验收](../testing/in-app-update-2026-10-17.md)不转用于 0.1.15。
