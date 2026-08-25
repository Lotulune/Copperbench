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
3. 使用 `list_mod_elements` 获取元素摘要。
4. 对写操作先调用对应 preview 工具，检查诊断和引用影响。
5. 取得用户确认后，以最新 `expectedRevision` 提交。
6. 运行校验或构建；长任务使用 `get_task` 查询状态和日志。
7. 修订冲突时重新读取并重新生成计划，不要自动重试覆盖。

`list_mod_elements` 当前单次最多返回前 200 项，Cursor 分页属于下一阶段需求；大型工作区集成不能假定一次调用已返回全部元素。

## 开发验证

仓库提供测试用 MCP 服务和一致性脚本：

```powershell
pwsh -NoProfile -File .\scripts\verify-mcp-conformance.ps1 `
  -OutputDirectory build\mcp-conformance-results
```

脚本要求 JDK 25、Node.js 和 npm。结果写入指定目录，CI 同样保存该报告。工具 Schema 的基线测试位于 `ui-core/tests/schema.test.mjs`。

当前仓库尚未交付稳定 TypeScript/Python SDK、Cursor 分页、批量原子计划和任务事件订阅；这些能力在 [下一阶段 PRD](../../PRD-NEXT.md) 中排期，客户端应把当前协议视为 `0.x` 预览。
