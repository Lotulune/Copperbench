# Copperbench Windows 预览版

这是面向开发者和测试者的未签名 Windows 11 x64 预览版，不是稳定 Beta。

## 下载与校验

本 Release 应同时包含 EXE、Portable ZIP、MSIX、`SHA256SUMS.txt`、`RELEASE-METADATA.json`、`product-status.json`、SPDX SBOM、标准 GPL 正文、附加条款和第三方声明。`RELEASE-METADATA.json` 记录精确源码提交；缺少任一二进制时不要使用该 Release。

Windows SmartScreen 可能因未签名而显示警告。请先校验 SHA-256，并确认来源为 `Lotulune/Copperbench`。

## 能力状态

- Windows 11 x64；Fabric / NeoForge 26.2、26.1.2、1.21.1、1.20.1。
- Block、Item、Recipe 为已验证第一方创作切片。
- Procedure、Function、Loot Table、Advancement、服务端、datagen 和 GameTest 为 Stage 9 开发预览。Function/Loot Table/Advancement 的八生成器黄金编译已通过；专用编辑器、性能、服务端、JCEF/可访问性和干净 Windows 11 门禁尚未关闭。
- 其他迁入的上游元素类型多数只读，可回退到旧版编辑器。

## 已知限制

- 没有 Authenticode 签名。
- 仅支持 Windows 11，不支持 Windows 10、Linux 和 macOS。
- 首次构建需要联网下载依赖；Gradle `--offline` 只适用于已有缓存的受验证轨道。
- MCP 列表仍有 200 项上限，SDK、Cursor 分页和批量原子计划尚未交付。
