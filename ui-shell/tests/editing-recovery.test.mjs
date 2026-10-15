import assert from 'node:assert/strict';
import test from 'node:test';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';

const loader = await createServer({ configFile: false, root: fileURLToPath(new URL('../', import.meta.url)),
  server: { middlewareMode: true, hmr: false, watch: null }, appType: 'custom', optimizeDeps: { noDiscovery: true } });
let fields, failures, sessions, locale, layouts;
try {
  fields = await loader.ssrLoadModule('/src/hooks/editorFieldChanges.ts');
  failures = await loader.ssrLoadModule('/src/bridge/operationFailure.ts');
  sessions = await loader.ssrLoadModule('/src/hooks/specializedEditorSessions.ts');
  locale = await loader.ssrLoadModule('/src/i18n/locale.ts');
  layouts = await loader.ssrLoadModule('/src/hooks/layoutNumericDrafts.ts');
} finally { await loader.close(); }

const field = (path, value, control = 'number', extra = {}) => ({ path, value, control, readOnly: false,
  required: true, label: { key: 'fixture.field', fallback: path }, diagnostics: [], options: [], ...extra });
const editor = (...fields) => ({ element: { id: 'element-1', updatedAt: '2026-10-15T00:00:00Z' },
  sections: [{ id: 'main', fields }] });

test('partial and invalid decimal input remains an unsaved draft and never becomes zero', () => {
  for (const raw of ['', '-', '+', '.', '1e', '1e-', '  ', '1x', '0x10', 'Infinity']) {
    const result = fields.collectEditorFieldChanges(editor(field('/hardness', 3)), { '/hardness': raw });
    assert.equal(result.invalidNumber, true, JSON.stringify(raw));
    assert.deepEqual(result.changes, []);
    assert.equal(fields.numericDraftValue(raw), undefined);
  }
  const result = fields.collectEditorFieldChanges(editor(field('/hardness', 3), field('/name', 'A', 'text')),
    { '/hardness': '-', '/name': 'B' });
  assert.deepEqual(result.changes, [{ path: '/name', value: 'B' }]);
  assert.equal(result.invalid[0].path, '/hardness');
});

test('valid numeric spelling is preserved until submission and equivalent spelling needs no Core write', () => {
  const baseline = editor(field('/hardness', 1), field('/name', 'A', 'text'));
  for (const raw of ['1.', '1.0', '1e0']) {
    const draft = { '/hardness': raw, '/name': 'A' };
    const result = fields.collectEditorFieldChanges(baseline, draft);
    assert.equal(result.hasInputDraft, true);
    assert.deepEqual(result.changes, []);
    assert.equal(draft['/hardness'], raw);
    const submitted = fields.normalizeEditorNumericDrafts(baseline, draft);
    assert.equal(submitted['/hardness'], 1);
    assert.equal(fields.collectEditorFieldChanges(baseline, submitted).hasInputDraft, false);
    assert.equal(draft['/hardness'], raw);
  }
  assert.deepEqual(fields.collectEditorFieldChanges(baseline, { '/hardness': '2.5', '/name': 'A' }).changes,
    [{ path: '/hardness', value: 2.5 }]);
});

test('structured numeric input uses the actual item contract and keeps unknown properties', () => {
  const baseline = editor(field('/rows', [{ amount: 1, custom: { untouched: true } }], 'structured_list',
    { itemFields: [field('/amount', 1)] }));
  const draft = { '/rows': [{ amount: '-', custom: { untouched: true } }] };
  const invalid = fields.collectEditorFieldChanges(baseline, draft);
  assert.equal(invalid.invalid[0].path, '/rows/0/amount');
  draft['/rows'][0].amount = '1.';
  assert.equal(fields.collectEditorFieldChanges(baseline, draft).hasInputDraft, true);
  assert.deepEqual(fields.normalizeEditorNumericDrafts(baseline, draft)['/rows'], [{ amount: 1, custom: { untouched: true } }]);
});

test('read-only or removed contract fields retain their input without becoming writable changes', () => {
  for (const [control, baseline, value] of [['text', 'A', 'B'], ['number', 1, '-'], ['json', {}, '{invalid']]) {
    const projection = editor(field('/retained', baseline, control, { readOnly: true }));
    const draft = { '/retained': value };
    assert.equal(fields.hasEditorInputDraft(projection, draft), true);
    assert.deepEqual(fields.collectEditorFieldChanges(projection, draft).changes, []);
  }
  const numeric = editor(field('/retained', 1, 'number', { readOnly: true }));
  assert.equal(fields.hasEditorInputDraft(numeric, { '/retained': '1.' }), true);
  assert.equal(fields.hasEditorInputDraft(numeric, { '/retained': '1' }), false);
  assert.equal(fields.hasEditorInputDraft(editor(), { '/removed': 'preserved' }), true);
  const structured = editor(field('/rows', [{ amount: 1 }], 'structured_list', {
    readOnly: true, itemFields: [field('/amount', 1, 'number', { readOnly: true })]
  }));
  assert.equal(fields.hasEditorInputDraft(structured, { '/rows': [{ amount: '-' }] }), true);
  assert.deepEqual(fields.collectEditorFieldChanges(structured, { '/rows': [{ amount: '-' }] }).changes, []);
});

test('layout conversion does not reinterpret unknown string fields or erase extension data', () => {
  const baseline = { '/width': 176, '/components': [{ type: 'button', extension: 'keep', data: { x: 20, y: 40, custom: '01' } }] };
  const draft = { '/width': '180.', '/components': [{ type: 'button', extension: 'keep', data: { x: '-', y: '45', custom: '01' } }] };
  const result = layouts.layoutNumericDrafts(draft, baseline);
  assert.deepEqual(result.invalid, ['/components/0/data/x']);
  assert.equal(result.values['/width'], 180);
  assert.deepEqual(result.values['/components'][0], { type: 'button', extension: 'keep', data: { x: '-', y: 45, custom: '01' } });
  assert.equal(draft['/width'], '180.');
});

test('late save rebases its submission while preserving a newer draft in the same object session', () => {
  const initial = () => ({ name: 'A' });
  const key = sessions.specializedEditorKey(randomUUID(), 'same-id', 'gui');
  const submitted = { name: 'Submitted A' };
  sessions.updateSpecializedSession(key, initial, old => ({ ...old, data: submitted, dirty: true, saving: true }));
  const newer = { name: 'New input B' };
  sessions.updateSpecializedSession(key, initial, old => ({ ...old, data: newer }));
  const baseline = editor(field('/name', submitted.name, 'text'));
  sessions.commitSpecializedSession(key, initial, submitted, baseline, 11, []);
  const restored = sessions.getSpecializedSession(key, initial);
  assert.equal(restored.data, newer);
  assert.equal(restored.revision, 11);
  assert.equal(restored.dirty, true);
  assert.equal(restored.editor.sections[0].fields[0].value, submitted.name);
});

test('workspace identity isolates equal object IDs and save completion clears only its captured draft', () => {
  const initial = () => ({ value: 'original' });
  const first = sessions.specializedEditorKey(randomUUID(), 'same-id', 'loot');
  const second = sessions.specializedEditorKey(randomUUID(), 'same-id', 'loot');
  const submitted = { value: 'first workspace' };
  sessions.updateSpecializedSession(first, initial, old => ({ ...old, data: submitted, dirty: true }));
  sessions.updateSpecializedSession(second, initial, old => ({ ...old, data: { value: 'second workspace' }, dirty: true }));
  sessions.commitSpecializedSession(first, initial, submitted, editor(field('/value', submitted.value, 'text')), 9, []);
  assert.equal(sessions.getSpecializedSession(first, initial).dirty, false);
  assert.equal(sessions.getSpecializedSession(second, initial).data.value, 'second workspace');
  assert.equal(sessions.getSpecializedSession(second, initial).dirty, true);
});

test('display language changes render the same failure while preserving every original machine field', async (t) => {
  const previousWindow = globalThis.window;
  const previousLocale = locale.UI_LOCALE;
  globalThis.window = { localStorage: { setItem() {} } };
  t.after(async () => {
    await locale.setUiLocale(previousLocale);
    if (previousWindow === undefined) delete globalThis.window;
    else globalThis.window = previousWindow;
  });
  const original = [{ code: 'SOURCE_HASH_CONFLICT', path: '/src/Example.java', severity: 'error',
    message: { key: 'action.open_field', fallback: 'Open field', args: { detail: 'sha256 changed' } },
    recoverable: true, actions: [], details: { expected: 'old bytes', actual: 'new bytes' } }];
  const failure = new failures.OperationFailure(original, { zh: '无法读取', en: 'Could not read' });
  await locale.setUiLocale('zh');
  const chinese = failures.operationFailureText(failure);
  await locale.setUiLocale('en');
  const english = failures.operationFailureText(failure);
  assert.notEqual(chinese, english);
  assert.equal(failures.operationDiagnostics(failure), original);
  assert.deepEqual(failures.operationDiagnostics(failure)[0].details, { expected: 'old bytes', actual: 'new bytes' });
  assert.match(english, /sha256 changed/);
});
