# 阶段 4 G4 产品外壳验证记录

- 结论：G4 自动化门禁通过；产品外壳已具备桥接集成条件。
- 环境：Windows 11 x64，仓库内置 JBR 25，JCEF Chromium 137。
- 复现脚本：[`verify-stage-4-bridge.ps1`](../../scripts/verify-stage-4-bridge.ps1)

## 已通过

- UI-Core Schema/fixtures：10/10。
- React production build：通过，入口仅引用 `./assets/...`，资源随 Java `processResources` 打入 classpath。
- Playwright：72/72。功能回归覆盖 1920 与 1366 紧凑视口；视觉基线覆盖 1366×768@125%、1920×1080@150%、2560×1440@175%、3840×2160@200%，并断言页面及标题栏控件无横向溢出或视口越界。
- Java 集成：39/39，覆盖持久化兼容、稳定 workspace ID、共享 revision store、endpoint、Core/Window/Legacy Plugin transport、窗口区域协议、真实 Win32 HWND、真实 C 级插件装载、旧版 Swing 内容承载、资源解析和 renderer 恢复生命周期。
- 真实 JCEF：加载 `http://mcreator/copperbench/ui/index.html`，React DOM 出现并完成 Schema `1.0` 原生握手；Legacy Plugin host 注入和 `open` Java 回调通过。
- `run_client`：标题栏命令路由正确；运行中 task 经 `get_task` 刷新到终态并显示日志。
- renderer 恢复：测试精确结束当前页面 `--type=renderer` 的 JCEF helper，收到 `TS_PROCESS_WAS_KILLED`，显示恢复页并重建 browser/transport；恢复后工作区 ID 和已提交 revision 不变。
- 状态语义：重建期间保持同一 Java 工作区会话；重新握手读取已提交修订，恢复页明确声明崩溃前未提交的纯 UI 输入不会被视为已保存。
- 旧版插件窗口：保留原上游 Swing 工作区内容、菜单和工具栏对象；窗口独立关闭不影响主窗口，专用 transport 不提供文件系统、反射或任意 Java 方法入口。
- C 级插件：`fixture-c-swing` 在临时目录编译为真实 `JavaPlugin`，由 `PluginLoader` 动态加载；工作区标签、菜单、工具栏和偏好事件扩展点均通过合同验证，测试不写入用户插件目录。
- Windows 原生窗口：React 通过 `chromeRegionSchemaVersion: '1.0'` 上报 CSS viewport 区域；宿主实现八向 resize、`HTCAPTION`、`HTMINBUTTON`、`HTMAXBUTTON`、`HTCLOSE`、双击最大化/恢复、最小化/恢复、`Alt+Space` 系统菜单、第二显示器移动和 `WM_DPICHANGED` 建议矩形。
- 系统框架回退：Win32 hook 安装或消息处理失败时恢复原 `WndProc`，重建为有系统边框的 `JFrame` 并保留 bounds、显示状态和工作区会话。

## 保留风险

- 外部第三方 Java 插件拥有完整本机代码执行能力；在选择具体插件并完成来源审计前，不自动下载或执行未知插件。受控 C 级夹具已覆盖 G4 路由，第三方兼容清单仍需逐插件认证。
- 安装包/MSIX 和签名后二进制的 Windows 11 人工桌面走查属于 G7/G8 发布证据，不由源码级 G4 冒烟替代。Windows 10 已不支持。
- JCEF 会记录一次既有的 `WSALookupServiceBegin failed with: 10108` 网络变化通知错误；离线 DOM/bridge 和全部断言仍通过。
