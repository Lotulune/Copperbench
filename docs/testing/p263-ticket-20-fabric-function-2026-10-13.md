# P263-20 — Fabric 26.3 函数内容与实际命令效果

日期：2026-10-13（日本时间）。实施树 `codex/p263-unique-cases`，基线 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，尚未提交或合入。

## 实现范围

Fabric 26.3 沿用现有 function 专用契约、编辑器和命令入口，提供六个输入名称及可消费的默认例。发现入口为 `get_workspace_environment.fieldContracts.function`，未把专用契约伪装为 item/recipe 通用契约。具体字段、默认值和命令校验边界见[样例说明](../../examples/camp-kit-structured/fabric-26.3-function/README.md)。NeoForge function 尚未开放，supportStage 仍为 registered。

文本正文精确保留；命令数组按既有 LF 规则转换，同时提供两种表示时必须一致。预览命名空间固定为 mod，资源名限制为安全标识符；无效 JSON 类型、冲突别名、NUL、命令开头斜杠和未开放字段给出明确诊断。其他语法和注册表引用由真实 Minecraft 解析器判断。标签及自动 tick/load 注册未开放。

函数专用界面提供会话预览选择；撤销选择后保留草稿并禁用保存。命名空间显示为只读。原先仅依据行格式提示便显示“语法检查通过”的文案改为“未发现行格式问题”，计数显示“命令行数”。

## 实际验证

38 个不同 Java 行为最终通过，包含新函数四项、原八精确生成器函数保存/生成/重开、输入别名、既有契约投影及预览协商。Javadoc、100 项 Python SDK、42 项 UI-Core、31 项 UI unit 和 UI build 通过。重跑按类与测试名去重。

16 个不同浏览器场景/布局通过：四个 Fabric 中英文/窗口尺寸流程，加 12 个既有函数只读、专用投影、保存重开和失败提示场景。截图检查了命令区、预览选择、草稿、保存和只读命名空间。浏览器使用真实 Core 编辑器 fixture 和模拟外壳，不能替代原生安装截图。两个 fixture 与 Core 数据逐字段一致；完整 editor strict v1.0 基线差异继续登记在 27。

Native 运行 `output/p263-20/native/delivery-5527414939578159300`，耗时 206.759 秒。工作区 `6a5f24d2-2b87-4390-a955-53d3e9978980`；产品 PID 24768、44048、44952 均由进程句柄确认 exit 0。

产品创建函数，将原分数 7 的正文在新进程中编辑为 11，生成、构建、独立宿主验收、导出，再由第三个产品进程重开并再次导出。未知字段 fixture 只在产品关闭时导入，编辑及重开保留；最终 JAR 内正文与产品生成资源一致。九项 Native 负向保持 revision 和定义字节不变。

冻结清单 `camp-kit-function-v1` 只含 T13，minimumTests=1。独立宿主通过实际 ServerFunctionManager 执行 JAR 中函数，并断言：

- 执行前不存在 camp_count；执行后读取 camp_probe 的实际分数为 11。
- 预先清空的位置 `(8,80,8)` 在执行后变为金块。
- 真实 CommandFunction.fromLines 对未知命令报 line 1 的语法错误；对 missing_function_item 报 line 1、position 8 的原始注册表引用错误。两条负向不增加业务用例数，框架 always_pass 也不计入。

已保存效果回执和两条原始诊断，26.3 API 签名与 JAR 来源哈希位于 `output/p263-20/api`。

| 对象 | SHA-256 |
| --- | --- |
| 构建 / 受测 / 两次导出 JAR | `4ec91bbb0654f497d694bcb76e86c575093fa5c9cd19793ba8ec517f2c83f0f4` |
| 原始 XML | `80c13867b88b5940ee2167727269e6055350e0e4eb74489194aed209e28207c7` |
| T13 清单 | `0631c25a606d553a868fed4c0e02adda1d95852f1ac9cd26e6097a3c367d02c1` |

稳定归档：`D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-20`。保留任务、XML/JAR、函数效果、解析诊断、截图、检查日志和精确源快照；排除依赖缓存、玩家世界和授权私钥。

开发时的 TS 可选 operations 处理缺口、新测试缺少显式生成步骤，以及在标签页查找命名空间控件的浏览器断言错误均已修正；首次失败与成功重跑分开保存。

本票不替代玩家输入、安装、Ubuntu 或完整 G2 验收。使用资格化依赖缓存，未声称冷缓存通过。
