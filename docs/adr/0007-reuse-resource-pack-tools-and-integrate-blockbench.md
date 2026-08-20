# ADR-0007：复用上游资源包工具并集成 Blockbench

- 状态：已接受
- 日期：2026-08-16

## 决定

保留并维护 MCreator 现有的独立资源包工作区、原版资源覆盖、内置纹理编辑、自动加载测试客户端和 ZIP 导出能力，不重新实现同类编辑器。

模型、UV、模型动画和复杂贴图工作流以 Blockbench 集成为主。首期由产品启动和管理外部 Blockbench 桌面进程，通过插件、文件协议和可选 MCP 实现资产往返，不把完整 Blockbench 嵌入主窗口。产品负责资产导入导出、引用关系、版本记录、兼容检查、错误定位和 AI/MCP 编排；Blockbench 负责专业创作界面。

## 原因

上游从 MCreator 2024.4 起已正式提供独立资源包工作区。重写现有功能不会形成产品差异，反而会增加维护成本。Blockbench 已拥有成熟的 Minecraft 资产创作流程，更适合作为专业编辑器。

## 后果

- 首期资源包工作重点是增强资产治理与验证，不是新建纹理或模型编辑内核。
- Blockbench 往返必须处理格式版本、命名、纹理路径、动画引用和丢失能力报告。
- 产品需要检测 Blockbench 安装、版本和插件状态，并能处理进程退出与未保存资产。
- Blockbench MCP 只能获得完成当前资产任务所需的工作区范围，不继承主产品的完全访问权限。

## 依据

- [MCreator 官方资源包制作说明](https://mcreator.net/wiki/how-make-minecraft-resource-pack)
- [MCreator 官方 Blockbench 实体动画说明](https://mcreator.net/wiki/entity-model-animations)
- [MCreator 1.21.1 资源包生成器](https://github.com/MCreator/MCreator/tree/master/plugins/generator-1.21.1/resourcepack-1.21.1)
- [MCreator 当前资源包生成器](https://github.com/MCreator/MCreator/tree/master/plugins/generator-26.1.x/resourcepack-26.1.x)
