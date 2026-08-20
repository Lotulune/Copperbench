# UI 重写交接简报

- 负责人：zcodeglm5.3
- 状态：U0、U1、U2 与 G4 自动化门禁完成；UI-Core 已冻结为 `1.0`；Playwright 72/72，Java 集成 39/39；真实 JCEF renderer、Win32 无边框/DPI/多显示器/回退和 C 级 `PluginLoader` 夹具均通过
- 日期：2026-08-16（首版）、2026-08-17（状态更新）

## 已落地的 U2 transport

- `ui-shell/src/bridge/`：`CoreBridge` 接口（含 `negotiateHandshake`）+ 单一绑定点 `index.ts`。完整 native host 存在时绑定 `JcefCoreBridge`，Vite/Playwright 环境保持 Mock。
- 启动握手：UI 以 `supportedSchemaVersions: ['1.0']` 协商，不兼容时渲染结构化启动错误（`schema-incompatible` 场景由协商结果驱动，不依赖场景名特判）。
- Java transport：只接受单一 WebView 主 frame 和工作区 ID；在页面加载开始时注入 host，WebView 关闭时注销 router/listener。
- 桌面组合：`CopperbenchProductShell` 通过 `runProductShell` 接入已打开的上游工作区，React 包从 classpath 离线加载；真实 JCEF 测试已验证 DOM、Schema 握手和原生状态标识。
- 窗口控制：动作桥只有最小化、最大化/恢复、关闭三个白名单动作；React 另以 Schema `1.0` 上报标题栏命中区域，Win32 宿主负责拖动、八向缩放、Snap、系统菜单、DPI 与动态系统框架回退。
- renderer 恢复：React 失效时由 Swing 宿主显示阻断恢复页；重建浏览器和 bridge 时复用原 Java 工作区会话，页面重新握手只读取已提交状态。
- 旧版插件：兼容状态页通过只接受 `open` 的独立 host 打开原上游 Swing 内容、工具栏和菜单；关闭旧版窗口不卸载插件或关闭主窗口，renderer 重建后会重新挂载该 host。
- 任务生命周期：真实 bridge 对运行中的 task 轮询 `get_task`，同步终态和日志，并在 dispose 时清理 timer。
- i18n：产品决策界面主语言为中文；合同 LocalizedText 经 `t()` + zh 词典渲染，缺词条回退英文 fallback；技术标识（元素名、枚举、日志原文、MCP 档位）保留英文。

## 目标

重写产品外壳，而不是为 MCreator 换主题。整体体验应更直观、更简洁、更高级并具有沉浸感，优先服务不要求掌握 Java 的个人 Minecraft 模组创作者。

## 已确定约束

- 产品外壳使用 JCEF + React + TypeScript，不使用 Electron。
- 工作区、生成器、资产、版本历史和 MCP 的业务规则属于 Java 领域服务，UI 只调用它们。
- UI 不能直接访问文件系统或持有 Java 领域对象，只能使用带 Schema 的命令、查询和事件桥。
- Fabric 是主加载器，NeoForge 是正式辅加载器；差异必须在界面中明确表达。
- 一个工作区同一时刻只有一个活动生成器。
- MCP 显示只读、工作区、完全访问三档权限，并始终确认受保护操作。
- 版本历史使用“版本、比较、恢复点、还原”等产品语言，不要求用户理解 Git。
- Blockbench 是受管外部应用，不在产品中重做模型编辑器。
- 生成源码与手写源码必须明确区分，并在可能覆盖修改时警告。
- 首期正式支持 Windows 11 x64；Windows 10、Linux 与 macOS 不提供安装包或支持承诺。
- 所有前端资源离线随安装包分发，不使用 CDN，默认不包含遥测。
- 原版 Swing UI 插件在独立旧版插件界面中运行，不强行嵌入 React 页面。

## 自适应与窗口要求

- 布局适配 1366×768、1080p、2K、4K，以及 125%、150%、175%、200% Windows 显示缩放。
- 紧凑窗口折叠次要面板，宽屏增加信息并排能力；不得简单按比例缩小整张界面。
- 无边框窗口必须支持拖动、边缘缩放、最小化、最大化、恢复、Windows Snap Layout、`Alt+Space` 系统菜单和多显示器切换。
- 自定义标题栏的拖动区域、交互控件和窗口按钮必须有稳定命中区域，不能互相覆盖。
- 原生窗口能力异常时回退到系统窗口框架，不能让用户失去移动或关闭窗口的能力。

## 验收要求

- 视觉回归覆盖上述分辨率和显示缩放组合。
- 覆盖键盘导航、焦点可见性、长文本、空状态、加载状态、错误状态和离线状态。
- 独立运行 React 前端的 Playwright 交互与截图测试；Java/JCEF 层运行桥接契约和窗口行为测试。
- JCEF 渲染进程崩溃后显示可恢复状态，并保留未提交领域操作的真实状态。
- 核心任务以可重复的用户路径验收，不以单张设计稿作为完成标准。

## 分工

- zcodeglm5.3：信息架构、视觉系统、交互原型、React 产品外壳和前端测试。
- 核心开发：领域命令、查询、事件、权限、插件兼容、窗口原生桥和集成测试。
- 双方通过版本化 Schema 合同协作，不通过直接读取彼此内部状态耦合。

U0 已关闭：选定方向 A「专注工坊流」。三套原型保留在 `prototypes/`。产品壳为默认启动。
