# Copperbench remaining work

Published versions and package-specific verification are maintained in [current downloads](releases/current.md). See [current status](status.md) for source capabilities and preview boundaries.

## 下一阶段：方向调整

依据与取舍见 [ADR-0019](adr/0019-agent-harness-and-asset-workbench.md)（提议中）。按顺序做，前一组没有结论时不展开后一组。

**先做：让默认路径符合新定位**

1. 改写内置对话的系统指令（`AgentWorkspaceContext.instructions()`）、[Agent 接入指南](ai/getting-started.md)和 [SDK 路线](../sdk/agent-workflows.md)：默认直接编写工作区内的 Java、JSON 与资源，结构化元素作为可选捷径。完成标准：重跑对照任务时，Copperbench 组不再因为先读契约和创建元素而多花成本。进展：内置指令、Agent 接入指南和 [Python SDK 说明](../sdk/python/README.md)已改为原生路线，并加入 `native_edit_brief()`、临时文件目录和 GameTest 生存玩家模板；2026-11-20、11-21 两轮对照中 Copperbench 组没有再读字段契约或创建元素，独立评分与裸 Agent 组相同，端到端耗时仍是裸 Agent 组的 1.4 到 2.5 倍，多出的部分主要是 GameTest 验收（每格一次运行，记录在本地 `output/`，不入库）。[SDK 路线](../sdk/agent-workflows.md)也已改为原生路线在前，结构化元素为可选捷径，W1 至 W3 保留为固定回归。
2. 已完成：修掉对照实验暴露的三处绕行：Fabric 构建以 `cmd.exe /c gradlew.bat` 启动，父进程环境带 `NoDefaultCurrentDirectoryInExePath=1` 时首次构建失败（`Fabric1211ProcessRunner`）；`update_workspace_file` 不能新建手写文件；独立宿主拿不到 `import_asset` 所需的授权。另需复核被试报告的两点：物品 `textureBase64` 被接受但不生效，方块元素生成的战利品表没有掉落。
3. 把对照任务做成可重跑的评测：同一 GameTest 宿主加载两组产物，断言注册 ID、配方产出、挖掘条件和物品行为。完成标准：每次运行给出逐项通过/失败，而不是只有“构建成功”。进展：Emberkit T1/T2 的逐项评分器已在两轮对照中使用，代码在 `claude/agent-eval-harness` 分支，尚未合入。
4. MCreator 2026.3 正式发布后评估同步成本（冲突范围、产品外壳受影响的服务、生成器差异），再决定是否执行。NeoForge 26.3 元素覆盖、26.3 数据包与资源包生成器都取决于这一步。

**其次：数据包**

5. 确认数据包工作区打开后只提供该生成器支持的元素类型，并在游戏内验证一次加载（测试存档或 `packloader` 客户端）。当前只验证了创建和文件骨架。
6. 对 `src/main/data` 下的原始 JSON 做结构校验和引用检查，覆盖没有元素类型的内容；不为每个注册表新做编辑器。
7. 导入现有数据包 ZIP 为工作区；补中英文用户指南。26.2 与 26.3 的生成器随第 4 项取得。

**其次：资产**

8. 元素编辑器中的纹理与资源字段改用带缩略图的选择器（现为文本框加候选列表）。
9. 资产删除与“清理候选”的执行动作，受恢复点保护并预览受影响的引用。
10. 资产分类区分纹理用途（方块、物品、实体、界面等）并纳入数据侧文件（结构 `.nbt` 等）；补声音试听与声音事件注册。

**再次：范围与流程**

11. 为“接管现有 Fabric/NeoForge Gradle 工程”写范围决定：哪些能力不依赖 `.mcreator`，元数据放在哪里。
12. 修订[发布流程](build/release-process.md)：不改变已发布产物的改动不走发布授权；入库证据限于发布绑定所需。

**停止投入**：扩充结构化元素类型和字段覆盖；以结构化元素覆盖率作为新版本轨道的发布条件。

## Current maintenance

- Maintain Minecraft, loader, generator and toolchain compatibility; keep user workflows, bilingual guides and regression coverage aligned with the supported versions.
- Independently check the semantics of the historical Fabric Commands remap warning. The [original integrity/runtime follow-up](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/remaining-work.md#historical-integrityruntime-results-and-open-follow-up) retains the reproduced cases and their evidence.
- Revalidate affected source, installation or gameplay paths using the [change-specific checks](status.md#改动后补哪些验收). Existing records keep their source and package identities.

## Additional quality work

- Real JCEF accessibility verification in a known-good Windows accessibility environment.
- Authenticode signing.
- Broader Linux distributions, architectures and installed game-version coverage; macOS and other platform targets require their own implementation and acceptance evidence.

External-agent and unfamiliar-user studies were terminated by the user. They are not pending work or release gates; completed results retain their recorded limits.

Completed stage plans, audits and maintenance narratives are available in the [historical snapshot](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/remaining-work.md). New follow-up belongs on this page only while it remains actionable.
