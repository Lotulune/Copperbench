import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { digest, SOURCE_PATH_FILTER_VERSION } from '../evidence/source-identity.mjs';
import { inspectTaskPlan, PLAN_SCHEMA, RESULTS_SCHEMA, summarizeTaskEvaluation,
  taskEvaluationIdentity } from '../evidence/task-evaluation.mjs';

// Every byte and outcome in this suite is synthetic. Nothing here is participant evidence.
function fixture(t, { cohort = 'novice', slots = ['slot-01'], tasks = ['task-one'] } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'copperbench-task-evaluation-fixture-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const write = (path, contents) => {
    const bytes = Buffer.from(typeof contents === 'string' ? contents : JSON.stringify(contents));
    mkdirSync(dirname(join(root, path)), { recursive: true });
    writeFileSync(join(root, path), bytes);
    return { path, sha256: digest(bytes) };
  };
  const files = [{ path: 'src/Fixture.java', kind: 'file', mode: '100644', size: 7, sha256: digest('fixture') }];
  const manifest = { schemaVersion: '1.0', pathFilterVersion: SOURCE_PATH_FILTER_VERSION,
    commit: '1'.repeat(40), dirty: false, fileCount: files.length,
    sourceSha256: digest(JSON.stringify(files)), dirtyDeltaSha256: digest('[]'), files, dirtyFiles: [] };
  const sourceReference = write('source.json', manifest);
  const packageBytes = 'SYNTHETIC package bytes; not a product artifact';
  const artifact = { ...write('fixture-package.bin', packageBytes), size: Buffer.byteLength(packageBytes) };
  const action = { ...write('actions.json', { synthetic: true, action: 'fixture only' }), kind: 'actions' };
  const outcome = { ...write('outcome.txt', 'SYNTHETIC outcome; not an observed product result'), kind: 'outcome' };
  const plan = { schemaVersion: PLAN_SCHEMA, studyId: 'synthetic-study', cohort,
    state: 'preregistered', dataKind: 'fixture', registeredAt: '2026-10-16T00:00:00Z',
    product: { version: 'fixture-version', source: { commit: manifest.commit,
      sourceSha256: manifest.sourceSha256, dirtyDeltaSha256: manifest.dirtyDeltaSha256,
      pathFilterVersion: SOURCE_PATH_FILTER_VERSION, manifest: sourceReference }, artifact },
    environment: 'Synthetic OS, hardware, warm cache and empty fixture workspace; never execute a product.',
    model: cohort === 'novice' ? null : { id: 'fixture-model', version: 'fixture-v1', settings: 'synthetic deterministic fixture' },
    prompt: 'Synthetic prompt only. Do not contact people or run a product.',
    permissions: { allowed: ['Read synthetic fixture bytes'], prohibited: ['Execute a product'] },
    rules: { timeBudgetSeconds: 60, tokenBudget: cohort === 'novice' ? null : 1000,
      maxAttempts: 2, retryPolicy: 'One recorded retry; never replace the first failure.',
      allowIntervention: false, interventionPolicy: 'No assistance.' },
    tasks: tasks.map(id => ({ id, description: 'Synthetic task only', successCriteria: ['Synthetic assertion'],
      requiredEvidence: ['actions', 'outcome'], materials: [] })), subjectSlots: slots };
  const attempt = (overrides = {}) => ({ subjectSlot: slots[0], taskId: tasks[0], attempt: 1,
    startedAt: '2026-10-16T00:01:00Z', endedAt: '2026-10-16T00:01:10Z', outcome: 'success',
    failureCause: null, identity: taskEvaluationIdentity(plan), usageTokens: cohort === 'novice' ? null : 100,
    interventions: [], evidence: structuredClone([action, outcome]), ...overrides });
  const results = (attempts, overrides = {}) => ({ schemaVersion: RESULTS_SCHEMA,
    studyId: plan.studyId, cohort: plan.cohort, dataKind: plan.dataKind, planSha256: digest(JSON.stringify(plan)),
    closedAt: '2026-10-16T01:00:00Z', attempts, ...overrides });
  const summarize = records => summarizeTaskEvaluation(plan, records, { root });
  return { root, plan, manifest, write, action, outcome, attempt, results, summarize };
}

const failure = (category = 'product') => ({ category, details: 'Synthetic failure retained for regression coverage.' });

test('both shipped templates explicitly remain planned with no observations or real participants', t => {
  for (const name of ['novice', 'agent']) {
    const plan = JSON.parse(readFileSync(new URL(`../../docs/testing/task-evaluation/${name}-plan.template.json`, import.meta.url), 'utf8'));
    const report = summarizeTaskEvaluation(plan, null, { root: tmpdir() });
    assert.deepEqual(report.validationIssues, []);
    assert.equal(report.status, 'planned');
    assert.equal(report.registrationReady, false);
    assert.equal(report.recordsComplete, false);
    assert.equal(report.researchComplete, false);
    assert.equal(report.summary.subjects.realParticipantSlotsStarted, 0);
    assert.deepEqual(report.summary.eventualSuccess, { numerator: 0, denominator: 2, rate: 0 });
    assert.ok(report.registrationIssues.length > 0);
  }
});

test('complete synthetic records are fixture_only and never real study completion', t => {
  const f = fixture(t);
  assert.equal(inspectTaskPlan(f.plan, { root: f.root }).ready, true);
  const report = f.summarize(f.results([f.attempt()]));
  assert.deepEqual(report.validationIssues, []);
  assert.equal(report.status, 'fixture_only');
  assert.equal(report.recordsComplete, true);
  assert.equal(report.researchComplete, false);
  assert.equal(report.summary.subjects.realParticipantSlotsStarted, 0);
  assert.equal(report.summary.subjects.fixtureSlotsStarted, 1);
  assert.deepEqual(report.summary.firstAttemptSuccess, { numerator: 1, denominator: 1, rate: 1 });
  assert.deepEqual(report.summary.terminalValidAssignmentTime, { n: 1, totalSeconds: 10, medianSeconds: 10, maxSeconds: 10 });
  assert.equal(Object.hasOwn(report, 'passed'), false);
});

test('a successful retry retains failures, separates first/eventual success and uses assignment/attempt denominators', t => {
  const f = fixture(t, { slots: ['slot-01', 'slot-02'] });
  const report = f.summarize(f.results([
    f.attempt({ outcome: 'failed', failureCause: failure('environment') }),
    f.attempt({ attempt: 2, startedAt: '2026-10-16T00:01:15Z', endedAt: '2026-10-16T00:01:25Z' }),
    f.attempt({ subjectSlot: 'slot-02', startedAt: '2026-10-16T00:02:00Z', endedAt: '2026-10-16T00:02:20Z',
      outcome: 'failed', failureCause: failure('product') }),
  ]));
  assert.equal(report.recordsComplete, true);
  assert.deepEqual(report.summary.firstAttemptSuccess, { numerator: 0, denominator: 2, rate: 0 });
  assert.deepEqual(report.summary.eventualSuccess, { numerator: 1, denominator: 2, rate: 0.5 });
  assert.deepEqual(report.summary.retryAssignments, { numerator: 1, denominator: 2, rate: 0.5 });
  assert.equal(report.summary.retryCount, 1);
  assert.equal(report.summary.attempts.records, 3);
  assert.equal(report.summary.attempts.failed, 2);
  assert.equal(report.records[0].reported.failureCause.category, 'environment');
  assert.deepEqual(report.summary.failuresByCause.map(row => [row.category, row.numerator, row.denominator, row.allAttemptDenominator]),
    [['environment', 1, 2, 3], ['product', 1, 2, 3]]);
  assert.equal(report.summary.terminalValidAssignmentTime.totalSeconds, 45); // Includes the five-second retry gap.
  assert.equal(report.summary.terminalValidAttemptTime.totalSeconds, 40);
  assert.deepEqual(report.summary.subjects.allTasksEventualSuccess, { numerator: 1, denominator: 2, rate: 0.5 });
});

test('missing tasks/slots and empty or absent results never shrink the fixed denominator', t => {
  const f = fixture(t, { slots: ['slot-01', 'slot-02'], tasks: ['task-one', 'task-two'] });
  for (const input of [null, f.results([])]) {
    const report = f.summarize(input);
    assert.equal(report.status, 'not_run');
    assert.equal(report.recordsComplete, false);
    assert.deepEqual(report.summary.eventualSuccess, { numerator: 0, denominator: 4, rate: 0 });
    assert.equal(report.summary.taskAssignments.notRun, 4);
  }
  const partial = f.summarize(f.results([f.attempt()]));
  assert.equal(partial.status, 'incomplete');
  assert.equal(partial.summary.taskAssignments.notRun, 3);
  assert.deepEqual(partial.summary.eventualSuccess, { numerator: 1, denominator: 4, rate: 0.25 });
  assert.equal(partial.summary.subjects.completed, 0);
});

test('unfinished and unclosed runs remain incomplete even with a prior successful record', t => {
  const f = fixture(t);
  const running = f.summarize(f.results([f.attempt({ outcome: 'running', endedAt: null, evidence: [] })], { closedAt: null }));
  assert.equal(running.status, 'incomplete');
  assert.equal(running.summary.attempts.unfinished, 1);
  assert.equal(running.summary.eventualSuccess.numerator, 0);
  assert.equal(running.summary.terminalValidAttemptTime.n, 0);
  const unclosed = f.summarize(f.results([f.attempt()], { closedAt: null }));
  assert.equal(unclosed.status, 'incomplete');
  assert.equal(unclosed.recordsComplete, false);
});

test('duplicate, skipped, excessive and post-success attempts cannot count as success', t => {
  const f = fixture(t);
  const variants = [
    [f.attempt(), f.attempt()],
    [f.attempt({ attempt: 2 })],
    [f.attempt({ outcome: 'failed', failureCause: failure() }),
      f.attempt({ attempt: 2, outcome: 'failed', failureCause: failure(), startedAt: '2026-10-16T00:01:10Z', endedAt: '2026-10-16T00:01:20Z' }),
      f.attempt({ attempt: 3, startedAt: '2026-10-16T00:01:20Z', endedAt: '2026-10-16T00:01:30Z' })],
    [f.attempt(), f.attempt({ attempt: 2, startedAt: '2026-10-16T00:01:10Z', endedAt: '2026-10-16T00:01:20Z' })],
  ];
  for (const records of variants) {
    const report = f.summarize(f.results(records));
    assert.equal(report.status, 'incomplete');
    assert.equal(report.summary.eventualSuccess.numerator, 0);
    assert.equal(report.records.length, records.length);
    assert.ok(report.validationIssues.length > 0);
  }
  const duplicate = f.summarize(f.results(variants[0]));
  assert.equal(duplicate.summary.attempts.duplicateRecords, 1);
  assert.equal(duplicate.summary.attempts.valid, 0);
});

test('out-of-roster records remain visible without adding subjects or assignments', t => {
  const f = fixture(t);
  const report = f.summarize(f.results([f.attempt({ subjectSlot: 'unknown' }), f.attempt({ taskId: 'unknown' }),
    f.attempt({ subjectSlot: ['slot-01'] })]));
  assert.equal(report.summary.attempts.records, 3);
  assert.equal(report.summary.attempts.unassigned, 3);
  assert.equal(report.summary.subjects.started, 0);
  assert.equal(report.summary.eventualSuccess.denominator, 1);
  assert.equal(report.summary.eventualSuccess.numerator, 0);
  assert.equal(report.recordsComplete, false);
});

test('exact plan hash, cohort, data kind and every tested identity are mandatory', t => {
  const f = fixture(t, { cohort: 'autonomous_agent' });
  for (const field of ['version', 'sourceSha256', 'artifactSha256', 'model', 'promptSha256', 'permissionsSha256']) {
    const attempt = f.attempt();
    attempt.identity[field] = field === 'model' ? { ...attempt.identity.model, version: 'other' } : 'wrong';
    const report = f.summarize(f.results([attempt]));
    assert.equal(report.summary.eventualSuccess.numerator, 0, field);
    assert.match(report.validationIssues.join(' '), /identity differs/);
  }
  for (const changes of [{ planSha256: '0'.repeat(64) }, { cohort: 'novice' }, { dataKind: 'participant' }]) {
    const report = f.summarize(f.results([f.attempt()], changes));
    assert.equal(report.summary.eventualSuccess.numerator, 0);
    assert.equal(report.recordsComplete, false);
  }
});

test('no, malformed, missing, empty, changed, duplicate or escaping evidence cannot establish success', t => {
  const f = fixture(t);
  const empty = f.write('empty.txt', '');
  const variants = [[], {}, [f.action], [f.action, { ...f.outcome, path: 'missing.txt' }],
    [f.action, { ...empty, kind: 'outcome' }], [f.action, { ...f.outcome, sha256: '0'.repeat(64) }],
    [f.action, { ...f.outcome, path: '../outcome.txt' }], [f.action, { ...f.outcome, path: join(f.root, 'outcome.txt') }],
    [f.action, f.outcome, f.outcome]];
  for (const evidence of variants) {
    const report = f.summarize(f.results([f.attempt({ evidence })]));
    assert.equal(report.summary.eventualSuccess.numerator, 0);
    assert.equal(report.recordsComplete, false);
    assert.ok(report.validationIssues.length > 0);
  }
});

test('a parent symlink cannot smuggle evidence from outside the evidence root', t => {
  const f = fixture(t);
  const outside = mkdtempSync(join(tmpdir(), 'copperbench-task-evaluation-outside-'));
  t.after(() => rmSync(outside, { recursive: true, force: true }));
  writeFileSync(join(outside, 'outside.txt'), 'synthetic external bytes');
  try { symlinkSync(outside, join(f.root, 'escape'), process.platform === 'win32' ? 'junction' : 'dir'); }
  catch (error) {
    if (['EPERM', 'EACCES', 'ENOTSUP'].includes(error.code)) { t.skip('Local filesystem does not permit fixture symlinks.'); return; }
    throw error;
  }
  const report = f.summarize(f.results([f.attempt({ evidence: [f.action,
    { kind: 'outcome', path: 'escape/outside.txt', sha256: digest('synthetic external bytes') }] })]));
  assert.equal(report.summary.eventualSuccess.numerator, 0);
  assert.match(report.validationIssues.join(' '), /evidence root/);
});

test('source manifests and package bytes are checked, not merely their declared hash syntax', t => {
  const f = fixture(t);
  f.write('fixture-package.bin', 'changed synthetic package');
  let report = f.summarize(f.results([f.attempt()]));
  assert.equal(report.registrationReady, false);
  assert.equal(report.summary.eventualSuccess.numerator, 0);
  assert.match(report.registrationIssues.join(' '), /SHA-256 mismatch/);
  f.plan.product.artifact = { ...f.write('fixture-package.bin', 'restored fixture'), size: Buffer.byteLength('restored fixture') };
  const broken = { ...f.manifest, files: [] };
  f.plan.product.source.manifest = f.write('source.json', broken);
  report = f.summarize(f.results([f.attempt()]));
  assert.equal(report.registrationReady, false);
  assert.match(report.registrationIssues.join(' '), /nonempty unique source snapshot/);
  assert.equal(report.summary.eventualSuccess.numerator, 0);
});

test('registered task materials are byte-bound before observations', t => {
  const f = fixture(t);
  f.plan.tasks[0].materials = [{ ...f.write('failure-fixture.json', { synthetic: true, payload: 'fixed compiler error' }), kind: 'build-failure-fixture' }];
  assert.equal(inspectTaskPlan(f.plan, { root: f.root }).ready, true);
  f.write('failure-fixture.json', 'changed after registration');
  const report = f.summarize(f.results([f.attempt()]));
  assert.equal(report.registrationReady, false);
  assert.equal(report.summary.eventualSuccess.numerator, 0);
  assert.match(report.registrationIssues.join(' '), /SHA-256 mismatch/);
});

test('registration chronology, retry chronology and whole-assignment wall-clock budget are enforced', t => {
  const f = fixture(t);
  for (const overrides of [
    { startedAt: '2026-10-15T23:59:59Z' },
    { endedAt: '2026-10-16T00:00:59Z' },
    { endedAt: '2026-02-30T00:01:10Z' },
    { endedAt: '2026-10-16T00:02:01Z' },
  ]) {
    const report = f.summarize(f.results([f.attempt(overrides)]));
    assert.equal(report.summary.eventualSuccess.numerator, 0);
    assert.equal(report.recordsComplete, false);
  }
  const report = f.summarize(f.results([
    f.attempt({ outcome: 'failed', failureCause: failure() }),
    f.attempt({ attempt: 2, startedAt: '2026-10-16T00:02:00Z', endedAt: '2026-10-16T00:02:10Z' }),
  ]));
  assert.equal(report.summary.eventualSuccess.numerator, 0); // Two short attempts still exceed the total budget with their gap.
  assert.match(report.validationIssues.join(' '), /wall-clock budget/);
});

test('agent tokens accumulate across retries; unknown counts and changed models remain ineligible', t => {
  const f = fixture(t, { cohort: 'autonomous_agent' });
  const report = f.summarize(f.results([
    f.attempt({ usageTokens: 700, outcome: 'failed', failureCause: failure() }),
    f.attempt({ attempt: 2, usageTokens: 400, startedAt: '2026-10-16T00:01:15Z', endedAt: '2026-10-16T00:01:25Z' }),
  ]));
  assert.equal(report.summary.eventualSuccess.numerator, 0);
  assert.match(report.validationIssues.join(' '), /token budget/);
  const unknown = f.summarize(f.results([f.attempt({ usageTokens: null })]));
  assert.equal(unknown.summary.eventualSuccess.numerator, 0);
  f.plan.model.version = null;
  assert.equal(inspectTaskPlan(f.plan, { root: f.root }).ready, false);
});

test('interventions retain evidence/counts and assisted success is distinct from unassisted success', t => {
  const f = fixture(t);
  const event = { at: '2026-10-16T00:01:05Z', kind: 'hint', reason: 'Synthetic observer hint', evidence: [f.action] };
  let report = f.summarize(f.results([f.attempt({ interventions: [event] })]));
  assert.equal(report.summary.eventualSuccess.numerator, 0);
  assert.equal(report.summary.interventionEventsInRawRecords, 1);
  f.plan.rules.allowIntervention = true;
  f.plan.rules.interventionPolicy = 'One recorded fixture hint is permitted.';
  report = f.summarize(f.results([f.attempt({ interventions: [event] })]));
  assert.equal(report.recordsComplete, true);
  assert.equal(report.summary.firstAttemptSuccess.numerator, 1);
  assert.equal(report.summary.unassistedEventualSuccess.numerator, 0);
  assert.deepEqual(report.summary.intervenedAssignments, { numerator: 1, denominator: 1, rate: 1 });
  report = f.summarize(f.results([f.attempt({ interventions: [{ ...event, evidence: [] }] })]));
  assert.equal(report.summary.eventualSuccess.numerator, 0);
});

test('a later success cannot repair missing first-attempt evidence or hide a prohibited earlier intervention', t => {
  const f = fixture(t);
  const retry = () => f.attempt({ attempt: 2, startedAt: '2026-10-16T00:01:15Z', endedAt: '2026-10-16T00:01:25Z' });
  const event = { at: '2026-10-16T00:01:05Z', kind: 'hint', reason: 'Synthetic hint before retry', evidence: [f.action] };
  for (const first of [f.attempt({ outcome: 'failed', failureCause: failure(), evidence: [] }),
    f.attempt({ outcome: 'failed', failureCause: failure(), interventions: [event] })]) {
    const report = f.summarize(f.results([first, retry()]));
    assert.equal(report.summary.eventualSuccess.numerator, 0);
    assert.equal(report.summary.attempts.eligibleSuccess, 0);
    assert.equal(report.recordsComplete, false);
    assert.equal(report.records.length, 2);
  }
});

test('failed/timeout/abandoned attempts need causes and can complete records without any task success', t => {
  const f = fixture(t, { slots: ['slot-01', 'slot-02', 'slot-03'] });
  const report = f.summarize(f.results(['failed', 'timeout', 'abandoned'].map((outcome, index) =>
    f.attempt({ subjectSlot: f.plan.subjectSlots[index], outcome, failureCause: failure(index === 1 ? 'time_budget' : 'product') }))));
  assert.equal(report.recordsComplete, true);
  assert.equal(report.status, 'fixture_only');
  assert.deepEqual(report.summary.eventualSuccess, { numerator: 0, denominator: 3, rate: 0 });
  assert.equal(report.summary.attempts.failed, 1);
  assert.equal(report.summary.attempts.timeout, 1);
  assert.equal(report.summary.attempts.abandoned, 1);
  const missing = f.summarize(f.results([f.attempt({ outcome: 'failed', failureCause: null })]));
  assert.match(missing.validationIssues.join(' '), /failureCause/);
});

test('invalid empty/duplicate rosters have no inferred denominator and retain raw records', t => {
  for (const mutate of [plan => { plan.subjectSlots = []; }, plan => { plan.tasks = []; },
    plan => { plan.subjectSlots = ['slot-01', 'slot-01']; }, plan => { plan.tasks.push(structuredClone(plan.tasks[0])); },
    plan => { delete plan.studyId; }]) {
    const f = fixture(t);
    const attempt = f.attempt();
    mutate(f.plan);
    const report = f.summarize(f.results([attempt]));
    assert.equal(report.summary, null);
    assert.equal(report.recordsComplete, false);
    assert.equal(report.records.length, 1);
    assert.equal(report.records[0].acceptedOutcome, null);
  }
});

function tree(root, prefix = '') {
  return readdirSync(join(root, prefix), { withFileTypes: true }).flatMap(entry => {
    const path = join(prefix, entry.name);
    return entry.isDirectory() ? tree(root, path) : [[path, digest(readFileSync(join(root, path)))]];
  }).sort(([left], [right]) => left.localeCompare(right));
}

test('CLI uses exact raw plan bytes, supports help/planned inspection and leaves every input byte unchanged', t => {
  const f = fixture(t);
  const cli = fileURLToPath(new URL('../evidence/task-evaluation.mjs', import.meta.url));
  const run = args => spawnSync(process.execPath, [cli, ...args], { encoding: 'utf8', timeout: 30000 });
  const planPath = join(f.root, 'plan.json');
  const resultPath = join(f.root, 'results.json');
  const bytes = `${JSON.stringify(f.plan, null, 2)}\n`;
  writeFileSync(planPath, bytes, 'utf8');
  writeFileSync(resultPath, JSON.stringify(f.results([f.attempt()], { planSha256: digest(bytes) })), 'utf8');
  const before = tree(f.root);
  const help = run(['--help']);
  assert.equal(help.status, 0);
  assert.match(help.stdout, /Read-only/);
  const complete = run([planPath, resultPath, '--root', f.root]);
  assert.equal(complete.status, 0, complete.stderr);
  assert.equal(JSON.parse(complete.stdout).status, 'fixture_only');
  assert.equal(JSON.parse(complete.stdout).planSha256, digest(bytes));
  const absent = run([planPath, '--root', f.root]);
  assert.equal(absent.status, 2);
  assert.equal(JSON.parse(absent.stdout).status, 'not_run');
  assert.deepEqual(tree(f.root), before);
  const draft = JSON.parse(readFileSync(new URL('../../docs/testing/task-evaluation/novice-plan.template.json', import.meta.url), 'utf8'));
  writeFileSync(planPath, JSON.stringify(draft), 'utf8');
  const planned = run([planPath, '--root', f.root]);
  assert.equal(planned.status, 0, planned.stderr);
  assert.equal(JSON.parse(planned.stdout).status, 'planned');
  // Retaining the old result after a plan edit is rejected rather than reinterpreted.
  const stale = run([planPath, resultPath, '--root', f.root]);
  assert.equal(stale.status, 1);
  assert.equal(JSON.parse(stale.stdout).summary.eventualSuccess.numerator, 0);
});
