# 上游 Swing 行为到领域命令映射

本清单固定阶段 1 的迁移边界。Swing 暂时仍可作为行为参考和旧插件 UI 宿主，但新入口不得复制这些路径里的校验与写入规则。

| 用户行为 | 当前上游入口 | 当前直接副作用 | 目标应用服务 | 阶段 1 状态 |
| --- | --- | --- | --- | --- |
| 打开工作区 | `OpenWorkspaceAction` / `Workspace.readFromFS` | 解析工作区、初始化生成器与插件 | 工作区会话打开 + `get_workbench` | 文件租约与兼容保存已接入；会话投影待纵向适配 |
| 新建元素 | `ModElementGUI.finishModCreation` | `addModElement`、保存定义、生成代码和图片 | `create_mod_element`，生成另走任务命令 | 共享规则与事务已实现；Swing 调用切换待纵向适配 |
| 保存元素 | `ModElementGUI.finishModCreation` | 更新对象、保存定义、生成代码和图片 | `update_mod_element` | 共享校验与修订冲突已实现；Swing 调用切换待纵向适配 |
| 删除元素 | `WorkspacePanel.deleteCurrentlySelectedModElement` | 恢复点、删定义/生成文件、重生成引用 | `delete_mod_element` | 事务命令已实现；Swing 调用切换待纵向适配 |
| 复制元素 | `WorkspacePanel.duplicateCurrentlySelectedModElement` | JSON 克隆、添加、生成、保存 | `create_mod_element` | 保留为行为特征样例，尚未暴露复制专用命令 |
| 验证/生成/构建 | `ActionRegistry`、`RegenerateCodeAction` | Generator/Gradle 任务和 UI 进度 | `validate_workspace` / `generate_workspace` / `build_workspace` | 任务端口和一致结果已实现；真实进程网关属于阶段 2-3 |
| 取消构建 | `CancelGradleTaskAction` | 终止 Gradle 任务 | `cancel_task` | 任务端口已实现；真实进程清理属于阶段 2-3 |

## 已识别旁路

- `ModElementGUI.finishModCreation` 同时做校验、工作区写入、定义保存、代码生成和可选构建，是首要拆分点。
- `WorkspacePanel` 的复制和删除直接组合多个可失败副作用，正式迁移前必须由同一事务/恢复点策略包裹。
- `RegenerateCodeAction` 使用并行定义保存；现在真实 `.mod.json` 保存会保留未知字段，但事件顺序和失败汇总仍应由任务网关统一。
- Java 插件事件仍可触发上游对象写入。A/B/C/X 分类决定加载路由；未迁移插件不得宣称经过新应用服务事务。

阶段 1 的 legacy/headless 契约测试使用独立入口适配器，但二者只委托同一个 `WorkspaceApplicationService`，用于阻止入口层重新实现业务规则。
