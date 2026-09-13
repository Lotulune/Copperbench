# Stage16：低内存配置与安装后重放

状态：**原先的内存恢复阻塞已解决。Windows 与 Ubuntu 在单台最高 4 GiB 下完成固定候选安装和各 5/5 JAR 行为重放；C 段仍有明确未验收项，Stage16 整体不关闭。**

后续[安装版桌面补验](stage16-installed-desktop-2026-09-12.md)已补齐 Ubuntu 的授权、创建、认证 MCP 与客户端生命周期，并补齐 Windows 的授权、创建和 MCP 构建；Windows OpenGL 环境失败及零退出码误报成功另行记录，不能将本文早期局部通过扩展为客户端通过。

## 内存处理

用户已允许降低验证内存，并在对应验证前清理内存。此前的 6–8 GB 建议来自旧虚拟机挂起状态的恢复需求。本轮先为两台机器创建 `Stage16-Before-LowMemory-20260912` 标准检查点，确认检查点为 `Saved` 后，清除工作实例的挂起内存并改为较小配置冷启动。原检查点保留，虚拟磁盘、既有工作区和历史验收证据未删除。

| 验证机 | 原配置 | 当前配置 | 执行方式 |
| --- | --- | --- | --- |
| Windows `Copperbench-G7` | 固定 6 GiB | 启动 4 GiB，动态范围 3–4 GiB | 先单独运行，完成后正常关机 |
| Ubuntu `Copperbench-Stage15-Linux` | 启动 4 GiB，动态范围 4–12 GiB；旧保存状态恢复请求约 5410 MB | 启动 3 GiB，动态范围 2–4 GiB | Windows 关机后再运行，负载中增至 4 GiB 上限 |

主机仅对选定桌面应用执行一次 `EmptyWorkingSet`，37 个进程成功回收工作集，没有结束或暂停这些进程，也没有修改主机持久设置。当时可用物理内存从约 1.91 GiB 增至 4.28 GiB；这是一次观测，应用重新活动后可再次占用内存。该系统接口及标准检查点的内存保存语义分别依据微软 [EmptyWorkingSet 文档（2022-08-23）](https://learn.microsoft.com/en-us/windows/win32/api/psapi/nf-psapi-emptyworkingset)和 [Hyper-V 检查点文档](https://learn.microsoft.com/en-us/virtualization/hyper-v-on-windows/user-guide/checkpoints)核对。

[原配置](../../evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/original-vm-memory.json)、[当前配置](../../evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/reduced-vm-memory.json)、[原会话检查点](../../evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/preserved-checkpoints-verified.json)和[主机回收摘要](../../evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/host-working-set-reclaim.json)可分别复核。详细主机应用清单仅保留在本机 `build`，不复制进证据归档。

## 固定候选的实际验证

两平台均绑定源码 `33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5`，使用既有的共振令牌源码副本及机器依赖缓存，不增加 B 段陌生 agent 案例数或不同需求数。

| 检查 | Windows | Ubuntu |
| --- | --- | --- |
| 安装包 SHA-256 | `717d7de82a30209b15b086daf2af9d351e47f85967f5657001d85559492803e4` | `6ae06ca2033d3f40bb4c1401901de36fcaf3cad218ff9243d064968ea48668c0` |
| 安装结果 | NSIS 升级退出码 0，实际 EXE/JAR 与固定候选一致 | DEB 安装成功，`dpkg -V copperbench` 退出码 0 且无差异 |
| 环境 | Windows 11 build 26200，无系统 Java/Javac/Gradle/Git | Ubuntu 24.04 x86_64，无系统 Java/Javac/Gradle/Git |
| 无授权创建 | 明确拒绝，未创建目标 | 明确拒绝，未创建目标 |
| 故障恢复 | 注入编译错误后定位到实际文件，修复构建成功 | 同一流程通过 |
| 修复构建耗时 | 29.043 秒 | 12.972 秒 |
| 最终真实 JAR | 5/5，`packaged_jar`、`frameworkTests=0`、当前源码校验通过 | 同样为 5/5，通过当前源码校验 |
| GameTest 耗时 | 182.983 秒 | 68.623 秒 |

两轮均保留失败输出、成功输出、XML、实际被测 JAR、源快照清单及原始输入校验；原始源码副本字节未变。这些耗时受虚拟机限制与主机负载影响，不作为冷缓存或稳定性能基准。Ubuntu 地址在冷启动后变化，通过虚拟机 MAC 找到新地址，并继续用原 SSH 公钥严格校验，没有跳过主机身份检查。

Windows 已通过原生桌面快捷方式启动新安装包，再从文件选择器打开工作区。JCEF 工作台正常显示，桌面 Gradle 同步使用随包 Java 21。Desktop MCP 为 `listening`，无令牌初始化请求返回 `401`。通过原生关闭按钮退出后，应用进程消失、连接描述文件删除、旧端口关闭。此记录不包含带令牌的完整 MCP 读写流程；原生源码夹具的健康面板还显示两项资源诊断，也没有被写成“所有诊断归零”。

Ubuntu 原生控制台仍显示 `stage15` 登录页，没有代输密码或操作认证界面。因此它的本轮图形桌面验收未执行，CLI 重放不能替代这一项。

## 最终状态与剩余范围

两台验证机已正常关机，`MemoryAssigned=0`，[收尾时主机约有 5.60 GiB 可用内存](../../evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/final-memory-state.json)。小内存配置保留，后续逐台启动，并可在验证前复用本机工作集回收脚本；不再要求先恢复旧的大内存挂起会话。

C 段剩余：对应安装基线上的用户签发任务授权与授权创建、完整 authenticated Desktop MCP 流程、安装版 Run Client 相关回归，以及 Ubuntu 登录后的真实图形观察。现有缓存重放不宣称冷缓存覆盖。D 段此前完成的真实客户端退出、重进及持久状态证据继续有效。本轮没有修改产品实现、CI、公开产品状态，也没有提交、推送或发布。

[总摘要](../../evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/summary.json)、[66 个原始文件的哈希清单](../../evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/archive-manifest.json)和[校验回执](../../evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/verification.json)已归档。复核命令只检查证据，不启动虚拟机或执行安装：

```powershell
python evidence/stage16/2026-09-12/low-memory-installed-33ceb6e9/verify.py
```
