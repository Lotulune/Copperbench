import { existsSync, readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const read = (path) => readFileSync(resolve(root, path), 'utf8');
const fail = (message) => { throw new Error(`PRODUCT_STATUS_INVALID: ${message}`); };
const status = JSON.parse(read('product-status.json'));
const tracked = new Set(execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard'], { cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 })
  .split(/\r?\n/).filter(Boolean).map((path) => path.replaceAll('\\', '/')));

if (status.schemaVersion !== '1.0') fail('schemaVersion must be 1.0');
if (!/^[0-9a-f]{40}$/.test(status.snapshot?.lastVerifiedCommit ?? '')) fail('lastVerifiedCommit must be a full SHA');

const configuration = Object.fromEntries(read('src/main/resources/mcreator.conf').split(/\r?\n/)
  .filter((line) => line.includes('=')).map((line) => line.split('=', 2)));
if (status.product?.version !== configuration['product.version']) fail('product.version differs from mcreator.conf');
if (status.product?.id !== configuration['product.id']) fail('product.id differs from mcreator.conf');
if (status.product?.license !== 'GPL-3.0-only') fail('license must use the GPL-3.0-only SPDX identifier');

const coverageSource = read('src/main/java/dev/copperbench/release/ElementCoverageCatalog.java');
const slice = coverageSource.match(/FIRST_PARTY_SLICE\s*=\s*List\.of\((.*?)\);/s)?.[1]
  .match(/"[^"]+"/g)?.map((value) => JSON.parse(value)) ?? [];
const declaredSlice = [...status.elements.supported, ...status.elements.preview];
if (JSON.stringify(slice) !== JSON.stringify(declaredSlice)) fail('supported + preview differs from ElementCoverageCatalog');

const generatorIds = status.generators.map((generator) => generator.id);
if (generatorIds.length !== 8 || new Set(generatorIds).size !== 8) fail('exactly eight unique generators are required');
const gateIds = status.gates.map((gate) => gate.id);
if (new Set(gateIds).size !== gateIds.length) fail('gate ids must be unique');
for (const gate of status.gates) {
  for (const evidence of gate.evidence) {
    if (/^https:\/\//.test(evidence)) continue;
    if (!existsSync(resolve(root, evidence)) || !tracked.has(evidence.replaceAll('\\', '/'))) {
      fail(`${gate.id} references missing or untracked evidence: ${evidence}`);
    }
  }
}
const openBetaGate = status.gates.some((gate) => gate.betaBlocking && gate.status !== 'passed');
if (status.product.betaEligible === openBetaGate) fail('betaEligible contradicts beta-blocking gates');

const betaRelease = status.delivery?.betaRelease;
if (!betaRelease || !new RegExp(`^v${status.product.version.replaceAll('.', '\\.')}\\-beta\\.\\d+$`).test(betaRelease.tag ?? '')) {
  fail('delivery.betaRelease.tag must be a Beta tag for product.version');
}
const candidateRelease = betaRelease.candidateRelease;
if (status.product.betaEligible && !candidateRelease) {
  fail('betaEligible requires delivery.betaRelease.candidateRelease');
}
if (candidateRelease) {
  if (!new RegExp(`^v${status.product.version.replaceAll('.', '\\.')}\\-preview\\.\\d+$`).test(candidateRelease.tag ?? '')) {
    fail('candidateRelease.tag must be a Preview tag for product.version');
  }
  if (!/^[0-9a-f]{40}$/.test(candidateRelease.sourceCommit ?? '')) {
    fail('candidateRelease.sourceCommit must be a full lowercase SHA');
  }
  const expectedAssets = { exe: '.exe', zip: '.zip', msix: '.msix', sbom: '.json' };
  const assetNames = [];
  for (const [role, extension] of Object.entries(expectedAssets)) {
    const asset = candidateRelease.assets?.[role];
    const plainWindowsFileName = typeof asset?.name === 'string'
      && asset.name.length > 0
      && asset.name !== '.'
      && asset.name !== '..'
      && !/[\u0000-\u001f<>:"/\\|?*]/.test(asset.name)
      && !/[ .]$/.test(asset.name)
      && !/^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(asset.name);
    if (!asset || !plainWindowsFileName || !asset.name.toLowerCase().endsWith(extension)) {
      fail(`candidateRelease.assets.${role}.name must use ${extension}`);
    }
    if (!/^[0-9a-f]{64}$/.test(asset.sha256 ?? '')) {
      fail(`candidateRelease.assets.${role}.sha256 must be a lowercase SHA-256`);
    }
    assetNames.push(asset.name);
  }
  if (candidateRelease.assets.sbom.name !== 'copperbench.spdx.json') {
    fail('candidateRelease SBOM must be named copperbench.spdx.json');
  }
  if (new Set(assetNames).size !== assetNames.length) fail('candidateRelease asset names must be unique');
}

if (existsSync(resolve(root, '.github/workflows/dependency-submission.yml'))) {
  fail('dependency-submission.yml must be removed while the repository dependency graph is disabled');
}
if (!existsSync(resolve(root, status.delivery.nightly.workflow))) fail('nightly workflow is missing');
if (!read('LICENSE.txt').trimStart().startsWith('GNU GENERAL PUBLIC LICENSE')) fail('LICENSE.txt is not the standard GPL text');
if (!read('LICENSE-ADDITIONAL-TERMS.md').includes('MCreator Section 7 Permission')) fail('additional terms are missing');
for (const packageFile of ['ui-core/package.json', 'ui-shell/package.json']) {
  if (JSON.parse(read(packageFile)).license !== 'GPL-3.0-only') fail(`${packageFile} has no SPDX license`);
}

console.log(`Product status passed: ${status.generators.length} generators, ${status.gates.length} gates, betaEligible=${status.product.betaEligible}.`);
