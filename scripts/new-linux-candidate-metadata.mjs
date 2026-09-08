import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { resolve, basename } from 'node:path';

function fail(message) {
  console.error(`linux candidate metadata: ${message}`);
  process.exit(1);
}

function option(name, fallback = undefined) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function asset(path, role, mediaType) {
  const stats = statSync(path);
  if (!stats.isFile()) fail(`${path} is not a file`);
  return { role, name: basename(path), mediaType, size: stats.size, sha256: sha256(path) };
}

function exactlyOne(directory, matcher, label) {
  const matches = readdirSync(directory).filter((name) => matcher.test(name));
  if (matches.length !== 1) fail(`expected exactly one ${label}, found ${matches.length}: ${matches.join(', ')}`);
  return resolve(directory, matches[0]);
}

const exportDir = resolve(option('--export-dir', 'build/export'));
const manifestPath = resolve(option('--manifest', resolve(exportDir, 'linux-candidate-manifest.json')));
const sbomPath = resolve(option('--sbom', resolve(exportDir, 'copperbench-linux.spdx.json')));
const outputPath = resolve(option('--output', resolve(exportDir, 'LINUX-CANDIDATE-METADATA.json')));
let sourceCommit = option('--source-commit');
if (!sourceCommit) {
  try { sourceCommit = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(); }
  catch { sourceCommit = ''; }
}
const sourceRef = option('--source-ref', process.env.GITHUB_REF ?? 'local');
const workflowRunId = option('--run-id', process.env.GITHUB_RUN_ID ?? 'local');
const workflowRunAttempt = option('--run-attempt', process.env.GITHUB_RUN_ATTEMPT ?? '1');

if (!/^[0-9a-f]{40}$/.test(sourceCommit)) fail('source commit must be a lowercase 40-character Git SHA');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
if (manifest.kind !== 'stage15-linux-candidate') fail('manifest kind is not stage15-linux-candidate');
if (manifest.status !== 'development-not-certified' || manifest.formalSupportClaim !== false)
  fail('manifest must remain development-not-certified with formalSupportClaim=false');
if (manifest.platform?.os !== 'linux' || manifest.platform?.arch !== 'x86_64')
  fail('manifest platform must be linux/x86_64');

const portablePath = exactlyOne(exportDir, /^Copperbench.*Linux x86_64\.tar\.gz$/, 'portable tarball');
const debPath = exactlyOne(exportDir, /^copperbench_.*_amd64\.deb$/, 'Debian package');
const assets = {
  portable: asset(portablePath, 'portable', 'application/gzip'),
  deb: asset(debPath, 'deb', 'application/vnd.debian.binary-package'),
  sbom: asset(sbomPath, 'sbom', 'application/spdx+json'),
  manifest: asset(manifestPath, 'manifest', 'application/json')
};

const identityInput = [sourceCommit, ...Object.values(assets)
  .sort((left, right) => left.role.localeCompare(right.role))
  .map((entry) => `${entry.role}:${entry.name}:${entry.sha256}:${entry.size}`)].join('\n');
const candidateSha256 = createHash('sha256').update(identityInput).digest('hex');
const metadata = {
  schemaVersion: '1.0',
  kind: 'stage15-linux-immutable-candidate',
  status: 'development-not-certified',
  formalSupportClaim: false,
  exactBinaryPromotionEligible: false,
  candidateId: `sha256:${candidateSha256}`,
  productId: manifest.productId,
  productVersion: manifest.productVersion,
  platform: manifest.platform,
  source: {
    commit: sourceCommit,
    ref: sourceRef,
    workflowRunId,
    workflowRunAttempt
  },
  assets
};
writeFileSync(outputPath, `${JSON.stringify(metadata, null, 2)}\n`, 'utf8');
console.log(`Linux candidate metadata: ${metadata.candidateId} -> ${outputPath}`);
