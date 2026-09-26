# Stage 17 自定义字段适配器与 v21 复验

接续 [v20 通用字段复验](stage-17-generic-fields-2026-09-23.md)，本轮补充内置自定义序列化格式的输入检查，并在 v21 两平台公开入口验证。**PRD17 尚未整体验收；以下结果不关闭全部 G17-A/B/C/D。**

## 已复现问题与修复

真实工作区反例确认，物品 `glowCondition="false"` 被隐式转换成布尔值；GUI 中不存在的组件 type 被替换成 Unknown，占位对象丢弃原 data。两者原先均返回 committed。红灯记录 `.tmp/stage17-custom-red.log`、`.tmp/stage17-custom-red.xml`。

[CustomFieldInputContract](../../src/main/java/dev/copperbench/core/application/CustomFieldInputContract.java) 按实际序列化格式检查：

- 普通过程引用：字符串、name 对象或 null；不接受数字转成名称。
- 返回值过程：对应类型的常量／列表，或 name、fixedValue 对象；没有非空过程名时必须提供常量。字符串布尔、数字文本化、未知键均拒绝。
- GUI 组件：已注册 type 与 data；未知 type 不再落入空占位对象，data 内继续进行递归类型检查。
- 属性：logic/integer/number/string 的实际类型、数值范围及字符串选项；状态映射不能重复声明同名属性，否则上游 Map 会覆盖较早条目。

这些检查接入既有通用写入前校验与旧定义保留检查，环境接口提供 `fieldContracts.custom`。第三方未知序列化、过程目标存在性、返回值与调用上下文的匹配仍需另行审核，不能从输入形状有效推导行为正确。

合法兼容用例还发现并修复：

1. v20 的颜色检查没有识别上游序列化固定生成的 `falpha: 0`，导致正常颜色的后续修改被保留保护误拦截。现在允许这个旧常量，非零 falpha 仍拒绝，透明度须编码在 ARGB value 中。原八轨失败记录 `.tmp/stage17-custom-eight.log`、`.tmp/stage17-custom-eight-initial-xml/` 保留。
2. 通用字段 Gson 没有像上游定义适配器那样显式注册 StateMap 适配器，标准 property/value 数组会走普通 Map 转换器。现补齐注册，八轨真实物品状态映射写入和重开通过；没有改变上游存储格式。

## 源码回归

新增 11 项真实工作区用例（含八轨参数用例）和 3 项属性／返回值适配器用例。八轨核对过程常量、字符串列表、物品状态映射与 GUI 标签保存、非法更新和计划拒绝、合法坐标更新及关闭重开；另核对旧 GUI data 含扩展对象时拒绝无关字段重写并保留原字节。属性测试覆盖精确整数、范围、布尔、选项、未知键、重复状态名及真实 Gson 往返。

| 检查 | 结果 | 证据 |
|---|---|---|
| 全 Core | 208 通过、4 条件跳过、0 失败／错误 | `.tmp/stage17-custom-core-regression.log`、`.tmp/stage17-custom-core-summary.json`、`.tmp/stage17-custom-core-xml/` |
| Schema | 28 通过、0 跳过 | `.tmp/stage17-custom-schema.log` |
| 候选构建 | Windows 导出和 Linux deb 根目录成功，1m9s | `.tmp/stage17-v21-build.log` |

条件跳过不计通过。这里的 GUI 测试验证定义和序列化，不是桌面或 Minecraft 画面验收。

## v21 固定候选

- 源码快照：`D:/Hyper-V/Stage17-Linux/source-candidate-v21`，27146 文件；`source-manifest-v21.json`。非干净工作树快照，未提交／发布。
- 两平台主 JAR SHA-256：`3343e93f91fca477a79f9efad96386fa1dcb3218b51284c6d632708bf7e346de`。
- 22 份随包文件、24 条文档链接、640 个 Schema 引用检查通过，冻结源文件未变；`D:/Hyper-V/Stage17-Linux/candidate-v21-proof.json`。
- Ubuntu 本地开发 deb 已安装，SHA-256：`3780469a282db6e4f9192e9a9fc7a75330e30628f00bf819cc62f1a7f9d820e6`。来宾原模型、PNG、旧 Mod JAR 哈希不变。

Windows 使用导出包 EXE 和随包 SDK，不能称作 Windows 安装器认证；Ubuntu 使用安装后的 `/usr/bin/copperbench` 与随包 SDK。沿用已核验的 IPv6 SSH 及固定主机密钥，没有改动测试机网络配置。

## 两平台公开入口结果

[自定义字段探针](../../scripts/stage17-installed-custom-field-probe.py) 在全新示例副本执行；旧扩展数据反例再使用一个独立副本，避免更改已通过的原证据。

每个平台均通过：

- 10 次非法创建拒绝，覆盖过程转换、未知键、错误 GUI 类型／坐标／布尔、重复状态名及非整数属性值。
- GUI 非法更新及计划拒绝；合法更新后 x=24，关闭重开仍保留标签正文、颜色旧格式和阴影配置。
- 物品重开后 glowCondition=false、specialInformation 为两条原文，状态映射中的布尔值仍为 true。
- 旧 GUI data 含 pluginValue 后，更新和计划均返回 `FIELD_PRESERVATION_REQUIRES_REVIEW`，具体字段路径正确，定义字节及 revision 不变。
- 原有 [字段探针](../../scripts/stage17-installed-contract-probe.py) 的 22 次拒绝及四类合法定义／重开回归通过。

因此每个平台为新增 14 次拒绝／保护检查，加原有 22 次字段检查；不是 36 条 Minecraft 行为断言。

Windows 证明：`.tmp/stage17-v21-custom-proof.json`、`.tmp/stage17-v21-sdk-contract.json`。Ubuntu 原始证据 `/home/stage17/evidence/v21/` 已归档到 `D:/Hyper-V/Stage17-Linux/guest-evidence-v21/`，包括 `custom-proof.json`、`contract-proof.json`、安装证明及专用工作区。全部探针的主 JAR 哈希与 v21 一致。

## 剩余门禁

输入类型审核推进至内置自定义字段格式；[37 类型清单](stage-17-field-inventory-2026-09-23.md)仍是审查范围快照，不是全部字段验收通过。专用元素正文／扩展合同、引用存在性与返回类型／跨字段条件、候选恢复点与故障回滚仍需收尾。

UI 六场景、英文／缩放／JCEF、真实插件兼容、同源最终候选双平台连续建模和游戏闭环、独立试作与复评分也仍开放。本轮没有新增 Minecraft 输入、GUI 渲染或玩法证明；历史 v18 客户端与不同版本 GameTest 的归属不变。
