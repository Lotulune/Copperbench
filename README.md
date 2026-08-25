# Copperbench

Copperbench is a Windows 11 desktop Minecraft mod-creation workbench. It is an independent GPL-3.0 derivative of MCreator, with the Fabric generator included as a built-in plugin.

Public distribution is the GitHub repository [Lotulune/Copperbench](https://github.com/Lotulune/Copperbench) and unsigned GitHub Releases. There is no product website, app-store listing, or Authenticode-signed installer. Windows SmartScreen may warn on the unsigned binaries. The product ID `dev.copperbench.studio` is a reverse-DNS identifier, not a live website.

Immutable source records are in [`UPSTREAM.md`](UPSTREAM.md) and [`compliance/baseline.lock.json`](compliance/baseline.lock.json). Start with the [development setup](docs/build/development-setup.md) or the [Windows clean-build baseline](docs/build/windows-clean-build.md).

## Development

Closed capabilities are specified in [`PRD.md`](PRD.md). The next delivery is the [trusted preview and Stage 9 closure](PRD-NEXT.md). Domain terms are in [`CONTEXT.md`](CONTEXT.md).

Copperbench uses the checked-in Gradle Wrapper for building and packaging. CI covers Java, UI-Core schemas, the React shell, fast Playwright scenarios, MCP conformance, Javadoc, and local Markdown links. Packaging and publication rules are in the [Windows release process](docs/build/release-process.md).

User documentation starts with [Getting Started](docs/user/getting-started.md) and [Troubleshooting](docs/user/troubleshooting.md). Local AI integrations start with the [MCP guide](docs/ai/getting-started.md).

Windows packages include JetBrains Runtime with JCEF `25.0.3+1-b329.124`. Automatic news, update, analytics, and Discord connections are disabled.

The `net.mcreator` packages, `.mcreator` workspace extension, and MCreator core version remain intact where compatibility requires them. Product-owned Java code uses the `dev.copperbench` namespace.

> [!TIP]
> It is recommended to use Intellij IDEA for development and testing. Learn more about the development process, and 
tips on [MCreator developers wiki](https://github.com/MCreator/MCreator/wiki).

## Upstream and attribution

Inherited contribution guidance remains in [`CONTRIBUTING.md`](CONTRIBUTING.md). The pinned upstream source is MCreator `2026.2.33518` at `361429609b772039a3eb9ab81662c25b225f1d0d`.

Big thanks to [all the people](https://github.com/MCreator/MCreator/graphs/contributors) who already contributed to MCreator! 💚

<a href="https://github.com/MCreator/MCreator/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=MCreator/MCreator" width="615"/>
</a>

### Translations

> [!TIP]
> If you would like to help us translate MCreator to your language, join us on [translate.mcreator.net](https://translate.mcreator.net/)! If your language is not on the list yet, feel free to suggest us to add it.

## License and trademark

MCreator is licensed under the GPL-3.0 license (with exceptions implemented as specified in section 7 of GPL-3.0) if not otherwise stated in source files or other files of this project. Copyright 2020 Pylo and [contributors](https://github.com/MCreator/MCreator/graphs/contributors).

MCreator is a trademark of Pylo. Custom distributions of this software may not include Pylo or MCreator trademark (trademark name and logo) to not confuse the software with the official distribution of MCreator project.

Copperbench is the public product name of this unofficial derivative. It must not be presented as official MCreator, or as a Minecraft / Mojang product.
MCreator and Pylo brand files in this repository are not covered by the GPL-3.0 license.

MCreator uses several third-party libraries and projects. License files, attributions, and credits for these projects are located in the `license` subdirectory.

Some code generators use official Minecraft mappings. 
The use of these mappings is covered under a license by Microsoft. You should
be fully aware of this license and the fact your mod may use these mappings.
At the time of writing, the license is:

> © 2020 Microsoft Corporation. These mappings are provided "as-is" and you bear 
> the risk of using them. You may copy and use the mappings for development purposes, 
> but you may not redistribute the mappings complete and unmodified. 
> Microsoft makes no warranties, express or implied, with respect to the mappings 
> provided here.  Use and modification of this document or the source code (in any form) 
> of Minecraft: Java Edition is governed by the Minecraft End User License Agreement 
> available at https://account.mojang.com/documents/minecraft_eula.

## Notice

> [!IMPORTANT]
> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
