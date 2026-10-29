# 不完整输入的预检与修复

官方 **0.1.5** 已发布输入诊断改进，见[固定发布记录](https://github.com/Lotulune/Copperbench/blob/e5f91e58d7d3610ef2e3b4d66137022b6ba68f14/docs/testing/maintenance-release-0.1.5.md)；历史 0.1.4 安装包不随此改动回填。本页具体预检以实际宿主元数据为准：先读 `get_workspace_environment`，存在 `fieldContracts.inputPreflight` 时检查范围，生成器／支持矩阵在 `generator`／`versionTracks`。未暴露新增字段的旧构建仍可用 `get_version_tracks`。本批其他未发布 SDK／引导优化另见[状态页](../status.md)。

`create_mod_element` 在创建恢复点及写入元素之前检查 `elementType`、`name`、`initialValues` 的缺失和 JSON 类型，以及 `fields` 的对象形状。已确认无可用默认值的生成前提还包括：

| 元素 | 必需输入（可放在 `initialValues.fields` 中） | 类型与修复 |
| --- | --- | --- |
| `tool` | 未提供非空 `guiTexture` 时要求 `texture` | 非空字符串；先导入或选择物品纹理，再填写资源名；保留自定义模型使用 GUI 纹理的路径 |
| `specialentity` | `entityTexture`、`itemTexture` | 非空字符串；分别提供实体与物品纹理 |
| `livingentity` | `mobModelName`、`mobModelTexture` | 非空字符串；选择实体模型并提供实体纹理 |

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
      "missingFields": ["/initialValues/entityTexture", "/initialValues/itemTexture"]
    }
  }
}
```

这是诊断字段摘录。完整响应包含其余 UI-Core 字段。类型错误使用 `FIELD_TYPE_INVALID`；显式 `null`、缺省和必需字符串为空使用 `FIELD_REQUIRED`。路径是 JSON Pointer，使用嵌套输入时会保留 `/fields/`。

Agent 应读取整个 `diagnostics` 数组，按字段修正后重试。计划预检也执行相同规则；计划可能只返回第一条阻断诊断，但其 `missingFields` 保留该元素的全部已知缺失字段。更新／预览使用 `/elements/{id}/…` 路径，返回第一个阻断问题。不要解析异常堆栈来猜字段，也不要用空字符串绕过纹理要求。

预检失败不写入元素、不递增 revision；无法预检的后期失败仍通过原有事务回滚处理。对应回归检查及范围见[验证记录](../testing/first-success-2026-10-14.md)。
