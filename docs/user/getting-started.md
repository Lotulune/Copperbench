# Copperbench 快速开始

[English](getting-started.en.md) · [首作](first-creation.md) · [构建失败与导出](build-and-export.md)


默认简洁模式可直接跟随[对话首作路线](simple-creation.md)；界面中的“快速教程”可重看各个入口。
## 安装前

从[当前下载](../releases/current.md)选择对应平台的稳定包，并查看该版本的验证范围。Windows 与 Linux 的版本分别列出。目标平台为 Windows 11 x64（build 22000 及以上）与 Ubuntu 24.04 LTS x86_64；其他 Linux 发行版与架构未验证。

0.1.15 新增简洁创作与专业工作台切换，以及五种本机 CLI 创作助手。具体平台变更见 [Windows 版本说明](../releases/v0.1.15.md)、[Linux 版本说明](../releases/v0.1.15-linux.md)和[状态页](../status.md)。

只从项目 GitHub Releases 下载，并使用同一 Release 附带的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 安装包没有 Authenticode 签名，SmartScreen 可能显示警告。

应用运行环境随包提供。建议预留至少 8 GB 内存和足够的磁盘空间；首次创建或构建工作区需要下载 Gradle、加载器和 Minecraft 依赖。

## 第一次启动

1. Windows：运行 EXE 安装包，或解压 Portable ZIP 后启动 `copperbench.exe`。Ubuntu：使用 [图形安装向导](./graphical-installation.md)，或按 [Linux 安装说明](./linux-installation.md)安装 `.deb`、使用便携版 `.tar.gz`。
2. 选择是否使用中国大陆镜像。Windows 偏好设置默认位于 `%USERPROFILE%\.copperbench`；Linux 默认位于 `~/.config/copperbench`，并遵循 `XDG_CONFIG_HOME`。显式用户目录覆盖和旧设置迁移见 [Linux 安装说明](./linux-installation.md)。
3. 新用户首次打开默认进入「简洁创作」，点击「新建作品」选择 Fabric 1.21.1。按手动编辑教程操作时，先在顶部切换「专业工作台」，再使用「新建工作区」或首作引导。其他工程仍可选择所需 Fabric/NeoForge 轨道或资源包。
4. 填写名称并选择新的空工作区目录。简洁模式会生成默认 ID 和 Java 包名；ID 可直接修改，Java 包名可在「更多设置」中修改。核对后确认创建。
5. 按[第一件作品：步道方块](./first-creation.md)保存、构建，在游戏中取得／放置，再正常退出客户端并启动新进程重开同一个世界。最后按[构建失败与导出](build-and-export.md)交付 JAR。

## 当前可编辑范围

新 UI、MCP 和 headless 共用 37 种第一方 Java 模组元素的编辑与校验路径，包括 Block、Item、Recipe、Procedure、Function、Loot Table、Advancement、实体、GUI、世界生成和原生代码元素。多文件 Java 可用于复杂玩法；Bedrock Add-on 不在第一方编辑范围内。具体字段和生成能力仍受加载器、Minecraft 版本及插件限制，不能把类型覆盖理解为所有组合均已通过游戏内验证。

datagen 输出先进入隔离暂存区，查看差异并明确确认后才发布到工作区。AI 权限分为只读、工作区、完全访问；删除、外部发布和启用 Java 插件仍需要用户确认。

详细能力和验证边界见[稳定版使用说明](./README.md)。遇到启动、网络或构建问题时查阅[故障排查](./troubleshooting.md)。首次构建需要联网，暖缓存验证不代表冷缓存或离线构建认证。

具体版本的改动、已知问题和验证范围见[当前下载中的平台发布说明](../releases/current.md)。安装包的功能随该版本固定；阅读源码分支中的新说明时，请核对所安装的版本和实际入口。

后续更新可从「帮助与关于」选择「检查更新」，按提示操作。安装包和当前验证范围见[当前下载](../releases/current.md)。
