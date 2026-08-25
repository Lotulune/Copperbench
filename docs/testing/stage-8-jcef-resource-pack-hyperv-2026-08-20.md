# 阶段 8：JCEF Snap/DPI、资源包客户端加载与 Hyper-V G7（2026-08-20）

- 结论（历史记录）：JCEF 实机 Snap/DPI 与 Fabric 1.21.1 资源包真实客户端加载**已宣称**；2026-08-20 当时 G7 尚未通过。2026-08-23 Hyper-V 最终复验已补齐客机常驻证据，G7 现已通过。签名与品牌不作为 GA 阻断；公开分发是 GitHub 未签名 GPL 衍生版（[ADR-0015](../adr/0015-github-unsigned-gpl-fork.md)）。
- 环境：Windows 11 x64，仓库内置 JBR 25。
- Hyper-V 现状（2026-08-20）：客户机 `Copperbench-G7` 已安装 Windows 11；静默安装/升级/卸载已通过。客机 GUI 常驻当时未宣称，后续已补证据。

## 已宣称

| 项 | 结果 | 证据 / 复现 |
| --- | --- | --- |
| Playwright U4 | 帮助/About、DPI、命中区、崩溃恢复 100/100 | `npx playwright test`（ui-shell） |
| JCEF 实机 Snap/DPI | 真实产品壳 JCEF 窗口：React 上报 8 个 chrome 区域；`HTTOPLEFT=13`；最大化区 `HTMAXBUTTON=9`；向 HWND 发送 `WM_DPICHANGED` 144 后 DPR=1.5 | [`jcef-snap-dpi.json`](../../evidence/stage-8/2026-08-20/jcef-snap-dpi.json)；`pwsh -NoProfile -File .\scripts\verify-stage-8-jcef-snap-dpi.ps1` |
| 资源包真实客户端加载 | `prepare_resource_pack_client` 仍不自动启动游戏。独立 Fabric 1.21.1 `runClient` 探测看到 `COPPERBENCH_STAGE3_READY`，且 `Reloading ResourceManager:` 列出 `file/copper_ready_pack.zip` | [`resource-pack-1211-client.json`](../../evidence/stage-8/2026-08-20/resource-pack-1211-client.json)；日志同行见 [`resource-pack-1211-client.log`](../../evidence/stage-8/2026-08-20/resource-pack-1211-client.log)；`pwsh -NoProfile -File .\scripts\verify-resource-pack-1211-client.ps1` |

未宣称：物理显示器热插拔 / 用户拖到 Snap 弹出菜单的桌面走查。DPI 证据是对真实 JCEF HWND 发送 `WM_DPICHANGED`，不是换显示器。

## Hyper-V G7（后续已通过）

路径改为主机 Hyper-V 干净客户机，不再把朋友实机当唯一关闭方式。

1. 已启用功能：`scripts/Enable-G7HyperV.ps1`
2. 主机探测：`scripts/verify-stage-8-hyperv-ready.ps1` → [`hyperv-ready.json`](../../evidence/stage-8/2026-08-20/hyperv-ready.json)
3. 重启主机，使 Hyper-V 模块可用
4. 放入官方 Windows 11 x64 ISO（拒绝 KMS/激活器 ISO；不再接受 Windows 10）
5. `scripts/New-G7HyperVGuest.ps1 -IsoPath <windows.iso> -GuestOs windows11`
6. 在客户机安装无开发工具的 Windows
7. `scripts/Invoke-G7HyperVGuestChecks.ps1 -GuestUser <admin> -GuestPassword <secure> -DisconnectNetwork`

核对清单：[`clean-windows-hyperv-checklist.md`](./clean-windows-hyperv-checklist.md)

2026-08-23 最终复验：`processStartedWhileDisconnected=true`，且安装/升级/卸载、工作区和用户目录保留均通过。G7 已收口为 `passed`。签名与品牌按 ADR-0015 处理，不属于 G7 门禁。
