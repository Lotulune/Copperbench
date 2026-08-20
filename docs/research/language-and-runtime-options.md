# 语言与运行时选型调研

- 调研日期：2026-08-16
- 状态：技术组合已接受

## 已核查事实

MCreator 当前 `master` 的权威 `build.gradle` 使用 Java 25 toolchain。核心构建是 Gradle，并直接依赖 FreeMarker、Gson、JGit、FlatLaf、RSyntaxTextArea、JUnit 6 等 JVM 库。搜索摘要曾错误声称 Java 21，因此版本判断以仓库源码为准。

官方 MCP Java SDK 支持 Java 17 及以上，提供同步和异步服务端 API，以及 STDIO、SSE、Streamable HTTP 等传输；官方仓库公布了协议一致性测试结果。它与 Java 25 核心不存在 JVM 版本冲突，但默认 JSON 实现使用 Jackson，必须隔离在 MCP 模块内，不能迫使 MCreator 核心从 Gson 整体迁移。

现有 Blockbench MCP 插件使用 TypeScript 5、官方 TypeScript MCP SDK 与 Blockbench 类型定义，构建产物是可加载的 JavaScript 插件。Blockbench 桥接继续使用 TypeScript 比从 Java 重写其插件生态更合理。

## 推荐组合

| 范围 | 推荐技术 | 理由 |
| --- | --- | --- |
| MCreator 分支核心 | Java 25 + Gradle | 与当前上游一致，降低合并与插件兼容成本 |
| 产品外壳 | JCEF + React + TypeScript | 支持复杂工作台与可测试的 Web 交互，同时保留 Java 桌面宿主 |
| 生成器 | FreeMarker + YAML/JSON，必要逻辑使用 Java | 延续现有生成器结构并保持模板可审查 |
| 第一方 MCP | Java 25 + 官方 MCP Java SDK | 可直接调用工作区领域服务，不引入第二套运行时 |
| MCP 传输 | 首期本机 Streamable HTTP；测试与工具链保留 STDIO 适配器 | 方便多个外部客户端连接，同时保持传输可替换 |
| Blockbench 插件/桥接 | TypeScript，发布为 JavaScript | 符合 Blockbench 插件生态和现有 MCP 实现 |
| 自动化测试 | Java/JUnit 为主；协议一致性测试可调用官方 Node 测试工具 | 核心测试留在 Gradle，跨协议只在测试环境引入 Node |

## 不推荐

- 不允许 UI 技术选型迫使 MCreator 领域核心和生成器整体迁离 Java；产品外壳可以重写，但核心仍保持 JVM 边界。
- 不用 Rust 重写 MCP 或资产服务；当前没有性能、安全隔离或部署收益足以抵消 FFI 与双语言维护成本。
- 不把 Python 作为随产品分发的必需运行时；可以用于开发脚本，但不应成为正式功能依赖。
- 不为 MCP 引入完整 Spring Boot 应用，除非后续认证或远程服务需求证明核心 SDK 无法满足。

## 已确定的平台边界

- 首期仅正式支持 Windows 11 x64。Windows 10 不支持。
- Linux 与 macOS 暂不提供正式支持。
- 原版 Swing UI 插件仅在独立旧版插件界面中尽力兼容。
- 产品外壳自适应桌面窗口尺寸与 DPI，并提供完整 Windows 无边框窗口行为。

## 依据

- [MCreator 当前 build.gradle](https://github.com/MCreator/MCreator/blob/master/build.gradle)
- [MCreator 开发与许可说明](https://github.com/MCreator/MCreator/blob/master/README.md)
- [官方 MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- [MCP Java SDK 服务端文档](https://java.sdk.modelcontextprotocol.io/latest/server/)
- [Blockbench MCP package.json](https://github.com/jasonjgardner/blockbench-mcp-plugin/blob/main/package.json)
- [Blockbench MCP 使用说明](https://github.com/jasonjgardner/blockbench-mcp-plugin/blob/main/README.md)
