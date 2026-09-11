# Stage 15 clean Ubuntu GNOME Hyper-V checklist

This is an environment and acceptance checklist for the Stage 15 Linux formal-support gate. Creating a VM, booting
Ubuntu, or passing a hosted Xvfb workflow is **not** clean-GNOME certification. A prepared helper is not evidence until
it is executed against the exact candidate inside the real guest session.

## 1. Host and ISO readiness

Use an official Ubuntu **24.04 LTS Desktop amd64** ISO from Canonical and obtain its SHA-256 from Canonical's published
checksum for that exact point release. Keep the ISO and checksum outside the repository; do not commit installation
media or credentials.

Run the read-only probe first:

```powershell
pwsh -NoProfile -File .\scripts\verify-stage15-linux-hyperv-ready.ps1 `
  -IsoPath D:\ISO\ubuntu-24.04.x-desktop-amd64.iso `
  -ExpectedIsoSha256 <canonical-sha256>
```

`readyToCreateCleanVm=true` means only that Hyper-V, the selected virtual switch and VHD root drive, unused VM/VHD
names, an Ubuntu Desktop-sized ISO file, its filename, and supplied SHA-256 are consistent. The probe does not create
or alter a VM and does not prove the ISO came from Canonical; the maintainer must obtain both ISO and checksum from the
official source.

## 2. Create the guest

Run elevated. For the recommended partially automated path, create the VM **without** `-Start` first:

```powershell
pwsh -NoProfile -File .\scripts\New-Stage15LinuxHyperVGuest.ps1 `
  -IsoPath D:\ISO\ubuntu-24.04.x-desktop-amd64.iso `
  -ExpectedIsoSha256 <canonical-sha256>
```

The script creates a Generation 2 VM with dynamic memory, a 100 GB VHDX, the Default Switch, Linux-compatible Secure
Boot (`MicrosoftUEFICertificateAuthority`) and automatic checkpoints disabled. It preserves Hyper-V's default integration-
service enablement instead of turning on optional guest services for automation convenience, and refuses to overwrite an
existing VM or VHDX. VM creation is setup only; `formalSupportClaim` and clean-GNOME certification remain false.

Optionally attach the repository's credential-free CIDATA seed before the first boot:

```powershell
pwsh -NoProfile -File .\scripts\New-Stage15LinuxAutoinstallSeed.ps1 -Start
```

The seed follows Canonical's NoCloud/autoinstall mechanism but deliberately keeps `identity` interactive and does not
add the kernel `autoinstall` parameter, so the installer retains its disk-write confirmation safeguard. It selects the
standard `ubuntu-desktop` source, disables optional drivers/codecs/OEM additions and SSH, stores no password or SSH key,
and runs a late assertion that fails if the target unexpectedly contains system Java, Gradle, or Git. The assertion
does **not** uninstall anything. If it fails, record the image/setup cause and rebuild the guest instead of modifying the
guest to manufacture a clean result.

Install the normal Ubuntu 24.04 Desktop environment through VMConnect. Fill the identity section yourself and explicitly
approve the install. Do **not** install Java, Gradle, Git, Blockbench, or Copperbench during OS setup. Do not disable
Secure Boot merely to make the gate easier.

## 3. Establish the clean desktop baseline

At the first GNOME login, before Copperbench or Blockbench installation, open a terminal in the logged-in graphical
session and verify:

```bash
uname -m
cat /etc/os-release
printf 'desktop=%s\nsession=%s\n' "$XDG_CURRENT_DESKTOP" "$XDG_SESSION_TYPE"
for tool in java gradle git; do command -v "$tool" || printf '%s=absent\n' "$tool"; done
```

Expected baseline: Ubuntu `24.04`, `x86_64`, GNOME, and no system `java`, `gradle`, or `git`. If any forbidden tool is
present, do not delete it merely to manufacture a pass; record the image/setup cause and recreate the clean guest.

Use the normal GNOME **Wayland** session for the primary target. Later log out and choose **GNOME on Xorg** from the
login-screen session selector for the compatibility replay. Run graphical verification from a terminal opened inside
the active desktop session; SSH/PowerShell Direct cannot substitute for the real Wayland/Xorg environment variables.

## 4. Transfer one exact green candidate

Use one completed Stage 15 GitHub Actions run and keep all of these tied to that same run:

- `copperbench_*_amd64.deb`;
- `linux-candidate-sha256.txt`;
- `LINUX-CANDIDATE-METADATA.json` and `linux-candidate-manifest.json`;
- `stage15-linux-installed-gate-harness.tar.gz`.

Transfer them by the guest browser or an ordinary shared medium. Do not install Git merely to fetch the harness. Extract
the harness in the guest and read `INSTALLED-GATE.md` before executing it.

## 5. Two-loader / two-session replay without inventing extra requirements

Use disposable workspaces. A practical matrix that covers both Loader builds and both desktop backends is:

1. GNOME Wayland: run `verify-stage15-linux-installed-guest.sh` with a `fabric-1.21.1` workspace.
2. GNOME on Xorg: run the same gate with a `neoforge-1.21.1` workspace.

This gives clean-candidate generate/build/render evidence for both Loaders and exercises the product/JCEF session
classification under both Wayland and Xorg. The PRD requires **at least one** real user-visible interactive graphical
`runClient`; it does not require two separate Loader windows merely for symmetry.

In at least one session, open the installed Copperbench UI, create/save/reopen a disposable workspace, confirm the JCEF
window is visibly usable, open **AI 与 MCP**, reveal the one-time token, and run the bundled external-Agent helper. The
helper machine-checks MCP read/write/plan/build/conflict, interactive `run_client` render readiness and continued task
lifetime, then asks the tester to confirm the Minecraft window is visible and close Minecraft normally. Later it asks
for a normal Copperbench close and verifies descriptor/old-endpoint cleanup.

## 6. Real Blockbench after the clean-tooling evidence

Only after the no-Java/Gradle/Git baseline has been captured may a real Linux Blockbench package be installed. Run the
bundled `Stage15InstalledBlockbenchVerifier.java` exactly as documented in `INSTALLED-GATE.md`, make one visible model
edit in Blockbench, save, and close normally. Also perform the separate installed **Asset Center → open in Blockbench**
observation. The helper proves production discovery/process/lease/change behavior; it does not synthesize the JCEF click.

## 7. Closeout boundary

Keep Wayland/Xorg environment facts, helper JSON, product/Minecraft logs and the exact candidate SHA together. Do not
promote Linux support merely because the helpers were packaged successfully. After the clean GNOME gate is green, the
remaining Stage 15 closeout still includes affected Windows installed-product regression replay and the existing
exact-binary release-candidate/promotion chain.
