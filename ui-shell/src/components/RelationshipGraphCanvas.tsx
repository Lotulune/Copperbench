import React, { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp, Box, ChevronDown, ChevronLeft, ChevronRight, File, Folder, Focus, Link2, Minus, Move, Network, Plus, RotateCcw, Search, X } from 'lucide-react';
import { t, uiText, useUiLocale } from '../i18n';
import { elementShortLabel } from '../i18n/labels';
import {
  layoutRelationshipGraph, revealRelationshipNode, searchRelationshipGraph,
  type PositionedRelationshipNode, type RelationshipGraphEdge, type RelationshipGraphModel,
  type RelationshipGraphNode, type RelationshipGraphOptions
} from '../relations/relationshipGraph';
import './relationshipGraph.css';

export interface RelationshipGraphCanvasProps {
  model: RelationshipGraphModel;
  onOpenElement?: (elementId: string) => void;
  onOpenAsset?: (assetId: string) => void;
  onOpenRegistry?: (registryId: string) => void;
  className?: string;
}

function categoryLabel(type: string): string {
  const categories: Record<string, string> = {
    MODEL: uiText('模型', 'Models'), TEXTURE: uiText('纹理', 'Textures'), ANIMATION: uiText('动画', 'Animations'),
    LANGUAGE: uiText('语言', 'Languages'), SOUND: uiText('声音', 'Sounds'), RESOURCE_PACK: uiText('资源包', 'Resource packs'),
    BLOCKSTATE: uiText('方块状态', 'Block states'), OTHER: uiText('其他资产', 'Other assets'),
    variables: uiText('变量', 'Variables'), tags: uiText('标签', 'Tags'), languageKeys: uiText('语言键', 'Language keys')
  };
  return categories[type] ?? elementShortLabel(type);
}

function nodeLabel(node: RelationshipGraphNode): string {
  return node.kind === 'group' ? categoryLabel(node.type ?? node.label) : node.label;
}

function nodeSubtitle(node: RelationshipGraphNode): string {
  if (node.kind === 'workspace') return uiText('工作区', 'Workspace');
  if (node.kind === 'group') return `${node.subtitle === 'asset' ? uiText('资产', 'Assets')
    : node.subtitle === 'registry' ? uiText('数据', 'Data') : uiText('模组元素', 'Mod elements')} · ${node.type}`;
  if (node.kind === 'unresolved') return node.resolutions.join(' · ') || uiText('目标未解析', 'Unresolved target');
  return node.subtitle ?? node.type ?? '';
}

function initialOptions(model: RelationshipGraphModel): RelationshipGraphOptions {
  return { groupPageSize: 6, pageSize: 6, maxVisibleNodes: 96, maxVisibleEdges: 160,
    collapsedGroupIds: model.groups.filter(group => group.childIds.length > 24).map(group => group.id) };
}

function edgeGeometry(edge: RelationshipGraphEdge, source: PositionedRelationshipNode, target: PositionedRelationshipNode, lane: number) {
  const sy = source.y + source.height / 2;
  const ty = target.y + target.height / 2;
  const sx = source.x + source.width;
  if (edge.relation === 'hierarchy' || target.x > source.x) {
    const tx = target.x;
    const mx = (sx + tx) / 2;
    return { path: `M${sx},${sy} C${mx},${sy} ${mx},${ty} ${tx},${ty}`, x: mx, y: (sy + ty) / 2 - 8 };
  }
  const tx = target.x + target.width;
  const bend = Math.max(sx, tx) + 45 + lane * 13;
  const endY = source.id === target.id ? ty - 15 : ty;
  return { path: `M${sx},${sy} C${bend},${sy + (source.id === target.id ? 55 : 0)} ${bend},${endY} ${tx},${endY}`,
    x: bend - 8, y: (sy + endY) / 2 - 8 };
}

type GraphDrag = {
  pointer: number;
  x: number;
  y: number;
  capture: SVGSVGElement | SVGGElement;
  moved: boolean;
} & ({ kind: 'pan'; cameraX: number; cameraY: number }
  | { kind: 'node'; nodeId: string; offsetX: number; offsetY: number; zoom: number });

export const RelationshipGraphCanvas: React.FC<RelationshipGraphCanvasProps> = ({
  model, onOpenElement, onOpenAsset, onOpenRegistry, className = ''
}) => {
  useUiLocale();
  const markerId = `relationship-${useId().replace(/:/g, '')}`;
  const containerRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const nodeRefs = useRef(new Map<string, SVGGElement>());
  const [options, setOptions] = useState<RelationshipGraphOptions>(() => initialOptions(model));
  const [viewport, setViewport] = useState({ width: 900, height: 560 });
  const [camera, setCamera] = useState({ x: 0, y: 0, zoom: 1 });
  const cameraRef = useRef(camera);
  cameraRef.current = camera;
  const drag = useRef<GraphDrag | null>(null);
  const suppressedClick = useRef<string | null>(null);
  const [panning, setPanning] = useState(false);
  const [draggingNodeId, setDraggingNodeId] = useState<string | null>(null);
  const [nodeOffsets, setNodeOffsets] = useState<Record<string, { x: number; y: number }>>({});
  const [moveControlsOpen, setMoveControlsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchIndex, setSearchIndex] = useState(0);
  const [focusId, setFocusId] = useState(model.rootId);
  const [pendingFocus, setPendingFocus] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [diagnosticsOpen, setDiagnosticsOpen] = useState(false);
  const [diagnosticPage, setDiagnosticPage] = useState(0);
  const [referencePage, setReferencePage] = useState(0);
  const layout = useMemo(() => layoutRelationshipGraph(model, options), [model, options]);
  const positionedNodes = useMemo(() => layout.nodes.map(node => ({ ...node,
    x: node.x + (nodeOffsets[node.id]?.x ?? 0), y: node.y + (nodeOffsets[node.id]?.y ?? 0) })), [layout, nodeOffsets]);
  const positions = useMemo(() => new Map(positionedNodes.map(node => [node.id, node])), [positionedNodes]);
  const results = useMemo(() => searchRelationshipGraph(model, query, 20), [model, query]);
  const referenceKinds = useMemo(() => [...new Set(model.edges.filter(edge => edge.relation === 'reference').map(edge => edge.kind))].sort(), [model]);
  const selectedNode = model.nodeById.get(focusId);
  const nodeReferences = useMemo(() => model.edges.filter(edge => edge.relation === 'reference'
    && (edge.source === focusId || edge.target === focusId)), [model, focusId]);
  const edgeById = useMemo(() => new Map(model.edges.map(edge => [edge.id, edge])), [model]);
  const selectedEdge = selectedEdgeId ? edgeById.get(selectedEdgeId) : undefined;
  const selectionDiagnostics = selectedEdge?.diagnostics ?? selectedNode?.diagnostics ?? [];
  const connectionCount = useMemo(() => model.edges.filter(edge => edge.relation === 'reference').length, [model]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver(entries => {
      const { width, height } = entries[0].contentRect;
      if (width > 0 && height > 0) setViewport({ width, height });
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    setOptions(initialOptions(model)); setFocusId(model.rootId); setDetailsOpen(false); setQuery('');
    setNodeOffsets({}); setMoveControlsOpen(false); suppressedClick.current = null;
  }, [model.rootId]);

  const fitNodes = useCallback((nodes: PositionedRelationshipNode[]) => {
    const left = Math.min(...nodes.map(node => node.x));
    const top = Math.min(...nodes.map(node => node.y));
    const width = Math.max(...nodes.map(node => node.x + node.width)) - left;
    const height = Math.max(...nodes.map(node => node.y + node.height)) - top;
    const zoom = Math.min(1.15, Math.max(1, viewport.width - 96) / width, Math.max(1, viewport.height - 96) / height);
    setCamera({ zoom, x: (viewport.width - width * zoom) / 2 - left * zoom,
      y: (viewport.height - height * zoom) / 2 - top * zoom });
  }, [viewport]);
  const fit = useCallback(() => fitNodes(positionedNodes), [fitNodes, positionedNodes]);
  const fitRef = useRef(fit);
  fitRef.current = fit;

  // Resizing or changing the displayed page fits once. Moving a node leaves the camera alone.
  useEffect(() => { if (!pendingFocus) fitRef.current(); }, [layout, viewport.width, viewport.height]);

  useEffect(() => {
    if (!pendingFocus) return;
    const node = positions.get(pendingFocus);
    if (!node) return;
    setCamera(current => ({ zoom: Math.max(0.8, current.zoom),
      x: viewport.width / 2 - (node.x + node.width / 2) * Math.max(0.8, current.zoom),
      y: viewport.height / 2 - (node.y + node.height / 2) * Math.max(0.8, current.zoom) }));
    nodeRefs.current.get(pendingFocus)?.focus({ preventScroll: true });
    setPendingFocus(null);
  }, [pendingFocus, positions, viewport]);

  const zoomAt = useCallback((factor: number, x: number, y: number) => {
    setCamera(current => {
      const zoom = Math.max(Math.min(0.2, current.zoom), Math.min(2.5, current.zoom * factor));
      const ratio = zoom / current.zoom;
      return { zoom, x: x - (x - current.x) * ratio, y: y - (y - current.y) * ratio };
    });
  }, []);

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return;
    const wheel = (event: WheelEvent) => {
      event.preventDefault();
      if (drag.current) return;
      const bounds = svg.getBoundingClientRect();
      zoomAt(Math.exp(-event.deltaY * 0.0015), event.clientX - bounds.left, event.clientY - bounds.top);
    };
    svg.addEventListener('wheel', wheel, { passive: false });
    return () => svg.removeEventListener('wheel', wheel);
  }, [zoomAt]);

  const finishDrag = (pointerId: number, cancelled = false) => {
    const active = drag.current;
    if (!active || active.pointer !== pointerId) return;
    drag.current = null;
    setPanning(false); setDraggingNodeId(null);
    if (active.kind === 'node' && active.moved) {
      suppressedClick.current = active.nodeId;
      if (cancelled) setNodeOffsets(current => ({ ...current, [active.nodeId]: { x: active.offsetX, y: active.offsetY } }));
    }
    if (active.capture.hasPointerCapture(pointerId)) active.capture.releasePointerCapture(pointerId);
  };

  useEffect(() => () => {
    const active = drag.current;
    drag.current = null;
    if (active?.capture.hasPointerCapture(active.pointer)) active.capture.releasePointerCapture(active.pointer);
  }, [model.rootId]);

  const moveNode = (id: string, dx: number, dy: number) => {
    if (!positions.has(id)) return;
    setNodeOffsets(current => ({ ...current, [id]: {
      x: (current[id]?.x ?? 0) + dx / cameraRef.current.zoom,
      y: (current[id]?.y ?? 0) + dy / cameraRef.current.zoom
    } }));
  };

  const toggleGroup = (id: string) => {
    const expanded = layout.groups.find(group => group.id === id)?.expanded;
    setOptions(current => ({ ...current, focusNodeId: undefined,
      expandedGroupIds: expanded ? current.expandedGroupIds?.filter(value => value !== id) : [...new Set([...(current.expandedGroupIds ?? []), id])],
      collapsedGroupIds: expanded ? [...new Set([...(current.collapsedGroupIds ?? []), id])] : current.collapsedGroupIds?.filter(value => value !== id) }));
  };

  const reveal = (id: string) => {
    setOptions(current => revealRelationshipNode(model, id, current));
    setFocusId(id); setPendingFocus(id); setSelectedEdgeId(null); setSearchOpen(false); setDiagnosticsOpen(false); setDiagnosticPage(0); setReferencePage(0);
    setDetailsOpen(model.nodeById.get(id)?.kind === 'unresolved');
  };

  const activate = (node: RelationshipGraphNode) => {
    setFocusId(node.id); setSelectedEdgeId(null); setDiagnosticsOpen(false); setDiagnosticPage(0); setReferencePage(0);
    if (node.kind === 'group') toggleGroup(node.id);
    else if (node.elementId && onOpenElement) onOpenElement(node.elementId);
    else if (node.assetId && onOpenAsset) onOpenAsset(node.assetId);
    else if (node.registryId && onOpenRegistry) onOpenRegistry(node.registryId);
    else setDetailsOpen(true);
  };

  const nodeKeyDown = (event: React.KeyboardEvent<SVGGElement>, node: PositionedRelationshipNode) => {
    if (event.key === 'Escape' && drag.current) { event.preventDefault(); finishDrag(drag.current.pointer, true); return; }
    if (event.shiftKey && ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
      event.preventDefault(); event.stopPropagation();
      moveNode(node.id, event.key === 'ArrowLeft' ? -24 : event.key === 'ArrowRight' ? 24 : 0,
        event.key === 'ArrowUp' ? -24 : event.key === 'ArrowDown' ? 24 : 0);
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); activate(node); return; }
    if (event.key === 'Escape') { setDetailsOpen(false); svgRef.current?.focus(); return; }
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault(); event.stopPropagation();
    let next: PositionedRelationshipNode | undefined;
    if (event.key === 'Home') next = positions.get(model.rootId);
    else if (event.key === 'End') next = positionedNodes.at(-1);
    else {
      const horizontal = event.key === 'ArrowLeft' || event.key === 'ArrowRight';
      const sign = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1;
      next = positionedNodes.filter(candidate => candidate.id !== node.id
        && (horizontal ? candidate.x - node.x : candidate.y - node.y) * sign > 0)
        .sort((a, b) => {
          const score = (candidate: PositionedRelationshipNode) => horizontal
            ? Math.abs(candidate.x - node.x) + Math.abs(candidate.y - node.y) * 2
            : Math.abs(candidate.y - node.y) + Math.abs(candidate.x - node.x) * 2;
          return score(a) - score(b);
        })[0];
    }
    if (next) { setFocusId(next.id); nodeRefs.current.get(next.id)?.focus({ preventScroll: true }); }
  };

  const selectEdge = (edge: RelationshipGraphEdge) => { setSelectedEdgeId(edge.id); setDetailsOpen(true); setDiagnosticsOpen(false); setDiagnosticPage(0); };
  const displayedDiagnostics = diagnosticsOpen ? model.diagnostics : selectionDiagnostics;
  const diagnosticPageCount = Math.max(1, Math.ceil(displayedDiagnostics.length / 20));
  const currentDiagnosticPage = Math.min(diagnosticPage, diagnosticPageCount - 1);
  const rootLabel = model.nodeById.get(model.rootId)?.label ?? '';

  return <section className={`relationship-canvas ${className}`} data-testid="relationship-canvas">
    <header className="relationship-heading">
      <div><h1><Network size={20} aria-hidden="true" />{uiText('关系图', 'Relationships')}</h1>
        <p title={`${uiText('修订', 'Revision')} ${model.referenceRevision}`}>{rootLabel}<span>·</span>
          {uiText(`${connectionCount} 条引用`, `${connectionCount} references`)}</p></div>
      <div className="relationship-search">
        <Search size={16} aria-hidden="true" />
        <input type="search" value={query} placeholder={uiText('查找元素、资产或引用目标', 'Find an element, asset or target')}
          aria-label={uiText('搜索关系图', 'Search relationships')} role="combobox" aria-autocomplete="list"
          aria-expanded={searchOpen && query.trim().length > 0} aria-controls={`${markerId}-results`}
          aria-activedescendant={searchOpen && results[searchIndex] ? `${markerId}-result-${searchIndex}` : undefined}
          data-testid="relationship-search" onFocus={() => setSearchOpen(true)}
          onBlur={event => { if (!event.currentTarget.parentElement?.contains(event.relatedTarget as Node)) setSearchOpen(false); }}
          onChange={event => { setQuery(event.target.value); setSearchOpen(true); setSearchIndex(0); }}
          onKeyDown={event => {
            if (event.key === 'Escape') { setSearchOpen(false); return; }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
              event.preventDefault(); setSearchIndex(index => Math.max(0, Math.min(results.length - 1, index + (event.key === 'ArrowDown' ? 1 : -1))));
            }
            if (event.key === 'Enter' && results[searchIndex]) { event.preventDefault(); reveal(results[searchIndex].id); }
          }} />
        {query && <button type="button" aria-label={uiText('清除搜索', 'Clear search')} onClick={() => { setQuery(''); setSearchOpen(false); }}><X size={14} /></button>}
        {searchOpen && query.trim() && <div className="relationship-search-results" id={`${markerId}-results`} role="listbox">
          {results.length === 0 ? <p role="status">{uiText('没有匹配的节点', 'No matching nodes')}</p> : results.map((node, index) =>
            <button type="button" role="option" aria-selected={searchIndex === index} tabIndex={-1} key={node.id}
              id={`${markerId}-result-${index}`} onMouseDown={event => event.preventDefault()} onClick={() => reveal(node.id)}>
              <strong>{nodeLabel(node)}</strong><span>{nodeSubtitle(node)}</span>
            </button>)}
          {results.length === 20 && <p>{uiText('前 20 项，继续输入可缩小范围。', 'First 20 results. Type more to narrow them.')}</p>}
        </div>}
      </div>
    </header>

    <div className="relationship-tools">
      <div className="relationship-legend" aria-label={uiText('连线图例', 'Connection legend')}>
        <span><i className="relationship-hierarchy-swatch" />{uiText('分类层级', 'Hierarchy')}</span>
        {referenceKinds.slice(0, 8).map((kind, index) => <span key={kind} className={`relationship-kind-${index % 5}`}><i />{kind}</span>)}
        {referenceKinds.length > 8 && <span>+{referenceKinds.length - 8}</span>}
      </div>
      <div className="relationship-tool-actions">
        <button type="button" aria-expanded={moveControlsOpen} data-testid="relationship-move-controls"
          onClick={() => setMoveControlsOpen(open => !open)}><Move size={14} aria-hidden="true" />{uiText('移动节点', 'Move node')}</button>
        <button type="button" disabled={!selectedNode || selectedNode.kind === 'workspace' || selectedNode.kind === 'group'}
          data-testid="relationship-inspect-node" onClick={() => { setDetailsOpen(true); setDiagnosticsOpen(false); setSelectedEdgeId(null); setReferencePage(0); }}>
          <Link2 size={14} aria-hidden="true" />{uiText('节点引用', 'Node references')}</button>
        {model.diagnostics.length > 0 && <button type="button" className="relationship-diagnostics-toggle" onClick={() => { setDiagnosticsOpen(!diagnosticsOpen); setDetailsOpen(false); setDiagnosticPage(0); }}
          aria-expanded={diagnosticsOpen}>{uiText(`诊断 ${model.diagnostics.length}`, `${model.diagnostics.length} diagnostics`)}</button>}
        <button type="button" onClick={() => { setOptions(current => ({ ...current, expandedGroupIds: [], collapsedGroupIds: model.groups.map(group => group.id), focusNodeId: undefined })); setFocusId(model.rootId); }}
          data-testid="relationship-collapse-all">{uiText('收起全部', 'Collapse all')}</button>
        <button type="button" onClick={fit} title={uiText('适应画布', 'Fit graph')} aria-label={uiText('适应画布', 'Fit graph')} data-testid="relationship-fit"><Focus size={16} /></button>
        <button type="button" disabled={Object.values(nodeOffsets).every(offset => offset.x === 0 && offset.y === 0)}
          onClick={() => { setNodeOffsets({}); fitNodes(layout.nodes); }} title={uiText('恢复布局', 'Restore layout')}
          aria-label={uiText('恢复布局', 'Restore layout')} data-testid="relationship-restore-layout"><RotateCcw size={16} /></button>
      </div>
    </div>

    {moveControlsOpen && <div className="relationship-node-movement" role="group" aria-label={uiText('移动节点控件', 'Node movement controls')}>
      <select value={positions.has(focusId) ? focusId : model.rootId} aria-label={uiText('选择要移动的节点', 'Choose a node to move')}
        onChange={event => { setFocusId(event.target.value); setSelectedEdgeId(null); }}>
        {positionedNodes.map(node => <option key={node.id} value={node.id}>{nodeLabel(node)}</option>)}
      </select>
      {([{ label: uiText('节点向左移动', 'Move node left'), dx: -24, dy: 0, Icon: ArrowLeft },
        { label: uiText('节点向上移动', 'Move node up'), dx: 0, dy: -24, Icon: ArrowUp },
        { label: uiText('节点向下移动', 'Move node down'), dx: 0, dy: 24, Icon: ArrowDown },
        { label: uiText('节点向右移动', 'Move node right'), dx: 24, dy: 0, Icon: ArrowRight }]).map(({ label, dx, dy, Icon }) =>
        <button type="button" key={label} aria-label={label} onClick={() => moveNode(positions.has(focusId) ? focusId : model.rootId, dx, dy)}><Icon size={15} /></button>)}
      <span>{uiText('Shift + 方向键', 'Shift + arrow keys')}</span>
    </div>}

    <div className={`relationship-viewport${panning ? ' is-panning' : ''}`} ref={containerRef}>
      <svg ref={svgRef} role="group" aria-label={uiText('工作区关系画布；拖动节点调整布局，方向键浏览，Shift 加方向键移动，回车打开', 'Workspace relationships; drag nodes to arrange, use arrow keys to browse, Shift plus arrows to move, and Enter to open')}
        tabIndex={0} data-testid="relationship-svg" width="100%" height="100%"
        onPointerDown={event => {
          if (event.button !== 0 || drag.current || (event.target as Element).closest('[data-graph-node], [data-graph-edge]')) return;
          const current = cameraRef.current;
          drag.current = { kind: 'pan', pointer: event.pointerId, x: event.clientX, y: event.clientY,
            cameraX: current.x, cameraY: current.y, capture: event.currentTarget, moved: false };
          event.currentTarget.setPointerCapture(event.pointerId); setPanning(true); setSearchOpen(false);
        }}
        onPointerMove={event => {
          const active = drag.current;
          if (!active || active.pointer !== event.pointerId) return;
          const dx = event.clientX - active.x; const dy = event.clientY - active.y;
          if (active.kind === 'pan') setCamera(current => ({ ...current, x: active.cameraX + dx, y: active.cameraY + dy }));
          else {
            if (!active.moved && Math.hypot(dx, dy) < 5) return;
            active.moved = true; setDraggingNodeId(active.nodeId);
            setNodeOffsets(current => ({ ...current, [active.nodeId]: { x: active.offsetX + dx / active.zoom, y: active.offsetY + dy / active.zoom } }));
          }
        }}
        onPointerUp={event => finishDrag(event.pointerId)}
        onPointerCancel={event => finishDrag(event.pointerId, true)}
        onLostPointerCapture={event => finishDrag(event.pointerId, true)}
        onKeyDown={event => {
          if (event.target !== event.currentTarget) return;
          if (event.key === '0') { event.preventDefault(); fit(); }
          else if (event.key === '+' || event.key === '=') { event.preventDefault(); zoomAt(1.2, viewport.width / 2, viewport.height / 2); }
          else if (event.key === '-') { event.preventDefault(); zoomAt(1 / 1.2, viewport.width / 2, viewport.height / 2); }
          else if (event.key === 'Enter') { event.preventDefault(); nodeRefs.current.get(focusId)?.focus(); }
          else if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
            event.preventDefault(); setCamera(current => ({ ...current, x: current.x + (event.key === 'ArrowLeft' ? 48 : event.key === 'ArrowRight' ? -48 : 0),
              y: current.y + (event.key === 'ArrowUp' ? 48 : event.key === 'ArrowDown' ? -48 : 0) }));
          }
        }}>
        <defs>
          <pattern id={`${markerId}-dots`} width="24" height="24" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r="0.75" className="relationship-grid-dot" /></pattern>
          <clipPath id={`${markerId}-label`}><rect x="36" y="7" width="153" height="23" /></clipPath>
          <clipPath id={`${markerId}-subtitle`}><rect x="14" y="32" width="192" height="21" /></clipPath>
          <clipPath id={`${markerId}-group-subtitle`}><rect x="14" y="32" width="155" height="21" /></clipPath>
          {Array.from({ length: 5 }, (_, index) => <marker key={index} id={`${markerId}-arrow-${index}`} markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto-start-reverse">
            <path d="M0,0 L7,3.5 L0,7 Z" className={`relationship-arrow relationship-kind-${index}`} /></marker>)}
        </defs>
        <rect width="100%" height="100%" fill={`url(#${markerId}-dots)`} />
        <g transform={`translate(${camera.x} ${camera.y}) scale(${camera.zoom})`} data-testid="relationship-transform" data-zoom={camera.zoom.toFixed(3)}>
          {layout.edges.map(edge => {
            const source = positions.get(edge.source)!; const target = positions.get(edge.target)!;
            const kindIndex = Math.max(0, referenceKinds.indexOf(edge.kind));
            const geometry = edgeGeometry(edge, source, target, kindIndex % 5);
            const reference = edge.relation === 'reference';
            return <g key={edge.id} className={`relationship-edge ${reference ? `is-reference relationship-kind-${kindIndex % 5}` : 'is-hierarchy'}${selectedEdgeId === edge.id ? ' is-selected' : ''}`}
              data-graph-edge={edge.id} data-edge-kind={edge.kind} data-edge-relation={edge.relation}
              data-edge-source={edge.source} data-edge-target={edge.target}
              role={reference ? 'button' : undefined} tabIndex={reference ? 0 : undefined}
              aria-label={reference ? `${nodeLabel(source)} → ${nodeLabel(target)} · ${edge.kind}` : undefined}
              onClick={reference ? () => selectEdge(edge) : undefined}
              onKeyDown={event => { if (reference && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); selectEdge(edge); } }}>
              <path d={geometry.path} markerEnd={reference ? `url(#${markerId}-arrow-${kindIndex % 5})` : undefined} />
              {reference && <>
                <path d={geometry.path} className="relationship-edge-hit" onClick={() => selectEdge(edge)}><title>{`${nodeLabel(source)} → ${nodeLabel(target)} · ${edge.kind}`}</title></path>
                <text x={geometry.x} y={geometry.y} textAnchor="middle" className="relationship-edge-label" onClick={() => selectEdge(edge)}>{edge.kind}</text>
              </>}
            </g>;
          })}
          {positionedNodes.map(node => {
            const group = layout.groups.find(group => group.id === node.id);
            const Icon = node.kind === 'workspace' ? Folder : node.kind === 'group' ? Network : node.kind === 'asset' ? File : node.kind === 'unresolved' ? Link2 : Box;
            return <g key={node.id} ref={element => { if (element) nodeRefs.current.set(node.id, element); else nodeRefs.current.delete(node.id); }}
              transform={`translate(${node.x} ${node.y})`} className={`relationship-node is-${node.kind}${focusId === node.id ? ' is-selected' : ''}${draggingNodeId === node.id ? ' is-dragging' : ''}`}
              role="button" tabIndex={focusId === node.id || !positions.has(focusId) && node.id === model.rootId ? 0 : -1}
              aria-label={`${nodeLabel(node)} · ${nodeSubtitle(node)}${group ? ` · ${node.count}` : ''}`}
              aria-expanded={group?.expanded} data-graph-node={node.id} data-node-kind={node.kind}
              data-element-id={node.elementId} data-asset-id={node.assetId} data-testid={`relationship-node-${node.id}`}
              onFocus={() => setFocusId(node.id)} onKeyDown={event => nodeKeyDown(event, node)}
              onPointerDown={event => {
                if (event.button !== 0 || drag.current) return;
                event.stopPropagation(); suppressedClick.current = null;
                const offset = nodeOffsets[node.id] ?? { x: 0, y: 0 };
                drag.current = { kind: 'node', pointer: event.pointerId, x: event.clientX, y: event.clientY,
                  capture: event.currentTarget, nodeId: node.id, offsetX: offset.x, offsetY: offset.y,
                  zoom: cameraRef.current.zoom, moved: false };
                event.currentTarget.focus({ preventScroll: true });
                event.currentTarget.setPointerCapture(event.pointerId); setSearchOpen(false);
              }}
              onClick={event => {
                if (suppressedClick.current === node.id && event.detail !== 0) {
                  suppressedClick.current = null; event.preventDefault(); event.stopPropagation(); return;
                }
                activate(node);
              }}>
              <title>{`${nodeLabel(node)}\n${nodeSubtitle(node)}`}</title>
              <rect className="relationship-node-surface" width={node.width} height={node.height} rx="8" />
              <g transform="translate(14 13)"><Icon size={15} aria-hidden="true" /></g>
              <text x="36" y="25" className="relationship-node-label" clipPath={`url(#${markerId}-label)`}>{nodeLabel(node)}</text>
              <text x="14" y="46" className="relationship-node-subtitle" clipPath={`url(#${markerId}-${group ? 'group-subtitle' : 'subtitle'})`}>{nodeSubtitle(node)}</text>
              {group && <text x="206" y="46" textAnchor="end" className="relationship-node-subtitle">{group.total}</text>}
              {group && <g transform="translate(196 13)">{group.expanded ? <ChevronDown size={15} aria-hidden="true" /> : <ChevronRight size={15} aria-hidden="true" />}</g>}
              {node.diagnostics.length > 0 && <circle cx="210" cy="48" r="3" className="relationship-diagnostic-dot" />}
            </g>;
          })}
        </g>
      </svg>

      <div className="relationship-zoom" role="group" aria-label={uiText('画布缩放', 'Graph zoom')}>
        <button type="button" aria-label={uiText('缩小关系图', 'Zoom out')} onClick={() => zoomAt(1 / 1.2, viewport.width / 2, viewport.height / 2)}><Minus size={15} /></button>
        <output>{Math.round(camera.zoom * 100)}%</output>
        <button type="button" aria-label={uiText('放大关系图', 'Zoom in')} onClick={() => zoomAt(1.2, viewport.width / 2, viewport.height / 2)}><Plus size={15} /></button>
      </div>

      {(detailsOpen || diagnosticsOpen) && <aside className="relationship-details" aria-label={uiText('关系详情', 'Relationship details')}>
        <div className="relationship-details-heading"><strong>{diagnosticsOpen ? uiText('工作区诊断', 'Workspace diagnostics')
          : selectedEdge ? selectedEdge.kind : selectedNode ? nodeLabel(selectedNode) : ''}</strong>
          <button type="button" aria-label={uiText('关闭关系详情', 'Close relationship details')} onClick={() => { setDetailsOpen(false); setDiagnosticsOpen(false); }}><X size={15} /></button></div>
        {!diagnosticsOpen && selectedEdge && <dl>
          <dt>{uiText('来源', 'Source')}</dt><dd>{model.nodeById.get(selectedEdge.source)?.label}</dd>
          <dt>{uiText('目标', 'Target')}</dt><dd>{selectedEdge.targetValue ?? model.nodeById.get(selectedEdge.target)?.label}</dd>
          {selectedEdge.resolution && <><dt>{uiText('解析状态', 'Resolution')}</dt><dd><code>{selectedEdge.resolution}</code></dd></>}
          {selectedEdge.sourcePath && <><dt>{uiText('引用位置', 'Reference location')}</dt><dd><code>{selectedEdge.sourcePath}</code></dd></>}
          {selectedEdge.originalTargetId === null && <><dt>{uiText('目标标识', 'Target ID')}</dt><dd><code>{JSON.stringify(selectedEdge.originalTargetId)}</code></dd></>}
        </dl>}
        {!diagnosticsOpen && selectedEdge && <div className="relationship-reference-actions">
          <button type="button" onClick={() => { reveal(selectedEdge.source); setSelectedEdgeId(selectedEdge.id); setDetailsOpen(true); }}>
            {uiText('定位来源', 'Locate source')}</button>
          <button type="button" onClick={() => { reveal(selectedEdge.target); setSelectedEdgeId(selectedEdge.id); setDetailsOpen(true); }}>
            {uiText('定位目标', 'Locate target')}</button>
        </div>}
        {!diagnosticsOpen && !selectedEdge && selectedNode && <><p>{nodeSubtitle(selectedNode)}</p>
          {selectedNode.kind === 'unresolved' && <p className="relationship-target-value">{selectedNode.label}</p>}
          {selectedNode.kind === 'workspace' && <p>{uiText('展开分类查看节点。', 'Expand a category to see its nodes.')}</p>}
          {selectedNode.kind !== 'workspace' && selectedNode.kind !== 'group' && <>
            <p>{uiText(`${nodeReferences.length} 条引用`, `${nodeReferences.length} references`)}</p>
            <ul className="relationship-reference-list">{nodeReferences.slice(referencePage * 12, (referencePage + 1) * 12).map(edge =>
              <li key={edge.id}><button type="button" onClick={() => selectEdge(edge)}>
                <strong>{edge.kind}</strong><span>{model.nodeById.get(edge.source)?.label} → {model.nodeById.get(edge.target)?.label}</span>
                {edge.resolution && <code>{edge.resolution}</code>}
              </button></li>)}</ul>
            {nodeReferences.length > 12 && <div className="relationship-page-control">
              <button type="button" aria-label={uiText('上一页引用', 'Previous references')} disabled={referencePage === 0} onClick={() => setReferencePage(page => page - 1)}><ChevronLeft size={14} /></button>
              <span>{referencePage + 1}/{Math.ceil(nodeReferences.length / 12)}</span>
              <button type="button" aria-label={uiText('下一页引用', 'Next references')} disabled={(referencePage + 1) * 12 >= nodeReferences.length} onClick={() => setReferencePage(page => page + 1)}><ChevronRight size={14} /></button>
            </div>}
          </>}
        </>}
        {displayedDiagnostics.length > 0 && <ul className="relationship-diagnostic-list">{displayedDiagnostics.slice(currentDiagnosticPage * 20, (currentDiagnosticPage + 1) * 20).map((diagnostic, index) =>
          <li key={`${diagnostic.code}-${index}`} data-severity={diagnostic.severity}><strong>{diagnostic.code}</strong><span>{t(diagnostic.message)}</span>
            {diagnostic.path && <code>{diagnostic.path}</code>}</li>)}</ul>}
        {diagnosticPageCount > 1 && <div className="relationship-page-control">
          <button type="button" aria-label={uiText('上一页诊断', 'Previous diagnostics')} disabled={currentDiagnosticPage === 0}
            onClick={() => setDiagnosticPage(page => Math.max(0, page - 1))}><ChevronLeft size={14} /></button>
          <span>{currentDiagnosticPage + 1}/{diagnosticPageCount} · {displayedDiagnostics.length}</span>
          <button type="button" aria-label={uiText('下一页诊断', 'Next diagnostics')} disabled={currentDiagnosticPage + 1 >= diagnosticPageCount}
            onClick={() => setDiagnosticPage(page => page + 1)}><ChevronRight size={14} /></button>
        </div>}
      </aside>}
    </div>

    <div className="relationship-pages">
      {layout.groupPageCount > 1 && <div className="relationship-page-control"><span>{uiText('分类', 'Categories')}</span>
        <button type="button" aria-label={uiText('上一组分类', 'Previous categories')} disabled={layout.groupPage === 0}
          onClick={() => setOptions(current => ({ ...current, groupPage: layout.groupPage - 1, focusNodeId: undefined }))}><ChevronLeft size={14} /></button>
        <span>{layout.groupPage + 1}/{layout.groupPageCount}</span>
        <button type="button" aria-label={uiText('下一组分类', 'Next categories')} disabled={layout.groupPage + 1 >= layout.groupPageCount}
          onClick={() => setOptions(current => ({ ...current, groupPage: layout.groupPage + 1, focusNodeId: undefined }))}><ChevronRight size={14} /></button>
      </div>}
      {layout.groups.filter(group => group.expanded && group.pageCount > 1).map(group => <div className="relationship-page-control" key={group.id}>
        <span>{nodeLabel(model.nodeById.get(group.id)!)} {group.total}</span>
        <button type="button" aria-label={uiText(`上一页 ${nodeLabel(model.nodeById.get(group.id)!)}`, `Previous ${nodeLabel(model.nodeById.get(group.id)!)}`)} disabled={group.page === 0}
          onClick={() => setOptions(current => ({ ...current, focusNodeId: undefined, pages: { ...current.pages, [group.id]: group.page - 1 } }))}><ChevronLeft size={14} /></button>
        <span>{group.page + 1}/{group.pageCount}</span>
        <button type="button" aria-label={uiText(`下一页 ${nodeLabel(model.nodeById.get(group.id)!)}`, `Next ${nodeLabel(model.nodeById.get(group.id)!)}`)} disabled={group.page + 1 >= group.pageCount}
          onClick={() => setOptions(current => ({ ...current, focusNodeId: undefined, pages: { ...current.pages, [group.id]: group.page + 1 } }))}><ChevronRight size={14} /></button>
      </div>)}
    </div>
    <footer className="relationship-footer">
      <span>{uiText('拖动节点 · 滚轮缩放', 'Drag nodes · Scroll to zoom')}</span>
      <span role="status" data-testid="relationship-visible-count">{uiText(`显示 ${layout.nodes.length} / ${model.nodes.length} 个节点`, `${layout.nodes.length} / ${model.nodes.length} nodes shown`)}
        {layout.hiddenReferenceCount > 0 && ` · ${uiText(`${layout.hiddenReferenceCount} 条引用未展开`, `${layout.hiddenReferenceCount} references outside this view`)}`}</span>
    </footer>
  </section>;
};
