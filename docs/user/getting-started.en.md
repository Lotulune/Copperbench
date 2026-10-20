# Copperbench quick start

[简体中文](getting-started.md) · [First creation](first-creation.en.md) · [Build recovery and export](build-and-export.en.md)

Choose your platform's stable package from [current downloads](../releases/current.md) and review that version's verification scope. Windows and Linux versions are listed separately. Targets are Windows 11 x64 (build 22000+) and Ubuntu 24.04 LTS x86_64. Other distributions and architectures are unverified.

Verify the download against the same release's manifest: `SHA256SUMS.txt` on Windows or `linux-candidate-sha256.txt` on Linux. Windows packages are not Authenticode-signed and can trigger SmartScreen. The application runtime is bundled; the first workspace/build can download Gradle, loader and Minecraft dependencies. Allow at least 8 GB RAM and enough disk space for those dependencies.

1. On Windows, run the EXE installer or extract the complete portable ZIP and launch `copperbench.exe`. On Ubuntu, follow [Linux installation](linux-installation.en.md). For the graphical installer, use the [English wizard instructions](graphical-installation.md#english).
2. Choose the optional mainland-China dependency mirrors when asked. Windows preferences default to `%USERPROFILE%\.copperbench`; Linux uses `~/.config/copperbench` and respects `XDG_CONFIG_HOME`.
3. If a tour appears on first launch, choose **Start creating** to use the Fabric 1.21.1 preset. If your installed version has no tour, or you have skipped/ended it, use **New workspace** and select Fabric 1.21.1 manually. Other projects can use the required Fabric/NeoForge track or a resource-pack generator.
4. Check the name, ID and Java package, choose a new empty workspace directory, then confirm creation.
5. Follow [Trail Block](first-creation.en.md) to save, build, obtain/place the block and reopen the same world in a new client process. Continue to [build recovery and export](build-and-export.en.md) to deliver a JAR.

Each workspace has one active generator. Available fields and generation depend on that loader/version/plugin; a supported track does not establish every gameplay combination. Bedrock Add-ons are outside the first-party Java scope. Blockbench is optional and installed separately.

If startup is blank, use the package's bundled runtime and fully extract `jdk/` and `lib/` into a writable directory. If downloads/builds fail, retain the task's first diagnostic and log, then use [recovery steps](build-and-export.en.md#recover-a-failed-build). Warm-cache checks do not establish a first offline build. Exact published-package limits remain on the status/release pages.

Version changes, known issues and verification scope are linked from [current downloads](../releases/current.md). Each package retains the features of its release; when reading newer source-branch documentation, check your installed version and available controls.

For later updates, choose **Check for updates** in **Help & about** and follow the prompts. The [Windows 0.1.13 notes](../releases/v0.1.13.md) and [Linux 0.1.13 notes](../releases/v0.1.13-linux.md) record that release's platform changes and verification scope.
