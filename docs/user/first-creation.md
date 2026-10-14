# 第一件作品：在游戏里放下步道方块

完成后，你会在 Minecraft 中取得 16 个名为 **Trail Block** 的方块，放置其中一个，并在保存、重进世界后找到它。本教程使用 **Fabric 1.21.1** 和内置石头纹理，不需要准备图片、Blockbench 或编写 Java。

安装入口见[快速开始](getting-started.md)。教程使用 0.1.4 已有的工作区、方块编辑和客户端入口；当前源码新增的输入预检见[状态总览](../status.md)。

## 1. 创建独立工作区

打开「新建工作区」，填写以下内容：

| 输入 | 值 |
| --- | --- |
| 生成器 | `Fabric 1.21.1`（ID：`fabric-1.21.1`） |
| 模组名称 | `First Creation` |
| 模组 ID | `first_creation` |
| Java 包名 | `com.example.firstcreation` |
| 工作区目录 | 一个新的空目录，例如 Windows 的 `D:\CopperbenchWorkspaces\first_creation`，或 Linux 的 `/home/你的用户名/CopperbenchWorkspaces/first_creation` |

点击创建并确认。预期：新窗口打开工作区，活动生成器为 Fabric 1.21.1。生成器列表没有这一项时，先检查安装版本和插件，不要改选资源包生成器继续照抄。

## 2. 创建并保存方块

进入「模组元素」，点击新建，选择「方块」，内部标识符填 `trail_block`，提交创建。

预期：元素列表出现 `trail_block`，可以打开它的编辑器。内部标识符决定游戏注册名；显示名称只决定玩家看到的文字。

| 编辑器字段 | 值 |
| --- | --- |
| 显示名称（`displayName`） | `Trail Block` |
| 硬度（`hardness`） | `3` |
| 爆炸抗性（`resistance`） | `6` |
| 模型与纹理 | 保留默认普通立方体和内置石头纹理（`minecraft:stone`） |
| 方块物品（`hasBlockItem`） | 保持开启 |

点击「应用更改」。预期：编辑器显示已保存；重新选择这个元素，名称、硬度和抗性仍是上述值。

通过 Agent 创建同一个元素时，先调用 `get_workspace_environment` 确认活动生成器，再读取 `get_workspace` 的最新 revision。以下是 `create_mod_element` 的具体输入；将 `expectedRevision` 替换为刚读到的整数，仅在尚未用界面创建时执行一次：

```json
{
  "elementType": "block",
  "name": "trail_block",
  "initialValues": {
    "displayName": "Trail Block",
    "fields": {
      "hardness": 3,
      "resistance": 6,
      "hasBlockItem": true
    }
  },
  "expectedRevision": 0
}
```

预期返回 `status: "committed"`，并提供 `newRevision` 和元素 ID。后续写操作使用最新 revision；不要反复发送同名创建请求。配对与权限设置见 [MCP 入门](../ai/getting-started.md)。

## 3. 构建

运行「构建」，等待任务进入成功终态。首次运行会下载 Gradle、加载器和 Minecraft 依赖，下载日志持续变化时继续等待。

预期：任务成功，Gradle 日志包含 `BUILD SUCCESSFUL`。如果失败，打开该任务的诊断和日志，按下表修复后重新构建。不要仅凭“已保存”进入下一步。

## 4. 启动 Minecraft 并确认作品

1. 在顶部运行入口选择客户端并启动，等待 **Minecraft 1.21.1** 主菜单出现。
2. 创建新世界，名称用 `CB_FirstCreation_20261014`（可换成当天日期或增加编号），模式选「创造」，开启作弊／允许命令。
3. 进入世界，按 `T` 打开聊天栏，输入 `/give @s first_creation:trail_block 16` 并回车。
4. 预期：获得 16 个 `Trail Block`。打开物品栏，将其放入快捷栏并选中；对地面右键放置一个。预期：地面出现石头外观的方块，快捷栏物品提示是 `Trail Block`。
5. 记住位置，保存并退出世界，再打开**同一个世界**。预期：刚放置的方块仍在原处。
6. 保存并退出到主菜单，再退出 Minecraft。回到 Copperbench，等待客户端任务进入终态；正常退出应成功结束，不再显示运行中。

构建成功、客户端启动、取得并放置方块、重进世界保留方块是四个独立检查点。不要将第一项成功当成后三项已完成。

## 5. 常见阻塞与修复

| 现象 | 下一步 |
| --- | --- |
| 创建返回缺字段或类型错误 | 按诊断的 `path`、`message.args.expectedType` 和 `repairHint` 修正输入；`initialValues` 必须是对象，数字不能加引号。旧安装包可能只显示保存失败，参见[输入诊断](../ai/input-diagnostics.md)。 |
| 名称已存在 | 打开已有的 `trail_block` 编辑，不再创建同名元素。 |
| revision 冲突 | 重新读取工作区和元素，再基于最新 revision 提交。 |
| 构建下载失败 | 在任务日志中找到失败的依赖地址，检查网络／镜像配置，按[故障排查](troubleshooting.md)重试。 |
| `/give` 提示无权限 | 确认当前新世界已开启作弊／允许命令。 |
| `/give` 提示未知物品 | 核对模组 ID、元素内部名称；确认构建成功，并从这个 Copperbench 工作区重新启动客户端。 |
| 方块显示黑紫贴图 | 检查纹理是否仍为 `minecraft:stone`，保存、构建并重启客户端。 |
| 客户端已关闭但任务仍运行或失败 | 查看任务最后日志和退出状态，先处理退出问题再判定本次流程完成。 |

教程的输入和字段以本地源码为依据；本轮检查范围记录在[优化验证记录](../testing/first-success-2026-10-14.md)，其中游戏步骤需由实际客户端观察确认。
