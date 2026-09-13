# Stage16：存储清理与完整冷缓存验证

Windows 与 Ubuntu 的完整冷缓存验证均通过，首次地区弹窗修复已提交为 `d9fb1458`，两端实际安装版均已备份升级。验证于 2026-09-12 开始，收尾时间为北京时间 2026-09-13 00:02；下文原始任务时间使用 UTC。[最终机器核验](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/final-verification.json)覆盖空缓存、产物身份、两次构建、修订冲突恢复、真实客户端渲染、正常退出、会话失效与授权撤销。

## 已完成的存储清理

- `D:` 初始剩余约 26.2 GiB；95 个核定清理目标全部移除，实际释放 33.28 GiB，清理完成时剩余 59.44 GiB。
- 清理了历史 Gradle 依赖缓存、下载分片、重复压缩容器与可再生成的导出目录。删除重复容器前，逐字节哈希核对了其中 8 个安装包与保留副本。
- 清理脚本先验证绝对路径处于项目边界内、目标及子目录没有重解析点、不包含受 Git 跟踪的源码，并排除证据、当前候选和备份目录。
- 虚拟机磁盘占用合计约 197 GiB；两台 VM 及三个既有检查点全部保留，未删除或合并虚拟磁盘。宿主安装回退备份、用户数据备份与最终候选安装包保留。
- [清理清单](../../evidence/stage16/2026-09-12/cold-cache-and-storage/storage/cleanup-plan.json)、[实际清理结果](../../evidence/stage16/2026-09-12/cold-cache-and-storage/storage/cleanup-result.json)、[保留项核对](../../evidence/stage16/2026-09-12/cold-cache-and-storage/storage/cleanup-preserved-state.json)。逻辑文件大小与实际磁盘回收量分别记录。
- 修复候选构建后，再核对四个已保存产物的 SHA-256，清理重复导出目录，另释放 7.23 GiB。两次累计清理约 40.51 GiB；第二次清理时 `D:` 剩余约 50.98 GiB，后续候选备份和验证数据会继续占用空间。详见[第二次清理](../../evidence/stage16/2026-09-12/cold-cache-and-storage/post-build-export-cleanup.json)。

## 冷缓存边界

安装包自带的 JDK 与 Gradle 允许保留。每个平台使用新的隔离用户目录、Gradle 依赖缓存和工作区，依次验证授权、创建、构建、游戏资源下载、客户端渲染、正常退出和认证会话失效。创建后同一轮后续操作自然复用本轮下载结果。

低内存配置为 Gradle 堆 1 GiB、单工作线程、关闭 Gradle 并行。国内镜像连接失败后，成功轮次使用产品已有的官方源选项；这是依赖与资源冷缓存验证，不宣称完全默认偏好设置。进程级 `-Duser.home` 仅用于隔离历史 Gradle 发行版搜索，没有注入 socket 修复或替代产品启动代理。虚拟机限制为最高 4 GiB，且一次最多运行一台。1 GiB 指 Gradle 堆上限，不代表整个产品、Minecraft 或宿主机总内存。

## `c0178f6b` 首轮发现

Windows 实际安装版在空依赖缓存下创建 Fabric 1.21.1 工程成功，原生退出码为 `0`；创建时间为 2026-09-12 12:37:00–12:45:22 UTC，约 502.6 秒。日志确认 Gradle 从安装目录 `gradle-dists` 预填，Minecraft 客户端、服务端与映射文件本轮下载并处理。

桌面启动随后停在首次地区选择：线程栈显示 `ChinaNetworkSetupDialog.show` 等待输入，但自动化窗口清单没有对应应用窗口，用户也确认看不到弹窗。将验证启动器改为明确可见启动后仍复现。两次阻塞均保留原始记录并结束对应验证进程；该轮授权已撤销，没有计为完整冷缓存通过。

修复限制在 `MCreatorApplication` 首次启动顺序：当地区选择尚未完成时，先显示并置前工作区选择窗口，再显示其模态子对话框。已有选择的启动路径继续沿用原有顺序。[短路径开发回放](../../evidence/stage16/2026-09-12/cold-cache-and-storage/source-smoke-short-home/result.json)确认弹窗可见、键盘选择有效、选择持久化、主界面正常显示，且原生退出码为 `0`。首个开发夹具把用户目录置于很深的构建目录，触发 Unix-domain socket 路径限制，退出失败；[该失败](../../evidence/stage16/2026-09-12/cold-cache-and-storage/source-smoke-long-home/result.json)保留，没有算作退出通过。

## 修复候选与提交关联

源码基线为 `c0178f6b`，仅修改 `src/main/java/net/mcreator/ui/MCreatorApplication.java`。修复文件 SHA-256 为 `b0a718bafa571197e007a24876bc63295a6241d15fbffe410e4fbbbbbd7a6178`。候选在提交前构建；用户确认后，单独提交该文件为 `d9fb1458187110186c04e523d5044250637490bd`，并读取提交中的原始文件字节，确认与已构建候选的源快照完全一致。

| 产物 | SHA-256 |
| --- | --- |
| Windows EXE 安装包 | `2cb006da0f47564c385e3192dcd8aef565f06673db71e0f411527e5231bde11f` |
| Windows ZIP | `fa5a33a1ae9232dad7d2193991d6d192e55461458df5051bce7127616e60cde8` |
| Linux TAR | `9058fc97fcb96f2cd2fed2dec7bad30f4a9e9cad0c541e23bd6543c9d24659fa` |
| Ubuntu DEB | `977bf324cf9838539c0a5de655e51b22e2dd9e42e82f3cc78b0fb200c90f108a` |
| 两端应用 JAR | `3d58f235c0609d4c18fd6d9b43680bbfd9c80c168dbccbe9eccc5b951b05ac19` |

Windows 构建耗时 6 分 32 秒，Linux 分发构建耗时 1 分 49 秒；DEB 在 Ubuntu 上单线程封装，记录 2,681 个文件的哈希及权限。Windows 包内 `MCreatorApplication.class` 与实际开发窗口回放使用的类字节相同。另一个匿名验证器类仅构造函数调试变量名变化，`javap -c -p` 输出完全相同；其余应用类字节不变。已保留最初过严的逐字节校验失败及后续反汇编核对。

这些是私有候选，没有公开发布。宿主安装版于 14:12:02–14:14:09 UTC 升级，安装退出码为 `0`，EXE 与 JAR 的 SHA-256 匹配。升级前备份并核对了 3,203 个安装文件及 5 个用户配置文件；用户配置在升级后字节未变。Ubuntu 于 15:07:29–15:07:39 UTC 安装成功，安装后的 2,681 个文件哈希和权限全部匹配；原安装和用户状态已备份。提交关联见[来源核对](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/source-association.json)。

此前依据用户提供的 `AGENTS.md` 第 5 条请求明确确认，用户随后回复“可以，我允许提交”，本轮据此恢复提交、备份升级及验证。此前等待期间两台 VM 均正常关机、验证 Java 进程已结束；[检查点](../../evidence/stage16/2026-09-12/cold-cache-and-storage/approval-checkpoint.json)保留当时未提交、未升级、完整冷缓存未通过的历史状态。

## 网络失败与独立冷缓存重试

- Windows `s16-cold-fixed` 在 14:16:39–14:21:17 UTC 创建失败，退出码 `10`、错误 `WORKSPACE_GRADLE_SYNC_FAILED`。BMCLAPI 上 `org.jetbrains:annotations:26.0.2` 的 POM 请求重定向到 `cmcc.mirrors.ustc.edu.cn:443`，连接被拒绝。产品回滚整个新工程目录，本轮授权已撤销；原始日志和后续连接探测保留在[失败记录](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/windows-mirror-failure)。
- Ubuntu `s16-cold-fixed` 使用官方源，在 15:17:21–15:20:01 UTC 创建失败，退出码同为 `10`。Fabric Maven 下载 `fabric-lifecycle-events-v1:2.6.0+0865547519` 时记录 `Remote host terminated the handshake`。产品回滚新工程，随后撤销授权，完整[失败记录](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/ubuntu-network-failure)保留。
- 后续独立请求对应文件成功，只说明探测时可访问，不证明原 Java 请求成功。探测响应体没有预填到 Gradle 缓存。Windows 使用新目录 `s16-cold-official`，Ubuntu 使用新目录 `s16-cold-official2` 重试；只复制验证脚本和候选元数据，没有复制旧用户状态、依赖缓存或游戏资源。

## 安装版冷缓存闭环

| 项目 | Windows 宿主机 | Ubuntu 24.04 VM |
| --- | --- | --- |
| 创建 | 399.712 秒，退出 `0` | 299.512 秒，退出 `0` |
| 首次地区弹窗 | 可见，选择官方源并持久化 | 可见，选择官方源并持久化 |
| 首次 MCP 工作区查询 | 0.068 秒 | 0.037 秒 |
| MCP 两次构建 | 50.744 / 18.785 秒 | 31.982 / 13.822 秒 |
| 修订冲突与重试 | `WORKSPACE_REVISION_CONFLICT` 后成功提交 | `WORKSPACE_REVISION_CONFLICT` 后成功提交 |
| 客户端渲染与菜单正常退出 | 通过 | 通过 |
| 应用两次正常退出 | `0 / 0` | `0 / 0` |
| 重开后旧令牌 | `401 / TOKEN_INVALID` | `401 / TOKEN_INVALID` |
| 撤销授权后再次创建 | 拒绝，不生成目录 | 拒绝，不生成目录 |

Windows 的[原始结果](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/windows-official/agent-attempt1/external-agent-result.json)与 Ubuntu 的[原始结果](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/ubuntu-official/agent-attempt1/external-agent-result.json)均确认：工作区从修订 `0`、零元素变为修订 `3`、三个元素，产生三个恢复点；计划预览和应用成功；Minecraft 实际主菜单可见，三个渲染标记齐全并稳定运行 10 秒；通过游戏菜单正常退出，任务最终为 `succeeded`。安装版两次正常退出均为 `0`，工作区重开后身份保留，旧令牌在新端点收到 `HTTP 401 / TOKEN_INVALID`。两次关闭均移除连接描述文件，授权撤销后的新建请求返回 `TASK_AUTHORIZATION_REVOKED` 且不生成目录。

两端分别从空资源缓存下载了 `1.21.1-17.json` 索引对应的 3,888 个不同资源对象，每端合计 824,678,547 字节；[Windows 校验](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/windows-official/cold-download-verification.json)和 [Ubuntu 校验](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/ubuntu-official/cold-download-verification.json)确认 SHA-1、大小全部匹配，修改时间均不早于对应轮次开始。后续构建与客户端步骤可复用同一轮下载结果。

## 资源回收与最终归档

提交后续跑记录了 621 次宿主机采样：最多一台 VM 运行，分配内存始终不超过 4 GiB。宿主机最低可用内存约 0.479 GiB，期间多次回收已授权后台应用的工作集，因此不宣称全程内存充裕。[最终状态](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/final-resource-state.json)确认两台 VM 均为 `Off`、分配内存为零，验证 Java 进程为零，监控和本轮 VM 控制台已关闭，三个既有检查点全部保留。

结束时 `D:` 剩余约 41.90 GiB，宿主机可用内存约 5.92 GiB。累计实际清理约 40.51 GiB；最终剩余空间同时受新候选、安装回退备份、两端验证数据和虚拟磁盘增长影响，不能直接用清理量加初始剩余空间推算。

续跑归档的[哈希清单](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/final-manifest.json)核验了 243 个文件，其中包含 12 张成功轮次截图；源码 ZIP 内每个文件和最终模组 JAR 也完成核对。[凭据扫描](../../evidence/stage16/2026-09-12/cold-cache-and-storage/resumed-d9fb1458/final-credential-scan.json)检查普通文件及 ZIP/JAR 内容，未发现私钥、完整 Bearer 请求头或字面令牌值。历史审批检查点、不可见弹窗失败、开发夹具失败及两次网络失败保持独立，没有改写为成功。

收尾检查通过：所有本地 Markdown 链接可解析；产品状态校验为 8 个生成器、24 项门禁、`betaEligible=true`；`git diff --check` 通过，`src` 下没有提交后的未提交改动。这些静态检查不扩大上文实际安装和玩法验证范围。

## 证据边界

创建耗时包含依赖下载和配置。两次构建耗时取产品任务的开始、完成时间；客户端任务总耗时还包含资源下载、观察和人工桌面操作，不当作纯启动性能。

默认投射物夹具仍有两项缺失资源健康诊断；Windows 首次创建日志还有本地历史初始化锁警告，后续 MCP 恢复点正常生成。不宣称项目健康全绿。主菜单渲染不替代额外玩法断言，本轮不增加陌生 agent 案例或既有 17 个 GameTest 用例数量。

验证过程中只回收用户已允许清理的后台工作集，没有关闭这些用户应用。Ubuntu 空闲锁屏后使用已授权账号解锁，临时空闲抑制仅随验证进程存在，没有修改持久锁屏设置。完整令牌由界面复制到掩码输入框，通过私有 stdin 传递；验证程序在仍持有令牌时逐一检查应用日志与自动化审计，未保存该令牌。安装和用户数据备份留在私有目录。

本次补齐 Stage16 C 的完整冷缓存范围，与 A/B/D 的既有独立证据共同完成[阶段既定验收](../roadmap/stage-16-agent-reliability.md)。各段使用的候选和缓存条件保持分别记录，不能把历史 17 个 GameTest 表述为在 `d9fb1458` 上新增执行。Java 修复单独提交为 `d9fb1458`；用户随后授权推送，报告与证据纳入独立的 Stage16 收口提交。候选安装包没有公开发布。
