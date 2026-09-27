# Copperbench Linux 安装说明（0.1.2）

从 [Linux 0.1.2 稳定版](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.2-linux-stable)下载 Debian 包或便携压缩包。支持范围为 Ubuntu 24.04 LTS x86_64、GNOME Wayland / Xorg；其他发行版和架构尚未认证。包内提供 JBR 25 / JCEF 和独立 Java 21，不要求预先安装系统 Java、Gradle 或 Git。

## 校验与安装

核对 Release 附带的 `linux-candidate-sha256.txt`、`LINUX-RELEASE-AUTHORIZATION.json` 及 GitHub 来源证明。0.1.2 的已验收文件哈希为：

- `copperbench_0.1.2_amd64.deb`：`fd43bd23926a2f37042f14d6a65a7d606b59b08035a92d490ff5ded156931aef`。
- `Copperbench.0.1.2.Linux.x86_64.tar.gz`：`14576bfafd24cf773b689fecb448aa7dd2f78cf05fd80e79c25eefe874e408fa`。

Debian 安装：

```bash
sudo apt install ./copperbench_0.1.2_amd64.deb
```

然后从应用菜单启动 Copperbench，或运行 `copperbench`。便携版解压后运行：

```bash
tar -xzf Copperbench.0.1.2.Linux.x86_64.tar.gz
./Copperbench012/copperbench.sh
```

GitHub 将候选包文件名中的空格规范化为点；原候选清单仍使用 `Copperbench 0.1.2 Linux x86_64.tar.gz`。两者字节和 SHA-256 相同；使用原候选校验器时，将下载文件保存为清单中的原名。

## 设置与使用

Linux 偏好设置默认位于 `~/.config/copperbench`；设置绝对路径 `XDG_CONFIG_HOME` 时使用其下的 `copperbench` 子目录。旧 `~/.copperbench` 偏好文件只在新配置不存在时迁入，原件保留；显式指定 `COPPERBENCH_HOME` 或 `MCREATOR_HOME` 时使用独立目录。工作区里的 `.copperbench` 是项目元数据，不等同于用户偏好目录。

GNOME 自动登录会话可能需要通过系统提示正常解锁密钥环，JCEF 才能继续初始化。首次构建需要下载依赖；本轮验收使用暖缓存，不代表冷缓存、默认网络或离线环境认证。Blockbench 需要另外安装。

0.1.2 已修复模型回导后刷新收起任务面板的问题，以及复制失败仍显示成功的问题。十项安装验收和两类偏好迁移均通过，具体环境、失败重试及未验证范围见[安装验收报告](../testing/maintenance-012-linux-installed-2026-09-28.md)。Linux 安装后的客户端复演覆盖 Fabric / NeoForge 1.21.1，不自动扩大到其他轨道或玩法。

Debian 包可用 `sudo apt remove copperbench` 卸载；用户设置和自行选择的工作区保留。旧版发布说明保留在[0.1.1 历史说明](../releases/linux-release-notes.md)，本轮交付状态见[0.1.2 发布记录](../testing/maintenance-release-0.1.2.md)。
