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
const report = (p95, ids = ['large:graph-open']) => ({ status: 'passed', comparisonConditions: conditions(),
  cells: ids.map(id => ({ id, status: 'passed', endToEnd: { n: 20, p95Ms: p95 },
    browserInputToReady: { n: 20, p95Ms: p95 }, feedback: { n: 20, p95Ms: p95 } })) });

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

test('AUDIT-COVERAGE-01: losing a baseline cell cannot look like a complete comparison', () => {
  const before = report(100, ['small:navigation', 'large:graph-open']);
  const after = report(100, ['small:navigation']);
  const comparison = compareReports(before, after);
  assert.equal(comparison.comparisonMode, 'full');
  assert.equal(comparison.coverageComplete, false);
  assert.equal(comparison.comparable, false);
  assert.deepEqual(comparison.baselineOnlyCells, ['large:graph-open']);
  assert.deepEqual(comparison.candidateOnlyCells, []);
  assert.deepEqual(comparison.scope.sharedCells, ['small:navigation']);
  assert.match(comparison.reason, /coverage differs/);
  assert.equal(comparison.cells[0].status, 'no_review_trigger');
});

test('candidate-only cells are disclosed and full coverage is independent of cell ordering', () => {
  const before = report(100, ['small:navigation']);
  const comparison = compareReports(before, report(100, ['small:navigation', 'large:graph-open']));
  assert.equal(comparison.coverageComplete, false);
  assert.equal(comparison.comparable, false);
  assert.deepEqual(comparison.baselineOnlyCells, []);
  assert.deepEqual(comparison.candidateOnlyCells, ['large:graph-open']);
  assert.equal(comparison.cells[1].status, 'not_comparable');
  assert.match(comparison.cells[1].metrics.feedback.reason, /Baseline metric is missing/);
  const reordered = compareReports(report(100, ['small:navigation', 'large:graph-open']),
    report(100, ['large:graph-open', 'small:navigation']));
  assert.equal(reordered.coverageComplete, true);
  assert.equal(reordered.comparable, true);
  assert.deepEqual(reordered.baselineOnlyCells, []);
  assert.deepEqual(reordered.candidateOnlyCells, []);
});

test('explicit partial comparison retains both differences and shared-cell metric conclusions', () => {
  const before = report(100, ['small:navigation', 'large:graph-open']);
  const after = report(100, ['small:navigation', 'large:graph-search']);
  after.cells[0].feedback.p95Ms = 121;
  after.cells[0].browserInputToReady = { n: 19, p95Ms: null };
  const comparison = compareReports(before, after, { partial: true });
  assert.equal(comparison.comparisonMode, 'partial');
  assert.equal(comparison.coverageComplete, false);
  assert.equal(comparison.comparable, true);
  assert.deepEqual(comparison.baselineOnlyCells, ['large:graph-open']);
  assert.deepEqual(comparison.candidateOnlyCells, ['large:graph-search']);
  assert.deepEqual(comparison.scope.sharedCells, ['small:navigation']);
  assert.match(comparison.scope.description, /only to shared cells/);
  assert.equal(comparison.cells[0].status, 'review_required');
  assert.equal(comparison.cells[0].metrics.endToEnd.status, 'no_review_trigger');
  assert.equal(comparison.cells[0].metrics.browserInputToReady.status, 'not_comparable');
  assert.deepEqual(comparison.cells[0].reviewMetrics, ['feedback']);
  after.comparisonConditions.fixtures.small = 'different-fixture';
  const mismatch = compareReports(before, after, { partial: true });
  assert.equal(mismatch.comparable, false);
  assert.match(mismatch.reason, /conditions differ/);
});

test('completely disjoint nonempty reports have no comparable scope in either mode', () => {
  for (const partial of [false, true]) {
    const comparison = compareReports(report(100, ['small:navigation']), report(100, ['large:graph-open']), { partial });
    assert.equal(comparison.coverageComplete, false);
    assert.equal(comparison.comparable, false);
    assert.deepEqual(comparison.baselineOnlyCells, ['small:navigation']);
    assert.deepEqual(comparison.candidateOnlyCells, ['large:graph-open']);
    assert.deepEqual(comparison.scope.sharedCells, []);
    assert.match(comparison.reason, /no shared cells/);
    assert.equal(comparison.cells[0].status, 'not_comparable');
  }
});

test('comparison rejects empty, duplicate and invalid report cell IDs in both modes', () => {
  const invalidReports = [report(100, []), report(100, ['small:navigation', 'small:navigation']),
    ...['', null, 42, 'large:unknown', 'small:navigation:extra', 'small:navigation,large:graph-open']
      .map(id => ({ ...report(100), cells: [{ ...report(100).cells[0], id }] })),
    { ...report(100), cells: [null] }];
  for (const invalid of invalidReports) for (const partial of [false, true]) {
    assert.throws(() => compareReports(invalid, report(100), { partial }), /cell/i);
    assert.throws(() => compareReports(report(100), invalid, { partial }), /cell/i);
  }
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

test('comparison CLI returns failing full coverage with JSON and allows only an explicit partial scope', t => {
  const directory = mkdtempSync(join(tmpdir(), 'copperbench-performance-coverage-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const baselinePath = join(directory, 'baseline.json');
  const candidatePath = join(directory, 'candidate.json');
  const cli = fileURLToPath(new URL('../performance/compare.mjs', import.meta.url));
  writeFileSync(baselinePath, JSON.stringify(report(100, ['small:navigation', 'large:graph-open'])), 'utf8');
  writeFileSync(candidatePath, JSON.stringify(report(100, ['small:navigation'])), 'utf8');
  const full = spawnSync(process.execPath, [cli, baselinePath, candidatePath], { encoding: 'utf8', timeout: 30000 });
  assert.equal(full.status, 1, full.stderr);
  const comparison = JSON.parse(full.stdout);
  assert.equal(comparison.coverageComplete, false);
  assert.equal(comparison.comparable, false);
  assert.deepEqual(comparison.baselineOnlyCells, ['large:graph-open']);
  assert.match(full.stderr, /coverage \(full\): incomplete; baseline-only: large:graph-open/);
  const partial = spawnSync(process.execPath, [cli, '--partial', baselinePath, candidatePath], { encoding: 'utf8', timeout: 30000 });
  assert.equal(partial.status, 0, partial.stderr);
  const subset = JSON.parse(partial.stdout);
  assert.equal(subset.coverageComplete, false);
  assert.equal(subset.comparisonMode, 'partial');
  assert.deepEqual(subset.scope.sharedCells, ['small:navigation']);
  assert.match(subset.scope.description, /Explicit partial/);
  writeFileSync(candidatePath, JSON.stringify(report(100, ['large:graph-search'])), 'utf8');
  const disjoint = spawnSync(process.execPath, [cli, '--partial', baselinePath, candidatePath], { encoding: 'utf8', timeout: 30000 });
  assert.equal(disjoint.status, 1, disjoint.stderr);
  assert.deepEqual(JSON.parse(disjoint.stdout).scope.sharedCells, []);
  writeFileSync(candidatePath, JSON.stringify(report(100, [])), 'utf8');
  const empty = spawnSync(process.execPath, [cli, baselinePath, candidatePath], { encoding: 'utf8', timeout: 30000 });
  assert.equal(empty.status, 1);
  assert.equal(empty.stdout, '');
  assert.match(empty.stderr, /nonempty/);
});

test('sanitized failures retain actionable locator/count details without local paths or credentials', () => {
  const message = sanitizeFailure('Expected locator("relationship-canvas") count 1; received 0 at C:\\repo\\ui-shell\\test.ts. token="fixture-value" Bearer fixture-credential', ['C:\\repo']);
  assert.match(message, /relationship-canvas/);
  assert.match(message, /received 0/);
  assert.match(message, /<local-path>/);
  assert.doesNotMatch(message, /C:\\repo|fixture-value|fixture-credential/);
});

test('AUDIT-REDACTION-01: JSON and single-quoted sensitive keys both redact synthetic credentials', () => {
  for (const input of ['{"token":"fixture-not-a-real-credential"}', "{'token': 'fixture-not-a-real-credential'}"]) {
    const message = sanitizeFailure(input);
    assert.match(message, /token/);
    assert.match(message, /<redacted>/);
    assert.doesNotMatch(message, /fixture-not-a-real-credential/);
  }
});

test('parseable JSON redacts sensitive objects and nested arrays without losing diagnostic fields', () => {
  const credential = 'fixture-structured-credential';
  const input = JSON.stringify({ locator: 'relationship-canvas', expectedCount: 1, receivedCount: 0,
    token: { nested: [credential] }, details: [{ PASSWORD: credential, receivedCount: 0 },
      [ { api_key: credential, operation: 'graph-open' }, { authorization: `Bearer ${credential}` } ]],
    encodedDetail: JSON.stringify({ secret: credential, locator: 'relationship-canvas', receivedCount: 0 }) });
  const message = sanitizeFailure(input);
  assert.doesNotMatch(message, /fixture-structured-credential/);
  const parsed = JSON.parse(message);
  assert.equal(parsed.token, '<redacted>');
  assert.equal(parsed.details[0].PASSWORD, '<redacted>');
  assert.equal(parsed.details[1][0].api_key, '<redacted>');
  assert.equal(parsed.details[1][1].authorization, '<redacted>');
  assert.equal(parsed.details[1][0].operation, 'graph-open');
  assert.equal(parsed.expectedCount, 1);
  assert.equal(parsed.receivedCount, 0);
  assert.equal(JSON.parse(parsed.encodedDetail).receivedCount, 0);
});

test('text log fields consume quoted spaces, escaped quotes and whole sensitive containers', () => {
  const inputs = [
    String.raw`{'token': 'fixture-single\'quote-credential'}`,
    String.raw`{"password"="fixture-double\"quote-credential"}`,
    `authorization='Bearer fixture-authorization credential with spaces'`,
    `"Authorization": "Bearer fixture-header credential with spaces"`,
    `Authorization: Bearer fixture-bare-credential`,
    `api-key=fixture-api-credential; phase=graph-open`,
    `SECRET={'nested': ['fixture-object-credential', {'value': 'fixture-second-credential'}]}`,
    `token=["fixture-array-credential", {"value": "fixture-nested-credential"}]`,
    String.raw`Bearer "fixture-standalone\"quote-credential"`,
  ];
  for (const input of inputs) {
    const message = sanitizeFailure(`${input}; locator("relationship-canvas") expected count 1; received 0`);
    assert.doesNotMatch(message, /fixture-|quote-credential|credential with spaces/);
    assert.match(message, /<redacted>/);
    assert.match(message, /relationship-canvas/);
    assert.match(message, /expected count 1; received 0/);
  }
});

test('quoted diagnostic strings sanitize nested payloads through escaped log layers', () => {
  const detail = String.raw`locator("relationship-canvas") expected count 1; received 0; {'token': 'fixture-detail\'credential'}`;
  const payload = JSON.stringify({ detail, authorization: 'Bearer fixture-encoded credential with spaces' });
  for (const input of [`error detail=${JSON.stringify(detail)}; phase=graph-open`,
    `error payload=${JSON.stringify(payload)}; phase=graph-open`,
    JSON.stringify({ diagnostic: detail, payload })]) {
    const message = sanitizeFailure(input);
    assert.doesNotMatch(message, /fixture-detail|fixture-encoded|credential with spaces/);
    assert.match(message, /relationship-canvas/);
    assert.match(message, /expected count 1; received 0/);
  }
});

test('apostrophes and an unfinished diagnostic quote do not hide sensitive fields', () => {
  for (const input of ["Can't locate element; {'token': 'fixture-apostrophe-credential'}; received 0",
    'Error detail="locator expected count 1; token=\'fixture-unfinished-credential\'; received 0']) {
    const message = sanitizeFailure(input);
    assert.doesNotMatch(message, /fixture-apostrophe|fixture-unfinished/);
    assert.match(message, /received 0/);
  }
});

test('sanitization keeps the length bound and redacts decoded JSON and alternate path spellings', () => {
  const root = 'C:\\fixture-repository';
  const message = sanitizeFailure(JSON.stringify({ path: `${root}\\ui-shell\\test.ts`,
    detail: `locator("relationship-canvas") received 0 at ${root.replaceAll('\\', '/')}/test.ts` }), [root]);
  assert.doesNotMatch(message, /fixture-repository/);
  assert.match(message, /<local-path>/);
  assert.match(message, /received 0/);
  assert.equal(sanitizeFailure('x'.repeat(5000)).length, 4000);
});
