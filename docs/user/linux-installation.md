# Copperbench Linux 安装说明（0.1.4）

从 [Linux 0.1.4 稳定版](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.4-linux-stable)下载 Debian 包或便携压缩包。目标环境为 Ubuntu 24.04 LTS x86_64、GNOME Wayland / Xorg；其他发行版和架构尚未认证。包内提供 JBR 25 / JCEF 和独立 Java 21，不要求预先安装系统 Java、Gradle 或 Git。

## 校验与安装

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

0.1.4 包含新版界面、源码编辑、关系图、资产预览和设置。候选 CI 通过包结构、隔离启动、JCEF X11 与 Fabric / NeoForge 1.21.1 渲染预检；经明确授权发布，未重做完整 GNOME 安装、偏好迁移或游戏验收，详见[发布记录](../testing/product-shell-linux-publication-0.1.4.md)。[0.1.2 安装验收报告](../testing/maintenance-012-linux-installed-2026-09-28.md)仅适用于当时的包。

Debian 包可用 `sudo apt remove copperbench` 卸载；用户设置和自行选择的工作区保留。本次变更见[0.1.4 Linux 发布说明](../releases/v0.1.4-linux.md)，其他平台见[当前下载](../releases/current.md)。
