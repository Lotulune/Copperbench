# Blockbench M4：安装版实际验证（首轮完成）

整理提交时已将关键截图和哈希结果归档到[版本化验收证据](../../evidence/blockbench/2026-09-14/README.md)。本文的 `.tmp/` 路径仍指原始本机完整记录；后续分支整理和重新验证见[提交前检查](modeling-scripting-closeout-2026-09-14.md)。

截至 2026-09-14，R7 Windows 安装版与 Ubuntu TAR 解包安装版已完成真实社区 MCP 建模、原生编辑保存、编辑器开启时回导、绑定、构建、JAR 字节核对及游戏视觉验收，首轮 PRD 范围完成。历史章节保留各阶段失败与当时待办，最终结论见末尾 R7 两平台记录；不将历史“进行中”描述当作当前状态。测试范围已获用户授权，包括候选和独立编辑器、插件安装及本轮必要权限。Windows 临时内存调整已恢复 4 GiB，临时 Mesa 已移除。

## 已取得的真实证据

- Windows 11 测试 VM `Copperbench-G7` 中，原生首次启动引导可见、可以跳过，跳过偏好在 R2 → R3 升级后保留。
- 独立安装的官方 Blockbench 5.1.6 Windows portable 已运行。社区插件为 `jasonjgardner/blockbench-mcp-plugin` 1.7.0，固定源码提交 `b187b4b056f0efafcc573335400ecbb21ad26ecc`，本地构建插件 SHA-256 为 `b97f921968701df4d20103f1e7ab85823341eb2981358392d6f7921eb6d840db`。没有把它标记为官方 MCP，也没有捆绑进 Copperbench。
- 已安装 R3 的 `BlockbenchEnvironmentService` 和 Copperbench MCP 均完成真实 `initialize` / `tools/list`，返回协议 `2025-11-25`、社区服务 97 个工具；路径选择后编辑器报告 `ready`、版本 `5.1.6`。
- 测试工作区由已安装产品的 bootstrap 创建，路径为 `C:/Temp/Copperbench-Blockbench-R2/workspace-r3/bb_gate_win.mcreator`，工作区 ID 为 `fc44b199-5ca8-47ba-89a4-7467883d66b8`。目录中的 R2/R3 字样是测试目录名称，不代表后续安装版本。
- Copperbench MCP 建立任务 `1fdbb73c-96e7-4294-8b82-edc84d1ad353`；社区 MCP 实际创建 `java_block` 项目、三段几何和 16×16 PNG 纹理，绘制边框与亮色区域，再通过 Blockbench 的 `project`、`java_block` codec 导出。PNG 来自实际 `get_texture` 返回值，未用手写夹具代替。
- 编辑器保持运行时，任务完成、三文件预览和回导成功，工作区修订从 0 变为 1。正式资源分别为 `models/blockbench/verification_lamp.bbmodel`、`src/main/resources/assets/bb_gate_win/models/custom/verification_lamp.json`、`src/main/resources/assets/bb_gate_win/textures/block/verification_lamp.png`。
- 模型 JSON SHA-256 为 `2a470b0c0a1dc67c378ec2dc7bb07ccd6277dcdce8f7fd4b6b87a92b0487a2d9`；PNG SHA-256 为 `89f87126036f7b8d91c30c56283b6dc2ab39b21097daf4cb799b41049bba9ead`。这些是回导文件证据，还不是 JAR 或游戏正确性的证据。

本地原始请求、结果与截图保存在 `.tmp/blockbench-validation/windows-evidence/`。MCP 令牌只经产品界面复制到 VM 私有目录，不进入本记录。社区服务测试时实际监听 `::`；本机地址显示不能解释为插件只绑定回环接口。

## 安装实测发现的问题

1. **R2 拒绝官方便携版文件名。** 原文件选择仅接受 `Blockbench.exe` 等名称。已增加严格的 `Blockbench_<major>.<minor>.<patch>_portable.exe` 规则，仍拒绝安装器文件名；R3 中通过原生文件选择器实测接受，并由实际 Core 查询确认版本。
2. **R3 的工作台连接检测因 `crypto.randomUUID is not a function` 失败。** 内嵌 JCEF 环境不能直接使用该方法。连接、建模任务、回导三个组件已改用项目现有 `safeRandomUUID`。三个 E2E 文件现在预先移除 `crypto.randomUUID`，12 项测试（1920 和 1366 两种视口）全部通过。R4 两平台候选已构建，安装后的界面复核仍待完成。
3. **R3 创建默认关联方块持久化失败。** 实际上游模板 `block/block.java.ftl` 读取 `data.soundOnStep` 时缺少值，产品返回 `WORKSPACE_PERSISTENCE_FAILED` 并回滚，修订仍为 1。已补充默认 `StepSound(STONE)`；真实产品入口的回归测试与新候选验收正在进行。不能通过手工造元素文件跳过该失败。

首次插件文件权限等待超过客户端请求超时的尝试保留为失败；后续实际导出成功结果单独记录。界面数值编辑尚未完成完整的手工修改、保存复核，因此不将 MCP 导出成功冒充该验收项。

## 固定候选

| 候选 | Windows 安装器 SHA-256 | Linux TAR SHA-256 | 共用应用 JAR SHA-256 |
| --- | --- | --- | --- |
| R3 | `1699b1fae1305dbc5073d95a74cc45423b9a433bda3ee24af520c3e72e7e74c8` | `b4d72d65fd07defe8e1683186b07d610976dc5132207661a2f746e48a22ea66b` | `c95dda628c5dafa93dbce35e7ad82dd254b4937113ace4d99800ca8597b68c91` |
| R4 | `f6ceb740d4e45914eade961b0037df37b77625d97b103fafd95d4616e9d0cc1b` | `840dc72e77fca6abe3e9bacbb7f9eb2b8bfb53a4718a4a3dea5e4f0dd12f3046` | `7dd6d03b4900444404e07cd6ab82c81fbebbd67fbde4e5ad29875675a2d5a34c` |

R4 正常执行 `buildInstallerWin64 exportLinuxCandidate --no-daemon --console=plain`，5 分 7 秒成功；日志 `.tmp/gradle-external/82c5cd6619214558852558029c14bbc8.log`。候选已独立保存在 `.tmp/blockbench-validation/candidate-r3/` 和 `candidate-r4/`，没有发布、提交或推送。

## 未关闭的验收

默认方块修复后的安装验收、R4 或后续候选界面验证、元素绑定、构建及 JAR 字节比对、真实游戏内显示、完整手工编辑保存与重开、Ubuntu 同候选闭环、卸载非干扰检查及 VM 内存恢复仍待完成。Windows VM 历史上曾缺少 Minecraft 所需 OpenGL，后续必须以本次实际客户端结果判断，不能复用旧主菜单截图或仅据构建成功判定显示正确。

## 2026-09-14 继续验证

R5 已通过正常 Windows 安装器升级，安装核心 SHA-256 为 `ffc31d04b20ec48e0f8ec7ac695ba3d9919938efee2c6feb7d988b02cd5c2daf`。升级前后逐项核对工作区文件、三项导入资源与 Blockbench 配置，哈希均相同；独立 Blockbench 的四个进程保持运行。此前 R3 产品进程在成功回导后被有意终止，用于检查中断后恢复，不记作正常退出。

R5 已补齐默认方块的 `soundOnStep=STONE`、`luminance=0`，实际安装产品的原生 API 成功创建了方块。模板生成、保存与重开的回归通过，日志 `.tmp/gradle-external/573f1a632afd441b885b5044c3d5a0fd.log`。Windows 安装器 SHA-256 为 `c3d8fb949c7192a4562310fcc8302e091e666415d658fb8380158dc7b174340e`，Linux TAR 为 `4c78ecaa0088dee234e170a4744d004b605f2a14491d2fbdf04df6317952191a`；固定副本在 `candidate-r5/`。

继续实测发现两个此前测试替身未覆盖的问题：

- 回导后的修订号只在内存中变为 1，重开后仍为 0。资源文件及导入凭据均保留，但不能称作完整事务通过。修复后，回导及恢复在记录成功前同步持久修订号；元数据写入失败会撤销该修订并保留或恢复文件日志。
- `bind_blockbench_model` 到达真实上游持久化入口时，操作分支没有接入，报 `Operation is not a content mutation: BIND_BLOCKBENCH_MODEL`。已接入现有元素更新分支。

针对上述问题，真实工作区回归现在覆盖回导、关闭前读取磁盘修订、创建方块、绑定、重开与导入幂等重试；另有元数据失败回滚和恢复重试测试。2026-09-14 的 23 项定向测试全部通过（真实工作区 2、回导服务 12、MCP HTTP 9），日志 `.tmp/gradle-external/c2c99a5863d44372bd3483e1ee2ef165.log`。含修复的 R6 正在打包，尚未据此宣布安装闭环或游戏验收完成。

### R6 安装、回导与构建复核

R6 两平台正常打包成功（3 分 48 秒，日志 `.tmp/gradle-external/a2bcdfb724304bdda3161245ebb27c2b.log`），Windows 安装器退出码 0，实际安装核心与固定候选一致：

| 产物 | SHA-256 |
| --- | --- |
| Windows 安装器 | `afab052353324b21c07995236a57d683d5c262e4de7fc4584762561b0d9c5db6` |
| Linux TAR | `5b89fd58ee39eef2659323f0a78c30043e60ff0c2abd0d60bf792c9b081a6a05` |
| 共用核心 JAR | `6e1e1ff1176ff7e2da9b443fe32ccd53ee92672350aba106c9f8e094bef3a435` |

在 R6 中另建任务 `1d879aff-50c5-4380-b890-92878d013176`。通过 Blockbench 界面原生步进按钮将顶盖高度从 4 改为 3，社区 MCP 读回确认 `cap.to=[15,15,15]`。文件名输入在虚拟机连接中未成功传入，故使用 Blockbench 自身 codec 导出到该任务编辑目录；不把这一点写成原生文件选择器完整通过。随后 R6 的实际原生 API 完成候选冻结、三文件预览、回导（修订 3）、更新既有方块绑定（修订 4）与构建成功。全程 Blockbench 保持开启。

重新打开安装产品，修订仍为 4，导入凭据重试返回 `completed` / `idempotentReplay=true`，未增加修订。再次从 R6 产品界面取得一次性测试令牌，经认证的 Copperbench MCP 也成功查询修订 4、探测社区 97 个工具及幂等重试。界面实测显示“已检测到编辑器（5.1.6）”“握手成功，工具可发现（本页 97 个工具）”，未再出现 JCEF UUID 错误。

最终模组 JAR 逐字节核对通过：

| 项目 | 核验结果 |
| --- | --- |
| `bb_gate_win-1.0.jar` | SHA-256 `384d379b87558e36e900992a6e8353caeb82b2649be83099df98e9265b0904ce` |
| 自定义模型 JSON | 与实际 codec 导出相同；SHA-256 `96372b66d5df6b1580f4a51f215e5cf362bb35b1cea1274844ec31bf03f8fdc8` |
| PNG | 与真实纹理返回字节相同；SHA-256 `89f87126036f7b8d91c30c56283b6dc2ab39b21097daf4cb799b41049bba9ead` |
| 方块模型引用 | `bb_gate_win:custom/verification_lamp_r6` |
| 手工修改后几何 | JAR 中 `cap.to=[15,15,15]` |

原始证据位于 `.tmp/blockbench-validation/windows-evidence/`，其中 `native-r6-roundtrip-result.json`、`native-r6-reopen-result.json`、`core-r6-replay-result.json`、`r6-ui-mcp-ready.png` 和 `r6-final/verification.json` 分别对应上述步骤。早期 R6 的第一次绑定构建沿用请求文件名 `native-r5-bind-build.json`；该次 R6 结果另外保存为 `native-r6-bind-build-result.json`，不将名称中的 R5 当作安装版本。

### 实际游戏失败与剩余条件

通过 R6 安装产品在交互用户会话启动客户端，任务 `ed60dd6f-6ea0-416e-b6f0-7d47b6fec490` 实际显示 `GLFW error 65542: WGL: The driver does not appear to support OpenGL`。产品正确返回 `failed`、`FABRIC_RUN_CLIENT_WINDOWS_OPENGL_INITIALIZATION_FAILED`，并保留 `exitCode=0`；没有把 Gradle 的零退出码当作客户端成功。该次没有进入游戏，不能提供游戏内模型视觉通过结论。

已准备社区 `pal1000/mesa-dist-win` 26.2.0 的 MSVC 包，下载来源为项目 GitHub release，归档 SHA-256 `dcb2719ef346dab5b609fcb193a5f13cfc4b0502e3f4de1ad43d349477402f47`，两个 DLL 的版本信息均为 `26.2.0.0`；这是固定下载的哈希记录，并非独立签名证明。拟仅在测试 Java 21 目录临时使用软件渲染，已单独请求授权，尚未复制进虚拟机或执行。Mesa 官方文档说明可按应用放置 DLL：[LLVMpipe Windows 使用说明](https://docs.mesa3d.org/llvmpipe.html)。Smart Search 对 release 页面提取为空，Exa 路由缺少配置；软件包来源另由仓库说明、实际 HTTPS 下载及解包版本信息核对，未隐藏检索失败。

Ubuntu 验证机保留既有 `stage15-operator` SSH 安装记录，但当前未找到先前使用的私钥，SSH agent 也不可用。已请求用户提供私钥的本地路径，不要求粘贴凭据。Linux 同候选安装与图形闭环仍未执行。

结束本轮 Windows 检查前，Copperbench 已正常关闭并移除 MCP 连接元数据；Blockbench 使用“全部保存”正常退出，编辑源仍存在且顶盖坐标保持 `[15,15,15]`，见 `r6-normal-close.json`。完整 PRD 继续保持未关闭，等待上述环境条件后继续游戏与 Ubuntu 验收。

Windows VM 随后正常关机，启动内存已恢复为原来的 `4294967296` 字节（4 GiB）；两台测试 VM 均已停止、分配内存为 0，见 `.tmp/blockbench-validation/vm-state-restored.json`。Mesa 尚未加入安装目录；本轮完整 MCP 令牌未出现在工作区自动化审计中，见 `r6-final-integrity.json`。

### R7：实际游戏发现并修复图集与物品模型问题

2026-09-14 用户明确授权继续使用临时 Mesa 软件渲染，并允许测试机密码登录。Windows 使用增强会话及原生按键成功登录，未改登录配置。仅放置 DLL 的首次 Mesa 尝试在资源加载时退出；显式给测试子进程设置 `GALLIUM_DRIVER=llvmpipe` 和 `LIBGL_ALWAYS_SOFTWARE=true` 后，进入 Minecraft 1.21.1 主菜单及新建的临时超平坦创造世界。

R6 实际放置 `bb_gate_win:verification_lamp` 后，三段几何存在但贴图紫黑。日志报告 `minecraft:textures/atlas/blocks.png:bb_gate_win:native_texture` 缺失，另报 `models/item/verification_lamp.json` 缺失。版本匹配的原版 `minecraft-client.jar` 中 `assets/minecraft/atlases/blocks.json` 表明默认目录源包含 `block`、`item`，未包含纹理根目录。此前 JAR 文件存在及字节一致不能证明图集加载成功；失败截图为 `windows-evidence/r6-game-missing-texture.png`。

修复内容：回导验证自定义贴图引用必须位于 `block/` 或 `item/`，否则返回 `MODEL_TEXTURE_ATLAS_PATH`，并说明当前流程不支持自定义图集；真实插件工作区生成路径为绑定方块补写物品模型引用。20 项定向回归（回导 14、Fabric 生成器 4、真实工作区 2）通过，日志 `.tmp/gradle-external/43cb2a76bb7249d3aaee751d668f6e8b.log`。新测试初次因括号语法错误未编译，修正后重新运行通过，未把初次运行记为通过。

R7 正常两平台打包成功，日志 `.tmp/gradle-external/53c7b980beb9406a9195d24536520d7c.log`；固定副本在 `candidate-r7/`：Windows 安装器 SHA-256 `347dbe146aadef1e375b20a98677fa9bd6144c9bbabc7caf42e27a5f68eea819`，Linux TAR `862c8101aa38719e230987d6cdd4a3f8dd3d257f50c85e85376bb4adb207f3d1`，共用核心 `e67f1248ca7aa8f04c5f4f2dbf5c76a77ec580c715b819a4e8d59dc3d423d4dc`。

卸载非干扰实测通过：正常卸载 R6，退出码 0、主程序被移除、独立 Blockbench 原进程仍存活，所选工作区文件、模型、编辑器程序及配置哈希不变，见 `r6-uninstall-retention.json`。首次卸载驱动参数使用正斜杠导致未删程序；改为 NSIS 接受的原生反斜杠绝对路径后通过。R6 的临时 Mesa DLL 已移除；R7 安装成功后为继续游戏验收临时加入同一对 DLL，结束时仍须移除。

R7 新任务 `40a2412e-9404-4da8-8ef7-a251801e0d66` 已使用 Blockbench 原生 File → Save Project As 文件选择器保存到任务编辑目录，并确认替换仅为新任务初始化源。普通文本输入不能传入 VM，增强会话原生按键可用；正斜杠完整路径被文件选择器拒绝，改原生 Windows 路径后保存成功。磁盘源保留手工修改的 `cap.to=[15,15,15]`。社区 MCP 将纹理文件夹设为 `block`，界面保存项目后，实际 codec 导出引用 `bb_gate_win:block/native_texture`。

R7 安装核心哈希匹配固定候选。真实原生 API 完成冻结、预览、回导（修订 5）、绑定（修订 6）及构建成功，编辑器保持运行。JAR SHA-256 `22621f3ed8c97d57c249ebf67ea983eed2b268a0dfae53842346f38e648a38f4`；导出模型 JSON SHA-256 `e1908b813b9557ac4be03a283ecddace1abbc1bcee4bface3423d7502528ca64`，PNG 仍为 `89f87126036f7b8d91c30c56283b6dc2ab39b21097daf4cb799b41049bba9ead`，均与 JAR 字节相同。方块包装指向 `bb_gate_win:custom/verification_lamp_r7`，物品包装指向 `bb_gate_win:block/verification_lamp`。见 `native-r7-roundtrip-result.json`、`r7-jar-verification.json`、`bb_gate_win-r7-1.0.jar`。R7 游戏视觉复核及 Ubuntu 同候选验证正在继续，尚未关闭 PRD。

R7 Windows 游戏视觉复核通过：此前世界内方块现已显示橙色、深色边框和亮色区域，物品可通过游戏命令取得并在快捷栏、手中渲染。手持变换沿用编辑器导出的默认值，未做额外展示调优。截图为 r7-game-block-visible.png、r7-game-item-visible.png。客户端任务 0e9ef5bc-9fbf-4053-a6d3-c30d5c62a361 正常保存退出后为 succeeded。首次重开驱动误用了 MCP 名 get_workspace，原生 API 拒绝；改为实际 get_workbench 后，修订 6、幂等重试 completed / idempotentReplay=true 均通过，见 native-r7-reopen-verified-result.json。编辑器正常关闭，R7 临时 Mesa 两文件已校验哈希后移除；Windows VM 已停止、启动内存 4 GiB。Ubuntu 启动后实际 SSH 支持密码认证，stage15 已使用用户提供的密码登录，不再依赖缺失私钥，也未修改 SSH 配置。

### R7：Ubuntu 24.04 同候选端到端复核

使用同一 R7 Linux TAR 解包安装至 `/home/stage15/blockbench-r7/installed/Copperbench010`，未替换既有 `/opt/copperbench`。这是 TAR 候选安装验证，不是 DEB 升级验证。实际独立编辑器为 Blockbench 5.1.6，社区插件为上述固定提交构建的 1.7.0；原生插件界面加载并按已获授权允许本轮操作。安装核心 SHA-256 与 Windows 共用核心一致。

通过安装产品 bootstrap 创建工作区 `75a788f9-3650-4373-96ae-5049b0b94958`（`bb_gate_linux.mcreator`），原生 API 创建任务 `a25514c5-0663-462f-bcf7-5921db3b0a0c`。社区 MCP 完成真实握手、97 个工具发现、三段几何和 16×16 纹理制作。通过编辑器原生数值步进把顶盖高度调整为 2，最终 `cap.to=[15,14,15]`；使用原生 Save Project 文件选择器保存任务副本。随后由真实 codec 导出 JSON、读取 PNG，编辑器保持运行时完成冻结、预览、回导、创建方块、绑定和构建，修订为 3。

| Ubuntu 核验对象 | 结果 |
| --- | --- |
| 构建任务 | `91bf53e2-14dd-4897-bb72-abf782bc09c6`，`succeeded` |
| 模组 JAR | `baf99054b71f872db6bab81656eca78066c720d1a75db36ab0d2823f6d58c3d5` |
| 实际导出 JSON 与 JAR 内字节 | 相同，SHA-256 `24336f2e829f4aac31d91a2d4f71bd1bcc4743206a8b65640ddaf90efd4e6a4d` |
| 实际 PNG 与 JAR 内字节 | 相同，SHA-256 `89f87126036f7b8d91c30c56283b6dc2ab39b21097daf4cb799b41049bba9ead` |
| 模型与纹理引用 | `bb_gate_linux:custom/linux_lamp`、`bb_gate_linux:block/linux_lamp`；方块及物品包装均正确 |
| 安装产品 JCEF 界面 | 显示编辑器已找到、版本未确认；MCP 握手成功且发现 97 个工具 |
| 游戏视觉 | Minecraft 1.21.1 临时创造超平坦世界中，`bb_gate_linux:linux_lamp` 在 `(3,-60,-2)` 显示正确三段几何及橙色、深色边框、亮色纹理；快捷栏和手持可见 |
| 游戏重开 | 正常保存退出后，第二次启动读取同一世界，方块及物品仍正确 |
| 最终客户端任务 | `8b3f7cd7-5911-48ac-a7c5-92dc74ec511b`，正常退出后 `succeeded`，无任务诊断错误 |
| 产品重开和回导重试 | 修订保持 3；原成功凭据重试返回 `completed` / `idempotentReplay=true`，不重复写入 |

Linux 的编辑器探测状态为 `ready_unverified`，没有把外部已知版本伪装成 Core 已验证版本。Linux 游戏使用现有图形环境，未额外安装 Mesa 或改系统驱动。手持变换沿用导出默认值，模型在手中偏大；名称还显示翻译键，JAR 的语言文件实际只有 `item.bb_gate_linux.linux_lamp`。本轮证明模型、贴图和物品包装加载正确，不把默认展示调优或既有生成器名称翻译问题记为通过。

保留的验证异常：首次辅助程序直接调用 Java 未经 Linux 产品 wrapper，在响应前退出；驱动改为调用实际 `copperbench.sh headless ... api` 后通过。首次客户端驱动在 30 分钟轮询上限后退出，最后观察值仍为 `running`，即使驱动退出码为 0，也未记为客户端成功。延长驱动观察期限后重新运行，以上第二次任务取得明确的 `succeeded`。没有为通过验收改写任务结果。

另外，独立 Blockbench 在 08:25 UTC 长时间等待期间发生 Signal 5 崩溃；日志报告 GPU process launch failed / GPU process isn't usable。系统提示内存不足以分析报告，不能据此直接断言崩溃根因就是内存。未发送崩溃报告。此前原生保存、回导和构建均已完成；保存源及游戏结果未丢失。该第三方图形环境异常与 Copperbench 任务事务结论分别记录，继续检查编辑器重开恢复。

原始证据目录为 `.tmp/blockbench-validation/linux-evidence/`；游戏截图为 `r7-game-block-visible.png`、`r7-game-item-visible.png`、`r7-game-reopened-visible.png`。最终客户端与幂等结果分别为 `native-client-resumed-result.json`、`native-reopen-result.json`；安装、社区调用、手工保存和 JAR 核验的独立结果一并归档。

### 最终收尾与结论

Ubuntu 编辑器使用原来 `/opt/Blockbench/blockbench <任务源>` 启动方式重开成功，没有加入禁用 GPU 参数。实际界面显示保存的三段几何和完整纹理，磁盘源仍为 `cap.to=[15,14,15]`，随后原生关闭按钮正常退出；见 `r7-editor-recovered.png`、`blockbench-resumed.log`。这证明已保存内容可恢复，不等于第三方 GPU 崩溃根因已修复或长时稳定性已保证。

Ubuntu TAR 卸载非干扰检查完成：校验精确安装路径和 R7 核心哈希、确认无安装进程后，仅移除 `/home/stage15/blockbench-r7/installed/Copperbench010`。独立 Blockbench 程序、其 Preferences、社区插件文件、工作区文件、正式模型和编辑副本共 6 项哈希前后完全相同；既有 `/opt/copperbench` 保留。见 `uninstall-retention.json`。本结论仅对应 TAR 目录移除，不替代 DEB 包卸载脚本验收。测试 TAR 和工作区均保留，可重新解包复现。

Linux 50 个结果、日志、驱动源及交付文件已下载到主机 `linux-evidence/`。归档 `linux-evidence.tar.gz` SHA-256 为 `3495d2c79776ae48d9c79fa4886ef2c3c73bf25555d66c3728211d9fcdc9a7b7`，传输后复核一致；截图另存同目录。临时 GNOME 防休眠进程已结束，Ubuntu 正常关机。最终两台 VM 均为 Off、分配内存 0，Windows 启动内存恢复 4 GiB，Linux 保持原 3 GiB，见 `vm-state-r7-final.json`。

首轮 M1–M4 已完成。实际游戏验证范围是 Fabric 1.21.1 Java 方块模型；NeoForge 共用绑定逻辑没有据此宣称游戏内实测通过。模型和纹理加载正确，默认手持比例、名称翻译以及第三方 GPU 长时稳定性仍按上述限制记录。代码未提交、未推送，候选未发布。
