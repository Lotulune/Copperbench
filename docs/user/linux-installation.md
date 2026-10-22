# Copperbench Linux 安装说明

[English](linux-installation.en.md) · [下一步：第一件作品](first-creation.md)

从[当前下载](../releases/current.md)的 Linux 一行进入对应 Release，选择图形安装向导、Debian 包或便携压缩包，并查看该包的来源与验收范围。Linux 版本与 Windows 分别发布。目标环境为 Ubuntu 24.04 LTS x86_64、GNOME Wayland／Xorg；其他发行版和架构尚未验证。包内提供 JBR 25／JCEF 和独立 Java 21，不要求预先安装系统 Java、Gradle 或 Git。

## 校验与安装

核对同一 Release 附带的 `linux-candidate-sha256.txt`、`LINUX-RELEASE-AUTHORIZATION.json` 及 GitHub 来源证明。以下文件名和哈希对应 0.1.16；其他版本应使用自己的校验清单。

[Linux 0.1.16](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.16-linux-stable) 的公开文件哈希如下，仅适用于该版本：

- `copperbench_0.1.16_amd64.deb`：`96cd39fba71273981e6b8accea18ba73b67f5c2213077b3a0aba1bc9fcd08ebb`。
- `Copperbench.0.1.16.Linux.x86_64.tar.gz`：`b27084b1a7875ba50a6c4e11306892a0e1f932624ba4d6b8b435b99dc5a1bf2b`。
- `Copperbench.0.1.16.Linux.x86_64.Setup.tar.gz`：`9d1d74dc066a11d3724912209bd7eb5c47a06a036405ca04dbca4c9713e325c4`。

图形安装：解压 Setup 包，运行 `Install Copperbench`，按[双语向导说明](graphical-installation.md)完成安装。

下面以 0.1.16 为例；安装其他版本时使用对应 Release 的文件名与解压目录。在下载文件所在目录执行：

```sh
sudo apt install ./copperbench_0.1.16_amd64.deb
```

然后从应用菜单启动 Copperbench，或运行 `copperbench`。0.1.16 便携版解压后运行：

```sh
tar -xzf Copperbench.0.1.16.Linux.x86_64.tar.gz
./Copperbench0116/copperbench.sh
```

GitHub 将候选包文件名中的空格规范化为点；原候选清单仍使用 `Copperbench 0.1.16 Linux x86_64.tar.gz`。两者字节和 SHA-256 相同；使用原候选校验器时，将下载文件保存为清单中的原名。

## 设置与使用

Linux 偏好设置默认位于 `~/.config/copperbench`；设置绝对路径 `XDG_CONFIG_HOME` 时使用其下的 `copperbench` 子目录。旧 `~/.copperbench` 偏好文件只在新配置不存在时迁入，原件保留；显式指定 `COPPERBENCH_HOME` 或 `MCREATOR_HOME` 时使用独立目录。工作区里的 `.copperbench` 是项目元数据，不等同于用户偏好目录。

GNOME 自动登录会话可能需要通过系统提示正常解锁密钥环，JCEF 才能继续初始化。首次构建可能需要下载依赖，Blockbench 需要另外安装。网络、缓存、桌面与游戏验收范围按所下载包的[发布记录](../releases/current.md)确认。

Debian 包可用 `sudo apt remove copperbench` 卸载；用户设置和自行选择的工作区保留。继续按[第一件作品](first-creation.md)创作；若该安装版本没有首启引导，可按教程手动新建 Fabric 1.21.1 工作区。

## 0.1.16 发布与验证

0.1.16 完善简洁引导、模型与附件、会话权限、历史恢复和隐藏侧栏性能，并补齐随包 UI 依赖许可正文。13 项候选门禁、独立消费者核验、来源证明和草稿下载字节比对通过，18 项公开资产及 8 个更新组件的大小与 SHA-256 已核对；未重复完整 GNOME 安装、升级、卸载或玩家操作验收，详见[发布记录](../testing/linux-publication-0.1.16.md)。[0.1.2 安装验收报告](../testing/maintenance-012-linux-installed-2026-09-28.md)和[0.1.4 记录](../testing/product-shell-linux-publication-0.1.4.md)保留原范围。

该版本变更见 [0.1.16 Linux 说明](../releases/v0.1.16-linux.md)；各平台当前版本见[当前下载](../releases/current.md)。

## 历史 0.1.7 包的核验记录

以下哈希仅适用于 0.1.7，不用于校验当前下载页中其他版本的包：

- `copperbench_0.1.7_amd64.deb`：`7c632878bf8537638466435e79de8254230dbb941b81e0214c59e634f72d44a1`。
- `Copperbench.0.1.7.Linux.x86_64.tar.gz`：`fdaa48f6a33f5387380fe8070c68a5a8639d1335aba3113ac26f18e2432a0598`。
- `Copperbench.0.1.7.Linux.x86_64.Setup.tar.gz`：`685cd8c91c2508c1ce95a1d75380a3cca2f7ba0086ade05a33a6c6d51f11180b`。

GitHub 将候选包文件名中的空格规范化为点；0.1.7 原候选清单使用 `Copperbench 0.1.7 Linux x86_64.tar.gz`，对应公开文件字节和 SHA-256 不变。

0.1.7 包含双语离线安装器、输入诊断、首作教程及编辑器和生成器修复。候选 CI 通过包结构、隔离启动、双语安装器、JCEF X11 与 Fabric／NeoForge 1.21.1 渲染预检；经明确授权发布，未重做完整 GNOME 安装、偏好迁移或游戏验收，详见[发布记录](../testing/maintenance-release-0.1.7.md)。[0.1.2 安装验收报告](../testing/maintenance-012-linux-installed-2026-09-28.md)仅适用于当时的包；[0.1.4 记录](../testing/product-shell-linux-publication-0.1.4.md)同样保留原范围。

该版本变更见[历史 0.1.7 Linux 说明](../releases/v0.1.7-linux.md)。
