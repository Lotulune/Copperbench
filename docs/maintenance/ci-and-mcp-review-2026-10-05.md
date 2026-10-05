# CI 复核与 MCP 权限选择 — 2026-10-05

## 结论

CI 优化方向合理，建议保留按改动层选择检查的方案，补齐失败诊断和显示回归即可；暂不继续扩大跳过规则或拆分工作流。本轮同时完成桌面 MCP 权限直接选择。下面的初次验证记录对应本地工作树，不代表安装包发布或验收；后续整合范围见本节末尾。

## 评估基线

- 本地工作树基于 `c0e601d0`，包含用户已有的界面和 Minecraft 控制桥等修改。本轮保留这些改动。
- 用户的 CI 优化位于尚未合并的 [PR #88](https://github.com/Lotulune/Copperbench/pull/88)，核对提交为 `f4956f65d1d033f11806a8bca3f6361d107e4c7d`。不能把本地旧 workflow 当成该优化版本。
- 该提交的 [Build and test](https://github.com/Lotulune/Copperbench/actions/runs/37220599382) 成功：检查选择、Java/Javadoc、UI 和 MCP 均通过。本次读取的是 GitHub 实际运行记录与源码，没有重新触发 Actions。
- [最近一次 main CI](https://github.com/Lotulune/Copperbench/actions/runs/37230563253) 在 `a860d1a8` 成功；[最近一次 Nightly](https://github.com/Lotulune/Copperbench/actions/runs/37229367278) 使用的是更早的 `a6e00e9f`。这些记录不能替代 PR #88 合并后的全量验证。

## CI：建议小范围补齐

现有优化已经处理了 PR 按层路由、未知路径全量、选择器失败阻断、required check 名称保持、重复触发、重复 UI 构建、SDK 回归和 Windows 文件边界检查。源码入口为 [检查选择脚本](https://github.com/Lotulune/Copperbench/blob/f4956f65d1d033f11806a8bca3f6361d107e4c7d/scripts/ci/select_checks.py) 与 [PR workflow](https://github.com/Lotulune/Copperbench/blob/f4956f65d1d033f11806a8bca3f6361d107e4c7d/.github/workflows/test.yml)。当前完整改动集触发了全量任务，没有足够的同条件数据来量化路由优化的提速幅度。

| 优先级 | 具体建议 | 依据与价值 |
| --- | --- | --- |
| P1 | 将 MCP runtime 回归加入前端快测，或按变动选择相关 E2E spec | 优化后的快测仍只执行四个固定 spec；新增权限选择的浏览器回归需等 Nightly 才会执行。后端权限测试已在普通 Java `test` 范围内。 |
| P2 | 保持零重试，把 trace 改为失败保留，例如 `retain-on-failure` | [Playwright 配置](../../ui-shell/playwright.config.ts) 同时设为 `retries: 0` 与 `trace: 'on-first-retry'`，因此普通失败不会生成首次重试 trace。 |
| P2 | 给 Nightly 增加现有 compact 与 visual 项目 | [Nightly](https://github.com/Lotulune/Copperbench/blob/f4956f65d1d033f11806a8bca3f6361d107e4c7d/.github/workflows/nightly.yml) 只指定 `chromium`；该项目排除了 `visual-matrix.spec.ts`。现有 1366 小窗口与四档 DPI 项目不会因此运行。保持这些显示回归在 Nightly，不增加每个普通 PR 的重型任务。 |
| P2 | 文档校验只枚举受版本管理或未忽略的源文档 | [链接检查脚本](../../scripts/verify-markdown-links.mjs) 计算了 Git 文件集合，却仍递归遍历忽略目录中的 Markdown。本轮本地执行因已有 `output/` 与控制桥 `.venv/` 中的断链失败；不能将干净 CI 的成功等同于开发工作树下也能稳定复现。 |

初次评估时 PR #88 落后于最新 main，因此建议更新后重跑现有检查再合并。用户随后授权提交并一起合并：整合分支已纳入 `main@a860d1a8`，保留 PR #88 的可靠性修复与 CI 路由，并将 `mcp-runtime.spec.ts` 加入前端快测。其余 CI 建议仍为后续工作；最终提交的云端检查结果以 PR #88 的 Actions 记录为准。

## 已实现：直接选择 MCP 权限

- 「AI 与 MCP」页可直接选择只读、工作区、完全访问，不增加确认弹窗或保存按钮。服务端确认成功后才显示新档位。
- 切换同时替换 HTTP 鉴权与 Core 权限上下文，保留当前 URL，关闭旧会话并吊销全部旧令牌，包括续期重叠令牌。客户端用新令牌重新初始化。
- 当前档位重复点击不重建服务；切换期间阻止重复提交，失败时显示错误并允许重新选择。
- 设置保存在当前工作区 `.copperbench/mcp-settings.json`，重开继续使用；新工作区默认 Workspace。配置不含令牌，配置损坏时服务不启动。
- MCP 工具目录不包含提权工具；完全访问仍遵守现有受保护操作确认。
- 页面与状态栏同步；停服时显示未启动，旧令牌从页面清除。紧凑窗口中三个选项初始均可见。

主要实现：[桌面运行时](../../src/main/java/dev/copperbench/mcp/DesktopMcpRuntime.java)、[原生桥](../../src/main/java/dev/copperbench/bridge/JcefMcpBridgeTransport.java)、[权限界面](../../ui-shell/src/components/AIControlView.tsx)。使用说明已补入[权限模型](../security/permission-model.md)。

## 实际验证

| 验证 | 结果 |
| --- | --- |
| `DesktopMcpRuntimeTest`、`JcefMcpBridgeTransportTest`、`McpHttpServerTest` | 17/17 通过，无跳过；包含真实 loopback HTTP 初始化、旧令牌和会话失效、只读拒写、恢复写入、完整权限投影、受保护操作仍拒绝、设置持久化与失败恢复 |
| Playwright：MCP runtime 与 permission-denied 场景，`chromium`、`compact-1366` | 16/16 通过，覆盖直接选择、状态同步、离线、错误、键盘、重复点击、中英文和亮暗主题；已查看实际截图 |
| `npm run build --prefix ui-shell` | TypeScript、中文/英文检查与生产构建通过 |
| 相关 diff 检查 | 使用 `git -c core.whitespace=cr-at-eol diff --check` 通过，保留仓库原有换行 |
| 文档链接 | 本轮两份文档的本地目标检查通过；全库脚本因上述既有输出/依赖目录的断链失败，未修改或清理用户数据 |

Java 验证使用仓库 JBR 25。初次 Gradle 启动遇到本机已有 Windows loopback 问题，按现有兼容代码设置当前验证进程的 `jdk.net.unixdomain.tmpdir` 后完成；未更改全局配置。Java 输出在 `build/mcp-permission-tests.log`，JUnit XML 在 `build/test-results/test/`，截图归档在本机 `output/mcp-permissions-2026-10-05/`。

本轮没有重新验证安装包里的 JCEF 操作，也没有运行 Minecraft 游戏验收；本次改动不涉及游戏玩法或生成器。尚不能用上述结果宣称新安装候选已经通过 Desktop MCP 产品验收。

## 后续项目改进优先级

1. **整合已验证的可靠性修复。** PR #88 同时修复文件边界、SDK 写请求重放与任务状态乱序，应先完成合并验证，避免只关注 UI 上的新入口。
2. **完善 MCP 接入和恢复。** 在现有监听状态、一次性令牌和复制配置基础上，增加连接自检与明确的令牌重新签发入口。用户丢失已显示令牌时，应能恢复接入，无需切换权限或重开工作区。
3. **减少真实宿主与浏览器夹具的偏差。** 将本次权限切换、原生状态、客户端重新初始化与实际读写结果纳入安装版冒烟验收；历史功能保存问题也说明仅靠 mock 成功不够。
4. **按测量结果优化启动。** 当前生产构建入口约 821 kB、Procedure 块约 899 kB（压缩前），已有分包提示。先测安装版冷启动与编辑器首次打开耗时，再决定拆包；本轮没有测得性能退化。

导航里的「设置」已经接到桌面宿主，浏览器缺少原生能力时才禁用，不能把它作为另一个尚未实现的功能。
