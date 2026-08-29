import { test, expect } from '@playwright/test';

test.describe('Diagnostic bundle export', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      window.__diagnosticExportRequests = [];
      window.__COPPERBENCH_DIAGNOSTICS_HOST__ = {
        available: true,
        openLogs: async () => undefined,
        exportBundle: async (includeWorkspaceFiles: boolean) => {
          window.__diagnosticExportRequests.push(includeWorkspaceFiles);
          return {
            status: 'exported',
            path: 'C:\\Users\\tester\\.copperbench\\diagnostics\\bundle.zip',
            fileName: 'bundle.zip',
            includedWorkspaceFiles: includeWorkspaceFiles,
            reproductionFileCount: includeWorkspaceFiles ? 2 : 0
          };
        }
      };
    });
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-help"]');
  });

  test('defaults to a redacted bundle and requires an explicit workspace-file choice', async ({ page }) => {
    const include = page.locator('[data-testid="diagnostic-include-workspace"]');
    const exportButton = page.locator('[data-testid="diagnostic-export-btn"]');
    await expect(include).not.toBeChecked();
    await expect(exportButton).toBeEnabled();

    await exportButton.click();
    await expect(page.locator('[data-testid="diagnostic-export-status"]')).toContainText('已导出 bundle.zip');

    await include.check();
    await exportButton.click();
    await expect(page.locator('[data-testid="diagnostic-export-status"]')).toContainText('附加 2 个复现文件');
    expect(await page.evaluate(() => window.__diagnosticExportRequests)).toEqual([false, true]);
  });
});

declare global {
  interface Window {
    __diagnosticExportRequests: boolean[];
  }
}
