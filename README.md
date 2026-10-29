<div align="center">
  <img src="assets/branding/copperbench-icon-source.png" alt="Copperbench" width="96">
  <h1>Copperbench</h1>
  <p><strong>Visual editing, manual Java and local Agent collaboration in one Minecraft mod project.</strong></p>
  <p><strong>English</strong> · <a href="README.zh-CN.md">简体中文</a></p>
  <p>
    <a href="https://github.com/Lotulune/Copperbench/actions/workflows/test.yml"><img src="https://img.shields.io/github/actions/workflow/status/Lotulune/Copperbench/test.yml?branch=main&amp;style=flat-square&amp;label=CI" alt="Build and test status on main"></a>
    <a href="docs/releases/current.md"><img src="https://img.shields.io/badge/App-Windows%20%7C%20Linux-7289da?style=flat-square" alt="App: Windows and Linux"></a>
    <a href="docs/user/README.md"><img src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge-c77d48?style=flat-square" alt="Loaders: Fabric and NeoForge"></a>
    <a href="#development"><img src="https://img.shields.io/badge/JDK-25-437a54?style=flat-square" alt="Development JDK: 25"></a>
    <a href="LICENSE.txt"><img src="https://img.shields.io/badge/License-GPL--3.0--only-2ea44f?style=flat-square" alt="License: GPL-3.0-only"></a>
  </p>
  <p><a href="docs/releases/current.md">Download</a> · <a href="docs/README.md">Documentation</a> · <a href="#development">Run from source</a></p>
</div>

Copperbench is a desktop workbench built on MCreator, with Fabric and NeoForge support. Start a block in the visual editor, extend behavior in manual Java, then let an external Agent inspect the same workspace, repair a build and return a reviewable artifact through a local session. Revisions, source hashes and generated/manual ownership protect the handoff between these steps.

**Current release: 0.1.18 for Windows and Linux.** This release adds static model and element previews, brings the shared Agent workflow into the professional workbench, and improves Blockbench opening and saved-model synchronization. See [current downloads](docs/releases/current.md) for packages and release details.

<a id="getting-started"></a>

## Download and get started

Download links and package formats are maintained in [current downloads](docs/releases/current.md). Follow the [Windows quick start](docs/user/getting-started.en.md) or [Ubuntu installation guide](docs/user/linux-installation.en.md).

1. Download your platform's package and verify it against the attached checksum manifest.
2. Create a workspace and choose a loader and Minecraft version.
3. Use Simple creation to work with a local assistant, or switch to the professional workbench for visual editors and source. Follow [your first creation](docs/user/first-creation.en.md), then [build recovery and export](docs/user/build-and-export.en.md).

The workbench supports English and Simplified Chinese. Bundled Fabric / NeoForge generators cover Minecraft **26.2, 26.1.2, 1.21.1 and 1.20.1**; each workspace uses one active generator.

Windows packages are not Authenticode-signed and may trigger SmartScreen. The first build needs network access. Blockbench and creation-assistant CLIs are installed and configured separately; the Python workbench needs Python 3.11 or later.

<a id="showcase"></a>

## Workbench

| Simple creation | Professional workbench |
| :---: | :---: |
| ![Light-theme Simple creation: content, conversation and element settings](assets/screenshots/2026-10-24/en/simple-creation-light.png) | ![Light-theme professional workbench: project overview and navigation](assets/screenshots/2026-10-24/en/workbench-light.png) |
| Project content, assistant conversation and common settings in one view. | Open editors, inspect project status, and build or run from the overview. |
| **Mod Element editing** | **Java source** |
| ![Light-theme Mod Element editor: element list and block properties](assets/screenshots/2026-10-24/en/elements-light.png) | ![Light-theme Java source editor](assets/screenshots/2026-10-24/en/source-light.png) |
| Browse elements and edit their fields in the inspector. | Browse, search and edit manual files; generated files carry a read-only label. |
| **Python workbench** | **Relationships** |
| ![Light-theme Python workbench: built-in script, console and current context](assets/screenshots/2026-10-24/en/python-light.png) | ![Light-theme workspace relationship graph](assets/screenshots/2026-10-24/en/relations-light.png) |
| Script editor, console and current workspace context. | Browse, search and locate workspace elements and assets by category. |

Captured on 2026-10-24 from this checkout's light-theme browser preview with bundled Copper Trails example data. [Source and capture instructions](assets/screenshots/README.md)

## What you can do

| Feature | In practice |
| --- | --- |
| Simple creation and Agent | Switch between Simple creation and the professional workbench, sharing assistant conversations. Current source integrates Codex CLI, Claude Code, Grok Build, Google Antigravity CLI and DeepSeek Harness. |
| Visual editors | Create blocks, items, entities and other Mod Elements; arrange logic with Blockly and follow field diagnostics to the relevant control. |
| Source and Python | Edit manual Java with draft and conflict protection; use the [Python workbench](docs/user/python-workbench.md) for scripts and a persistent console connected to the current workspace. Generated files remain read-only. |
| Assets and models | Inspect textures, static model geometry and element appearance; open models in Blockbench and synchronize saved changes. |
| Workspace tools | Browse relationships, edit variables/tags/translations, and review local history and migration previews. |
| Build and deliver | Build, launch a test client or server, follow diagnostics to source, and export a mod. Exact verified export binds the artifact to the selected acceptance record. |

See the [user guide](docs/user/README.md) for feature and generator coverage. Bedrock Add-ons are outside the current first-party Java editing scope.

## Documentation

The key installation, first-creation, build/export and Agent routes have paired English and Simplified Chinese pages with same-topic language links.

- [Documentation index](docs/README.md) · [First creation](docs/user/first-creation.en.md) · [Build recovery and export](docs/user/build-and-export.en.md)
- [Simple creation（简体中文）](docs/user/simple-creation.md) · [Python workbench（简体中文）](docs/user/python-workbench.md) · [Blockbench setup（简体中文）](docs/user/blockbench-setup.md)
- [Agent onboarding](docs/ai/getting-started.en.md) · [The same Trail Block through public APIs](docs/ai/trail-block.en.md) · [SDK](sdk/README.md)
- [Development setup](docs/build/development-setup.en.md) · [Contributing](CONTRIBUTING.md) · [Repository layout](docs/maintenance/repository-maintenance.md)
- [Current release and source status](docs/status.md) · [Tests and release records](docs/testing/README.md) · [Historical roadmap](docs/roadmap/README.md)

<a id="development"></a>

## Run from source

Requires JDK 25 (JetBrains Runtime with JCEF for the native desktop app), Node.js 22, npm, and Git. Windows commands use PowerShell 7. Configure the relevant tools using the [development setup guide](docs/build/development-setup.en.md).

```powershell
git clone https://github.com/Lotulune/Copperbench.git
Set-Location Copperbench
npm ci --prefix ui-core
npm ci --prefix ui-shell
.\gradlew.bat runProductShell
```

On Linux, use `./gradlew runProductShell`. Builds use the Gradle Wrapper included in the repository.

## Credits and license

Copperbench is an independent derivative of MCreator, licensed under [GPL-3.0-only](LICENSE.txt). Thanks to Pylo and the [MCreator contributors](https://github.com/MCreator/MCreator/graphs/contributors). See [UPSTREAM.md](UPSTREAM.md) for the pinned upstream version and source records.

[Additional terms](LICENSE-ADDITIONAL-TERMS.md) retain the template exception, trademark terms, and Minecraft mappings notice. Third-party licenses and credits are in [license](license/) and [compliance](compliance/). MCreator is a trademark of Pylo; its names and logos are not licensed under the GPL.

**Not an official MCreator or Minecraft product. Not approved by or associated with Mojang or Microsoft.**
