# Public release cloud acceptance

Dispatch the existing **Agent readiness M1 gates** workflow with `release_tag`
and `previous_release_tag` (for example `v0.1.18` and `v0.1.17`). Empty inputs keep
the original source-level M1 gates. The reusable workflow tests public assets on
Windows Server 2025 and Ubuntu 24.04; it does not rebuild Copperbench.

`packs_only=true` is a supplemental Setup lifecycle and three-data-pack run,
used to collect independent evidence after a mod export fails. It omits the DEB
lane and mod acceptance, and records `nativeMode=packs`; it cannot replace or
turn a failed full run into a pass.

`windows_source_sha=<full SHA>` runs the same full Windows test commands used by
the release workflow before a tag is created. Its receipt records both the tested
commit/tree and the workflow commit. It does not publish packages or certify a desktop.

`linux_candidate_run` with `linux_candidate_source` independently downloads and
verifies the frozen Linux candidate in a cloud consumer, then uploads its small
evidence bundle. Its product version comes from `mcreator.conf` in the exact
source commit, and must match both candidate metadata and the packaged JAR.
The shipped download index, Linux installation guide and development guide are
compared byte-for-byte with that source. The original 0.1.9 document checks still
apply to 0.1.9 packages. Version-specific 0.1.13 notes are not shipped by its
frozen export configuration, so they are not required inside that archive. The
legacy fix and template checks remain required. This supports the 0.1.13 candidate without rebuilding it or
substituting current-checkout documentation. See the
[Linux 0.1.13 preparation record](../../docs/testing/maintenance-linux-0.1.13.md).

`windows_release_source` similarly derives the version from the exact source and checks every public
Windows asset against API digests, checksums and signed provenance and inspects
the packaged fix classes/templates. These read-only byte checks do not replace
installed-product acceptance.

`run.py` refuses to change OS registration outside this repository's disposable
GitHub-hosted runners. It verifies GitHub asset digests and signed provenance
against the release's source tag and producing workflow/run before execution.
It installs the requested version fresh, uninstalls it, installs the previous version, upgrades to the requested version, then
uninstalls the upgraded package. Version arguments also permit future releases.

The Setup cases compile only `InstallerProbe` and the existing component test
against the published setup JAR. They call the real installer transaction and
registration code; Windows uses the original EXE registrar and uninstaller.
Linux user uninstall calls the same installed engine and registration removal
used by the wizard. The DEB case uses the system package manager. Unmanaged
files, preferences sentinels and external workspace/export bytes must survive.

The current installer removes its temporary rollback copy after successful
activation and registration. Acceptance requires a null backup result and no
new previous-version directories, records the old application hash before
upgrading, and verifies that the installed application changed to the published
payload. The runner does not remove leftover installer backups on the product's
behalf. Historical receipts retain their original harness and package scope.

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
That smoke starts with the released 1.20.1 Gradle 8.3 wrapper so its migration
and first distribution download are exercised before the new runner cache is warm.

Evidence, previews, command exits, task receipts and hash manifests are uploaded
from `output/minecraft-validation/cloud-release/`, including failed attempts.
Swing component tests and offscreen previews do not certify Windows 11, UAC,
MSIX, GNOME Wayland/Xorg or player interaction. No Minecraft control bridge is
started. Windows uses an ASCII installation path with spaces: the hosted runner's
external Java driver cannot transfer Chinese command-line arguments. Non-ASCII
Windows paths and world saving/reopening by a player remain unverified.
