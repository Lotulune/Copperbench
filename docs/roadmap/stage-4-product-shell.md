# 阶段 4：新产品外壳集成

- 状态：G4 自动化门禁已通过；待发布阶段的打包桌面走查与第三方插件逐项认证
- 依赖：阶段 1；正式集成依赖阶段 3 Schema v1
- 对应门禁：G4，部分 G1/G3

## 目标

用自适应、无边框、离线的 JCEF + React 产品外壳承载阶段 3 完整工作流，并建立后续页面扩展标准。

## 范围

- Java 25/JCEF 宿主和 React/TypeScript 构建、打包、加载与版本协商。
- 带 Schema 的命令、查询、事件桥。
- 工作区选择、工作区总览、元素列表、纵向元素编辑、资源入口、构建运行、日志、历史和 MCP 权限页面。
- 紧凑、标准和宽屏自适应工作台。
- Windows 无边框窗口、Snap、系统菜单、多显示器和 DPI 处理。
- 旧版 Swing 插件独立窗口与插件兼容状态页面。
- Playwright、视觉回归、JCEF bridge 和原生窗口测试。

## 2026-08-17 已完成

- `buildUiShell -> processResources` 将相对路径 React 产物打入 `copperbench/ui`，由 `http://mcreator/copperbench/ui/index.html` 离线加载。
- `CopperbenchProductShell` 持有真实 `MCreatorWorkspaceSession`、JCEF WebView、Core/Window/Legacy Plugin transport；初始化失败、renderer 重建与关闭路径均释放对应 transport、WebView 和 task executor。
- Workspace ID 重开后保持稳定；Fabric task gateway 与应用服务共享同一 `RevisionedWorkspaceStore`。
- 窗口动作桥只接受 `minimize`、`toggle_maximize`、`close`；独立的 Schema `1.0` 区域通道只接收严格校验的 CSS viewport 命中快照，不暴露 Java 对象、文件系统或反射。
- 原生 task 查询轮询会更新终态、日志并在 bridge dispose 时清理 timer；“测试客户端”已绑定 `run_client`，不再误发 `build_workspace`。
- `WebView` 监听本地 JCEF 四参数 renderer 终止回调；宿主切到可键盘操作的恢复页，重建 WebView 与三条 transport，并继续复用同一个 `MCreatorWorkspaceSession`。恢复页明确说明只恢复已提交状态，不把崩溃前的 UI 输入标记为已保存。
- 原上游 Swing 工作区内容、工具栏和菜单由 `LegacyPluginWindow` 承载为独立系统窗口；关闭该窗口不卸载插件，也不影响 React 主窗口。前端兼容状态页通过只接受 `open` 的专用 host 打开它，不暴露文件系统、反射或任意 Java 调用。
- 兼容状态页展示 A/B/C/X 分级、C 级旧版窗口路由和 Java 插件默认禁用警告；浏览器预览中入口明确禁用，真实 JCEF 冒烟已验证 host 注入与 Java 回调。
- 打包的 `copperbench.exe` 与 `runMCreator` 默认打开新产品外壳（`-Dcopperbench.productShell=true`）。旧 Swing 工作区用 `-Dcopperbench.productShell=false` 回退。

复现：`pwsh -NoProfile -File .\scripts\verify-stage-4-bridge.ps1`。当前结果为 UI-Core 10/10、Playwright 72/72、Java 集成 39/39；包含真实 JCEF renderer 故障、Win32 HWND、120/144 DPI、第二显示器和真实 `PluginLoader` C 级夹具。

## 发布前后续

- 打包产物的 Windows 11 安装、签名、升级和人工桌面走查在 G7/G8 执行。Windows 10 不支持。
- 选择经过来源审计的第三方 C 级 Swing 插件，逐项加入兼容清单；未知 Java 插件不得由自动化静默启用。
- [x] 实际安装插件动态清单已接入兼容状态页（`list_installed_plugins`）。第三方 C 级插件仍需逐项认证。

## 不在范围

- 移动端或浏览器独立版。
- Electron。
- 把 Blockbench 嵌入主窗口。
- 为了视觉一致性重写尚未进入产品范围的所有上游工具。

## 分工

**zcodeglm5.3**：信息架构、三方向原型、视觉系统、交互规范、React 实现、前端组件测试和视觉回归。

**核心开发**：Java bridge、Schema、窗口原生桥、领域服务、权限、旧版插件窗口、JCEF 生命周期和集成测试。

## 自适应验收矩阵

| 模式 | 典型条件 | 行为 |
| --- | --- | --- |
| 紧凑 | 1366×768 或窄窗口 | 次要面板折叠，主任务保持完整，命令进入菜单 |
| 标准 | 1920×1080 | 导航、主编辑区和上下文面板协同显示 |
| 宽屏 | 2K/4K | 使用额外空间并排比较、预览或日志，不放大空白 |

所有模式覆盖 125%-200% 显示缩放、长文本和本地化内容。

## 无边框窗口验收

- 标题栏拖动区域不覆盖导航和操作控件。
- 四边与四角缩放命中稳定。
- 最小化、最大化、恢复、双击标题栏和 Snap Layout 正常。
- `Alt+Space` 系统菜单、键盘关闭与焦点导航可用。
- 跨不同 DPI 显示器移动时不跳变、不模糊、不丢失窗口。
- 自定义框架失败时回退系统窗口框架。

## 风险

- JCEF 与原生窗口消息处理互相干扰。缓解：窗口桥独立模块并提供系统框架回退。
- UI 团队复制业务校验。缓解：前端只使用 DTO 与稳定错误代码，契约测试阻止绕过。
- 全量 UI 重写扩大范围。缓解：先完整覆盖纵向场景，再按兼容矩阵扩展。

## 退出条件

- [x] G4 自动化门禁全部通过。
- [x] 阶段 3 场景可完全在新产品外壳中完成。
- [x] 前端包完全离线加载，无 CDN 和默认遥测。
- [x] UI 无文件系统直连或任意 Java 反射桥。
- [x] 代表性 C 级插件可在旧版插件窗口承载，失败不影响主窗口。
