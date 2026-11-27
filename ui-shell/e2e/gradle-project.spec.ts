import { test, expect, type Page } from '@playwright/test';

/** A stand-in for the desktop host that keeps the project's settings and records what the page asked for. */
async function installProjectHost(page: Page, repository = true) {
  await page.addInitScript(inRepository => {
    const state = {
      projectRoot: 'D:\\mods\\emberkit',
      gitIgnore: { repository: inRepository, ignored: false, decision: null as 'added' | 'declined' | null },
      gradleUserHome: { mode: 'product' as 'product' | 'environment', product: 'C:\\Users\\me\\.copperbench\\gradle', environment: 'C:\\Users\\me\\.gradle' }
    };
    const requests: unknown[] = [];
    (window as unknown as { __projectRequests: unknown[] }).__projectRequests = requests;
    window.__COPPERBENCH_GRADLE_PROJECT_HOST__ = {
      available: true,
      request: async payload => {
        requests.push(payload);
        if (payload.operation === 'git_ignore') {
          state.gitIgnore.decision = payload.add ? 'added' : 'declined';
          if (payload.add) state.gitIgnore.ignored = true;
        } else if (payload.operation === 'gradle_user_home') state.gradleUserHome.mode = payload.mode;
        return JSON.parse(JSON.stringify(state));
      }
    };
  }, repository);
}

async function openProject(page: Page) {
  await page.goto('/');
  await page.waitForSelector('[data-testid="app-shell"]');
  await page.click('[data-testid="scenario-switcher-trigger"]');
  await page.click('[data-testid="scenario-btn-gradle-project"]');
  await expect(page.getByTestId('gradle-project-panel')).toBeVisible();
  await settle(page);
}

/** Clicks during a view's entrance animation are not stable in compact layouts. */
async function settle(page: Page) {
  await page.waitForFunction(() => document.getAnimations().every(animation => (animation as CSSAnimation).animationName !== 'rise-in' || animation.playState !== 'running'));
}

/** The requests that would change the project; status reads may repeat while the page mounts. */
const changes = (page: Page) => page.evaluate(() => (window as unknown as { __projectRequests: { operation: string }[] }).__projectRequests
  .filter(request => request.operation !== 'status'));

test.describe('Adopted Gradle project (ADR-0020)', () => {
  test('the workbench offers only what an adopted project supports', async ({ page }) => {
    await installProjectHost(page);
    await openProject(page);

    await expect(page.getByTestId('hub-target')).toHaveText('Fabric 1.21.1');
    await expect(page.getByTestId('gradle-project-target')).toHaveText('Fabric 1.21.1');
    await expect(page.getByTestId('gradle-project-panel')).toContainText('emberkit');
    await expect(page.getByTestId('gradle-project-panel')).toContainText('D:\\mods\\emberkit');
    await expect(page.getByTestId('empty-primary-action')).toHaveCount(0);
    await expect(page.getByTestId('hub-tracks-badge')).toHaveCount(0);
    await expect(page.getByTestId('workspace-health-panel')).toHaveCount(0);
    await expect(page.getByTestId('hub-source-entries')).toBeVisible();

    // Only the professional workbench exists for a project edited as source files.
    await expect(page.getByTestId('workbench-mode-locked')).toBeAttached();
    await expect(page.getByTestId('workbench-mode-control')).toHaveCount(0);
    for (const view of ['elements', 'relations', 'data', 'tracks', 'plugins'])
      await expect(page.getByTestId(`nav-${view}`)).toHaveCount(0);
    for (const view of ['hub', 'assets', 'source', 'history'])
      await expect(page.getByTestId(`nav-${view}`)).toHaveCount(1);

    await settle(page);
    await page.getByTestId('compact-run-menu').click();
    await expect(page.getByTestId('compact-open-jar-folder-btn')).toBeVisible();
    await expect(page.getByTestId('titlebar-generate-btn')).toHaveCount(0);
    await expect(page.getByRole('button', { name: '在暂存区运行数据生成' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '运行已有 GameTest' })).toBeVisible();
  });

  test('the .gitignore question changes nothing until the user answers', async ({ page }) => {
    await installProjectHost(page);
    await openProject(page);

    const question = page.getByTestId('gradle-project-gitignore');
    await expect(question).toBeVisible();
    await expect(page.getByTestId('gradle-project-gradle-home')).toBeVisible();
    expect(await changes(page)).toEqual([]);
    await page.getByTestId('gradle-project-gitignore-add').click();
    await expect(question).toHaveCount(0);
    await expect(page.getByTestId('gradle-project-gitignore-state')).toContainText('.gitignore');
    await expect.poll(() => changes(page)).toEqual([{ operation: 'git_ignore', add: true }]);
  });

  test('declining keeps the project files and offers the change later', async ({ page }) => {
    await installProjectHost(page);
    await openProject(page);

    await page.getByTestId('gradle-project-gitignore-decline').click();
    await expect(page.getByTestId('gradle-project-gitignore')).toHaveCount(0);
    const state = page.getByTestId('gradle-project-gitignore-state');
    await expect(state).toBeVisible();
    await expect(state.getByRole('button')).toBeVisible();
    await expect.poll(() => changes(page)).toEqual([{ operation: 'git_ignore', add: false }]);
  });

  test('outside a Git repository there is no .gitignore question', async ({ page }) => {
    await installProjectHost(page, false);
    await openProject(page);
    await expect(page.getByTestId('gradle-project-gradle-home')).toBeVisible();
    await expect(page.getByTestId('gradle-project-gitignore')).toHaveCount(0);
    await expect(page.getByTestId('gradle-project-gitignore-state')).toHaveCount(0);
  });

  test('builds can use the environment Gradle user home instead of the product one', async ({ page }) => {
    await installProjectHost(page);
    await openProject(page);

    const choice = page.getByTestId('gradle-project-gradle-home');
    await expect(choice.locator('input[value="product"]')).toBeChecked();
    await expect(choice).toContainText('C:\\Users\\me\\.copperbench\\gradle');
    await choice.locator('input[value="environment"]').check();
    await expect(choice.locator('input[value="environment"]')).toBeChecked();
    await expect.poll(() => changes(page)).toEqual([{ operation: 'gradle_user_home', mode: 'environment' }]);
  });

  test('without the desktop host the page says where the settings live', async ({ page }) => {
    await openProject(page);
    await expect(page.getByTestId('gradle-project-panel')).toContainText('桌面应用');
    await expect(page.getByTestId('gradle-project-gradle-home')).toHaveCount(0);
  });
});
