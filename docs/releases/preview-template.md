# Copperbench Windows 预览版

这是面向开发者和测试者的未签名 Windows 11 x64 预览版，不是稳定 Beta。

## 下载与校验

本 Release 应同时包含 EXE、Portable ZIP、MSIX、`SHA256SUMS.txt`、`RELEASE-METADATA.json`、`product-status.json`、SPDX SBOM、标准 GPL 正文、附加条款和第三方声明。`RELEASE-METADATA.json` 记录精确源码提交；缺少任一二进制时不要使用该 Release。

Windows SmartScreen 可能因未签名而显示警告。请先校验 SHA-256，并确认来源为 `Lotulune/Copperbench`。

## 能力状态

- Windows 11 x64；Fabric / NeoForge 26.2、26.1.2、1.21.1、1.20.1。
- Block、Item、Recipe 为已验证第一方创作切片。
- Procedure、Function、Loot Table、Advancement、datagen 和 GameTest 仍属于 Stage 9 开发预览范围；Function/Loot Table/Advancement 的专用编辑器和八生成器黄金编译已通过。Fabric / NeoForge 26.2、26.1.2、1.21.1、1.20.1 的真实 dedicated-server readiness 已 8/8 通过。仍未关闭的发布门禁包括大工作区/Procedure 性能、真实 JCEF/可访问性、最终 Windows 11 RC 完整矩阵重放和外部测试者。
- 其他迁入的上游元素类型多数只读，可回退到旧版编辑器。

## 已知限制

- 没有 Authenticode 签名。
- 仅支持 Windows 11，不支持 Windows 10、Linux 和 macOS。
- 首次构建需要联网下载依赖；Gradle `--offline` 只适用于已有缓存的受验证轨道。
- MCP/SDK 已提供游标分页与批量原子工作区计划；最终发布资格以随包 `product-status.json` 的 beta-blocking 门禁为准。
