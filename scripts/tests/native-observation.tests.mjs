import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { NativeObservationRecorder } from '../performance/native-observation.mjs';

function recorder(t, options = {}) {
  const repository = mkdtempSync(join(tmpdir(), 'copperbench-native-record-'));
  t.after(() => rmSync(repository, { recursive: true, force: true }));
  let now = 0;
  const instance = new NativeObservationRecorder({ repository, output: 'output/native/test',
    metadata: { candidate: { applicationSha256: 'a'.repeat(64) }, sample: { scope: 'unit fixture' },
      environment: { scope: 'injected test clock; no native runtime' } }, clock: () => now, ...options });
  return { instance, repository, advance: value => { now = value; } };
}

test('a snapshot receipt never becomes a visual pass and low samples never publish p95', t => {
  const { instance, advance } = recorder(t);
  const index = instance.begin({ cellId: 'native-search', operation: 'Enter exact element name',
    beforeObservation: { windowId: 10, screenshotId: 'before' } });
  advance(350);
  instance.observed(index, { windowId: 10, screenshotId: 'after' });
  assert.equal(instance.report().attempts[0].status, 'awaiting_visual_review');
  assert.equal(instance.report().cells[0].validCount, 0);
  instance.judge(index, { status: 'passed', reason: 'Parent inspected exact result and editable fields',
    evidencePaths: ['screenshots/after.png'] });
  const cell = instance.report().cells[0];
  assert.deepEqual(cell.rawDispatchToObservationMs, [350]);
  assert.equal(cell.dispatchToObservation.p95Ms, null);
  assert.equal(instance.report().attempts[0].rendererMs, null);
  assert.match(instance.report().boundaries.comparison, /Not directly comparable/);
});

test('unknown input and failed attempts stay in the denominator and cannot be replayed invisibly', t => {
  const { instance } = recorder(t, { maxAttemptsPerCell: 1 });
  assert.throws(() => instance.begin({ cellId: 'search', operation: 'type' }), /inspected/);
  const index = instance.begin({ cellId: 'search', operation: 'type',
    beforeObservation: { windowId: 10, screenshotId: 'before' } });
  assert.throws(() => instance.begin({ cellId: 'search', operation: 'retry' }), /current observation/);
  assert.throws(() => instance.judge(index, { status: 'passed', reason: 'input returned success' }), /retained visual/);
  instance.judge(index, { status: 'interrupted', reason: 'Focus was lost; no retry without fresh observation' });
  assert.equal(instance.report().cells[0].attemptedCount, 1);
  assert.equal(instance.report().cells[0].unverifiedCount, 1);
  assert.throws(() => instance.begin({ cellId: 'search', operation: 'retry',
    beforeObservation: { windowId: 10, screenshotId: 'new' } }), /attempt bound/);
});

test('memory point samples retain process identity and existing evidence is protected', t => {
  const { instance, repository } = recorder(t);
  instance.recordMemory({ pid: 123, processStartedAt: '2026-10-15T00:00:00Z', executable: 'isolated/javaw.exe',
    phase: 'workspace idle', workingSetBytes: 1000, privateBytes: 2000 });
  const raw = JSON.parse(readFileSync(join(repository, 'output/native/test/report.json'), 'utf8'));
  assert.equal(raw.memoryObservations[0].pid, 123);
  assert.match(raw.memoryObservations[0].scope, /not.*proven peak/);
  assert.throws(() => instance.recordMemory({ pid: 123, processStartedAt: 'x', executable: 'x', phase: 'x',
    workingSetBytes: NaN, privateBytes: 1 }), /finite byte counts/);
  assert.throws(() => new NativeObservationRecorder({ repository, output: 'output/native/test' }), /overwritten/);
  assert.throws(() => new NativeObservationRecorder({ repository, output: '../elsewhere' }), /under repository output/);
});
