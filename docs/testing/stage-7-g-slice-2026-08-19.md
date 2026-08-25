# 阶段 7 G 切片验证记录（2026-08-19）

> 这是 2026-08-19 当天的证据快照。第一方切片后来已升为八生成器 `TRACK_SUPPORTED`（见 `VersionTrackCatalog`），工作区生成器插件空工程黄金编译也已在 [阶段 8 验证](./stage-8-workspace-generators-assets-2026-08-23.md) 中完成。不要把本页的 `preview` / `TRACK_GENERATE_READY` 当成当前产品状态。

- 结论：G6 通过；G2 按轨道诚实关闭（1.21.1 黄金构建已有；26.2 / 26.1 / 1.20.1 第一方生成已有）。preview 轨与迁移副本 Gradle 编译仍是可选门禁。
- 环境：Windows 11 x64，仓库内置 JBR 25。
- 复现：`pwsh -NoProfile -File .\scripts\verify-stage-7.ps1`

## 已通过

| 命令 | 结果 |
| --- | --- |
| `gradle test --tests dev.copperbench.tracks.* --tests dev.copperbench.migration.* --tests dev.copperbench.core.Stage67ApplicationServiceTest --tests dev.copperbench.assets.AssetPublishBatchServiceTest --tests dev.copperbench.assets.ResourcePackClientLoadServiceTest --tests dev.copperbench.headless.HeadlessCliTest --no-daemon` | 20/20 通过 |
| `ui-core: npm test` | 12/12 通过，含 `urn:ui-core:1.0:tracks` 夹具 |

## 目录结论

- 最新稳定版：Minecraft 26.2。`fabric-26.2` / `neoforge-26.2` 为 `preview`（`TRACK_GENERATE_READY`；黄金构建未宣称）。钉版本：Fabric Loader `0.19.3`、Fabric API `0.158.0+26.2`、NeoForge `26.2.0.63`。
- 前一个稳定版：Minecraft 26.1。`fabric-26.1.2` / `neoforge-26.1.2` 为 `preview`（`TRACK_GENERATE_READY`；黄金构建未宣称）。
- 维护轨 1.21.1：`fabric-1.21.1` / `neoforge-1.21.1` 为 `supported`。
- 维护轨 1.20.1：`fabric-1.20.1` 与 `neoforge-1.20.1` 均为 `supported`（编译 + runClient 证据）。NeoForge 钉选 NeoForged Forge `1.20.1-47.1.106`。

## 迁移与迁入

- 仅同一 Minecraft 版本的第一方 Fabric↔NeoForge 对可执行。
- 源工作区只读；结果写入兄弟目录；源树 SHA-256 在成功/失败后保持不变。
- 未知字段与加载器专属字段进入 `manual`；切片外元素进入 `blocked`。
- 1.21.1 完整迁移在复制后用目标生成器校验并生成（`rebuild.status=generated`）；源目录仍不变。Gradle 构建由 `-Dcopperbench.stage7.migrationBuild=true` 门禁。
- 上游迁入要求 `workspace.mcreator`，复制后写 `.copperbench/import/report.json`，导入命令需要 Full Access。

## 资产尾巴

- 发布批次导出确定性 ZIP 并写 `.copperbench/publish-batches/<name>.json`。
- 资源包准备写入 `run/resourcepacks` 与 `options.txt`；`prepare_resource_pack_client` 的 `clientLaunched` 固定为 false。Fabric 1.21.1 独立 `runClient` 探测已看到 ResourceManager 列出 `file/copper_ready_pack.zip`。

## 未宣称完成

- 26.2 / 26.1 / 1.20.1 黄金编译、`runClient`。
- 上游 MCreator 26.2 生成器插件（当前官方发行仍为 2026.2 / Minecraft 26.1.2）。
- 迁移副本的再次构建门禁。
- 资源包客户端加载已在 Fabric 1.21.1 宣称；其他轨道的资源包 `runClient` 仍未宣称。
- 第三方 C 级插件逐项认证。Windows 10 已不支持。已安装插件动态清单由 `list_installed_plugins` 提供。
