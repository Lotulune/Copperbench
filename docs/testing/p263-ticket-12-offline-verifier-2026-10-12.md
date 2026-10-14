# P263-12 — 独立离线证据目录与 ZIP 核验

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，未提交或合入。

单文件 [copperbench_evidence.py](../../tools/portable-evidence/copperbench_evidence.py) 的 `verify` 入口使用 Python 标准库读取目录或 ZIP，不启动 Java、Gradle、Minecraft、SDK，不解压 ZIP，不写成功文件。[使用说明](../../tools/portable-evidence/README.md)列出格式、退出码和固定资源限额。

核验重算三份原始文件的大小与摘要，交叉检查 manifest、原始便携回执、任务/输入/环境/必要清单绑定，再直接解析原始 XML。业务身份使用未截断的 JUnit 二元组或完整 GameTest 资源 ID。跨 suite/重试/结构类重复不能增加覆盖；失败优先，框架项和 skipped 不计入业务执行数；minimumTests、每项必要用例和汇总数量都必须满足。

## 实际离线消费

将 11 冻结归档中的 M1、Fabric 26.3、NeoForge 26.3 当前输入证据及 Fabric 历史输入证据，复制到新的 `output/p263-12/real-final/clean`。该目录另有本票单独复制的脚本和四份 ZIP；脚本没有从工程导入模块。

每次使用独立 Python 进程及 `-I`，工作目录为该干净目录，PATH 和 JAVA_HOME 为空，GRADLE_USER_HOME 指向不存在的目录。实际 Python 为 3.13，网络调用未在实现中使用；没有以修改防火墙或机器网络状态来模拟断网。

四份目录和四份 ZIP 共 **8 次通过**，均返回 `integrity_verified`。M1 保持原五项及 minimum_only_legacy；26.3 两路线各六项必要业务用例；历史输入保留 historical_at_export。`recipientWorkspaceChecked=false` 与 `producerAuthenticated=false` 没有被成功状态覆盖。正向输入及复制的脚本在运行后保持原字节，未出现解压目录或 Gradle 缓存。

最终实际执行的独立脚本 SHA-256：`3b87adb0baddfe9e85ae536ff67411907fcf161015af5282952f95ae414ace48`。每次 PID、退出码、输入路径、完整结果和文件范围检查见 `output/p263-12/real-final/real-verification-results.json`。

## 独立拒绝检查

由真实 NeoForge 证据复制得到的 **15 份 CLI 反例**分别启动独立进程，均 exit 2：

- JAR、XML、便携回执字节篡改。
- 同步更新原始 XML、回执和成员摘要后，六行重复业务身份仍拒绝；用额外无关用例替代 T12 仍拒绝。
- 同步改大 passed、acceptanceExecuted 及 coverage 后，原始数量复核仍拒绝。
- 外层 manifest 输入身份变化、未知版本，以及重新计算摘要后的固定清单重定义均拒绝。
- 缺文件、ZIP 上级路径、绝对路径、归一化重复成员、多余成员和截断 ZIP 均拒绝。

第一次真实反例构造调用 ZIP 的 `write` 接口，Windows 盘符被该接口自动去除，得到的实际 ZIP 只有正常相对成员，因此核验通过。已保留这个构造失败记录，改用 `writestr` 保留原始成员名，并在执行前断言 ZIP 的实际成员名。修正后绝对路径反例明确拒绝。它不是核验器曾接受真实绝对路径的证据。

## 共享语义与文件边界

41 项 Python 测试通过，包含 17 项收集回归和 24 项独立消费检查。另有 [26 组共享身份向量](../../tools/portable-evidence/identity-vectors.json)同时通过 Python 与 Java 的 [动态测试](../../src/test/java/dev/copperbench/generator/PortableEvidenceIdentityVectorsTest.java)，覆盖完整长名称、类名二元组、参数化身份、框架/跳过/失败优先级、GameTest 资源重复、必要缺项及 Unicode 清单排序和摘要。向量明确标识为合成测试数据，不称为 Minecraft 运行证据。

输入边界检查包括路径穿越、绝对路径、重复归一化路径、NUL 截断名称、文件系统链接和 Windows junction/reparse 标志、ZIP 链接与特殊文件、未知版本、CRC/头部/截断损坏、成员数量、文件字节、XML/JSON 深度、10000 项上限及压缩展开限制。ZIP 中央目录在创建 ZipFile 成员对象前先按数量和字节限制检查；没有落盘解压路径。DTD/实体声明包括 UTF-16 编码形式均拒绝。当前 Windows 的真实符号链接用例执行通过，无跳过。

Markdown 链接及 diff 检查通过。只新增 Python 工具、测试向量和 Java 消费一致性测试；未修改 Java 公共 API、UI-Core 或 SDK，未重复无关层的既有检查。

## 结论边界与归档

离线结果证明本包内部字节、声明和原始用例语义一致。它保留“导出时为当前输入”的声明，不声称核对接收方此刻的工作区；原始含路径 frozen 文件未打包，frozenSha256 作为生产者引用保留。所有回执未签名，完全一致地重写全部证据可以制造另一份声明，因此完整性不是生产者认证。

稳定证据目录为 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-12`，保留初次反例构造错误、修正及最终程序的各自结果、四份实际交付、ZIP、负向输入、测试 XML、日志及源快照。未将本票用作玩家、安装或 Ubuntu 验收。
