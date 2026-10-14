# P263-24 — 26.2 到 26.3 只读复制升级预览

日期：2026-10-13（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

新增独立 `version_copy_upgrade` version 1；只允许 negotiate / preview，不改变 Loader Migration 的同版本边界，不提供执行写入。范围、入口、指纹排除项及审计位置见[协议说明](../ai/version-copy-upgrade.md)。Native、Python/TypeScript SDK、Core MCP、HTTP MCP 和 JCEF/UI 共同使用一个计划模型；strict UI-Core 1.0 未扩展。

计划绑定精确源/目标、规则版本、源 revision、文件树指纹和规范化 planId。逐项显示原值、转换状态、语义、恢复方式与依据。内部名、注册名、元素 UUID、未知元数据和手写归属保留；未证明的字段/文件语义列为人工任务，未知类型列为不支持。预览阶段 executionAvailable=false，不会将人工任务当作转换成功。

50 个不同 Java 行为、Javadoc、102 Python SDK、20 TypeScript SDK、44 UI-Core、31 UI unit 和 UI build 通过。按 Gradle 类、方法及参数展示身份去重。新测试覆盖真实 MCreator 双加载器工程、源与世界字节保护、旧迁移边界、协商/写参数/跨加载器拒绝、缺失或链接输入、未知类型、Windows 写锁和真实 HTTP。测试中的世界文件是受控字节 fixture，不是玩家运行证据。

真实 MCreator 工程通过 UI Core 建立 item 和手写 code，显式加入未知字段与世界文件 fixture 后，经 NativeApiSession 的实际序列化流、Core MCP 适配器和 JCEF 请求得到相同计划。Native 测试直接运行真实 session / Core，不启动子产品进程或 Gradle 构建游戏工程。外部修改 item 定义后，下一次计划读取磁盘新原值并改变指纹；外部修改 code 后，正文从其实际关联文件读取。预览前后 revision、源树摘要与世界 marker 字节不变，没有目标目录。

真实 loopback MCP HTTP 以 read-only token 连续预览两次，计划相同且源树字节不变。升级操作的审计位于产品配置目录、源树之外，仍记录开始/结果并在不可写时拒绝。请求不获得写权限、预览 opt-in 或世界复制授权。

两加载器 × 中英文 × 两布局共 8 个浏览器展示场景，以及两布局的失败刷新，共 10 个场景通过。检查了原值展开、人工任务标识、请求不含写命令、无横向溢出、刷新失败移除旧计划且不重试。已检查中英文截图。浏览器采用真实 Core 计划 fixture，周边工作台为 mock，页脚诊断来自既有 mock；不是安装版或玩家截图。

验证过程中修复了三个实际边界：整树哈希只排除 Windows 强制锁住的精确 IPC 锁路径；枚举 .mcreator 时不把同名目录当文件；code 从既有工作区元数据和关联源码读取，不要求不存在的 .mod.json。另修正新测试的缺失名称/版本、HTTP workspace header，以及 JSON Schema 的严格分支声明。浏览器首轮没有展开 Tools 导航，已中止并保留失败截图/trace；按实际导航展开后全部通过。相关失败与最终通过均归档。

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-24`。包含 Core 原始计划、HTTP 回执和审计副本、校验日志、XML、浏览器截图、失败记录及源快照。本票不宣称复制执行、目标编译/验收、安装或玩家门禁通过；25/26 继续完成受限执行。
