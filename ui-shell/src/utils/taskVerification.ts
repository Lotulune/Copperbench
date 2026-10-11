import type { LocalizedText, TaskSummary, TaskVerificationResult } from '../types/contract';

/** Old hosts provide counts but cannot establish a fresh current-input observation. */
export function legacyTaskVerification(task: TaskSummary): TaskVerificationResult {
  const v = task.verification;
  const count = (value: number | undefined, floor = 0) =>
    value !== undefined && Number.isSafeInteger(value) && value >= floor ? value : null;
  const minimumTests = count(v?.minimumTests, 1);
  let acceptanceExecuted = count(v?.acceptanceExecuted);
  let shortfall = minimumTests === null || acceptanceExecuted === null ? null : Math.max(0, minimumTests - acceptanceExecuted);
  const reasonCode = v?.reasonCode ?? 'VERIFICATION_UNAVAILABLE';
  let subreason: TaskVerificationResult['subreason'] = 'failed';
  if (task.resultObservation) subreason = 'unconfirmed';
  else if (task.state === 'running' || task.state === 'queued') subreason = 'running';
  else if (task.state === 'cancelled') subreason = 'cancelled';
  else if (reasonCode === 'GAMETEST_REPORT_MISSING') subreason = 'report_missing';
  else if (reasonCode === 'GAMETEST_REPORT_INVALID') subreason = 'report_invalid';
  else if (reasonCode === 'GAMETEST_REPORT_STALE') subreason = 'report_stale';
  else if (reasonCode === 'GAMETEST_FAILED' || (v?.failed ?? 0) > 0) subreason = 'business_failed';
  else if (reasonCode === 'GAMETEST_NO_TESTS' && acceptanceExecuted === 0) subreason = 'no_business_tests';
  else if (reasonCode === 'GAMETEST_NO_TESTS' && shortfall !== null && shortfall > 0) subreason = 'insufficient_business_tests';
  else if (task.state === 'succeeded' && v?.status === 'passed') subreason = 'passed_input_unconfirmed';
  else if (!v) subreason = 'unavailable';
  if (['running', 'cancelled', 'unconfirmed', 'report_missing', 'report_invalid', 'report_stale', 'unavailable'].includes(subreason)
      || v?.status === 'pending') { acceptanceExecuted = null; shortfall = null; }
  const coverage = { minimumTests, acceptanceExecuted, shortfall };
  const message: LocalizedText = { key: 'verification.' + subreason, fallback: reasonCode, args: { ...coverage, reasonCode } };
  return { resultVersion: '1', taskId: task.id, taskState: task.state, reasonCode, subreason, coverage, message };
}
