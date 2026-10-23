# Copperbench 图形安装指南

本页说明 Copperbench 双语离线图形安装器的操作；请从[当前下载](../releases/current.md)选择平台版本，并查看该包的验证范围。

This page describes Copperbench's bilingual offline graphical installer. Choose your platform's version and check its package-specific verification in [current downloads](../releases/current.md).

0.1.18 安装包的发布与验证见 [Windows 记录](../testing/maintenance-release-0.1.18.md)和 [Linux 记录](../testing/linux-publication-0.1.18.md)。

For the 0.1.18 installers, see the [Windows publication](../testing/maintenance-release-0.1.18.md) and [Linux publication](../testing/linux-publication-0.1.18.md) records.

## 简体中文

目标平台为 Windows 11 x64 和 Ubuntu 24.04 LTS x86_64（GNOME Wayland / Xorg）；完整桌面与安装验收范围按各包记录确认。安装主程序无需联网，运行环境随包提供；创建工作区、构建模组时需要的依赖下载另算。

### Windows

1. 打开完整的 `Copperbench … Windows 64bit.exe` 安装包，等待解包完成。
2. 在安装向导右上角选择“简体中文”或“English”，并选择深色或浅色。
3. 确认安装位置、桌面快捷方式，阅读许可协议与附加条款后勾选接受。
4. 点击“开始安装”，完成后点击“打开 Copperbench”。

新安装默认只为当前用户安装。检测到旧安装时使用原目录、保留原安装范围；系统安装升级会弹出 Windows 授权窗口。升级前请保存工作并关闭 Copperbench 及它启动的 Minecraft 客户端。

### Ubuntu

1. 在“文件”中右键 `Copperbench … Linux x86_64 Setup.tar.gz`，选择“提取到…”并打开解压目录。
2. 右键 `Install Copperbench`，选择“作为程序运行”。若菜单里没有该项，在“属性”中启用“作为程序执行”，再试一次。
3. 安装向导的语言、安装位置与许可步骤同 Windows。首次安装默认位于 `~/.local/opt/copperbench`。
4. 完成后点击“打开 Copperbench”，之后也可从应用菜单启动。

整个过程不需要输入终端命令。GNOME 启动脚本时可能显示终端窗口，让它随安装向导正常结束即可。已有 Debian 系统安装通过系统包管理器升级，会请求系统授权。

### 语言与卸载

首次按系统语言匹配：中文使用简中，其余使用英文。可以手选，应用首启继承安装语言；应用内已经保存的语言偏好优先，升级保留该偏好。

Windows 从系统“已安装的应用”卸载；Ubuntu 当前用户安装从应用菜单打开“卸载 Copperbench”。卸载保留个人设置、自己创建的工作区以及放入程序目录的其他文件。工作区应放在程序安装目录之外；若旧程序目录中检测到 `.mcreator` 工作区，安装器会提示先移出再升级。

### 安装失败或安全软件告警

- 校验失败：重新下载完整安装包，核对发布页的 SHA-256。
- 文件被占用：关闭 Copperbench 及它启动的客户端、终端后重试。
- 空间不足或无法写入：释放空间，或选择可写的新目录。
- 安全软件报木马：保留检测名称、文件路径、软件版本和安装包 SHA-256，按[安全告警说明](./security-alerts.md)处理。

## English

The target platform is Windows 11 x64 or Ubuntu 24.04 LTS x86_64 with GNOME Wayland / Xorg; full desktop and installation acceptance remain specific to each package's record. The complete package includes the application runtime and installs without a download. Creating workspaces and building mods can require additional downloads.

### Windows

1. Open the complete `Copperbench … Windows 64bit.exe` package and wait for extraction.
2. Choose English or 简体中文 and a light or dark theme in the top right.
3. Review the install location, desktop shortcut, license and additional terms. Accept the license to continue.
4. Click **Install**, then **Open Copperbench** when installation completes.

New installations default to the current user. Upgrades retain the existing location and installation scope; upgrading a system installation requests Windows authorization. Save your work and close Copperbench and its Minecraft clients first.

### Ubuntu

1. In Files, right-click `Copperbench … Linux x86_64 Setup.tar.gz`, choose **Extract to…**, and open the extracted folder.
2. Right-click **Install Copperbench** and choose **Run as a Program**. If unavailable, enable **Executable as Program** in the file's Properties and try again.
3. Choose the language, install location and license options in the same wizard. A new installation defaults to `~/.local/opt/copperbench`.
4. Click **Open Copperbench** when finished. You can also open it from the applications menu later.

No terminal commands are required. GNOME may display a terminal while launching the script; let it close with the wizard. Existing Debian system installations are upgraded through the system package manager and request authorization.

### Language, removal and recovery

First launch uses Simplified Chinese for Chinese system locales and English otherwise. The application inherits the installation choice unless you already saved a language preference in the app. Upgrades preserve that preference.

Use Windows **Installed apps**, or **Uninstall Copperbench** in the Ubuntu applications menu for a per-user installation. Workspaces, personal settings and files added by you are kept. Keep workspaces outside the application directory; move any `.mcreator` workspace out before upgrading if prompted.

If verification fails, download the complete package again and compare its SHA-256 with the release page. If files are busy, close Copperbench, its clients and terminals; if storage or permissions prevent installation, free space or choose a writable new folder. For antivirus detections, retain the exact detection and package details and follow the [security alert guide](./security-alerts.md).
