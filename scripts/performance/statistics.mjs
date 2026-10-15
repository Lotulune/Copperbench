export const OPERATIONS = ['navigation', 'element-search', 'asset-search', 'editor-open', 'graph-open', 'graph-search'];
export const DEFAULT_CELLS = ['small:navigation', 'small:element-search', 'medium:navigation',
  'medium:element-search', 'medium:editor-open', 'large:element-search', 'large:asset-search', 'large:graph-open', 'large:graph-search'];

export function parseCells(value = DEFAULT_CELLS.join(',')) {
  const cells = value.split(',');
  if (!cells.length || new Set(cells).size !== cells.length) throw new Error('Performance cells must be nonempty and unique.');
  for (const cell of cells) {
    const [size, operation, extra] = cell.split(':');
    if (!['small', 'medium', 'large'].includes(size) || !OPERATIONS.includes(operation) || extra !== undefined)
      throw new Error(`Unknown performance cell: ${cell}`);
  }
  return cells;
}

export function statistics(values, minimumP95Samples = 20) {
  if (!Number.isInteger(minimumP95Samples) || minimumP95Samples < 1) throw new Error('The p95 minimum sample count must be positive.');
  if (!Array.isArray(values) || values.some(value => typeof value !== 'number' || !Number.isFinite(value) || value < 0))
    throw new Error('Durations must be finite, nonnegative numbers.');
  const sorted = [...values].sort((a, b) => a - b);
  const n = sorted.length;
  return { n, medianMs: n ? (sorted[Math.floor((n - 1) / 2)] + sorted[Math.floor(n / 2)]) / 2 : null,
    maxMs: n ? sorted[n - 1] : null, p95Ms: n >= minimumP95Samples ? sorted[Math.ceil(0.95 * n) - 1] : null,
    p95Algorithm: 'nearest-rank: sorted[ceil(0.95*n)-1]; valid samples only, failure denominator reported separately',
    p95WithheldReason: n < minimumP95Samples ? `Fewer than ${minimumP95Samples} valid repetitions.` : null };
}

export function summarizeCell(cell) {
  if (!Number.isInteger(cell.requiredValidRepetitions) || cell.requiredValidRepetitions < 20)
    throw new Error('A baseline cell requires at least 20 valid repetitions.');
  const attempts = cell.attempts ?? [];
  const valid = attempts.filter(attempt => attempt.status === 'passed');
  const failed = attempts.filter(attempt => attempt.status === 'failed').length;
  const timeouts = attempts.filter(attempt => attempt.status === 'timeout').length;
  const interrupted = attempts.filter(attempt => !['passed', 'failed', 'timeout'].includes(attempt.status)).length;
  const rawDurationMs = valid.map(attempt => attempt.endToEndMs);
  const rawBrowserInputToReadyMs = valid.map(attempt => attempt.browserInputToReadyMs).filter(value => value !== null);
  const rawFeedbackMs = valid.map(attempt => attempt.feedbackMs).filter(value => value !== null);
  const endToEnd = statistics(rawDurationMs);
  const browserInputToReady = statistics(rawBrowserInputToReadyMs);
  const feedback = statistics(rawFeedbackMs);
  return { id: cell.id, status: valid.length >= cell.requiredValidRepetitions && !failed && !timeouts && !interrupted
      && browserInputToReady.n >= cell.requiredValidRepetitions && feedback.n >= cell.requiredValidRepetitions
      && (cell.warmups ?? []).every(attempt => attempt.status === 'passed') ? 'passed' : 'failed',
    requiredValidRepetitions: cell.requiredValidRepetitions, attemptedCount: attempts.length, validCount: valid.length,
    failureCount: failed, timeoutCount: timeouts, interruptedCount: interrupted,
    warmupCount: (cell.warmups ?? []).length,
    warmupFailureCount: (cell.warmups ?? []).filter(attempt => attempt.status !== 'passed').length,
    rawDurationMs, rawBrowserInputToReadyMs, rawFeedbackMs,
    endToEnd, browserInputToReady, feedback,
    budgetObservation: { endToEndTargetMs: 500, feedbackTargetMs: 200,
      hardReleaseGate: false, endToEndExceeded: endToEnd.p95Ms === null ? null : endToEnd.p95Ms > 500,
      feedbackExceeded: feedback.p95Ms === null ? null : feedback.p95Ms > 200 } };
}

function canonicalConditions(conditions) {
  if (!conditions || typeof conditions !== 'object' || Array.isArray(conditions))
    throw new Error('Comparison conditions are missing or invalid.');
  const required = ['level', 'measurementVersion', 'environment', 'browser', 'viewport', 'locale', 'theme', 'cache', 'fixtures', 'warmups'];
  if (required.some(key => !Object.hasOwn(conditions, key))) throw new Error('Comparison conditions are incomplete.');
  if (['level', 'measurementVersion', 'locale', 'theme', 'cache'].some(key => typeof conditions[key] !== 'string' || !conditions[key])
    || ['environment', 'browser', 'viewport', 'fixtures'].some(key => !conditions[key] || typeof conditions[key] !== 'object'
      || Array.isArray(conditions[key]) || !Object.keys(conditions[key]).length)
    || !Number.isInteger(conditions.warmups) || conditions.warmups < 0)
    throw new Error('Comparison conditions have invalid field types.');
  function normalize(value) {
    if (value === undefined || typeof value === 'number' && !Number.isFinite(value))
      throw new Error('Comparison conditions contain unavailable or nonfinite values.');
    if (Array.isArray(value)) return value.map(normalize);
    if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, normalize(value[key])]));
    if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value;
    throw new Error('Comparison conditions contain an unsupported value.');
  }
  return JSON.stringify(normalize(conditions));
}

const COMPARISON_METRICS = ['endToEnd', 'browserInputToReady', 'feedback'];

export function compareReports(baseline, candidate) {
  if (!baseline || !candidate || !Array.isArray(baseline.cells) || !Array.isArray(candidate.cells))
    throw new Error('Expected two performance reports with cells.');
  const sameConditions = canonicalConditions(baseline.comparisonConditions) === canonicalConditions(candidate.comparisonConditions);
  for (const report of [baseline, candidate]) for (const cell of report.cells) for (const name of COMPARISON_METRICS) {
    const metric = cell[name];
    // Older reports may omit a metric; its absence must be visible in the result.
    if (metric === undefined || metric === null) continue;
    const p95 = metric.p95Ms;
    if (p95 !== null && (typeof p95 !== 'number' || !Number.isFinite(p95) || p95 < 0))
      throw new Error(`Invalid p95 for ${name} in cell ${cell.id ?? 'unknown'}.`);
    if (!Number.isInteger(metric.n) || metric.n < 0 || p95 !== null && metric.n < 20)
      throw new Error(`Invalid p95 sample count for ${name} in cell ${cell.id ?? 'unknown'}.`);
  }
  return { thresholdRatio: 1.2, metricNames: [...COMPARISON_METRICS],
    conclusion: 'Each metric is checked separately. Review triggers require a repeat under the same conditions; no automatic regression verdict. Missing or low-sample metrics cannot establish an all-metrics-clear result.',
    legacyCellFields: 'baselineP95Ms, candidateP95Ms and ratio retain their endToEnd meaning; metrics contain per-metric results.',
    comparable: sameConditions, reason: sameConditions ? null : 'Environment, fixture, measurement, or cache conditions differ.',
    cells: candidate.cells.map(cell => {
      const before = baseline.cells.find(entry => entry.id === cell.id);
      const metrics = Object.fromEntries(COMPARISON_METRICS.map(name => {
        const earlier = before?.[name];
        const later = cell[name];
        const reasons = [];
        if (!sameConditions) reasons.push('Environment, fixture, measurement, or cache conditions differ.');
        if (baseline.status !== 'passed' || candidate.status !== 'passed'
          || before?.status !== 'passed' || cell.status !== 'passed') reasons.push('Both reports and cells must have passed.');
        for (const [label, metric] of [['Baseline', earlier], ['Candidate', later]]) {
          if (metric === undefined || metric === null) reasons.push(`${label} metric is missing.`);
          else if (metric.n < 20) reasons.push(`${label} has fewer than 20 valid samples (n=${metric.n}).`);
          else if (metric.p95Ms === null) reasons.push(`${label} p95 is unavailable.`);
        }
        if (earlier?.p95Ms === 0) reasons.push('A zero baseline p95 does not define a relative ratio.');
        const comparable = reasons.length === 0;
        const ratio = comparable ? later.p95Ms / earlier.p95Ms : null;
        return [name, { comparable, baselineN: earlier?.n ?? null, candidateN: later?.n ?? null,
          baselineP95Ms: earlier?.p95Ms ?? null, candidateP95Ms: later?.p95Ms ?? null, ratio,
          status: !comparable ? 'not_comparable' : ratio > 1.2 ? 'review_required' : 'no_review_trigger',
          reason: reasons.length ? reasons.join(' ') : null }];
      }));
      const comparable = Object.values(metrics).every(metric => metric.comparable);
      const reviewMetrics = COMPARISON_METRICS.filter(name => metrics[name].status === 'review_required');
      return { id: cell.id, comparable, baselineP95Ms: metrics.endToEnd.baselineP95Ms,
        candidateP95Ms: metrics.endToEnd.candidateP95Ms, ratio: metrics.endToEnd.ratio, metrics, reviewMetrics,
        status: reviewMetrics.length ? 'review_required' : comparable ? 'no_review_trigger' : 'not_comparable' };
    }) };
}
