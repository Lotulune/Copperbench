# First creation: place a Trail Block

[简体中文](first-creation.md) · [Installation](getting-started.en.md) · [Build recovery and export](build-and-export.en.md)

The goal is to obtain 16 **Trail Blocks**, place one, quit Minecraft normally and find it in the same world from a **new client process**, then export a JAR. Use **Fabric 1.21.1** and the built-in stone texture; no image, Blockbench or Java is needed.

Official 0.1.5 includes the first-creation tutorial using ordinary workspace, block, build and client entry points; historical 0.1.4 also provides those manual entries. This batch's new native startup/closable **First creation** guide and **Delivery & export** controls are unreleased candidate changes. Use them if present; otherwise follow the manual steps. [Current status](../status.md) preserves those boundaries. The [Agent example](../ai/trail-block.en.md) is a separate public API route.

## One-page route

| Step | Inputs and UI action | Continue when |
| --- | --- | --- |
| Create | **New workspace**: Fabric 1.21.1, name `First Creation`, ID `first_creation`, package `com.example.firstcreation`, new empty directory; confirm. | The workspace opens with the intended generator. |
| Save | **Mod Elements** → new **Block** `trail_block`; display name `Trail Block`, hardness `3`, resistance `6`, default `minecraft:stone` and block item; **Apply changes**. | Reselecting the element shows the saved values. |
| Validate and repair | Set hardness to `-2`, apply, use **Go to field** in the diagnostic, restore `3` and apply again. | Record the original range error/path, then repair and read back the saved value. |
| Prepare and build | For exact export, [prepare GameTest templates](#export-and-continue) before the final build; use **Build**. | The new task succeeds with `BUILD SUCCESSFUL`. |
| In-game result | Use **Run**, create a uniquely named Creative world with commands enabled, enter `/give @s first_creation:trail_block 16`, check the name/count and place one. | The block is actually obtained, selected and placed. |
| Reopen | Save, leave the world, quit Minecraft and wait for a successful client task; launch a new process and open the same world. | The block remains; the second client also exits normally with a successful task. |
| Exact export | **More run options** → **GameTest**; after a real packaged-JAR task passes, use **Export this verified JAR** and open that receipt's exact directory. | Export succeeds and JAR/report hashes match the original verification. Without a real acceptance task, this step remains unverified; record ordinary export separately. |

The host must actually expose packaged-JAR verification; the initial template only checks mod loading. The [continuous native capture protocol（简体中文）](../testing/first-creation-demo.md) defines evidence for each step and is awaiting a recording, not a completed demo.

## Create an isolated workspace

New users see an animated spotlight tour on their first launch. Choose **Start creating**, check the preset, choose a new empty directory and confirm creation. After skipping, you can use **New workspace** manually:

| Input | Value |
| --- | --- |
| Generator | Fabric 1.21.1 (`fabric-1.21.1`) |
| Mod name | `First Creation` |
| Mod ID | `first_creation` |
| Java package | `com.example.firstcreation` |
| Directory | A new empty directory, such as `D:\CopperbenchWorkspaces\first_creation` or `/home/yourname/CopperbenchWorkspaces/first_creation` |

The new workspace should open with Fabric 1.21.1 active. If that generator is absent, check the installed package/plugins instead of substituting a resource-pack generator. The first-creation workspace continues that tour. A temporary **Continue tour** control returns from editing or task details. Closing or skipping ends the tour; later launches do not reopen it. Ordinary workspace overviews have no first-creation or open-workspace cards. Deleted elements are never recreated automatically.

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

Practice a safe save-input validation failure: set hardness to `-2` and apply. The current block contract allows `-1..64000`, so expect `FIELD_VALUE_OUT_OF_RANGE`. Record the original diagnostic, use **Go to field**, restore `3`, apply and reselect to read it back. Use the returned path, which may be `/hardness` or `/fields/hardness`. If the value is not rejected, record the difference and check the tested version before continuing. The [build recovery guide](build-and-export.en.md#recover-a-failed-build) covers the separate build-failure chain.

Draft retention covers route changes, rejected validation and conflict handling in the same frontend session. Element/source drafts live in memory; they are not disk recovery points. Apply and read back changes before leaving the application. Reopening after a normal exit, a crash and power loss remain three separate scenarios awaiting evidence in the [draft recovery register（简体中文）](../testing/first-creation-demo.md#草稿恢复单独登记).

## Build

For exact export, prepare the templates described below before this final build and gameplay check; preparation changes workspace inputs.

Use **Build** and wait for the task's successful terminal state. A first run can download dependencies. If it fails, open that task's diagnostics/log and follow [build recovery](build-and-export.en.md#recover-a-failed-build). Saved fields and a successful build are separate results.

## Confirm behavior and persistence

1. Use **Run** in Copperbench to launch the client and wait for the Minecraft 1.21.1 menu.
2. Create a new Creative world with commands enabled. Use a unique name such as `CB_FirstCreation_YYYYMMDD_01`.
3. Open chat with `T`, enter `/give @s first_creation:trail_block 16`, and confirm 16 **Trail Blocks** appear.
4. Select the block in the hotbar and place one on the ground. Verify its stone appearance and item name; remember the location.
5. Save and quit to the menu, then **quit Minecraft**. Wait for the Copperbench client task to finish successfully. Inspect its final logs if it remains running or fails.
6. Launch the client again from Copperbench to start a **new process**. Open the **same saved world**, not a new world, and verify the placed block remains.
7. Save, quit Minecraft again and wait for the second client task to succeed. If the guide offers a player confirmation, confirm obtaining/placing the block and finding it after reopening that world in the new client.

Saving, compiling, starting a client, player behavior and persistence are separate observations. The guide's player confirmation is scoped to this session, element and input/build reference. After editing files externally, use **Refresh inputs and original tasks**: a task can remain successful while changed inputs require a new build or run. Older tasks without an input record remain unconfirmed. Inputs that changed during a task also require checking again. Player confirmation is not an automated acceptance report.

## Export and continue

If **Delivery & export** is available, review the workspace-relative output path and choose **Rebuild and export**. After the task succeeds, open the build folder and find the JAR at that exact path. Older packages use their existing build/export entry. See [export steps](build-and-export.en.md#export-an-ordinary-build).

An ordinary export proves a build/copy; player confirmation records observed behavior. **Export this verified JAR** uses a passed packaged-JAR acceptance task and checks its report/artifact hashes. Keep these results distinct.

To finish the route with exact export:

1. **Before the final build/gameplay check**, open **More run options** → **GameTest**. If configuration is missing, use **Prepare test templates** on the original failed task and wait for preparation to succeed. Review existing configuration instead of replacing it. The default `packaged_jar` template checks mod loading only; the gameplay observations above remain separate. See [test configuration（简体中文）](../ai/task-authorization-and-acceptance.md#从模板到玩法验收).
2. After building and reopening the same world, run **GameTest** again. Wait for `succeeded`, then open its **GameTest verification report** → **Test cases and tested content**. Confirm `verification.status=passed`, executed cases, tested JAR hash and current inputs. Changed inputs require checking the affected steps and rerunning verification.
3. Leave **Allow exporting a previously verified artifact for older input** unchecked and choose **Export this verified JAR**. Wait for the export task to succeed, then use **Delivery & export** → **Open this exact export directory**, or the exact receipt directory if that button is absent. Check `verified-mod.jar`, `gametest-results.xml`, `verification.json` and their hashes against the original verification; do not substitute a rebuilt JAR.

| Blocker | Action |
| --- | --- |
| Field/save failure | Use the diagnostic's field location, correct the value and save again. Unmapped diagnostics retain their original path/details. |
| External-change conflict | Read the newer content/diff before deciding how to save; visible old drafts are not committed state. |
| Download/build failure | Open the first diagnostic and dependency URL in that task's log; use the recovery guide. |
| `/give` permission error | Enable commands in this isolated world. |
| Unknown item | Check mod ID/internal name, successful build and that this workspace launched the client. |
| Missing texture | Restore `minecraft:stone`, save, build and start a new client. |
| Exit not successful | Inspect final client logs/state before marking the route complete. |

The [2026-10-14 input-check record](../testing/first-success-2026-10-14.md) is historical evidence, not acceptance of the new guide or this run. The status page identifies the applicable package/source scope.
