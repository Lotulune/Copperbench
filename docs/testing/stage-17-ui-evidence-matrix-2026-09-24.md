# PRD17 诊断 UI 分层证据矩阵

依据原 PRD S17-04 和 7.2：六种夹具、两档窗口、中英文、键盘定位由 Playwright 覆盖，另加真实 JCEF 关键流程；不要求每个浏览器单元都重复为原生全交叉测试，也不把 DPR 模拟称为 Windows 设置变更。

## v37 当前浏览器矩阵

`ui-shell/playwright.stage17-matrix.config.ts` 设置 1280×720、1920×1080 × DPR 1/1.5 四项目。每个项目执行六种夹具的中英文当前计数、健康卡/底部文字边界和键盘导航，以及五项语言/草稿/定位/原生标题栏区域/主题检查，共 **44 项通过**。其中六夹具×四配置为 24 项，其他 20 项为相关语言检查，不将 44 误写成 44 种诊断夹具。

本轮补上中文状态切换前的健康卡和底部边界断言。测试文件/专用配置的更新晚于 v37 冻结，仅为验收驱动，不改变产品二进制；生产前端与已测 v37 一致。日志及图片在 `output/stage17-v37/diagnostic-layout-matrix.log`、`diagnostic-layout-matrix/`。

## 真实 JCEF 关键流程

| PRD 夹具 | 原生证据与实际范围 |
|---|---|
| 0 | v34 中英文零状态；v36 连续修订恢复回零；v37 任务完成与重开回零 |
| 单元素多错误 | v34 API/原生 1 个无效元素、2 条错误；返回节点跳转先白屏，v35 修复后正确选中节点并保留原连接 |
| 只有资产错误 | v34 同 revision 外部资产变化为全局 9、资产 3/9；键盘跳转到准确 stable ID，修复回零 |
| 混合来源 | v35 11 条错误与资产 3/9 分开；v36 保持资产页、不导航/切焦点的连续 revision 0→11→0 |
| 仅历史失败 | v37 公开导出不存在的验收任务产生真实 failed；关闭重开后 API/总览/健康/底部均为当前 0；中英文任务日志仍显示准确失败原因 |
| 索引/检查未完成 | v37 客户端运行期间 API/原生同为 233 条部分检查警告，没有伪全绿；完成后 API 回零、可见 UI/重开回零。原生遮挡期间精确收敛未测，无焦点终态事件因果由协议回归覆盖 |

历史失败任务为 `405b941c-8fcd-4c3d-8105-562389376939`，诊断 `VERIFIED_TASK_NOT_FOUND`。用合法 UUID 故意指向不存在的验收记录，返回任务 failed；不是手工伪造任务缓存，也没有导出未验证 JAR。工作区 revision 始终 6。API 证据 `historical-failure-fixture.json`、`historical-failure-reopened.json`；原生图片 `historical-zero-overview`、`historical-log-target-settled`、`historical-english-log`，均在 `output/stage17-v37/`。中文已恢复，产品正常退出。

## 真实 Windows 缩放范围

沿用 [v34 原生缩放记录](stage-17-native-health-2026-09-24.md)：100%/96 DPI 下物理 1280×720，150%/144 DPI 下物理 1920×1080（逻辑 1280×720），中英文关键计数可辨。物理尺寸证明位于 `output/stage17-v34/scale150-window-metrics.json`，而非 jcef 子目录。设置已恢复原 125%。

v34 与 v37 打包主 JAR 中全部 CSS 字节相同，`output/stage17-v37/css-reuse-proof.json`；主样式 SHA-256 `8c50d1846f785d42eaea98c636da6ff0142709d690f949b1e8d4cdfcb423ab91`。因此复用已观察过的原生缩放布局，新增逻辑用当前候选测试补证。此为按原 PRD 的分层范围；没有声称所有夹具/语言/尺寸/系统缩放都做了原生全交叉，也没有把旧进程动态跨 DPI 裁切记为通过。
