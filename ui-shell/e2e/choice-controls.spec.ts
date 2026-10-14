import { expect, test } from '@playwright/test';

test('choice popup preserves keyboard, labels, native form data and disabled groups', async ({ page }) => {
  await page.goto('/e2e/fixtures/choice-controls.html');
  const select = page.getByLabel('Target', { exact: true });
  await page.getByText('Target', { exact: true }).click();
  await expect(select).toBeFocused();
  await select.press('Escape');
  await select.press('ArrowDown');
  await expect(page.getByRole('listbox')).toBeVisible();
  await select.press('ArrowDown');
  await select.press('Enter');
  await expect(select).toHaveValue('neoforge');
  await expect(page.getByTestId('saved')).toBeEmpty();
  await select.press('Space');
  await select.press('ArrowDown');
  await select.press('Enter');
  await expect(select).toHaveValue('target-0');
  await select.press('Space');
  await select.press('f');
  await select.press('Enter');
  await expect(select).toHaveValue('fabric');
  await select.press('Space');
  await select.press('End');
  await select.press('Escape');
  await expect(select).toHaveValue('fabric');
  await select.click();
  await page.getByRole('listbox').getByRole('option', { name: 'NeoForge', exact: true }).click();
  await expect(select).toBeFocused();
  await page.getByRole('button', { name: 'Save target' }).click();
  await expect(page.getByTestId('saved')).toHaveText('neoforge');
  await select.click();
  await select.press('Tab');
  await expect(page.getByRole('listbox')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Save target' })).toBeFocused();
  const disabled = page.getByRole('checkbox', { name: 'Disable choices' });
  await disabled.focus(); await disabled.press('Space');
  await expect(disabled).toBeChecked(); await expect(select).toBeDisabled();
});

test('long lists scroll and popups stay inside the viewport at its lower right edge', async ({ page }, testInfo) => {
  await page.goto('/e2e/fixtures/choice-controls.html');
  const select = page.getByLabel('Target', { exact: true });
  await select.click(); await select.press('End');
  const last = page.getByRole('listbox').getByRole('option', { name: /^Target 23/ });
  await expect(last).toBeInViewport();
  await select.press('Enter'); await expect(select).toHaveValue('target-23');
  const edge = page.getByRole('combobox', { name: 'Edge choice' });
  await edge.click();
  const bounds = await page.getByRole('listbox').boundingBox();
  const anchor = await edge.boundingBox();
  expect(bounds!.y + bounds!.height).toBeLessThan(anchor!.y);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
  await page.screenshot({ path: testInfo.outputPath('edge-popup.png') });
  await page.getByRole('button', { name: 'Save target' }).click();
  await expect(page.getByRole('listbox')).toHaveCount(0);
});

for (const theme of ['dark', 'light']) for (const locale of ['zh', 'en']) {
  test(`workbench choice menu uses the current theme and language (${theme}, ${locale})`, async ({ page }, testInfo) => {
    await page.addInitScript(({ theme, locale }) => {
      localStorage.setItem('copperbench.theme', theme); localStorage.setItem('copperbench.ui.locale', locale);
    }, { theme, locale });
    await page.goto('/');
    await page.getByTestId('nav-elements').click();
    const select = page.getByTestId('elements-state-select');
    await select.click();
    await expect(page.getByRole('listbox')).toBeVisible();
    await expect(page.getByRole('listbox').getByRole('option')).toHaveCount(4);
    await expect(page.getByRole('listbox').getByRole('option', { selected: true })).toHaveText(locale === 'zh' ? '全部状态' : 'All statuses');
    await page.screenshot({ path: testInfo.outputPath(`elements-${locale}-${theme}.png`), animations: 'disabled' });
    await select.press('ArrowDown'); await select.press('Enter');
    await expect(select).toHaveValue('valid');
    const language = page.getByTestId('ui-language-select');
    await language.click();
    await page.screenshot({ path: testInfo.outputPath(`language-${locale}-${theme}.png`), animations: 'disabled' });
    await language.press('Escape');
    await expect(language).toBeFocused();
  });
}

for (const theme of ['dark', 'light']) test(`checkbox glyph alignment (${theme})`, async ({ page }, testInfo) => {
  await page.goto('/e2e/fixtures/choice-controls.html');
  await page.evaluate(theme => { document.documentElement.dataset.theme = theme; }, theme);
  const checked = page.getByTestId('alignment-checked');
  await expect(checked).toBeChecked();
  await checked.focus();
  await checked.press('Space'); await expect(checked).not.toBeChecked();
  await checked.press('Space'); await expect(checked).toBeChecked();
  await expect(page.getByTestId('alignment-disabled')).toBeDisabled();
  const measurements = [];
  for (const kind of ['checked', 'unchecked', 'disabled', 'mixed', 'radio']) {
    const input = page.getByTestId(`alignment-${kind}`);
    const png = await input.screenshot({ path: testInfo.outputPath(`checkbox-${kind}.png`), animations: 'disabled' });
    if (['checked', 'mixed', 'radio'].includes(kind)) {
      const offset = await page.evaluate(async ({ png, foreground, background }) => {
        const image = new Image(); image.src = `data:image/png;base64,${png}`; await image.decode();
        const canvas = document.createElement('canvas'); canvas.width = image.width; canvas.height = image.height;
        const context = canvas.getContext('2d')!; context.drawImage(image, 0, 0);
        const data = context.getImageData(0, 0, image.width, image.height).data;
        function bounds(color: string) {
          const rgb = color.match(/\d+/g)!.slice(0, 3).map(Number);
          let left = image.width, top = image.height, right = -1, bottom = -1;
          for (let y = 0; y < image.height; y++) for (let x = 0; x < image.width; x++) {
            const at = (y * image.width + x) * 4;
            if (Math.abs(data[at] - rgb[0]) + Math.abs(data[at + 1] - rgb[1]) + Math.abs(data[at + 2] - rgb[2]) <= 24) {
              left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
          }
          return { left, top, right, bottom };
        }
        const mark = bounds(foreground), box = bounds(background);
        // Locator screenshots round fractional CSS bounds outwards; measure against the painted box.
        return { pixelsFound: mark.right >= mark.left && box.right >= box.left,
          dx: (mark.left + mark.right - box.left - box.right) / 2,
          dy: (mark.top + mark.bottom - box.top - box.bottom) / 2 };
      }, { png: png.toString('base64'), ...await input.evaluate(element => ({
        foreground: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor })) });
      expect(offset.pixelsFound, `${kind}: visible mark`).toBe(true);
      expect(Math.abs(offset.dx), `${kind}: horizontal center`).toBeLessThanOrEqual(.75);
      expect(Math.abs(offset.dy), `${kind}: vertical center`).toBeLessThanOrEqual(.75);
      measurements.push({ kind, paintedPixelOffset: offset });
    }
    measurements.push(await input.evaluate((element, kind) => ({ kind, rect: element.getBoundingClientRect().toJSON(),
      foreground: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor,
      disabled: (element as HTMLInputElement).disabled, checked: (element as HTMLInputElement).checked,
      indeterminate: (element as HTMLInputElement).indeterminate }), kind));
  }
  await page.getByTestId('checkbox-alignment').screenshot({ path: testInfo.outputPath(`checkbox-states-${theme}.png`), animations: 'disabled' });
  await testInfo.attach('checkbox-measurements', { body: JSON.stringify({ theme, deviceScaleFactor: testInfo.project.use.deviceScaleFactor ?? 1, measurements }), contentType: 'application/json' });
});
