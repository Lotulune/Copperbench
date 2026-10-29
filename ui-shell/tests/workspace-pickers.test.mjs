import assert from 'node:assert/strict';
import test from 'node:test';
import { loadSsrTestModules } from './helpers/vite-ssr-loader.mjs';

async function bridgeFor(t, host) {
  const previousWindow = globalThis.window;
  globalThis.window = {
    addEventListener() {},
    __COPPERBENCH_UI_LOCALE__: 'en',
    __COPPERBENCH_WORKSPACE_OPEN_HOST__: host
  };
  t.after(() => {
    if (previousWindow === undefined) delete globalThis.window;
    else globalThis.window = previousWindow;
  });
  const [{ workspaceOpenBridge }] = await loadSsrTestModules(['/src/bridge/workspaceOpenBridge.ts']);
  return workspaceOpenBridge;
}

test('native selections and cancellation do not open a workspace implicitly', async t => {
  const calls = [];
  let selection = { cancelled: false, workspaceFile: 'D:/My creations/Trail.mcreator' };
  const bridge = await bridgeFor(t, {
    available: true, workspacePickerAvailable: true, workspaceFolderPickerAvailable: true,
    chooseWorkspace: async () => selection,
    chooseWorkspaceFolder: async initialPath => { calls.push(['folder', initialPath]); return { cancelled: false, path: 'D:/My creations' }; },
    open: async path => { calls.push(['open', path]); }
  });
  assert.equal(bridge.workspacePickerAvailable, true);
  assert.equal(bridge.workspaceFolderPickerAvailable, true);
  assert.deepEqual(await bridge.chooseWorkspace(), selection);
  selection = { cancelled: true };
  assert.deepEqual(await bridge.chooseWorkspace(), { cancelled: true });
  assert.deepEqual(await bridge.chooseWorkspaceFolder('D:/My creations/Trail'), { cancelled: false, path: 'D:/My creations' });
  assert.deepEqual(calls, [['folder', 'D:/My creations/Trail']]);
});

test('older native hosts retain manual opening without advertising unavailable pickers', async t => {
  const calls = [];
  const bridge = await bridgeFor(t, { available: true, open: async path => { calls.push(path); } });
  assert.equal(bridge.workspacePickerAvailable, false);
  assert.equal(bridge.workspaceFolderPickerAvailable, false);
  await assert.rejects(bridge.chooseWorkspace(), /Enter the path manually/);
  await assert.rejects(bridge.chooseWorkspaceFolder(), /Enter the path manually/);
  await bridge.open('D:/Existing.mcreator');
  assert.deepEqual(calls, ['D:/Existing.mcreator']);
});

test('capability flags require callable host methods and picker errors reach the caller', async t => {
  const failure = new Error('The selected location is unavailable');
  const bridge = await bridgeFor(t, {
    available: true, workspacePickerAvailable: true, workspaceFolderPickerAvailable: true,
    chooseWorkspace: async () => { throw failure; },
    open: async () => {}
  });
  assert.equal(bridge.workspaceFolderPickerAvailable, false);
  await assert.rejects(bridge.chooseWorkspace(), error => error === failure);
});

test('browser-only hosts have no native picker capabilities', async t => {
  const bridge = await bridgeFor(t, undefined);
  assert.equal(bridge.available, false);
  assert.equal(bridge.workspacePickerAvailable, false);
  assert.equal(bridge.workspaceFolderPickerAvailable, false);
  await assert.rejects(bridge.chooseWorkspace(), /Enter the path manually/);
});
