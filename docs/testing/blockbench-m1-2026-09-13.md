# Blockbench M1：连接就绪基础执行记录

日期：2026-09-13。需求：[PRD-BLOCKBENCH.md](../../PRD-BLOCKBENCH.md) BB-01、BB-02 首轮引导、BB-05。

## 交付

- [BlockbenchEnvironmentService](../../src/main/java/dev/copperbench/assets/BlockbenchEnvironmentService.java)：本地安装检查，显式 HTTP/SSE MCP initialize/tools-list 探测，独立编辑器/服务状态，本机地址校验、拒绝重定向/凭据 URL、无 Copperbench 凭据转发、并发限额与超时。
- [MCP 工具目录](../../src/main/java/dev/copperbench/mcp/McpToolCatalog.java)：暴露 `get_blockbench_environment`，复用 Core query/权限/审计，明确托管建模任务尚未实现。
- [连接面板](../../ui-shell/src/components/BlockbenchSetupPanel.tsx)：资产中心（包括空资产状态）与 AI 设置按需展开；重新检测、测试连接、复制官方下载/社区说明/连接地址。浏览器预览明确无法探测本机服务。
- [桌面 Core 桥接](../../src/main/java/dev/copperbench/bridge/JcefCoreBridgeTransport.java)：该查询在后台完成，避免等待 MCP 时阻塞 Chromium 消息线程。
- [受管编辑器](../../src/main/java/dev/copperbench/assets/BlockbenchProcessService.java)：产品启动后安装 Blockbench，下一次打开资产重新定位，无需沿用启动时的空路径。
- [用户文档](../user/blockbench-setup.md)与[第三方通知](../../compliance/THIRD_PARTY_NOTICES.md)：独立安装、GPL 来源、社区身份、当前能力和后续分发义务。

## 自动验证

| 范围 | 结果 |
| --- | --- |
| BlockbenchEnvironmentServiceTest | 7 通过；真实本机 HTTP/SSE 测试服务器，覆盖 opt-in、握手、工具发现、认证失败、无工具、错误 HTTP、超时、拒绝重定向、关闭端口、敏感信息不转发 |
| BlockbenchProcessServiceTest | 10 通过、1 跳过；包括产品启动后新增安装、已有租约/生命周期回归 |
| BlockbenchEditApplicationServiceTest | 1 通过；恢复点、文件变化及版本登记回归 |
| JcefCoreBridgeTransportTest | 9 通过；包括后台查询、既有作用域与消息处理 |
| JcefBlockbenchBridgeTransportTest | 2 通过 |
| McpHttpServerTest | 7 通过；包括通过实际认证 HTTP MCP 会话发现及调用新增只读工具，revision 不变、无恢复点写入 |
| UI-Core schemas | 23 通过；新增 query 参数类型和额外执行参数拒绝 |
| Playwright：安装引导＋资产中心＋MCP 页面 | 44 通过，1920×1080 与 1366×768 |
| 安装引导最终截图复测 | 4 通过；检查 1366×768 截图，键盘展开、地址编辑清除旧状态、可滚动内容与无横向溢出 |
| UI 生产构建、中文检查、Markdown 本地链接、diff 空白检查 | 通过 |

最终 Java 选择集共 37 项：36 通过、1 跳过、0 失败。跳过的是需要显式本机安装配置的真实 Blockbench 启停测试，不能算成通过。

复现命令：

```powershell
pwsh -NoProfile -File ./scripts/run-gradle-external.ps1 test --tests dev.copperbench.assets.BlockbenchEnvironmentServiceTest --tests dev.copperbench.assets.BlockbenchProcessServiceTest --tests dev.copperbench.core.BlockbenchEditApplicationServiceTest --tests dev.copperbench.bridge.JcefCoreBridgeTransportTest --tests dev.copperbench.bridge.JcefBlockbenchBridgeTransportTest --tests dev.copperbench.mcp.McpHttpServerTest --console=plain
npm test --prefix ui-core
```

在 `ui-shell` 目录运行：

```powershell
npx playwright test e2e/blockbench-setup.spec.ts e2e/asset-browser.spec.ts e2e/mcp-runtime.spec.ts --project=chromium --project=compact-1366
```

普通 Gradle 启动遇到仓库已记录的 Windows Java NIO loopback 问题，改用已有 `run-gradle-external.ps1` 后通过。未修改系统代理、网络、全局 Java 配置或 CI。最终 Gradle 原始日志位于本机 `.tmp/gradle-external/0780ef24d50946bf950090262f880bfe.log`；XML 在 `build/test-results/test/`，均为本次本地执行产物。

## 证据边界与后续

- 本轮在未提交的开发工作区完成，既有及并行的原生 Python API 改动被保留；不把它们列为本 PRD 交付。没有创建提交、推送或发行包。
- MCP 探测使用真实 HTTP 协议测试服务器；没有安装或启用社区插件，没有验证某个社区插件发布版本，也没有操作真实用户模组。
- UI 为 Playwright 开发预览和桥接测试；不是已安装 JCEF 产品、Windows/Ubuntu 干净机或游戏内证明。
- M2 下一步：新建/已有模型任务、编辑副本及依赖纹理处理、源版本冲突、无需关闭编辑器的完成入口、安装路径选择。
- M3/M4 后续：多文件回导和失败恢复、真实插件试作、元素关联、打包与游戏视觉验证。当前不宣称完整 Agent 建模闭环已完成。
