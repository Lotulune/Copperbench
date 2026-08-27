# MCP 接入快速开始

本文面向本机 AI 客户端和自动化集成开发者。Copperbench MCP 当前为开发预览协议，版本、Schema 和工具字段仍可能在预览版间变化。

## 安全边界

- 服务仅绑定 `127.0.0.1`，入口为 `/mcp`。
- 令牌按工作区隔离，不得提交到仓库或发送到远端日志。
- 权限档位为 Read Only、Workspace、Full Access。
- 写操作使用 `expectedRevision`；冲突必须重新读取，不能静默覆盖。
- 删除、外部发布、凭据导出和启用 Java 插件必须由用户确认。

完整规则见 [MCP 权限模型](../security/permission-model.md)。

## 推荐调用顺序

1. 初始化连接并读取服务能力。
2. 调用 `get_workspace` 获取工作区与当前 revision。
3. 使用 `list_mod_elements` 获取元素摘要。大型工作区使用 `cursor` / `limit` / `sort` / `filter` / `fields`，并持续使用响应里的 `nextCursor`，直到其为 `null`；旧 `page/pageSize` 仅保留一个预览周期兼容。
4. 对写操作先调用对应 preview 工具，检查诊断和引用影响。
5. 需要把多个内容写操作作为一个原子单元时，调用 `plan_workspace_changes`，再用 `preview_workspace_plan` 复核语义差异、权限和 revision；取得用户确认后调用 `apply_workspace_plan`。
6. 单项写操作仍以最新 `expectedRevision` 提交。
7. 运行校验或构建；长任务使用 `get_task` 查询状态和日志。
8. 修订冲突时重新读取并重新生成计划，不要自动重试覆盖。

无界工作区列表统一使用 `cursor` / `limit` / `sort` / `filter` / `fields` / `nextCursor`，单页最多 200 项。当前覆盖 `list_mod_elements`、`list_recovery_points`、`list_publish_batches`，以及指定 `registry` 后的 `list_workspace_registries`；空 payload 的桌面 UI 旧投影仍保留。Cursor 绑定数据集和查询条件，workspace revision 变化返回 `LIST_CURSOR_STALE`，数据集或查询条件变化返回 `LIST_CURSOR_INVALID`。大型工作区集成必须遍历到 `nextCursor=null`。

`plan_workspace_changes` 当前覆盖元素 create/update/delete、Procedure 更新，以及 registry create/update/delete/rename。计划记录统一 `baseRevision`、`idempotencyKey`、预分配对象 ID、语义差异、权限评估和目标状态摘要；`apply_workspace_plan` 成功时只推进一次 workspace revision，并只建立一个计划级恢复点。`planId` 是可审计的内容摘要，`planToken` 是当前 Copperbench 会话签发的 HMAC 认证令牌；客户端不得自行生成或修改两者。旧 revision 返回 `WORKSPACE_PLAN_STALE`；对已经达到同一目标状态、且持有当前会话有效 `planToken` 的同一计划重放不会再次推进 revision。Copperbench 重启后旧 `planToken` 会失效，应重新读取 workspace 并生成新计划。构建、运行、迁移、导入和外部发布等长任务/边界操作不进入这一批原子内容计划。

## 开发验证

仓库提供测试用 MCP 服务和一致性脚本：

```powershell
pwsh -NoProfile -File .\scripts\verify-mcp-conformance.ps1 `
  -OutputDirectory build\mcp-conformance-results
```

脚本要求 JDK 25、Node.js 和 npm。结果写入指定目录，CI 同样保存该报告。工具 Schema 的基线测试位于 `ui-core/tests/schema.test.mjs`。

FR-AI-02 已由 PR #14、合并后 main CI 和 `main@515c212c` 的 Nightly `32998281437` 正式关闭；`list_new_workspace_generators`、`list_installed_plugins` 等产品有界目录不进入这一分页门禁，`get_workspace_references` 属于图查询并由独立的 2,000/10,000 引用性能门禁约束。FR-AI-03 的 Workspace Plan 正在收口，稳定 TypeScript/Python SDK 和任务事件订阅仍未交付。完整能力见 [下一阶段 PRD](../../PRD-NEXT.md)，客户端仍应把当前协议视为 `0.x` 预览。
