# Stage16：c0178f6b 安装版回归

2026-09-12，用户批准提交五个 OpenGL 修复文件，并单独批准备份、升级宿主机安装以完成真实图形验证。修复提交为 `c0178f6b933825921cc3fb3f8f8ef8aa0874e688`。本轮三个回归均通过；整体 Stage16 继续保持进行中，完整冷缓存覆盖尚未完成。

[归档摘要](../../evidence/stage16/2026-09-12/installed-regression-c0178f6b/summary.json)包含 192 个受清单校验的条目和 13 张截图；[校验入口](../../evidence/stage16/2026-09-12/installed-regression-c0178f6b/verify.py)验证候选来源、真实任务结果、授权、源码/JAR、连接关闭和内存上限。此前 `33ceb6e9` 的[零退出码误报成功记录](stage16-installed-desktop-2026-09-12.md)保留，不以修复后结果替换。

## 结果

| 环境 | 安装、授权、创建 | 认证 MCP、两次构建、修订冲突恢复 | 客户端 | 关闭及重开 |
| --- | --- | --- | --- | --- |
| Windows 11 Hyper-V，最高 4 GiB | 通过 | 通过 | 真实触发 GLFW/OpenGL 错误；Gradle 退出码仍为 `0`，产品正确返回 `failed` 和稳定诊断 | 两次正常退出；旧连接失效；新会话以 `HTTP_401 / TOKEN_INVALID` 拒绝旧令牌 |
| Windows 11 宿主机，实际安装版 | 通过 | 通过 | Minecraft 主菜单真实渲染，资源标记齐全，连续 10 秒保持运行；从游戏菜单退出后任务为 `succeeded` | 同上 |
| Ubuntu 24.04 Wayland Hyper-V，最高 4 GiB | 通过；安装后 2,681 个文件的 SHA-256 和权限匹配 | 通过 | 同上，使用安装包自带运行时 | 同上 |

Windows 负向任务的诊断为 `FABRIC_RUN_CLIENT_WINDOWS_OPENGL_INITIALIZATION_FAILED`，消息键为 `diagnostic.task_client_opengl_initialization_failed`，诊断参数明确保留 `exitCode=0`。这证明本次修复覆盖了实际失败路径；Windows 虚拟机结果不计作正常图形渲染通过。正向图形证据来自宿主机实际安装版。

三个工作区均由修订 `0`、零元素开始，经 MCP 变为修订 `3`、三个元素。所有受保护的操作使用本轮界面签发的任务授权；审计中的完整令牌检查通过。回归结束后撤销授权，再次创建返回 `TASK_AUTHORIZATION_REVOKED`，没有生成被拒绝的目标目录。

## 固定候选

| 文件 | SHA-256 |
| --- | --- |
| Windows EXE 安装包，855,081,917 字节 | `4e1c70c7c7c99cb6853ece539232c17973bec0e09e2ad24d6af547967592b75d` |
| Windows 应用 JAR | `3e68b305f5fe0364c776e08ad6624d49d21dd736f71b8f4ac6ddb623252962cc` |
| Linux DEB，968,416,582 字节 | `4308bd319c4382a1e281a4bd90fd5a8574027bc397d382aef9df6bc3e32f0d51` |
| Linux 应用 JAR | `ba2af8159d04236e127e34f00e3ca8fdb9d429b25663dbd12b0f62c7fb5b2a50` |
| Linux 标准 TAR | `31feb17d5e4ac619459b37e592d3be5fdaa90714ff9d0de9dbc31b1dfe0502ae` |

Windows 使用标准 `exportWindowsZip`、`buildInstallerWin64` 任务构建，耗时 6 分 48 秒，单工作进程、768 MiB Gradle 堆。五个提交文件与此前通过 26 项定向测试的文件哈希一致；[历史测试快照](../../evidence/stage16/2026-09-12/windows-opengl-zero-exit-fix/source-and-tests.json)保留提交前状态。

Linux 使用同一提交的标准 `tarLinux64`、`prepareDebLinux64` 输出，耗时 2 分 18 秒。未改变的 JDK 和 Gradle 构建缓存复用已校验旧候选的运行时文件，应用 JAR 单独重新生成。随后在 Ubuntu 用 `dpkg-deb --build --root-owner-group --threads-max=1 -Zgzip -z6` 封装，以控制内存；安装后逐文件比对[负载清单](../../evidence/stage16/2026-09-12/installed-regression-c0178f6b/linux-package/linux-payload-manifest.json)。这是本地回归候选，不构成发布或支持范围升级。

## 耗时与内存

| 环境 | 创建耗时 | 首个创建 JSON，约值 | 首次 MCP 工作区查询 | 两次构建 |
| --- | ---: | ---: | ---: | ---: |
| Windows VM | 126.133 秒 | 125.114 秒 | 0.041 秒 | 47.143 / 15.504 秒 |
| Windows 宿主机 | 232.369 秒 | 232.173 秒 | 0.038 秒 | 31.070 / 15.472 秒 |
| Ubuntu VM | 30.302 秒 | 28.982 秒 | 0.037 秒 | 16.130 / 10.063 秒 |

创建命令只有一条终态 JSON；首次反馈约值由原始 stdout 修改时间估算，包含文件时间精度误差。MCP 查询时间来自客户端实测。游戏等待观察、桌面操作和空闲锁屏时间不解释为构建耗时。

1,143 次宿主机内存采样显示：最多一台验证 VM 运行，分配内存始终不超过 4 GiB。结束时两台 VM 均为 `Off`、分配内存为零；两个原始 Saved 检查点保留。验证 Java 进程、VM 控制台和监控均已结束。[最终状态](../../evidence/stage16/2026-09-12/installed-regression-c0178f6b/final-host-state.json)记录宿主机约 6.21 GiB 可用内存。

## 操作记录与边界

- 宿主机旧安装的 808 个实际文件完成备份和逐文件哈希核对，升级前备份的四个用户状态文件在安装后保持一致。旧 JDK 的自引用 junction 单独记录并在升级前移除；失败的重复备份副本已清理，完整备份保留在本轮私有构建目录。
- Ubuntu 首次凭据传递因空闲锁屏后输入为空而以 `2` 退出，尚未执行 MCP 操作；该记录保留。恢复已授权会话、重新复制配置后，第二次传递及全部回归通过。临时 GNOME 空闲抑制器只随验证进程运行，没有修改持久锁屏设置；VM 关闭后已释放。
- 所有保存的截图来自测试界面；账号备份、私钥、完整登录密码和完整 MCP 令牌不进入归档。三次成功验证都在持有令牌时检查了自动化审计不含该完整令牌。
- 此次复用默认投射物夹具仍产生两项缺失资源健康诊断，不宣称项目健康全绿。游戏菜单渲染和正常关闭通过，不替代额外玩法断言。
- 使用已有依赖和资源缓存；未覆盖完整冷缓存。维护回放不计为陌生 agent 新案例，也不增加既有 17 个 GameTest 用例数量。
