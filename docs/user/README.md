# Copperbench 使用说明（稳定版）

这是稳定版说明，不是商店发行手册。产品名 `Copperbench` 是公开名称。公开分发走 GitHub，安装包未签名。

## 工作区

一个工作区同一时间只有一个活动生成器（Fabric 或 NeoForge 的某一个版本）。创建、打开、从官方 MCreator 迁入都走同一套 Java 服务。迁入会复制到新目录，并保留未知字段。

工作区文件扩展名仍是 `.mcreator`，以便兼容上游插件。用户设置位于用户目录下的 `.copperbench`：Windows 为 `%USERPROFILE%\.copperbench`，Linux 为 `~/.copperbench`。

## 版本轨道

| 轨道 | 状态 |
| --- | --- |
| 最新 26.2 | Fabric / NeoForge 正式支持（编译 + runClient）。Fabric 走未混淆 Loom |
| 前一 26.1 | Fabric / NeoForge 正式支持（编译 + runClient）。钉选 Minecraft `26.1.2` |
| 维护 1.21.1 | 正式支持，有黄金构建 / runClient |
| 维护 1.20.1 | Fabric / NeoForge 正式支持（编译 + runClient）。NeoForge 钉选 `1.20.1-47.1.106` |

「新建工作区」列出已安装的生成器插件。Fabric 与 NeoForge 均有 26.2、26.1.2、1.21.1、1.20.1，并提供独立 `resourcepack-1.21.1` 资源包生成器。选择生成器、填写模组名 / ID / 包名 / 文件夹，校验通过并确认后创建，随后在新窗口打开；选择资源包时不需要 Java 包名。MCP 与 headless 也可列出生成器并提交创建命令，但必须显式提供用户批准事实。旧版 Swing 对话框仍保留可回退。

八条生成器轨道有黄金生成与编译证据；这些证据不代表所有元素、字段组合及玩法均已验证。0.1.1 安装候选的客户端复演覆盖 Fabric / NeoForge 1.21.1，具体环境和场景见[安装验收](../testing/stage17-stable-installed-acceptance-2026-09-27.md)。物理屏幕阅读器认证、最终干净 Windows RC 复演、外部测试者认证，以及 Linux 冷缓存／默认网络认证不在当前发布宣称内。外部试用延期，不阻塞本轮维护；内部回归也不替代陌生用户可用性验证。

资源包工作区可导出 ZIP；`prepare_resource_pack_client` 只准备测试客户端文件，不自动启动 Minecraft。当前状态见[剩余完善清单](../remaining-work.md)，历史 Stage 9 需求保留在 [PRD-STAGE-9.md](../../PRD-STAGE-9.md)。下载包与后续源码维护的区别见[维护记录](../testing/maintenance-2026-09-27.md)。

## 模组元素

应用内脚本编辑、持久控制台与当前工程 Python 对象接口见 [Python 工作台](./python-workbench.md)。

新 UI / MCP / headless 现在共用 Stage 11/12 的 37 种第一方 Java Mod Element schema：除方块、物品、配方、Procedure、Function、Loot Table、Advancement 外，还包括装备/战斗、实体、世界生成、GUI/Overlay、村民、粒子、药水、命令、规则等类型。`livingentity`、`biome`、`dimension`、`gui` 以及 Stage 12B/12C 的相关复杂类型会按用途分组显示字段，并对数字范围、枚举、资源引用、元素引用和 Procedure 引用提供对应控件；结构化列表（例如 Villager Trade 的交易条目）可直接逐行增删和编辑，不需要手写原始 JSON。

保存复杂元素时，未在当前编辑器中展示的字段和未知插件字段不会被静默删除；保存后重新打开工作区仍会保留。字段校验失败时，诊断会关联到具体元素和字段，支持直接定位到对应编辑控件。Procedure 继续使用内置 Blockly 工作台；未知上游 Blockly 节点只读显示并在往返保存时保留。Bedrock Add-on 类型仍不属于当前第一方 Java Mod Element 范围。

变量、标签和语言位于「创作数据」视图，支持创建、编辑、引用计数以及重命名影响预览。语言工具支持 CSV/JSON 导入导出，以及 merge/keep/replace 冲突处理和缺失/重复键统计。

顶部运行入口提供客户端、专用服务端、datagen 和已有 GameTest。datagen 完成后只生成隔离暂存结果；必须先查看文件差异并明确确认，才会发布到工作区。历史 dedicated-server readiness 的八轨通过记录只覆盖对应工程与环境。GameTest 验收和已验证产物导出只证明报告所列测试及绑定的 JAR，不隐含客户端玩法通过；各轨能力以当前生成器与任务诊断为准。

## 本地历史

用「版本 / 恢复点」而不是 Git 术语。已有远端仓库不会被自动改写。恢复会回到一致快照。

## MCP 权限

本机 MCP 三档：只读、工作区、完全访问。删除工作区、导出凭据、对外发布、启用 Java 插件必须你亲自确认。AI 不能替你打开 Java 插件。

## Blockbench 与资源包

可选安装、社区 MCP 连接测试和当前自动建模边界见 [连接 Blockbench](./blockbench-setup.md)。

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

0.1.1 稳定版支持 Windows 11 x64（build 22000 及以上）和 Ubuntu 24.04 LTS x86_64（GNOME Wayland / Xorg）。Windows 10 会在安装器和启动时被拒绝；其他 Linux 发行版和架构尚未验证。安装入口见[快速开始](./getting-started.md)和 [Linux 安装说明](../releases/linux-release-notes.md)。

安装后默认打开新产品外壳（无边框 JCEF 工作台）。若要旧版 Swing 工作区，启动时加 `-Dcopperbench.productShell=false`。

卸载默认保留 `.copperbench` 设置。你自己选的工作区目录不会被卸载删除。GitHub 安装包没有 Authenticode 签名；Windows SmartScreen 可能提示“已保护你的电脑”，这是预期行为。
