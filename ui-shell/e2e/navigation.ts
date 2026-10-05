import { expect, type Page } from '@playwright/test';

/** Follow the same expandable Tools navigation used by a person. */
export async function openToolView(page: Page, view: 'ai' | 'python' | 'plugins' | 'tracks') {
  const toggle = page.getByTestId('nav-tools-toggle');
  await expect(toggle).toBeVisible();
  if (await toggle.getAttribute('aria-expanded') !== 'true') await toggle.click();
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  await page.getByTestId(`nav-${view}`).click();
}
