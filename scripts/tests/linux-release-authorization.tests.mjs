import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { dirname, resolve, join } from 'node:path';
import { tmpdir } from 'node:os';
import { validateAuthorization, validateHeader, validateSourceDelta, planDraftUploads, releaseAssetName } from '../verify-linux-release-authorization.mjs';

const repository = resolve(import.meta.dirname, '../..');
const base = 'evidence/stage15/2026-09-10/run34-candidate/';
const sha = (data) => createHash('sha256').update(data).digest('hex');
const read = (path) => JSON.parse(readFileSync(path, 'utf8'));
const tag = 'v0.1.0-linux-preview.1';

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'copperbench-linux-release-test-'));
  t.after(() => {
    assert.ok(root.startsWith(join(tmpdir(), 'copperbench-linux-release-test-')));
    rmSync(root, { recursive: true, force: true });
  });
  const metadata = read(resolve(repository, base, 'LINUX-CANDIDATE-METADATA.json'));
  const index = read(resolve(repository, base, 'accepted-evidence-index.json'));
  const entries = structuredClone(index.acceptedEvidence);
  for (const entry of Object.values(entries)) {
    const target = resolve(root, entry.path);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, readFileSync(resolve(repository, entry.path)));
  }
  const report = 'docs/testing/stage15-test-acceptance.md';
  mkdirSync(dirname(resolve(root, report)), { recursive: true });
  writeFileSync(resolve(root, report), 'Test-only accepted candidate report\n');
  const auth = {
    schemaVersion: '1.0', kind: 'linux-release-authorization', status: 'approved', releaseEligible: true,
    formalSupportClaim: false, releaseTag: tag, candidateId: metadata.candidateId,
    candidateSourceCommit: metadata.source.commit, candidateWorkflowRunId: metadata.source.workflowRunId,
    debSha256: metadata.assets.deb.sha256, portableSha256: metadata.assets.portable.sha256,
    installedProductJarSha256: read(resolve(repository, base, 'final-checks.json')).installedAndPortableProductJarSha256,
    validationReportPath: report, validationReportSha256: sha(readFileSync(resolve(root, report))), acceptedEvidence: entries
  };
  entries.managedBlockbench.installedProductJarSha256 = auth.installedProductJarSha256;
  // Synthetic unit-test evidence for the additional migration contract, not an installed acceptance record.
  const migrationPath = 'evidence/stage15/test/legacy-preferences.json';
  const passedMigration = { status: 'passed', legacyValueLoaded: true, legacySourcePreserved: true, newWritesUseXdg: true };
  mkdirSync(dirname(resolve(root, migrationPath)), { recursive: true });
  writeFileSync(resolve(root, migrationPath), JSON.stringify({ status: 'passed', candidateSha256: auth.debSha256,
    installedProductJarSha256: auth.installedProductJarSha256, classpathOverrideUsed: false,
    modern: passedMigration, old: passedMigration }));
  auth.legacyPreferencesMigration = { path: migrationPath, sha256: sha(readFileSync(resolve(root, migrationPath))) };
  const check = () => validateAuthorization(auth, metadata, root, tag);
  const mutate = (name, update) => {
    const entry = entries[name]; const path = resolve(root, entry.path); const data = read(path);
    update(data); const bytes = JSON.stringify(data); writeFileSync(path, bytes); entry.sha256 = sha(bytes);
  };
  return { root, auth, metadata, check, mutate };
}

test('ten bound gates permit a separately authorized Linux release without rewriting candidate metadata', (t) => {
  const { metadata, check } = fixture(t);
  assert.equal(check().gatesVerified, 10);
  assert.equal(metadata.formalSupportClaim, false);
  assert.equal(metadata.exactBinaryPromotionEligible, false);
});

test('pending authorization and a Windows tag never reach release eligibility', (t) => {
  const { auth } = fixture(t);
  assert.throws(() => validateHeader(auth, 'v0.1.0-preview.9'), /dedicated Linux/);
  auth.status = 'pending-validation';
  assert.throws(() => validateHeader(auth, tag), /not approved/);
});

test('legacy preferences evidence cannot be omitted or replaced by a diagnostic override', (t) => {
  const { root, auth, check } = fixture(t);
  const entry = auth.legacyPreferencesMigration;
  delete auth.legacyPreferencesMigration;
  assert.throws(check, /legacy-preferences evidence required/);
  auth.legacyPreferencesMigration = entry;
  const path = resolve(root, entry.path);
  const data = read(path); data.classpathOverrideUsed = true;
  writeFileSync(path, JSON.stringify(data)); entry.sha256 = sha(readFileSync(path));
  assert.throws(check, /installed candidate binding failed/);
});

test('candidate source or package substitution is rejected', (t) => {
  const { auth, metadata, check } = fixture(t);
  auth.candidateSourceCommit = 'a'.repeat(40);
  assert.throws(check, /identity\/source\/run mismatch/);
  auth.candidateSourceCommit = metadata.source.commit;
  auth.debSha256 = 'b'.repeat(64);
  assert.throws(check, /Package digest mismatch/);
});

test('a changed report or changed evidence file invalidates authorization', (t) => {
  const { root, auth, check } = fixture(t);
  writeFileSync(resolve(root, auth.validationReportPath), 'changed');
  assert.throws(check, /digest mismatch/);
});

test('evidence traversal and duplicate gate reuse are rejected', (t) => {
  const { auth, check } = fixture(t);
  auth.acceptedEvidence.xorgFabric.path = 'evidence/stage15/../../outside.json';
  assert.throws(check, /Unsafe evidence/);
  auth.acceptedEvidence.xorgFabric = auth.acceptedEvidence.xorgNeoForge;
  assert.throws(check, /wrong loader|reused/);
});

test('a passed Xorg preflight cannot certify Wayland', (t) => {
  const { mutate, check } = fixture(t);
  mutate('waylandFabric', (data) => { data.sessionType = 'x11'; });
  assert.throws(check, /wrong session/);
});

test('an Agent result without game normal close or descriptor removal cannot pass', (t) => {
  const { mutate, check } = fixture(t);
  mutate('waylandAgent', (data) => { data.shutdown.descriptorRemoved = false; });
  assert.throws(check, /product shutdown failed/);
});

test('a diagnostic classpath override is not installed Asset Center evidence', (t) => {
  const { mutate, check } = fixture(t);
  mutate('assetCenter', (data) => { data.classpathOverrideUsed = true; });
  assert.throws(check, /Asset Center/);
});

test('Blockbench evidence requires the installed product JAR binding', (t) => {
  const { auth, check } = fixture(t);
  delete auth.acceptedEvidence.managedBlockbench.installedProductJarSha256;
  assert.throws(check, /runtime binding/);
});

test('a release-only delta cannot conceal product or packaging changes', () => {
  assert.doesNotThrow(() => validateSourceDelta(['docs/testing/acceptance.md', '.github/workflows/linux-release-control.yml']));
  assert.throws(() => validateSourceDelta(['src/main/java/Changed.java']), /Build-affecting/);
  assert.throws(() => validateSourceDelta(['platform/linux/export.gradle']), /Build-affecting/);
  assert.doesNotThrow(() => validateSourceDelta(['src/test/java/dev/copperbench/release/ReleaseManifestTest.java']));
  assert.throws(() => validateSourceDelta(['src/test/java/dev/copperbench/release/UnreviewedTest.java']), /Build-affecting/);
});

test('a partial draft resumes only missing assets without clobbering existing files', () => {
  const files = [{ name: 'candidate.deb', size: 12 }, { name: 'portable.tar.gz', size: 20 }];
  assert.deepEqual(planDraftUploads({ isDraft: true, assets: [{ name: 'candidate.deb', size: 12 }] }, files), ['portable.tar.gz']);
  assert.throws(() => planDraftUploads({ isDraft: false, assets: [] }, files), /public release/);
  assert.throws(() => planDraftUploads({ isDraft: true, assets: [{ name: 'candidate.deb', size: 11 }] }, files), /size mismatch/);
  assert.throws(() => planDraftUploads({ isDraft: true, assets: [{ name: 'unexpected', size: 12 }] }, files), /Unexpected/);
});

test('Linux publication keeps production review and excludes Linux tags from Windows routing', () => {
  const workflow = readFileSync(resolve(repository, '.github/workflows/linux-release-control.yml'), 'utf8');
  const windows = readFileSync(resolve(repository, '.github/workflows/deploy.yml'), 'utf8');
  assert.match(workflow, /environment: production/);
  assert.match(workflow, /default: false/);
  assert.match(workflow, /--source-digest "\$CANDIDATE_COMMIT"/);
  assert.ok(workflow.indexOf('cmp "$file"') < workflow.indexOf('--draft=false'));
  assert.doesNotMatch(workflow, /--clobber/);
  assert.match(windows, /"!v\*-linux-\*"/);
});

test('GitHub-normalized portable names resume without reupload and retain the original upload path', () => {
  const original = 'Copperbench 0.1.0 Linux x86_64.tar.gz';
  const published = 'Copperbench.0.1.0.Linux.x86_64.tar.gz';
  assert.equal(releaseAssetName(original), published);
  assert.equal(releaseAssetName('linux-candidate-manifest.json'), 'linux-candidate-manifest.json');
  const files = [{ name: original, size: 968204286 }];
  assert.deepEqual(planDraftUploads({ isDraft: true, assets: [] }, files), [original]);
  assert.deepEqual(planDraftUploads({ isDraft: true, assets: [{ name: published, size: 968204286 }] }, files), []);
  assert.throws(() => planDraftUploads({ isDraft: true, assets: [{ name: published, size: 1 }] }, files), /size mismatch/);
});

test('ambiguous normalized names and unsupported upload characters are rejected', () => {
  assert.throws(() => planDraftUploads({ isDraft: true, assets: [] },
    [{ name: 'a b.tar.gz', size: 1 }, { name: 'a.b.tar.gz', size: 1 }]), /collide/);
  for (const name of ['../asset', 'name\nasset', '.hidden', 'trailing ', 'archive#label'])
    assert.throws(() => releaseAssetName(name), /Unsupported/);
});
