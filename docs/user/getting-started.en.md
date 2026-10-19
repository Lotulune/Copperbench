# Copperbench quick start

[简体中文](getting-started.md) · [First creation](first-creation.en.md) · [Build recovery and export](build-and-export.en.md)

Current stable packages for Windows and Linux are **0.1.13**; use the verified platform links in [current downloads](../releases/current.md). Targets remain Windows 11 x64 (build 22000+) and Ubuntu 24.04 LTS x86_64. Other distributions and architectures are unverified.

Verify the download against the same release's manifest: `SHA256SUMS.txt` on Windows or `linux-candidate-sha256.txt` on Linux. Windows packages are not Authenticode-signed and can trigger SmartScreen. The application runtime is bundled; the first workspace/build can download Gradle, loader and Minecraft dependencies. Allow at least 8 GB RAM and enough disk space for those dependencies.

1. On Windows, run the EXE installer or extract the complete portable ZIP and launch `copperbench.exe`. On Ubuntu, follow [Linux installation](linux-installation.en.md). Follow the [English wizard instructions](graphical-installation.md#english) for the graphical installer.
2. Choose the optional mainland-China dependency mirrors when asked. Windows preferences default to `%USERPROFILE%\.copperbench`; Linux uses `~/.config/copperbench` and respects `XDG_CONFIG_HOME`.
3. In the first-launch tour, choose **Start creating**, check the Fabric 1.21.1 preset and select a new empty workspace directory before confirming. If you skip the tour, use **New workspace** and select Fabric 1.21.1 manually.
4. Follow [Trail Block](first-creation.en.md) to save, build, obtain/place the block and reopen the same world in a new client process.
5. Continue to [build recovery and export](build-and-export.en.md) to deliver a JAR.

Each workspace has one active generator. Available fields and generation depend on that loader/version/plugin; a supported track does not establish every gameplay combination. Bedrock Add-ons are outside the first-party Java scope. Blockbench is optional and installed separately.

If startup is blank, use the package's bundled runtime and fully extract `jdk/` and `lib/` into a writable directory. If downloads/builds fail, retain the task's first diagnostic and log, then use [recovery steps](build-and-export.en.md#recover-a-failed-build). Warm-cache checks do not establish a first offline build. For later updates, choose **Check for updates** in **Help & about** and follow the prompts. See the [Windows release notes](../releases/v0.1.13.md), [Linux release notes](../releases/v0.1.13-linux.md) and [current downloads](../releases/current.md) for platform changes and verification scope.
