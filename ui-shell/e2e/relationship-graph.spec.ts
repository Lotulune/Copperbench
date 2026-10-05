import { expect, test, type Locator, type Page } from '@playwright/test';

const distantId = 'aaaaaaaa-aaaa-4aaa-8aaa-000000000039';

async function installGraphFixture(page: Page, large = false) {
  await page.addInitScript(({ large }) => {
    const modulePath = '/src/mock/mockBridge.ts';
    const ready = import(modulePath).then(({ MockCoreBridge }) => {
      const core = new MockCoreBridge();
      const state = core.getState();
      const seed = state.elements.find(element => element.type === 'block');
      const count = large ? 2000 : 40;
      for (let index = 0; index < count; index++) state.elements.push({ ...seed,
        id: `aaaaaaaa-aaaa-4aaa-8aaa-${String(index).padStart(12, '0')}`,
        name: `graph_block_${String(index).padStart(4, '0')}`, displayName: `Graph Block ${String(index).padStart(4, '0')}` });
      return core;
    });
    (window as unknown as { graphQueryTargets: unknown[] }).graphQueryTargets = [];
    window.copperbenchHost = {
      workspaceId: '11111111-1111-4111-8111-111111111111', onEvent() { return () => {}; },
      async invoke(raw) {
        const core = await ready;
        const request = JSON.parse(raw);
        const result = request.messageType === 'handshake' ? await core.negotiateHandshake(request)
          : request.messageType === 'command' ? await core.sendCommand(request) : await core.sendQuery(request);
        const state = core.getState();
        if (request.operation === 'list_mod_elements') {
          // The graph must not depend on the currently loaded list subset.
          result.data.items = state.elements.slice(0, 2); result.data.nextCursor = null;
        }
        if (request.operation === 'get_workspace_references') {
          (window as unknown as { graphQueryTargets: unknown[] }).graphQueryTargets.push(request.payload);
          const source = state.elements.find(element => element.name === 'copper_lamp');
          const distant = state.elements.find(element => element.name === 'graph_block_0039');
          const edges = large ? Array.from({ length: 10000 }, (_, index) => ({
            id: `edge-${index}`, sourceId: state.elements[index % state.elements.length].id,
            sourcePath: `/references/${index}`, target: `unresolved_${index}`, targetId: null, kind: 'element', resolution: 'missing'
          })) : [
            { id: 'across-pages', sourceId: source.id, sourcePath: '/next', target: distant.name,
              targetId: distant.id, kind: 'element', resolution: 'resolved' },
            { id: 'missing-model', sourceId: source.id, sourcePath: '/model', target: 'missing_graph_model',
              targetId: null, kind: 'resource', resolution: 'missing' }
          ];
          const diagnostics = (large ? edges : [edges[1]]).map(edge => ({ code: 'GRAPH_TARGET_MISSING', severity: 'error',
            message: { key: 'graph.fixture.missing', fallback: 'The referenced target is missing.' },
            path: `/elements/${edge.sourceId}${edge.sourcePath}`, elementId: edge.sourceId, recoverable: true, actions: [] }));
          result.data = { revision: state.workbench.workspace.revision,
            nodes: state.elements.map(element => ({ id: element.id, kind: 'element', type: element.type, name: element.name, displayName: element.displayName })),
            edges, diagnostics, stats: { indexedElements: state.elements.length, edgeCount: edges.length, incremental: true } };
        }
        if (request.operation === 'list_assets') {
          if ((window as unknown as { graphMismatchAssetRevision?: boolean }).graphMismatchAssetRevision) result.revision -= 1;
          const seed = result.data.assets[0];
          const asset = (id: string, relativePath: string, category: string) => ({ ...seed, id, relativePath, category,
            health: { ...seed.health, assetId: id, relativePath, status: 'READY', issueCodes: [] } });
          result.data.assets = [
            asset('graph-model', 'assets/copperbench/models/block/graph_source.json', 'MODEL'),
            asset('graph-texture', 'assets/copperbench/textures/block/graph_remote.png', 'TEXTURE')
          ];
          result.data.references = [
            { sourceAssetId: 'graph-model', sourcePath: result.data.assets[0].relativePath, sourcePointer: '/textures/all',
              rawValue: 'copperbench:block/graph_remote', expectedPrefix: 'textures', targetPath: result.data.assets[1].relativePath,
              targetAssetId: 'graph-texture', kind: 'RESOURCE_ID', resolution: 'workspace_resolved' },
            { sourceAssetId: 'graph-model', sourcePath: result.data.assets[0].relativePath, sourcePointer: '/parent',
              rawValue: 'minecraft:block/cube_all', expectedPrefix: 'models', targetPath: 'minecraft:block/cube_all',
              targetAssetId: null, kind: 'RESOURCE_ID', resolution: 'vanilla_resolved' }
          ];
          result.data.diagnostics = [];
        }
        return JSON.stringify(result);
      }
    };
  }, { large });
}

async function openGraph(page: Page) {
  await page.goto('/');
  await page.getByTestId('nav-relations').click();
  await expect(page.getByTestId('relationship-canvas')).toBeVisible();
}

async function reveal(page: Page, query: string) {
  await page.getByTestId('relationship-search').fill(query);
  await page.getByTestId('relationship-search').press('Enter');
}

async function nodePosition(node: Locator) {
  const transform = await node.getAttribute('transform');
  const [x, y] = transform!.slice('translate('.length, -1).split(' ').map(Number);
  return { x, y };
}

async function cameraZoom(page: Page) {
  const transform = await page.getByTestId('relationship-transform').getAttribute('transform');
  return Number(/scale\(([^)]+)\)/.exec(transform!)![1]);
}

async function dragNode(page: Page, node: Locator, dx: number, dy: number) {
  const bounds = await node.locator('rect.relationship-node-surface').boundingBox();
  const x = bounds!.x + bounds!.width / 2; const y = bounds!.y + bounds!.height / 2;
  await page.mouse.move(x, y); await page.mouse.down();
  await page.mouse.move(x + dx, y + dy, { steps: 8 }); await page.mouse.up();
}

for (const targetZoom of [0.55, 1.8]) {
  test(`dragging a node at ${targetZoom} zoom moves its links without panning or opening the editor`, async ({ page }) => {
    await installGraphFixture(page);
    await openGraph(page);
    await reveal(page, 'Graph Block 0039');
    const node = page.locator(`[data-graph-node][data-element-id="${distantId}"]`);
    const svg = await page.getByTestId('relationship-svg').boundingBox();
    await page.mouse.move(svg!.x + svg!.width / 2, svg!.y + svg!.height / 2);
    await page.mouse.wheel(0, -Math.log(targetZoom / await cameraZoom(page)) / 0.0015);
    await expect.poll(() => cameraZoom(page)).toBeCloseTo(targetZoom, 2);
    const zoom = await cameraZoom(page);
    const camera = await page.getByTestId('relationship-transform').getAttribute('transform');
    const before = await nodePosition(node);
    const root = await page.locator('[data-node-kind="workspace"]').getAttribute('transform');
    const nodeId = await node.getAttribute('data-graph-node');
    const incoming = page.locator(`[data-edge-relation="hierarchy"][data-edge-target="${nodeId}"] > path`).first();
    const path = await incoming.getAttribute('d');
    const requests = await page.evaluate(() => (window as unknown as { graphQueryTargets: unknown[] }).graphQueryTargets.length);
    await dragNode(page, node, 96, 48);
    await expect(page.getByTestId('relationship-canvas')).toBeVisible();
    const after = await nodePosition(node);
    expect(after.x - before.x).toBeCloseTo(96 / zoom, 1);
    expect(after.y - before.y).toBeCloseTo(48 / zoom, 1);
    await expect(page.getByTestId('relationship-transform')).toHaveAttribute('transform', camera!);
    await expect(page.locator('[data-node-kind="workspace"]')).toHaveAttribute('transform', root!);
    await expect(incoming).not.toHaveAttribute('d', path!);
    await expect(incoming).toHaveAttribute('d', new RegExp(`${after.x},${after.y + 29}$`));
    expect(await page.evaluate(() => (window as unknown as { graphQueryTargets: unknown[] }).graphQueryTargets.length)).toBe(requests);
    await expect(page.getByTestId('relationship-restore-layout')).toBeEnabled();
    await page.getByTestId('relationship-restore-layout').click();
    expect(await nodePosition(node)).toEqual(before);
    await expect(page.getByTestId('relationship-restore-layout')).toBeDisabled();
    await node.locator('rect.relationship-node-surface').click();
    await expect(page.getByTestId('field-name')).toHaveValue('graph_block_0039');
  });
}

test('groups drag independently, cancellation restores a node, and sub-threshold clicks still open', async ({ page }) => {
  await installGraphFixture(page);
  await openGraph(page);
  const group = page.locator('[data-node-kind="group"]').filter({ hasText: '方块' });
  const groupBefore = await nodePosition(group);
  await expect(group).toHaveAttribute('aria-expanded', 'false');
  await dragNode(page, group, 35, 25);
  expect(await nodePosition(group)).not.toEqual(groupBefore);
  await expect(group).toHaveAttribute('aria-expanded', 'false');
  await reveal(page, 'Graph Block 0039');
  const node = page.locator(`[data-graph-node][data-element-id="${distantId}"]`);
  const before = await nodePosition(node);
  const bounds = await node.locator('rect.relationship-node-surface').boundingBox();
  const x = bounds!.x + bounds!.width / 2; const y = bounds!.y + bounds!.height / 2;
  await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.move(x + 60, y + 30, { steps: 4 });
  await expect(node).toHaveClass(/is-dragging/);
  await node.dispatchEvent('pointercancel', { pointerId: 1 });
  await page.mouse.up();
  await expect(node).not.toHaveClass(/is-dragging/);
  expect(await nodePosition(node)).toEqual(before);
  await dragNode(page, node, 40, 20);
  expect(await nodePosition(node)).not.toEqual(before);
  await page.getByTestId('relationship-restore-layout').click();
  // A normal click may have a small amount of hand movement.
  await dragNode(page, node, 2, 1);
  await expect(page.getByTestId('field-name')).toHaveValue('graph_block_0039');
});

test('keyboard and move controls arrange negative positions, fit them, and restore the tree', async ({ page }) => {
  await installGraphFixture(page);
  await openGraph(page);
  const root = page.locator('[data-node-kind="workspace"]');
  const before = await nodePosition(root);
  const camera = await page.getByTestId('relationship-transform').getAttribute('transform');
  await root.focus();
  for (let index = 0; index < 10; index++) await root.press('Shift+ArrowLeft');
  expect((await nodePosition(root)).x).toBeLessThan(0);
  await expect(root).toBeFocused();
  await expect(page.getByTestId('relationship-transform')).toHaveAttribute('transform', camera!);
  await page.getByTestId('relationship-fit').click();
  const viewport = await page.getByTestId('relationship-svg').boundingBox();
  for (const surface of await page.locator('rect.relationship-node-surface').all()) {
    const bounds = await surface.boundingBox();
    expect(bounds!.x).toBeGreaterThanOrEqual(viewport!.x);
    expect(bounds!.y).toBeGreaterThanOrEqual(viewport!.y);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(viewport!.x + viewport!.width);
    expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(viewport!.y + viewport!.height);
  }
  await page.getByTestId('relationship-move-controls').click();
  const position = await nodePosition(root);
  await page.getByRole('button', { name: '节点向右移动', exact: true }).click();
  expect((await nodePosition(root)).x).toBeGreaterThan(position.x);
  await page.getByTestId('relationship-restore-layout').click();
  expect(await nodePosition(root)).toEqual(before);
  await root.focus(); await root.press('ArrowRight');
  expect(await nodePosition(root)).toEqual(before);
  await expect(root).not.toBeFocused();
  await reveal(page, 'minecraft:block/cube_all');
  await page.getByRole('button', { name: '关闭关系详情' }).click();
  const target = page.locator('[data-node-kind="unresolved"]').filter({ hasText: 'minecraft:block/cube_all' });
  const targetBefore = await nodePosition(target);
  await dragNode(page, target, -45, 25);
  expect(await nodePosition(target)).not.toEqual(targetBefore);
  await expect(page.getByRole('complementary', { name: '关系详情' })).not.toBeVisible();
});

test('full graph uses explicit empty target and supports pan, zoom, collapse and keyboard reveal', async ({ page }) => {
  await page.addInitScript(() => Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true }));
  const errors: string[] = [];
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('pageerror', error => errors.push(error.message));
  await installGraphFixture(page);
  await openGraph(page);
  await expect(page.locator('[data-node-kind="workspace"]')).toContainText('Copper Trails');
  const queryTargets = await page.evaluate(() => (window as unknown as { graphQueryTargets: unknown[] }).graphQueryTargets);
  expect(queryTargets.length).toBeGreaterThan(0);
  for (const payload of queryTargets) expect(payload).toEqual({ target: '' });
  const blockGroup = page.locator('[data-node-kind="group"]').filter({ hasText: '方块' });
  await expect(blockGroup).toHaveAttribute('aria-expanded', 'false');
  await blockGroup.click();
  await expect(blockGroup).toHaveAttribute('aria-expanded', 'true');
  await expect(page.locator('.relationship-edge-label').filter({ hasText: 'RESOURCE_ID' }).first()).toBeVisible();
  const svg = page.getByTestId('relationship-svg');
  const transform = page.getByTestId('relationship-transform');
  const initial = await transform.getAttribute('transform');
  const bounds = await svg.boundingBox();
  await page.mouse.move(bounds!.x + 30, bounds!.y + bounds!.height - 90);
  await page.mouse.down(); await page.mouse.move(bounds!.x + 100, bounds!.y + bounds!.height - 60); await page.mouse.up();
  await expect(transform).not.toHaveAttribute('transform', initial!);
  const zoom = await transform.getAttribute('data-zoom');
  await page.mouse.wheel(0, -180);
  await expect(transform).not.toHaveAttribute('data-zoom', zoom!);
  await page.getByTestId('relationship-collapse-all').click();
  await expect(page.locator('[data-node-kind="asset"]')).toHaveCount(0);
  await reveal(page, 'Graph Block 0039');
  const node = page.locator(`[data-graph-node][data-element-id="${distantId}"]`);
  await expect(node).toBeVisible();
  await expect(node).toBeFocused();
  await node.press('Home');
  await expect(page.locator('[data-node-kind="workspace"]')).toBeFocused();
  expect(await page.evaluate(() => (window as unknown as { graphQueryTargets: unknown[] }).graphQueryTargets.length)).toBe(queryTargets.length);
  await node.press('Enter');
  await expect(page.getByTestId('field-name')).toHaveValue('graph_block_0039');
  expect(errors).toEqual([]);
});

test('off-page element and asset nodes open the existing exact editors', async ({ page }) => {
  await installGraphFixture(page);
  await openGraph(page);
  await reveal(page, 'Graph Block 0039');
  await page.locator(`[data-graph-node][data-element-id="${distantId}"]`).click();
  await expect(page.getByTestId('element-inspector')).toBeVisible();
  await expect(page.getByTestId('field-name')).toHaveValue('graph_block_0039');
  await page.getByTestId('nav-relations').click();
  await expect(page.getByTestId('relationship-canvas')).toBeVisible();
  await reveal(page, 'graph_remote.png');
  await page.locator('[data-graph-node][data-asset-id="graph-texture"]').click();
  await expect(page.getByTestId('asset-details')).toBeVisible();
  await expect(page.getByTestId('asset-stable-id')).toHaveText('graph-texture');
});

test('null targets retain resolution and diagnostics, and cross-page references remain inspectable', async ({ page }) => {
  await installGraphFixture(page);
  await openGraph(page);
  await reveal(page, 'minecraft:block/cube_all');
  await page.locator('[data-node-kind="unresolved"]').filter({ hasText: 'minecraft:block/cube_all' }).click();
  await expect(page.getByRole('complementary', { name: '关系详情' })).toContainText('vanilla_resolved');
  await expect(page.getByRole('complementary', { name: '关系详情' })).not.toContainText('GRAPH_TARGET_MISSING');
  await page.getByRole('button', { name: '关闭关系详情' }).click();
  await reveal(page, 'missing_graph_model');
  await expect(page.getByRole('complementary', { name: '关系详情' })).toContainText('GRAPH_TARGET_MISSING');
  await page.getByRole('button', { name: '关闭关系详情' }).click();
  await reveal(page, 'Copper Lamp');
  await page.getByTestId('relationship-inspect-node').click();
  await page.locator('.relationship-reference-list button').filter({ hasText: 'Graph Block 0039' }).click();
  await expect(page.getByRole('complementary', { name: '关系详情' })).toContainText('/next');
  await page.getByRole('button', { name: '定位目标', exact: true }).click();
  await expect(page.locator(`[data-graph-node][data-element-id="${distantId}"]`)).toBeVisible();
  await expect(page.getByRole('complementary', { name: '关系详情' })).toContainText('element');
});

test('large projections keep nodes, references and diagnostics bounded', async ({ page }) => {
  await installGraphFixture(page, true);
  await openGraph(page);
  expect(await page.locator('[data-graph-node]').count()).toBeLessThanOrEqual(96);
  expect(await page.locator('[data-edge-relation="reference"]').count()).toBeLessThanOrEqual(160);
  await expect(page.getByTestId('relationship-visible-count')).toContainText('未展开');
  await page.getByRole('button', { name: '诊断 10000', exact: true }).click();
  await expect(page.locator('.relationship-diagnostic-list li')).toHaveCount(20);
  await page.getByRole('button', { name: '下一页诊断' }).click();
  await expect(page.locator('.relationship-diagnostic-list li')).toHaveCount(20);
  await page.getByRole('button', { name: '关闭关系详情' }).click();
  await reveal(page, 'Graph Block 1999');
  await expect(page.locator('[data-graph-node][data-element-id="aaaaaaaa-aaaa-4aaa-8aaa-000000001999"]')).toBeVisible();
  expect(await page.locator('[data-graph-node]').count()).toBeLessThanOrEqual(96);
});

test('a refresh never combines snapshots from different revisions or retains the old graph on error', async ({ page }) => {
  await installGraphFixture(page);
  await openGraph(page);
  await page.evaluate(() => { (window as unknown as { graphMismatchAssetRevision: boolean }).graphMismatchAssetRevision = true; });
  await page.getByTestId('relationship-refresh').click();
  await expect(page.getByTestId('relationship-graph-view').getByRole('alert')).toContainText('工作区已更新');
  await expect(page.getByTestId('relationship-canvas')).not.toBeVisible();
  await page.evaluate(() => { (window as unknown as { graphMismatchAssetRevision: boolean }).graphMismatchAssetRevision = false; });
  await page.getByTestId('relationship-refresh').click();
  await expect(page.getByTestId('relationship-canvas')).toBeVisible();
});
