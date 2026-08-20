# UI-Core Contract v1.0

`1.0` 是阶段 3 冻结、供 JCEF 产品外壳接入的稳定合同。`0.1` 目录继续保留并纳入回归验证，但 Java Core 与当前 UI 默认只协商 `1.0`。

## 目录

- `schemas/v1.0/`：冻结的命令、查询、结果、事件、握手和公共投影 Schema。
- `fixtures/v1.0/scenarios/`：当前产品外壳使用的自包含模拟场景。
- `schemas/v0.1/`、`fixtures/v0.1/`：阶段 1-2 兼容基线。

## 不变量

- Command 总是携带 `workspaceId` 与 `expectedRevision`。
- Query 不产生写入、生成、构建或外部进程副作用。
- Result 总是携带当前/新修订号与结构化诊断。
- Event 只描述已发生事实，不可作为命令重放。
- UI 只能使用 `capabilities`、`permission` 和 `diagnostics` 的 core 判定，不能自行复制业务规则。
- 所有路径字段都是领域 JSON Pointer 或不透明标识，不是本机文件路径。

## 验证

```powershell
cd ui-core
npm install
npm test
```

验证器默认编译 `v1.0` JSON Schema 并校验全部 `v1.0` 场景；测试同时回归 `v0.1`。Mock bridge 按 `initialMessages` 初始化，再按 `timeline.afterMs` 回放后续消息。

## 版本策略

- `0.x`：阶段 1-2 的只读兼容基线。
- `1.x`：新增可选字段或新增 operation/event，不改变既有语义。
- `2.x`：删除字段、收紧枚举或改变既有语义。

UI 启动时必须协商支持版本；不兼容时显示结构化启动错误，不能退回任意 JSON。
