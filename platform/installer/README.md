# Copperbench unified graphical installer

The independent Java 17-compatible wizard uses the runtime already present in the offline payload. It never starts the workbench, JCEF, Python or MCP during setup. Windows and Linux share its interface, language selection, license, copy/verification progress, cancellation and completion flow. The current application icon is unchanged.

## Build

On Windows, run `gradlew.bat buildInstallerWin64`. On Ubuntu, run `./gradlew buildInstallerLinux64` or `./gradlew exportLinuxCandidate`. These tasks first export the real application, including its bundled runtimes. Node.js and the existing build JDKs are required on the build host only.

`node platform/installer/build.mjs --platform windows --version 0.1.4 --compile-only` compiles only the wizard. Use the resulting JAR's `--preview <output-directory>` option to render Chinese/English light/dark screenshots without installing anything. Compile-only and small fixture packages are not application distributions.

The Windows output is `build/export/Copperbench <version> Windows 64bit.exe`. Its NSIS wrapper extracts the offline payload, checks Windows 11 x64, starts the unprivileged wizard and handles Windows registration, uninstall and authorization for existing system installations. New current-user installations do not request elevation. `/S /D=...` is intentionally rejected with exit code 2; the old Stage 8 silent rehearsal is guarded against this installer.

The Linux output is `build/export/Copperbench <version> Linux x86_64 Setup.tar.gz`, containing a shared wizard, runtime, payload manifest and the executable `Install Copperbench` script. [Mouse-only instructions](../../docs/user/graphical-installation.md) cover GNOME's **Run as a Program** menu. This menu launches a terminal automatically; it does not require a typed command. Plain double-click execution is not assumed.

## Installation and recovery

- The UTF-8 payload manifest records relative paths, entry types, SHA-256, size and executable permissions. Copies are checked before activation; relative symlinks must remain inside the payload.
- A receipt binds the product, target directory and installation scope. An unrelated existing directory or invalid receipt is rejected. Registered legacy installations are recognized separately.
- Installs stage files beside the target directory. During a direct upgrade, previous app files move to `Copperbench-previous-<UUID>`; the prior directory is restored if activation or OS registration fails. Previously unrecorded user files are kept, and the old app directory remains after success. Users can remove that backup after validating the new version.
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

`test/InstallRehearsal.java` exercises a complete Windows payload in a new temporary directory. Compile it with the installer classes on the classpath, then pass the payload, manifest, setup JAR, a new output directory and version. It deliberately does not register an application, request UAC, launch Copperbench or claim an end-to-end OS installation test.

Before release, use a clean test account for the full Windows install/start/upgrade/uninstall flow and an Ubuntu 24.04 GNOME session for extraction, the actual script context menu, authorization, launch and uninstall on Wayland/Xorg. Also re-scan the exact final Windows artifact with the reported 360 product/database version. CI/headless tests and old package acceptance cannot replace those checks.

## Build inputs and artwork

NSIS 3.12 is obtained from its [official SourceForge release](https://sourceforge.net/projects/nsis/files/NSIS%203/3.12/) and pinned to SHA-256 `56581f90db321581c5381193d796fffcf2d24b2f8fed2160a6c6a3baa67f2c4f`. The downloader handles the observed SourceForge HTML download-page response, then checks the archive digest before extraction. The default installer does not use the legacy LockedList plugin.

Every generated installer receives a `.build.json` containing its SHA-256, byte size and payload-manifest SHA-256. Linux candidate metadata and CI artifact collection include the graphical installer. If signing is enabled in a future build, compute release hashes after signing; `.build.json` describes the unsigned packaging output.

`assets/landscape.png` was generated for Copperbench on 2026-10-12 with the image-generation tool, following the approved copper/block-world installation direction. It is original generated scenery, not an extracted Minecraft game asset or an official Minecraft logo. It is distributed under the repository's GPL-3.0-only terms. Existing application icons remain governed by `compliance/branding-assets.lock.json` and were not changed.
