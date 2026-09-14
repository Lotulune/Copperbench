# Blockbench M2：建模副本与候选执行记录

日期：2026-09-13。需求：[PRD-BLOCKBENCH.md](../../PRD-BLOCKBENCH.md) BB-02、BB-03；范围为 `java_block` 方块/物品编辑源，自动回导仍归 M3。

## 实现

- [BlockbenchModelingService](../../src/main/java/dev/copperbench/assets/BlockbenchModelingService.java)：新建/已有源资产、恢复点、持久任务记录、纹理嵌入、源哈希与保存哈希检查、候选冻结、重复请求和取消保留。编辑副本和候选不会覆盖正式资产；完成不依赖编辑器退出或 launcher PID。
- [Core 领域入口](../../src/main/java/dev/copperbench/core/application/WorkspaceApplicationService.java)和[MCP 工具](../../src/main/java/dev/copperbench/mcp/McpToolCatalog.java)：五个任务操作共用工作区权限、revision 协调；MCP 进入既有审计，内部活动记录标识 UI/MCP/原生 Actor。
- [BlockbenchConfiguration](../../src/main/java/dev/copperbench/assets/BlockbenchConfiguration.java)：用户通过本机文件选择器指定安装，保存到产品用户配置；不是系统环境变量或 Agent 配置修改。启动 JVM 覆盖值优先，其次保存路径，再次自动检测。
- [任务面板](../../ui-shell/src/components/BlockbenchTasksPanel.tsx)：新建副本、从所选模型开始、刷新磁盘保存状态、复制路径、确认保存候选和取消。安装/任务面板互斥展开，减少窄屏遮挡。
- [用户步骤及 API 说明](../user/blockbench-modeling-tasks.md)。环境查询区分 `java_block_candidate_only` 和尚未实现的自动回导。

## 验证与范围

| 检查 | 结果 |
| --- | --- |
| Java 九个相关测试类选择集 | 50 项，48 通过、2 跳过、0 失败 |
| 追加真实文件兼容测试后的 BlockbenchModelingServiceTest | 8/8 通过，含前述 7 项和新增真实保存文件测试 |
| UI-Core schema | 24/24 通过；开始必须指定单一来源，完成必须包含保存哈希 |
| Playwright 资产中心＋安装引导＋任务 UI | 44/44 通过，1920×1080 与 1366×768 |
| 候选变化提示的任务 UI 复测 | 4/4 通过 |
| 面板互斥展开及最终截图复测 | 安装引导＋任务共 8/8 通过；已检查 1366×768 候选状态截图 |
| UI 生产构建、中文检查 | 通过 |

Java 共覆盖 51 个不同用例：49 通过、2 条有条件测试跳过（真实安装 Blockbench 启停，以及 Windows 上不可执行位语义）。未把跳过项算成通过。前一轮桥接测试原本禁止所有带 `executable` 的文本，已按新增的无参数本机选择器契约更新；继续断言不接受调用方传入 executable 或任意 command。

测试重点：

- MCP 真实认证 HTTP 会话完成 begin → get → finish → 重复 finish → cancel；旧 revision 在产生任务目录前拒绝，READ_ONLY 不能创建任务；正式模型不存在、revision 不变、恢复点仅一次。
- 新模型、已有相对纹理模型、坏 JSON、缺纹理、非法几何、外部 URL、源目标冲突、同目标并发任务、同 ID 不同输入、取消保留、服务重建后恢复、未完成准备目录、候选被改动。
- 使用仓库[原生保存文件](../../src/test/resources/assets/blockbench/README.md) `signal_lantern_saved_5_1_6.bbmodel` 完成副本→候选：13 cubes、78 face bindings、1 texture、无结构诊断，原文件字节保持不变。此为真实文件 fixture 兼容测试，不是本轮真实编辑器启动证据。
- UI 用 JCEF 宿主模拟及可观察请求覆盖保存哈希过期→刷新→完成、候选变化提示和取消；浏览器预览不冒充已创建文件或已选择安装位置。

Java 选择集通过日志：本机 `.tmp/gradle-external/e65fd2900ba14875ba9a8f90b69a804d.log`；补充真实文件回归日志：`.tmp/gradle-external/1f3f46567a5e4809be7d912b69603325.log`。测试使用仓库现有外部 Gradle 入口，未改 CI 或全局环境。

## 后续验收

- 未安装/启用社区 MCP 插件，未更改真实用户模组或用户当前安装配置；没有提交、推送、发行包或部署。
- 未进行原生系统文件选择器的已安装 JCEF 手工验收；配置存储及桥接契约已自动验证。
- M2 不自动启动任务副本；用户或 Agent 用返回路径在 Blockbench 中打开。任务与编辑器进程解耦，单实例转交不会触发自动完成。
- M3 需要实现独立游戏格式输出、多文件回导、原子失败恢复、正式资产/元素关联；候选文件的存在不能代替这些步骤。
- M4 仍需同一已安装候选的 Windows/Ubuntu、真实社区插件、用户编辑保存与游戏内视觉验证。
