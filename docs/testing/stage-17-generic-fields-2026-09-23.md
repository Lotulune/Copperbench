# Stage 17 通用字段与 v20 候选复验

> 本页保留 v20 结果；后续修复及复验见 [v21 自定义字段适配器](stage-17-custom-adapters-2026-09-23.md)，其中修复了本轮颜色旧格式检查的兼容问题。

本轮接续 [v19 收尾记录](stage-17-closeout-2026-09-23.md)。完成通用嵌套输入和旧数据保护修复、37 类型字段映射清单、v20 双平台公开入口复验。**PRD17 尚未整体验收，G17-A/B/C/D 仍开放。**

## 发现与修复

真实工作区反例确认：交易 `trades[0].countOffer=1.0000000000000001` 被舍入后保存；物品 `repairItems=[42]` 被转成文本引用；两者原先均返回 committed。失败日志及 XML 为 `.tmp/stage17-nested-red.log`、`.tmp/stage17-nested-red.xml`。

[GenericFieldInputContract](../../src/main/java/dev/copperbench/core/application/GenericFieldInputContract.java) 在 Gson 转换前递归检查普通对象、数组、集合、Map 值、数字、布尔、字符串和枚举；保留映射引用的字符串／`{value: string}` 两种既有形式。错误路径指向具体成员，数值先检查精确整数及注解范围。新未知嵌套键拒绝；旧定义含可能丢失的未知嵌套键时，结构化重写返回 `FIELD_PRESERVATION_REQUIRES_REVIEW`，原字节和 revision 保持不变。此处没有修改全局 JSON 合并器或猜测数组对象身份。

另修复两处审查发现：

- 生物群系 `genDepthMin/Max` 是 transient 临时成员，上游序列化不保存；现在从可写字段声明和通用赋值中排除，编辑器显示只读。读取不清除旧导入元数据。
- 药水创建原先自动补入没有存储映射的顶层 duration，合法效果列表也因此被拒绝。本轮改用实际的 effects 列表；持续时间留在每个效果条目的 duration。补齐 effects 的中文标签。

通用字段单独更新 `fields` 兼容路径时清除旧副本，显式冲突仍拒绝。环境查询新增 `fieldContracts.generic`，明确其范围和未覆盖的自定义适配器；公开文档已更新。

## 字段清单与源码验证

[字段清单](stage-17-field-inventory-2026-09-23.md)覆盖 37 个第一方类型、893 个类型内字段声明，包含元数据和只读内部名；[完整 JSON](stage-17-field-inventory-2026-09-23.json)列出名称、存储类型、嵌套结构与排除项。这是范围清单，不是 893 个字段全部通过行为验收。

- 新增 13 项真实工作区测试：八轨交易、物品引用、药水合法写入与重开；嵌套整数、布尔、引用、枚举及未知键拒绝；非法更新／计划保留字节；旧未知交易字段保护；临时字段不可写。
- 全 Core 回归：194 通过、4 条件跳过、0 失败／错误。JUnit 原始 XML、统计和日志保留于 `.tmp/stage17-nested-core-final-xml/`、`-summary.json`、`.tmp/stage17-nested-core-final-runtime.log`。跳过的规模／冷工作区条件用例不算通过。
- Schema：28 通过，0 跳过；`.tmp/stage17-nested-schema.log`。产品构建也执行了中文词条、TypeScript 与 UI 打包检查。

首次递归校验曾因定义构造前合并 values 丢失诊断中的 fields 路径前缀；已把检查前移到原请求写入边界，保留精确路径断言。第一次全 Core 回归只有新药水用例失败，暴露上述旧默认值问题，初轮日志及 XML 保留于 `.tmp/stage17-nested-core-regression.log`、`.tmp/stage17-nested-core-initial-xml/`。清单脚本参数及 effects 缺中文词条的准备失败另保留日志，未冒称产品行为失败或跳过检查。

## v20 候选及两平台公开入口

冻结源码为 `D:/Hyper-V/Stage17-Linux/source-candidate-v20`，27141 文件；源清单 `source-manifest-v20.json`。Windows 导出包和 Linux deb 根目录构建成功（1m18s），22 份随包文件、24 条文档链接、640 个 Schema 引用通过；冻结文件哈希没有变化。证明 `D:/Hyper-V/Stage17-Linux/candidate-v20-proof.json`。

- 两平台主 JAR SHA-256：`1d3d66ba1f3c106a3f75cbba325d6fe3ab57c7723e1752f8162c649b1dd90793`。
- Ubuntu 本地开发 deb 已安装，SHA-256：`fa92258843410dde4a8cc73b7dabed5c3ee0e522a8adb49d4d2727c84e6247c2`。
- 原共鸣工坊模型、PNG 和旧 Mod JAR 哈希不变。Windows 是导出包 EXE 入口，不是安装器认证；Ubuntu 使用安装后的 `/usr/bin/copperbench` 与随包 SDK。

| 复验 | Windows EXE | Ubuntu 安装入口 |
|---|---|---|
| 函数／投射物／进度／通用合同发现 | 通过 | 通过 |
| 非法创建、更新、计划及诊断路径 | 22 次拒绝均符合预期，revision 不变 | 同左 |
| 四类合法更新、真实定义及关闭重开 | 通过，交易 countOffer=42、maxTrades=37 | 同左 |
| 旧交易条目含未知对象后更新／计划 | 两次 `FIELD_PRESERVATION_REQUIRES_REVIEW`；重开后字节不变 | 同左 |
| 药水效果保存／重开及字符串布尔拒绝 | duration=2400、ambient=false；非法更新被拒绝 | 同左 |

复验分别使用 [字段探针](../../scripts/stage17-installed-contract-probe.py)、[嵌套保留探针](../../scripts/stage17-installed-nested-preservation-probe.py) 和 `.tmp/stage17-v20-potion-probe.py`；仅操作全新专用副本。

Windows 证明为 `.tmp/stage17-v20-sdk-contract.json`、`.tmp/stage17-v20-nested-preservation.json`、`.tmp/stage17-v20-potion-proof.json`。Ubuntu 原始证明在 `/home/stage17/evidence/v20/`，已完整归档到 `D:/Hyper-V/Stage17-Linux/guest-evidence-v20/`；包括安装、三组探针及工作区副本。两平台探针记录的 JAR 哈希与上述候选一致。

本轮 Ubuntu IPv4 SSH 超时，已通过固定主机密钥核验并使用现有 IPv6 链路本地连接继续；没有更改网络、代理、路由或防火墙设置，也没有接受新主机密钥。

## 尚未关闭的范围

本轮关闭的是通用嵌套类型转换、对应保留反例和药水默认值的已复现问题。过程／返回值过程、GUI 组件和属性／状态映射等自定义序列化、引用存在性及跨字段条件仍需逐项审核。清单已把这些边界显式列出，不能将整个 G17-A 标为通过。

候选包恢复点／故障回滚边界、诊断六场景与语言／缩放／JCEF 矩阵、真实插件兼容、同源最终候选双平台连续建模／客户端闭环及独立试作仍待完成。本轮没有新增 Minecraft 输入或游戏行为证明；此前 v18 的放置和库存重进、不同候选的八轨 GameTest 仍保留原版本归属，不改写为 v20 通过。
