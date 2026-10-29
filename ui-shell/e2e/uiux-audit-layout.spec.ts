import { expect, test } from '@playwright/test';

const views = [
  { name: '1920', width: 1920, height: 1080 },
  { name: '1366', width: 1366, height: 768 },
  { name: '1280', width: 1280, height: 720 },
  { name: '125pct-content', width: 1093, height: 614 },
  { name: '150pct-content', width: 853, height: 480 },
  { name: '200pct-content', width: 640, height: 360 }
];

for (const locale of ['zh', 'en']) for (const theme of ['light', 'dark']) for (const view of views) {
  test(`creation remains keyboard reachable: ${locale}/${theme}/${view.name}`, async ({ page }, testInfo) => {
    await page.addInitScript(({ locale, theme }) => {
      localStorage.setItem('copperbench.ui.locale', locale); localStorage.setItem('copperbench.theme', theme);
    }, { locale, theme });
    await page.setViewportSize(view); await page.goto('/');
    await page.getByTestId('empty-primary-action').click();
    await page.getByTestId('create-element-name-input').fill('audit_layout');
    await page.getByTestId('create-type-search').fill('villagerprofession');
    const type = page.getByTestId('create-element-type-villagerprofession');
    await type.focus(); await page.keyboard.press('Space');
    await expect(type).toHaveAttribute('aria-pressed', 'true');
    const submit = page.getByTestId('create-element-submit-btn');
    await submit.focus(); await expect(submit).toBeInViewport();
    const dialog = page.getByTestId('create-element-modal').getByRole('dialog');
    const bounds = await dialog.boundingBox();
    expect(bounds!.x).toBeGreaterThanOrEqual(0); expect(bounds!.y).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(view.width + 1);
    expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(view.height + 1);
    expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
    const measurements = await page.evaluate(() => {
      const rgb = (color: string) => (color.match(/[\d.]+/g) ?? []).map(Number);
      const luminance = (channels: number[]) => channels.slice(0, 3).map(value => {
        const c = value / 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
      }).reduce((sum, c, i) => sum + c * [0.2126, 0.7152, 0.0722][i], 0);
      const contrast = (a: number[], b: number[]) => (Math.max(luminance(a), luminance(b)) + 0.05) / (Math.min(luminance(a), luminance(b)) + 0.05);
      function background(element: Element): number[] {
        const chain: Element[] = []; for (let e: Element | null = element; e; e = e.parentElement) chain.unshift(e);
        return chain.reduce((under, e) => {
          const over = rgb(getComputedStyle(e).backgroundColor); const alpha = over[3] ?? 1;
          return under.map((v, i) => (over[i] ?? 0) * alpha + v * (1 - alpha));
        }, [255, 255, 255]);
      }
      const actual = Array.from(document.querySelectorAll('.create-name-field p, .create-type-option small')).map(element => ({
        selector: element.className || element.tagName, ratio: contrast(rgb(getComputedStyle(element).color), background(element))
      }));
      const tokenSamples = ['--bg-base', '--bg-panel', '--navrail-bg', '--footer-bg', '--bg-card-hover'].map(surface => {
        const sample = document.createElement('span'); sample.style.cssText = `color:var(--text-sub);background:var(${surface})`;
        document.body.append(sample); const style = getComputedStyle(sample);
        const value = { surface, ratio: contrast(rgb(style.color), rgb(style.backgroundColor)) }; sample.remove(); return value;
      });
      const scroll = document.querySelector('.create-type-grid')!;
      return { actual, tokenSamples, scrollbarWidth: getComputedStyle(scroll, '::-webkit-scrollbar').width,
        thumb: getComputedStyle(scroll, '::-webkit-scrollbar-thumb').backgroundColor,
        track: getComputedStyle(scroll, '::-webkit-scrollbar-track').backgroundColor };
    });
    for (const sample of [...measurements.actual, ...measurements.tokenSamples]) expect(sample.ratio).toBeGreaterThanOrEqual(4.5);
    expect(measurements.scrollbarWidth).toBe('10px'); expect(measurements.thumb).not.toBe(measurements.track);
    await testInfo.attach('computed-contrast', { body: JSON.stringify(measurements, null, 2), contentType: 'application/json' });
    if (view.name === '1366' || view.name === '200pct-content') await page.screenshot({ path: testInfo.outputPath(`creation-${locale}-${theme}-${view.name}.png`) });
  });
}
