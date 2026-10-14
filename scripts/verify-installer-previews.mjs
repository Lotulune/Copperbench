// Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { createHash } from 'node:crypto';

const folder = process.argv[2];
if (!folder) throw new Error('Usage: node scripts/verify-installer-previews.mjs <preview-directory>');
const report = JSON.parse(readFileSync(join(folder, 'glyph-report.json'), 'utf8'));
const source = readFileSync(join(folder, 'installer-source.json'));
if (createHash('sha256').update(source).digest('hex') !== report.sourceManifestSha256
    || !/^[a-f0-9]{64}$/.test(report.installerJarSha256 ?? '')) throw new Error('Missing installer/source binding');
if (report.schemaVersion !== 1 || report.glyphCoverage !== 'passed' || report.checkedTexts < 100
    || report.installerActionsExecuted !== false || report.visualReview !== 'required'
    || report.fontSha256 !== '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b')
  throw new Error('Installer glyph evidence is missing or invalid');
const expected = ['zh', 'en'].flatMap(locale => ['dark', 'light'].flatMap(theme =>
  ['', '-progress', '-complete', '-error', '-license', '-license-additional', '-license-font'].map(page => `installer-${locale}-${theme}${page}.png`)));
if (report.captures?.length !== expected.length) throw new Error('Incomplete installer page matrix');
for (const file of expected) {
  const entries = report.captures.filter(entry => entry.file === file);
  if (entries.length !== 1) throw new Error(`Missing or duplicate capture: ${file}`);
  const bytes = readFileSync(join(folder, file));
  if (bytes.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a' || bytes.readUInt32BE(16) < 300 || bytes.readUInt32BE(20) < 200
      || createHash('sha256').update(bytes).digest('hex') !== entries[0].sha256)
    throw new Error(`Invalid or stale capture: ${file}`);
}
console.log(`Installer glyph coverage passed; ${expected.length} hash-bound captures require visual review.`);
