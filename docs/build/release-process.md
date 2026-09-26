# Windows 发布流程

Copperbench 发布未签名的 Windows 11 x64 安装包。稳定版使用 `vX.Y.Z`，预发布使用 `-preview.N` 或 `-beta.N`；Linux 由独立的 exact-binary 工作流发布。macOS、商店发布和 Authenticode 不在当前范围。

## 发布前提

1. `main` 的 `Build and test`、`Generate documentation` 全部通过。
2. `product.version`、发布说明和目标 Tag 一致。
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
Get-FileHash '.\Copperbench 0.1.0 Windows 64bit.zip' -Algorithm SHA256
```

GitHub 的 provenance 可使用 GitHub CLI 验证，具体命令以 Release 页面显示的 attestation 指引为准。

## Stable release gate

A stable Windows tag also requires `product.channel=stable`, an approved matching `delivery.stableRelease` with `status=ready`, and passed `stableBlocking` gates with tracked evidence. Pending gates cannot be bypassed by removing a preview suffix. The same production review, signature, latest-main, build, payload and uploaded-asset checks remain mandatory. Release notes: [0.1.0](../releases/v0.1.0.md).

Linux uses `vX.Y.Z-linux-stable` for a non-prerelease release. Its ten installed candidate gates, legacy migration evidence, exact SHA-256 binding, provenance and downloaded draft comparison remain required. A stable channel does not extend distribution, architecture, accessibility or offline claims beyond recorded evidence.
