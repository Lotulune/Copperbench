# Agent readiness 首批实施记录 — 2026-10-09

对应[下一阶段 PRD](../roadmap/agent-readiness-prd-2026-10-09.md)。产品父基线：`2bfbb48e80dac267b2043ffcbfc795a0bdd931a3`；独立分支：`codex/agent-readiness-prd-20261009`。不修改 main、发布包或历史 gate。

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
