# 阶段 1 Java Core 基础交接

- 合同版本：UI-Core `0.1`
- 对应需求：`FR-WS-04`、`FR-WS-05`、`FR-WS-06`、`FR-MOD-01`、`FR-BUILD-02`、
  `FR-BUILD-03`、`FR-BUILD-06`
- 状态：G1 已于 2026-08-17 在 Windows 11 x64 本机门禁通过

## 已实现

- `dev.copperbench.core.contract`：Command、Query、Result、Event、Handshake Java 合同和显式空值序列化。
- `dev.copperbench.core.application`：共享 CRUD/Query 应用服务、结构化诊断、权限拒绝、长任务端口和薄入口适配器。
- `dev.copperbench.core.workspace`：原子候选快照、乐观修订冲突、跨进程文件租约、产品元数据升级和未知字段保留写入。
- `ui-core/schemas/v0.1`：增加启动版本协商；不兼容时禁止降级为无类型 JSON。
- `ui-core/fixtures/v0.1/scenarios`：增加 Schema 不兼容启动场景；fixture 校验增加继承引用和循环检查。
- `dev.copperbench.core.workspace.mcreator`：真实 Workspace 投影、Procedure CRUD、修订持久化、文件快照回滚和 B 级插件事务观察器。
- `dev.copperbench.core.plugin`：A/B/C/X 分类、版本识别、Java/Swing/内部 API 静态检查和内容哈希。
- `WorkspaceFileManager` / `ModElementManager`：实际保存路径持有单写者租约并保留未知 JSON 字段。
- legacy、MCP、headless 三个独立入口适配器对相同命令返回相同 Result 与 Event。

## 不变量

1. 变更只在 `expectedRevision` 等于当前修订时进入候选快照。
2. 候选快照只有完整提交或完整丢弃两种结果；拒绝不会推进修订。
3. build/generate/validate 是运行时任务，不推进工作区内容修订。
4. UI-Core wire 序列化必须使用 `UiCore.wireGson()`，以保留 Schema 要求的显式 `null` 字段。
5. 产品字段只写入 `dev.copperbench` 命名空间，原始文档的其他字段按 JSON 结构保留。

## 后续阶段边界

- 将 `WorkspaceTaskGateway` 接到 Generator/Gradle 受管进程，补进度、日志、取消和残留进程清理。
- 阶段 3 在 Procedure 纵向链基础上扩展 Block、Item、Recipe 等元素专属字段映射。
- 阶段 2 实现 MCP 传输、认证、令牌、审计和正式恢复点历史；当前 MCP 入口只是共享应用服务边界。

验证记录：[`stage-1-g1-2026-08-17.md`](../testing/stage-1-g1-2026-08-17.md)。
