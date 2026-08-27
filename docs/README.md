# 项目文档导航

已关闭能力读根目录 [PRD](../PRD.md)。下一阶段交付读 [PRD-NEXT.md](../PRD-NEXT.md)。本目录保存开发、用户、AI、ADR、架构和阶段证据。

## 从这里开始

1. 已关闭能力：根目录 [PRD](../PRD.md)。
2. 下一阶段可信预览发布：根目录 [PRD-NEXT.md](../PRD-NEXT.md)；历史状态索引 [剩余完善清单](./remaining-work.md)。
3. 术语存在歧义时查询 [领域词汇表](../CONTEXT.md)。
4. 只有遇到专项问题时，才从本页选择对应附件。

## 决策记录

- [ADR 0001：以 MCreator 为产品基础](./adr/0001-fork-mcreator-as-the-product-base.md)
- [ADR 0002：Fabric 为主、NeoForge 为辅](./adr/0002-support-fabric-and-neoforge-with-fabric-first.md)
- [ADR 0003：单活动生成器与复制式迁移](./adr/0003-use-one-active-generator-per-workspace.md)
- [ADR 0004：四条 Minecraft 版本轨道](./adr/0004-maintain-four-minecraft-version-tracks.md)
- [ADR 0005：第一方 MCP 控制面](./adr/0005-ship-a-first-party-mcp-control-plane.md)
- [ADR 0006：上游工作区与插件兼容](./adr/0006-preserve-upstream-workspace-and-plugin-compatibility.md)
- [ADR 0007：继承资源包工具并集成 Blockbench](./adr/0007-reuse-resource-pack-tools-and-integrate-blockbench.md)
- [ADR 0008：本地 Git 工作区历史](./adr/0008-use-local-git-for-workspace-history.md)
- [ADR 0009：手写源码作为高级出口](./adr/0009-support-manual-source-as-an-advanced-exit.md)
- [ADR 0010：Java 核心与 TypeScript 边界](./adr/0010-use-java-for-core-and-typescript-for-blockbench.md)
- [ADR 0011：重写自适应无边框产品外壳](./adr/0011-replace-the-upstream-user-interface.md)
- [ADR 0012：内置并维护 Fabric 生成器](./adr/0012-bundle-and-own-the-fabric-generator.md)
- [ADR 0013：基于 MCreator 稳定标签发布](./adr/0013-release-from-upstream-stable-tags.md)
- [ADR 0014：Windows 首发与离线优先](./adr/0014-target-windows-and-operate-offline-first.md)
- [ADR 0015：GitHub 未签名 GPL 衍生版](./adr/0015-github-unsigned-gpl-fork.md)

## 专项设计

- [系统架构总览](./architecture/system-overview.md)
- [UI 与 Java Core 合同](./architecture/ui-core-contract.md)
- [上游插件兼容架构](./architecture/plugin-compatibility.md)
- [MCP 与自动化权限模型](./security/permission-model.md)
- [Fabric 1.21.1 纵向能力清单](./compatibility/fabric-1211-vertical-slice.md)
- [阶段 3 桥接集成准备](./handoffs/stage-3-bridge-readiness.md)
- [阶段 3 G2/G3 验证记录](./testing/stage-3-g2-g3-2026-08-17.md)
- [阶段与发布门禁](./testing/release-gates.md)
- [阶段 10 可信预览与 Stage 9 收口](../PRD-NEXT.md)
- [剩余完善清单](./remaining-work.md)
- [UI 重写交接简报](./handoffs/ui-rewrite-brief.md)
- [zcode UI-Core v0.1 交接](./handoffs/zcode-ui-core-v0.1.md)
- [阶段 0 基线执行记录](./testing/stage-0-baseline-2026-08-16.md)
- [Windows 干净构建基线](./build/windows-clean-build.md)

## 开发与发布

- [开发环境](./build/development-setup.md)
- [Windows 干净构建基线](./build/windows-clean-build.md)
- [Windows 预览版发布流程](./build/release-process.md)
- [预览版说明模板](./releases/preview-template.md)

## 用户与 AI

- [快速开始](./user/getting-started.md)
- [开发测试版使用说明](./user/README.md)
- [故障排查](./user/troubleshooting.md)
- [MCP 接入快速开始](./ai/getting-started.md)
- [AI SDK 与评测](../sdk/README.md)
- [AI 协议规则](../sdk/protocol.md)

## 调研依据

- [MCP 生态与可复用项目](./research/mcp-landscape.md)
- [MCreator 资源包现有能力](./research/mcreator-resource-pack-capabilities.md)
- [语言与运行时选择](./research/language-and-runtime-options.md)
- [Fabric Generator 采用方案](./research/fabric-generator-adoption.md)

## 执行前确认

[执行期未决事项](./open-decisions.md)仅记录必须基于原型、固定提交或实际兼容测试才能确定的内容。已由 ADR 固化的决策不得在任务实现中被隐式改写。下一阶段实现以 [PRD-NEXT.md](../PRD-NEXT.md) 为准。
