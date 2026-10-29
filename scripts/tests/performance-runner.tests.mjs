import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseOptions, buildRunPlan, finalizeReport } from '../performance/measure.mjs';

test('measurement options require twenty valid repetitions and an honest attempt cap', () => {
  assert.equal(parseOptions([]).repetitions, 20);
  assert.equal(parseOptions([]).warmups, 2);
  assert.equal(parseOptions([]).partial, false);
  assert.equal(parseOptions(['--cells', 'large:graph-search', '--repetitions', '21', '--max-attempts', '25']).cells[0], 'large:graph-search');
  for (const arguments_ of [ ['--repetitions', '19'], ['--repetitions', '26'], ['--warmups', '-1'],
    ['--timeout-ms', 'NaN'], ['--locale', 'unknown'], ['--port', '80'], ['--cells', 'small:unknown'], ['--output'], ['--partial'] ])
    assert.throws(() => parseOptions(arguments_));
});

test('measurement partial mode is explicit and appears in the dry-run baseline plan', t => {
  const root = mkdtempSync(join(tmpdir(), 'copperbench-performance-partial-plan-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const full = parseOptions(['--dry-run', '--baseline', 'baseline.json']);
  const partial = parseOptions(['--dry-run', '--baseline', 'baseline.json', '--partial']);
  assert.equal(buildRunPlan(full, root).comparisonMode, 'full');
  assert.equal(partial.partial, true);
  assert.equal(buildRunPlan(partial, root).comparisonMode, 'partial');
  assert.equal(buildRunPlan(parseOptions(['--dry-run']), root).comparisonMode, null);
});

test('measurement final report preserves coverage evidence and applies full/partial exit status', t => {
  const root = mkdtempSync(join(tmpdir(), 'copperbench-performance-baseline-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const conditions = { level: 'browser-ui-with-mock-core-bridge', measurementVersion: 'browser-interaction-v1',
    environment: { machineFingerprint: 'fixture-machine' }, browser: { version: 'fixture-browser' },
    viewport: { width: 1366, height: 768 }, locale: 'zh', theme: 'light', cache: 'warm fixture',
    fixtures: { small: 'fixture-small', large: 'fixture-large' }, warmups: 2 };
  const cell = id => ({ id, status: 'passed', endToEnd: { n: 20, p95Ms: 100 },
    browserInputToReady: { n: 20, p95Ms: 100 }, feedback: { n: 20, p95Ms: 100 } });
  const report = ids => ({ status: 'passed', comparisonConditions: structuredClone(conditions), cells: ids.map(cell) });
  writeFileSync(join(root, 'baseline.json'), JSON.stringify(report(['small:navigation', 'large:graph-open'])), 'utf8');
  const readReport = () => JSON.parse(readFileSync(join(root, 'report.json'), 'utf8'));
  const options = parseOptions(['--baseline', 'baseline.json']);
  assert.equal(finalizeReport(report(['small:navigation']), options, root, root), 1);
  const full = readReport();
  assert.equal(full.status, 'passed');
  assert.equal(full.comparison.coverageComplete, false);
  assert.equal(full.comparison.comparable, false);
  assert.deepEqual(full.comparison.baselineOnlyCells, ['large:graph-open']);
  assert.equal(finalizeReport(report(['small:navigation']), { ...options, partial: true }, root, root), 0);
  const partial = readReport().comparison;
  assert.equal(partial.comparisonMode, 'partial');
  assert.equal(partial.coverageComplete, false);
  assert.deepEqual(partial.scope.sharedCells, ['small:navigation']);
  assert.equal(finalizeReport(report(['large:graph-search']), { ...options, partial: true }, root, root), 1);
  assert.deepEqual(readReport().comparison.scope.sharedCells, []);
  const complete = report(['small:navigation', 'large:graph-open']);
  complete.cells[0].feedback.p95Ms = 121;
  complete.cells[0].browserInputToReady = { n: 19, p95Ms: null };
  assert.equal(finalizeReport(complete, options, root, root), 0);
  assert.equal(readReport().comparison.cells[0].status, 'review_required');
  writeFileSync(join(root, 'baseline.json'), '{}', 'utf8');
  assert.equal(finalizeReport(report(['small:navigation']), options, root, root), 1);
  assert.equal(readReport().comparison.status, 'invalid_comparison');
  assert.equal(readReport().status, 'passed');
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
