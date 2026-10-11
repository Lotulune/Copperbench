import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const loader = await createServer({ configFile: false, root: fileURLToPath(new URL('../', import.meta.url)),
  server: { middlewareMode: true, hmr: false, watch: null }, appType: 'custom', optimizeDeps: { noDiscovery: true } });
let legacyTaskVerification, i18n;
try {
  ({ legacyTaskVerification } = await loader.ssrLoadModule('/src/utils/taskVerification.ts'));
  i18n = await loader.ssrLoadModule('/src/i18n/index.ts');
} finally { await loader.close(); }
const fixtures = JSON.parse(readFileSync(new URL('../../ui-core/fixtures/task-verification-v1.json', import.meta.url), 'utf8'));

test('legacy presentation shares the Core reason matrix without interpreting English details', () => {
  for (const fixture of fixtures.cases) {
    const before = structuredClone(fixture.task);
    const result = legacyTaskVerification(fixture.task);
    assert.equal(result.subreason, fixture.subreason, fixture.name);
    if (['running', 'cancelled', 'unconfirmed', 'report_missing', 'report_invalid', 'report_stale', 'unavailable'].includes(fixture.subreason)) {
      assert.equal(result.coverage.acceptanceExecuted, null);
      assert.equal(result.coverage.shortfall, null);
    }
    assert.deepEqual(fixture.task, before);
  }
  for (const fixture of fixtures.cases.filter(f => f.name.startsWith('missing '))) {
    assert.equal(legacyTaskVerification(fixture.task).coverage.shortfall, null);
  }
});

test('retained results render both languages with zeroes and unchanged parameters', async () => {
  globalThis.window = { localStorage: { setItem() {} } };
  try {
    const result = legacyTaskVerification(fixtures.cases[0].task);
    const before = structuredClone(result);
    await i18n.setUiLocale('zh');
    assert.equal(i18n.t(result.message), '要求至少 6 项业务测试，实际执行 5 项，还差 1 项。');
    await i18n.setUiLocale('en');
    assert.equal(i18n.t(result.message), '6 required; 5 business tests executed; 1 missing.');
    assert.deepEqual(result, before);
    const zero = legacyTaskVerification(fixtures.cases[1].task);
    assert.equal(zero.coverage.acceptanceExecuted, 0);
    assert.match(i18n.t(zero.message), /No business tests executed/);
  } finally { await i18n.setUiLocale('zh'); delete globalThis.window; }
});
