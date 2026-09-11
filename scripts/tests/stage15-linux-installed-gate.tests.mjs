import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const gate = fs.readFileSync(new URL('../verify-stage15-linux-installed-guest.sh', import.meta.url), 'utf8');
const agentGate = fs.readFileSync(new URL('../verify-stage15-linux-installed-agent.py', import.meta.url), 'utf8');
const verifier = fs.readFileSync(new URL('../stage15/Stage15GraphicalProbeVerifier.java', import.meta.url), 'utf8');
const blockbenchVerifier = fs.readFileSync(new URL('../stage15/Stage15InstalledBlockbenchVerifier.java', import.meta.url), 'utf8');
const instructions = fs.readFileSync(new URL('../stage15/INSTALLED-GATE.md', import.meta.url), 'utf8');

test('installed Linux guest gate binds exact candidate and Ubuntu GNOME session facts', () => {
  assert.match(gate, /sha256sum/);
  assert.match(gate, /ID:-.*ubuntu/);
  assert.match(gate, /VERSION_ID:-.*24\.04/);
  assert.match(gate, /XDG_CURRENT_DESKTOP/);
  assert.match(gate, /guest desktop is not GNOME/);
  assert.match(gate, /XDG_SESSION_TYPE/);
  assert.match(gate, /WAYLAND_DISPLAY/);
  assert.match(gate, /DISPLAY/);
  assert.match(gate, /for tool in java gradle git/);
  assert.match(gate, /clean guest unexpectedly has system Java, Gradle, or Git/);
  assert.match(gate, /preinstall-system-tooling\.txt/);
  assert.match(gate, /guest-environment\.txt/);
  assert.match(gate, /sudo apt-get install -y --reinstall/);
  assert.match(gate, /\/usr\/share\/applications\/copperbench\.desktop/);
  assert.match(gate, /Exec=\/usr\/bin\/copperbench %F/);
  assert.match(gate, /\/usr\/share\/icons\/hicolor\/256x256\/apps\/copperbench\.png/);
  assert.match(gate, /development-not-certified/);
  assert.match(gate, /formalSupportClaim/);
});

test('installed Blockbench verifier uses production discovery, managed process, lease and change detection', () => {
  assert.match(blockbenchVerifier, /BlockbenchExecutableLocator\.locate\(\)/);
  assert.match(blockbenchVerifier, /new BlockbenchProcessService/);
  assert.match(blockbenchVerifier, /BLOCKBENCH_ASSET_LEASED/);
  assert.match(blockbenchVerifier, /ASSET_CHANGED_EXTERNALLY/);
  assert.match(blockbenchVerifier, /ProcessHandle\.of/);
  assert.match(blockbenchVerifier, /save the \.bbmodel/);
  assert.match(blockbenchVerifier, /formalSupportClaim.*false/);
  assert.doesNotMatch(blockbenchVerifier, /ProcessBuilder/);
});

test('installed Linux guest gate uses installed Copperbench Core for build and runClient', () => {
  assert.match(gate, /\/usr\/bin\/copperbench headless --workspace \"\$workspace_file\" build/);
  assert.match(gate, /\/usr\/bin\/copperbench headless --workspace \"\$workspace_file\" run-client/);
  assert.doesNotMatch(gate, /gradlew runClient/);
  assert.match(gate, /Backend library: LWJGL version/);
  assert.match(gate, /Reloading ResourceManager:/);
  assert.match(gate, /minecraft:textures\/atlas\/blocks\.png-atlas/);
  assert.match(gate, /\[\[ "\$client_log" -nt "\$run_start_marker" \]\]/);
  assert.ok(gate.indexOf('touch "$run_start_marker"') < gate.indexOf('setsid /usr/bin/copperbench'));
});

test('installed Linux guest gate never turns automated preflight into formal certification', () => {
  assert.match(gate, /automated-preflight-passed-manual-gates-pending/);
  assert.match(gate, /confirm-user-visible-copperbench-jcef-window/);
  assert.match(gate, /confirm-user-visible-minecraft-window-during-agent-helper-runclient/);
  assert.match(gate, /copy-one-time-desktop-mcp-token-from-ui-and-run-bundled-agent-helper/);
  assert.match(gate, /normal-close-copperbench-after-agent-helper-prompt/);
  assert.match(gate, /run-bundled-blockbench-helper-edit-save-close/);
  assert.match(gate, /confirm-installed-asset-center-launches-real-blockbench/);
  assert.match(gate, /ubuntu2404GnomeVerified\":true/);
  assert.match(gate, /systemJavaGradleGitAbsentBeforeInstall\":true/);
  assert.match(gate, /formalSupportClaim\":false/);
});

test('graphical probe verifier supports both Stage15 desktop targets', () => {
  assert.match(verifier, /\[x11\|wayland\]/);
  assert.match(verifier, /stage15-primary-target/);
  assert.match(verifier, /stage15-compatibility-target/);
  assert.match(verifier, /Expected desktop session must be x11 or wayland/);
});

test('installed external-Agent helper preserves UI authorization and verifies the full MCP lifecycle', () => {
  assert.match(agentGate, /getpass\.getpass/);
  assert.match(agentGate, /read_workspace_connection/);
  assert.match(agentGate, /permissionProfile.*workspace/);
  assert.match(agentGate, /descriptor contains credential material/);
  assert.match(agentGate, /list_mod_elements\(limit=1\)/);
  assert.match(agentGate, /plan_workspace_changes/);
  assert.match(agentGate, /preview_workspace_plan/);
  assert.match(agentGate, /apply_workspace_plan/);
  assert.match(agentGate, /build_workspace/);
  assert.match(agentGate, /call_tool\("run_client"/);
  assert.match(agentGate, /Backend library: LWJGL version/);
  assert.match(agentGate, /minecraft:textures\/atlas\/blocks\.png-atlas/);
  assert.match(agentGate, /stableBeforeUserCloseSeconds/);
  assert.match(agentGate, /terminalStateAfterUserClose/);
  assert.doesNotMatch(agentGate, /cancel_task\(run_task_id/);
  assert.match(agentGate, /WORKSPACE_REVISION_CONFLICT/);
  assert.match(agentGate, /elementType="projectile"/);
  assert.match(agentGate, /automation-audit\.jsonl/);
  assert.match(agentGate, /descriptorRemoved/);
  assert.match(agentGate, /oldConnectionRejected/);
  assert.match(agentGate, /tokenPersisted.*False/);
  assert.doesNotMatch(agentGate, /parser\.add_argument\("--token"/);
});

test('installed gate bundle tells maintainers to use a disposable workspace and the real UI token control', () => {
  assert.match(instructions, /disposable copy of the workspace/);
  assert.match(instructions, /AI 与 MCP/);
  assert.match(instructions, /显示一次令牌/);
  assert.match(instructions, /Do not paste the token into chat/);
  assert.match(instructions, /GNOME\s+Wayland\s+and GNOME on Xorg/);
  assert.match(instructions, /Do not change\s+`formalSupportClaim`/);
  assert.match(instructions, /Asset Center/);
  assert.match(instructions, /close Minecraft normally/);
});
