# Linux 发布控制提案（已授权，实施中）

Run34 候选验收已完成，详见 [验收报告](./stage15-closeout-2026-09-10.md)。现有 `.github/workflows/deploy.yml` 仅构建 Windows；用户已授权新增发布 CI、合入 main 和推进签名 tag / production 审批；公开发布尚未执行。

拟新增独立的 `.github/workflows/linux-release-control.yml`，复用 `production` 环境审批与现有 SSH 签名者名单。由于主线包含新增产品修复，流程将下载重新验证后的最终冻结候选资产，Run34 保留为历史证据，验证 SHA-256、SBOM、候选身份和 GitHub provenance，不重新构建已测二进制。签名 tag 必须指向当时最新 main，且相对候选源代码只能包含明确列出的发布控制、验收文档和辅助验证改动。

候选原始 metadata 的 `development-not-certified`、`formalSupportClaim=false`、`exactBinaryPromotionEligible=false` 保持原样。正式交付资格必须由新的独立发布授权记录明确批准，绑定候选 ID、两个包的摘要、验收报告摘要和十项验收证据摘要；不能静默改写原始 metadata。

流程默认只创建 draft prerelease，重新下载并逐字节核对远端资产。只有显式选择 publish 且通过 production 审批，才公开该已核对的 draft。正式平台声明在发布控制审批时同步处理，不提前标记 Stage15 完成。

本地可审阅草稿（尚未写入 CI 路径）：

- `build/stage15-linux-hyperv/run34/linux-release-control.draft.yml`
- `build/stage15-linux-hyperv/run34/linux-candidate-authorization.draft.json`（故意保持 pending-approval / releaseEligible=false）
- `build/stage15-linux-hyperv/run34/linux-release-notes.draft.md`

2026-09-11 已获得明确授权：新增 Linux 发布流程和发布授权记录、按规则合入 main，并推进签名 tag / production 审批。tag 名称和公开发布操作尚未执行。该授权要求来自本项目 AGENTS 指令对 CI 变更的限制；本轮已有的提交/推送授权不包含 CI 配置变更。

实施状态以 [主线集成记录](./stage15-release-integration-2026-09-11.md) 为准；使用独立 Linux tag 命名，避免触发 Windows 发布。
