# ADR-0010：核心使用 Java，Blockbench 桥接使用 TypeScript

- 状态：已接受
- 日期：2026-08-16

MCreator 分支核心、工作区领域服务、第一方 MCP 与主要自动化测试使用 Java 25 和 Gradle；生成器延续 FreeMarker、YAML 与 JSON；JCEF 产品外壳使用 React 与 TypeScript；Blockbench 插件和桥接使用 TypeScript 并发布为 JavaScript。这个组合跟随各上游生态的原生语言，避免为了形式上的单语言而重写 MCreator 或 Blockbench 扩展层。

UI 重写不改变核心的 Java 所有权边界。React 前端不能直接操作文件系统或持有 Java 领域对象，只能调用带 Schema 的命令、查询和事件桥。MCP Java SDK 的 Jackson 依赖隔离在 MCP 模块，不触发 MCreator 核心从 Gson 迁移。Electron、Rust 和产品级 Python 运行时不进入首期正式架构。
