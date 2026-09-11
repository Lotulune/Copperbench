# Stage 16：首次使用与客户端验收记录

状态：进行中。本文不能单独作为 Stage16 完成或发布批准。

后续生命周期修复候选 `33ceb6e9`、完整回归和真实方块交互进度见[2026-09-12 检查点](./stage16-runtime-and-client-checkpoint-2026-09-12.md)。本文中的旧候选与失败记录保留其原有范围。

## 初始候选发现

候选源码 `a3322c86256686edec5ac260dac2b8adaccf73d1`，Windows ZIP SHA-256 为 `3c4dded214d7ae36f347a34149753a72cdb9d69ba76996b3fe53ad8ea6c71837`。

- 独立 agent 只使用安装包、公开文档和新建试作目录。首次 `bootstrap create-workspace --no-prompt true` 返回 `USER_APPROVAL_REQUIRED`，约 6 秒完成，没有创建工作区。这是预期拒绝。
- 主开发 agent 为该任务启动 `bootstrap authorize-task`。线程转储证明程序等待在 `JOptionPane.showConfirmDialog`，桌面自动化没有得到可操作的授权窗口。该尝试终止后未签发授权，不计为用户拒绝。
- 从分发包桌面打开寻路铃的独立副本，实际出现配置失败。应用日志另证明 Desktop MCP 在 `NioEndpoint` 创建 selector 时遭遇 `Unable to establish loopback connection`。此前子进程的 Gradle 修复未覆盖应用 JVM。
- 桌面旧 Gradle 初始化仍默认使用应用 JVM；它必须与 Core 按版本轨道选择的随包 JDK 一致。

## 修复与局部验证

CLI 审批改为独立顶层窗口，提供任务栏入口，默认选中取消，关闭窗口或按 Escape 拒绝。仅本机明确选择 Allow 才返回批准；测试中的按钮模拟不签发真实任务授权。

桌面启动在创建 selector 前用独立进程检查本机通信，仅当默认路径失败且 TCP 路径验证成功时设置当前应用 JVM 的属性。桌面 Gradle 初始化使用版本目录中的 JDK，并复用子进程兼容检查。没有修改全局环境。

本机相关测试共 17 项通过：Bootstrap 9、Gradle 兼容 4、桌面 JDK 轨道 1、真实 Swing 窗口生命周期 3。窗口测试覆盖默认取消、明确选择与关闭拒绝，未调用授权存储。尚需绑定修复后的分发包，复验用户签发、创建、Desktop MCP 和客户端生命周期。

陌生 agent 的三个新需求为交互状态物品、持久计数方块、成熟小麦收获事件记账。需求与反例先于实现写入；在这一初始轮仍等待合法授权与固定候选，草稿没有计为构建或玩法通过。

## 第二次分发包试作

`a78274ce` 候选 ZIP SHA-256 为 `e6b8f82544a1bb331f46965b1d99070cef193fdfcc2274cf4252ca7183da1e02`，应用 JAR 为 `c28bccddc6ffd792b8dfa3b0c3309daab3ba38192b8006b79edb8dde4ff9d7a2`。本机用户于 `2026-09-11T11:25:27Z` 从该包签发三个试作目录的任务授权；开发 agent 没有模拟此批准。

随后三个新案例的创建均返回 `WORKSPACE_GRADLE_SYNC_FAILED`，耗时约 11–15 秒。原因是 `bootstrap create-workspace` 也会在当前 JVM 使用 Gradle Tooling API，第一轮修复只在桌面启动时执行应用 JVM 检查。修复将这一检查覆盖到创建命令。有效授权继续使用，不要求用户重复批准；修复包的实际创建仍需重新验证，不能引用源码测试代替。

客户端准备工具及公开操作文档已经加入；5 项完整性测试通过，覆盖报告/JAR 变化、失败或旧源报告、JSONL 终态、异常 mod ID 和客户端额外 JAR。它们没有执行真实客户端操作，客户端行为门禁继续保持未完成。

## 守护进程入口补证

主开发 agent 用 `8e6033dd` 安装包自测创建，仍收到同步失败；此包没有交给陌生 agent 重试。该次 Gradle 9.7.0 守护进程日志证明其 Java 21 selector 在接受连接时失败。Tooling API 的构建环境参数不足以影响守护进程的启动，因此验证过的兼容选项还需进入守护进程 JVM 参数。

新增的真实 Tooling API 回归在独立的最小 Gradle 工程中打开 selector，并检查守护进程实际采用的 JDK 和属性；它不依赖 Minecraft 下载。创建失败另保留有界、脱敏的异常原因，供命令行 agent 诊断；稳定错误码不变。完整分发包仍须在这些修复后重新验收。

进一步的安装包自测证明，普通 `-D` 参数在这个 Tooling API 路径中也未进入守护进程的启动参数。旧测试读到的是任务执行时的可变属性，并受到测试进程继承的临时设置影响，因此不能证明启动边界已经修好。

最终修复使用约 1.5 KiB 的 Java 17 启动辅助 JAR：仅在默认通信检查失败而 TCP 检查通过时，于守护进程 `main` 之前设置该项属性。它不改写类、不修改全局环境，常规 Core 子进程保持已有路径。新的测试检查真实 JVM 启动参数，并在清除调用方 Java 选项的新 Java 21/25 进程中验证 selector。辅助文件定位同时覆盖 Launch4j EXE 与直接应用 JAR 的分发布局。

修复后的未压缩分发目录已通过主开发 agent 的真实 `bootstrap` 创建，并成功准备独立客户端宿主。这是提交前产品自测；陌生 agent 将在随后固定的候选上重新开始，不把开发目录自测计入其通过数。

## 固定候选的独立重试

`3c0aa090` 的完整 Java/Javadoc、UI 和 MCP [CI 通过](https://github.com/Lotulune/Copperbench/actions/runs/34598244961)，对应 [Linux 候选构建通过](https://github.com/Lotulune/Copperbench/actions/runs/34598244943)。随后独立 agent 在同一个用户授权下完成三个真实创建、构建和最终 17 项不同的 JAR 验收，详见[独立试作报告](./stage16-fresh-agent-three-mods-2026-09-11.md)。此前 `701df44d` 的 CI 失败记录仍保留，没有被这次通过覆盖。

Windows 安装器复用固定的 3202 个应用文件生成，SHA-256 为 `bf1b4305fc22849e80414fac138235118e7f86e505afedabee2dadf9ca19d5b0`，855079242 字节。安装器构建第一次额外使用 `-Psnapshot=true` 时，原有未加引号的 NSIS 上游版本宏不能接受 `EAP` 空格而失败；按标准安装器任务配置重试成功，应用文件没有重建。这个本地安装器仍不是公开发布。

主任务通过实际分发包启动独立寻路铃 JAR 宿主，任务在渲染日志后持续运行。桌面工具随后拒绝输入，原生截图确认主机锁屏。未执行成功的玩法输入、未创建世界；为释放内存进行独立安装回归，主任务结束其所属 Minecraft JVM，产品如实返回失败，未将其记为正常关闭或客户端通过。现有 Windows 验证虚拟机恢复需要 6144 MiB，主机可用内存不足，配置和保存状态保持原样；先继续 Ubuntu 的非桌面检查。

## Ubuntu 安装后演练与完整回归

固定产品代码的 [Nightly 完整回归](https://github.com/Lotulune/Copperbench/actions/runs/34600779842)已通过：产品回归及八条 Fabric/NeoForge 生成器轨道全部成功。[机器回执](../../evidence/stage16/2026-09-11/runtime-candidate-3c0aa090/ci-nightly.json)记录候选分支提交 `3c0aa090`。

Ubuntu 24.04.4 GNOME Wayland 验证环境未安装系统 Java、Javac、Gradle 或 Git，保留先前认证环境的依赖缓存。安装 PR 开发候选后，通过公开 `/usr/bin/copperbench` 完成环境发现、无授权及时拒绝、注入编译错误并定位到真实文件、修复构建，以及修复后实际 JAR 的 5/5 行为验收；原始工程字节未变。修复后的构建耗时 16.689 秒，JAR 验收 95.971 秒，流式首条输出约 0.66 秒。这个重放沿用已经编写的共振令牌源码，不增加 B 段的陌生 agent 案例数。

该包的实际来源是 PR 临时合并提交 `94346b61d98ade884113f17dc3c1b6fc6edb79e4`，与分支 `3c0aa090` 的 Git tree 都是 `fc7a3452d5ad5507b37c43b21a0f2585e1da116b`；不能把包的来源字段写成分支提交。DEB SHA-256 为 `65dafdee5f8e06b922ae336eb687aa955c3b43029f2f3feaca0deeeb18eca520`，安装后的应用 JAR 为 `71e94c61ca0a807c09bfda0a85f0255eef209c0548c1779b0c6f3ec9eb3c4e67`。[完整演练记录](../../evidence/stage16/2026-09-11/linux-pr-rehearsal/pr-repair-evidence/summary.json)保留每条命令、失败诊断、产物及报告哈希；[来源说明](../../evidence/stage16/2026-09-11/linux-pr-rehearsal/source-identity.json)明确它不是所选的最终发布候选。

为遵守既有来源和精确二进制发布约束，已从实际分支提交 `b62d67095c2c915db95ecd7383f7eeffe6c4fd09` 单独触发 [Linux 候选构建](https://github.com/Lotulune/Copperbench/actions/runs/34603469756)，运行成功。后续安装门禁必须绑定该候选实际字节，不能直接重标上述开发演练的包哈希。

Windows 上另一个独立客户端宿主已装入 B 段三个经过验收的 JAR，产物清单哈希为 `4fc686a872ce7e5e8ab6650d63c9c5181d314c4ce7e1e2318250d31c9e705c73`。它使用固定离线测试玩家配置，真实产品构建成功且配置字节保持一致。客户端测试计划要求保存非默认状态，并验证正常退出、重启和同一世界/玩家的数据恢复。

## 客户端第二次运行：真实输入与生命周期缺陷

任务 `4428fdfd-529a-4ced-bfd1-31d087582965` 从固定 `3c0aa090` 分发包启动，三个实际 JAR 均出现在本次资源加载日志。原生 UI 新建 `Stage16 Agent Acceptance` 超平坦世界，Minecraft 日志记录玩家 `Stage16Trial` 入场，实际 `usercache.json` 记录 UUID `8fb12eef-98f0-409b-a3c1-18abf66578f6`。这只观察了首次运行，尚不能证明重启后的身份保持。

原生右键使用共振令牌，观察到激活、关闭、再激活的 HUD 反馈。正常 20 TPS 下，两次点击后的截图没有捕获到短暂反馈；游戏内只读查询确认当时物品状态为关闭。随后为观察短暂 HUD，使用原生聊天输入将 tick rate 调到 5 TPS，三次右键的反馈截图清晰可见。因此本轮只证明该受控条件下的显示与输入，不声称正常速率的可读性或声音体验通过。后续还通过 Minecraft 原生设置绑定 `Use=R`、`Attack=V`、切换式潜行，以避免绝对坐标鼠标动作改变视角；方块和收获操作尚未执行。

该次公开命令在开始约 45 分钟后返回退出码 `10`、`HEADLESS_TASK_TIMEOUT`，最终 JSON 中任务仍是 `running`，Minecraft JVM 继续运行。源码 `HeadlessProductLauncher.awaitTask` 为所有任务写死了同一等待期限，导致长时间客户端会话失去命令层的生命周期观察。这是实际分发包缺陷，不能归因于锁屏，也不计为正常退出。

修复将持续交互的 `run_client`／`run_server` 与有限构建任务的等待期限区分；通过可控时钟的轮询回归验证超过 45 分钟后继续观察，并覆盖正常关闭、失败、取消、流式日志游标与有限任务原有超时。[源码回归共 18 项通过](../../evidence/stage16/2026-09-11/interactive-lifecycle-fix/result.json)。修复后的候选仍需重新构建并执行真实客户端闭环。[第二次运行的完整失败记录](../../evidence/stage16/2026-09-11/client-attempt2-timeout/summary.json)包含真实 UI 事件、截图、产品 JSONL 和游戏日志，D 段继续未完成。

此前从分支提交 `b62d6709` 下载的 Linux 候选，其六项资产已通过各自的 `gh attestation verify` 和来源提交／工作流／SHA-256 复核，详见[机器回执](../../evidence/stage16/2026-09-11/linux-final-candidate-b62d6709/verification.json)。它与 Windows `3c0aa090` 一样未包含此次生命周期修复，因而不再作为本阶段最终发布候选；原始签名和哈希保留，不重新标注。
