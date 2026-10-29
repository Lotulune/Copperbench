import { readFileSync } from 'node:fs';
import { compareReports } from './statistics.mjs';

try {
  const arguments_ = process.argv.slice(2);
  const partial = arguments_.includes('--partial');
  const paths = arguments_.filter(argument => argument !== '--partial');
  if (paths.length !== 2 || paths.some(path => path.startsWith('--')))
    throw new Error('Usage: node scripts/performance/compare.mjs [--partial] BASELINE-REPORT CANDIDATE-REPORT');
  const [baseline, candidate] = paths.map(path => JSON.parse(readFileSync(path, 'utf8')));
  const comparison = compareReports(baseline, candidate, { partial });
  console.error(`coverage (${comparison.comparisonMode}): ${comparison.coverageComplete ? 'complete' : 'incomplete'}; baseline-only: ${comparison.baselineOnlyCells.join(', ') || 'none'}; candidate-only: ${comparison.candidateOnlyCells.join(', ') || 'none'}`);
  for (const cell of comparison.cells) for (const [name, metric] of Object.entries(cell.metrics)) {
    const detail = metric.comparable
      ? `p95 ${metric.baselineP95Ms} -> ${metric.candidateP95Ms} ms; ratio ${metric.ratio.toFixed(3)}`
      : metric.reason;
    console.error(`${cell.id} ${name}: ${metric.status}; ${detail}`);
  }
  console.log(JSON.stringify(comparison, null, 2));
  if ((!partial && !comparison.coverageComplete) || !comparison.scope.sharedCells.length) process.exitCode = 1;
} catch (error) { console.error(`performance comparison: ${error.message}`); process.exitCode = 1; }
