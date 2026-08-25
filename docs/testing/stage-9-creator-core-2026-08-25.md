# 阶段 9 创作者核心验证记录（2026-08-25）

本记录对应 `PRD-STAGE-9.md` 的当前开发预览实现。它证明核心创作闭环在当前工作树可运行，不代表 G9.2～G9.5 已全部通过，也不替代八生成器或 Windows 11 干净虚拟机证据。

## 已实现范围

- Procedure IR、结构化编辑合同、未知节点载荷保留、类型/连接校验和工作区引用索引。
- React/Blockly Procedure 工作台，以及 UI、MCP、headless 共用的预览与提交服务。
- 变量、标签、语言注册表的稳定 ID、CRUD、引用计数和重命名影响预览。
- `function`、`loottable`、`achievement` 第一方 CRUD 与真实 MCreator 工作区持久化。
- `run_server`、`run_datagen`、`run_gametest` 受管任务；datagen 输出隔离、差异预览、显式发布和失败回滚。

## 自动化结果

| 验证 | 结果 | 覆盖 |
| --- | --- | --- |
| `./gradlew compileJava --rerun-tasks` | 通过 | Java 主源码与跨模块合同编译 |
| `ui-core` 的 `npm test` | 15/15 通过 | v1.0 command/query/result/event Schema 与夹具 |
| `ui-shell` 的 `npm run build` | 通过 | TypeScript、Vite、115/115 i18n；Procedure 独立懒加载 chunk |
| 阶段 9 Java 定向测试 | 48 个执行，0 失败，3 个环境跳过 | Procedure、引用、注册表、真实工作区持久化、Fabric/NeoForge 路由、datagen 发布/回滚、MCP、headless、JCEF/Shell smoke |
| 既有 UI E2E 回归 | 108/108 通过 | Chromium 桌面与 1366 紧凑视口 |
| `stage9-creator-core.spec.ts` | 6/6 通过 | 注册表重命名、Procedure 节点保存、datagen 预览/取消/确认发布 |

3 个跳过项均为当前环境无法提供真实 JCEF 宿主的 smoke 场景，不能据此宣称生产 JCEF 门禁通过。构建仍报告 Blockly 懒加载 chunk 约 792 KiB 的体积警告；它不阻断当前构建，但应在后续做包体和首次打开性能测量。

## 手工界面检查

在 `1024 x 768` 视口验证了 datagen 任务完成后的完整交互：查看暂存差异、打开发布确认对话框、取消不改变修订、再次确认发布、工作区修订从 42 增至 43，并且已发布任务不再显示重复发布入口。页面没有水平溢出或控件遮挡。

## 未关闭门禁

- 500 节点 Procedure 可用性、2,000 元素/10,000 引用查询 P95 小于 300 ms，以及正式键盘/a11y 审计。
- Function 命令级诊断与函数标签、Loot Table 专用池/条目编辑、Advancement criteria 与父级循环防护。
- 语言 CSV/JSON 导入导出、主/回退语言和缺失/重复键报告。
- Fabric/NeoForge 26.2、26.1.2、1.21.1、1.20.1 的阶段 9 八生成器黄金编译和专用服务端 readiness/故障矩阵。
- 真实 JCEF 宿主、Windows 11 干净虚拟机安装/升级/离线启动与数据保留。CI/发行入口在仓库所有者明确授权前不修改。
