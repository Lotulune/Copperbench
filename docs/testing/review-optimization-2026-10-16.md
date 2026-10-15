# 复评优化验收记录（2026-10-16）

状态：本轮受影响检查通过。Node 65 项、UI 单测 67 项、贡献者／CI 规划 29 项、Chromium 45 个独立用例通过，均无跳过。生产构建各关卡在本轮隔离输入上通过；初次共享工作树构建阻断、隔离准备缺项及两项旧断言失败均保留。

需求见[spec](../roadmap/review-optimization-spec-2026-10-16.md)，当前任务状态见[决策看板](../maintenance/review-improvement-board-2026-10-16.md)。四名 `gpt-6.1-sol / max` 实现代理与主代理最多五并发；实现期间未运行编译、测试或产品。

## 来源与范围

- 审计附件 SHA-256：`dcd1a1b4655043134e17a32adf5ad362a201ba89fbd465e6da3283a07c30ec90`。
- 本地 HEAD：`b1a69d244ab7337608cc302826ed55d4a707f37f`，分支 `codex/local-pending-20261011`，保留任务前既有未提交内容。
- 本轮源码变更：性能脱敏／覆盖合同、任务评估校验／汇总、Vite／Playwright 测试服务隔离、CI／本地检查入口及文档；验收中同步两处旧场景断言。没有修改产品 Java、游戏行为或草稿持久化实现。
- 附件的 0.1.6 主线／发布字节与本地检出不同；本轮未更新发布版本、打包、安装、提交或推送。

本地原始回执统一保存到 `output/review-optimization-2026-10-16/`。任务前原件、既有文件的小型修改前切片、源码清单、每条命令的 argv／退出码／日志 hash 与原始日志分开保留，不复制旧世界或完整候选包。

## 统一验收

所有四组实现冻结后统一执行检查。以下计数为各自检查层的实际用例数，不合并成产品成功率。

| 检查 | 实际结果 | 原始回执 |
| --- | --- | --- |
| Node 证据／性能／源码身份／发布历史 | 65 通过，0 失败／跳过；含 19 项任务研究 fixture | `node-evidence.json`、`.log` |
| UI 单测 | 67 通过，0 失败／跳过；含 7 项真实服务隔离回归 | `ui-unit.json`、`.log` |
| 贡献者规划与 CI 路由 | 15 + 14 通过，0 失败／跳过 | `python-planning.json`、`python-ci-selection.json` |
| 文档与状态 | Markdown 本地链接、产品状态通过 | `markdown-links.json`、`product-status.json` |
| 两份研究模板 | 均 `planned`、`registrationReady=false`、真实观察 0；退出 0 仅计划查看 | `novice-plan.json`／`.log`、`agent-plan.json`／`.log` |
| 生产构建 | 翻译检查（中文引用 388/388、英文消息 1705）及 TypeScript 通过；补齐快照图标后 Vite 打包通过 | `isolated-ui-build.json`、`isolated-vite-build-2.json` |
| Chromium PR smoke + 首启语言 | 首轮 43 通过、2 旧断言失败；两项修正后定向 2/2 通过，合计 45 个独立用例通过 | `isolated-chromium-smoke.json`、`isolated-chromium-recheck-2.json` |

Node 批次使用 `node --experimental-strip-types --test` 执行 `performance-statistics`、`performance-fixtures`、`performance-runner`、`source-identity`、`native-observation`、`task-evaluation`、`product-release-history` 七个现有 runner 下的套件。规划分别运行 `test_verify_changed.py` 与 `test_ci_selection.py`。UI 使用 `npm test --prefix ui-shell`。

浏览器一次选择 `scenarios`、`new-workspace`、`sdk-connection-security`、`jcef-bridge`、`mcp-runtime` 五份 PR smoke，以及 `dialog-locales.spec.ts:4` 的三个首启语言场景；保留既有用例少量截图。仅失败保留 trace。完整 argv、cwd、退出码、持续时间与日志摘要见对应 JSON，没有隐式重试或删除失败回执。

## 失败、隔离与定向复验

1. 同一共享工作树在本轮实现期间另有 UI/UX 任务修改产品源码。统一批次观察到其新建 spec 和 UI 文件变化；完整 `tested-source-before.json`／`after.json` 的批次 hash 均为 `c059730e44aad3bf388c353c87012ed8d4149811ee6685951dc3ff07d1bad1aa`。该批次工具检查通过，但 `ui-build` 在翻译门禁被 `FirstCreationGuide.tsx` 两处新增字面文本阻断，未进入 TypeScript／Vite。这里不判定另一任务的最终实现结果。
2. 未改写另一任务的 UI 源码。建立约 6.6 MB 的局部验收快照：任务前字节由初始清单核对；22 个同时变化的文件从已有备份恢复至**快照内**，其余按摘要复制；本轮测试配置／工具改动叠入。Node 依赖通过 junction 复用，没有复制 JDK、安装包或完整工作树。
3. 第一次隔离构建的翻译、TypeScript 已通过，Vite 因准备快照漏了原有 `icon.png` 失败。补入与任务开始 SHA 一致的该资源后，只重跑 Vite 打包通过，未重复通过的单测／规划／翻译／TypeScript 检查。保留两份隔离输入清单；第二份包含 627 个输入文件，摘要 `4aa259cd55950958f8b3a22e34afd7aa209bd39c9225e9f61b16504ffaa5e789`。这是声明范围的输入清单，不能与全仓 `source-paths-v2` hash 直接比较。
4. 首轮 Chromium 的两项失败是已有场景断言过时：硬度字段的 `data-field-path` 已在容器上，实际焦点在文本框；冲突标题已为“工作区已有新修改”。仅修改 `scenarios.spec.ts`，保留聚焦和可访问名称检查，随后只跑这两个用例并通过。最后快照摘要 `8ef9ae863ba9ca8b61ed707cbc7e4c73f18c9bab8dd4403a9239f4d54cb9fad1` 仅比第二份清单多了测试文件变更，产品构建输入不变。

隔离测试前后输入与已记录依赖包摘要一致。`isolated-validation-summary-2.json` 保留首轮浏览器失败，`targeted-recheck-summary.json` 记录修正后的 2/2；`final-validation-summary.json` 组合有效检查与范围，初始失败总结不覆盖。共享工作树里另一项 UI/UX 优化的最终合并构建仍由该任务验收，本记录不把隔离结果移作整个并行工作树通过。

Vite 仍提示两个大 chunk；它是体积观察，没有在本轮进行拆包或原生性能优化。没有采集重复性能基线，也没有生成安装包、ISO 或世界副本。

真实新手／自治研究、原生 JCEF 性能、安装升级卸载、Minecraft 玩家行为与同世界重开均未包含在这些工具／浏览器检查中。相应任务已经登记，保持待执行状态。

## 需求结果

| 需求 | 当前结果 | 实测边界 |
| --- | --- | --- |
| R01 脱敏、R02 覆盖 | 源码与回归通过 | 合成日志及性能报告回归，不是生产凭据泄露认证或原生性能测量 |
| R04 研究准备 | 工具与模板通过 | 校验／分母统计与 planned 模板；没有真实研究样本 |
| R07 服务隔离 | 回归与隔离浏览器检查通过 | 实际测试 HTTP／WS 服务与浏览器 fixture；不代表 JCEF 原生验收 |
| R05／R08／R10 入口与恢复说明 | 文档交付与检查完成 | 新实机演示待拍摄；没有新增崩溃／断电恢复能力 |
| R03／R06／R09 规划与看板 | 准备文档交付完成 | 准确最终包实测和原生采样仍待执行；历史证据未改写 |
