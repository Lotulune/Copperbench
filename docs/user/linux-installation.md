# Copperbench Linux 安装说明（0.1.13）

[English](linux-installation.en.md) · [下一步：第一件作品](first-creation.md)

从 [Linux 0.1.13 稳定版](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.13-linux-stable)下载图形安装向导、Debian 包或便携压缩包。目标环境为 Ubuntu 24.04 LTS x86_64、GNOME Wayland／Xorg；其他发行版和架构尚未验证。包内提供 JBR 25／JCEF 和独立 Java 21，不要求预先安装系统 Java、Gradle 或 Git。实际发布、来源与验收范围见[状态页](../status.md)。

## 校验与安装

核对同一 Release 附带的 `linux-candidate-sha256.txt`、`LINUX-RELEASE-AUTHORIZATION.json` 及 GitHub 来源证明。0.1.13 的公开文件哈希为：

- `copperbench_0.1.13_amd64.deb`：`ae4839c26d372e76e56bf55396993021cf0670b94ffdff639c9d63a4533a9b26`。
- `Copperbench.0.1.13.Linux.x86_64.tar.gz`：`a142f745943778dd541b2b522749942da138ff03090e8d9949be9f381cb171c6`。
- `Copperbench.0.1.13.Linux.x86_64.Setup.tar.gz`：`d7103fa1267c6e791a7deba8397fc764d45e997dcd41f8db01dd2fa3ea5f464d`。

图形安装：解压 Setup 包，运行 `Install Copperbench`，按[双语向导说明](graphical-installation.md)完成安装。

Debian 安装：

```sh
sudo apt install ./copperbench_0.1.13_amd64.deb
```

然后从应用菜单启动 Copperbench，或运行 `copperbench`。便携版解压后运行：

```sh
tar -xzf Copperbench.0.1.13.Linux.x86_64.tar.gz
./Copperbench0113/copperbench.sh
```

GitHub 将候选包文件名中的空格规范化为点；原候选清单仍使用 `Copperbench 0.1.13 Linux x86_64.tar.gz`。两者字节和 SHA-256 相同；使用原候选校验器时，将下载文件保存为清单中的原名。

## 设置与使用

Linux 偏好设置默认位于 `~/.config/copperbench`；设置绝对路径 `XDG_CONFIG_HOME` 时使用其下的 `copperbench` 子目录。旧 `~/.copperbench` 偏好文件只在新配置不存在时迁入，原件保留；显式指定 `COPPERBENCH_HOME` 或 `MCREATOR_HOME` 时使用独立目录。工作区里的 `.copperbench` 是项目元数据，不等同于用户偏好目录。

GNOME 自动登录会话可能需要通过系统提示正常解锁密钥环，JCEF 才能继续初始化。首次构建需要下载依赖，Blockbench 需要另外安装。公开版本没有新的冷缓存、默认网络或离线环境认证。

0.1.13 包含共享桌面、原生窗口及应用内更新改进，并保留双语离线安装器、首作与导出功能。13 项候选门禁、独立消费者核验、来源证明和草稿下载字节比对通过，18 项公开资产的大小与 SHA-256 已核对；未重复完整 GNOME 安装、升级、卸载或玩家操作验收，详见[发布记录](../testing/linux-publication-0.1.13.md)。[0.1.2 安装验收报告](../testing/maintenance-012-linux-installed-2026-09-28.md)仅适用于当时的包；[0.1.4 记录](../testing/product-shell-linux-publication-0.1.4.md)同样保留原范围。

Debian 包可用 `sudo apt remove copperbench` 卸载；用户设置和自行选择的工作区保留。当前变更见[0.1.13 Linux 说明](../releases/v0.1.13-linux.md)，其他平台见[当前下载](../releases/current.md)。继续按[第一件作品](first-creation.md)创作。
