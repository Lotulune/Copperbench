# 2026-10-15 优化候选验收报告

本报告保留旧 `b1a69d244ab7337608cc302826ed55d4a707f37f` 基线上的候选与补修检查。下文的“当前”“本轮”和 0.1.4 配置均指该历史阶段；源码现已进入最新主线上的 0.1.6 发布准备，见[整合准备记录](maintenance-release-0.1.6.md)。这里的哈希、用例数和玩家观察不转作最终 0.1.6 二进制的验收结论。

**后续独立复审发现的13项缺陷已在受影响无界面范围修复并验证通过；历史Windows C4选定验收与Linux run3受限无界面跟进保留原结论。** 候选4完成Fabric真实诊断修复、同revision输入失效、两次客户端同世界持久化、普通／精确导出、旧同路径数据保留，以及NeoForge构建／渲染／加载／正常退出smoke。Linux和未执行范围另列；C1/C2的通过、失败与强清始终保留原身份。

C4及其前序阶段包含确定性工程与实际原生／游戏观察；Linux run3限于无界面范围，13项后续补修的验证范围另列。未恢复已终止的新手用户或自治 Agent 研究。本地版本配置仍为 **0.1.4**，本批候选未发布；官方已发布 Windows／Linux 0.1.5 的身份与范围仍见[当前下载](../releases/current.md)，不由本轮改写。执行过程与需求分组见[实施计划](../roadmap/optimization-plan-2026-10-15.md)，面向使用者的唯一当前入口为[状态页](../status.md)。

## 独立复审后的13项源码补修（本轮）

独立复审 `output/optimization-2026-10-15/post-completion-review/summary.json` 于2026-10-15记录54项需求检查和13项缺陷（P1 3项、P2 8项、P3 2项），结论为“尚未全部完成”。该结论及原始失败探针保留原身份；本节单独记录后续补修，不将复审原件改写为通过。统一检查与必要定向复验的最终结论为 **13项缺陷已修复，受影响无界面检查通过；1项既有Java按需用例跳过**；逐项关闭与命令计数见 `output/optimization-2026-10-15/review-fixes-1/` 下的 `finding-closures.json`、`validation-summary.json`。

| 复审缺陷 | 本次源码补修 |
| --- | --- |
| ONB-REVIEW-01 | 普通模组导出取本次实际执行的Gradle归档任务声明文件并核对hash，不按目录内容或修改时间挑JAR；资源包／数据包保留固定ZIP合同 |
| ONB-REVIEW-02 | 自定义输出父目录在输入观察开始前准备；只排除成功发布且交付回执hash仍匹配的精确文件，其他输入变化仍保留；保护已有文件与源码／依赖／元数据／运行目录 |
| ONB-REVIEW-03 | 引导在引用读取完成前被明确关闭时，迟到结果保留关闭偏好和原对象／任务引用 |
| REC-F01 | 未知／进行中的保存结果保留恢复会话，即使草稿重新等于旧基线也不自动清掉 |
| REC-F02 | 字段变为只读或被移除后仍保留原草稿和非法中间输入；不会把只读字段重新作为可写变更，明确丢弃入口保留 |
| REC-F03 | 确认重读使用既有工作区／revision保护；读取期间版本变化时保留原草稿与未知状态 |
| REC-F04 | 编译诊断所属元素来自任务捕获的生成路径映射或MCreator实际关联文件；MCreator与Core共用持久化ID优先的解析器，ID缺失／无效时保留原确定性后备；无依据、歧义和手写归属不猜元素 |
| REC-F05 | 同文件各条编译诊断使用各自的源码行号；SourceWorkbench消费显式请求身份，对已加载同文件／同行的重复跳转也重新选择并聚焦；捕获字节可按路径共享 |
| NATIVE-CLOSE-01 | 空闲关闭在原总预算内等待已应答请求的发送线程，再发送EOF并等待宿主；附加连接shutdown后释放仍被makefile引用持有的OS socket，回归增加实际发送线程退出断言；进行中／故障中断保留短预算与不重放规则 |
| AD-01 | Native响应字节计量包含实际CRLF／UTF-8字节，保留原字符限额和无效响应语义 |
| PERF-F01 | 源码身份过滤器纳入自身及合法同名源码目录；新记录声明 `pathFilterVersion=source-paths-v2` |
| PERF-F02 | 分别比较endToEnd、browserInputToReady、feedback；缺失／null／低样本明确不可比较，任一可比指标退化超过20%触发建议复核 |
| AD-02 | 贡献者报告解析Node实际spec／TAP汇总；只记录实际输出字段，重复歧义汇总不编造总数，命令exit保持原值 |

统一检查中的只读草稿浏览器初次失败来自fixture：字段写入后元素摘要已更新，但缓存的 `editor.element` 仍为旧投影，重挂载时正确触发产品的版本差异保护。该次修正只让fixture返回当前元素摘要，保留原草稿、只读、不重放及明确丢弃断言，未更改ElementInspector产品源码。它与上表诊断跳转、MCreator归属及Native连接关闭的后续产品修正分别记录；原13项缺陷ID保持不变。实际失败和复验次数见下表，未将fixture修正写成额外产品缺陷。

普通导出仅替换带Copperbench交付所有权回执且内容未变的旧交付文件；其他已有文件保留。受保护目录不可作为目标。目标正是本次构建声明的实际归档文件时可直接使用该文件；这不允许覆盖同目录其他旧文件。中英文[导出帮助](../user/build-and-export.md)同步说明这些规则。

| 受测补修实现与验证身份 | 本轮记录 |
| --- | --- |
| 当前受测实现SHA-256／dirty delta | `4a4dfd5170cb7257b55f4032a55c7249fcf073ec7901c9dd5712a186f914569d`／`4f0362fb6e7944fdd170dc934b8b68ab04d3f7c7452356bdfa9781e33e1dbc3d` |
| 当前受测实现回执 | `output/optimization-2026-10-15/review-fixes-1/source-implementation-3.json`，过滤范围为 `source-paths-v2`；验证结论以本节实际检查回执为准 |
| 原过滤范围的对照回执 | `output/optimization-2026-10-15/review-fixes-1/source-legacy-implementation-3.json`；只用于同过滤范围的变化核对，不与v2摘要直接等同比较 |
| 本轮构建应用JAR | `output/optimization-2026-10-15/review-fixes-1/artifacts/copperbench.jar`，7833711bytes；SHA-256 `275462694b79289b095a1e1e6ff8b23c1727b38a3751e617e1190dcbe0363348`，不是发行包／发布 |
| 实际统一检查 | Java 67项通过、1项既有按需跳过；浏览器138项、Windows SDK 69项、Linux Native 28项通过；UI 60项、UI-Core 35项、Node 29项、贡献者15项及真实Gradle归档smoke 6项通过 |
| 命令、exit、用例数和源码绑定 | `validation-summary.json`及`finding-closures.json`（均在 `output/optimization-2026-10-15/review-fixes-1/`） |
| 首轮失败与必要定向复验 | 7份失败命令回执原样保留；Java／浏览器／SDK复验及两次Gradle环境启动失败详见下表和原log，不作为7项新增缺陷 |
| 新增原生桌面／JCEF／Linux GUI／Minecraft玩家操作／同世界重开 | **not_run**；本轮为无可见界面的源码与自动化检查，不产生新游戏验收结论 |
| 包与发布 | 既有Windows C4 ZIP及Linux run3包不含本次13项补修；本节不产生新包验收、签名、发布或支持扩展结论 |

### 本轮实际检查与原失败

下表短路径均相对 `output/optimization-2026-10-15/review-fixes-1/`。用例按唯一集合计数，不把首轮与复验重复累加；未改变的组件保留原命令及其源码身份，不能将这些结果改写为最终实现的一次全量重跑。

| 检查 | 实际结果与次数 | 回执与范围 |
| --- | --- | --- |
| Java受影响回归 | 68个不同用例最终67通过、1跳过；首轮67项为65通过／1失败／1跳过，受影响复验9/9通过 | `checks/java-build-1.json`、`checks/java-build-2.json`；既有 `DataPackWorkspaceTaskGatewayTest.uiBuildCommandProducesARealDataPackZip` 因未启用原 `copperbench.stage8.workspaceGeneratorBuild` 按需标志而跳过 |
| 浏览器 | 138个不同用例通过；首轮104项为98通过／6失败；受影响及SourceWorkbench复验90/90，另48项未改首作／草稿用例保留初轮通过 | `checks/browser-1.json`、`checks/browser-2.json`及各自results／trace；两种尺寸的headless Chromium／MockCoreBridge fixtures，不是原生／JCEF／游戏验收 |
| Windows Python SDK | 最终完整69/69通过 | `checks/python-sdk-2.json`；独立管道、进程及loopback socket fixtures，不扩大为打包Java宿主或游戏认证 |
| Linux Native SDK | 最终28/28通过 | `checks/linux-native-sdk-2.json`；Linux管道／进程及socket生命周期范围 |
| UI／UI-Core／Node／贡献者单元 | 分别60/60、35/35、29/29、15/15通过 | `checks/ui-unit-1.json`、`checks/ui-contract-1.json`、`checks/tooling-node-1.json`、`checks/contributor-python-1.json`；未改组件沿用各回执原身份 |
| 真实Gradle归档smoke | Gradle 8.8和9.7.0各执行remapJar、jar及禁用主归档负例，共6/6通过；负例确认准确拒绝原因且不产生归档回执 | `checks/gradle88-*-*.json`、`checks/gradle97-*-*.json`及对应smoke目录；隔离Java归档fixtures，不是Minecraft构建重验 |
| 生产UI构建／Javadoc／应用JAR | `java-build-2` exit0，完成生产UI、Javadoc及JAR构建 | `checks/java-build-2.json/.log`；产物仅为上表冻结应用JAR，不是Windows／Linux发行候选 |

Java首次失败来自MCreator归属钩子使用旧确定性ID而Core优先持久化ID，随后共用解析器并保留旧后备。浏览器初次6项失败涉及只读fixture的过时投影和已加载源码重复跳转的请求消费；fixture更正与产品修正分别保留在交接中。原Java／浏览器失败、XML、trace和受影响复验回执均保留。

SDK首轮完整69项有1失败；Windows后续28项定向轮在同一用例记录2条failure，Linux 28项轮记录1条error，不改写为额外独立失败用例。阻塞写fixture的准备边界、重连fixture及实际附加OS socket释放／发送线程退出断言修正后，最终Windows完整69项与Linux 28项均通过。原 `checks/python-sdk-1.json`、`checks/python-sdk-native-1.json`、`checks/linux-native-sdk-1.json` 和原log保留。

两次最初直接Gradle启动在任务执行前因 `Unable to establish loopback connection` 失败，保留 `checks/gradle88-remapjar-1.json`、`checks/gradle97-remapjar-1.json`；随后采用项目既有隐藏外部worker方式，六项smoke通过。主归档禁用负例确认 `Copperbench: the primary declared mod archive task is missing, disabled, skipped or outside this build`，其Gradle exit1是预期拒绝，外层验收通过。汇总共有7份失败命令回执，属于上述命令尝试次数，不是7项新缺陷。

本轮Linux SDK复验结束后由guest正常poweroff，`linux-sdk/final-shutdown.json` 确认同一VM为Off、MemoryAssigned=0，没有强制turnoff；本轮没有启动Linux GUI。

本节、状态页及实施计划按统一检查的最终结果作后置文档更新；表中源码摘要绑定实际受测实现，不把这些后置文档视为已随同受测或已进入历史包。

交接分别位于 `backend-delivery/implementation-handoff.json`、`ui/implementation.json`、`sdk/handoff.json`、`tooling/handoff.json`；后续修正见 `backend-delivery/source-navigation-2/handoff.json`、`sdk/attempt-3/handoff.json`、`ui/browser-repair-1/handoff.json`，均相对 `output/optimization-2026-10-15/review-fixes-1/`。最终检查以主代理的实际命令回执为准，交接中的“实现就绪”不是测试通过。旧源码清单、C4／Linux run3身份和复审探针未重生成；过滤范围更正产生的新摘要不能替换旧摘要。历史性能243.4ms、低样本原生观察、同步取消超时和初始检查点告警仍保留各自边界。

## 历史候选4与后置文档身份

| 项目 | 候选 4 冻结记录 |
| --- | --- |
| 基线提交 | `b1a69d244ab7337608cc302826ed55d4a707f37f`，保留既有 dirty baseline |
| 受验收冻结源码 SHA-256 | `21066c06a8145f9f90928c91344313ec830aa5d9fb90934c55891257a5c93c20` |
| dirty delta SHA-256 | `0792c3b0243ff8feca3aeb19463c2b48b5579b68ace9a33087b92bb6780244b5` |
| Windows ZIP | `output/optimization-2026-10-15/candidate-4/Copperbench-optimization-20261015-r4-windows.zip` |
| ZIP SHA-256 | `768a470899dfd9b10eae00f04f7f23a4159963a7939d91ce33a8da16d52296b7` |
| 应用 `lib/copperbench.jar` SHA-256 | `5aa5c93287b1d79dfed8133ace1a09de68bf46973099c5e27d421ccf94d3f1f6` |
| `copperbench.exe` SHA-256 | `7393ceab69b4787aeaef285628b9e5f8330dc9c5b14cf0502eb00b687ecc116f` |
| 身份与解压入口 | `candidate-4/identity.json`，运行源为其 `unpacked/Copperbench014`，非旧安装或开发客户端 |
| 冻结记录与后续验收 | identity的`runtimeAcceptance=not_run`是构建冻结时字段，保持原样；后续`candidate-4/acceptance.json`另记`completed_with_limits`并连接最终证据索引，不把冻结字段误读为当前未验 |

上表路径相对 `output/optimization-2026-10-15/` 的条目以文件本身为准。`candidate-4/comparison.json` 比较 C3→C4 每个非目录 entry 的解压字节：外层只变 EXE、应用 JAR 和验收任务卡 Markdown；JAR 中变更包括诊断源码委派／捕获相关 class、前端资源和 manifest。SDK、生成器、随包 JDK 等外层 payload 未变。不能将该比较说成“只改一个 Java class”或“全部 UI 字节不变”。

本报告、实施计划与状态页的最后整理发生在上述C4源码／ZIP及下文Linux run3包冻结之后；它们是后置文档变化，没有重新打包或变更发布元数据。新的文档工作树摘要不替换受测源码摘要，也不表示Windows ZIP或Linux包已包含这三份最终文档。

## C3／C4阶段补修与统一检查（历史）

四组初始补修的 applied=true 回执分别为 `completion-audit/integration-measurement-2.json`（7路径）、`integration-onboarding-1.json`（22）、`integration-recovery-2.json`（16）、`integration-tooling-3.json`（16），唯一并集61路径。它们补齐同 revision 输入身份、精确回执环境展示、未知写入恢复、有界 SDK／固定旧客户端回归、验证计划与现有 CI 命令一致性、原生／依赖测量入口及包内关键文档。首次统一检查源码为 `1d871734…90104e`。后续浏览器、helper与运行发现的11文件诊断补修分别保留各自身份与重验范围。

检查前缀 `K=output/optimization-2026-10-15/checks/`。下列每个 stem 均有原命令／exit／source identity 的 JSON 和原 log；重复运行不累计成独立用例数。

| 检查 | 实际结果 | K/证据与范围 |
| --- | --- | --- |
| UI 单元／UI-Core | 54/54、35/35，均exit0 | `completion-ui-unit-1`、`completion-ui-contract-1`；模型、bridge、schema层 |
| Python SDK | 57/57，exit0 | `completion-python-sdk-1`；含固定旧源码客户端实际调用的受控 HTTP seam，不是旧公开包或游戏认证 |
| Node工具／贡献者／测量单元 | 22/22、11/11、5/5，均exit0 | `completion-tooling-node-1`、`completion-contributor-unit-1`、`completion-measurement-unit-1` |
| Java＋Javadoc | 13 suites、87 tests，0failure/error/skipped，exit0 | `completion-java-1`、summary与 `completion-java-artifacts/` 原 XML；不是全 Java 套件 |
| 实际贡献者入口 | README变更选出的4命令实际执行，sourceStable=true；selector14项、release-history3项、links/status exit0 | `completion-contributor-run-1`；`completion-audit/contributor-real-run/`。不是只打印计划，也不是把所有混合计划都真实执行了 |
| 浏览器初轮 | **118项：103通过、15失败，exit1**，原trace／截图保留 | `completion-browser-1`、summary、`completion-browser-artifacts/` |
| 浏览器修复后 | Chromium受影响四文件48/48；compact三文件39/39，exit0 | `completion-browser-recheck-1`、`completion-browser-compact-1`；不把初轮改成118/118 |
| helper定向复验 | import/source/native观察8/8；冷构建helper7/7，exit0 | `completion-helper-import-recheck-1`、`completion-cold-helper-recheck-1` |
| C4诊断bridge／browser | 28/28、34/34，exit0；两张源码预览有独立目视回执 | `c4-ui-unit-1`、`c4-browser-1`、`c4-browser-visual-review.json`；浏览器fixtures |
| C4 Java初轮→复验 | **初轮9项2失败**；fixture修复后受影响suite6/6通过，另3项保留先前通过。没有新一次9/9的回执 | `c4-java-1`、`c4-java-2`、`c4-java-summary.json` |
| C3／C4 Windows ZIP | 各1次，exit0；138.531s／114.475s | `completion-candidate-build-1`、`c4-candidate-build-1`；不是安装升级卸载验收 |

C4 Java 两个初次失败来自新增 NeoForge fixture 的 backendIdentity过滤条件不完整，随后读到缺失 `restoredFromHistory` 字段；修复期间产品源码未改，原失败和6项定向复验分开保留。C3 W1首个调用在业务开始前 `ModuleNotFoundError`，后续正确SDK环境重跑通过；冷构建的 o3d/o3d2 两次失败也保留，未改写为一次无失败的通过。

旧复审 G02/G05 缺的三组实际 Core断言已在87项中执行：游标5/5、任务授权 store5/5、application3/3。固定旧SDK来自优化前提交的原始Git blob；其不检查所选MCP版本的历史行为与当前SDK明确拒绝不兼容的行为均如实断言。mock／fixture不冒充真实授权签发或旧二进制认证。

## C3原生抽样与受限结论

C3身份见 `candidate-3/identity.json`：源码 `79e8eae8869faebeca56e50c4e3702c2236d21883281a4fbf0d0c3aa57d99134`，ZIP `1301b1e378a3a92c29bdd50d0ad58b3efce66486ac9705b97ace91e3a9c3c078`。以下记录继续归属 C3，不称为 C4性能测量。

- `output/minecraft-validation/o3/actions.jsonl` 与 native读回记录中文非法数字草稿、切英文／导航后保留、明确Apply后hardness3/resistance6、新宿主重开仍为3/6；另有英语引导键盘抽查及手写修复草稿切中文后保存前磁盘未改的记录。
- 真随包SDK W1、W2分别通过：W1创建／build各1，133逻辑调用／133尝试、162216响应字节、35日志0重复；W2旧revision及同revision旧源码hash均拒绝，12调用、85238字节。证据在 `o3/sdk-w1-2/`、`o3/sdk-w2/`，未把受控外部编辑说成人与Agent研究。
- `o3/native-observations/report.json` 使用20真实元素、81索引资源（含20固定PNG），导航／末元素查询／末PNG查询各1次正确观察。3318.9／646.1／1817.6ms为控制器派发到刷新截图回执，含传输与捕获开销，renderer/Core未知；**n=1，p95=null**，不与浏览器200/500ms建议预算直接比较。
- `o3d3/build-1/` 为新隔离Gradle目录（开始0文件／0bytes）的真实Fabric构建：task `2df92725-8738-49a9-8fdb-62e9f82c2de3` succeeded，395依赖下载日志、69 resolution、20 task stages，659poll、3573日志0重复。SDK观察674158.8ms；网络传输字节／HTTP时长未测。此为显式bundled JBR＋JAR公共SDK入口，supervisor exit0；独立host正常退出未单独观察，不覆盖原EXE stdio失败，也不是OS／Wrapper全冷或首次离线认证。
- 真task `2d3eb067-5356-4577-8845-78c380b025df` 运行中改源码再还原，端点hash虽相同，`inputUnchangedAtCompletion=false`，瞬时输入事件未被抹掉。证据在 `o3/input-identity-runtime/observation/`，不声称确定编译器读到了哪个瞬时字节。
- 取消首次task在点击前自然完成，未计取消。第二task `29a7d0b0-7064-4b5c-b610-db1b119e7d5f` 经实际点击进入真实cancelled终态，临时hold已恢复；**同步cancel仍记录15秒进程清理超时TASK_CANCEL_FAILED**。相关取消路径相对HEAD未改，无当时线程栈，具体阻塞未确证。终态取消、同步超时与稍后正常host退出分别成立。
- C3两次原生宿主正常exit0、目标PID消失，见 `o3/native/shutdown-1.json`、`o3/native-external/shutdown.json`及launcher／supervisor原回执。C3编译诊断源码查询的 `TASK_SOURCE_NOT_FOUND` 原失败保留，触发下一阶段C4修复。

历史9单元／180有效样本的浏览器数据仍见下方C1原记录；large asset-search反馈p95 **243.4ms** 超200ms建议，没有同条件before/after。C3低n原生样本不覆盖它，也不产生C4提速结论。

## C4 Fabric：诊断、输入、游戏与交付

运行根 `F=output/minecraft-validation/o4/`，实际沿用隔离 `o3/u/MCreatorWorkspaces/first_creation`，workspaceId `a023c7e3-774c-4ada-9e8c-6ffc5d2478ad`。准备、真实输入、观察和SDK读回分别保存；并非用脚本setblock代替玩家放置。

| 单元 | 实际结果 | F/原始依据 |
| --- | --- | --- |
| 真实诊断→修复 | task `3f4862fb-9586-4834-a171-73025d94d86c` 故意编译失败；打开其捕获预览，再进入当前manual源码第4行，Save一次恢复精确 `e4891475a800b0df537d8b17bea30d7da401265409ea97c71c44a4080bf4bdf2`。原task仍failed，捕获仍保留故障 `NOTE = ;` | `compile-failure/task/summary.json`、`after-repair-readback.json`、`saved-file.json`；actions3/4/6–10 |
| 同revision外部变化 | 原build `ec0b0196-8dc8-4487-91cc-a74a927c7cf9` 成功、input/read revision均24；外部源码改变后引导回读为“任务成功 · 输入随后发生变化”，不把成功改失败、不保留当前输入徽标。最终新build `bc97e688-88db-4d19-8a7c-2373c2f2c9c0` succeeded | `external-input-change/summary.json`、`guide-final-build/summary.json`、actions17 |
| 玩家放置与两进程同世界 | PID34984与48404，世界 `CB_OPT_C4_20261015_A`；实际鼠标放置后及第二新进程重开，F3均确认 `first_creation:trail_block` 在 `(9,-60,3)`。原client task `09b155b1-712f-488b-a6d6-4c9e9325246f`／`c5a3f6d5-cce0-49d2-bfc1-1cb4c8db8b10` 均succeeded、PID消失，两份日志正常保存／Stopping | `game-persistence-visual.json`、`client-first/`、`client-second/` 的lifecycle-summary/process-exit/client-latest.log；actions37/49 |
| 普通GUI导出 | `931a517f-ea3e-453e-af65-3db2d6a5d647` succeeded；输出88131bytes，JAR hash见下表；**无自动验收标签** | `ordinary-export/summary.json`、`artifact.json` |
| 真GameTest | `dcf5fed1-3f39-482d-b559-4994f6b6638d` packaged_jar，执行／通过1，0失败／跳过，processExit0。唯一case为 `acceptancetests.modloads`，是加载用例，**不是方块玩法自动测试** | `gametest/summary.json`、原JUnit XML；准备任务另在gametest-prepare，不算测试通过 |
| W3精确交付 | 原build `08ad091f-0a0d-4587-beb2-eb8a4a4cc024`，build/export各1、重连按sequence1继续、104调用／尝试、247020字节、31日志0重复；`passed_current_input`，复制JAR/XML与被选验收相同 | `sdk-w3/summary.json`、`w3-delivery.json`、polls与原start/finished；actions63显示回执自身fabric1.21.1、loader0.19.3 |
| Fabric宿主关闭 | launcher49936 exit0、JVM47900及launcher均消失，supervisor17596 exit0 | `native/shutdown.json`、`application-exit.json`、`supervisor/result.json` |

| C4被验收与精确导出身份 | SHA-256 |
| --- | --- |
| 模组JAR（不是应用JAR） | `4d5138e491be55d65ec962af2b13fb041b2d37142a5c7dabe01da4f25f146f4f` |
| GameTest XML | `3ec0da0802a8d88d48682913fe0f1e6e5fea792359ffee3bc1511d8a6f6cdd80` |
| 输入sourceSnapshot（revision24、178文件） | `a4d5a6fc0419233168b6f3937e134bb022716f0da4f647c8e02ea12b0c29734d` |

精确目录为隔离工作区 `.copperbench/verified-exports/09e320af-bc2a-4903-8834-78a157ffbd87`。普通导出字节恰好相同，也不继承自动验收。方块放置／持久化的依据是两进程玩家观察及保存退出日志，不能由 `modloads` XML推导。

## C4同路径旧数据保留（U）

`output/minecraft-validation/o4u/summary.json` 为passed，核对原C2工作区 `443a0b0e-c309-4f2d-9a45-6f26f00a3253`、原TrailBlock `819b091c-c566-4611-8242-0faccb06e12f`、refs及无关元素保持，语言zh、`ide.fontSize=12`、`gradle.buildOnSave=false` 保持。真实UI单次硬度3→4、revision4→5；task `37f6f375-5572-4fc6-afd6-c9ab21496b3d` build成功；新宿主重开仍4/6。两宿主均exit0、各自launcher/JVM消失，原回执在native-first/second及supervisor-first/second。

这覆盖同路径候选替换、修改、构建与重开，**不等于安装器升级／卸载验收**。初次汇总脚本把嵌套core偏好当成顶层dotted key而得到null，其failed记录保留为 `summary-parser-attempt1.json`；修正读取层级后的summary使用原始同一读回，不把该解析失败描述为产品丢失偏好。

## NeoForge路线 R

**通过，范围为Windows NeoForge 1.21.1构建／启动／渲染／加载及正常退出smoke。** `output/minecraft-validation/o4r2/summary.json` 绑定同一C4冻结身份：build `63532017-bdb5-4424-a133-50d442fb7bb7` succeeded，产物 `neoforge_acceptance-1.0.jar` SHA-256为 `704a050e07bf10e57b4c16fa140116d9ed868e7c001271979ec26a34a24ae78d`。

client task `c09e95ec-da16-4ae5-8f12-5f60654e5314` succeeded；PID34060／窗口276498664由 `client-process.json`、`game-directory.json` 的实际参数绑定到随包jdk21及o4r2工作区。游戏菜单实显NeoForge **21.1.232**，Mods页 `neoforge_acceptance` 为loaded；截图由 `o4/actions.jsonl` 的 `neoforge-rendered-client`／`neoforge-acceptance-mod-loaded` 记录。Quit Game后 `client-measurement/client-final.log` 有Stopping，`client-process-exit.json` 确认PID消失，SDK helper exit0。宿主launcher32792／JVM46516正常关闭，application／supervisor exit0，`native/shutdown.json` 进程空。本单元**没有创建NeoForge世界，不声称NeoForge放置、玩法或持久化**。

首次 `o4r/bootstrap/` 因maven.neoforged.net依赖连接超时创建失败、自动回滚，exit10原记录保留。第二次明确把本机modules缓存复制到独立缓存，见 `o4r2/bootstrap/cache-seed.json`，coldDownloadClaim=false；本成功为**warm缓存**，不能替代冷网络认证。

R原生打开时仍出现既有自动初始检查点限制：`o4r2/user/.copperbench/logs/mcreator.log:105` 为 `Failed to save initial checkpoint`，116行为Windows文件锁IOException。它并非只在C1出现的历史信号；本任务没有修复这项旧路径。记录检查点失败本身，不推断数据丢失，也不以随后构建／正常退出覆盖它。


## C4／Linux run3逐规格覆盖与证据索引（历史）

下表保留C4／Linux run3阶段的原覆盖，不代表13项后续补修已获得相同包或运行验收。初始逐项复审矩阵在本地 `completion-audit/spec-01.md`、`spec-02.md`、`spec-03-05.md`、`spec-04-06.md`；实现交接与原失败保留当时范围，续验以本页明确的新身份／证据连接，不把初审所有格子改成同一层的“全绿”。

| 规格／需求 | 本轮已有覆盖 | 范围／不适用 |
| --- | --- | --- |
| SPEC01 ONB01–09／A01–07 | 同revision输入失效、精确回执环境、首作恢复/冲突fixture，C3双语草稿/键盘抽查，C4最终构建、玩家两进程持久化及两类交付 | 独立新手研究未做；不宣称所有辅助技术或所有生成器组合全验 |
| SPEC02 REC01–10 | 未知写入/资产/字段定位、迟到保存与新草稿保护、原任务恢复；C4真实诊断→捕获/当前源码→保存，旧失败内容保留；真实取消终态 | 取消同步15秒清理超时既有限制保留；C4 NeoForge自动初始检查点失败再次观察，Windows文件锁原因见R记录，不推断数据丢失 |
| SPEC03 PERF01–07／P01–05 | C1浏览器可复放基线、完整性与比较工具；C3真实Small原生抽样及需下载构建分组；低成本CI工具化 | 数据仍按候选区分；native n1无p95；无同条件before/after与百分比提速，P2性能重构未触发 |
| SPEC04 AGT01–09／G01–06 | 公开入口/有界读取/缓存边界/原诊断/写不重放；固定旧SDK回归；游标与授权真实Core suites；C3 W1/W2、C4 W3与实际验收字节导出 | MCP源码工具未暴露的步骤明确限项；fixtures不冒充真实用户grant全生命周期或旧二进制认证；无自治研究 |
| SPEC05 REL01–05／Q1–6 | C4身份、字节比较、真实Fabric/native/游戏/交付/正常退出、U同路径数据保留及NeoForge启动／渲染／加载／退出smoke；C1/C2历史失败范围 | Linux无界面范围已完成：暖缓存Fabric构建／GameTest／两类导出及NeoForge构建smoke通过；GUI／玩家操作／世界重开按用户无界面约束not_run，完整安装升级卸载未做，未发布/签名/扩展支持 |
| SPEC06 DOC01–08／DEV01–06／D01–07 | 关键双语路线与版本、单status入口、媒体scope、源码工具标注、包内主路线白名单、CI同一selector与真实4命令执行 | About/topics为只读观察及具体建议，未写远端；后置文档未重包；不翻译全部历史资料 |

| 证据定位 | 身份与用途 |
| --- | --- |
| `output/optimization-2026-10-15/candidate[-2/-3/-4]/` | 各自identity、ZIP与比较；不相互覆盖 |
| `checks/completion-*`、`checks/c4-*` | 命令、exit、源码摘要、原log、trace/截图和XML；以JSON记录的实际cwd/参数为准 |
| `completion-audit/integration-*.json`、source manifests | 集成与source身份；applied=false的预览不算已集成文件 |
| `output/minecraft-validation/o3/`、`o3d3/` | C3双语/草稿、W1/W2、Small观察、冷构建、输入事件、取消与退出 |
| `output/minecraft-validation/o4/`、`o4u/` | C4 Fabric诊断、输入、两进程游戏、普通/精确交付、U数据保留与各宿主退出 |
| `output/minecraft-validation/o4r/`、`o4r2/` | R首次网络失败、显式暖缓存、实际build／渲染／加载smoke与正常退出，未声称NeoForge世界玩法 |

`checks/`、`candidate*/`、`completion-audit/` 上表短路径均在 `output/optimization-2026-10-15/` 下。本轮集中证据索引为 `output/minecraft-validation/optimization-final-index.json`；原始task、输入／观察、日志和候选身份仍按上表各目录保留。该索引与本页是后置记录，不改变冻结包字节。

原C1/C2的290文件manifest仍只覆盖原范围；截图、输入回执或SDK成功均不是单独的玩法证明。未附本地 `output/` 的检出不具备相同原始证据材料；证据索引不包含会话秘密、用户profile、依赖缓存或世界内部文件。

早期无WSL／VirtualBox的环境盘点保留为当时记录；现新增真实Hyper-V Ubuntu24.04.4的严格无界面跟进。首轮SDK关闭失败与后续任务失败均保留；run3已完成编辑／重开／正常关闭，显式暖缓存与任务本地堆配置下的Fabric构建／GameTest／两类导出及NeoForge构建smoke通过。Linux GUI、玩家操作和世界重开按用户约束not_run。完整GNOME、额外架构、完整安装升级卸载、OS／Wrapper全冷与网络字节归因、原生辅助技术全矩阵、新手／自治研究未做。关闭的安装器问题没有重新打开。性能建议超标、取消同步超时及C4再次出现的自动初始检查点失败保留各自限项；不新增发布、未来性能或平台承诺。

## Linux无界面跟进（受限通过，原失败保留）

本节为Windows验收后的独立Linux跟进，**只覆盖下表无界面范围**。按用户约束通过SSH执行，未作宿主桌面输入。公共证据根为 `output/minecraft-validation/linux-hyperv-20261015/`，集中回执为其 `acceptance.json`；未读取或纳入私有凭据／captures。

| 单元 | 实际结果与边界 |
| --- | --- |
| guest与运行条件 | 真实Hyper-V **Ubuntu24.04.4 LTS x86_64**，使用产品自带 **JBR25.0.2／JDK21.0.12**；初始系统PATH无Java／Gradle／Git。固定1.5GiB guest，gdm停止、multi-user目标，额外4GiB swap仅按boot显式启用，加原2GiB共6GiB。见provision的 `ssh-installed-os.json`、`guest-headless-preparation.json`、`run3-guest-resources.json`、`baseline-run3.json`；version／ldd预检不构成GUI证明 |
| 独立内存事件 | 首轮SDK关闭失败约12分钟后另发生动态内存压力下的kernel OOM／panic；不能把它当作先前SDK关闭失败的原因。宿主拒绝固定2GiB启动，固定1.5GiB恢复，随后正常关机重包。原provision的kernel-memory-panic／static-memory／pre-repackage记录均保留 |
| run2原失败及SDK补修 | run2 archive SHA-256为 `dc4b60c0e8f972cd14e87fff4210493fd7b2df421c75466f06bd5b8ef80ecbf9`，身份见 `output/optimization-2026-10-15/linux-followup/package-run-2/identity.json`。`headless-acceptance-1` 在字段拒绝／创建／保存后，首个空闲close被旧2秒预算强制结束（-9）；当轮build／GameTest／export／NeoForge未运行。六个打包Gradle启动器由正常初始化获得exec位。独立EOF诊断2.8184s正常exit0，main等待GitHistoryBackend.close、worker处于JGit测量，不据此认定永久死锁。补修仅Native SDK、其测试和README：空闲EOF最多等30s，故障／进行中请求仍短中断，不重放操作。Windows SDK **60/60**、Linux Native API **19/19**均exit0；后者使用与run3包相同字节的SDK，覆盖Python pipe／process生命周期。见 `output/optimization-2026-10-15/linux-followup/sdk-close-fix/`、`output/optimization-2026-10-15/linux-followup/checks/python-sdk-windows.json/.log`、`native-sdk-tests-run3.json`／`native-sdk-tests-run3-full.log` |
| run3冻结身份 | archive SHA-256 **`dc664d968936f8967eff0770d9a339fecbd971dbe5494e34854fd8725d853d5a`**，**1257421334bytes**；identity SHA-256 `387de41422ffe7241bf3eabc84455d5d25a7b4dcff8bc5e35ad66745a0360ebc`。完整源码摘要 `20a401b9931197d55aea145909b1f26bc1580c4fdf8c1219b887b543ec84e8b0`；相对run2的包内delta恰为Native SDK与README，应用JAR仍为C4的 `5aa5c932…3f1f6`，Linux运行时与平台输入另有身份，不称全部Windows产品字节未变。身份见 `output/optimization-2026-10-15/linux-followup/package-run-3/identity.json`，guest的 `provision/extract-run3.json`／`baseline-run3.json`核对同一身份；冻结字段 `runtimeAcceptance=not_run` 不重写，后续运行另记 |
| 编辑／重开／正常关闭 | Fabric公开SDK完成字段拒绝／创建／保存，Fabric与NeoForge均由新host重开确认字段保持；最终宿主正常exit0、目标进程消失，无已知残留子进程／未完task。首次修复后编辑host已处于预热JGit环境，不作冷启动或前后提速结论。见 `headless-acceptance-2` 及 `evidence-neoforge-3/` |
| Fabric生成／构建与暖缓存 | 显式安装已审计的Minecraft反编译依赖缓存 `v1.zip`（63336516bytes，SHA-256 `5b2f9dfc6c604c6d5d25fab178f236f780c5874cd1bf60424cf88a0d55669525`），保留原22bytes空ZIP，从本任务备份精确恢复fixture，未改产品或源码归属状态。continuation-3的generate `621879e4-a775-49c9-842c-e0fef549f89e` 记录5363 hits／0 misses并succeeded，rebuild `43f56309-af5d-4e02-b828-f5101280d155` succeeded。见 `warm-cache-install-1/result.json`、`restore-cfr-continuation-3/result.json`、`evidence-continuation-3/`；这是warm条件，不证明冷反编译可完成 |
| Fabric最终GameTest／两类导出 | continuation-4只续验GameTest与导出，未重跑独立generate／build；sourceSnapshot保持下表身份。实际server JVM确认 `-Xmx1536m`。GameTest `11ca1829-ba0e-40db-b88d-d5b148add5a1` 为packaged_jar，**2通过、0失败／跳过、processExit0**；两case为 `acceptancetests.blockitemandworldplacement`、`acceptancetests.savedfieldsandcollision`。精确导出 `9e1d7ee3-b822-4e8d-81cd-1a2d07b3dbb5` 为 `passed_current_input`，JAR／XML与被选验收相同；普通导出 `9f264865-6c27-42a0-b526-32921184317e` succeeded，虽字节相同仍无自动验收标签。host正常exit0。原summary、XML／verification／工件和实际argv见 `evidence-continuation-4/`，其128文件collection检查issues／missing为空；不据此推导玩家操作或世界重开 |
| NeoForge最终构建 | 继续显式暖缓存，通过已安装ModDevGradle的 `getJavaExecutable()` 为NeoFormRuntime选择任务本地thin launcher；实际进程为原随包JDK21、唯一 `-Xmx1536m`。tools Java、外部工具堆参数和原缓存key／marker未改，未用JAVA_TOOL_OPTIONS。正式Neo3 build `fa8e5a2b-ce77-419c-ab16-e322892f4b07` succeeded，实际完成5364源码编译；两host均正常exit0、无残留／未完task。见 `warm-neoforge-cache-install-1/result.json`、`neoforge-runtime-1536m-neo3/result.json`、`neo3-jvm-launch-observation.json`、`evidence-neoforge-3/`（120文件、issues／missing为空）。范围是公开SDK／Core／NeoForge1.21.1 build smoke，未验Linux NeoForge GameTest、客户端或世界玩法 |
| Fabric原失败 | build `3aae8afe-ddfe-4218-a41e-861eb8c7b651` failed；下一轮build `a1f11b62-5778-419f-9979-9ccc36339d58` succeeded，但GameTest `621b973e-fdd7-46a7-aa29-1ce612e33cb1` 因 `GENERATION_SOURCE_CONFLICT` failed、执行0项。续验generate `f709c9d4-9aa1-4230-b64d-cc02af78ba5d` 在默认CFR `-Xmx4096m` 下达15分钟超时；任务本地CFR1024MiB的generate `1d564031-357a-4e5c-8f30-b5b9274380e0` 仍在0 hits／5363 misses后因Java heap space failed（1m52s）。暖缓存生成／重建后，GameTest `46e38c5d-5c3e-4231-b528-d6586d549ecf` 又在RuntimeModRemapper因Java heap space failed、执行0项。上述run3 host均正常exit0；原 `headless-acceptance-2/`、`headless-acceptance-3/`、`headless-continuation-1/`、`evidence-continuation-2/`、`evidence-continuation-3/` 不被最终通过覆盖 |
| NeoForge原失败／预检 | 首build `cd75f988-0437-4772-b2c7-501195861335` 达15分钟而failed；暖缓存第二build `d7e44c28-aafa-456b-91f9-27ffe4c9e7d9` 完成decompile cache hit、patch／transform后，在进程内javac21.0.12因Java heap space failed。默认guest最大堆诊断为367001600bytes，两轮host均正常exit0。见 `evidence-neoforge-1/`、`evidence-neoforge-2/`、`neoform-memory-profile.json`。此前JAVA_TOOL_OPTIONS尝试被harness预检拒绝，未建run root／未启动产品task，不计产品运行失败；见 `neoforge-3-environment-preflight-rejection.json`，与随后正式Neo3区分 |
| 最终关闭／磁盘 | `provision/final-shutdown.json` 为completed：最终两路线normalHostShutdownsConfirmed，guest验收Java／runner为空；SSH正常poweroff exit0，确认为同一任务的隧道已停止，VM为 **Off**、分配内存0。动态VHD配置48GiB，实际 **26075987968bytes（约24.29GiB）**；宿主D可用 **70109433856bytes（约65.29GiB）**，高于40GiB保留额。关闭前再次核对应用JAR与Native SDK摘要 |
| 明确未验／未宣称 | Linux GUI／GNOME、玩家操作与同世界重开为 **not_run**（用户无界面约束）；未完成默认资源／冷缓存、完整安装升级卸载或正式Stage15认证，不作发布或支持扩展。该范围不改写Windows C4与C1/C2历史结果 |

| Linux输入／工件（模组JAR均非应用JAR） | SHA-256 |
| --- | --- |
| Fabric sourceSnapshot（43文件） | `d1332b920d2ba307a8ae1c9eb089656d9b0980d230643ef8f6f14dc09735f3e8` |
| Fabric受验与两类导出模组JAR（42129bytes） | `d07a684c4b1f908187cb3c90e2f5763fe8636420824e6fc8a1f627544e9b6f7e` |
| Fabric GameTest XML（381bytes） | `0e860aaa7449f6936cc3060fed26a9b6179260399b088de92ac2b71e2041d2d9` |
| Fabric精确导出verification.json（696bytes） | `69500cd02e14bedc5b1930f969dd2db6e3b72165d9a987916f46794b9bb0f035` |
| NeoForge sourceSnapshot（21文件） | `c70918ddcc33b05a9062b71b4d6021408c840234b867544131b064e2c6d03d07` |
| NeoForge模组JAR（8079bytes） | `fa6042bb3cabb271cedf4f6204a70126300649fbe3836eded8aae4f45920c5e0` |

任务派生autoinstall ISO删除被自动审批拒绝，原理由仅为 **`Automatic approval review rejected exec_command: blocked by policy`**，因此保留 **6655619072bytes（约6.2GiB）**；原用户ISO未动，没有再次发起权限流程。见 `provision/temporary-media-cleanup-blocked.json` 及最终关机回执。

## C1/C2历史阶段原始记录

以下保留上一阶段的实际命令、失败和验收边界；其中“未验”“最终收尾”只指C1/C2当时范围，不覆盖上方C3/C4新增事实。C1宿主退出失败及强清仍是失败，C2正常退出不能改写该事实。checkpoint当时“未单独复验”的原描述也不覆盖上文R已记录的C4再次出现Windows文件锁告警。


## 候选 2 与受影响复验（上一阶段）

| 身份 | 最终记录 |
| --- | --- |
| source SHA-256 | `4386973d64efa049e8e09124e43787ea56bd135b471a903bb7606ebb01b8ad4a` |
| dirty delta SHA-256 | `147a35e8c8e29f13d7b2d4b9936e4c02ea522f28cebcca330807604c59d29e1d` |
| ZIP | `output/optimization-2026-10-15/candidate-2/Copperbench-optimization-20261015-r2-windows.zip` |
| ZIP SHA-256 | `079c37429e08534d44a43f7ab7aac43735fc9c15ccc475edf64d2a7a8020465d` |
| 应用 JAR SHA-256 | `03c6f00c98135a8bab326e3b433bffd9ffd32bf82d751aedf437382fe90f7336` |
| EXE SHA-256 | `ad87ade6c45bdac3c8c9df9eddd95df1c9add177672d2524b563e570507d9fa6` |
| 版本／发布 | 仍配置为 0.1.4，未发布；与官方 0.1.5 无身份替换 |

身份见 `output/optimization-2026-10-15/candidate-2/identity.json`。`source-candidate-2-delta.json` 证明较候选 1 仅改 `src/main/java/net/mcreator/util/SingleAppHandler.java` 并新增对应 `SingleAppHandlerTest.java`。修复使用库的实际持有状态，close 同步／幂等，只释放本实例持有的锁，保留原 IOException 日志、启动策略和 IPC 行为。

`candidate-2/comparison.json` 对两个 ZIP／应用 JAR 的每个非目录 entry 的**解压字节**作 SHA-256 比较：ZIP payload 只变 EXE 和应用 JAR；JAR 只变 `SingleAppHandler.class` 与 `META-INF/MANIFEST.MF`（Build-Date `42405` → `42406`）。`launcher-comparison.json` 证明各 EXE 内嵌内容与各自 `lib/copperbench.jar` 一致；EXE 前缀摘要也变化，不能称启动器前缀完全不变。SDK、UI、生成器、文档和运行时 payload 与候选 1 相同。

| 实际复验 | 结果／范围 | 证据 |
| --- | --- | --- |
| 锁所有权定向回归／路径合同 | 1 次，SingleAppHandlerTest 9/9、LinuxDesktopPathIntegrationTest 2/2，0 failure/error/skipped；exit 0，同次 Windows ZIP 构建成功，121.73 s | `checks/lock-repair-and-candidate-1.json/.log`、`lock-repair-summary.json`、`lock-repair-artifacts/` XML；后两项是源码合同，不是 Linux 桌面 |
| Diff / CRLF | 1 次，exit 0 | `checks/lock-diff-preserved-eol-1.json/.log` |
| 原隔离工作区重开 | 新包原生 UI／随包 SDK 读取 2 个原元素、唯一 trail_block，hardness3／resistance6 保留 | `candidate-2-deep/reopened-workspace.json`（下方均相对 validation 根），截图 95 |
| 深目录获取失败后正常退出 | 实际仍触发 Unix domain path too long，但正常 exit 0，无未持有锁 unlock 异常，host／launcher／supervisor 0 存活、discovery 0 | `candidate-2-deep/shutdown-summary.json` 与 raw stdout；动作 96 |
| 短目录真实持锁与退出 | 主 JVM32796／launcher36116／supervisor41156 正常 exit 0、0 存活，没有 lock acquisition/release 错误，candidate windows 为 0 | `candidate-2-owned/shutdown-summary.json`、`windows-after-exit.json`；截图 97/98 初始化仅隔离 `o2-user`，动作 100 正常关闭 |
| 第二实例复用 | 1 次，记录 `Another instance...Reusing args []`，secondary／supervisor exit 0，当时主进程仍存在；随后主实例正常关闭 | `candidate-2-secondary/summary.json` 与 raw log，截图 99；仅实际无参数复用场景，不扩大为全部 IPC 参数测试 |

这些 native 路径均在 `output/minecraft-validation/optimization-20261015/` 下。深路径 socket 获取限制**没有被根除**；本次修复和实测结论是该原有 fallback 不再因错误释放锁阻断关闭。候选 2 只重验直接受影响的启动／关闭与原工程回读；候选 1 的完整玩法／SDK／GameTest 记录仍绑定旧应用／ZIP，不写成候选 2 已完整重跑。

## 候选 1 身份与边界

| 项目 | 实际身份 |
| --- | --- |
| 本地基线提交 | `b1a69d244ab7337608cc302826ed55d4a707f37f`，包含保留的既有 dirty baseline 和本批未提交实现 |
| 候选 1 受测冻结源码 SHA-256 | `93a5370cbc897b0e6f15177ecae22d5bd7817662bfc0e8897392ffb15d0ffca9` |
| 冻结 dirty delta SHA-256 | `088f6a8db9de2cb0c333832a6a472bb2533098d8550d68beeee8d8f28de12057` |
| Windows ZIP | `output/optimization-2026-10-15/candidate/Copperbench-optimization-20261015-windows.zip` |
| ZIP SHA-256 | `46947525d9a7f84ee38fb62bdfd9060819fe79bb68c9f2199473f0f6f1be06c1` |
| 应用 `lib/copperbench.jar` SHA-256 | `293a2dd8d99a410fcf6cc0cfc757ee29b48bd44b23b8aaddaa95dc1eb9e93f7f` |
| `copperbench.exe` SHA-256 | `14a6229142ecd453c4f58e33f5f1e375e793a3a02527d7b8b0acab7fbd9104fd` |
| 包配置版本／发布状态 | `0.1.4`，本地未发布优化候选；不修改既有版本、授权／签名／发布元数据 |
| 实际解压目录 | `output/optimization-2026-10-15/candidate/unpacked/Copperbench014` |
| 候选身份回执 | `output/optimization-2026-10-15/candidate/identity.json`；原生验收另留 `output/minecraft-validation/optimization-20261015/candidate-identity.json` 与 `launch-request.json` |

官方已发布的 Windows／Linux 0.1.5 与本候选是不同字节和记录，见当前下载索引；其历史 CI／开发客户端／VM 证据不移用于本候选。本页应用 JAR 身份也不等同于测试工程的模组 JAR 或 GameTest 输入快照身份。

候选构建 1 次，退出码 0，`exportWindowsZip` 成功；identity 回执列出 28 个必需文件检查，包括中英关键指南、`docs/README.md`、实际 SDK 模块与公开示例。运行时 Java 窗口路径来自该解压包的 `jdk/bin/javaw.exe`，不是开发目录或旧安装；窗口记录显示产品 0.1.4、PID `15808`。这是解压候选验收，不是安装／升级／卸载验收。

本报告在冻结后新增，不进入上述候选 ZIP 或运行字节。早期检查绑定其各自回执中的源码摘要；不能把不同摘要的结果改写为所有检查都在 `93a537...` 上重新全量运行。相关修复后仅复跑受影响检查，明细如下。

## 已执行检查与实际次数

检查目录统一为 `output/optimization-2026-10-15/checks/`。表中名称对应同名 `.json` 命令／身份回执与 `.log` 原始日志；测试数是每次实际执行数，重复运行不累计成独立用例数。

| 检查 | 实际运行与结果 | 层级／范围 |
| --- | --- | --- |
| Python SDK | 2 次；首次 52 项，51 通过、1 error；修复后 52/52 通过，exit 0 | Python 传输／客户端／导出验证器 fixtures；`python-sdk-1`、`python-sdk-2` |
| TypeScript SDK | 1 次，23/23 通过，exit 0 | 生产 SDK 代码配 fetch seam；`typescript-sdk-1`，不等于真实桌面工作区 |
| UI-Core 合同 | 1 次，35/35 通过，exit 0 | Schema／事件／权限投影；`ui-contract-1` |
| UI 单元 | 2 次，每次 50/50 通过，exit 0 | 草稿、首作引用与图模型；`ui-unit-1`、`ui-unit-2` |
| 性能／源码身份工具 Node 单元 | 2 次；首次 19 项，16 通过、3 失败；修复后 19/19，exit 0 | fixtures／统计／完整性／脱敏；`tooling-node-1`、`tooling-node-2` |
| CI 路由 Python 单元 | 2 次；首次 14 项，2 errors；修复后 14/14，exit 0 | Windows 文件名边界和保守路由；`ci-selection-1`、`ci-selection-2` |
| 贡献者入口 Python 单元 | 1 次，8/8 通过，exit 0 | 计划／环境缺项／执行汇总 fixtures；`contributor-unit-1` |
| 贡献者／性能计划 | 各 1 次，exit 0 | `contributor-plan-1`、`performance-plan-1` 只输出计划，不作为其计划内命令均已执行的证明 |
| Java 定向测试与 Javadoc | 直接 Wrapper 1 次阻塞；外部 launcher 4 次尝试，最后成功；18 suites、114 tests，0 failure/error/skipped，Javadoc 完成 | 真实 Java Core／持久化／Native transport／MCP 合同；`java-core-1`、`java-external-1..4`、`java-summary.json` 和 `java-artifacts/` XML。不是全部 Java 套件 |
| 独立 TypeScript 编译检查 | 1 次，exit 2；错误修复后由成功的 Gradle UI 生产构建覆盖 | `typescript-build-check-1`；未单独重跑同一 tsc 命令，不伪造第二次回执 |
| 浏览器定向回归 | 首轮 86 项，85 通过、1 失败；受影响 element-drafts 文件复验 9/9 通过；compact-1366 首作／恢复 12/12 通过 | Playwright / Core bridge fixtures；`browser-regression-1`、`browser-draft-recheck-1`、`browser-compact-1`。不写成首轮 86/86，也不写成全量浏览器套件 |
| 真 MCP HTTP | 1 次，exit 0；协商 `2025-11-25`，目录 73 工具，删除旧 session 后读取 `HTTP_404`，新 session 读取成功 | `mcp-transport-1`：实际 Java HTTP／catalog，Core 为 InMemory conformance fixture；不是原生工作区／玩法 |
| Markdown 本地链接（实现阶段） | 2 次；首次 3 个历史证据路径缺失，修正说明后 exit 0、All local Markdown links resolve | `markdown-1`、`markdown-2`；不等于外链或示例命令均已执行 |
| 本地产品状态 | 1 次，exit 0；8 generators、27 gates，metadata 校验通过 | `product-status-1`；仍是本地 0.1.4 基线的一致性，不是发布或新增平台认证 |
| Diff 检查 | 默认 whitespace 规则 exit 2；保留 CRLF 的 `cr-at-eol` 规则检查 exit 0 | `diff-review-1`、`diff-preserved-eol-1`；不通过重写无关用户行尾消除记录 |
| Windows 候选构建 | 1 次，exit 0，128.65 s | `candidate-build-1`；冻结源码 `93a537...`，没有发布操作 |
| 实际包 GameTest | 1 次，2/2 acceptance 通过，0 failed/skipped，processExitCode 0 | `packaged-gametest-1` 与 `gametest-run/summary.json`，真实 packaged_jar，非加载 fixture |
| 实际包 SDK W3 | 1 次，exit 0；build/export 各一次，原 task 恢复、精确 current-input 导出通过 | `packaged-sdk-w3-1` 与 `sdk-w3/summary.json` |
| 两个 Minecraft 客户端生命周期 | 各 1 次，收集命令 exit 0、实际任务 succeeded，两个 PID 均已消失，正常保存／Stopping 日志保留 | `client-first-lifecycle-1`、`client-second-lifecycle-1`，与实际玩家观察配合；不是仅依据命令 exit 0 判游戏通过 |

Java 成功汇总绑定 `acc1b11920e572063799160bfb5fdba9af1a1670c6fece192b4ce4d2d09615e4`；Python、工具与文档复验分别保留自己的早期摘要。element-drafts 9 项复验、性能采集、候选打包及实际 W1/W2 回执绑定冻结 `93a537...`。完整命令、时间和身份以各 `.json` 为准。

## 初次失败保留与修复范围

- Python 新 W3 拒绝用例的 fixture 漏 `mode`，在进入“普通构建不是验收”断言前触发 AttributeError。仅补 `mode=desktop` 并收紧对应错误消息断言，保留不得调用 export 的断言；没有放宽生产合同。
- 性能 fixture 首轮 small/medium/large 图模型断言 3 失败；保留首轮 19 项日志，第二轮 19 项全通过。不能以工具 fixture 通过宣称原生大工作区性能。
- CI 路由首轮在 Windows 创建含换行／制表的 POSIX 文件名 fixture 时 2 errors；适配平台 fixture 后 14 项复验通过，未知路径保守全选规则保留。
- Java 直接 Wrapper 的 `Unable to establish loopback connection` 是宿主限制；外部第 1 次 PowerShell `-I` 参数歧义，第 2 次 `--tests` 跟在非 test 任务后导致参数错误，第 3 次停在 UI 本地化检查（包括 SHA-256、生成器／注册名和 `/give` 命令片段）。修正对应调用／界面声明后第 4 次完成 114 tests + Javadoc。前三次不是已执行 Java 用例的失败计数。
- 独立 tsc 首轮报告 `query.payload` 未收窄及未使用的 React import；修复后的 Gradle UI 构建成功，不隐去首轮编译错误。
- 浏览器首轮的迟到保存草稿用例找不到 `inspector-external-change`。原失败 PNG、error-context 和 trace 保留在 `checks/browser-regression-1-artifacts/`；后续针对 element-drafts 文件 9 项通过。它是受影响复验，不覆盖重跑其余 77 项的承诺。
- Markdown 首轮仅因历史安装云端记录的 3 个本地 JSON 不随本检出存在；只改为明确的历史路径说明，保留原结论、hash 和 Page 链接。没有重新打开安装器验收或补造文件。

MCP fixture 记录 `fixtureServerExitCode=1`；其 transport/session 断言通过不能用来证明那个服务进程正常退出，更不能替代候选桌面或 Minecraft 的正常退出验收。

## 浏览器性能基线

证据：`output/performance/optimization-2026-10-15/report.json`，命令回执 `checks/performance-baseline-1.json`。9 个测量单元，每单元 2 次预热 + 20 个有效采样，共 180 有效采样、18 预热；记录 0 failure/timeout/interrupted。采集前后源码 SHA 均为冻结 `93a537...`。`status=passed` 指声明的采集和正确性断言完成，不指全部建议预算满足。

环境是 Windows x64 的 headless Chromium 151.0.7922.34、Playwright 1.62.1、Node 24.19.0，1366×768 / scale 1、中文／浅色。每单元新浏览器 context，依赖／Vite 缓存按现状复用；没有清全局缓存或进行冷启动／依赖下载认证。fixture 为 synthetic browser projections：small 20 元素／100 引用边／20 资产，medium 200／1000／200，large 2000／10000／2000；它们不是真实 Java 索引。

| 单元 | 有效 n | 首次反馈 p95 ms | 输入到就绪 p95 ms | 脚本总阶段 p95 ms |
| --- | --- | --- | --- | --- |
| small navigation | 20 | 93.5 | 96.3 | 127.6 |
| small element-search | 20 | 5.4 | 10.1 | 19.2 |
| medium navigation | 20 | 96.9 | 102.0 | 122.8 |
| medium element-search | 20 | 5.9 | 10.9 | 19.5 |
| medium editor-open | 20 | 52.3 | 53.6 | 247.9 |
| large element-search | 20 | 6.2 | 10.8 | 19.8 |
| large asset-search | 20 | **243.4** | 250.6 | 273.9 |
| large graph-open | 20 | 82.7 | 91.8 | 128.1 |
| large graph-search | 20 | 35.2 | 36.5 | 73.9 |

p95 使用有效样本 nearest-rank，失败分母另记。large asset-search 的**首次反馈 243.4 ms 超过 200 ms 建议目标**；它的输入到就绪 250.6 ms 是另一个边界，不能混作同一数字。总阶段均低于该报告的 500 ms 建议值，建议不是新硬发布门禁。

没有同条件“优化前 vs 优化后”对照，不宣称百分比提速、token 成本下降或大资产搜索已达建议目标。P03 保存／输入和 P04 增量日志由定向正确性回归覆盖，本基线未测这两类性能；Java Core、原生 JCEF、包启动、下载／构建等待及游戏性能保持未测范围。

## Windows 原生与真实 SDK

根路径 `output/minecraft-validation/optimization-20261015/`。准备、玩家／桌面输入与观察分别留在 `launch-request.json` / `w2-preparation.json`、`actions.jsonl`、`observations.jsonl` 和 `screenshots/`。目标来自候选包，验收使用隔离用户目录及 `user/MCreatorWorkspaces/first_creation/first_creation.mcreator`，Core workspaceId 为 `443a0b0e-c309-4f2d-9a45-6f26f00a3253`。Gradle 缓存复用已有目录，不是冷缓存测试。

已留存原生启动三路线、Fabric1.21.1 首作表单、明确确认后打开真实工作区、Trail Block 创建／编辑及原生无效数值草稿导航的观察。代表性截图：`05-native-startup-routes-0.png`、`07-native-first-form-ready-0.png`、`16-real-workspace-first-guide-0.png`、`23-partial-number-retained-0.png`、`27-return-to-draft-0.png`、`36-guide-core-reread-0.png`（均在 screenshots）。两张草稿场景显示字段无效提示与未保存状态；第 36 张引导的自身构建当时仍“尚未运行”，未借用 SDK 其他任务。后来 `native/guide-build-readback.json` 记录指南自身 build `12b2157f-68f9-4422-a414-7ad154ea73a3` 成功，绑定 revision 4 与 Trail Block ID `819b091c-c566-4611-8242-0faccb06e12f`，与 W1 的元素／build 身份不同。

截图和成功输入回执不单独证明玩法或存档；游戏结论另由下方实际行为、同世界重开、两个客户端终态与保存日志共同支持。原生双语键盘全矩阵不在本次全面认证范围；候选 1 全应用正常退出明确失败，见收尾记录。

| 实际公开 SDK 路线 | 业务结果与计数 | 证据 |
| --- | --- | --- |
| W1 desktop Native | 1 次固定路线通过；故意字符串 hardness 被拒绝且内容／revision／元素无意外写入；1 次有效创建、1 次实际 Fabric1.21.1 build 成功。115 逻辑 IPC 调用／115 attempts，112338 响应字节，收到 35 条日志、0 重复 | `sdk-w1/summary.json`、`w1-rejected.json`、`w1-created.json`、`w1-build-start.json`、`w1-build-finished.json`、`task-polls.jsonl`；任务 ID `1c6e9da6-cd64-459b-a577-1dbff2b1bdf6`，元素 ID `331826f7-df4d-441a-a6cd-c928cb8d669d` |
| W2 desktop Native | 1 次固定路线通过；旧 revision 与同 revision 的旧源码 hash 被拒绝，较新字段／手写磁盘字节及 generated/manual 归属保留。12 逻辑 IPC 调用／12 attempts，70846 响应字节 | `sdk-w2/summary.json`、`w2-revision-rejected.json`、`w2-source-rejected.json`、`w2-reread.json`、`w2-source-reread.json`；手写 fixture 的准备单独记入 `w2-preparation.json`，不是玩家输入 |
| prepare_game_tests | 1 次成功准备任务，**verification=null，不是 GameTest 通过** | `gametest-prepare/summary.json`、`accepted.json`、`finished.json`、`polls.jsonl`；taskId `88a9285f-b66f-43be-ae73-9e874fcc0a23` |
| 真 GameTest | 1 次 packaged_jar 通过：发现／执行／通过 2，失败／跳过 0，frameworkTests 0、acceptanceExecuted 2、processExitCode 0、sourceCurrentAtCompletion=true | `gametest-run/summary.json`、`accepted.json`、`polls.jsonl`；taskId `6213720e-2cb9-476d-9f3e-8483cd2eedc5`，两用例为 `blockitemandworldplacement` / `savedfieldsandcollision` |
| W3 原 build 恢复 → 精确导出 | 1 次真实路线通过；build/export 各启动 1 次、handshake 2 次，按原 buildId 从日志 sequence 1 重连继续；96 IPC calls／96 attempts、170596 字节、31 日志、0 重复，delivery=`passed_current_input` | `sdk-w3/summary.json`、`w3-build-start.json`、`w3-build-finished.json`、`w3-selected-acceptance.json`、`w3-export-finished.json`、`w3-delivery.json`、`task-polls.jsonl`；build `cb2329e4-1d95-4e5a-8e1a-8dacb4e0ac35`，export `4c163962-9346-433a-a8d5-86ed8102afcc` |
| 玩家取得／放置 → 新客户端同世界重开 | 对本 Trail Block／Fabric1.21.1 场景通过；第一 PID32040、第二 PID50764，同 run 目录／世界 `CB_OPT_20261015_A`，两次都在坐标 `(3,-60,-1)` 观察到 `first_creation:trail_block`，物品栏保留；两次退出任务 succeeded、进程消失并留正常保存日志 | `client-first/exit-and-world.json`、`client-second/exit-and-world.json`、两目录的 `client-latest.log`；截图 `60-observe-placed-block-identity-0.png`、`73-reopened-world-block-present-0.png`、`74-verify-reopened-block-identity-0.png` |

### GameTest 与精确导出字节

| 身份 | 记录值 |
| --- | --- |
| 被验收模组 JAR SHA-256 | `812b400e19d26912998563ce76cd3d12f4f4064d127319036f9798e9f7c73665` |
| GameTest XML SHA-256 | `29fb29e007656e9929aaf9f389feffbcc67df832f44c8667bd2e9638ed51d95c` |
| 被验收工作区 sourceSnapshot SHA-256 | `f4a88d92f0a741636c8525c518c5f368628b2425b627193cacd49877906b3427`（50 文件、1320463 bytes、workspace revision 4） |
| 精确目录（相对隔离工作区） | `.copperbench/verified-exports/4c163962-9346-433a-a8d5-86ed8102afcc` |

GameTest 来源为工作区 `.copperbench/task-runs/run_gametest/6213720e-2cb9-476d-9f3e-8483cd2eedc5/` 下的 source-manifest、实际 host mod JAR／XML／verification.json。W3 回执与该 acceptanceId、source 和 JAR／XML hashes 一致，公开 runner 在返回通过前核对复制字节及写入 receipt。报告仅覆盖这两条验收用例；client 行为不由 XML 自动推出。

### 两进程玩家观察与退出

第一客户端通过聊天 `/give @s first_creation:trail_block 16` 取得方块，再用鼠标右键放置，动作不是准备脚本的 setblock。F3 截图第 60 张确认注册 ID 和坐标。正常退出后，task `09b312a6-c56f-477c-83a5-33adf86e7a87` succeeded、PID32040 不存在；日志包含 `All dimensions are saved` 和 `Stopping!`。

第二客户端 PID50764 / window2102636 与第一次不同；从同工作区 `run/` 打开同一 `run/saves/CB_OPT_20261015_A`，第 73／74 张截图重新看到原位置方块和保留的物品栏。退出后 task `308c2835-aeaf-47a8-a47e-299208b86dac` succeeded、进程不存在，第二份日志同样正常保存所有维度并停止。两份 exit-and-world 回执保留 world 文件清单，不要求两次保存的全部文件 hash 不变。

两次任务正常退出后，指南的两项观察 checkbox 可操作；验收操作者基于上述实际观察勾选，第 83 张 `83-confirm-observed-persistence-0.png` 留存。它是当前会话／输入／元素／构建的观察确认，不是陌生用户研究，也不是持久化的自动验收 passed 事实。

### GUI 普通导出与精确目录入口

候选 1 的普通「重新构建并导出」任务 `a8dc5f82-5f38-4ee7-9652-78b090e6e99a` succeeded，inputRevision 4，路径为隔离工作区 `build/libs/copperbench-delivery-a74477bc-1167-4bdb-9759-c996ea9c1292/first_creation.jar`。`native/guide-final-readback.json` 保留指南自身 build、两个 client 和 export 的实际引用；`native/delivery-files.json` 记录普通 JAR 45447 bytes 与 SHA-256 `812b400e19d26912998563ce76cd3d12f4f4064d127319036f9798e9f7c73665`。第 87 张显示普通任务成功与本次路径。

这个普通 JAR 恰好与被验收精确 JAR hash 相同，但普通任务的 `verifiedExport=null`，不因此继承自动验收 passed 标识。第 89 张显示实际精确回执，第 91 张显示从 UUID-bound 入口打开的原精确目录，包含 JAR、XML 和 verification.json；目录打开不重新验收或导出。

### 候选 1 退出失败与定向清理

关闭时窗口消失、工作区与 SDK 发现元数据已关闭，但 javaw PID15808 和 launcher PID11168 仍存在。`native/application.stdout.log` 起始记录 `Unix domain path too long`，在 `SingleAppHandler.tryAcquireLock` 获取单实例锁失败后继续启动；结尾在 close 对未持有锁调用 unlock，抛 `UnsupportedOperationException: Lock wasn't acquired by this app instance`，阻断正常退出。

`native/shutdown-failure-and-cleanup.json` 明确 `status=failed`、`normalExit=false`、`workspaceClosed=true`、`clientTasksSucceeded=true`。随后只对已确认的候选宿主作定向强制清理；`native/application-exit.json` 的 launcher exitCode 为 **-1**，不是正常退出成功。两个 Minecraft 客户端成功退出、普通／精确导出通过都不能覆盖这项宿主失败。

结果索引 `output/optimization-2026-10-15/candidate/acceptance-outcome.json` 标为 `completed_with_shutdown_failure`。`native/post-cleanup-state.json` 确认宿主、launcher、supervisor 已不存在，discovery metadata 为 0；这是异常后的强清结果，不把“最终无进程”改写为正常关闭。

最小锁所有权修复及候选 2 定向复验已完成，见报告前部。候选 1 的失败与通过单元仍绑定旧身份，未用候选 2 的通过覆盖原始强制清理记录。

W1/W2 使用该候选的实际随包 SDK 和 Java Core，不是 Python pipe fixture。W2 回执保留 Native exception `REVISION_CONFLICT` 与原始 Core `WORKSPACE_REVISION_CONFLICT`，源码拒绝为 `WORKSPACE_SOURCE_CONFLICT`。计数字节是完整 IPC 响应行，排除启动 ready line／headers／中断体，不包含 payload 或凭据，也不换算 token。

`sessionCleanup=completed` 只说明 SDK 脚本断开；`Workspace.connect` 没有停止桌面宿主。候选 1 identity 的 `runtimeAcceptance=not_run` 是打包时原始字段；后续按独立回执记录通过单元和退出失败，不回写早期记录为全部通过。

### 另一个未单独复验的日志信号

候选 1 `native/application.stdout.log` 在 05:32:34 记录一次 `GitHistoryBackend Failed to save local history checkpoint: Exception caught during execution of add command`，没有更深 cause。随后 W1/W2、世界保存与导出成功，但这些结果以及 Java 回归不证明该次自动 local-history checkpoint 成功。该日志属于原有模块／路径，未纳入锁关闭修复；保留观察与未单独复验边界，不推测根因或丢失范围。

## 需求覆盖与未完成项（C1/C2当时范围）

| 规格／需求 | 已覆盖层 | 尚有的实际边界 |
| --- | --- | --- |
| SPEC-01 / ONB-01..09 | 首作／交付 fixture、compact 回归，候选 1 完整原生首作／玩家／导出链；候选 2 原工程回读及启动／退出补验 | 候选 1 退出失败保留；不宣称独立新手用户研究或候选 2 完整游戏重演 |
| SPEC-02 / REC-01..10 | UI 单元、定向编辑恢复／element-drafts 复验、Java 真持久化／源码保护，原生未保存数值观察与 W2 | 浏览器／Core／原生层分别成立；原生辅助技术／所有编辑面未全面认证。实际自动 history checkpoint 曾报错且未单独复验，不由其他保存结果代验 |
| SPEC-03 / PERF-01..04,06 | 原始样本、阶段边界、源码／fixture 身份、单元与大图完整性；9 cells 采集 | 仅浏览器 fixture／本地统计；native/Core 性能未测。PERF-05 没有前后提速证据，PERF-07 仅比较算法单元，无真实 before/after 复测 |
| SPEC-04 / AGT-01..09 | SDK 52／23 回归、真 HTTP+fixture Core、候选 1 真 Native W1/W2/W3／验收导出；候选 2 同 SDK 字节原工程回读和宿主退出补验 | MCP W2 不冒充 Native 源码 hash 路线；真实用户 grant 生命周期不由 fixture 代验，候选 1 失败保留 |
| SPEC-05 / REL-01..05 | 两候选独立源／ZIP／应用身份、必需文件、候选 1 完整选定功能链和失败、候选 2 字节变化证明与 scoped reacceptance | 不以新候选冒充完整无关套件重跑；Linux／完整安装升级卸载／签名认证未执行，本轮未发布或扩展支持 |
| SPEC-06 / DOC-01..08 | 关键配对路线、单 status 入口、实际 0.1.5 发布 vs 本地 0.1.4 候选、旧 0.1.4 媒体 scope、链接复验与包内文件清单 | 外链与远端 About/topics 只有主来源观察／建议，没有写远端；文档走查不是实际用户研究 |
| SPEC-06 / DEV-01..06 | 复用 selector 的环境／计划／报告入口，8 项 fixture／14 项路由复验与实际 dry-run，复用 Gradle UI 依赖 | fixture 中的模拟命令不能当所有工具均真实运行；未知环境阻塞不改写为产品通过 |

## 最终收尾与未验范围（C1/C2当时范围）

原打包前 `build/export` 的旧 EXE 与 `.build.json` 已按原 SHA 恢复，见 `output/optimization-2026-10-15/pre-existing-export-restored.json`；新候选仍独立保存在上述 output 目录。最后目标包 JDK 进程查询为空，无遗留候选游戏／Gradle 进程。候选 1 强清与候选 2 正常退出分别保留，不互相改写。

仍未验：本批 Linux 桌面／游戏与 GNOME 会话、完整安装升级卸载、额外平台／架构、原生辅助技术全矩阵、独立新手研究／自治成功率、冷缓存／默认网络、native/Core 性能、真实 grant 全生命周期，以及已记录的自动 local-history checkpoint。大资产搜索首次反馈超过建议目标，只有基线没有前后提速对照。

本报告及 status／计划的最终补记是在两候选冻结后形成，没有重包或改发布元数据，不作为 ZIP 内文档已经更新的承诺。全部初次失败 `.json`、`.log`、截图和 trace 保留。正式发布／支持扩展仍按原审批与实际证据执行。

最终文档补记的链接与差异检查另见 `output/optimization-2026-10-15/checks/markdown-final-1.json` 和 `diff-final-1.json`；执行结果、时间及收尾源码摘要以这些回执为准，不计为重新运行产品或游戏验收。
