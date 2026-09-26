# Stage 17 当前验收清单（2026-09-21）

> 本页保留 9 月 21 日快照。[9 月 22 日进展](stage-17-network-and-runtime-2026-09-22.md)已恢复 Ubuntu SSH、完成 v16/v17 安装及原生启动/退出，并关闭两条 NeoForge 正式行为阻断；以下当时的未完成项不作为最新状态。

状态：**仍未整体验收，G17-A/B/C/D 保持开放**。本页汇总当前结果；[实施记录](stage-17-implementation-2026-09-20.md)保留各候选的失败、修复和介入历史。不同候选的通过结果不合并成 v16 的安装或玩法通过证明。

## 本次完成

- 植物默认字段修复接续完成：八轨最小合法植物均持久化、生成并重开；缺效果、缺贴图在写入前定位字段。两个旧编辑器投影测试改用合法植物输入，保留缺字段拒绝用例。
- 修复精确整数校验：真实工作区对 `musicDiscLengthInTicks=1.0000000000000001` 曾返回 `committed`，未拒绝非整数；原测试在该断言失败处停止。现在在浮点或 Gson 转换前检查原始十进制值及存储范围，共用于方块、库存槽索引与通用字段转换；非法值拒绝且 revision 不变。`1e2` 仍合法。原失败保留于 `.tmp/stage17-integer-range-red.log` 和同名 XML。
- 修复三项历史回归预期：恢复点的非法硬度采用现行上限之外的 64001；资产测试保留已解析和缺失两条引用，并检查具体贴图位置；异步任务测试等待 `task_completed` 后检查事件，避免把已落盘终态误当作通知已经送达。
- 公开文档补充植物最小请求及整数规则；客户端辅助脚本实际进入两平台候选包，帮助命令可运行。

## 本轮验证

| 范围 | 结果 | 证据 |
|---|---|---|
| Core、资产、生成器、MCP 回归 | 446 通过，43 跳过，0 失败/错误；按保留的 JUnit XML 汇总，含条件禁用用例 | `.tmp/stage17-resume-backend-final.log`、`.tmp/stage17-resume-backend-final-xml/`、`.tmp/stage17-resume-backend-summary.json` |
| SDK | 14 通过 | `.tmp/stage17-resume-sdk.log` |
| Schema | 28 通过 | `.tmp/stage17-resume-schema.log` |
| 客户端辅助脚本 | 5 通过 | `.tmp/stage17-resume-client-script.log` |
| 中文浏览器建模/诊断/任务恢复 | 两个窗口项目共 26 通过；资产文本另遍历 1280×720、1366×768、1920×1080 | `.tmp/stage17-resume-ui.log`、`.tmp/stage17-resume-ui-artifacts/` |
| v16 公开 SDK 故障矩阵 | 11 场景通过；不默认联网、故障分类、预算、凭据不转发及程序身份均检查 | `.tmp/stage17-v16-probe-matrix.json` |
| v16 产品可执行文件与随包 SDK | 两类整数越界、植物缺效果/贴图均定位拒绝且 revision 不变；合法物品创建、关闭重开和实际定义值 100 通过 | `.tmp/stage17-v16-sdk-fields.json` |

首轮后端 3 项失败及其原始 XML 保留于 `.tmp/stage17-resume-backend-regression.log` 和 `.tmp/stage17-resume-backend-initial-xml/`，未覆盖。后端条件跳过、浏览器 mock、读取历史日志都不计作新安装或游戏验收。v16 SDK 测试只操作从随包示例复制的 `.tmp/stage17-v16-sdk-workspace/`；未使用原用户 Mod 工程。

## v16 候选

源码副本：`D:/Hyper-V/Stage17-Linux/source-candidate-v16`，冻结 27084 文件；构建后源清单哈希无变化。`exportWin64 prepareDebLinux64 --offline --max-workers=1` 成功（1m25s）。

- Windows：`D:/Hyper-V/Stage17-Linux/source-candidate-v16/build/export/win64`。
- Linux deb 根目录：`D:/Hyper-V/Stage17-Linux/source-candidate-v16/build/export/linux-deb-root`；尚未安装 v16。
- 两平台主 JAR SHA-256：`4f87a1c1d9181fec3f4de3831ac2286ed34a74d688ed1e8c472c652c4edb007e`。
- 1.20.1 生成器 ZIP SHA-256：`4af168534812a4855b1a137de64455b033c9caa4c86a8bdc527aabcf1e844c5f`。
- 22 份随包文件逐一匹配冻结源码，24 条本地文档链接及 640 个 Schema 引用可解析；两份 Python 辅助工具在两个包目录分别执行 `--help` 成功。证明：`D:/Hyper-V/Stage17-Linux/candidate-v16-proof.json`；复查脚本：`.tmp/stage17-audit-v16.py`。

Windows 本轮使用导出目录的产品入口和随包运行时，不能称为 Windows 安装器验收。Linux 工具帮助检查由宿主 Python 执行，只证明包内容自足，不能称为 Linux 运行验证。候选未发布、未提交、未推送。

## 八轨行为证据与缺口

以下为既有独立试作的最新已核对结果，目录均相对于 `D:/Hyper-V/Stage17-Linux/independent-eval/fields/`。这些结果属于所列候选，**不是 v16 八轨复验**。

| 轨道 | 已有正式行为结果 | 原始报告 |
|---|---|---|
| Fabric 1.20.1 | v12 六断言通过，原物品堆叠上限失败已修复 | `other-tracks-v12-fabric1201/report.md` |
| Fabric 1.21.1 | v6 六断言通过，冷 create→build 成功 | `v6/report.md` |
| Fabric 26.1.2 | v9 六断言及单列框架自检通过 | `other-tracks-v9/report.md` |
| Fabric 26.2 | v9 普通 Windows 进程轮次六断言及框架自检通过 | `other-tracks-v9-native/report.md` |
| NeoForge 1.20.1 | v14 生成/构建通过；正式 GameTest 超时退出 124，零断言；后续独立资源下载成功，不改变失败任务 | `neoforge1201-v14-preparation/memory-rerun/report.md`、`asset-preparation/summary.json`（在同一 preparation 目录） |
| NeoForge 1.21.1 | v6 六断言通过，冷 create→build 成功 | `v6/report.md` |
| NeoForge 26.1.2 | v9 六断言及框架自检通过，但最终进程退出 1，正式任务仍失败 | `other-tracks-v9-native-proxy/report.md` |
| NeoForge 26.2 | v9 进程代理轮次六断言及框架自检、正式任务通过 | `other-tracks-v9-native-proxy/report.md` |

缓存、代理、测试 API 适配及前轮失败的限制继续按原报告保留。不得以 XML 通过覆盖非零退出，不得以资源下载完成代替正式测试。

## 关闭门禁前仍需完成

| 门禁 | 已有证据 | 未关闭项 |
|---|---|---|
| G17-A | 方块一致性/回滚/漂移处理及新增整数、植物回归；两个 1.21.1 独立场景 A 通过 | 其余已声明字段及嵌套合同的完整审核；安装候选旧工程恢复反例；两条未通过轨道的正式行为复验 |
| G17-B | 来源/版本资源解析与反例、共享诊断、故障矩阵均有实现及测试 | 完整 UI 六夹具定位与语言/缩放/JCEF 矩阵；真实社区插件就绪兼容复验。当前 `UI_LOCALE` 固定中文，不能声称英文界面已验收 |
| G17-C | Ubuntu v14 真实连续建模、绑定、客户端渲染/重进及产品重开；双加载器 Code 模板正负例、可信导出已有证明 | 同源新候选两平台实机闭环；Windows 建模客户端尚缺可观察的正常交互/退出；Code 模板真实玩家输入与存档边界须按原 PRD 单列 |
| G17-D | v7 原共鸣工坊 14/14、手写源码保留、可信导出及重开；三个独立评估方向已有原始报告 | 新候选的受影响平台回归和场景 C/D 收尾；独立建模评估尚未完整通过；复评分尚未形成完整结果 |

本轮环境观察：Ubuntu VM 仍为 Running，但原 SSH `172.20.86.21:22` 连接超时；Hyper-V `IPAddresses` 为空，宿主 Default Switch 当前邻居表未提供该 VM 地址。不能仅凭这些信息断定来宾宕机或根因。本会话的原生桌面控制 API 不可用，因此无法继续真实 Blockbench/Minecraft 输入、窗口观察及正常退出证明。未重启 VM、改网络/交换空间、绕过桌面限制或伪造任务终态。需要恢复测试机连通性及原生桌面操作能力后继续安装与实机门禁。
