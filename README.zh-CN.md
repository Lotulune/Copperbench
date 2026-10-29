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

Copperbench 是基于 MCreator 的桌面工作台，支持 Fabric 和 NeoForge。先用可视化编辑器创建方块，再用手写 Java 扩展行为，让外部 Agent 通过本机会话检查同一个工作区、修复构建并交付可审阅的产物。revision、源码 hash 和 generated/manual 归属保护各步骤间的修改。

**当前版本：0.1.18，Windows 与 Linux 均已发布。** 本版加入静态模型与元素预览、与简洁创作共享会话的专业工作台 Agent，并改进 Blockbench 打开响应与模型保存同步。安装包和发布详情见[当前下载](docs/releases/current.md)。

<a id="getting-started"></a>

## 下载与开始使用

安装包格式与链接统一维护在[当前下载](docs/releases/current.md)。Windows 按[快速开始](docs/user/getting-started.md)安装，Ubuntu 按[Linux 安装说明](docs/user/linux-installation.md)安装。

1. 下载对应平台的安装包，用 Release 附带的校验文件核验。
2. 新建工作区，选择加载器和 Minecraft 版本。
3. 在「简洁创作」中连接本机助手，或切换到专业工作台使用可视化编辑器和源码。按[第一件作品](docs/user/first-creation.md)创建方块，再看[构建失败与导出](docs/user/build-and-export.md)。

工作台支持简体中文和英文。内置 Fabric / NeoForge 生成器覆盖 Minecraft **26.2、26.1.2、1.21.1、1.20.1**；一个工作区同时使用一个活动生成器。

Windows 安装包未做 Authenticode 签名，可能出现 SmartScreen 提示。首次构建需要联网。Blockbench 和创作助手 CLI 需单独安装与配置；Python 工作台需要 Python 3.11 或更新版本。

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

- [文档导航](docs/README.md) · [第一件作品](docs/user/first-creation.md) · [构建失败与导出](docs/user/build-and-export.md)
- [简洁创作](docs/user/simple-creation.md) · [Python 工作台](docs/user/python-workbench.md) · [连接 Blockbench](docs/user/blockbench-setup.md)
- [Agent 接入](docs/ai/getting-started.md) · [用公开 API 创建同一个步道方块](docs/ai/trail-block.md) · [SDK](sdk/README.md)
- [开发环境](docs/build/development-setup.md) · [贡献指南](CONTRIBUTING.md) · [项目目录与维护](docs/maintenance/repository-maintenance.md)
- [当前版本与源码状态](docs/status.md) · [测试与发布记录](docs/testing/README.md) · [历史路线](docs/roadmap/README.md)

<a id="development"></a>

## 从源码运行

需要 JDK 25（原生桌面运行需带 JCEF 的 JetBrains Runtime）、Node.js 22、npm 和 Git；Windows 命令使用 PowerShell 7。先按[开发环境说明](docs/build/development-setup.md)配置开发工具。

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
