# Agent 首作：步道方块

本页适用于 MCP 或原生 SDK 操作。图形界面用户从[第一件作品](../user/first-creation.md)开始。

## 选择正确的接口

| 操作 | MCP 工具／客户端 | Native/Core |
| --- | --- | --- |
| 读取元素编辑器 | `read_mod_element` | `get_mod_element_editor` |
| 创建元素 | `create_mod_element` | `create_mod_element` |
| 查询创建字段 | `get_mod_element_field_contract` | `get_mod_element_field_contract` |

MCP 的工具名查服务端 `tools/list`；原生查询名查随包 Core schema。原始 MCP `read_mod_element` 省略 `schemaVersion` 时使用冻结的 1.0 投影；完整编辑器需显式请求 `schemaVersion: "1.1"`。原生 Python SDK 的 `workspace.query("get_mod_element_editor", elementId=...)` 默认请求 1.1，可用 `schema_version="1.0"` 显式读取旧投影。

## 创建

先建立独立的 Fabric 1.21.1 工作区，模组 ID 为 `first_creation`，Java 包名为 `com.example.firstcreation`。只读核对环境、权限和最新 revision。

通过 Agent 创建同一个元素时，先调用 `get_workspace_environment` 确认活动生成器，再读取 `get_workspace` 的最新 revision。以下是 `create_mod_element` 的具体输入；将 `expectedRevision` 替换为刚读到的整数，仅在尚未用界面创建时执行一次：

```json
{
  "elementType": "block",
  "name": "trail_block",
  "initialValues": {
    "displayName": "Trail Block",
    "fields": {
      "hardness": 3,
      "resistance": 6,
      "hasBlockItem": true
    }
  },
  "expectedRevision": 0
}
```

预期返回 `status: "committed"`，并提供 `newRevision` 和元素 ID。后续写操作使用最新 revision；不要反复发送同名创建请求。配对与权限设置见 [MCP 入门](getting-started.md)。

## 构建与玩家验收

运行构建并等待成功；通过工作区生命周期启动客户端，在客户端退出前保持 SDK 会话存活。游戏内取得、放置、保存重进和正常退出按[首作第 4 步](../user/first-creation.md#4-启动-minecraft-并确认作品)进行。构建成功不能替代游戏观察。

## 常见输入边界

- 发光字段是 `luminance`，例如 `15` 或 `{"fixedValue":15}`；`lightOpacity` 控制遮光。固定值均为 0–15 的整数。`lightValue` 不会自动转换。
- 生物需要 `mobModelTexture`（含 `.png`）；特殊实体需要 `entityTexture`（不含 `.png`）。先导入工作区实体贴图，再读取字段合同；模型、AI 和船物品纹理可保留合同提供的默认值。
- 字段错误按全部 diagnostics 修正，同名重试前先确认前一次是否提交。revision 冲突需要重新读取。

详见[输入诊断](input-diagnostics.md)和 [Python SDK](../../sdk/python/README.md)。
