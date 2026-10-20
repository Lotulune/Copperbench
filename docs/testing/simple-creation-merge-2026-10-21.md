# 简洁创作主线集成验证 · 2026-10-21

本轮把简洁模式、五个宿主 CLI、图标、原生文件选择器及 [7 项 Review 修复](simple-creation-fixes-2026-10-21.md) 整理到基于 `b41501d5c` 的独立分支 `codex/simple-creation-mode`。原工作区中其他未提交改动保留，没有整体复制或提交。

集成保留主线已有行为：生成器预览的显式选择与能力限制、精确目标目录范围、按生成器选择导出路径、任务打开流程、对话框焦点栈，以及原生对话框的自动换行与屏幕适配。默认按钮颜色修复落在主线共用的 `NativeDialogTheme`；进度窗口保留主线行高刷新和换行，只补齐百分比需要的高度。

## 本地检查

| 检查 | 结果 |
| --- | --- |
| Java Agent、桥接、原生选择器和绘制回归 | 44 项通过，0 失败、0 跳过。 |
| UI 单元测试 | 73 项通过。 |
| 简洁模式与新建工作区 Playwright | 19 项通过。 |
| Java、TypeScript、Vite、JAR 和 Javadoc | 通过。 |
| 中文与英文检查 | 424 个引用键、1746 条消息，通过。 |
| 仓库 Markdown 链接 | 通过。 |

本地证据位于独立工作树的 `output/simple-creation-merge/`。此前 [CLI 实机连接](simple-creation-fixes-2026-10-21.md) 的源和模型结果按当时记录保留；本轮不重复把 fixture 称作实机工具执行。未启动 Minecraft，也未发布安装包。

提交后的完整主线必需检查由 PR 上的 GitHub Actions 记录。
