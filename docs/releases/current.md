# Current downloads / 当前下载

Checked on 2026-10-06 (Asia/Tokyo). This index lists published packages; source changes do not update existing downloads.

Windows 0.1.4：已发布（稳定版）。本次更新包含新版 Product Shell、源码编辑、关系图、资产预览和设置。Linux 继续保持 0.1.3，本轮未发布新的 Linux 安装包。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.4](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.4), published 2026-10-06T00:35:06Z | EXE · Portable ZIP · MSIX | [Quick start / 快速开始](../user/getting-started.md) |
| Ubuntu 24.04 LTS x86_64, GNOME Wayland/Xorg | [0.1.3](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.3-linux-stable), published 2026-09-30 UTC | Debian `.deb` · Portable `.tar.gz` | [Linux installation / 安装说明](../user/linux-installation.md) |

请使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 安装包未做 Authenticode 签名；首次构建需要联网，Blockbench 需单独安装。其他 Linux 发行版和架构尚未验证。

## Release scope / 版本验证范围

- [Windows 0.1.4 说明](v0.1.4.md)：最终源码 `aabd70bc` 的[必需 CI](https://github.com/Lotulune/Copperbench/actions/runs/37391113372)与[文档 CI](https://github.com/Lotulune/Copperbench/actions/runs/37391168449)通过。另有开发环境原生 JCEF 和浏览器专项验证；未重做新安装包的完整桌面或全量 Minecraft 游戏验收。[发布工作流](https://github.com/Lotulune/Copperbench/actions/runs/37392359625)结果：success；公开资产核验：[10 项资产的哈希和来源证明](../../evidence/maintenance/2026-10-06/publication-0.1.4/windows-public-verification.json)。详见[发布记录](../testing/product-shell-release-0.1.4.md)。
- [Linux 0.1.3 说明](v0.1.3-linux.md)：固定候选通过包结构、隔离启动、JCEF X11 和 Fabric/NeoForge 1.21.1 渲染预检；未重做完整 GNOME 安装验收。

已知问题：资产预览失败后切换语言，已有错误提示可能保留原语言；点击“重试预览”或重新打开该资产可重新取得当前语言的提示。

[0.1.2 发布记录](../testing/maintenance-release-0.1.2.md)和[Linux 安装验收](../testing/maintenance-012-linux-installed-2026-09-28.md)保留原始源码、二进制和验证范围，不代表 0.1.4 重新通过了相同验收。
