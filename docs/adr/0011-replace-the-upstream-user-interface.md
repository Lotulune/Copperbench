# ADR-0011：重写上游用户界面

- 状态：已接受
- 日期：2026-08-16

产品不以调整主题或逐页修补 MCreator 界面为目标，而是使用 JCEF 承载 React/TypeScript 产品外壳，使工作流更直观、简洁、高级并具有沉浸感。新界面通过带 Schema 的命令、查询和事件桥使用工作区、生成器、资产、版本历史和 MCP 能力；业务规则不得散落到 UI 实现中。

产品外壳是自适应桌面工作台，并使用 Windows 无边框窗口。无边框实现必须保留拖动、边缘缩放、最小化、最大化、恢复、Snap Layout、系统菜单、键盘操作、多显示器与高 DPI 行为；原生集成失效时必须能够回退到系统窗口框架。

这是浅分支策略的有意例外，代价是上游 UI 变更无法直接继承。原版 Java 插件可注入工作区标签、菜单按钮和偏好页面，因此这些界面仅通过独立的旧版插件界面尽力兼容，不假定它们能自动进入 React 产品外壳。

## 依据

- [MCreator 官方插件开发说明](https://mcreator.net/wiki/developing-mcreator-plugins)
- [MCreator 官方插件安装与安全说明](https://mcreator.net/wiki/understand-plugins)
