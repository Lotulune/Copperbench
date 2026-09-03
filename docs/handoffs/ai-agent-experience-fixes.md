# Copperbench：给后续 AI Agent 的改进任务书

日期：2026-09-02
来源：外部 AI agent（Grok）在已安装产品上，用工作区 `D:\AICoding\testmod2`（Fabric 26.1.2）完整走一遍「万剑归宗」模组开发。
目标：按优先级修产品，使 **外部 AI 客户端能真正通过 Copperbench MCP 做模组**，并且 **安装包上的测试客户端能启动**。

不要把本文件当成已完成证明。下列每条都必须改代码、补测试、给可复现证据。

---

## 0. 必读与边界

仓库：`D:\AICoding\Minecraft_ModCreator`
已安装产品：`C:\Program Files\Copperbench`（`jdk/bin/java.exe` 存在；**没有** `jdk/jbr25_win_64`）
用户数据：`%USERPROFILE%\.copperbench`
评测工作区：`D:\AICoding\testmod2`（modid `testmod2`，generator `fabric-26.1.2`，workspaceId `2fe545df-81c2-4521-ad18-32e2ecc7e698`）

硬规则：

- 不要把「测试里能起 MCP」当成「安装包能起 MCP」。
- 不要把源码树 JDK 布局当成安装包 JDK 布局。
- 不要在「AI 与 MCP」页显示「已连接」，除非真有 MCP 客户端连上。
- 不要让 `runClient` 在 JAVA_HOME 无效时只报 Gradle wrapper 原文；要报可操作的诊断码和解析到的路径。
- 不要覆盖用户代码块（`// Start of user code block`）和 `wanjian` 包里的评测代码。

---

## P0-1 安装包测试客户端 JAVA_HOME 无效（用户刚踩到）

### 现象

产品外壳点「测试客户端」后日志：

```
Starting Fabric 26.1.2 run_client from revision 0
Fabric 26.1.2 generation completed: 27 files
ERROR: JAVA_HOME is set to an invalid directory: C:\Program Files\Copperbench\jdk\jbr25_win_64
Task failed. Error ID: 5b69333b-0ee7-4584-a70e-c15af128e306
```

任务失败包装为：`Fabric 26.1.2 client did not reach the readiness marker`（`GradleWorkspaceTaskGateway.java`）。

### 根因（已核实）

两套 JDK 布局被混用：

| 布局 | 路径 | `java.exe` |
| --- | --- | --- |
| 源码树 | `jdk/jbr25_win_64/bin/java.exe` | 存在 |
| 安装/导出 | `jdk/bin/java.exe` | 存在；`jdk/jbr25_win_64` **不存在** |

导出在 `platform/windows/windows.gradle`：

```gradle
into('jdk') { from 'jdk/jbr25_win_64' }
```

`WindowsDistributionLayout.REQUIRED_ENTRIES` 也要求 `jdk/bin/java.exe`。

但运行时把源码相对路径拼到安装根上：

1. `CopperbenchProductShell.open` 把 `distributionRoot` 设成 `user.dir`（安装目录 `C:\Program Files\Copperbench`）。
2. `Fabric1211WorkspaceTaskGateway` / `NeoForge1211WorkspaceTaskGateway` 调用
   `Fabric1211ProcessRunner.system(marker, distributionRoot.resolve(profile.jdkRelativePath()))`。
3. `Fabric1211Generator.Profile.jdkRelativePath()` 对 Java 25 轨道返回 `"jdk/jbr25_win_64"`。
4. `Fabric1211ProcessRunner` 无条件
   `builder.environment().put("JAVA_HOME", javaHome.toString())`。
5. `gradlew.bat` 检查 `%JAVA_HOME%/bin/java.exe`，失败。

`net.mcreator.gradle.GradleUtils.getJavaHome()` 会用 `System.getProperty("java.home")`（正确的 `C:\Program Files\Copperbench\jdk`），但 **Fabric/NeoForge 任务网关不用这条路**，被 ProcessRunner 覆盖。

### 要求的修复

新增一个共享解析器（建议放 `dev.copperbench.generator.BundledJdkLocator`），对 `distributionRoot` + 目标 `javaRelease`：

1. 若 `{root}/jdk/bin/java.exe` 存在 → 用 `{root}/jdk`（安装包）。
2. 否则若 `{root}/jdk/jbr25_win_64/bin/java.exe` 存在且需要 Java 25 → 用该目录（源码树）。
3. 否则若 `{root}/jdk/jdk21_win_64/bin/java.exe` 存在且需要 Java 21 → 用该目录。
4. 否则回退 `System.getProperty("java.home")`，且必须仍含 `bin/java.exe`。
5. 全部失败 → 结构化诊断，例如 `BUNDLED_JDK_MISSING`，正文包含尝试过的路径，**不要**再把无效路径塞进 `JAVA_HOME`。

接线：

- `Fabric1211WorkspaceTaskGateway` 构造函数里的 `distributionRoot.resolve(profile.jdkRelativePath())`
- `NeoForge1211WorkspaceTaskGateway` 同样位置
- `WindowsDistributionLayout.toJson()` 的 `"bundledJdk": "jdk/jbr25_win_64"` 改为同时描述两种布局

### 验收

- 在 **安装布局**（只有 `jdk/bin/java.exe`）下 `RUN_CLIENT` 不再写出 `jdk/jbr25_win_64`。
- 在 **源码布局**（`jdk/jbr25_win_64`）下开发机测试仍绿。
- 单测覆盖两种假文件系统布局，不要只测源码树。
- 用已安装 Copperbench 打开 `D:\AICoding\testmod2`，点测试客户端：Gradle 能找到 Java。游戏是否进标题画面另计，但 JAVA_HOME 错误必须消失。

---

## P0-2 桌面产品从未启动 MCP

### 现象

外部 Grok 会话要「用 Copperbench 做模组」时：

- `search_tool` 对 copperbench 返回空。
- `http://127.0.0.1:8787/mcp` 连接被拒绝。
- 正在跑的 `copperbench.exe` / `javaw` **没有监听端口**。
- `CopperbenchMcpServer.start()` 只在
  `src/test/java/dev/copperbench/mcp/McpHttpServerTest.java` 和
  `McpConformanceServerMain.java` 里调用。
- `CopperbenchProductShell` 只挂 JCEF bridge，不启动 Tomcat MCP。
- `HeadlessCli` 只在测试里 `new`，安装的 `copperbench.exe` 没有 headless 子命令。
- UI「AI 与 MCP」写死「已连接」，无 URL、无令牌、无 workspaceId、无一键复制。
- `GET_WORKBENCH` 的 `connection` 是 `core/network/bridge`，不是 MCP。

工作区有 `WorkspaceFileLease`，第二个进程无法再挂同一工作区。因此 **MCP 必须由已打开工作区的桌面进程启动**。

### 要求的修复

工作区在产品外壳打开成功后：

1. 在 `127.0.0.1` 随机或固定端口启动 `CopperbenchMcpServer`。
2. 用当前 `workspaceId` 签发 `WorkspaceToken`（默认档位 `workspace`）。
3. 把连接信息写到工作区本地（不要进 git），例如
   `D:\AICoding\testmod2\.copperbench\mcp-connection.json`：
   - `url`（`http://127.0.0.1:<port>/mcp`）
   - `workspaceId`
   - `permissionProfile`
   - `expiresAt`
   - 令牌只显示一次或用配对码，禁止打进普通日志。
4. 「AI 与 MCP」页展示：
   - 真实监听地址
   - 当前档位
   - 复制「Grok / Cursor / Claude」配置片段
     例：`grok mcp add copperbench --transport http http://127.0.0.1:<port>/mcp`
   - 未监听时显示「未启动」，禁止「已连接」。
5. `ServerCapabilities.tools(true)`（或至少能 `tools/list`）。现在是 `tools(false)`。
6. 关闭工作区时停止 MCP、吊销令牌、删除或作废连接文件。

参考：`docs/ai/getting-started.md`、`sdk/python/copperbench.py`、`sdk/protocol.md`、`examples/ai/quickstart.py`。

### 验收

- 安装包打开 `testmod2` 后，本机 `GET http://127.0.0.1:<port>/mcp` 不再是连接拒绝。
- 外部 agent 能 `initialize` + `get_workspace` 读到 revision 和 workspaceId。
- UI 复制出来的 URL/workspaceId 与真实服务一致。
- 没有 MCP 客户端时页上不是「已连接」。

---

## P0-3 Agent 最小闭环：读 → 写元素 → 构建

MCP 起来之后，必须能不手改 Java 完成：

1. `get_workspace`
2. `list_mod_elements`（cursor 直到 `nextCursor=null`）
3. `create_mod_element`（至少 `item` + `code` 或 `projectile`）
4. `preview_mod_element_change` / `plan_workspace_changes` → `apply_workspace_plan`
5. `build_workspace` + `get_task(afterLogSequence)`
6. revision 冲突返回 `WORKSPACE_REVISION_CONFLICT`，客户端重读后再写

补齐文档缺口：

- `create_mod_element` 的 `initialValues` 示例（item 蓄力、projectile、procedure IR 最小图）。
- 何种效果必须用 `code`：环绕碰撞、蓄力数量、齐射。Blockly 不够。
- 给 agent 的 `SKILL.md` 或 `docs/ai/agent-playbook.md`：推荐调用顺序、禁止静默覆盖、禁止把令牌贴到聊天。

---

## P0-4 测试客户端：用户以为没加载 Fabric / 模组

### 现象

JAVA_HOME 临时绕过之后，用户点测试客户端进了游戏，反馈「没有加载 Fabric，也没有这 mod」。

同时 Copperbench 任务仍失败：`Fabric 26.1.2 client did not reach the readiness marker`（Error ID `20f63e4c-59d0-4a96-a02f-cb1fcb8cc4ae`，约 2026-09-02 02:37）。

### 实际情况（已核实 `D:\AICoding\testmod2\run\logs\latest.log`）

游戏 **已经** 加载了 Fabric 和模组：

```
Loading Minecraft 26.1.2 with Fabric Loader 0.19.3
Loading 51 mods:
  ...
  - fabricloader 0.19.3
  - minecraft 26.1.2
  - testmod2 1.0.0
Initializing Testmod2Mod
```

玩家 `Player61` 进了 `New World`。看起来像原版，是因为：

1. Minecraft 26.1 标题画面几乎没有 Fabric 大字。
2. 评测武器用钻石剑模型，创造「战斗」页里不像新物品。
3. 这是 Loom 开发客户端（离线用户 `Player61`），不是官方启动器那套带 Fabric 配置文件的安装。

### 第二个产品 bug：runClient 被当成冒烟探针

`Fabric1211ProcessRunner` 对 `runClient`：

- 在 stdout 里找 `COPPERBENCH_STAGE7_FABRIC261_READY`（见 `Fabric1211Generator.Profile.FABRIC_261.readyMarker()`）。
- **一旦看到 marker，立刻 `destroy(process)` 杀掉游戏。**
- 看不到 marker：进程退出或超时后，任务失败，包装成 readiness marker 失败。

MCreator/插件工作区走 `PluginWorkspaceLayout.present` 时 **不会** 重写 `Testmod2Mod.java`，因此不会打印这行 marker。结果：

- 用户能玩一会儿（游戏是真 Fabric）。
- IDE 红字失败，像没跑起来。
- 若以后 generate 补上 marker，用户刚进标题画面游戏就会被杀掉。

「测试客户端」对创作者必须是 **长寿命进程**：等到用户关窗口，而不是等探针字符串。探针逻辑只留给 CI / `verify-*-runclient.ps1`。

JAVA_HOME 原文错误也被包成同一个 “readiness marker” 失败，排障信息丢失。

### 要求的修复

1. 产品外壳的 `RUN_CLIENT` 不要在看到 marker 后杀进程；不要把「没看到 marker」当成用户失败。
2. 用户关游戏 → 任务成功或取消；Gradle 非 0 且游戏没起来 → 失败，并展示 Gradle 原文 + 解析后的 JAVA_HOME。
3. 冒烟探针与「打开测试客户端」分成两个 operation 或显式 flag。
4. 标题/任务文案写明这是 Fabric Loom 开发客户端；第一次进世界给模组加载提示（评测模组侧已加聊天提示，产品侧仍应说明）。

### 验收

- 安装包打开 `testmod2`，点测试客户端：游戏保持运行直到用户关闭。
- 任务 UI 在游戏运行期间显示 running，不要因为缺 `COPPERBENCH_STAGE7_FABRIC261_READY` 变红。
- JAVA_HOME 无效时，诊断码是 JDK 路径问题，不是 readiness marker。

---

## P1 产品化缺口

1. **Headless 入口**
   `copperbench.exe headless --workspace <path> <command>` 接到 `HeadlessCli`。MCP 挂了时 agent 的第二条路。

2. **runClient 诊断**
   Gradle wrapper 的 `JAVA_HOME is set to an invalid directory` 应映射成 Copperbench 诊断码，日志里同时打：`distributionRoot`、解析结果、`java.home`、`user.dir`。现在 Error ID `5b69333b-...` 被包成 readiness marker 失败，误导排障。

3. **生成不要冲掉评测代码**
   `testmod2` 里万剑归宗挂在用户代码块和 `net.mcreator.testmod.wanjian.*`。`RUN_CLIENT` 会先 `generate`（日志「27 files」）。必须证明 generate 不删除用户代码块、不删除非元素包。

4. **复杂战斗的 code 元素**
   MCP `create_mod_element` type=`code` 应能提交 Java、返回编译诊断。万剑归宗这类不要逼 agent 只拼 Procedure。

5. **创造页 / 语言 / 配方**
   安装包 Fabric 26.1.2 编译类路径未必有 `net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents`。生成器已用 `net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents`。文档和 SDK 示例必须用安装包真实 API，不要抄 1.21 教程。

---

## P2 体验与一致性

- `EntityRendererRegistry` 在 26.1.2 已过时，生成器/文档跟上新客户端渲染 API。
- `AIControlView.tsx` 的「已连接」改成真实 MCP 状态。
- `quickstart.py` 默认 `8787` 改为读 `.copperbench/mcp-connection.json`。
- 工作区 `gradle.properties` 的 `actualmodid=potatoesAreBetterThanEggs` 与 `archivesName=modid` 会让导出 jar 叫 `modid-1.0.jar`。新建工作区应用真实 modid。

---

## 评测工作区里已有的模组（不要当回归破坏）

功能：万剑归宗。长按右键蓄力，身旁环绕最多 6 把钻石剑，环绕可伤生物，松开沿准星直线射出。

关键路径：

- `src/main/java/net/mcreator/testmod/wanjian/WanJianRegistries.java`
- `WanJianGuiZongItem.java`
- `FlyingSwordEntity.java`
- `src/main/java/net/mcreator/testmod/client/WanJianClient.java`
- `Testmod2Mod.java` / `Testmod2ModClient.java` 的 user code block
- `src/main/resources/assets/testmod2/items/wan_jian_gui_zong.json`
- `src/main/resources/data/testmod2/recipe/wan_jian_gui_zong.json`

本机已 `gradlew build` 通过，产物 `D:\AICoding\testmod2\build\libs\modid-1.0.jar`。安装包测试客户端在 junction 绕过 JAVA_HOME 后能进游戏；日志证明 Fabric + `testmod2` 已加载。不要删 `wanjian` 包和用户代码块。

本机应急（不是产品修复）：曾创建 junction
`C:\Program Files\Copperbench\jdk\jbr25_win_64` → `C:\Program Files\Copperbench\jdk`。卸载/升级后可能消失。P0-1 仍必须做。

---

## 建议实施顺序

1. P0-1 JDK 解析（不修这个，安装包无法 runClient）。
2. P0-4 测试客户端改为长寿命进程，诊断不要包成 marker 失败。
3. P0-2 桌面启动 MCP + UI 连接信息。
4. P0-3 用 MCP 在空工作区创建 item 并 build。
5. P1 诊断、headless、generate 保用户代码。
6. 再考虑 Blockly/code 元素体验。

每项提交说明：改了什么文件、两种 JDK 布局如何测、MCP 用哪条 URL、不要只贴单元测试绿。
