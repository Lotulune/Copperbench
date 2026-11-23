import { expect, test } from '@playwright/test';

const chime = `asset:${'5'.repeat(64)}`;

test('a sound file is registered as a sound event from its asset details', async ({ page }) => {
  await page.goto('/');
  await page.waitForSelector('[data-testid="app-shell"]');
  await page.click('[data-testid="nav-assets"]');
  await expect(page.getByTestId('asset-browser')).toBeVisible();
  await page.waitForFunction(() => document.getAnimations().every(animation => (animation as CSSAnimation).animationName !== 'rise-in' || animation.playState !== 'running'));
  await page.getByTestId(`asset-card-${chime}`).click();

  const panel = page.getByTestId('asset-sound-events');
  await expect(page.getByTestId('asset-sound-event-name')).toHaveValue('copper_chime');
  await page.getByTestId('asset-sound-event-category').selectOption('block');
  await page.getByTestId('asset-sound-event-register').click();
  await expect(panel.getByTestId('asset-sound-event-list')).toContainText('copper_chime');
  await expect(panel.getByTestId('asset-sound-event-list')).toContainText('block');
  await expect(page.getByTestId('asset-sound-event-form')).toHaveCount(0);
});
