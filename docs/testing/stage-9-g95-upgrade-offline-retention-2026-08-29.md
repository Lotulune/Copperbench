# Stage 9 G9.5 升级、断网与数据保留验证（2026-08-29）

## 结论边界

在已经完成首次安装、首次启动、GUI 新建工作区和 `-workspace` 冷启动验证的同一台 Windows 11
`Copperbench-G7` 客机上，本轮补齐了 G9.5 的旧版本升级、断网启动以及升级/卸载数据保留矩阵。

机器可读结果为 `passed=true`，但同时明确记录 `finalRcReplayRequired=true` 与
`gatePromotionReady=false`。因此本记录证明当前候选实现能够通过这组 G9.5 路径，不代表最终 Public Beta/RC
二进制已经完成发布候选重放；`product-status.json` 中 `clean-windows-11-stage9` 继续保持 `blocked`。

## 输入与真实性约束

- 客机：Windows 11 `Copperbench-G7`，沿用前一轮干净系统安装基线；
- 旧版本：公开 GitHub Release `v0.1.0-preview.3` 的 Windows x64 安装器；
- 旧版安装器 SHA-256：`4c621c330e933422fca918c3c88ba87bec15eef937e6ed51cccc128a0a61bccf`；
- 当前候选安装器 SHA-256：`59c9a01252e2529490e597887dea994bb927ff01cd8264edc256e91369786eca`；
- 保留工作区：`C:\Users\g7admin\MCreatorWorkspaces\guigatedelta\guigatedelta.mcreator`；
- 安装目录：`C:\Copperbench-G9`。

旧版本安装器在进入客机验证前先按 GitHub Release 资产给出的 SHA-256 做完整文件校验；不使用“同一安装器重复
安装”冒充升级。升级前后还比较 `lib\copperbench.jar` 的 SHA-256，要求旧版和当前候选实际载荷不同。

## 验证流程

[`Invoke-G9CleanWindowsUpgradeRetentionGate.ps1`](../../scripts/Invoke-G9CleanWindowsUpgradeRetentionGate.ps1)
执行以下真实产品路径：

1. 在已有 `guigatedelta` 工作区和 `.copperbench` 用户目录中植入本轮唯一 token marker，并记录
   `.mcreator` SHA-256；
2. 如当前候选已经安装，先静默卸载并确认工作区和用户数据均未变化；
3. 静默安装公开旧版 `v0.1.0-preview.3`；
4. 在同一安装目录直接运行当前候选安装器，形成真实旧版到当前候选的升级；
5. 确认升级后的 `copperbench.jar` 与旧版不同，同时工作区文件 hash、工作区 marker 和用户目录 marker 全部保持；
6. 从 Hyper-V 主机断开客机全部已连接网络适配器；
7. 在 `g7admin` 的交互桌面会话中执行 `copperbench.exe -workspace <guigatedelta.mcreator>`；
8. 使用 Windows UI Automation 按目标 Java PID、`SunAwtFrame`、工作区标题和非零 HWND 识别真实工作区窗口，
   并要求窗口出现后进程继续稳定存活；
9. 断网启动结束后再次计算 `.mcreator` SHA-256，并要求它仍然等于步骤 1 记录的原始 baseline hash；后续卸载保留检查继续沿用这一原始 baseline，不允许把断网后可能变化的 hash 重新作为新基线；
10. 恢复网络，静默卸载升级后的当前候选，验证工作区与 `.copperbench` 用户数据仍保留；
11. 重新安装当前候选，把测试客机恢复到测试前的产品版本；
12. 仅在 marker 内容仍等于本轮唯一 token 时删除测试 marker，避免污染后续人工验证。

任何阶段失败都会保持 `passed=false`。脚本还会在 `finally` 中尽力恢复客机 NIC、重新安装当前候选，并只清理
属于本轮的 marker；不会删除工作区目录。

## 断网窗口判定

早期严格化尝试曾在 PowerShell Direct 的非交互会话中读取 `Get-Process.MainWindowHandle`。目标 Java 进程已经
稳定运行且命令行包含正确工作区，但该非交互会话得到的 `MainWindowHandle=0`，因此测试按失败处理，没有继续卸载。

最终门禁改为复用已在 GUI gate 中验证过的交互桌面 UIA 方法。最终通过证据实际观察到：

- `offlineProcessStarted=true`；
- `offlineProcessStable=true`；
- `offlineWorkspaceArgumentObserved=true`；
- `offlineMainWindowObserved=true`；
- 窗口标题：`guigatedelta - Copperbench 0.1.0`；
- AWT 类：`SunAwtFrame`；
- `nativeWindowHandle=327772`；
- `isOffscreen=false`；
- 交互会话 ID：`1`。

这比仅验证“离线时进程没有退出”更严格：目标工作区必须在真实交互桌面上出现可观察的原生窗口。

## 最终结果

| 字段 | 结果 |
| --- | --- |
| `passed` | `true` |
| `previousInstallerVerified` | `true` |
| `preflightCurrentUninstallPreservedWorkspace` | `true` |
| `preflightCurrentUninstallPreservedUserData` | `true` |
| `previousReleaseInstalled` | `true` |
| `oldToCurrentUpgrade` | `true` |
| `upgradeInstalledDifferentPayload` | `true` |
| `upgradePreservedWorkspace` | `true` |
| `upgradePreservedUserData` | `true` |
| `networkDisconnected` | `true` |
| `offlineProcessStable` | `true` |
| `offlineWorkspaceArgumentObserved` | `true` |
| `offlineMainWindowObserved` | `true` |
| `silentUninstall` | `true` |
| `uninstallPreservedWorkspace` | `true` |
| `uninstallPreservedUserData` | `true` |
| `restoredCurrentInstall` | `true` |
| `testMarkersRemoved` | `true` |
| `finalRcReplayRequired` | `true` |
| `gatePromotionReady` | `false` |

升级前工作区 `.mcreator` SHA-256 为
`68485e5f94c7c316545f6ed01d84c95e84e4f3fc06ee5798b2f1f1fa823372fc`，升级、断网启动、卸载以及重新安装当前候选后
均保持不变。

## 证据与后续门禁

- [`evidence/stage-9/2026-08-29/clean-windows11-upgrade-offline-retention.json`](../../evidence/stage-9/2026-08-29/clean-windows11-upgrade-offline-retention.json)
- [`scripts/Invoke-G9CleanWindowsUpgradeRetentionGate.ps1`](../../scripts/Invoke-G9CleanWindowsUpgradeRetentionGate.ps1)
- [Stage 9 Windows GUI / CLI 产品路径验证](./stage-9-clean-windows-gui-2026-08-28.md)

当前 G9.5 的开发候选路径已经覆盖首次安装/首次启动、真实 GUI 新建工作区、generator setup、在线
`-workspace` 冷启动、公开旧版升级、断网工作区启动和升级/卸载数据保留。剩余的 G9.5 发布阻断项是：**在最终确定的
Public Beta/RC 安装包上重放同一整套矩阵，并把最终 RC 的机器证据挂回状态源。**
