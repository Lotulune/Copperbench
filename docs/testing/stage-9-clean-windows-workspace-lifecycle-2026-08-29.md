# Stage 9 clean Windows workspace lifecycle（2026-08-29）

## 结论与边界

在此前刚安装 Windows 11、安装 Copperbench 并通过真实产品 GUI 创建 `guigatedelta` 工作区的同一台
`Copperbench-G7` 客机上，本轮补齐“产品创建工作区 → 已生成工作区 → Gradle build → jar → runClient”的后半段
真实运行闭环。工作区创建本身仍由
[Stage 9 Windows GUI / CLI 产品路径验证](./stage-9-clean-windows-gui-2026-08-28.md) 的
`clean-windows11-gui-new-workspace.json` 证明；本轮脚本不重新创建工作区，也不把已有工作区伪装成一次新的 GUI 创建。

机器可读结果为
[`clean-windows11-workspace-lifecycle.json`](../../evidence/stage-9/2026-08-29/clean-windows11-workspace-lifecycle.json)，
最终 `passed=true`。

## 客机与构建输入

- 客机：Windows 11 `Copperbench-G7`；
- 工作区：`C:\Users\g7admin\MCreatorWorkspaces\guigatedelta\guigatedelta.mcreator`；
- Generator：`neoforge-1.21.1`；
- 工作区 Wrapper：存在并实际使用；
- JDK：Copperbench 管理的 Eclipse Adoptium Java 21；
- Gradle home：`%USERPROFILE%\.copperbench\gradle`；
- 已生成主源码与 `META-INF/neoforge.mods.toml` 均存在，并记录 SHA-256；
- 产品启动器：`C:\Copperbench-G9\copperbench.exe` 存在。

## build 结果

客机直接在真实工作区执行 `gradlew.bat --no-daemon --stacktrace build`：

| 字段 | 结果 |
| --- | --- |
| `buildExitCode` | `0` |
| `buildSucceeded` | `true` |
| `buildArtifactPresent` | `true` |
| Gradle | `BUILD SUCCESSFUL` |
| jar | `build\libs\modid-1.0.jar` |
| jar SHA-256 | `6ccbefa6bb3eee4f9cde0ce2f7beb83ec1820960dc25975bfbd7bf2c1146e533` |

## runClient 结果

同一客机、同一工作区使用同一管理 JDK/Gradle home 启动 `runClient`。交互桌面实际观察到新 Java 进程的
非零主窗口句柄与窗口标题 `Minecraft: NeoForge Loading...`；窗口出现后继续观察 10 秒，进程保持存活，随后才由
门禁主动终止测试客户端。

关键机器字段：

| 字段 | 结果 |
| --- | --- |
| `runClientStarted` | `true` |
| `runClientWindowObserved` | `true` |
| `runClientStable` | `true` |
| `runClientLogObserved` | `true` |
| `runClientTerminatedAfterReadiness` | `true` |
| native HWND | non-zero (`1049728` in the final run) |
| probe duration | `25.98 s` |

Minecraft/NeoForge 日志同时确认 Java 21、Minecraft 1.21.1、NeoForge 21.1.232、`guigatedelta` 模组发现，
以及 NVIDIA GeForce RTX 3060 Laptop GPU 的 OpenGL 4.6 初始化成功。

## 门禁脚本加固

初始验证暴露的是测试探针问题而非 Copperbench 产品失败：

1. 单次宿主工具等待时间短于脚本内部 `runClient` 门限，因此脚本改为可由宿主独立进程执行并读取机器证据；
2. 清洁客机上的 UIA/CIM 查询可能长时间阻塞，最终客户端窗口检测收敛为交互会话内的
   `Process.MainWindowHandle/MainWindowTitle`，不依赖 Chromium UIA provider；
3. `Process.HasExited` 在该环境曾于已结束子进程上阻塞，改为按 PID 的 `Get-Process` 存在性检查；
4. `Get-Content` 返回的日志行携带 FileSystem ETS Provider 元数据，直接 `ConvertTo-Json -Depth 8` 会递归展开
   `PSDrive/PSProvider` 并导致证据序列化近似挂死；门禁现先把每行归一化为纯 `System.String`，并保留 stage heartbeat
   与 emergency result 作为 fail-diagnostic 保护。

这些修复没有放宽产品通过标准；最终 PASS 仍要求真实 build、jar、交互窗口、稳定窗口期和运行日志同时成立。

## 剩余边界

本记录关闭的是当前候选在干净 Windows 11 客机上的产品创建工作区后续 build/run 闭环。它不会把
`clean-windows-11-stage9` 提升为 `passed`：最终 Public Beta/RC 安装包仍需重放完整 G9.5 安装/升级/断网/保留矩阵。
真实 JCEF 可访问性也仍独立缺少物理 150%/175%/200% DPI、Windows 屏幕阅读器、完整人工键盘审计和清洁客机
Chromium UIA provider 控件暴露证据。
