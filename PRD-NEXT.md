# Copperbench 下一步需求：阶段 8 收口

> 状态：下一阶段需求基线（一次交付）  
> 更新日期：2026-08-22  
> 对应基线：[PRD.md](./PRD.md) v1（阶段 0–7 领域门禁）  
> 公开产品名：`Copperbench` `0.1.0`  
> 本文件范围：把**已经宣称、但源码/证据/安装包仍不成立**的首发缺口收口。不是扩元素类型，也不是新平台。

## 0. AI 阅读协议

从现在起到本文件退出条件全部满足为止：

- 普通开发任务先读**本文件**和实际源码。`PRD.md` 只作为已关闭能力的基线，不再作为剩余工作清单。
- 本文件与 `PRD.md`、`docs/remaining-work.md` 冲突时，以本文件为准；三者都与代码冲突时，以源码和测试为准。
- 不得把 ADR-0015 排除项、约 30 类只读模组元素、React Blockly、运行服务端/调试客户端塞进本次交付。
- 每个实现任务必须引用本文件需求编号，例如 `FR-CLOSE-03`。
- 关闭方式是落地实现 + 可重复证据，不是改文档把缺口写没。

## 1. 为什么还要这一步

阶段 0–7 的领域服务、第一方纵向切片、产品外壳主路径、四轨编译/`runClient` 探针、G7 自动化切片已经在源码里。阶段 8 尚未退出：G7 仍为 `in_progress`。

当前工作树还存在三类缺口，必须**同一批次**做完，否则公开预览会继续说一套、装一套、测一套：

1. **安装包落后于源码**：国内源、Gradle 进度/发行包池、八套新建工作区插件、Gradle 9.7.0/8.8 对齐还没打进正在跑的 `Copperbench-ReleasePreview`。
2. **两套生成器未对齐**：第一方切片（block / item / recipe / procedure）有编译 + `runClient` 证据；「新建工作区」用的工作区生成器插件没有空工作区 Gradle 黄金编译。
3. **三入口与实接不完整**：`create_workspace` 在 UI-Core 和产品外壳里有，MCP / headless 没有；资产页仍读 mock 夹具；新建工作区没有成功落盘 `.mcreator` 的自动测试。

本批次做完后，不懂 Java 的创作者应能在新产品外壳里：选四轨之一的 Fabric/NeoForge → 创建工作区 → 生成切片元素 → 构建 → 运行测试客户端；同一操作在 UI / MCP / headless 结果一致；安装包内容与支持矩阵一致。

## 2. 非目标（本次明确不做）

- Linux / macOS 安装包、Windows 10、Authenticode、产品网站、账号云同步、远程 MCP、内置厂商聊天（ADR-0015 / `PRD.md` §3）。
- 新 UI / MCP 扩到实体、GUI、维度等约 30 类；迁入类型继续只读。见 `ElementCoverageCatalog`。
- 把 Blockly、纹理绘制器、盔甲制作器、代码 IDE、Pack Maker、偏好设置全树迁进 React。
- 运行服务端、调试客户端、任意 Gradle 任务。
- 把 Fabric Maven / NeoForge 专用仓库改到阿里云或 BMCLAPI（不是完整代理，乱改会缺构件）。
- 宣称每一个工作区生成器插件里的模组元素类型都能生成可编译代码。

## 3. 一次交付的工作包

八个工作包必须一起验收。允许并行实现，不允许只交付其中几项就宣称阶段 8 收口完成。

| 工作包 | 需求编号 | 用户可感知结果 |
| --- | --- | --- |
| 发行包对齐 | FR-CLOSE-01 | 新的未签名 Windows 预览包含当前工作树能力 |
| 新建工作区证据 | FR-CLOSE-02 | 确认后真写 `.mcreator`，并有自动测试 |
| 三入口一致 | FR-CLOSE-03 | UI / MCP / headless 都能列出生成器并创建工作区 |
| 插件空工程编译 | FR-CLOSE-04 | 八套新建工作区插件空工程能 Gradle 出 jar |
| 资产实接 | FR-CLOSE-05 | 资产页显示当前工作区真实资产，不再用夹具冒充 |
| 资源包工作区 | FR-CLOSE-06 | 新产品外壳能创建独立资源包工作区 |
| 离线构建宣称 | FR-CLOSE-07 | 发布说明里的 `--offline` 范围与探针一致 |
| G7 与矩阵一致 | FR-CLOSE-08 | 安装包矩阵 = 源码矩阵；G7 未过的项保持诚实 |

## 4. 功能需求

### 4.1 发行包与源码对齐

- **FR-CLOSE-01**：重新导出未签名 Windows ZIP / NSIS 预览，使安装包包含：
  - 首次启动中国大陆询问（华为云 Gradle、阿里云 Maven / Plugin Portal、BMCLAPI `libraries.minecraft.net`）
  - Gradle 下载进度与发行包池
  - 安装目录 `gradle-dists` 启动预填到 `%USERPROFILE%\.copperbench\gradle`
  - 工作区生成器插件：Fabric / NeoForge 的 26.2、26.1.2、1.21.1、1.20.1
  - 26.x 与 1.21.1 工作区 Gradle 9.7.0；1.20.1 仍为 8.8
- **验收**：`BundledPluginInventory`、导出布局测试、预览包内 `plugins/` 与 `user/README.md` 与源码清单一致。干净构建机若本地没有 Gradle zip，安装包可以不带 zip，但发布说明必须写明。

### 4.2 新建工作区：落盘证据

- **FR-CLOSE-02**：`WorkspaceCreationService.create` 在校验通过且生成器已安装时，必须在建议工作区根目录下写入 `.mcreator` 与工作区骨架。不得只返回 JSON 成功。
- **验收**：
  - Java 测试覆盖成功路径（真实或受控的 `GENERATOR_CACHE`），断言工作区文件存在。
  - 现有拒绝路径（`MOD_ID_INVALID`、`UNSUPPORTED_GENERATOR`、`WORKSPACE_FOLDER_OUTSIDE_ROOT` 等）保持。
  - 产品外壳创建成功后，宿主通道校验 `.mcreator` 存在再开新窗口（已有 `JcefWorkspaceOpenBridgeTransport`，补集成证据）。
  - Playwright mock 场景保留，但不能当作落盘证据。

### 4.3 UI / MCP / headless 三入口

- **FR-CLOSE-03**：`list_new_workspace_generators` 与 `create_workspace` 必须同时出现在 UI-Core、MCP 工具表、headless 命令中，并调用同一 `WorkspaceApplicationService`。
- **约束**：`create_workspace` 仍需 `userApproved`；MCP 不得代用户确认；headless 必须显式 `--approve true`。
- **验收**：同一组合法/非法参数在三个入口产生相同诊断码；`HeadlessCli` help 列出对应命令。对应原需求 `FR-WS-01`、`FR-BUILD-06`、`FR-MCP-01`。

### 4.4 工作区生成器插件空工程黄金编译

- **FR-CLOSE-04**：对「新建工作区」列出的八个插件生成器各建一个**空工作区**（无模组元素），跑 Gradle 构建并产出 jar。
  - `fabric-26.2`、`neoforge-26.2`、`fabric-26.1.2`、`neoforge-26.1.2`、`fabric-1.21.1`、`neoforge-1.21.1`、`fabric-1.20.1`、`neoforge-1.20.1`
- **不得**用第一方纵向切片的 compile / `runClient` 探针替代本项。
- **不得**因此宣称插件树里每一种模组元素都能编译。
- **验收**：每个生成器有可重复脚本或门禁测试；失败必须带稳定诊断，不能只贴 Gradle 堆栈。对应原需求 `FR-LOAD-03`、门禁 G2 中「工作区生成器」部分。

### 4.5 资产浏览器接真实工作区

- **FR-CLOSE-05**：`AssetBrowserView` 必须通过 UI-Core 查询展示当前工作区资产（`AssetWorkspaceService` / MCP `list_assets` 同源），删除对 `ASSET_FIXTURES` 的产品路径依赖。夹具只允许留在非宿主 Playwright / 场景切换器。
- **验收**：空工作区显示空态；创建切片元素或导入资产后列表变化；路径越权仍拒绝。对应原需求 `FR-ASSET-01`、`FR-ASSET-05`。独立纹理绘制器、声音浏览器、结构编辑器可继续走旧版窗口。

### 4.6 独立资源包工作区

- **FR-CLOSE-06**：新产品外壳「新建工作区」必须能创建独立资源包工作区（沿用已捆绑的 resource-pack 生成器），不只支持八个 Java 模组生成器。
- **验收**：列出可用资源包生成器、创建后可导出 ZIP、`prepare_resource_pack_client` 行为不变（不自动启动 Minecraft）。数据包 / Bedrock addon 不在本次范围。对应原需求 `FR-ASSET-06`。

### 4.7 离线构建宣称

- **FR-CLOSE-07**：`ReleaseManifest.knownLimitations` 与发布说明中的 Gradle `--offline` 范围必须与真实探针一致。
  - 继续区分：Gradle `--offline` ≠ 操作系统断网。
  - 已有缓存预热 + `--offline` jar 的轨道，写入正式宣称；没有证据的轨道不得写进 `goldenCompileClaimed` 或 offline 列表。
  - NeoForge 1.20.1 若仍不能 `--offline`，保持限制项，不静默省略。
- **验收**：`ReleaseManifestTest` / 发布说明夹具与探针脚本结果一致。

### 4.8 G7 与支持矩阵

- **FR-CLOSE-08**：
  - 安装包内的版本轨道、插件清单、元素覆盖、上游工具去向与源码 `VersionTrackCatalog` / `BundledPluginInventory` / `ElementCoverageCatalog` / `UpstreamToolCatalog` 一致。
  - Hyper-V 客机 `copperbench.exe` 常驻：能证则证；不能证则 G7 保持 `in_progress`，禁止改状态而不补证据。
  - 操作系统级断网下的核心创作：能证则证；否则保持「仅 Gradle `--offline`」限制项。
- **验收**：`verify-stage-8.ps1` 与 `verify-unsigned-release-preview.ps1` 针对**本批次导出包**通过其已宣称检查；未宣称项不得从 `knownLimitations` 删除。

## 5. 原 PRD 中仍未闭合、纳入本次的条目

| 原编号 | 本次对应 | 说明 |
| --- | --- | --- |
| FR-WS-01 | FR-CLOSE-02/03/06 | 新外壳能选活动生成器并创建；含资源包工作区 |
| FR-BUILD-06 | FR-CLOSE-03 | 三入口同一应用服务 |
| FR-MCP-01 | FR-CLOSE-03 | MCP 覆盖创建工作区 |
| FR-LOAD-03 | FR-CLOSE-04/07 | 轨道状态与编译证据诚实 |
| FR-ASSET-01/05 | FR-CLOSE-05 | 资产浏览接真实工作区 |
| FR-ASSET-06 | FR-CLOSE-06 | 独立资源包工作区进入新外壳 |
| G2 | FR-CLOSE-04 | 工作区生成器插件空工程编译 |
| G5 | FR-CLOSE-05 | 资产页不再用夹具冒充 |
| G7 | FR-CLOSE-01/08 | 包与矩阵一致；未证项保持 in_progress |

原 `FR-MOD-02` 的纹理/语言：第一方切片生成路径已覆盖；独立编辑器本次仍走旧版窗口，不迁 React。

## 6. 明确延期（本文件退出后另开需求）

- 约 30 类上游模组元素的新 UI / MCP 创建与更新。
- Procedure / Feature 的 React Blockly。
- 纹理、声音、结构、图像/盔甲制作器、标签、变量、语言、代码 IDE、工作区设置全树迁入新外壳。
- 运行服务端、调试客户端。
- Fabric Maven / NeoForge 专用仓库国内镜像。
- 签名级 SBOM、公开 Beta 高严重度回归集、性能/长时运行专项。

## 7. 验收与退出

本文件完成的定义（须全部满足）：

1. FR-CLOSE-01～08 均有自动或脚本证据，路径写入阶段记录。
2. 新的未签名 Windows 预览已按当前工作树导出；`docs/user/README.md` 与包内说明一致。
3. 八个工作区生成器插件空工程编译通过；第一方切片证据不冒充插件黄金编译。
4. UI / MCP / headless 均可列出生成器并在确认后创建工作区。
5. 资产页在宿主模式下不依赖 `ASSET_FIXTURES`。
6. 新产品外壳可创建独立资源包工作区。
7. G7 要么补齐客机 GUI 常驻与 OS 断网证据并改状态，要么保持 `in_progress` 且限制项仍在发布说明中。
8. 不扩大 §2 非目标。

未完成不得把 `docs/remaining-work.md` 写成「当前正在做：无」，也不得把 G7 改为 passed。

## 8. 实现任务拆分（执行时按此开任务）

1. `FR-CLOSE-02` 成功落盘测试  
2. `FR-CLOSE-03` MCP + headless 新建工作区  
3. `FR-CLOSE-04` 八插件空工程 Gradle  
4. `FR-CLOSE-05` 资产页实接  
5. `FR-CLOSE-06` 资源包工作区进入新外壳  
6. `FR-CLOSE-07` 离线宣称与探针对齐  
7. `FR-CLOSE-01` + `FR-CLOSE-08` 导出预览包并跑阶段 8 / 未签名预览脚本  

任务 1–6 可并行；任务 7 必须在 1–6 合入后执行。
