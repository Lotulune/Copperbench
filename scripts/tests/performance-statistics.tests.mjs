import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { statistics, summarizeCell, parseCells, compareReports } from '../performance/statistics.mjs';
import { sanitizeFailure } from '../performance/failure.mjs';

const attempt = value => ({ status: 'passed', endToEndMs: value, browserInputToReadyMs: value / 2, feedbackMs: value / 4 });
const conditions = () => ({ level: 'browser-ui-with-mock-core-bridge', measurementVersion: 'browser-interaction-v1',
  environment: { machineFingerprint: 'fixture-machine' }, browser: { version: 'fixture-browser' },
  viewport: { width: 1366, height: 768 }, locale: 'zh', theme: 'light', cache: 'warm fixture',
  fixtures: { large: 'fixture-sha256' }, warmups: 2 });
const report = p95 => ({ status: 'passed', comparisonConditions: conditions(),
  cells: [{ id: 'large:graph-open', status: 'passed', endToEnd: { n: 20, p95Ms: p95 },
    browserInputToReady: { n: 20, p95Ms: p95 }, feedback: { n: 20, p95Ms: p95 } }] });

test('nearest-rank p95 retains chronological raw values and reports the conventional median', () => {
  const values = Array.from({ length: 20 }, (_, index) => 20 - index);
  const original = [...values];
  assert.deepEqual(statistics(values), { n: 20, medianMs: 10.5, maxMs: 20, p95Ms: 19,
    p95Algorithm: 'nearest-rank: sorted[ceil(0.95*n)-1]; valid samples only, failure denominator reported separately', p95WithheldReason: null });
  assert.deepEqual(values, original);
});

test('small samples retain null p95 and unknown budget judgments', () => {
  const cell = summarizeCell({ id: 'small:navigation', requiredValidRepetitions: 20,
    warmups: [attempt(1)], attempts: [attempt(3)] });
  assert.equal(cell.status, 'failed');
  assert.equal(cell.endToEnd.p95Ms, null);
  assert.equal(cell.budgetObservation.endToEndExceeded, null);
  assert.equal(cell.budgetObservation.feedbackExceeded, null);
  assert.equal(cell.warmupCount, 1);
  assert.deepEqual(cell.rawDurationMs, [3]);
  assert.throws(() => statistics([NaN]));
  assert.throws(() => statistics([-1]));
  assert.throws(() => statistics([Infinity]));
});

test('failures, timeouts, interrupted attempts and failed warmups remain in the denominator', () => {
  const attempts = Array.from({ length: 20 }, (_, index) => attempt(index + 1));
  const cell = summarizeCell({ id: 'large:graph-search', requiredValidRepetitions: 20,
    warmups: [{ status: 'failed' }], attempts: [...attempts, { status: 'failed' }, { status: 'timeout' }, { status: 'running' }] });
  assert.equal(cell.status, 'failed');
  assert.equal(cell.validCount, 20);
  assert.equal(cell.attemptedCount, 23);
  assert.equal(cell.failureCount, 1);
  assert.equal(cell.timeoutCount, 1);
  assert.equal(cell.interruptedCount, 1);
  assert.equal(cell.warmupFailureCount, 1);
  assert.equal(cell.endToEnd.p95Ms, 19);
  const missingMetrics = summarizeCell({ id: cell.id, requiredValidRepetitions: 20, warmups: [],
    attempts: attempts.map(value => ({ ...value, feedbackMs: null })) });
  assert.equal(missingMetrics.status, 'failed');
  assert.equal(missingMetrics.budgetObservation.feedbackExceeded, null);
});

test('selected cells are explicit, unique and do not permit unknown operations', () => {
  assert.deepEqual(parseCells('small:navigation,large:graph-search'), ['small:navigation', 'large:graph-search']);
  for (const invalid of ['', 'large:unknown', 'large:graph-search,large:graph-search', 'giant:navigation', 'small:navigation:extra'])
    assert.throws(() => parseCells(invalid));
});

test('same-condition degradation over 20% asks for review and never makes an automatic regression verdict', () => {
  const before = report(100);
  assert.equal(compareReports(before, report(120)).cells[0].status, 'no_review_trigger');
  const result = compareReports(before, report(121));
  assert.equal(result.cells[0].status, 'review_required');
  assert.equal(result.cells[0].ratio, 1.21);
  assert.deepEqual(result.cells[0].reviewMetrics, ['endToEnd', 'browserInputToReady', 'feedback']);
  assert.match(result.conclusion, /repeat/);
  const reordered = report(100);
  reordered.comparisonConditions = Object.fromEntries(Object.entries(reordered.comparisonConditions).reverse());
  assert.equal(compareReports(before, reordered).comparable, true);
  const changedMachine = report(200);
  changedMachine.comparisonConditions.environment.machineFingerprint = 'another-machine';
  assert.equal(compareReports(before, changedMachine).cells[0].status, 'not_comparable');
});

test('renderer and feedback degradation trigger independent review even with unchanged runner time', () => {
  for (const name of ['browserInputToReady', 'feedback']) {
    const before = report(100);
    const boundary = report(100);
    boundary.cells[0][name].p95Ms = 120;
    assert.equal(compareReports(before, boundary).cells[0].status, 'no_review_trigger');
    const after = report(100);
    after.cells[0][name].p95Ms = 121;
    const cell = compareReports(before, after).cells[0];
    assert.equal(cell.status, 'review_required');
    assert.equal(cell.comparable, true);
    assert.deepEqual(cell.reviewMetrics, [name]);
    assert.equal(cell.metrics[name].ratio, 1.21);
    assert.equal(cell.metrics[name].status, 'review_required');
    assert.equal(cell.metrics.endToEnd.status, 'no_review_trigger');
    assert.equal(cell.baselineP95Ms, 100);
    assert.equal(cell.candidateP95Ms, 100);
    assert.equal(cell.ratio, 1);
  }
});

test('missing and low-sample metrics remain explicit and cannot produce an all-metrics-clear verdict', () => {
  const before = report(100);
  const after = report(100);
  delete after.cells[0].feedback;
  after.cells[0].browserInputToReady = { n: 19, p95Ms: null };
  const cell = compareReports(before, after).cells[0];
  assert.equal(cell.status, 'not_comparable');
  assert.equal(cell.comparable, false);
  assert.equal(cell.metrics.endToEnd.status, 'no_review_trigger');
  assert.equal(cell.ratio, 1);
  assert.equal(cell.metrics.browserInputToReady.candidateN, 19);
  assert.equal(cell.metrics.browserInputToReady.candidateP95Ms, null);
  assert.equal(cell.metrics.browserInputToReady.ratio, null);
  assert.match(cell.metrics.browserInputToReady.reason, /fewer than 20/);
  assert.equal(cell.metrics.feedback.candidateN, null);
  assert.equal(cell.metrics.feedback.status, 'not_comparable');
  assert.match(cell.metrics.feedback.reason, /Candidate metric is missing/);
  after.cells[0].endToEnd.p95Ms = 121;
  const degraded = compareReports(before, after).cells[0];
  assert.equal(degraded.status, 'review_required');
  assert.equal(degraded.comparable, false);
  assert.equal(degraded.metrics.feedback.status, 'not_comparable');
});

test('legacy E2E-only reports preserve their E2E fields without claiming complete metric coverage', () => {
  const before = report(100);
  const after = report(121);
  for (const value of [before, after]) {
    delete value.cells[0].browserInputToReady;
    delete value.cells[0].feedback;
  }
  const cell = compareReports(before, after).cells[0];
  assert.equal(cell.ratio, 1.21);
  assert.equal(cell.status, 'review_required');
  assert.equal(cell.comparable, false);
  assert.equal(cell.metrics.endToEnd.comparable, true);
  assert.match(cell.metrics.feedback.reason, /Baseline metric is missing/);
});

test('comparison rejects missing conditions and nonfinite/missing p95 instead of treating them as comparable', () => {
  const bare = { status: 'passed', cells: [] };
  assert.throws(() => compareReports(bare, bare), /conditions/);
  for (const invalid of [undefined, NaN, Infinity, -1]) {
    const candidate = report(invalid);
    assert.throws(() => compareReports(report(100), candidate), /p95/);
  }
  const invalidConditions = report(100);
  invalidConditions.comparisonConditions.environment = null;
  assert.throws(() => compareReports(report(100), invalidConditions), /conditions/);
  const unknown = report(null);
  unknown.cells[0].endToEnd.n = 19;
  assert.equal(compareReports(report(100), unknown).cells[0].status, 'not_comparable');
  const invalidN = report(100);
  invalidN.cells[0].endToEnd.n = 19;
  assert.throws(() => compareReports(report(100), invalidN), /sample count/);
  for (const name of ['browserInputToReady', 'feedback']) {
    const invalidMetric = report(100);
    invalidMetric.cells[0][name].p95Ms = NaN;
    assert.throws(() => compareReports(report(100), invalidMetric), new RegExp(name));
    invalidMetric.cells[0][name] = { n: 19, p95Ms: 100 };
    assert.throws(() => compareReports(report(100), invalidMetric), /sample count/);
  }
});

test('comparison CLI preserves JSON stdout and describes review and unavailable metrics on stderr', t => {
  const directory = mkdtempSync(join(tmpdir(), 'copperbench-performance-comparison-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const baselinePath = join(directory, 'baseline.json');
  const candidatePath = join(directory, 'candidate.json');
  const before = report(100);
  const after = report(100);
  after.cells[0].feedback.p95Ms = 121;
  after.cells[0].browserInputToReady = { n: 19, p95Ms: null };
  writeFileSync(baselinePath, JSON.stringify(before), 'utf8');
  writeFileSync(candidatePath, JSON.stringify(after), 'utf8');
  const cli = fileURLToPath(new URL('../performance/compare.mjs', import.meta.url));
  const result = spawnSync(process.execPath, [cli, baselinePath, candidatePath], { encoding: 'utf8', timeout: 30000 });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(result.stdout).cells[0].status, 'review_required');
  assert.match(result.stderr, /feedback: review_required/);
  assert.match(result.stderr, /browserInputToReady: not_comparable; Candidate has fewer than 20/);
  assert.match(result.stderr, /endToEnd: no_review_trigger/);
});

test('sanitized failures retain actionable locator/count details without local paths or credentials', () => {
  const message = sanitizeFailure('Expected locator("relationship-canvas") count 1; received 0 at C:\\repo\\ui-shell\\test.ts. token="fixture-value" Bearer fixture-credential', ['C:\\repo']);
  assert.match(message, /relationship-canvas/);
  assert.match(message, /received 0/);
  assert.match(message, /<local-path>/);
  assert.doesNotMatch(message, /C:\\repo|fixture-value|fixture-credential/);
});
