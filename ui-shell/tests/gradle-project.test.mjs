import assert from 'node:assert/strict';
import test from 'node:test';
import { loadSsrTestModules } from './helpers/vite-ssr-loader.mjs';

function withWindow(t, extra = {}) {
  const previousWindow = globalThis.window;
  globalThis.window = { addEventListener() {}, __COPPERBENCH_UI_LOCALE__: 'en', ...extra };
  t.after(() => {
    if (previousWindow === undefined) delete globalThis.window;
    else globalThis.window = previousWindow;
  });
  return globalThis.window;
}

test('an adopted Gradle project offers no view that needs the MCreator workspace model', async t => {
  withWindow(t);
  const [{ workbenchNavigation, viewAvailable }] = await loadSsrTestModules(['/src/components/workbenchNavigation.ts']);
  const workspace = workbenchNavigation('mod').map(item => item.id);
  const project = workbenchNavigation('gradle_project').map(item => item.id);
  for (const view of ['elements', 'relations', 'data', 'tracks', 'plugins']) {
    assert.ok(workspace.includes(view), view);
    assert.ok(!project.includes(view), view);
    assert.equal(viewAvailable(view, 'gradle_project'), false);
  }
  for (const view of ['hub', 'assets', 'source', 'history', 'ai', 'python', 'help', 'settings'])
    assert.ok(project.includes(view), view);
  // Before the workbench loads, and for other kinds, nothing is hidden.
  assert.deepEqual(workbenchNavigation(undefined).map(item => item.id), workspace);
  assert.deepEqual(workbenchNavigation('data_pack').map(item => item.id), workspace);
});

test('project settings go to the native host as exact requests and return its state', async t => {
  const window = withWindow(t);
  const [{ gradleProjectBridge }] = await loadSsrTestModules(['/src/bridge/gradleProjectBridge.ts']);
  assert.equal(gradleProjectBridge.available, false);
  await assert.rejects(gradleProjectBridge.status(), /desktop app/);

  // The host installs itself when a page load starts, which can be after the module was imported.
  const requests = [];
  const state = { projectRoot: 'D:/mods/emberkit', gitIgnore: { repository: true, ignored: false, decision: null },
    gradleUserHome: { mode: 'product', product: 'C:/Users/me/.copperbench/gradle', environment: 'C:/Users/me/.gradle' } };
  window.__COPPERBENCH_GRADLE_PROJECT_HOST__ = { available: true, request: async payload => { requests.push(payload); return state; } };
  assert.equal(gradleProjectBridge.available, true);
  assert.deepEqual(await gradleProjectBridge.status(), state);
  await gradleProjectBridge.decideGitIgnore(false);
  await gradleProjectBridge.setGradleUserHome('environment');
  assert.deepEqual(requests, [
    { operation: 'status' },
    { operation: 'git_ignore', add: false },
    { operation: 'gradle_user_home', mode: 'environment' }
  ]);

  window.__COPPERBENCH_GRADLE_PROJECT_HOST__ = { available: false, request: async () => state };
  assert.equal(gradleProjectBridge.available, false);
});
