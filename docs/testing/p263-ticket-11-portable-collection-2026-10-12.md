# P263-11 — 从真实导出收集便携证据

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，未提交或合入。

[收集命令与格式](../../tools/portable-evidence/README.md)使用 Python 标准库，不依赖 Gradle、Minecraft 或网络。入口接收实际 VerifiedArtifactExporter 导出目录，仅复制 verification.json、verified-mod.jar、gametest-results.xml 的原字节。新增 manifest.json 保存相对成员、大小、摘要及原回执中的任务、输入、必要清单、已观测环境和当前/历史范围。

复制在同级临时目录中完成，源与副本在发布前再次核对，最终用一次目录重命名发布。原目录不改写，目标已存在则拒绝。缺项、复制变化或发布失败都不会留下完整交付目录；异常路径清理暂存内容。强制杀进程可能遗留明确命名的暂存目录，不将其当作完成标记。

便携 v1 加 requiredCases/1 标为 required_cases_v1；原有 M1 便携 v1 保持 minimum_only_legacy。26.3 缺必要清单、未知版本、未版本化文件或 API envelope 均拒绝。未观测的 Java/API 版本保持缺失，不从当前机器补写。只接受已知字段与便携版本标记，未审阅日志、路径、缓存、世界和凭据不随包复制。现有 strict v1.0 API 未改变。

## 实际来源与结果

复用前序已经由产品真实创建的导出目录，并直接读取其中的便携文件；未从 API 回执重建：

| 来源 | 原验收任务 | 结果与范围 |
| --- | --- | --- |
| 01 的 Fabric 1.21.1 M1 | `468c544b-23fa-4859-901c-b38e37399055` | 通过收集；原五项、minimumTests=5，旧证据范围 |
| 09 的 Fabric 26.3 | `324932fb-5b49-4b62-825d-433a677e8da8` | 通过收集；原六项固定清单 |
| 10 的 NeoForge 26.3 | `9e072fe4-77ae-44c5-adba-67e7bb88bea6` | 通过收集；原六项固定清单 |
| 09 的显式历史导出 | 以原导出目录中 taskId 为准 | 通过收集；historical_at_export，没有升级为当前输入 |

四份交付中的三类文件均逐字节等于各自源文件，manifest 的大小和 SHA-256 均重新核对。实际目录、CLI 结果和每个文件摘要见 `output/p263-11/real-collection-results.json`。

另对真实 M1 导出的三份独立副本删除回执、修改 JAR、修改 XML，CLI 均返回 exit 2，没有生成目标交付目录。原始 M1 导出保持不变。

## 检查与边界

17 项文件系统/CLI 测试通过，包括复制中分别改变三份源文件、复制中缺项、发布失败、已存在目标保留、符号链接拒绝、任务/输入/环境绑定、固定清单重定义、重复 JSON 字段、未知字段和版本、缺少版本观测及历史范围。Windows 此次实际成功创建并拒绝测试链接，无跳过。

初次检查发现当前 Windows Python 的路径 stat 与句柄 fstat 对 st_ctime 返回不同含义，导致正常文件误报。修复改为比较设备/文件身份、大小和修改时间，并继续以原字节及 SHA-256 再读复核；原失败日志保留。

Markdown 链接和 diff 检查通过。新功能仅为 Python 文件工具及文档，未修改 Java、SDK 或 UI API；没有重复运行这些层的既有检查。原始日志、四份完整交付、三份失败副本和源码快照归档于 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-11`。

收集成功只说明所选导出已按原字节完整交付。原始 XML 唯一身份和必要用例的独立离线重算、ZIP 输入边界由 12 完成；文件完整性不等于生产者认证，亦不代表接收方当前工作区或玩家客户端已经验证。
