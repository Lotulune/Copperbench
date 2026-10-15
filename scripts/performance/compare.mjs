import { readFileSync } from 'node:fs';
import { compareReports } from './statistics.mjs';

try {
  if (process.argv.length !== 4) throw new Error('Usage: node scripts/performance/compare.mjs BASELINE-REPORT CANDIDATE-REPORT');
  const [baseline, candidate] = process.argv.slice(2).map(path => JSON.parse(readFileSync(path, 'utf8')));
  const comparison = compareReports(baseline, candidate);
  for (const cell of comparison.cells) for (const [name, metric] of Object.entries(cell.metrics)) {
    const detail = metric.comparable
      ? `p95 ${metric.baselineP95Ms} -> ${metric.candidateP95Ms} ms; ratio ${metric.ratio.toFixed(3)}`
      : metric.reason;
    console.error(`${cell.id} ${name}: ${metric.status}; ${detail}`);
  }
  console.log(JSON.stringify(comparison, null, 2));
} catch (error) { console.error(`performance comparison: ${error.message}`); process.exitCode = 1; }
