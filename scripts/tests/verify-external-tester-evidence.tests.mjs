import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const verifier = resolve(root, 'scripts/verify-external-tester-evidence.mjs');
const source = {
  version: '0.1.0',
  commit: '1'.repeat(40),
  installerSha256: '2'.repeat(64),
  packageType: 'exe'
};
const tasks = Object.fromEntries([
  'downloaded', 'hashVerified', 'installed', 'workspaceCreatedOrImported', 'elementCreated',
  'buildCompleted', 'failureInduced', 'diagnosticInspected', 'recoveryPointCreated',
  'recoveryRestored', 'uninstalled', 'workspaceRetainedAfterUninstall'
].map((name) => [name, 'passed']));

function record(index) {
  return {
    schemaVersion: '1.0',
    testerId: `tester-${index.toString(16).padStart(8, '0')}`,
    nonCoreDeveloper: true,
    testedAt: '2026-08-29T01:00:00Z',
    source,
    environment: {
      windowsVersion: 'Windows 11',
      windowsBuild: '26100',
      architecture: 'x64',
      preinstalledDeveloperTools: []
    },
    tasks,
    issues: [],
    privacyConfirmed: true,
    result: 'passed'
  };
}

function evidenceDir(records) {
  const directory = mkdtempSync(resolve(tmpdir(), 'copperbench-external-testers-'));
  records.forEach((value, index) => writeFileSync(resolve(directory, `tester-${index}.json`), JSON.stringify(value)));
  return directory;
}

function verify(records, ...extraArgs) {
  return spawnSync(process.execPath, [verifier, '--evidence-dir', evidenceDir(records), ...extraArgs], {
    cwd: root,
    encoding: 'utf8'
  });
}

test('five anonymous records for one candidate close the verifier', () => {
  const result = verify([1, 2, 3, 4, 5].map(record), '--require-complete',
    '--expected-commit', source.commit, '--expected-installer-sha256', source.installerSha256);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /5\/5 valid record\(s\), complete=true/);
});

test('complete mode cannot run without an explicitly selected candidate', () => {
  const result = verify([1, 2, 3, 4, 5].map(record), '--require-complete');
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /requires --expected-commit and --expected-installer-sha256/);
});

test('records for another installer cannot close the candidate gate', () => {
  const result = verify([1, 2, 3, 4, 5].map(record), '--require-complete',
    '--expected-commit', source.commit, '--expected-installer-sha256', '3'.repeat(64));
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /installer SHA-256 is not the requested candidate/);
});

test('unknown fields cannot bypass the evidence schema', () => {
  const invalid = { ...record(1), userName: 'hidden-user' };
  const result = verify([invalid]);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /record fields must be exactly/);
});

test('P0 and P1 issues keep the evidence gate closed', () => {
  const invalid = { ...record(1), issues: [{ severity: 'P1', area: 'generator', summary: 'Build fails' }] };
  const result = verify([invalid]);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /P0\/P1 issue remains open/);
});

test('personal paths and credentials are rejected', () => {
  const invalid = { ...record(1), issues: [{ severity: 'P2', area: 'ui', summary: 'C:\\Users\\alice\\trace.log' }] };
  const result = verify([invalid]);
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /possible personal data or credential found/);
});
