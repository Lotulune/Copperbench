# Ticket 26 — NeoForge 26.2 → 26.3 受限复制升级

日期：2026-10-13（JST）。第 26 张票已完成本地实现、验证和代码自查；未提交、合入或发布。

## 结果与边界

`version_copy_upgrade` v2 新增 NeoForge 同加载器路线，规则标识为 `neoforge-26.2-to-26.3-function-v1`。两加载器分别绑定精确源/目标及插件摘要；跨加载器和其他版本仍拒绝。v1 继续只读，原 Loader Migration 的适用范围不扩大。规则、授权、失败恢复与 SDK 用法见[版本复制升级文档](../ai/version-copy-upgrade.md)。

范围仍为有原始生成归属且字节与原模板一致的未锁定 function 工程。名称、元素 UUID、注册名、未知属性与原始定义保留；新副本使用独立 workspace UUID/revision 0。未证明字段、命令、手写 Java、外部改写文件和自定义 IDE 配置仍进入阻断的人工任务，不自动推断转换语义。

NeoForge 生命周期生成的 `Client.launch` / `Server.launch` 含源 build/run 路径，计划明确排除这两个源配置，目标由自身 Gradle 初始化重新生成。目标可以有新建的空 run 目录，但不能继承源设置、服务器列表或世界。v2 源指纹改为相对路径与固定长度单文件 SHA-256 编码，消除原始二进制内容与文件边界的拼接歧义；旧计划必须重新预览。冻结 v1 指纹没有改动。

## 实际 Native / SDK 闭环

最终证据目录：`output/p263-26/native/delivery-4490523183731301049`。Python 驱动耗时 **336.359 秒**，四个真实产品进程 PID 6672, 27036, 17808, 37132，均 exit 0；完整 Gradle 任务耗时 6 分 21 秒。

- 源 UUID：`6b04bd0e-0f8a-4306-a4d2-ecc658e8032c`；目标 UUID：`97a6f4c1-6a09-4ee3-b4e0-ab090150e288`；保留的 function 元素 UUID：`de8f80f8-c0f0-4a57-a18e-5baf88151fde`。
- 26.2 源由真实生命周期创建，Native 创建 function、生成并构建；v2 将其复制到独立 26.3 工程。
- 复制前后 **122 个源文件**的路径/SHA-256 字典完全一致，覆盖旧运行目录和世界字节夹具，仅排除 Git 与精确写锁。世界是明确标记的边界夹具，不代表玩家世界运行验收。
- 新目标 run 为空；目标重新生成的两个 IDE 配置均不含源路径。证据为 `runtime-boundary.json` 与两个工作区的 `.eclipse/configurations`。
- 缺少预览选择、缺 build 授权、授权目录不符、旧 revision、已有目标五项真实拒绝通过。复制完成后，源 26.2 工程再次构建成功。
- 目标新进程重开后 UUID 独立、revision 0；原始定义字节、名称/注册名及未知属性保留，随后真实构建通过。
- 独立宿主以实际打包 JAR 执行固定清单 `camp-kit-function-v1` 的 T13；实际读取 `camp_probe / camp_count = 11` 和 `(8,80,8)` 的 `minecraft:gold_block`。框架自检另计，业务执行数与 minimum 均为 1。
- 无效命令和缺失引用的真实第 1 行诊断另存，不增加业务用例数量。首次导出与再次重开后的导出均为 `passed_current_input`。
- JAR SHA-256：`9bd072d182439e3cf542cdf753e75493974c21113b7bed2dc15b03fe4a1ac4ad`。
- 原始 XML SHA-256：`719d3a971591dfd0db03ddd2e7c222c6f34490679134fc6f0fbfcf0dcf25fbe7`。

## 检查和保留的失败历史

最终 20 项 Java 行为回归通过：双加载器参数化执行边界 18 项、源 IDE/自定义配置边界 1 项、二进制指纹反例 1 项。参数化用例按类、方法和参数去重，记录于 `output/p263-26/java-verification.json`，原始 XML 在 `java-final`。Javadoc 与 UI build 阶段通过；47 项 UI-Core、31 项 UI unit、26 项 Chromium 场景通过，中英文截图已目视检查。7 份实际 Native v2 响应通过严格 schema 校验；没有放宽冻结 v1.0 schema。

两加载器均检查：旧指纹/旧 revision、已有目录、越界、伪造确认或缺权限、源活动任务、缺失生成归属、磁盘与编辑器字段分歧、未证明命令、失败目标状态、静默元数据损坏与源/世界保留。第一轮 Native 被源 IDE 配置阻断；明确排除并在目标重新生成后，第二轮复制完成，但验收脚本把新建空 run 目录误判为复制世界。检查真实目录后，脚本改为验证目标独立且完全为空，并核验目标 IDE 无源路径；最终第三轮完整闭环通过。三轮回执与日志分别保留，未将失败改写为成功。

源码差异自查包含加载器身份、计划/回执、目录与字节边界、UI 显式确认和严格契约；未发现未解决的本票问题。归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-26`。安装候选、Ubuntu 云端与真实玩家客户端仍由后续 tickets 独立验证，本票不扩大发布支持声明。
