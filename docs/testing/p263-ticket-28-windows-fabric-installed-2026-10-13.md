# Ticket 28：Windows Fabric 安装候选验收

日期：2026-10-13。状态：本地安装 API 单元 passed；冷/暖缓存闭环与独立离线复核通过。
本报告仅绑定下列 Windows 候选，未声明玩家客户端通过或正式支持资格。

## 冷缓存发现与修复

27 的原候选 `sha256:40205a6e06bd8af91a3568db63bf84e575b09410cd834f01a179e66ff4547ed8`
在独立便携目录、空 Gradle home、隔离 JVM user.home 下执行真实 Native 创建。
发现、授权拒绝、创建、环境与 doctor 身份核对成功；首次构建因本地 Loom 合并 JAR 的严格校验失败。
对应任务为 `135adf70-ef88-46bb-9989-4e3982f2ecb4`，测试授权已撤销。
失败回执、原始校验 HTML 和独立失败单元保存在 `output/p263-28/installed-cold-9a0a34ae`、
`output/p263-28/loom-cold-difference` 与 `output/p263-28/old-candidate-failure`。
01–27 的冻结归档和旧候选均未改写。

冷缓存与原资格化缓存的 Mojang 元数据、客户端、服务端、解包服务端及 Loom 1.17.21 JAR 的哈希均相同。
两份本地合并产物的 34,225 个 ZIP 条目名称及载荷字节全部一致；顺序和 1,349 个目录条目的时间戳不同。
原文件 SHA-256 分别为 `3124c9eecf620d52a95e9819d8d19e2f8623e3bf01737b023df67d6a4881d4cf`
与 `d72432b86e03dc08e6f60be5cbca9ea9a3adaf47a305bdc74617468f439747d5`。

修复对精确的本地生成模块 `net.minecraft:minecraft-merged-7e9a32a5b8:26.3`
规范化 ZIP 顺序、固定 1980-01-02 时间、STORED 存储及容器附加信息，保留每个条目的原始载荷。
输出先与单一固定 SHA-256 比较，再原子替换本地文件；Gradle 严格依赖校验继续启用，没有 trusted-artifact 例外。
内容变更、增删、重复条目、文件缺失或固定校验值缺失均拒绝，失败不会替换原文件。
两份来源独立规范化后的 SHA-256 均为 `d14dbbc7b2d6c18227e983617b8423e29099aec31c91623f47c20fe00f4af99f`。

首次原型使用 1980-01-01 时，固定 ZIP 格式测试发现 Java 会为 DOS 起始边界加入带时区的扩展字段；
已改为 1980-01-02，并验证东京与洛杉矶 JVM 时区得到相同输出。原失败日志保留。
Windows/Linux 打包池也补入 26.3 实际锁定的 Gradle 9.6.0。

## 已运行的相关检查

- 6 项真实 Gradle 规范化边界测试通过，涵盖顺序/元数据、跨时区、重复运行不重写、内容增删改、重复条目、缺失及规范化后的原始字节篡改。
- Fabric/NeoForge 模板与环境的 6 项 Java 测试通过；4 项显式 opt-in 的构建/服务器用例未在这次模板检查中运行。
- 模板检查发现 07/08 的“所有元素支持为空”断言已过时，现精确约束已实现的 item、block、recipe、function、code 五类及各自覆盖等级。
- 9 项环境探针单元测试、探针与生成器脚本/元数据的字节一致性、更新后的锁定摘要检查通过。
- 使用原冷构建的独立副本，最终规范化脚本完成真实离线强制重建，31 秒；这属于修复复现，不作为新候选冷缓存通过。
- 工作流 YAML 1.2 解析、Markdown 本地链接及 diff 空白检查通过；未声称运行完整 actionlint 或全部项目测试。

## 替代 Windows 候选

候选 `sha256:4f42f5a4ee02188fa0020a3316699614ad803b899415671449ec61e47134d9ff`，
目录 `output/p263-28/windows-candidate-v2/frozen`。NSIS 与便携 ZIP 构建通过，Gradle 6 分 54 秒。
构建前后实际源码摘要一致：`59cfae55bb9f72b0fe148f04bd3bba36a5a06fc5c73f1892b589ed2ec810bcae`。

| 资产 | SHA-256 |
| --- | --- |
| 应用 JAR | `7fa51bccc899e7ba51885486fe49f96f85869d19ee03aabc80bcfa55f890641b` |
| 启动器 | `0d59cf9cfe36262e7f489310cd58283e1537a282013df8ec20a364e5f61cc2b6` |
| NSIS | `b30028712484402098c6646566c4c3e2cd5e61f75ac73fd65ca24ef241d21e39` |
| ZIP | `582a2f5297a95a352878f7e9c58789a1d52b8f5bc559929e7c5195180a6f3a7d` |
| Fabric 生成器 | `e4d6baa38add7c67e8f9d3c46a835fc75796e4cc794837c02bbd70e3a9721eee` |

使用冻结 ZIP 和包内 EXE/SDK，独立安装目录为 `C:/CB263/p263-28-v2-a918e2/Copperbench014`。
未执行会写入注册表和文件关联的 NSIS 安装器。新候选来自尚未提交的工作树，`dirty=true`；
即使后续本地安装证据完整，也不能推导正式支持资格。

## 安装闭环与边界

| 运行 | 冷缓存 | 暖缓存 |
| --- | --- | --- |
| 原始记录 | `installed-cold-4a634758` | `installed-warm-f61689f8` |
| 总耗时 | 558.820 秒 | 385.968 秒 |
| 初始依赖缓存 | 全空 | 绑定成功冷结果，逐项校验 3,018 个保留载荷 |
| 固定业务用例 | 6/6 | 6/6 |
| Native 负向门禁 | 12 项通过 | 12 项通过 |
| 产品进程 | 3 个不同 PID，均正常退出 | 3 个不同 PID，均正常退出 |
| 测试授权 | 已撤销 | 已撤销 |

两轮均实际执行包内 EXE/SDK 的发现、Native 创建、编译故障、显式修复、六项业务验收、
同 JAR 导出、结束旧进程和新进程重连。重复身份、真实 5/6 必要用例缺失、外部源码冲突、
当前输入变化、JAR/XML 篡改、过期 revision、超时与取消均有原始回执。
默认 CLI 与 Native 创建在无授权时拒绝；无请求授权的 environment/doctor 查询前后源码与 revision 不变。
独立授权 fixture 只用于已授权的测试范围。

冷、暖两轮各自的当前/历史导出均选中同一轮的同一个验收 task；原始 JAR/XML/便携回执被复制为四文件 ZIP，
在独立目录使用包内 standalone verifier 核验通过。额外篡改包返回失败。冷、暖最终 JAR 的 SHA-256 一致：
`85efba8002bf9ebf16a109a4b955a3a3bc34d43ddb103649761d01af442723d8`。
固定六项业务之外的框架自检不计入业务数量。

安装单元回执为 `output/p263-28/installed-cell-v2/cell.json`，原始材料、每项检查引用及哈希一并保存。
候选 evaluator 已复核两个原始便携 ZIP 的业务身份及同任务/JAR 绑定，结果为 `install:windows:fabric=passed`；
其他安装/客户端单元没有据此提升。候选仍为 `dirty=true`，`formalSupportClaim=false`。

本次运行显式使用既有系统代理 `127.0.0.1:3067`，只设置任务内 JVM 代理参数；没有更改全局代理或仓库 URL。
冷缓存记录从零开始；暖缓存必须绑定同候选、同加载器的一次成功冷结果，并逐文件复核保留缓存载荷。
允许复用冻结包内 Gradle 分发；空 JVM user.home 排除真实用户缓存。此条件不等于默认直连下载保证。

授权签发使用安装包内 TaskAuthorizationStore 的隔离测试 fixture，用户已授权本任务；桌面授权签发 UI 未测。
空 Native 会话种子仅含 metadata，没有元素、依赖或验收记录；实际 Camp Kit 仍由安装版 Native API 创建。
原生样例写入是明确记录的准备步骤，后续编辑、构建、验收、导出与重连使用真实产品 API。

安装器生成、源码测试、原型重建、包内 CLI 发现及导航均不能代替安装闭环或玩家验收。
Ubuntu 云端执行等待此前已发出的提交/推送授权；当前会话也没有可调用的 Minecraft MCP 工具。
29 的后续修复将产生另一候选；本报告的结果不可改绑到其新资产。
