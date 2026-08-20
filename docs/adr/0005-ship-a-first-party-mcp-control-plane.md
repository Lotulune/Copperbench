# ADR-0005：将第一方 MCP 控制面作为正式功能

- 状态：已接受
- 日期：2026-08-16

## 决定

产品内置第一方 MCP 服务，把 AI 操作与验证作为正式、受测试、受版本管理的产品能力，而不是实验性插件。

MCP 对外暴露领域操作，例如 `create_mod_element`、`update_mod_element`、`validate_loader_target`、`build_workspace` 和 `run_client`，不把任意文件写入作为主要 API。

产品提供三档权限：`Read Only`（只读）、`Workspace`（工作区）和 `Full Access`（完全访问/YOLO）。内部实现必须把文件系统、执行和网络的技术边界与审批策略分开建模；审批策略不能扩大权限边界。

删除工作区、导出凭据和向外部平台发布属于受保护操作，即使在 `Full Access` 下也必须明确确认。所有变更操作都必须具备结构化校验、操作日志和恢复点；YOLO 只能减少普通批准提示，不能关闭审计、校验或回滚。

首期服务仅监听 `localhost`，每个工作区会话使用独立的短期令牌。首期以 MCP 为 AI 集成面，兼容外部 AI 客户端，不内置模型厂商绑定的聊天面板。

交互式 MCP 首期作为桌面 JVM 内的模块运行，由桌面应用持有唯一工作区写锁。正式版同时提供复用相同领域服务的无界面入口，覆盖 `build`、`validate` 和 `export`；无界面入口不复制一套工作区写入逻辑。

## 原因

现有 [MCreatorMCP](https://github.com/modpotato/MCreatorMCP) 证明了方向可行，但实现面、许可声明一致性和安全控制不足以直接作为正式控制面。第一方边界才能与工作区事务、生成器验证和产品版本共同演进。

## 后果

- MCP API 需要版本化、兼容策略、契约测试和安全测试。
- 远程或局域网访问需要单独威胁建模和后续 ADR，不能通过配置项偶然开启。
- Blockbench 与源码/映射查询能力可通过互补 MCP 集成，不在本服务重复实现。
- 内置 AI 任务面板可在后续复用同一领域服务，但不是第一阶段依赖。

## 依据

- [OpenAI Codex 权限模式](https://learn.chatgpt.com/docs/permission-modes)
- [OpenAI Codex 权限配置文件](https://learn.chatgpt.com/docs/permissions)
- [MCP 调研](../research/mcp-landscape.md)
