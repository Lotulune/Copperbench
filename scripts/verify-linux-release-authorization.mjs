import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, realpathSync, appendFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { resolve, relative, isAbsolute, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';

export const gateNames = ['xorgFabric', 'xorgNeoForge', 'waylandFabric', 'waylandNeoForge',
  'xorgAgent', 'waylandAgent', 'managedBlockbench', 'assetCenter', 'uiPersistence', 'portableBuild'];
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex');
const requireValue = (condition, message) => assert.ok(condition, message);

export function validateHeader(auth, tag) {
  requireValue(/^v\d+\.\d+\.\d+-linux-(?:(?:preview|beta)\.\d+|stable)$/.test(tag), 'Expected a dedicated Linux release tag');
  requireValue(auth.schemaVersion === '1.0' && auth.kind === 'linux-release-authorization', 'Invalid authorization schema');
  requireValue(auth.status === 'approved' && auth.releaseEligible === true, 'Linux release authorization is not approved');
  requireValue(auth.releaseTag === tag, 'Authorization tag mismatch');
  requireValue(typeof auth.formalSupportClaim === 'boolean', 'Explicit support classification required');
  requireValue(/^[0-9a-f]{40}$/.test(auth.candidateSourceCommit), 'Invalid candidate source commit');
  requireValue(/^[1-9][0-9]{0,19}$/.test(auth.candidateWorkflowRunId), 'Invalid candidate workflow run');
  requireValue(/^sha256:[0-9a-f]{64}$/.test(auth.candidateId), 'Invalid candidate identity');
  for (const key of ['debSha256', 'portableSha256', 'validationReportSha256'])
    requireValue(/^[0-9a-f]{64}$/.test(auth[key]), `Invalid ${key}`);
  if (auth.acceptancePolicy !== undefined) {
    const approved013 = auth.acceptancePolicy === 'maintenance-ci-0.1.3'
      && tag === 'v0.1.3-linux-stable'
      && auth.candidateSourceCommit === '6fd7acfdb0049a875ff1a0f7971d8fa63fb63040'
      && auth.candidateWorkflowRunId === '36583819071';
    const approved014 = auth.acceptancePolicy === 'maintenance-ci-0.1.4'
      && tag === 'v0.1.4-linux-stable'
      && auth.candidateSourceCommit === 'aabd70bc3b0eb05f8ef0484209d87f2bc3f63c4f'
      && auth.candidateWorkflowRunId === '37391113672';
    requireValue(approved013 || approved014, 'Maintenance authorization is limited to the approved 0.1.3 and 0.1.4 candidates');
    requireValue(auth.formalSupportClaim === false && auth.fullInstalledAcceptance === 'not-repeated'
      && auth.maintenanceApproval?.approved === true && auth.maintenanceApproval?.releaseTag === tag,
      'Maintenance publication requires explicit approval and an honest installed-acceptance classification');
  }
}

export function validateSourceDelta(paths) {
  const exact = new Set(['README.md', 'PRD-NEXT.md', 'product-status.json',
    '.github/workflows/linux-release-control.yml', '.github/workflows/deploy.yml',
    'scripts/verify-linux-release-authorization.mjs', 'scripts/tests/linux-release-authorization.tests.mjs',
    'src/test/java/dev/copperbench/release/ReleaseManifestTest.java',
    'src/test/java/dev/copperbench/assets/BlockbenchProcessServiceTest.java',
    'scripts/verify-stage15-linux-installed-guest.sh', 'scripts/tests/stage15-linux-installed-gate.tests.mjs',
    'scripts/tests/test_stage15_client_log_freshness.py', 'release-control/linux-candidate-authorization.json',
    'release-control/linux-platform-support.json']);
  for (const path of paths) requireValue(exact.has(path) || /^(docs|evidence|scripts\/stage15)\//.test(path),
    `Build-affecting change after frozen candidate: ${path}`);
}

export function releaseAssetName(name) {
  requireValue(typeof name === 'string' && /^[A-Za-z0-9][A-Za-z0-9._ -]*$/.test(name)
    && !/[. ]$/.test(name), 'Unsupported release asset filename');
  // GitHub's observed release upload normalizes the candidate portable name's spaces to dots.
  return name.replaceAll(' ', '.');
}

export function planDraftUploads(release, files) {
  requireValue(release.isDraft === true, 'An existing public release cannot be changed');
  const expected = new Map(files.map((file) => [releaseAssetName(file.name), file.size]));
  requireValue(expected.size === files.length, 'Release asset names collide after normalization');
  const existing = new Set();
  for (const asset of release.assets ?? []) {
    requireValue(expected.has(asset.name) && !existing.has(asset.name), 'Unexpected or duplicate draft asset');
    requireValue(expected.get(asset.name) === asset.size, `Existing asset size mismatch: ${asset.name}`);
    existing.add(asset.name);
  }
  return files.filter((file) => !existing.has(releaseAssetName(file.name))).map((file) => file.name);
}

function boundFile(root, entry, prefix) {
  requireValue(typeof entry?.path === 'string' && entry.path.startsWith(prefix)
    && !entry.path.includes('\\') && !entry.path.split('/').includes('..') && !isAbsolute(entry.path), 'Unsafe evidence path');
  const file = realpathSync(resolve(root, entry.path));
  const rel = relative(realpathSync(root), file);
  requireValue(rel !== '..' && !rel.startsWith(`..${sep}`) && !isAbsolute(rel), 'Evidence symlink escapes repository');
  const bytes = readFileSync(file);
  requireValue(digest(bytes) === entry.sha256, `Evidence digest mismatch: ${entry.path}`);
  return bytes;
}

function validateMaintenanceCi(auth, root, tag) {
  const run = JSON.parse(boundFile(root, auth.candidateCi?.run, 'evidence/maintenance/'));
  const receipt = JSON.parse(boundFile(root, auth.candidateCi?.jobs, 'evidence/maintenance/'));
  requireValue(String(run.id) === auth.candidateWorkflowRunId && run.head_sha === auth.candidateSourceCommit
    && run.repository?.full_name === 'Lotulune/Copperbench'
    && run.path === '.github/workflows/stage15-linux-candidate.yml'
    && run.head_branch === 'main' && run.event === 'push'
    && run.status === 'completed' && run.conclusion === 'success', 'Maintenance candidate CI identity or result mismatch');
  const job = receipt.jobs?.find(value => value.name === 'Ubuntu 24.04 package candidate smoke');
  requireValue(job && String(job.run_id) === auth.candidateWorkflowRunId
    && job.head_sha === auth.candidateSourceCommit && job.status === 'completed' && job.conclusion === 'success',
    'Maintenance candidate job identity or result mismatch');
  const requiredSteps = [
    'Run Linux platform and generator regressions', 'Run Linux candidate and installed-gate contract tests',
    'Build portable and deb candidates', 'Verify portable candidate contents and executable modes',
    'Launch packaged headless bootstrap without system Java Gradle or Git',
    'Launch packaged JCEF product shell under Xvfb',
    'Run packaged NeoForge 1.21.1 X11 render preflight under Xvfb',
    'Run packaged Fabric 1.21.1 X11 render preflight under Xvfb',
    'Verify deb candidate layout', 'Freeze Linux candidate metadata',
    'Attest Linux candidate provenance', 'Upload Linux candidate artifacts'
  ];
  for (const name of requiredSteps) requireValue(job.steps?.some(step => step.name === name
    && step.status === 'completed' && step.conclusion === 'success'), `Maintenance candidate CI step did not pass: ${name}`);
  requireValue(auth.acceptedEvidence === undefined && auth.legacyPreferencesMigration === undefined,
    'Maintenance CI must not reuse historical installed evidence');
  return { candidateId: auth.candidateId, releaseTag: tag, acceptancePolicy: auth.acceptancePolicy,
    ciStepsVerified: requiredSteps.length, fullInstalledAcceptance: 'not-repeated' };
}

export function validateAuthorization(auth, metadata, root, tag) {
  validateHeader(auth, tag);
  requireValue(metadata.kind === 'stage15-linux-immutable-candidate'
    && metadata.status === 'development-not-certified' && metadata.formalSupportClaim === false
    && metadata.exactBinaryPromotionEligible === false, 'Original candidate metadata must remain unchanged');
  requireValue(auth.candidateId === metadata.candidateId && auth.candidateSourceCommit === metadata.source?.commit
    && auth.candidateWorkflowRunId === metadata.source?.workflowRunId, 'Frozen candidate identity/source/run mismatch');
  requireValue(auth.debSha256 === metadata.assets?.deb?.sha256
    && auth.portableSha256 === metadata.assets?.portable?.sha256, 'Package digest mismatch');
  boundFile(root, { path: auth.validationReportPath, sha256: auth.validationReportSha256 }, 'docs/testing/');
  if (auth.acceptancePolicy !== undefined) return validateMaintenanceCi(auth, root, tag);
  assert.deepEqual(Object.keys(auth.acceptedEvidence ?? {}).sort(), [...gateNames].sort(), 'Required gate set mismatch');
  const paths = new Set();
  const hashes = new Set();
  for (const name of gateNames) {
    const entry = auth.acceptedEvidence[name];
    requireValue(!paths.has(entry.path) && !hashes.has(entry.sha256), 'Evidence must not be reused for different gates');
    paths.add(entry.path); hashes.add(entry.sha256);
    const result = JSON.parse(boundFile(root, entry, 'evidence/stage15/'));
    if (name !== 'managedBlockbench' && name !== 'portableBuild')
      requireValue(result.candidateSha256 === auth.debSha256, `${name}: wrong installed candidate`);
    if (/^(xorg|wayland)(Fabric|NeoForge)$/.test(name)) {
      requireValue(result.status === 'automated-preflight-passed-manual-gates-pending', `${name}: preflight failed`);
      requireValue(result.sessionType === (name.startsWith('xorg') ? 'x11' : 'wayland'), `${name}: wrong session`);
      requireValue(result.generatorId === (name.endsWith('Fabric') ? 'fabric-1.21.1' : 'neoforge-1.21.1'), `${name}: wrong loader`);
      requireValue(result.ubuntu2404GnomeVerified === true && result.systemJavaGradleGitAbsentBeforeInstall === true,
        `${name}: clean guest facts missing`);
    } else if (name.endsWith('Agent')) {
      const loop = result.agentLoop;
      requireValue(result.status === 'passed' && result.tokenPersisted === false
        && result.automationAuditCredentialLeak === false, `${name}: Agent or credential gate failed`);
      requireValue(loop?.firstBuildState === 'succeeded' && loop.finalBuildState === 'succeeded'
        && loop.planPreviewWouldApply === true && loop.revisionConflictCode === 'WORKSPACE_REVISION_CONFLICT'
        && loop.conflictRetryCommitted === true, `${name}: incomplete Agent loop`);
      requireValue(loop.runClientLifecycle?.renderReady === true && loop.runClientLifecycle.stableBeforeUserCloseSeconds >= 10
        && loop.runClientLifecycle.terminalStateAfterUserClose === 'succeeded', `${name}: game lifecycle failed`);
      requireValue(result.shutdown?.descriptorRemoved === true && result.shutdown.oldConnectionRejected === true,
        `${name}: product shutdown failed`);
    } else if (name === 'managedBlockbench') {
      requireValue(result.status === 'passed' && result.exitCode === 0 && result.managedProcessStable === true
        && result.changeDetected === true && result.openedSha256 !== result.currentSha256
        && result.leaseConflictCode === 'BLOCKBENCH_ASSET_LEASED'
        && result.changeDiagnosticCode === 'ASSET_CHANGED_EXTERNALLY', 'Blockbench lifecycle failed');
      requireValue(entry.installedProductJarSha256 === auth.installedProductJarSha256
        && /^[0-9a-f]{64}$/.test(auth.installedProductJarSha256), 'Blockbench installed runtime binding missing');
    } else if (name === 'assetCenter') {
      requireValue(result.assetCenterOpenedRealBlockbench === true && result.installedProductUnmodified === true
        && result.classpathOverrideUsed === false, 'Real installed Asset Center observation missing');
    } else if (name === 'uiPersistence') {
      requireValue(result.status === 'passed' && result.createdThroughInstalledUi === true
        && result.savedThroughInstalledUi === true && result.closedNormally === true
        && result.reopenedThroughRecentWorkspaceUi === true && result.savedAndReopenedBytesEqual === true
        && result.descriptorRemovedAfterClose === true, 'UI persistence lifecycle incomplete');
    } else {
      requireValue(result.status === 'passed' && result.portableSha256 === auth.portableSha256 && result.exitCode === 0
        && result.headlessBuildStatus === 'succeeded' && result.provenanceVerified === true, 'Portable gate failed');
    }
  }
  const migrationEntry = auth.legacyPreferencesMigration;
  requireValue(migrationEntry && !paths.has(migrationEntry.path), 'Installed legacy-preferences evidence required');
  const migration = JSON.parse(boundFile(root, migrationEntry, 'evidence/stage15/'));
  requireValue(migration.status === 'passed' && migration.candidateSha256 === auth.debSha256
    && migration.installedProductJarSha256 === auth.installedProductJarSha256
    && migration.classpathOverrideUsed === false, 'Legacy-preferences installed candidate binding failed');
  for (const mode of ['modern', 'old']) requireValue(migration[mode]?.status === 'passed'
    && migration[mode].legacyValueLoaded === true && migration[mode].legacySourcePreserved === true
    && migration[mode].newWritesUseXdg === true, `Legacy-preferences ${mode} migration failed`);
  return { candidateId: auth.candidateId, releaseTag: tag, gatesVerified: gateNames.length };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const { values } = parseArgs({ options: Object.fromEntries(['source-delta', 'authorization', 'payload', 'tag',
      'github-env', 'draft-state', 'missing-list', 'asset-name'].map((key) => [key, { type: 'string' }])) });
    const value = (key) => {
      const result = values[key.slice(2)];
      requireValue(typeof result === 'string' && result.length > 0, `Missing required option: ${key}`);
      return result;
    };
    if (values['asset-name']) {
      console.log(releaseAssetName(value('--asset-name')));
    } else if (values['source-delta']) {
      validateSourceDelta(readFileSync(value('--source-delta'), 'utf8').split(/\r?\n/).filter(Boolean));
    } else if (values['draft-state']) {
      const files = readdirSync(value('--payload')).map((name) => {
        requireValue(!/[\r\n\0]/.test(name), 'Invalid payload filename');
        const stats = statSync(resolve(value('--payload'), name));
        requireValue(stats.isFile(), 'Payload must contain regular files only');
        return { name, size: stats.size };
      });
      const missing = planDraftUploads(JSON.parse(readFileSync(value('--draft-state'), 'utf8')), files);
      writeFileSync(value('--missing-list'), missing.map((name) => name + '\n').join(''));
    } else {
      const auth = JSON.parse(readFileSync(value('--authorization'), 'utf8').replace(/^\uFEFF/, ''));
      validateHeader(auth, value('--tag'));
      if (values['github-env']) {
        appendFileSync(value('--github-env'), `CANDIDATE_COMMIT=${auth.candidateSourceCommit}\nCANDIDATE_RUN=${auth.candidateWorkflowRunId}\n`);
      } else {
        const metadata = JSON.parse(readFileSync(resolve(value('--payload'), 'LINUX-CANDIDATE-METADATA.json'), 'utf8'));
        console.log(JSON.stringify(validateAuthorization(auth, metadata, process.cwd(), value('--tag'))));
      }
    }
  } catch (error) {
    console.error(`Linux release authorization rejected: ${error.message}`);
    process.exitCode = 1;
  }
}
