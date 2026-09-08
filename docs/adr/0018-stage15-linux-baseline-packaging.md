# ADR-0018：Stage 15 Linux 认证基线与首期打包形态

- 状态：已接受
- 日期：2026-09-08
- 前置决策：[ADR-0016](./0016-stage15-linux-formal-support.md)

## 背景

ADR-0016 已决定 Stage 15 正式支持 Linux x86_64，但要求开工时冻结至少一个认证发行版和首期打包组合。仓库保留了上游 Linux tarball/JBR 导出脚本，但该路径仍使用 MCreator 名称、只包含 Java 25 JBR，且没有 Copperbench 的发行布局、Java 21 sidecar 与发布供应链契约，因此只能视为开发基础，不能视为正式 Linux 产品。

## 决策

1. 首个 clean-VM 正式认证基线固定为 **Ubuntu 24.04 LTS x86_64**，桌面主路径为 **GNOME Wayland**；**GNOME on Xorg** 作为兼容会话单独记录差异。
2. 首期提供两种发行形态：
   - portable：`tar.gz`，必须可直接解压运行；
   - desktop package：`.deb`，负责稳定桌面入口、图标、安装/卸载语义。
3. 两种形态都必须自带 JBR 25 + JCEF 与 Java 21 sidecar；不得要求系统预装 Java、Gradle 或 Git 才能完成 Stage 15 DoD 的基础工作区路径。
4. portable launcher 使用 `copperbench.sh`，以脚本自身目录为根，不依赖调用者当前工作目录；Gradle wrapper 与 JDK 可执行位必须在归档中保留。
5. Linux package/release metadata 进入现有 SHA-256、SBOM、provenance 与 immutable candidate 合同。Stage 15 完成前，新增 Linux manifest/layout 只能标记为 `stage15-development`，不得提升 `product-status.json` 或公开支持声明。
6. Linux 差异必须收敛到 platform/runtime/package adapter；Core workspace、MCP、revision/recovery、generator schema 不建立 Linux 分叉。

## 后续门禁

- 在真实 Ubuntu 24.04 LTS x86_64 clean VM 上验证 bundled JDK/JCEF、工作区 create/open/reopen、Fabric/NeoForge build 与真实 `runClient`。
- 验证 Wayland 主路径并单列 Xorg 差异；任何图形环境问题必须输出稳定诊断而非误报成功。
- 在同一安装候选上完成 Desktop MCP/external-Agent 闭环，并验证 connection descriptor 权限与关闭后的凭据清理。
- `.deb`、portable tarball、SBOM/release metadata/provenance 形成同一不可变候选记录后，才允许进入 Linux Preview/Beta。
