# Copperbench Linux x86_64

Copperbench 0.1.1 is the stable Stage17 update. The frozen candidate from `b813e6cf` passed all ten installed acceptance gates and both legacy-preferences migration cases. Its separate digest-bound release authorization promotes those exact tested bytes. The update includes consistent diagnostics and resource refresh, preserved procedure drafts, the English workbench, and the installed function-save correction. See the [installed acceptance report](https://github.com/Lotulune/Copperbench/blob/main/docs/testing/stage17-stable-installed-acceptance-2026-09-27.md).

The Linux payload provides a Debian desktop package and a portable tarball with bundled JBR 25/JCEF and Java 21.
The validation target is Ubuntu 24.04 LTS x86_64, GNOME Wayland and GNOME on Xorg. Other distributions are not certified
by this validation. Blockbench remains a separately installed external application.

Automatic-login GNOME sessions can require normal login-keyring authentication before Chromium initializes.
Build dependencies require access to the official repositories. Desktop credentials remain local; never paste them
into chat or diagnostic logs.

The original development-candidate metadata retains its historical classification. The attached `LINUX-RELEASE-AUTHORIZATION.json` binds this release to the candidate source, package hashes and installed evidence. The repository's post-publication support record identifies its applicable release tag. Promotion does not rebuild the tested binaries.

Install the Debian package with `sudo apt install ./copperbench_0.1.1_amd64.deb`, or extract the portable archive and run `Copperbench011/copperbench.sh`. The Debian package can be uninstalled with `sudo apt remove copperbench`.

Existing pre-XDG Linux preference files are copied on first use only when no active XDG preference file exists. Originals are retained; explicit COPPERBENCH_HOME/MCREATOR_HOME roots remain isolated. The accepted runtime replay covers Fabric 1.21.1 and NeoForge 1.21.1.

GitHub exposes the portable asset as `Copperbench.0.1.1.Linux.x86_64.tar.gz`. The immutable candidate manifest retains its original space-separated filename. To use the original candidate verifier locally, save the downloaded archive as `Copperbench 0.1.1 Linux x86_64.tar.gz`; its bytes and SHA-256 are unchanged. Build validation used warmed caches and does not certify cold-cache or default-network behavior.
