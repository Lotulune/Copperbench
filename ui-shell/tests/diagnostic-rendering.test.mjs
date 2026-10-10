import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const loader = await createServer({
  configFile: false, root: fileURLToPath(new URL('../', import.meta.url)),
  server: { middlewareMode: true, hmr: false, watch: null }, appType: 'custom',
  optimizeDeps: { noDiscovery: true }
});
let i18n;
try { i18n = await loader.ssrLoadModule('/src/i18n/index.ts'); }
finally { await loader.close(); }
const fixtures = JSON.parse(readFileSync(new URL('../../sdk/tests/diagnostic-rendering.json', import.meta.url), 'utf8'));

test('UI uses the same literal Core messages as the SDKs without changing evidence', () => {
  for (const fixture of fixtures.cases) {
    const before = structuredClone(fixture.message);
    assert.equal(i18n.t(fixture.message, fixtures.code), fixture.expected, fixture.name);
    assert.deepEqual(fixture.message, before);
  }
  const inherited = Object.create({ hidden: 'must not render' });
  inherited.value = Number.POSITIVE_INFINITY;
  assert.equal(i18n.formatTemplate('{hidden}|{value}', inherited), '{hidden}|{value}');
});

test('locale switches rerender the same raw diagnostic', async () => {
  globalThis.window = { localStorage: { setItem() {} }, alert(message) { throw new Error(message); } };
  const message = { key: 'diagnostic.field_contract_invalid', fallback: '{field}: {reason}',
    args: { field: '/commands/0', reason: 'Expected text.' } };
  const original = structuredClone(message);
  const savedFailure = i18n.diagnosticMessages([{ code: 'FIELD_TYPE_INVALID', message }], 'fallback');
  // No browser storage or host bridge is needed for the in-memory locale state.
  await i18n.setUiLocale('zh');
  const chinese = i18n.t(message);
  assert.equal(i18n.renderUiMessage(savedFailure), chinese);
  await i18n.setUiLocale('en');
  const english = i18n.t(message);
  assert.equal(i18n.renderUiMessage(savedFailure), english);
  assert.equal(savedFailure.diagnostics[0].message, message);
  assert.ok(chinese.includes('/commands/0'));
  assert.ok(english.includes('/commands/0'));
  assert.ok(chinese.includes('Expected text.'));
  assert.ok(english.includes('Expected text.'));
  assert.notEqual(chinese, english);
  assert.deepEqual(message, original);
  await i18n.setUiLocale('zh');
  delete globalThis.window;
});
