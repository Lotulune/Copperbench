# 剩余完善清单

本页是**状态索引**，不是需求基线。阶段 8 收口的需求、验收和明确不做项见根目录 [PRD-NEXT.md](../PRD-NEXT.md)。排除项见 [open-decisions.md](./open-decisions.md) 与 [ADR-0015](./adr/0015-github-unsigned-gpl-fork.md)。

## 当前正在做

阶段 8 收口（[PRD-NEXT.md](../PRD-NEXT.md)）：发行包对齐、新建工作区落盘证据与三入口、八套工作区生成器插件空工程编译、资产页实接、资源包工作区进入新外壳、离线宣称与 G7 诚实状态。须一次交付，见该文件 §3 / §7。

## 源码已有、证据或安装包仍缺

| 项 | 源码 | 仍缺 |
| --- | --- | --- |
| 产品外壳「新建工作区」 | `NewWorkspaceView`、`WorkspaceCreationService`、`create_workspace`、宿主打开通道 | 成功落盘 `.mcreator` 的自动测试；MCP / headless 入口；Playwright 目前只打 mock |
| 八套工作区生成器插件 | Fabric / NeoForge 26.2、26.1.2、1.21.1、1.20.1 插件树 | 空工作区 Gradle 黄金编译未宣称 |
| 国内源 / Gradle 池 / 9.7.0 | `ChinaNetworkSetupDialog`、`GradleDistributionPool`、wrapper 钉选 | 未打进正在跑的 `Copperbench-ReleasePreview` |
| 第一方纵向切片 | 八生成器 × block/item/recipe/procedure，编译 + `runClient` | 不能替代插件空工程黄金编译 |
| 资产 | Java `AssetWorkspaceService`、MCP `list_assets` | `AssetBrowserView` 仍读 `ASSET_FIXTURES` |
| 独立资源包 | 插件与 ZIP / prepare 命令 | 新产品外壳新建流程未列出资源包生成器 |

## 两套生成器（不要混用证据）

可视化「新建工作区」列出的是**工作区生成器插件**，不是版本轨道里的第一方纵向切片。

| 加载器 | 对话框已有插件 | 空工程黄金编译 |
| --- | --- | --- |
| Fabric | 26.2、26.1.2、1.21.1、1.20.1 | 未宣称 |
| NeoForge | 26.2、26.1.2、1.21.1、1.20.1 | 未宣称 |

这些插件是从相邻轨道模板改目标版本得到的。第一方切片四轨均有编译 + `runClient` 证据，但不能替代对话框里的完整生成器插件。

## 网络与 Gradle（限制，不是待办）

- Fabric Maven 与 NeoForge 专用仓库仍走官方地址。阿里云 / BMCLAPI / 华为云 Maven 不是 Fabric 的完整代理。
- 导出时仅当构建机本地已有 9.7.0 / 8.8 才会打进 `gradle-dists`。
- 产品自身构建仍用 Gradle 9.6.0；工作区发行包共用 `%USERPROFILE%\.copperbench\gradle`。
- 官方 Stage 8 `--offline` 宣称仍以 1.21.1 为准，直到 [PRD-NEXT.md](../PRD-NEXT.md) `FR-CLOSE-07` 对齐。

## 产品外壳与模组元素（本次不扩范围）

- 新 UI / MCP 只能创建方块、物品、配方、Procedure；约 30 类迁入只读。不在本收口范围。
- 纹理绘制、声音、结构、图像/盔甲编辑器、标签/变量/语言、代码 IDE 走旧版窗口。
- 运行服务端、调试客户端明确不支持。
- Blockly 仍在旧版 Swing。

## 首发范围外（除非新 ADR）

Linux / macOS、Windows 10、Authenticode、产品网站、账号云同步、远程 MCP、内置厂商聊天。G7 客机 GUI 常驻未宣称；未补证据前不得改为 passed。
