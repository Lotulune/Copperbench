# 数据包工作区

[English](data-packs.en.md) · [用户指南](README.md)

数据包是不依赖 Fabric 或 NeoForge 的独立工作区，产物是可以直接放进存档 `datapacks` 目录的 ZIP。模组里的配方、战利品表、进度、函数和标签仍在模组工作区中制作，不需要另建数据包。当前源码提供这个入口，它尚未随安装包发布。

## 新建

「新建工作区」的「独立数据包」一行提供三个生成器。填写名称和 ID 即可，不需要 Java 包名。ID 是你自己内容的命名空间，内容检查用它解析包内引用。

| 生成器 | 新建元素对话框提供的类型 |
| --- | --- |
| `datapack-26.1.x` | 进度、生物群系、伤害类型、维度、附魔、地物、函数、战利品表、配方、结构、村民交易 |
| `datapack-1.21.1` | 进度、生物群系、伤害类型、维度、附魔、地物、函数、战利品表、配方、结构 |
| `datapack-1.20.1` | 进度、生物群系、伤害类型、维度、函数、战利品表、配方、结构 |

没有元素类型的内容，例如谓词、物品修饰器、标签文件和其他注册表，直接在 `src/main/data/<命名空间>/` 下编写 JSON 或 `.mcfunction`。Minecraft 26.2 与 26.3 目前没有数据包生成器。

## 从已有数据包开始

选择数据包生成器后，用「从已有数据包开始（可选）」选择一个数据包 ZIP。这个选择器只在桌面应用中可用。产品会先读取压缩包，此时不写入任何文件：

- 按 `pack.mcmeta` 声明的格式选择版本：格式 15 对应 1.20.1，48 对应 1.21.1，101 对应 26.1.x。包同时声明多个格式（`supported_formats`、`min_format` / `max_format`）时，选择其中最新的可用版本。你改选的版本不在声明范围内时，表单会提示。
- 名称和 ID 为空时，用 ZIP 文件名和包内文件最多的命名空间填写。`minecraft`、`c` 等公共命名空间不会被选作 ID。
- 表单会列出不会导入的内容，以及 `pack.mcmeta` 中不会保留的字段。

确认创建后，产品先生成数据包骨架，再把 ZIP 中的 `data/` 和 `pack.png` 复制到 `src/main/`。导入的文件都是手写文件，可以在「源码」中编辑。

- `pack.mcmeta` 由生成器维护，只保留纯文本描述。`overlays`、`filter`、`supported_formats` 和富文本描述等字段不会保留。
- ZIP 根目录下除 `data/`、`pack.png`、`pack.mcmeta` 以外的内容不导入，例如覆盖层目录、`assets/` 和说明文件。只包了一层文件夹的 ZIP 也能识别。
- 压缩包最多 20000 个条目，单个文件不超过 64 MiB，总计不超过 256 MiB。含 `..`、绝对路径或重复名称的条目会使导入被拒绝，此时不会创建任何文件。

创建完成后，横幅会显示导入的文件数和内容检查结果。例如把 1.20 的包导入 1.21.1 工作区时，检查会指出 `recipes/`、`functions/` 等旧目录名。修好这些问题之前，构建会失败。

## 校验、构建与导出

构建把 `src/main` 打包为 `build/export/export.zip`，交付文件使用 `.zip`。

校验、构建和导出都会先检查 `src/main` 下游戏将读取的内容，无论文件由元素生成还是手写。发现以下问题时任务失败，诊断会指出具体文件：

- JSON 无法解析。
- 文件名或路径含大写字母等 Minecraft 不接受的字符。
- 目录名与目标版本不符：1.21 起使用 `recipe/`、`function/`、`tags/item/` 等单数目录，1.20.1 使用复数目录。同时保留两套目录的多版本数据包不报错。
- `pack.mcmeta` 缺少格式号或描述。
- 标签条目格式错误。
- 在本工作区命名空间内，引用了包里不存在的内容：函数、函数标签、标签、进度、战利品表、配方，或生物群系、伤害类型等数据驱动注册项。

`minecraft:`、`c:` 等其他命名空间的引用不检查，因为游戏、模组或其他数据包可能提供它们。字段取值是否合理，例如配方用到的物品是否存在，仍要进游戏确认。「校验」只运行这项检查，不启动 Gradle，适合写完 JSON 后快速确认。

## 在游戏中测试

用专门的测试存档，不要用重要存档。把导出的 ZIP 复制到存档的 `datapacks` 目录，进入存档后运行 `/datapack list`，确认它已启用。修改后重新构建，替换 ZIP，再运行 `/reload`。

`datapack-1.21.1` 已在 Fabric 1.21.1 GameTest 宿主中验证过函数、配方和标签的加载。其他版本的游戏内加载需要你在测试存档中确认。

## Agent 与命令行

- 引导命令 `bootstrap create-workspace` 接受 `--import-archive <数据包.zip>`，只适用于 `datapack-*` 生成器。使用 `--task-authorization` 时，ZIP 也必须位于授权目录之内。

  ```powershell
  .\copperbench.exe bootstrap create-workspace --generator-id datapack-1.21.1 --mod-name "Ember Tweaks" --mod-id ember --workspace-folder "$env:USERPROFILE\MCreatorWorkspaces\ember" --import-archive "$env:USERPROFILE\Downloads\ember.zip" --task-authorization '<user-issued authorization ID>' --no-prompt true
  ```

- UI-Core 与 MCP 的 `create_workspace` 接受 `importArchivePath`，值为绝对路径。成功结果的 `data.imported` 包含 `importedFiles`、`skipped`、`droppedMetadata` 和 `contentIssues`。
- 导入失败的诊断代码为 `IMPORT_REQUIRES_DATA_PACK`、`IMPORT_ARCHIVE_UNREADABLE`、`IMPORT_ARCHIVE_NOT_DATA_PACK`、`IMPORT_ARCHIVE_TOO_LARGE` 和 `IMPORT_ARCHIVE_PATH_INVALID`。内容检查的诊断代码以 `DATA_PACK_` 开头。
