# Linux 发布控制（已实施，等待发布执行）

用户已授权 Linux 发布 CI、独立授权记录、合入 main，并推进签名标签和 production 审批。实现位于 `.github/workflows/linux-release-control.yml`，复用现有 production 标签策略、审核者和 SSH 签名者名单。

最终候选 Run37（`34537005983`，源提交 `f8114839`）通过十项 clean-guest 验收和完整 PR 回归。独立授权记录绑定候选 ID、安装包、产品 JAR、验收报告和十项证据摘要。详见 [最终验收报告](./stage15-release-integration-2026-09-11.md)。Run34 保留为历史证据。

原始 metadata 的 `development-not-certified`、`formalSupportClaim=false`、`exactBinaryPromotionEligible=false` 保持原样。专用 Linux 标签避免触发 Windows 发布工作流。标签必须签名并指向最新 main；流程拒绝候选之后的产品或构建改动。

流程校验哈希、SBOM、候选身份、来源证明和验收绑定后创建草稿，下载并逐字节核对远端资产。默认 `publish=false`，公开发布要求显式 publish 和 production 审批。正式平台声明与 Stage15 完成状态将在该边界关闭后更新。
