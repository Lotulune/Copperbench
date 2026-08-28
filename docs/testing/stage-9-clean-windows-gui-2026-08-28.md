# Stage 9 Windows GUI / CLI 产品路径验证（2026-08-28）

## 范围与结论边界

本记录组合两段真实 Windows 11 产品路径证据：首先由
[`Invoke-G9CleanWindowsGuestSmoke.ps1`](../../scripts/Invoke-G9CleanWindowsGuestSmoke.ps1) 在刚完成系统安装的
`Copperbench-G7` 客机上记录基线、静默安装当前 Windows 安装包并首次启动产品；随后由
[`Invoke-G9CleanWindowsGuiGate.ps1`](../../scripts/Invoke-G9CleanWindowsGuiGate.ps1) 在同一客机的已安装产品上验证
WorkspaceSelector、新建工作区、Generator setup 与 `-workspace` 冷启动。它们共同构成 Stage 9 G9.5 的**部分证据**，
后续 2026-08-29 已补齐公开旧版升级、断网工作区启动与升级/卸载数据保留，见
[G9.5 升级、断网与数据保留验证](./stage-9-g95-upgrade-offline-retention-2026-08-29.md)。
`clean-windows-11-stage9` 仍不会提升为 `passed`，因为最终 Public Beta/RC 安装包尚未确定并重放完整矩阵。

首次安装 smoke 的机器可读基线显示 Windows 11 专业版 build 26200，系统安装时间为本测试当天；PATH 中没有项目
自带 JDK，且 `git`、`java`、`javac`、`gradle`、VS Code、IntelliJ、Visual Studio、Android Studio 均不可用。
安装器退出码为 0，`copperbench.exe` 与 bundled Java 均存在，产品在交互会话启动成功，扫描未发现
Unique4j/loopback/address-in-use 等 IPC 失败信号。

本轮已关闭的子路径：

- 真实 `WorkspaceSelector` 打开真实 `NewWorkspaceDialog`；
- 键盘输入工作区名称后，真实 Swing 派生字段、工作区目录与 `.mcreator` 文件一致；
- 新工作区 Generator / Gradle setup 自然完成，不向进度对话框发送确认键；
- 工作区主窗口可用；
- 正常关闭 GUI 实例后，用同一原生启动器执行 `copperbench.exe -workspace <file>` 冷启动；
- 冷启动直接进入目标工作区，不显示 `WorkspaceSelector`，并确认 Java 进程实际收到目标 `.mcreator` 路径。

本记录本身不证明旧版本升级、断网启动和升级/卸载数据保留；这些子路径已由 2026-08-29 的
[后续 G9.5 验证](./stage-9-g95-upgrade-offline-retention-2026-08-29.md) 独立关闭。两组记录组合后，G9.5 仍只剩最终
Public Beta/RC 安装包的完整矩阵重放，未完成前仍是 Beta 阻断项。

## 自动化保护边界

验证脚本 [`scripts/Invoke-G9CleanWindowsGuiGate.ps1`](../../scripts/Invoke-G9CleanWindowsGuiGate.ps1)
不使用坐标点击或 OCR。它按目标进程、AWT 窗口类、标题和非零 HWND 识别真实窗口，并把键盘/窗口消息只投递到
已确认的目标 HWND；窗口角色、启用状态或对话框状态不符合预期时立即停止。

恢复逻辑也只允许处理本脚本创建的 `guigate* - Copperbench*` 测试工作区：必须只有一个符合条件的 enabled
主窗口且不存在其他 enabled AWT 对话框，随后通过产品正常 `WM_CLOSE` 路径退出。脚本不会删除任何工作区目录。

## Swing 派生字段同步

早期真实运行发现一个**测试自动化事件时序问题**：可见的 `modName` 已经是 `guigatealpha`，但通过
`KeyAdapter.keyReleased` 自动派生的 `modID` 只得到 `guigatealph`，从而生成
`guigatealph\guigatealph.mcreator`。这不是通过放宽断言解决的。

源码链路为：

1. `NewWorkspaceDialog` 的主输入字段是 `modName`；
2. `WorkspaceDialogs` 在 `modName` 的 `keyReleased` 中同步 `modID` 与 package；
3. `AbstractWorkspacePanel` 再由 `modID` 的 DocumentListener 同步工作区目录；
4. 创建动作最终从这些已派生字段构造并保存 `.mcreator`。

脚本发送完整名称后增加一个**不修改文本的 Right Arrow AWT 键循环**并等待 EDT 消化，使最后一次
`keyReleased` 观察到完整文本，再提交创建动作。之后真实运行生成了完整路径；断言仍要求候选名称、目录名、
mod ID 与 `.mcreator` 文件名完全一致。

## Generator setup 判定

`WorkspaceGeneratorSetupDialog` 是自动执行 workspace base、Gradle daemon 处理、Gradle sync、缓存导入和 base
generation 的进度对话框，不是需要按 Enter 的设置表单。本脚本只观察它，不发送输入，并等待其自然关闭。

Windows UI Automation 在本环境把该 `SunAwtDialog` 暴露为 `ControlType.Pane`，因此脚本按精确的
`SunAwtDialog` 类、`Workspace setup for selected generator` 标题和非零 HWND 识别，而不依赖 UIA
`ControlType.Window`。setup 期间主窗口 disabled；setup 完成后主窗口恢复 enabled。

## 通过结果

最终端到端候选为 `guigatedelta`。最新机器可读证据的关键字段为：

| 字段 | 结果 |
| --- | --- |
| `passed` | `true` |
| `selectorObserved` | `true` |
| `dialogObserved` | `true` |
| `derivedFieldsSettled` | `true` |
| `workspaceCreated` | `true` |
| `dialogClosed` | `true` |
| `workspaceMainObserved` | `true` |
| `generatorSetupObserved` | `true` |
| `generatorSetupClosed` | `true` |
| `cliWorkspaceObserved` | `true` |
| `cliArgumentObserved` | `true` |
| `cliSelectorObserved` | `false` |

真实生成文件：

`C:\Users\g7admin\MCreatorWorkspaces\guigatedelta\guigatedelta.mcreator`

CLI 冷启动使用：

`C:\Copperbench-G9\copperbench.exe -workspace C:\Users\g7admin\MCreatorWorkspaces\guigatedelta\guigatedelta.mcreator`

启动后的 Java 命令行包含：

`net.mcreator.Launcher -workspace C:\Users\g7admin\MCreatorWorkspaces\guigatedelta\guigatedelta.mcreator`

最终可用窗口为 `guigatedelta - Copperbench 0.1.0`，AWT 类为 `SunAwtFrame`，`IsEnabled=true`。

## 证据

- [`evidence/stage-9/2026-08-28/clean-windows11-product-shell.json`](../../evidence/stage-9/2026-08-28/clean-windows11-product-shell.json)
- [`evidence/stage-9/2026-08-28/clean-windows11-product-shell.png`](../../evidence/stage-9/2026-08-28/clean-windows11-product-shell.png)
- [`evidence/stage-9/2026-08-28/clean-windows11-gui-new-workspace.json`](../../evidence/stage-9/2026-08-28/clean-windows11-gui-new-workspace.json)
- [`evidence/stage-9/2026-08-28/clean-windows11-gui-new-workspace.png`](../../evidence/stage-9/2026-08-28/clean-windows11-gui-new-workspace.png)
- [`scripts/Invoke-G9CleanWindowsGuestSmoke.ps1`](../../scripts/Invoke-G9CleanWindowsGuestSmoke.ps1)
- [`scripts/Invoke-G9CleanWindowsGuiGate.ps1`](../../scripts/Invoke-G9CleanWindowsGuiGate.ps1)

这组证据关闭的是“新装 Windows 11 客机首次安装/首次启动 + 真实 Windows GUI 创建 + generator setup + 原生启动器
`-workspace` 冷启动”子路径；
2026-08-29 的后续记录已补齐升级/断网/保留子路径。`product-status.json` 中 `clean-windows-11-stage9` 继续保持
`blocked`，直到最终 Public Beta/RC 安装包重放完整 G9.5 矩阵。
