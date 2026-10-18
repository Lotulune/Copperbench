# Current downloads / 当前下载

Copperbench Windows **0.1.10** 更新了界面本地化、启动页、窗口外沿圆角、过渡动画、产品图标和应用内更新体验，正式安装包已发布。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.10](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.10), published 2026-10-18T07:54:36Z | 图形安装 EXE · Portable ZIP · MSIX · 应用内更新组件 | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.9](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.9-linux-stable), published 2026-10-16T12:49:41Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 单独安装。

## Release scope / 版本验证范围

- Windows 0.1.10 来自签名标签对应的主线提交 `c3866a36163ca0ccd40d0029c0f2b5178d90c581`。主线 CI、431 项浏览器回归、完整 Windows 发布测试及 Linux 候选检查通过。
- Windows 的 15 个公开文件通过上传摘要与校验文件核对，最终安装 EXE 的图标检查通过；4 个更新组件的大小与哈希和公开清单一致。[0.1.10 发布记录](../testing/maintenance-release-0.1.10.md)。
- Linux 继续使用 0.1.9 及其[发布记录](../testing/maintenance-release-0.1.9.md)。[0.1.9 公开包云端验收](../testing/cloud-acceptance-0.1.9.md)覆盖 Windows Server 2025 Setup、Ubuntu 24.04 Setup 与 DEB 的安装生命周期、安装后 SDK 构建、导出和服务端 GameTest；该证据不转用于 0.1.10。

0.1.10 的候选包文件级安装演练与[独立升级验收](../testing/in-app-update-2026-10-17.md)保留各自源码和包哈希；本次没有重复执行最终公开包的安装态或完整玩家操作验收。Linux 保持 `formalSupportClaim=false`，完整桌面认证与云端自动验收分别记录。

随包文档与状态声明保留对应源码构建时的文本；后续发布记录不改变已发布字节。0.1.8 的签名源码标签保留为失败发布记录，没有正式二进制。[0.1.7 记录](../testing/maintenance-release-0.1.7.md)及更早证据保持原样。
