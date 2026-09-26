# Code 生命周期接入：Fabric / NeoForge 1.21.1

这组模板限定 Minecraft 1.21.1、Java 21。Fabric 与 NeoForge 使用各自的真实右键事件入口，向指定现有方块库存的第 0 槽增加一个圆石；不覆盖其他物品、不重复注册方块、不在客户端改库存。`init()` 有重复调用保护。模板 1.1 已在双加载器独立试作中通过重新生成、重开和实际事件回调 GameTest；遗漏初始化的副本仍能编译，但行为断言失败。这不替代你自己工程的玩法验收，也不代表真实客户端鼠标输入已验证。

在创建带库存的目标方块后，使用公开环境查询确认生成器，并读取工程主类的 `mod init` 保留区：

```powershell
python render.py --generator fabric-1.21.1 --package net.mcreator.example --block-id example:machine --main-source C:/workspace/src/main/java/net/mcreator/example/ExampleMod.java
```

NeoForge 将 `--generator` 改为 `neoforge-1.21.1`。渲染器只输出可审查的 JSON：`command` 可作为 `create_mod_element` 的内容参数（另加最新 `expectedRevision`）；`mainSourceAfter` 是主类保留区的完整修改提案。先核对 `mainSourceSha256` 与当前文件一致，再通过原生源码编辑写入该提案。渲染器本身不写工作区。缺少/重复保留区、已有冲突初始化会拒绝；已有相同调用则不再插入。

重复应用时先查询已有 `stage17_machine_runtime` Code 元素，比较实际源码；相同内容不再创建，不同内容使用现有字段更新/源指纹冲突流程。Code 元素自己的源码由产品保存，主类只在指定保留区加入调用。模板版本为 `1.1`，结果中的 `runtimeStatus` 始终要求后续构建和交互验收。

Code 元素的 Java 能编译，只证明语法和依赖成立。必须有加载器调用的入口，或被现有游戏对象调用的逻辑，行为才会执行。

| 路径 | 谁调用入口 | 何时可以连接已有对象 |
|---|---|---|
| Fabric 原生入口 | `fabric.mod.json` 的 `entrypoints.main` 指定实现 `ModInitializer` 的类 | `onInitialize()` 中注册对象/事件；只在客户端入口引用客户端类 |
| Fabric 生成主类 | 生成主类的 `onInitialize()` | `mod init` 保留区位于生成对象加载之后；早期 `mod constructor` 区位于这些调用之前 |
| NeoForge 生成主类 | `@Mod` 标注的主类构造器，加载器注入 `IEventBus modEventBus` | 构造期间把 DeferredRegister 注册到 modEventBus；不能把注册句柄已创建误认为实际对象已就绪 |

在生成主类的以下保留区中显式加入一次调用，保留边界注释：

```java
// Start of user code block mod init
dev.example.machine.MachineRuntime.init();
// End of user code block mod init
```

这是调用约定示例，`MachineRuntime` 必须由工程自己的实现提供。NeoForge 若实现需要绑定注册表，使用自己显式定义的 `init(modEventBus)` 签名；不要把 Fabric 的立即注册代码搬入 NeoForge 的延迟注册阶段。重复应用前比较实际文件：同一保留区已经存在完全相同调用时不再插入；发现其他调用或文件变化时先审查差异。产品不会搜索任意 `init()` 并自动执行。

多文件逻辑可使用 Code 的 `codeFiles`，文件路径相对主源码包。保持现有源文件指纹，编辑冲突时重新读取。用已有方块注册 ID 或显式传入的对象连接机器逻辑，不重复注册相同 ID。模型属于资源关联，不能替代行为入口；手写源码元素保留源码权威，按自己代码消费的模型路径关联资源。

已存在的完整 Fabric 1.21.1 示例是 [Wayfinder Bell](../wayfinder-bell/README.md)，其实际入口为 `dev.wayfinder.WayfinderBell`，由 `fabric.mod.json` 调用；业务类和 GameTest 分文件存放。测试骨架可通过 `prepare_game_tests` 生成，必须补充注册对象存在、真实使用回调、状态/库存改变的断言。

验收应包含“删掉初始化调用”的负例：代码仍可编译，但注册或交互断言必须失败。重新生成并重开工程后再次测试保留区。独立宿主必须加载选定的最终 JAR，核对测试数量、报告及产物哈希。尚未执行这些检查时，只能标为“仅编译验证”。
