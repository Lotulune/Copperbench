import { createHash } from 'node:crypto';
import { deflateSync } from 'node:zlib';

export const SIZES = Object.freeze({ small: 20, medium: 200, large: 2000 });
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const uuid = (prefix, index) => `${prefix}-aaaa-4aaa-8aaa-${String(index).padStart(12, '0')}`;

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const name = Buffer.from(type);
  const length = Buffer.alloc(4); length.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([name, data])));
  return Buffer.concat([length, name, data, crc]);
}

// A valid, unique 1x1 RGBA PNG for each synthetic asset; no user files or URLs.
function png(index) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(1, 0); header.writeUInt32BE(1, 4); header[8] = 8; header[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.from([0, index >>> 16 & 255, index >>> 8 & 255, index & 255, 255]))),
    chunk('IEND', Buffer.alloc(0))]);
}

export function createPerformanceFixture(size) {
  const count = SIZES[size];
  if (!count) throw new Error(`Unknown performance fixture size: ${size}`);
  const elements = Array.from({ length: count }, (_, index) => ({
    id: uuid('aaaaaaaa', index), type: 'block', name: `perf_block_${String(index).padStart(5, '0')}`,
    displayName: `Performance Block ${String(index).padStart(5, '0')}`, state: 'valid', ownership: 'generated',
    updatedAt: '2026-01-01T00:00:00Z', diagnostics: { error: 0, warning: 0, info: 0 },
  }));
  const diagnostics = [];
  const edges = elements.flatMap((source, index) => Array.from({ length: 5 }, (_, slot) => {
    const missing = slot === 4 && index % 10 === 0;
    const target = elements[(index + slot + 1) % count];
    const edge = { id: uuid('bbbbbbbb', index * 5 + slot), sourceId: source.id,
      sourcePath: `/fixture/references/${slot}`, target: missing ? `missing_perf_${String(index).padStart(5, '0')}` : target.name,
      targetId: missing ? null : target.id, kind: 'element', resolution: missing ? 'missing' : 'resolved' };
    if (missing) diagnostics.push({ code: 'PERFORMANCE_FIXTURE_TARGET_MISSING', severity: 'error',
      message: { key: 'performance.fixture.target_missing', fallback: `Missing synthetic target ${edge.target}.` },
      path: `/elements/${source.id}${edge.sourcePath}`, elementId: source.id, recoverable: true, actions: [] });
    return edge;
  }));
  const assetContents = {};
  const assets = elements.map((_, index) => {
    const relativePath = `assets/performance/textures/block/perf_texture_${String(index).padStart(5, '0')}.png`;
    const id = `asset:${hash(relativePath)}`;
    const bytes = png(index);
    assetContents[id] = bytes.toString('base64');
    return { id, relativePath, category: 'TEXTURE', size: bytes.length, sha256: hash(bytes), mediaType: 'image/png',
      updatedAt: '2026-01-01T00:00:00Z', health: { assetId: id, relativePath, status: 'READY',
        usageAssessed: true, unused: true, inboundCount: 0, outboundCount: 0, workspaceReferenceCount: 0,
        cleanupAssessed: true, safeUnused: true, duplicateContent: false, duplicatePaths: [], issueCodes: [] } };
  });
  const fixture = { schemaVersion: '1.0', size, workspaceName: `Performance ${size}`, elements,
    references: { revision: 42, nodes: elements.map(({ id, type, name, displayName }) => ({ id, type, name, displayName, kind: 'element' })),
      edges, diagnostics, stats: { indexedElements: elements.length, edgeCount: edges.length, incremental: false } },
    assetProjection: { schemaVersion: '1.0', assets, references: [], diagnostics: [], health: {
      totalAssets: assets.length, readyAssets: assets.length, warningAssets: 0, errorAssets: 0,
      unusedAssets: assets.length, safeUnusedAssets: assets.length, duplicateAssets: 0, duplicateGroups: 0,
      missingReferences: 0, invalidDocuments: 0, pathEscapes: 0 } }, assetContents };
  return fixture;
}

export function fixtureSummary(fixture) {
  return { size: fixture.size, sha256: hash(JSON.stringify(fixture)), elementCount: fixture.elements.length,
    referenceNodeCount: fixture.references.nodes.length, referenceEdgeCount: fixture.references.edges.length,
    diagnosticCount: fixture.references.diagnostics.length, assetCount: fixture.assetProjection.assets.length,
    relationshipModelNodeCount: fixture.elements.length + fixture.assetProjection.assets.length
      + fixture.references.diagnostics.length + 3,
    scope: 'Synthetic browser projections and PNG bytes; not a native workspace or Java Core index.',
    relationships: 'Five directed symbol-reference slots per element; every tenth element has one intentionally missing target.',
  };
}
