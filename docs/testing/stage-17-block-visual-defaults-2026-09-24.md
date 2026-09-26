# Stage 17：客户端发现后的方块默认资源修复

本轮根据 [v31 被测 JAR 客户端验收](stage-17-v31-packaged-client-2026-09-24.md)定位并修复两项源码问题。修复晚于 v31 冻结，尚未进入新候选包，也未完成修复后的两平台客户端复验；不得改写 v31 的问题记录为已通过。

## 改动与边界

1. Fabric 1.20.1、1.21.1 的 `block.definition.yaml` 原来为方块名称生成 `item.<modid>.<name>`，而 v31 1.21.1 客户端实际显示未翻译的 `block.<modid>.<name>`。两条旧轨道改用 `block.*` 名称键；26.x 的版本约定保留。
2. `MCreatorWorkspaceMutationGateway.newBlock` 原来未初始化 `renderType` 与基础材质，导致默认值 0 不匹配普通方块模型模板。新建方块现在使用渲染类型 10 和 `minecraft:stone`，生成普通六面立方体模型。`BlockFieldContract.capabilities` 同步暴露这两个默认值。明确提供的合法字段仍由既有 `applyBlock` 应用；普通更新不会重新调用 `newBlock`，不静默重写旧工程。

这只修复新建默认路径。v31 旧工作区中已持久化的 `renderType=0` 不会自动迁移；应通过公开编辑明确修复后重新生成与验收。此处也不宣称全部自定义渲染类型、资源组合或第一人称美术表现已通过。

## 验证

执行：

```powershell
pwsh -NoProfile -File scripts/run-gradle-external.ps1 test --tests dev.copperbench.core.workspace.mcreator.Stage17BlockConsistencyTest --offline --max-workers=1
```

结果为 **11 通过、0 失败、0 错误、0 跳过**。其中八个真实原生生成器工作区覆盖 Fabric/NeoForge 的 1.20.1、1.21.1、26.1.2、26.2：公开创建、持久化、Java 生成、方块模型文件、六面与粒子材质引用、对应版本名称键、修改硬度与重开。另有非法字段原子性及两种旧工程差异处理回归。构建耗时 46 秒，测试报告时间 16.569 秒。

模型断言读取实际生成的 JSON，确认 parent 为 `block/cube`，七个纹理引用均为 `minecraft:block/stone`；语言断言读取实际 `en_us.json`，确认版本对应键的值为请求中的 `Resonance Forge`。本轮未启动八轨 Minecraft，也未把模板生成通过当成游戏运行通过。

证据为 `output/stage17-v31/post-v31-block-visual-regression.xml`、同名 `.log` 及 `post-v31-block-visual-proof.json`。v31 源码冻结目录、发行产物与 Ubuntu 安装包均未替换；本轮无提交或发布。
