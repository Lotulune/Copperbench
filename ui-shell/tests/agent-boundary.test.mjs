import assert from 'node:assert/strict';
import test from 'node:test';
import { loadSsrTestModules } from './helpers/vite-ssr-loader.mjs';
const [{ parseAgentSnapshot, parseAttachmentReply }] = await loadSsrTestModules(['/src/bridge/agentBridge.ts']);
const snapshot = { state: 'idle', sequence: 1, conversationId: null, provider: null, sessions: [], messages: [], approvals: [], providers: [] };
test('native settings and attachment payloads are validated independently of snapshots', () => {
  assert.equal(parseAgentSnapshot({ ...snapshot, effort: 'xhigh', permission: 'default' }).effort, 'xhigh');
  for (const patch of [{ effort: 'ultra' }, { permission: 'bypass' }, { messages: [{ id: 'x', role: 'user', text: '', at: 1, attachments: [{ id: '../../outside' }] }] }])
    assert.throws(() => parseAgentSnapshot({ ...snapshot, ...patch }));
  assert.throws(() => parseAttachmentReply(snapshot));
  assert.throws(() => parseAttachmentReply({ ok: true, dataUrl: 'data:image/svg+xml,<svg/>' }));
  assert.throws(() => parseAttachmentReply({ ok: true, offset: -1 }));
  assert.throws(() => parseAttachmentReply({ ok: false, error: 'Upload was rejected' }), /Upload was rejected/);
  assert.equal(parseAttachmentReply({ ok: true, id: 'a', offset: 0 }).offset, 0);
});
