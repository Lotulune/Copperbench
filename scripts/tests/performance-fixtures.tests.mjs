import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { inflateSync } from 'node:zlib';
import { createPerformanceFixture, fixtureSummary, SIZES } from '../../ui-shell/performance/fixtures.mjs';
import { buildRelationshipGraph, layoutRelationshipGraph, revealRelationshipNode, searchRelationshipGraph }
  from '../../ui-shell/src/relations/relationshipGraph.ts';

for (const [size, count] of Object.entries(SIZES)) {
  test(`${size} has the declared counts, consistent edges, diagnostics and actual synthetic PNG hashes`, () => {
    const fixture = createPerformanceFixture(size);
    const summary = fixtureSummary(fixture);
    assert.equal(summary.elementCount, count);
    assert.equal(summary.assetCount, count);
    assert.equal(summary.referenceEdgeCount, count * 5);
    assert.equal(summary.referenceNodeCount, count);
    assert.equal(summary.diagnosticCount, count / 10);
    assert.equal(new Set(fixture.elements.map(element => element.id)).size, count);
    assert.equal(new Set(fixture.references.edges.map(edge => edge.id)).size, count * 5);
    const byId = new Map(fixture.elements.map(element => [element.id, element]));
    const missing = fixture.references.edges.filter(edge => edge.targetId === null);
    assert.equal(missing.length, summary.diagnosticCount);
    for (const edge of fixture.references.edges) {
      assert.ok(byId.has(edge.sourceId));
      assert.match(edge.id, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}$/);
      if (edge.targetId) assert.equal(byId.get(edge.targetId).name, edge.target);
      else assert.ok(fixture.references.diagnostics.some(diagnostic => diagnostic.path === `/elements/${edge.sourceId}${edge.sourcePath}`));
    }
    const hashes = new Set();
    for (const asset of fixture.assetProjection.assets) {
      const bytes = Buffer.from(fixture.assetContents[asset.id], 'base64');
      assert.equal(bytes.length, asset.size);
      assert.equal(createHash('sha256').update(bytes).digest('hex'), asset.sha256);
      assert.equal(bytes.readUInt32BE(16), 1); assert.equal(bytes.readUInt32BE(20), 1);
      assert.deepEqual([...bytes.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
      let offset = 8;
      const data = [];
      while (offset < bytes.length) {
        const length = bytes.readUInt32BE(offset);
        const kind = bytes.toString('ascii', offset + 4, offset + 8);
        if (kind === 'IDAT') data.push(bytes.subarray(offset + 8, offset + 8 + length));
        offset += length + 12;
      }
      const pixels = inflateSync(Buffer.concat(data));
      assert.equal(pixels.length, 5); assert.equal(pixels[0], 0); assert.equal(pixels[4], 255);
      hashes.add(asset.sha256);
    }
    assert.equal(hashes.size, count);
    assert.equal(fixtureSummary(createPerformanceFixture(size)).sha256, summary.sha256);
  });

  test(`${size} uses the product graph model without dropping hidden edges, last-page search or diagnostics`, () => {
    const fixture = createPerformanceFixture(size);
    const before = JSON.stringify(fixture);
    const model = buildRelationshipGraph({ workspace: { id: '11111111-1111-4111-8111-111111111111', name: fixture.workspaceName },
      references: fixture.references, assets: fixture.assetProjection });
    assert.equal(model.nodes.length, fixtureSummary(fixture).relationshipModelNodeCount);
    assert.equal(model.edges.filter(edge => edge.relation === 'reference').length, count * 5);
    assert.equal(model.diagnostics.length, count / 10);
    const last = fixture.elements.at(-1);
    const [found] = searchRelationshipGraph(model, last.name);
    assert.equal(found.elementId, last.id);
    const options = { groupPageSize: 6, pageSize: 6, maxVisibleNodes: 96, maxVisibleEdges: 160 };
    // Reveal must calculate its page with the same limits the canvas will render.
    const layout = layoutRelationshipGraph(model, revealRelationshipNode(model, found.id, options));
    assert.ok(layout.nodes.some(node => node.elementId === last.id));
    const group = layout.groups.find(candidate => candidate.id === found.groupId);
    assert.equal(group.page, group.pageCount - 1);
    assert.ok(layout.nodes.length <= 96); assert.ok(layout.edges.length <= 160);
    const [missing] = searchRelationshipGraph(model, `missing_perf_${String(count - 10).padStart(5, '0')}`);
    assert.equal(missing.diagnostics.length, 1);
    assert.equal(missing.diagnostics[0].code, 'PERFORMANCE_FIXTURE_TARGET_MISSING');
    assert.equal(JSON.stringify(fixture), before);
  });
}

test('unknown fixture sizes fail explicitly', () => assert.throws(() => createPerformanceFixture('giant')));
