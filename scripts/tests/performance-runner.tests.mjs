import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseOptions, buildRunPlan } from '../performance/measure.mjs';

test('measurement options require twenty valid repetitions and an honest attempt cap', () => {
  assert.equal(parseOptions([]).repetitions, 20);
  assert.equal(parseOptions([]).warmups, 2);
  assert.equal(parseOptions(['--cells', 'large:graph-search', '--repetitions', '21', '--max-attempts', '25']).cells[0], 'large:graph-search');
  for (const arguments_ of [ ['--repetitions', '19'], ['--repetitions', '26'], ['--warmups', '-1'],
    ['--timeout-ms', 'NaN'], ['--locale', 'unknown'], ['--port', '80'], ['--cells', 'small:unknown'], ['--output'] ])
    assert.throws(() => parseOptions(arguments_));
});

test('a dry-run plan reports dependency gaps and cannot overwrite an old report or escape its output scope', t => {
  const root = mkdtempSync(join(tmpdir(), 'copperbench-performance-plan-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const options = parseOptions(['--dry-run', '--cells', 'small:navigation', '--output', 'output/performance/new']);
  const plan = buildRunPlan(options, root);
  assert.equal(plan.cwd, 'ui-shell');
  assert.equal(plan.environmentGaps.length, 2);
  assert.equal(plan.validRepetitionsPerCell, 20);
  assert.equal(plan.boundaries.javaCore.startsWith('unknown'), true);
  assert.equal(plan.boundaries.nativeJcef, 'not_run');
  assert.equal(existsSync(join(root, plan.output)), false);
  mkdirSync(join(root, 'output/performance/new'), { recursive: true });
  writeFileSync(join(root, 'output/performance/new/report.json'), 'retained');
  assert.throws(() => buildRunPlan(options, root), /already exists/);
  assert.throws(() => buildRunPlan({ ...options, output: '../outside' }, root), /inside repository/);
  assert.throws(() => buildRunPlan({ ...options, output: 'ui-shell/src' }, root), /inside repository/);
});
