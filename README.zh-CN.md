<div align="center">
  <img src="assets/branding/copperbench-icon-source.png" alt="Copperbench" width="96">
  <h1>Copperbench</h1>
  <p><strong>在桌面上制作、构建和测试 Minecraft Java 模组。</strong></p>
  <p><a href="README.md">English</a> · <strong>简体中文</strong></p>
  <p>
    <a href="https://github.com/Lotulune/Copperbench/releases">下载</a> ·
    <a href="#showcase">截图</a> ·
    <a href="#getting-started">开始使用</a> ·
    <a href="#development">源码运行</a>
  </p>
</div>

Copperbench 基于 MCreator，提供模组元素编辑、Blockly 逻辑、模型与纹理管理、本地历史，以及供外部 AI 工具使用的 MCP 接口。支持 Fabric 和 NeoForge。

<a id="showcase"></a>

## 看看它怎么用

**工作台** — 查看模组元素、项目诊断和构建入口。

![Copperbench 工作台，显示模组元素与项目状态](assets/screenshots/zh-CN/workbench.png)

<table>
  <tr>
    <td width="50%"><img src="assets/screenshots/zh-CN/function.png" alt="中文版函数编辑器" width="480"></td>
    <td width="50%"><img src="assets/screenshots/zh-CN/procedure.png" alt="中文版 Blockly 过程编辑器" width="480"></td>
  </tr>
  <tr>
    <td><strong>编辑函数</strong><br>编写 mcfunction，查看语法诊断，保存后继续编辑。</td>
    <td><strong>编排逻辑</strong><br>连接 Blockly 节点，查看对应的过程逻辑。</td>
  </tr>
  <tr>
    <td><img src="assets/screenshots/zh-CN/assets.png" alt="中文版资产列表与引用详情" width="480"></td>
    <td><img src="assets/screenshots/zh-CN/history.png" alt="中文版恢复点与文件变化" width="480"></td>
  </tr>
  <tr>
    <td><strong>管理资产</strong><br>浏览纹理、检查引用关系，找出内容重复的文件。</td>
    <td><strong>查看本地历史</strong><br>比较恢复点，查看具体哪些文件发生了变化。</td>
  </tr>
</table>

<sub>截图于 2026 年 9 月 14 日拍摄，使用 Windows 桌面开发构建和 Mossglow 示例工程；下载包的界面可能不同。Blockbench 需单独安装。</sub>

## 能做什么

| 功能 | 用法 |
| --- | --- |
| 模组 | 编辑方块、物品、配方、实体等元素，用 Blockly 编排 Procedure。 |
| 资源 | 管理模型、纹理、标签和语言文件，导出资源包 ZIP。 |
| 构建 | 构建工程，启动测试客户端或专用服务端，查看任务日志。 |
| 历史 | 创建本地恢复点，预览还原涉及的文件。 |
| 迁移 | 预览同版本 Fabric ↔ NeoForge 迁移，复制到新工作区。 |
| 自动化 | 通过本机 MCP 或 headless 接口读取工程、修改内容和执行构建。 |

具体支持范围见[使用说明](docs/user/README.md)。源码中的功能可能尚未进入下载包；Bedrock Add-on 不在当前第一方编辑范围内。

<a id="getting-started"></a>

## 开始使用

工作台支持简体中文和英文，可在标题栏切换。请先保存编辑内容，再确认重新加载界面。

| 系统 | 下载格式 | 安装说明 |
| --- | --- | --- |
| Windows 11 x64 | EXE 安装包 · 便携版 ZIP | [Windows 快速开始](docs/user/getting-started.md) |
| Ubuntu 24.04 LTS x86_64 | Debian `.deb` · 便携版 `.tar.gz` | [Linux 安装说明](docs/releases/linux-release-notes.md) |

Ubuntu 已验证 GNOME Wayland 和 Xorg。其他 Linux 发行版和架构尚未验证。

1. **下载安装** — 在 [GitHub Releases](https://github.com/Lotulune/Copperbench/releases) 选择系统对应的包，对照该版本附带的校验文件核验下载包（Windows 为 `SHA256SUMS.txt`；Linux Preview 2 为 `linux-candidate-sha256.txt`）。
2. **新建工作区** — 选择 Fabric 或 NeoForge 及 Minecraft 版本，填写模组名称与目录。
3. **做一个物品** — 新建物品、保存并构建，然后启动测试客户端。

发行包仍处于预览 / 测试阶段；Windows 安装包未签名，SmartScreen 可能提示警告。首次构建需要联网下载依赖。

## 文档

- [使用说明](docs/user/README.md) · [故障排查](docs/user/troubleshooting.md)
- [MCP 接入](docs/ai/getting-started.md) · [Agent 操作示例](docs/ai/agent-playbook.md) · [SDK](sdk/README.md)
- [开发环境](docs/build/development-setup.md) · [Windows 干净构建](docs/build/windows-clean-build.md)
- [后续计划](PRD-NEXT.md) · [贡献指南](CONTRIBUTING.md)

<a id="development"></a>

## 从源码运行

需要 JDK 25（桌面运行推荐带 JCEF 的 JetBrains Runtime）、Node.js 22、npm 和 Git；Windows 命令使用 PowerShell 7。先按[开发环境说明](docs/build/development-setup.md)配置 JDK。

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
