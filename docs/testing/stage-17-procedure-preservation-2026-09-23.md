# Stage 17 Procedure XML 保留边界与 v26 复验

接续 [v25 正文同步复验](stage-17-procedure-bodies-2026-09-23.md)，本轮阻止 IR 无法完整表达的 XML 被结构化操作重写。**PRD17 尚未整体验收；G17-A/B/C/D 仍按各自剩余门禁跟踪。**

## 问题与处理

红灯用例确认：包含根级变量和已知块 mutation 的过程，移动节点仍返回 committed。源码中的 Blockly 读取／导出只处理部分结构，该路径会用缺少扩展数据的 IR 重建 XML。反例记录 `.tmp/stage17-procedure-preservation-red.log/.xml`。

[ProcedureIrCodec.structuredEditingBlocker](../../src/main/java/dev/copperbench/procedure/ProcedureIrCodec.java) 现检查 XML 是否包含结构化编辑无法保留的数据，包括根级变量、已知块 mutation、未支持属性／注释、额外字段属性、shadow／多子节点连接、重复字段或身份以及会改变种类的连接。未知 block 的完整原文子树继续作为整体保留。

保护接入 [ProcedureFieldContract](../../src/main/java/dev/copperbench/core/application/ProcedureFieldContract.java) 和应用服务：

- 过程编辑器投影返回只读状态及 `PROCEDURE_XML_PRESERVATION_REQUIRED`，附具体 XML 位置与原因。
- 专用结构化更新、IR 字段更新及相应预览／计划停止，保留原定义与 revision。
- 会重写受影响过程的注册表改名及其预览也停止，原注册表名称不变。
- 说明字段修改、显式完整 XML 更新仍可用；明确替换为受支持的 XML 后恢复结构化编辑。

这些扩展目前按保留原文、拒绝不安全改写处理，未实现全部 Blockly 扩展的原生编辑。接口说明已更新至 [Stage 17 工作流](../stage-17-agent-workflow.md)。本轮验证了编辑器投影和 API 行为，未进行桌面画面验收。

## 源码回归

新增真实工作区保留测试、XML 结构边界测试和注册表改名原子性测试，检查普通修改仍可用，以及显式替换正文后可恢复编辑。

| 检查 | 结果 | 证据 |
|---|---|---|
| Core + Procedure 编解码 | 246 通过、5 条件跳过、0 失败／错误 | `.tmp/stage17-preservation-core.log`、`.tmp/stage17-preservation-core-summary.json`、`.tmp/stage17-preservation-core-xml/` |
| 最后补齐改名预览的一致性回归 | 7 通过、0 跳过；为上行已含用例的专项复跑 | `.tmp/stage17-preservation-final-targeted.log`、`.tmp/stage17-preservation-final-targeted-xml/` |
| v26 构建 | Windows 导出、Linux deb 根目录成功，1m23s | `.tmp/stage17-v26-build.log` |

5 条件跳过包括原有 Core 4 项及 Procedure 500 节点规模门禁 1 项，不计通过。全量回归后补齐了改名预览，最终专项和以下公开入口验证使用该最终实现。

## v26 固定候选

- 源码：`D:/Hyper-V/Stage17-Linux/source-candidate-v26`，27158 文件，清单 `source-manifest-v26.json`；来自未提交工作树，未发布。
- 两平台主 JAR SHA-256：`f25500c7b2c178e684c10657a84bdbade474b5afc5c8a8a91169ce6568d89206`。
- 随包 22 份文件、24 条文档链接、640 个 Schema 引用及冻结文件完整性检查通过；`D:/Hyper-V/Stage17-Linux/candidate-v26-proof.json`。
- Ubuntu 开发 deb SHA-256：`347145617ebd0ac8308de8aa81c810daafff23f2b5527b3fdbab3f8d0228ec51`；安装后主 JAR 匹配，既有模型、JSON、PNG 和 Mod JAR 哈希未变。

Windows 使用导出包 EXE 与随包 SDK；Ubuntu 使用安装后的 `/usr/bin/copperbench` 与 `/opt/copperbench` 随包 SDK。Windows 结果不代表安装器认证。

## 双平台公开入口

[保留边界探针](../../scripts/stage17-installed-procedure-preservation-probe.py) 为根级变量和已知块 mutation 各建一个独立过程，两平台分别完成：

1. 每种过程的专用更新、专用预览、专用计划、IR 更新、IR 预览、IR 计划共 6 项保护；加注册表改名及改名预览，总计 **14 项保护检查**。定义字节及 revision 不变。
2. 编辑器返回只读与保留诊断；修改说明仍保留原 XML，显式更新 XML 原文可成功。
3. 关闭重开后，两份扩展 XML 保留，仍处于受保护状态，注册表变量仍名为 `score`。
4. 显式把其中一个过程替换为受支持的完整 XML 后，可再次移动节点；最终 revision 为 9。

两个平台还分别重跑 [v25 正文探针](../../scripts/stage17-installed-procedure-body-probe.py)，其 12 次输入／冲突拒绝、计划应用、结构化编辑、两次重开及未知 block 原文保留全部通过。普通流程没有被本轮保护一律禁用。

首次 Windows 保留探针有两次脚本错误：误用 `list_registries` 查询名，以及误取未分页响应的 `items` 字段。已按实际公开接口改为 `list_workspace_registries` 的 `data.variables`，使用新工作区重跑；失败记录 `.tmp/stage17-v26-preservation-proof.json`、`.tmp/stage17-v26-preservation-recheck-proof.json` 保留。产品二进制未因此变更。

冻结源码中的初版探针与最终实际执行脚本不同。修正后的脚本已单独归档为 `D:/Hyper-V/Stage17-Linux/guest-evidence-v26/actual-preservation-probe.py`，Windows／Ubuntu 实际执行脚本 SHA-256 均为 `b8f05f04e446a410ee41412e5e47e2eb8cce30585a3415fdd1d38e4587c7d54a`；未覆盖冻结快照。

最终 Windows 证明：`.tmp/stage17-v26-preservation-final-proof.json`、`.tmp/stage17-v26-procedure-proof.json`。Ubuntu 原始证据 `/home/stage17/evidence/v26/` 已归档到 `D:/Hyper-V/Stage17-Linux/guest-evidence-v26/`，包含两组专用工作区、证明及安装记录。四份证明均核对到 v26 主 JAR，汇总 `.tmp/stage17-v26-final-proof-summary.json`。结束时两平台无遗留产品 API 进程。

## 剩余门禁

引用目标、返回类型／触发上下文、依赖元数据与节点语义的匹配仍未完成全面审核。XML 保留保护覆盖上述结构边界，完整第三方插件／Blockly 编辑兼容尚未验收。

候选恢复点与故障回滚边界、UI 六场景及英文／缩放／JCEF、同源最终候选双平台连续建模和游戏闭环、独立试作及复评分仍开放。本轮没有 Minecraft 输入或 Jev 调用，没有新增玩法通过证明。
