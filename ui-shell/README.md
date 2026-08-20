# @mod-creator/ui-shell (Copperbench UI Product Shell)

这是基于 React 19 + TypeScript + Vite 构建的 Copperbench 桌面产品外壳与自适应工作台工程（阶段 U1 产物）。

## 架构原则与边界

- **CoreBridge 接口 + 单一绑定点（U2 准备）**：组件只依赖 `src/bridge/CoreBridge.ts` 接口（`sendCommand` / `sendQuery` / `negotiateHandshake` / 事件订阅），U1 绑定内存 Mock（`src/bridge/index.ts`），U2 换绑 JCEF 实现时组件与状态层零改动；**严格不直接连接真实 JCEF Bridge 或本机文件系统**。
- **启动协议协商**：UI 以 `supportedSchemaVersions: ['0.1']` 执行 handshake，不兼容时渲染结构化启动错误（NFR-UI-10 / ui-core handshake schema），不回退无类型 JSON。
- **i18n 中文为主**：产品决策（2026-08-17）界面主语言为中文；合同 LocalizedText 经 `t()` + `src/i18n/zh.ts` 词典渲染，缺词条回退英文 fallback；技术标识（元素名、枚举、日志原文、MCP 档位）保留英文。
- **场景数据单一事实源**：`src/mock/scenarios.ts` 直接 import `ui-core/fixtures/v0.1/scenarios/*.json`，不手工复制场景数据，保证 mock 回放与 JSON Schema 校验器消费同一份合同（含 `expectedUi` 焦点/播报契约）。
- **Schema 驱动编辑器**：`ElementInspector` 完全由 `get_mod_element_editor` 投影（sections/fields/controls/constraints/capabilities）驱动渲染，UI 不硬编码任何字段或加载器能力规则。
- **设计系统**：定制 Vanilla CSS 语义令牌（`tokens.css` / `global.css`），覆盖暗色 (Dark) 与亮色 (Light) 双主题、WCAG AA 对比度、自适应无边框窗口与系统框架回退。
- **13 个官方场景全量覆盖**：对 `ui-core/fixtures/v0.1/scenarios` 的全部 13 种状态（就绪、空白、加载中、校验失败、权限拒绝、版本冲突、加载器差异、离线、构建中、外部进程退出、渲染崩溃恢复、协议不兼容、元素创建）均有 Playwright 断言。
- **可访问性基线（NFR-UI-08）**：对话框焦点陷阱 + Esc 关闭 + 焦点归还（`useDialogA11y`）、`expectedUi.focusTarget` 契约焦点、`expectedUi.announcement` 经 aria-live 播报、任务进度与日志流使用非打断 live region、`role="alert"` 顶层诊断横幅。
- **窗口桥桩（NFR-UI-05/06）**：`src/mock/windowBridge.ts` 定义最小化/最大化/关闭接口形状；真实原生窗口行为由阶段 4 的 Java 宿主桥实现，异常时回退系统窗口框架。

## 目录结构

```text
ui-shell/
├── bridge/                          # CoreBridge 接口、握手类型与绑定入口
├── e2e/                             # Playwright 端到端基线测试套件
│   ├── accessibility.spec.ts          # 焦点陷阱、Esc、live region、阻断恢复
│   ├── adaptive-and-frameless.spec.ts # 自适应、无边框与主题测试
│   ├── commands.spec.ts               # Command / Query 交互与变异测试
│   └── scenarios.spec.ts              # 13 个契约场景回放与断言测试
├── src/
│   ├── components/                  # 设计系统核心组件（中文为主文案）
│   │   ├── BridgeRecoveryView.tsx      # JCEF 崩溃恢复界面 (NFR-UI-10)
│   │   ├── CreateElementModal.tsx      # 元素创建模态框
│   │   ├── ElementInspector.tsx        # Schema 驱动的元素检查器
│   │   ├── FramelessTitlebar.tsx       # 无边框标题栏与窗口控制 (NFR-UI-05/06)
│   │   ├── ModElementsWorkbench.tsx    # 模组元素工作台 (网格/表格/过滤)
│   │   ├── NavRail.tsx                 # 左侧主导航轨
│   │   ├── RevisionConflictModal.tsx   # 版本冲突仲裁弹窗 (FR-WS-05)
│   │   ├── ScenarioSwitcher.tsx        # 13 场景即时切换托盘
│   │   ├── SchemaIncompatibleView.tsx  # 协议版本协商失败屏
│   │   ├── SecondaryViews.tsx          # 资产/历史/AI/插件扩展占位
│   │   ├── StatusFooter.tsx            # 全局连接、权限与任务底栏
│   │   ├── TaskDrawer.tsx              # 构建/运行长任务流式日志抽屉
│   │   └── WorkspaceHub.tsx            # 工作区总览、健康看板与顶层诊断横幅
│   ├── context/
│   │   └── WorkbenchContext.tsx        # 状态管理、调度 Hook 与启动握手
│   ├── i18n/
│   │   ├── index.ts                    # t() 渲染机制（中文为主）
│   │   └── zh.ts                       # 中文词条表
│   ├── hooks/
│   │   └── useDialogA11y.ts            # 对话框焦点陷阱 / Esc / 焦点归还
│   ├── mock/
│   │   ├── mockBridge.ts               # 内存级 Command/Query/Event 模拟适配器
│   │   ├── scenarios.ts                # ui-core 官方 fixtures 导出（单一事实源）
│   │   └── windowBridge.ts             # 原生窗口桥桩（阶段 4 接真实实现）
│   ├── styles/
│   │   ├── global.css                  # 全局样式、响应式网格与 sr-only
│   │   └── tokens.css                  # 明暗主题设计令牌
│   ├── types/
│   │   └── contract.ts                 # UI-Core v0.1 强类型定义
│   ├── App.tsx                         # 外壳根组件与全局播报 live region
│   └── main.tsx                        # React 入口
├── playwright.config.ts             # Playwright 双视口 (1920x1080 / 1366x768) 配置
└── package.json
```

## 运行与测试

```bash
# 启动本地开发服务 (http://localhost:5173)
cd ui-shell
npm run dev

# 构建生产版本 (TypeScript 编译 + Vite 打包)
npm run build

# 运行 Playwright 全量自动化测试基线 (50/50 tests = 25 用例 × 2 视口，含中文文案断言)
npm run test:e2e
```

## 需求映射

| 能力 | 需求编号 |
| --- | --- |
| 修订冲突结构化拒绝与仲裁 | FR-WS-05 |
| Schema 驱动编辑器与加载器差异保留 | FR-MOD-04 / FR-MOD-05 |
| 任务进度、日志与取消反馈 | FR-BUILD-02 |
| 结构化诊断与可执行动作 | FR-BUILD-03 |
| 权限拒绝解释与提升路径 | FR-SEC-01 / FR-MCP-03 |
| 崩溃恢复不伪造已保存 | NFR-UI-10 |
| 无边框窗口与系统框架回退 | NFR-UI-05 / NFR-UI-06 |
| 键盘导航、焦点与 live region | NFR-UI-08 |
| 启动协议版本协商与结构化失败 | NFR-UI-10 / §8.2 |
| 界面主语言中文（LocalizedText 消费） | NFR-UI-08（本地化） |
| 离线分发（无 CDN） | NFR-UI-09 |
