import { readFileSync, realpathSync, lstatSync } from 'node:fs';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { digest, isSourcePath, SOURCE_PATH_FILTER_VERSION } from './source-identity.mjs';

export const PLAN_SCHEMA = 'task-evaluation-plan-v1';
export const RESULTS_SCHEMA = 'task-evaluation-results-v1';
const HASH = /^[0-9a-f]{64}$/;
const COMMIT = /^[0-9a-f]{40}$/;
const ID = /^[a-z0-9][a-z0-9._-]*$/;
const OUTCOMES = ['success', 'failed', 'timeout', 'abandoned', 'running'];
const CAUSES = ['product', 'task_understanding', 'environment', 'tooling', 'time_budget',
  'permission', 'intervention', 'participant_withdrawal', 'other'];
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const text = value => typeof value === 'string' && value.trim().length > 0;
const id = value => typeof value === 'string' && ID.test(value);
const unique = values => new Set(values).size === values.length;
const strings = value => Array.isArray(value) && value.length > 0 && value.every(text) && unique(value);
const integer = value => Number.isSafeInteger(value) && value >= 0;
const time = value => typeof value === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{3})?Z$/.test(value)
  && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().replace('.000Z', 'Z') === value.replace('.000Z', 'Z')
  ? Date.parse(value) : null;
const canonical = value => Array.isArray(value) ? value.map(canonical) : object(value)
  ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
const equal = (left, right) => JSON.stringify(canonical(left)) === JSON.stringify(canonical(right));
const ratio = (numerator, denominator) => ({ numerator, denominator, rate: denominator ? numerator / denominator : null });
const need = (issues, condition, message) => { if (!condition) issues.push(message); };

function fileReference(reference, root, label, issues) {
  if (!object(reference) || !text(reference.path) || !HASH.test(reference.sha256)) {
    issues.push(`${label} requires a relative path and lowercase SHA-256.`);
    return null;
  }
  // Evidence is local to one explicit root; URLs, drive paths and traversal are not evidence.
  const parts = reference.path.split(/[\\/]/);
  if (isAbsolute(reference.path) || reference.path.includes(':') || reference.path.includes('\0')
    || parts.some(part => !part || part === '.' || part === '..')) {
    issues.push(`${label} path must stay within the evidence root.`);
    return null;
  }
  try {
    const base = realpathSync(root);
    const path = resolve(base, ...parts);
    const actual = realpathSync(path);
    const local = relative(base, actual);
    if (!local || local === '..' || local.startsWith(`..${sep}`) || isAbsolute(local)
      || !lstatSync(path).isFile()) throw new Error('not a regular file within the evidence root');
    const bytes = readFileSync(path);
    if (!bytes.length) throw new Error('empty files cannot establish evidence');
    if (digest(bytes) !== reference.sha256) throw new Error('SHA-256 mismatch');
    return { bytes, size: bytes.length, identityPath: process.platform === 'win32' ? actual.toLowerCase() : actual };
  } catch (error) {
    issues.push(`${label}: ${error.code ?? error.message}.`);
    return null;
  }
}

/** Values every attempt must report, in addition to the enclosing results' raw-plan hash. */
export function taskEvaluationIdentity(plan) {
  return {
    version: plan.product?.version ?? null,
    sourceSha256: plan.product?.source?.sourceSha256 ?? null,
    artifactSha256: plan.product?.artifact?.sha256 ?? null,
    model: plan.model ?? null,
    promptSha256: typeof plan.prompt === 'string' ? digest(plan.prompt) : null,
    permissionsSha256: object(plan.permissions) ? digest(JSON.stringify(canonical(plan.permissions))) : null,
  };
}

/** Inspect only local bytes; never executes Git, a product, an agent or participant contact. */
export function inspectTaskPlan(plan, { root = process.cwd() } = {}) {
  const issues = [];
  const registrationIssues = [];
  if (!object(plan)) return { issues: ['Plan must be an object.'], registrationIssues, ready: false };
  need(issues, plan.schemaVersion === PLAN_SCHEMA, `schemaVersion must be ${PLAN_SCHEMA}.`);
  need(issues, id(plan.studyId), 'studyId must be a nonempty lowercase ID.');
  need(issues, ['novice', 'autonomous_agent'].includes(plan.cohort), 'cohort must be novice or autonomous_agent.');
  need(issues, ['planned', 'preregistered'].includes(plan.state), 'state must be planned or preregistered.');
  need(issues, ['participant', 'fixture'].includes(plan.dataKind), 'dataKind must be participant or fixture.');
  need(issues, strings(plan.subjectSlots) && plan.subjectSlots.every(id),
    'subjectSlots must contain unique nonempty lowercase slot IDs.');
  need(issues, Array.isArray(plan.tasks) && plan.tasks.length > 0, 'At least one task is required.');
  if (Array.isArray(plan.tasks)) {
    need(issues, unique(plan.tasks.map(task => task?.id)), 'Task IDs must be unique.');
    plan.tasks.forEach((task, index) => {
      need(issues, object(task) && id(task.id) && text(task.description)
        && strings(task.successCriteria) && strings(task.requiredEvidence) && Array.isArray(task.materials),
      `tasks[${index}] requires id, description, unique successCriteria/requiredEvidence and a materials array.`);
      if (Array.isArray(task?.materials)) {
        need(registrationIssues, unique(task.materials.map(reference => reference?.path)), `tasks[${index}].materials paths must be unique.`);
        task.materials.forEach((reference, materialIndex) => {
          need(registrationIssues, text(reference?.kind), `tasks[${index}].materials[${materialIndex}] requires a kind.`);
          fileReference(reference, root, `tasks[${index}].materials[${materialIndex}]`, registrationIssues);
        });
      }
    });
  }
  const rules = plan.rules;
  need(issues, object(rules) && Number.isSafeInteger(rules.timeBudgetSeconds) && rules.timeBudgetSeconds > 0
    && Number.isSafeInteger(rules.maxAttempts) && rules.maxAttempts > 0 && text(rules.retryPolicy)
    && typeof rules.allowIntervention === 'boolean' && text(rules.interventionPolicy),
  'rules require positive timeBudgetSeconds/maxAttempts, retryPolicy, allowIntervention and interventionPolicy.');
  need(issues, object(plan.permissions) && strings(plan.permissions.allowed) && strings(plan.permissions.prohibited),
    'permissions must explicitly list allowed and prohibited actions.');
  need(issues, text(plan.prompt), 'prompt must contain the complete frozen briefing or agent prompt.');
  need(registrationIssues, text(plan.environment), 'environment must fix the OS, hardware, setup/cache and task starting state before registration.');
  if (plan.cohort === 'novice') {
    need(issues, plan.model === null && rules?.tokenBudget === null, 'Novice plans require model=null and tokenBudget=null.');
  } else if (plan.cohort === 'autonomous_agent') {
    need(registrationIssues, object(plan.model) && text(plan.model.id) && text(plan.model.version)
      && text(plan.model.settings), 'Agent model requires exact id, version and settings before registration.');
    need(issues, Number.isSafeInteger(rules?.tokenBudget) && rules.tokenBudget > 0,
      'Agent rules require a positive tokenBudget shared by all retries of one task assignment.');
  }
  need(registrationIssues, time(plan.registeredAt) !== null, 'registeredAt must be a UTC timestamp before observations.');
  need(registrationIssues, text(plan.product?.version), 'product.version is required before registration.');
  const source = plan.product?.source;
  need(registrationIssues, object(source) && COMMIT.test(source.commit) && HASH.test(source.sourceSha256)
    && HASH.test(source.dirtyDeltaSha256) && source.pathFilterVersion === SOURCE_PATH_FILTER_VERSION,
  'product.source requires a full commit, source/dirty SHA-256 and the supported path-filter version.');
  const manifest = fileReference(source?.manifest, root, 'Source manifest', registrationIssues);
  if (manifest) {
    try {
      const value = JSON.parse(manifest.bytes.toString('utf8'));
      need(registrationIssues, value.schemaVersion === '1.0' && value.commit === source?.commit
        && value.pathFilterVersion === source?.pathFilterVersion && value.sourceSha256 === source?.sourceSha256
        && value.dirtyDeltaSha256 === source?.dirtyDeltaSha256, 'Source manifest identity differs from the plan.');
      need(registrationIssues, Array.isArray(value.files) && value.files.length > 0
        && value.fileCount === value.files.length && unique(value.files.map(file => file?.path))
        && value.files.every(file => object(file) && text(file.path) && !isAbsolute(file.path)
          && !file.path.includes('\\') && !file.path.includes(':')
          && file.path.split('/').every(part => part && part !== '.' && part !== '..') && isSourcePath(file.path)
          && (file.kind === 'missing' ? file.size === null && file.sha256 === null
            : ['file', 'symlink'].includes(file.kind) && integer(file.size) && HASH.test(file.sha256)))
        && digest(JSON.stringify(value.files)) === value.sourceSha256,
      'Source manifest files/count/hash must describe a nonempty unique source snapshot.');
      need(registrationIssues, Array.isArray(value.dirtyFiles)
        && digest(JSON.stringify(value.dirtyFiles)) === value.dirtyDeltaSha256
        && value.dirty === (value.dirtyFiles.length > 0), 'Source manifest dirty-delta hash/flag is inconsistent.');
    } catch { registrationIssues.push('Source manifest must contain readable JSON.'); }
  }
  const artifact = fileReference(plan.product?.artifact, root, 'Product artifact', registrationIssues);
  need(registrationIssues, Number.isSafeInteger(plan.product?.artifact?.size) && plan.product.artifact.size > 0
    && (!artifact || artifact.size === plan.product.artifact.size), 'Product artifact size must match the nonempty local file.');
  return { issues, registrationIssues, ready: plan.state === 'preregistered' && !issues.length && !registrationIssues.length };
}

function durations(values) {
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return { n: values.length, totalSeconds: values.reduce((sum, value) => sum + value, 0),
    medianSeconds: sorted.length ? sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2 : null,
    maxSeconds: sorted.length ? sorted.at(-1) : null };
}

/** Summarize reported outcomes and byte-bound evidence, never a semantic study-pass verdict. */
export function summarizeTaskEvaluation(plan, results = null, { root = process.cwd(), planSha256 = digest(JSON.stringify(plan)) } = {}) {
  const inspected = inspectTaskPlan(plan, { root });
  const report = {
    schemaVersion: 'task-evaluation-summary-v1', studyId: plan?.studyId ?? null, cohort: plan?.cohort ?? null,
    dataKind: plan?.dataKind ?? null, planSha256, expectedAttemptIdentity: taskEvaluationIdentity(plan ?? {}),
    status: 'incomplete', registrationReady: inspected.ready, recordsComplete: false, researchComplete: false,
    validationIssues: [...inspected.issues], registrationIssues: inspected.registrationIssues,
    scope: 'Read-only validation of reported outcomes, chronology and local evidence hashes; no product or participant execution, no semantic success certification.',
  };
  // Invalid rosters cannot manufacture a smaller denominator.
  if (inspected.issues.length) return { ...report, summary: null, assignments: [],
    records: Array.isArray(results?.attempts) ? results.attempts.map((reported, index) => ({ index, reported,
      valid: false, acceptedOutcome: null, issues: ['Invalid plan; no denominator can be inferred.'] })) : [] };
  const assignments = plan.subjectSlots.flatMap(subjectSlot => plan.tasks.map(task => ({ subjectSlot, taskId: task.id,
    status: 'not_run', firstAttemptSuccess: false, eventualSuccess: false,
    unassistedFirstAttemptSuccess: false, unassistedEventualSuccess: false,
    retryCount: 0, interventionCount: 0, elapsedSeconds: null, recordIndexes: [], issues: [] })));
  const byKey = new Map(assignments.map(cell => [`${cell.subjectSlot}/${cell.taskId}`, cell]));
  const resultIssues = [];
  const hasResults = results !== null;
  if (hasResults) {
    need(resultIssues, object(results) && results.schemaVersion === RESULTS_SCHEMA, `Results schemaVersion must be ${RESULTS_SCHEMA}.`);
    need(resultIssues, results?.studyId === plan.studyId && results?.cohort === plan.cohort
      && results?.dataKind === plan.dataKind, 'Results studyId/cohort/dataKind differs from the plan.');
    need(resultIssues, HASH.test(planSha256) && results?.planSha256 === planSha256, 'Results planSha256 differs from the exact preregistered plan bytes.');
    need(resultIssues, Array.isArray(results?.attempts), 'Results attempts must be an array.');
    need(resultIssues, results?.closedAt === null || time(results?.closedAt) !== null, 'closedAt must be null or a UTC timestamp.');
    if (time(results?.closedAt) !== null) need(resultIssues, time(plan.registeredAt) !== null
      && time(results.closedAt) >= time(plan.registeredAt), 'closedAt cannot precede registration.');
    need(resultIssues, inspected.ready, 'Observed results require a complete preregistered plan.');
  }
  const raw = Array.isArray(results?.attempts) ? results.attempts : [];
  const globalInvalid = !!resultIssues.length || !inspected.ready;
  const seen = new Map();
  const duplicateIssues = new Map();
  const records = raw.map((attempt, index) => {
    const issues = [];
    const record = { index, reported: attempt, valid: false, acceptedOutcome: null, issues };
    if (!object(attempt)) { issues.push('Attempt must be an object.'); return record; }
    const cell = id(attempt.subjectSlot) && id(attempt.taskId)
      ? byKey.get(`${attempt.subjectSlot}/${attempt.taskId}`) : undefined;
    if (!cell) issues.push('Attempt is outside the preregistered subject-slot/task roster.');
    else cell.recordIndexes.push(index);
    need(issues, Number.isSafeInteger(attempt.attempt) && attempt.attempt > 0, 'attempt must be a positive integer.');
    if (cell && Number.isSafeInteger(attempt.attempt) && attempt.attempt > 0) {
      const key = `${attempt.subjectSlot}/${attempt.taskId}/${attempt.attempt}`;
      if (seen.has(key)) {
        issues.push('Duplicate attempt number.');
        recordsPlaceholder(seen.get(key)).push('Duplicate attempt number.');
      } else seen.set(key, index);
    }
    need(issues, OUTCOMES.includes(attempt.outcome), 'Unknown outcome.');
    const start = time(attempt.startedAt);
    const end = time(attempt.endedAt);
    need(issues, start !== null && start >= time(plan.registeredAt), 'Attempt must start at or after registration.');
    if (attempt.outcome === 'running') need(issues, attempt.endedAt === null, 'Running attempts require endedAt=null.');
    else need(issues, end !== null && start !== null && end >= start, 'Terminal attempts require ordered UTC start/end timestamps.');
    if (time(results?.closedAt) !== null) need(issues, end !== null && end <= time(results.closedAt), 'A closed study cannot contain a running or later attempt.');
    need(issues, equal(attempt.identity, report.expectedAttemptIdentity), 'Attempt version/source/artifact/model/prompt/permissions identity differs from the plan.');
    need(issues, plan.cohort === 'novice' ? attempt.usageTokens === null : integer(attempt.usageTokens),
      'usageTokens must be null for novices or a nonnegative measured token count for agents.');
    const failed = ['failed', 'timeout', 'abandoned'].includes(attempt.outcome);
    need(issues, failed ? object(attempt.failureCause) && CAUSES.includes(attempt.failureCause.category)
      && text(attempt.failureCause.details) : attempt.failureCause === null,
    'Non-success terminal outcomes require a categorized failureCause; success/running require null.');
    need(issues, Array.isArray(attempt.interventions), 'interventions must be an array, including when empty.');
    if (Array.isArray(attempt.interventions)) attempt.interventions.forEach((event, eventIndex) => {
      const at = time(event?.at);
      need(issues, object(event) && text(event.kind) && text(event.reason) && at !== null && start !== null
        && at >= start && (end === null || at <= end), `interventions[${eventIndex}] requires kind/reason and an in-attempt UTC timestamp.`);
      checkEvidence(event?.evidence, `interventions[${eventIndex}].evidence`, issues, true);
    });
    if (attempt.outcome === 'success' && attempt.interventions?.length && !plan.rules.allowIntervention)
      issues.push('Success with prohibited intervention is not an eligible success.');
    checkEvidence(attempt.evidence, 'evidence', issues, attempt.outcome !== 'running');
    if (attempt.outcome !== 'running' && cell) {
      const required = plan.tasks.find(task => task.id === cell.taskId).requiredEvidence;
      need(issues, Array.isArray(attempt.evidence)
        && required.every(kind => attempt.evidence.some(reference => reference?.kind === kind)),
        'Terminal attempt lacks a preregistered evidence kind.');
    }
    return record;
  });
  // Deferred duplicate marking avoids referring to the map result before initialization.
  function recordsPlaceholder(index) {
    const list = duplicateIssues.get(index) ?? [];
    duplicateIssues.set(index, list);
    return list;
  }
  function checkEvidence(evidence, label, issues, required) {
    need(issues, Array.isArray(evidence) && (!required || evidence.length > 0), `${label} must contain ${required ? 'at least one local evidence reference' : 'an array'}.`);
    if (!Array.isArray(evidence)) return;
    need(issues, unique(evidence.map(reference => reference?.path)), `${label} paths must be unique.`);
    const actualPaths = [];
    evidence.forEach((reference, referenceIndex) => {
      need(issues, text(reference?.kind), `${label}[${referenceIndex}] requires an evidence kind.`);
      const checked = fileReference(reference, root, `${label}[${referenceIndex}]`, issues);
      if (checked) actualPaths.push(checked.identityPath);
    });
    need(issues, unique(actualPaths), `${label} must reference distinct actual files, including aliases.`);
  }
  for (const [index, issues] of duplicateIssues) records[index].issues.push(...issues);
  for (const cell of assignments) {
    const attempts = cell.recordIndexes.map(index => records[index]).sort((a, b) => a.reported.attempt - b.reported.attempt);
    if (!attempts.length) continue;
    cell.status = 'incomplete';
    const numbers = attempts.map(record => record.reported.attempt);
    const uniqueNumbers = [...new Set(numbers.filter(number => integer(number) && number > 0))].sort((a, b) => a - b);
    cell.retryCount = uniqueNumbers.filter(number => number > 1).length;
    cell.interventionCount = attempts.reduce((sum, record) => sum + (Array.isArray(record.reported.interventions) ? record.reported.interventions.length : 0), 0);
    need(cell.issues, numbers.every((number, index) => number === index + 1), 'Attempt numbers must be contiguous from 1 with no duplicates.');
    need(cell.issues, attempts.length <= plan.rules.maxAttempts, 'Attempt count exceeds maxAttempts.');
    for (let index = 1; index < attempts.length; index++) {
      const previous = attempts[index - 1].reported;
      const current = attempts[index].reported;
      need(cell.issues, previous.outcome !== 'success' && previous.outcome !== 'running', 'Cannot retry a successful or unfinished attempt.');
      need(cell.issues, time(previous.endedAt) !== null && time(current.startedAt) !== null
        && time(current.startedAt) >= time(previous.endedAt), 'Retries must not overlap or reorder timestamps.');
    }
    const first = attempts[0].reported;
    const last = attempts.at(-1).reported;
    const start = time(first.startedAt);
    const end = time(last.endedAt);
    if (start !== null && end !== null && end >= start) cell.elapsedSeconds = (end - start) / 1000;
    if (attempts.some(record => record.reported.outcome === 'success')) {
      need(cell.issues, plan.rules.allowIntervention || cell.interventionCount === 0,
        'Success with prohibited intervention in any attempt of this task assignment is not eligible.');
      need(cell.issues, cell.elapsedSeconds !== null && cell.elapsedSeconds <= plan.rules.timeBudgetSeconds,
        'Success exceeds the task-assignment wall-clock budget, including retries and gaps.');
      if (plan.cohort === 'autonomous_agent') need(cell.issues,
        attempts.every(record => integer(record.reported.usageTokens))
          && attempts.reduce((sum, record) => sum + record.reported.usageTokens, 0) <= plan.rules.tokenBudget,
      'Success exceeds the task-assignment token budget, including retries.');
    }
    const eligible = !globalInvalid && !cell.issues.length && attempts.every(record => !record.issues.length);
    if (eligible && last.outcome !== 'running') {
      cell.firstAttemptSuccess = first.outcome === 'success';
      cell.eventualSuccess = last.outcome === 'success';
      cell.unassistedFirstAttemptSuccess = cell.firstAttemptSuccess && !first.interventions.length;
      cell.unassistedEventualSuccess = cell.eventualSuccess && cell.interventionCount === 0;
      cell.status = cell.eventualSuccess ? 'success' : 'failed';
    }
    for (const record of attempts) {
      record.valid = eligible;
      record.acceptedOutcome = record.valid ? record.reported.outcome : null;
    }
  }
  report.validationIssues.push(...resultIssues);
  for (const record of records) report.validationIssues.push(...record.issues.map(issue => `attempts[${record.index}]: ${issue}`));
  for (const cell of assignments) report.validationIssues.push(...cell.issues.map(issue => `${cell.subjectSlot}/${cell.taskId}: ${issue}`));
  const completed = assignments.filter(cell => ['success', 'failed'].includes(cell.status));
  const observed = assignments.filter(cell => cell.recordIndexes.length);
  const count = field => assignments.filter(cell => cell[field]).length;
  const subjectStarted = plan.subjectSlots.filter(id => observed.some(cell => cell.subjectSlot === id)).length;
  const subjectCompleted = plan.subjectSlots.filter(id => assignments.filter(cell => cell.subjectSlot === id)
    .every(cell => ['success', 'failed'].includes(cell.status))).length;
  const failureRecords = records.filter(record => ['failed', 'timeout', 'abandoned'].includes(record.reported?.outcome));
  const failureCategory = record => CAUSES.includes(record.reported.failureCause?.category)
    ? record.reported.failureCause.category : 'unclassified';
  const failuresByCause = [...new Set(failureRecords.map(failureCategory))]
    .sort().map(category => ({ category, ...ratio(failureRecords.filter(record => failureCategory(record) === category).length,
      failureRecords.length), allAttemptDenominator: records.length }));
  report.summary = {
    subjects: { planned: plan.subjectSlots.length, started: subjectStarted, completed: subjectCompleted,
      realParticipantSlotsStarted: plan.dataKind === 'participant' ? subjectStarted : 0,
      fixtureSlotsStarted: plan.dataKind === 'fixture' ? subjectStarted : 0,
      allTasksFirstAttemptSuccess: ratio(plan.subjectSlots.filter(id => assignments.filter(cell => cell.subjectSlot === id)
        .every(cell => cell.firstAttemptSuccess)).length, plan.subjectSlots.length),
      allTasksEventualSuccess: ratio(plan.subjectSlots.filter(id => assignments.filter(cell => cell.subjectSlot === id)
        .every(cell => cell.eventualSuccess)).length, plan.subjectSlots.length) },
    taskDefinitions: plan.tasks.length,
    taskAssignments: { planned: assignments.length, observed: observed.length, terminalValid: completed.length,
      notRun: assignments.length - observed.length, incomplete: observed.length - completed.length },
    firstAttemptSuccess: ratio(count('firstAttemptSuccess'), assignments.length),
    eventualSuccess: ratio(count('eventualSuccess'), assignments.length),
    unassistedFirstAttemptSuccess: ratio(count('unassistedFirstAttemptSuccess'), assignments.length),
    unassistedEventualSuccess: ratio(count('unassistedEventualSuccess'), assignments.length),
    retryAssignments: ratio(assignments.filter(cell => cell.retryCount > 0).length, assignments.length),
    retryCount: assignments.reduce((sum, cell) => sum + cell.retryCount, 0),
    intervenedAssignments: ratio(assignments.filter(cell => cell.interventionCount > 0).length, assignments.length),
    interventionEventsInRawRecords: assignments.reduce((sum, cell) => sum + cell.interventionCount, 0),
    attempts: { records: records.length, valid: records.filter(record => record.valid).length,
      invalid: records.filter(record => !record.valid).length,
      unassigned: records.filter(record => !id(record.reported?.subjectSlot) || !id(record.reported?.taskId)
        || !byKey.has(`${record.reported?.subjectSlot}/${record.reported?.taskId}`)).length,
      reportedSuccess: records.filter(record => record.reported?.outcome === 'success').length,
      eligibleSuccess: records.filter(record => record.acceptedOutcome === 'success').length,
      failedOrTimeoutOrAbandoned: failureRecords.length,
      failed: records.filter(record => record.reported?.outcome === 'failed').length,
      timeout: records.filter(record => record.reported?.outcome === 'timeout').length,
      abandoned: records.filter(record => record.reported?.outcome === 'abandoned').length,
      unfinished: records.filter(record => record.reported?.outcome === 'running').length,
      duplicateRecords: [...duplicateIssues.values()].reduce((sum, issues) => sum + issues.length, 0) },
    failuresByCause,
    terminalValidAssignmentTime: durations(completed.map(cell => cell.elapsedSeconds)),
    terminalValidAttemptTime: durations(records.filter(record => record.valid && record.reported.outcome !== 'running')
      .map(record => (time(record.reported.endedAt) - time(record.reported.startedAt)) / 1000)),
  };
  report.recordsComplete = inspected.ready && !report.validationIssues.length && !!raw.length
    && completed.length === assignments.length && time(results?.closedAt) !== null;
  report.researchComplete = report.recordsComplete && plan.dataKind === 'participant';
  report.status = plan.state === 'planned' && !hasResults ? 'planned'
    : !raw.length && !report.validationIssues.length ? 'not_run'
      : report.recordsComplete ? plan.dataKind === 'fixture' ? 'fixture_only' : 'complete' : 'incomplete';
  return { ...report, assignments, records };
}

if (typeof process !== 'undefined' && process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = process.argv.slice(2);
    if (args.length === 1 && ['--help', '-h'].includes(args[0])) {
      console.log('Usage: node scripts/evidence/task-evaluation.mjs PLAN.json [RESULTS.json] [--root EVIDENCE_ROOT]\n'
        + 'Read-only: omitted RESULTS inspects a planned/preregistered plan without running tasks.\n'
        + 'Paths in JSON are relative to --root (default: the plan directory).\n'
        + 'Exit 0: explicit planned/no-results inspection, or complete valid records; 1: invalid input/evidence; 2: not_run/incomplete.\n'
        + 'No exit code certifies participant or product success. Fixture records remain fixture_only.');
    } else {
      const rootIndex = args.indexOf('--root');
      let root;
      if (rootIndex >= 0) {
        if (!args[rootIndex + 1]) throw new Error('--root requires a directory.');
        root = resolve(args[rootIndex + 1]);
        args.splice(rootIndex, 2);
      }
      if (!args.length || args.length > 2 || args.some(arg => arg.startsWith('--')))
        throw new Error('Usage: node scripts/evidence/task-evaluation.mjs PLAN.json [RESULTS.json] [--root EVIDENCE_ROOT]');
      const planPath = resolve(args[0]);
      const bytes = readFileSync(planPath);
      const plan = JSON.parse(bytes.toString('utf8'));
      const results = args[1] ? JSON.parse(readFileSync(resolve(args[1]), 'utf8')) : null;
      const report = summarizeTaskEvaluation(plan, results, { root: root ?? dirname(planPath), planSha256: digest(bytes) });
      console.log(JSON.stringify(report, null, 2));
      process.exitCode = report.validationIssues.length || (plan.state === 'preregistered' && !report.registrationReady) ? 1
        : report.status === 'planned' || report.recordsComplete ? 0 : 2;
    }
  } catch (error) {
    console.error(`task evaluation: ${error.message}`);
    process.exitCode = 1;
  }
}
