import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const gate = fs.readFileSync(new URL('../verify-stage15-linux-installed-guest.sh', import.meta.url), 'utf8');
const verifier = fs.readFileSync(new URL('../stage15/Stage15GraphicalProbeVerifier.java', import.meta.url), 'utf8');

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
  assert.match(gate, /development-not-certified/);
  assert.match(gate, /formalSupportClaim/);
});

test('installed Linux guest gate uses installed Copperbench Core for build and runClient', () => {
  assert.match(gate, /\/usr\/bin\/copperbench headless --workspace \"\$workspace_file\" build/);
  assert.match(gate, /\/usr\/bin\/copperbench headless --workspace \"\$workspace_file\" run-client/);
  assert.doesNotMatch(gate, /gradlew runClient/);
  assert.match(gate, /Backend library: LWJGL version/);
  assert.match(gate, /Reloading ResourceManager:/);
  assert.match(gate, /minecraft:textures\/atlas\/blocks\.png-atlas/);
});

test('installed Linux guest gate never turns automated preflight into formal certification', () => {
  assert.match(gate, /automated-preflight-passed-manual-gates-pending/);
  assert.match(gate, /confirm-user-visible-copperbench-jcef-window/);
  assert.match(gate, /confirm-user-visible-minecraft-window/);
  assert.match(gate, /confirm-interactive-runclient-remains-running-until-user-closes-minecraft/);
  assert.match(gate, /authorize-desktop-mcp-via-ui-and-run-independent-external-agent-loop/);
  assert.match(gate, /close-installed-product-and-confirm-descriptor-removed-old-connection-rejected/);
  assert.match(gate, /exercise-installed-blockbench-open-edit-close/);
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
