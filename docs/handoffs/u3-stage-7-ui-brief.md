# U3 交接：版本轨道、加载器迁移与发布批次

- 负责人：agy / Gemini 3.7 Flash
- 核心合同：UI-Core `1.0`（已追加可选操作，未升主版本）
- 日期：2026-08-19
- 约束：只改 `ui-shell/` 与必要时的 `ui-core/fixtures` 中文词条；不得改 Java、不得直连文件系统、不得复制迁移/权限规则

## 必须新增的页面

1. **版本轨道矩阵页**（建议导航 `tracks` 或挂在工作区总览）
   - 查询：`get_version_tracks`
   - 展示四轨 × Fabric/NeoForge：`supported` / `preview` / `unavailable` / `coincides`
   - 使用 Core 返回的 `reasonCode` 与 `notes`，不要根据版本号自己推断能力
   - 当前工作区生成器来自 `currentWorkspace`

2. **加载器迁移页**
   - 预览：`preview_loader_migration`，payload `{ "targetGeneratorId" }`
   - 执行：`execute_loader_migration`，payload `{ clientMutationId, targetGeneratorId, outputName, userApproved }`
   - 未确认前不得发送 `userApproved: true`
   - 报告按 disposition 分组：`supported` / `substitute` / `lost` / `blocked` / `manual`
   - 明确文案：源工作区只读；结果写入新目录；`complete=false` 不是“源被破坏”

3. **上游工作区迁入**
   - 预览：`preview_upstream_import`
   - 执行：`import_upstream_workspace`
   - `sourceWorkspacePath` 只能来自桌面宿主以后提供的选择器；浏览器/Mock 下展示禁用态并说明“仅桌面 Full Access”
   - 权限不足时渲染 `PERMISSION_DENIED`，不要重试绕过

4. **资源包发布批次**
   - 列表：`list_publish_batches`
   - 创建：`create_publish_batch`
   - 测试客户端准备：`prepare_resource_pack_client`
   - `clientLaunched` 为 false 时只能显示“已就绪，尚未启动客户端”

## 合同操作

查询：`get_version_tracks`、`preview_loader_migration`、`preview_upstream_import`、`list_publish_batches`  
命令：`execute_loader_migration`、`import_upstream_workspace`、`create_publish_batch`、`prepare_resource_pack_client`

Schema：`ui-core/schemas/v1.0/command.schema.json`、`query.schema.json`、`event.schema.json`、`tracks.schema.json`  
夹具：`ui-core/fixtures/v1.0/tracks/version-tracks.json`

## 实现要求

- 沿用现有视觉系统、中文主语言、`t()` 词条、紧凑/标准/宽屏
- `ui-shell/src/types/contract.ts`、`mock/mockBridge.ts`、`context/WorkbenchContext.tsx` 同步新操作
- Playwright：至少覆盖轨道矩阵渲染、迁移预览分组、无确认不能执行、资源包批次空/成功态
- 现有 72 个 Playwright 不得无故失败
- 不引入 CDN、不改 Java、不把 Git 术语暴露给用户（用“版本 / 恢复点 / 副本”）

## 不要做

- 不要把 26.2 / 26.1 / 1.20.1 伪装成 Golden 已完成。状态以 Core `status` / `reasonCode` 为准。
- 不要在前端写加载器字段转换
- 不要把 Blockbench 嵌进主窗口
- 不要修改 `src/main/java`
