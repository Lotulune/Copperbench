# Current downloads / 当前下载

Checked on 2026-10-05 (Asia/Tokyo). This index lists published packages; source changes do not update existing downloads.

当前公开版本如下。0.1.3 之后正在开发的可靠性与文档修正**尚未发布**，下列安装包不包含这些改动。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.3](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.3), published 2026-09-29 UTC | EXE · Portable ZIP · MSIX | [Quick start / 快速开始](../user/getting-started.md) |
| Ubuntu 24.04 LTS x86_64, GNOME Wayland/Xorg | [0.1.3](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.3-linux-stable), published 2026-09-30 UTC | Debian `.deb` · Portable `.tar.gz` | [Linux installation / 安装说明](../user/linux-installation.md) |

Use the checksum file attached to the same release: `SHA256SUMS.txt` for Windows, `linux-candidate-sha256.txt` for Linux. Windows packages are not Authenticode signed. First builds require network access; Blockbench is installed separately. Other Linux distributions and architectures have not been validated.

请使用同一 Release 附带的校验文件核验下载包。Windows 安装包未做 Authenticode 签名；首次构建需要联网，Blockbench 需单独安装，其他 Linux 发行版和架构尚未验证。

## Release scope / 版本验证范围

0.1.3 adds a **JAR folder** button beside **Build**. Each platform's notes describe the checks that apply to its packages:

- [Windows 0.1.3 notes](v0.1.3-windows.md): targeted Java/UI checks and a browser replay with a simulated host. Opening Explorer from the installed client was not re-tested.
- [Linux 0.1.3 notes](v0.1.3-linux.md): the unchanged release candidate passed package/runtime checks, isolated headless startup, packaged JCEF X11 startup and Fabric/NeoForge 1.21.1 X11 render preflights. A fresh full GNOME installed acceptance replay was not performed.

0.1.3 新增构建产物目录按钮。Windows 使用针对性 Java/UI 检查与模拟宿主的浏览器验证；Linux 使用固定候选 CI 验证，并未重新执行完整 GNOME 安装验收。具体范围见上述平台说明。

The [0.1.2 publication record](../testing/maintenance-release-0.1.2.md) and [Linux installed acceptance](../testing/maintenance-012-linux-installed-2026-09-28.md) retain their original source and binary scope. They are historical evidence, not fresh 0.1.3 acceptance or verification of later source changes.

0.1.2 及更早的发布、哈希和验收记录保留原版本范围，不代表 0.1.3 或后续源码重新通过了相同验收。后续源码修正需要自己的验证和发布记录。
