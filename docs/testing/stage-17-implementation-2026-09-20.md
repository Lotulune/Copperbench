# Stage 17 实施记录（2026-09-20）

> 最新进展见 [2026-09-22 连接与运行复验](stage-17-network-and-runtime-2026-09-22.md)：SSH 已恢复，两条 NeoForge 正式行为阻断已关闭；掉落表嵌套合同及旧数据保护修复已进入 v18，并完成双平台公开 SDK 复验。下文保留各历史候选的原始结论。

状态：实施中，**G17-A/B/C/D 均未关闭**。本记录区分源码实现、自动回归与产品验收；不推进 `product-status.json` 或发行状态。

## 固定复现

RF-01 已在当前 Gradle 校验过输入的开发构建中复现。`Stage17BlockConsistencyTest` 使用原始纯结构化请求，命令返回 `committed`，真实定义硬度断言 `3 != 0` 失败。首次日志：`.tmp/gradle-external/b46bbc0175ee4371b7ba3c6c8f58454e.log`。这不是安装候选验证；首次运行的主类编译任务为 up-to-date，修改后的类已重新编译。

测试不打开或修改原共鸣工坊工程。夹具直接包含原失败字段，写入 JUnit 临时工作区；未收录个人凭据、缓存、世界存档或第三方二进制。

## 已实现的增量

| 需求 | 当前实现 | 证据边界 |
|---|---|---|
| S17-01 | 方块共享字段规则；硬度/抗爆、朝向、碰撞盒、库存和依赖字段写入；默认完整碰撞盒；落盘语义核对；单项事务纳入预测生成文件回滚；通用字段转换异常不再吞掉 | 八轨实际定义与源码生成回归；Fabric/NeoForge 1.21.1 实际构建及各 4 项独立 JAR GameTest 通过；其余六轨未做实际构建/玩法验收 |
| S17-02 | 读取声明/有效值差异；编辑器显示有效值；提供两种 Workspace Plan 操作；核对定义和关联源码指纹；采用定义不重写元素定义；未知元数据保留 | 旧版缺陷临时夹具、外部变化拒绝、显式选择；完整安装环境恢复门禁仍待执行 |
| S17-03 | 按活动版本读取 Loom/NeoForm 原版 client JAR；共享模型解析器用于资产健康与回导预览；父模型/贴图继承、覆盖、循环、源指纹和逐位置诊断；界面可展开来源依据 | 实际四版 client JAR 在八轨上下文验证原 5 处父模型及拼写负例；第三方活动依赖清单和运行时覆盖顺序仍待完成 |
| S17-04 | Core 返回去重诊断集合、范围、采集状态和快照 ID；总览/状态栏共用同一查询结果；历史失败任务单列；差异进入全局诊断 | 浏览器关键流程回归；完整中英文/JCEF/缩放反例矩阵仍待执行 |
| S17-05 | 传输构造纳入保护；MCP 探测 9 秒预算（不包含前置安装检测）；保留安装状态；行动提示；环境查询给出真实运行 Java/SDK/二进制来源信息 | 已在 Windows 同源打包候选复现 selector 管道初始化故障，正确返回 initialization_error；独立进程启动查询不可达端口返回 unreachable；完整安装故障矩阵仍待完成 |
| S17-06 | environment 的 `fieldContracts.block`；JSON Pointer 字段错误；Python 原生 Plan/preview/apply 便捷入口显式传递 payload revision | 当前字段能力清单只冻结方块；其他类型专用映射审计仍未全部完成 |
| S17-07 | 选定生成模式元素后，唯一 JSON/PNG 可自动推导 `models/custom` 与实际贴图引用目标；歧义/缺图/不同 namespace 拒绝；保留手动映射和完整覆盖预览；后续关联/构建入口 | 自动映射及已有导入事务测试；真实 Windows/Ubuntu 建模往返仍待执行 |
| S17-08 | 两个 Loader 的显式右键/库存模板、只读 Code payload 与主类保留区编辑提案生成器；初始化和保留区插入有重复应用保护 | 两个模板已随各自 1.21.1 真实宿主编译；渲染器幂等/冲突回归通过；模板完整生成/重开和真实右键仍待完成，不计作玩法通过 |
| S17-09 | 从通过任务选择实际 JAR/报告并核对哈希；独立导出目录和相对路径证明；旧输入须显式选择；受管任务落盘；重开后公开 recentTasks 及 UI 历史选择/日志加载；无终态标记未确认 | 独立 Agent 经真实产品 run_gametest 产生 4/4 通过任务，重开发现、当前导出、变化拒绝、显式历史导出与文件哈希/相对路径全部通过；真实异常消失矩阵仍待执行 |

## 字段清单与其余映射审计

方块当前可写定义字段由 `BlockFieldContract.DEFINITION_FIELDS` 显式限定，能力查询返回同一集合的类型、范围、枚举和条件。`displayName`、`description` 属于产品属性；`modelResource` 继续使用原有构建时资源绑定适配器。`name` 是创建身份，不能通过字段更新改名。未知导入数据只读保留，不因出现于 JSON 就自动获得写权限。

硬度范围采用上游 `Block.hardness` 的 `-1..64000`；原先只校验 `/fields/hardness` 的 `0..100` 限制已替换。`boundingBoxes: []` 明确表示空形状；不提供形状时保留上游完整方块默认值。

其余分支审计发现 Item 只映射名称/纹理/堆叠数、Recipe 只处理身份及默认结构；本次已接入这两类真实存储字段，补充食物属性和配方材料/产出回归。Procedure、Projectile、Function、LootTable、Achievement 保留专用转换，`ElementMappingSupport` 明确其已实现字段；新增不支持字段在实际写入/计划预检中拒绝，未知导入字段只读保留。通用转换器现校验原始标量类型、Numeric/LimitedOptions，转换失败返回字段路径。**这仍不等于完成所有类型和嵌套字段的全部合同验证**；条件依赖、引用及跨版本实际构建仍需逐项覆盖。

## 剩余门禁（首次实施快照）

以下列表保留首次实施时的状态；当前通过范围和未关闭事项以[2026-09-21 验收清单](stage-17-closeout-2026-09-21.md)为准，不将本列表中的旧“待执行”状态当作最新结论。

- G17-A：其余元素字段能力审计、原子计划/旧工程完整反例、其余六轨实际构建与适用玩法；两个 1.21.1 的本次四项 GameTest 已通过，但不能替代原共鸣工坊 14 用例。
- G17-B：完整活动依赖索引及其运行时顺序、依赖参与的继承/循环、全部 UI 范围跳转；工作区与原版之间的继承/循环已覆盖。MCP 故障矩阵已补到同源 Windows 导出包/Ubuntu 已安装包，真实社区插件就绪兼容回归仍待完成。
- G17-C：真正连续的建模任务入口及资产详情、Fabric/NeoForge 1.21.1 可应用 Code 模板与交互、可信导出/任务恢复完整产品体验。
- G17-D：同源 Windows/Ubuntu 安装候选、共鸣工坊原 14 用例、真实 Blockbench 往返、3 次不读产品源码的独立试作。

用户已明确授权本次一次性本地测试接受 Minecraft EULA；实际运行的两个 1.21.1 GameTest 宿主不需要写入 `eula=true`，记录中 `eulaAcceptedByHarness=false`。此授权不扩展至其他服务器或发布。

## 已执行验证

- 综合 Java 回归：35 个测试套件，202 项中 197 项通过、5 项条件跳过，0 失败。日志 `.tmp/gradle-external/d9e2903ea8794a9494258de4598b7a6a.log`。范围包括资产事务/诊断/建模、字段合同、MCreator 持久化、可信导出、GameTest 工作流、Fabric 生成/任务与 MCP。
- 纯结构化实际构建：`Stage17BlockConsistencyTest` 的八轨定义/生成测试与两个 1.21.1 实际构建通过。日志 `.tmp/gradle-external/0675db9e38bc4f0083a6cf1b11d5adf3.log`。
- 独立 JAR GameTest：两个 Loader 各 4 项通过，报告中的实际执行数均为 4，无跳过。分别验证更新后硬度 5/抗爆 6/非空碰撞、放置朝向及旋转、三槽库存 NBT 序列化、破坏掉落。**序列化验证不是玩家退出重进验证，未据此宣称真实客户端交互已通过。** 日志 `.tmp/gradle-external/619e2f03b33148d582bd53d5cc3fc5a6.log`；JAR、原始报告和哈希在 `build/stage17-structured-block-builds/<generator>/`。NeoForge 专用服务器复用真实版本 17 的本地资源索引，未下载剩余客户端资源对象。
- UI：Stage17 计数一致性、Stage13 健康、Blockbench 导入/任务在 1920×1080 和 1366×768 共 12 项 Playwright 通过；UI 构建与 369/369 本地化键门禁通过。不是 JCEF 全缩放验收。
- Python：SDK 原生接口及包装回归 15 项通过；生命周期渲染器 2 项通过。

| 被测 JAR | SHA-256 |
|---|---|
| Fabric 1.21.1 结构化方块 | `36bdb79284c22bdb748a400494fe9b60f1e5519a4802697fd509d3b1d118db47` |
| NeoForge 1.21.1 结构化方块 | `62285080758905de476b5cce3ca767823c769d2416061ea1ee53290a577a54e1` |
| Windows/Linux 同源候选主程序 | `92fd0d6fa5c3a7a02b59ae4980d34f4a003ecc68db3db6377c3aaba88c38f7ca` |

首次实际构建测试中，Fabric 已构建成功但 Windows 临时目录清理受上游缓存打开的 JAR 阻挡；现将显式启用的实际构建输入保留于证据目录，复跑通过。GameTest 测试夹具首次 API 签名错误、NeoForge 客户端资源下载中止及缺少资源属性文件的失败日志保留，未把这些失败当作玩法通过。

## 同源候选与新建 Ubuntu 环境

用户授权创建本地 Ubuntu 测试环境。新建 Hyper-V `Copperbench-Stage17-Linux`，独立 100 GB VHDX、4 CPU、启动 4 GB/最高 6 GB 内存、Default Switch、Secure Boot Microsoft UEFI CA。使用已有官方 Ubuntu Desktop 24.04.4 ISO，SHA-256 为 `3a4c9877b483ab46d7c3fbe165a0db275e1ae3cfe56a5657e5a47c2f99a99d1e`，2026-09-20 从 [Canonical 校验清单](https://releases.ubuntu.com/24.04.4/SHA256SUMS)核对。

安装已完成，安装介质和 CIDATA seed 已卸载，系统盘启动后确认 Ubuntu 24.04.4、x86_64、GNOME Wayland，安装前 `java/gradle/git` 均不存在。为自动测试配置了只允许密钥的 SSH 与测试用户自动登录；这是记录在案的测试环境设置。凭据仅在仓库外 `D:/Hyper-V/Stage17-Linux`，不提交。

候选从当前脏工作区的文件快照构建，不称为干净提交发行包。源文件清单/哈希在上述目录 `source-manifest.json`，源码副本 `source-candidate`，保留原仓库的既有 `build/export`。Windows/Linux 主程序 JAR 哈希一致，Windows 候选生成器发现和公开 SDK 只读探测通过。

Linux `.deb` 已安装，SHA-256 为 `60c8085fe3d58b944d509de87e04708554d68d1f888a1eb87d36e18c51aa2ece`。安装后的 Fabric/NeoForge 1.21.1 工作区均通过公开 SDK 只读能力/健康/Blockbench 探测；未安装 Blockbench 的状态与未监听 MCP 端口的 `unreachable` 分开保留。实际 GNOME 终端环境为 `ubuntu:GNOME / wayland / :0 / wayland-0`，已启动安装产品并通过现有 `Stage15GraphicalProbeVerifier`：JCEF 主帧 ready、MCP listening、工作区一致、证明文件权限符合要求。截图 `product-ready.png`、原始日志/JSON 与校验结果在上述目录和 `guest-evidence`。

首次桌面启动遇到系统密钥环创建及产品下载源选择提示，均记录在原始日志中；选择官方源，未冒充无人干预冷启动。自动登录测试用户的屏幕锁定已在测试 VM 中关闭。首次 GUI 自动依赖同步还在执行，不能把窗口 ready 当作 Linux 构建/客户端/GameTest 通过。Xorg、真实 Blockbench 往返和完整 Windows 安装回归仍未完成，G17-D 保持未关闭。

Windows RF-04 候选证据：继承当前工具宿主环境启动时，JBR `25.0.3+1-b329.124` / MCP SDK `2.0.0` 在 `HttpClientImpl` 创建 selector 时沿 `PipeImpl → UnixDomainSockets.connect` 报 `SocketException: Invalid argument: connect`，接口约 0.12 秒返回 `initialization_error`，同时保留 Blockbench 5.1.6 已安装状态。通过独立 WMI 子进程启动，同一候选针对未监听端口返回 `unreachable`。证据文件 `windows-native-probe-original-environment.json`、`windows-native-probe-external.json` 位于上述仓库外目录。当前证据支持启动环境差异，不把所有用户机器的探测失败归因于此。

## 公开接口独立评估及修正候选

用户明确批准后启动三个独立子 Agent，各自使用隔离模组副本，仅读公开文档/schema/接口，不读产品实现或其他评估报告。详细记录在 `D:/Hyper-V/Stage17-Linux/independent-eval`：

| 会话 | 实测结论 | 边界/介入 |
|---|---|---|
| fields | 33/33 返回断言通过；发现字段、创建/预览/更新/Plan、非法输入、revision/过期计划拒绝、关闭重开持久化 | 无人工介入；不包含构建、drift 或全部类型 |
| model | begin→finish→自动 preview→import→bind 通过；三文件哈希一致；缺 PNG/多模型拒绝 | 使用明确标注的协议导出夹具，非真实 Blockbench GUI；依据 preview 修正一次自身猜测的绑定路径 |
| delivery | 初轮发现任务历史不可枚举、无效任务只有通用错误、公开 schema 缺参数；修正候选复测通过真实 4/4 GameTest、重开历史发现、当前导出、变化拒绝、显式历史导出，JAR/XML/相对路径独立核对通过 | 主 Agent 初始复制的模组夹具遗漏空的 mcreator.gradle，造成一次真实构建失败；补齐真实文件后完成最终验证。保留失败与介入记录，不伪造验收任务 |

这些是三次有明确边界的独立公开接口评估，不等于三个 Agent 都完成了包含真实 GUI/客户端的完整共鸣工坊固定场景，体验五维评分仍未收集。

依据反馈新增 `get_workbench.recentTasks`（最多 100），保持 `activeTasks` 仅表示当前活动任务；JCEF 桥接加载历史并支持任务选择/读取日志；无效验收 ID 返回 `VERIFIED_TASK_NOT_FOUND` / `VERIFIED_TASK_ID_INVALID`。公开 query/command/asset/task schema 同步自动映射、可信导出、外部解析和历史观察。schema 26 项通过；历史恢复界面与诊断一致性在两个分辨率共 4 项通过；受影响 Java 回归通过（日志 `e7d9bf69cfe24e959b377f78fd83e723.log`），路由器跨会话/导出错误专项通过（日志 `27d8d6a5681b442ea0549ada6986d523.log`）；本地化门禁 370/370。

修正候选保留于 `source-candidate-v2`，源清单 `source-manifest-v2.json`，Windows/Linux 主程序 SHA-256 同为 `55dbce021aa1ec54aea510b77c6278ac8d9fe2f1590b1fd36be620208ab8e417`，初版候选及证明仍保留。Ubuntu 已重新安装修正版 `.deb`（SHA-256 `6f4bdace00463d57a54dc07327007c284df0e089ec26ecca0689a6682a77ec77`），两个 Loader 的安装后公开 SDK 探测再次通过。修正版 `.deb` 使用 `dpkg-deb -Zgzip -z1 --build --root-owner-group` 打包，属于本地开发验收候选，不冒充 CI 发行产物。

修正候选重新从真实 GNOME 终端启动后，Wayland 图形证明再次由既有 verifier 核验通过；证据在 `guest-evidence-v2/graphical-verifier-v2.log` 与 `graphical-product-probe.json`，截图 `product-v2-ready.png`。主帧 ready 与 MCP listening 不替代仍未完成的 Gradle 依赖同步。

交付最终真实验收任务：`8f9d802b-26f5-4bcc-a9c2-7d8bc7294b1e`，范围为 Fabric 1.21.1 的上述四项服务端断言。报告/原始 JSON/独立文件核验分别为 `delivery/report-v2.md`、`delivery/raw-v2-final.json`、`delivery/delivery-proof-v2.json`。

Linux 冷环境 GUI 初次依赖同步最终返回 `GRADLE_NO_INTERNET [-21]`。保留 `sync-state.png` 和日志，通过产品提供的国内镜像入口重试；尚未取得 Linux 真实构建通过证据。未关闭防火墙或更改宿主网络安全设置。

网络专项复查：首轮官方 Maven 依赖请求出现 `Network is unreachable`；国内源重试时 `useChinaMirrors=true`，Gradle 用户目录已安装镜像 init 脚本。修正版重试失败点明确为 `genSourcesWithCfr → cfrDecompilerClasspath → net.fabricmc:cfr:0.2.2`，请求 `https://maven.fabricmc.net/net/fabricmc/cfr/0.2.2/cfr-0.2.2.pom` 报 `Read timed out`（5m36s 后失败）。现有镜像配置有意保留 Fabric/NeoForge 专用仓库的官方地址，不能据此称国内镜像不可用。随后同一 VM 的 Python HTTP 独立探测：该 Fabric POM 返回 200（7.634s），阿里云 Central 的 commons-parent POM 返回 200（0.313s），官方 Central 同一 POM 返回 200（0.944s）。这些是即时连通性结果，不替代 Gradle 完整重试。证据位于 `guest-evidence-v2/gradle-mirror-retry.log` 和 `network-probe.json`；尚未确定瞬时故障的更底层原因。

## 后续闭环：探测故障矩阵与 Ubuntu 构建

S17-05 后续修正将安装检测、构建来源读取、传输/客户端构造、初始化、工具发现和关闭放入同一 9 秒用户可见预算。超时或取消后保留已完成的安装检测，未完成的检测明确为 `unverified`；两路并发耗尽时返回 `busy`，不排队。中断标记独立于线程中断，避免慢构造吞掉中断后继续建立网络会话；客户端构造失败仍关闭已创建的传输。

新增反例发现 SDK 会将先发生的 HTML/JSON 解析失败最终表现为初始化超时；现保留第一项异步传输错误，分别返回协议错误、认证要求或真正超时。内部日志保留异常类型、完整堆栈、cause/suppressed，移除可能含远端凭据的任意异常消息。关闭失败有独立诊断；UI 保留安装状态、说明失败及恢复动作，并可展开查看运行时、SDK、程序哈希和构建来源。程序版本读取实际 JAR 的 `Product-Version`，不再依赖该包没有声明的 `Implementation-Version`。

验证：`BlockbenchEnvironmentServiceTest` 15 项、`WorkspaceApplicationServiceTest` 24 项通过（`45b53640932f41b5b8b6a16974fc8b81.log`）；同次新增外部进程测试在系统临时目录启动 `cmd.exe` 被拒绝，改用与实际构建相同的仓库 build 目录保留夹具后，`Fabric1211ProcessRunnerTest` 全部 10 项通过（`c61476b66bee450f9089bb4776216831.log`）。此失败未计为通过。界面专项在两个分辨率 6 项通过；UI 构建及现有本地化门禁通过（检查器报告 370/370）。

Ubuntu 通过产品“重新运行设置”重试后完成实际依赖同步；随后从主界面构建的 v2 任务 `4cc75e19-b4d1-4df6-94d8-46227043ad45` 于 2026-09-20 14:25:36 UTC 成功，Gradle 用时 11m16s，日志在 `guest-evidence-v2/gui-build-task.json`。退出由真实桌面 Alt+F4 触发，日志记录保存工作区和 Exiting Copperbench；没有把进程消失单独当作正常退出依据。

同时发现产品初始化和受管构建的缓存目录不一致：初始化使用 `/home/stage17/.cache/copperbench/gradle`，受管构建却落到 `/home/stage17/.gradle`，因此未复用国内源 init 脚本。已修正受管进程的默认 `GRADLE_USER_HOME` 为 `UserFolderManager.getGradleHome()`，保留显式 `COPPERBENCH_GRADLE_USER_HOME` / `GRADLE_USER_HOME` 覆盖。测试验证实际子进程收到的环境与产品镜像脚本目录一致；没有更改宿主系统环境变量。

v3 来源冻结在 `D:/Hyper-V/Stage17-Linux/source-candidate-v3`，源清单 `source-manifest-v3.json`。只在该源码副本构建，未改动原仓库既有导出目录。主程序 SHA-256：`db461d05da22933dc6775e4c486e0d63a9b021cb741c5b51283e332f29ce1b32`。Windows 导出包与 Ubuntu 安装包使用同一主程序；Linux 复用未改变的 v2 原生依赖/启动器/插件和开发候选清单，在既有 candidate-root 替换主程序后重建 `.deb`。v3 `.deb` SHA-256：`b69dc63b20f9ac46461f9ba278406b06e45380be38512a7fca51a2a86e914a5e`；仍是开发验收候选，不是发行认证。

`scripts/stage17-installed-probe-matrix.py` 经公开 SDK 对 Windows 导出包和 Ubuntu 已安装包各完成 11 项：有效工具、空工具、401、403、非 MCP HTML、坏协议、HTTP 503、禁止重定向、超时、关闭端口、拒绝远端 URL。循环中另验证默认查询没有网络请求、不发送 Authorization、不调用 tools/call、错误不泄露服务端哨兵文本、已获取的编辑器状态保持不变及实际版本/程序哈希。可计时矩阵项均小于 10 秒；不是社区插件真实建模验收。证据为仓库外 `windows-probe-matrix-v3.json` 与 `guest-evidence-v3/installed-probe-matrix.json`。Windows 原继承环境下 RF-04 再次返回 `initialization_error` 且保留 Blockbench 已安装状态，证据 `windows-original-env-v3.json`；独立 WMI 启动环境则通过完整矩阵。

Ubuntu v3 从真实 GNOME Wayland 终端启动，既有 `Stage15GraphicalProbeVerifier` 通过；主界面构建任务 `9570a13f-711b-436a-9952-e2ef6c0a1ff6` 成功，实际 Gradle daemon 日志位于产品缓存的 `daemon-10802.out.log`。本次约 8 秒、8 项 up-to-date，明确属于暖缓存复跑，不作为冷构建性能比较。任务证据 `guest-evidence-v3/gui-build-task.json`，图形证明核验 `guest-evidence-v3/graphical-verifier.log`。两次 Ubuntu 构建的模组 JAR 哈希均为 `36bdb79284c22bdb748a400494fe9b60f1e5519a4802697fd509d3b1d118db47`，与本次 Windows Fabric 结构化方块产物一致；尚不能代替 Ubuntu 客户端交互/真实 Blockbench 往返、NeoForge 安装后构建及 Xorg 回归。

G17-A/B/C/D 继续保持未关闭；剩余全量依赖资源解析、字段/八轨矩阵、Code 模板运行入口与负例、真实模型往返、会话异常恢复、原 14 用例和完整独立试作仍按原 PRD 执行。

## 后续闭环：共享模型解析与实际原版资源

新增 `MinecraftModelResolver`，供资产健康和 Blockbench 回导预览共同使用。解析工作区文件及已核对版本/哈希的原版模型，合并父模型贴图变量和几何，使用子模型覆盖；检查父模型循环、变量循环、无绑定变量、缺 PNG、非法标识和读取中变更。未显式命名空间的 Minecraft 模型引用按 `minecraft:` 解释；`builtin/generated` / `builtin/entity` 为明确的渲染器哨兵，其余原版路径仍查索引。NeoForm 的 `artifacts/minecraft_<version>_client.jar` 与 Loom 缓存均可读取，只读查询不联网。

回导不再整体放过 `minecraft:`：冷目录返回 `MODEL_RESOURCE_UNVERIFIED`，索引已齐但资源不存在时返回具体缺失错误。计划额外保留实际读取的资源上下文指纹；父模型在预览后变化，即使新父模型仍合法且导出文件字节没变，也拒绝旧计划。旧 MCP 合同回归原先依赖“原版总是存在”的隐式假设，现先断言冷目录拒绝，再提供真实本地覆盖文件验证重新查询/导入；没有恢复该错误豁免。

资产引用按真实模型路径解析，纠正了旧测试夹具中 `namespace:textures/block/name` 被错误当成 `namespace:block/name` 的情况；两种资源 ID 在 Minecraft 中指向不同路径，健康检查不再把前者静默修好。模型模板可由子模型补变量，但若该模板同时被方块状态直接使用，仍检查自身绑定。诊断保留具体 JSON Pointer；继承错误指向子模型的 `/parent`，消息保留继承内部位置。同一源文件的多处缺失引用不会因目标相同而丢失位置。

资产详情新增可键盘展开的解析记录，展示工作区/原版/活动依赖/缺失/未验证/无效状态、引用位置、目标、来源指纹、版本和未验证时的下一步；未知第三方引用仍显示未验证，没有因 UI 标签存在而宣称活动依赖索引已完成。

验证证据：

- 资产、回导、资源反例、Core、真实 MCP 合同共 70 项通过，日志 `.tmp/gradle-external/de145708ddfd49448f17ceaf25fbc248.log`；随后真实目录探测和资产移动 4 项通过，日志 `.tmp/gradle-external/79de839e6102473d866bb39cf997bcfc.log`。
- `Stage17RealResourceCatalogTest` 显式读取实际缓存客户端 JAR，在 Fabric/NeoForge × 1.20.1、1.21.1、26.1.2、26.2 八个上下文各建立原报告的 5 处合法引用（2×cube_all、2×generated、1×handheld），全部零诊断；每轨加入 cube_all_typo 后均检出缺失。未下载或改写缓存，没有用合成原版 JSON 替代此项验证。
- 原始结果和工作区夹具：`build/stage17-resource-catalog-probes/e793b22a-dbb8-4e5d-8fe0-bda1c83a16a5/result.json`。实际 client JAR SHA-256：1.20.1=`56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f`；1.21.1=`499f6897d1837516680f3114072d8106e11c9adcd933fe5cf051b551089b0c99`；26.1.2=`b1b3158572666445eff01e82fad8c7de2e4953db6d354f311730d77a8359d0b0`；26.2=`40896ee9f1e2bec3c934daac7e93d41e9e3d9c2f8ae0ca366d52ffbfd1afa290`。
- 合同 schema 26 项通过；资产浏览和 Stage17 一致性界面在两个现有分辨率共 42 项通过，包括键盘展开、长哈希换行及未验证说明。不是全部语言/JCEF/缩放矩阵。
- 补充“父模板同时被方块状态直接使用”的反例后，资源解析专项 9 项通过，日志 `.tmp/gradle-external/b0a46ed15a55447fa933c9b861b3914b.log`。

本批改动尚未重建到 v3 安装候选。第三方活动依赖仍需从实际运行配置建立可复核清单，不能复用编译类路径或遍历缓存假定已启用；运行时覆盖顺序也不能按文件名猜测。已查阅 [Fabric 1.21.1 ModResourcePackCreator](https://raw.githubusercontent.com/FabricMC/fabric/1.21.1/fabric-resource-loader-v0/src/main/java/net/fabricmc/fabric/impl/resource/loader/ModResourcePackCreator.java)（2026-09-20，`smart-search fetch`，本地证据 `C:/tmp/smart-search-evidence/stage17-fabric-resource-order.json`），其区分默认、模组、模组内置和用户资源包；后续须继续核对具体 Loader/依赖版本的包顺序，不将这次原版上下文验证视为完整依赖解析或八轨构建/玩法验收。

## 后续闭环：活动运行依赖与 v4 原场景 B（2026-09-21）

显式受管 Gradle 任务新增运行依赖采集：只解析根工程 `runtimeClasspath` / `clientRuntimeClasspath`，记录实际解析的 JAR、组件版本、配置及 SHA-256；不从编译类路径或任意缓存推断模组启用。普通健康查询只读已有快照，不运行 Gradle 或联网。依赖快照绑定生成器和工作区输入指纹；构建失败/取消、输入或 JAR 变化均使其不可用于证明资源存在或缺失。

`DependencyResourceIndex` 读取实际模组元数据，排除 Fabric 仅服务端模组，解析唯一活动依赖的模型和贴图。工作区/依赖重叠、多依赖同名、嵌套模组或资源子包顺序仍返回未验证；不能将类路径次序冒充运行时包顺序。目录形式的运行依赖也不能冒充完整 JAR 目录。健康检查存在外部未验证引用时显示部分检查；资产描述和引用内容参与快照 ID，即使 revision/mtime 未变，实际文件内容变化仍会更新诊断快照。

证据：

- 真实离线 Gradle 夹具区分 `runtimeOnly` 与 `compileOnly`：只采集前者，资源查询与实际清单一致。日志 `.tmp/gradle-external/4691fe9d65344ac581529bc01d99a29b.log`；证据 `build/stage17-runtime-dependency-probes/ec6181ba-9ca5-4937-99f4-eb7f9f0dd8cd`。其中资源模组为明确的合成测试 JAR，不冒充第三方发布包。
- 真实 Fabric/NeoForge 1.21.1 结构化工程分别构建成功，运行配置记录 135/108 个 JAR。证据 `build/stage17-deps/ff554822/{fabric-1.21.1,neoforge-1.21.1}/workspace/.copperbench/resource-index/runtime-dependencies.json`；日志 `.tmp/gradle-external/6a09ec314485482e8864f587a99924e2.log`。产物 SHA-256 仍为此前通过玩法测试的 `36bdb79284c22bdb748a400494fe9b60f1e5519a4802697fd509d3b1d118db47` / `62285080758905de476b5cce3ca767823c769d2416061ea1ee53290a577a54e1`。
- 首次真实工程探测发现 `.mcreator` 缓存目录被当成第二个工作区，导致采集未启动；已改为只计普通 `.mcreator` 文件，并补反例。另一轮 NeoForge 夹具复制发生旁路清单重名，改成每个 Loader 独立证据根后两者完整重跑通过。失败没有计为通过。
- 依赖索引、资源解析、回导、Core 健康快照/规模和进程目录回归 52 项通过，日志 `.tmp/gradle-external/dbff52b1c4074e22b6823f812504a5c4.log`。

v4 Windows 候选在独立普通源码副本 `D:/Hyper-V/Stage17-Linux/source-candidate-v4` 构建；源清单 `source-manifest-v4.json`，主程序 SHA-256 `7c14d6bca1e4158487a5024759c9e71ba260e637c7cc317cdc7f41c2f1c57a00`。`exportWin64` 成功，日志 `export-win-v4.log`。该候选包含上述资源解析/采集；尚未安装到 Ubuntu，也不包含下述随后发生的进程/日志修正。

独立 Agent 只读公开资料、随包 SDK 接口及模组工程，将原共鸣工坊 87 个输入文件逐一核对后复制，保留原 14 个测试与配置。真实验收任务 `f905deed-ad01-40d8-8250-41946f6f31ea` 的 14/14 测试全部通过，0 失败/跳过；重开发现任务、正式可信导出 `a8d46c7b-f57d-40bd-be04-229837055e95`、再次重开查询均成功。实际被测/导出 JAR 哈希均为 `5e71416a4dc707423fe1dde8cec18e1d11841aae94db0417f9fafd7187e88175`，XML 哈希 `eedecf7ac45658d7711a56131acf9ec4913f26a114e753c088cc9235fe29528f`，独立逐项核对原 14 方法名和可移植相对路径。证据在 `D:/Hyper-V/Stage17-Linux/independent-eval/delivery/report-workshop-v4.md` 与 `workshop-v4-delivery-proof.json`。

首轮随包 SDK 启动在 120 秒返回 `NATIVE_TIMEOUT`，原日志含初始 Git 恢复点保存时 Windows 文件部分锁定错误；不能据此认定超时根因。第二轮将公开启动预算调至 300 秒后成功。该介入、首轮进程观察证据缺口与原日志均保留。场景 B 通过不替代 GUI 场景 C、完整故障场景 D 或三次完整独立试作。

## 后续闭环：进程取消与会话恢复（2026-09-21）

真实进程边界发现 `waitFor`/`join` 收到中断后可能跳过循环内取消检查，外部进程未被清理。改为每条异常退出路径都清理仍存活的进程树，保留线程中断标记；启动前已取消不启动进程。输出观察失败及时结束；退出后输出流仍未关闭时不伪造成功。

`Stage17ProcessLifecycleTest` 用真实包装器、Java 父子进程验证六项：等待期间取消、超时、正常退出、非零退出、输出观察失败、启动前取消。检查真实 PID 终止，readiness 标记不会让交互任务提前成功。本机 Windows 六项通过；与既有进程/依赖反例合计 23 项通过，日志 `.tmp/gradle-external/1ec3bafc5b4e4d6db5824472f3ed93ed.log`。这些是隔离进程夹具，不冒充 Minecraft 客户端正常退出验收。

运行日志此前仅随状态快照落盘，宿主异常退出可能丢失最后阶段输出。现增加逐条脱敏日志旁路文件，重开时合并最后最多 8 MiB / 10000 条记录；不因截断的末行丢弃此前日志，不向历史记录恢复取消权限。

`Stage17TaskSessionRecoveryTest` 启动真实独立宿主 JVM，经生产任务网关/进程边界运行隔离客户端夹具。三项通过：强制结束宿主后重开保留未确认状态、最近输出且无取消权限；真实观察的正常退出重开仍成功；非零退出重开仍失败。原子历史快照不会被查询改写，新任务使用新 ID，日志中的 Bearer/多词密码哨兵不落盘。日志 `.tmp/gradle-external/b407232c00bf4d55a08606c6f320dff3.log`。首次夹具依赖 URLClassLoader 提取类路径失败，未启动测试宿主；改由 Gradle 提供实际 test runtime classpath 后三项重跑通过，首次失败保留在 `6cdcfadd42034fb7ad95c71cddf3f908.log`。

后续取消终态持久化调整触发既有回归：取消等待期间先通知订阅者，订阅者又需要取消命令持有的工作区锁，造成 15 秒等待失败。已改为先保存终态、再释放取消等待者、最后发布订阅事件；测试增加“取消回复时磁盘已是 cancelled”的断言。最终任务网关 19 项通过，日志 `.tmp/gradle-external/beab5d332bbc466f8e733921a5671259.log`。触发该问题的组合回归日志 `89daff57b0f3496b856d855266f0bb45.log` 中其余 30 项通过，失败项未计为通过；该轮亦包含日志末行截断和脱敏恢复反例。当前代码尚未重建安装候选。

实测 Blockbench 窗口操作时，既有 Windows Gradle 外部辅助启动器未显式隐藏窗口；已在 `scripts/run-gradle-external.ps1` 传 `Win32_ProcessStartup.ShowWindow=0`，上述最终回归由修正版启动成功。未更改宿主桌面设置或环境变量。

## 独立试作新结果与当前阻断（2026-09-21）

交付 Agent 在独占场景 D 副本重新通过原 14 用例后执行 18 项故障核验，全部达到预期并恢复：资源拼写显示源/目标/JSON Pointer；非法 hardness 类型拒绝而不强转；陈旧计划预览/应用拒绝，重新规划成功；显式关闭 SDK 后拒绝请求，重开后读回；输入变化拒绝当前验收导出，显式历史导出标记旧输入；较新未测 JAR 不取代已测文件；替换被测 JAR、报告缺失、任务证据缓存缺失均拒绝可信导出。恢复原件后再次导出成功，所有原证据哈希及场景 B 产物保持不变。证据 `D:/Hyper-V/Stage17-Linux/independent-eval/delivery/report-scenario-d.md`、`scenario-d-summary.json`。会话部分仅为 SDK 显式关闭/重开，不冒充突发 MCP 传输故障或上述真实宿主失联。

字段 Agent 的 v4 场景 A 暴露未关闭的产品阻断：独立 Fabric/NeoForge 1.21.1 副本均能创建/修改、生成并重开一致读回，但真实构建均因缺少 Java 导入失败；正式 GameTest 返回 `GAMETEST_BUILD_FAILED`，执行验收用例为 0。Fabric 不调用 generate、仅 create→build 的对照亦失败。六项新行为断言只是已编写，尚未进入测试宿主编译/执行；不得计为通过。证据 `D:/Hyper-V/Stage17-Linux/independent-eval/fields/v4/report.md`、`summary.json`，副本保留待修正候选复验。

源码核查：该独立副本不含 `setupInfo` / `generatorGradleCache`；`Generator.loadOrCreateGradleCaches` 在 `shouldSetupBeRan=true` 时不建立导入索引。`MCreatorWorkspaceMutationGateway.generateRegisteredSources` 目前仅以源目录存在判断可生成，模板则依赖 `ImportFormat` 的 Gradle 类索引补齐导入。因此冷工程仍会写出只含模板显式导入的 Java；任务生成器随后仅保留已物化插件工程，未补做准备/重生成。已定位准备流程缺口，尚未修复；需在明确生成/构建操作中完成依赖准备并保证源码所有权，避免把查询变成隐式下载或手补生成源码。G17-A/B/C/D 仍未整体关闭，下一优先项为该阻断及真实 Blockbench 往返。

`Stage17ColdWorkspaceProbeTest` 随后在两份独立诊断副本验证修复路径：先确认冷打开无导入索引，执行显式离线同步、重载真实 Gradle 类型缓存、正常模板重生成，再执行真实构建。Fabric 和 NeoForge 均通过，包括 Agent 的自定义碰撞箱、朝向、库存和堆叠字段，无手写补导入或 Code fallback。证据分别为 `build/stage17-cold-probes/fabric-358253af-eeab-4986-a0a3-846ffef17e47`、`neoforge-b645a6a3-d92b-4ebb-9873-6de26e96e9cc`，日志 `.tmp/gradle-external/09c25a23181e483f923a3bbe02f71dfe.log`，5m11s。Fabric 首次反编译实际统计 0 hits / 5363 misses，耗时不能归为网络故障。该探测通过 `-Dcopperbench.stage17.coldWorkspaceRoot=../../Hyper-V/Stage17-Linux/independent-eval/fields/v4` 显式启用，默认不执行；首次绝对路径参数被 Windows 批处理拆分而未启动测试，保留失败日志 `d54d26b352a8461f80dd628f788cb244.log`。这里只证明准备路径可修复编译，不代表该路径已接入产品命令，也不代表六项新 GameTest 已运行。

## 冷工程产品准备流程及 v5 独立反例（2026-09-21）

`MCreatorGenerationPreparation` 已接入真实会话的生成/构建/运行任务：冷工程字段修改只保存定义和待生成记录，不联网或写出缺少导入的 Java；显式任务先准备实际依赖、读取类型索引，再生成受管源码。待生成目标保留源指纹，外部新增/修改目标会拒绝覆盖；准备期间 revision 改变拒绝提交。代码锁定元素和无关手写文件保留，生成失败恢复原文件和工作区元数据。编辑器以 `configuration.generationState=pending` 显示“定义已保存，源码尚待生成”；任务终态会刷新该状态而不清空未保存字段草稿。

真实 Core 链路 `coldMutationThenManagedBuildPreparesImportsWithoutOverwritingManualSources` 在两个独立工程副本均通过：冷字段修改不生成 Java，直接 build 完成准备与编译，受管 hardness 更新为 6，锁定元素及无关源码哈希不变。证据 `build/stage17-managed-cold`，日志 `.tmp/gradle-external/28340be848144257b57f696d1e31dafb.log`，3m17s。字段/任务回归通过 `51cc8f89b74548d292366b7dfce9fdcf.log`；待生成记录重开、外部目标冲突、持久化失败回滚三项及既有回归通过 `e1db6de09cb94d8bb19d6e19fa21b5af.log`。界面在 1920×1080 / 1366×768 两项通过，覆盖待生成说明、任务后清除说明及未保存草稿保留；不等价完整 JCEF/语言/缩放门禁。

v5 导出候选主 JAR SHA-256 `3a7c419236d5c061954ec35f29b107c117a6ad30ca880c3bf47919afe69bbb33`，源码副本 `source-candidate-v5`，清单 `source-manifest-v5.json`；首次构建前补齐两份前端任务状态刷新文件并在清单中记录 `preBuildRefresh`，构建后未修改候选。导出日志 `D:/Hyper-V/Stage17-Linux/export-win-v5.log`。

独立 v5 字段试作仍失败，不能用上述开发环境通过覆盖它：两条 generate 和两条 cold create→build 均失败，六项行为测试实际执行 0。主 Fabric 同步已成功，随后应用 JVM 的 Gradle Tooling API 报 `Unable to establish loopback connection` / `PipeImpl`；Launcher 原来只对桌面和 bootstrap 创建运行 IPC 兼容探测，遗漏了 headless 原生 SDK。另一次 NeoForge 包装器输出“拒绝访问”，证据尚不能把它直接归为同一原因。完整报告 `D:/Hyper-V/Stage17-Linux/independent-eval/fields/v5/report.md`，内部异常日志 `C:/Users/Administrator/.copperbench/logs/mcreator_2026_09_21-19.log`。现已让 headless 启动也先执行既有应用 IPC 兼容处理；类型索引读取失败增加稳定准备诊断，待新候选复验。

独立 Blockbench v4 GUI 往返已有真实证据：GUI 将模型改成 16×12×16、原生导出 JSON/PNG、回导/绑定/Blockbench 正式资源重开，以及关闭 SDK 后重开的三资源哈希一致；真实导出变化后旧计划返回 `MODEL_IMPORT_STALE`。报告 `D:/Hyper-V/Stage17-Linux/independent-eval/model/report-v4-real-roundtrip.md` 保留路径/IME/前台焦点等介入；尚未覆盖游戏视觉、changed-parent 或 Copperbench UI 选择流程。该副本构建因缺少实际零字节 `mcreator.gradle` 失败，产品现仅在显式任务发现该生成 include 缺失时恢复，保留已存在文件及声明的 Mod API 内容。

独立 Code 模板 v5 试作还发现公开模板错误：`Stage17MachineRuntime` 不符合元素小写内部名；只改名后 public class 与文件名不匹配；在指定 mod init 保留区加调用被准备指纹误判为冲突。模板现为 1.1，统一输出 `stage17_machine_runtime` 内部名、Java 类/构造器和 init 调用；渲染器两项回归通过。准备指纹现只忽略明确、唯一且闭合的 user-code 保留区正文，仍校验生成区域和边界；现有生成器确实保留该正文。四项准备保护与 IPC/任务回归通过 `462bd6effee149b6b9462828b9c0a763.log`，补充缺 include/已有 include 保留后五项通过 `5ac62f664bc745b7b4c48c6d36214215.log`。旧 v5 原始失败记录 `delivery/lifecycle-v5/report.md` 未改写；正例行为通过和“遗漏 init 仍编译但行为失败”仍待新候选实测。

冷准备将重建全部未锁定的受管元素，以修复已在旧冷路径生成的缺导入源码；待生成记录同时保护这些既有受管源文件。G17-A/B/C/D 保持未关闭。Ubuntu v3 已经由实际 Alt+F4 正常退出，保存/退出日志已观察，准备安装修正候选；尚未把 v5 安装到来宾机。

## v6 安装、独立复验及网络定位（2026-09-21）

v6 候选主 JAR SHA-256 为 `0c73285bf5ac4208f7fbdf4ae6c94f299ccba423f53f21bcaa2de78028ea9f8c`，冻结副本 `D:/Hyper-V/Stage17-Linux/source-candidate-v6`，清单 `source-manifest-v6.json`。Ubuntu 24.04 安装的本地开发 deb 使用同一主 JAR 和随包 SDK；deb SHA-256 `820a1b7ce3444fa6aba391ba71b12a8593a805107ad61b9f07ad5e4632c85ecb`。安装后 11 项连接故障探测通过，真实 Wayland GUI 启动及图形探针验证通过；证据 `guest-evidence-v6/installed-probe-matrix-v6.json`。不等于所有安装/界面门禁完成。

字段独立 v6 Fabric 和 NeoForge generate/build 均成功。Fabric 首轮六用例因夹具注册名 `contract_probe_v6` 与实际 `contract_probe_v_6` 不符而失败，保留原始失败；仅修正测试注册名后六项真实 GameTest 通过，task `b4e37995-71e4-464d-8345-63e06b88dd07`，JAR SHA-256 `243cb7965f8e7b02441f176180066b85e03b7a0627249fcbf9ad668efbbeeb0e`。NeoForge 字段 GameTest 仍在原任务下载阶段；不记为通过，cold 对照尚待该任务自然终态。

Code 生命周期 v6 两加载器正负对照均达到预期：正例真实事件回调通过；遗漏 init 的独立负例仍构建成功，真实 GameTest 因 `LIFECYCLE_CALLBACK_NOT_REGISTERED_OR_NOT_EXACTLY_ONCE` 失败。Fabric 正例 task `9b28f804-430c-412d-a4f3-304eb3036028`，负例 `efb31395-b38e-4acd-9903-59f0b105ec17`；NeoForge 正例首轮下载到 1800/1845 后达到 20 分钟任务超时、退出码 124、执行 0 用例，失败保留。后续缓存预热后，同输入同 JAR 的一次正式重跑 task `2f16ee32-e148-4620-85d5-0cb224645a78` 通过；负例 task `0ab5c91b-d879-43a1-9271-48e8f769ea97` 实际执行并达到预期失败。证据 `independent-eval/delivery/lifecycle-v6/summary.json`，不冒充冷网络稳定通过。

模型独立 v6 工作区重开、模型导入和三项资源哈希保持正确，缺少 `mcreator.gradle` 已由产品恢复；但旧冷工程直接 build 仍未补齐此前生成 Java 的 imports，实际 compileJava 失败。证据 `independent-eval/model/report-v6-build-followup.md`。这是产品准备路径缺口，与下面网络失败分开记录；尚需修正无 pending 元数据的既有冷工作区直接 build 路径并核实源码所有权。

网络只读对照于 2026-09-20 18:23:55 UTC 执行，原始证据 `D:/Hyper-V/Stage17-Linux/network-mirror-host-v6.json` 与 `guest-evidence-v6/network-mirror-v6.json`：

- `net.neoforged:neoform-runtime:2.0.18` 的官方 POM 在 Windows Python 返回 200，而 Ubuntu Python 连续返回 `Connection reset by peer`；Ubuntu 正式构建 task `e7a5c26f-0366-4fb9-a567-5a08c5e7279d` 也因同地址 reset 失败，不能将外层 configuration-cache serialization 文案当作根因。
- 同一 POM 在已探测的阿里云 public、华为 Maven、BMCLAPI Maven 路径均返回 404；这只证明这些具体路径缺失该文件，不代表国内源普遍不可用。此前 Ubuntu 从阿里云下载 commons-parent POM 已返回 200。
- 同一 Mojang index17 官方 URL 和 BMCLAPI 对应 URL，此轮在 Windows/Ubuntu 均返回 200，449557 字节，SHA-1 均为 `76d7a97b9e0778fda3b14e474f012450ca0de1bb`；此前独立 PowerShell 探测超时仍保留。当前可达不证明长期运行的 Java 下载已经恢复。
- Windows Python 检测到 http/https/all 代理指向本机回环；Ubuntu Python 无代理项。因此两端并非同一网络路径，不能直接把宿主成功等同来宾成功。未输出凭据，未改变宿主/来宾代理或活动测试输入。
- 产品 `china-mirrors.init.gradle` 重写通用 Maven 与 Minecraft libraries，但明确保留 NeoForge/Fabric 专用仓库，未重写 Mojang 资源索引。国内源开关并未覆盖所有下载地址。

BMCLAPI 文档通过 smart-search fetch 尝试检索失败（退出 1、无正文），本段结论只基于本地配置、实际构建日志与上述具体 URL 网络探测，不援引未获取的在线文档。G17-A/B/C/D 仍未整体关闭。

## 旧冷工程直接构建修复及 v7 候选（2026-09-21）

独立模型工作区 v6 失败已确认由缺少 pending 标记的旧冷工程跳过准备引起。`MCreatorGenerationPreparation` 现在在显式 build/run 且存在未锁定受管元素、类型索引缺失时执行准备；Code 元素和代码锁定元素不进入重生成列表。没有受管元素的工程仍只恢复缺失 include，不触发无意义的依赖准备。查询和普通修改保持原来的离线语义。

`legacyModelWorkspaceBuildPreparesImportsAndRetainsImportedAssetsWithoutPendingMetadata` 使用原独立模型工作区的普通副本，不先 generate、不手补导入：确认原文件无 imports、无 pending，然后经真实 Core build 成功，检查补齐 RandomizableContainerBlockEntity 导入；三项导入资产和额外无主源码哈希保持，JAR 内模型 JSON/PNG 与工作区逐字节一致。证据 `build/stage17-legacy-model-build/c29da1b3-e717-42ff-af01-4993385e646b/build-task.json`。六项准备保护测试与该真实构建合计七项通过，日志 `.tmp/gradle-external/02c85819d754472990a5e13f569ffe5d.log`。首次测试夹具误把工程根 models/blockbench 路径当成资源目录，尚未执行产品构建即失败，原日志 `eafbdfcec1be4185b0f785a5b7f0ab1b.log` 保留；修正路径及核实原 build.gradle 的1.0归档版本后重跑。

v7 主 JAR SHA-256 `d853758dfca5cab291466dd564e7aeabb6f57b60adac6ad8cf4e2bf1d7d51d47`，冻结副本 `D:/Hyper-V/Stage17-Linux/source-candidate-v7`，清单 `source-manifest-v7.json`，Windows export 成功日志 `export-win-v7.log`。不在原仓库执行会清理 export 的打包任务。模型 Agent 已接收 v7，复验原工作区直接 build 并继续客户端可视验证；交付 Agent 在独立副本回归原场景 B 14 用例。任务尚在执行，未预先记通过。

字段 Agent v6 最终四条公开流程均成功（两端 generate/build、两端 cold create→build），两端原六项真实行为断言均通过。NeoForge 最终 task `50ca901e-739e-4510-8e4e-f6ceeba428f6`，JAR SHA-256 `d088d3554bd7db77aa6d67708e5d4fa59f0fbcc5f79da4214674fe8a276a8902`。其首轮20分钟超时、Fabric首轮测试ID错误均完整保留。独立证据 `independent-eval/fields/v6/report.md`、`verification.json`；50项审计检查不是额外50个Minecraft用例。

Ubuntu v6 同一失败地址通过测试专用 SSH 回环转发已返回200，POM内容SHA-1与宿主官方响应相同。来宾127.0.0.1:23067仅临时转发宿主现有127.0.0.1:3067代理；未更改全局配置。新的正式构建 task `703cf4ca-41b7-4493-9a11-77550172ad82` 只在测试进程树追加 JAVA_TOOL_OPTIONS 的代理属性并绕过localhost。证据独立写入 `neoforge-build-v6-temporary-proxy*.json*`，不覆盖直连失败。已进入 NeoForm 实际Minecraft下载/准备，仍在运行；不记为直连通过或冷网络稳定通过。

PRD八轨的真实构建仍有缺口：此前 `structuredBuild=true` 仅激活1.21.1；现移除该限制并按活动生成器选择JDK，开始八轨真实定义/生成/编译/JAR核对。该改动仅在测试中，晚于v7冻结；不改写v7来源清单。运行证据 `.tmp/stage17-eight-track-real-build.log` 尚待终态，各轨结果须逐项记录。G17-A/B/C/D 保持未整体关闭。

v7 独立补充：模型 Agent 原工作区直接 build task `16eae02d-8028-4d28-8ce8-a3e56a063431` 已 succeeded、零诊断；重开 imported/consistent，三项导入哈希不变，继续客户端视觉和正常退出。交付 Agent 的 v7 场景 B build 成功，11个锁定/Code关联文件、8个保留区、21个模型/纹理/测试文件以及全部Java源码都未改变；原14用例执行中，被测JAR SHA仍为 `5e71416a4dc707423fe1dde8cec18e1d11841aae94db0417f9fafd7187e88175`，尚未预记GameTest/导出成功。

S17-06 新公开接口一致性缺口：字段 Agent 无产品实现阅读地复查五类公开查询，发现创建/生成/构建的同一会话中 `/data/element/name` 返回内部名 `contract_probe_v6`，重开后同字段变成 `contract_probe_v_6`；`configuration.declaredValues/effectiveValues.name` 仍是原内部名。当前重开后的 editor/list 是不读Java的注册名发现途径，但没有单独的完整 namespace:path 字段，重开前后的语义不稳定。证据 `D:/Hyper-V/Stage17-Linux/independent-eval/fields/registry-id-discovery.md`。须后续修正公开身份合同及规范化说明，不能只归因测试夹具。

Ubuntu v7 主JAR/SDK和升级脚本已上传到独立文件名，尚未安装；必须等v6构建终态并正常退出v6 GUI后再升级。临时SSH回环转发仍仅服务当前构建，结束后应关闭。所有阶段门禁仍开放。

v7 场景 B 正式完成：build `2b1f6db4-81c9-4e4a-a4f8-a594eaa7ad9b`、原14/14 GameTest `f99942e1-05ac-4b01-ab24-a2865af1d6c6`、可信导出 `a2604297-b04f-4c82-a120-c9e11782a477` 全部 succeeded；重开后历史查询可发现且结论保持。原26个Java、11个锁定/Code关联文件、8个用户保留区、21个模型/纹理/测试文件及3个锁定定义不变；被测与导出JAR内36部件模型逐字节一致。完整独立报告 `D:/Hyper-V/Stage17-Linux/independent-eval/delivery/report-workshop-v7.md`，汇总 `workshop-v7-summary.json`。无失败/重试/环境或缓存介入，不替代客户端场景 C。

Ubuntu v6 临时代理构建正式在15分钟上限失败，task `703cf4ca-41b7-4493-9a11-77550172ad82`，failureId `d04d3239-870f-426d-bc24-d0e700e6038f`。完整增量日志确认服务端51,627,615字节最终下载成功（713.10秒），之后完成extract/strip/merge/rename，在decompile阶段达到整体任务上限。因此本次是慢下载耗尽准备预算，并非之前POM reset；父子Java进程均已退出。没有人工复制或替换依赖缓存。原始日志下载到 `guest-evidence-v6/neoforge-build-v6-temporary-proxy*`。

v6 GUI 随后经观察窗口发送真实Alt+F4，日志完整保存/关闭并记录 `Exiting Copperbench`（18:48:48Z），对应PID退出后才升级。Ubuntu v7 本地开发deb SHA-256 `349242eb7892fee2e4489baf1caacb74ec7d7c4e8ab5890b9f0177e9ddad57e0` 已安装，同主JAR哈希匹配，11项安装探测通过，证据 `guest-evidence-v7`。v7 后续正式NeoForge构建task `33156223-9e1f-47ea-80be-bbee23146989` 已启动，使用同一测试进程临时代理和前次真实下载缓存；不与v6首轮混记，也不称冷缓存通过。真实GNOME终端已输入安装产品启动脚本，图形启动待验证。

八轨首项 Fabric1.20.1 的真实同步完成，但测试夹具随后强制offline build，因未缓存 `net.fabricmc:fabric-mixin-compile-extensions:0.6.0` 失败。日志 `build/stage17-structured-block-builds/fabric-1.20.1/build.log`；尚未触达源码编译，不能归为字段生成缺陷。其余轨道继续原串行任务。后续应移除真实构建步骤不必要的offline约束，并仅对未通过单元重新执行；不抹去本次失败。

## 明确公开元素身份与 v8 独立复验（2026-09-21）

数字名称的根因已修正：`MCreatorWorkspaceStateMapper` 对符合公开格式的内部名称保持 `getName()`，不再重开后用 `getRegistryName()` 替换同一字段。旧上游 CamelCase 等不符合公开格式的名称保留历史列表投影，原始内部名与实际存储注册名另见 `element.identity`，不迁移文件。Core创建/editor/list/workbench复用身份投影，给出 `internalName`、`registryName`、`source=generator_definition`；仅非锁定的受管方块/物品补充 `namespace`/`resourceId`，不会把手写代码元数据推断成实际运行注册。

回归先验证创建、立即查询、重开及字段更新、源码路径集合不变，再增加旧CamelCase手写元素自定义registry_name查询不改磁盘。两端身份案例和既有Core/mapper共27项通过，日志 `.tmp/gradle-external/62e0891fea2d46a39125bd094db1806a.log`；增加旧工程用例、将列表请求改用正式limit后3项通过 `76a4d16f3e6a45f98f86e62901628d20.log`。为不覆盖运行中的八轨构建结果，身份回归通过临时init脚本使用独立 `.tmp/stage17-identity-build` 输出目录。首轮新增Java夹具遗漏列表分页参数而失败，保留 `2d8e3507f5cc48d2914d36758a5dfdbd.log`；首轮schema只加到列表投影而漏完整summary，新增校验捕获并修复，最终UI-Core27项全部通过。文档/TS类型/两个JSON schema已对齐；旧不含identity的响应仍合法。

v8 Windows export 成功，主JAR SHA-256 `1f5ac8ceb49ce0efbf7b7ff4522772034a276b2fc67cae1053f04a0e4b2a4891`，冻结副本 `D:/Hyper-V/Stage17-Linux/source-candidate-v8`，清单 `source-manifest-v8.json`，导出日志 `export-win-v8.log`。字段Agent在自己的独立副本创建 `identity_probe_v8`，只通过公开返回的 `structured_forge:identity_probe_v_8` 编写正式GameTest；task `aad4bf4e-725a-41ee-8a2b-f0ec614dda97` 真实1/1通过。重开和hardness3→7后名称/identity稳定，第二次GameTest执行中。没有测试ID修正或生成源码介入。

独立试作指出v8缺少随包 `docs/stage-17-agent-workflow.md`，只能读取源码树的公开文档；原始候选保持不变，此介入必须记录。现修改共用export Copy规则，将公开Stage17文档、双加载器生命周期渲染器/模板和其引用的Wayfinder多文件示例明确打包（仅指定源文件，不含缓存/世界/日志），SDK README增加在源码与发行目录均有效的相对链接。生命周期README更新真实双加载器正负例范围，不将事件注入等同客户端鼠标验收。该打包修复晚于v8冻结，待下一候选验证。

## 八轨真实构建首轮与定向复验（2026-09-21）

首轮八轨均实际执行，无跳过：Fabric1.21.1、26.1.2、26.2定义/生成/真实构建与JAR核对通过；其余五项未通过，完整日志 `.tmp/gradle-external/b4917317932d4feb8684ba9ff46ecf93.log`，28m59s，原XML额外保留 `.tmp/stage17-eight-track-initial.xml`。

- Fabric1.20.1在compileJava解析annotationProcessor时，测试强制offline导致 fabric-mixin-compile-extensions:0.6.0 未缓存，未触达源码编译。
- NeoForge1.20.1准备在10分钟达到真实超时，退出124，最后输出daemon启动；具体停滞原因尚未证实。
- NeoForge1.21.1依赖准备日志显示Gradle BUILD SUCCESSFUL，之后包装进程出现本机“拒绝访问”乱码并返回1，资源索引不可用；未将此未经确认的系统结果改记成功。
- NeoForge26.1.2/26.2准备均因NeoForge仓库的installertools/vineflower依赖连接reset失败；外层configuration-cache序列化消息不是该轮根因。

复验仅选择这五条未通过轨道；移除嵌套真实build不必要的offline限制，显式Gradle参数仅为本次测试子进程使用已验证本机回环代理127.0.0.1:3067，不改全局配置。代理参数仅接受HTTP回环无凭据地址。已通过三条在该次调用标记skipped，不能把skipped算新通过，合并时引用首轮逐项证据。后续每个独立工程将日志/JAR哈希写到自身build/stage17-evidence，避免覆盖历史轨道级文件；GameTest证据选择同步支持该目录。复验日志 `.tmp/stage17-five-track-build-retry.log` 仍待正式终态。

## Ubuntu v7 构建与 Blockbench 准备（2026-09-21）

Ubuntu v7 NeoForge正式构建task `33156223-9e1f-47ea-80be-bbee23146989` 已succeeded，开始18:52:36Z、终止19:00:13Z；资源索引available，关闭SDK后重开查询仍保留成功。证据 `D:/Hyper-V/Stage17-Linux/guest-evidence-v7/neoforge-build-v7-temporary-proxy*`。本轮使用前次真实下载缓存及临时代理，不能称直连或全冷通过。仅该构建使用的SSH回环转发进程25328已关闭，session64138已终止；未保留全局代理变更。v7真实Wayland窗口、探针验证和主JAR哈希均已保存，GUI进程17531当时仍运行。

为后续Ubuntu手工Blockbench往返，从官方GitHub v5.1.6 release（已通过smart-search fetch获取发布页及expanded_assets）下载AMD64 deb；文件99,922,728字节，SHA-256 `9c5c4aa85bf2ec75e2b4db59bf966460428603f8aa378c65964542a3f84a7a59` 与GitHub发布asset.digest一致，传输到来宾后再次核对。包声明blockbench5.1.6/amd64，已在已授权的测试VM正常apt安装，日志 `/home/stage17/evidence/blockbench-5.1.6-install.log`。下载清单 `D:/Hyper-V/Stage17-Linux/blockbench-5.1.6-linux/download-manifest.json`。尚未据此宣称GUI启动或往返完成。

v8独立身份全链已完成：第二次正式GameTest `65807f5a-94bb-4f0e-82e9-328086b32e07` 真实1/1通过，验证公开resourceId及更新后的hardness7；第一次对应hardness3。两次重开/create/editor/list/update全阶段name与identity相同；43份受管输入的路径集合无增删，只有目标block Java与定义内容按字段变化。未检查Java文本、未修测试ID或环境。证据 `independent-eval/fields/v8-identity/report.md`、`verification.json`（35项审计不是35个游戏用例）。

v9 Windows export已成功，主JAR SHA-256与v8逐字节相同 `1f5ac8ceb49ce0efbf7b7ff4522772034a276b2fc67cae1053f04a0e4b2a4891`，源码副本/清单 `source-candidate-v9` / `source-manifest-v9.json`，日志 `export-win-v9.log`。交付Agent只使用导出包完成独立文档审计：SDK→Stage17→lifecycle→Wayfinder链可用，两个Loader初次/幂等渲染共4次exit0，原输入未改，31份示例未包含build/cache/world/log。仍发现SDK原有Python工作台链接文件缺失，以及Wayfinder README未写先构建步骤；报告 `independent-eval/delivery/report-packaging-v9.md`。已在主工作树补充打包 `docs/user/python-workbench.md` 和源码示例构建指引；这些修改晚于v9冻结，尚未新打包验收。

五轨复验第一次未进入测试：Windows批处理将命令行-D参数中的 `http://127.0.0.1:3067` 拆成Gradle项目路径，保留 `b42cf073a1ba4cf690e88874ccbee222.log`。随后改由仅本次Gradle init脚本设置测试参数，不再通过含冒号的-D命令行传递；真实复验session80060，日志 `.tmp/stage17-five-track-build-retry2.log` / `.tmp/gradle-external/2625adcbb1454d09a8fa4951e15cd850.log`。Fabric1.20.1与NeoForge1.21.1已真实通过；NeoForge1.20.1虽然跨过原无输出阶段并执行eclipse/下载/NeoForm步骤，仍达到10分钟准备上限，未通过。NeoForge26.1.2/26.2仍在该串行任务后续阶段；未重新计入被跳过的三条首轮成功Fabric轨道。

Ubuntu Blockbench5.1.6已通过正常桌面启动打开共鸣熔炉36部件/1张64×32内嵌贴图，截图 `D:/Hyper-V/Stage17-Linux/blockbench-v5.1.6-open.png`。日志存在Wayland/GPU与自动更新DNS告警，但实际模型与贴图已显示；未加no-sandbox等安全禁用参数。原模型从工作区文件复制到来宾Downloads，未改原输入。通过已打开的Copperbench v7桌面原生SDK新建编辑任务 `cb9f033d-0c2a-44b9-9dee-765e1e995ae6`，目标 `models/blockbench/resonance_forge_linux.bbmodel`；原始请求/响应在来宾 `/home/stage17/evidence/linux-model-roundtrip-v7`。此入口为SDK，尚不等价元素到建模的连续UI引导。

通过真实Blockbench菜单 Save Project As，将36部件模型保存到产品创建的编辑副本 `/home/stage17/fixtures/fabric-1.21.1/.copperbench/modeling-tasks/cb9f033d-0c2a-44b9-9dee-765e1e995ae6/edit/model.bbmodel`，确认覆盖的仅是该新任务空白占位副本。保存后36部件，SHA-256 `563c20aacf19fc88351d2f79b210edaef47380ba029dc804a319d8b5355615e1`。菜单快捷键实为Ctrl+Shift+Alt+S；最初Ctrl+Shift+S未触发，原生文件对话框出现较慢，相关截图均保留。尚未导出游戏JSON/PNG或回导/绑定/构建，Blockbench保持打开待继续。来宾显示器GetCurrentState列出1280×720与1920×1080模式，目前仍1024×768，未更改分辨率。

Windows模型Agent仍持有宿主GUI，真实客户端task `80c5e1fa-be63-4be8-b950-3508768219f4` 已到标题和首次世界创建界面，玩家日志Player758；尚未报告视觉/正常退出通过。不得因长时间无终态重启该任务。G17-A/B/C/D继续保持开放。

## 八轨构建合并证据与 Ubuntu 模型回导（2026-09-21）

五轨定向复验最终新增 Fabric1.20.1、NeoForge1.21.1、NeoForge26.1.2、NeoForge26.2 通过；NeoForge1.20.1 仍达到准备阶段10分钟上限。原始 XML 保存为 `.tmp/stage17-five-track-retry2.xml`。随后只在原失败测试工程执行带 `--info` 的准备诊断，确认 Gradle 自动下载项目所需 JDK17，再运行 listLibraries、merge、decompile；诊断8m46s成功，证据 `.tmp/stage17-neoforge1201-diagnostic`。这是项目工具链自动配置到应用 Gradle 缓存，不是全局 JAVA_HOME 修改；不能据此将所有此前失败统一归为网络问题。

正式 NeoForge1.20.1 定向复验5m57s通过，其他七项明确跳过，日志 `.tmp/gradle-external/34737b4c38834d2b8622c82a61d412fb.log`，原始 XML `.tmp/stage17-neoforge1201-final.xml`。`build/stage17-eight-track-build-proof.json` 合并三轮保留记录，逐轨检查实际定义、JAR 哈希与 Block/BlockEntity class；八轨均有真实成功证据。范围仅为该组结构化方块配置的开发源码生成、编译与 JAR 回归，不等于全部字段、全部玩法或同一安装候选验收通过。

Ubuntu Blockbench5.1.6 中通过真实菜单保存36部件工程、修改贴图 namespace/folder、原生导出游戏 JSON 与 PNG；未用脚本修改模型内容。首次贴图另存导致 folder 清空，真实 GUI 恢复 `block` 后重新导出，最终引用 `structured_forge:block/atlas_export`。公开 API 无手填 outputs 的预览自动推导工程、模型和贴图三项 CREATE，无冲突或替换；导入后绑定 `structured_forge:custom/resonance_forge`。构建 task `498e5adf-3680-4d28-b3ca-f4704242f264` 成功、零诊断，JAR 内 JSON/PNG 与导入文件逐字节相同，36部件保留；SDK 断开重连查询通过。来宾证据 `/home/stage17/evidence/linux-model-roundtrip-v7`。这不是几何形状修改，也还未完成整个桌面应用关闭重开、游戏视觉/正常退出或连续 UI 引导。

网络说明已按原始证据细分：Ubuntu 对 NeoForge `neoform-runtime:2.0.18` 官方 POM 的直连返回 connection reset；同一依赖的阿里云、华为云、BMCLAPI 已测 Maven 路径返回404，说明这些路径缺包，不能推广为国内源整体不可用。Mojang 版本 JSON 的官方与 BMCLAPI 请求均200且 SHA-1 一致。临时代理后的 Ubuntu v7 构建成功依赖前轮真实下载缓存，保留该限制。证据 `D:/Hyper-V/Stage17-Linux/guest-evidence-v6/network-mirror-v6.json`、`network-mirror-host-v6.json`、`guest-evidence-v7/neoforge-build-v7-temporary-proxy.json`。

其余六轨独立玩法试作通过公开 bootstrap 无弹窗创建时返回 `USER_APPROVAL_REQUIRED`、退出3，尚无产品签发授权；未伪造批准字段。原始拒绝保留在 `independent-eval/fields/other-tracks-v9`。改为明确提供现有开发测试工程作为输入夹具，要求仅复制源码/资源/配置/包装器，排除历史任务、构建产物、缓存与世界；这些工程含既有方块，不称空白或独立创建成功。Agent 将在自己的副本中使用公开接口创建独立探针并重新构建、运行 GameTest，不能复用主 Agent 成功结论。

Windows 原客户端已进入 `PRD17 Scene C v7` 世界，但前台句柄为空且截图失败，尚未确认桌面锁定原因。已请求用户恢复可交互桌面；原客户端保留，不绕过锁屏，不重启或取消任务，视觉放置与正常退出仍未通过。

## 元素建模连续入口与真实桌面重开（2026-09-21）

本轮在当前工作树增加 S17-07 连续入口：方块/物品检查器可新建任务或选择已有 bbmodel 作为副本来源；`begin_blockbench_task` 可用 `elementId` 默认选择源模型路径，并把目标元素、命名空间、独立 `custom` 模型资源建议与 block/item 贴图目录存入 `elementContext`。任务 ID 重试不能更换该上下文中的目标元素。查询从现有元素定义与导入记录投影独立 `binding`，不增加混合的任务状态。手写源码元素在创建前解释限制，Core 同样拒绝。

桌面桥新增 `openTask(taskId)`，Core 从本工作区持久任务解析编辑副本；不向前端开放任意程序参数或路径。任务必须仍在 editing、源文件无冲突且编辑副本存在。复用进程与资产租约，但编辑器退出不触发候选完成、回导或定义提交。手工打开和复制编辑目录仍可用，不依赖 MCP。

界面展示步骤、源模型候选与游戏导出各自的状态及下一步；进入或返回窗口时重新读取任务。自动建议映射与手工修改明确区分，重新选择元素不会误用前一份自动映射；过期候选不允许应用。导入与绑定仍分开，唯一导入模型默认选中，绑定失败保留已导入文件并可重试；成功关联后才开放本面板构建/客户端入口，提交任务不标成验证成功。已有模型/任务和编辑器中未保存的内容不被进程退出信号替代。

验证：40 项 Java 测试通过，另1项真实安装编辑器启动用例按条件跳过，日志 `.tmp/gradle-external/c8b7f6532fa2470db51d10b4828d4184.log`。新增 `Stage17ModelingWorkflowTest` 通过真实文件完成默认路径创建、候选、自动预览、回导、独立绑定、服务重开与幂等绑定；另测手写目标拒绝不创建任务目录。`BlockbenchModelingServiceTest` 验证上下文重开、拒绝任务改投及取消保留副本；`BlockbenchProcessServiceTest` 验证任务路径启动、保存后进程退出不提交/导入。首次与第二次失败来自拒绝用例误断言同步返回 failed，而产品实际返回 rejected（第二轮明确为 MODEL_BINDING_UNSUPPORTED）；已按合同修正，原日志 `cd9ba41f04384c829cc8bca41078ca3d.log`、`e5bb99f59070455dbec71011fb266569.log` 保留。

UI-Core schema 27项通过 `.tmp/stage17-modeling-schema-final.log`；首次调整schema时 AJV strictRequired 拒绝未就地声明的 not/required 条件，已补齐属性声明后通过。TypeScript 与中文词条检查通过。建模入口/回导/设置/新连续流程在 Chromium1920×1080、1366×768 两组共18项交互回归通过 `.tmp/stage17-modeling-ui-final.log`。连续流程覆盖键盘入口、无需目标路径、重载恢复、自动映射、绑定失败重试及恢复后的绑定状态，另覆盖手写元素前置解释；截图已人工检查。最后补充“复制编辑目录”按钮及常驻导出位置说明属于本轮界面文案/便捷入口，尚未重新冻结安装候选。浏览器 mock 不等于原生 JCEF、真实 Blockbench 或游戏效果证明。

Ubuntu v7 实机追加：通过观察真实窗口后 Alt+F4 正常关闭产品，原PID17531退出，日志 `Exiting Copperbench` 于21:06:52Z；随后经 GNOME 终端重新运行原安装启动脚本，新PID21462。新 Wayland 图形探针验证通过，实际窗口截图 `D:/Hyper-V/Stage17-Linux/product-reopened-v7.png`。随包原生 SDK 连接新桌面，确认原建模 task `cb9f033d-0c2a-44b9-9dee-765e1e995ae6` 仍 imported、元素 consistent，declared/effective modelResource 均 `structured_forge:custom/resonance_forge`，三项导入文件和 JAR 哈希均不变。完整证据复制至 `D:/Hyper-V/Stage17-Linux/guest-evidence-v7/linux-model-roundtrip-after-reopen`，含 `desktop-reopen-proof.json`、关闭日志和原始响应。JAR SHA-256 `cb05312ba4cee03d4e800c078962f71e4a241dcad227aed5c6153ba89b343c47`。这证明 v7 真实桌面重开，尚非新连续 UI 的安装验收，Linux游戏视觉/正常退出仍未完成。

六轨独立 Agent 新发现：Fabric1.20.1/26.2 的 generate 在 Loom 显式本地 IPC 文件处失败，路径为用户默认 Temp 下 loom...ipc；不能归因网络。NeoForge1.20.1 的 Gradle 准备日志 BUILD SUCCESSFUL 后仍出现 RESOURCE_INDEX_UNAVAILABLE / GENERATOR_SOURCE_PREPARATION_FAILED，根因待查。Fabric26.1.2 经仅测试 API 签名适配后真实6项验收断言通过，另1项框架自检单列（task `c05b2675-3dd7-45d0-8a2a-af318586d4f4`）；其他轨道继续。v9候选、首次失败和输入夹具来源均保留，不把 root 开发构建结论代替独立 Agent 结果。G17-A/B/C/D 仍未整体关闭。

v10 已冻结并 exportWin64 成功（1m27s），源快照 `D:/Hyper-V/Stage17-Linux/source-candidate-v10`、27080文件清单 `source-manifest-v10.json`。主 JAR SHA-256 `7fd9737b6f5ee90ab3226c6e6b0485790e3efaf11c3e2a358875d8f0c035ca3e`，导出日志 `export-win-v10.log` / `1c395ca71791413fa47f486daef6ad28.log`。包含本轮连续建模入口和 v9 两项随包文档修复；交付 Agent 正做公开包独立审计。此候选尚未升级 Windows/Ubuntu 安装，不包含新的 Loom IPC 或隔离宿主网络修复。

六轨首轮实际终态补充：NeoForge26.1.2 的独立 generate/build 成功，但正式 GameTest 隔离构建在 Mojang version_manifest_v2.json 处 ConnectException/UnresolvedAddressException，task `43b22c1c-0959-4ed6-863a-b63733e91b4f`，验收0。NeoForge26.2 的独立 generate/build 成功，正式测试宿主下载4865资源后，在 neoforge-26.2.0.63-moddev-config.json 下载时 connection reset，task `096302fa-6bba-4402-8729-4726495e855f`，验收0；独立被测 JAR SHA `fdcf19451415d982ced3826f3dd6f550b9d7e5d88965b2f6010062076aaca058`。这两项有网络日志证据，不能与两个 Loom 本地 IPC 或源码准备失败混记。

针对 IPC 的初步本机只读/临时测试：`.tmp/Stage17SocketProbe.java` 分别使用默认用户 Temp 与仅子进程作用域的 Copperbench/Temp；JDK21 显式 Unix socket bind 均成功，但 Selector.open 均报 Unable to establish loopback connection，日志 `.tmp/stage17-socket-default.log` / `.tmp/stage17-socket-scoped-temp.log`。因此不能假定只换 Temp 即修复当前执行环境，更不能据此修改生成代码或将独立失败改记成功；尚需匹配 Loom 实际 IPC 路径与启动上下文诊断。没有更改全局环境变量。

## 独立失败的分类复验与 Ubuntu 客户端正常退出（2026-09-21）

独立 v9 普通 Windows 隐藏进程轮次，Fabric26.2 的 generate/build 与正式 GameTest 全部通过，六项自定义行为断言加一项框架自检通过，task `9e5e869b-aec4-4a79-85c4-4186bf9d4668`。输入和候选字节保持不变，反编译仍有4658 hits / 2397 misses，不能解释为跳过准备。自然继承的 NO_PROXY 有差异，其余已检查环境项相同；不声称所有系统状态相同。报告 `D:/Hyper-V/Stage17-Linux/independent-eval/fields/other-tracks-v9-native/report.md`。

同轮 NeoForge26.1.2/26.2 的隔离构建与测试源码编译通过，但正式测试宿主从 `maven.neoforged.net` 下载各自 `moddev-config.json` 时 Connection reset，实际执行0项断言。另起一次明确留证的测试子进程轮次，仅给该进程树追加 JVM HTTP/HTTPS 代理及 localhost 绕过，不改全局环境、仓库 URL、TLS、缓存或测试期待。NeoForge26.2 task `1dc3f6c1-59f5-489d-870e-c814c59a1eaf` 正式通过六项自定义断言与一项框架自检。NeoForge26.1.2 task `729974ac-c616-4c15-a238-2d2bdd93585f` 的六项断言和框架自检虽全部通过，但 BUILD SUCCESSFUL 后31.558490秒出现损坏文本，最终 processExitCode=1，仍保持 GAMETEST_PROCESS_EXITED；不能用 XML 覆盖失败终态。报告与原始尾日志在 `other-tracks-v9-native-proxy/report.md`。两轮共享缓存正常存在，不能据此宣布默认网络或空缓存已修复。

上述下载问题不等同国内源整体失效：产品镜像开关未覆盖全部专用仓库及 Mojang 下载；先前具体 NeoForm POM 的三个镜像路径404与普通 Maven 阿里云下载200均保留。Fabric Loom 本地 IPC、1.20.1 模板错误及 NeoForge26.1.2 结束阶段非零退出分别记录，不归为同一网络错误。

IPC 后续直接探针表明：工具启动上下文中的 JDK21/JBR25 均可绑定 Unix socket，但三种 Java 删除 API 均失败；同 JDK、同 TEMP 的普通隐藏 Windows 进程三种清理均成功。证据 `.tmp/stage17-socket-delete-jdk21.log`、`stage17-socket-delete-jdk25.log`、`stage17-socket-delete-native-process.log` 与 result.json。实际缓存 Loom1.7.4/1.17.21 字节码表明其能力探针只测试打开通道，生成源码结束仍清理 IPC 文件。这定位到启动上下文差异，未证明更底层安全机制。产品新增 `GENERATOR_LOCAL_IPC_UNAVAILABLE` 精确诊断和正常桌面/终端启动建议；普通 TCP fallback 提示不误判，网络 reset 仍保留原分类；没有修改 OS 安全策略或第三方运行代码。

NeoForge1.20.1 首轮 SOURCE_PREPARATION_FAILED 已定位：实际生成自定义碰撞箱时，block/plant 模板给三参数宏传入过多参数。修复两个 Loader 的对应模板调用，并保留 offset 语义。增加自定义16×12×16方块和带 offset 植物的实际生成用例；真实编译又发现1.20.1缺少 effects 映射，已补有效原版效果及 Fabric 自定义覆盖，规范输入 SPEED 映射为 MobEffects.MOVEMENT_SPEED。八轨生成检查通过；两个1.20.1 Loader 的真实编译及 JAR 类检查通过，证据 `build/stage17-bounding-build-proof.json`、`.tmp/stage17-bounding-real-build2.xml` 和日志 `0a0d9ef126a34e4fbf52c38084c6d3a0.log`。开发编译不替代独立 Agent 行为验收。首次宏失败、植物缺少必填 fixture 字段、效果映射失败等原始日志均保留。植物缺字段仍产生泛化持久化失败，属于待补的字段默认值/拒绝说明范围。

IPC/生成单元回归15项通过，证据 `.tmp/stage17-bounding-unit-results`；随后效果映射八轨生成通过 `.tmp/stage17-effect-mapping-render.xml`。v10公开交付审计确认此前缺少的随包文档和 Wayfinder 构建说明已修复，但建模便捷入口仅 kwargs，包内缺完整参数 schema。当前源树已补 begin 操作示例、SDK schema 链接并纳入导出，待下一冻结候选独立检查。

Ubuntu 已安装 v7 客户端 task `54121c7f-e4e1-4675-adb9-eba79debf70b` 正式 succeeded。真实 GUI 创建 Creative/Superflat 世界 `PRD17 Linux Model v7`，通过本地单人命令获取物品，实际右键放置36部件模型，前后移动观察正面与侧面；未用命令替代放置。随后 Save and Quit to Title，再 Quit Game；日志保存全部维度、Stopping、BUILD SUCCESSFUL，公开 SDK 重连读到成功终态。三项导入文件和既有 JAR SHA-256 `cb05312ba4cee03d4e800c078962f71e4a241dcad227aed5c6153ba89b343c47` 均未变。完整证据 `D:/Hyper-V/Stage17-Linux/guest-evidence-v7/linux-model-client-v7/client-proof.json` 及同目录任务增量日志；截图 `linux-client-held-model-confirmed-v7.png`、`linux-client-placed-model-v7.png`、`linux-client-model-angle-v7.png`、`linux-client-returned-title-v7.png`。首次快速键入被截断，保留原图，再以逐字符150ms输入并视觉确认完整命令后执行。

该客户端是 v7 开发运行，不是新候选打包 JAR 客户端或连续 UI 的整体验收。观察到物品显示翻译键、第一人称模型占据较大画面，导出无 firstperson display/parent；保留用户导出原样，尚不宣称视觉体验全部通过。Windows 原客户端仍待可交互桌面，未绕过锁屏或重启。G17-A/B/C/D 仍未整体关闭。


v11 已冻结并 exportWin64 成功（1m29s），源清单27082文件。候选位于 D:/Hyper-V/Stage17-Linux/source-candidate-v11/build/export/win64，主 JAR SHA-256 cca89f698965f235b451c77b829ee9b7e2a2578876421907da11dc9a6da69da6，generator-1.20.1.zip SHA-256 22f4ce9cc1b915ef9b0dbf8b0e3a9443cff3c8b1b17abdedbd0a89262fb61acc；详细哈希见 candidate-v11-proof.json。已让原字段 Agent 以自身旧输入的新副本复验两个1.20.1 Loader，让原交付 Agent 复查随包公开合同；不新增 Agent、不覆盖旧结果。此候选仍未完成双方安装验收。

## v11 独立行为失败、结构化资源引用与结果合同补齐（2026-09-21）

v11 字段 Agent 两个1.20.1 Loader 的 generate/build 均成功。Fabric GameTest `09ec8d0d-3498-4a62-ae2e-6f558b233509` 六项执行、五项通过，唯一失败为方块物品堆叠上限16；同一方法中的库存槽数5和库存堆叠16已通过，不能混同库存配置。公开 editor 的声明值、有效值均16，hasBlockItem=true，但实际 ItemStack 上限错误。JAR SHA-256 `45c558841eed45afb232231dea3508919430c3fb4f266aaeb49dd3db7c02dea6`。NeoForge GameTest `fbb60bbb-fd0b-4392-8d4f-9dfb2705aec6` 在执行前返回 SERVER_EULA_APPROVAL_REQUIRED，实际0项；用户本地 EULA 同意仍有效，新副本缺产品签发的 run_server 授权记录，不伪造记录或改判为通过。两轨都未重试，原证据见 `D:/Hyper-V/Stage17-Linux/independent-eval/fields/other-tracks-v11-1201/report.md`。

实际模板检查发现两个1.20.1 Loader 的方块/植物物品注册均直接用默认 Item.Properties，未使用 maxStackSize、rarity、immuneToFire。当前源树已在注册处传入这些已有属性，NeoForge 同时覆盖 DoubleHighBlockItem。新增上限16、稀有度RARE的八轨生成回归，修复前2失败6通过 `.tmp/stage17-block-item-properties-red.xml`，修复后8通过 `.tmp/stage17-block-item-properties-green.xml`，日志 `052146dd04974bf0b5b9bb1d16b440d9.log`。真实编译和新候选 GameTest 仍需单独证明，不能把生成字符串检查当运行通过。

S17-03 新增 MinecraftRenderReferences，按 items 的 model/condition/select/range_dispatch/composite/special 结构，以及 blockstates 的 variants/multipart/apply 提取文件引用。类型、属性、组件、case值不再被视为模型文件；两个文档类型复用现有 MinecraftModelResolver 的命名空间、活动版本、覆盖/未知来源语义，省略 namespace 的模型ID默认 minecraft。未知物品 codec 与 special 内部动态渲染资源给出独立警告 ASSET_RENDERER_REFERENCES_UNVERIFIED，基础模型仍正常校验；没有冒充完整 codec 或 renderer 验证。新增三项反例修复前均失败 `.tmp/stage17-item-reference-red.xml`，修复后与既有资产回归均通过 `.tmp/stage17-item-reference-green.xml`、`stage17-item-reference-existing-green.xml`。真实缺文件仍定位到具体 JSON Pointer。

实际26.1.2/26.2客户端 JAR 的 bow、bundle、shield、chest 和 oak_log blockstate 原始字节被复制为只读测试输入，在两个 Loader 上下文共四组通过：文件引用全部 vanilla_resolved、版本匹配，没有 ERROR；special 警告明确保留。JAR前后哈希不变，输入哈希、完整引用及诊断见 `build/stage17-real-item-references/7c099e1b-a6e0-43f6-b85f-2558af98a9a7/result.json`，XML `.tmp/stage17-real-item-reference.xml`。官方1.21.4发布页经 smart-search fetch 返回的正文仅包含相关标题，字段细节缺失；实现及26.x语义验证依据本地实际客户端资源，不把该不完整抓取当字段合同。首次测试启动把未加引号的-D属性拆成任务名，日志 `.tmp/stage17-real-item-reference.log` 保留；改为仅测试进程 init-script 后实际执行通过，日志 `31d0dc17b5944483abce04d7c1df5442.log`。

v11交付Agent复查7链接、12份Schema的305引用、18个请求形状均通过，但结果data缺少建模上下文/绑定定义，报告 `independent-eval/delivery/report-packaging-v11.md`。当前源树已定义 modelingTask、modelingElementContext、modelingBinding，并约束 get/list 成功结果；保留无上下文旧任务、失败响应和额外回执字段。28项合同测试通过 `.tmp/stage17-modeling-result-schema.log`。Core真实建模→回导→绑定→重开流程同时导出 editing/unbound 与 imported/bound 两份查询投影，两份均通过新Schema，证据 `.tmp/stage17-modeling-actual-result-schema.json` 及 `build/stage17-modeling-contract-projections/4af13427-221c-4bca-afd1-fd5576a164ac`。本轮15项Java相关回归通过 `f28dad49165343cc929e117bc2f158fd.log`；仍待新随包候选独立审计，不据此宣称全部S17-06或G17门禁通过。

v12冻结27084文件，exportWin64成功（1m46s），日志 `D:/Hyper-V/Stage17-Linux/export-win-v12.log`。主JAR SHA-256 `9b7843abe766503b18e9121bb3686b6e1b9f0536ed5b2611db8fac2f95e7a5d3`、generator-1.20.1.zip `4af168534812a4855b1a137de64455b033c9caa4c86a8bdc527aabcf1e844c5f`，哈希记录 `candidate-v12-proof.json`。字段Agent已在全新自身输入副本启动 Fabric1.20.1 六断言复验，交付Agent已启动结果Schema审计；NeoForge缺有效服务器授权记录前不重复发起相同受保护请求，旧失败保留。

本轮开发真实编译最终只证明 Fabric1.20.1通过，新工作区 `build/stage17-structured-block-builds/fabric-1.20.1/workspace-0360a687-7f96-492e-8cb6-8a2e50f956ac`。NeoForge1.20.1新工作区 `workspace-727beece-8eed-4bd1-a3c9-600c04508ab8` 在同步准备时下载 `https://piston-meta.mojang.com/mc/game/version_manifest.json` 失败；后续 client run type 缺失为同轮配置失败，不能据此外推模板编译错误或宣称该轨真实编译通过。原报告 `.tmp/stage17-block-item-properties-build.xml`、日志 `9ecfb930f35b48b7a84322597ea608a0.log`（6m10s），不覆盖上轮实际编译或独立证据。该轮仅有下载失败摘要，未获得底层连接异常，不能直接断言与之前的 Connection reset 完全同因。

## v12独立通过与交互运行状态（2026-09-21）

字段Agent v12 Fabric1.20.1正式通过：generate `d86e3731-d295-4d3e-859c-4b3ba0782afa`、build `033ad324-54bb-4efb-83ad-68a65692eb8b`、GameTest `21af26f3-fc7e-4558-895f-609b0fa76efb`，六项验收全通过、framework0、exit0。方块物品上限16的原失败已在同样六断言中变为通过，测试字节/min6未改，v11原报告未改。JAR `65838cdf4598a22e0004de4b562faf1cc1be5c09d2a32756a38a5e3cc8ccb656`，XML `fca0bfdc72002b6ccde58e7b73875a755d1cd6e7e724c0b7b5c54df82b1556a4`，报告 `D:/Hyper-V/Stage17-Linux/independent-eval/fields/other-tracks-v12-fabric1201/report.md`。仍有进程代理和共享缓存限制，非默认网络或空缓存证明。

交付Agent v12审计确认建模结果契约残余已补齐；33个结果正反形状（15合法、18非法）、12份Schema的320个引用、9条链接均符合预期，包含该Agent此前真实空列表结果。主JAR/指定生成器ZIP哈希符合候选。报告 `independent-eval/delivery/report-packaging-v12.md`；这是公开结果合同证明，不是新GUI/游戏证明。

S17-09当前源码增加渲染日志观察：只有Render thread的有效atlas创建日志才把客户端阶段从starting推进到rendering，task仍running、progress<1，等待真实进程退出。模组初始化、音频启动及服务端日志不触发该阶段。阶段事件沿现有任务记录持久化；真实Ubuntu v7日志中的atlas创建行作为固定观察反例，未把重放旧日志当成新安装客户端运行。原例在修复前失败 `.tmp/stage17-client-progress-red.xml`，修复后相关回归通过；任务记录中的running/rendering也经读取核对。

新增故障反例同时发现：Linux图形失败被客户端捕获后exit0，旧判定会误记succeeded，即使只有模组初始化标记。`.tmp/stage17-client-initialization-red.xml` 保留该失败。当前源码对没有实际渲染证据的已分类图形错误保留失败终态；已有渲染证据后的非零退出仍是一般进程异常，不把之前的环境提示误归为当前初始化失败。最终34项通过（TaskGateway20、ProcessRunner11、SessionRecovery3），日志 `573e7d78b4214e9894ebf186d59728aa.log`，XML `.tmp/stage17-client-final-*.xml`。进程正常退出/异常退出/宿主突然丢失及原始记录保留均在既有真实子进程测试中验证。尚未安装含此修复的新候选。

Windows独立建模Agent单次复查（2026-09-20T23:50:56Z）：原任务 `80c5e1fa-be63-4be8-b950-3508768219f4` 仍running，Minecraft PID37344响应，前台为空、截图screen grab failed；未重启、取消、解锁或绕过桌面。报告 `independent-eval/model/report-v7-current-check.md`。root只读进程信息显示explorer、Minecraft、LogonUI均在Session1，但不能仅凭LogonUI存在确定全部桌面限制原因。Windows视觉/正常退出证据仍缺；其他可推进范围继续。

## v13同源Linux安装与连续建模实机反例（2026-09-21）

v13源快照27084文件，同时 exportWin64 / prepareDebLinux64 成功（1m21s）。Windows/Linux主JAR均为 `9da392d4d2fc494d2d0454e9b5d08b9b7d57271ae63e9abc4a484174220d5860`，源码清单 `D:/Hyper-V/Stage17-Linux/source-manifest-v13.json`。在Windows生成独立deb根目录并打tar，归档SHA-256 `f83e1da11a40dd593b488461b22010eafd936acb563e0b3f75363acbe0319b3b`；Ubuntu校验后以安全解包过滤器解到新目录，仅恢复该候选可执行权限，使用dpkg-deb gzip/z1打本地开发包并重装。deb SHA-256 `50b70e07a316ebde0e926729d57cb3effb6f2d1ffd052604a1cb9f80895d795f`，不冒充CI发行认证。

先观察旧产品窗口，再Alt+F4正常关闭；PID21462于00:08:05Z记录Exiting Copperbench并退出，Blockbench原进程19615保留。安装前后原bbmodel/游戏JSON/PNG/Mod JAR哈希全部不变。通过真实GNOME终端启动已安装v13，PID24777，JBR25.0.2+10-b329.117，00:12:54Z主帧与MCP ready，既有Stage15GraphicalProbeVerifier按Wayland验证通过。随包SDK连接新桌面，旧任务cb9f033d…仍imported、元素配置consistent、模型关联及文件哈希保留。证据在 `D:/Hyper-V/Stage17-Linux/guest-evidence-v13` 的安装/图形/读取证明，截图 `product-ready-v13.png`。Windows该版本目前只导出，未安装。

连续UI实机流程：打开Resonance Forge检查器，选择原源模型，点击从现有模型创建副本；任务 `2ccd97f3-8bae-40cd-a418-69823ec0e94e` 自动带目标元素、namespace、建议模型及贴图目录，无手输工作区目标路径。点击打开副本后，已有Blockbench实例接收了新model.bbmodel标签，但仍保持最小化；在GNOME概览选择其真实窗口，36部件模型正确可见。保存PNG时首次人工转录UUID把ec写为ce，GTK拒绝不存在目录；原错误截图保留，随后用产品“复制编辑目录”与原生粘贴正确保存。这里是测试操作失误，不归为产品路径生成错误。

真实Blockbench保存PNG、保存项目并导出forge.json。其Texture Import提示说明临时目录不在资源包内，本次仅选择Ignore，未勾选永久隐藏提示或改全局偏好。源/导出都保留36部件。首次UI完成候选返回MODEL_EDIT_CHANGED；任务实际sourceChanged=false、hasSavedChanges=true，实机返回窗口未触发可靠哈希刷新。手动刷新后同一UI完成成功，state=ready_to_import。继续点击自动识别/预览，实际返回MODEL_TEXTURE_ATLAS_PATH，公开SDK只读重现同一码及英文详细原因：导出的纹理ID为structured_forge:atlas_export，缺少block/item目录。这项保护正确阻止导入，但中文提示只泛称检查文件/纹理/冲突，没有说明Folder修复。

完整现场、原始bbmodel/JSON/PNG及哈希已复制到 `guest-evidence-v13/continuous-ui-before-fixes-captured`，截图相对清单 `guest-evidence-v13/screenshots.json`。源资产和旧JAR仍未改变，没有回导、绑定或新客户端通过声明。首次证据脚本未捕获SDK的NativeApiError而退出，空目录保留；修正为捕获该正式异常并从details记录响应，使用另一个新证据目录，未改写旧失败。副本/编辑器现场继续保留。

针对实机反例，当前源码（晚于v13）已让“确认磁盘保存并生成候选”在本次显式操作前调用get_blockbench_task读取实时哈希和源状态；不依赖窗口focus，不改Core哈希/版本保护，不自动重放失败写请求。浏览器中“不触发focus的保存”反例修复前失败 `.tmp/stage17-finish-refresh-red.log`；修复后通过。同时保留“查询后又保存”的竞态反例，证明首次只提交一次命令、失败后需再次显式操作。

Core建模异常投影现在保留detail，并给MODEL_EDIT_CHANGED和MODEL_TEXTURE_ATLAS_PATH独立中文行动提示；其他建模异常显示原有具体原因。29项Java回归通过（Workflow2、ModelingService9、ImportService18），日志 `3fb0a0cfa7b446f69a7ec85c7199a864.log`；18项Chromium1920×1080/1366×768相关界面回归通过 `.tmp/stage17-modeling-recovery-ui.log`，截图已检查。TypeScript和379个引用词条检查通过。此修复尚未冻结或安装新候选；实机回导和客户端路径仍须在后续候选继续，当前侧栏长路径与反馈需要多次滚动的问题也保留为体验观察。


截图复核另见1366宽资产工作台右上角的资产引用徽章被逐字换行（.tmp/stage17-modeling-recovery-ui-artifacts/stage17-modeling-flow-elem-d9b39-rt-binding-and-verification-compact-1366/guided-modeling-bound.png）；现有交互断言未覆盖该徽章布局，S17-04可辨识展示仍需修复/补验，不能以18项交互通过关闭UI门禁。

## 建模侧栏与索引计数可读性（2026-09-21）

进一步定位，上述徽章实际是“已索引(过滤数/总数)”，不是入站引用数。根因是`.connection-state > span`把文本span也设置为7×7像素。当前源码只给aria-hidden装饰圆点该尺寸，状态文本不收缩/换行；资产页头允许动作组换行并保持内容高度。新增真实DOM尺寸断言与截图在1280×720、1366×768、1920×1080均通过，确保计数在页头范围内且没有逐字换行，不外推为原生DPI验收。

建模卡片将完整资源路径、恢复点及保存详情收起，已有任务时收起再次创建入口，保留刷新与各阶段操作。元素检查器内去掉第二层固定高度滚动，提示在滚动时保持可见；资产页仍限制展开面板高度以保留资产列表。标题优先显示目标元素/模型文件名，不展示整条路径。旧任务没有elementContext时文案明确为当前元素没有关联任务；没有改动任务状态、候选保护或导入语义。

28项相关浏览器回归通过`.tmp/stage17-modeling-layout-ui.log`；随后补充“创建后打开按钮在可视区域、路径详情默认关闭”断言，两种窗口下2项通过`.tmp/stage17-modeling-layout-action.log`。截图分别保存在对应artifacts目录并已人工核对，索引文本恢复正常、可选详情保持可发现。TypeScript通过`.tmp/stage17-modeling-layout-types.log`。这些源码改动晚于v13，待下一候选原生安装复验；不能据此关闭完整S17-04/07或G17门禁。

## v14 安装与独立 NeoForge 1.20.1 复验准备（2026-09-21）

v14 普通源副本已完成 Windows 导出与 Linux deb 根目录准备，日志 `D:/Hyper-V/Stage17-Linux/export-platforms-v14.log`，1m22s；源清单 `source-manifest-v14.json`。两平台主 JAR SHA-256 `5cf2dbf14ecc98663ef2575bc9ee287836afd6bdf85317ce951fa1330fa9b46d`，1.20.1 生成器 ZIP `4af168534812a4855b1a137de64455b033c9caa4c86a8bdc527aabcf1e844c5f`。原 v13 图形进程24777经真实 Alt+F4正常退出后，Ubuntu 本地安装 v14 deb，包 SHA-256 `30111197b692c61aeb7d65c3e6e259d317728fb733a07bf0f54d8ed89f9171f5`。

真实 GNOME 终端启动安装后的 `/usr/bin/copperbench`，新 PID27294，Wayland JCEF/MCP 图形探针通过。随包公开 SDK 读取原 v7 已导入模型任务仍为 imported，元素配置 consistent，原模型、JSON、PNG、已有 JAR 四项哈希保持不变。证据 `D:/Hyper-V/Stage17-Linux/guest-evidence-v14`、`product-ready-v14.png`。仅证明本轮安装、启动及旧资产保留；v14 连续建模与新客户端运行尚未通过。

按用户此前明确批准的本地测试及 EULA 范围，经真实桌面“AI 与 MCP → 任务授权”填写、审查并确认，为独立字段 Agent 的 `/home/stage17/independent-eval/fields/neoforge1201-v14` 签发2小时授权，范围仅 edit/build/test/run_server，EULA已接受。公开 bootstrap 查询确认授权 `5a46f451-ea3e-49eb-893d-924090e0aac3` 有效至2026-09-21T03:43:29Z；未自行写入授权记录或绕过确认接口。截图 `v14-grant-confirm.png`、`v14-grant-issued.png`。已交还原独立 Agent 使用未修改的六项断言继续 generate/build/GameTest；尚无本轮正式终态。允许其仅测试进程使用临时 SSH 回环代理，须单独留证，不能据此宣布默认网络或冷缓存通过。

## v14 Ubuntu 连续建模、客户端与重开（2026-09-21）

真实 v14 UI 对保留的 v13 错误候选重新预览，显示 `MODEL_TEXTURE_ATLAS_PATH` 的中文修复步骤：设置 Blockbench 贴图 Folder 为 block/item，保存并重新导出；候选变化需新任务。取消旧任务 `2ccd97f3-8bae-40cd-a418-69823ec0e94e` 后保留原副本、候选及失败证据。随后从目标元素选择现有模型创建任务 `3741e1a5-5300-4c85-8e1f-341938373501`，经产品按钮打开实际 Blockbench5.1.6的新标签，使用“复制编辑目录”粘贴到原生保存窗口。没有手输工作区目标路径，也没有改写导出 JSON。

实际 Blockbench 保存 PNG 后出现资源包提示，选择 Ignore且未改变全局偏好；贴图 Properties证实 Folder为空，通过UI设为block，Save Project并导出forge.json。返回产品未手动刷新，直接 Finish一次成功，证明新的保存状态读取覆盖了原生焦点事件缺失。自动回导预览识别36部件模型和PNG，建议 `models/custom/resonance_forge.json` / `textures/block/atlas_export.png`；预览明确.bbmodel替换、JSON/PNG内容相同，确认后回导，revision4→5。原有效关联仍指向 `structured_forge:custom/resonance_forge`，不冒称发生了新的绑定写入。

从连续流程点击构建，task `af4ae1a5-dd5c-4124-a302-4b85e343aa02` succeeded（02:12:49Z–02:12:57Z）。新.bbmodel SHA-256 `fac1b2e080b22e908449aa2fa4da0f31c21ae365a074baddababef2f0218c4a8`，游戏JSON/PNG字节与原资产相同，JAR仍为 `cb05312ba4cee03d4e800c078962f71e4a241dcad227aed5c6153ba89b343c47`；实际打开ZIP核验两资源哈希匹配。没有把未变化的JAR包装成新玩法产物。

真实客户端task `3a4a05ad-ebc1-4ed5-9443-ecf9081b73ef` 从02:24:30Z运行到02:39:58Z；渲染阶段公开查询为running/progress0.7、`task.run_client.rendering`，实际UI明确仍运行。重进原世界 `PRD17 Linux Model v7`（本次Player568），看到已保存熔炉，切换物品并真实右键新增放置；随后 Save and Quit to Title、Quit Game，日志有区块保存/Stopping/BUILD SUCCESSFUL，产品才记录succeeded。尝试 `/time set day` 被游戏拒绝，原截图/日志保留，未更改世界权限。可见模型无紫黑缺贴图，但物品翻译键与偏大的第一人称呈现仍保留，不宣称美术验收无缺陷；这是开发客户端而非独立JAR游戏宿主。

真实退出产品PID27294，再从GNOME终端重开为PID32304；图形探针通过，公开SDK读取新模型任务imported、绑定bound、配置consistent，build/client成功终态均保留，四资产/JAR哈希不变。证据 `D:/Hyper-V/Stage17-Linux/guest-evidence-v14/continuous-model-roundtrip`、`client-rendering-observation.json`、`client-final-observation.json`、`reopen/proof.json`，61张本轮截图及其SHA清单位于screenshots。测试启动辅助脚本末尾多余CR空行在产品正常退出后产生shell错误，截图 `v14-editor-normal-close.png` 保留；不以辅助shell退出码证明产品正常退出，依据真实任务终态、产品退出日志和PID消失。后续正常关闭PID32304与Blockbench19615以释放测试机内存。

本轮原生流程还发现：已有取消任务时，新建任务位于其后且创建区不收起。当前源码已在成功创建后收起创建区、将新任务置前并移动键盘焦点，重开按活动状态/创建时间排序；取消卡片不再误称保留的候选“未生成”。6项两窗口浏览器回归通过 `.tmp/stage17-modeling-replacement-ui.log`，TypeScript通过 `.tmp/stage17-modeling-replacement-types.log`。这些改动晚于v14，尚未作为新的安装候选验证。

## NeoForge 1.20.1 内存诊断与独立复验（2026-09-21）

独立Agent v14首轮generate `3e68e819-1da9-47c7-b06d-fa3f24d9e73a` failed，failureId `1d79c990-c4dd-430f-9311-756fe8191c9d`，NeoFormDecompile失败，build/GameTest未启动、验收0。公开诊断仍泛化为GENERATOR_DEPENDENCIES_UNAVAILABLE。只读kernel日志确认02:02:00.190756Z全局OOM杀Java29807，anon-rss3318524kB，约0.974秒后反编译报失败；缺少该次PID到反编译命令的直接对应，因此保留“强时间关联”而非伪造exit137。原报告、summary未改，补充诊断位于 `D:/Hyper-V/Stage17-Linux/independent-eval/fields/neoforge1201-v14-preparation/diagnostic-followup`。

宿主物理内存可用不足1GiB，未增加VM物理内存上限或改宿主配置。按既有创建测试环境授权，正常关闭本轮GUI/编辑器后，仅在Ubuntu新增8GiB临时swap文件 `/home/stage17/stage17-v14-test-swap.img`，总swap12GiB；未改fstab，证据 `guest-evidence-v14/memory-followup/configuration.json`。旧4GiB swap保留，临时文件尚在使用，应在测试结束且内存允许时关闭。原Agent在独立memory-rerun目录复验同一候选、源输入、六断言/min6及堆参数，重新建立仅测试进程代理。

memory-rerun已实际通过generate `0725a9d0-cb9b-43ae-ae5d-67965de814c9` 和build `52c5297a-812c-4c13-9a43-ccb13185488c`，独立GameTest `d8d1e00f-b88b-40d8-8a90-7c29b97ad144`仍在运行。生成阶段采集到ConsoleDecompiler PID33624/PPid33366及本工作区cwd，便于核实进程身份。本轮不能冒称首轮通过或冷缓存/默认网络通过。

针对真实日志明确报neoFormDecompile失败的情况，源码新增 `GENERATOR_DECOMPILATION_FAILED` 及中文“检查反编译/系统日志，失败阶段本身不证明下载故障”诊断；只识别该任务的明确FAILED/Execution failed行，普通开始/UP-TO-DATE/ClassPath任务不归此类，具体Loom IPC诊断优先。尚待本轮Java检查，不改变已冻结v14或独立Agent结论。

后续Java检查正式通过：MCreatorGenerationPreparationTest7项、Fabric1211TaskGatewayTest21项，日志 `.tmp/stage17-decompilation-diagnostic-test.log` / `0b44c8f587ae4fb892fe47f0f994db81.log`，2m8s；分类矩阵验证明确失败/普通开始/缓存命中/相似classpath任务及定义保留，任务服务验证稳定代码、中文消息key和日志入口。XML已独立复制到 `.tmp/stage17-decompilation-*.xml`。380/380引用中文key检查通过。建模多任务定位及既有竞争保存/拒绝重放回归合计10项通过 `.tmp/stage17-modeling-replacement-full-ui.log`。

memory-rerun正式GameTest仍失败：task `d8d1e00f-b88b-40d8-8a90-7c29b97ad144`、failureId `3cd89ccb-0cfa-4027-b9f7-d6d34fc8b0a3`，03:01:21.640Z–03:21:22.739Z（1201.099s），processExitCode124，六断言0执行，XML不存在。真实模组JAR SHA-256 `89d4fc66327dd286583de544201ef1738b766422a01182a36ab3887a79c4b0ac`、sourceSnapshot `c77b0f9627f9c2432fbf658e154ea6153f4984b07e9158dd8ce90eff81d04cf5`、sourceCurrent=true；不能作为验收通过产物。此任务精确时间窗kernel查询无记录，未发现新OOM，记录的两个反编译进程均有命令/cwd关联。自己的临时代理隧道已关闭。

只读资源观察证实下载部分进行：asset index5 SHA-1 `78fe335ef048443d060bc53ace10bb0f41af7d50`，3598逻辑项/3575唯一对象；已落盘1796对象163052441字节，仍缺1779对象487250407字节，已有文件尺寸均符合索引，未声称逐文件内容哈希全部验证。全部对象ctime落在03:08:43.827085Z–03:21:22.189644Z，直到超时前约0.55秒仍有落盘；没有逐文件公开进度不等于完全停滞。该时间属性本身不证明唯一进程来源。独立补充记录 `memory-rerun/asset-download-observation.md` 与JSON保留，正式超时报告未改。

Root于03:32:27Z检查新增swap仅使用约54MiB且可用物理内存3.3GiB后，停用 `/home/stage17/stage17-v14-test-swap.img`，保留文件与原4GiB swap，未改fstab；证据 `memory-followup/cleanup.json`。另让原Agent准备仅执行已有测试宿主声明的 `:neoFormJoined1.20.1-20230612.114412DownloadAssets` 的独立依赖下载，正常Gradle缓存落盘、单独60分钟准备上限，保持正式验收20分钟预算和旧失败不变；不运行游戏、不手工填缓存、不改断言，尚待实际准备结果。

独立试作还暴露Python README缺少直接发现既有授权的bootstrap命令。源码README补充真实已核验的Ubuntu `/usr/bin/copperbench bootstrap list-authorizations`、Windows入口及授权字段检查，去掉依赖内部UiCore.Operation查操作名的建议；链接公开schema与授权指南。导出规则增加授权指南和其客户端验收引用文档。该文档/打包调整与上述诊断、任务定位改动均晚于v14，待v15候选打包审计。

## v15 候选冻结与下一轮准备（2026-09-21）

已在普通源副本 `D:/Hyper-V/Stage17-Linux/source-candidate-v15` 冻结27084文件，清单 `source-manifest-v15.json`，未创建worktree、提交或在原仓库执行export/jar。Windows导出与Linux deb根目录准备均成功（1m33s，`export-platforms-v15.log` / `2571e3f2b3f349bfb42f1a6c1fd58327.log`）。两平台主JAR SHA-256均为 `dad7f964f4988786b68d1c2f1429cc1f433687ec94dc0e3471933c5ee3c8aca0`，1.20.1生成器ZIP仍为 `4af168534812a4855b1a137de64455b033c9caa4c86a8bdc527aabcf1e844c5f`。四项公开文档均实际随包且两平台哈希一致，见 `candidate-v15-proof.json`。尚未安装v15，不将v14原生验证套用于v15；原交付Agent正在独立进行窄范围文档/包审计。

字段Agent的独立依赖下载于03:53:36Z启动，supervisor38345、wrapper38391，仅执行已有host的 `:neoFormJoined1.20.1-20230612.114412DownloadAssets`，`--info`确认任务集合只有此项；与正式GameTest结果分开记录。任务开始后已有缓存对象数曾从1796降到35，随后增长到138，表明正常Gradle任务重新处理了输出，具体机制尚未确定；Agent未手动改写/填充缓存。保留原进程观察，不重启或提前宣称准备完成。后续还需检查正式测试是否会再次重新下载，不能仅凭一次预热推出验收已恢复。
