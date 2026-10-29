# 贡献者一页入口：只准备本次改动需要的层

[贡献规范](../../CONTRIBUTING.md) · [完整开发环境](development-setup.md) · [CI 分流](../../CONTRIBUTING.md#how-ci-selects-checks)

先完成本次实现，再一起检查。无需先读历史验收记录或准备整套桌面环境；先用本次实际改动路径取得计划：

```powershell
python scripts/verify-changed.py --paths docs/user/first-creation.md --dry-run
```

将示例替换为本次全部改动的仓库相对路径。计划入口最低需要 **Python 3.11+、Node.js 22、Git**，复用 `scripts/ci/select_checks.py`，不会执行检查或安装依赖。`--base origin/main` 还会纳入当前工作树改动；多人共用脏工作树时，优先明确列出本次范围。

| 改动层 | 该层最低环境（计划入口之外） | 最短检查入口 | 完成标准 |
| --- | --- | --- | --- |
| 文档、链接、状态声明 | 无需 JDK、npm 依赖或显示会话 | `python scripts/verify-changed.py --paths docs/user/first-creation.md --run` | 所选链接、状态及仓库规则检查通过；版本和证据范围与原记录一致。 |
| 工具脚本 | 工具自身运行时；例如 Node 工具用 Node.js 22 | 定向例子：`node --experimental-strip-types --test scripts/tests/performance-statistics.tests.mjs` | 受影响边界与失败路径通过；工具 fixture 不计为产品或原生样本。未列入 CI allowlist 的工具路径会选全量检查，定向通过不消除计划中的缺项。 |
| 浏览器 UI（普通编辑器） | npm、`ui-core`／`ui-shell` 依赖；Chromium | `python scripts/verify-changed.py --paths ui-shell/src/components/ElementInspector.tsx --run` | 单元、一次生产构建、所选五个 smoke 通过；另运行覆盖本次交互但未在 smoke 内的现有 Playwright 用例。 |
| Python SDK | Python 3.11+ | `python scripts/verify-changed.py --paths sdk/python/copperbench.py --run` | SDK 与所选工具回归通过；真实 Core／桌面集成另记。 |
| TypeScript SDK | npm、`ui-shell` 依赖 | `python scripts/verify-changed.py --paths sdk/typescript/copperbench.ts --run` | SDK 与连接文件安全回归通过；此分流无需 UI 生产构建或 Chromium。 |
| Java、共享 schema、启动与构建配置 | JDK 25；原生外壳用 JBR 25 with JCEF；Node/npm、UI 依赖、Chromium、显示会话；Windows 用 PowerShell 7 | 定向 Java 例子：`./gradlew --no-daemon test --tests dev.copperbench.bridge.JcefWindowBridgeTransportTest`；公开 API 另需 `javadoc` | 本次相关 Java 检查及计划中的合同、平台检查通过。此类路径保守全选；缺少目标平台保持 blocked。 |
| 原生桌面、安装包、Minecraft | 对应目标包／JBR、平台和显示会话；所测生成器所需运行环境 | 按 [AGENTS.md](../../AGENTS.md) 通过既有 lifecycle／SDK 执行；首作使用[连续演示协议](../testing/first-creation-demo.md) | 记录准确包／源码身份、可见行为、日志和退出终态。编译、浏览器 fixture 或 SDK 回执不能替代这些观察。 |

Windows 的 Gradle 命令用 `.\gradlew.bat`，Linux 用 `./gradlew`。只有所选层需要依赖时，才运行 `npm ci --prefix ui-core`／`npm ci --prefix ui-shell`；Chromium 按[开发环境](development-setup.md#日常验证)准备。普通 UI allowlist 之外的新组件、原生桥接、共享 schema 等仍按完整计划执行。

审阅计划并补齐所需环境后，对同一组路径执行一次：

```powershell
python scripts/verify-changed.py --paths docs/user/first-creation.md --run --output output/contributor-validation/my-change-01
```

Gradle 任务可能下载依赖，执行时需明确加 `--allow-downloads`。输出目录必须新建；报告保留命令、退出码、日志、源码摘要和可取得的计数。退出码 `0` 表示本次执行通过（纯计划也为 `0`），`1` 为失败／超时，`2` 为环境或手工检查缺项。完整合同见[验证入口](../../scripts/performance/README.md#contributor-verification)。

**不重复构建：**表中的定向命令用于定位受影响检查，已由计划执行的检查不再手工重复。可运行的 Gradle `test → processResources → buildUiShell` 已包含生产 UI 构建；同一份输入无需紧接着再跑 `npm run build --prefix ui-shell`。只跑前端时，计划会单独构建一次。

提交说明写清改动结果、实际命令／退出码、未完成项。通过所选检查后停止；只有修改、失败或具体未解问题才需要复验。提交、推送与发布沿用[贡献规范](../../CONTRIBUTING.md)，本入口不会执行它们。
