# Stage 18 Nightly 与协议契约评估范围

需求：**S18-04（持续回归独立性）**、**S18-05（评估范围标识）**。

## Nightly 独立套件

[Nightly product gates](../../.github/workflows/nightly.yml) 将产品回归分成五个独立 Windows job。某个套件失败后，其他套件仍可产生自己的结果与证据；`Nightly product regression` 汇总 job 只在五项均为 `success` 时通过。`failure`、`skipped`、`cancelled` 和缺失结果都使汇总失败。工作流权限、每日调度和运行并发规则保持原有范围。

| 独立套件 | 执行范围 | 证据 artifact |
| --- | --- | --- |
| Java/Javadoc/scale | 原完整 Java 测试、Javadoc、`copperbench.stage9.scale=true`；Gradle `--continue` 仅继续没有失败依赖的任务，仍返回失败退出码 | `nightly-java-regression` |
| SDK/production bridge | Windows Python SDK、production bridge、TypeScript SDK；Node 测试依赖自己的 npm 安装结果 | `nightly-sdk-regression` |
| UI | UI-Core contract、一次独立生产构建、现有 `chromium` 项目的完整 Playwright 范围；生产构建失败时不运行 Playwright | `nightly-ui-regression` |
| MCP | 先独立编译 test host，再执行现有 MCP conformance；编译失败时不启动 host | `nightly-mcp-regression` |
| Repository | Nightly 结构与 eval 范围回归、product status、Markdown links、eval manifest 校验 | `nightly-repository-checks` |

各套件保留 30 天证据，失败后仍执行 artifact 上传。原 `nightly-product-regression` artifact 保存五套件的机器可读 `summary.json`，完整日志与原始报告在各自的套件 artifact 中。八轨 generator golden 继续单独报告，保留 `fail-fast: false`、`max-parallel: 2`、每轨 90 分钟超时、Java 21 sidecar、JBR 25/JCEF、原构建参数及诊断目录。

这一调整增加了 MCP test host 的独立编译成本，避免 Java 测试失败阻断 MCP 验证。MCP 的冷编译发生在既有 180 秒 host 启动等待之前。UI job 使用自己的生产构建；Java/MCP job 仍通过 Gradle `processResources → buildUiShell` 准备其所需资源。

## 现有 AI live eval 的准确含义

`scripts/run-ai-live-evals.py` 与 `scripts/verify-ai-live-evals.ps1` 使用真实 loopback HTTP MCP 会话，host 是测试源中的 `McpConformanceServerMain`。该 host 连接 `InMemoryWorkspaceTaskGateway`，工作区 mutation gateway 为 `noOp()`。因此这十项结果属于 **MCP 协议契约评估**：它们证明 SDK 请求、响应、权限和 fixture 状态转换可经过真实 HTTP 边界执行。

`scope.kind` 固定为 `protocol-contract`，同时声明 host、task gateway、mutation 范围，以及以下布尔字段：`modelDriven`、`realBuild`、`repairLoop`、`transportReconnect`、`gameplay` 均为 `false`。这些是可以由脚本校验的边界；单纯将 manifest 中的任一字段改为 `true` 会使校验失败。

为保持已有消费方兼容，manifest 的 `schemaVersion: "1.0"`、suite 标识、十个 case ID、`covers` 和计数字段保留。`covers` 与 case ID 是历史兼容标识，**展示与覆盖解读应使用新增的 `displayName`、`scope` 和 `observedCoverage`**。每个 profile 报告继续提供 `mode`、`passed`、`failed`、`cases`；总报告继续提供数值型 `cases: 10`、`passed`、`failed`，并附带相同 scope。

| 保留的 case ID | 实际观察范围 |
| --- | --- |
| `create-element` | fixture 工作区中的元素创建接受与 revision 更新 |
| `procedure-edit` | fixture 中的 procedure 修改 |
| `rename-reference` | fixture registry entry 重命名 |
| `build-repair` | 构建任务 accepted、running 状态可查询、cancelled；没有执行 Gradle 构建或修复循环 |
| `revision-conflict` | 过期 revision 写入被拒绝 |
| `readonly-denial` | 只读会话 mutation 被拒绝 |
| `datagen-cancel` | fixture datagen 任务接受与取消 |
| `datagen-publish` | fixture datagen 预览、manifest hash 形状检查与发布 |
| `recovery-restore` | recovery point 创建与未经批准 restore 的拒绝 |
| `task-reconnect` | 同一客户端、同一会话内重复调用 `get_task`；没有断开或重建连接 |

旧 case ID 不证明对应名称所暗示的完整产品能力。真实 mod 生成、Gradle 编译、修复后重新构建、连接恢复和游戏内验收需要分别记录实际执行证据。既有历史结果的源码、时间与执行范围仍以各自记录为准。

## 验证命令与证据界限

无需 Java 的本地结构与元数据检查：

```sh
python -m unittest discover -s scripts/tests -p 'test_nightly_and_eval_contracts.py'
node scripts/verify-ai-evals.mjs
node scripts/verify-markdown-links.mjs
```

回归测试会真正执行 Nightly 汇总脚本，分别注入每个套件的失败、跳过、取消、缺失和空结果，确认汇总保持失败且仍写出五项结果；也检查八轨及原检查范围。eval 测试用受控客户端响应验证兼容报告、错误计数和覆盖标识，并对伪报真实 build、repair、reconnect、gameplay 或 model-driven coverage 的 manifest 执行拒绝测试。这些测试不启动 Java/MCP host。

在已准备 JDK 25/JCEF、Node、Python 3.11+ 与 PowerShell 7 的 Windows 开发环境中执行实际 HTTP 契约 harness：

```powershell
.\gradlew.bat --no-daemon testClasses
pwsh -NoProfile -File ./scripts/verify-ai-live-evals.ps1 -OutputDirectory build/ai-live-evals
```

期望结果为 workspace profile 9 项、read-only profile 1 项。JSON 报告带有明确 scope，失败仍返回非零；输出目录仅收集报告与 server 日志，不复制连接文件中的 token。Nightly 的 repository job 执行上述结构/元数据检查；现有 MCP conformance 和这项十案例 HTTP harness 是独立命令，其结果应分别报告。
