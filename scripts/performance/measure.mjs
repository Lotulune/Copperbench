import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import * as os from 'node:os';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { captureSourceIdentity, digest, fileIdentity } from '../evidence/source-identity.mjs';
import { createPerformanceFixture, fixtureSummary } from '../../ui-shell/performance/fixtures.mjs';
import { DEFAULT_CELLS, parseCells, summarizeCell, compareReports } from './statistics.mjs';
import { sanitizeFailure } from './failure.mjs';

const ROOT = fileURLToPath(new URL('../../', import.meta.url));
const MEASUREMENT = 'browser-interaction-v1';

export function parseOptions(arguments_) {
  const options = { cells: [...DEFAULT_CELLS], repetitions: 20, warmups: 2, maxAttempts: 25,
    timeoutMs: 10000, port: 5179, locale: 'zh', headed: false, dryRun: false };
  const values = { '--cells': 'cells', '--repetitions': 'repetitions', '--warmups': 'warmups',
    '--max-attempts': 'maxAttempts', '--timeout-ms': 'timeoutMs', '--port': 'port', '--locale': 'locale',
    '--output': 'output', '--browser-executable': 'browserExecutable', '--baseline': 'baseline' };
  for (let index = 0; index < arguments_.length; index++) {
    const argument = arguments_[index];
    if (argument === '--dry-run') options.dryRun = true;
    else if (argument === '--headed') options.headed = true;
    else if (argument === '--help') options.help = true;
    else {
      const key = values[argument];
      const value = arguments_[++index];
      if (!key || !value || value.startsWith('--')) throw new Error(`Unknown option or missing value: ${argument}`);
      options[key] = key === 'cells' ? parseCells(value)
        : ['repetitions', 'warmups', 'maxAttempts', 'timeoutMs', 'port'].includes(key) ? Number(value) : value;
    }
  }
  for (const key of ['repetitions', 'warmups', 'maxAttempts', 'timeoutMs', 'port'])
    if (!Number.isSafeInteger(options[key])) throw new Error(`${key} must be a safe integer.`);
  if (options.repetitions < 20 || options.warmups < 0 || options.maxAttempts < options.repetitions || options.timeoutMs < 1)
    throw new Error('Use at least 20 valid repetitions, nonnegative warmups, and a sufficient attempt cap.');
  if (options.port < 1024 || options.port > 65535) throw new Error('The loopback port must be between 1024 and 65535.');
  if (!['zh', 'en'].includes(options.locale)) throw new Error('Locale must be zh or en.');
  return options;
}

function newOutput(root, configured) {
  const path = configured ? resolve(root, configured) : resolve(root, 'output/performance',
    `${new Date().toISOString().replace(/[:.]/g, '-')}-${randomUUID().slice(0, 8)}`);
  const inside = ['output', 'build'].some(name => {
    const rel = relative(resolve(root, name), path);
    return rel && rel !== '..' && !rel.startsWith(`..${sep}`) && !isAbsolute(rel);
  });
  if (!inside) throw new Error('Output must be a new directory inside repository output/ or build/.');
  if (existsSync(path)) throw new Error('Output already exists; previous results are preserved.');
  return path;
}

export function buildRunPlan(options, root = ROOT) {
  const output = newOutput(root, options.output);
  const gaps = [];
  if (Number(process.versions.node.split('.')[0]) < 22) gaps.push('Node 22 or later is required.');
  for (const path of ['ui-shell/node_modules/@playwright/test/cli.js', 'ui-shell/node_modules/vite/bin/vite.js'])
    if (!existsSync(resolve(root, path))) gaps.push(`Missing ${path}; install repository dependencies manually.`);
  return { schemaVersion: '1.0', kind: 'browser-performance-plan', cells: options.cells,
    validRepetitionsPerCell: options.repetitions, warmupsPerCell: options.warmups, maxAttemptsPerCell: options.maxAttempts,
    command: ['node', 'node_modules/@playwright/test/cli.js', 'test', '--config=performance/playwright.config.ts'],
    cwd: 'ui-shell', output: relative(root, output).split(sep).join('/'), environmentGaps: gaps,
    browserExecutable: options.browserExecutable ? fileIdentity(resolve(options.browserExecutable)) : { status: 'resolve-installed-playwright-chromium-at-run' },
    locale: options.locale, theme: 'light', viewport: { width: 1366, height: 768, deviceScaleFactor: 1 },
    mode: options.headed ? 'headed browser' : 'headless browser',
    cache: 'fresh isolated browser context per cell; one fixture bootstrap; explicit warmups; dependency/Vite caches reused as found, never cleared; startup prebundling may occur',
    boundaries: { browser: 'React UI with synthetic JcefCoreBridge-compatible MockCoreBridge projections',
      javaCore: 'unknown: no Java Core process is exercised', nativeJcef: 'not_run',
      productStartup: 'not_run: only single browser fixture bootstrap observations are recorded',
      downloads: 'not_run: no automatic dependency/browser installation', build: 'not_run',
      taskLogs: 'not_run', nativeMemory: 'unknown: only optional Chromium JS heap observations', gameplay: 'not_applicable' },
    retention: 'Local output only; generated fixture data and operation names, never user workspace content, credentials or request payloads.',
    telemetry: 'No cloud telemetry; page requests outside the local fixture origin are blocked.',
    instrumentScope: 'Dedicated fixture only. Normal UI code and normal Playwright e2e selection remain unchanged.' };
}

function environment(options) {
  const cpus = os.cpus();
  let jdk = { status: 'unknown', reason: 'JDK/JBR is not used by this browser-only measurement.' };
  if (process.env.JAVA_HOME && existsSync(resolve(process.env.JAVA_HOME, 'release'))) {
    const text = readFileSync(resolve(process.env.JAVA_HOME, 'release'), 'utf8');
    const version = /^JAVA_VERSION="([^"]+)"/m.exec(text)?.[1];
    if (version) jdk = { status: 'observed-release-file', version, scope: 'JAVA_HOME release file; not a tested native runtime.' };
  }
  return { platform: os.platform(), osRelease: os.release(), architecture: os.arch(), cpuModel: cpus[0]?.model ?? 'unknown',
    logicalCpuCount: cpus.length, totalMemoryBytes: os.totalmem(), nodeVersion: process.versions.node,
    machineFingerprint: digest(JSON.stringify([os.hostname(), os.platform(), os.release(), os.arch(), cpus[0]?.model, os.totalmem()])),
    desktopSession: options.headed ? process.env.XDG_SESSION_TYPE ?? process.env.SESSIONNAME ?? 'unknown' : 'headless-browser',
    jdk, loadObservation: { freeMemoryBytes: os.freemem(), loadAverage: os.loadavg(),
      limitation: 'Host load is observational and is not controlled by this runner.' } };
}

function sanitizeLog(log, root) {
  return log.split(root).join('<repository>').split(root.split(sep).join('/')).join('<repository>')
    .split(os.homedir()).join('<user-home>');
}

async function runPlaywright(root, childEnvironment) {
  return new Promise(resolveResult => {
    const child = spawn(process.execPath, ['node_modules/@playwright/test/cli.js', 'test', '--config=performance/playwright.config.ts'], {
      cwd: resolve(root, 'ui-shell'), env: childEnvironment, windowsHide: true,
    });
    let log = '';
    child.stdout.on('data', bytes => { log += bytes.toString(); process.stdout.write(bytes); });
    child.stderr.on('data', bytes => { log += bytes.toString(); process.stderr.write(bytes); });
    const stop = () => child.kill('SIGINT');
    process.once('SIGINT', stop); process.once('SIGTERM', stop);
    child.once('error', error => resolveResult({ exitCode: null, signal: null, errorCode: error.code, log }));
    child.once('close', (exitCode, signal) => {
      process.removeListener('SIGINT', stop); process.removeListener('SIGTERM', stop);
      resolveResult({ exitCode, signal, log });
    });
  });
}

export async function measure(options, root = ROOT) {
  const plan = buildRunPlan(options, root);
  if (options.dryRun) { console.log(JSON.stringify(plan, null, 2)); return 0; }
  const output = resolve(root, plan.output);
  mkdirSync(dirname(output), { recursive: true });
  mkdirSync(output);
  const save = (name, value) => writeFileSync(resolve(output, name), `${JSON.stringify(value, null, 2)}\n`, 'utf8');
  const manifest = { ...plan, kind: 'browser-performance-run', measurementVersion: MEASUREMENT,
    startedAt: new Date().toISOString(), status: 'not_run', environment: environment(options),
    requirementIds: ['PERF-01', 'PERF-02', 'PERF-03', 'PERF-04', 'PERF-06', 'PERF-07'],
    limitations: ['Diagnostic engineering baseline, not a hardware/user-population certification.',
      'Browser fixture measurements do not establish Java Core, native JCEF, packaged startup or gameplay acceptance.',
      'No measured bottleneck has been assumed; this runner changes no product implementation.',
      'Warm interactions do not establish cold cache/dependency download behavior; global caches are never cleared.',
      'P03 save/input and P04 task-log correctness use their existing focused regressions; this baseline does not exercise them.'] };
  save('run-manifest.json', manifest);
  if (plan.environmentGaps.length) {
    save('report.json', { ...manifest, status: 'blocked', cells: [] });
    console.error('Performance measurement blocked by the environment gaps in report.json.');
    return 2;
  }
  let browserPath;
  try {
    const require = createRequire(resolve(root, 'ui-shell/package.json'));
    const { chromium } = require('@playwright/test');
    browserPath = options.browserExecutable ? resolve(options.browserExecutable) : chromium.executablePath();
    if (!existsSync(browserPath)) throw new Error('missing installed Chromium executable');
  } catch {
    save('report.json', { ...manifest, status: 'blocked', cells: [], environmentGaps: [
      'The installed Playwright Chromium executable is unavailable. Configure --browser-executable or install the matching browser manually.'] });
    return 2;
  }
  manifest.browserExecutable = fileIdentity(browserPath);
  try { manifest.sourceBefore = captureSourceIdentity(root); }
  catch (error) {
    save('report.json', { ...manifest, status: 'blocked', cells: [], identityFailure: {
      code: 'SOURCE_IDENTITY_UNAVAILABLE', message: sanitizeFailure(error.message, [root, os.homedir()]) } });
    return 2;
  }
  save('source-before.json', manifest.sourceBefore);
  const fixtures = Object.fromEntries([...new Set(options.cells.map(id => id.split(':')[0]))].map(size => {
    const fixture = createPerformanceFixture(size);
    save(`fixture-${size}.json`, fixture);
    return [size, fixtureSummary(fixture)];
  }));
  manifest.fixtures = fixtures;
  save('run-manifest.json', manifest);
  const result = await runPlaywright(root, { ...process.env,
    COPPERBENCH_PERF_CELLS: options.cells.join(','), COPPERBENCH_PERF_REPETITIONS: String(options.repetitions),
    COPPERBENCH_PERF_WARMUPS: String(options.warmups), COPPERBENCH_PERF_MAX_ATTEMPTS: String(options.maxAttempts),
    COPPERBENCH_PERF_TIMEOUT_MS: String(options.timeoutMs), COPPERBENCH_PERF_PORT: String(options.port),
    COPPERBENCH_PERF_LOCALE: options.locale, COPPERBENCH_PERF_HEADED: options.headed ? '1' : '0',
    COPPERBENCH_PERF_BROWSER_EXECUTABLE: browserPath, COPPERBENCH_PERF_OUTPUT: output,
    COPPERBENCH_PERF_PLAYWRIGHT_OUTPUT: resolve(output, 'playwright'),
  });
  writeFileSync(resolve(output, 'runner.log'), sanitizeLog(result.log, root), 'utf8');
  const rawCells = options.cells.map(id => {
    const path = resolve(output, `${id.replace(':', '-')}.json`);
    if (!existsSync(path)) return { id, requiredValidRepetitions: options.repetitions, attempts: [], warmups: [],
      status: 'not_run', failureReason: 'Playwright did not produce this cell; inspect the retained runner log.' };
    try { return JSON.parse(readFileSync(path, 'utf8')); }
    catch (error) { return { id, requiredValidRepetitions: options.repetitions, attempts: [], warmups: [], status: 'failed',
      setupOrIntegrityFailure: { code: 'RAW_CELL_UNREADABLE', message: sanitizeFailure(error.message, [root, os.homedir()]) } }; }
  });
  const cells = rawCells.map(cell => ({ ...summarizeCell(cell),
    status: cell.status === 'passed' ? summarizeCell(cell).status : cell.status,
    fixture: cell.fixture ?? fixtures[cell.id.split(':')[0]], raw: `${cell.id.replace(':', '-')}.json`,
    setupOrIntegrityFailure: cell.setupOrIntegrityFailure ?? null }));
  let sourceAfter;
  try { sourceAfter = captureSourceIdentity(root); }
  catch (error) { sourceAfter = { status: 'failed', commit: null, sourceSha256: null, dirtyDeltaSha256: null,
    message: sanitizeFailure(error.message, [root, os.homedir()]) }; }
  save('source-after.json', sourceAfter);
  const sourceStable = manifest.sourceBefore.commit === sourceAfter.commit
    && manifest.sourceBefore.sourceSha256 === sourceAfter.sourceSha256
    && manifest.sourceBefore.dirtyDeltaSha256 === sourceAfter.dirtyDeltaSha256;
  const browserEnvironments = rawCells.filter(cell => cell.environment).map(cell => cell.environment);
  const browserEnvironment = browserEnvironments[0] ?? null;
  const browserConsistent = browserEnvironments.length === cells.length
    && browserEnvironments.every(entry => JSON.stringify(entry) === JSON.stringify(browserEnvironment));
  const comparisonConditions = { level: 'browser-ui-with-mock-core-bridge', measurementVersion: MEASUREMENT,
    environment: { platform: manifest.environment.platform, osRelease: manifest.environment.osRelease,
      architecture: manifest.environment.architecture, cpuModel: manifest.environment.cpuModel,
      logicalCpuCount: manifest.environment.logicalCpuCount, totalMemoryBytes: manifest.environment.totalMemoryBytes,
      machineFingerprint: manifest.environment.machineFingerprint, nodeVersion: manifest.environment.nodeVersion,
      desktopSession: manifest.environment.desktopSession },
    browser: { executable: manifest.browserExecutable, observed: browserEnvironment,
      playwrightVersion: JSON.parse(readFileSync(resolve(root, 'ui-shell/node_modules/@playwright/test/package.json'), 'utf8')).version },
    viewport: plan.viewport, locale: options.locale, theme: plan.theme, cache: plan.cache, fixtures,
    warmups: options.warmups, repetitions: options.repetitions };
  const report = { ...manifest, completedAt: new Date().toISOString(),
    status: result.exitCode === 0 && cells.every(cell => cell.status === 'passed') && sourceStable && browserConsistent ? 'passed' : 'failed',
    process: { exitCode: result.exitCode, signal: result.signal, errorCode: result.errorCode ?? null },
    sourceStable, sourceAfter: { commit: sourceAfter.commit, sourceSha256: sourceAfter.sourceSha256,
      dirtyDeltaSha256: sourceAfter.dirtyDeltaSha256 }, browserConsistent, comparisonConditions, cells,
    endLoadObservation: { freeMemoryBytes: os.freemem(), loadAverage: os.loadavg() },
    resultMeaning: 'passed means repeat collection plus the declared browser correctness checks completed. Advisory budgets and other runtime layers remain separate.' };
  if (options.baseline) {
    try { report.comparison = compareReports(JSON.parse(readFileSync(resolve(root, options.baseline), 'utf8')), report); }
    catch (error) { report.comparison = { status: 'invalid_comparison', reason: error.message }; }
  }
  save('report.json', report);
  console.log(`Performance collection ${report.status}; local report: ${plan.output}/report.json`);
  return report.status === 'passed' ? 0 : 1;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const options = parseOptions(process.argv.slice(2));
    if (options.help) console.log('node scripts/performance/measure.mjs [--dry-run] [--cells small:navigation,large:graph-search] [--repetitions 20] [--warmups 2] [--max-attempts 25] [--timeout-ms 10000] [--locale zh|en] [--headed] [--browser-executable PATH] [--output output/performance/NEW-RUN] [--baseline REPORT]');
    else process.exitCode = await measure(options);
  } catch (error) { console.error(`performance collection: ${error.message}`); process.exitCode = 1; }
}
