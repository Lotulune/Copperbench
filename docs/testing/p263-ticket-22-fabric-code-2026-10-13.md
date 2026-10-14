# P263-22 — Fabric 26.3 多文件手写代码

日期：2026-10-13（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

Fabric 的 code 使用既有专用契约和手写持久化路径，开放六个输入名称及可编辑的空默认例。编辑器隐藏只读指纹，仅在源码保存时附带草稿的原始指纹；metadata-only 不重发源码或指纹。多文件、路径所有权和接管授权保持原边界。范围见[样例说明](../../examples/camp-kit-structured/fabric-26.3-code/README.md)，supportStage 仍为 registered。

35 个不同 Java 行为、Javadoc、100 Python SDK、42 UI-Core、31 UI unit 和 UI build 通过。Java 包含新 code 3 项、PreviewAccess 6 项、CodeElementPersistence 9 项及原八生成器冷/热两组的 Stage17 17 项；按类、方法、参数展示身份去重，没有把两组同名参数折叠。中英文 × 两布局的保存场景，以及两布局的过期源码拒绝，共 6 个浏览器场景通过。已检查截图，code/codeFiles 可编辑且不暴露指纹输入，拒绝后保留草稿且不自动重试。修正了手写 code 错误的重新生成提示。截图是 Core fixture 驱动的浏览器外壳，不是安装版。

Native 最终运行 `output/p263-22/native/delivery-14732661771143997184`，耗时 123.338 秒。工作区 `7deaa0cd-b8c9-4fa7-a9c2-7d16c440b532`，三个产品 PID 42040, 14924, 43292 均由句柄确认 exit 0。产品创建主 Java 和两个辅助文件；外部修改辅助值为 41 后，metadata-only 保留其 CRLF 字节。旧 revision、旧指纹、所有权冲突、非法类型/路径和未授权接管扩展均拒绝，冲突没有留下孤儿主文件。

故意注入 `missing_p263_symbol` 后，真实编译失败返回 JAVA_COMPILE_ERROR，并定位 `/src/main/java/dev/copperbench/campkit/parts/BundleMath.java` 第 5 行。关闭产品后外部添加中文注释和 CRLF；新进程读回实际磁盘内容，再通过 API 显式修复辅助文件。重新生成、构建、宿主 T12、导出、第三进程重开/再次生成和再次导出全部通过，手写三文件按原始字节比较不变。

固定 `camp-kit-code-v1` 只有 T12，minimumTests=1。宿主对最终 JAR 反射调用 bundles，独立断言九个边界值、负数拒绝及 externalStamp=41。编译负向和框架 always_pass 均不计入业务数。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 JAR | `2af896cd69460bfbaecc491cde60c6f7e08397be8e7447f453ea5242f829501f` |
| 原始 XML | `4177c7d2c634b6a334fe91a3ae3fd181da80e1d6a36862cb366fcc7dd33a9504` |
| T12 清单 | `907576949f1b9f3b2e29d1cdcac277975788e9003a36c35f8f60eababa4f4dad` |

前两次 Native 失败完整保留：第一次 harness 把实际 WORKSPACE_REVISION_CONFLICT 写成 REVISION_CONFLICT；第二次 Python 文本写入转 CRLF 而预期仍是 LF。修复脚本后采用显式 CRLF 与字节比较，未放宽产品拒绝规则。浏览器首次拒绝场景定位器匹配两处同文案，改为定位错误摘要后通过。

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-22`。包含全部 Native 尝试、编译诊断、XML/JAR、实际效果、检查日志、截图和精确源快照；不包含缓存、玩家世界或授权私钥。完整 editor strict v1.0 的既有差异仍归 27，本票不替代玩家、安装、Ubuntu 或完整 G2 验收。
