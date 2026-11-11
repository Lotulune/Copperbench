# Current downloads / 当前下载

Copperbench Windows 与 Linux **0.1.20** 均已正式发布。打磨 Notebook 界面的新建工作区、设置与对话框，修复简洁创作中打开的视图的按钮样式，并让 Agent 输入框的选项菜单在上方放不下时向下展开。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.20](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.20), published 2026-11-11T07:03:25Z | 图形安装 EXE · Portable ZIP · MSIX · 应用内更新组件 | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.20](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.20-linux-stable), published 2026-11-11T07:55:17Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz · 应用内更新组件 | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 与创作 CLI 单独安装和配置。

## Release scope / 版本验证范围

- 两平台二进制源码为 `5e1ab97f0a10ebf2792e2622ad0d563f8b4ffc29`。Windows 使用签名标签 `v0.1.20`；Linux 签名发布标签指向 `bfb0375420965f133a10c66d5f002bfd4938ad07`，直接晋级已验证的固定候选文件。
- Windows 全部 15 项公开资产经独立下载、逐文件哈希、校验清单和来源核验，见[Windows 发布记录](../testing/maintenance-release-0.1.20.md)。
- Linux 13 项候选门禁、独立消费者与完整草稿下载比对通过，18 项公开资产与固定候选及授权一致，见[Linux 发布记录](../testing/linux-publication-0.1.20.md)。
- [公开包安装验收](../testing/installed-release-0.1.20.md)记录隔离云端的安装、0.1.19 升级、用户文件保留与卸载结果及限制。界面改动在源码浏览器预览中检查，未在原生 JCEF 窗口中复查；完整 GNOME/Wayland 与玩家操作认证未扩展，Linux 保持 `formalSupportClaim=false`。

[Windows 0.1.19](../testing/maintenance-release-0.1.19.md)和 [Linux 0.1.19](../testing/linux-publication-0.1.19.md)保留各自源码、包哈希与范围。随包文档保留构建时的文本；后续记录不改变已发布字节。
