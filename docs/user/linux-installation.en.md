# Copperbench Linux installation

[简体中文](linux-installation.md) · [Next: Trail Block](first-creation.en.md)

Open the Release linked from the Linux row in [current downloads](../releases/current.md), choose graphical `Setup.tar.gz`, Debian `.deb` or portable `.tar.gz`, and review that package's source and acceptance scope. Linux and Windows versions are published separately. The target is Ubuntu 24.04 LTS x86_64 with GNOME Wayland/Xorg. JBR 25/JCEF and Java 21 are bundled; no system Java, Gradle or Git is required. Other distributions and architectures are unverified.

## Verify and install

Use the same release's `linux-candidate-sha256.txt`, candidate metadata, provenance and `LINUX-RELEASE-AUTHORIZATION.json`. Verify against your downloaded version's manifest; the old hashes at the end of this page apply only to their historical packages.

The [Linux 0.1.13 release](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.13-linux-stable) published these hashes, which apply only to that version:

- `copperbench_0.1.13_amd64.deb`: `ae4839c26d372e76e56bf55396993021cf0670b94ffdff639c9d63a4533a9b26`.
- `Copperbench.0.1.13.Linux.x86_64.tar.gz`: `a142f745943778dd541b2b522749942da138ff03090e8d9949be9f381cb171c6`.
- `Copperbench.0.1.13.Linux.x86_64.Setup.tar.gz`: `d7103fa1267c6e791a7deba8397fc764d45e997dcd41f8db01dd2fa3ea5f464d`.

For the graphical wizard, extract `Setup.tar.gz` and run `Install Copperbench`. Follow the [English wizard instructions](graphical-installation.md#english).

The commands below use 0.1.13; for another version, use that Release's filenames and extracted directory. From the download directory, install the Debian package, then start Copperbench from the applications menu or with `copperbench`:

```sh
sudo apt install ./copperbench_0.1.13_amd64.deb
```

For the 0.1.13 portable package:

```sh
tar -xzf Copperbench.0.1.13.Linux.x86_64.tar.gz
./Copperbench0113/copperbench.sh
```

GitHub normalizes spaces in candidate filenames to dots. The original manifest uses `Copperbench 0.1.13 Linux x86_64.tar.gz`; the normalized published filename has the same bytes and SHA-256. A checker using original candidate names needs that original filename.

## Settings, scope and removal

Preferences default to `~/.config/copperbench`, or the `copperbench` child of an absolute `XDG_CONFIG_HOME`. Legacy `~/.copperbench` preferences migrate only when the new configuration is absent; the original is retained. An explicit `COPPERBENCH_HOME` or `MCREATOR_HOME` uses a separate directory. A workspace's `.copperbench` directory contains project metadata.

GNOME automatic login can require unlocking the keyring through the normal system prompt before JCEF initializes. First builds can download dependencies; Blockbench is installed separately. Network, cache, desktop and gameplay acceptance remain scoped to the downloaded package's [release record](../releases/current.md).

Uninstall the Debian package with `sudo apt remove copperbench`; preferences and user workspaces remain. Continue with [Trail Block](first-creation.en.md). If your installed version has no first-launch tour, follow the tutorial's manual Fabric 1.21.1 workspace route.

## 0.1.13 publication and verification

Version 0.1.13 includes shared desktop, native-window and in-app update improvements. The [publication record](../testing/linux-publication-0.1.13.md) records 13 candidate gates, independent consumer verification, signed provenance, complete downloaded-draft byte comparison and matching sizes/SHA-256 digests for all 18 public assets. Full GNOME installation, upgrade, uninstall and player-controlled gameplay acceptance were not repeated. The [0.1.4 publication record](../testing/product-shell-linux-publication-0.1.4.md) and [0.1.2 installed acceptance](../testing/maintenance-012-linux-installed-2026-09-28.md) retain their original package scope.

Changes for that version are in the [Linux 0.1.13 notes](../releases/v0.1.13-linux.md); current platform versions remain in [current downloads](../releases/current.md).

## Historical 0.1.7 package verification

These hashes apply only to 0.1.7, not to other versions linked from current downloads:

- `copperbench_0.1.7_amd64.deb`: `7c632878bf8537638466435e79de8254230dbb941b81e0214c59e634f72d44a1`.
- `Copperbench.0.1.7.Linux.x86_64.tar.gz`: `fdaa48f6a33f5387380fe8070c68a5a8639d1335aba3113ac26f18e2432a0598`.
- `Copperbench.0.1.7.Linux.x86_64.Setup.tar.gz`: `685cd8c91c2508c1ce95a1d75380a3cca2f7ba0086ade05a33a6c6d51f11180b`.

GitHub normalizes spaces in candidate filenames to dots. The original 0.1.7 manifest uses `Copperbench 0.1.7 Linux x86_64.tar.gz`; its normalized published filename retains the same bytes and SHA-256.

The [0.1.7 publication record](../testing/maintenance-release-0.1.7.md) distinguishes package/layout, isolated bootstrap, bilingual installer, JCEF X11 and Fabric/NeoForge render CI from full GNOME installation and gameplay acceptance. Full installation, preference migration and gameplay were not repeated on the final 0.1.7 packages. The [0.1.4 publication record](../testing/product-shell-linux-publication-0.1.4.md) and [0.1.2 installed acceptance](../testing/maintenance-012-linux-installed-2026-09-28.md) retain their original package scope.

Changes for that version remain in the [historical 0.1.7 Linux notes](../releases/v0.1.7-linux.md).
