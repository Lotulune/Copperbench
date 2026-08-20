# 干净 Windows Hyper-V 客户机核对清单（G7）

这是阶段 8 干净安装路径。在客户机完成安装、升级、卸载和断网启动并写出证据之前，G7 不算通过。

不要使用 KMS / 激活器 ISO。使用官方 Windows 11 x64 ISO。Windows 10 不在支持范围。

## 主机

1. 以管理员运行 `pwsh -NoProfile -File .\scripts\Enable-G7HyperV.ps1`
2. 若 `hyperVModuleAvailable=false`，重启主机（本脚本默认不重启）
3. `pwsh -NoProfile -File .\scripts\verify-stage-8-hyperv-ready.ps1`
4. 确认 `getVmAvailable=true` 且 `windowsIsoCandidates` 非空
5. `pwsh -NoProfile -File .\scripts\New-G7HyperVGuest.ps1 -IsoPath <官方ISO> -GuestOs windows11`

Win11 客户机脚本会打开 Secure Boot、TPM 和 Guest Service Interface。

## 客户机安装

1. 在虚拟机里安装 Windows，不要装 Visual Studio / IntelliJ / 额外 JDK / Git
2. 创建一个本地管理员账户，记下用户名和密码
3. 完成 OOBE 后保持虚拟机运行

## 客户机检查

```powershell
$pass = Read-Host -AsSecureString 'Guest admin password'
pwsh -NoProfile -File .\scripts\Invoke-G7HyperVGuestChecks.ps1 `
	-GuestUser '<admin>' `
	-GuestPassword $pass `
	-DisconnectNetwork
```

脚本会：静默安装 → 种工作区与 `.copperbench` 标记 → 静默升级 → 断网后启动 `copperbench.exe` → 静默卸载（默认保留用户目录）→ 确认工作区与用户标记仍在 → 写 `evidence/stage-8/<date>/hyperv-g7-guest-checks.json`。

2026-08-20：`hyperv-g7-guest-checks.json` 为 `passed=true`。已宣称静默安装/升级/卸载与用户数据保留。客机 `copperbench.exe` 常驻未宣称。

## 不要宣称

- 主机开发机上的静默演练（已有，但不是干净客户机）
- 仅启用 Hyper-V 功能
- 未写出 `passed=true` 的客户机检查
