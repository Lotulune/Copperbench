# 执行期未决事项

当前没有仍打开的执行期未决事项。关闭方式是补充 ADR、阶段记录或固定版本清单，而不是只在聊天中口头决定。

远程 MCP、自有云账户、Linux/macOS 正式支持、内置模型厂商聊天、产品网站、付费 Authenticode 不属于当前未决事项；它们已经明确排除在首个公开 GitHub 衍生版之外，后续若重启必须新增 ADR。

## 已关闭

| 事项 | 结论 | 证据 |
| --- | --- | --- |
| 首个 MCreator 稳定标签及 Fabric Generator 固定提交 | MCreator `2026.2.33518` / `361429609b772039a3eb9ab81662c25b225f1d0d`；Fabric Generator `26.1.2-2026.2-2.8` / `abfe19329126b679a26baafe5cade5a75d455528` | [`UPSTREAM.md`](../UPSTREAM.md)、[`baseline.lock.json`](../compliance/baseline.lock.json) |
| 三套 UI 方向中的最终信息架构与视觉方向 | 选定方向 A「专注工坊流」：左导航 + 中主区 + 右检查器、铜强调色、无边框标题栏。B/C 保留为历史原型，不再并行实现。 | [`information-architecture.md`](./architecture/information-architecture.md)、[`prototypes/`](../prototypes/)、现行 `ui-shell` 产品壳 |
| 被保留、迁移到新外壳或仅在旧版窗口开放的上游高级功能 | 机器可读盘点：新 UI / 旧版窗口 / 不支持 / 不适用。旧版窗口不是视觉承诺。 | [`UpstreamToolCatalog`](../src/main/java/dev/copperbench/release/UpstreamToolCatalog.java)、`get_upstream_tools` |
| 首个正式版完整 Mod Element 覆盖清单 | 第一方切片为 block / item / recipe / procedure，覆盖全部 8 个第一方生成器。迁入的上游类型只读；create/update 拒绝。 | [`ElementCoverageCatalog`](../src/main/java/dev/copperbench/release/ElementCoverageCatalog.java)、`get_element_coverage` / `headless elements` |
| 无开发工具的干净 Windows 11 安装环境 | Hyper-V 客户机静默安装/升级/卸载通过；工作区与 `.copperbench` 保留。客机 GUI 常驻未宣称。 | [`hyperv-g7-guest-checks.json`](../evidence/stage-8/2026-08-20/hyperv-g7-guest-checks.json) |
| 阶段 0 开发发行身份 | 公开名称 `Copperbench`；产品 ID `dev.copperbench.studio`（反向 DNS，不是网站）；新增 Java 命名空间 `dev.copperbench`；Publisher `Copperbench Contributors` | [`BRANDING.md`](../compliance/BRANDING.md)、[`branding-assets.lock.json`](../compliance/branding-assets.lock.json) |
| 最终公开发行品牌、域名、签名主体与商标法律复核 | 不做商业化。公开身份为 Copperbench；分发仅为 GitHub 未签名 GPL 衍生版；无域名、无商店、无 Authenticode。不是律师商标意见。G7 不因此变为 passed。 | [ADR-0015](./adr/0015-github-unsigned-gpl-fork.md)、[`BRANDING.md`](../compliance/BRANDING.md)、`ReleaseManifest` |
| 最新稳定版与前一个稳定版对应的 Minecraft 版本 | 最新稳定版 = Minecraft `26.2`（第一方纵向生成，`TRACK_GENERATE_READY`；钉版本见 Fabric meta / NeoForge Maven `26.2.0.63`）。前一个稳定版 = Minecraft `26.1`（第一方纵向生成，`TRACK_GENERATE_READY`）。1.21.1 与 1.20.1 均为维护轨且 `supported`（编译 + runClient 证据已有）。 | [`VersionTrackCatalog`](../src/main/java/dev/copperbench/tracks/VersionTrackCatalog.java)、[`stage-7-g-slice-2026-08-19.md`](./testing/stage-7-g-slice-2026-08-19.md) |

`Minecraft Mod Creator` 只保留为 PRD 工作标题。`Copperbench` 是公开产品名。G7 仍为 `in_progress`：Hyper-V 客机 GUI 常驻未宣称。
