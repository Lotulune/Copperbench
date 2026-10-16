# Linux installation — Copperbench 0.1.7

[简体中文](linux-installation.md) · [Next: Trail Block](first-creation.en.md)

Download the [published Linux 0.1.7 release](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.7-linux-stable) for Ubuntu 24.04 LTS x86_64 with GNOME Wayland/Xorg. It provides graphical `Setup.tar.gz`, Debian `.deb` and portable `.tar.gz` packages. JBR 25/JCEF and Java 21 are bundled; no system Java, Gradle or Git is required. Other distributions and architectures are unverified. Publication, source and acceptance scope are recorded in [current status](../status.md).

## Verify and install

Use the same release's `linux-candidate-sha256.txt`, candidate metadata, provenance and `LINUX-RELEASE-AUTHORIZATION.json`. The published 0.1.7 hashes are:

- `copperbench_0.1.7_amd64.deb`: `7c632878bf8537638466435e79de8254230dbb941b81e0214c59e634f72d44a1`.
- `Copperbench.0.1.7.Linux.x86_64.tar.gz`: `fdaa48f6a33f5387380fe8070c68a5a8639d1335aba3113ac26f18e2432a0598`.
- `Copperbench.0.1.7.Linux.x86_64.Setup.tar.gz`: `685cd8c91c2508c1ce95a1d75380a3cca2f7ba0086ade05a33a6c6d51f11180b`.

For the graphical wizard, extract `Setup.tar.gz` and run `Install Copperbench`. Follow the [English wizard instructions](graphical-installation.md#english).

Install the Debian package, then start Copperbench from the applications menu or with `copperbench`:

```sh
sudo apt install ./copperbench_0.1.7_amd64.deb
```

For the portable package:

```sh
tar -xzf Copperbench.0.1.7.Linux.x86_64.tar.gz
./Copperbench017/copperbench.sh
```

GitHub normalizes spaces in candidate filenames to dots. The original manifest uses `Copperbench 0.1.7 Linux x86_64.tar.gz`; the normalized published filename has the same bytes and SHA-256. A checker using original candidate names needs that original filename.

## Settings, scope and removal

Preferences default to `~/.config/copperbench`, or the `copperbench` child of an absolute `XDG_CONFIG_HOME`. Legacy `~/.copperbench` preferences migrate only when the new configuration is absent; the original is retained. An explicit `COPPERBENCH_HOME` or `MCREATOR_HOME` uses a separate directory. A workspace's `.copperbench` directory contains project metadata.

GNOME automatic login can require unlocking the keyring through the normal system prompt before JCEF initializes. First builds can download dependencies; Blockbench is installed separately. No new cold-cache, default-network or offline certification is claimed.

The [0.1.7 publication record](../testing/maintenance-release-0.1.7.md) distinguishes package/layout, isolated bootstrap, bilingual installer, JCEF X11 and Fabric/NeoForge render CI from full GNOME installation and gameplay acceptance. Full installation, preference migration and gameplay were not repeated on the final 0.1.7 packages. The [0.1.4 publication record](../testing/product-shell-linux-publication-0.1.4.md) and [0.1.2 installed acceptance](../testing/maintenance-012-linux-installed-2026-09-28.md) retain their original package scope.

Uninstall the Debian package with `sudo apt remove copperbench`; preferences and user workspaces remain. Continue with [Trail Block](first-creation.en.md). Platform releases are listed in [current downloads](../releases/current.md).
