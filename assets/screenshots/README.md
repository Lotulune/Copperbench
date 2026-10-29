# README screenshots

## Current: 2026-10-24 working-tree frontend

Both root READMEs display six light-theme views from **2026-10-24/en/** and **2026-10-24/zh-CN/** in a two-column Markdown table with three image rows, titles and short descriptions: Simple creation, the professional workbench, Mod Element editing, Java source, Python and relationships.

| View | English | 简体中文 |
| --- | --- | --- |
| Simple creation | [Content and settings](2026-10-24/en/simple-creation-light.png) | [简洁创作](2026-10-24/zh-CN/simple-creation-light.png) |
| Professional workbench | [Overview](2026-10-24/en/workbench-light.png) | [专业工作台](2026-10-24/zh-CN/workbench-light.png) |
| Mod Elements | [Element inspector](2026-10-24/en/elements-light.png) | [模组元素编辑](2026-10-24/zh-CN/elements-light.png) |
| Source | [Manual Java](2026-10-24/en/source-light.png) | [源码编辑](2026-10-24/zh-CN/source-light.png) |
| Python | [Script editor and console](2026-10-24/en/python-light.png) | [Python 工作台](2026-10-24/zh-CN/python-light.png) |
| Relationships | [Graph](2026-10-24/en/relations-light.png) | [关系图](2026-10-24/zh-CN/relations-light.png) |

These are direct, unretouched **1600 × 1000** Chromium screenshots, in **light theme**, using the bundled **Copper Trails** browser development fixture. Language, theme, mode and navigation are selected through the existing application. The Mod Element view collapses its 3D preview to show the fields, and Python shows its built-in example without running it. The scenario selector, example diagnostics and unavailable desktop-assistant/Python states remain visible. No real user workspace or fabricated assistant conversation is included.

[capture.json](2026-10-24/capture.json) records the Git baseline, hashes of the actual frontend/fixture inputs, Chromium version and every image's SHA-256. This working tree still has a 0.1.4 package-version baseline with later local changes; these images are dated source previews, **not screenshots of the published 0.1.18 binaries**. Native Agent/Python execution, package acceptance and Minecraft gameplay are outside this capture's scope.

### Refresh the gallery

From the repository root, after installing `ui-shell` dependencies and Playwright Chromium:

```sh
node ui-shell/scripts/capture-readme.mjs YYYY-MM-DD
```

The [capture script](../../ui-shell/scripts/capture-readme.mjs) starts its own loopback Vite server (port 5197, overridable with `COPPERBENCH_SCREENSHOT_PORT`), captures six light-theme views in each language and closes its browser/server. It rejects an occupied port and checks the active theme and unchanged frontend inputs during capture. Reusing a date replaces that date's generated light-theme images and manifest. Inspect every image, then update both root READMEs and this index together. Keep standard Markdown image syntax inside the two-column Markdown gallery; avoid wrapping the gallery in raw HTML tables. Keep older directories for historical links.

## Historical: simple creation / 2026-10-21

The **2026-10-21/** archive preserves the 13 browser-fixture screenshots and one detector result referenced by [DESIGN.md](../../DESIGN.md). They were copied unchanged from the ignored local `output/` directories on 2026-10-24 so those references work in a fresh checkout. [archive.json](2026-10-21/archive.json) records the original paths and SHA-256 digests.

| Directory | Historical scope | Verification record |
| --- | --- | --- |
| [simple-creation-validation](2026-10-21/simple-creation-validation/) | Light/dark and compact layouts, running/approval/permission states, new creation, detector findings | [Initial implementation](../../docs/testing/simple-creation-2026-10-21.md) |
| [deepseek-harness-validation](2026-10-21/deepseek-harness-validation/) | Five-provider menus and compact layout | [DeepSeek extension](../../docs/testing/deepseek-harness-2026-10-21.md) |
| [simple-creation-fixes](2026-10-21/simple-creation-fixes/) | Question forms and orphaned-draft recovery | [Review fixes](../../docs/testing/simple-creation-fixes-2026-10-21.md) |

The images retain their original fixture states and framing, including cropped controls and the hidden scenario toolbar in the compact question capture. Archiving these files does not rerun or extend their historical validation. The root READMEs continue to use the 2026-10-24 gallery above.

## Historical: 0.1.4 / 2026-10-06

**v0.1.4/en/** and **v0.1.4/zh-CN/** show the overview, manual-source editor and relationship graph in the 0.1.4 React frontend. Captured on 2026-10-06 from product source **aabd70bc3b0eb05f8ef0484209d87f2bc3f63c4f** (unchanged frontend in documentation checkout **b9c36480**).

These are direct, unretouched 1600 × 1000 Chromium screenshots of the browser preview using the repository's bundled **Copper Trails** mock data. The scenario selector and example diagnostics remain visible. They show the interface; they are not native JCEF, installed-package or gameplay acceptance evidence. No user workspace is included.

## Historical: 2026-09-14

The original **en/** and **zh-CN/** images remain available for historical references. They show a Windows native JCEF development build with the on-disk **Mossglow Workshop** example through the Java UI-Core bridge. They include the workbench, Function, Procedure, asset and history views and do not represent the 0.1.4 layout.
