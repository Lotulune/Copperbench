# UI/UX 优化验收（2026-10-16）

本轮按[优化 Spec](../roadmap/uiux-optimization-spec-2026-10-16.md)独立实施，未使用子代理。保留任务开始时的全部既有工作树修改；本记录只归属本轮 UI/UX 优化，不把其他聊天的成果或旧安装包证据计入本轮。

## 已实现内容

| 需求 | 实现与受影响范围 | 验证结果 |
| --- | --- | --- |
| UX-01 | [弹窗栈](../../ui-shell/src/hooks/dialogStack.ts)：一个键盘事件所有者、顶层 Esc/Tab、背景 inert、保留 autofocus、原生 summary、显式/默认回退焦点 | K01–K09 迁入真实 React StrictMode；嵌套同时挂载、阻断窗口、触发器卸载、错误后输入焦点通过。 |
| UX-02、15 | [创建元素](../../ui-shell/src/components/CreateElementModal.tsx)：工作区/会话/请求及输入归属、未决请求去重、迟到结果通知；类型搜索、常用/最近、37 种用途说明 | 提交 A 后关闭重开或直接填写 B，A 回执不覆盖 B；键盘搜索、只读原因和紧凑视口通过。生成器可用性只按 Core 合同表达。 |
| UX-03、04、10、14 | [任务抽屉](../../ui-shell/src/components/TaskDrawer.tsx)、[日志阅读](../../ui-shell/src/components/TaskLogView.tsx)：任务精确定位、最近失败按时间排序、暂停/恢复跟随、未读数、搜索/级别/复制、状态配色和应用语言时间 | 追加日志保持暂停位置；跨任务位置隔离；失败提示进入对应日志/诊断；缺失任务明确显示不可用；五种终态/过程状态区分；日期随应用语言切换，原始时间保留。 |
| UX-05 | [原生授权文案](../../src/main/java/dev/copperbench/headless/TaskAuthorizationCopy.java)和[授权窗口](../../src/main/java/dev/copperbench/headless/LocalApprovalWindow.java)：使用产品语言偏好，全文双语、根目录/操作/期限/撤销、EULA、默认取消、长文本滚动 | 文案与窗口测试通过；实窗中英文正文与按钮可见，Esc 返回取消；中文 Tab 可到达允许按钮。仅展示夹具，不签发真实权限。 |
| UX-06、07 | 亮色次级文字 `#61675c`，独立滚动条配色，10px 宽度，面板滚动边界 | 24 组浏览器矩阵验证受影响文字/表面的 computed style 对比度 ≥4.5:1、滚动条样式及主要动作可达；没有宣称全产品 WCAG 合规。 |
| UX-08 | [共享确认框](../../ui-shell/src/components/Dialog.tsx)与[持久反馈](../../ui-shell/src/components/FeedbackHost.tsx)；替换产品自有 `window.confirm/alert` | 删除取消、函数草稿重载、发布确认、语言保存失败、焦点和键盘回归通过。浏览器卸载保护和原生文件选择器保留。 |
| UX-09、16 | 状态未知不显示已连接，不定进度；诊断导航携带严重程度，展开后定位并消耗导航请求 | 握手/状态缺失不误报成功；从其他视图进入具体错误、动作和焦点通过。 |
| UX-11 | [按工作区保存列表状态](../../ui-shell/src/hooks/elementListSessions.ts)，保留筛选、排序、布局、分页和滚动 | 第二页选中元素后跨页返回恢复；换工作区不串用筛选。此处是会话内浏览状态，不新增崩溃恢复承诺。 |
| UX-12、13 | 首作常规状态简化，技术身份/时间进入详情，保留输入变化和结果未知；设置说明直接可见并保留描述关联 | 首作既有流程、设置与语言回归通过；重要风险和草稿保护保留。 |

## 实际执行

| 检查 | 结果 |
| --- | --- |
| `npm test --prefix ui-shell` | 67/67，0 失败、0 跳过。 |
| Gradle `test`，限定 `TaskAuthorizationCopyTest`、`LocalApprovalWindowTest`、`UiLocalePreferencesTest` | 最终 10/10，0 失败、0 错误、0 跳过；包括中英文默认取消、明确允许和关闭返回值。 |
| Gradle 依赖的 `buildUiShell` | i18n、TypeScript、Vite 生产构建通过；不另行重复运行独立 UI 构建。现有大 bundle 提示不等同原生性能退化。 |
| 12 个受影响 Chromium spec | 首轮 118 个用例：115 通过、3 失败；受影响 3 个 spec 定向复验 27/27。118 个不同用例最终无未解决失败，27 中的重复用例不再计入独立用例数量。 |
| 新增矩阵 | 中/英文 × 亮/暗 × 1920×1080、1366×768、1280×720、1093×614、853×480、640×360，共 24 组通过；后三种模拟缩放后的 CSS 内容空间。系统主题已有回归通过。 |
| Windows 原生展示夹具 | 英文正文、按钮、Esc 取消；中文正文、按钮、Tab 和 Esc 取消。两次回执均 `confirmed=false; authorizationIssued=false`；中文夹具退出码 0，英文保留完成回执及窗口消失观察，未另行捕获 OS 退出码。 |

Java 使用仓库 JBR 25 和[现有独立进程入口](../../scripts/run-gradle-external.ps1)。只在本地验收 init script 中为 `Test` 设置 `COPPERBENCH_TEST_APPROVAL_WINDOW=true`，不修改系统设置或用户全局配置。

浏览器首轮范围为 `accessibility`、`first-creation-delivery`、`localization`、`new-workspace`、`scenarios`、`settings`、`task-diagnostic-source`、`theme-preference`、`uiux-regressions` 和三个新增 `uiux-audit-*` spec，使用隔离端口 5197。复验仅运行 `accessibility`、`task-diagnostic-source`、`uiux-audit-dialogs`。

## 失败及修复记录

1. 直接启动 Gradle 出现 `Unable to establish loopback connection`。按项目开发指南使用独立进程入口后可以构建，不更改代理或网络适配器。
2. i18n 检查发现两处未标注的技术 JSX 文本。主标题改为双语行动文案，技术标识继续以原值显示于详情；复验通过。
3. 显式回退焦点和普通 `main` 写在同一个选择器中时受 DOM 顺序影响。改为先查显式回退，再查 `main`，StrictMode 回归通过。
4. 任务恢复按钮已简化为“检查任务状态”，旧断言仍匹配原文。更新语义对应断言并保留错误详情/成功恢复的验证。
5. Procedure 首轮等待失败的 trace 记录了两次 Vite 连接和变化的依赖版本，页面被重新加载；同一产品源码定向复验通过。未把这次重载记成后端创建丢失。
6. 原生授权最初的长文本未充分留出可读空间。增加按可用屏幕约束的滚动区域，确保取消按钮位于窗口内，Java 与实窗复验通过。
7. Windows 控制器两次拒绝使用返回的窗口状态对象，另一次观察被其他窗口遮挡；重新从窗口清单选择唯一夹具并激活后，观察和原始窗口对象的键盘输入恢复。未盲目点击其他应用，未签发实际授权。

所有失败日志和截图保留在本地证据目录，不以复验输出覆盖。

## 证据及边界

本地 `output/uiux-optimization-2026-10-16/` 保存：

- `baseline.json`、`baseline/`、`initial-git-status.txt`：附件摘要及本轮修改前的文件字节。
- `ui-unit-pass-1.log`、`gradle-pass-1.log` 至 `gradle-pass-4.log`、`playwright-pass-1.log`、`playwright-pass-2.log`：实际命令输出和失败历史。
- `playwright-pass-1/`：8 张矩阵截图及 3 个首轮失败的截图/trace；`playwright-pass-2/` 为定向复验输出。
- `native-*-final.jpg`、`native-zh-tab.jpg`、`native-*-actions.json`、进程记录和原始 stdout/stderr：原生展示与键盘证据。
- 最终来源/产物摘要、检查汇总及 Java XML 报告：绑定所测试源码、UI 构建和相关 class 文件。

浏览器测试使用完整 React 应用及既有模拟 Core/注入桥接夹具。Windows 原生检查运行实际授权展示组件的独立测试进程，不进入授权存储，不接受真实服务器 EULA。未执行最终安装包、真实 CLI 签发、Linux/JCEF、OS DPI 切换、触控板或屏幕阅读器全链；没有把 CSS 视口模拟称为原生缩放验收。Minecraft 玩法、世界持久化和安装器不在本轮变更范围。
