# Copperbench Linux 安装说明

[English](linux-installation.en.md) · [下一步：第一件作品](first-creation.md)

从[当前下载](../releases/current.md)的 Linux 稳定版入口下载图形 `Setup.tar.gz`、Debian `.deb` 或便携 `.tar.gz`。目标环境为 Ubuntu 24.04 LTS x86_64；其他发行版和架构未验证。包内提供 JBR 25 / JCEF 和独立 Java 21，不要求先安装系统 Java、Gradle 或 Git。

## 当前版本校验与安装

使用**同一 Release** 的 `linux-candidate-sha256.txt`；核对其候选元数据、来源证明与 `LINUX-RELEASE-AUTHORIZATION.json`。不能用下方历史版本的哈希校验新包。

1. 图形向导：解压 `Setup.tar.gz`，运行 `Install Copperbench`，按[中英图形安装步骤](graphical-installation.md)选择位置并审阅许可。
2. Debian：在下载目录用 `sudo apt install ./"downloaded-package.deb"` 安装，将引号内名称替换为实际下载文件名；随后从菜单或 `copperbench` 启动。
3. 便携包：解压 Release 中的便携 `.tar.gz`，打开实际解压目录运行 `copperbench.sh`；不假定旧版目录名。

官方记录区分候选的包内容、权限、启动、安装器／JCEF／X11 渲染检查与完整 GNOME 会话。当前发布不扩大完整 GNOME／Wayland 安装、升级、卸载、真实 CLI 账号或玩家操作认证，具体身份与结果见[当前发布范围](../releases/current.md#current-release-scope--当前发布范围)。

## 历史 0.1.4 文件与命令

以下仅供既有 0.1.4 包使用，保留原文件名、哈希与记录；新安装请使用上方当前版本入口。

核对 Release 附带的 `linux-candidate-sha256.txt`、`LINUX-RELEASE-AUTHORIZATION.json` 及 GitHub 来源证明。0.1.4 的公开文件哈希为：

- `copperbench_0.1.4_amd64.deb`：`0460c27bb26927ef8240265775b6f071a49614119043ef21c4616f285c4512d4`。
- `Copperbench.0.1.4.Linux.x86_64.tar.gz`：`e141762c65e2c8c309eef0be3b3c5f64f422bcd590c1158a2dda443f8ae181ae`。

Debian 安装：

```bash
sudo apt install ./copperbench_0.1.4_amd64.deb
```

然后从应用菜单启动 Copperbench，或运行 `copperbench`。便携版解压后运行：

```bash
tar -xzf Copperbench.0.1.4.Linux.x86_64.tar.gz
./Copperbench014/copperbench.sh
```

GitHub 将候选包文件名中的空格规范化为点；原候选清单仍使用 `Copperbench 0.1.4 Linux x86_64.tar.gz`。两者字节和 SHA-256 相同；使用原候选校验器时，将下载文件保存为清单中的原名。

## 设置与使用

Linux 偏好设置默认位于 `~/.config/copperbench`；设置绝对路径 `XDG_CONFIG_HOME` 时使用其下的 `copperbench` 子目录。旧 `~/.copperbench` 偏好文件只在新配置不存在时迁入，原件保留；显式指定 `COPPERBENCH_HOME` 或 `MCREATOR_HOME` 时使用独立目录。工作区里的 `.copperbench` 是项目元数据，不等同于用户偏好目录。

GNOME 自动登录会话可能需要通过系统提示正常解锁密钥环，JCEF 才能继续初始化。首次构建需要下载依赖；本次发布不声明冷缓存、默认网络或离线环境认证。Blockbench 需要另外安装。

历史 0.1.4 包含新版界面、源码编辑、关系图、资产预览和设置，原候选检查见[0.1.4 发布记录](../testing/product-shell-linux-publication-0.1.4.md)。它及 [0.1.2 安装验收](../testing/maintenance-012-linux-installed-2026-09-28.md)只适用于原来的包，不代替后续版本的验收。

Debian 包可用 `sudo apt remove copperbench` 卸载；用户设置和自行选择的工作区保留。历史变更见[0.1.4 Linux 发布说明](../releases/v0.1.4-linux.md)，当前平台发布见[当前下载](../releases/current.md)。
