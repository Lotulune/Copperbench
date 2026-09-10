# Stage 15 installed validation — 2026-09-10

Stage 15 remains in progress; `formalSupportClaim=false`. This record distinguishes recovered prior-run evidence
from the new interactive replay and does not promote the historical Beta 4 machine status.

## Candidate and environment

- Worktree: `.tmp/stage15-linux`, branch `codex/stage15-linux-support`, source HEAD
  `627910b14dd7e4d82f1e8ac7d3647ccb5e6cf4c3` (Fabric 1.21.1 projectile template correction).
- Installed Run 33 package: `/home/stage15/stage15-run33/copperbench_0.1.0_amd64.deb`, 888107620 bytes,
  SHA-256 `f897aadcfac5991114c2a89bf81e251ae58a25653d1a6b8f86fdd830aebee7c2`, independently rehashed in the guest.
- Real Hyper-V Ubuntu 24.04 GNOME Xorg seat0; desktop user `stage15`. Recovered preinstall evidence records
  system Java/Gradle/Git absent. No package, system settings, CI, branch, commit or release changes were made in this replay.
- Installed version is 0.1.0. Published harness from Run 31 is used unchanged; the added optional desktop launcher
  only transports the UI-entered credential and repeats the verifier's prompts.

## Recovered evidence

- `evidence/stage15/2026-09-09/run32-xorg-fabric/`: prior Run 32 exact-binary Xorg Fabric automated preflight.
- `evidence/stage15/2026-09-10/run33-xorg-fabric/`: Run 33 automated preflight collected from the guest;
  JCEF frame loaded, Desktop MCP listening, installed-Core build succeeded with exit 0, Minecraft render markers present.
  Its original status remains `automated-preflight-passed-manual-gates-pending`.
- `evidence/stage15/2026-09-10/run32-blockbench/installed-blockbench-result.json`: prior Blockbench 5.1.6
  production locator/managed-process/lease/edit/save/normal-close pass. Exit 0, stable process,
  `BLOCKBENCH_ASSET_LEASED`, changed model SHA and `ASSET_CHANGED_EXTERNALLY` are recorded.
  This is Run 32 evidence, not a new Run 33 managed-process replay.

## External-Agent replay passed

The prior GUI launcher redirected the helper's interactive progress to a hidden log. Its shutdown deadline expired
at 06:21:10 UTC; the product log records normal workspace close at 06:32:35 UTC and Tomcat shutdown immediately
afterward. The later read-only inspection found no descriptor and no product JVM. This supports a missed shutdown
prompt/timing explanation; it is not evidence that descriptor cleanup passed within that failed attempt.
The original failure is retained in `run33-agent-timeout/helper.log`.

The new optional `scripts/stage15/run-installed-agent-desktop.py` displays a hidden Zenity credential input and
nonblocking Minecraft/Copperbench close prompts. The user copied the token from the installed UI into that input.
The credential was passed only through a private child stdin pipe; output is defensively redacted. The wrapper
does not change the verifier or its five-minute shutdown timeout and refuses existing evidence directories.

The replay completed at 07:03:24 UTC with helper exit 0 and `status=passed`:

- workspace revision 3 → 6 and element count 3 → 6;
- cursor traversal, direct create, Workspace Plan preview/apply, first build and final build passed;
- stale revision rejected with `WORKSPACE_REVISION_CONFLICT`, reread/retry committed;
- real Minecraft 1.21.1/Fabric reached LWJGL/resource/atlas readiness and remained running for ten seconds;
- the agent observed the real Minecraft main menu and clicked **Quit Game**; the MCP run task reached `succeeded`;
- the agent clicked the installed workspace's native close button after the desktop shutdown prompt;
- descriptor removed, old authenticated connection rejected with `MCP_TRANSPORT_FAILED`;
- verifier reports no credential in persisted evidence or automation audit.

Evidence: `evidence/stage15/2026-09-10/run33-agent-xorg/{external-agent-result.json,desktop-launcher-result.json,helper.log}`.
The original helper's getpass fallback warning refers to the private pipe, not an echoing terminal.
After a separate installed launch, the UI overview read back revision 6 and six elements; this proves Agent-created
data survived reopen, but does not by itself prove the UI-create/save/reopen gate.

## Asset Center visible Blockbench launch passed

At 07:28:44 UTC, clicking **Asset Center → 在 Blockbench 打开** on the selected `Stage15Probe` opened the real
Blockbench window titled `Stage15Probe - Blockbench`, with its mesh visibly rendered. The child process was PID 14946,
`/opt/Blockbench/blockbench`; its parent PID 14479 resolved to `/opt/copperbench/jdk/bin/java`. The source model
was a copy of the prior disposable probe, prepared on disk before re-entering Asset Center. This verifies the UI
open action, not UI import. Native close was clicked and the child exited; the installed Asset Center remained open.

Evidence: `evidence/stage15/2026-09-10/run33-asset-center-blockbench.json` and
`evidence/stage15/2026-09-10/run33-asset-center-blockbench.png`.

## Installed UI persistence passed; independent JCEF subprocess defect remains

The installed New Workspace form created `stage15_ui_xorg_0910` using Fabric 1.21.1. The product opened the new
workspace, using `/opt/copperbench/jdk21`. A function `ui_saved_probe` was created through the UI and edited to add
`say Stage15 UI persistence 0910`; **Save Function** persisted the content and advanced the revision to 2.
The workspace was closed normally and its MCP descriptor disappeared. A separate installed launcher opened the
Recent Workspaces selector; double-clicking the new workspace entry reopened it. The UI showed one element,
revision 2 and the exact saved command. Saved/reopened `.mod.json` bytes match at SHA-256
`f423c587e3ba9d7082953ae9c73570fdc8ecdee19dbe9c9ac6234959b176d929`.

Evidence is in `evidence/stage15/2026-09-10/run33-ui-persistence/`, including the saved and reopened editor screenshots,
byte copies, result JSON and selector log excerpt. Text input used the guest's existing Xorg input utility after
VM-console clipboard typing proved unreliable; there were no direct file/API mutations for this UI-created workspace.

During the earlier selector session at 07:55:06 UTC, Ubuntu reported a separate native subprocess crash.
Apport identifies `/opt/copperbench/jdk/lib/jcef_helper`, utility subtype `unzip.mojom.Unzipper`, signal 6 / `SIGABRT`,
and `--change-stack-guard-on-fork=enable`. The captured product stderr includes `stack smashing detected`.
The main JVM PID 16289 remained alive and subsequently reopened the workspace at 09:16:15 UTC. The UI persistence
pass therefore does not clear the helper crash, and the crash must not be described as workspace data loss or a main-JVM exit.
The bounded Apport summary is `run33-ui-persistence/jcef-helper-crash-summary.json`; the original crash report remains
in the guest's `/var/crash/` and was not submitted externally.

External comparison: CEF [issue #3912](https://github.com/chromiumembedded/cef/issues/3912), opened 2025-03-31,
describes the same Unzipper/stack-check symptom after a few minutes. The maintainer's
[diagnosis](https://github.com/chromiumembedded/cef/issues/3912#issuecomment-2766859435) requires appropriate annotations
along the native process-entry call chain. This is a strong matching hypothesis, not a confirmed root cause for this
bundled JBR build. No speculative runtime flag, stack-protection change or installed binary replacement was applied.
Smart Search fetch retrieved the issue body; Exa was unconfigured and the configured primary search endpoint failed,
so discovery used the fallback web tool and the maintainer discussion was read from the public GitHub API.

## Validation

- Existing Linux installed-gate Node contracts: 7/7 passed.
- Desktop launcher Linux subprocess tests: 2/2 passed, including credential transport/redaction, helper success/failure
  propagation and refusal to overwrite prior evidence. These tests use a synthetic credential and fake Zenity/helper.
- Windows source regressions: seven JUnit tests passed across `Fabric1211ProjectileTemplateRegressionTest`,
  `DesktopMcpRuntimeTest` and `DesktopMcpAgentLoopTest`. Initial direct Gradle startup failed with
  `Unable to establish loopback connection`; the existing external-child script completed successfully when launched
  as a hidden external process. No product change was needed. Summary: `evidence/stage15/2026-09-10/local-regression.json`.
- Guest NeoForge Maven retry at 06:52:19 UTC still fails with `Connection reset by peer`; preserved in
  `evidence/stage15/2026-09-10/neoforge-network-probe.json`. No TLS bypass, repository substitution or false pass is used.

## Remaining gates

- Resolve and revalidate the independent JCEF Unzipper native crash against a frozen replacement candidate.
- Clean-guest NeoForge build/render once the official repository is reachable.
- Required final-candidate Wayland graphical observations and formal exact-binary release promotion.

No commit, push, CI change, platform promotion or release was performed.
