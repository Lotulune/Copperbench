# Copperbench unified graphical installer

The independent Java 17-compatible wizard uses the runtime already present in the offline payload. It never starts the workbench, JCEF, Python or MCP during setup. Windows and Linux share its interface, language selection, license, copy/verification progress, cancellation and completion flow. The header, window icon and executable use the shared Copperbench workbench icon.

## Build

On Windows, run `gradlew.bat buildInstallerWin64`. On Ubuntu, run `./gradlew buildInstallerLinux64` or `./gradlew exportLinuxCandidate`. These tasks first export the real application, including its bundled runtimes. Node.js and the existing build JDKs are required on the build host only.

Run `gradlew.bat installerJar` (or `./gradlew installerJar`) to compile only the wizard into `build/installer/copperbench-setup.jar`. This JAR includes the same FlatLaf version used by the application, its native decoration support, product artwork and notices; it works without the main application's classpath. The package builders consume this exact JAR through `--installer-jar`, so setup and the independent updater cannot accidentally ship a second, unthemed build. Use the JAR's `--preview <output-directory>` option to render Chinese/English light/dark screenshots without installing anything. Preview and small fixture packages are not application distributions.

The Windows output is `build/export/Copperbench <version> Windows 64bit.exe`. Its NSIS wrapper extracts the offline payload, checks Windows 11 x64, starts the unprivileged wizard and handles Windows registration, uninstall and authorization for existing system installations. New current-user installations do not request elevation. `/S /D=...` is intentionally rejected with exit code 2; the old Stage 8 silent rehearsal is guarded against this installer.

The Linux output is `build/export/Copperbench <version> Linux x86_64 Setup.tar.gz`, containing a shared wizard, runtime, payload manifest and the executable `Install Copperbench` script. [Mouse-only instructions](../../docs/user/graphical-installation.md) cover GNOME's **Run as a Program** menu. This menu launches a terminal automatically; it does not require a typed command. Plain double-click execution is not assumed.

## Installation and recovery

- The UTF-8 payload manifest records relative paths, entry types, SHA-256, size and executable permissions. Copies are checked before activation; relative symlinks must remain inside the payload.
- A receipt binds the product, target directory and installation scope. An unrelated existing directory or invalid receipt is rejected. Registered legacy installations are recognized separately.
- Installs stage files beside the target directory. During a direct upgrade, previous app files move to `Copperbench-previous-<UUID>`; the prior directory is restored if activation or OS registration fails. Previously unrecorded user files are kept, and the temporary old app directory is removed after successful registration. If cleanup fails, the installer names the remaining directory without rolling back the committed installation.
- Registry and shortcut writes on Windows, and per-user desktop entries on Linux, are backed up and restored on registration failure. Errors restoring those entries are surfaced in installation details. This is not a power-loss transaction across the filesystem and OS configuration.
- A user workspace inside the app folder blocks an upgrade. Manifest-owned example workspaces are permitted; unchanged packaged examples are also recognized during a legacy upgrade.
- Linux system `.deb` upgrades build a package from the same offline payload and invoke `pkexec dpkg --install`. They retain system scope and package-manager ownership. They do not implement automatic rollback of an old Debian package; package-manager output is shown on failure.
- Uninstall removes only manifest-owned application files. Windows preflights existing files with `OPEN_EXISTING` before removal and keeps registration on failure. Personal settings, independently chosen workspaces and unrecorded files are retained.
- Windows system uninstall requests elevation by relaunching the installed `uninstall.exe`. Relaunching NSIS's temporary `Un.exe` loses the real installation directory and exits before removal. Validate the consent prompt and actual cleanup from an unelevated test session; an already elevated shell misses this boundary.
- The keep-workspaces/close-app notice belongs to the localized uninstall confirmation page. A message box inside `un.onInit` still resolves its text using the previous language table, before NSIS applies the selected language to its pages.

The application reads a saved UI locale first, then `install-language.txt`, then the original system locale. The installer also reads the saved locale before the prior installation choice. Supported product UI languages are `zh` and `en`.

## Validation

Focused Java checks: `gradlew.bat test --tests dev.copperbench.installer.InstallEngineTest --tests dev.copperbench.shell.UiLocalePreferencesTest --tests dev.copperbench.shell.RecoverableBrowserHostTest`. The installer tests cover corrupt payloads, cancellation, receipt and path rejection, failed registration, packaged examples, upgrade preservation and uninstall preservation.

With an actual desktop display, compile `test/InstallerInteractionTest.java` against the built `copperbench-setup.jar` and run `dev.copperbench.installer.InstallerInteractionTest` with both on the classpath. This regression opens each language/theme popup, changes its selection, and checks translated content, retained form inputs and usable actions. Headless rendering does not exercise popup removal.

On Linux, run `python3 platform/installer/test/launch_detach_test.py /path/to/jdk /path/to/copperbench-setup.jar` to verify the application survives closure of setup's controlling terminal. Installer-initiated Linux launches retain output in `$XDG_STATE_HOME/copperbench/startup.log` (default `~/.local/state/copperbench/startup.log`).

On Windows, compile `test/InstallerProgressFileTest.java` against the setup JAR and run `dev.copperbench.installer.InstallerProgressFileTest` with both on the classpath. It holds a Windows handle that denies deletion, then verifies recovery after a short lock and a bounded error for a permanent lock. Progress publication retries transient Windows replacement failures for at most 900 ms, preserving the previous complete file. The test-only JDK extended open option does not enter the application payload.

On Linux, compile `test/InstallerReceiptPermissionsTest.java` against the setup JAR and run `dev.copperbench.installer.InstallerReceiptPermissionsTest` with both on the classpath. Atomic property-file replacement preserves existing POSIX permissions, so system installation receipts remain readable to ordinary users. Newly created request/progress files retain their private temporary-file permissions. The regression checks both boundaries; a real system-package upgrade and ordinary-user uninstall must still be verified on GNOME.

The same permission boundaries run in the Ubuntu Java CI job through `./gradlew test --tests dev.copperbench.installer.InstallerReceiptTest`. This test skips filesystems without POSIX permission support.

Native presentation regression: `gradlew.bat test --tests dev.copperbench.installer.NativeDialogPresentationTest --tests dev.copperbench.release.ProductUpdatesTest`. The isolated Swing probe checks both languages and themes, branded stock prompts, bounded long messages, preserved input fields, waiting/copy/authorization progress and cancellation boundaries. For desktop inspection, compile `src/test/java/dev/copperbench/installer/UpdateWorkerLocaleFixture.java` against only the setup JAR, then run that fixture with arguments `<zh|en> <waiting|preparing|progress|authorize|cleanup|error|confirm|long|details|installer|uninstall> <dark|light>`. The fixture displays production UI classes without installing files or changing OS registration; the actual display must still be inspected.

`test/InstallRehearsal.java` exercises a complete Windows payload in a new temporary directory. Compile it with the installer classes on the classpath, then pass the payload, manifest, setup JAR, a new output directory and version. It deliberately does not register an application, request UAC, launch Copperbench or claim an end-to-end OS installation test.

Before release, use a clean test account for the full Windows install/start/upgrade/uninstall flow and an Ubuntu 24.04 GNOME session for extraction, the actual script context menu, authorization, launch and uninstall on Wayland/Xorg. Also re-scan the exact final Windows artifact with the reported 360 product/database version. CI/headless tests and old package acceptance cannot replace those checks.

## Build inputs and artwork

NSIS 3.12 is obtained from its [official SourceForge release](https://sourceforge.net/projects/nsis/files/NSIS%203/3.12/) and pinned to SHA-256 `56581f90db321581c5381193d796fffcf2d24b2f8fed2160a6c6a3baa67f2c4f`. The downloader handles the observed SourceForge HTML download-page response, then checks the archive digest before extraction. The default installer does not use the legacy LockedList plugin.

Every generated installer receives a `.build.json` containing its SHA-256, byte size and payload-manifest SHA-256. Linux candidate metadata and CI artifact collection include the graphical installer. If signing is enabled in a future build, compute release hashes after signing; `.build.json` describes the unsigned packaging output.

`assets/landscape.png` was generated for Copperbench on 2026-10-12 with the image-generation tool, following the approved copper/block-world installation direction. It is original generated scenery, not an extracted Minecraft game asset or an official Minecraft logo. It is distributed under the repository's GPL-3.0-only terms. The copper workbench icon supplied on 2026-10-18 is exported deterministically by `scripts/stage0/New-CopperbenchBrandAssets.py`; source and output hashes are recorded in `compliance/branding-assets.lock.json`.

## In-app component updates

Help & about exposes the manual stable-release check. A shared background check shows a versioned update button in the lower-left corner when a newer release is available. `buildWindowsUpdates` and `buildLinuxUpdates` publish a platform inventory plus independently hashed application, library, Java and Gradle ZIP components. The client reuses unchanged local components, verifies all downloaded files, and starts the replacement worker from the prepared runtime after normal application shutdown. Release workflows include these assets in source metadata, hashes and provenance; no full-installer download is used by this path. Existing settings and unmanaged files are retained. A release must publish the component assets before it can be installed through this entry point.

On Windows, the update host preserves existing desktop and Start menu shortcuts, including user customization or removal. The full graphical installer retains its explicit shortcut creation behavior. `build-update-host.mjs` preprocesses the actual NSIS update-host build and rejects any `CreateShortCut` instruction. Registration still updates the uninstaller and application registration; it does not disable security checks or eliminate the need for administrator authorization on system installations. Rollback writes an integration file only when its contents changed. `PlatformInstallRollbackTest` covers unchanged timestamps, changed/missing files and removal of newly created files.

`/LOCATIONS` returns `ShortcutPolicy=preserve` for the update host and `ShortcutPolicy=create` for the full installer. The Java transaction omits preserved shortcuts from both its snapshot and rollback, so failed registration cannot restore or delete a shortcut the update never owned. Older registrars without this field retain the creation contract; unknown policies fail before registration.

Run `pwsh -NoProfile -File platform/installer/test/Test-ShortcutTargetPreservation.ps1 -OutputDirectory output/shortcut-target-check` with a new output directory on Windows. It replaces a fixture application directory and asks the real Windows Shell to resolve an unchanged, customized shortcut to the new payload. It neither runs the target nor writes Start menu, desktop or uninstall registration. This complements the update-host compiler invariant; it is not a 360 rescan or full installation acceptance.

The component updater passes an explicit update mode through the elevated worker. In that mode Java does not start `/LOCATIONS`: the bundled update host already preserves shortcuts, so shell-folder discovery and shortcut snapshots are unnecessary. Full graphical setup and older callers retain their location lookup. System updates still request authorization and perform required installation registration; this change does not establish that security-software alerts have disappeared.

Update workers record UTC timestamps, phase changes, helper modes, process IDs, exit codes and failures in `update-steps.log`. Elevated-worker diagnostics are relayed into `update.log` before the small worker scratch directory is removed. No arbitrary request properties or complete command-line arguments are copied into these records.

After the worker exits, application startup archives a report under the Copperbench profile's `updates/history/update-*.log` before deleting the prepared payload. The five most recent reports are retained, with at most the last 1 MiB from each of the two logs. Unrelated files are not pruned. If archival fails, the original prepared directory and logs remain available. Reports stay local; Windows defaults to `%USERPROFILE%\.copperbench\updates\history`. Existing 0.1.13 installations do not include this diagnostic retention yet.
