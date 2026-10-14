# P263-21 — NeoForge 26.3 函数内容与实际命令效果

日期：2026-10-13（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

NeoForge 开放与 [Fabric 函数](p263-ticket-20-fabric-function-2026-10-13.md)相同的六个输入名称、专用发现入口、默认例、正文/别名校验与编辑器。两份模板逐字节一致。NeoForge 宿主通过 mod event bus 和 DeferredRegister 独立注册 T13；未沿用 Fabric 的运行结果。范围见[样例说明](../../examples/camp-kit-structured/neoforge-26.3-function/README.md)，supportStage 仍为 registered。

14 个定向 Java 行为、Javadoc、100 项 Python SDK、42 项 UI-Core、31 项 UI unit 与 UI build 通过。两加载器 × 中英文 × 两种窗口尺寸的 8 个函数浏览器场景通过，检查了创建、编辑、撤销后保留草稿/禁止保存、只读命名空间和标签、保存重开及生成。已检查 NeoForge 两种语言的截图；这些是 Core fixture 驱动的浏览器外壳，不是原生安装截图。

NeoForge 编辑器 fixture 与 Core 响应逐字段一致。完整 editor strict v1.0 的既有差异仍归 27，函数专用契约未改造成通用 item/recipe schema。原八轨道函数回归已在 20 通过；21 只扩展 NeoForge 目标、模板、测试及能力表，未修改共享正文处理或旧模板。

实际 Native 运行 `output/p263-21/native/delivery-10645296946526943174`，耗时 181.373 秒。工作区 `4fcadf42-394f-4c8b-a4bd-b947d77cb038`，产品 PID 5200、14788、8928 均由句柄确认 exit 0。产品创建函数后显式导入未知字段 fixture，在新进程中把正文分数由 7 改为 11，完成生成、构建、独立宿主、导出和第三进程重开后的再次导出。九项 Native 负向保持 revision 和定义字节不变，未知字段及最终 JAR 正文保持一致。

冻结的 `camp-kit-function-v1` 只有 T13，minimumTests=1。真实 ServerFunctionManager 执行后，独立读取 `camp_probe / camp_count = 11` 和 `(8,80,8)` 的金块。真实 CommandFunction.fromLines 对未知命令和 missing_function_item 分别给出 line 1 的原始诊断，后者包含 position 8；两条负向及框架 always_pass 均不计入业务用例数。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 JAR | `56eb9a395837d5cbfeae3735132cbbe4fb59f0bc30410b5e8988c9cbe6172972` |
| 原始 XML | `1b4f9445c45c4afb3e45b38174b595aeb42886576e8f1c38b8f96ca5753f6e5c` |
| T13 清单 | `0631c25a606d553a868fed4c0e02adda1d95852f1ac9cd26e6097a3c367d02c1` |

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-21`。包含原始回执、XML/JAR、效果与解析诊断、API 依据来源、截图、检查日志和精确源快照；不包含依赖缓存、玩家世界或授权私钥。

本票不替代玩家输入、安装、Ubuntu 或完整 G2 验收；没有声明冷缓存通过。
