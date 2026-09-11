# Copperbench Linux x86_64

Run37 passed ten clean-guest gates, but a subsequent legacy-preferences migration fix requires replacement-candidate validation before release eligibility. Public publication and formal-support classification remain subject to release approval.

The Linux payload provides a Debian desktop package and a portable tarball with bundled JBR 25/JCEF and Java 21.
The validation target is Ubuntu 24.04 LTS x86_64, GNOME Wayland and GNOME on Xorg. Other distributions are not certified
by this validation. Blockbench remains a separately installed external application.

Automatic-login GNOME sessions can require normal login-keyring authentication before Chromium initializes.
Build dependencies require access to the official repositories. Desktop credentials remain local; never paste them
into chat or diagnostic logs.

The original development-candidate metadata is retained as historical provenance. Release eligibility and any formal
support assertion come from the separately approved Linux release authorization, bound to the signed release tag and
immutable asset digests. Promotion does not rebuild the tested binaries.
