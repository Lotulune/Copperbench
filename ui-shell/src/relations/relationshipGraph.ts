import type { AssetProjection, Diagnostic, WorkspaceReferenceProjection } from '../types/contract';

export interface RelationshipGraphNode {
  id: string;
  kind: 'workspace' | 'group' | 'element' | 'registry' | 'asset' | 'unresolved';
  label: string;
  subtitle?: string;
  groupId?: string;
  elementId?: string;
  assetId?: string;
  registryId?: string;
  type?: string;
  count?: number;
  diagnostics: Diagnostic[];
  resolutions: string[];
}

export interface RelationshipGraphGroup extends RelationshipGraphNode {
  kind: 'group';
  childIds: string[];
}

export interface RelationshipGraphEdge {
  id: string;
  source: string;
  target: string;
  relation: 'hierarchy' | 'reference';
  kind: string;
  origin?: 'workspace' | 'asset';
  originalTargetId?: string | null;
  resolution?: string;
  sourcePath?: string;
  targetValue?: string;
  diagnostics: Diagnostic[];
}

export interface RelationshipGraphModel {
  rootId: string;
  referenceRevision: number;
  nodes: RelationshipGraphNode[];
  groups: RelationshipGraphGroup[];
  edges: RelationshipGraphEdge[];
  nodeById: ReadonlyMap<string, RelationshipGraphNode>;
  diagnostics: Diagnostic[];
}

export interface RelationshipGraphOptions {
  expandedGroupIds?: readonly string[];
  collapsedGroupIds?: readonly string[];
  pages?: Record<string, number>;
  groupPage?: number;
  pageSize?: number;
  groupPageSize?: number;
  maxVisibleNodes?: number;
  maxVisibleEdges?: number;
  collapseThreshold?: number;
  focusNodeId?: string;
}

export interface PositionedRelationshipNode extends RelationshipGraphNode {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface RelationshipGraphLayout {
  nodes: PositionedRelationshipNode[];
  edges: RelationshipGraphEdge[];
  width: number;
  height: number;
  hiddenNodeCount: number;
  hiddenReferenceCount: number;
  groupPage: number;
  groupPageCount: number;
  groups: Array<{ id: string; expanded: boolean; page: number; pageCount: number; total: number; visibleCount: number }>;
}

const compare = (a: string, b: string) => a < b ? -1 : a > b ? 1 : 0;
const key = (kind: string, value: string) => `${kind}:${encodeURIComponent(value)}`;

/** A display-only projection: no resource inference, filesystem scan or workspace mutations. */
export function buildRelationshipGraph(input: {
  workspace: { id: string; name: string };
  references: WorkspaceReferenceProjection;
  assets: AssetProjection | null;
}): RelationshipGraphModel {
  const { workspace, references, assets } = input;
  const nodes = new Map<string, RelationshipGraphNode>();
  const groups = new Map<string, RelationshipGraphGroup>();
  const edges = new Map<string, RelationshipGraphEdge>();
  const workspaceIds = new Map<string, string>();
  const assetIds = new Map<string, string>();
  const diagnostics = [...references.diagnostics, ...(assets?.diagnostics ?? [])];
  const diagnosticsByElement = new Map<string, Diagnostic[]>();
  const diagnosticsByPath = new Map<string, Diagnostic[]>();
  for (const diagnostic of diagnostics) {
    if (diagnostic.elementId) {
      const entries = diagnosticsByElement.get(diagnostic.elementId) ?? [];
      entries.push(diagnostic); diagnosticsByElement.set(diagnostic.elementId, entries);
    }
    if (diagnostic.path) {
      const entries = diagnosticsByPath.get(diagnostic.path) ?? [];
      entries.push(diagnostic); diagnosticsByPath.set(diagnostic.path, entries);
    }
  }
  const rootId = key('workspace', workspace.id);
  nodes.set(rootId, { id: rootId, kind: 'workspace', label: workspace.name, diagnostics: [], resolutions: [] });

  const addMember = (node: RelationshipGraphNode, groupKind: string, type: string) => {
    if (nodes.has(node.id)) return;
    const groupId = key(`group:${groupKind}`, type);
    let group = groups.get(groupId);
    if (!group) {
      group = { id: groupId, kind: 'group', label: type, type, subtitle: groupKind,
        childIds: [], count: 0, diagnostics: [], resolutions: [] };
      groups.set(groupId, group); nodes.set(groupId, group);
    }
    node.groupId = groupId;
    group.childIds.push(node.id); group.count = group.childIds.length;
    nodes.set(node.id, node);
  };
  for (const node of references.nodes) {
    const id = key(node.kind, node.id);
    workspaceIds.set(node.id, id);
    addMember({ id, kind: node.kind, label: node.displayName || node.name, subtitle: node.name,
      type: node.type, ...(node.kind === 'element' ? { elementId: node.id } : { registryId: node.id }),
      diagnostics: diagnosticsByElement.get(node.id) ?? [], resolutions: [] }, node.kind, node.type);
  }
  for (const asset of assets?.assets ?? []) {
    const id = key('asset', asset.id);
    assetIds.set(asset.id, id);
    addMember({ id, kind: 'asset', label: asset.relativePath.split('/').at(-1) || asset.relativePath,
      subtitle: asset.relativePath, type: asset.category, assetId: asset.id,
      diagnostics: diagnosticsByPath.get(`/assets/${asset.id}`) ?? [], resolutions: [] }, 'asset', asset.category);
  }
  const virtual = (origin: string, kind: string, target: string, resolution: string | undefined, issues: Diagnostic[]) => {
    const id = key('unresolved', JSON.stringify([origin, kind, target]));
    let node = nodes.get(id);
    if (!node) {
      node = { id, kind: 'unresolved', label: target, type: kind, diagnostics: [], resolutions: [] };
      nodes.set(id, node);
    }
    if (resolution && !node.resolutions.includes(resolution)) node.resolutions.push(resolution);
    for (const diagnostic of issues) if (!node.diagnostics.includes(diagnostic)) node.diagnostics.push(diagnostic);
    return id;
  };
  for (const edge of references.edges) {
    const id = key('reference:workspace', edge.id);
    if (edges.has(id)) continue;
    const issues = diagnosticsByPath.get(`/elements/${edge.sourceId}${edge.sourcePath}`) ?? [];
    const source = workspaceIds.get(edge.sourceId) ?? virtual('workspace-source', edge.kind, edge.sourceId, undefined, []);
    const target = (edge.targetId ? workspaceIds.get(edge.targetId) : undefined)
      ?? virtual('workspace', edge.kind, edge.targetId ?? edge.target, edge.resolution, issues);
    edges.set(id, { id, source, target, relation: 'reference', kind: edge.kind, origin: 'workspace',
      originalTargetId: edge.targetId, resolution: edge.resolution, sourcePath: edge.sourcePath,
      targetValue: edge.target, diagnostics: issues });
  }
  for (const edge of assets?.references ?? []) {
    const id = key('reference:asset', JSON.stringify([edge.sourceAssetId, edge.sourcePointer, edge.rawValue,
      edge.targetPath, edge.targetAssetId, edge.kind]));
    if (edges.has(id)) continue;
    const issues = (diagnosticsByPath.get(`/assets/${edge.sourceAssetId}`) ?? []).filter(diagnostic => {
      const targetPath = diagnostic.message.args?.targetPath;
      return targetPath == null || targetPath === edge.targetPath;
    });
    const source = assetIds.get(edge.sourceAssetId) ?? virtual('asset-source', edge.kind, edge.sourceAssetId, undefined, []);
    const target = (edge.targetAssetId ? assetIds.get(edge.targetAssetId) : undefined)
      ?? virtual('asset', edge.kind, edge.targetAssetId ?? (edge.targetPath || edge.rawValue), edge.resolution, issues);
    edges.set(id, { id, source, target, relation: 'reference', kind: edge.kind, origin: 'asset',
      originalTargetId: edge.targetAssetId, resolution: edge.resolution,
      sourcePath: `${edge.sourcePath}#${edge.sourcePointer}`, targetValue: edge.rawValue, diagnostics: issues });
  }
  const orderedGroups = [...groups.values()].sort((a, b) => compare(a.id, b.id));
  const hierarchy = (source: string, target: string) => {
    const id = key('hierarchy', target);
    edges.set(id, { id, source, target, relation: 'hierarchy', kind: 'hierarchy', diagnostics: [] });
  };
  for (const group of orderedGroups) {
    group.childIds.sort((a, b) => compare(nodes.get(a)!.label, nodes.get(b)!.label) || compare(a, b));
    hierarchy(rootId, group.id);
    for (const id of group.childIds) hierarchy(group.id, id);
  }
  return { rootId, referenceRevision: references.revision, nodes: [...nodes.values()], groups: orderedGroups,
    edges: [...edges.values()].sort((a, b) => compare(a.id, b.id)), nodeById: nodes, diagnostics };
}

const bounded = (value: number | undefined, fallback: number, minimum: number, maximum: number) =>
  Math.min(maximum, Math.max(minimum, Number.isFinite(value) ? Math.floor(value!) : fallback));

function limits(options: RelationshipGraphOptions) {
  const maxNodes = bounded(options.maxVisibleNodes, 180, 4, 360);
  return { maxNodes, maxEdges: bounded(options.maxVisibleEdges, 320, 1, 720),
    pageSize: bounded(options.pageSize, 24, 1, 48),
    groupPageSize: bounded(options.groupPageSize, 12, 1, Math.min(48, maxNodes - 3)),
    threshold: bounded(options.collapseThreshold, 24, 0, 100) };
}

/** Deterministic tree positions and bounded cross-reference overlays, independent of zoom/pan. */
export function layoutRelationshipGraph(model: RelationshipGraphModel, options: RelationshipGraphOptions = {}): RelationshipGraphLayout {
  const { maxNodes, maxEdges, pageSize, groupPageSize, threshold } = limits(options);
  const groupPageCount = Math.max(1, Math.ceil(model.groups.length / groupPageSize));
  const groupPage = bounded(options.groupPage, 0, 0, groupPageCount - 1);
  const visibleGroups = model.groups.slice(groupPage * groupPageSize, (groupPage + 1) * groupPageSize);
  const expanded = new Set(options.expandedGroupIds);
  const collapsed = new Set(options.collapsedGroupIds);
  const selected = new Map<string, string[]>();
  const focused = options.focusNodeId ? model.nodeById.get(options.focusNodeId) : undefined;
  const referenceEdges = model.edges.filter(edge => edge.relation === 'reference');
  const focusedReference = focused?.kind === 'unresolved'
    ? referenceEdges.find(edge => edge.source === focused.id || edge.target === focused.id) : undefined;
  const focusMember = focusedReference
    ? model.nodeById.get(focusedReference.source === focused?.id ? focusedReference.target : focusedReference.source) : focused;
  // Leave room for null targets instead of using the whole budget for group children.
  const virtualReserve = Math.max(focused?.kind === 'unresolved' ? 1 : 0,
    Math.min(24, Math.floor((maxNodes - visibleGroups.length - 1) / 4),
      model.nodes.filter(node => node.kind === 'unresolved').length));
  let available = maxNodes - visibleGroups.length - 1 - virtualReserve;
  const groupStates = visibleGroups.map(group => {
    const pageCount = Math.max(1, Math.ceil(group.childIds.length / pageSize));
    return { id: group.id, expanded: !collapsed.has(group.id) && (expanded.has(group.id) || group.childIds.length <= threshold),
      page: bounded(options.pages?.[group.id], 0, 0, pageCount - 1), pageCount, total: group.childIds.length, visibleCount: 0 };
  });
  const allocationOrder = [...visibleGroups].sort((a, b) => Number(b.id === focusMember?.groupId) - Number(a.id === focusMember?.groupId));
  for (const group of allocationOrder) {
    const state = groupStates.find(state => state.id === group.id)!;
    const ids = state.expanded ? group.childIds.slice(state.page * pageSize, (state.page + 1) * pageSize) : [];
    // The selected result is retained even when earlier groups exhausted the normal page budget.
    if (focusMember?.groupId === group.id && ids.includes(focusMember.id)) {
      ids.splice(ids.indexOf(focusMember.id), 1); ids.unshift(focusMember.id);
    }
    const shown = ids.slice(0, available);
    selected.set(group.id, shown); available -= shown.length; state.visibleCount = shown.length;
  }
  const positioned: PositionedRelationshipNode[] = [];
  const place = (node: RelationshipGraphNode, x: number, y: number) => positioned.push({ ...node, x, y, width: 220, height: 58 });
  let y = 40;
  for (const group of visibleGroups) {
    const ids = selected.get(group.id) ?? [];
    const groupHeight = Math.max(1, ids.length) * 74;
    place(group, 330, y + (groupHeight - 74) / 2);
    ids.forEach((id, index) => place(model.nodeById.get(id)!, 620, y + index * 74));
    y += groupHeight + 42;
  }
  place(model.nodeById.get(model.rootId)!, 40, Math.max(40, (y - 42) / 2));
  const positions = new Map(positioned.map(node => [node.id, node]));
  const virtualCandidates = new Map<string, number>();
  for (const edge of referenceEdges) {
    const source = positions.get(edge.source);
    const target = positions.get(edge.target);
    if (source && model.nodeById.get(edge.target)?.kind === 'unresolved') virtualCandidates.set(edge.target, source.y);
    if (target && model.nodeById.get(edge.source)?.kind === 'unresolved') virtualCandidates.set(edge.source, target.y);
  }
  if (focused?.kind === 'unresolved') virtualCandidates.set(focused.id, 40);
  const virtualOrder = [...virtualCandidates].sort(([a, ay], [b, by]) =>
    Number(b === focused?.id) - Number(a === focused?.id) || ay - by || compare(a, b));
  let virtualY = 40;
  for (const [id, anchorY] of virtualOrder) {
    if (positioned.length >= maxNodes) break;
    const nextY = Math.max(virtualY, anchorY);
    place(model.nodeById.get(id)!, 930, nextY);
    positions.set(id, positioned.at(-1)!); virtualY = nextY + 74;
  }
  const visibleIds = new Set(positioned.map(node => node.id));
  const shownEdges = model.edges.filter(edge => visibleIds.has(edge.source) && visibleIds.has(edge.target))
    .sort((a, b) => Number(b.source === focused?.id || b.target === focused?.id) - Number(a.source === focused?.id || a.target === focused?.id)
      || Number(b.relation === 'hierarchy') - Number(a.relation === 'hierarchy'))
    .slice(0, maxEdges);
  return { nodes: positioned, edges: shownEdges,
    width: Math.max(...positioned.map(node => node.x + node.width)) + 40,
    height: Math.max(...positioned.map(node => node.y + node.height)) + 40,
    hiddenNodeCount: model.nodes.length - positioned.length,
    hiddenReferenceCount: referenceEdges.length - shownEdges.filter(edge => edge.relation === 'reference').length,
    groupPage, groupPageCount, groups: groupStates };
}

export function searchRelationshipGraph(model: RelationshipGraphModel, query: string, limit = 30): RelationshipGraphNode[] {
  const normalized = query.trim().toLocaleLowerCase();
  if (!normalized) return [];
  return model.nodes.filter(node => node.kind !== 'workspace' && node.kind !== 'group'
    && `${node.label}\n${node.subtitle ?? ''}\n${node.type ?? ''}\n${node.elementId ?? node.assetId ?? node.registryId ?? ''}`
      .toLocaleLowerCase().includes(normalized)).slice(0, bounded(limit, 30, 1, 100));
}

/** Reveal changes only view state; original projection objects remain untouched. */
export function revealRelationshipNode(model: RelationshipGraphModel, nodeId: string, options: RelationshipGraphOptions = {}): RelationshipGraphOptions {
  const node = model.nodeById.get(nodeId);
  if (!node) return options;
  const { pageSize, groupPageSize } = limits(options);
  let member = node;
  if (node.kind === 'unresolved') {
    const reference = model.edges.find(edge => edge.relation === 'reference' && (edge.target === nodeId || edge.source === nodeId));
    member = model.nodeById.get(reference?.target === nodeId ? reference.source : reference?.target ?? '') ?? node;
  }
  const groupId = member.kind === 'group' ? member.id : member.groupId;
  const groupIndex = model.groups.findIndex(group => group.id === groupId);
  if (groupIndex < 0) return { ...options, focusNodeId: nodeId };
  const group = model.groups[groupIndex];
  const page = Math.max(0, Math.floor(group.childIds.indexOf(member.id) / pageSize));
  return { ...options, focusNodeId: nodeId, groupPage: Math.floor(groupIndex / groupPageSize),
    expandedGroupIds: [...new Set([...(options.expandedGroupIds ?? []), group.id])],
    collapsedGroupIds: options.collapsedGroupIds?.filter(id => id !== group.id),
    pages: { ...options.pages, [group.id]: page } };
}
