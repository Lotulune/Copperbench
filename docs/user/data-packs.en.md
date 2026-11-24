# Data pack workspaces

[简体中文](data-packs.md) · [User guide](README.md)

A data pack is a standalone workspace with no Fabric or NeoForge. It builds a ZIP that goes straight into a world's `datapacks` folder. Recipes, loot tables, advancements, functions and tags for a mod still belong in the mod workspace; you do not need a separate data pack for them. The current source has this entry; it is not in a released package yet.

## Create one

**New workspace** lists three generators in the **Standalone data pack** row. Enter a name and an ID; no Java package is needed. The ID is the namespace of your own content, and the content check resolves references within the pack with it.

| Generator | Types the new-element dialog offers |
| --- | --- |
| `datapack-26.1.x` | Advancement, biome, damage type, dimension, enchantment, feature, function, loot table, recipe, structure, villager trade |
| `datapack-1.21.1` | Advancement, biome, damage type, dimension, enchantment, feature, function, loot table, recipe, structure |
| `datapack-1.20.1` | Advancement, biome, damage type, dimension, function, loot table, recipe, structure |

Content without an element type, such as predicates, item modifiers, tag files and other registries, is written as JSON or `.mcfunction` directly under `src/main/data/<namespace>/`. Minecraft 26.2 and 26.3 have no data pack generator yet.

## Start from an existing data pack

After choosing a data pack generator, use **Start from an existing data pack (optional)** to pick a data pack ZIP. The picker is available in the desktop app only. The product reads the archive first and writes nothing yet:

- It picks the version from the format that `pack.mcmeta` declares: format 15 is 1.20.1, 48 is 1.21.1 and 101 is 26.1.x. When the pack declares several formats (`supported_formats`, `min_format` / `max_format`), the newest available version among them is selected. The form warns you if you then choose a version outside that range.
- Empty name and ID fields are filled from the ZIP file name and the namespace with the most files. Shared namespaces such as `minecraft` and `c` are never proposed as the ID.
- The form lists what will not be imported and which `pack.mcmeta` fields will not be kept.

After you confirm, the product generates the data pack skeleton and then copies the ZIP's `data/` and `pack.png` into `src/main/`. Imported files are hand-written files that you can edit in **Source**.

- The generator owns `pack.mcmeta` and keeps only a plain-text description. Fields such as `overlays`, `filter`, `supported_formats` and text-component descriptions are not kept.
- Anything at the ZIP's top level other than `data/`, `pack.png` and `pack.mcmeta` is not imported, for example overlay folders, `assets/` and readme files. A ZIP that wraps the pack in one folder is recognized.
- An archive may hold up to 20,000 entries, 64 MiB per file and 256 MiB in total. Entries with `..`, absolute paths or duplicate names refuse the import, and no files are created.

When creation finishes, the banner shows how many files were imported and what the content check found. For example, importing a 1.20 pack into a 1.21.1 workspace names its old `recipes/` and `functions/` folders. Builds fail until those problems are fixed.

## Validate, build and export

A build packs `src/main` into `build/export/export.zip`; deliveries use `.zip`.

Validation, builds and exports first check what the game will read under `src/main`, whether an element generated the file or someone wrote it. The task fails on these problems, and each diagnostic names the file:

- JSON that does not parse.
- File names or paths with characters Minecraft refuses, such as uppercase letters.
- Folder names that the target version does not read: 1.21 and later use singular folders such as `recipe/`, `function/` and `tags/item/`, while 1.20.1 uses plural ones. Multi-version packs that keep both sets are accepted.
- `pack.mcmeta` without a format or a description.
- Malformed tag entries.
- References within the workspace's own namespace to content that the pack does not define: functions, function tags, tags, advancements, loot tables, recipes, or data-driven registry entries such as biomes and damage types.

References into `minecraft:`, `c:` or other namespaces are not checked, because the game, a mod or another pack may provide them. Whether values make sense, such as whether a recipe's items exist, still needs checking in game. **Validate** runs only this check, without Gradle, so it is a quick confirmation after writing JSON.

## Test it in game

Use a dedicated test world, never an important one. Copy the exported ZIP into the world's `datapacks` folder, enter the world and run `/datapack list` to confirm that the pack is enabled. After a change, rebuild, replace the ZIP and run `/reload`.

`datapack-1.21.1` has been verified in the Fabric 1.21.1 GameTest host to load functions, recipes and tags. Confirm in-game loading for the other versions in a test world.

## Agents and the command line

- `bootstrap create-workspace` accepts `--import-archive <data-pack.zip>` for `datapack-*` generators. With `--task-authorization`, the ZIP must also be inside the authorized directory.

  ```powershell
  .\copperbench.exe bootstrap create-workspace --generator-id datapack-1.21.1 --mod-name "Ember Tweaks" --mod-id ember --workspace-folder "$env:USERPROFILE\MCreatorWorkspaces\ember" --import-archive "$env:USERPROFILE\Downloads\ember.zip" --task-authorization '<user-issued authorization ID>' --no-prompt true
  ```

- UI-Core and MCP `create_workspace` accept `importArchivePath` as an absolute path. A successful result reports `data.imported` with `importedFiles`, `skipped`, `droppedMetadata` and `contentIssues`.
- Import failures use the diagnostic codes `IMPORT_REQUIRES_DATA_PACK`, `IMPORT_ARCHIVE_UNREADABLE`, `IMPORT_ARCHIVE_NOT_DATA_PACK`, `IMPORT_ARCHIVE_TOO_LARGE` and `IMPORT_ARCHIVE_PATH_INVALID`. Content-check diagnostics start with `DATA_PACK_`.
