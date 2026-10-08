# Stage 18 首批实现与验证

日期：2026-10-09（Asia/Tokyo）
需求入口：[PRD-STAGE-18.md](../../PRD-STAGE-18.md)
开发基线：main@2bfbb48e80dac267b2043ffcbfc795a0bdd931a3

## 状态

首批代码已实现，本地针对性检查与第一轮真实 Native mod 回归通过。第一轮常规CI发现遗漏的中文诊断消息；已经补齐三种参数形状的中英词条并通过本地UI生产构建，正在集成修复后的同源CI。Java测试、Javadoc、完整UI与Windows MCP是否通过以该轮实际结果为准。Stage 18 整体仍未关闭。

## 基线变化

上次评估基于 b1a69d2；其后 PR97 已修复短超时测试启动预算，并更新全量UI回归。新基线的[常规CI](https://github.com/Lotulune/Copperbench/actions/runs/37813145564)与[Linux candidate](https://github.com/Lotulune/Copperbench/actions/runs/37813145670)通过。本批保留该修复，不重复记为本批贡献。

## 首批范围

| 子项 | 实现 | 验证 |
| --- | --- | --- |
| S18-01 item/recipe局部契约、未知契约稳定错误 | item 25字段、recipe 29字段，显式partial；缺失、未知和畸形契约使用稳定错误 | Python侧通过；实际Core发现/创建/拒绝/重开等待Java CI与Native mod回归 |
| S18-02 Native诊断参数安全渲染 | 简单具名参数只替换一次，保留原始code/details | Native SDK 21/21、完整Python SDK 43/43通过；先在旧实现复现4 failures + 4 errors |
| S18-03 生成冲突路径与拒绝不写 | typed原因、安全相对路径、多条诊断、可用Java预览、归属预检先于include文件恢复 | 静态检查通过；3个Java测试类与真实混合工作区拒绝等待CI |
| S18-04 独立Nightly与红灯汇总 | 5个独立产品suite与聚合；保留8轨道generator矩阵 | Nightly/协议契约16/16通过，包含25种异常聚合子场景；完整Nightly尚未运行 |
| S18-05 协议scope、严格真实mod回归 | 协议fixture明确非模型/真实build；真实mod独立工作流，关键probe缺失/失败/未验证均为红灯 | manifest通过；严格门槛7/7通过；本批mod构建、修复、12项GameTest与可信导出等待CI |

## 本地验证

- `python -m unittest discover -s sdk/python -p 'test_*.py'`：43项通过。
- `python -m unittest discover -s scripts/tests -p 'test_nightly_and_eval_contracts.py'`：16项通过；覆盖独立job图、失败/取消/跳过/缺失状态的聚合，以及协议scope兼容字段。
- `python -m unittest discover -s scripts/tests -p 'test_chronometer_regression.py'`：7项通过；逐项缺少必需probe、错误phase、失败或未验证状态均不能通过；旧revision不替代成功查询；必需证据收集失败撤销completed状态。
- `node scripts/verify-ai-evals.mjs`：10个既有case ID、16项observed checks与10个legacy labels通过。
- `node scripts/verify-markdown-links.mjs`、`git diff --check`：通过。

本地Java运行时不可用，不把已添加的Java测试记为通过；后续以CI日志及实际产物为准。

## 集成发现与修复

首个远端提交为 `de4c794ab43b839793438bf333bcc59250a84f8b`，评审入口：[PR98](https://github.com/Lotulune/Copperbench/pull/98)。

- [真实Native mod回归 37818611410](https://github.com/Lotulune/Copperbench/actions/runs/37818611410)成功：46项必需probe全部通过，包括实际字段发现、可读错误、带路径归属拒绝且revision/源码不变、独立原生副本构建、注入编译错误与修复重建、打包后GameTest、导出字节哈希及重开。该结果对应上述首个提交，后续修复另行复测。
- [常规CI 37818611368](https://github.com/Lotulune/Copperbench/actions/runs/37818611368)的UI schema 35/35通过，随后UI构建发现缺少 `diagnostic.generation_source_conflict` 的中文词条。生产Java编译成功，但此job中的Java测试与Javadoc未执行；报告步骤显示success但明确没有JUnit结果，不能计为测试通过。
- [Linux candidate 37818611327](https://github.com/Lotulune/Copperbench/actions/runs/37818611327)的打包步骤也在同一个i18n检查失败。
- 修复时为通用无参数、仅原因、路径加原因分别使用对应message key，补齐中英词条；6/6本地化回归通过。CI同款 `npm run build --prefix ui-shell` 本地成功，包含392/392中文key、1696条英文消息校验、TypeScript编译和Vite生产构建。

保留本次失败记录；后续成功结果不覆盖这次真实发现。

## 已知边界

当前会话没有可用桌面Minecraft客户端与minecraft-control连接。本批不声称安装版桌面、客户端输入/渲染、音效、玩家实际合成、完整世界保存重进、外部用户或冷缓存默认网络体验通过。

完整类型/轨道发现、完整生成预检、安装体验、职责重构与外部试用按PRD后续批次继续。
