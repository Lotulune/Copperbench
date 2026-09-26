# Stage 17：过程触发器目录与旧值保留

本轮为 v30 之后的源码修复，没有更新安装包或执行游戏验收。G17-A/B/C/D 不因本报告关闭。

## 问题与修复

`ProcedureWorkbench` 的 `event_trigger` 下拉框原先硬编码 `no_ext_trigger` 及三个 `on_*` ID，没有读取当前生成器的触发器目录。实际加载的 `player_ticks`、`mod_serverload` 等值不在菜单内；Blockly 对菜单外的值会拒绝赋值，因此打开已有过程时存在显示为默认值、后续保存时改变触发器的风险。

- `MCreatorProcedureContextValidation.catalog` 从当前运行时的 `BlocklyLoader` 和工作区生成器读取外部触发器，只投影生成器支持且所需 API 已启用的项。每项包含稳定 ID、标签及提供的依赖名称／类型。目录查询不启动生成、安装或网络探测。
- `WorkspaceMutationGateway.procedureTriggerCatalog` 接入 `get_procedure_editor` 的 `triggerCatalog`。协议与 TypeScript 合同定义该字段；旧宿主可以不提供它。
- Blockly 每个编辑器实例使用自己的后端目录，在载入字段之前配置菜单。产品语义中的 `no_ext_trigger` 始终可选。未知、已移除或当前不可用的旧值作为标记项保留，不静默映射为其他事件。
- 界面明确提示不可用的当前值和修复方向；选择支持的触发器后提示消失，撤销会恢复旧值与提示。旧宿主没有目录时只显示无外部触发器和原值，不猜测可用的事件 ID。

## 验证与证据

`Stage17ProcedureTriggerCatalogTest` 在八轨真实临时工作区通过公开 UI-Core 查询检查：`player_ticks` / `mod_serverload` 可发现、目录 ID 唯一且属于实际加载与生成器支持的集合、坐标依赖具有真实类型、未知原值及其诊断保留、查询前后元素与工作区文件字节不变、修订不变。

| 范围 | 结果 | 证据 |
|---|---|---|
| 八轨目录、直接上下文、Core 应用回归 | 53 通过、0 失败、0 跳过 | `native/`、`stage17-trigger-native-final.log` |
| UI-Core Schema | 29 通过 | `stage17-trigger-schema.log` |
| 新增界面用例，两种视口 | 8 通过 | `stage17-trigger-browser-final.log` |
| 新增用例与现有创作编辑器回归，两种视口 | 28 通过 | `stage17-trigger-browser-regression.log` |
| 修正模拟预览文本并加强断言后复验 | 8 通过 | `stage17-trigger-browser-verified.log` |

界面用例通过模拟宿主返回目录，覆盖真实 ID 提交、不可用旧值、无目录旧宿主、保存其他节点不生成 `set_trigger`、修复及撤销。浏览器视口为 1920×1080 与 1366×768；不等同于真实 JCEF 或系统缩放验收。后续相关界面回归结果见 `proof.json` 与 `stage17-trigger-browser-regression.log`，重复执行不累计为独立测试。

初次 Java 测试因新夹具未设置工作区名称而有 8 项初始化失败，修正夹具后通过。初次浏览器测试的 CSS 选择器与当前 Blockly 不匹配，8 项失败；改用实际可访问按钮名称后通过。原失败日志与截图保留，不作为通过证据。

截图检查发现模拟宿主注入触发器时仍有旧的源码预览文本；夹具现同时生成一致的 IR 与预览，并加入文本断言，8 项复验通过。最终展示证据采用 `browser-verified/`，此前图片只保留为过程记录。

构建包含 Java 编译、中文键门禁、TypeScript 与 Vite；保留既有大包提示。证据及源码 SHA-256 清单位于 `output/stage17-trigger-catalog/proof.json`。

## 边界

触发器依赖目录不是完整过程语义证明。传递调用、未知插件块、被调用过程的真实返回类型、真实 JCEF 和最终统一候选安装／SDK／游戏闭环仍待完成。必需 API 过滤已实现，本轮没有安装外部插件或注入外部插件 API 的完整兼容矩阵。插件标签沿用宿主本地化回退，不承诺本轮完成全部触发器的中英文翻译。
