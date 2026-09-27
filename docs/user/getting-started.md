# Copperbench 快速开始

## 安装前

Copperbench 0.1.1 已提供 Windows 与 Linux 稳定版。支持 Windows 11 x64（build 22000 及以上）和 Ubuntu 24.04 LTS x86_64（GNOME Wayland / Xorg）；其他 Linux 发行版和架构尚未验证。

只从项目 GitHub Releases 下载，并使用同一 Release 附带的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 安装包没有 Authenticode 签名，SmartScreen 可能显示警告。

建议预留至少 8 GB 内存和足够的磁盘空间。首次创建或构建工作区需要下载 Gradle、加载器和 Minecraft 依赖。

## 第一次启动

1. Windows：运行 EXE 安装包，或解压 Portable ZIP 后启动 `copperbench.exe`。Ubuntu：按 [Linux 安装说明](../releases/linux-release-notes.md)安装 `.deb` 或使用便携版 `.tar.gz`。
2. 选择是否使用中国大陆镜像。用户设置保存在用户目录下的 `.copperbench`（Windows：`%USERPROFILE%\.copperbench`；Linux：`~/.copperbench`）。
3. 在“新建工作区”中选择 Fabric/NeoForge 版本轨道或资源包。
4. 填写名称、ID、Java 包名和工作区目录，确认后创建。
5. 先创建一个基础元素并运行“构建”，确认本机依赖环境可用。

## 当前可编辑范围

新 UI、MCP 和 headless 共用 37 种第一方 Java 模组元素的编辑与校验路径，包括 Block、Item、Recipe、Procedure、Function、Loot Table、Advancement、实体、GUI、世界生成和原生代码元素。多文件 Java 可用于复杂玩法；Bedrock Add-on 不在第一方编辑范围内。具体字段和生成能力仍受加载器、Minecraft 版本及插件限制，不能把类型覆盖理解为所有组合均已通过游戏内验证。

datagen 输出先进入隔离暂存区，查看差异并明确确认后才发布到工作区。AI 权限分为只读、工作区、完全访问；删除、外部发布和启用 Java 插件仍需要用户确认。

详细能力和验证边界见[稳定版使用说明](./README.md)。遇到启动、网络或构建问题时查阅[故障排查](./troubleshooting.md)。首次构建需要联网，暖缓存验证不代表冷缓存或离线构建认证。

已发布的 0.1.1 在模型回导后可能收起任务面板，可重新展开继续绑定。当前源码维护结果见[维护记录](../testing/maintenance-2026-09-27.md)，不能据此认为旧安装包已更新。
