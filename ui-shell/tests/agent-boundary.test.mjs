import assert from 'node:assert/strict';
import test from 'node:test';
import { loadSsrTestModules } from './helpers/vite-ssr-loader.mjs';
const [{ parseAgentSnapshot, parseAttachmentReply, attachmentErrorMessage }, { renderUiMessage, setUiLocale }] =
  await loadSsrTestModules(['/src/bridge/agentBridge.ts', '/src/i18n/index.ts']);
const snapshot = { state: 'idle', sequence: 1, conversationId: null, provider: null, sessions: [], messages: [], approvals: [], providers: [] };
test('native settings and attachment payloads are validated independently of snapshots', () => {
  assert.equal(parseAgentSnapshot({ ...snapshot, effort: 'xhigh', permission: 'default' }).effort, 'xhigh');
  assert.equal(parseAgentSnapshot({ ...snapshot, effort: 'ultra', permission: 'read-only' }).effort, 'ultra');
  for (const patch of [{ effort: 'invalid effort' }, { effort: 'x'.repeat(65) }, { permission: 'bypass' }, { messages: [{ id: 'x', role: 'user', text: '', at: 1, attachments: [{ id: '../../outside' }] }] }])
    assert.throws(() => parseAgentSnapshot({ ...snapshot, ...patch }));
  assert.throws(() => parseAttachmentReply(snapshot));
  assert.throws(() => parseAttachmentReply({ ok: true, dataUrl: 'data:image/svg+xml,<svg/>' }));
  assert.throws(() => parseAttachmentReply({ ok: true, offset: -1 }));
  assert.throws(() => parseAttachmentReply({ ok: false, error: 'Upload was rejected' }), /Upload was rejected/);
  assert.equal(parseAttachmentReply({ ok: true, id: 'a', offset: 0 }).offset, 0);
});

test('legacy session model inheritance requires a conversation without a model override', () => {
  assert.equal(parseAgentSnapshot({ ...snapshot, conversationId: 'old', model: null, usesSessionModel: true }).usesSessionModel, true);
  for (const patch of [{ usesSessionModel: 'yes' }, { usesSessionModel: true }, { conversationId: 'old', model: 'new-model', usesSessionModel: true }])
    assert.throws(() => parseAgentSnapshot({ ...snapshot, ...patch }));
});

test('attachment errors retain translations and raw diagnostics until render time', async () => {
  const previousWindow = globalThis.window;
  globalThis.window = { localStorage: { setItem() {} } };
  try {
    await setUiLocale('en');
    let failure;
    try { parseAttachmentReply({ ok: false, error: 'raw diagnostic', errorText: { key: 'attachment.fixture', zh: '未能保存 {file}', en: 'Could not save {file}', args: { file: 'notes.txt' } } }); }
    catch (error) { failure = attachmentErrorMessage(error); }
    assert.equal(renderUiMessage(failure), 'Could not save notes.txt');
    await setUiLocale('zh'); assert.equal(renderUiMessage(failure), '未能保存 notes.txt');
    await setUiLocale('en'); assert.equal(renderUiMessage(failure), 'Could not save notes.txt');
    const raw = attachmentErrorMessage(new Error('OS diagnostic: notes.txt'));
    await setUiLocale('zh'); assert.equal(renderUiMessage(raw), 'OS diagnostic: notes.txt');
  } finally { await setUiLocale('en'); globalThis.window = previousWindow; }
});
