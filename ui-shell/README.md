# Copperbench UI Shell

Copperbench 的 React 19、TypeScript 与 Vite 前端，同时用于原生 JCEF 桌面宿主和浏览器开发预览。产品功能见[项目 README](../README.zh-CN.md)，运行环境见[开发说明](../docs/build/development-setup.md)。

## 架构与运行边界

- **CoreBridge**：`src/bridge/index.ts` 是绑定入口。检测到原生宿主时使用 `JcefCoreBridge`；仅 Vite 开发模式可以使用 `MockCoreBridge`。生产构建缺失宿主时使用 `UnavailableCoreBridge`，不会静默展示模拟工作区。
- **共享合同**：UI 与 Java Core 通过握手、Command、Query 和事件交换数据；当前操作使用 Core `1.0`。版本协商失败显示启动错误。Schema 驱动的字段、约束和能力以宿主返回的编辑器合同为准。
- **两种创作模式**：简洁创作集中显示作品、助手对话和常用设置；专业工作台提供元素、源码、关系图、资产、数据与历史等视图。二者使用同一工作区，专业 Agent 面板共享助手会话。
- **专用宿主桥**：窗口控制、工作区选择、文件操作、Blockbench、Agent 和 Python 通过各自桥接口访问桌面能力。普通浏览器预览不能运行本机 CLI 或 CPython；对应入口显示不可用状态。
- **语言与主题**：支持简体中文／英文、亮色／暗色／跟随系统主题。语言与主题偏好保存在本地；原生宿主提供相应设置同步。
- **示例数据**：`src/mock/scenarios.ts` 导入 `ui-core/fixtures/v1.0/scenarios/` 的合同场景。浏览器开发模式提供场景选择器，原生宿主不显示它。
- **交互保护**：修订与文件哈希冲突、未保存草稿、字段诊断、键盘焦点和可访问名称由组件及共享 Hook 处理；验证范围以对应测试和原生验收记录为准。

## 目录结构

```text
ui-shell/
├── src/
│   ├── bridge/         # Core、窗口、Agent、Python、源码和资产等桥接口
│   ├── components/     # 两种创作模式、专业编辑器、预览与导航
│   ├── context/        # 工作区、模式和 Agent 会话状态
│   ├── hooks/          # 草稿、主题、焦点与交互逻辑
│   ├── i18n/           # 中英文词条、语言选择与显示标签
│   ├── mock/           # 浏览器开发适配器与合同场景入口
│   ├── onboarding/     # 首作引导状态
│   ├── styles/         # 语义令牌、工作台布局与动效
│   └── types/          # UI 合同与专用能力类型
├── e2e/                # Playwright 场景、编辑器和布局检查
├── tests/              # Node 单元测试与服务辅助工具
├── scripts/            # i18n 检查和 README 截图工具
├── playwright.config.ts
└── package.json
```

## 开发预览

在仓库根目录安装依赖并启动前端：

```sh
npm ci --prefix ui-core
npm ci --prefix ui-shell
npm run dev --prefix ui-shell
```

Vite 默认使用 `http://localhost:5173`；以终端实际打印的地址为准。原生桌面入口是仓库根目录的 `./gradlew runProductShell`，Windows 使用 `.\gradlew.bat runProductShell`，需要带 JCEF 的 JBR 25。

## 构建与检查

以下命令在 `ui-shell` 目录执行；按改动选择所需检查，命令不代表本次已经通过：

```sh
# i18n、TypeScript 与生产打包
npm run build

# Node 单元测试 / TypeScript SDK 测试
npm test
npm run test:sdk

# 首次准备浏览器，再运行受影响的用例
npx playwright install chromium
npx playwright test e2e/source-workbench.spec.ts --project=chromium

# 需要完整浏览器回归时
npm run test:e2e
```

默认 Chromium 项目覆盖 1920 × 1080 与 1366 × 768；`visual-matrix.spec.ts` 另有四组缩放视口。普通工作台用例预设专业模式，简洁创作用例自行覆盖首访状态。测试数量以实际运行结果为准。

Playwright 从当前 `ui-shell` 启动独立的 `http://127.0.0.1:5183` 服务；端口占用时失败，不复用其他进程。可通过 `COPPERBENCH_E2E_PORT` 选择 `1024–65535` 范围内的端口。性能工具使用独立的 `COPPERBENCH_PERF_PORT`（默认 `5179`）。

`npm test` 包含 Vite 监听、HMR 握手和 Playwright 服务冲突回归；SSR 单元测试只加载生产模块，不创建 HTTP 或 WebSocket 监听。普通 `npm run dev` 保留 HMR。

## README 截图

在仓库根目录运行以下命令，用实际拍摄日期替换参数：

```sh
node ui-shell/scripts/capture-readme.mjs YYYY-MM-DD
```

脚本使用独立端口 5197，生成简洁创作、专业工作台、元素编辑、源码、Python 和关系图的中英文亮色截图，以及输入／图片哈希清单。截图使用内置示例数据，原生能力仍按实际状态显示。README 在两列 Markdown 表格中展示三排截图，每张配有名称和简短说明。完整范围与更新步骤见[截图说明](../assets/screenshots/README.md)。
