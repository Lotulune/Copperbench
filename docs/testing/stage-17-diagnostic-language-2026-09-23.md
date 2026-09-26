# Stage 17 字段与任务诊断中英文验证

接续[总览](stage-17-language-2026-09-23.md)与[资产页面](stage-17-asset-language-2026-09-23.md)。本轮接入字段检查器和任务控制台的中英文提示，并修复由格式错误命令回执触发的修订号 `NaN`。**验证范围仍为当前源码、浏览器及模拟宿主；不是新的安装候选或真实 JCEF 验收，PRD17 总门禁继续开放。**

## 改动

[ElementInspector](../../ui-shell/src/components/ElementInspector.tsx) 的保存／删除失败、无效 JSON、引用缺失、配置漂移审阅、字段条件与影响预览提示使用当前语言。已经保存到 React 状态中的错误保留原始 `LocalizedText` 或双语消息，渲染时才选择语言，因此切换后不会继续显示旧语言，也不丢失字段草稿或重复提交。

[TaskDrawer](../../ui-shell/src/components/TaskDrawer.tsx) 的源码预览、修复失败、数据生成预览／发布确认及 GameTest 报告静态文案接入语言选择。源码、路径、日志、测试消息与诊断参数保持原值。发布仍需明确确认，过期修复仍不能应用。

[i18n/index.ts](../../ui-shell/src/i18n/index.ts) 增加延迟渲染消息类型；[en.ts](../../ui-shell/src/i18n/en.ts) 为已知编辑器分区、字段和材质标签提供英文，解决旧宿主回退文本自带中文的问题。未注册的扩展键仍使用宿主 fallback，不对任意用户文本进行替换。

截图检查发现一个可复现的协议边界缺陷：测试夹具遗漏 `newRevision`，旧桥接仍调用 `Math.max(currentRevision, undefined)`，标题栏显示 `Rev. NaN`。根据 `CommandResult` 合同修正有效夹具，并在 [JcefCoreBridge.invoke](../../ui-shell/src/bridge/JcefCoreBridge.ts) 应用命令结果前要求 `newRevision` 是非负安全整数。缺失、`null`、字符串、负数、小数和超出安全整数范围的值均被拒绝；原修订号和编辑草稿保留。这项防护只覆盖命令回执修订号，不冒称完整消息 Schema 校验。

## 验证

[专项测试](../../ui-shell/e2e/stage17-diagnostic-language.spec.ts)覆盖保存失败后的草稿与原始参数、无效 JSON、编译诊断到源码和所属元素的键盘定位、已有源码预览失败消息的切换、过期修复保持阻断、英文数据生成发布确认与 Escape 取消，以及六类非法命令修订号。对源查询、命令计数进行断言，确认语言切换不重复执行操作。字段错误和编译诊断在 1280×720、1920×1080 检查横向溢出并留图；发布确认框留图。

| 检查 | 结果 | 证据 |
|---|---|---|
| 命令、桥接、资产语言及诊断语言回归 | 39 用例 × 2 项目，78 通过；2.4 分钟 | `output/stage17-diagnostic-language/stage17-diagnostic-language-verified.log` |
| 最后单复数文案调整后的诊断专项 | 12 用例 × 2 项目，24 通过；44.4 秒 | `output/stage17-diagnostic-language/stage17-diagnostic-language-accepted.log` |
| 原有诊断场景 | 16 用例 × 2 项目，32 通过，位于首轮混合运行 | `output/stage17-diagnostic-language/stage17-diagnostic-language-first.log` |
| 最终生产构建 | 中文词典 384/384、TypeScript、Vite 通过；保留大包体积提示 | `output/stage17-diagnostic-language/stage17-diagnostic-language-build-accepted.log` |
| 截图与文件哈希 | 10 张最终截图；源码、测试、日志及构建产物清单 | `output/stage17-diagnostic-language/accepted/`、`proof.json` |

各轮有重复用例，不相加声称独立测试总数。首轮混合运行 40 通过、4 失败：新增 JSON 夹具重复追加字段，以及把输入框值当作普通文本断言，均在两个项目失败；修正夹具与定位断言后 12 项专项通过。早期有效回执缺少 `newRevision` 的问题由截图揭示，即使当时测试通过，也不作为状态一致性的最终证据；最终夹具符合合同，并增加非法回执反例。

使用 MockCoreBridge 和注入宿主，不会真正删除元素文件、发布数据、打开外部编辑器或导出 JAR。浏览器测试的 `JCEF Bridge` 名称只表示桥接适配器路径，不代表启动了原生 JCEF。

## 尚待完成

元素列表、内嵌 Blockbench 面板、Procedure/Blockly 和其他区域仍有中文；并非完整英文产品。GameTest 报告、配置漂移处理等其他静态分支虽接入翻译，本轮没有逐一独立操作验收。真实 JCEF、Windows 系统缩放、同一候选全流程、真实诊断采集和 PRD17 其他门禁仍待完成。

本轮未冻结新发行候选，未安装更新 Ubuntu，未提交或发布；既有 v30 证据不包含这些源码变更。
