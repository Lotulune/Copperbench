import { test, expect } from '@playwright/test';

test.describe('Stage 9 creator core', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="scenario-switcher-trigger"]');
    await page.click('[data-testid="scenario-btn-ready"]');
  });

  test('creates and reference-safely renames a workspace variable', async ({ page }) => {
    await page.click('[data-testid="nav-data"]');
    await expect(page.locator('[data-testid="creator-data-view"]')).toBeVisible();
    await page.getByRole('button', { name: '新建条目' }).click();
    await page.getByPlaceholder('entry_name').fill('quest_score');
    await page.getByRole('button', { name: '创建', exact: true }).click();
    await expect(page.getByText('quest_score', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: '重命名 quest_score' }).click();
    await page.locator('.registry-rename-input input').fill('quest_progress');
    await page.getByRole('button', { name: '预览重命名' }).click();
    await expect(page.getByText(/确认重命名：quest_score → quest_progress/)).toBeVisible();
    await page.getByRole('button', { name: '确认提交' }).click();
    await expect(page.getByText('quest_progress', { exact: true })).toBeVisible();
    await expect(page.getByText('quest_score', { exact: true })).not.toBeVisible();
  });

  test('creates a Procedure, adds a structured node, and commits once', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.locator('[data-testid="create-element-modal"]').getByRole('button', { name: '过程', exact: true }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'quest_tick');
    await page.click('[data-testid="create-element-submit-btn"]');

    await expect(page.locator('[data-testid="procedure-workbench"]')).toBeVisible();
    await page.getByLabel('搜索 Procedure 节点').fill('数值');
    await page.getByRole('button', { name: /^数值 value/ }).click();
    await page.getByRole('button', { name: /保存/ }).click();
    await expect(page.getByText(/已保存 1 项结构化变更/)).toBeVisible();
  });

  test('reviews and explicitly publishes isolated datagen output', async ({ page }) => {
    await page.getByRole('button', { name: '在暂存区运行数据生成' }).click();
    await expect(page.getByText('任务完成').first()).toBeVisible({ timeout: 5000 });
    await page.getByRole('button', { name: '查看暂存差异' }).click();
    await expect(page.locator('[data-testid="datagen-preview"]')).toContainText('暂存差异 1 项');
    await expect(page.locator('[data-testid="datagen-preview"]')).toContainText('src/generated/resources');

    await page.locator('[data-testid="datagen-publish-btn"]').click();
    await expect(page.getByRole('dialog', { name: '发布数据生成结果' })).toBeVisible();
    await page.getByRole('button', { name: '取消', exact: true }).click();
    await expect(page.getByRole('dialog', { name: '发布数据生成结果' })).not.toBeVisible();

    await page.locator('[data-testid="datagen-publish-btn"]').click();
    await page.locator('[data-testid="datagen-confirm-publish"]').click();
    await expect(page.locator('[data-testid="datagen-preview"]')).toContainText('生成结果已发布');
    await expect(page.getByText('修订 43').first()).toBeVisible();
    await expect(page.locator('[data-testid="datagen-publish-btn"]')).not.toBeVisible();
  });
});
