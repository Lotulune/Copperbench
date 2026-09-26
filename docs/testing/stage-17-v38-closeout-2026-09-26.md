# PRD17 v38：默认值候选与客户端重启补验（2026-09-26）

最终结论：v38 开发候选验收通过，尚未提交或发布。用户授权后，Ubuntu v38 安装及安装入口八轨复验通过；v37 新进程恢复同一世界模型、保存和正常退出通过，MCP 已留证并 detach。最终完整回归为 983 项：924 通过、59 条件跳过、0 失败/错误。下文保留首次推进与续接各自的证据范围，不把历史失败或条件跳过改写成通过。

## 候选和默认值

候选位于 `D:/Hyper-V/Stage17-Linux/source-candidate-v38`，从当前非干净工作树复制 27,227 个源文件，逐文件核对 SHA-256。未创建 Git 工作树、提交或推送。Windows 和 Linux 导出成功，主 JAR SHA-256 均为 `6797747dbd09c2cbaf9a383784f751ff65ddcc27e4e18ec775b36c68ab594879`。冻结源审计无变动，22 项随包公开文件与源一致。

与 v37 比较，Linux 导出 1,050 个文件中只有主 JAR 改变，没有增删文件。JAR 中 2,159 个 Java class 字节不变，19 个变化 class 均属于 `WorkspaceApplicationService` 及其内部类；源码比较只有 `structure.poolName` 默认注入移除和 `keybind.keyBindingCategoryKey` 默认改为 `misc` 两处语义变化，不能将所有变化 class 数当成功能变化数。

通过产品启动器及随包 SDK 验证 1.20.1、1.21.1、26.1.2、26.2 × Fabric/NeoForge：

- Windows v38 导出目录：八轨全部通过。
- Ubuntu v38 独立产品目录：八轨全部通过；本记录不把独立目录执行称为系统安装验证。
- 每轨创建结构和按键元素，核对实际定义、按键分类 `misc`、`triggerKey=K`、结构不含顶层 `poolName`；关闭产品再重开，revision 和定义字节保持。
- 每轨另验证显式顶层 `poolName` 和未知按键字段仍被 `FIELD_UNSUPPORTED` 拒绝，不写文件、不推进 revision。
- v37 对照在首条 Fabric 1.20.1 的结构默认创建即失败，诊断路径 `/poolName`；保留原失败报告，没有继续冒称其余轨道已复现。

范围是默认创建、落盘、严格拒绝与重开，不是新增十六条构建或玩法验收。机器证据在 `output/stage17-v38/defaults-windows/proof.json`、`defaults-linux-proof.json`、`defaults-v37-baseline/proof.json`。

## Ubuntu 准备

已在独立目录制作 `/home/stage17/copperbench-stage17-v38-amd64.deb`，SHA-256 为 `d396eb9b54d29d7cb5db436785ceec44186a6b8182da742c2b75d382b27de904`。1,050 个产品文件与 Windows 构建出的 Linux 导出逐一一致。准备后 `/opt/copperbench/lib/copperbench.jar` 仍是 v37 哈希，未安装新包；升级需用户明确授权。

默认 IPv4 SSH 连接超时；复用了已有可信密钥和固定 host-key alias 的 IPv6 链路本地地址完成准备/验证，没有修改网络配置。该结果不证明默认直连网络通过。

## Minecraft 桥续接

本轮 `mc_doctor` 成功，版本 `0.2.0`；最初 `mc_list_windows` 返回空列表。对历史退出会话 `19f82258-1a75-4fbb-9978-3eb0d218a049` 调用 `mc_detach` 返回 `SESSION_NOT_FOUND`，因此无法追溯补写该旧 worker 的验证或清理成功记录。旧证据保留，新进程补证另建会话。

新客户端通过 v37 产品及随包 SDK 启动，task `fec60b90-d36c-4c6a-ad6f-ea5c15907a76`，PID `33292`，window `1837976`，游戏目录为原独立 model 工程的 `run`。桥始终报告 `focused=false`；首次 attach 的聚焦尝试未成功，随即 detach。本会话没有 computer-use 工具，已请求用户点击游戏并保持前台，未使用替代输入控制器。

随后仅以 `focus=false` 绑定并记录 `unverified`，没有再次抢焦点。会话 `82465b85-b2dc-434a-898f-39e378849071` 的 `mc_record_verification` 和 `mc_detach` 均成功，原始文件在 `output/minecraft-validation/82465b85-b2dc-434a-898f-39e378849071/`；首次新会话 `cf91dc90-2651-4ba4-966b-1924b02e7130` 也已 detach。

本轮实际玩家交互、step 批次、菜单流程、截图、拒绝批次和 Jev 调用均为 0，输入/截图耗时为 0。两次新会话共执行 2 次 attach、1 次验证记录、2 次 detach；doctor、窗口查询和旧会话清理尝试属于准备/焦点检查，不属于游戏交互。客户端任务仍为 `running`，终态和玩法端到端耗时未产生。SDK 保活进程仍运行，日志与状态持续写入 `output/stage17-v38/client-restart/`；不要另起同一工作区写入会话或第二个输入控制器。本轮是主代理补证尝试，不冒充新增独立试作。

历史截图经主代理实际查看后已生成菜单模板 `output/stage17-v38/model-menu-profile/profile.json`，固定世界 `PRD17 Scene C v7` 和 854×480 英文布局。本轮尚未调用菜单流程，因此模板准备不记为导航通过。

## 全量回归和夹具修正

完整运行 `pwsh -NoProfile -File scripts/run-gradle-external.ps1 test --no-daemon` 用时 19 分 53 秒：983 项、3 失败、0 错误、59 跳过。该失败记录保留，不改写为全量绿色。完整日志为 `output/stage17-full-regression-2026-09-26.log`，XML/哈希在 `output/stage17-v38/regression-evidence/full/`。

1. `Stage17ProcessLifecycleTest.nonzeroExitRemainsFailureEvenAfterReadiness` 在 15 秒等待夹具就绪时超时，尚未执行退出码断言。相同源码在冻结 v38 的独立定向运行中 6/6 通过；没有修改超时或产品生命周期逻辑。未确定首次启动超时根因，不能称其已被修复。
2. 两项 37 类元素的持久化测试在全量严格环境暴露了不完整夹具：启用的盔甲部件缺纹理、村民职业缺工作站。补齐后首次联合运行持久化 24 项和旧工程转换 15 项均通过；该运行顺序不作为严格模式覆盖保证。
3. 为使夹具问题能在单独运行时被发现，两项测试现在显式收集 `GEValidator` 诊断并要求为空。新增断言确实发现盔甲/工具数值、实体生成蛋颜色、附魔槽位和流体地图颜色仍依赖自动修正。最终夹具使用声明的合法数值初值并显式提供上述输入；方块保持其较窄的公开字段合同，不放宽未知字段拒绝。中间失败日志全部保留。
4. 最终 `WorkspacePersistenceCompatibilityTest` 24/24 通过，0 失败/错误/跳过，包含两条 37 类路径、未知字段拒绝、保留/重开以及已有回滚用例。命令通过 `--tests net.mcreator.workspace.WorkspacePersistenceCompatibilityTest` 选择该类；日志 `output/stage17-v38/persistence-verified.log`，XML/哈希在 `regression-evidence/final-persistence/`。本轮没有在修正后再跑完整 983 项。

这次新增源码改动仅在持久化测试夹具和断言。冻结后的已列源文件变化只有该测试文件和 PRD 状态文档，产品运行源码与已验 v38 相同；新测试版本通过补丁 `output/stage17-v38/persistence-fixture.patch` 单独留证，没有覆盖冻结源。限定文件的 `git diff --check` 通过。

## 用户授权后的安装与客户端续接

用户明确授权继续操作后，在包哈希、现有 v37 哈希、产品无运行进程和软件包变更范围核对通过后，2026-09-26 10:54:38 UTC 安装指定 v38 deb。安装后主 JAR 为预期 `6797747d…94879`，原工坊 `.bbmodel`、游戏模型 JSON、纹理和 Mod JAR 四项哈希不变。通过实际 `/usr/bin/copperbench` 启动器、`/opt/copperbench/sdk/python` 再执行八轨创建/拒绝/重开验证，全部通过。证据在 `output/stage17-v38/resumed/installation-proof.json`、`install-plan.log`、`install-dpkg.log` 和 `defaults-installed-proof.json`；不是独立目录结果替代安装入口。

首次继续时窗口仍未聚焦，会话 `71abce4b-9e4a-4723-8012-2618404058b9` 记录 `unverified` 并 detach；没有发送输入。用户实际点击游戏后，桥确认相同 PID/window 的 `focused=true`，新建会话 `4dea8899-4d3c-4512-9c9d-8f187449a65a`，先观察实际标题图，再调用已审查模板的 `enter_world`。流程筛选精确世界名 `PRD17 Scene C v7` 并进入，终图 `frame-283c7afe-a952-410f-b828-f4ede882857b.png` 中原铜棕色低模型、原位置/视角和快捷栏数量 3 可见，与前次退出前的模型证据一致。本轮没有新放置操作。

历史已正常退出的 PID 为 `3500`，本轮 PID 为 `33292`，因此补上了实际进程边界后的世界模型恢复检查。日志记录新玩家 `Player429` 登录相同坐标 `(-7.886104736066716, 64.0, 3.300000011920929)`；前轮为 `Player369`，故本轮不宣称固定 UUID 玩家的库存连续性。该客户端沿用原独立 model 工程，是 v37 开发客户端路径；不冒充新一次 v38/Ubuntu 游戏测试或新建独立 JAR 宿主。

`save_and_quit` 先成功保存到标题，最终退出点击已执行；窗口关闭使原回执保持 `blocked / WINDOW_NOT_VISIBLE`，内部最终批次为 `interrupted / completed_actions=1`，未改写成工具 completed。后续检查确认 PID 已退出，日志含该世界全维度保存与 `Stopping!`，公开 task `fec60b90-d36c-4c6a-ad6f-ea5c15907a76` 为 `succeeded`，关闭 SDK 再重开读取仍为 `succeeded`，产品及工作区 `src` 文件指纹未变。`mc_record_verification` 据此记录限定范围 `passed`，`mc_detach` 成功并完成哈希清单，44 项会话证据哈希复核一致。SDK 保活脚本现已正常结束。

操作与耗时分别计量：

| 项目 | 本轮事实 |
|---|---|
| MCP 调用 | 15 次，含最初失焦准备；有焦点会话 8 次 |
| 输入批次 | 6 个本地菜单批次，5 completed、1 关窗 interrupted，13 个动作已完成 |
| 输入前拒绝 / Jev | 0 / 0 |
| 输入耗时 | 2,562.87 ms |
| 截图 | 35 次、2,404.88 ms，含菜单状态机中间截图 |
| 两次菜单流程 | 进入 9,734.21 ms；保存退出 1,572.96 ms |
| 有焦点会话端到端 | 245,543 ms，含主代理核对日志/任务/记录验证 |
| SDK 客户端任务 | 17,658.53 秒，含前一轮启动后长时间等待用户，不能当作输入耗时 |

未据此宣称速度提升。完整动作、截图和日志在该 MCP 会话目录，任务/指标汇总在 `output/stage17-v38/resumed/client-proof.json` 和 `bridge-call-timing.json`。旧失联 task 和失效控制会话仍保留原记录，不追溯伪造其成功终态。

## 最终完整回归与门禁核对

授权后的第一次全量仍有 2 项持久化失败，生命周期 6 项已通过：983 项中 922 通过、59 跳过、2 失败。日志 `resumed/full-regression.log` 和 `resumed/full-regression-evidence/` 保留该结果。根因是夹具满足字段校验，但生成模板还依赖盔甲猪灵中立条件、村民职业音效、实体生成类别、特征生成阶段和工作区版本号。

测试现为这两条 37 类路径增加局部模板日志捕获：模板错误收集后必须为空，捕获器在 `try-with-resources` 结束时拆除，不改变全局上游错误策略。该断言在单独运行时实际检出了其余缺项；补齐合法输入后，两条路径通过。修改仍仅涉及测试文件，不改变产品/生成器代码或已经安装的 v38 包。

最终再次执行完整 `test --no-daemon`，19 分 38 秒成功：**983 项，924 通过，59 条件跳过，0 失败、0 错误**。原失败的两项持久化测试和 6 项生命周期测试全部通过；没有增大启动超时、跳过失败用例或移除严格校验。最早一次生命周期启动超时的根因仍不能确定，仅记录后续定向及两次完整上下文未重现，不声称做了产品修复。

最终日志 `output/stage17-v38/resumed/full-regression-final.log`，原始日志 `.tmp/gradle-external/5bb91f41a3c14702963dd3bafbbd230b.log`；全部 XML、逐套件统计/跳过项和哈希在 `resumed/full-regression-final-evidence/`。最终测试源 SHA-256 `590cb4354cb64f8890bed6e5113412038ffa1de4d9103b8530087fb9507cf8eb`，相对冻结源的测试补丁为 `resumed/persistence-fixture-final.patch`。运行源码与冻结 v38 一致，Windows 和 Ubuntu 已安装主 JAR 哈希相同，见 `resumed/final-source-audit.json`。

G17-A～D 按原 PRD 的受影响范围完成证据核对，汇总见 [最终门禁核对](stage-17-final-gate-review-2026-09-25.md)。条件跳过保留为未执行，本轮不扩大历史八轨/平台/UI/独立试作的证据范围；不声称固定玩家 UUID 库存连续、Ubuntu 默认直连网络、全量冷缓存或三个无介入完整试作通过。历史失联 task 继续保持未知。

本轮已获授权安装 Ubuntu v38；未提交、推送或发布候选，未推进与提交绑定的 `product-status.json`。开发候选验收与公开发行分别记账。
