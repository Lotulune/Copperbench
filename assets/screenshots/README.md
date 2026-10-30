# Current promotional images and screenshots

Both root READMEs lead with the introduction animation below. For static covers and product illustrations, use the versioned UI images listed here. The [Copperbench icon](../branding/copperbench-icon-source.png) and its [source details](../branding/README.md) are the current branding assets.

## README introduction: 0.1.17

[Copperbench introduction GIF](../promo/v0.1.17/copperbench-intro.gif) — 960 × 540, 242 frames, 20.17 seconds, continuous looping, 6,473,774 bytes. The on-screen captions are Chinese and the title strip identifies 0.1.17.

Prepared on 2026-10-23 from the user's supplied `copperbench-promo-960.gif`. The recording environment and exact product source commit were not supplied. The original has 271 frames and a duration of 22.58 seconds. The README copy retains frames 0–241, ending before the closing project-name/address card and its transition. Original compressed frame data, palettes and frame delays are preserved without re-encoding.

| File | SHA-256 |
| --- | --- |
| Supplied `copperbench-promo-960.gif` | `7f30440a8edce11d6f67f74d8ee1911b0b83d2af3bb9353545594e21c814ecfe` |
| README `copperbench-intro.gif` | `ccac1d4fe9902a8eb5e096f0bc5776770628eb399ee416a62823b218e878e400` |

## Current: 2026-10-31 Notebook interface (0.1.19 frontend)

Both root READMEs display six light-theme views from **2026-10-31/en/** and **2026-10-31/zh-CN/** in a two-column Markdown table with three image rows, titles and short descriptions: Simple creation, the professional workbench, Mod Element editing, Java source, Python and relationships.

| View | English | 简体中文 |
| --- | --- | --- |
| Simple creation | [Content and settings](2026-10-31/en/simple-creation-light.png) | [简洁创作](2026-10-31/zh-CN/simple-creation-light.png) |
| Professional workbench | [Overview](2026-10-31/en/workbench-light.png) | [专业工作台](2026-10-31/zh-CN/workbench-light.png) |
| Mod Elements | [Element inspector](2026-10-31/en/elements-light.png) | [模组元素编辑](2026-10-31/zh-CN/elements-light.png) |
| Source | [Manual Java](2026-10-31/en/source-light.png) | [源码编辑](2026-10-31/zh-CN/source-light.png) |
| Python | [Script editor and console](2026-10-31/en/python-light.png) | [Python 工作台](2026-10-31/zh-CN/python-light.png) |
| Relationships | [Graph](2026-10-31/en/relations-light.png) | [关系图](2026-10-31/zh-CN/relations-light.png) |

These are direct, unretouched **1600 × 1000** headless Chromium screenshots, in **light theme**, using the bundled **Copper Trails** browser development fixture. They show the Notebook interface introduced in 0.1.19. Language, theme, mode and navigation are selected through the existing application. The Mod Element view collapses its 3D preview to show the fields, and Python shows its built-in example without running it. The scenario selector, example diagnostics and unavailable desktop-assistant/Python states remain visible; in Simple creation the scenario selector overlaps the lower edge of the composer. No real user workspace or fabricated assistant conversation is included.

[capture.json](2026-10-31/capture.json) records the Git baseline (`1b22c43c4625b96a3778c85e0f3ca1c0fdc590eb`, a clean `main` checkout), hashes of the actual frontend/fixture inputs, Chromium version and every image's SHA-256. Every hashed input is unchanged between that baseline and the `v0.1.19` tag commit `a6851b3b16a071a922c833d66b3e7ae99008b58f`. These images are dated browser previews of that frontend source, **not screenshots of the published 0.1.19 Windows or Linux binaries**. Native window chrome, native Agent/Python execution, package acceptance and Minecraft gameplay are outside this capture's scope.

### Refresh the gallery

From the repository root, after installing `ui-shell` dependencies and Playwright Chromium:

```sh
node ui-shell/scripts/capture-readme.mjs YYYY-MM-DD
```

On Windows, run it with `NO_COLOR=1` set. Vite colours its startup banner on Windows even when its output is piped, which hides the plain `Local:` text the script waits for.

The [capture script](../../ui-shell/scripts/capture-readme.mjs) starts its own loopback Vite server (port 5197, overridable with `COPPERBENCH_SCREENSHOT_PORT`), captures six light-theme views in each language and closes its browser/server. It rejects an occupied port and checks the active theme and unchanged frontend inputs during capture. Reusing a date replaces that date's generated light-theme images and manifest. Inspect every image, then update both root READMEs and this index together. Keep standard Markdown image syntax inside the two-column Markdown gallery; avoid wrapping the gallery in raw HTML tables. Keep older directories for historical links.

## Historical: 2026-10-24 copper interface

The **2026-10-24/** set shows the same six views in both languages before the 0.1.19 Notebook redesign. They were captured with the same script and fixture from Git baseline `6e4f6f47718d0c2f5cd7ef47ac6ace5e4f4ac5d2`; [capture.json](2026-10-24/capture.json) records their inputs and SHA-256 digests. They are browser previews of an integration working tree, not screenshots of the published 0.1.18 binaries, and no longer represent the current interface.

| View | English | 简体中文 |
| --- | --- | --- |
| Simple creation | [Content and settings](2026-10-24/en/simple-creation-light.png) | [简洁创作](2026-10-24/zh-CN/simple-creation-light.png) |
| Professional workbench | [Overview](2026-10-24/en/workbench-light.png) | [专业工作台](2026-10-24/zh-CN/workbench-light.png) |
| Mod Elements | [Element inspector](2026-10-24/en/elements-light.png) | [模组元素编辑](2026-10-24/zh-CN/elements-light.png) |
| Source | [Manual Java](2026-10-24/en/source-light.png) | [源码编辑](2026-10-24/zh-CN/source-light.png) |
| Python | [Script editor and console](2026-10-24/en/python-light.png) | [Python 工作台](2026-10-24/zh-CN/python-light.png) |
| Relationships | [Graph](2026-10-24/en/relations-light.png) | [关系图](2026-10-24/zh-CN/relations-light.png) |

## Historical: simple creation / 2026-10-21

The **2026-10-21/** archive preserves the 13 browser-fixture screenshots and one detector result referenced by [DESIGN.md](../../DESIGN.md). They were copied unchanged from the ignored local `output/` directories on 2026-10-24 so those references work in a fresh checkout. [archive.json](2026-10-21/archive.json) records the original paths and SHA-256 digests.

| Directory | Historical scope | Verification record |
| --- | --- | --- |
| [simple-creation-validation](2026-10-21/simple-creation-validation/) | Light/dark and compact layouts, running/approval/permission states, new creation, detector findings | [Initial implementation](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/simple-creation-2026-10-21.md) |
| [deepseek-harness-validation](2026-10-21/deepseek-harness-validation/) | Five-provider menus and compact layout | [DeepSeek extension](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/deepseek-harness-2026-10-21.md) |
| [simple-creation-fixes](2026-10-21/simple-creation-fixes/) | Question forms and orphaned-draft recovery | [Review fixes](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/simple-creation-fixes-2026-10-21.md) |

The images retain their original fixture states and framing, including cropped controls and the hidden scenario toolbar in the compact question capture. Archiving these files does not rerun or extend their historical validation. The root READMEs use the current 2026-10-31 gallery above.

## Historical workbench screenshots: 0.1.4

| View | English | 简体中文 |
| --- | --- | --- |
| Workbench overview / 工作台总览 | [workbench.png](v0.1.4/en/workbench.png) | [workbench.png](v0.1.4/zh-CN/workbench.png) |
| Manual-source editor / 源码编辑器 | [source.png](v0.1.4/en/source.png) | [source.png](v0.1.4/zh-CN/source.png) |
| Relationship graph / 关系图 | [relations.png](v0.1.4/en/relations.png) | [relations.png](v0.1.4/zh-CN/relations.png) |

Captured on 2026-10-06 from product source **aabd70bc3b0eb05f8ef0484209d87f2bc3f63c4f** (unchanged frontend in documentation checkout **b9c36480**).

These are direct, unretouched 1600 × 1000 Chromium screenshots of the browser preview using the repository's bundled **Copper Trails** mock data. The scenario selector and example diagnostics remain visible. They show the interface; they are not native JCEF, installed-package or gameplay acceptance evidence. No user workspace is included.

For native desktop or gameplay footage, capture the intended product version. Historical test images in [evidence/](../../evidence/README.md) and U0 prototype renders are not substitutes for current product footage.

When updating this set, use a versioned directory, record the source commit, capture date, environment and data source, and update this index and both root READMEs together. Retire superseded display images from the current tree; Git history preserves their provenance.

## Retired: 2026-09-14 native screenshots

The ten unversioned **en/** and **zh-CN/** images were removed from the current tree on 2026-10-11. They showed the Windows native JCEF development build with the **Mossglow Workshop** example before the UI refresh. They do not represent the current interface and should not be used for current promotional material.

Historical references remain available in the fixed Git snapshot: [English](https://github.com/Lotulune/Copperbench/tree/70261a71ce0403e00f8c118026f80c3579d554b7/assets/screenshots/en) and [简体中文](https://github.com/Lotulune/Copperbench/tree/70261a71ce0403e00f8c118026f80c3579d554b7/assets/screenshots/zh-CN). The [U0 prototypes](https://github.com/Lotulune/Copperbench/tree/70261a71ce0403e00f8c118026f80c3579d554b7/prototypes) are also historical material, removed from the current tree after their design phase ended.
