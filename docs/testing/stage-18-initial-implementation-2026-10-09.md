# Stage 18 首批实现与验证

日期：2026-10-09（Asia/Tokyo）
需求入口：[PRD-STAGE-18.md](../../PRD-STAGE-18.md)
开发基线：main@2bfbb48e80dac267b2043ffcbfc795a0bdd931a3

## 状态

首批实现与本轮验证已完成：代码提交`886fc5ee`的常规CI、真实Native mod回归和Linux candidate三项工作流均通过。第一轮CI发现遗漏的中文诊断消息，已修复并通过跨平台构建。完成范围限于以下已列证据；Stage 18整体仍未关闭。

## 基线变化

上次评估基于 b1a69d2；其后 PR97 已修复短超时测试启动预算，并更新全量UI回归。新基线的[常规CI](https://github.com/Lotulune/Copperbench/actions/runs/37813145564)与[Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/37813145670)通过。本批保留该修复，不重复记为本批贡献。

## 首批范围

| 子项 | 实现 | 验证 |
| --- | --- | --- |
| S18-01 item/recipe局部契约、未知契约稳定错误 | item 25字段、recipe 29字段，显式partial；缺失、未知和非对象契约使用稳定错误 | Python与真实Native发现通过；字段投影5例及真实Core创建/拒绝/重开1例全部通过 |
| S18-02 Native诊断参数安全渲染 | 简单具名参数只替换一次，保留原始code/details | Native SDK 21/21、完整Python SDK 43/43通过；先在旧实现复现4 failures + 4 errors |
| S18-03 生成冲突路径与拒绝不写 | typed原因、安全相对路径、多条诊断、可用Java预览、归属预检先于include文件恢复 | 3个Java类27例全部通过；真实混合副本拒绝、哈希/revision保护与重开通过 |
| S18-04 独立Nightly与红灯汇总 | 5个独立产品suite与聚合；保留8轨道generator矩阵 | Nightly/协议契约16/16通过，包含25种异常聚合子场景；完整Nightly尚未运行 |
| S18-05 协议scope、严格真实mod回归 | 协议fixture明确非模型/真实build；真实mod独立工作流，关键probe缺失/失败/未验证均为红灯 | manifest及严格门槛7/7通过；修复后真实回归46/46、纯Java计算54条、GameTest12/12、导出字节与重开均通过 |

## 本地验证

- `python -m unittest discover -s sdk/python -p 'test_*.py'`：43项通过。
- `python -m unittest discover -s scripts/tests -p 'test_nightly_and_eval_contracts.py'`：16项通过；覆盖独立job图、失败/取消/跳过/缺失状态的聚合，以及协议scope兼容字段。
- `python -m unittest discover -s scripts/tests -p 'test_chronometer_regression.py'`：7项通过；逐项缺少必需probe、错误phase、失败或未验证状态均不能通过；旧revision不替代成功查询；必需证据收集失败撤销completed状态。
- `node scripts/verify-ai-evals.mjs`：10个既有case ID、16项observed checks与10个legacy labels通过。
- `node scripts/verify-markdown-links.mjs`、`git diff --check`：通过。

本地Java运行时不可用，不把已添加的Java测试记为通过；后续以CI日志及实际产物为准。

## 集成发现与修复

首个远端提交为 `de4c794ab43b839793438bf333bcc59250a84f8b`，评审入口：[PR98](https://github.com/Lotulune/Copperbench/pull/98)。

- [真实Native mod回归 37818611410](https://github.com/Lotulune/Copperbench/actions/runs/37818611410)成功：46项必需probe全部通过，包括实际字段发现、可读错误、带路径归属拒绝且revision/源码不变、独立原生副本构建、注入编译错误与修复重建、打包后GameTest、导出字节哈希及重开。该结果对应上述首个提交，后续修复另行复测。
- [常规CI 37818611368](https://github.com/Lotulune/Copperbench/actions/runs/37818611368)的UI schema 35/35通过，随后UI构建发现缺少 `diagnostic.generation_source_conflict` 的中文词条。生产Java编译成功，但此job中的Java测试与Javadoc未执行；报告步骤显示success但明确没有JUnit结果，不能计为测试通过。
- [Linux candidate 37818611327](https://github.com/Lotulune/Copperbench/actions/runs/37818611327)的打包步骤也在同一个i18n检查失败。
- 修复时为通用无参数、仅原因、路径加原因分别使用对应message key，补齐中英词条；6/6本地化回归通过。CI同款 `npm run build --prefix ui-shell` 本地成功，包含392/392中文key、1696条英文消息校验、TypeScript编译和Vite生产构建。

保留本次失败记录；后续成功结果不覆盖这次真实发现。

## 修复后的真实 mod 复测

代码提交：`886fc5ee432e6af212491a1c2a07d95b8153239e`。实际PR合并测试树：`f61b43bf614e30d03db32ec11994a1403a3148d5`，包含开发基线main与本PR。运行：[Native mod regression 37819568131](https://github.com/Lotulune/Copperbench/actions/runs/37819568131)。

| 验收项 | 真实结果 |
| --- | --- |
| 必需探针 | 46/46通过，缺失0、失败0、未验证0 |
| 字段发现 | item 25字段、recipe 29字段，实际响应标记partial；未知类型返回稳定错误与可用类型 |
| 字段拒绝 | 非法function错误包含`/commands/0`与原因；拒绝后的成功查询确认revision不变 |
| 归属拒绝 | 实际返回`/src/main/resources/pack.mcmeta`、`/src/main/resources/fabric.mod.json`及`UNOWNED_BASE_FILE`；使用已翻译的路径消息key |
| 文件保护与持久化 | 手写文件哈希和revision不变；混合副本重开仍保留已创建function |
| 独立原生交付 | 真实Gradle构建；注入`BrokenProbe.java`触发`JAVA_COMPILE_ERROR`和精确路径；移除该已知错误后重新构建成功 |
| 独立时间计算 | 54条纯Java断言通过，包含精确倒计时预期 |
| 最终JAR服务端验收 | 12执行、12通过、0失败、0跳过、0框架占位用例；mode为packaged_jar |
| 可信导出 | passed_current_input；sourceCurrentAtCompletion=true；复制后的JAR/XML实际字节哈希与验收、导出回执一致 |
| 交付重开 | 验收任务及artifact摘要仍可读取；原生手写文件保持一致 |

已下载artifact并独立解析XML、JAR与原始响应；并非仅依据job绿灯。XML含12个testcase且无failure/error/skipped节点；交付JAR包含正式mod类，没有打入GameTest测试类。

`diagnostic.path`的前导`/`表示工作区相对文件指针，不是宿主机绝对路径。上述两个资源文件提供可定位路径和日志动作；Java源码预览仅用于安全、可读取且符合现有大小边界的Java文件。

- JAR：9,487字节；SHA-256 `540371ca504117ff10a25ed272ef373abc38a5aa7f685c54b9d0618dd6208b29`。
- GameTest XML SHA-256：`684098e018bba21b746c7f0ad0929eefc52bfe2e46b74a8165abd863914a356b`。
- 验收输入快照：23文件；SHA-256 `17af9510d8bb4cd892b66370331d5d57afe215b68ef3ddf1d3cd86f07e30ad27`。
- 原始artifact：`copper-chronometer-regression-37819568131`，ID `11568802451`；ZIP SHA-256 `29ee0328d29fd4ab19c0281e26be84c4e97ff569a5ed8cfd912f4db38ac488e7`，按工作流保留14天，源码与驱动保留在PR。

Native试用驱动记录190.336秒；它不含CI准备/产品编译，允许恢复已有Gradle缓存。这个数值只描述本次运行，不用于冷启动或效率提升结论。受控错误由驱动注入与移除，不是未知bug的一般自动修复测试。

## 修复后的常规 CI

同源运行：[Build and test 37819568135](https://github.com/Lotulune/Copperbench/actions/runs/37819568135)，head同为`886fc5ee432e6af212491a1c2a07d95b8153239e`，整体成功。

| 层级 | 实际结果与来源 |
| --- | --- |
| Java与Javadoc | JUnit汇总1077个用例：1008 passed、69 skipped、0 failed；compileJava、compileTestJava、test、javadoc均实际执行，BUILD SUCCESSFUL |
| 本批五个Java目标类 | 33 passed、0 skipped、0 failed；按Gradle逐用例PASSED日志核对，详见下表 |
| Python SDK | 同一组43例在Linux与Windows均通过；不重复加总成86个独立用例 |
| UI-Core contract/schema | 35 passed、0 failed、0 skipped |
| Production bridge/localization | 29 passed、0 failed、0 skipped |
| TypeScript SDK | 12 passed、0 failed、0 skipped |
| 完整Chromium项目 | 275 passed、0 failed、0 skipped；1 worker、0 retry；compact与visual-matrix项目未执行 |
| UI生产构建 | TypeScript、Vite、i18n在Linux/Windows通过；392/392中文引用key、1696条英文消息通过检查 |
| Windows文件系统边界 | 31 passed、1 skipped、0 failed；唯一跳过项要求POSIX权限视图，Windows不提供该视图 |
| Windows MCP | 配置的6个适用场景成功；底层check总数未从artifact读取，不另填未经核实的数字 |
| 维护检查 | release-history 3/3、Linux metadata 5/5；signed release-source gate、Stage9 Windows harness、product status和Markdown links通过 |

| 本批Java类 | Passed | Skipped | Failed |
| --- | ---: | ---: | ---: |
| GenerationPreparationExceptionTest | 14 | 0 | 0 |
| MCreatorGenerationPreparationTest | 11 | 0 | 0 |
| GenerationConflictTaskDiagnosticTest | 2 | 0 | 0 |
| ItemRecipeFieldContractTest | 5 | 0 | 0 |
| Stage18ItemRecipeFieldContractTest | 1 | 0 | 0 |

Java数量来自完成日志中的逐用例记录及读取本轮XML的JUnit汇总步骤。成功配置没有上传原始Java XML，本记录不声称另行取得该文件。69个跳过项包括60条逐例SKIPPED日志及9个条件禁用的factory/参数化容器；后者由reporter明细与源码启用守卫核对。它们不计为通过；本批33个目标用例含符号链接拒绝用例，均实际执行。

| Java报告跳过项分类 | 数量 |
| --- | ---: |
| 需要显式启用或指定工作区的Minecraft构建、客户端、服务端与GameTest矩阵 | 39 |
| 桌面JCEF、Windows特定行为与交互授权窗口 | 19 |
| 真实资源与依赖专项探针 | 4 |
| 需要`copperbench.stage9.scale=true`的Nightly规模门禁 | 3 |
| 外部工具、现有导出包或专用fixture | 4 |

UI中的精简连接安全入口、fast smoke入口与此次完整Chromium路径互斥，因此按条件跳过；failure-only诊断上传也未执行。这些step跳过与测试用例的skipped分别记录。Windows MCP job没有执行新十案例HTTP eval harness；16项scope/结构测试与manifest校验来自Native回归的独立preflight。

## Linux候选包与启动预检

[Stage 15 Linux candidate 37819568175](https://github.com/Lotulune/Copperbench/actions/runs/37819568175)在同一代码提交上成功。完成日志确认：

- portable和`.deb`候选包构建成功，包内容、执行权限和自带JDK路径检查通过。
- 在PATH中没有系统Java、Gradle或Git的隔离环境中，打包后的headless bootstrap启动通过。
- 打包后的JCEF/MCP在Xvfb/X11中启动冒烟通过。
- 打包后的NeoForge 1.21.1与Fabric 1.21.1客户端X11渲染预检分别通过。
- 候选产物及installed-gate harness上传成功；候选artifact为`stage15-linux-candidate`，ID `11568864806`。

这些是Ubuntu CI候选包的打包、启动和渲染预检。候选manifest仍声明`development-not-certified`及`formalSupportClaim=false`；没有因此发布新的正式安装包或关闭指定Windows/GNOME交互桌面的首次使用门禁。上述客户端预检也不代表铜质昼夜仪的实际玩家交互验收。

## 剩余门禁与下一步

| 门禁 | 本轮结果 | 后续工作 |
| --- | --- | --- |
| G18-A | 常用item/recipe发现、稳定错误、消息渲染已验证 | 完整公开类型、八轨适用能力和更多条件/资源规则 |
| G18-B | 类型化冲突、位置、文件保护和原生交付已验证 | 完整生成预检、拟写文件预览、迁移选择及竞态回归 |
| G18-C | PR产品套件、严格mod回归和聚合逻辑已验证 | 新拆分后的同源完整Nightly；八轨本轮未重新运行 |
| G18-D | 本轮未关闭 | 环境doctor、Windows/GNOME指定安装候选的首次成功、按职责渐进拆分 |
| G18-E | 本轮未执行 | 冻结任务与成功标准，安排实际陌生用户试用和受控比较 |

继续按PRD的B、C、D批次推进；首批实现完成不等于Stage18整体完成。所有固定结果绑定上述代码提交，PRD/执行记录的文字补充与产品代码分别审阅。

## 已知边界

当前会话没有可交互的桌面Minecraft客户端与minecraft-control连接。铜质昼夜仪的真实客户端输入与显示、音效、玩家实际合成、完整世界保存重进、外部用户及冷缓存默认网络体验仍待验证。Linux候选包的Xvfb自动化结果按上一节范围单独成立。

完整类型/轨道发现、完整生成预检、安装体验、职责重构与外部试用按PRD后续批次继续。
