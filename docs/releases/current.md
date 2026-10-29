# Current downloads / 当前下载

Checked on **2026-10-24 (Asia/Tokyo)** against the official GitHub Releases API, published asset lists and release records. This page describes published packages; the local checkout's version configuration is separate.

Windows 与 Linux 当前稳定版均为 **0.1.18**。本版加入静态模型与元素预览、与简洁创作共享会话的专业工作台 Agent，并修复 Blockbench 打开响应和保存同步。两平台均提供应用内更新组件。

**English:** 0.1.18 adds static model/element previews, the shared Agent workflow in the professional workbench, and Blockbench opening/synchronization fixes. Both platforms also ship in-app update components.

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.18](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.18), published 2026-10-23T14:15:18Z | Graphical EXE · Portable ZIP · MSIX | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64 | [0.1.18](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.18-linux-stable), published 2026-10-23T14:32:49Z | Graphical `Setup.tar.gz` · Debian `.deb` · Portable `.tar.gz` | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 安装包未做 Authenticode 签名；首次构建需要联网。Blockbench 和创作 CLI 单独安装与配置，Python 工作台需要 Python 3.11 或更新版本。

## Current release scope / 当前发布范围

Windows 源码标签 `v0.1.18` 对应 `28b912e94b7bf271e91bafeaef7a02daf1abde98`。源码回归、Windows JCEF／Codex／Blockbench 实测、正式包构建与 15 项公开资产核验分别记录在[固定版本的发布记录](https://github.com/Lotulune/Copperbench/blob/6e4f6f47718d0c2f5cd7ef47ac6ace5e4f4ac5d2/docs/testing/maintenance-release-0.1.18.md)中；安装、升级与卸载另有该记录链接的正式包验收。

Linux 从冻结候选提升发布，不重新构建；包内容、权限、启动、安装器渲染、JCEF 与 Fabric／NeoForge X11 渲染及公开字节的核验范围见 [Linux 0.1.18 Release](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.18-linux-stable) 和随包 `LINUX-RELEASE-AUTHORIZATION.json`。该发布不扩大完整 GNOME／Wayland 安装、升级、卸载、真实 CLI 账号或玩家操作认证，`formalSupportClaim=false`。

模型预览显示静态几何和默认姿态，不模拟游戏动画或完整光照。源码、正式包与游戏内验收保留各自范围。当前检出的 `product-status.json` 和版本配置仍为本地 0.1.4 基线；README 的新截图来自带本地改动的源码预览，不代表 0.1.18 安装包截图。详见[状态页](../status.md)和[截图来源](../../assets/screenshots/README.md)。

## Historical: 0.1.5 / 2026-10-15

The following snapshot was checked on 2026-10-15. Its publication facts and candidate boundaries are retained for that date; it is not the current download recommendation.

下载版已有／源码新增／预览能力／未验证范围的一页入口：[当前状态与发布能力](../status.md)。首次使用：[第一件作品](../user/first-creation.md)。

Windows 与 Linux **0.1.5** 均已发布，包含输入诊断、首作教程、草稿／数字输入和创建默认值修复、编辑器／生成器修复，以及双语离线安装器。官方来源与具体范围见[0.1.5 发布记录](https://github.com/Lotulune/Copperbench/blob/e5f91e58d7d3610ef2e3b4d66137022b6ba68f14/docs/testing/maintenance-release-0.1.5.md)。

| Platform / 平台 | Stable release / 稳定版 | Packages / 格式 | Installation / 安装 |
| --- | --- | --- | --- |
| Windows 11 x64 | [0.1.5](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.5), published 2026-10-14T13:35:33Z | Graphical EXE · Portable ZIP · MSIX | [简体中文](../user/getting-started.md) · [English](../user/getting-started.en.md) |
| Ubuntu 24.04 LTS x86_64, GNOME Wayland/Xorg | [0.1.5](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.5-linux-stable), published 2026-10-14T13:48:26Z | Graphical `Setup.tar.gz` · Debian `.deb` · Portable `.tar.gz` | [简体中文](../user/linux-installation.md) · [English](../user/linux-installation.en.md) |

请使用同一 Release 的校验文件：Windows 为 `SHA256SUMS.txt`，Linux 为 `linux-candidate-sha256.txt`。Windows 安装包未做 Authenticode 签名；首次构建需要联网，Blockbench 需单独安装。其他 Linux 发行版和架构尚未验证。

### Release scope / 版本验证范围

Windows Release 指向源码标签 `7c9bc21`；Linux 以其候选元数据、来源证明与发布授权标识实际字节。官方记录包含 Fabric 1.21.1 开发客户端的光照／透光、剪羊毛、刷怪蛋、普通船及同世界存档重开证据；另外三种船变体是生成／编译范围。**最终 0.1.5 二进制未重新完成完整安装与全量游戏复演，Linux 完整 GNOME Wayland/Xorg 安装会话也未重新验收。** 详细身份与结果以[固定提交的发布记录](https://github.com/Lotulune/Copperbench/blob/e5f91e58d7d3610ef2e3b4d66137022b6ba68f14/docs/testing/maintenance-release-0.1.5.md)为准。

This checkout is based on `b1a69d244ab7337608cc302826ed55d4a707f37f` with local product configuration and `product-status.json` still at 0.1.4, plus uncommitted optimization changes. Those files remain local baseline records; they do not override the official 0.1.5 publication. The new native guide/delivery controls, SDK helpers and contributor tooling are unreleased candidate changes identified by their final source/artifact hashes, not features claimed for the published 0.1.5 bytes.

本地配置、授权／元数据门禁不因更新下载说明而改版本。本批候选的身份和验收要另绑定最终源码／产物 hash；新增文档或源码不代表已经发布。

## Historical 0.1.4 scope / 历史 0.1.4 范围

此前公开包为 [Windows v0.1.4](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.4) 与 [Linux v0.1.4-linux-stable](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.4-linux-stable)。以下记录保留原身份，不是当前下载版本：

- [Windows 0.1.4 说明](v0.1.4.md)：最终源码 `aabd70bc` 的[必需 CI](https://github.com/Lotulune/Copperbench/actions/runs/37391113372)与[文档 CI](https://github.com/Lotulune/Copperbench/actions/runs/37391168449)通过。另有开发环境原生 JCEF 和浏览器专项验证；未重做新安装包的完整桌面或全量 Minecraft 游戏验收。[发布工作流](https://github.com/Lotulune/Copperbench/actions/runs/37392359625)结果：success；公开资产核验：[10 项资产的哈希和来源证明](../../evidence/maintenance/2026-10-06/publication-0.1.4/windows-public-verification.json)。详见[发布记录](../testing/product-shell-release-0.1.4.md)。
- [Linux 0.1.4 说明](v0.1.4-linux.md)：与 Windows 0.1.4 使用相同产品源码，固定候选通过包结构、隔离启动、JCEF X11 和 Fabric/NeoForge 1.21.1 渲染预检；按本次明确授权发布，未重做完整 GNOME 安装验收。[发布工作流](https://github.com/Lotulune/Copperbench/actions/runs/37433909734)及[7 项公开资产核验](../../evidence/maintenance/2026-10-06/publication-0.1.4/linux/linux-public-verification.json)通过，详见[发布记录](../testing/product-shell-linux-publication-0.1.4.md)。

0.1.4 历史问题：资产预览失败后切换语言，已有错误提示可能保留原语言；这条旧说明不自动成为 0.1.5 的未修复问题声明。

[0.1.2 发布记录](../testing/maintenance-release-0.1.2.md)和[Linux 安装验收](../testing/maintenance-012-linux-installed-2026-09-28.md)保留原始源码、二进制和验证范围，不代表 0.1.5 或本批优化候选重新通过了相同验收。
