# 核心体验与可信交付优化实施计划

日期：2026-10-15（Asia/Tokyo）。依据用户提供的 `copperbench-optimization-specs-md.zip`，由主代理统筹，`gpt-6.1-sol / max` 子代理实现；包含主代理在内最多 5 并发。全部实现和集成完成后统一验证。

## 独立复审后补修（当前源码）

后续独立复审确认13项缺陷（P1 3项、P2 8项、P3 2项），原结论和失败证据见 `output/optimization-2026-10-15/post-completion-review/summary.json`。统一检查结论为 **13项缺陷已修复，受影响无界面检查通过；1项既有Java按需用例跳过**；实际结果为 Java 67项通过、1项既有按需跳过；浏览器138项、Windows SDK 69项、Linux Native 28项通过；UI 60项、UI-Core 35项、Node 29项、贡献者15项及真实Gradle归档smoke 6项通过。权威回执为 `validation-summary.json`及`finding-closures.json`（均在 `output/optimization-2026-10-15/review-fixes-1/`）。首轮失败、7份失败命令回执与必要定向复验均保留，次数和原因见验收报告，不重复累计用例。

- 普通交付：使用本次实际Gradle归档任务产物，修正自定义输出的输入观察，按交付所有权及hash保护已有文件，并保留实际归档自身路径。
- 编辑与诊断：保护未知／只读草稿、拒绝迟到旧投影、保留已关闭引导偏好；MCreator归属与Core共用持久化ID优先解析，保留旧后备；各诊断保留自己的行号，同文件同行的再次明确跳转也重新聚焦。只读场景的过时fixture投影修正另记，不增列产品缺陷ID。
- Native SDK：修正已应答写线程与正常关闭之间的时序；附加连接关闭释放被makefile引用持有的OS socket，断言实际发送线程退出；按实际CRLF／UTF-8计量响应字节。
- 工具：源码过滤器纳入自身并使用 `source-paths-v2`；独立比较三个性能指标；解析Node实际测试汇总。

当前受测实现源码为 `4a4dfd5170cb7257b55f4032a55c7249fcf073ec7901c9dd5712a186f914569d`，身份回执 `output/optimization-2026-10-15/review-fixes-1/source-implementation-3.json`；原过滤范围对照另见 `output/optimization-2026-10-15/review-fixes-1/source-legacy-implementation-3.json`，不同过滤范围的hash不直接比较。`java-build-2`完成生产UI、Javadoc及JAR，冻结应用JAR的SHA-256为 `275462694b79289b095a1e1e6ff8b23c1727b38a3751e617e1190dcbe0363348`，仅为本轮构建产物。完整缺陷对应、实际次数和边界见[本轮补修记录](../testing/optimization-2026-10-15.md#独立复审后的13项源码补修本轮)。本轮源码独立于下列Windows C4／Linux run3冻结包，没有新增原生桌面／JCEF／Linux GUI／Minecraft玩家操作或同世界重开验收，也没有发行包或发布。历史源码清单、包身份、失败与通过均保留原记录。

## C4与Linux run3完成记录（历史）

四组61文件补修与运行中发现的11文件诊断补修已集成，形成未发布 Windows 候选4。冻结源码为 `21066c06a8145f9f90928c91344313ec830aa5d9fb90934c55891257a5c93c20`，ZIP为 `768a470899dfd9b10eae00f04f7f23a4159963a7939d91ce33a8da16d52296b7`。Fabric真实诊断修复、同revision输入变化失效、两进程同世界持久化、普通／精确交付及旧同路径数据保留已验；NeoForge 1.21.1构建、真实菜单渲染／Mod加载及正常退出smoke也已通过。Windows验收按原范围完成，后续Linux受限无界面跟进另列；不扩大为完整跨平台或全部玩法认证。逐规格矩阵、实际命令／次数／失败和证据索引统一见[验收报告](../testing/optimization-2026-10-15.md)，产品与官方发布状态仍见[状态页](../status.md)。

- 首轮统一回归：Java87项、Python SDK57项、UI54项、UI-Core35项、Node工具22项、贡献者11项、测量5项通过；浏览器初轮118项中15失败原样保留，受影响Chromium48项和compact39项随后通过。
- C3真实Small观察为20元素／81资源（含20固定PNG），每单元n=1、p95不发布；隔离Gradle目录真实构建记录395下载日志／69 resolution／20阶段，不宣称OS／Wrapper全冷或网络字节归因。历史browser large asset反馈p95 243.4ms仍超200ms建议，没有前后提速结论。
- C4诊断补修bridge28项／browser34项通过；Java首次9项中2个新fixture失败，修复后受影响6项通过、原相关3项保留先前通过，不改写初轮。实际失败task的捕获源码、当前manual文件第4行与单次Save恢复已用原生新包验证。
- C4 GameTest是 `modloads` 加载用例1/1，不能代替方块行为自动测试；方块放置与跨进程持久化由实际鼠标、同世界两新客户端、F3及保存退出日志证明。W3按原task恢复、build/export各一次并核对JAR/XML/input hashes。U覆盖同路径替换、修改／构建／重开与偏好保持，不等于完整安装器升级卸载。
- C3取消进入真实cancelled终态，但同步15秒进程清理超时为既有未改路径的限制，保留原错误；C4 NeoForge原生打开也再次出现自动初始检查点失败及Windows文件锁IOException，本任务未修复该旧路径、不推断数据丢失。Linux run3在显式暖缓存与任务本地内存配置下完成编辑／重开／正常关闭、Fabric构建／GameTest／两类导出及NeoForge构建smoke；首轮关闭及后续任务失败均保留；GUI／玩家操作／世界重开按用户严格无界面要求not_run，完整安装升级卸载、新手／自治研究未做，没有发布或支持扩展。Windows NeoForge首次网络失败exit10及回滚保留，后续成功明确为隔离暖缓存，不声称冷网络或NeoForge世界玩法。

候选1原生首作／SDK W1-W3／GameTest2/2／两进程存档／导出属于原字节，宿主正常退出失败和强清保留。候选2仅对锁所有权、启动／退出／回读／二实例完成定向复验，不冒充完整游戏重演。候选3的原生/下载测量与双语草稿也保留C3身份，不移作C4性能数据。本地版本配置仍0.1.4、官方0.1.5已有发布范围不变。三份最终文档是候选4冻结后的整理，没有重包，不能宣称包内已包含最终稿。


## Linux无界面跟进（独立范围，受限通过）

真实Ubuntu24.04.4 Hyper-V guest使用随包JBR25.0.2／JDK21.0.12，经SSH执行。首轮SDK空闲close被旧2秒预算结束（-9），独立EOF诊断2.8184s正常exit0；仅3文件的定向补修后Windows SDK60项、Linux Native API19项均通过。run3包SHA-256为 `dc664d968936f8967eff0770d9a339fecbd971dbe5494e34854fd8725d853d5a`，相对run2仅Native SDK与README改变，应用JAR仍为C4。公开SDK编辑／新host重开字段保持／正常关闭通过，不作冷启动或提速结论。

显式暖缓存及任务本地堆配置下，Fabric生成／构建通过，最终GameTest使用实际确认的1536MiB server堆，2/2通过，普通／精确导出成功、host正常exit0；最终续验未重跑独立generate／build，sourceSnapshot保持。NeoForge首轮约15分钟后失败、暖缓存第二轮javac堆不足均保留；第三次正式构建通过支持的JavaExecutable API与任务本地launcher为NeoFormRuntime设1536MiB，实际完成5364源码编译，产物及两host正常退出已核对。原SDK关闭、源码归属、CFR超时／堆不足及GameTest重映射失败不被最终通过覆盖；环境预检拒绝不算产品任务失败。

独立guest内存panic后固定1.5GiB、停止gdm／multi-user、按boot显式启用共6GiB swap；最终确认验收Java／runner为空，SSH正常关机、隧道停止、VM Off，见 `output/minecraft-validation/linux-hyperv-20261015/provision/final-shutdown.json`。Linux GUI／GNOME、玩家操作与世界重开按用户要求not_run；不作默认资源／冷缓存、完整安装升级卸载、正式认证或发布结论。派生6.2GiB ISO删除被自动审批以“blocked by policy”拒绝，已保留。完整身份、工件与原始失败见验收报告的Linux附录，原Windows及历史结论不变。

## 本次基线

- 实际 `baseCommit`：`b1a69d244ab7337608cc302826ed55d4a707f37f`，分支 `codex/local-pending-20261011`。
- 需求 ZIP 的 SHA-256：`0a693092b31397d1faf959c6c0fb68b6d67c160682198df714b1a831558cd869`。
- 开始时已有 133 个修改或未跟踪文件，保留于本地 `output/optimization-2026-10-15/` 的差异和文件摘要中；隔离工作树基于相同提交并覆盖这些既有修改。
- 本地版本配置与历史发布记录为 0.1.4；不根据附件引用的历史身份重置当前工作树。
- 后续官方抓取已确认 [Windows 0.1.5](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.5) 与 [Linux 0.1.5](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.5-linux-stable) 发布。本检出的版本配置／历史门禁仍是 0.1.4；本次保留实际基线，单独记录未发布优化候选的源码与产物身份，不把远端发布或旧门禁套给新字节。
- 附件是需求提案。实际任务授权来自本次用户请求；本次执行实现与验收，不进行提交、推送、发布或远端仓库元数据修改。

## 实施分组

| 分组 | 需求 | 交付及边界 |
| --- | --- | --- |
| 首作与交付 | SPEC-01 / ONB | 在现有创建、编辑、构建和导出入口上增加可关闭并恢复的指引，区分保存、构建、玩家确认和实际导出事实；双语与键盘可用 |
| 编辑与恢复 | SPEC-02 / REC | 清点现有草稿、诊断、冲突和任务行为，只修复实际缺口，保护迟到结果、新输入和原始错误 |
| 公开 Agent 路线 | SPEC-04 / AGT | 复用 MCP / Native 公共接口，补齐有界读取、任务恢复及示例，保留旧客户端和授权边界 |
| 测量与贡献者入口 | SPEC-03 / PERF；SPEC-06 / DEV | 建立可复放的浏览器性能测量和原始结果格式；复用现有 CI 选择规则形成可审阅验证计划 |
| 文档整合 | SPEC-06 / DOC | 根据最终实现补齐中英文安装、首作、失败恢复、Agent、导出路线，统一版本说明及当前状态入口 |
| 统一验收 | SPEC-05 / REL | 绑定实际源码和候选包摘要，分层记录静态、自动化、浏览器、真实 Core、原生桌面与游戏结果；缺失单元保留原因 |

前四组可并行；文档组按已确认行为展开。共享文件在主工作区集成，不同时直接修改主工作区。所有实现者先提供“复用／补齐／缺失／不适用”清单，再做对应修改。

## 状态与兼容约定

1. 复用现有 Core taskId、终态、revision、文件指纹、产物和验收回执；不添加独立的“项目已验证”事实。
2. 进度只持久化展示偏好和对象引用；重新进入先读取原对象、原任务，不重复创建或启动。
3. 保存只确认实际提交的草稿版本，迟到刷新不得覆盖新输入；未知写入结果不得自动重放。
4. 普通导出与既有验收的精确导出分开；构建成功、进程启动和用户确认各自表述。
5. 保持 loopback、用户授权、单次写入、工作区锁、generated/manual 归属及事务回滚规则。

## 原定统一验证顺序（保留执行约定）

1. 实现冻结后审查整合差异、检查共享接口和双语文案，确认保留既有改动。
2. 统一运行相关 UI、SDK、脚本及 Core 回归；需要 Gradle 构建 UI 时不重复生产构建。
3. 运行受影响 Playwright 路线并观察界面、键盘操作和草稿恢复；建立至少 20 个有效重复的选定交互基线，保留失败及原始数组。
4. 使用隔离真实工作区验证公开 API 的失败—修复、冲突、任务恢复及交付路径；不把 fixture 结果表述为真实 Core 通过。
5. 构建可用平台的候选包并冻结 SHA-256，按实际环境执行原生和游戏验收。游戏操作使用 computer-use 技能、单一控制器、新世界和既有 Copperbench 生命周期；记录新进程重进、日志和正常退出。
6. 记录环境受限的单元，形成需求覆盖与验收报告；发布状态和历史证据保持原范围。

本轮建立了带候选、样本和边界的分层基线，没有同条件before/after，未据此宣称产品提速或追加推测性重构。原始失败与对应受影响复验分别保留；编译或脚本通过不替代实际包、玩家行为与持久化验收。
