# Stage 17 恢复与故障回滚验证

接续 [v26 XML 保留复验](stage-17-procedure-preservation-2026-09-23.md)。本轮未修改产品实现，仍使用 v26；新增真实工作区测试与公开入口恢复探针。**Windows EXE 和 Ubuntu 安装产品的正向恢复均已在用户分别签发授权后通过；PRD17 尚未整体验收。**

## 已完成

[Stage17RecoveryPersistenceTest](../../src/test/java/dev/copperbench/core/workspace/mcreator/Stage17RecoveryPersistenceTest.java) 使用真实 MCreator 工作区、真实持久化网关和 JGit 历史，只有故障点在测试中注入。三个用例通过：

1. 在 revision 2 创建恢复点，修改方块硬度、手写 Java 并增加新元素至 revision 5；恢复后旧文件及手写锁定还原，新元素文件消失，revision 前进至 6。再恢复安全恢复点，重新得到改动后的文件，revision 前进至 7。关闭重开核对持久状态。
2. 在文件恢复并加载工作区后注入一次异常：退回恢复前安全点，四个被检查文件字节、内存 revision 5、方块定义和手写源码状态不变，关闭重开一致。
3. 在恢复后的新 revision 已写入磁盘后注入一次异常：同样回滚至 revision 5，文件及重开状态一致。

三种情况均保留 `.copperbench` 中不参与恢复的标记文件。测试中的 UI actor 和批准字段用于测试可信 UI 分支，没有为已安装产品签发任务授权。

新用例加既有历史／Core 合同共 **13 通过、0 跳过、0 失败／错误**，日志 `.tmp/stage17-recovery-persistence.log`，明细 `.tmp/stage17-recovery-persistence-summary.json`、`.tmp/stage17-recovery-persistence-xml/`。本轮不将这两种注入故障推广为任意磁盘故障都能恢复的保证。

## 实际产品已准备的恢复范围

[公开探针](../../scripts/stage17-installed-recovery-probe.py) 在 Windows EXE 和 Ubuntu 安装产品上分别创建了独立示例副本，主 JAR 均为 v26：`f25500c7b2c178e684c10657a84bdbade474b5afc5c8a8a91169ce6568d89206`。

- Windows 工作区：`D:/Hyper-V/Stage17-Linux/restore-validation-v26/windows`；清单与恢复预览为父目录的 `windows-manifest.json`、`windows-prepare.json`。
- Ubuntu 工作区：`/home/stage17/evidence/v26/restore-workspace`；`restore-manifest.json`、`restore-prepare.json` 已归档至 `D:/Hyper-V/Stage17-Linux/guest-evidence-v26/`。
- 准备时两者均为 revision 5，目标恢复点来自 revision 2。预览仅包含五个测试副本路径：方块定义、手写 Java、工作区定义，以及恢复点之后新增的 function 定义和 `restore-fixture-only.txt`。后两者预期移除；不参与历史的标记文件不在恢复清单中。
- 两平台均验证：未授权恢复被拒绝；传入 `userApproved: true` 仍被拒绝；被检查的文件及 revision 不变。准备证明状态为 `prepared`，不是 `passed`。

两平台正向探针均已执行并通过：拒绝过期 revision、拒绝不存在的恢复点且保留文件、恢复至 revision 6、借助自动安全点撤回至 revision 7、关闭重开核对。方块定义、手写源码及手写锁定符合预期，忽略的标记文件保留。所有操作限定于上述一次性测试副本。

最终 Windows 证明：`D:/Hyper-V/Stage17-Linux/restore-validation-v26/windows-verified.json`；Ubuntu 证明：`/home/stage17/evidence/v26/restore-verified.json`，已归档至 `D:/Hyper-V/Stage17-Linux/guest-evidence-v26/restore-verified.json`。两者状态均为 `passed`，主 JAR 与 v26 一致，两工作区最终均为 revision 7。

运行日志为 `.tmp/stage17-restore-windows-verify.log`、`.tmp/stage17-linux-restore-verify.log`，核对汇总 `.tmp/stage17-recovery-final-summary.json`。结束时两平台无遗留产品 API 进程。源码故障注入与产品实际恢复结果分开计证；Windows 使用导出 EXE，不代表 Windows 安装器认证。

## 授权要求与剩余范围

初次核对时 Windows 只有已失效且不含 `restore` 的历史授权。用户随后于 2026-09-23 10:37:24 UTC 在本机确认，签发仅覆盖 `D:/Hyper-V/Stage17-Linux/restore-validation-v26`、能力 `restore`、有效期一小时的授权；产品再次查询确认有效后才执行实际恢复。有效期从用户确认时起算，等待确认的时间不消耗该期限。

Ubuntu 初次公开授权列表也只有已失效且不含 `restore` 的历史授权。用户随后在其确认窗口签发了仅覆盖 `/home/stage17/evidence/v26/restore-workspace`、能力 `restore`、有效期一小时的授权，探针才继续执行。产品 [任务授权文档](../ai/task-authorization-and-acceptance.md) 要求用户在对应环境亲自确认，agent 不能用 `--approve`、`userApproved: true` 或工作区文件自行批准；本轮没有把 Windows 授权复制或伪造为 Ubuntu 授权。

本轮所列恢复、撤回、授权／revision 拒绝及两种故障回滚场景已有结果。PRD17 仍未整体验收，引用语义、完整插件兼容、UI 矩阵、同源最终候选建模／游戏闭环和独立试作仍需继续。本轮没有 Minecraft 输入或 Jev 调用。
