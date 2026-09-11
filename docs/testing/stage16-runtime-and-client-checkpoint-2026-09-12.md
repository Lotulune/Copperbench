# Stage16：生命周期修复候选与客户端检查点

状态：进行中，尚不满足发布条件。Stage16 A/B 的独立行为证据保留，C/D 尚未全部关闭；没有发布新版安装包。

## 固定候选及回归

源码提交 `33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5` 包含 headless 交互任务生命周期修复。旧候选把所有任务限制为等待 45 分钟，实际客户端运行因此返回 `HEADLESS_TASK_TIMEOUT` 而 Minecraft 仍在运行。修复后，客户端和服务器会持续被观察到任务结束；有限构建仍保留原期限。18 项针对性源码回归已通过；此前失败运行完整保留。

Windows 标准 ZIP 和 NSIS 安装器从同一份 3202 文件的应用目录生成；复制到独立运行目录后的每个文件都与导出目录哈希一致，未使用源码类路径覆盖。候选信息及全文件清单见[运行时证据](../../evidence/stage16/2026-09-12/runtime-candidate-33ceb6e9/candidate.json)。

| 产物 | SHA-256 |
| --- | --- |
| Windows 运行 EXE | `203721a14453bdfe275a635cf56ea48e3ae9b914299dc5bbd6cd49fb3b7cbffd` |
| Windows 应用 JAR | `1f049a071cfe709fba9c98ce22defb9bbe6688fc334f421b64ce802315758c01` |
| Windows ZIP | `d1080b5a09b0ec4386165337938186873aa512adce53ab22675d72056065dccf` |
| Windows 安装器 | `717d7de82a30209b15b086daf2af9d351e47f85967f5657001d85559492803e4` |
| Linux DEB | `6ae06ca2033d3f40bb4c1401901de36fcaf3cad218ff9243d064968ea48668c0` |
| Linux portable | `5e1ae34cbc1734618d93837cb85c6487e8507d042ba967b79656af02832a1e05` |

[Java/Javadoc、UI 与 MCP CI](https://github.com/Lotulune/Copperbench/actions/runs/34615653591)、[Linux 显式分支候选构建](https://github.com/Lotulune/Copperbench/actions/runs/34615723771)、[完整 Nightly 与八条生成器轨道](https://github.com/Lotulune/Copperbench/actions/runs/34615812721)均通过。Linux 六项产物的实际哈希、签名来源、工作流与来源提交均已核对，[签名校验回执](../../evidence/stage16/2026-09-12/linux-candidate-33ceb6e9/verification.json)没有声称安装回归已完成。

## 实际 EXE 故障恢复重放

使用固定 EXE 和已交付的共振令牌源码副本，再次完成环境发现、无授权及时拒绝、故意编译错误定位、修复构建、实际执行后拒绝不足的验收数量，以及最终 JAR 的 5/5 行为验收。最终报告为 `packaged_jar`、`frameworkTests=0`、`sourceCurrentAtCompletion=true`，原始源码字节未变；被测 JAR 和 XML 的当前哈希与报告一致。

本轮是维护者重放，沿用现有依赖缓存，部分时间与打包、另一个客户端运行重叠；不增加陌生 agent 案例数，也不作为隔离性能基准。完整输出、失败记录、XML、被测 JAR 和来源指纹见[重放摘要](../../evidence/stage16/2026-09-12/packaged-replay-33ceb6e9/summary.json)。

## 客户端第三次运行

任务 `e882d901-fa63-4cd8-957c-4154e202354f` 从上述固定 EXE 启动，继续使用用户此前签发的目录授权。测试宿主中的三个模组 JAR 未更换，启动前身份校验通过。正常创建新客户端进程后，原生窗口可见、点击和键盘输入均得到实际响应；此前第二次超时遗留的进程已单独清理，明确未计为正常退出。

在 `Stage16 Agent Acceptance` 世界、固定玩家 `Stage16Trial` 中，本次实际观察如下：

- D1：使用绑定到原生 `Use Item` 的 `R`，令牌反馈为关闭、再激活。短暂 HUD 截图使用 5 TPS，未把它写成正常 20 TPS 的可读性结论。
- D2：原生放置两个方块，并分别通过按键操作，令 `(0,-60,1)` 的计数达到 3、`(2,-60,2)` 达到 7。F3 显示实际坐标及计数；另一个只读条件命令同时检查两者，并返回 `D2_INDEPENDENT_3_7`。聊天命令没有写入这些计数。
- D3/D4/D5：尚未完成。第一次为重置切换潜行时，桌面工具返回 `failed to activate captured window`；刷新窗口后又返回 `GetCursorPos failed: 拒绝访问。 (0x80070005)`。后续没有继续发送输入。

[客户端检查点](../../evidence/stage16/2026-09-12/client-attempt3-checkpoint/checkpoint.json)保留 24 张原生截图、输入事件、产品 JSONL、游戏日志与玩家缓存。它是运行中的快照，没有最终成功状态、正常退出或重启通过结论。当前待恢复状态为 A=3、B=7、令牌激活、观察速率 5 TPS；后续必须恢复 20 TPS，再验证收获事件、正常保存退出、进程结束和同世界／同玩家重进。

## 剩余条件

本机当时可用内存不足 1 GiB，已保存的安装验证虚拟机尚不能恢复；没有调整虚拟机配置或关闭用户应用。已请求恢复可操作桌面并为后续安装验收留出 6–8 GB 内存。候选级 Windows/Ubuntu 安装后 Desktop MCP、实际授权创建及对应安装门禁仍需完成。发布继续使用既有来源、签名、安装证据和精确二进制约束，不能用以上 CI、缓存重放或客户端部分结果替代剩余门禁。
