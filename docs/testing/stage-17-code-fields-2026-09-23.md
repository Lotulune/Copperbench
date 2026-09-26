# Stage 17 Code 保存合同与 v24 复验

接续 [v21 自定义字段复验](stage-17-custom-adapters-2026-09-23.md)，本轮完成手写 Java 正文、兼容字段及文件包的输入检查，并修复冷工作区和待生成状态下的实际保存问题。**PRD17 尚未整体验收；本记录不关闭全部 G17-A/B/C/D。**

## 已复现问题与修复

1. `code=true` 原先返回 committed，非文本正文被隐式转换。[CodeFieldContract](../../src/main/java/dev/copperbench/core/application/CodeFieldContract.java) 现检查正文、文件路径及 helper 正文的字符串类型，拒绝 null、布尔和数字替代源码；检查文件包数组、未知条目键和指纹映射。路径须为源包内的相对 `.java` 文件。原有路径归属、重复物理路径和源码冲突保护继续执行。
2. `fields` 兼容入口现在与源文件写入使用的顶层正文同步；外部源码读回后，兼容副本及指纹一并刷新。`parts\Helper.java` 在 Windows 和 Linux 均解析为真正的 `parts/Helper.java`。
3. 尚未生成源码目录时，Code 创建会返回 committed，却提前跳过物理写入。移除 `persistCustomCode` 中这一提前返回后，首次创建即写出主文件和 helper，并保持手写锁定。生成器管理的元素仍遵守既有延迟生成规则。
4. v23 的 Windows 公开 SDK 复验发现：重开外部修改过的源码后，修改说明字段被回滚。日志确认 `GENERATION_SOURCE_CONFLICT`：用于回滚备份的手写文件路径被误加入待生成文件哈希。现在 [MCreatorGenerationPreparation](../../src/main/java/dev/copperbench/core/workspace/mcreator/MCreatorGenerationPreparation.java) 按当前手写所有权排除这些文件；旧记录也适用，生成文件本身的哈希保护保留。Code 写入仍单独执行乐观并发检查，不能借此覆盖外部修改。

环境接口提供 `fieldContracts.code`，公开说明见 [Stage 17 工作流](../stage-17-agent-workflow.md)。源码文本有效不表示 Java 能编译或生命周期已接入。

## 回归与反例

新增 [Stage17CodeFieldContractTest](../../src/test/java/dev/copperbench/core/workspace/mcreator/Stage17CodeFieldContractTest.java) 共 17 项：非法输入检查，以及有／无源码根目录各八轨。最终用例均补齐真实延迟生成前提，验证准确文件内容、手写锁定、非法更新／计划不修改文件或 revision、兼容入口更新、外部编辑后重开、旧待生成记录兼容。现有 Code 持久化与生成准备测试继续覆盖所有权、回滚及生成内容冲突。

| 检查 | 结果 | 证据 |
|---|---|---|
| 非文本正文反例 | 修复前错误提交 | `.tmp/stage17-code-contract-red.log`、`.tmp/stage17-code-contract-red.xml` |
| 冷源码目录反例 | 修复前八轨失败 | `.tmp/stage17-code-cold-red.log`、`.tmp/stage17-code-cold-red.xml` |
| 延迟生成所有权反例 | 补齐前提后 16 项失败，修复后相关回归通过 | `.tmp/stage17-code-deferred-red.xml`、`.tmp/stage17-code-deferred-green.log` |
| 最终全 Core | 225 通过、4 条件跳过、0 失败／错误 | `.tmp/stage17-code-deferred-core.log`、`.tmp/stage17-code-deferred-core-summary.json`、`.tmp/stage17-code-deferred-core-xml/` |
| Schema | 本轮前段 28 通过，其后未更改 Schema | `.tmp/stage17-code-schema.log` |
| v24 候选构建 | Windows 导出、Linux deb 根目录成功，1m52s | `.tmp/stage17-v24-build.log` |

条件跳过不计通过。v22 是冷目录修复前的中间快照；v23 包含冷目录修复但未通过公开 SDK 的外部编辑用例，均不充当本轮通过候选。v23 失败证据保留于 `.tmp/stage17-v23-code-proof.json`、`.tmp/stage17-v23-code.log`、`.tmp/stage17-v23-code-persistence-failure.log`。

## v24 固定候选与双平台公开入口

- 源码快照：`D:/Hyper-V/Stage17-Linux/source-candidate-v24`，27150 文件；清单 `source-manifest-v24.json`。来自未提交工作树，未发布。
- 两平台主 JAR SHA-256：`5f35fc664432ab30c889e75605cd4a7578c536776a9fbbefaa1c41153916e9c7`。
- 随包 22 份文件、24 条文档链接、640 个 Schema 引用及冻结源码完整性检查通过；`D:/Hyper-V/Stage17-Linux/candidate-v24-proof.json`。
- Ubuntu 开发 deb SHA-256：`c77c7fd81c9d16a62ef9e4a57b323216fccece5943cb00967617ad6900b2321c`；安装后 JAR 哈希匹配，原测试模型、JSON、PNG 和 Mod JAR 哈希未变。

[公开 SDK 探针](../../scripts/stage17-installed-code-field-probe.py) 分别运行普通示例副本和去除源码目录的冷副本。Windows 使用导出包 EXE，不是 Windows 安装器认证；Ubuntu 使用实际安装后的 `/usr/bin/copperbench` 和 `/opt/copperbench` 随包 SDK。

四组全部通过，每组验证：

- 8 次非法创建、非法 helper 更新和计划、1 次陈旧正文覆盖，共 11 次拒绝；具体错误码、字段路径和 revision 不变。
- 兼容 `fields` 创建和正文更新准确写入主文件，反斜杠 helper 路径形成实际子目录文件。
- 关闭后外部修改正文，重开再改说明成功且保留外部文本。
- 会话内再发生外部修改时，旧正文覆盖被 `SOURCE_CONTENT_CONFLICT` 拒绝，主文件及 helper 保持预期字节。

另在 Windows 用 v24 打开 **实际 v23 失败现场的全新副本**，修改说明成功，revision 从 2 到 3，主文件和 helper 哈希不变。旧待生成键可能被既有未知字段保留机制保存在文档中，但当前手写所有权使其不参与生成哈希检查；没有宣称旧键全部从磁盘清除。

Windows 证据：`.tmp/stage17-v24-code-proof.json`、`.tmp/stage17-v24-code-cold-proof.json`、`.tmp/stage17-v24-legacy-code-proof.json`。Ubuntu 原始证据 `/home/stage17/evidence/v24/` 已归档到 `D:/Hyper-V/Stage17-Linux/guest-evidence-v24/`，包含普通／冷副本、安装和探针证明。五份证明均核对到 v24 主 JAR，汇总为 `.tmp/stage17-v24-final-proof-summary.json`。结束时两平台无遗留产品 API 进程。

## 尚未关闭的门禁

本轮证明范围是手写文件保存、输入拒绝和并发保护，不是编译或玩法验收；没有新增 Minecraft 输入或 Jev 调用。

Procedure XML／IR 正文一致性、引用目标及上下文语义、候选恢复点和故障回滚边界仍需收尾。UI 六场景及英文／缩放／JCEF、真实插件兼容、同源最终候选的双平台连续建模和游戏闭环、独立试作及复评分也仍开放。历史游戏证据继续归属其原候选，不能转记为 v24 通过。
