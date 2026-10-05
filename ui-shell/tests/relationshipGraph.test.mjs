import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
  buildRelationshipGraph, layoutRelationshipGraph, revealRelationshipNode, searchRelationshipGraph
} from '../src/relations/relationshipGraph.ts';

const workspace = { id: 'workspace-id', name: 'Copper Trails' };
const assetFixture = JSON.parse(await readFile(new URL('../../ui-core/fixtures/v1.0/assets/asset-reference-graph.json', import.meta.url), 'utf8'));
const referenceProjection = (nodes = [], edges = [], diagnostics = []) => ({
  revision: 7, nodes, edges, diagnostics, stats: { indexedElements: nodes.length, edgeCount: edges.length, incremental: true }
});
const element = (id, type = 'block', label = id) => ({ id, kind: 'element', type, name: id, displayName: label });
const reference = (id, sourceId, targetId, extra = {}) => ({
  id, sourceId, targetId, target: targetId ?? 'missing_target', sourcePath: '/fields/target', kind: 'element', ...extra
});
const graph = (nodes, edges = [], assets = null, diagnostics = []) => buildRelationshipGraph({
  workspace, references: referenceProjection(nodes, edges, diagnostics), assets
});

test('empty workspace has only its named root and no invented groups', () => {
  const model = graph([]);
  assert.equal(model.nodes.length, 1);
  assert.equal(model.nodes[0].label, workspace.name);
  assert.equal(model.referenceRevision, 7);
  assert.deepEqual(model.groups, []);
  assert.deepEqual(model.edges, []);
  const layout = layoutRelationshipGraph(model);
  assert.equal(layout.nodes.length, 1);
  assert.equal(layout.hiddenNodeCount, 0);
  assert.equal(layout.hiddenReferenceCount, 0);
  assert.ok(layout.width > 0 && layout.height > 0);
});

test('mixed Core projections preserve unique real objects and distinguish hierarchy from references', () => {
  const elements = [element('block-id', 'block', 'Copper lamp'), element('item-id', 'item', 'Copper ingot')];
  const edges = [reference('item-use', 'block-id', 'item-id', { kind: 'ingredient', resolution: 'resolved' })];
  const model = graph([...elements, elements[0]], [...edges, edges[0]], {
    ...assetFixture, assets: [...assetFixture.assets, assetFixture.assets[0]],
    references: [...assetFixture.references, assetFixture.references[0]]
  });
  assert.equal(model.nodes.filter(node => node.kind === 'element').length, 2);
  assert.equal(model.nodes.filter(node => node.kind === 'asset').length, 2);
  assert.equal(new Set(model.nodes.map(node => node.id)).size, model.nodes.length);
  assert.equal(new Set(model.edges.map(edge => edge.id)).size, model.edges.length);
  assert.deepEqual(model.groups.map(group => group.type).sort(), ['MODEL', 'TEXTURE', 'block', 'item']);
  const actual = model.edges.filter(edge => edge.relation === 'reference');
  assert.equal(actual.length, 1 + assetFixture.references.length);
  assert.equal(actual.find(edge => edge.origin === 'workspace').kind, 'ingredient');
  for (const edge of model.edges) {
    assert.ok(model.nodeById.has(edge.source)); assert.ok(model.nodeById.has(edge.target));
    if (edge.relation === 'hierarchy') assert.equal(edge.kind, 'hierarchy');
  }
  for (const node of model.nodes.filter(node => ['element', 'asset'].includes(node.kind))) {
    assert.equal(model.edges.filter(edge => edge.relation === 'hierarchy' && edge.target === node.id).length, 1);
  }
});

test('null targets survive as virtual nodes with unchanged Core resolution and diagnostics', () => {
  const diagnostic = { code: 'MISSING_REFERENCE', severity: 'error', path: '/elements/block-id/fields/target',
    elementId: 'block-id', message: { key: 'reference.missing', fallback: 'Missing reference' }, recoverable: true, actions: [] };
  const references = [
    reference('missing', 'block-id', null, { resolution: 'missing' }),
    reference('external', 'block-id', null, { kind: 'resource', target: 'coppertrails:block/copper_lamp', resolution: 'external' })
  ];
  const missingAssetReference = { ...assetFixture.references[0], sourcePointer: '/textures/side',
    rawValue: 'coppertrails:block/missing', targetPath: 'assets/coppertrails/textures/block/missing.png', targetAssetId: null };
  const input = { workspace, references: referenceProjection([element('block-id')], references, [diagnostic]),
    assets: { ...assetFixture, references: [...assetFixture.references, missingAssetReference] } };
  const before = JSON.stringify(input);
  const model = buildRelationshipGraph(input);
  assert.equal(JSON.stringify(input), before, 'building a display graph must not modify projections');
  const missing = model.edges.find(edge => edge.origin === 'workspace' && edge.resolution === 'missing');
  assert.equal(missing.originalTargetId, null);
  assert.equal(model.nodeById.get(missing.target).kind, 'unresolved');
  assert.deepEqual(model.nodeById.get(missing.target).resolutions, ['missing']);
  assert.ok(missing.diagnostics.includes(diagnostic));
  assert.ok(model.nodeById.get(missing.target).diagnostics.includes(diagnostic));
  const external = model.edges.find(edge => edge.resolution === 'external');
  assert.equal(model.nodeById.get(external.target).kind, 'unresolved', 'do not infer a resource alias match with an asset');
  assert.equal(external.resolution, 'external');
  const missingAsset = model.edges.find(edge => edge.origin === 'asset' && edge.originalTargetId === null);
  assert.ok(missingAsset, 'null asset references must not disappear');
  assert.equal(missingAsset.resolution, undefined, 'older fixtures without resolution remain unspecified');
  assert.ok(model.nodeById.get(missingAsset.target).diagnostics.some(issue => issue.code === 'MISSING_ASSET_REFERENCE'));
  assert.equal(model.diagnostics.length, assetFixture.diagnostics.length + 1);
  const layout = layoutRelationshipGraph(model);
  assert.ok(layout.nodes.some(node => node.id === missing.target));
  assert.ok(layout.edges.some(edge => edge.id === missing.id));
});

test('node and edge identities stay stable across projection ordering and refreshes', () => {
  const nodes = [element('a', 'block'), element('b', 'item')];
  const edges = [reference('first', 'a', 'b'), reference('second', 'a', null, { resolution: 'missing' })];
  const first = graph(nodes, edges, assetFixture);
  const second = graph([...nodes].reverse(), [...edges].reverse(), {
    ...assetFixture, assets: [...assetFixture.assets].reverse(), references: [...assetFixture.references].reverse()
  });
  assert.deepEqual(first.nodes.map(node => node.id).sort(), second.nodes.map(node => node.id).sort());
  assert.deepEqual(first.edges.map(edge => edge.id), second.edges.map(edge => edge.id));
  assert.deepEqual(first.groups.map(group => group.childIds), second.groups.map(group => group.childIds));
});

test('large groups collapse, page, and reveal a search result even under a small rendering budget', () => {
  const model = graph(Array.from({ length: 70 }, (_, i) => element(`block-${i}`, 'block', `Hidden ${String(i).padStart(4, '0')}`)));
  const group = model.groups[0];
  const initial = layoutRelationshipGraph(model);
  assert.equal(initial.nodes.length, 2);
  assert.equal(initial.groups[0].expanded, false);
  assert.equal(initial.groups[0].total, 70);
  const expanded = { expandedGroupIds: [group.id] };
  const page = layoutRelationshipGraph(model, expanded);
  assert.equal(page.groups[0].visibleCount, 24);
  assert.equal(page.groups[0].pageCount, 3);
  const result = searchRelationshipGraph(model, 'Hidden 0069')[0];
  assert.ok(result);
  const options = revealRelationshipNode(model, result.id, { ...expanded, maxVisibleNodes: 4 });
  assert.equal(options.pages[group.id], 2);
  const revealed = layoutRelationshipGraph(model, options);
  assert.ok(revealed.nodes.some(node => node.id === result.id));
  assert.ok(revealed.nodes.length <= 4);
  assert.equal(revealed.hiddenNodeCount, model.nodes.length - revealed.nodes.length);
  assert.equal(searchRelationshipGraph(model, 'Hidden', 5).length, 5);
  assert.deepEqual(searchRelationshipGraph(model, '   '), []);
});

test('revealing a result switches group pages and does not mutate previous view state', () => {
  const model = graph(Array.from({ length: 40 }, (_, i) => element(`item-${i}`, `present_type_${String(i).padStart(2, '0')}`)));
  const result = model.nodes.find(node => node.elementId === 'item-39');
  const previous = { groupPage: 0, collapsedGroupIds: [result.groupId], pages: {} };
  const before = JSON.stringify(previous);
  const next = revealRelationshipNode(model, result.id, previous);
  assert.equal(next.groupPage, 3);
  assert.equal(JSON.stringify(previous), before);
  const layout = layoutRelationshipGraph(model, next);
  assert.ok(layout.nodes.some(node => node.id === result.id));
  assert.equal(layout.groupPageCount, 4);
  assert.equal(layout.groups.find(group => group.id === result.groupId).expanded, true);
});

test('small budgets clamp default group pages and keep a revealed virtual target with its source', () => {
  const model = graph(Array.from({ length: 40 }, (_, i) => element(`element-${i}`, `type_${i}`)),
    [reference('missing', 'element-39', null, { resolution: 'missing' })]);
  const initial = layoutRelationshipGraph(model, { maxVisibleNodes: 4 });
  assert.ok(initial.nodes.length <= 4);
  const target = model.nodes.find(node => node.kind === 'unresolved');
  const options = revealRelationshipNode(model, target.id, { maxVisibleNodes: 4 });
  const layout = layoutRelationshipGraph(model, options);
  assert.ok(layout.nodes.length <= 4);
  assert.ok(layout.nodes.some(node => node.id === target.id));
  assert.ok(layout.nodes.some(node => node.elementId === 'element-39'));
  assert.ok(layout.edges.some(edge => edge.relation === 'reference' && edge.target === target.id));
});

test('2000 elements and 10000 actual edges retain data while bounding displayed nodes and edges', () => {
  const elements = Array.from({ length: 2000 }, (_, i) => element(`element-${i}`, `type_${i % 32}`));
  const references = Array.from({ length: 10000 }, (_, i) => reference(`edge-${i}`, `element-${i % 2000}`, `element-${(i + 13) % 2000}`, { kind: 'procedure' }));
  const model = graph(elements, references);
  assert.equal(model.nodes.filter(node => node.kind === 'element').length, 2000);
  assert.equal(model.edges.filter(edge => edge.relation === 'reference').length, 10000);
  const initial = layoutRelationshipGraph(model);
  assert.equal(initial.nodes.length, 13, 'initial view contains root plus one group page, never 2000 DOM nodes');
  const options = { expandedGroupIds: model.groups.map(group => group.id) };
  const expanded = layoutRelationshipGraph(model, options);
  assert.ok(expanded.nodes.length <= 180);
  assert.ok(expanded.edges.length <= 320);
  const visibleIds = new Set(expanded.nodes.map(node => node.id));
  assert.ok(expanded.edges.every(edge => visibleIds.has(edge.source) && visibleIds.has(edge.target)));
  assert.equal(expanded.hiddenReferenceCount + expanded.edges.filter(edge => edge.relation === 'reference').length, 10000);
  const excessive = layoutRelationshipGraph(model, { ...options, maxVisibleNodes: 10000, maxVisibleEdges: 10000, pageSize: 2000, groupPageSize: 2000 });
  assert.ok(excessive.nodes.length <= 360 && excessive.edges.length <= 720, 'hard bounds cannot be accidentally removed by options');
  const found = searchRelationshipGraph(model, 'element-1999').find(node => node.elementId === 'element-1999');
  assert.ok(layoutRelationshipGraph(model, revealRelationshipNode(model, found.id, options)).nodes.some(node => node.id === found.id));
});
