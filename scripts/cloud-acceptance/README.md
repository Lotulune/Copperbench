# Public release cloud acceptance

Dispatch the existing **Agent readiness M1 gates** workflow with `release_tag`
and `previous_release_tag` (for example `v0.1.7` and `v0.1.6`). Empty inputs keep
the original source-level M1 gates. The reusable workflow tests public assets on
Windows Server 2025 and Ubuntu 24.04; it does not rebuild Copperbench.

`packs_only=true` is a supplemental Setup lifecycle and three-data-pack run,
used to collect independent evidence after a mod export fails. It omits the DEB
lane and mod acceptance, and records `nativeMode=packs`; it cannot replace or
turn a failed full run into a pass.

`run.py` refuses to change OS registration outside this repository's disposable
GitHub-hosted runners. It verifies GitHub asset digests and signed provenance
against the release's source tag and producing workflow/run before execution.
It installs 0.1.7 fresh, uninstalls it, installs 0.1.6, upgrades to 0.1.7, then
uninstalls the upgraded package. Version arguments also permit future releases.

The Setup cases compile only `InstallerProbe` and the existing component test
against the published setup JAR. They call the real installer transaction and
registration code; Windows uses the original EXE registrar and uninstaller.
Linux user uninstall calls the same installed engine and registration removal
used by the wizard. The DEB case uses the system package manager. Unmanaged
files, preferences sentinels and external workspace/export bytes must survive.

`native.py` prepares new workspaces with the existing test-only
`Stage15GraphicalWorkspaceFixture`, compiled against the installed libraries.
This preparation does not issue task grants or simulate a user approving a
dialog. Legacy data packs are prepared with the production persisted-workspace
model, because the new-mod bootstrap does not offer these generators. All
subsequent Core sessions launch the installed executable and import
its bundled Python SDK. Setup cases exercise Fabric build/fail/repair, five
packaged-JAR GameTests, exact export, host exit/reopen and task persistence,
Fabric/NeoForge exports with no `build/libs`, and three data-pack ZIP exports.
Fresh installs and DEBs get a smaller data-pack create/edit/export smoke.

Evidence, previews, command exits, task receipts and hash manifests are uploaded
from `output/minecraft-validation/cloud-release/`, including failed attempts.
Swing component tests and offscreen previews do not certify Windows 11, UAC,
MSIX, GNOME Wayland/Xorg or player interaction. No Minecraft control bridge is
started. Windows uses an ASCII installation path with spaces: the hosted runner's
external Java driver cannot transfer Chinese command-line arguments. Non-ASCII
Windows paths and world saving/reopening by a player remain unverified.
