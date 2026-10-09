# M3 resumed acceptance — 2026-10-10 JST

The user resumed testing. All operations stayed inside isolated guests; no host
focus or VMConnect was used. The Windows and Ubuntu guests ran serially, each
with a 4 GiB minimum/startup and 5 GiB maximum. Ubuntu used temporary 4 GiB swap.
Dependency caches were retained: these are warm-cache installed regressions.
The [frozen candidates](agent-readiness-m3.md#frozen-corrected-candidates) remain
product commit `37fcb5b4`; later test/script changes do not change those binaries.

## Installed replay

| Attempt | Result | Elapsed |
| --- | --- | --- |
| Windows run-003 | Creation/discovery, build, deliberate failure, repair, 5/5 packaged tests, verified export and real reconnect passed; final conflict-fixture prerequisite was wrong, so the original overall record remains failed | 399.87 s |
| Windows run-003-conflict-replay | Corrected separate copy passed pending-generation, `SOURCE_CHANGED`, rejected-build and unchanged-external-bytes checks | 11.78 s |
| Ubuntu run-006 | Entire scripted installed replay, including the corrected conflict case, passed | 133.50 s |

The conflict copy retained `.mcreator`, so the managed edit regenerated
immediately. This was a fixture failure, not evidence of lost source protection.
The [replay](../../scripts/verify-agent-readiness-installed.py) now excludes that
generator cache and requires `dependenciesRequired=true` before the external
edit. The same helper passed on both platforms. Its SHA-256 is
`2a71499ee3802bf4b02496617516e8dd4c1b15f4744176bc62425c2a7590a830`.

| Task | Windows run-003 | Ubuntu run-006 |
| --- | --- | --- |
| Initial build | `ffb2a503-d2f2-4bf7-a743-a157fa659811` | `8e19239b-94b1-448e-a042-3db84e312f84` |
| Expected compile failure | `57caf9a7-54f8-4509-94d8-e36574285cd9` | `cbd6cf30-f7b2-4265-afbb-b834f1ede2ef` |
| Repaired build | `d27a534d-17cb-4f0c-b406-26fbcd565e3e` | `c2768367-9cf0-42ce-b03d-235307e90da1` |
| Packaged GameTest, 5/5, no failures/skips, exit 0 | `7ebbb199-7b64-4ef9-9f27-16a41cf95206` | `3a291bc4-e257-45b7-8cd0-19f9069bf451` |
| Verified export | `2de2161b-432a-4e0b-8ce0-4dfc3a8c6c3c` | `ce83efd2-04ad-49c5-ad76-ecc26edf2245` |
| Export after process reconnect | `b4578718-5ae2-4268-a8ae-d53d95edd5da` | `f1f8bc0b-183e-4f2c-9559-03b3b166b2e8` |
| Expected conflict-build failure | `79a13222-d8bd-4896-91d6-a4e95bddfe27` (separate replay) | `813f468d-193b-49a2-b675-0614f1103b60` |

Both platforms tested and exported identical mod bytes, SHA-256
`cbeb70e122aaa6d1a833b1e9776056c0f7b63abc7d16c5ac26fefe1eed08dd3e`.
The Windows XML hash is
`e7bed4f4d3faf4bce1ce3b7da40d18dcef181bb668ec2d5f8ef494304cb28d70`;
the Ubuntu XML hash is
`385bed979872163ec599a8f54b02ec2171bd9d8cc5c15946b8a46a170b2bc86e`.
The installed Ubuntu application hash is
`cc817c921272d399c0984e66c69fde71166b58d50c2aead1edb988232c4806ea`.

## Actual workspace UI

Independent warm copies on both platforms opened the installed JCEF workspace.
Guest input changed Discovery Item stack size from 16 to 15, clicked Apply,
observed the saved state and started a successful build. Saved files were checked;
both product processes exited normally with code 0. Build tasks were
`20de5645-6113-4ed0-9b61-d9d4d6fca4b2` (Windows, Gradle reported 29 s) and
`f3cde3c2-7322-45ac-89e7-14a69dfe2dbe` (Ubuntu, 8 s). These copies did not alter
the verified stack-of-16 artifact.

The first Windows UI copy omitted `.gradle`, leaving an import-cache path
unresolved. Cold setup invoked a 4 GiB decompiler and exhausted available virtual
memory. That exact decompiler was interrupted; the workspace was closed through
the setup-failure dialog and offline mode restored in Preferences before normal
exit. This failed preparation remains recorded. The warm pass does not certify
cold initialization.

## Ubuntu packaged-client gameplay

Public bootstrap created a new empty Fabric host at
`/home/cbtest/workspaces/m3/client-006`, containing the verified external JAR.
Installed SDK sessions kept each `run_client` task alive until actual exit.
Fixture preparation fixed username `CopperbenchM3`, UUID
`7815ca73-e68b-3a9d-ad39-9a880f72891e`, a 2 GiB client heap and reduced rendering.
The GNOME Xorg desktop was 1024x768 and the game capture 958x699, using software
rendering. Wayland and physical GPU rendering were not tested.

Guest Bridge 0.3.0 doctor confirmed `linux-x11`. In the unique world
`Copperbench M3 2026-10-10`, preparation commands set Peaceful and supplied
17 sticks and one dirt. Actual clicks placed dirt in the crafting grid and
attempted the empty output, then replaced it with sticks. One normal craft and
a Shift-click crafted all 17 Discovery Items. Screenshots and read-only `/data
get` queries confirmed 16 and 1 in slots 0 and 2, dirt in slot 1, and no sticks.

First client PID `5144` saved, returned to title and closed. Product task
`10916553-7895-43de-af22-cf8eb4945e46` succeeded and retained its terminal state
after a real SDK reconnect. New PID `11025`, task
`ad06ac9f-8eab-4e24-b792-d18114a3f56a`, entered the same world and restored the
same inventory. Actual UUID queries matched on both runs, not just the settings.
The second task also succeeded after normal save/quit and reconnect. Artifact
identity checks passed before and after both sessions.

Initial navigation used inspected short batches. A profile was then built from
reviewed title/world-list/pause/HUD frames; deterministic `enter_world` and
`save_and_quit` completed with matching guards. Jev was not used. Both final
close clicks lost the window during input and reported interrupted receipts;
process exit, saved-world logs, `BUILD SUCCESSFUL` and product terminal states
independently confirmed normal completion. Navigation alone was not counted as
gameplay. Both sessions retain planner verdicts, action/image/hash evidence;
the controller was detached and stopped.

| Control measurement | First client | Second client |
| --- | --- | --- |
| Attach-to-detach elapsed | 384.10 s | 142.93 s |
| Input time | 15.44 s | 5.27 s |
| Capture time / frames | 1.71 s / 22 | 2.40 s / 23 |
| Input batches / rejected | 20 / 0 | 10 / 0 |
| Interrupted final close batches | 1 | 1 |
| Jev calls | 0 | 0 |

The mailbox recorded 44 Bridge API calls, including startup diagnostics.
Elapsed time includes planner decisions and calibration; no speedup claim is made.

## Windows client boundary

The public bootstrap created `C:/M3/workspaces/client-003` and verified deployment
of the same tested JAR. Task `c2d7a37f-2543-45cb-ac09-8bb94b4f9de1` loaded the mod
but displayed **GLFW error 65542: WGL driver does not appear to support OpenGL**.
The guest adapter was Microsoft Hyper-V Video. The displayed error was dismissed
and the product recorded failure; the original task and image are retained.

A guest-only per-application Mesa dependency supplied rendering. The
[upstream Windows distribution](https://github.com/pal1000/mesa-dist-win/releases/tag/26.2.4)
MSVC release archive matched GitHub's SHA-256
`351fc8c8b695878ffb3eaa044b3ead08672a48b1a045e3c3e3975811df0f6695`.
Only `opengl32.dll` and `libgallium_wgl.dll` were added beside the guest's workspace
Java executable; no existing files or host/global graphics drivers were replaced.
The launch process supplied `GALLIUM_DRIVER=llvmpipe`. Product JAR and mod hashes
remain unchanged, but this is explicitly a Mesa-equipped test environment.
Task `4d01e29c-4b8f-45ee-887d-95b43e782620`, PID `10036`, initialized graphics and
reached the real title and new-world screens.
Loaded-module inspection confirmed both Mesa DLL paths. The client later closed
normally, the product task succeeded, and a real SDK reconnect recovered that
terminal state. This is a rendering/lifecycle pass, not a player-behavior pass.

Player acceptance remains unverified. Guest Bridge 0.3.0 stopped on
`IME_SWITCH_UNCONFIRMED`; its preserve mode also rejected `IME_ACTIVE`. Guest
layout enumeration found only Chinese HKL `134481924`, with no existing English
layout. The original long world name was truncated in the form and no world
creation was submitted. Focus recovery and mode-switch attempts are recorded;
none are counted as gameplay. The user's agent instructions prohibit installing
keyboard layouts, so adding a guest-only US keyboard requires a separate explicit
authorization. No keyboard layout was installed or loaded as a workaround.

## Test and guest-infrastructure repairs

CI exposed a `UITestUtil` race: counting `Window.getWindows()` could be affected
by GC and counted windows that had never opened. It now waits for a new window's
`WINDOW_OPENED`, preserves existing windows, cleans up on the EDT and waits for
the opener to return. Actual Windows guest JUnit results: old helper 2 failed/
1 passed; fixed helper 3/3 passed. Full CI then exposed selector/image-dialog
tests that only constructed their windows; those callers now show them.
All required checks passed on `b3c72a81`
([CI](https://github.com/Lotulune/Copperbench/actions/runs/37959130174),
[candidate smoke](https://github.com/Lotulune/Copperbench/actions/runs/37959130230)).
The frozen product binaries remain unchanged.

Ubuntu lost networking after reboot because Netplan selected NetworkManager
although that service was absent and systemd-networkd was active. A separate
local Netplan override selects networkd. Normal shutdown/reboot confirmed DHCP
and strictly host-key-verified SSH recovery. This guest-network failure is
separate from the earlier Codex upstream response-stream issue.

## Evidence and boundaries

Durable evidence in the primary checkout is
`output/agent-readiness/m3-20261010`. Windows archive SHA-256:
`82293bcb5a79250fbddb515bc4bd502c5a6071d6a0c86e3b1144bc70c22ab830`.
Ubuntu archive SHA-256:
`a87a820fc224aee68b7ada48573720f58e815f77a381c1d5492feffc592e4ef4`.
These preserve the original failures. Ubuntu evidence includes the player save,
verified JAR/XML, two completed client tasks and input/screenshot records.
The additional Windows client archive SHA-256 is
`9d3eea1232080b4dbefb03c33fa1e6afc0c358e2a517f0e96605581f0b1b7c2e`;
it includes the original OpenGL failure, Mesa deployment hashes, loaded modules,
rendering task and keyboard rejections. Both VMs were saved after normal product
exit and controller cleanup and have zero assigned RAM.

Twenty preregistered autonomous-agent tasks and 5–8 unfamiliar-user trials have
not run; no participants were contacted. Scripted replay is neither a measured
agent success rate nor a user study. Linux still has `formalSupportClaim=false`.
The PR remains draft; no merge or release is included.
