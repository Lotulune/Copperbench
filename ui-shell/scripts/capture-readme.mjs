import { createHash } from 'node:crypto';
import { execFileSync, spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium, expect } from '@playwright/test';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const date = process.argv[2];
if (!/^\d{4}-\d{2}-\d{2}$/.test(date ?? '')) throw new Error('Usage: node ui-shell/scripts/capture-readme.mjs YYYY-MM-DD');
const port = Number(process.env.COPPERBENCH_SCREENSHOT_PORT ?? 5197);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid COPPERBENCH_SCREENSHOT_PORT.');
const output = resolve(root, 'assets/screenshots', date);
const theme = 'light';
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim();
async function sourceIdentity() {
  const paths = [...new Set(git('ls-files', '-z', '--cached', '--others', '--exclude-standard', '--',
    'ui-shell/src', 'ui-shell/public', 'ui-shell/index.html', 'ui-shell/package.json', 'ui-shell/package-lock.json',
    'ui-shell/vite.config.ts', 'ui-core/fixtures/v1.0', 'ui-shell/scripts/capture-readme.mjs').split('\0').filter(Boolean))].sort();
  const files = await Promise.all(paths.map(async path => ({ path, sha256: sha256(await readFile(resolve(root, path))) })));
  return { sha256: sha256(JSON.stringify(files)), files };
}

const source = await sourceIdentity();
const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', String(port), '--strictPort'],
  { cwd: resolve(root, 'ui-shell'), windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
let serverOutput = '';
server.stdout.on('data', data => { serverOutput += data; });
server.stderr.on('data', data => { serverOutput += data; });
let serverError;
server.on('error', error => { serverError = error; });
const serverExit = new Promise(done => server.once('close', done));
let browser;
try {
  const deadline = Date.now() + 15000;
  while (!serverOutput.includes('Local:')) {
    if (serverError) throw serverError;
    if (server.exitCode !== null || Date.now() > deadline) throw new Error(`Screenshot server did not start:\n${serverOutput}`);
    await delay(100);
  }
  browser = await chromium.launch({ headless: true });
  const screenshots = [];
  for (const [language, locale] of [['en', 'en'], ['zh-CN', 'zh']]) {
    await mkdir(resolve(output, language), { recursive: true });
    const context = await browser.newContext({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 1,
      locale: language === 'en' ? 'en-US' : language, colorScheme: theme, reducedMotion: 'reduce' });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(({ locale, theme }) => {
      localStorage.setItem('copperbench.workbench.mode', 'professional');
      localStorage.setItem('copperbench.ui.locale', locale);
      localStorage.setItem('copperbench.theme', theme);
    }, { locale, theme });
    const capture = async name => {
      await page.evaluate(() => document.fonts.ready);
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await page.mouse.move(1595, 995);
      if (errors.length) throw new Error(errors.join('\n'));
      const file = `${language}/${name}-${theme}.png`;
      const bytes = await page.screenshot({ path: resolve(output, file), animations: 'disabled' });
      screenshots.push({ file, sha256: sha256(bytes), bytes: bytes.length, width: 1600, height: 1000 });
    };
    await page.goto(`http://127.0.0.1:${port}/`);
    await expect(page.getByTestId('nav-hub')).toHaveAttribute('aria-current', 'page');
    await expect(page.getByTestId('workbench-main')).toContainText('Copper Lamp');
    await capture('workbench');

    await page.getByTestId('nav-elements').click();
    await page.locator('.element-card').filter({ hasText: 'Copper Lamp' }).click();
    await expect(page.getByTestId('field-displayName')).toHaveValue('Copper Lamp');
    await page.getByTestId('element-visual-preview').getByRole('button', {
      name: language === 'en' ? '3D preview' : '立体预览', exact: true
    }).click();
    await capture('elements');

    await page.getByTestId('nav-source').click();
    await page.locator('[data-source-path="src/main/java/example/ExampleMod.java"]').click();
    await expect(page.getByTestId('source-editor')).toHaveValue(/class ExampleMod/);
    await capture('source');

    await page.getByTestId('nav-relations').click();
    await expect(page.getByTestId('nav-relations')).toHaveAttribute('aria-current', 'page');
    await expect(page.getByTestId('relationship-graph-view')).toHaveAttribute('aria-busy', 'false');
    await capture('relations');

    await page.getByTestId('nav-tools-toggle').click();
    await page.getByTestId('nav-python').click();
    await expect(page.getByTestId('python-source')).toHaveValue(/import copperbench as cb/);
    await expect(page.getByTestId('python-run')).toBeDisabled();
    await capture('python');

    await page.getByTestId('mode-simple').click();
    await page.locator('[data-testid^="simple-element-"]').filter({ hasText: 'Copper Lamp' }).click();
    await page.getByRole('tab', { name: language === 'en' ? 'Settings' : '设置', exact: true }).click();
    await expect(page.getByTestId('simple-parameter-displayName')).toHaveValue('Copper Lamp');
    await capture('simple-creation');
    await context.close();
  }
  if ((await sourceIdentity()).sha256 !== source.sha256) throw new Error('Frontend inputs changed during capture. Capture again from stable inputs.');
  const manifest = { capturedAt: new Date().toISOString(), dateTimezone: 'Asia/Tokyo',
    sourceCommit: git('rev-parse', 'HEAD'), sourceScope: 'Working-tree frontend, UI-Core v1.0 fixtures, dependency manifests and capture script; not a released binary.',
    source, browser: browser.version(), viewport: { width: 1600, height: 1000 }, theme,
    scenario: 'Bundled Copper Trails ready fixture; language, theme, mode and navigation changed. Element preview collapsed to show fields; Python shows the built-in script without executing it.',
    scope: 'Unretouched browser development screenshots. Native Agent/Python execution is unavailable. Not native, installed-package or gameplay acceptance.',
    screenshots };
  await writeFile(resolve(output, 'capture.json'), JSON.stringify(manifest, null, 2) + '\n', 'utf8');
  console.log(`Captured ${screenshots.length} screenshots in assets/screenshots/${date}; frontend SHA-256 ${source.sha256}`);
} finally {
  try { await browser?.close(); }
  finally { if (server.exitCode === null) server.kill(); await serverExit; }
}
