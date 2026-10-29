# Copperbench quick start

[简体中文](getting-started.md) · [First creation](first-creation.en.md) · [Build recovery and export](build-and-export.en.md)

Get the latest Windows and Linux packages from [current downloads](../releases/current.md), which also records package formats and release scope. Targets are Windows 11 x64 (build 22000+) and Ubuntu 24.04 LTS x86_64. Other Linux distributions and architectures are unverified.

Verify the download against the same release's manifest: `SHA256SUMS.txt` on Windows or `linux-candidate-sha256.txt` on Linux. Windows packages are not Authenticode-signed and can trigger SmartScreen. The application runtime is bundled; the first workspace/build can download Gradle, loader and Minecraft dependencies. Allow at least 8 GB RAM and enough disk space for those dependencies.

1. On Windows, run the EXE installer or extract the complete portable ZIP and launch `copperbench.exe`. On Ubuntu, follow [Linux installation](linux-installation.en.md). See the [English wizard instructions](graphical-installation.md#english) for graphical installation.
2. Choose the optional mainland-China dependency mirrors when asked. Windows preferences default to `%USERPROFILE%\.copperbench`; Linux uses `~/.config/copperbench` and respects `XDG_CONFIG_HOME`.
3. Choose **New creation** in Simple creation, or switch to the professional workbench and choose **New workspace**. Select Fabric 1.21.1 for the Trail Block tutorial; choose the loader and version required by other projects.
4. Enter the project name and a new workspace directory, check the generated mod ID and Java package, then create. Configure a local assistant CLI using [Simple creation（简体中文）](simple-creation.md), or use the professional visual editors. Follow [Trail Block](first-creation.en.md) to save, build, obtain/place the block and reopen the same world in a new client process.
5. Continue to [build recovery and export](build-and-export.en.md) to deliver a JAR.

Each workspace has one active generator. Available fields and generation depend on that loader/version/plugin; a supported track does not establish every gameplay combination. Bedrock Add-ons are outside the first-party Java scope. Blockbench is optional and installed separately.

The professional Agent panel shares conversations with Simple creation. The [Python workbench（简体中文）](python-workbench.md) provides scripts and a persistent console and requires Python 3.11 or later. See [current status](../status.md) for the distinction between published packages and this local source tree.

If startup is blank, use the package's bundled runtime and fully extract `jdk/` and `lib/` into a writable directory. If downloads/builds fail, retain the task's first diagnostic and log, then use [recovery steps](build-and-export.en.md#recover-a-failed-build). Warm-cache checks do not establish a first offline build. Exact published-package limits remain on the status/release pages.
