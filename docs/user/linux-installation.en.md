# Copperbench Linux installation

[简体中文](linux-installation.md) · [Next: Trail Block](first-creation.en.md)

Open the Release linked from the Linux row in [current downloads](../releases/current.md), choose graphical `Setup.tar.gz`, Debian `.deb` or portable `.tar.gz`, and review that package's source and acceptance scope. Linux and Windows versions are published separately. The target is Ubuntu 24.04 LTS x86_64 with GNOME Wayland/Xorg. JBR 25/JCEF and Java 21 are bundled; no system Java, Gradle or Git is required. Other distributions and architectures are unverified.

## Verify and install

Use the same release's `linux-candidate-sha256.txt`, candidate metadata, provenance and `LINUX-RELEASE-AUTHORIZATION.json`. Verify against your downloaded version's manifest; the hashes below apply only to their named versions.

The [Linux 0.1.19 release](https://github.com/Lotulune/Copperbench/releases/tag/v0.1.19-linux-stable) published these hashes, which apply only to that version:

- `copperbench_0.1.19_amd64.deb`: `af40c52ada059be26e1b56f86c5cbedd8ce641ee7cf7552d4790c13ece53e93e`.
- `Copperbench.0.1.19.Linux.x86_64.tar.gz`: `936db85bcb361d75460aa95cf6e87f957a0aec8fb60076653a622fd8e2008ef5`.
- `Copperbench.0.1.19.Linux.x86_64.Setup.tar.gz`: `d71214cf321ae72298ccf8513c28c232aff8951ca413f7bd924783c3372e6d19`.

For the graphical wizard, extract `Setup.tar.gz` and run `Install Copperbench`. Follow the [English wizard instructions](graphical-installation.md#english).

The commands below use 0.1.19; for another version, use that Release's filenames and extracted directory. From the download directory, install the Debian package, then start Copperbench from the applications menu or with `copperbench`:

```sh
sudo apt install ./copperbench_0.1.19_amd64.deb
```

For the 0.1.19 portable package:

```sh
tar -xzf Copperbench.0.1.19.Linux.x86_64.tar.gz
./Copperbench0119/copperbench.sh
```

GitHub normalizes spaces in candidate filenames to dots. The original manifest uses `Copperbench 0.1.19 Linux x86_64.tar.gz`; the normalized published filename has the same bytes and SHA-256. A checker using original candidate names needs that original filename.

## Settings, scope and removal

Preferences default to `~/.config/copperbench`, or the `copperbench` child of an absolute `XDG_CONFIG_HOME`. Legacy `~/.copperbench` preferences migrate only when the new configuration is absent; the original is retained. An explicit `COPPERBENCH_HOME` or `MCREATOR_HOME` uses a separate directory. A workspace's `.copperbench` directory contains project metadata.

GNOME automatic login can require unlocking the keyring through the normal system prompt before JCEF initializes. First builds can download dependencies; Blockbench is installed separately. Network, cache, desktop and gameplay acceptance remain scoped to the downloaded package's [release record](../releases/current.md).

Uninstall the Debian package with `sudo apt remove copperbench`; preferences and user workspaces remain. Continue with [Trail Block](first-creation.en.md). If your installed version has no first-launch tour, follow the tutorial's manual Fabric 1.21.1 workspace route.

## 0.1.19 publication and verification

Version 0.1.19 introduces the Notebook interface, keeps professional Agent approvals visible, shares CLI permission guidance between both workbenches and fixes backend transaction and workspace-event ordering. Thirteen candidate gates, the independent consumer, provenance and complete downloaded-draft comparison passed. See the [publication record](../testing/linux-publication-0.1.19.md) for public-package and installation/upgrade/removal checks. Full GNOME/Wayland desktop and player-controlled gameplay certification remain outside this release's new claims; `formalSupportClaim=false`.

Changes for that version are in the [Linux 0.1.19 notes](../releases/v0.1.19-linux.md); current platform versions remain in [current downloads](../releases/current.md).

## Historical 0.1.7 package verification

These hashes apply only to 0.1.7, not to other versions linked from current downloads:

- `copperbench_0.1.7_amd64.deb`: `7c632878bf8537638466435e79de8254230dbb941b81e0214c59e634f72d44a1`.
- `Copperbench.0.1.7.Linux.x86_64.tar.gz`: `fdaa48f6a33f5387380fe8070c68a5a8639d1335aba3113ac26f18e2432a0598`.
- `Copperbench.0.1.7.Linux.x86_64.Setup.tar.gz`: `685cd8c91c2508c1ce95a1d75380a3cca2f7ba0086ade05a33a6c6d51f11180b`.

GitHub normalizes spaces in candidate filenames to dots. The original 0.1.7 manifest uses `Copperbench 0.1.7 Linux x86_64.tar.gz`; its normalized published filename retains the same bytes and SHA-256.

The [0.1.7 publication record](../testing/maintenance-release-0.1.7.md) distinguishes package/layout, isolated bootstrap, bilingual installer, JCEF X11 and Fabric/NeoForge render CI from full GNOME installation and gameplay acceptance. Full installation, preference migration and gameplay were not repeated on the final 0.1.7 packages. The [0.1.4 publication record](https://github.com/Lotulune/Copperbench/blob/07924ba11a00a8bab9ac1b720bbc01867f27a506/docs/testing/product-shell-linux-publication-0.1.4.md) and [0.1.2 installed acceptance](../testing/maintenance-012-linux-installed-2026-09-28.md) retain their original package scope.

Changes for that version remain in the [historical 0.1.7 Linux notes](../releases/v0.1.7-linux.md).
