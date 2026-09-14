# Blockbench M3 执行记录（2026-09-13）

范围为 [Blockbench PRD](../../PRD-BLOCKBENCH.md) 的多文件回导、恢复和元素关联，以及可跳过的首次引导。本记录区分自动测试与真实安装产品验收；整个 PRD 尚未关闭。

## 实现

- `BlockbenchImportService`：预览真实游戏导出和 `.bbmodel` 候选，校验模型/贴图/父模型引用；拒绝越界、重复目标、多个资产根中的同名资源。复核预览哈希后暂存内容与备份，记录持久导入日志。
- `WorkspaceApplicationService`：提供预览、导入、恢复和关联四个 Core/MCP 入口；预览有效期 15 分钟。导入复用现有资产校验和文件写入服务；工作区 revision、权限、恢复点及审计沿用现有设施。
- 回导失败仅恢复本批目标，保留候选；进程中断后可重开恢复。目标存在无关修改时停止，不用全工作区回滚覆盖其他工作。成功记录可在重开后核对全部文件哈希并幂等重试。
- 方块/物品通过 `modelResource` 关联导入模型。Fabric/NeoForge 生成器处理此引用；上游插件工作区的提前返回分支也补写绑定引用。模型使用独立资源名以避免自身继承，生成时保留导入贴图。
- 资产中心增加导出映射、预览、替换确认、恢复和元素关联。首次进入工作台的可选提示可以跳过，并通过原生配置保存选择。
- 导出包规则加入 Blockbench 使用说明和第三方通知；不加入 Blockbench 程序或社区插件。

## 自动验证证据

2026-09-13 已执行：

- Java 原有选定回归（环境、任务、安装配置、进程桥、JCEF、真实 HTTP MCP、Fabric/NeoForge 生成器）通过；日志 `.tmp/gradle-external/a904efa5931946a181146058888c29f9.log`。
- 扩大回归至全部 `dev.copperbench.assets.Blockbench*`、Core 旧往返、两个 JCEF 桥、MCP HTTP 和两个生成器：90 项，86 通过、4 项按平台/编辑器条件跳过，0 失败；与首个 Windows 打包同次运行成功，日志 `.tmp/gradle-external/701f5139838b43e9824523e4c969cc6c.log`。
- 新增父模型继承/覆盖、跨现有文件的循环引用和跨资产根资源冲突后，`BlockbenchImportServiceTest` 共 10 项通过；日志 `.tmp/gradle-external/c5127d1d386349c69769f6d40520039b.log`。包含真实临时磁盘写入、中途写入失败、模拟进程中断恢复、拒绝覆盖后续修改和重开后回执重试。
- `McpHttpServerTest` 通过已认证 HTTP 的预览→导入→创建元素→关联→重复关联/导入；此处服务器是 Copperbench，不是社区 Blockbench 服务。
- Fabric 生成测试验证普通路径和已存在 `.mcreator` 的插件工作区路径均保留导入模型、贴图及绑定引用；不等同于真实插件生成或游戏渲染证明。
- `npm test --prefix ui-core`：25 项通过，包含导出映射、预览 token、替换确认布尔值以及请求/响应 operation 集合一致性。
- Playwright 的安装引导、任务、回导三组用例在 1920×1080 和 1366×768 两个尺寸共 12 项通过（23.4 秒）。原生桥为测试替身，不是已安装 JCEF 产品。
- UI 生产构建通过，365/365 被引用中文键通过本地化检查。

复跑 Java 使用项目现有 `scripts/run-gradle-external.ps1` 包装器；直接从工具宿主运行 Gradle 会受到其 Java NIO 本机 socket 环境影响。

## 真实插件与安装验收准备

- Windows 11 验证虚拟机 `Copperbench-G7` 可通过 PowerShell Direct 访问；未发现常用位置中的 Blockbench 安装。
- 宿主机 Blockbench 有未保存模型，本次未操作该模型或安装插件到该会话。
- Ubuntu 验证虚拟机暂未启动：与 Windows 虚拟机并行启动时内存不足，后续串行验证；未调整虚拟机内存配置。
- 社区插件源码取自 [固定提交 b187b4b](https://github.com/jasonjgardner/blockbench-mcp-plugin/tree/b187b4b056f0efafcc573335400ecbb21ad26ecc)，`package.json` 声明版本 `1.7.0`、许可 `GPL-3.0-only`。源码归档 SHA-256 为 `22b42a9fb6e5c924d1461c9b7da861bf30c748c73ef7eca491c76b2d53df88e1`。下载至临时验证目录，尚未在编辑器内加载。
- 该提交 `server/net.ts:617` 未指定监听 host；依据 Node.js 官方 `server.listen` 文档，不能据 `localhost` 日志认定只监听回环地址。此项列入真实插件安装前检查，接入指南已注明。
- 临时目录中使用 Bun `1.3.8`、上游 `bun.lock` 执行冻结依赖安装（禁用安装脚本）并运行上游构建；产出的 `dist/mcp.js` SHA-256 为 `b97f921968701df4d20103f1e7ab85823341eb2981358392d6f7921eb6d840db`。未修改插件代码，尚未加载进 Blockbench。
- Windows 预检完成后，已正常关闭本次启动的 `Copperbench-G7`，两台验证虚拟机均回到原来的关闭状态。

## Windows 验证候选（首次启动入口补齐前）

最终 `buildInstallerWin64` 成功，耗时 3 分 50 秒；日志 `.tmp/gradle-external/ed3bec3ea89b4968ad530900bef9fd94.log`。候选仅在本地生成，未发布、未执行安装。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `Copperbench-0.1.0-blockbench-validation.exe` | 795430301 | `24a52f2890c5f8adcf266f676e3b58587b4f2c1744510e3e09101c8e126ec5d5` |
| 包内 `copperbench.jar` | 7294772 | `744dc1c1349d8eacde070f9921b3b52528df59efc2f3d46fd08901a7ba75e528` |

固定副本位于 `.tmp/blockbench-validation/candidate/`，安装包原始输出位于 `build/export/`。导出内容检查确认：三个建模服务类存在、打包 UI 含回导/绑定入口、两份 Blockbench 使用说明和 `license/THIRD_PARTY_NOTICES.md` 与源码文件逐字节一致、没有捆入 Blockbench 程序或 `mcp.js`。证据为 `.tmp/blockbench-validation/windows-export-check.json`。

构建过程中另一路修改了标题栏组件及其测试；已保留，并在源码清单中记录差异。不能把这个未提交工作树称为静止的完整源码快照，也不宣称上述自动测试覆盖了那一路的最后修改。后续两平台闭环必须按实际安装产物/JAR 哈希核对候选，不能仅以相同版本号或 Git HEAD 认定同一构建。

进入安装与插件启用前，已按用户 `AGENTS.md` 的环境变更确认要求请求授权。真实环境验收尚未开始。

继续核查发现，原可选提示仅在打开工作区后的工作台出现，尚未覆盖空白安装启动页。已补充 `BlockbenchStartupPanel` 并接入原生 `WorkspaceSelector`，允许无工作区时查看来源、选择已有编辑器及跳过提示，跳过偏好与工作台共用。上表候选不含这个后续修正，因此仅保留为先前构建证据，不能用于完整 M4 验收；修正后的候选与测试结果另行记录。

补齐后针对启动组件、持久配置、回导服务和 MCP HTTP 的 23 项回归全部通过，包含无工作区渲染不写设置、指南按钮触发回调不写配置、跳过后重开仍可进入设置。首次重跑在测试 worker 尚未开始执行用例时遭遇 Java NIO 回环连接错误；改用现有外部包装器的 `--no-daemon` 单次构建后通过，未修改全局配置。

Linux 的 Java 21 `21.0.12` 与 JBR/JCEF `25.0.2-b329.117` 已由项目固定下载任务准备。此前以旧核心 JAR 验证跨平台打包的预备尝试成功（日志 `.tmp/gradle-external/7d4239307f454003b5946213067df712.log`），但仍不含启动页修正，不作为最终候选。

## 补齐首次启动入口后的两平台候选 R2

同一次正常构建执行上述 23 项测试、`buildInstallerWin64` 和 `exportLinuxCandidate` 成功，耗时 4 分 2 秒，日志 `.tmp/gradle-external/3aa5976c225b49599024cdda286038d3.log`。本次未使用旧核心注入脚本。

| 产物 | 字节数 | SHA-256 |
| --- | ---: | --- |
| Windows `Copperbench-0.1.0-blockbench-validation.exe` | 795437542 | `b295742ec48c103f2f83369c82b71e15b169c8a8f63a6cc2b6a41c6e5e3ca835` |
| Linux `Copperbench-0.1.0-blockbench-validation-linux.tar.gz` | 539747253 | `c5a4d42f8058007a510b267e6599dd2993a9bd6914c6000ad87f895e9a29366e` |
| 两平台共同的 `copperbench.jar` | 7298720 | `539a7ec450d3b7f196d43b837c2d939b8c8a75ffc0830e90a97168456f5c8f93` |

固定副本及 `artifacts.json`、`verification.json` 位于 `.tmp/blockbench-validation/candidate-r2/`。直接读取打包文件验证：

- JAR 包含原生启动引导类，工作区选择器实际引用该类。
- Windows/Linux 导出目录共有的 74 个库和插件文件哈希一致；Linux 压缩包内核心 JAR 与固定副本一致。
- Linux 压缩包内两个 Java 可执行文件具有 ELF 标识和执行权限；主启动脚本与 Gradle 脚本有执行权限，主脚本没有重复拼接。
- 两份 Blockbench 使用说明和第三方通知与源码一致，没有捆入 Blockbench 或社区插件。

Linux 当前产物为项目支持的便携开发候选，未在 Windows 上生成 `.deb`，也未安装 WSL。以上核验不涉及启动 Linux 产品或游戏。真实验收使用 R2，不能混用前一个候选的哈希。

另外已下载 Blockbench `5.1.6` 的 Windows 便携版和 Linux `.deb` 至独立临时目录。Windows 文件通过官方发布页 SHA-256 核对；Linux 文件通过发布资产 `latest-linux.yml` 的 SHA-512 核对（99922728 字节，SHA-256 `9c5c4aa85bf2ec75e2b4db59bf966460428603f8aa378c65964542a3f84a7a59`）。没有运行这些外部软件或在编辑器内加载插件。验证虚拟机的安装授权仍待用户答复。

## 尚未满足的验收项

1. 固定版本的真实社区 MCP：工具发现、制作模型、保存项目及游戏格式导出。
2. 同一源码候选的 Windows/Ubuntu 安装产品：编辑器保持开启时用户保存，再通过产品回导与关联。
3. 实际生成/构建后的 JAR 与导出文件逐项核对，以及两平台游戏中的形状、贴图视觉证据。
4. 已安装产品中的首次引导、路径选择、重开持久性和卸载不影响独立 Blockbench 的验证。

这些项目完成前，M3 的真实插件试作和 M4 不标记通过。测试替身、仅打包成功、客户端仅启动成功均不能替代上述证据。
