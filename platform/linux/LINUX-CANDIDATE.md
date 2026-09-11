# Copperbench Linux development candidate

This package is a **Stage 15 development candidate**, not a formally supported Linux release. The current certification target is Ubuntu 24.04 LTS x86_64 with GNOME Wayland; clean-desktop certification is still pending.

## Portable tarball

Extract the archive and run `./copperbench.sh` from the extracted directory. The launcher uses the JBR/JCEF and Java 21 runtimes bundled in the archive. Removing the extracted directory uninstalls the portable application files, but does not remove workspaces or per-user Copperbench data.

## Debian development package

Install the candidate with `sudo apt install ./copperbench_<version>_amd64.deb` and remove the package files with `sudo apt remove copperbench`. Package removal intentionally leaves workspaces and per-user Copperbench data intact.

## Per-user data and optional cleanup

Copperbench follows XDG locations on Linux. With the default XDG environment, its main application-data roots are:

- data: `~/.local/share/copperbench`
- config: `~/.config/copperbench`
- cache: `~/.cache/copperbench`
- state/logs: `~/.local/state/copperbench`
- runtime/IPC: `$XDG_RUNTIME_DIR/copperbench` when an XDG runtime directory is available

When the corresponding `XDG_*` variable is set to a valid absolute path, Copperbench uses that location instead.

Do not delete these directories during a normal upgrade or uninstall. Delete them manually only when you intentionally want to remove Copperbench settings, caches, state, or runtime remnants. User-selected Minecraft workspaces are independent of these application-data directories and are never part of package cleanup.

## Current evidence boundary

The Stage 15 candidate workflow verifies package layout, bundled runtimes, headless bootstrap, Xvfb JCEF compatibility, representative packaged `runClient` render paths, SBOM, hashes, immutable metadata, and provenance. A clean Ubuntu GNOME installed-product replay, visible desktop-window certification, external-tool replay, and installed Desktop MCP/external-Agent loop remain required before Linux can be marked formally supported.
