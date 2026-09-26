# Stage 17 Procedure 正文同步与 v25 复验

接续 [v24 Code 保存复验](stage-17-code-fields-2026-09-23.md)，本轮处理通用创建／更新入口的 Procedure XML、IR 和兼容副本同步。**PRD17 尚未整体验收；本记录不关闭全部 G17-A/B/C/D。**

## 复现与修复

第一轮反例共 9 项失败：错误形状的 IR 可以返回 committed；只提交合法 IR 时，实际 Procedure 定义仍写入默认空正文。补上初步同步后，八轨关闭重开测试继续失败：旧 IR 被既有元数据保留机制恢复，编辑器又读到了旧正文。两轮失败记录分别为 `.tmp/stage17-procedure-body-red.log/.xml` 和 `.tmp/stage17-procedure-reopen-red.log/.xml`，没有将首次保存成功当作完整通过。

[ProcedureFieldContract](../../src/main/java/dev/copperbench/core/application/ProcedureFieldContract.java) 现接入创建、通用更新、预览及计划模拟，环境接口提供 `fieldContracts.procedure`：

- XML 必须是根元素为 `xml` 的合法字符串；不能把布尔、null 或其他根元素当成空 Blockly 图。
- IR 的节点／依赖数组、字段／端口对象、UUID、有限数值坐标、布尔标记和版本按实际类型检查；重复节点 ID、未知结构成员及无有效原文的未知节点被拒绝。
- 单独编辑 XML 或 IR 时，刷新另一种正文及 `fields` 兼容副本；显式同时提交两种正文时，比较其导出的块结构，冲突返回 `PROCEDURE_BODY_CONFLICT`。编辑器生成的 block id 不参与该比较。
- XML 输入保留原文；IR 输入导出到真实 Procedure 定义。同步后的副本均写入，避免重开时恢复旧 IR。
- 结构化编辑及注册表改名生成新正文后也刷新兼容副本。IR 导出保留零坐标和小数坐标，不再省略零值或取整。

没有把图诊断错误一律改为拒绝保存。类型正确但存在循环／悬空连接的图仍可作为草稿保留，需另行读取诊断；未知 block 的原始载荷继续保留。输入正文、引用语义、代码生成和玩法验收是不同范围。

## 源码验证

新增 [9 项真实工作区用例](../../src/test/java/dev/copperbench/core/workspace/mcreator/Stage17ProcedureBodyContractTest.java)（包含八轨参数用例）及 [3 项正文合同用例](../../src/test/java/dev/copperbench/core/application/ProcedureFieldContractTest.java)。八轨核对 IR 创建的实际定义、XML 替换后编辑器内容、非法更新／计划不改变定义或 revision，以及关闭重开后的定义和 IR。合同用例覆盖结构成员被丢弃的反例、重复 ID、正文冲突、编辑器 ID 差异、草稿与未知 block 原文保留。

| 检查 | 结果 | 证据 |
|---|---|---|
| 最终 Core + Procedure 编解码回归 | 243 通过、5 条件跳过、0 失败／错误 | `.tmp/stage17-procedure-core.log`、`.tmp/stage17-procedure-core-summary.json`、`.tmp/stage17-procedure-core-xml/` |
| 其中 Core | 237 通过、4 条件跳过 | 同上 |
| 其中 Procedure 编解码 | 6 通过，500 节点规模门禁 1 条件跳过 | 同上；跳过项不计通过 |
| v25 构建 | Windows 导出和 Linux deb 根目录成功，1m23s | `.tmp/stage17-v25-build.log` |

本轮没有更改 Schema 文件；随包引用解析检查见下。上述工作区测试证明定义持久化，不代替八轨 Mod 编译和 Minecraft 行为验证。

## v25 候选与两平台公开入口

- 冻结源码：`D:/Hyper-V/Stage17-Linux/source-candidate-v25`，27155 文件，清单 `source-manifest-v25.json`；来源为未提交工作树，未发布。
- Windows／Linux 主 JAR SHA-256：`a9121782a7434df17823dee86baf8289e465a2aab05d1a50d85b0ab6076e3202`。
- 22 份随包文件、24 条文档链接、640 个 Schema 引用及冻结源文件完整性检查通过；`D:/Hyper-V/Stage17-Linux/candidate-v25-proof.json`。
- Ubuntu 开发 deb SHA-256：`7ff03f9c13e22b44e6fc33a61b5913bff0da760f6f7dfca5e9bd9dcf7c4653f8`。安装后主 JAR 匹配，既有测试模型、JSON、PNG 和 Mod JAR 哈希未变。

[公开 SDK 探针](../../scripts/stage17-installed-procedure-body-probe.py) 在全新示例副本运行。Windows 使用导出包 EXE 和随包 SDK，不能称作 Windows 安装器认证；Ubuntu 使用实际安装的 `/usr/bin/copperbench` 和 `/opt/copperbench` 随包 SDK。

两个平台各通过：

1. 10 次非法创建拒绝，覆盖 XML 类型／根元素、IR 类型／数组／对象、坐标、重复 ID 和正文冲突；另有非法更新及非法计划，共 12 次拒绝，revision 与定义字节保持不变。
2. 通过 `fields.procedureIr` 创建，实际定义中的坐标为 `(0, 40.5)`，未知 block 原文保留。
3. 经公开计划接口应用 `fields.procedurexml` 更新，实际文件保留所提交 XML 原文，编辑器坐标为 `(120, 40.5)`。
4. 第一次重开后，用 `update_procedure` 移动节点到 `(240.25, 0)`；再修改说明字段，两种正文继续一致。
5. 第二次重开后，文件和编辑器仍是新坐标，未知 block 的 mutation／字段原文保持不变，revision 为 4。

两平台最终定义文件 SHA-256 相同：`91b3816a1328c8fb942e073cc6e1a57c529421ef14833301bc419de411308c0f`。

Windows 证明为 `.tmp/stage17-v25-procedure-proof.json`。Ubuntu 原始记录 `/home/stage17/evidence/v25/` 已归档到 `D:/Hyper-V/Stage17-Linux/guest-evidence-v25/`，包含安装证明、探针证明与专用工作区。两份证明的状态、拒绝数、revision、坐标、定义哈希和候选 JAR 已核对；汇总 `.tmp/stage17-v25-final-proof-summary.json`。结束时两平台无遗留产品 API 进程。

## 仍未关闭的范围

本轮收口的是通用正文写入同步及所列输入形状反例。引用目标存在性、返回类型／触发上下文、依赖元数据与节点语义的匹配仍需审核；已验证未知 block 原文保留，但没有全面验证已知 block 的所有 mutation、根级变量和第三方 Blockly XML 扩展。

候选恢复点与故障回滚边界、UI 六场景及英文／缩放／JCEF、真实插件兼容、同源最终候选双平台连续建模和游戏闭环、独立试作及复评分仍开放。本轮无 Minecraft 输入或 Jev 调用，不把历史候选的游戏证明转记为 v25 通过。
