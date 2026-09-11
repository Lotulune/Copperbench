# Copperbench Linux x86_64

Run42 passed all ten clean-guest acceptance gates and both legacy-preferences migration cases; its separate digest-bound release authorization is approved. Public publication and formal-support classification remain subject to release approval.

The Linux payload provides a Debian desktop package and a portable tarball with bundled JBR 25/JCEF and Java 21.
The validation target is Ubuntu 24.04 LTS x86_64, GNOME Wayland and GNOME on Xorg. Other distributions are not certified
by this validation. Blockbench remains a separately installed external application.

Automatic-login GNOME sessions can require normal login-keyring authentication before Chromium initializes.
Build dependencies require access to the official repositories. Desktop credentials remain local; never paste them
into chat or diagnostic logs.

The original development-candidate metadata is retained as historical provenance. Release eligibility and any formal
support assertion come from the separately approved Linux release authorization, bound to the signed release tag and
immutable asset digests. Promotion does not rebuild the tested binaries.

Install the Debian package with `sudo apt install ./copperbench_0.1.0_amd64.deb`, or extract the portable archive and run `Copperbench010/copperbench.sh`. The Debian package can be uninstalled with `sudo apt remove copperbench`.

Existing pre-XDG Linux preference files are copied on first use only when no active XDG preference file exists. Originals are retained; explicit COPPERBENCH_HOME/MCREATOR_HOME roots remain isolated. The accepted runtime replay covers Fabric 1.21.1 and NeoForge 1.21.1.
