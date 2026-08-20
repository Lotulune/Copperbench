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

新项目优先用 Fabric 1.21.1。

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

## 安装与卸载

仅支持 64 位 Windows 11（build 22000 及以上）。Windows 10 会在安装器和启动时被拒绝。

安装后默认打开新产品外壳（无边框 JCEF 工作台）。若要旧版 Swing 工作区，启动时加 `-Dcopperbench.productShell=false`。

卸载默认保留 `.copperbench` 设置。你自己选的工作区目录不会被卸载删除。GitHub 安装包没有 Authenticode 签名；Windows SmartScreen 可能提示“已保护你的电脑”，这是预期行为。
