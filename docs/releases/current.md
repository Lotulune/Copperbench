# Current downloads / 当前下载

Copperbench Windows 与 Linux **0.1.13** 均已正式发布。Windows 保留应用内更新时的现有快捷方式，减少不必要的文件写入，并完善更新助手的身份说明和中英文提示。Linux 包含共享桌面、原生窗口及应用内更新改进，18 项公开资产已核对大小和 SHA-256，详见[Linux 发布记录](../testing/linux-publication-0.1.13.md)。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.13](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.13), published 2026-10-19T15:05:14Z | 图形安装 EXE · Portable ZIP · MSIX · 应用内更新组件 | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.13](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.13-linux-stable), published 2026-10-19T21:45:26Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz · 应用内更新组件 | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 单独安装。

## Release scope / 版本验证范围

- Windows 0.1.13 来自签名标签对应的主线提交 `464b75a39c34252f61c7d859b8d267dfabba205c`。主线 CI、431 项浏览器回归、完整 Windows 发布测试及 Linux 候选检查通过。
- 15 个公开文件的上传摘要与校验清单一致；已下载正式 EXE 并核对 SHA-256，4 个更新组件的大小与哈希匹配公开清单。[0.1.13 发布记录](../testing/maintenance-release-0.1.13.md)。
- [更新助手修复验证](../testing/update-helper-2026-10-19.md)覆盖快捷方式保留、中英文弹窗和高缩放显示。本次没有重复最终包的 360 复扫、实际安装、升级、卸载或完整玩家验收，尚未确认所有安全软件告警消失。
- Linux 0.1.13 使用冻结源码 `464b75a39c34252f61c7d859b8d267dfabba205c` 的候选包，签名发布标签指向 `4110b253f5d3f99218caf817759154ff3e2d2613`。13 项候选门禁、独立消费者核验、来源证明及草稿下载字节比对通过；[发布工作流](https://github.com/Lotulune/Copperbench/actions/runs/39328695381)成功，18 项公开资产的大小和 SHA-256 与冻结记录一致。本次未重复完整 GNOME 安装、升级、卸载及玩家操作验收。
- [0.1.9 公开包云端验收](../testing/cloud-acceptance-0.1.9.md)覆盖 Windows Server 2025 Setup、Ubuntu 24.04 Setup 与 DEB 的安装生命周期、安装后 SDK 构建、导出和服务端 GameTest；该历史证据不转用于 0.1.13。

[0.1.12 发布记录](../testing/maintenance-release-0.1.12.md)、[0.1.11 发布记录](../testing/maintenance-release-0.1.11.md)及更早的[独立升级验收](../testing/in-app-update-2026-10-17.md)保留各自源码和包哈希。Linux 保持 `formalSupportClaim=false`，完整桌面认证与云端自动验收分别记录。

随包文档与状态声明保留对应源码构建时的文本；后续发布记录不改变已发布字节。0.1.8 的签名源码标签保留为失败发布记录，没有正式二进制。[0.1.7 记录](../testing/maintenance-release-0.1.7.md)及更早证据保持原样。
