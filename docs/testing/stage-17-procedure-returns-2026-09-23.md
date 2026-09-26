# Stage 17 过程返回值校验与身份解析

接续 [v30 过程调用生成链](stage-17-procedure-calls-2026-09-23.md)。本轮修复两个源码层缺口：已知返回节点连接错误类型或没有返回值时缺少诊断；返回值调用仍把稳定 UUID 当成过程名称。**69 个不同源码测试通过，尚未冻结新候选、安装复验或执行游戏。返回／调用／上下文完整语义仍未关闭。**

## 反例

[Stage17ProcedureReturnValidationTest](../../src/test/java/dev/copperbench/procedure/Stage17ProcedureReturnValidationTest.java) 为 number、logic、string、entity、itemstack 构造五组已知不兼容输入，并构造缺少返回值的节点。修复前六组均无法取得预期诊断；五组合法值与未知插件原文保留对照通过。

[Stage17ProcedureReturnCallGenerationTest](../../src/test/java/dev/copperbench/core/workspace/mcreator/Stage17ProcedureReturnCallGenerationTest.java) 使用真实工作区、公开创建入口和实际上游 Blockly 生成器。八轨中的名称调用可生成 `targetProcedure.execute(...)`，同一目标 UUID 调用则均出现 `Procedure return value block is calling nonexistent procedure <UUID>`。红灯合计 20 项中 14 失败、6 通过，日志与 XML 位于 `output/stage17-procedure-returns/red/`。

## 修复与边界

[ProcedureIrCodec.validate](../../src/main/java/dev/copperbench/procedure/ProcedureIrCodec.java) 对已知返回节点新增：

- `PROCEDURE_RETURN_VALUE_REQUIRED`：没有连接返回值。
- `PROCEDURE_RETURN_TYPE_MISMATCH`：已知输出类型与返回节点要求不符；保留返回节点 ID 和实际输入端口定位，英文消息说明预期和实际类型。

类型来自已知内置节点，不信任请求中的显示 `kind` 或依赖摘要。未知插件／多态输出不作猜测，不改其原始 XML，仍交给相应生成器校验。没有宣称所有输出类型、控制流和第三方扩展都完成了静态证明。

既有 Core 预览和任务层复用这一校验。[WorkspaceApplicationServiceTest](../../src/test/java/dev/copperbench/core/WorkspaceApplicationServiceTest.java) 证明错误返回值的预览 `canGenerate=false`、`canSaveDraft=true`，诊断动作定位到返回节点及 `VALUE` 端口；保存草稿后元素状态为 `invalid`。[八轨任务预检](../../src/test/java/dev/copperbench/generator/Stage17ProcedureTaskValidationTest.java) 均检出不兼容返回值。

[ProcedureRetvalBlock](../../src/main/java/net/mcreator/blockly/java/blocks/ProcedureRetvalBlock.java) 使用已存在的 `WorkspaceProcedureTargets`，按显式 `procedureId`／旧 `procedure` 字段解析。非空 UUID 优先于过期名称提示；缺失身份不能回退到名称；不再取 XML 第一个字段作为目标。上游 `DynamicBlockLoader` 的标准字段名已核查为 `procedure`，保留旧名称调用兼容。

八轨数值返回调用均验证：名称与 UUID 生成相同代码、目标方法实际出现、生成器返回类型为 number、名称提示在 UUID 前面时仍绑定正确、缺失 UUID 产生错误且不生成错误回退调用、读取／生成不改变调用方定义文件、重开后结果一致。测试是 Blockly 生成结果验证，未进行本轮独立 Mod JAR 编译或游戏执行；不能推广为所有返回类型的运行通过。

## 回归证据

| 测试范围 | 结果 |
|---|---|
| 八轨原语句调用生成 | 8 通过 |
| 八轨数值返回调用生成 | 8 通过 |
| Core 应用服务，包括返回预览／草稿／定位 | 25 通过 |
| 八轨任务层预检与真实正文保留 | 8 通过 |
| 既有 IR 编解码 | 7 通过 |
| 新增返回值校验与保留对照 | 12 通过 |
| 显式启用的 500 节点规模测试 | 1 通过 |

常规回归为 68 通过、1 条件跳过；随后显式启用规模门禁，补齐该 1 项。总计 69 个不同测试通过。保留日志、每套 XML、源码和构建产物哈希，清单为 `output/stage17-procedure-returns/proof.json`。

两个中间构建失败也保留：先由中文词典门禁发现新增诊断键缺失，补齐至 386/386；之后新测试对不同 record 类型的公共流调用 `code()` 导致测试编译失败，改为各自检查后才成功执行。没有修改断言来接受错误返回值或 UUID 调用。

## 剩余项

调用点期望类型与目标过程实际返回类型、传递参数／依赖元数据的新鲜度、分支与终止控制流、外部触发器可提供的上下文、其他具名过程字段和不透明子树引用仍需进一步验证。旧 `ProcedureGUI` 中存在触发器依赖检查，但当前 Core IR 校验未涵盖相同上下文规则，不能把此次类型检查当作上下文验收。

安装包／公开 SDK／独立 JAR／双平台运行尚未复验本轮源码；v30 的旧证明不转记到这些改动。UI 与真实 JCEF、同一最终候选回放、场景 A～D 和三次独立试作仍按原 PRD 范围执行。未提交或发布，未安装更新 Ubuntu。
