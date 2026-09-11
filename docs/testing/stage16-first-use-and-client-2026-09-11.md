# Stage 16：首次使用与客户端验收记录

状态：进行中。本文不能单独作为 Stage16 完成或发布批准。

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

陌生 agent 的三个新需求为交互状态物品、持久计数方块、成熟小麦收获事件记账。需求与反例先于实现写入；当前仍等待合法授权与固定候选，草稿不能计为构建或玩法通过。

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
