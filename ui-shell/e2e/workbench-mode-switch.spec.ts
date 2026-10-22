import { expect, test, type Page } from '@playwright/test';

test.use({ storageState: { cookies: [], origins: [] } });

async function fixture(page: Page, options: { mode?: 'simple' | 'professional'; english?: boolean; dark?: boolean } = {}) {
  await page.addInitScript(options => {
    if (!localStorage.getItem('copperbench.workbench.mode')) localStorage.setItem('copperbench.workbench.mode', options.mode ?? 'simple');
    localStorage.setItem('copperbench.ui.locale', options.english ? 'en' : 'zh');
    localStorage.setItem('copperbench.theme', options.dark ? 'dark' : 'light');
    (window as any).modeChrome = null;
    window.__COPPERBENCH_WINDOW_HOST__ = { systemFrame: false, chromeRegionSchemaVersion: '1.0', invoke: async () => {},
      reportChromeRegions: async snapshot => { (window as any).modeChrome = snapshot; }, pointerGesture: () => {} };
  }, options);
  await page.goto('/');
  await expect(page.getByTestId('workbench-mode-control')).toHaveAttribute('data-expanded', 'false');
}

test('hover expands equal choices, slides on selection and collapses after the leave delay', async ({ page }, info) => {
  await fixture(page);
  const control = page.getByTestId('workbench-mode-control');
  await expect(page.getByTestId('mode-simple')).toBeVisible();
  await expect(page.getByTestId('mode-professional')).not.toBeVisible();
  const collapsed = await control.boundingBox(), tools = await page.locator('.titlebar-tools').boundingBox();
  await control.screenshot({ path: info.outputPath('mode-collapsed.png') });
  await control.hover();
  await expect(control).toHaveAttribute('data-expanded', 'true');
  const slot = await page.locator('.workbench-mode-slot').boundingBox();
  await expect.poll(async () => (await control.boundingBox())!.width).toBeCloseTo(slot!.width, 0);
  const simple = await page.getByTestId('mode-simple').boundingBox(), professional = await page.getByTestId('mode-professional').boundingBox();
  expect(simple!.width).toBeCloseTo(professional!.width, 1); expect(simple!.height).toBe(professional!.height);
  expect((await page.locator('.titlebar-tools').boundingBox())!.x).toBeCloseTo(tools!.x, 1);
  await expect.poll(() => page.evaluate(() => (window as any).modeChrome?.regions.find((region: any) => region.id === 'workbench-mode')?.bounds.width)).toBeCloseTo(slot!.width, 0);
  const startX = (await page.locator('.workbench-mode-slider').boundingBox())!.x;
  await page.evaluate(() => {
    (window as any).modeSlideSamples = [];
    document.querySelector('[data-testid=mode-professional]')!.addEventListener('click', () => {
      const started = performance.now();
      const sample = () => {
        (window as any).modeSlideSamples.push(document.querySelector('.workbench-mode-slider')!.getBoundingClientRect().x);
        if (performance.now() - started < 420) requestAnimationFrame(sample);
      };
      requestAnimationFrame(sample);
    }, { once: true });
  });
  await page.getByTestId('mode-professional').click();
  await expect(page.getByTestId('mode-professional')).toHaveAttribute('aria-pressed', 'true');
  await expect.poll(() => page.evaluate(() => (window as any).modeSlideSamples.length)).toBeGreaterThan(10);
  const endX = (await page.locator('.workbench-mode-slider').boundingBox())!.x;
  const samples: number[] = await page.evaluate(() => (window as any).modeSlideSamples);
  expect(samples.some(x => x > startX + 1 && x < endX - 1)).toBe(true);
  await control.screenshot({ path: info.outputPath('mode-expanded-professional.png') });
  await page.mouse.move(20, 120);
  await page.waitForTimeout(600);
  await expect(control).toHaveAttribute('data-expanded', 'true');
  await control.hover(); // Re-entry cancels the original close timer.
  await page.waitForTimeout(550);
  await expect(control).toHaveAttribute('data-expanded', 'true');
  await page.mouse.move(20, 120);
  await expect(control).toHaveAttribute('data-expanded', 'false');
  await expect.poll(async () => (await control.boundingBox())!.width).toBeCloseTo(collapsed!.width, 0);
  await expect(page.getByTestId('mode-simple')).not.toBeVisible();
  await expect(page.getByTestId('mode-professional')).toBeVisible();
  await expect.poll(() => page.evaluate(() => (window as any).modeChrome?.regions.find((region: any) => region.id === 'workbench-mode')?.bounds.width)).toBeCloseTo(collapsed!.width, 0);
  await page.reload();
  await expect(control).toHaveAttribute('data-expanded', 'false');
  await expect(page.getByTestId('mode-professional')).toHaveAttribute('aria-pressed', 'true');
});

test('keyboard focus reveals options, arrows move focus, Escape returns to the selected mode', async ({ page }) => {
  await fixture(page);
  const control = page.getByTestId('workbench-mode-control');
  await page.keyboard.press('Tab');
  await expect(page.getByTestId('mode-simple')).toBeFocused();
  await expect(control).toHaveAttribute('data-expanded', 'true');
  await page.keyboard.press('ArrowRight');
  await expect(page.getByTestId('mode-professional')).toBeFocused();
  await page.keyboard.press('Enter');
  await page.mouse.move(20, 120); await page.waitForTimeout(1150);
  await expect(control).toHaveAttribute('data-expanded', 'true');
  await page.keyboard.press('Home'); await expect(page.getByTestId('mode-simple')).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(control).toHaveAttribute('data-expanded', 'false');
  await expect(page.getByTestId('mode-professional')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByTestId('workbench-command-trigger')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByTestId('ui-language-select')).toBeFocused();
});

for (const dark of [false, true]) test(`compact equal segments fit both labels with reduced motion (${dark ? 'dark' : 'light'})`, async ({ page }, info) => {
  await page.setViewportSize({ width: 500, height: 600 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await fixture(page, { mode: 'professional', english: true, dark });
  const control = page.getByTestId('workbench-mode-control');
  await control.hover();
  await expect(page.getByTestId('mode-simple')).toBeInViewport({ ratio: 1 });
  const bounds = await Promise.all(['mode-simple', 'mode-professional'].map(id => page.getByTestId(id).boundingBox()));
  expect(bounds[0]!.width).toBeCloseTo(bounds[1]!.width, 1);
  expect(await control.evaluate(element => parseFloat(getComputedStyle(element).transitionDuration))).toBeLessThan(0.001);
  expect(await page.locator('.workbench-mode-slider').evaluate(element => parseFloat(getComputedStyle(element).transitionDuration))).toBeLessThan(0.001);
  for (const id of ['mode-simple', 'mode-professional']) expect(await page.getByTestId(id).evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('compact-mode-expanded.png'), style: '.scenario-switcher { visibility: hidden !important; }' });
});

test.describe('touch mode choice', () => {
  test.use({ hasTouch: true });
  test('tap reveals both modes and selection settles on the chosen mode', async ({ page }) => {
    await fixture(page);
    await page.getByTestId('mode-simple').tap();
    await expect(page.getByTestId('workbench-mode-control')).toHaveAttribute('data-expanded', 'true');
    await page.getByTestId('mode-professional').tap();
    await expect(page.getByTestId('mode-professional')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('workbench-mode-control')).toHaveAttribute('data-expanded', 'false');
  });
});
