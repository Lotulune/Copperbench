import { test, expect } from '@playwright/test';

test.describe('Adaptive Layout, Frameless Window & Theme Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForSelector('[data-testid="app-shell"]');
  });

  test('theme toggle switches between dark and light modes', async ({ page }) => {
    const html = page.locator('html');
    await expect(html).toHaveAttribute('data-theme', 'dark');

    // Click theme toggle
    await page.click('[data-testid="theme-toggle-btn"]');
    await expect(html).toHaveAttribute('data-theme', 'light');

    // Click back to dark
    await page.click('[data-testid="theme-toggle-btn"]');
    await expect(html).toHaveAttribute('data-theme', 'dark');
  });

  test('system window frame fallback toggle hides custom window buttons (NFR-UI-06)', async ({ page }) => {
    // Custom window controls are initially visible
    await expect(page.locator('[data-testid="window-minimize-btn"]')).toBeVisible();
    await expect(page.locator('[data-testid="window-maximize-btn"]')).toBeVisible();
    await expect(page.locator('[data-testid="window-close-btn"]')).toBeVisible();

    // Toggle fallback
    await page.click('[data-testid="system-fallback-toggle-btn"]');

    // Custom window controls should now be hidden
    await expect(page.locator('[data-testid="window-minimize-btn"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="window-maximize-btn"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="window-close-btn"]')).not.toBeVisible();

    // Toggle back
    await page.click('[data-testid="system-fallback-toggle-btn"]');
    await expect(page.locator('[data-testid="window-minimize-btn"]')).toBeVisible();
  });

  test('window maximize button toggles maximized state', async ({ page }) => {
    const maxBtn = page.locator('[data-testid="window-maximize-btn"]');
    await expect(maxBtn).toBeVisible();
    await maxBtn.click();
    await expect(maxBtn).toHaveAttribute('title', '恢复');
    await maxBtn.click();
    await expect(maxBtn).toHaveAttribute('title', '最大化');
  });


  test('frameless titlebar computed and inline -webkit-app-region is not drag while explicit pointer bridge is used', async ({ page }) => {
    const titlebar = page.locator('[data-testid="frameless-titlebar"]');
    await expect(titlebar).toBeVisible();

    const appRegion = await titlebar.evaluate((element) => {
      const computed = window.getComputedStyle(element) as CSSStyleDeclaration & {
        webkitAppRegion?: string;
      };
      return {
        inline: element.style.webkitAppRegion,
        computed: computed.webkitAppRegion || window.getComputedStyle(element).getPropertyValue('-webkit-app-region')
      };
    });
    expect(appRegion.inline).not.toBe('drag');
    expect(appRegion.computed).not.toBe('drag');

    const minBtn = page.locator('[data-testid="window-minimize-btn"]');
    await expect(minBtn).toBeVisible();
    await expect(minBtn).toBeEnabled();
  });
  test('reports typed chrome regions to a compatible native host', async ({ page }) => {
    await page.addInitScript(() => {
      window.__COPPERBENCH_WINDOW_HOST__ = {
        systemFrame: false,
        chromeRegionSchemaVersion: '1.0',
        invoke: async () => undefined,
        reportChromeRegions: async (snapshot) => {
          window.sessionStorage.setItem('windowChromeSnapshot', JSON.stringify(snapshot));
        }
      };
    });
    await page.reload();
    await page.waitForSelector('[data-testid="app-shell"]');

    await expect.poll(() => page.evaluate(() => window.sessionStorage.getItem('windowChromeSnapshot')))
      .not.toBeNull();
    const snapshot = await page.evaluate(() => JSON.parse(window.sessionStorage.getItem('windowChromeSnapshot')!));
    expect(snapshot).toMatchObject({
      schemaVersion: '1.0',
      coordinateSpace: 'css_viewport',
      devicePixelRatio: 1,
      viewport: { width: page.viewportSize()!.width, height: page.viewportSize()!.height }
    });
    expect(snapshot.sequence).toBeGreaterThan(0);
    expect(snapshot.regions.map((region: { id: string }) => region.id)).toEqual(expect.arrayContaining([
      'titlebar', 'build', 'run-client', 'theme', 'system-frame-fallback', 'minimize', 'maximize', 'close'
    ]));
    expect(snapshot.regions.find((region: { id: string }) => region.id === 'maximize').kind).toBe('maximize');
    expect(snapshot.regions.every((region: { bounds: { width: number; height: number } }) =>
      region.bounds.width > 0 && region.bounds.height > 0)).toBe(true);
  });

  test('keeps titlebar controls usable at the 500px snap target', async ({ page }) => {
    await page.setViewportSize({ width: 500, height: 700 });

    const titlebar = page.locator('[data-testid="frameless-titlebar"]');
    await expect(titlebar).toBeVisible();
    await expect(page.locator('[data-testid="titlebar-workspace"]')).toBeHidden();
    await expect(page.locator('[data-testid="titlebar-build-btn"]')).toBeVisible();
    await expect(page.locator('[data-testid="window-close-btn"]')).toBeVisible();
    await expect(page.locator('[data-testid="titlebar-build-btn"] span')).toBeHidden();
    await expect(titlebar).toHaveJSProperty('scrollWidth', 500);
  });

  test('navigation rail tabs switch views properly', async ({ page }) => {
    await page.click('[data-testid="nav-assets"]');
    await expect(page.getByText('资产与 Blockbench 集成')).toBeVisible();

    await page.click('[data-testid="nav-history"]');
    await expect(page.getByRole('heading', { name: '本地历史' })).toBeVisible();

    await page.click('[data-testid="nav-ai"]');
    await expect(page.getByRole('heading', { name: 'AI 与 MCP' })).toBeVisible();


    await page.click('[data-testid="nav-plugins"]');
    await expect(page.getByText('MCreator 插件兼容中心')).toBeVisible();
    await expect(page.locator('[data-testid="installed-plugin-inventory"]')).toContainText('Generator 1.21.1');
    await expect(page.locator('[data-testid="upstream-tool-catalog"]')).toContainText('legacy_window');
    await expect(page.locator('[data-testid="open-legacy-plugin-window"]')).toBeDisabled();

    await page.click('[data-testid="nav-help"]');
    await expect(page.locator('[data-testid="help-view"]')).toBeVisible();
    await expect(page.locator('[data-testid="about-panel"]')).toBeVisible();
    await expect(page.locator('[data-testid="about-panel"]')).toContainText('Copperbench 0.1.0');

    await page.click('[data-testid="nav-hub"]');
    await expect(page.locator('[data-testid="workbench-main"]')).toBeVisible();
  });

  test('help/about view displays honest facts, honest track statuses, and user guide content', async ({ page }) => {
    await page.click('[data-testid="nav-help"]');
    const helpView = page.locator('[data-testid="help-view"]');
    await expect(helpView).toBeVisible();

    // Header and dev-build badge
    await expect(helpView).toContainText('帮助与使用说明');
    await expect(helpView).toContainText('开发测试版');

    // About panel facts
    const aboutPanel = page.locator('[data-testid="about-panel"]');
    await expect(aboutPanel).toBeVisible();
    await expect(aboutPanel).toContainText('Copperbench 0.1.0');
    await expect(aboutPanel).toContainText('GPL-3.0');
    await expect(aboutPanel).toContainText('MCreator 2026.2.33518');
    await expect(aboutPanel).toContainText('开发测试版');
    await expect(aboutPanel).toContainText('未生产签名');

    // Honest version tracks table
    const tracksTable = page.locator('[data-testid="help-tracks-table"]');
    await expect(tracksTable).toBeVisible();
    await expect(page.locator('[data-testid="help-track-row-1.21.1"]')).toContainText('正式支持');
    await expect(page.locator('[data-testid="help-track-row-26.2"]')).toContainText('Fabric / NeoForge 正式支持');
    await expect(page.locator('[data-testid="help-track-row-26.1"]')).toContainText('Fabric / NeoForge 正式支持');
    await expect(page.locator('[data-testid="help-track-row-1.20.1"]')).toContainText('Fabric / NeoForge 正式支持');

    // User guide sections
    await expect(page.locator('[data-testid="guide-section-workspace"]')).toContainText('工作区');
    await expect(page.locator('[data-testid="guide-section-mod-elements"]')).toContainText('模组元素');
    await expect(page.locator('[data-testid="guide-section-mod-elements"]')).toContainText('可视化创建');
    await expect(page.locator('[data-testid="guide-section-local-history"]')).toContainText('本地历史');
    await expect(page.locator('[data-testid="guide-section-mcp-permissions"]')).toContainText('MCP 权限');
    await expect(page.locator('[data-testid="guide-section-blockbench-assets"]')).toContainText('Blockbench 与资源包');
    await expect(page.locator('[data-testid="guide-section-loader-migration"]')).toContainText('加载器迁移');
    await expect(page.locator('[data-testid="guide-section-plugins"]')).toContainText('插件');
    await expect(page.locator('[data-testid="guide-section-install-uninstall"]')).toContainText('安装与卸载');

    // Navigation link from help to tracks view
    await page.click('[data-testid="help-to-tracks-btn"]');
    await expect(page.locator('[data-testid="tracks-view"]')).toBeVisible();
  });

  test('help page and titlebar do not overflow at compact, standard, and wide viewports', async ({ page }) => {
    const testViewports = [
      { width: 1280, height: 720 },
      { width: 1366, height: 768 },
      { width: 1920, height: 1080 },
      { width: 2560, height: 1440 }
    ];

    for (const vp of testViewports) {
      await page.setViewportSize(vp);
      await page.click('[data-testid="nav-help"]');
      await expect(page.locator('[data-testid="help-view"]')).toBeVisible();

      const layout = await page.evaluate(() => {
        const titlebar = document.querySelector<HTMLElement>('[data-testid="frameless-titlebar"]');
        const controls = Array.from(titlebar?.querySelectorAll<HTMLElement>('button') ?? []);
        return {
          documentWidth: document.documentElement.scrollWidth,
          documentHeight: document.documentElement.scrollHeight,
          viewport: { width: window.innerWidth, height: window.innerHeight },
          controls: controls.map((c) => c.getBoundingClientRect().toJSON())
        };
      });

      expect(layout.documentWidth).toBeLessThanOrEqual(vp.width);
      expect(layout.documentHeight).toBeLessThanOrEqual(vp.height);

      for (const ctrl of layout.controls) {
        expect(ctrl.left).toBeGreaterThanOrEqual(0);
        expect(ctrl.right).toBeLessThanOrEqual(vp.width);
      }
    }
  });

  test('reports updated chrome regions dynamically after viewport resize', async ({ page }) => {
    await page.addInitScript(() => {
      window.__COPPERBENCH_WINDOW_HOST__ = {
        systemFrame: false,
        chromeRegionSchemaVersion: '1.0',
        invoke: async () => undefined,
        reportChromeRegions: async (snapshot) => {
          const history = JSON.parse(window.sessionStorage.getItem('windowChromeHistory') || '[]');
          history.push(snapshot);
          window.sessionStorage.setItem('windowChromeHistory', JSON.stringify(history));
          window.sessionStorage.setItem('windowChromeSnapshot', JSON.stringify(snapshot));
        }
      };
    });
    await page.reload();
    await page.waitForSelector('[data-testid="app-shell"]');

    await expect.poll(() => page.evaluate(() => window.sessionStorage.getItem('windowChromeSnapshot')))
      .not.toBeNull();

    const initial = await page.evaluate(() => JSON.parse(window.sessionStorage.getItem('windowChromeSnapshot')!));
    expect(initial.regions.length).toBeGreaterThan(0);

    // Resize viewport
    await page.setViewportSize({ width: 1400, height: 900 });

    await expect.poll(async () => {
      const snap = await page.evaluate(() => JSON.parse(window.sessionStorage.getItem('windowChromeSnapshot')!));
      return snap.viewport.width;
    }).toBe(1400);

    const updated = await page.evaluate(() => JSON.parse(window.sessionStorage.getItem('windowChromeSnapshot')!));
    expect(updated.sequence).toBeGreaterThan(initial.sequence);
    expect(updated.viewport).toEqual({ width: 1400, height: 900 });
    expect(updated.regions.every((r: { bounds: { width: number; height: number } }) =>
      r.bounds.width > 0 && r.bounds.height > 0)).toBe(true);
  });
});
