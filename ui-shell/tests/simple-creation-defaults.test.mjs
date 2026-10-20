import test from 'node:test';
import assert from 'node:assert/strict';
import { creationModId } from '../src/onboarding/simpleCreationDefaults.ts';

test('display names become valid bounded namespaces without losing a non-Latin name', () => {
  assert.equal(creationModId('Copper Lantern', 'work_123'), 'copper_lantern');
  assert.equal(creationModId('林间铜灯', 'work_123'), 'work_123');
  assert.equal(creationModId('123', 'work_123'), 'work_123');
  assert.match(creationModId('9 lights', 'work_123'), /^[a-z][a-z0-9_]{1,31}$/);
  assert.equal(creationModId('x'.repeat(80), 'work_123').length, 32);
});
