# 发布流程

Copperbench 发布未签名的 Windows 11 x64 安装包。稳定版使用 `vX.Y.Z`，预发布使用 `-preview.N` 或 `-beta.N`；Linux 由独立的 exact-binary 工作流发布。macOS、商店发布和 Authenticode 不在当前范围。

## 哪些改动走发布流程

依据 [ADR-0019](../adr/0019-agent-harness-and-asset-workbench.md) 决定 6，按改动是否改变已发布产物分级。合入 `main` 本身不改变任何已发布产物；只有发布某个 Tag 才会产生新的安装包与公开说明。

| 改动 | 流程 | 不需要 |
| --- | --- | --- |
| 源码、测试、文档、CI 与工具改动 | 普通 PR：`main` 的必需检查通过，并按[贡献指南](../../CONTRIBUTING.md)的验证表补充对应层的检查 | 发布授权（签名 Tag、`production` 审批、`release-control/` 授权）、版本号变更、`product-status.json` 的发布与门禁字段、新增 `evidence/` 文件 |
| 发布准备：版本号、发布说明、`product-status.json` 的发布与门禁字段 | 发布准备 PR；门禁提升仍须绑定到提交的证据 | — |
| 发布：签名 Tag、Windows 工作流、Linux 授权与提升 | 本文其余各节，要求不变 | — |
| 发布后同步：下载页、README、指南 | 文档 PR | 发布授权 |

## 发布节奏

按需发布：有值得交付的用户可见变化、修复或安全更新时准备发布，不按固定周期。发布前提、签名、`production` 审批和验收要求不因发布频率而放宽。

## 入库证据

- `evidence/` 只收发布绑定需要的文件，即 `product-status.json` 门禁、`release-control/` 授权、发布说明和验收报告按路径或哈希引用的结果。
- 其他验证记录，例如截图、日志、调试运行、对照实验和 Agent 交接稿，留在 Git 忽略的 `output/`。PR 说明写清验证了什么、结果、限制和本地记录的位置；游戏内验收的本地记录格式见 [AGENTS.md](../../AGENTS.md#minecraft-runtime-validation)。
- 测试代码、夹具和可重跑的验证脚本照常入库：它们是源码，不是证据。
- 已入库的证据保持原路径与字节，不因本规则移动或删除。

## 发布前提

1. `main` 的 `Build and test`、`Generate documentation` 全部通过。
2. `src/main/resources/mcreator.conf`、`product-status.json`、`ui-shell/package.json` / `package-lock.json` 与发布说明、目标 Tag 版本一致；同步 [release-notes 夹具](../../ui-core/fixtures/v1.0/release/release-notes.json)中的当前产品与第一方插件版本，以及 UI 帮助页（`userGuide.ts`）、握手身份（`CoreBridge.ts`）和相关测试期望。保留第三方依赖版本和历史支持记录。
3. Tag 是由 `.github/release-signers` 中允许签名者签署的 annotated Tag，指向干净、已推送且等于最新 `main` HEAD 的提交，格式为 `vX.Y.Z` 或 `vX.Y.Z-preview.N`。
4. Stage 9 未关闭门禁仍明确标为开发预览，不能写成正式支持。
5. GitHub `production` Environment 建议配置必需审阅者。

## 触发方式

- 推送 `v*` Tag 会执行发布流程。
- 也可手动运行 `Build Windows release`，填写一个已存在的 Tag；`publish=false` 只生成工作流产物，`publish=true` 才创建 GitHub Release 草稿。

工作流会从 Tag 重新检出源码，通过 GitHub API 解析最新 `main`，并执行 `scripts/verify-release-source.ps1`。Tag 未通过允许签名者验签、不匹配 `HEAD`/最新 `main`、版本不一致或工作树不干净时立即失败；上传草稿前会再次确认 Tag 仍等于最新 `main`。

## 产物与顺序

工作流依次执行：

1. Java、UI-Core、UI Shell 和 Javadoc 验证。
2. 通过 Gradle Wrapper 构建 EXE、ZIP、MSIX。
3. 生成 SPDX JSON SBOM、`SHA256SUMS.txt` 和 `RELEASE-METADATA.json`。
4. 为发布载荷生成 GitHub Artifact Attestation provenance。
5. 上传一份 30 天保留的 Actions artifact。
6. 需要公开发布时，先创建草稿并上传全部文件；稳定标签设置 `prerelease=false`，其它通道保持预发布。
7. `Test-ReleaseAssets.ps1` 确认二进制、哈希、元数据和 SBOM 均实际存在。
8. 只有验证通过后，才解除草稿状态。

任何步骤失败都不得手工把不完整草稿改为公开 Release。

## 用户可见说明

Release 必须说明：对应提交、Windows 11 x64、未签名、SmartScreen 提示、支持矩阵、开发预览能力和已知限制。基础模板见 [预览版说明模板](../releases/preview-template.md)。下载后可按 `SHA256SUMS.txt` 校验：

```powershell
Get-FileHash '.\Copperbench.0.1.20.Windows.64bit.zip' -Algorithm SHA256
```

上例使用 0.1.20 的公开文件名；其他版本应使用实际下载的文件名。GitHub 的 provenance 可使用 GitHub CLI 验证，具体命令以 Release 页面显示的 attestation 指引为准。

发布完成后，同步中英文 README、当前下载页、文档首页、状态页、用户指南、两种语言的快速开始与 Linux 安装说明、图形安装指南和测试索引。核对下载链接、包名、解压目录、校验哈希及平台验证范围，再运行 Markdown 链接检查。历史发布记录、截图版本和绑定哈希的验收报告保留原身份。

## Stable release gate

A stable Windows tag also requires `product.channel=stable`, an approved matching `delivery.stableRelease` with `status=ready`, and passed `stableBlocking` gates with tracked evidence. Pending gates cannot be bypassed by removing a preview suffix. The same production review, signature, latest-main, build, payload and uploaded-asset checks remain mandatory. Current platform releases and notes: [current downloads](../releases/current.md).

Linux uses `vX.Y.Z-linux-stable` for a non-prerelease release. Its ten installed candidate gates, legacy migration evidence, exact SHA-256 binding, provenance and downloaded draft comparison remain required. A stable channel does not extend distribution, architecture, accessibility or offline claims beyond recorded evidence.
