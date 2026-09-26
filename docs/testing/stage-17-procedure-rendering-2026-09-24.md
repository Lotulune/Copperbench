# PRD17 真实诊断跳转与外部修订刷新

## 原生反例及修复

在专用 `final-acceptance/v32/jcef-scene-a` 副本，用 v34 随包公开 SDK 新建 `diagnostic_fixture`：服务端启动触发器下，逻辑返回节点连接上下文 X。Core 正确报告一个无效元素、两条错误（返回类型不匹配、触发器缺少 X）。真实 JCEF 总览、健康卡、底部均显示 2。Tab/Enter 打开第一条诊断后白屏，日志明确为 `Invalid block definition for type: return_logic`；API 标记该节点为已知，前端却仅注册了 `return_number`。失败图片、日志和实际投影在 `output/stage17-v34/jcef/` 及 `jcef-diagnostic-fixtures.log`。

修复限于 `ProcedureWorkbench.tsx`：补齐逻辑、文本、物品堆叠、实体返回节点；已有错误值连接仍可展示，由 Core 诊断，不在打开时自动断开。画布初始化失败时销毁局部工作区，显示原因、保留源码/诊断/返回入口并禁用保存，避免白屏或保存残缺图。未知的后端类型不会被猜测成可编辑节点。

从真实公开投影提取的回归夹具已加入 `ui-shell/e2e/fixtures/stage17-return-diagnostic.json`。原版本 5 失败、1 通过；修复后五种返回节点的加载和无关编辑保存均保留原连接，缺少渲染器的反例保留源码与返回入口。加上触发器目录回归，双浏览器尺寸共 20 项通过，TypeScript 通过。日志 `output/stage17-v34/procedure-render-red.log`、`procedure-render-green.log`；未覆盖旧失败证据。

## v35 原生重放

v35 冻结 27,202 个源文件，Windows/Linux 准备根包构建通过；22 文件、24 链接、642 Schema 引用审计通过。它未替换 Ubuntu 的已安装 v34。

真实 JCEF 的同一 Tab/Enter 操作现可打开目标过程，返回逻辑节点被选中，原 X 连接与两条诊断仍在，保存按钮未启用。图片 `output/stage17-v35/jcef/procedure-diagnostic-target-fixed.jpg`。增加原资源拼写错误后，API、总览、健康卡与底部一致为 11 条错误；资产页明确为 3 个有错误资产、9 条缺失引用，错误筛选只返回这 3 个资产。中英文截图与 API 结果在同目录。

修复资源再通过 SDK 修复过程后，API 错误数 11→2→0、revision 6→7，但持续停在资产页时仍显示旧的 11；导航回总览后才归零。失败图片 `mixed-repaired-filter-en.jpg` 与后续 `mixed-repaired-overview-en.jpg` 同时保留。原因是原生桥原地推进工作区修订，React 回调仅依赖对象身份，未重新索引和收集健康。源码改为额外监听修订数值。最初模拟用例因事件序列缺口触发整个投影重取而通过；改成连续事件后稳定失败，见 `revision-contiguous-red.log`，未以错误夹具的通过代替产品修复证据。

修订监听修复与返回节点、触发器回归共 **24 项通过**，`output/stage17-v35/final-refresh-green.log`。已知但不在快捷目录的节点标题改为显示类型标识，避免误称未知节点。上述修订监听和标题改动晚于 v35 冻结，进入 v36 后仍需原生复验。

v34/v35 产品均已正常退出，退出日志保留。测试资源恢复为原始字节；测试过程修为有效，保留在专用副本，未删除文件。Windows 缩放保持恢复后的 125%。整体 PRD17 门禁继续开放。

## v36 准备与被遮挡的原生复验

v36 两平台构建 59 秒通过，冻结 27,202 个文件；随包审计再次通过。主 JAR SHA-256：`27fac176653d52302bd3f3b179039e3307cc2c288350d5e9801e504104835894`。2,178 个 Java 类及全部生成器插件 ZIP 与 v34 字节相同，见 `output/stage17-v36/impact-proof.json`。冻结后的工作树仅把 ProcedureWorkbench 换行规范为 LF，归一化换行后与冻结文件一致；冻结源及其清单未修改。

真实产品 PID 9568、窗口 2887298 在修订 7 从资产页零错误开始；公开 SDK 注入资源错误并更新过程后修订 8，原生可访问性返回资产 3 错误/9 缺失、全局 11 错误，期间未点击导航。此时另一应用覆盖了产品，截图 `revision-eight-assets-eleven.jpg` **不是 Copperbench 可见内容，不计视觉通过**；同名 JSON 是目标产品的 UIA 读取。随后停止桌面输入。

资源与过程已由公开 SDK 恢复，`repair-fixture-api.json` 为修订 9、0 错误/0 无效。后台 UIA 仍返回修订 8，既没有观察到修订 9 的通知，也没有观察到其重绘，因此不能据此证明修订 9 刷新通过或断定新的计数故障。保留两次后台读取。已询问用户何时可让出桌面，待前台可用时对相同流程完成短重放；不反复抢焦点。产品目前保留打开，尚未记录本次正常退出。

Ubuntu 独立候选与具体 deb 已准备，**未安装**：`/home/stage17/copperbench-stage17-v36-amd64.deb`，SHA-256 `1e1953d16e6b209ac8dc77263a583fdcf6b60372e44c99283c19ad484f62bfbb`。准备记录 `output/stage17-v36/linux-preparation-proof.json` 确认已安装主 JAR 仍为获授权的 v34。v34 安装许可不自动扩大到此包。

下一段 Windows 场景 C 使用 `D:/Hyper-V/Stage17-Linux/final-acceptance/v36/model-c-windows`：复制并核对 239 个 v32 测试工程输入，再加入原共鸣工坊的 36 部件 BBModel，原文件哈希保持。`model-c-preparation.json` 仅证明准备；尚未打开 Blockbench、保存、导出或回导，不计新的连续建模通过。

## 后续只读核对与桌面阻塞

后台继续完成了 [v36 场景 B 原 14 项实测](stage-17-v36-workshop-2026-09-24.md)。再次只读查询原生窗口时，UIA 已报告 revision 9、全局 0 错误以及资产错误/缺失回零，记录于 `revision-nine-repaired-background-uia-confirmed.json`；期间未抢焦点或输入。故修复后的最终计数收敛已有真实原生 UIA 证明，先前两次仍为 revision 8 的读取继续保留，不推断精确收敛耗时，也不将后台 UIA 视为布局截图。

同一桌面不可用条件已跨三轮目标执行出现。期间完成了可独立推进的打包审计、24 项 UI 回归、场景 B 14 项测试、资源/源码哈希、导出和重开，尚未收到先前桌面使用询问的答复。只读窗口清单仍见用户的其他应用及保留的 Copperbench 测试窗口；PID 9568 存活，未关闭或重启。剩余真实画面、Blockbench 保存/导出/回导和客户端操作需要桌面可用，当前目标记为 blocked，非 complete 或用户主动 paused。

恢复时先核对上述窗口/进程与签名授权是否仍有效，延续既有测试副本；不要重跑已通过场景 B 或重建未改动候选。Windows 缩放已恢复 125%，Ubuntu 安装仍是已授权的 v34；具体 v36 包另需安装许可。保留全部工作树改动、失败证据和测试存档，未提交或推送。

## 用户让出桌面后的 v36 前台补验

用户明确表示“桌面可用，请继续”后，目标恢复 active。沿用原窗口和测试副本，实际可见资产页由 revision 9/0 错误，经 SDK 注入变成 revision 10/11 错误（资产 3/9），再恢复 revision 11/0；中途没有导航或以焦点切换代替修订事件。`output/stage17-v36/jcef/resumed-revision-nine-zero`、`resumed-revision-ten-eleven`、`resumed-revision-eleven-zero` 的 JPG/JSON 及 `resumed-*.json` API 收据保留；旧遮挡图片未覆盖。该连续修订刷新范围现在通过，产品随后正常关闭。

又完成 [v36 Windows 连续 Blockbench 和客户端补验](stage-17-v36-model-c-2026-09-24.md)，同时发现另一条**任务结束、不改变工作区修订**的刷新缺口：API 健康为 0/0，而底部仍显示客户端资源上下文加载期间的 233 条部分检查警告。新增用例先失败，再补任务终态稳定值依赖；相关 26 项回归和 TypeScript 通过，尚待新候选原生复验。此反例不推翻已完成的修订变化复验，但整体诊断一致性门禁仍开放。具体 v36 安装询问已向用户说明暂不执行，VM 保留 v34。
