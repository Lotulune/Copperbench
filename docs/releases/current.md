# Current downloads / 当前下载

Checked on 2026-10-14 (Asia/Tokyo). This index lists verified public packages.

Windows 与 Linux **0.1.5** 均已发布，包含输入诊断、首作教程、编辑器与生成器修复，以及双语离线安装器。能力范围见[当前状态](../status.md)，首次创作见[第一件作品](../user/first-creation.md)。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.5](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.5), published 2026-10-14T13:35:33Z | 图形安装 EXE · Portable ZIP · MSIX | [图形安装说明](../user/graphical-installation.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.5](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.5-linux-stable), published 2026-10-14T13:48:26Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz | [Linux 安装说明](../user/linux-installation.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 安装包未做 Authenticode 签名；首次构建需要联网，Blockbench 单独安装。

## Release scope / 版本验证范围

- [Windows 0.1.5 说明](v0.1.5.md)：[发布工作流](https://github.com/Lotulune/Copperbench/actions/runs/38568259409)成功，10 项[公开资产的摘要与来源核验](../../evidence/maintenance/2026-10-14/publication-0.1.5/windows/windows-public-verification.json)通过。
- [Linux 0.1.5 说明](v0.1.5-linux.md)：固定候选通过包结构、隔离启动、双语安装器、JCEF X11 和 Fabric／NeoForge 渲染预检；[发布工作流](https://github.com/Lotulune/Copperbench/actions/runs/38573709058)成功，9 项[公开资产核验](../../evidence/maintenance/2026-10-14/publication-0.1.5/linux/linux-public-verification.json)通过。

本轮有开发客户端的光照、透光、剪羊毛、刷怪蛋、普通船和同一世界存档重开记录。最终 0.1.5 二进制未重跑完整安装和全量游戏验收；Linux 完整 GNOME Wayland/Xorg 安装验收仍未重跑，其他发行版与架构未验证。既有 VM 和历史验收保留原始源码与产物范围。完整来源、检查结果和发布过程见[0.1.5 发布记录](../testing/maintenance-release-0.1.5.md)。
