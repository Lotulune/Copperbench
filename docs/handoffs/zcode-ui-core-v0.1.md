# zcode UI-Core v0.1 交接

- 负责人：zcodeglm5.3（UI）/ 核心团队（合同）
- 对应：`NFR-UI-01` 至 `NFR-UI-10`、`FR-WS-04`、`FR-WS-05`、`FR-BUILD-02`、`FR-BUILD-03`
- 合同状态：预稳定 `0.1`（阶段 1 Schema v0）
- 工件：[`ui-core/`](../../ui-core/README.md)

## zcode 可立即使用的边界

UI 可以只依赖 fixtures 完成工作台、元素列表、元素编辑和任务反馈原型，不需要 MCreator 源码、Java 内部类或本机文件路径。

| UI 需要 | Query/Result/Event |
| --- | --- |
| 工作区标题、活动生成器、锁、权限、连接和计数 | `get_workbench` |
| 元素列表、过滤、状态与诊断计数 | `list_mod_elements` |
| 字段值、控件提示、只读字段与加载器差异 | `get_mod_element_editor` |
| 构建/生成/测试进度、日志和取消 | `build_workspace` / `get_task` / `task_*` |
| 写入成功、校验拒绝、权限拒绝、并发冲突 | `command_result` |
| Core/JCEF 状态恢复 | `connectivity_changed` / `bridge_recovery_required` |

## 信息架构约束

第一轮原型围绕四个相邻任务面组织：

1. **工作区总览**：当前生成器、健康状态、最近元素和进行中的任务。
2. **元素**：扫描、筛选、创建并进入元素编辑器。
3. **构建与测试**：校验、生成、构建、日志、取消和最终状态。
4. **全局状态**：权限、离线、锁、修订冲突和 bridge 恢复。

资产、历史、AI 权限管理和插件页面属于后续合同扩展；可以在信息架构中预留入口，但不要伪造可用业务数据。三套视觉/交互方向必须共用相同 scenario 数据，这样评审比较的是布局与交互，而不是不同业务假设。

## Mock adapter 规则

1. 读取目标场景的 `extendsScenarioId`；非空时先应用基场景。
2. 按文件顺序交付 `initialMessages`。
3. 从场景激活时刻起，按 `timeline.afterMs` 交付后续消息。
4. `loading-workbench` 只有 Query，没有 Result；UI 不得把“请求已发出”显示为成功。
5. Event `sequence` 用于去重和排序；`revision` 用于丢弃旧投影。
6. Command Result 与 Event 可能接近同时到达，UI reducer 必须幂等。

## 状态与可访问性

| Scenario | 预期 |
| --- | --- |
| `ready` | 正常工作台和元素列表 |
| `empty-workspace` | 直接提供创建元素主操作 |
| `loading-workbench` | 保留稳定布局，不显示虚假成功 |
| `validation-failed` | 将错误放在对应字段附近，并通过 live region 宣告 |
| `permission-denied` | 解释当前/所需权限，给出可执行下一步 |
| `revision-conflict` | 阻止覆盖，要求查看最新修改 |
| `partial-capability` | 保留不可编辑值，明确加载器差异 |
| `offline` | 本地可用功能继续工作，只标记网络受限 |
| `build-running` | 显示进度、阶段、日志入口和取消 |
| `external-process-exited` | 显示退出事实、日志入口和恢复建议 |
| `bridge-recovery` | 以最后提交修订恢复，不声称未提交请求已保存 |

错误不能只使用颜色表达。字段诊断使用 `path` 关联控件，顶层错误使用 `role="alert"` 或等价 live region，并在阻断对话框打开时把焦点移到 `expectedUi.focusTarget`。任务进度用非打断式 live region，避免每条日志抢焦点。

## 前端状态建议

- 以 `{ workspaceId, revision }` 标识投影快照。
- 频繁变化的 task/event 不放入承载主题、语言等全局设置的 React Context。
- 过滤列表、诊断计数等派生数据在 selector/render 阶段计算，不用 Effect 复制一份可漂移状态。
- 所有命令先生成稳定 `requestId`/`clientMutationId`，收到 `command_result` 后再更新提交状态。

## 明确不做

- mock 不读取真实工作区文件。
- UI 不从 `loader + minecraftVersion` 推导能力。
- UI 不解析 Gradle/Java 异常文本决定交互。
- UI 不使用任意 bridge 方法、任意路径或任意命令执行。
- `0.1` 不是阶段 4 的最终 bridge；阶段 3 冻结 `1.0` 后再正式接入。
