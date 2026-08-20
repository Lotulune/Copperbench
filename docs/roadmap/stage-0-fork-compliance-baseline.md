# 阶段 0：分支、许可与可复现基线

- 状态：本机实现完成，G0 按 Windows 11 证据关闭。Windows 10 已移出支持范围，不再等待 Win10 复验。产品 Git 初始化仍需授权。
- 依赖：无
- 对应门禁：G0

## 目标

建立可以合法分发、从干净 Windows 环境重复构建、能够持续吸收上游更新的产品基线。该阶段不开发产品功能。

## 范围

- 选择并固定 MCreator 上游稳定标签。
- 建立上游集成分支与产品发布分支规则。
- 固定 Fabric Generator 的初始提交和目标分支许可快照。
- 审计 GPL-3.0、Section 7 例外、第三方许可证、Minecraft mappings 条款和 Fabric 来源。
- 移除或替换 MCreator/Pylo 名称、Logo 和不在 GPL 范围内的品牌资产。
- 建立 Java 25、Gradle、JCEF 和 Windows 本机组件的干净构建流程。
- 记录当前上游测试、打包和启动行为作为回归基线。

## 不在范围

- UI 重写。
- 生成器功能修改。
- MCP、版本历史或 Blockbench 集成。
- 发布到任何外部平台。

## 交付物

- 上游来源、标签、提交和本地修改策略文档。
- Fabric Generator 来源与许可证据包。
- 第三方通知、品牌替换清单和源代码分发说明。
- Windows 干净构建与启动脚本说明。
- 基线测试结果、依赖清单和已知构建问题。
- 上游同步演练记录：导入一个后续上游提交并证明可以回退。

## 关键任务

1. 从稳定标签建立产品基线，不从滚动 `master` 发布。
2. 为所有外部来源记录 URL、提交 SHA、许可证和修改日期。
3. 搜索品牌名称、Logo、下载地址、遥测、更新服务和默认网络调用。
4. 在新 Windows 用户环境验证克隆、构建、测试、启动和打包。
5. 固定构建工具版本，并确保失败时输出可诊断日志。

## 风险

- Fabric 插件页与仓库许可证元数据不一致。缓解：以固定提交实际文件和文件头为准，并保留证据。
- 品牌资源可能散布在代码与二进制资产中。缓解：建立自动扫描和人工启动检查。
- Java 25/JCEF 打包可能依赖开发机缓存。缓解：使用干净环境和断缓存重建。

## 退出条件

- [x] G0 按 Windows 11 本机证据关闭。Windows 10 已不支持，不再作为条件项。
- [x] 产品可在 Windows 11 x64 隔离环境构建、测试、打包并从发行目录启动。
- [x] 无受限制的 MCreator/Pylo 品牌资产进入产品 ZIP；23 个替换资产有哈希锁和自动扫描。
- [x] ZIP、NSIS、MSIX、主 JAR、Fabric 插件与 JBR 运行时可追溯到固定源提交、内容树和许可证。
- [x] 上游集成与产品发布分支职责有书面规则。

## 当前证据

- 固定来源与分支规则：[`UPSTREAM.md`](../../UPSTREAM.md)
- 合规基线：[`compliance/`](../../compliance/README.md)
- Windows 执行记录：[`stage-0-baseline-2026-08-16.md`](../testing/stage-0-baseline-2026-08-16.md)
- 机器可读门禁结果：[`windows-11-baseline.json`](../../evidence/stage-0/2026-08-16/windows-11-baseline.json)
- 产品产物哈希：[`product-artifacts.lock.json`](../../evidence/stage-0/2026-08-16/product-artifacts.lock.json)
- 发行启动结果：[`copperbench-windows-11-startup.json`](../../evidence/stage-0/2026-08-16/copperbench-windows-11-startup.json)
- JBR 运行时锁：[`jbr25-windows-runtime-lock.json`](../../evidence/stage-0/2026-08-16/jbr25-windows-runtime-lock.json)

本机 G0 工作已闭环：Copperbench 临时身份、产品源码、去品牌、离线默认、6 项基础测试、29 项 UI 集成测试、ZIP/NSIS/MSIX 和真实发行启动均通过。2026-08-20 起 Windows 10 移出支持范围，不再作为 G0 外部条件。仍待用户授权的是产品 Git 初始化与书面分支拓扑。Fabric 旧工作区转换与生成器 Gradle 同步问题属于 G1/G2 兼容矩阵，不用于降低或伪造 G0 结果。
