# Copperbench 使用说明（开发测试版）

这是开发测试版说明，不是商店发行手册。产品名 `Copperbench` 是公开名称。公开分发走 GitHub，安装包未签名。

## 工作区

一个工作区同一时间只有一个活动生成器（Fabric 或 NeoForge 的某一个版本）。创建、打开、从官方 MCreator 迁入都走同一套 Java 服务。迁入会复制到新目录，并保留未知字段。

工作区文件扩展名仍是 `.mcreator`，以便兼容上游插件。用户设置在 `%USERPROFILE%\.copperbench`。

## 版本轨道

| 轨道 | 状态 |
| --- | --- |
| 最新 26.2 | Fabric / NeoForge 正式支持（编译 + runClient）。Fabric 走未混淆 Loom |
| 前一 26.1 | Fabric / NeoForge 正式支持（编译 + runClient）。钉选 Minecraft `26.1.2` |
| 维护 1.21.1 | 正式支持，有黄金构建 / runClient |
| 维护 1.20.1 | Fabric / NeoForge 正式支持（编译 + runClient）。NeoForge 钉选 `1.20.1-47.1.106` |

「新建工作区」列出已安装的生成器插件。Fabric 与 NeoForge 均有 26.2、26.1.2、1.21.1、1.20.1。产品外壳有自己的「新建工作区」视图（导航栏「新建工作区」）：选择生成器、填写模组名 / ID / 包名 / 文件夹，校验通过并确认后创建，随后在新窗口打开。旧版 Swing 对话框仍保留可回退。当前 MCP 与 headless 还不能创建工作区。第一方纵向切片（方块、物品、配方、Procedure）四轨均有编译 + runClient 证据；这不等于对话框插件的空工作区 Gradle 黄金编译。尚未宣称每一个模组元素类型都能生成可编译代码。收口需求见 [PRD-NEXT.md](../../PRD-NEXT.md)，状态见 [剩余完善清单](../remaining-work.md)。你正在用的发行预览包可能落后于源码（国内源、Gradle 池、八套插件）。

## 模组元素

第一方纵向切片：方块、物品、配方、Procedure。四轨 Fabric/NeoForge 均如此。迁入的上游类型会保留并只读列出，不能在新 UI / MCP 里创建或更新。

## 本地历史

用「版本 / 恢复点」而不是 Git 术语。已有远端仓库不会被自动改写。恢复会回到一致快照。

## MCP 权限

本机 MCP 三档：只读、工作区、完全访问。删除工作区、导出凭据、对外发布、启用 Java 插件必须你亲自确认。AI 不能替你打开 Java 插件。

## Blockbench 与资源包

模型和纹理可以往返 Blockbench。资源包可以导出 ZIP，并准备到 `run/resourcepacks`。产品不会自动启动 Minecraft。Fabric 1.21.1 测试客户端已验证 ResourceManager 会加载该包。

## 加载器迁移

只做同版本 Fabric↔NeoForge 的安全拷贝。源工作区只读。预览报告里的阻断项没清完，不要当成迁移成功。

## 插件

- A：资源/生成器模板
- B：不碰 Swing 的 Java 逻辑
- C：Swing 界面，走旧版窗口
- X：拒绝或不兼容

Java 插件默认关闭，启用即完全本机信任。兼容中心列出已安装插件，以及上游工具是走新 UI、旧版窗口，还是明确不支持。

## 国内网络

首次启动会询问你是否在中国大陆。选“是”后，Copperbench 会把 Gradle 发行版改到华为云镜像，把 Maven Central / Plugin Portal 改到阿里云镜像，并把 Minecraft 库（`libraries.minecraft.net`）改到 BMCLAPI。这些文件写在 `%USERPROFILE%\.copperbench\gradle`，不是系统全局 `\.gradle`。该目录是所有工作区共用的 Gradle 用户主目录：发行包和 Maven 缓存只下一次。若官方源和国内镜像 URL 不同，产品会把已解压的同一份 Gradle 复制到对应哈希目录，避免重下。安装包若带了 `gradle-dists`，启动时会预填到这个目录。

Fabric Maven 与 NeoForge 专用仓库仍走官方地址。之后可在偏好设置的 Gradle 页开关「使用中国大陆软件源」。若创建工作区时卡在 `services.gradle.org`，失败对话框也可以直接配置国内源并重试。

## 安装与卸载

仅支持 64 位 Windows 11（build 22000 及以上）。Windows 10 会在安装器和启动时被拒绝。

安装后默认打开新产品外壳（无边框 JCEF 工作台）。若要旧版 Swing 工作区，启动时加 `-Dcopperbench.productShell=false`。

卸载默认保留 `.copperbench` 设置。你自己选的工作区目录不会被卸载删除。GitHub 安装包没有 Authenticode 签名；Windows SmartScreen 可能提示“已保护你的电脑”，这是预期行为。
