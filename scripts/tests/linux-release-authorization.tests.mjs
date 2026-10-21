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
  validateSourceDelta(['src/test/java/dev/copperbench/assets/BlockbenchProcessServiceTest.java']);
  assert.doesNotThrow(() => validateSourceDelta(['docs/testing/acceptance.md', '.github/workflows/linux-release-control.yml']));
  assert.throws(() => validateSourceDelta(['src/main/java/Changed.java']), /Build-affecting/);
  assert.throws(() => validateSourceDelta(['platform/linux/export.gradle']), /Build-affecting/);
  assert.doesNotThrow(() => validateSourceDelta(['src/test/java/dev/copperbench/release/ReleaseManifestTest.java']));
  assert.doesNotThrow(() => validateSourceDelta(['release-control/linux-platform-support.json']));
  assert.throws(() => validateSourceDelta(['release-control/unreviewed-packaging.json']), /Build-affecting/);
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

test('Linux publication requires notes for the selected product version', () => {
  const workflow = readFileSync(resolve(repository, '.github/workflows/linux-release-control.yml'), 'utf8');
  assert.ok(workflow.includes('notes_file="docs/releases/${RELEASE_TAG%%-linux-*}-linux.md"'));
  assert.ok(workflow.includes('test -f "$notes_file"'));
  assert.ok(workflow.includes('--notes-file "$notes_file"'));
  assert.doesNotMatch(workflow, /--notes-file docs\/releases\/linux-release-notes\.md/);
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

test('stable Linux tags retain exact binary and installed acceptance gates', (t) => {
  const { auth, metadata, root } = fixture(t);
  const stableTag = 'v0.1.0-linux-stable';
  auth.releaseTag = stableTag;
  assert.equal(validateAuthorization(auth, metadata, root, stableTag).gatesVerified, 10);
  auth.debSha256 = '0'.repeat(64);
  assert.throws(() => validateAuthorization(auth, metadata, root, stableTag), /Package digest mismatch/);
  assert.throws(() => validateHeader(auth, 'v0.1.0-linux-unknown'), /dedicated Linux/);
});

function maintenanceFixture(t, authorizationPath = 'evidence/maintenance/2026-09-30/linux-013/maintenance-authorization.json') {
  const { root } = fixture(t);
  const auth = read(resolve(repository, authorizationPath));
  for (const entry of [auth.candidateCi.run, auth.candidateCi.jobs,
    { path: auth.validationReportPath, sha256: auth.validationReportSha256 }]) {
    const target = resolve(root, entry.path);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, readFileSync(resolve(repository, entry.path)));
  }
  // Unit-only metadata; production checks the real artifact and its provenance remotely.
  const metadata = { kind: 'stage15-linux-immutable-candidate', status: 'development-not-certified',
    formalSupportClaim: false, exactBinaryPromotionEligible: false, candidateId: auth.candidateId,
    source: { commit: auth.candidateSourceCommit, workflowRunId: auth.candidateWorkflowRunId },
    assets: { deb: { sha256: auth.debSha256 }, portable: { sha256: auth.portableSha256 } } };
  const check = () => validateAuthorization(auth, metadata, root, auth.releaseTag);
  const mutate = (kind, update) => {
    const entry = auth.candidateCi[kind], path = resolve(root, entry.path), data = read(path);
    update(data); writeFileSync(path, JSON.stringify(data)); entry.sha256 = sha(readFileSync(path));
  };
  return { auth, root, check, mutate };
}

test('owner-approved 0.1.3 maintenance uses bound candidate CI without claiming installed acceptance', (t) => {
  const { check } = maintenanceFixture(t);
  assert.equal(check().ciStepsVerified, 12);
  assert.equal(check().fullInstalledAcceptance, 'not-repeated');
});

test('maintenance approval cannot be reused for another release, source or run', (t) => {
  const { auth, check } = maintenanceFixture(t);
  for (const [key, invalid] of [['releaseTag', 'v0.1.4-linux-stable'], ['candidateSourceCommit', 'a'.repeat(40)],
    ['candidateWorkflowRunId', '123'], ['acceptancePolicy', 'skip-all']]) {
    const original = auth[key]; auth[key] = invalid;
    assert.throws(check, /limited to the approved/); auth[key] = original;
  }
  auth.maintenanceApproval.approved = false;
  assert.throws(check, /explicit approval/);
});

test('maintenance CI rejects failed or foreign runs and skipped smoke steps', (t) => {
  const { check, mutate } = maintenanceFixture(t);
  mutate('run', run => { run.conclusion = 'failure'; });
  assert.throws(check, /CI identity or result mismatch/);
  mutate('run', run => { run.conclusion = 'success'; run.repository.full_name = 'other/repository'; });
  assert.throws(check, /CI identity or result mismatch/);
  mutate('run', run => { run.repository.full_name = 'Lotulune/Copperbench'; });
  mutate('jobs', receipt => { receipt.jobs[0].steps.find(step => step.name === 'Launch packaged JCEF product shell under Xvfb').conclusion = 'skipped'; });
  assert.throws(check, /step did not pass/);
});

test('maintenance rejects changed receipts, substituted packages and historical installed evidence', (t) => {
  const { auth, root, check } = maintenanceFixture(t);
  auth.debSha256 = '0'.repeat(64);
  assert.throws(check, /Package digest mismatch/);
  auth.debSha256 = read(resolve(repository, 'evidence/maintenance/2026-09-30/linux-013/maintenance-authorization.json')).debSha256;
  auth.acceptedEvidence = {};
  assert.throws(check, /must not reuse historical/);
  delete auth.acceptedEvidence;
  writeFileSync(resolve(root, auth.candidateCi.run.path), '{}');
  assert.throws(check, /digest mismatch/);
});

test('owner-approved 0.1.4 uses its own bound CI without claiming fresh installed acceptance', (t) => {
  const { auth, check } = maintenanceFixture(t, 'evidence/maintenance/2026-10-06/linux-014/maintenance-authorization.json');
  assert.equal(auth.releaseTag, 'v0.1.4-linux-stable');
  assert.equal(check().ciStepsVerified, 12);
  assert.equal(check().fullInstalledAcceptance, 'not-repeated');
});

test('0.1.4 approval cannot authorize another candidate, release or installed-support claim', (t) => {
  const { auth, check } = maintenanceFixture(t, 'evidence/maintenance/2026-10-06/linux-014/maintenance-authorization.json');
  for (const [key, invalid] of [['releaseTag', 'v0.1.5-linux-stable'], ['candidateSourceCommit', 'a'.repeat(40)],
    ['candidateWorkflowRunId', '123'], ['acceptancePolicy', 'maintenance-ci-0.1.3']]) {
    const original = auth[key]; auth[key] = invalid;
    assert.throws(check, /limited to the approved/); auth[key] = original;
  }
  auth.formalSupportClaim = true;
  assert.throws(check, /honest installed-acceptance/);
  auth.formalSupportClaim = false;
  auth.maintenanceApproval.approved = false;
  assert.throws(check, /explicit approval/);
});

test('0.1.4 approval rejects missing runtime preflights and old installed results', (t) => {
  const { auth, check, mutate } = maintenanceFixture(t, 'evidence/maintenance/2026-10-06/linux-014/maintenance-authorization.json');
  auth.acceptedEvidence = {};
  assert.throws(check, /must not reuse historical/);
  delete auth.acceptedEvidence;
  mutate('jobs', receipt => {
    receipt.jobs[0].steps.find(step => step.name === 'Run packaged Fabric 1.21.1 X11 render preflight under Xvfb').conclusion = 'skipped';
  });
  assert.throws(check, /step did not pass/);
});

test('requested 0.1.5 publication requires its bound main CI and graphical installer check', (t) => {
  const { auth, check } = maintenanceFixture(t, 'evidence/maintenance/2026-10-14/linux-015/maintenance-authorization.json');
  assert.equal(auth.releaseTag, 'v0.1.5-linux-stable');
  assert.equal(check().ciStepsVerified, 13);
  assert.equal(check().fullInstalledAcceptance, 'not-repeated');
  assert.equal(auth.formalSupportClaim, false);
});

test('0.1.5 publication cannot authorize another candidate or claim installed certification', (t) => {
  const { auth, check } = maintenanceFixture(t, 'evidence/maintenance/2026-10-14/linux-015/maintenance-authorization.json');
  for (const [key, invalid] of [['releaseTag', 'v0.1.6-linux-stable'], ['candidateSourceCommit', 'a'.repeat(40)],
    ['candidateWorkflowRunId', '123'], ['acceptancePolicy', 'maintenance-ci-0.1.4']]) {
    const original = auth[key]; auth[key] = invalid;
    assert.throws(check, /limited to the approved/); auth[key] = original;
  }
  auth.formalSupportClaim = true;
  assert.throws(check, /honest installed-acceptance/);
  auth.formalSupportClaim = false;
  auth.maintenanceApproval.approved = false;
  assert.throws(check, /explicit approval/);
});

test('0.1.5 publication rejects a skipped graphical installer check or a non-main candidate', (t) => {
  const { check, mutate } = maintenanceFixture(t, 'evidence/maintenance/2026-10-14/linux-015/maintenance-authorization.json');
  mutate('run', run => { run.head_branch = 'unapproved-branch'; });
  assert.throws(check, /CI identity or result mismatch/);
  mutate('run', run => { run.head_branch = 'main'; });
  mutate('jobs', receipt => {
    receipt.jobs[0].steps.find(step => step.name === 'Verify graphical installer package and render its bilingual wizard').conclusion = 'skipped';
  });
  assert.throws(check, /step did not pass/);
});

function maintenanceUnitFixture(t, version = '0.1.6', commit = '9e3dfdeab1bc19c099e739bcda14940d9a6fee30', runId = '38748273030') {
  // Synthetic unit-only data. These temporary files are never production CI,
  // candidate metadata, approval or installed-acceptance evidence.
  const { root } = fixture(t);
  const releaseTag = `v${version}-linux-stable`;
  const reportPath = 'docs/testing/linux-016-unit-fixture.md';
  mkdirSync(dirname(resolve(root, reportPath)), { recursive: true });
  writeFileSync(resolve(root, reportPath), 'Synthetic unit fixture; not candidate acceptance evidence.\n');
  const stepNames = [
    'Run Linux platform and generator regressions', 'Run Linux candidate and installed-gate contract tests',
    'Build portable and deb candidates', 'Verify portable candidate contents and executable modes',
    'Launch packaged headless bootstrap without system Java Gradle or Git',
    'Launch packaged JCEF product shell under Xvfb',
    'Run packaged NeoForge 1.21.1 X11 render preflight under Xvfb',
    'Run packaged Fabric 1.21.1 X11 render preflight under Xvfb',
    'Verify deb candidate layout', 'Freeze Linux candidate metadata',
    'Attest Linux candidate provenance', 'Upload Linux candidate artifacts',
    'Verify graphical installer package and render its bilingual wizard'
  ];
  const writeBound = (path, data) => {
    const target = resolve(root, path);
    mkdirSync(dirname(target), { recursive: true });
    writeFileSync(target, JSON.stringify(data));
    return { path, sha256: sha(readFileSync(target)) };
  };
  const run = { id: runId, head_sha: commit, repository: { full_name: 'Lotulune/Copperbench' },
    path: '.github/workflows/stage15-linux-candidate.yml', head_branch: 'main', event: 'push',
    status: 'completed', conclusion: 'success' };
  const jobs = { jobs: [{ name: 'Ubuntu 24.04 package candidate smoke', run_id: runId, head_sha: commit,
    status: 'completed', conclusion: 'success',
    steps: stepNames.map(name => ({ name, status: 'completed', conclusion: 'success' })) }] };
  const auth = { schemaVersion: '1.0', kind: 'linux-release-authorization', status: 'approved',
    releaseEligible: true, releaseTag, formalSupportClaim: false, fullInstalledAcceptance: 'not-repeated',
    acceptancePolicy: `maintenance-ci-${version}`, candidateSourceCommit: commit, candidateWorkflowRunId: runId,
    candidateId: 'sha256:' + 'c'.repeat(64), debSha256: 'd'.repeat(64), portableSha256: 'e'.repeat(64),
    validationReportPath: reportPath, validationReportSha256: sha(readFileSync(resolve(root, reportPath))),
    maintenanceApproval: { approved: true, releaseTag, scope: 'Synthetic unit fixture only.' },
    candidateCi: {
      run: writeBound('evidence/maintenance/unit-fixtures/linux-016/run.json', run),
      jobs: writeBound('evidence/maintenance/unit-fixtures/linux-016/jobs.json', jobs)
    } };
  const metadata = { kind: 'stage15-linux-immutable-candidate', status: 'development-not-certified',
    formalSupportClaim: false, exactBinaryPromotionEligible: false, candidateId: auth.candidateId,
    source: { commit, workflowRunId: runId },
    assets: { deb: { sha256: auth.debSha256 }, portable: { sha256: auth.portableSha256 } } };
  const check = () => validateAuthorization(auth, metadata, root, auth.releaseTag);
  const mutate = (kind, update) => {
    const entry = auth.candidateCi[kind], path = resolve(root, entry.path), data = read(path);
    update(data); writeFileSync(path, JSON.stringify(data)); entry.sha256 = sha(readFileSync(path));
  };
  return { auth, check, mutate };
}

test('0.1.6 exact maintenance tuple requires thirteen CI steps without installed certification', (t) => {
  const { auth, check } = maintenanceUnitFixture(t);
  assert.equal(check().ciStepsVerified, 13);
  assert.equal(check().fullInstalledAcceptance, 'not-repeated');
  assert.equal(auth.formalSupportClaim, false);
});

test('0.1.6 cannot reuse a PR candidate, another tuple or installed-support claim', (t) => {
  const { auth, check } = maintenanceUnitFixture(t);
  for (const [key, invalid] of [['releaseTag', 'v0.1.7-linux-stable'],
    ['candidateSourceCommit', 'a'.repeat(40)], ['candidateWorkflowRunId', '38744646888'],
    ['acceptancePolicy', 'maintenance-ci-0.1.5']]) {
    const original = auth[key]; auth[key] = invalid;
    assert.throws(check, /limited to the approved/); auth[key] = original;
  }
  auth.formalSupportClaim = true;
  assert.throws(check, /honest installed-acceptance/);
  auth.formalSupportClaim = false;
  auth.fullInstalledAcceptance = 'passed';
  assert.throws(check, /honest installed-acceptance/);
  auth.fullInstalledAcceptance = 'not-repeated';
  auth.maintenanceApproval.approved = false;
  assert.throws(check, /explicit approval/);
});

test('0.1.6 rejects a non-push run and a skipped graphical installer check', (t) => {
  const { check, mutate } = maintenanceUnitFixture(t);
  mutate('run', run => { run.event = 'pull_request'; });
  assert.throws(check, /CI identity or result mismatch/);
  mutate('run', run => { run.event = 'push'; });
  mutate('jobs', receipt => {
    receipt.jobs[0].steps.find(step => step.name === 'Verify graphical installer package and render its bilingual wizard').conclusion = 'skipped';
  });
  assert.throws(check, /step did not pass/);
});

test('0.1.7 exact maintenance tuple requires all thirteen CI steps', (t) => {
  const { auth, check } = maintenanceUnitFixture(t, '0.1.7', 'db8d08336c274cc37239b48e837f0bf3727c5fdc', '38850069834');
  assert.equal(check().ciStepsVerified, 13);
  assert.equal(check().fullInstalledAcceptance, 'not-repeated');
  assert.equal(auth.formalSupportClaim, false);
});

test('0.1.7 rejects another source, PR candidate, release tag or installed certification', (t) => {
  const { auth, check } = maintenanceUnitFixture(t, '0.1.7', 'db8d08336c274cc37239b48e837f0bf3727c5fdc', '38850069834');
  for (const [key, invalid] of [['releaseTag', 'v0.1.8-linux-stable'],
    ['candidateSourceCommit', 'a'.repeat(40)], ['candidateWorkflowRunId', '38848335973'],
    ['acceptancePolicy', 'maintenance-ci-0.1.6']]) {
    const original = auth[key]; auth[key] = invalid;
    assert.throws(check, /limited to the approved/); auth[key] = original;
  }
  auth.formalSupportClaim = true;
  assert.throws(check, /honest installed-acceptance/);
  auth.formalSupportClaim = false;
  auth.maintenanceApproval.approved = false;
  assert.throws(check, /explicit approval/);
});

test('0.1.7 rejects a non-main run and a skipped graphical installer check', (t) => {
  const { check, mutate } = maintenanceUnitFixture(t, '0.1.7', 'db8d08336c274cc37239b48e837f0bf3727c5fdc', '38850069834');
  mutate('run', run => { run.head_branch = 'unapproved-branch'; });
  assert.throws(check, /CI identity or result mismatch/);
  mutate('run', run => { run.head_branch = 'main'; });
  mutate('jobs', receipt => {
    receipt.jobs[0].steps.find(step => step.name === 'Verify graphical installer package and render its bilingual wizard').conclusion = 'skipped';
  });
  assert.throws(check, /step did not pass/);
});

test('0.1.9 exact maintenance tuple requires all thirteen CI steps', (t) => {
  const { auth, check } = maintenanceUnitFixture(t, '0.1.9', '9e8fcda24e58d165cb2b767f3a0cb3092a7f527c', '38894781422');
  assert.equal(check().ciStepsVerified, 13);
  assert.equal(check().fullInstalledAcceptance, 'not-repeated');
  assert.equal(auth.formalSupportClaim, false);
});

test('0.1.9 rejects another source, PR candidate, release tag or installed certification', (t) => {
  const { auth, check } = maintenanceUnitFixture(t, '0.1.9', '9e8fcda24e58d165cb2b767f3a0cb3092a7f527c', '38894781422');
  for (const [key, invalid] of [['releaseTag', 'v0.1.8-linux-stable'],
    ['candidateSourceCommit', 'a'.repeat(40)], ['candidateWorkflowRunId', '38848335973'],
    ['acceptancePolicy', 'maintenance-ci-0.1.6']]) {
    const original = auth[key]; auth[key] = invalid;
    assert.throws(check, /limited to the approved/); auth[key] = original;
  }
  auth.formalSupportClaim = true;
  assert.throws(check, /honest installed-acceptance/);
  auth.formalSupportClaim = false;
  auth.maintenanceApproval.approved = false;
  assert.throws(check, /explicit approval/);
});

test('0.1.9 rejects a non-main run and a skipped graphical installer check', (t) => {
  const { check, mutate } = maintenanceUnitFixture(t, '0.1.9', '9e8fcda24e58d165cb2b767f3a0cb3092a7f527c', '38894781422');
  mutate('run', run => { run.head_branch = 'unapproved-branch'; });
  assert.throws(check, /CI identity or result mismatch/);
  mutate('run', run => { run.head_branch = 'main'; });
  mutate('jobs', receipt => {
    receipt.jobs[0].steps.find(step => step.name === 'Verify graphical installer package and render its bilingual wizard').conclusion = 'skipped';
  });
  assert.throws(check, /step did not pass/);
});

test('0.1.13 exact candidate requires thirteen successful CI steps and separate approval', (t) => {
  const { auth, check } = maintenanceUnitFixture(t, '0.1.13', '464b75a39c34252f61c7d859b8d267dfabba205c', '39264954357');
  assert.equal(check().ciStepsVerified, 13);
  assert.equal(check().fullInstalledAcceptance, 'not-repeated');
  auth.maintenanceApproval.approved = false;
  assert.throws(check, /explicit approval/);
});

test('0.1.13 rejects substituted source, run, tag, package digest and installed certification', (t) => {
  const { auth, check } = maintenanceUnitFixture(t, '0.1.13', '464b75a39c34252f61c7d859b8d267dfabba205c', '39264954357');
  for (const [key, invalid] of [['releaseTag', 'v0.1.14-linux-stable'],
    ['candidateSourceCommit', 'a'.repeat(40)], ['candidateWorkflowRunId', '39261567659'],
    ['acceptancePolicy', 'maintenance-ci-0.1.9'], ['portableSha256', 'f'.repeat(64)],
    ['formalSupportClaim', true], ['fullInstalledAcceptance', 'passed']]) {
    const original = auth[key]; auth[key] = invalid;
    assert.throws(check); auth[key] = original;
  }
});

test('0.1.13 rejects non-main or failed CI receipts and skipped installer checks', (t) => {
  const { check, mutate } = maintenanceUnitFixture(t, '0.1.13', '464b75a39c34252f61c7d859b8d267dfabba205c', '39264954357');
  for (const [key, value, restore] of [['head_branch', 'other', 'main'],
    ['event', 'pull_request', 'push'], ['conclusion', 'failure', 'success']]) {
    mutate('run', run => { run[key] = value; });
    assert.throws(check, /CI identity or result mismatch/);
    mutate('run', run => { run[key] = restore; });
  }
  mutate('jobs', receipt => {
    receipt.jobs[0].steps.find(step => step.name === 'Verify graphical installer package and render its bilingual wizard').conclusion = 'skipped';
  });
  assert.throws(check, /step did not pass/);
});

test('candidate consumer updates allow only named non-product harness files after freezing', () => {
  assert.doesNotThrow(() => validateSourceDelta(['README.zh-CN.md',
    '.github/workflows/linux-candidate-verification.yml',
    'scripts/cloud-acceptance/verify-candidate-package.py', 'scripts/cloud-acceptance/README.md',
    'scripts/tests/test_candidate_package.py']));
  for (const path of ['scripts/cloud-acceptance/run.py', '.github/workflows/stage15-linux-candidate.yml',
    'platform/linux/linux.gradle', 'scripts/prepare-client-trial.py']) {
    assert.throws(() => validateSourceDelta([path]), /Build-affecting/);
  }
});
