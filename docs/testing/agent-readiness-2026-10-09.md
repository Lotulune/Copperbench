# Agent readiness 首批实施记录 — 2026-10-09

对应[下一阶段 PRD](../roadmap/agent-readiness-prd-2026-10-09.md)。产品父基线：`2bfbb48e80dac267b2043ffcbfc795a0bdd931a3`；独立分支：`codex/agent-readiness-prd-20261009`。不修改 main、发布包或历史 gate。

以下“已实现”“本地验证”“未完成 / 未验证”是 PR99 原始提交 `436a41761d885fa5ad0d76500e2257438137d75f` 的历史记录；当前对齐状态见文末，不将旧错误命名或当时待办沿用为当前契约。

## 已实现

- `Workspace.available_field_contracts()`：一次只读查询，列出当前 Core 真正公开的契约。
- `field_contract()`：缺失契约返回 `FIELD_CONTRACT_UNAVAILABLE`，含可用名称、generator 和现有元素检查入口；畸形元数据返回 `NATIVE_INVALID_RESPONSE`。不假装已经有 item/recipe 的完整契约。
- 诊断 fallback 的简单命名占位符安全渲染；未知参数不丢失；原始 code、key、args 与 details 保留；不执行格式表达式、不递归替换、不重试写入。
- `wait_task()` 拒绝 NaN、Infinity 和非正等待参数；有效等待的行为及显式取消责任不变。
- 生成冲突显示保留原有原因与首个相关路径；控制字符清理、详情限长；原始 cause 保留。所有权、revision、逐文件指纹和回滚代码不变。
- 新增 SDK 回归 23 项、Java 展示层回归 6 项及真实工作区 adapter 保护回归 1 项。

## 本地验证

本地逐文件读取 GitHub 源码，重建文件的 Git blob SHA 与父提交一致后再改动：SDK `5af87c68f477817cca7645a3de96821235080414`；生成准备类 `72a2a4f9d34c2218938d3b76f08a5f15e1c97071`。没有取得完整可运行工程，不能把局部检查表述为全仓库构建成功。

| 检查 | 结果 | 范围 |
| --- | --- | --- |
| 新 SDK 回归在父版本上执行 | 预期失败：23 个 test methods，13 failures / 23 errors（含子用例） | red 对照；不是 36 个独立产品缺陷 |
| 新 SDK 回归在修复版上执行 | 23/23 通过，ResourceWarning 作为 error | SDK/模拟传输；不是实际 Core 进程 |
| Python 语法编译 | 通过 | 修改模块 |
| Java 诊断 helper 独立契约 | Java 21 编译并运行，14 条断言通过 | 纯展示层；不是产品 JDK 25 编译 |
| Java JUnit 与真实 adapter 测试 | 提交后由 PR CI 验证 | 本地未执行 |
| 全部现有 SDK、Java/Javadoc、UI/MCP 检查 | 提交后由 PR CI 验证 | 不继承其他提交的绿色状态 |

集成验证以本 PR **相同提交**的 required checks 和测试报告为准。若代码发生变更，之前通过的 CI 不自动覆盖新提交。

## 未完成 / 未验证

Core 的 item/recipe 完整预创建契约、结构化冲突列表、只读生成预检、Nightly job 拆分、真实 mod 正负门禁、doctor、wrapper 摘要、依赖风险修复和安装版/陌生用户任务仍是 PRD 后续范围。

未重做完整 Minecraft 客户端交互或新 JAR 玩法验收。上一轮昼夜仪结果属于其历史提交；本轮是诊断与 SDK 小步修复，不新增 Gameplay 通过声明。PR #97 的启动超时修复已在父提交中，本轮不重复归功。

## PR98 / PR99 对齐说明

本次组合 PR98 记录提交 `dedf7f3f6732a91fcc4021ef75a83d46489bfbf1`（产品代码验证提交 `886fc5ee432e6af212491a1c2a07d95b8153239e`）与 PR99 原始提交 `436a41761d885fa5ad0d76500e2257438137d75f`。权威阶段入口是 [Stage 18 PRD](../../PRD-STAGE-18.md)，配套需求见 [Agent readiness PRD](../roadmap/agent-readiness-prd-2026-10-09.md)。既有 PR 分支对齐同一组合树，不修改 main 或发布包。

- 保留 item/recipe partial 契约、类型化多路径生成冲突、拒绝不写保护、五个独立 Nightly 产品套件/红灯汇总、八轨 generator 矩阵和严格真实 Native mod 工作流；这些不再是全未实现的未来工作。
- SDK 统一为：有效映射缺少请求类型时 `NATIVE_FIELD_CONTRACT_UNAVAILABLE`，details 含 `availableTypes`、`reason=type_not_advertised`、`nextAction`，并保留 generator、检查入口与 environment；畸形元数据为 `NATIVE_INVALID_RESPONSE`，保留原始 envelope；`available_field_contracts()` 返回排序名称。上述原始记录的旧错误名不再是当前公开契约。
- 保留 PR99 的有限等待参数校验、安全诊断渲染和受限兼容展示；不能以兼容 fallback 替代 PR98 的结构化诊断或削弱源码保护。
- 上述历史测试数不相加为组合树结果，也不继承旧 CI 绿色状态。当前组合提交的验证须单独记录；本说明不声明其 CI 已通过。

完整契约/八轨适用性、完整只读预检、新拆分后的同源完整 Nightly、doctor/构建加固、指定安装候选、真实客户端玩法与陌生用户任务仍开放。[Stage 18 历史证据](./stage-18-initial-implementation-2026-10-09.md)中的服务端 GameTest 与候选渲染预检不代替客户端交互验收。

### 对齐树本地回归

- Python SDK 67/67（ResourceWarning 为错误）、UI-Core Schema 35/35、UI bridge/本地化 29/29、TypeScript SDK 12/12、scripts/tests 56/56 通过。前端生产构建、本地化门槛、product status 与 Markdown 本地链接检查通过。
- Java 安全展示辅助类和原异常类的抽取编译 smoke 在 JDK21 执行 19 条断言通过；不是完整 JDK25 产品构建或 adapter 测试。Gradle Wrapper 下载因网络不可达受阻，完整 Java/Javadoc/adapter 和 Chromium 交由对应发布提交的 CI 重新验证。
- 类型化冲突保留完整 sourcePath、reasonCode、定位 action；新增 displaySourcePath 仅用于有长度边界、控制字符安全的文字展示。带相同错误前缀的原始 IOException 仍不得提供公开路径或私有原因。
- 这些局部回归不证明同源完整 Nightly、八轨发现、安装候选或真实客户端玩法；精确最终提交与工作流终态另见 PR 的对齐验收更新。
