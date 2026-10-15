import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { performance } from 'node:perf_hooks';
import { fileIdentity } from '../evidence/source-identity.mjs';
import { statistics } from './statistics.mjs';

const ROOT = fileURLToPath(new URL('../../', import.meta.url));
const VERDICTS = ['passed', 'failed', 'unverified', 'timeout', 'interrupted'];
const within = (root, path) => {
  const difference = relative(root, path);
  return difference && difference !== '..' && !difference.startsWith(`..${sep}`) && !isAbsolute(difference);
};
const text = value => typeof value === 'string' && value.trim().length > 0;

export function readCandidateBindings(identityFile) {
  const identity = JSON.parse(readFileSync(identityFile, 'utf8').replace(/^\uFEFF/, ''));
  for (const path of ['copperbench.exe', 'lib/copperbench.jar']) {
    const expected = identity.files?.find(entry => entry.path === path);
    const actual = fileIdentity(resolve(identity.productRoot, path));
    if (!expected || expected.sha256 !== actual.sha256) throw new Error(`Candidate bytes changed: ${path}`);
  }
  return { archiveSha256: identity.archiveSha256, sourceIdentity: identity.sourceIdentity,
    applicationSha256: identity.files.find(entry => entry.path === 'lib/copperbench.jar').sha256,
    executableSha256: identity.files.find(entry => entry.path === 'copperbench.exe').sha256,
    productRoot: identity.productRoot,
    scope: 'EXE and application JAR checked against the frozen identity; archive hash is the original freeze receipt.' };
}

/** Passive recorder: no controller, screenshot interpretation, DOM access or product mutation. */
export class NativeObservationRecorder {
  constructor({ output, metadata, repository = ROOT, maxAttemptsPerCell = 25, clock = () => performance.now() }) {
    const path = resolve(repository, output);
    if (!within(resolve(repository, 'output'), path)) throw new Error('Use a new directory under repository output/.');
    if (existsSync(path)) throw new Error('Existing native observations cannot be overwritten.');
    if (!metadata?.candidate?.applicationSha256 || !metadata?.sample || !metadata?.environment)
      throw new Error('Candidate, actual sample and environment metadata are required.');
    if (!Number.isInteger(maxAttemptsPerCell) || maxAttemptsPerCell < 1 || maxAttemptsPerCell > 25)
      throw new Error('Native observation attempts must be bounded between 1 and 25 per cell.');
    this.output = path;
    this.metadata = structuredClone(metadata);
    this.maxAttemptsPerCell = maxAttemptsPerCell;
    this.clock = clock;
    this.attempts = [];
    this.memory = [];
    this.active = null;
    mkdirSync(dirname(path), { recursive: true });
    mkdirSync(path);
    this.flush();
  }

  begin({ cellId, operation, beforeObservation, startupDispatch = false }) {
    if (this.active) throw new Error('Finish and review the current observation before starting another action.');
    if (!text(cellId) || !text(operation)) throw new Error('A cell ID and operation definition are required.');
    if (!startupDispatch && (!beforeObservation?.screenshotId || !beforeObservation?.windowId))
      throw new Error('An action requires the already inspected current window/screenshot identity.');
    if (this.attempts.filter(attempt => attempt.cellId === cellId).length >= this.maxAttemptsPerCell)
      throw new Error('The cell reached its retained attempt bound.');
    const attempt = { index: this.attempts.length + 1, cellId, operation,
      scope: startupDispatch ? 'controller-startup-dispatch-to-observation' : 'controller-action-dispatch-to-observation',
      beforeObservation: beforeObservation ?? null, status: 'running', correctness: 'unverified',
      dispatchToObservationMs: null, rendererMs: null, coreMs: null, buildMs: null };
    this.attempts.push(attempt);
    this.active = { attempt, started: this.clock() };
    this.flush();
    return attempt.index;
  }

  observed(index, afterObservation) {
    const active = this.active;
    if (!active || active.attempt.index !== index) throw new Error('Unknown or no longer active observation.');
    const elapsed = this.clock() - active.started;
    if (!Number.isFinite(elapsed) || elapsed < 0) throw new Error('Invalid monotonic observation duration.');
    if (!afterObservation?.screenshotId || !afterObservation?.windowId)
      throw new Error('Refresh must produce an actual after-observation window and screenshot identity.');
    Object.assign(active.attempt, { dispatchToObservationMs: elapsed, afterObservation,
      status: 'awaiting_visual_review' });
    this.flush();
  }

  judge(index, { status, reason, evidencePaths = [] }) {
    const active = this.active;
    if (!active || active.attempt.index !== index) throw new Error('Unknown or no longer active observation.');
    if (!VERDICTS.includes(status) || !text(reason)) throw new Error('Record an explicit human review result and reason.');
    if (status === 'passed' && (!active.attempt.afterObservation || !evidencePaths.length))
      throw new Error('A passing review needs the refreshed observation and retained visual evidence.');
    if (status !== 'passed' && active.attempt.dispatchToObservationMs === null)
      active.attempt.elapsedIncludingFailureMs = this.clock() - active.started;
    Object.assign(active.attempt, { status, correctness: status, reason, evidencePaths });
    this.active = null;
    this.flush();
  }

  recordMemory({ pid, processStartedAt, executable, phase, workingSetBytes, privateBytes }) {
    if (!Number.isInteger(pid) || pid < 1 || !text(processStartedAt) || !text(executable) || !text(phase)
      || [workingSetBytes, privateBytes].some(value => !Number.isSafeInteger(value) || value < 0))
      throw new Error('Memory observation needs a confirmed process identity and finite byte counts.');
    this.memory.push({ pid, processStartedAt, executable, phase, workingSetBytes, privateBytes,
      recorderArrivalMonotonicMs: this.clock(), scope: 'Manually sampled Windows process values; not process-tree RSS or a proven peak.' });
    this.flush();
  }

  report() {
    const cellIds = [...new Set(this.attempts.map(attempt => attempt.cellId))];
    return { schemaVersion: '1.0', kind: 'native-manual-observation', metadata: this.metadata,
      status: this.active ? 'incomplete' : this.attempts.length && this.attempts.every(attempt => attempt.status === 'passed')
        ? 'observed_with_explicit_scope' : 'contains_unverified_or_failed_observations',
      boundaries: { timing: 'One Node process performance.now(): before parent dispatch through refreshed snapshot receipt. Includes controller transport and snapshot overhead; human review occurs afterwards.',
        renderer: 'unknown; no DOM or renderer instrumentation', core: 'unknown', build: 'unknown',
        comparison: 'Not directly comparable with browser renderer timing or its 200/500 ms budgets.',
        memory: 'Working set/private-byte point samples of explicitly identified processes; no peak or unified native limit.' },
      cells: cellIds.map(cellId => {
        const attempts = this.attempts.filter(attempt => attempt.cellId === cellId);
        const values = attempts.filter(attempt => attempt.status === 'passed').map(attempt => attempt.dispatchToObservationMs);
        return { cellId, attemptedCount: attempts.length, validCount: values.length,
          failureCount: attempts.filter(attempt => attempt.status === 'failed').length,
          timeoutCount: attempts.filter(attempt => attempt.status === 'timeout').length,
          unverifiedCount: attempts.filter(attempt => !['passed', 'failed', 'timeout'].includes(attempt.status)).length,
          rawDispatchToObservationMs: values, dispatchToObservation: statistics(values),
          resultMeaning: 'Human-reviewed observation, not native UI latency certification; low-n p95 remains null.' };
      }), attempts: this.attempts, memoryObservations: this.memory };
  }

  flush() {
    writeFileSync(resolve(this.output, 'report.json'), `${JSON.stringify(this.report(), null, 2)}\n`, 'utf8');
  }
}
