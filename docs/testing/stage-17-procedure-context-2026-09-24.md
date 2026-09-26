# Stage 17 直接过程上下文与诊断一致性

接续[返回值校验](stage-17-procedure-returns-2026-09-23.md)。本轮补齐已知直接上下文节点的原生工作区校验，统一预览、保存状态、重开、全局诊断与生成准备。**源码回归通过，但传递调用／插件完整语义、安装候选和游戏验证仍未完成，PRD17 总门禁保持开放。**

## 已复现问题

[Stage17ProcedureContextTest](../../src/test/java/dev/copperbench/core/workspace/mcreator/Stage17ProcedureContextTest.java) 通过公开创建与预览入口构造读取 `coord_x` 的过程，将触发器由 `no_ext_trigger` 改为 `mod_serverload`。当前加载的 `mod_serverload` 元数据不提供 `x`，修复前八轨预览却都返回 `canGenerate=true`。红灯 8 项均失败，原日志和 XML 保留于 `output/stage17-procedure-context/`。

进一步检查发现两处相关口径缺口：全局健康汇总没有纳入过程图语义诊断；通用创建／更新及磁盘状态映射固定使用 `valid`，会抹掉已知无效过程的状态。

## 实现

[MCreatorProcedureContextValidation](../../src/main/java/dev/copperbench/core/workspace/mcreator/MCreatorProcedureContextValidation.java) 只读访问已加载的 `BlocklyLoader` 触发器目录、活动生成器支持列表和工作区已启用 API。没有硬编码外部触发器依赖清单，也没有加载新插件或修改环境配置。

对六个已知节点按名称和类型比对：实体 `entity`、源实体 `sourceentity`、直接源实体 `immediatesourceentity`，以及数值 `x/y/z`。不会因都属于 entity 类型便互相替代，不依赖请求附带的 `dependencies` 摘要。`no_ext_trigger` 保留由调用方提供依赖的语义。

稳定代码包括 `PROCEDURE_CONTEXT_MISSING`、`PROCEDURE_TRIGGER_UNKNOWN`、`PROCEDURE_TRIGGER_UNSUPPORTED`、`PROCEDURE_TRIGGER_API_REQUIRED` 和 `PROCEDURE_CONTEXT_CATALOG_UNAVAILABLE`。节点定位沿用现有 `open_procedure_node` 合同。实际测试覆盖直接依赖缺失、正常提供、无外部触发器和未知触发器；目录不可用、缺 API 与不支持触发器分支没有在本轮单独注入验收。

[WorkspaceMutationGateway](../../src/main/java/dev/copperbench/core/application/WorkspaceMutationGateway.java) 提供后台元数据校验接口，由原生网关实现；[WorkspaceApplicationService](../../src/main/java/dev/copperbench/core/application/WorkspaceApplicationService.java) 将其与 IR 基础校验合并，用于过程查询、预览、结构化更新与提取。普通创建和字段更新也计算已知过程错误状态；允许保留 `invalid` 草稿，不冒充生成成功。

全局健康汇总复用相同语义诊断并按既有规则去重，修复后重新计算。[MCreatorWorkspaceStateMapper](../../src/main/java/dev/copperbench/core/workspace/mcreator/MCreatorWorkspaceStateMapper.java) 在读取生成式过程时重新校验，不再一律标记有效。手动管理源码的过程排除于这项生成式正文检查。

[MCreatorGenerationPreparation](../../src/main/java/dev/copperbench/core/workspace/mcreator/MCreatorGenerationPreparation.java) 在原生生成准备入口检查同一上下文规则，失败保留具体代码，发生在依赖设置及生成文件写入之前。该项没有替代实际插件编译器的完整语义检查。

## 验证

八轨公开入口检查分别覆盖：错误预览、节点定位、只读预览不改定义、无效草稿保存、全局恰好 1 条错误与 1 个无效元素、生成准备拒绝且定义／工作区文档字节不变、改成 `player_ticks` 后可生成且错误清零、无外部触发器合法、未知触发器拒绝、通用字段更新及创建保留无效状态、关闭重开后两个过程仍无效。另有 12 组直接节点与触发器匹配检查，验证三类实体和三个坐标的区分。

| 运行 | 结果 | 证据 |
|---|---|---|
| 反例 | 8 失败，均为服务端启动未提供 x 却可生成 | `stage17-context-red.log`、`red.xml` |
| Core／过程／任务相关广泛回归 | 315 通过、5 条件跳过；5 分 11 秒 | `stage17-context-core-regression.log`、`core/` |
| 通用保存和重开状态修复后的相关回归 | 79 通过、0 失败；1 分 24 秒 | `stage17-context-final.log`、`final/` |
| 加强全局精确计数与重开数量断言后 | 20 通过、0 失败 | `stage17-context-counts-verified.log`、`counts.xml` |

全部证据归档于 `output/stage17-procedure-context/`，源码与 XML／日志哈希见 `proof.json`。多轮有重复，不相加声称独立测试总数。5 个跳过项为三个冷工作区探针、工作区健康规模和 Procedure IR 规模测试，本轮不记为通过。构建流程同时执行中文词典门禁、TypeScript 与 Vite；保留既有大包提示。

## 限制与下一步

本轮只验证已知直接上下文节点。调用链上传递的依赖、目标过程实际返回类型、变量隐含上下文、未知块内部依赖及局部依赖提供者仍需实际生成器语义验证。未给不透明插件内容做猜测，也未宣称全部过程兼容。

这些是原生工作区适配器与实际已加载插件元数据的源码测试，使用测试临时工作区；不是安装启动器／随包 SDK、原生 JCEF 或游戏行为证明。没有启动外部 Gradle 依赖设置来验证上下文拒绝路径；没有新候选安装、提交或发布。最终同源候选回放、场景 A～D、跨平台运行及三次独立试作仍按原 PRD 执行。
