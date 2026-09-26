# Installed function save correction — 2026-09-27

The installed blocker is resolved by the rebuilt `b813e6cf` candidate; see the [completed installed acceptance](stage17-stable-installed-acceptance-2026-09-27.md). Public publication remains a separate protected workflow step. The original failure below remains historical evidence: during installed Ubuntu acceptance, a newly created Fabric 1.21.1 function could not save its edited code because the product rejected `/tags` with `FIELD_UNSUPPORTED`. The saved definition retained `# New Copperbench function` while the UI retained the unsaved `say Stage17 stable 0.1.1 persistence` draft. The observation occurred on September 26 UTC / September 27 Asia/Tokyo.

## Exact candidate and evidence

The affected candidate was built from `bef9a7ee72b048a040ec3f7ad0ff8913f92382fa` by [run 36245988459](https://github.com/Lotulune/Copperbench/actions/runs/36245988459). Its Debian SHA-256 is `640d52064d0a486bf1487b370d39383f982dc1c02bb9d187ef399ce90629e675`; installed JAR SHA-256 is `3c25834fe8b2a8a712dba85043622fd65a4d309d046bbd69085add7e0e850aaa`.

[Failure and partial acceptance receipt](../../evidence/stage17/2026-09-27/function-save-release-blocker.json) and [observed save rejection](../../evidence/stage17/2026-09-27/function-save-rejected.png) preserve this failed gate. The dedicated VM upgrade retained the four protected original fixture files. Portable build, installed old/modern preferences migration, managed Blockbench, installed Asset Center, and Wayland Fabric/NeoForge build/render checks passed for these exact bytes. UI persistence and the remaining Xorg/external-Agent gates did not pass and are not inferred from historical releases. Automated process cleanup is not normal-close evidence. Builds used warmed caches; missing official Mojang metadata and assets were hash-verified before retrying, with initial failures retained locally.

## Cause and correction

`FunctionWorkbench` synthesized `minecraft:load` and always submitted `/tags`, even when the native editor projection omitted that unsupported field. `ElementMappingSupport` correctly permits only function code/commands and namespace in its specialized adapter. The existing mock editor exposed writable tags and therefore concealed the installed mismatch.

The workbench now uses the projection's exact paths and writable fields for save requests. Missing tags remain empty, imported read-only tags remain visible and unchanged, and unsupported tag controls are disabled. Code and namespace also respect read-only fields. A failed projection load cannot save invented defaults. The native backend's unsupported-field guard is unchanged.

Four regression cases first failed against the old UI. After correction, five projection cases plus the existing writable-tag scenario pass at both 1920×1080 and 1366×768 (12 tests). The production UI build and Chinese/English localization checks pass. These browser tests establish the correction's request contract; installed verification of a rebuilt candidate remains required.

## Release consequence

This correction changes shipped UI resources. The old Linux candidate cannot be promoted for the corrected release, and the source-delta guard continues rejecting it. The new immutable `b813e6cf` candidate has now passed all ten installed gates and both preference migrations. No source-delta allowlist exception or substituted pass result was used. The user authorized archiving and repairing the unpublished Windows tag, and its original signed object was preserved as `archive/v0.1.1-before-windows-fixture-fix`. Publication must use the corrected source plus the committed acceptance declaration on latest main.
