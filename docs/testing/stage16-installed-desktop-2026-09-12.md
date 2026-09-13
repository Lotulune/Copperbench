# Stage16：低内存安装版桌面验证与 OpenGL 误判修复

后续：五个修复文件已提交为 `c0178f6b`，新候选的 Windows 负向 OpenGL 检查、宿主机真实图形与 Ubuntu 安装回归均通过，见[修复候选安装回归](stage16-installed-regression-2026-09-12.md)。下文保留 `33ceb6e9` 和修复提交前的历史状态。

状态：**Ubuntu 安装版的授权、创建、认证 MCP、Run Client 和正常关闭通过。Windows 安装版的授权、创建、MCP 编辑与构建通过，客户端因虚拟机缺少 OpenGL 支持而失败，同时复现了退出码为 0 时被产品误报成功的问题。该误判已在本地源码修复，Stage16 C 尚未关闭。**

## 候选与资源范围

安装版实测固定在 `33ceb6e9a68aab2b2fcd9153d6c4f23f4d85e6c5`，沿用[低内存安装重放](stage16-low-memory-installed-2026-09-12.md)核对过的两个安装包。完整哈希与原始结果保存在[归档摘要](../../evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/summary.json)。

| 基线 | 安装包 SHA-256 前缀 | 应用 JAR SHA-256 前缀 | 本轮结果 |
| --- | --- | --- | --- |
| Ubuntu 24.04、GNOME Wayland | `6ae06ca2033d3f40` | `473f9b06af5ee6e2` | 图形窗口、认证 MCP 和客户端生命周期通过；`dpkg -V` 无差异 |
| Windows 11、Hyper-V G7 | `717d7de82a30209b` | `1f049a071cfe709f` | 创建和 MCP 构建通过；OpenGL 客户端失败，产品错误地报告任务成功 |

两台虚拟机都保留最高 **4 GiB** 的动态内存配置，并且只运行一台。507 条[主机采样](../../evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/host-memory.jsonl)没有出现同时运行或超出 4 GiB 的情况。Ubuntu 使用了已有交换空间，因此这些结果不构成“完全不使用 swap”的承诺。原先的两个 Saved 标准检查点仍在；本轮结束后两台虚拟机均正常关机、分配内存为 0。

主机清理只回收明确列出的后台应用工作集；[汇总](../../evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/host-reclaim-summary.json)记录了回收前后内存，未关闭或暂停这些应用。验证完成后另行关闭了两个已关机虚拟机的连接进程。

## 用户授权、创建与 MCP

用户分别确认了产品显示的本机授权窗口。两份授权均为 4 小时，只包含 `create,edit,build,test,run_client`，没有服务器 EULA 或恢复历史权限。

| 平台 | 授权根目录 | 新建工作区的首次最终 JSON 响应 |
| --- | --- | --- |
| Ubuntu | `/home/stage15/s16-33ceb6e9` | 约 41 秒，依据启动记录与 stdout 文件时间 |
| Windows | `C:\Temp\S16-33ceb6e9` | 约 135 秒，进程总耗时 135.148 秒 |

随后通过安装版 `bootstrap create-workspace --task-authorization … --no-prompt true` 创建新的 Fabric 1.21.1 工作区。两端均没有系统 Java、Javac、Gradle 或 Git；Windows 外部验证器使用临时目录中的 Python，不依赖系统开发环境。

认证 MCP 验证复用公开的[安装版 Agent 验证器](../../scripts/verify-stage15-linux-installed-agent.py)，通过 SDK 附加用户签发的授权 ID，执行：

1. 初始化连接、读取工作区，验证每页 1 项的游标遍历。
2. 创建物品，预览并应用带恢复点的 projectile 创建计划。
3. 等待第一次真实构建成功。
4. 使用旧 revision 写入并收到 `WORKSPACE_REVISION_CONFLICT`，重新读取后重试提交。
5. 等待第二次构建成功，读取 revision 3 和三个新增元素。

两端均完成上述步骤。Windows 两次构建分别耗时 **52.611 秒、18.288 秒**，提交调用分别在约 **25.6 ms、11.7 ms** 返回接受结果；这些是不同指标，不把任务接受当成构建完成。Ubuntu 保留了审计、启动记录与最终断言结果，没有采集每次 SDK 请求的精确往返耗时。

依赖缓存属于已有机器缓存，两个工作区是新建的。Windows 客户端还需要补齐资源；末次采样确认 3888 个唯一资源对象、共 824,678,547 字节已齐全，未测量其中本轮新增下载的总字节数。这些结果不能当作完整冷缓存基准。

## Ubuntu 客户端与正常关闭

[完整结果](../../evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/ubuntu/agent-attempt1/external-agent-result.json)记录了 `run_client` 的 LWJGL、ResourceManager 和 blocks atlas 标记，之后任务继续运行至少 10 秒。原生窗口显示 Minecraft 主菜单，打开 Options 后能返回主菜单，最后通过 Quit Game 正常退出。任务 `30cd94df-06e1-4da3-8a2b-49be1b6c6d8e` 最终为 `succeeded`。

正常关闭 Copperbench 后，描述文件被删除，旧令牌与旧端点无法继续查询。再次打开安装版工作区，revision 3、三个元素和恢复点仍然存在，再次正常关闭后描述文件同样消失。撤销任务授权后，新的创建请求返回 `TASK_AUTHORIZATION_REVOKED`，目标目录没有生成。

原生操作由开发 agent 完成；两次本机任务授权由用户确认。此轮不增加 B 段的陌生 agent 案例或玩法断言数量。默认 projectile 夹具在两端重开后均有两项资源诊断，因此不宣称该夹具的全部项目健康检查通过。

## Windows 的失败与边界

[窗口证据](../../evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/screenshots/windows-minecraft-opengl-failure.png)与[Minecraft 日志](../../evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/windows/minecraft-latest.log.txt)记录了：

```text
GLFW error 65542: WGL: The driver does not appear to support OpenGL
```

[显示适配器记录](../../evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/windows/graphics-facts.json)为 Microsoft Hyper-V Video 和 Microsoft Remote Display Adapter。客户端没有到达资源重载、atlas 渲染就绪标记，外部验证器在其 600 秒就绪等待结束后退出 1；原生错误窗口确认关闭后，Gradle 又输出 `BUILD SUCCESSFUL`。

[真实 MCP 响应](../../evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/windows/captured-run-client-response.json)仍将任务 `90a4da0e-17fb-4beb-8249-5d992d059477` 标成 `succeeded`、诊断错误数为 0。该 JSON 从后续验证器断言保留的完整响应解析而来，对应原始日志也在归档中。**这是已复现的状态误判，不能据此放行客户端门禁。**

Windows 工作区仍可正常关闭和重开，三个元素、revision 3 保持不变；应用退出码为 0，描述文件消失，监听端口关闭。授权撤销后的创建也被拒绝。但 Windows 的“用旧认证令牌再次请求”及精确令牌与审计内容比较没有执行完成，明确保持未验证；Ubuntu 已完成这两项。

保留了两个后续检查尝试：一次是辅助脚本没有处理响应 `data` 为 `null` 的情况，修正后以已捕获事件重放检查通过；另一次由于真实任务却报告 `succeeded` 而触发断言，由此保留了上述误判证据。归档校验另按授权 ID 前缀检查可能被截断的审计 `parameterSummary`，保留其原文。没有把失败尝试改写成客户端通过。

## 本地源码修复

问题位于 [GradleWorkspaceTaskGateway](../../src/main/java/dev/copperbench/generator/GradleWorkspaceTaskGateway.java) 的 `RUN_CLIENT` 完成判断，以及 [Fabric1211ProcessRunner](../../src/main/java/dev/copperbench/generator/fabric/Fabric1211ProcessRunner.java) 原先只针对 Linux 的图形错误分类。

修复识别实测的 Windows WGL/OpenGL 错误；即使进程退出码为 0，或已经出现模组就绪标记，也返回失败诊断 `FABRIC_RUN_CLIENT_WINDOWS_OPENGL_INITIALIZATION_FAILED`。同时增加中文说明，避免只显示容易误解的“退出码 0”。现有 Linux 就绪后警告处理和正常客户端关闭逻辑保留。

[本地测试证据](../../evidence/stage16/2026-09-12/windows-opengl-zero-exit-fix/source-and-tests.json)对应当时尚未提交的源码，两个定向测试类共 **26 项通过，0 失败、0 跳过**。新增回归覆盖平台分类，以及有/无模组就绪标记时的零退出码误判。Gradle 与测试 JVM 分别限制为 768 MiB，只有一个 worker；中文检查为 246/246。

首次源码测试启动遇到已知的本机 loopback 问题，使用现有产品代码所采用的、仅对该进程生效的 TCP 回退后继续。另一个探索性的真实子进程回放在主机上因 `CreateProcess error=5` 无法启动 `cmd.exe`，没有形成通过证据；最终套件验证捕获错误的分类及应用服务任务结果，不冒充已修复安装包的真实图形复验。

源码修复没有回填到已验证的 `33ceb6e9` 安装包。仍需生成包含修复的新候选，在有 OpenGL 支持的 Windows 环境完成安装版客户端与认证连接关闭复验；也应复测缺少 OpenGL 时返回失败。当前没有提交、推送、发布或修改 CI。

## 复核入口

归档包含 15 张截图、两端原始日志、MCP 响应、授权结果、源码快照和构建产物；118 个归档文件通过哈希校验。验证脚本只核对已声明的范围，不将 Windows 的部分结果提升为完整通过。

```powershell
python evidence/stage16/2026-09-12/installed-desktop-33ceb6e9/verify.py
python evidence/stage16/2026-09-12/windows-opengl-zero-exit-fix/verify.py
```
