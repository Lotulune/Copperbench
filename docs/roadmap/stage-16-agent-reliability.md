# Stage 16：Agent 任务成功率与自动验收

- 状态：已完成既定 A–D 验证范围；2026-09-11 获用户批准启动，2026-09-13 北京时间完成最后冷缓存收尾。未公开发布新候选。
- 基线：Stage 15 关闭提交 `090b1a9d`；任务授权、真实文件隔离和产品 GameTest 闭环提交 `943fd582`。
- 目标：让只持有安装包和公开文档的 agent，完成实际模组任务、处理故障，并交付对应最终产物的验收证据。

## 验证规则

每次试作记录安装包和应用 JAR 的 SHA-256、来源提交、操作系统、工作区轨道、依赖缓存状态、任务耗时、首条机器反馈时间、失败原因、修复次数和人工介入次数。构建成功、模组加载成功、玩法断言通过、客户端体验分别计证据。

脚本重放验证接口可靠性；已经读过内部代码的开发 agent 不计为陌生 agent。只有仅使用安装包、公开文档和模组工程的独立试作，才能关闭陌生 agent 使用门禁。试作期间若需要读取产品实现或修改 Copperbench，该次记为产品缺陷发现，修复后另记一次重试。

## 工作分段

| 分段 | 交付及通过条件 | 当前范围 |
| --- | --- | --- |
| A：分发包中的故障恢复 | 直接运行分发包 EXE；验证环境发现、无授权及时拒绝、故意编译失败及定位、修复构建、验收数量不足时拒绝、最终真实 JAR 行为验收；保留完整 JSONL 和摘要 | [首个案例通过](../testing/stage16-packaged-agent-reliability-2026-09-11.md)；Windows / Fabric 1.21.1，现有机器缓存与提供的模组工作区 |
| B：陌生 agent 的不同模组试作 | 至少三个案例，覆盖交互物品、具有持久状态的方块、数据或服务器事件；验收断言从需求推导；逐次记录人工介入和文档缺口 | [三个新案例通过](../testing/stage16-fresh-agent-three-mods-2026-09-11.md)：最终候选 `3c0aa090`，17 个不同用例；保留早期创建失败及被替换的测试夹具结果 |
| C：首次使用与安装回归 | 固定候选包，在已支持的 Windows 与 Ubuntu 基线上验证首次启动、用户签发任务授权、创建、构建和失败恢复；冷缓存与热缓存分开记录 | [候选 `d9fb1458` 完整冷缓存通过](../testing/stage16-cold-cache-2026-09-12.md)：宿主 Windows 与 Ubuntu 实际安装版分别从空依赖及资源缓存完成首次启动、授权、创建、两次构建、修订冲突恢复、真实渲染、正常退出、重开后旧令牌拒绝和授权撤销。此前 [`c0178f6b` 热缓存回归](../testing/stage16-installed-regression-2026-09-12.md)保留 Windows VM 的 OpenGL 零退出码失败识别，以及[低内存恢复](../testing/stage16-low-memory-installed-2026-09-12.md)和[误报成功历史](../testing/stage16-installed-desktop-2026-09-12.md)。最多一台 VM、最高 4 GiB，结束均关机 |
| D：客户端可自动验证的行为 | 建立真实输入、客户端反馈及存档重进的可观测验收；对不能由服务端断言覆盖的需求逐项补证据 | [Windows / Fabric 1.21.1 范围通过](../testing/stage16-client-native-restart-2026-09-12.md)：固定候选 `33ceb6e9`，20 TPS 物品反馈、方块重置与独立计数、成熟/未成熟及创造模式收获、两次正常退出和完整重启后状态恢复均有原生输入、截图及日志证据；79 张截图与 113 个原始文件校验通过 |

先固定主轨道完成 A/B，再扩展必要轨道与平台。编译模板数量不作为陌生 agent 成功率或玩法覆盖数量。

后续[存储清理与冷缓存验证](../testing/stage16-cold-cache-2026-09-12.md)累计实际清理约 40.51 GiB，保留 VM 磁盘与检查点。冷缓存发现的首次地区弹窗不可见问题仅修改 `MCreatorApplication` 的所有者显示顺序，获准提交为 `d9fb1458`；两端备份升级后完整验证通过，分别核对 3,888 个资源对象。国内镜像连接拒绝和 Fabric 官方仓库握手失败均保留，成功重试使用另一个空目录，没有混用失败轮次缓存。

阶段结论由表中各段不同候选的独立证据组成。`d9fb1458` 相对 `c0178f6b` 只有首次弹窗顺序改变执行字节码；现有模组行为证据继续按原来源提交标识。本轮维护验证不增加陌生 agent 案例或 17 个不同 GameTest 的数量，不能推广为其他生成器、操作系统或所有网络环境均通过。

## A 段可重复入口

仓库提供[寻路铃示例](../../examples/agent-native/wayfinder-bell/README.md)及其[工作区文件](../../examples/agent-native/wayfinder-bell/wayfinder_bell.mcreator)，包含原生实现、测试配置和行为断言，不包含本机历史、缓存、授权记录或构建产物。它用于重放既有案例，不计为 B 段的新模组试作。

```powershell
pwsh -NoProfile -File scripts/verify-stage16-packaged-agent.ps1 `
  -ProductExe '<extracted-product>/copperbench.exe' `
  -SourceWorkspaceFile '<authored-mod>/example.mcreator' `
  -TrialRoot '<new-empty-trial-path>' `
  -EvidenceDirectory '<evidence-path>'
```

输入工程应已包含 `packaged_jar` 测试配置与真实行为测试。脚本复制源码到新的试作目录，在副本注入并修复编译错误，并验证原工程字节保持一致。产品命令只调用 EXE，不注入授权、不使用仓库运行类路径，也不通过进程环境设置 JDK socket 兼容补丁。脚本不会接受服务器 EULA；当前 A 段目标为 Fabric 1.21.1。

每次重放保留全部失败和成功输出。`summary.json` 中的 `freshAgent=false`、缓存状态与未测试的创建流程必须如实保留，不能仅凭 `passed=true` 关闭整个 Stage 16。

## 发布边界

本阶段的推送与本地 ZIP 构建均不构成公开发布。Windows Gradle/JDK 运行路径已发生变化，新的候选发布前需复验相关 FR-PROD 安装产品门禁，尤其 Run Client 与 Desktop MCP；Linux 条件分支的单元验证不替代 Ubuntu 安装回归。正式发布继续使用既有 release-control 流程，当前阶段不改 CI、发布通道或已发布版本状态。
