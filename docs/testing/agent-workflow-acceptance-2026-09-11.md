# Agent 开发闭环：实现与验收

日期：2026-09-11。基线提交：`090b1a9d2343fd21e3367a4b8d24e6babbbf3c23`。

本次在已有 `stage15-linux` 工作目录完成三项改动。验收执行时改动尚未提交；机器汇总中的 `uncommittedImplementation` 记录该次验收状态。后续 Git 提交不代表已经发布或替换已安装的 Copperbench。

## 已完成的能力

| 改动 | 实际行为 |
| --- | --- |
| 真实工作区隔离 | Server、Datagen、GameTest 复制真实源码、资源、入口和构建配置；保存文件清单及 SHA-256。复制期间变化会中止；原生文件变化也会使数据生成预览失效。 |
| 任务授权 | 本机用户一次确认目录、操作和期限；CLI/MCP 后续使用授权 ID。支持持久化、到期、撤销，保留连接权限、revision 和恢复点约束。 |
| 产品内自动验收 | `prepare_game_tests` 提供版本模板；`run_gametest` 构建真实快照中的 JAR 并自动装配独立宿主，返回用例、失败原因、输入和产物哈希。CLI 支持增量 JSONL，桌面展示报告。 |

操作入口和配置见[任务授权与自动验收](../ai/task-authorization-and-acceptance.md)。

## 寻路铃的实际结果

本次将之前独立宿主里的寻路铃行为测试放入模组工程，通过正式产品入口 `net.mcreator.Launcher headless ... run-gametest --stream true` 完成验收，未手工装配运行宿主。

- 2 个验收用例通过，0 失败、0 跳过：一个模板加载检查，一个包含多条玩法断言的服务端行为场景。
- 行为场景覆盖注册、配方、坐标保存、物品序列化、其他数据保留、方向/距离/高度、冷却、旁观者、玩家隔离和维度不符处理。
- 被测 JAR SHA-256：`5b622e704a4db3a33ef15e3de966f88fc1c0fb940bc6a5894ff171e61bd33de4`，与上一轮构建产物一致。
- 快照包含 41 个输入文件；生成阶段没有改变这些输入的字节。三个原生实现文件在原工程中保持不变。
- 最终报告 `sourceCurrentAtCompletion=true`，即验收结束时仍对应当前工作区内容。
- 标准输出每一行均为合法 JSON；初始化、进度、增量日志和最终结果均可由 agent 消费。

证据：[机器汇总](../../evidence/agent-workflow/2026-09-11/verification-summary.json)、[寻路铃 XML](../../evidence/agent-workflow/2026-09-11/wayfinder-gametest.xml)、[输入清单](../../evidence/agent-workflow/2026-09-11/wayfinder-source-manifest.json)。原始日志保存在本机 `build/agent-workflow-verification/`。

## 验证范围

| 检查 | 结果 |
| --- | --- |
| Java 核心回归 | 58 个不同用例通过，无跳过；包含授权、真实恢复、MCP HTTP、bootstrap、快照和验收失败路径。最终验收专项另跑 18 项，其中包含重复回归及一个覆盖两个 NeoForge 版本的运行测试，数量不相加。 |
| UI-Core Schema | 22/22 |
| Playwright | 12/12，覆盖 1920×1080、1366×768，授权审查/撤销、独立 EULA 选择、任务报告和 TypeScript SDK；检查深浅主题截图。 |
| Python SDK | 4/4 |
| UI 构建及中文词条 | 通过 |
| Minecraft/Loader 模板编译 | Fabric、NeoForge 的 1.20.1、1.21.1、26.1.2、26.2，8 项均完成真实编译。 |
| NeoForge 实际运行 | 1.21.1 和 26.2 均通过独立宿主加载被测 JAR，各执行 1 个用户验收用例。 |
| Markdown 链接、AI eval 清单、Git 空白检查 | 通过 |

NeoForge 运行证据：[1.21.1 XML](../../evidence/agent-workflow/2026-09-11/neoforge-1.21.1-gametest.xml)、[26.2 XML](../../evidence/agent-workflow/2026-09-11/neoforge-26.2-gametest.xml)。这两个版本的运行测试验证宿主与报告链路，仅作模组加载冒烟；寻路铃才是本次的玩法场景。

## 验收中补上的问题

NeoForge 26.2 会额外执行 Minecraft 的 `minecraft:always_pass` 自检。产品现在保留真实总数，并以 `frameworkTests`、`acceptanceExecuted` 和逐用例 `scope` 区分它；它不能满足最低验收数量。只有内置自检的报告会失败。零用例、仅跳过、失败/异常、缺少/过期/畸形报告、XML 外部实体、过深嵌套、非零进程退出、运行后 JAR 变化和运行期间源码变化均有回归覆盖。

旧版 NeoForge 首次准备 Minecraft 依赖时超过了当时 12 分钟的编译检查时限。在同一宿主保留已完成依赖输出、使用 2 GiB Gradle 堆重试后编译成功。产品旧版宿主现采用 2 GiB，编译检查时限与产品任务的 20 分钟一致。这不是一次全新冷缓存重跑。

## 保留的边界

- Windows 深目录中的 wrapper 启动限制仍存在。本次一个 265 字符的 wrapper 路径用普通路径访问失败，扩展路径可读；同输入在短临时目录中运行成功。因此运行夹具采用短目录，没有把它记录成已修复的长路径支持。
- NeoForge 1.20.1 只做编译，未替用户接受服务器 EULA。启动它需要用户明确授权。
- 未重跑全部八轨道的玩法、Linux 虚拟机或安装包认证；已发布的版本状态保持原样。
- 授权是产品操作批准机制，不是操作系统沙箱，也不会撤销已有 MCP Workspace 令牌的基础权限。图像、声音听感和真实输入体验仍需对应客户端证据。
