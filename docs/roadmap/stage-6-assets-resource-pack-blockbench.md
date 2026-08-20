# 阶段 6：资产、资源包与 Blockbench

- 状态：G5 核心资产门禁完成；发布批次与测试客户端准备已接入领域服务；Fabric 1.21.1 真实 `runClient` 加载已宣称（ResourceManager 列出 `file/copper_ready_pack.zip`）
- 依赖：阶段 4、阶段 5
- 对应门禁：G5

## 目标

在不重做专业模型编辑器和上游资源包基础工具的前提下，建立可版本化、可验证、可由 AI 操作的资产工作流。

## 范围

- 新资产浏览器、稳定资产标识、分类、搜索、预览和引用关系。
- 模型、纹理、UV、动画、语言和声音引用检查。
- 受管 Blockbench 启动、安装/版本检测、项目文件关联和未保存状态处理。
- TypeScript Blockbench 桥接和可选 Blockbench MCP 编排。
- `.bbmodel` 源文件与导出资产关联、往返差异和格式诊断。
- 继承独立资源包工作区、原版资源覆盖、纹理编辑入口、测试客户端和 ZIP 导出。
- 资产历史、比较、替换、恢复和发布批次。
- 资产相关 MCP 工具与任务范围限制。

## 不在范围

- 在产品内重做 Blockbench 模型/UV/动画编辑器。
- 云资产库或账户同步。
- 未核查的着色器、字体和任意资源 UI 承诺。

## 交付物

- Asset Schema 与 Reference Graph v1。
- Blockbench 受管进程和 TypeScript 桥接插件。
- 模型/纹理/动画往返黄金样例。
- 独立资源包黄金工作区和 ZIP 产物测试。
- 资产 MCP 工具、权限范围和日志。
- 上游纹理编辑器的明确入口；如仍为 Swing，则通过受控兼容窗口打开。

## 关键场景

1. 从工作区选择实体资产并打开 Blockbench。
2. 修改模型和纹理后导回，保持资产标识与引用。
3. 检测命名、路径、动画或版本不兼容并定位到具体资产。
4. 用 MCP 替换纹理并在测试客户端验证。
5. 将原版资源加入独立资源包覆盖并导出 ZIP。
6. 回退资产版本并证明引用恢复一致。

## 风险

- Blockbench 与产品同时修改同一文件。缓解：任务租约、文件哈希和冲突对话框。
- Blockbench MCP 权限过宽。缓解：每次任务只签发资产范围令牌。
- 上游资源包 UI 支持深度不明。缓解：源码与运行样例核查后再进入兼容矩阵。

## 退出条件

- [x] G5 核心资产门禁通过（U3、Blockbench 版本/启动/关闭、租约、异常退出、外部修改冲突、往返黄金样例和确定性 ZIP 导出）。
- [x] Asset Schema、稳定路径身份 ID、内容 SHA-256、引用图和越界/缺失诊断可重复执行。
- [x] 资产浏览器已接入 UI-Shell，包含搜索、分类、状态、详情和引用关系。
- [x] 资产列表与引用诊断已通过可选 MCP 工具暴露，并写入现有审计日志。
- [x] Blockbench JCEF 窄桥已接入产品壳，仅接受 `status` 和 `openAsset(assetId)`；Java 端执行路径授权与进程管理。
- [x] 往返编辑不会静默断开模型、纹理和动画引用；导回前报告格式、纹理、元素和动画差异。
- [x] Blockbench 异常退出不会损坏已提交工作区状态；返回诊断并释放资产租约。
- [x] 资源包 `pack.mcmeta` 校验与 ZIP 导出可重复，条目固定排序和时间戳。
- [x] 资产导入/替换/恢复进入本地历史边界；MCP 资产查询继续写入审计日志。
- [x] 发布批次可创建确定性 ZIP、写入 `.copperbench/publish-batches` 清单并进入历史边界。
- [x] 资源包可导出到 `run/resourcepacks` 并写入 `options.txt`；`prepare_resource_pack_client` 仍保持 `clientLaunched=false`。
- [x] Fabric 1.21.1 测试客户端真实加载已宣称：`runClient` 看到就绪标记，且 ResourceManager 列出 `file/copper_ready_pack.zip`。证据：[`resource-pack-1211-client.json`](../../evidence/stage-8/2026-08-20/resource-pack-1211-client.json)。
