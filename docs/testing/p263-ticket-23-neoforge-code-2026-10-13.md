# P263-23 — NeoForge 26.3 多文件手写代码

日期：2026-10-13（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

NeoForge 开放与 [Fabric code](p263-ticket-22-fabric-code-2026-10-13.md) 相同的六个输入名称、空默认例和专用契约。模板相同；NeoForge 宿主通过 mod event bus / DeferredRegister 注册 T12，独立消费该路线的最终 JAR。范围见[样例说明](../../examples/camp-kit-structured/neoforge-26.3-code/README.md)，supportStage 仍为 registered。

12 个不同 Java 行为、Javadoc、100 Python SDK、42 UI-Core、31 UI unit 和 UI build 通过。两加载器 × 两语言 × 两窗口的 8 个保存场景，以及两加载器 × 两窗口的 4 个源码冲突场景，共 12 个浏览器场景通过。已检查 NeoForge 中英文截图；它们是 Core fixture 驱动的浏览器外壳。fixture 与真实 Core 投影一致，完整 editor strict v1.0 的既有差异仍归 27。

首次 Gradle 在测试前收到 daemon stop，未计为测试执行；随后 6 项新双加载器 code 检查全部通过，2 项旧能力表断言因 NeoForge 从四类扩为五类而失效。更新断言并保留 loottable 未开放检查后，6 项 PreviewAccess 通过。按最终行为去重计为 12；各次日志和 XML 保留。原八轨道源码保护已在 22 通过，23 没有改动共享源码写入行为。

Native 运行 `output/p263-23/native/delivery-7970018617734806617`，耗时 190.714 秒。工作区 `2e3a81ca-5fe2-4906-8ce8-5313a1b60180`，产品 PID 3352, 43920, 40936 均由句柄确认 exit 0。创建三文件、外部辅助值改为 41、metadata-only 保留、旧指纹/旧 revision/所有权冲突写前拒绝、真实编译故障、显式修复、重开/生成、T12 和两次验证导出通过。三份手写源文件逐字节保留，包含 CRLF 与中文注释；不放宽源码接管门禁。

固定 `camp-kit-code-v1` 只有 T12，minimumTests=1。独立宿主验证九组固定边界值、负数抛 IllegalArgumentException 及 externalStamp=41。故障实际定位 BundleMath.java 第 5 行的 missing_p263_symbol。负向与框架项不增加业务用例数。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 JAR | `74a85b2c08c8950f181785040ccd1d23c0ec35111a59a45e47527ef295234607` |
| 原始 XML | `e89dd73bf25e534c9044b33967e2b4d70fdaf3e9879de1be578a3fa5ed041e96` |
| T12 清单 | `907576949f1b9f3b2e29d1cdcac277975788e9003a36c35f8f60eababa4f4dad` |

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-23`。包含原始回执、编译失败诊断、XML/JAR、实际效果、检查日志、截图与源快照。本票不替代玩家、安装、Ubuntu 或完整 G2 验收。
