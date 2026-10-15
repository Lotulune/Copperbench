# Agent 创建同一个步道方块

[English](trail-block.en.md) · [接入](getting-started.md) · [GUI 作品](../user/first-creation.md)

这条路线使用 `fabric-1.21.1`、模组 ID `first_creation` 与内部名 `trail_block`，与 GUI 教程相同。已有元素不再次创建；先通过原生创建对话框建立隔离工作区并打开桌面，再使用同一系统用户的 Python。新便捷方法按 [SDK 合同](../../sdk/compatibility.md)发现，不以历史安装包版本猜可用性。

安装随包 SDK：`python -m pip install ./sdk/python`。在包含当前 SDK 方法的构建中运行以下 Python，把文件路径替换为实际隔离工程：

```python
from copperbench import Workspace

with Workspace.connect(r"D:\Acceptance\first_creation\first_creation.mcreator") as workspace:
    workspace.require_schema()
    environment = workspace.get_workspace_environment()
    assert environment["data"]["generator"]["id"] == "fabric-1.21.1"
    contract = workspace.field_contract("block", environment=environment)
    assert contract["supported"] is True
    existing = [item for item in workspace.list_mod_elements(
        search="trail_block", fields=["id", "name"]
    ) if item["name"] == "trail_block"]
    if existing:
        raise RuntimeError("Open the existing trail_block instead of recreating it")
    revision = workspace.get_workspace()["revision"]
    created = workspace.create_mod_element(
        elementType="block", name="trail_block", expected_revision=revision,
        initialValues={"displayName": "Trail Block", "fields": {
            "hardness": 3, "resistance": 6, "hasBlockItem": True
        }},
    )
    assert created["status"] == "committed"
    started = workspace.build(expected_revision=workspace.get_workspace()["revision"])
    finished = workspace.wait_task(started["task"]["id"])
    if finished["data"]["task"]["state"] != "succeeded":
        raise RuntimeError(finished["data"])
```

错误以 `NativeApiError.code/details` 保留原回执；读取全部诊断修正字段，不重放未知结果的写入。MCP 使用实际 `tools/list` 中的 `create_mod_element`，相同业务 payload 的 revision 键是 `expectedRevision`；先 `initialize`、发现环境并从 `get_workspace` 取最新值。不要直接照搬 Native 的请求封装或填固定 revision。配对 token 不放在示例参数或日志。

此例只创建和构建。游戏取得／放置与跨进程持久化仍按 [GUI 教程](../user/first-creation.md)实际确认；普通导出、玩家确认与真实打包 JAR 验收分别记录。[公开固定回归](../../sdk/agent-workflows.md)另提供 W1 错误字段修复、W2 外部修改拒绝与 W3 原任务恢复／精确导出，保留各自回执。
