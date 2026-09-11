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
