import { test, expect } from '@playwright/test';

test.describe('Stage 9 creator core', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    if (await page.locator('[data-testid="scenario-switcher-trigger"]').isVisible()) {
      await page.click('[data-testid="scenario-switcher-trigger"]');
      await page.click('[data-testid="scenario-btn-ready"]');
    }
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

  test('opens dedicated FunctionWorkbench, edits code and tags, and saves', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.locator('[data-testid="create-element-modal"]').getByRole('button', { name: '函数', exact: true }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'spell_cast');
    await page.click('[data-testid="create-element-submit-btn"]');

    // Dedicated workbench opens automatically
    await expect(page.locator('[data-testid="function-workbench"]')).toBeVisible();
    await expect(page.getByText('spell_cast.mcfunction')).toBeVisible();

    // Edit code via snippet insertion
    await page.click('[data-testid="snippet-execute"]');
    await expect(page.locator('[data-testid="function-dirty-badge"]')).toBeVisible();

    // Switch to tags tab and add a custom tag
    await page.click('[data-testid="function-tab-tags"]');
    await page.fill('[data-testid="function-add-tag-input"]', 'copperbench:custom_spells');
    await page.click('[data-testid="function-add-tag-btn"]');
    await expect(page.getByText('#copperbench:custom_spells')).toBeVisible();

    // Save
    await page.click('[data-testid="function-save-btn"]');
    await expect(page.getByText('已保存')).toBeVisible();
    await expect(page.locator('[data-testid="function-dirty-badge"]')).not.toBeVisible();

    // Navigate back to elements list and reopen from matching card
    await page.click('[data-testid="function-back-btn"]');
    await expect(page.locator('[data-testid="elements-workbench"]')).toBeVisible();
    await page.locator('[data-element-id]').filter({ hasText: 'spell_cast' }).first().click();
    await expect(page.locator('[data-testid="function-workbench"]')).toBeVisible();
  });

  test('opens dedicated LootTableWorkbench, configures pools, and saves', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.locator('[data-testid="create-element-modal"]').getByRole('button', { name: '战利品表', exact: true }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'copper_dungeon_chest');
    await page.click('[data-testid="create-element-submit-btn"]');

    // Dedicated workbench opens
    await expect(page.locator('[data-testid="loottable-workbench"]')).toBeVisible();

    // Change drop type
    await page.selectOption('[data-testid="loottable-type-select"]', 'Chest');
    await expect(page.locator('[data-testid="loottable-dirty-badge"]')).toBeVisible();

    // Configure pool parameters
    await page.fill('[data-testid="pool-name-input"]', 'Dungeon Rare Pool');
    await page.fill('[data-testid="pool-minrolls-input"]', '2');
    await page.fill('[data-testid="pool-maxrolls-input"]', '5');

    // Check JSON preview tab
    await page.click('[data-testid="loottable-tab-json"]');
    await expect(page.getByText('"type": "minecraft:chest"')).toBeVisible();

    // Switch back to designer and save
    await page.click('[data-testid="loottable-tab-designer"]');
    await page.click('[data-testid="loottable-save-btn"]');
    await expect(page.getByText('已保存')).toBeVisible();
    await expect(page.locator('[data-testid="loottable-dirty-badge"]')).not.toBeVisible();

    // Back to elements list and reopen
    await page.click('[data-testid="loottable-back-btn"]');
    await expect(page.locator('[data-testid="elements-workbench"]')).toBeVisible();
    await page.locator('[data-element-id]').filter({ hasText: 'copper_dungeon_chest' }).first().click();
    await expect(page.locator('[data-testid="loottable-workbench"]')).toBeVisible();
  });

  test('opens dedicated AdvancementWorkbench, modifies criteria and frame, and saves', async ({ page }) => {
    await page.click('[data-testid="nav-elements"]');
    await page.click('[data-testid="create-element-btn"]');
    await page.locator('[data-testid="create-element-modal"]').getByRole('button', { name: '进度', exact: true }).click();
    await page.fill('[data-testid="create-element-name-input"]', 'copper_age');
    await page.click('[data-testid="create-element-submit-btn"]');

    // Dedicated workbench opens
    await expect(page.locator('[data-testid="advancement-workbench"]')).toBeVisible();

    // Edit display fields
    await page.fill('[data-testid="advancement-title-input"]', '青铜时代');
    await page.fill('[data-testid="advancement-desc-input"]', '制作第一把铜镐');
    await page.selectOption('[data-testid="advancement-type-select"]', 'goal');
    await expect(page.locator('[data-testid="advancement-dirty-badge"]')).toBeVisible();

    // Add criteria
    await page.click('[data-testid="advancement-tab-criteria"]');
    await page.click('[data-testid="advancement-add-criteria-btn"]');
    await expect(page.locator('[data-testid="criteria-card-1"]')).toBeVisible();
    await page.fill('[data-testid="criteria-name-input-1"]', 'has_copper_pickaxe');

    // Save
    await page.click('[data-testid="advancement-save-btn"]');
    await expect(page.getByText('已保存')).toBeVisible();
    await expect(page.locator('[data-testid="advancement-dirty-badge"]')).not.toBeVisible();

    // Back to elements list and reopen
    await page.click('[data-testid="advancement-back-btn"]');
    await expect(page.locator('[data-testid="elements-workbench"]')).toBeVisible();
    await page.locator('[data-element-id]').filter({ hasText: 'copper_age' }).first().click();
    await expect(page.locator('[data-testid="advancement-workbench"]')).toBeVisible();
  });

  test('parses, previews diff, and applies language import in CreatorDataView (CSV and JSON)', async ({ page }) => {
    await page.click('[data-testid="nav-data"]');
    await expect(page.locator('[data-testid="creator-data-view"]')).toBeVisible();

    // Switch to language keys tab
    await page.click('[data-testid="tab-languageKeys"]');
    await expect(page.locator('[data-testid="language-import-btn"]')).toBeVisible();

    // 1. Open language import modal and test CSV format
    await page.click('[data-testid="language-import-btn"]');
    await expect(page.locator('[data-testid="language-import-modal"]')).toBeVisible();

    const csvContent = [
      'key,zh_cn,en_us',
      'item.copperbench.copper_dagger,铜匕首,Copper Dagger',
      'item.copperbench.ruby,至臻红宝石,Flawless Ruby',
      'block.copperbench.reinforced_copper,强化铜块,Reinforced Copper'
    ].join('\n');
    await page.fill('[data-testid="language-paste-input"]', csvContent);

    // Verify diff summary: 3 total, 2 new, 1 update
    await expect(page.locator('[data-testid="import-diff-summary"]')).toBeVisible();
    await expect(page.locator('[data-testid="import-diff-summary"]')).toContainText('成功解析 3 个词条');
    await expect(page.locator('[data-testid="import-diff-summary"]')).toContainText('新增 2 项');
    await expect(page.locator('[data-testid="import-diff-summary"]')).toContainText('更新 1 项');

    // Select conflict strategy and apply
    await page.click('[data-testid="strategy-merge"]');
    await page.click('[data-testid="confirm-language-import-btn"]');

    // Modal closed and registry table updated
    await expect(page.locator('[data-testid="language-import-modal"]')).not.toBeVisible();
    await expect(page.getByText('铜匕首')).toBeVisible();
    await expect(page.getByText('强化铜块')).toBeVisible();
    await expect(page.getByText('至臻红宝石')).toBeVisible();

    // 2. Reopen modal and test JSON format
    await page.click('[data-testid="language-import-btn"]');
    await expect(page.locator('[data-testid="language-import-modal"]')).toBeVisible();

    const jsonContent = JSON.stringify({
      'item.copperbench.sapphire': '蓝宝石',
      'block.copperbench.amethyst_lamp': '紫水晶灯'
    }, null, 2);
    await page.fill('[data-testid="language-paste-input"]', jsonContent);

    await expect(page.locator('[data-testid="import-diff-summary"]')).toContainText('成功解析 2 个词条');
    await page.click('[data-testid="confirm-language-import-btn"]');
    await expect(page.locator('[data-testid="language-import-modal"]')).not.toBeVisible();
    await expect(page.getByText('蓝宝石')).toBeVisible();
    await expect(page.getByText('紫水晶灯')).toBeVisible();

    // Filter search
    await page.fill('[data-testid="registry-search-input"]', 'dagger');
    await expect(page.getByText('item.copperbench.copper_dagger')).toBeVisible();
    await expect(page.getByText('block.copperbench.reinforced_copper')).not.toBeVisible();
  });

  test('handles large element list pagination, page size switching, and filtering', async ({ page }) => {
    // Inject a simulated host with 28 elements to exercise pagination deterministically
    await page.addInitScript(() => {
      const workspaceId = '11111111-1111-4111-8111-111111111111';
      const items = Array.from({ length: 28 }, (_, i) => ({
        id: `33333333-3333-4333-8333-${String(i).padStart(12, '0')}`,
        type: i % 2 === 0 ? 'block' : 'item',
        name: i % 2 === 0 ? `copper_block_${i}` : `copper_item_${i}`,
        displayName: i % 2 === 0 ? `Copper Block ${i}` : `Copper Item ${i}`,
        state: i % 4 === 0 ? 'draft' : 'valid',
        ownership: 'generated',
        updatedAt: `2026-08-${String(10 + (i % 15)).padStart(2, '0')}T00:00:00Z`,
        diagnostics: { error: 0, warning: 0, info: 0 }
      }));

      window.copperbenchHost = {
        workspaceId,
        invoke: async (rawJson: string) => {
          const envelope = JSON.parse(rawJson);
          if (envelope.messageType === 'handshake') {
            return JSON.stringify({
              messageType: 'handshake_result',
              requestId: envelope.requestId,
              status: 'compatible',
              selectedSchemaVersion: '1.0',
              coreSchemaVersions: ['1.0'],
              diagnostics: []
            });
          }
          if (envelope.messageType === 'query' && envelope.operation === 'get_workbench') {
            return JSON.stringify({
              messageType: 'query_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              workspaceId,
              operation: 'get_workbench',
              status: 'succeeded',
              revision: 42,
              data: {
                workspace: {
                  id: workspaceId,
                  name: 'Large Workspace',
                  kind: 'mod',
                  revision: 42,
                  dirty: false,
                  generator: { id: 'fabric-1.21.1', loader: 'fabric', minecraftVersion: '1.21.1', displayName: 'Fabric 1.21.1', state: 'ready' },
                  lock: { state: 'write_available', holder: null },
                  compatibility: { mode: 'native', unknownDataPreserved: true }
                },
                permission: { profile: 'workspace', canRequestElevation: true, protectedOperationsAlwaysConfirm: true },
                connection: { core: 'connected', network: 'online', bridge: 'ready' },
                elementCounts: { total: 28, valid: 21, invalid: 0, draft: 7, unsupported: 0 },
                activeTasks: [],
                capabilities: [{ id: 'mod_elements.create', availability: 'available', reasonCode: null, message: null, affectedPaths: [] }],
                recentElements: items.slice(0, 5)
              },
              diagnostics: []
            });
          }
          if (envelope.messageType === 'query' && envelope.operation === 'list_mod_elements') {
            return JSON.stringify({
              messageType: 'query_result',
              schemaVersion: '1.0',
              requestId: envelope.requestId,
              workspaceId,
              operation: 'list_mod_elements',
              status: 'succeeded',
              revision: 42,
              data: { items, total: 28, page: 1, pageSize: 200, availableTypes: ['block', 'item'] },
              diagnostics: []
            });
          }
          return JSON.stringify({ messageType: 'query_result', schemaVersion: '1.0', requestId: envelope.requestId, status: 'succeeded', diagnostics: [] });
        },
        onEvent: () => () => {}
      };
    });

    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
    await page.click('[data-testid="nav-elements"]');

    // 28 elements total, default pageSize = 24 -> page 1 shows 24 items, totalPages = 2
    await expect(page.locator('[data-testid="elements-pagination"]')).toBeVisible();
    await expect(page.getByText('1 / 2')).toBeVisible();
    await expect(page.getByText('显示第 1 - 24 项，共 28 个元素')).toBeVisible();

    // Next page
    await page.click('[data-testid="elements-next-page-btn"]');
    await expect(page.getByText('2 / 2')).toBeVisible();
    await expect(page.getByText('显示第 25 - 28 项，共 28 个元素')).toBeVisible();
    await expect(page.locator('[data-testid="elements-next-page-btn"]')).toBeDisabled();

    // Previous page
    await page.click('[data-testid="elements-prev-page-btn"]');
    await expect(page.getByText('1 / 2')).toBeVisible();
    await expect(page.locator('[data-testid="elements-prev-page-btn"]')).toBeDisabled();

    // Change page size to 48
    await page.selectOption('[data-testid="elements-page-size-select"]', '48');
    // When all items fit on 1 page, pagination footer is hidden (totalPages = 1)
    await expect(page.locator('[data-testid="elements-pagination"]')).not.toBeVisible();

    // Type filter: filter blocks only (14 items)
    await page.click('[data-testid="filter-type-block"]');
    await expect(page.getByText('Copper Block 0').first()).toBeVisible();
    await expect(page.getByText('Copper Item 1')).toHaveCount(0);

    // Search query filter
    await page.fill('[data-testid="elements-search-input"]', 'copper_block_12');
    await expect(page.getByText('Copper Block 12').first()).toBeVisible();
    await expect(page.getByText('Copper Block 0')).toHaveCount(0);
  });
});
