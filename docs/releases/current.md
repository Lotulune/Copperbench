# Current downloads / 当前下载

Copperbench **0.1.7** 修复数据包默认导出格式和自定义 Gradle 归档目录的导出问题，保留首作、草稿恢复、SDK 与双语离线安装器。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.7](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.7), published 2026-10-16T05:08:48Z | 图形安装 EXE · Portable ZIP · MSIX | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.7](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.7-linux-stable), published 2026-10-16T05:29:42Z | 图形安装 Setup.tar.gz · Debian .deb · Portable .tar.gz | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 包保持 Authenticode 未签名分发；首次构建需要联网，Blockbench 单独安装。

## Release scope / 版本验证范围

- 二进制来源 A：`db8d08336c274cc37239b48e837f0bf3727c5fdc`；[主线 CI](https://github.com/Lotulune/Copperbench/actions/runs/38850069890)通过。
- Windows：[发布运行](https://github.com/Lotulune/Copperbench/actions/runs/38851412919)成功，十项公开文件已下载核对摘要，签名来源证明覆盖全部十项，ZIP 内三份发布文档与 A 一致且应用版本为 0.1.7。[资产核验](../../evidence/maintenance/2026-10-16/publication-0.1.7/windows/windows-public-verification.json)。
- Linux：主线候选 [38850069834](https://github.com/Lotulune/Copperbench/actions/runs/38850069834)／A 的八项下载字节、签名来源及包内版本检查通过。晋级来源 `bd2b733e5709bfe952eef33f30028a8d70a928bf`，[发布运行](https://github.com/Lotulune/Copperbench/actions/runs/38855720737)成功，九项公开资产摘要与冻结候选及批准授权一致；三个公开元数据文件另做下载字节比较。[资产核验](../../evidence/maintenance/2026-10-16/publication-0.1.7/linux/linux-public-verification.json)。

最终包未重跑完整安装与 Minecraft 玩家／存档验收；Linux 保持 `formalSupportClaim=false`、`fullInstalledAcceptance=not-repeated`。实际检查和来源见[发布记录](../testing/maintenance-release-0.1.7.md)。

随包文档保留 A 构建时文本；后续仓库文档更新不改变已发布字节。[0.1.6 记录](../testing/maintenance-release-0.1.6.md)及更早证据保持原样。
