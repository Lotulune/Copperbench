# U4 交接：帮助/About 与发行硬化

- 负责人：agy / Gemini 3.7 Flash
- 核心合同：UI-Core `1.0`（不要升主版本）
- 日期：2026-08-19
- 约束：只改 `ui-shell/`；不得改 Java、Gradle、`src/main`、`plugins/`；不得直连文件系统

## 必须完成

1. **帮助 / About 页**
   - 导航增加 `help`（`data-testid="nav-help"`）
   - 页面 `data-testid="help-view"`，内容与仓库 `docs/user/README.md` 一致（开发测试说明，不是正式手册）
   - 因为 UI 不能读盘：把说明文案做成 `ui-shell` 内静态内容（例如 `src/content/userGuide.ts`），顶部注明“源文档是 docs/user/README.md”
   - About 区块：`Copperbench 0.1.0`、GPL-3.0、独立衍生自 MCreator `2026.2.33518`、开发测试版、未生产签名
   - 版本轨道表必须与 Core 一致：1.21.1 正式支持；26.2 / 26.1 / 1.20.1 为技术预览 / `TRACK_GENERATE_READY`，不要写成 golden/supported
   - 不要发明打开本机文件的命令或 query

2. **U4 发行硬化（在已有实现上补齐，不要重写外壳）**
   - 已有：无边框标题栏、系统框架回退、`BridgeRecoveryView`、`accessibility.spec.ts`、`adaptive-and-frameless.spec.ts`、`visual-matrix.spec.ts`
   - 补齐：高 DPI 命中区（普通 32×32，触控 44×44）、紧凑/标准/宽屏下帮助页与标题栏不溢出、崩溃恢复不可 Escape 关闭（已有则加强断言）、焦点与 `aria` 完整
   - 设备像素比变化时仍上报 chrome regions（`devicePixelRatio`、viewport、非零 bounds）
   - 不要把帮助按钮做成未声明的窗口 chrome kind；交互控件标 `client`

## 不要做

- 不要改 `src/main/java`、`build.gradle`、NSIS、生成器
- 不要把 26.2 / 26.1 / 1.20.1 标成正式支持或黄金编译
- 不要引入 CDN / 新网络请求
- 不要提交 git
- 不要重做 U3 轨道/迁移页，除非帮助入口需要链过去

## 验证

- `cd ui-shell && npx playwright test`
- 若导航宽度变化导致 `visual-matrix` 快照失败，更新快照
- 现有无障碍与无边框测试必须继续通过
