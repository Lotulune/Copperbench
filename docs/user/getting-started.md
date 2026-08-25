# Copperbench 快速开始

## 安装前

Copperbench 当前仅支持 64 位 Windows 11 build 22000 及以上。公开预览包没有 Authenticode 签名，Windows SmartScreen 可能显示警告。只从项目 GitHub Releases 下载，并对照同一 Release 的 `SHA256SUMS.txt` 校验文件。

建议预留至少 8 GB 内存和足够的磁盘空间。首次创建或构建工作区需要下载 Gradle、加载器和 Minecraft 依赖。

## 第一次启动

1. 运行 EXE 安装包，或解压 Portable ZIP 后启动 `copperbench.exe`。
2. 选择是否使用中国大陆镜像。该设置只写入 `%USERPROFILE%\.copperbench`。
3. 在“新建工作区”中选择 Fabric/NeoForge 版本轨道或资源包。
4. 填写名称、ID、Java 包名和工作区目录，确认后创建。
5. 先创建一个基础元素并运行“构建”，确认本机依赖环境可用。

## 当前可编辑范围

新 UI、MCP 和 headless 第一方支持 Block、Item、Recipe、Procedure；Function、Loot Table、Advancement 属于 Stage 9 开发预览。其他从 MCreator 迁入的类型会尽量保留，但通常只能查看或转到旧版编辑器。

datagen 输出先进入隔离暂存区，查看差异并明确确认后才发布到工作区。AI 权限分为只读、工作区、完全访问；删除、外部发布和启用 Java 插件仍需要用户确认。

详细能力和限制见 [开发测试版使用说明](./README.md)。遇到启动、网络或构建问题时查阅 [故障排查](./troubleshooting.md)。
