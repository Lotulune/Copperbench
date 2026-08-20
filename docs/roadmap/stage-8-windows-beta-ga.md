# 阶段 8：Windows Beta 与正式发布

- 状态：进行中（G7 自动化切片已落地，未宣称 G7/GA 通过）
- 依赖：阶段 0-7
- 对应门禁：G0-G7

## 目标

将已验证功能收敛为可公开下载、可升级、可诊断、许可完整的 Windows 产品，并通过公开 Beta 发现真实工作区和插件兼容问题。

## 范围

- Windows 11 x64 安装、升级、卸载与崩溃恢复。Windows 10 不支持。
- Java 25、JCEF、本机窗口桥及必需运行时打包。
- 离线启动、前端资源、依赖缓存提示和网络权限检查。
- 完整加载器/版本矩阵、插件兼容清单和已知限制。
- 性能、内存、长时间运行、日志、错误报告和隐私检查。
- 公开 GPL 源码、构建说明、NOTICE、来源和修改记录。
- Beta 反馈分类、兼容回归和发布阻断规则。
- 用户文档：工作区、版本、MCP 权限、Blockbench、迁移、插件信任和恢复。

## 不在范围

- Linux 或 macOS 安装包。
- 自有账户、云同步或默认遥测。
- 远程 MCP。
- 内置 AI 模型供应商界面。
- 未通过兼容矩阵的“支持全部插件”声明。

## Beta 准入

- G0-G6 全部通过。
- 所有已知数据损坏、凭据泄露和受保护操作绕过问题清零。
- 上游功能审计完成：每项功能标记为新 UI、兼容窗口、暂不支持或不适用。
- 安装包可在无开发工具的干净 Windows 机器启动。
- 升级演练保留工作区、历史和插件。

## GA 阻断项

- 可复现的工作区或历史损坏。
- 权限档位、令牌、受保护操作或日志脱敏绕过。
- 受支持矩阵中的生成或编译失败。
- 无边框窗口导致无法移动、缩放或关闭且不能回退。
- JCEF 崩溃造成已提交状态丢失。
- 安装、升级或卸载删除用户数据。
- GPL 源码、许可证、署名或来源不完整。

## 发行证据

- G0-G7 汇总报告和链接到各阶段证据。
- Windows 11 安装、升级、卸载矩阵。
- 四轨双加载器生成器结果。
- UI 分辨率/DPI/窗口行为截图与自动化报告。
- MCP 一致性与安全测试报告。
- Blockbench 与资源包黄金样例结果。
- 插件兼容清单和上游功能覆盖清单。
- SBOM、许可证和源代码获取说明。

## 当前 G7 自动化切片（2026-08-19）

- [x] 机器可读发布说明：四轨矩阵、黄金/可生成声明、已知限制、源码指针、G7 状态（`ReleaseManifest` / `get_release_notes`）。
- [x] 隐私默认：不要求账户、默认无遥测、无隐式网络、前端无 CDN。
- [x] 安装器卸载默认保留 `$PROFILE\.copperbench`；静默升级不再因未初始化而清用户数据；不删除用户自选工作区目录。
- [x] 源码分发清单文件存在性检查。
- [x] Windows 导出配方与现有 win64 布局：JDK/JCEF、许可证、第一方插件。
- [x] 第一方插件兼容清单（A/B/C/X）写入发布说明。
- [x] Windows 11 管理员开发机：隔离目录静默安装、升级、卸载，工作区与用户目录保留。
- [x] Fabric / NeoForge 1.21.1 已缓存依赖的 Gradle `--offline` 构建（先预热缓存；不是 OS 断网）。
- [x] Windows 10 移出支持范围（安装器 / 启动器 / 发布说明拒绝 build < 22000）。
- [x] Windows 11 Hyper-V 客户机：静默安装、升级、卸载，工作区与 `.copperbench` 保留。NIC 已断开。`copperbench.exe` 客机常驻未宣称。证据：[`hyperv-g7-guest-checks.json`](../../evidence/stage-8/2026-08-20/hyperv-g7-guest-checks.json)。
- [x] 已安装插件动态清单：`list_installed_plugins` / `headless plugins`，不加载 Java。
- [x] 开发期功能覆盖矩阵与组件清单（签名级 SBOM 仍未做）。
- [x] 开发测试版用户说明：[`docs/user/README.md`](../user/README.md)。
- [x] JCEF 实机 Snap/DPI：[`jcef-snap-dpi.json`](../../evidence/stage-8/2026-08-20/jcef-snap-dpi.json)。
- [x] Fabric 1.21.1 资源包真实客户端加载：[`resource-pack-1211-client.json`](../../evidence/stage-8/2026-08-20/resource-pack-1211-client.json)。
- [x] 公开身份与分发政策：Copperbench GitHub 未签名 GPL 衍生版；无域名、无商店、无 Authenticode（[ADR-0015](../adr/0015-github-unsigned-gpl-fork.md)）。G7 不因此变为 passed。

复现：`pwsh -NoProfile -File .\scripts\verify-stage-8.ps1`

## 退出条件

- [ ] G0-G7 全部通过且无 GA 阻断项。
- [ ] Beta 收集的高严重度回归已关闭并有自动化防回归。
- [ ] 支持矩阵、插件矩阵和功能覆盖矩阵与安装包一致。
- [ ] 断网状态下核心创作流程可用。
- [ ] 发布包、源码标签、构建说明和许可证同步可用。
