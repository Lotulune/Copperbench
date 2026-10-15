# Build recovery and export

[简体中文](build-and-export.md) · [First creation](first-creation.en.md)

Version 0.1.6 connects Delivery & export and Rebuild and export to the existing build/export flow; earlier packages use their original entry. Exact verified export still requires a real packaged-JAR acceptance task. A version number or ordinary successful build does not establish acceptance. See [current status](../status.md).

## Recover a failed build

1. Open the original failed task, retain its first structured diagnostic, code, path and relevant log/details.
2. For field errors, use the diagnostic's field entry, correct and save. For reference/texture errors, open the relevant asset action. An unmapped path stays visible; do not guess ownership from a filename.
3. For compiler errors, use the task's actual source path/element association and source entry if available. Edit manual files in **Source**; generated files remain read-only, so fix their owning element/generator.
4. For download failures, inspect the dependency URL and selected mirror/network/disk, then retry explicitly. Warm-cache success does not establish a first offline build.
5. Save and run a new build until that task succeeds. Keep earlier failure receipts tied to their original input.

A failed save/conflict leaves drafts unsaved. Inspect newer workspace/disk content before explicitly rereading or applying an available resolution; retain updated bytes. A disconnected transport is an observation gap: reconnect and query the original task, without starting it again. Cancellation needs the actual `cancelled` terminal state; closing logs or ending a wait is not cancellation.

## Export an ordinary build

1. Open **Delivery & export** and check the workspace-relative destination. Defaults use a new `build/libs/copperbench-delivery-<request UUID>/` directory and a normalized workspace filename: `.jar` for mods, `.zip` for resource packs. You can edit a relative path of the same artifact type.
2. Choose **Rebuild and export**. This starts a new build/export task; acceptance of the request is not task success. Follow that task's diagnostics if it fails.
3. On success, **Open build output folder** opens `build/libs`, where default deliveries can be found at the recorded path. For a custom destination, follow the chosen workspace-relative path from the workspace root; it may be outside `build/libs`. Keep that task and path instead of selecting an unrelated “latest” JAR.

Custom destinations must stay inside the workspace. An existing file is replaceable only if Copperbench recorded it as a previous delivery and its content still matches that receipt; use a new path for other existing or externally edited files. Authored-source/resource roots (such as `src`, `elements`, `assets`), dependency roots (such as `gradle`, `lib`, `libs`) and workspace metadata/runtime roots are rejected. `build/libs` is allowed, but unrelated existing JARs there remain protected. A destination that is exactly this build's actual declared archive file is allowed and uses that artifact directly.

Ordinary export does not inherit an acceptance badge. Saving, compiling, player confirmation and packaged-JAR acceptance describe separate results. Preparing a resource-pack client also does not start Minecraft.

After editing files externally, choose **Refresh inputs and original tasks**. A previous successful export remains recorded, but changed inputs need a new build/export. Changes during a task or unavailable input checks are shown separately with a next step. An unavailable input check does not block ordinary export; its cause remains in the original task log.

## Export the exact accepted JAR

Select an existing successful packaged-JAR task with `verification.status=passed`, then choose **Export this verified JAR** in its task panel. The backend checks the accepted artifact, XML report and current workspace input hashes. Its receipt identifies a directory containing `verified-mod.jar`, `gametest-results.xml` and `verification.json`.

Changed input is rejected by default. Select historical export explicitly only when delivering older input; its receipt labels it historical. Missing/changed evidence stays a diagnostic, not an ordinary-export fallback. Acceptance covers the report's cases, not all gameplay.

Use **Open this exact export directory** when available. It opens the directory bound to that export task's real receipt, not an arbitrary supplied path. Older interfaces expose the directory in the receipt.

The exact-export card and original task show the loader, Minecraft and loader versions recorded in that receipt. Historical compatibility comes from its own receipt. Missing values say **Not recorded in receipt** instead of using today's project version.

## Confirm and continue

Follow [the first-creation game steps](first-creation.en.md#confirm-behavior-and-persistence): obtain/place, quit normally, start a new client process and reopen the same world. The guide's confirmation is scoped to the session and corresponding input/element/build; saving a change or starting another client requires renewed confirmation. It is not a durable automated report.

Continue in **Source** or [Agent onboarding](../ai/getting-started.en.md), preserving manual files and unknown plugin fields. Save/build/check the new input; older receipts retain their original scope.
