# First creation: place a Trail Block

[简体中文](first-creation.md) · [Installation](getting-started.en.md) · [Build recovery and export](build-and-export.en.md)

The goal is to obtain 16 **Trail Blocks**, place one, quit Minecraft normally and find it in the same world from a **new client process**, then export a JAR. Use **Fabric 1.21.1** and the built-in stone texture; no image, Blockbench or Java is needed.

This tutorial provides both the creation-tour and manual routes. If your installed version lacks a guide control, use the ordinary workspace, block, build and client entries. [Current downloads](../releases/current.md) records platform versions and package acceptance; [current status](../status.md) describes source capabilities. The [Agent example](../ai/trail-block.en.md) is a separate public API route.

New 0.1.15 users start in **Simple creation**. This manual tutorial uses the visual editors, so first switch to **Professional** at the top. Simple creation also supports conversational work through your configured host CLI.

## Create an isolated workspace

If an animated spotlight tour appears on first launch, choose **Start creating**, check the preset, choose a new empty directory and confirm creation. If your installed version has no tour, or you have skipped/ended it, use **New workspace** manually:

| Input | Value |
| --- | --- |
| Generator | Fabric 1.21.1 (`fabric-1.21.1`) |
| Mod name | `First Creation` |
| Mod ID | `first_creation` |
| Java package | `com.example.firstcreation` |
| Directory | A new empty directory, such as `D:\CopperbenchWorkspaces\first_creation` or `/home/yourname/CopperbenchWorkspaces/first_creation` |

The new workspace should open with Fabric 1.21.1 active. If that generator is absent, check the installed package/plugins instead of substituting a resource-pack generator. A workspace created through the first-launch tour continues that tour. A temporary **Continue tour** control returns from editing or task details. Closing or skipping ends the tour; later launches do not reopen it. This tour does not add first-creation or open-workspace cards to ordinary workspace overviews or navigation. Deleted elements are never recreated automatically.

## Create and save the block

Open **Mod Elements**, create a **Block** with internal name `trail_block`, then open its editor:

| Field | Value |
| --- | --- |
| Display name | `Trail Block` |
| Hardness | `3` |
| Resistance | `6` |
| Model/texture | Default cube and `minecraft:stone` |
| Block item | Enabled (`hasBlockItem`) |

Click **Apply changes**. Select the element again and verify those saved values. The internal name determines the registry ID; the display name is player-facing text. If `trail_block` already exists, open it rather than creating another. The guide rereads actual saved values; visible draft text alone does not establish a save.

## Build

Use **Build** and wait for the task's successful terminal state. A first run can download dependencies. If it fails, open that task's diagnostics/log and follow [build recovery](build-and-export.en.md#recover-a-failed-build). Saved fields and a successful build are separate results.

## Confirm behavior and persistence

1. Launch the client from Copperbench and wait for the Minecraft 1.21.1 menu.
2. Create a new Creative world with commands enabled. Use a unique name such as `CB_FirstCreation_YYYYMMDD_01`.
3. Open chat with `T`, enter `/give @s first_creation:trail_block 16`, and confirm 16 **Trail Blocks** appear.
4. Select the block in the hotbar and place one on the ground. Verify its stone appearance and item name; remember the location.
5. Save and quit to the menu, then **quit Minecraft**. Wait for the Copperbench client task to finish successfully. Inspect its final logs if it remains running or fails.
6. Launch the client again from Copperbench to start a **new process**. Open the **same saved world**, not a new world, and verify the placed block remains.
7. Save, quit Minecraft again and wait for the second client task to succeed. If the guide offers a player confirmation, confirm obtaining/placing the block and finding it after reopening that world in the new client.

Saving, compiling, starting a client, player behavior and persistence are separate observations. The guide's player confirmation is scoped to this session, element and input/build reference. After editing files externally, use the guide's **Check task status** control: a task can remain successful while changed inputs require a new build or run. Older tasks without an input record remain unconfirmed. Inputs that changed during a task also require checking again. Player confirmation is not an automated acceptance report.

## Export and continue

If **Delivery & export** is available, review the workspace-relative output path and choose **Rebuild and export**. After the task succeeds, open the build folder and find the JAR at that exact path. Older packages use their existing build/export entry. See [export steps](build-and-export.en.md#export-an-ordinary-build).

An ordinary export proves a build/copy; player confirmation records observed behavior. **Export this verified JAR** uses a passed packaged-JAR acceptance task and checks its report/artifact hashes. Keep these results distinct.

| Blocker | Action |
| --- | --- |
| Field/save failure | Use the diagnostic's field location, correct the value and save again. Unmapped diagnostics retain their original path/details. |
| External-change conflict | Read the newer content/diff before deciding how to save; visible old drafts are not committed state. |
| Download/build failure | Open the first diagnostic and dependency URL in that task's log; use the recovery guide. |
| `/give` permission error | Enable commands in this isolated world. |
| Unknown item | Check mod ID/internal name, successful build and that this workspace launched the client. |
| Missing texture | Restore `minecraft:stone`, save, build and start a new client. |
| Exit not successful | Inspect final client logs/state before marking the route complete. |

The [2026-10-14 input-check record](../testing/first-success-2026-10-14.md) is historical evidence, not acceptance of the new guide or this run. Package-specific evidence is linked from [current downloads](../releases/current.md).
