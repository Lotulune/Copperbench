# Install Copperbench

[简体中文](getting-started.md) · [Create your first block](first-creation.en.md)

Copperbench 0.1.5 is available for **Windows 11 x64** and **Ubuntu 24.04 LTS x86_64 with GNOME**. Download your package and its checksum file from [current downloads](../releases/current.md).

## Windows

1. Download the EXE installer and compare its SHA-256 with `SHA256SUMS.txt` from the same release.
2. Open the installer, choose English and your preferred theme, review the license and select an installation folder. New installations use the current user; upgrading an existing system installation may request administrator permission.
3. Install, then choose **Open Copperbench**. Your saved application language takes precedence over the installer choice.

Windows packages are not Authenticode-signed and may trigger SmartScreen. The portable ZIP can instead be extracted into a new folder and launched with `copperbench.exe`.

## Ubuntu

1. Download **Linux x86_64 Setup.tar.gz** and check it against `linux-candidate-sha256.txt` from the same release.
2. Extract the entire archive. In Files, open the properties of **Install Copperbench** and allow execution if needed, then use **Run as a Program**. GNOME may open a terminal window automatically; you do not need to type a command.
3. Choose English, review the license and install. New installations default to your user directory. Upgrading a system `.deb` installation uses the package manager and may ask for system authorization.

The release also includes a `.deb` package and a portable `.tar.gz`. For detailed alternatives, see the [Linux installation guide (Chinese)](linux-installation.md).

## First launch

Choose a dependency mirror if needed, then follow [your first creation](first-creation.en.md). The installer contains its Java runtime and application payload; the first workspace build still needs network access to download Minecraft, Gradle and loader dependencies. Blockbench is installed separately when you need it.

Keep your workspaces outside the application directory. Uninstallation preserves personal preferences and independently stored workspaces. Platform and release-specific validation is recorded in [current downloads](../releases/current.md).
