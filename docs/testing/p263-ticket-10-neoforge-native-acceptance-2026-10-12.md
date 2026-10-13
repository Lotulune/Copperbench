# P263-10 — NeoForge 26.3 原生修复、六项验收与同 JAR 导出

日期：2026-10-12（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 实现与固定范围

[NeoForge 原生 Camp Kit](../../examples/camp-kit-native/README.md)通过事件总线注册 item、block 和测试函数；原生入口模板提供 `IEventBus`。独立宿主只编译测试源码，加载构建产出的 mod JAR。六项测试预期使用独立字面值，原始 XML 身份由六个 `test_instance` 数据文件产生，没有事后改名。

固定清单仍为 `camp-kit-native-v1`，SHA-256 为 `8c0ebf3b57be5fa982ef8cf8ea6675966996115754601cd82b3aeb8ac707bfe9`，minimumTests=6。T01/T02/T03/T04/T11/T12 覆盖注册与堆叠、组件序列化、正确合成数量、错误原料拒绝、真实 BlockItem 放置及轴状态、修复后的手写方法。独立宿主的空结构使用 data version 5023、8×8×8 和空方块列表。

域层真实构建及独立宿主通过，目录为 `output/p263-10/backend-5ddfc22c-0fa6-4395-92f1-9293ffc81535`。环境来自 04/08 的固定 NeoForge 26.3 路线，没有沿用旧版本元素模板。

## Native 验收结果

[Java 启动器](../../src/test/java/dev/copperbench/headless/Native263AcceptanceMain.java)和 [Python Native 编排](../../sdk/python/native_263_acceptance.py)现在接收精确 generatorId，默认仍为 Fabric。NeoForge 成功运行目录为 `output/p263-10/native/delivery-5994940566247724803`。

流程耗时 358.208 秒，完成 18 个受管任务步骤、12 项预期拒绝。产品进程 PID 9052、40556、42060 分别确认 exit 0；后两次确为新的进程，工作区身份均为 `f851e708-0181-4629-b197-57eed353d00a`。25 份预览扩展响应通过 schema 验证。

过程包括初始构建、经 SDK 写入真实未定义符号、带源码位置的编译失败、受保护修复、六项验收、选定同一 JAR 导出、关闭重开、读取持久任务及重新导出。手写归属与恢复内容在新进程中检查。

最终验收任务为 `9e072fe4-77ae-44c5-adba-67e7bb88bea6`。原始 XML 共 7 项通过，`minecraft:always_pass` 是框架项，业务执行数为 6。最终导出 `7eca5937-425a-4225-ae3d-0e67212a5aa0` 和重连导出 `4e8cd8b1-084f-4046-aaa6-d448225d5599` 均指向这一次验收。

| 身份 | SHA-256 |
| --- | --- |
| 受测及两次导出 JAR | `5a0e5ce63ac2514a65204e7741be14ba31fee82e0cccc06dadf724125713e485` |
| 原始 XML | `251edf5e3be17fa93066c82ae6b550eb632769e3c7a8514dfb4226f5d629b149` |
| 最终验收输入 | `0ecf845a8f6eabe338697e46a4a219c1b8bd11c51166391cde8ed0261f9ee8fd` |
| 实际便携 verification.json | `79686f21ef9712e3277734110fde2a9310981b785d2d918cd3b0908bbc3474f7` |

JAR、XML 和便携文件从真实导出目录复制，保留原字节。临时授权只写入隔离配置目录，两次运行均在 finally 撤销；没有在本票验证桌面授权 UI。

## 拒绝边界与重开修复

- 未选择预览、旧 revision、外部源文件 SHA 冲突均拒绝。
- SDK 等待超时后显式取消，任务终态为 cancelled。
- 修改宿主 JAR 或 XML 后，即使允许历史导出仍拒绝；随后恢复原字节。
- 六行相同业务身份且匹配文件摘要的损坏历史 fixture，在当前及历史导出时都因 `GAMETEST_REPORT_INVALID` 被拒绝。该 fixture 明确作为反例保存。
- 暂时移出 T12 数据文件，真实独立宿主只执行 5 项业务测试，固定清单未改。返回 `GAMETEST_REQUIRED_CASES_MISSING`，缺项明确为 `copperbench_camp_kit:t12`，导出拒绝。恢复后新验收六项全部通过。
- 源输入改变后，普通导出拒绝；显式历史导出保留原受测 JAR 并标记 `passed_historical_input`。最终通过来自新的当前输入验收。

首次完整运行 `delivery-515940907960461399` 在首轮六项验收及导出后，第二个产品进程重开失败。只读重开探针复现了工作区文件原子替换错误；当时的错误消息未提供具体占用者，不能据此断言某个后台线程持有文件。

源码检查发现 `loadOrCreate` 对已迁移且未改变的产品元数据仍重新写盘。[JSON store](../../src/main/java/dev/copperbench/core/workspace/UnknownFieldPreservingJsonStore.java)现在跳过完全相同 namespace 的替换。缺失 namespace 仍创建，有改变的 revision 仍正常写入。回归验证原始字节、修改时间、未知字段及已有 UUID 保持；无需申请新 UUID。失败的原工作区随后由 PID 16784 成功重开，完整新流程也通过。原失败、探针和修复后记录均保留，未改写为成功。

## 验证与证据

本票合并不同类名/方法名的实际执行，65 项 Java 测试通过；另有 2 项构建/服务器 opt-in 检查未在本票运行，不计入通过数。真实六项宿主已独立执行，常规检查中的对应 skip 不覆盖其实际结果。98 项 Python SDK、41 项 UI-Core、Javadoc、Markdown 链接和保留 CRLF 的 diff 检查通过。未修改 TypeScript SDK 或 UI 实现，因此未重复其既有检查。

稳定归档位于 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-10`，包含源码快照、原始测试结果、失败与成功回执、宿主输入、JAR/XML 与摘要。排除 Gradle 缓存、玩家世界和签名私钥。使用已有缓存，不作冷缓存声明。

双加载器现在允许九项已经有证据的 Native 操作，NeoForge 新增 prepare_game_tests、run_gametest、export_workspace。公开 supportStage 仍为 registered；结构化元素、客户端、安装候选和 Ubuntu 继续由后续票单独验收。
