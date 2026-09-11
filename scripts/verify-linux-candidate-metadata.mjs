import { createHash } from 'node:crypto';
import { readFileSync, statSync } from 'node:fs';
import { resolve, basename } from 'node:path';

function fail(message) {
  console.error(`linux candidate metadata verification: ${message}`);
  process.exit(1);
}

function option(name, fallback = undefined) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

const exportDir = resolve(option('--export-dir', 'build/export'));
const metadataPath = resolve(option('--metadata', resolve(exportDir, 'LINUX-CANDIDATE-METADATA.json')));
const expectedCommit = option('--expected-commit');
const metadata = JSON.parse(readFileSync(metadataPath, 'utf8'));

if (metadata.schemaVersion !== '1.0' || metadata.kind !== 'stage15-linux-immutable-candidate')
  fail('unexpected schemaVersion/kind');
if (metadata.status !== 'development-not-certified' || metadata.formalSupportClaim !== false)
  fail('candidate must remain development-not-certified with formalSupportClaim=false');
if (metadata.exactBinaryPromotionEligible !== false)
  fail('development candidate cannot claim exact-binary promotion eligibility');
if (!/^sha256:[0-9a-f]{64}$/.test(metadata.candidateId ?? '')) fail('candidateId is invalid');
if (!/^[0-9a-f]{40}$/.test(metadata.source?.commit ?? '')) fail('source commit is invalid');
if (expectedCommit && metadata.source.commit !== expectedCommit) fail('source commit does not match expected commit');
if (metadata.platform?.os !== 'linux' || metadata.platform?.arch !== 'x86_64') fail('platform is not linux/x86_64');

const requiredRoles = ['portable', 'deb', 'sbom', 'manifest'];
if (Object.keys(metadata.assets ?? {}).sort().join(',') !== requiredRoles.slice().sort().join(','))
  fail(`asset roles must be exactly ${requiredRoles.join(', ')}`);

for (const role of requiredRoles) {
  const entry = metadata.assets[role];
  if (entry.role !== role) fail(`${role} role field does not match key`);
  if (!/^[0-9a-f]{64}$/.test(entry.sha256 ?? '')) fail(`${role} SHA-256 is invalid`);
  if (!Number.isSafeInteger(entry.size) || entry.size < 0) fail(`${role} size is invalid`);
  if (!entry.name || basename(entry.name) !== entry.name) fail(`${role} name is not a leaf filename`);
  const path = resolve(exportDir, entry.name);
  const stats = statSync(path);
  if (!stats.isFile()) fail(`${role} asset is missing`);
  if (stats.size !== entry.size) fail(`${role} size mismatch`);
  const actual = sha256(path);
  if (actual !== entry.sha256) fail(`${role} SHA-256 mismatch`);
}

const identityInput = [metadata.source.commit, ...Object.values(metadata.assets)
  .sort((left, right) => left.role.localeCompare(right.role))
  .map((entry) => `${entry.role}:${entry.name}:${entry.sha256}:${entry.size}`)].join('\n');
const expectedId = `sha256:${createHash('sha256').update(identityInput).digest('hex')}`;
if (metadata.candidateId !== expectedId) fail('candidateId does not bind the declared source/assets');

console.log(`Linux candidate metadata verified: ${metadata.candidateId}`);
