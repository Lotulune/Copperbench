<div align="center">
  <img src="assets/branding/copperbench-icon-source.png" alt="Copperbench" width="96">
  <h1>Copperbench</h1>
  <p><strong>让可视化编辑、手写 Java 和本地 Agent 在同一个 Minecraft 模组工程中协作。</strong></p>

  <p><a href="README.md">English</a> · <strong>简体中文</strong></p>
  <p>
    <a href="https://github.com/Lotulune/Copperbench/actions/workflows/test.yml"><img src="https://img.shields.io/github/actions/workflow/status/Lotulune/Copperbench/test.yml?branch=main&amp;style=flat-square&amp;label=CI" alt="main 分支构建与测试状态"></a>
    <a href="docs/releases/current.md"><img src="https://img.shields.io/badge/App-Windows%20%7C%20Linux-7289da?style=flat-square" alt="应用平台：Windows 和 Linux"></a>
    <a href="docs/user/README.md"><img src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge-c77d48?style=flat-square" alt="加载器：Fabric 和 NeoForge"></a>
    <a href="#development"><img src="https://img.shields.io/badge/JDK-25-437a54?style=flat-square" alt="开发环境 JDK：25"></a>
    <a href="LICENSE.txt"><img src="https://img.shields.io/badge/License-GPL--3.0--only-2ea44f?style=flat-square" alt="许可证：GPL-3.0-only"></a>
  </p>
  <p><a href="docs/releases/current.md">下载</a> · <a href="docs/README.md">文档</a> · <a href="#development">源码运行</a></p>
</div>

<p align="center">
  <img src="assets/promo/v0.1.17/copperbench-intro.gif" alt="Copperbench 简要宣传片：简洁创作与专业工作台" width="960">
</p>

普通玩家可从[简洁创作首作路线](docs/user/simple-creation.md)开始；也可使用[专业模式手动路线](docs/user/first-creation.md)。


Copperbench 是基于 MCreator 的桌面工作台，支持 Fabric 和 NeoForge。先用可视化编辑器创建方块，再用手写 Java 扩展行为，让外部 Agent 通过本机会话检查同一个工作区、修复构建并交付可审阅的产物。revision、源码 hash 和 generated/manual 归属保护各步骤间的修改。

**当前稳定版：Windows 0.1.18、Linux 0.1.18。** 新增模型与元素静态预览、专业工作台 Agent，并修复 Blockbench 打开响应和保存同步。安装包和验证记录见[当前下载](docs/releases/current.md)。

源码身份、安装包哈希和验证范围分别记录在 [Windows 0.1.18 发布记录](docs/testing/maintenance-release-0.1.18.md)和 [Linux 0.1.18 发布记录](docs/testing/linux-publication-0.1.18.md)中。

<a id="getting-started"></a>

## 下载与开始使用

| 平台 | 当前版本 | 安装包 | 指南 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.18](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.18) | EXE · 便携 ZIP · MSIX · 应用内更新组件 | [快速开始](docs/user/getting-started.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.18](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.18-linux-stable) | 图形安装 Setup.tar.gz · Debian .deb · 便携 .tar.gz · 应用内更新组件 | [Linux 安装](docs/user/linux-installation.md) |

版本说明：[Windows 0.1.18](docs/releases/v0.1.18.md) · [Linux 0.1.18](docs/releases/v0.1.18-linux.md)。后续更新可在「帮助与关于」中选择「检查更新」。

1. 下载对应平台的安装包，用 Release 附带的校验文件核验。
2. 在「简洁创作」中点击「新建作品」，或切换「专业工作台」使用手动编辑教程；选择加载器、Minecraft 版本和新的工作区目录。
3. 按[第一件作品](docs/user/first-creation.md)创建方块，再看[构建失败与导出](docs/user/build-and-export.md)。持久化确认须正常退出 Minecraft，再启动新客户端进程，打开同一个世界。

工作台支持简体中文和英文。内置 Fabric / NeoForge 生成器覆盖 Minecraft **26.2、26.1.2、1.21.1、1.20.1**；一个工作区同时使用一个活动生成器。

另有需显式启用的 **26.3 预览**轨道，操作与字段范围以能力查询为准。见[当前状态](docs/status.md)，不扩大已发布生成器的支持声明。

Windows 安装包未做 Authenticode 签名，首次构建需要联网。Blockbench 和创作助手 CLI 需单独安装与配置；Python 工作台需要 Python 3.11 或更新版本。最终包的来源、资产核验及未重复的安装／玩法范围见[当前下载](docs/releases/current.md)。

<a id="showcase"></a>

## 工作台

| 简洁创作 | 专业工作台 |
| :---: | :---: |
| ![亮色简洁创作：作品内容、对话与元素设置](assets/screenshots/2026-10-24/zh-CN/simple-creation-light.png) | ![亮色专业工作台：项目总览与导航](assets/screenshots/2026-10-24/zh-CN/workbench-light.png) |
| 作品内容、助手对话和常用设置集中在一个界面。 | 从总览进入编辑器，查看项目状态并执行构建与运行。 |
| **模组元素编辑** | **Java 源码** |
| ![亮色模组元素编辑器：元素列表与方块属性](assets/screenshots/2026-10-24/zh-CN/elements-light.png) | ![亮色 Java 源码编辑器](assets/screenshots/2026-10-24/zh-CN/source-light.png) |
| 浏览元素，在检查器中编辑字段。 | 浏览、搜索和编辑手写文件，生成文件带有只读标识。 |
| **Python 工作台** | **关系图** |
| ![亮色 Python 工作台：内置脚本示例、控制台与当前上下文](assets/screenshots/2026-10-24/zh-CN/python-light.png) | ![亮色工作区关系图](assets/screenshots/2026-10-24/zh-CN/relations-light.png) |
| 脚本编辑器、控制台和当前工程上下文。 | 按分类浏览、搜索并定位工作区中的元素与资产。 |

2026-10-24 从当前检出的亮色浏览器预览截取，使用内置 Copper Trails 示例数据。[截图来源与复拍方法](assets/screenshots/README.md)

## 能做什么

| 功能 | 用法 |
| --- | --- |
| 简洁创作与 Agent | 在简洁创作和专业工作台之间切换、共享助手会话；当前源码接入 Codex CLI、Claude Code、Grok Build、Google Antigravity CLI 和 DeepSeek Harness。 |
| 可视化编辑 | 创建方块、物品、实体等模组元素，用 Blockly 编排逻辑，并从字段诊断定位到编辑控件。 |
| 源码与 Python | 编辑手写 Java，保留草稿并检查修改冲突；通过 [Python 工作台](docs/user/python-workbench.md)运行脚本和持久控制台，访问当前工程。生成文件保持只读。 |
| 资产与模型 | 查看纹理、静态模型几何和元素外观，打开 Blockbench 编辑模型并同步保存结果。 |
| 工作区工具 | 浏览关系图，编辑变量、标签和翻译，查看本地历史及迁移预览。 |
| 构建与交付 | 构建、启动测试客户端或服务端，从诊断定位源码并导出模组；精确已验证导出绑定所选验收记录的产物。 |

具体功能与生成器范围见[使用说明](docs/user/README.md)。Bedrock Add-on 不属于当前第一方 Java 模组编辑范围。

## 文档

- [使用指南](docs/user/README.md) · [下载](docs/releases/current.md) · [故障排查](docs/user/troubleshooting.md)
- [简洁创作](docs/user/simple-creation.md) · [专业版 Agent](docs/user/professional-agent.md) · [Python 工作台](docs/user/python-workbench.md)
- [Agent 接入](docs/ai/getting-started.md) · [SDK](sdk/README.md)
- [开发环境](docs/build/development-setup.md) · [贡献指南](CONTRIBUTING.md) · [全部文档](docs/README.md)

<a id="development"></a>

## 从源码运行

需要 JDK 25（原生桌面运行需带 JCEF 的 JetBrains Runtime）、Node.js 22、npm 和 Git；Windows 命令使用 PowerShell 7。先按[开发环境说明](docs/build/development-setup.md)配置 JDK。

```powershell
git clone https://github.com/Lotulune/Copperbench.git
Set-Location Copperbench
npm ci --prefix ui-core
npm ci --prefix ui-shell
.\gradlew.bat runProductShell
```

Linux 使用 `./gradlew runProductShell`。构建使用仓库自带的 Gradle Wrapper。

## 来源与许可

Copperbench 是 MCreator 的独立衍生项目，采用 [GPL-3.0-only](LICENSE.txt)。感谢 Pylo 和 [MCreator 贡献者](https://github.com/MCreator/MCreator/graphs/contributors)。上游版本与来源记录见 [UPSTREAM.md](UPSTREAM.md)。

[附加许可条款](LICENSE-ADDITIONAL-TERMS.md)保留了模板例外、商标及 Minecraft mappings 声明；第三方许可与致谢见 [license](license/) 和 [compliance](compliance/)。MCreator 是 Pylo 的商标，相关名称和标志不随 GPL 授权。

**本项目并非官方 MCreator 或 Minecraft 产品，未经 Mojang 或 Microsoft 批准，也与其无关联。**
