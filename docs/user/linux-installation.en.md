# Copperbench Linux installation

[简体中文](linux-installation.md) · [First creation](first-creation.en.md)

Use the Linux stable release linked from [current downloads](../releases/current.md) for Ubuntu 24.04 LTS x86_64. It provides graphical `Setup.tar.gz`, Debian `.deb` and portable `.tar.gz`. Other distributions/architectures are unverified. JBR 25/JCEF and Java 21 are bundled; no system Java, Gradle or Git is required.

## Install the current release

Use the **same release's** `linux-candidate-sha256.txt`, candidate metadata, provenance and `LINUX-RELEASE-AUTHORIZATION.json`. Do not reuse historical checksums.

1. Graphical installer: extract `Setup.tar.gz`, run **Install Copperbench**, and follow the [English wizard steps](graphical-installation.md#english).
2. Debian: run `sudo apt install ./"downloaded-package.deb"` in the download directory, replacing the quoted filename with the actual downloaded name. Launch from the menu or `copperbench`.
3. Portable: extract the actual release archive and run `copperbench.sh` in its extracted directory; do not assume the old directory name.

Candidate package, permission, startup, installer/JCEF/X11 rendering checks and full GNOME sessions are separate evidence. The current release does not expand full GNOME/Wayland installation, upgrade, removal, real CLI-account or player-controlled gameplay certification. See [current release scope](../releases/current.md#current-release-scope--当前发布范围) for the exact identities and results.

## Historical 0.1.4 files and commands

The following names/commands apply only to existing 0.1.4 packages. For a new installation, use the current release above.

Check the release's `linux-candidate-sha256.txt`, `LINUX-RELEASE-AUTHORIZATION.json` and provenance. Install the Debian package with:

```sh
sudo apt install ./copperbench_0.1.4_amd64.deb
```

Launch Copperbench from the application menu or `copperbench`. For the portable package:

```sh
tar -xzf Copperbench.0.1.4.Linux.x86_64.tar.gz
./Copperbench014/copperbench.sh
```

GitHub normalizes spaces in the candidate filename to dots. The original manifest uses `Copperbench 0.1.4 Linux x86_64.tar.gz`; the published normalized filename has the same bytes. A checker using the original candidate name needs that original filename.

Preferences default to `~/.config/copperbench`, or `copperbench` below an absolute `XDG_CONFIG_HOME`. Legacy `~/.copperbench` preferences are copied only when the new configuration is absent; originals remain. Explicit `COPPERBENCH_HOME`/`MCREATOR_HOME` choose a separate directory. A workspace's `.copperbench` contains project metadata, not user preferences.

GNOME automatic login can require unlocking the keyring through the normal system prompt before JCEF initializes. First builds can download dependencies; Blockbench is installed separately. The [original 0.1.4 publication record](../testing/product-shell-linux-publication-0.1.4.md) retains its package/CI scope; it does not establish acceptance for later versions.

Uninstall with `sudo apt remove copperbench`; preferences/workspaces remain. For graphical packages, follow the [wizard guide](graphical-installation.md#english). Continue with [Trail Block](first-creation.en.md).
