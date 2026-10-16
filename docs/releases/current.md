# Current downloads / 当前下载

Copperbench **0.1.9** 修复 Gradle 配置缓存、旧数据包运行时和 Windows 短路径导致的导出问题，公开安装包已通过云端自动验收。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.9](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.9), published 2026-10-16T12:35:43Z | 图形安装 EXE · Portable ZIP · MSIX | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.9](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.9-linux-stable), published 2026-10-16T12:49:41Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 单独安装。

## Release scope / 版本验证范围

- 二进制来源 A：`9e8fcda24e58d165cb2b767f3a0cb3092a7f527c`；主线 CI、完整 Windows 发布测试及 Linux 候选检查通过。
- Windows 十项公开文件经云端独立下载核对 API 摘要、校验文件和签名来源；Linux 九项公开资产与冻结候选及签名授权一致，发布流程已下载比较草稿字节。[发布记录](../testing/maintenance-release-0.1.9.md)。
- [最终公开包云端验收](../testing/cloud-acceptance-0.1.9.md)三条任务全部通过：Windows Server 2025 Setup、Ubuntu 24.04 Setup 与 DEB。覆盖新装、0.1.7→0.1.9 升级、卸载后数据保留，以及安装后 SDK 的构建、导出和服务端 GameTest。

完整 Windows 11／GNOME 桌面操作、MSIX、UAC 和玩家同世界存档重开不在本次自动验收范围。Linux 保持 `formalSupportClaim=false`，完整桌面认证与云端自动验收分别记录。0.1.8 的签名源码标签保留为失败发布记录，没有正式二进制。

随包文档保留 A 构建时文本；后续仓库记录不改变已发布字节。[0.1.7 记录](../testing/maintenance-release-0.1.7.md)及更早证据保持原样。
