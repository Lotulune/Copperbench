# Linux installation — Copperbench 0.1.13

[简体中文](linux-installation.md) · [Next: Trail Block](first-creation.en.md)

Download the [published Linux 0.1.13 release](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.13-linux-stable) for Ubuntu 24.04 LTS x86_64 with GNOME Wayland/Xorg. It provides graphical `Setup.tar.gz`, Debian `.deb` and portable `.tar.gz` packages. JBR 25/JCEF and Java 21 are bundled; no system Java, Gradle or Git is required. Other distributions and architectures are unverified. Publication, source and acceptance scope are recorded in [current status](../status.md).

## Verify and install

Use the same release's `linux-candidate-sha256.txt`, candidate metadata, provenance and `LINUX-RELEASE-AUTHORIZATION.json`. The published 0.1.13 hashes are:

- `copperbench_0.1.13_amd64.deb`: `ae4839c26d372e76e56bf55396993021cf0670b94ffdff639c9d63a4533a9b26`.
- `Copperbench.0.1.13.Linux.x86_64.tar.gz`: `a142f745943778dd541b2b522749942da138ff03090e8d9949be9f381cb171c6`.
- `Copperbench.0.1.13.Linux.x86_64.Setup.tar.gz`: `d7103fa1267c6e791a7deba8397fc764d45e997dcd41f8db01dd2fa3ea5f464d`.

For the graphical wizard, extract `Setup.tar.gz` and run `Install Copperbench`. Follow the [English wizard instructions](graphical-installation.md#english).

Install the Debian package, then start Copperbench from the applications menu or with `copperbench`:

```sh
sudo apt install ./copperbench_0.1.13_amd64.deb
```

For the portable package:

```sh
tar -xzf Copperbench.0.1.13.Linux.x86_64.tar.gz
./Copperbench0113/copperbench.sh
```

GitHub normalizes spaces in candidate filenames to dots. The original manifest uses `Copperbench 0.1.13 Linux x86_64.tar.gz`; the normalized published filename has the same bytes and SHA-256. A checker using original candidate names needs that original filename.

## Settings, scope and removal

Preferences default to `~/.config/copperbench`, or the `copperbench` child of an absolute `XDG_CONFIG_HOME`. Legacy `~/.copperbench` preferences migrate only when the new configuration is absent; the original is retained. An explicit `COPPERBENCH_HOME` or `MCREATOR_HOME` uses a separate directory. A workspace's `.copperbench` directory contains project metadata.

GNOME automatic login can require unlocking the keyring through the normal system prompt before JCEF initializes. First builds can download dependencies; Blockbench is installed separately. No new cold-cache, default-network or offline certification is claimed.

Version 0.1.13 includes shared desktop, native-window and in-app update improvements. The [publication record](../testing/linux-publication-0.1.13.md) records 13 candidate gates, independent consumer verification, signed provenance, complete downloaded-draft byte comparison and matching sizes/SHA-256 digests for all 18 public assets. Full GNOME installation, upgrade, uninstall and player-controlled gameplay acceptance were not repeated. The [0.1.4 publication record](../testing/product-shell-linux-publication-0.1.4.md) and [0.1.2 installed acceptance](../testing/maintenance-012-linux-installed-2026-09-28.md) retain their original package scope.

Uninstall the Debian package with `sudo apt remove copperbench`; preferences and user workspaces remain. Continue with [Trail Block](first-creation.en.md). See the [Linux 0.1.13 release notes](../releases/v0.1.13-linux.md) and [current downloads](../releases/current.md).
