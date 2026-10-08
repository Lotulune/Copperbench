# Stage 18 首批实现与验证

日期：2026-10-09（Asia/Tokyo）
需求入口：[PRD-STAGE-18.md](../../PRD-STAGE-18.md)
开发基线：main@2bfbb48e80dac267b2043ffcbfc795a0bdd931a3

## 状态

首批代码已实现，本地可运行的针对性检查通过；Java、Javadoc、真实源码运行时与打包后的服务端 GameTest 等待本批 CI。Stage 18 整体仍未关闭。本记录在每项实际验证后更新，保留源码、协议、服务端与桌面的范围区别。

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

## 已知边界

当前会话没有可用桌面Minecraft客户端与minecraft-control连接。本批不声称安装版桌面、客户端输入/渲染、音效、玩家实际合成、完整世界保存重进、外部用户或冷缓存默认网络体验通过。

完整类型/轨道发现、完整生成预检、安装体验、职责重构与外部试用按PRD后续批次继续。
