# Your first creation: place a Trail Block

[简体中文](first-creation.md) · [Install Copperbench](getting-started.en.md)

Use the graphical workbench to create a block, obtain and place it in Minecraft, then find it again after saving and reopening the world. This tutorial uses **Fabric 1.21.1**, the built-in stone texture and Copperbench 0.1.5. You do not need an image, Blockbench or Java code.

## 1. Create a workspace

Open **New workspace** and enter:

| Field | Value |
| --- | --- |
| Generator | Fabric 1.21.1 |
| Mod name | `First Creation` |
| Mod ID | `first_creation` |
| Java package | `com.example.firstcreation` |
| Workspace folder | A new empty folder outside the application directory |

Create the workspace. Confirm that it opens with Fabric 1.21.1 selected. If the generator is unavailable, check your installed version and plugins before continuing.

## 2. Create and save the block

Open **Mod Elements**, create a **Block** and enter `trail_block` as its internal identifier. Open its editor and set:

| Field | Value |
| --- | --- |
| Display name | `Trail Block` |
| Hardness | `3` |
| Resistance | `6` |
| Model and texture | Keep the default cube and `minecraft:stone` |
| Block item | Keep enabled |

Apply the changes. Select the element again and confirm that the saved values remain.

## 3. Build

Run **Build** and wait for the task to finish successfully. The first build downloads dependencies; allow it to continue while the download log is progressing. A successful build includes `BUILD SUCCESSFUL` in its log.

If it fails, inspect the task's diagnostics and log before retrying. Saving an element does not mean its build has passed.

## 4. Play, save and reopen

1. Start the client from this workspace and wait for the Minecraft 1.21.1 main menu.
2. Create a new Creative world with commands enabled. Give it a unique name, such as `CB_FirstCreation_20261015_01`.
3. Open chat and run `/give @s first_creation:trail_block 16`.
4. Confirm that you received 16 **Trail Block** items. Move one stack to your hotbar, select it and right-click the ground to place a block. It should look like stone.
5. Remember the location. Save and quit the world, then open **the same world**. Confirm that the block is still there.
6. Save and quit to the main menu, then quit Minecraft. Back in Copperbench, wait for the client task to finish successfully.

## Troubleshooting

| Problem | Next step |
| --- | --- |
| A required field or number is rejected | Correct the highlighted input. Confirm that you selected the Block type. |
| The identifier already exists | Edit the existing `trail_block` instead of creating it again. |
| Another window changed the workspace | Review the latest content, then apply your changes again. |
| A dependency download fails | Find the failed URL in the task log and check your network or mirror settings. |
| The give command is not permitted | Enable commands in this new test world. |
| Minecraft reports an unknown item | Check the mod ID and element identifier, then rebuild and restart the client from this workspace. |
| The block has a black-and-purple texture | Restore `minecraft:stone`, save, build and restart the client. |
| The client closed but its task still runs or failed | Inspect the final log and task status before considering the workflow complete. |

Agent and SDK users can follow the separate [Agent tutorial (Chinese)](../ai/first-creation.md). Release validation and current capabilities are listed in [current downloads](../releases/current.md).
