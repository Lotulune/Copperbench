# 不完整输入的预检与修复

输入预检已包含在 0.1.5 中，旧 0.1.4 安装包不包含此能力。先读 `get_workspace_environment`：`fieldContracts.inputPreflight` 存在时可查询预检范围；活动生成器与支持矩阵分别在 `generator`、`versionTracks`。通过 `get_mod_element_field_contract` 查询具体类型的默认值和必填字段。旧构建没有新增字段时，仍可使用 `get_version_tracks`。

`create_mod_element` 在创建恢复点及写入元素之前检查 `elementType`、`name`、`initialValues` 的缺失和 JSON 类型，以及 `fields` 的对象形状。已确认无可用默认值的生成前提还包括：

| 元素 | 必需输入（可放在 `initialValues.fields` 中） | 类型与修复 |
| --- | --- | --- |
| `tool` | 无额外必填输入；保留主线默认值 | 省略纹理、模型名时使用默认值；显式传入 null、空白或错误类型仍给出诊断 |
| `specialentity` | `entityTexture` | 非空字符串；导入工作区实体纹理；省略 `itemTexture` 时保留默认船物品纹理 |
| `livingentity` | `mobModelTexture` | 非空字符串；导入工作区实体纹理；省略模型名／AI XML 时保留默认值 |

普通方块、物品等具有默认值的字段仍可省略；未知插件字段继续遵守原有保留规则。预检只证明已列出的输入条件满足，不证明资源存在、模型匹配或所有生成器规则满足，后续校验／构建仍可能给出其他诊断。

例如创建空的 `specialentity`，返回 `status: "rejected"`，`newRevision` 不变，诊断包含：

```json
{
  "code": "FIELD_REQUIRED",
  "path": "/initialValues/entityTexture",
  "message": {
    "key": "diagnostic.input_preflight",
    "args": {
      "field": "/initialValues/entityTexture",
      "expectedType": "string",
      "actualType": "missing",
      "repairHint": "Import or select an entity texture in Assets, then supply its resource name in entityTexture.",
      "missingFields": ["/initialValues/entityTexture"]
    }
  }
}
```

这是诊断字段摘录。完整响应包含其余 UI-Core 字段。类型错误使用 `FIELD_TYPE_INVALID`；必需字段在补齐共享默认值后仍缺失、为 `null` 或空白时使用 `FIELD_REQUIRED`。路径是 JSON Pointer，使用嵌套输入时会保留 `/fields/`。预检复用主线创建合同，不能把可省略的默认值改成新的必填项。

Agent 应读取整个 `diagnostics` 数组，按字段修正后重试。计划预检也执行相同规则；计划可能只返回第一条阻断诊断，但其 `missingFields` 保留该元素的全部已知缺失字段。更新／预览使用 `/elements/{id}/…` 路径，返回第一个阻断问题。不要解析异常堆栈来猜字段，也不要用空字符串绕过纹理要求。

预检失败不写入元素、不递增 revision；无法预检的后期失败仍通过原有事务回滚处理。对应回归检查及范围见[验证记录](../testing/first-success-2026-10-14.md)。
