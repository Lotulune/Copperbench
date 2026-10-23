import assert from 'node:assert/strict';
import test from 'node:test';
import { agentAttachment } from '../src/components/agentEvidence.ts';

test('attached evidence is bounded and redacted before it reaches the composer', () => {
  const evidence = agentAttachment('task:build-id', 'task-log', 'authorization=Bearer private-value\napi_key=another-secret\n' + 'x'.repeat(70000));
  assert.equal(evidence.source, 'task:build-id'); assert.equal(evidence.truncated, true); assert.equal(evidence.text.length, 65536);
  assert.equal(evidence.text.includes('private-value'), false); assert.equal(evidence.text.includes('another-secret'), false);
});
