# P263-06：精确预览协商与执行边界

状态：本地实现、验证与代码自查完成，未提交、合入或发布。实施基线为 `99f95e1d7ea0e919342414572e31f6dd6e5c4429`，包含 01–05 的前置修改。

## 行为与边界

- 精确注册表登记十个 Java 生成器；latest=26.3、previous=26.2，26.1.2 通过新目录及 UI 的“显示历史精确版本”保留。旧八 ID、执行 profile 和直接创建规则不变。
- 26.3 使用明确的待就绪 profile，不能回落到旧版本后端。`generatable()` 仍只接受 `SUPPORTED`，现有同版本加载器迁移不能借此进入预览。
- 独立协议 `preview_targets` / `1`，Native `kind=preview`、MCP `preview`、JCEF `messageType=preview` 及 Python/TypeScript SDK 都在每次请求携带 `supportedVersions`。旧 strict v1.0 schema 没有增加字段。
- 操作包括 `catalog`/`negotiate`、`opt_in`、`revoke`、`command` 及嵌套 `acceptance`。后者仍须协商 `required_cases`，没有隐式清单协议升级。
- 选择保存在当前服务会话中，绑定工作区 UUID、活动生成器、目标精确 ID；新建工作区还绑定规范化后的目标目录。重开产品需重新选择。目录或目标变化不能借用旧选择。
- Core 在工作区锁内重新判定预览选择、当前修订和逐操作实现证据，然后执行原有命令。权限、任务授权、受保护操作审批及 EULA 检查保持有效。撤回拒绝后续操作，不暗中取消已经启动的任务。
- 注册、独立依赖资格、操作就绪和类型/字段范围分别投影。03/04 的独立环境证据只代表依赖资格；当前两个 26.3 目标仍为 `registered`，所有原生操作与五类结构化字段尚未开放。允许路径仅由契约测试夹具验证，07/08 负责真实新后端。
- 旧接口可以读取预览工作区；其写入/启动请求明确拒绝。取消任务、管理已有任务授权仍走原有控制规则。旧工作台不把预览写入或字段契约报告为全面可用。

UI 使用 Core 目录显示预览、逐操作就绪状态及字段范围；选择可撤回，目录变化会撤回旧选择。推荐项仍从已可用旧目标中选择。切换中英文保留表单，不重新发送产品操作。

## 验证

| 层 | 实际结果 |
| --- | --- |
| Java 定向回归 | 123 个不同用例：122 passed、0 failed、1 skipped；跳过项为显式 opt-in 的加载器迁移重建，不冒作本票重新运行 |
| Python SDK | 98 passed；覆盖精确目标、修订、嵌套任务授权及传输失败不重试 |
| TypeScript SDK | 18 passed |
| UI-Core | 41 passed；包括独立扩展 schema，旧 schema 拒绝预览内部字段 |
| UI 单元检查、构建和本地化 | 30 passed，构建及双语检查 passed |
| Playwright | 11 个相关流程在 1920/1366 两个桌面视口共 22 passed；最终文案及无副作用语言切换的 6 次定向复验 passed |
| Native/MCP 协议回执 | 4 份 Native adapter 与 1 份真实 HTTP MCP 响应通过扩展及嵌套 strict v1.0 检查 |
| Javadoc | passed |

实际检查了最终中英文预览面板截图；操作名称、状态与不可用原因没有截断。截图来自浏览器契约夹具，不是安装版或 Minecraft 画面。

真实产品 Native 验证：`output/p263-06/native/preview-4273124121402329720`。两个独立产品 PID 为 `40372`、`3292`，同一工作区 UUID 为 `ba1b705d-0481-46a6-8eef-45fe4cb3910e`；Python 检查耗时 25.558 秒。

流程对既有 Fabric 1.21.1 M1 的新副本查询十个精确目标，选择 Fabric 26.3，拒绝旧入口绕过、未验证创建、另一加载器借用选择、撤回后创建和未知协议；新进程重连后没有继承选择。目标目录未创建，28 个输入文件摘要保持一致。8 份扩展响应通过 schema。**这是实际产品的协商/拒绝验收，不是 26.3 构建、安装或玩家验收。**

## 修复与保留的失败

- 首次构建发现缺少新增诊断的中文词条，已补齐。
- 首轮 Core 检查发现统一修订检查破坏旧建模幂等回执；改为保持旧命令原有回放语义，在当前工作区锁内对预览单独检查请求修订，实际 MCP 回归通过。另两项更新了目录数量及预览拒绝码预期。
- 初次 Playwright 默认端口被另一应用占用，停止该测试，使用独立 5263 端口且禁止复用已有服务。随后发现界面把诊断键作为字符串展示，已改为 LocalizedText 并复验双语。
- 所有失败日志保留；既有源码的混合换行符已保留。`git -c core.whitespace=cr-at-eol diff --check` 通过，不通过重排旧文件隐藏改动。

## 复核入口

- [预览 schema](../../ui-core/schemas/extensions/preview-targets-v1.schema.json)
- [真实 Native 验证脚本](../../sdk/python/preview_negotiation_delivery.py)
- [Core 预览测试](../../src/test/java/dev/copperbench/core/application/PreviewAccessTest.java)
- [UI 流程测试](../../ui-shell/e2e/preview-targets.spec.ts)

原始记录在 `output/p263-06/`，稳定副本在 `D:/AICoding/Minecraft_ModCreator/output/copperbench-26-3/ticket-06/`。`java-summary.json` 对两个最终 Java XML 批次按用例身份去重；保留之前的失败批次。共同实现工作树为 `C:/Users/Administrator/.codex/worktrees/p263-unique-cases/Minecraft_ModCreator`。
