import { expect, test, type Page } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { homedir, tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { createPerformanceFixture, fixtureSummary } from './fixtures.mjs';
import { parseCells, summarizeCell } from '../../scripts/performance/statistics.mjs';
import { sanitizeFailure } from '../../scripts/performance/failure.mjs';
import { armTiming, finishTiming, installPerformanceFixture, type Condition } from './browser-fixture';

const cells = parseCells(process.env.COPPERBENCH_PERF_CELLS);
const repetitions = Number(process.env.COPPERBENCH_PERF_REPETITIONS ?? '20');
const warmups = Number(process.env.COPPERBENCH_PERF_WARMUPS ?? '2');
const maxAttempts = Number(process.env.COPPERBENCH_PERF_MAX_ATTEMPTS ?? '25');
const timeout = Number(process.env.COPPERBENCH_PERF_TIMEOUT_MS ?? '10000');
const output = process.env.COPPERBENCH_PERF_OUTPUT;
const locale = process.env.COPPERBENCH_PERF_LOCALE === 'en' ? 'en' : 'zh';
const safeMessage = (value: unknown) => sanitizeFailure(value, [fileURLToPath(new URL('../../', import.meta.url)),
  homedir(), tmpdir(), process.env.COPPERBENCH_PERF_BROWSER_EXECUTABLE]);
if (!output || !Number.isInteger(repetitions) || repetitions < 20 || !Number.isInteger(warmups) || warmups < 0
  || !Number.isInteger(maxAttempts) || maxAttempts < repetitions || !Number.isInteger(timeout) || timeout <= 0)
  throw new Error('Use scripts/performance/measure.mjs with a new output directory and valid sampling options.');

const monotonic = () => Number(process.hrtime.bigint()) / 1_000_000;
const visible = (page: Page, id: string) => expect(page.getByTestId(id)).toBeVisible({ timeout });

async function prepare(page: Page, operation: string, fixture: any) {
  const target = fixture.elements.at(-1);
  if (operation === 'navigation' || operation === 'graph-open') {
    await page.getByTestId('nav-hub').click(); await visible(page, 'hub-tracks-badge');
  } else if (operation === 'asset-search') {
    await page.getByTestId('nav-assets').click(); await visible(page, 'asset-search');
    await page.getByTestId('asset-search').fill('');
    await expect(page.locator('.asset-library-file-list [data-asset-id]')).toHaveCount(fixture.assetProjection.assets.length, { timeout });
  } else if (operation === 'graph-search') {
    await page.getByTestId('nav-relations').click(); await visible(page, 'relationship-canvas');
    await page.getByTestId('relationship-search').fill('');
    await page.getByTestId('relationship-collapse-all').click();
    await expect(page.locator(`[data-graph-node][data-element-id="${target.id}"]`)).toHaveCount(0, { timeout });
  } else {
    await page.getByTestId('nav-elements').click(); await visible(page, 'elements-search-input');
    await page.getByTestId('elements-search-input').fill(operation === 'editor-open' ? target.name : '');
    await expect(page.locator('.elements-grid [data-element-id], .elements-table [data-element-id]'))
      .toHaveCount(operation === 'editor-open' ? 1 : Math.min(24, fixture.elements.length), { timeout });
    if (operation === 'editor-open') {
      if (await page.getByTestId('element-inspector').isVisible()) {
        await page.getByTestId('inspector-close-btn').click();
        await expect(page.getByTestId('element-inspector')).toHaveCount(0, { timeout });
      }
      await expect(page.locator(`[data-element-id="${target.id}"]`).last()).toBeVisible({ timeout });
    }
  }
}

async function perform(page: Page, operation: string, fixture: any, stage: (value: string) => void) {
  const target = fixture.elements.at(-1);
  const asset = fixture.assetProjection.assets.at(-1);
  let input: string;
  let event: 'input' | 'click';
  let feedback: Condition;
  let ready: Condition;
  if (operation === 'navigation') {
    input = '[data-testid="nav-elements"]'; event = 'click';
    feedback = { selector: input, attribute: ['aria-current', 'page'] };
    ready = { selector: '[data-testid="elements-search-input"]', countSelector: '.elements-grid [data-element-id], .elements-table [data-element-id]', count: Math.min(24, fixture.elements.length) };
  } else if (operation === 'graph-open') {
    input = '[data-testid="nav-relations"]'; event = 'click';
    feedback = { selector: input, attribute: ['aria-current', 'page'] };
    ready = { selector: '[data-testid="relationship-graph-view"][aria-busy="false"] [data-testid="relationship-svg"]',
      countSelector: '[data-node-kind="workspace"]', count: 1,
      textSelector: '[data-testid="relationship-visible-count"]',
      textIncludes: `/ ${fixtureSummary(fixture).relationshipModelNodeCount}` };
  } else if (operation === 'graph-search') {
    input = '[data-testid="relationship-search"]'; event = 'input';
    feedback = { selector: `[data-graph-node][data-element-id="${target.id}"]` };
    ready = { selector: `[data-graph-node][data-element-id="${target.id}"]`, focused: true };
  } else if (operation === 'asset-search') {
    input = '[data-testid="asset-search"]'; event = 'input';
    feedback = { selector: `[data-testid="asset-card-${asset.id}"]`, countSelector: '.asset-library-file-list [data-asset-id]', count: 1 };
    ready = feedback;
  } else if (operation === 'editor-open') {
    input = `.elements-grid [data-element-id="${target.id}"], .elements-table [data-element-id="${target.id}"]`; event = 'click';
    feedback = { selector: '[data-testid="element-inspector"]' };
    ready = { selector: '[data-testid="field-name"]', value: target.name };
  } else {
    input = '[data-testid="elements-search-input"]'; event = 'input';
    feedback = { selector: `.elements-grid [data-element-id="${target.id}"], .elements-table [data-element-id="${target.id}"]`,
      countSelector: '.elements-grid [data-element-id], .elements-table [data-element-id]', count: 1 };
    ready = feedback;
  }
  await armTiming(page, input, event, feedback, ready);
  const start = monotonic();
  if (operation === 'graph-search') {
    await page.locator(input).fill(target.displayName, { timeout }); await page.locator(input).press('Enter', { timeout });
    await expect(page.locator(ready.selector)).toBeFocused({ timeout });
  } else if (event === 'input') {
    await page.locator(input).fill(operation === 'asset-search' ? asset.relativePath.split('/').at(-1) : target.name, { timeout });
  } else await page.locator(input).click({ timeout });
  await expect(page.locator(ready.selector)).toBeVisible({ timeout });
  if (ready.value !== undefined) await expect(page.locator(ready.selector)).toHaveValue(ready.value, { timeout });
  if (ready.countSelector) await expect(page.locator(ready.countSelector)).toHaveCount(ready.count!, { timeout });
  if (operation === 'graph-open') await visible(page, 'relationship-canvas');
  const timing = await finishTiming(page);
  const endToEndMs = monotonic() - start;
  stage('correctness-after-timing');
  if (timing.browserInputToReadyMs === null || timing.feedbackMs === null) throw new Error('Missing renderer monotonic timestamps.');
  if (operation === 'graph-open' || operation === 'graph-search') {
    const summary = fixtureSummary(fixture);
    await expect(page.getByTestId('relationship-visible-count')).toContainText(`/ ${summary.relationshipModelNodeCount}`);
    expect(await page.locator('[data-graph-node]').count()).toBeLessThanOrEqual(96);
    expect(await page.locator('[data-edge-relation="reference"]').count()).toBeLessThanOrEqual(160);
    await expect(page.getByRole('button', { name: locale === 'en' ? `${summary.diagnosticCount} diagnostics` : `诊断 ${summary.diagnosticCount}`, exact: true })).toBeVisible();
    if (operation === 'graph-open') {
      const references = timing.requests.filter(request => request.operation === 'get_workspace_references');
      expect(references.length).toBeGreaterThan(0); expect(references.length).toBeLessThanOrEqual(2);
      for (const request of references) expect(request.projectionCounts).toEqual({ nodes: summary.referenceNodeCount,
        edges: summary.referenceEdgeCount, diagnostics: summary.diagnosticCount });
    }
  }
  return { endToEndMs, ...timing, correctness: 'passed' };
}

for (const id of cells) {
  test(`browser performance ${id}`, async ({ page }, testInfo) => {
    test.setTimeout((warmups + maxAttempts) * (timeout * 3 + 5000) + 30000);
    page.setDefaultTimeout(timeout);
    const [size, operation] = id.split(':');
    const fixture = createPerformanceFixture(size);
    const errors: Array<{ name: string; message: string }> = [];
    page.on('pageerror', error => errors.push({ name: error.name, message: safeMessage(error.message) }));
    await page.route('**/*', route => {
      const url = new URL(route.request().url());
      return url.origin === new URL(testInfo.project.use.baseURL!).origin ? route.continue() : route.abort('blockedbyclient');
    });
    const report: any = { schemaVersion: '1.0', id, requiredValidRepetitions: repetitions,
      fixture: fixtureSummary(fixture), warmups: [], attempts: [], status: 'not_run',
      operation, level: 'browser-ui-with-mock-core-bridge', cache: 'fresh context per cell; one fixture bootstrap; two warmups by default; warm interactions thereafter',
      timings: { endToEnd: 'Runner monotonic clock: before Playwright input dispatch through observed correct interactable result and renderer timing readback; includes automation overhead.',
        browserInputToReady: 'Renderer performance.now(): input event received through two consecutive animation frames with the correct usable result.',
        feedback: 'Renderer performance.now(): input event received through first visible operation feedback on an animation frame.',
        simulatedBridge: 'Renderer performance.now(): MockCoreBridge query/handshake handling plus response serialization; excludes real Java Core, IPC, download and build.' } };
    mkdirSync(output!, { recursive: true });
    const path = join(output!, `${id.replace(':', '-')}.json`);
    const save = () => writeFileSync(path, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
    let runStage = 'fixture-bootstrap';
    save();
    try {
      await installPerformanceFixture(page, fixture, locale);
      const bootstrapStart = monotonic();
      await page.goto('/'); await visible(page, 'app-shell'); await visible(page, 'nav-elements');
      await expect(page.getByTestId('nav-elements')).toContainText(String(fixture.elements.length));
      report.bootstrap = { durationMs: monotonic() - bootstrapStart, n: 1, p95Ms: null,
        scope: 'Browser fixture bootstrap only; not cold product or native JCEF startup.' };
      report.environment = await page.evaluate(() => ({ browser: navigator.userAgent, language: document.documentElement.lang,
        theme: document.documentElement.dataset.theme, viewport: { width: innerWidth, height: innerHeight },
        devicePixelRatio, heapScope: 'Chromium performance.memory observation when available; not native RSS or a proven peak.' }));
      for (const phase of ['warmups', 'attempts']) {
        for (let index = 0; index < (phase === 'warmups' ? warmups : maxAttempts); index++) {
          if (phase === 'attempts' && report.attempts.filter((attempt: any) => attempt.status === 'passed').length >= repetitions) break;
          const attempt: any = { index: index + 1, status: 'running', stage: 'prepare', endToEndMs: null,
            browserInputToReadyMs: null, feedbackMs: null, requests: [], correctness: 'not_run' };
          report[phase].push(attempt); save();
          const before = monotonic();
          try {
            await prepare(page, operation, fixture);
            attempt.stage = 'input-to-usable';
            Object.assign(attempt, await perform(page, operation, fixture, stage => { attempt.stage = stage; }));
            expect(errors).toEqual([]);
            attempt.status = 'passed';
          } catch (error: any) {
            attempt.status = error.name === 'TimeoutError' || /timed out|timeout/i.test(error.message) ? 'timeout' : 'failed';
            attempt.failureCode = attempt.status === 'timeout' ? 'OPERATION_TIMEOUT' : 'CORRECTNESS_OR_OPERATION_FAILED';
            attempt.errorName = error.name;
            attempt.failureMessage = safeMessage(error.message);
            attempt.pageErrors = [...errors];
            attempt.elapsedIncludingPreparationMs = monotonic() - before;
            attempt.correctness = 'failed';
            if (!page.isClosed()) {
              attempt.requests = await page.evaluate(() => {
                const state = window.__copperbenchPerformance; state?.timing?.cancel();
                return state?.requests.slice(state?.timing?.requestOffset ?? 0) ?? [];
              }).catch(() => []);
              attempt.screenshot = `${id.replace(':', '-')}-${phase}-${index + 1}.png`;
              await page.screenshot({ path: join(output!, attempt.screenshot) }).catch(() => { attempt.screenshot = null; });
            }
          }
          save();
          if (page.isClosed()) break;
        }
      }
      // Data integrity checks are kept separate from the latency endpoint.
      if (operation === 'graph-search') {
        runStage = 'last-page-graph-diagnostic';
        const missingIndex = fixture.elements.length - 10;
        await page.getByTestId('relationship-search').fill(`missing_perf_${String(missingIndex).padStart(5, '0')}`);
        await page.getByTestId('relationship-search').press('Enter');
        await expect(page.getByRole('complementary', { name: locale === 'en' ? 'Relationship details' : '关系详情' }))
          .toContainText('PERFORMANCE_FIXTURE_TARGET_MISSING');
        report.lastPageDiagnosticIntegrity = 'passed';
      }
    } catch (error: any) {
      report.setupOrIntegrityFailure = { errorName: error.name, message: safeMessage(error.message),
        stage: runStage, code: 'FIXTURE_BOOTSTRAP_OR_INTEGRITY_FAILED' };
    } finally {
      report.pageErrors = errors;
      report.summary = summarizeCell(report);
      report.status = report.setupOrIntegrityFailure ? 'failed' : report.summary.status;
      save();
      await testInfo.attach('performance-cell', { path, contentType: 'application/json' });
    }
    expect(report.status, 'Raw attempts were retained; failed/timeout/setup records cannot become a passing baseline.').toBe('passed');
  });
}
