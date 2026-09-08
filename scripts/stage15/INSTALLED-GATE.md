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
direct mutation, Workspace Plan preview/apply, real build completion, revision-conflict reread/retry, final readback,
audit redaction, descriptor cleanup, and old-connection rejection. When it prints the shutdown prompt, close the
Copperbench workspace window normally. Do not terminate it from another shell for this lifecycle check.

## Manual evidence still required

Record evidence that a real user can create/save/reopen through the installed UI, see the Copperbench window, see
the Minecraft window for Fabric and NeoForge, keep interactive Run Client alive until Minecraft is closed by the
user, and complete a real installed Blockbench open/edit/close cycle. Repeat the desktop/window checks under GNOME
Wayland and GNOME on Xorg. Do not change `formalSupportClaim` until all Stage 15 release gates are closed.
