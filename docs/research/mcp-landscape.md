# MCreator 开发相关 MCP 调研

- 调研日期：2026-08-16
- 结论状态：已核查仓库说明与关键实现文件

## 结论

[modpotato/MCreatorMCP](https://github.com/modpotato/MCreatorMCP) 是真实可运行的 MCreator MCP 集成，可作为概念验证和实现参考，但成熟度不足以原样成为本产品的自动化边界。

调研时该仓库仅声明兼容 MCreator 2025.2。实际工具服务注册了 8 个工具：工作区构建、信息读取、代码再生成、元素列表、元素创建、元素删除、客户端启动和服务端启动。README 描述的能力面比已提交实现更宽，未发现可详细编辑元素字段的已实现工具。

仓库还存在许可声明冲突：README 声称 GPL-3.0，已提交的 `LICENSE` 内容则是 MIT。复用任何代码前必须先确认作者意图和具体文件的许可来源。

HTTP 传输监听 `localhost`，但检查到的实现允许通配 CORS，且变更、构建和运行操作没有认证、权限档位或审批机制。这不满足正式 AI 控制面的安全要求。

## 互补项目

- [Blockbench MCP](https://github.com/jasonjgardner/blockbench-mcp-plugin)：适合模型、纹理、UV 与动画自动化；成熟度明显高于 MCreatorMCP。
- [Minecraft Dev MCP](https://github.com/MCDxAI/minecraft-dev-mcp)：聚焦反编译、映射、源码搜索、版本差异，以及 Mixin、Access Widener、Access Transformer 验证。
- [mcmodding-mcp](https://github.com/OGMatrix/mcmodding-mcp)：提供 Fabric 与 NeoForge 文档和示例检索，不负责写入 MCreator 工作区。

## 建议

建设由本产品维护的第一方 MCP 服务。仅在许可审查后参考或选择性移植 MCreatorMCP 代码，并使用官方 MCP SDK 或通过一致性测试的实现。

工具按读取、提案、变更、构建、运行和发布风险分类。变更操作必须支持结构化预览、工作区恢复点、模式校验、操作日志与回滚。API 应表达领域操作，而不是暴露无限制文件编辑。

Blockbench MCP 与 Minecraft Dev MCP 应作为互补服务连接，避免在 MCreator 桥接层重复实现专业资产编辑和 Minecraft 源码分析。

## 关键实现依据

- [MCPToolsService.java](https://github.com/modpotato/MCreatorMCP/blob/master/src/main/java/net/mcreator/MCreatorMCP/MCPToolsService.java)
- [McpServer.java](https://github.com/modpotato/MCreatorMCP/blob/master/src/main/java/net/mcreator/MCreatorMCP/mcp/McpServer.java)
- [McpHttpTransport.java](https://github.com/modpotato/MCreatorMCP/blob/master/src/main/java/net/mcreator/MCreatorMCP/mcp/McpHttpTransport.java)
- [plugin.json](https://github.com/modpotato/MCreatorMCP/blob/master/src/main/resources/plugin.json)
