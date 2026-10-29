# Create the same Trail Block through public APIs

[简体中文](trail-block.md) · [Onboarding](getting-started.en.md) · [GUI creation](../user/first-creation.en.md)

Use `fabric-1.21.1`, mod ID `first_creation` and internal name `trail_block`, matching the GUI tutorial. Create an isolated workspace through the existing native creation dialog and open it in the desktop. Run Python as the same OS user. Do not recreate an element already created in the GUI. Discover the new helper methods through the [SDK contract](../../sdk/compatibility.md), rather than assuming an older package contains them.

Install the shipped SDK with `python -m pip install ./sdk/python`. In a build containing these helpers, replace the path below with the actual isolated descriptor:

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

Errors keep their raw `NativeApiError.code/details`. Read all diagnostics, correct the actual field and do not replay an uncertain write. MCP uses the real catalog's `create_mod_element`, with `expectedRevision` in its tool arguments. Initialize, discover the environment and read a fresh value from `get_workspace`; do not copy Native's wrapper or a fixed revision. Pairing tokens stay out of example arguments/logs.

This example proves no game observation by itself: it creates and builds. Follow [the GUI game steps](../user/first-creation.en.md) for obtaining/placing and persistence across new client processes. Record ordinary export, player confirmation and packaged-JAR acceptance separately. The fixed [W1/W2/W3 runners](../../sdk/agent-workflows.md) preserve field failure/repair, stale-input rejection and original-task recovery/exact-export receipts.
