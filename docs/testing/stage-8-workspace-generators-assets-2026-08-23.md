# Stage 8 Workspace Generators and Assets

本记录对应 `FR-CLOSE-04` 与 `FR-CLOSE-05`，证据来自当前工作树，不替代第一方纵向切片证据。

## FR-CLOSE-04

门禁测试：

```text
gradlew.bat test --tests dev.copperbench.generator.workspace.NewWorkspaceGeneratorGoldenBuildTest -Dcopperbench.stage8.workspaceGeneratorBuild=true
```

测试为每个生成器创建真实空 `.mcreator` 工作区，复制插件 `workspacebase`，完成 Gradle 同步、基础源码生成和 `gradlew.bat build`，并检查 `build/libs` 中的非 `sources` JAR。完整 Gradle 输出写入 `build/stage8-workspace-generator-logs/`；汇总证据见 [`workspace-generator-golden-build.json`](../../evidence/stage-8/2026-08-23/workspace-generator-golden-build.json)。

| 生成器 | 结果 | Gradle 日志 |
| --- | --- | --- |
| `fabric-26.2` | `BUILD SUCCESSFUL`，JAR 存在 | `build/stage8-workspace-generator-logs/fabric-26.2.log` |
| `neoforge-26.2` | `BUILD SUCCESSFUL`，JAR 存在 | `build/stage8-workspace-generator-logs/neoforge-26.2.log` |
| `fabric-26.1.2` | `BUILD SUCCESSFUL`，JAR 存在 | `build/stage8-workspace-generator-logs/fabric-26.1.2.log` |
| `neoforge-26.1.2` | `BUILD SUCCESSFUL`，JAR 存在 | `build/stage8-workspace-generator-logs/neoforge-26.1.2.log` |
| `fabric-1.21.1` | `BUILD SUCCESSFUL`，JAR 存在 | `build/stage8-workspace-generator-logs/fabric-1.21.1.log` |
| `neoforge-1.21.1` | `BUILD SUCCESSFUL`，JAR 存在 | `build/stage8-workspace-generator-logs/neoforge-1.21.1.log` |
| `fabric-1.20.1` | `BUILD SUCCESSFUL`，JAR 存在 | `build/stage8-workspace-generator-logs/fabric-1.20.1.log` |
| `neoforge-1.20.1` | `BUILD SUCCESSFUL`，JAR 存在 | `build/stage8-workspace-generator-logs/neoforge-1.20.1.log` |

1.20.1 NeoForge 使用 Forge 47.1.106 / UserDev 7.0.165；其工作区模板对旧 Forge API 单独处理，不能与现代 NeoForge 网络 API 混用。

## FR-CLOSE-05

资产页生产路径现在发送 UI-Core `list_assets` 查询。Java `WorkspaceApplicationService` 使用当前工作区根目录创建 `AssetWorkspaceService`，返回资产描述、引用边和诊断；MCP `list_assets` 仍使用同一 `AssetWorkspaceService`。`AssetBrowserView` 不再导入 `ASSET_FIXTURES`，只把查询投影转换成显示模型。

验证：

```text
gradlew.bat test --tests dev.copperbench.core.AssetQueryProjectionTest --tests dev.copperbench.assets.*
ui-shell: npm run build
ui-shell: npm run test:e2e -- e2e/asset-browser.spec.ts
```

Java 资产查询与资产服务测试通过，UI 生产构建通过，Playwright 资产浏览器 8/8 通过。夹具仍只由 mock bridge 使用，供浏览器场景测试，不进入 `AssetBrowserView` 产品路径。

## FR-CLOSE-06

产品外壳现在从同一 `list_new_workspace_generators` 目录列出独立资源包生成器 `resourcepack-1.21.1`。`WorkspaceCreationService.create` 对该生成器复用上游 `Workspace.createWorkspace`、`WorkspaceGeneratorSetup.setupWorkspaceBase`、基础模板生成和默认图标任务，真实写入 `.mcreator`、Gradle 骨架、`src/main/pack.mcmeta` 与 `src/main/pack.png`。资源包不要求 Java 包名。

资源包工作区状态映射为 `kind=resource_pack`、`loader=resource_pack`。任务网关识别其根工程的 `build/export/export.zip` 产物，导出任务复制为工作区内请求的 `.zip`；模组生成器继续使用 `build/libs` JAR 规则。既有 `ResourcePackExportService` 仍可直接从 `src/main` 生成确定性 ZIP。

验证：

```text
gradlew.bat test --tests dev.copperbench.core.workspace.WorkspaceCreationServiceTest --tests dev.copperbench.core.workspace.mcreator.MCreatorWorkspaceStateMapperTest --tests dev.copperbench.generator.resourcepack.ResourcePackWorkspaceTaskGatewayTest --tests dev.copperbench.generator.neoforge.LoaderRoutingWorkspaceTaskGatewayTest --no-daemon
gradlew.bat test --tests dev.copperbench.assets.ResourcePackClientLoadServiceTest --tests dev.copperbench.headless.HeadlessCliTest --tests dev.copperbench.mcp.McpHttpServerTest --no-daemon
ui-shell: npm run build
ui-shell: npm run test:e2e -- e2e/new-workspace.spec.ts
```

结果：资源包创建、骨架、ZIP 导出、Core 状态投影和任务路由测试通过；三入口目录测试通过；前端构建通过；新建工作区 Playwright 12/12 通过；`ResourcePackClientLoadServiceTest` 断言 `clientLaunched=false`，证明 `prepare_resource_pack_client` 不自动启动 Minecraft。机器可读证据见 [`resource-pack-workspace.json`](../../evidence/stage-8/2026-08-23/resource-pack-workspace.json)。
