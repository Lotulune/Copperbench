# README screenshots

Captured on Windows on 2026-09-14 from the production React bundle in native JCEF, connected to a separate on-disk **Mossglow Workshop** example through `MCreatorWorkspaceSession` and the Java UI-Core bridge.

- `en/`: English workbench, function editor, Blockly editor, assets, and local history.
- `zh-CN/`: the same workspace and views with Simplified Chinese selected.
- All images are 1600 × 1000 PNGs exported directly by Chromium `Page.captureScreenshot`. They have no mouse-cursor layer and were not retouched.
- The example contains a block, an item, a function, a seven-node procedure, textures, and two recovery points. Duplicate-texture warnings shown in Assets are actual index results.
- These are development-build screenshots, not evidence of a new packaged release or an in-game acceptance test. User content and code retain their original language.

The workbench language preference is stored by the desktop host independently of Chromium's temporary cache. The browser preview uses local storage. Switching languages asks the user to save their edits before reloading.
