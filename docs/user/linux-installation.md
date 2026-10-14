# Copperbench Linux 安装说明（0.1.5）

从 [Linux 0.1.5 稳定版](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.5-linux-stable)下载图形安装向导、Debian 包或便携压缩包。目标环境为 Ubuntu 24.04 LTS x86_64、GNOME Wayland / Xorg；其他发行版和架构尚未认证。包内提供 JBR 25 / JCEF 和独立 Java 21，不要求预先安装系统 Java、Gradle 或 Git。

## 校验与安装

核对 Release 附带的 `linux-candidate-sha256.txt`、`LINUX-RELEASE-AUTHORIZATION.json` 及 GitHub 来源证明。0.1.5 的公开文件哈希为：

- `copperbench_0.1.5_amd64.deb`：`1af8ffbd49361ef2815053c1d89c4b6b82cbd318a1aa13b9c6934a52b8bda2c4`。
- `Copperbench.0.1.5.Linux.x86_64.tar.gz`：`60eb139a5c48e101228378edc0e1d23ea882bc7d33aaa06572a4995839dc07b5`。
- `Copperbench.0.1.5.Linux.x86_64.Setup.tar.gz`：`3f9b7573511d46af9f2828aee58fa8d8154d1444180340d4f67c6c7ce591e0d7`。

图形安装：解压 Setup 包，运行 `Install Copperbench`，按[双语向导说明](./graphical-installation.md)完成安装。

Debian 安装：

```bash
sudo apt install ./copperbench_0.1.5_amd64.deb
```

然后从应用菜单启动 Copperbench，或运行 `copperbench`。便携版解压后运行：

```bash
tar -xzf Copperbench.0.1.5.Linux.x86_64.tar.gz
./Copperbench015/copperbench.sh
```

GitHub 将候选包文件名中的空格规范化为点；原候选清单仍使用 `Copperbench 0.1.5 Linux x86_64.tar.gz`。两者字节和 SHA-256 相同；使用原候选校验器时，将下载文件保存为清单中的原名。

## 设置与使用

Linux 偏好设置默认位于 `~/.config/copperbench`；设置绝对路径 `XDG_CONFIG_HOME` 时使用其下的 `copperbench` 子目录。旧 `~/.copperbench` 偏好文件只在新配置不存在时迁入，原件保留；显式指定 `COPPERBENCH_HOME` 或 `MCREATOR_HOME` 时使用独立目录。工作区里的 `.copperbench` 是项目元数据，不等同于用户偏好目录。

GNOME 自动登录会话可能需要通过系统提示正常解锁密钥环，JCEF 才能继续初始化。首次构建需要下载依赖；本次发布不声明冷缓存、默认网络或离线环境认证。Blockbench 需要另外安装。

0.1.5 包含双语离线安装器、输入诊断、首作教程及编辑器和生成器修复。候选 CI 通过包结构、隔离启动、双语安装器、JCEF X11 与 Fabric / NeoForge 1.21.1 渲染预检；经明确授权发布，未重做完整 GNOME 安装、偏好迁移或游戏验收，详见[发布记录](../testing/maintenance-release-0.1.5.md)。[0.1.2 安装验收报告](../testing/maintenance-012-linux-installed-2026-09-28.md)仅适用于当时的包。

Debian 包可用 `sudo apt remove copperbench` 卸载；用户设置和自行选择的工作区保留。本次变更见[0.1.5 Linux 发布说明](../releases/v0.1.5-linux.md)，其他平台见[当前下载](../releases/current.md)。
