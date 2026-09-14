# 连接 Blockbench 与社区 MCP

Blockbench 是独立的可选建模工具。Copperbench 不捆绑或自动安装它；普通模组开发不要求先完成这项设置。首次启动的提示可以选择“稍后再说”，之后仍可在资产中心和“AI 与 MCP”页进入连接引导。

尚未创建工作区时，启动页提供“建模工具设置 · 可选”入口，可查看官方来源或选择已有安装。“稍后设置”与工作台共用同一份偏好；跳过提示后设置入口仍保留，不影响创建或打开工作区。

## 手工模型编辑

1. 在资产中心展开“连接 Blockbench”，点击“重新检测安装”。
2. 未安装时，复制 [Blockbench 官方下载地址](https://www.blockbench.net/)，在浏览器中下载适合系统的桌面版。完成安装后重新检测。
3. 对已导入并索引的 `.bbmodel` 选择“在 Blockbench 打开”。现有手工流程在编辑器退出后处理已保存文件变化。

非标准安装位置可点击“选择安装位置”，在系统文件选择器中选择 `Blockbench.exe`、官方便携启动器（如 `Blockbench_5.1.6_portable.exe`）、`blockbench` 或 Blockbench AppImage。不要选择尚未运行安装的安装包。位置保存在 Copperbench 用户配置目录的 `blockbench.json`，取消选择不会改变原设置。产品会在打开模型时重新检查安装，无需重开工作区。

解析优先级：启动 JVM 的 `copperbench.blockbench.executable` 属性 → 用户保存的位置 → 平台默认位置。已保存的程序丢失时应重新选择，避免悄悄启动另一份安装。高级 JVM 参数 `-Dcopperbench.blockbench.executable=完整路径` 并非直接传给任意 EXE 启动器都生效；通常优先使用产品内选择器。

## Agent 自动建模连接

1. 在 Blockbench 的插件菜单中，按照 [社区 MCP 项目安装说明](https://github.com/jasonjgardner/blockbench-mcp-plugin) 安装并启用插件。该插件由社区维护，并非 Blockbench 官方 MCP。
2. 保持桌面 Blockbench 运行，核对插件设置的服务端口和路径。项目默认地址是 `http://localhost:3000/bb-mcp`；如已修改，以插件实际设置为准。
3. 在 Copperbench 的资产中心或“AI 与 MCP”页展开连接引导，输入地址并点击“测试 MCP 连接”。`localhost` 会规范化为 `127.0.0.1`。
4. 将这个地址添加到 Agent 的 MCP 连接配置。Copperbench MCP 和 Blockbench MCP 是两个独立服务；不要将 Copperbench 的 Bearer token 填到 Blockbench 服务。

本页测试仅建立临时 MCP 会话、握手并读取第一页工具清单，随后关闭会话；不执行建模工具，不传模型、工作区路径或 Copperbench 凭据，不安装插件，不修改 Agent 配置。

安装前还应核对插件的实际监听地址。已检查的社区 `1.7.0` [固定提交](https://github.com/jasonjgardner/blockbench-mcp-plugin/blob/b187b4b056f0efafcc573335400ecbb21ad26ecc/server/net.ts#L617) 使用未指定 host 的 `listen(port)`；按 [Node.js 文档](https://nodejs.org/api/net.html#serverlistenport-host-backlog-callback)，这可监听所有网络接口。Copperbench 仅向本机探测，不会替社区插件收紧监听范围；不要把日志中的 `localhost` 当作只对本机开放的证明。这项源码检查不代表该版本已通过 Copperbench 的真实插件验收。

“握手成功，工具可发现”只代表该本机端点通过协议发现。它不认证服务器身份，不证明某个具体工具可用、Agent 已配置成功或模型已进入游戏。当前代码已提供建模副本、候选完成、多文件回导与元素关联；真实插件与 Windows/Ubuntu 安装产品的游戏视觉验收仍待完成。

## Agent 查询

调用 Copperbench MCP 的 `get_blockbench_environment`：

```json
{}
```

默认只检查编辑器，不访问 MCP 服务。显式测试连接：

```json
{"probeMcp": true, "endpoint": "http://127.0.0.1:3000/bb-mcp"}
```

返回 Core query envelope，`data.editor` 是安装状态，`data.mcp` 是本次探测结果，`data.managedModelingTasksAvailable` 报告当前工作区是否具备任务所需的根目录和恢复服务。`modelingTaskScope=java_block_export_import` 表示 Java 方块/物品导出文件的回导范围；`automaticModelImportAvailable` 按工作区是否具备根目录与恢复服务报告可用性，不表示 Copperbench 会调用社区插件代为导出。只读 MCP 身份可以查询；工作区 revision 不变，MCP 查询仍记录既有审计日志。

| 状态 | 下一步 |
| --- | --- |
| `not_checked` | 用户或 Agent 显式发起连接测试 |
| `unreachable` | 打开 Blockbench，确认社区插件启动且端口正确 |
| `timeout` | 检查插件是否响应，稍后重试 |
| `busy` | 已有连接检测在运行，稍后重试 |
| `authentication_required` | 服务要求认证；在 Agent 中配置对应服务凭据，本页暂不提供凭据输入 |
| `protocol_error` | 检查该地址是否为 MCP 端点、插件版本和服务响应 |
| `no_tools` | 服务未声明工具或返回空工具清单，检查插件 |
| `tools_available` | Agent 可继续配置此端点；仍需实际工具和模型验收 |

只支持带显式端口和路径的本机 HTTP 地址，不支持远端、URL 内凭据、查询参数或重定向。端点输入只用于本次检测，不写入系统或 Agent 配置。修改地址会清除上次结果。

## 许可证与独立安装

建模副本及候选的操作步骤见 [建模任务](./blockbench-modeling-tasks.md)。

Blockbench 和此社区插件均采用 GPLv3，分别保留其项目身份。当前只链接官方来源，未把外部安装包或插件打入产品。未来捆绑或修改后分发需核查对应版本的许可证、版权、修改记录和完整对应源码。

用户独立安装的 Blockbench、其配置和模型不应随 Copperbench 卸载被删除。原创模型不会仅因使用 Blockbench 自动成为 GPL 软件；导入的第三方素材仍按各自许可使用。
