# Stage 15 clean GNOME installed gate

This bundle is a maintainer verification harness. Passing it does not by itself claim formal Linux support.

## Preconditions

- Fresh Ubuntu 24.04 x86_64 GNOME guest.
- Run the Wayland session first; repeat the final desktop checks under GNOME on Xorg.
- The guest must not have system `java`, `gradle`, or `git` installed before the candidate is installed.
- Transfer the exact Stage 15 `.deb`, its SHA-256 from `linux-candidate-sha256.txt`, and this harness bundle by browser or shared folder.
- Use a disposable copy of the workspace. The external-Agent helper intentionally creates elements and runs real builds.

## Automated installed-product preflight

Extract this archive and run one loader at a time:

```bash
./verify-stage15-linux-installed-guest.sh \
  /path/to/copperbench_<version>_amd64.deb \
  <exact-deb-sha256> \
  /path/to/disposable-workspace/workspace.mcreator \
  fabric-1.21.1 \
  ./evidence-fabric
```

Use `neoforge-1.21.1` and a NeoForge workspace for the second loader. The script verifies the exact candidate,
Ubuntu/GNOME session facts, absence of host Java/Gradle/Git, installed JBR/JCEF and Java 21, the graphical probe,
private Desktop MCP metadata, installed-Core build, and real Minecraft render readiness. Its result deliberately
remains `automated-preflight-passed-manual-gates-pending`.

## Visible UI and external-Agent gate

Open the disposable workspace through the installed product and confirm the Copperbench JCEF window is actually
visible. In the product open **AI 与 MCP**, confirm the permission shown is `workspace`, then click **显示一次令牌**.
Do not paste the token into chat, a log file, or a shell command.

With the Copperbench window still open, start the independent helper:

```bash
python3 ./verify-stage15-linux-installed-agent.py \
  /path/to/disposable-workspace/workspace.mcreator \
  --candidate-sha256 <exact-deb-sha256> \
  --output ./evidence-agent/external-agent-result.json
```

Paste the one-time token only into the hidden prompt. The helper verifies MCP initialization, read/list pagination,
direct mutation, Workspace Plan preview/apply, real build completion, revision-conflict reread/retry and final readback.
It then starts `run_client` through that same installed Desktop MCP runtime, waits for real LWJGL/resource/atlas render
markers and proves the task remains `running` for ten more seconds. Confirm the Minecraft window is visibly usable,
then close Minecraft normally when prompted. The helper requires the task to reach `succeeded` after that user close.
It also verifies audit redaction, descriptor cleanup, and old-connection rejection. When it later prints the Copperbench
shutdown prompt, close the Copperbench workspace window normally. Do not terminate either product from another shell.

## Manual evidence still required

Record evidence that a real user can create/save/reopen through the installed UI, see the Copperbench window, see
the helper-launched Minecraft window while the machine-checked Run Client task is still running, and complete the real
installed Blockbench gate below. Repeat the desktop/window checks under GNOME Wayland and GNOME on Xorg. Do not change
`formalSupportClaim` until all Stage 15 release gates are closed.

## Real installed Blockbench gate

Install a real Linux Blockbench build only **after** the pre-install clean-tooling evidence has been captured. Close
all pre-existing Blockbench windows, keep using the disposable workspace, then run the verifier with Copperbench's
bundled JDK (not a system Java):

```bash
/opt/copperbench/jdk/bin/java \
  -cp "/opt/copperbench/lib/copperbench.jar:/opt/copperbench/lib/*" \
  ./stage15/Stage15InstalledBlockbenchVerifier.java \
  /path/to/disposable-workspace \
  ./evidence-blockbench/installed-blockbench-result.json
```

The verifier uses Copperbench's production Linux executable locator and managed Blockbench process service. It creates
a unique probe `.bbmodel`, launches the real Blockbench executable, proves a second service cannot acquire the same
asset lease, and waits for the managed process to remain alive. In the Blockbench window, make one visible model edit
(for example add or rename an element), save, and close Blockbench normally. The verifier then requires exit code 0,
a changed asset SHA-256 and Copperbench's `ASSET_CHANGED_EXTERNALLY` result before writing `status=passed`. Do not edit
the probe from a text editor during this gate; that would test filesystem change detection without proving Blockbench.

This helper proves the installed production locator/process/lease/change path, but it does not synthesize a JCEF click.
Separately, in the installed Copperbench **Asset Center**, open one disposable `.bbmodel` with the Blockbench action and
confirm the same real Blockbench installation opens it. Keep that visible UI observation with the clean-GNOME evidence.
