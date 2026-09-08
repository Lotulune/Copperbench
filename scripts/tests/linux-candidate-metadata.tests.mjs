import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const root = resolve(import.meta.dirname, '../..');
const generator = resolve(root, 'scripts/new-linux-candidate-metadata.mjs');
const verifier = resolve(root, 'scripts/verify-linux-candidate-metadata.mjs');
const commit = '1'.repeat(40);

function fixture() {
  const directory = mkdtempSync(resolve(tmpdir(), 'copperbench-linux-candidate-'));
  writeFileSync(resolve(directory, 'Copperbench 0.1.0 Linux x86_64.tar.gz'), 'portable');
  writeFileSync(resolve(directory, 'copperbench_0.1.0_amd64.deb'), 'deb');
  writeFileSync(resolve(directory, 'copperbench-linux.spdx.json'), '{"spdxVersion":"SPDX-2.3"}\n');
  writeFileSync(resolve(directory, 'linux-candidate-manifest.json'), JSON.stringify({
    schemaVersion: '1.0',
    kind: 'stage15-linux-candidate',
    status: 'development-not-certified',
    formalSupportClaim: false,
    productId: 'copperbench',
    productVersion: '0.1.0',
    platform: { os: 'linux', arch: 'x86_64', certificationBaseline: 'Ubuntu 24.04 LTS x86_64' }
  }));
  return directory;
}

function generate(directory, ...extra) {
  return spawnSync(process.execPath, [generator, '--export-dir', directory, '--source-commit', commit,
    '--source-ref', 'refs/heads/test', '--run-id', '42', '--run-attempt', '1', ...extra],
  { cwd: root, encoding: 'utf8' });
}

function verify(directory, ...extra) {
  return spawnSync(process.execPath, [verifier, '--export-dir', directory, '--expected-commit', commit, ...extra],
    { cwd: root, encoding: 'utf8' });
}

test('candidate metadata binds commit and all four Linux supply-chain assets', () => {
  const directory = fixture();
  const generated = generate(directory);
  assert.equal(generated.status, 0, generated.stderr);
  const metadata = JSON.parse(readFileSync(resolve(directory, 'LINUX-CANDIDATE-METADATA.json'), 'utf8'));
  assert.match(metadata.candidateId, /^sha256:[0-9a-f]{64}$/);
  assert.equal(metadata.source.commit, commit);
  assert.equal(metadata.status, 'development-not-certified');
  assert.equal(metadata.formalSupportClaim, false);
  assert.equal(metadata.exactBinaryPromotionEligible, false);
  assert.deepEqual(Object.keys(metadata.assets).sort(), ['deb', 'manifest', 'portable', 'sbom']);
  const checked = verify(directory);
  assert.equal(checked.status, 0, checked.stderr);
});

test('tampered candidate bytes are rejected after metadata is frozen', () => {
  const directory = fixture();
  assert.equal(generate(directory).status, 0);
  writeFileSync(resolve(directory, 'copperbench_0.1.0_amd64.deb'), 'tampered');
  const result = verify(directory);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /deb size mismatch|deb SHA-256 mismatch/);
});

test('generator refuses a manifest that prematurely claims formal Linux support', () => {
  const directory = fixture();
  const path = resolve(directory, 'linux-candidate-manifest.json');
  const manifest = JSON.parse(readFileSync(path, 'utf8'));
  manifest.status = 'supported';
  manifest.formalSupportClaim = true;
  writeFileSync(path, JSON.stringify(manifest));
  const result = generate(directory);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /must remain development-not-certified/);
});

test('candidate identity changes if an input artifact changes before freezing', () => {
  const first = fixture();
  assert.equal(generate(first).status, 0);
  const firstId = JSON.parse(readFileSync(resolve(first, 'LINUX-CANDIDATE-METADATA.json'), 'utf8')).candidateId;
  const second = fixture();
  writeFileSync(resolve(second, 'Copperbench 0.1.0 Linux x86_64.tar.gz'), 'portable-v2');
  assert.equal(generate(second).status, 0);
  const secondId = JSON.parse(readFileSync(resolve(second, 'LINUX-CANDIDATE-METADATA.json'), 'utf8')).candidateId;
  assert.notEqual(firstId, secondId);
});


test('Stage 15 workflow carries the Linux supply-chain contract', () => {
  const workflow = readFileSync(resolve(root, '.github/workflows/stage15-linux-candidate.yml'), 'utf8');
  const graphicalSmoke = readFileSync(resolve(root, 'scripts/verify-stage15-linux-x11-ci-smoke.sh'), 'utf8');
  const graphicalFixture = readFileSync(resolve(root, 'src/test/java/dev/copperbench/headless/Stage15GraphicalWorkspaceFixture.java'), 'utf8');
  assert.match(workflow, /attestations: write/);
  assert.match(workflow, /id-token: write/);
  assert.match(workflow, /Generate SPDX SBOM for Linux candidate/);
  assert.match(workflow, /new-linux-candidate-metadata\.mjs/);
  assert.match(workflow, /verify-linux-candidate-metadata\.mjs/);
  assert.match(workflow, /Attest Linux candidate provenance/);
  assert.match(workflow, /actions\/attest-build-provenance@v3/);
  assert.match(workflow, /scripts\/\*linux-candidate-metadata\.mjs/);
  assert.match(workflow, /LINUX-CANDIDATE-METADATA\.json/);
  assert.match(workflow, /copperbench-linux\.spdx\.json/);
  assert.match(workflow, /manifest\.status !== "development-not-certified"/);
  assert.match(workflow, /manifest\.formalSupportClaim !== false/);
  assert.doesNotMatch(workflow, /grep -q '\"status\" :/);
  assert.match(workflow, /cmp "\$portable_manifest" build\/stage15-deb-smoke\/opt\/copperbench\/linux-candidate-manifest\.json/);
  assert.match(workflow, /Launch packaged JCEF product shell under Xvfb/);
  assert.match(workflow, /timeout-minutes: 8/);
  assert.match(workflow, /Upload X11 graphical smoke diagnostics on failure/);
  assert.match(workflow, /stage15-x11-smoke-diagnostics/);
  assert.match(graphicalSmoke, /Stage15GraphicalWorkspaceFixture\.java/);
  assert.match(graphicalSmoke, /"\$portable_root\/jdk\/bin\/javac"/);
  assert.match(graphicalSmoke, /-d "\$fixture_classes"/);
  assert.match(graphicalSmoke, /timeout 120s "\$portable_root\/jdk\/bin\/java"/);
  assert.match(graphicalSmoke, /dev\.copperbench\.headless\.Stage15GraphicalWorkspaceFixture/);
  assert.match(graphicalSmoke, /cd "\$portable_root"/);
  assert.doesNotMatch(graphicalSmoke, /xdotool key/);
  assert.doesNotMatch(graphicalSmoke, /xdotool windowfocus/);
  assert.match(graphicalFixture, /request -> true/);
  assert.match(graphicalFixture, /FileDescriptor\.out/);
  assert.doesNotMatch(graphicalFixture, /System\.out\.println/);
  assert.match(graphicalFixture, /ChinaMirrorService\.rememberChoice\(false\)/);
  assert.match(workflow, /BootstrapProductLauncherTest\.localApprovalCreatesResourcePackWorkspaceForGraphicalFixture/);
  assert.match(workflow, /BootstrapProductLauncherTest\.decliningLocalApprovalLeavesTheTargetUntouched/);
  assert.match(graphicalSmoke, /Timed out waiting for the packaged JCEF graphical probe/);
  assert.match(graphicalSmoke, /dump_bootstrap_failure/);
  assert.match(graphicalSmoke, /dump_product_failure/);
});
