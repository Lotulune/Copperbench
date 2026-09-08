import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as Blockly from 'blockly/core';
import 'blockly/blocks';
import * as BlocklyEnglish from 'blockly/msg/en';
import {
  AlignStartVertical,
  ArrowLeft,
  Braces,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Clock3,
  Code2,
  Filter,
  Link2,
  ListTree,
  LocateFixed,
  Redo2,
  Save,
  Search,
  Undo2
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import {
  Diagnostic,
  ModElementSummary,
  ProcedureEdit,
  ProcedureEditorProjection,
  ProcedureNode,
  ProcedureNodeCatalogItem,
  RegistryRenamePreview,
  WorkspacePlan
} from '../types/contract';
import { t } from '../i18n';

let blocksRegistered = false;
const blocklyEnglishMessages = Object.fromEntries(
  Object.entries(BlocklyEnglish).filter(([key, value]) => key !== 'default' && typeof value === 'string')
) as Record<string, string>;

function registerProcedureBlocks(): void {
  if (blocksRegistered) return;
  Blockly.setLocale(blocklyEnglishMessages);
  Blockly.common.defineBlocksWithJsonArray([
    {
      type: 'event_trigger',
      message0: '触发器 %1',
      args0: [{ type: 'field_dropdown', name: 'trigger', options: [
        ['无外部触发器', 'no_ext_trigger'],
        ['方块右键', 'on_block_right_clicked'],
        ['物品右键', 'on_item_right_clicked'],
        ['实体更新', 'on_entity_tick_update']
      ] }],
      nextStatement: null,
      colour: 24,
      tooltip: 'Procedure 入口触发器'
    },
    {
      type: 'controls_while',
      message0: '当 %1 时循环',
      args0: [{ type: 'input_value', name: 'BOOL', check: 'Boolean' }],
      message1: '执行 %1',
      args1: [{ type: 'input_statement', name: 'DO' }],
      previousStatement: null,
      nextStatement: null,
      colour: 120
    },
    {
      type: 'math_binary_ops',
      message0: '%1 %2 %3',
      args0: [
        { type: 'input_value', name: 'A', check: 'Number' },
        { type: 'field_dropdown', name: 'OP', options: [['+', 'ADD'], ['-', 'MINUS'], ['×', 'MULTIPLY'], ['÷', 'DIVIDE']] },
        { type: 'input_value', name: 'B', check: 'Number' }
      ],
      inputsInline: true,
      output: 'Number',
      colour: 230
    },
    {
      type: 'logic_binary_ops',
      message0: '%1 %2 %3',
      args0: [
        { type: 'input_value', name: 'A', check: 'Boolean' },
        { type: 'field_dropdown', name: 'OP', options: [['且', 'AND'], ['或', 'OR']] },
        { type: 'input_value', name: 'B', check: 'Boolean' }
      ],
      inputsInline: true,
      output: 'Boolean',
      colour: 210
    },
    {
      type: 'variables_get_number',
      message0: '读取变量 %1',
      args0: [{ type: 'field_input', name: 'VAR', text: 'variable' }],
      output: 'Number',
      colour: 330
    },
    {
      type: 'variables_set_number',
      message0: '设置变量 %1 为 %2',
      args0: [
        { type: 'field_input', name: 'VAR', text: 'variable' },
        { type: 'input_value', name: 'VALUE', check: 'Number' }
      ],
      previousStatement: null,
      nextStatement: null,
      colour: 330
    },
    { type: 'entity_from_deps', message0: '上下文实体', output: null, colour: 165 },
    { type: 'coord_x', message0: '上下文 X', output: 'Number', colour: 165 },
    { type: 'coord_y', message0: '上下文 Y', output: 'Number', colour: 165 },
    { type: 'coord_z', message0: '上下文 Z', output: 'Number', colour: 165 },
    {
      type: 'mcitem_all',
      message0: '物品 %1',
      args0: [{ type: 'field_input', name: 'value', text: 'minecraft:stone' }],
      output: null,
      colour: 48
    },
    {
      type: 'call_procedure',
      message0: '调用 Procedure %1',
      args0: [{ type: 'field_input', name: 'procedureId', text: '' }],
      previousStatement: null,
      nextStatement: null,
      colour: 285
    },
    {
      type: 'return_number',
      message0: '返回数值 %1',
      args0: [{ type: 'input_value', name: 'VALUE', check: 'Number' }],
      previousStatement: null,
      colour: 285
    }
  ]);
  blocksRegistered = true;
}

interface VariableRefactorDraft {
  entryId: string;
  oldName: string;
  newName: string;
}

interface ExtractRefactorDraft {
  nodeId: string;
  nodeType: string;
  newProcedureName: string;
}

interface CallRefactorDraft {
  sourceProcedureId: string;
  sourceName: string;
  targetProcedureId: string;
}

interface ResourceRefactorDraft {
  sourceResource: string;
  targetResource: string;
}

const WorkspacePlanReview: React.FC<{ plan: WorkspacePlan; testId: string }> = ({ plan, testId }) => {
  const { summary } = plan.review;
  return (
    <div className={`procedure-plan-review ${summary.highImpact ? 'high-impact' : ''}`} data-testid={testId}>
      <div className="procedure-plan-review-summary">
        <strong>{summary.affectedObjectCount} 个受影响对象 · {summary.operationCount} 步操作</strong>
        <span>{summary.createCount} 新建 · {summary.updateCount} 更新 · {summary.deleteCount} 删除 · {summary.changedPathCount} 条路径</span>
      </div>
      <div className="procedure-plan-review-groups" aria-label="计划操作分组">
        {plan.review.operationGroups.map((group) => (
          <span key={group.operation}><code>{group.operation}</code> × {group.count}</span>
        ))}
      </div>
      <details open={summary.highImpact}>
        <summary>审阅受影响对象</summary>
        <ol className="procedure-plan-review-objects">
          {plan.review.affectedObjects.map((item, index) => (
            <li key={`${item.kind}-${item.elementId ?? item.registry ?? index}`}>
              <span><strong>{item.displayName || item.name || item.registry || item.kind}</strong><code>{item.kind}</code></span>
              {item.changedProperties?.length ? <small>{item.changedProperties.join(' · ')}</small> : null}
            </li>
          ))}
        </ol>
      </details>
    </div>
  );
};

function defineUnknownBlock(node: ProcedureNode): void {
  if (Blockly.Blocks[node.type]) return;
  const shape = node.kind === 'value'
    ? { output: null }
    : { previousStatement: null, nextStatement: null };
  Blockly.common.defineBlocksWithJsonArray([{
    type: node.type,
    message0: `未知节点 · ${node.type}`,
    ...shape,
    colour: 0,
    tooltip: '来自上游或插件的未知节点；内容只读，但可移动或删除。'
  }]);
}

function fieldValues(block: Blockly.Block): ProcedureNode['fields'] {
  const values: ProcedureNode['fields'] = {};
  for (const input of block.inputList) {
    for (const field of input.fieldRow) {
      if (!field.name) continue;
      const value = field.getValue();
      if (value !== null && ['string', 'number', 'boolean'].includes(typeof value)) {
        values[field.name] = value as string | number | boolean;
      }
    }
  }
  return values;
}

function blockInputs(block: Blockly.Block): Record<string, string> {
  return Object.fromEntries(block.inputList.flatMap((input) => {
    const target = input.connection?.targetBlock();
    return input.name && target ? [[input.name, target.id]] : [];
  }));
}

function sameObject(left: Record<string, unknown>, right: Record<string, unknown>): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}

function buildEdits(workspace: Blockly.WorkspaceSvg, original: ProcedureEditorProjection): ProcedureEdit[] {
  const edits: ProcedureEdit[] = [];
  const oldById = new Map(original.ir.nodes.map((node) => [node.id, node]));
  const blocks = workspace.getAllBlocks(false);
  const currentIds = new Set(blocks.map((block) => block.id));
  const deletedIds = new Set(original.ir.nodes
    .filter((node) => node.type !== 'event_trigger' && !currentIds.has(node.id))
    .map((node) => node.id));

  deletedIds.forEach((nodeId) => edits.push({ operation: 'delete_node', nodeId }));

  for (const block of blocks) {
    const point = block.getRelativeToSurfaceXY();
    const fields = fieldValues(block);
    const existing = oldById.get(block.id);
    if (!existing) {
      edits.push({
        operation: 'add_node',
        node: {
          id: block.id,
          type: block.type,
          kind: block.outputConnection ? 'value' : 'statement',
          x: point.x,
          y: point.y,
          fields,
          inputs: {},
          next: null,
          unknown: false
        }
      });
    } else if (existing.unknown) {
      if (existing.x !== point.x || existing.y !== point.y) {
        edits.push({ operation: 'move_node', nodeId: block.id, x: point.x, y: point.y });
      }
    } else if (existing.x !== point.x || existing.y !== point.y || !sameObject(existing.fields, fields)) {
      edits.push({ operation: 'update_node', nodeId: block.id, fields, x: point.x, y: point.y });
    }
  }

  for (const block of blocks) {
    if (deletedIds.has(block.id)) continue;
    const existing = oldById.get(block.id);
    const inputs = blockInputs(block);
    const next = block.getNextBlock()?.id ?? null;
    const oldInputs = existing?.inputs ?? {};
    const ports = new Set([...Object.keys(oldInputs), ...Object.keys(inputs)]);
    ports.forEach((port) => {
      if (oldInputs[port] === inputs[port]) return;
      if (oldInputs[port] && existing) edits.push({ operation: 'disconnect', sourceNodeId: block.id, port });
      if (inputs[port]) edits.push({ operation: 'connect', sourceNodeId: block.id, port, targetNodeId: inputs[port] });
    });
    if ((existing?.next ?? null) !== next) {
      if (existing?.next) edits.push({ operation: 'disconnect', sourceNodeId: block.id, port: 'next' });
      if (next) edits.push({ operation: 'connect', sourceNodeId: block.id, port: 'next', targetNodeId: next });
    }
  }

  const triggerBlock = blocks.find((block) => block.type === 'event_trigger');
  const trigger = String(triggerBlock?.getFieldValue('trigger') ?? original.ir.trigger);
  if (trigger !== original.ir.trigger) edits.push({ operation: 'set_trigger', trigger });
  return edits;
}

function catalogLabel(item: ProcedureNodeCatalogItem): string {
  return t(item.label);
}

function generateNodeId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

interface ProcedureWorkbenchProps {
  element: ModElementSummary;
  onClose: () => void;
}

type ProcedurePanel = 'source' | 'diagnostics' | 'references' | 'outline';

const procedurePanels: ProcedurePanel[] = ['source', 'diagnostics', 'references', 'outline'];

interface ProcedureGraphNode {
  id: string;
  type: string;
  kind: 'value' | 'statement';
  x: number;
  y: number;
  fields: ProcedureNode['fields'];
}

const procedureCategoryLabels: Record<string, string> = {
  control: '控制',
  value: '值',
  variable: '变量',
  context: '上下文',
  procedure: 'Procedure'
};

function snapshotGraph(workspace: Blockly.WorkspaceSvg): ProcedureGraphNode[] {
  return workspace.getAllBlocks(false).map((block) => {
    const point = block.getRelativeToSurfaceXY();
    return {
      id: block.id,
      type: block.type,
      kind: block.outputConnection ? 'value' : 'statement',
      x: point.x,
      y: point.y,
      fields: fieldValues(block)
    };
  });
}

function diagnosticNodeId(diagnostic: Diagnostic): string | null {
  const match = diagnostic.path?.match(/\/nodes\/([0-9a-f-]{36})(?:\/|$)/i);
  return match?.[1] ?? null;
}

export const ProcedureWorkbench: React.FC<ProcedureWorkbenchProps> = ({ element, onClose }) => {
  const {
    getProcedureEditor,
    previewProcedureChange,
    updateProcedure,
    previewRegistryRename,
    planProcedureRefactor,
    planWorkspaceChanges,
    applyWorkspacePlan,
    procedureFocusRequest,
    clearProcedureFocusRequest
  } = useWorkbench();
  const hostRef = useRef<HTMLDivElement | null>(null);
  const workspaceRef = useRef<Blockly.WorkspaceSvg | null>(null);
  const [projection, setProjection] = useState<ProcedureEditorProjection | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('all');
  const [recentTypes, setRecentTypes] = useState<string[]>([]);
  const [graphNodes, setGraphNodes] = useState<ProcedureGraphNode[]>([]);
  const [graphSearch, setGraphSearch] = useState('');
  const [graphSearchIndex, setGraphSearchIndex] = useState(-1);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [liveDiagnostics, setLiveDiagnostics] = useState<Diagnostic[] | null>(null);
  const [liveSourcePreview, setLiveSourcePreview] = useState<string | null>(null);
  const [liveCanGenerate, setLiveCanGenerate] = useState<boolean | null>(null);
  const [refactorDraft, setRefactorDraft] = useState<VariableRefactorDraft | null>(null);
  const [refactorImpact, setRefactorImpact] = useState<RegistryRenamePreview | null>(null);
  const [refactorPlan, setRefactorPlan] = useState<WorkspacePlan | null>(null);
  const [extractDraft, setExtractDraft] = useState<ExtractRefactorDraft | null>(null);
  const [callRefactorDraft, setCallRefactorDraft] = useState<CallRefactorDraft | null>(null);
  const [resourceRefactorDraft, setResourceRefactorDraft] = useState<ResourceRefactorDraft | null>(null);
  const [semanticRefactorPlan, setSemanticRefactorPlan] = useState<WorkspacePlan | null>(null);
  const [refactorBusy, setRefactorBusy] = useState(false);
  const [panel, setPanel] = useState<ProcedurePanel>('source');
  const [message, setMessage] = useState<string | null>(null);
  const previewSequenceRef = useRef(0);

  const load = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      const next = await getProcedureEditor(element.id);
      setProjection(next);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setLoading(false);
    }
  }, [element.id, getProcedureEditor]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!projection || !hostRef.current) return;
    setLiveDiagnostics(null);
    setLiveSourcePreview(null);
    setLiveCanGenerate(null);
    registerProcedureBlocks();
    projection.ir.nodes.filter((node) => node.unknown).forEach(defineUnknownBlock);
    const workspace = Blockly.inject(hostRef.current, {
      readOnly: projection.readOnly,
      trashcan: !projection.readOnly,
      renderer: 'zelos',
      move: { scrollbars: true, drag: true, wheel: true },
      zoom: { controls: true, wheel: true, startScale: 0.88, maxScale: 1.5, minScale: 0.45, scaleSpeed: 1.1 },
      grid: { spacing: 24, length: 2, colour: '#3a414d', snap: false }
    });
    workspaceRef.current = workspace;
    Blockly.Events.disable();
    try {
      const byId = new Map<string, Blockly.BlockSvg>();
      for (const node of projection.ir.nodes) {
        const block = workspace.newBlock(node.type, node.id);
        block.initSvg();
        for (const [name, value] of Object.entries(node.fields)) {
          if (block.getField(name)) block.setFieldValue(String(value), name);
        }
        if (node.type === 'event_trigger') {
          block.setFieldValue(projection.ir.trigger, 'trigger');
          block.setDeletable(false);
        }
        if (node.unknown) block.setEditable(false);
        block.render();
        block.moveBy(node.x, node.y);
        byId.set(node.id, block);
      }
      for (const node of projection.ir.nodes) {
        const source = byId.get(node.id);
        if (!source) continue;
        for (const [port, targetId] of Object.entries(node.inputs)) {
          const target = byId.get(targetId);
          const sourceConnection = source.getInput(port)?.connection;
          const targetConnection = target?.outputConnection ?? target?.previousConnection;
          if (sourceConnection && targetConnection && !sourceConnection.isConnected() && !targetConnection.isConnected()) {
            sourceConnection.connect(targetConnection);
          }
        }
        const next = node.next ? byId.get(node.next) : undefined;
        if (source.nextConnection && next?.previousConnection
          && !source.nextConnection.isConnected() && !next.previousConnection.isConnected()) {
          source.nextConnection.connect(next.previousConnection);
        }
      }
    } finally {
      Blockly.Events.enable();
    }
    setGraphNodes(snapshotGraph(workspace));
    const listener = (event: Blockly.Events.Abstract) => {
      if (event.type === 'selected') {
        const selected = (event as unknown as { newElementId?: string | null }).newElementId;
        setSelectedNodeId(selected ?? null);
      }
      if (!event.isUiEvent) {
        setDirty(true);
        setGraphNodes(snapshotGraph(workspace));
      }
    };
    workspace.addChangeListener(listener);
    window.setTimeout(() => Blockly.svgResize(workspace), 0);
    return () => {
      workspace.removeChangeListener(listener);
      workspace.dispose();
      workspaceRef.current = null;
    };
  }, [projection]);

  useEffect(() => {
    if (!procedureFocusRequest || procedureFocusRequest.elementId !== element.id || !projection) return;
    const workspace = workspaceRef.current;
    const block = workspace?.getBlockById(procedureFocusRequest.nodeId);
    if (!workspace || !block) return;
    block.select();
    workspace.centerOnBlock(procedureFocusRequest.nodeId);
    setSelectedNodeId(procedureFocusRequest.nodeId);
    setPanel('diagnostics');
    clearProcedureFocusRequest();
  }, [clearProcedureFocusRequest, element.id, procedureFocusRequest, projection]);

  useEffect(() => {
    const workspace = workspaceRef.current;
    if (!workspace || !projection || !dirty) {
      setLiveDiagnostics(null);
      setLiveSourcePreview(null);
      setLiveCanGenerate(null);
      return;
    }
    const edits = buildEdits(workspace, projection);
    if (edits.length === 0) {
      setLiveDiagnostics(null);
      setLiveSourcePreview(null);
      setLiveCanGenerate(null);
      return;
    }
    const sequence = ++previewSequenceRef.current;
    const timer = window.setTimeout(() => {
      void previewProcedureChange(element.id, edits).then((preview) => {
        if (sequence !== previewSequenceRef.current || !preview) return;
        setLiveDiagnostics(preview.diagnostics);
        setLiveSourcePreview(preview.sourcePreview);
        setLiveCanGenerate(preview.canGenerate);
      }).catch(() => {
        if (sequence !== previewSequenceRef.current) return;
        setLiveDiagnostics(null);
        setLiveSourcePreview(null);
        setLiveCanGenerate(null);
      });
    }, 180);
    return () => window.clearTimeout(timer);
  }, [dirty, element.id, graphNodes, previewProcedureChange, projection]);

  const filteredCatalog = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (projection?.nodeCatalog ?? []).filter((item) =>
      (category === 'all' || item.category === category)
      && (!needle || item.type.toLowerCase().includes(needle) || catalogLabel(item).toLowerCase().includes(needle))
    );
  }, [category, projection, search]);

  const categories = useMemo(() => Array.from(new Set((projection?.nodeCatalog ?? []).map((item) => item.category))),
    [projection]);

  const recentCatalog = useMemo(() => recentTypes
    .map((type) => projection?.nodeCatalog.find((item) => item.type === type))
    .filter((item): item is ProcedureNodeCatalogItem => Boolean(item)), [projection, recentTypes]);

  const graphSearchResults = useMemo(() => {
    const needle = graphSearch.trim().toLowerCase();
    if (!needle) return [];
    return graphNodes.filter((node) => {
      const item = projection?.nodeCatalog.find((candidate) => candidate.type === node.type);
      const values = Object.entries(node.fields).flatMap(([name, value]) => [name, String(value)]);
      return [node.type, item ? catalogLabel(item) : '', ...values]
        .some((value) => value.toLowerCase().includes(needle));
    });
  }, [graphNodes, graphSearch, projection]);

  const selectedGraphNode = useMemo(() => graphNodes.find((node) => node.id === selectedNodeId) ?? null,
    [graphNodes, selectedNodeId]);
  const selectedIrNode = useMemo(() => projection?.ir.nodes.find((node) => node.id === selectedNodeId) ?? null,
    [projection, selectedNodeId]);

  const addBlock = (item: ProcedureNodeCatalogItem) => {
    const workspace = workspaceRef.current;
    if (!workspace || item.availability !== 'available' || projection?.readOnly) return;
    const block = workspace.newBlock(item.type, generateNodeId());
    block.initSvg();
    block.render();
    if ((item.type === 'variables_get_number' || item.type === 'variables_set_number')
      && projection?.symbols.availableVariables.length) {
      block.setFieldValue(projection.symbols.availableVariables[0].name, 'VAR');
    }
    const offset = workspace.getAllBlocks(false).length * 12;
    block.moveBy(72 + offset, 72 + offset);
    block.select();
    setSelectedNodeId(block.id);
    setRecentTypes((current) => [item.type, ...current.filter((type) => type !== item.type)].slice(0, 5));
    setDirty(true);
  };

  const beginResourceRefactor = (sourceResource: string) => {
    if (!sourceResource.trim()) {
      setMessage('该资源引用为空，无法启动批量替换。');
      return;
    }
    if (dirty) {
      setMessage('请先保存当前 Procedure 变更，再执行批量资源替换。');
      return;
    }
    setResourceRefactorDraft({ sourceResource, targetResource: sourceResource });
    setExtractDraft(null);
    setCallRefactorDraft(null);
    setSemanticRefactorPlan(null);
    setPanel('references');
    setMessage(null);
  };

  const previewResourceRefactor = async () => {
    if (!resourceRefactorDraft) return;
    const targetResource = resourceRefactorDraft.targetResource.trim();
    if (!targetResource || targetResource === resourceRefactorDraft.sourceResource) {
      setMessage('请输入与当前资源不同的新资源引用。');
      return;
    }
    setRefactorBusy(true);
    setMessage(null);
    try {
      const plan = await planProcedureRefactor({
        kind: 'replace_resource_target',
        sourceResource: resourceRefactorDraft.sourceResource,
        targetResource
      });
      setSemanticRefactorPlan(plan);
      if (!plan) setMessage('无法生成资源批量替换计划。');
    } catch (error) {
      setSemanticRefactorPlan(null);
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setRefactorBusy(false);
    }
  };

  const applyResourceRefactor = async () => {
    if (!resourceRefactorDraft || !semanticRefactorPlan?.safety.ready) return;
    setRefactorBusy(true);
    setMessage(null);
    try {
      const result = await applyWorkspacePlan(semanticRefactorPlan);
      if (result.status !== 'committed') {
        setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '资源批量替换计划应用失败。');
        return;
      }
      const recovery = result.recoveryPointId ? `，恢复点 ${result.recoveryPointId}` : '';
      setMessage(`已批量替换资源 ${resourceRefactorDraft.sourceResource} → ${resourceRefactorDraft.targetResource.trim()}${recovery}。`);
      setResourceRefactorDraft(null);
      setSemanticRefactorPlan(null);
      const refreshed = await getProcedureEditor(element.id);
      if (refreshed) setProjection(refreshed);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setRefactorBusy(false);
    }
  };

  const beginExtraction = () => {
    if (!selectedIrNode || selectedIrNode.kind !== 'statement' || selectedIrNode.type === 'event_trigger' || selectedIrNode.unknown) {
      setMessage('请选择一个受支持的语句节点再执行提取。');
      return;
    }
    if (dirty) {
      setMessage('请先保存当前 Procedure 变更，再执行跨工作区逻辑提取。');
      return;
    }
    const normalizedType = selectedIrNode.type.toLowerCase().replace(/[^a-z0-9_]+/g, '_');
    setExtractDraft({
      nodeId: selectedIrNode.id,
      nodeType: selectedIrNode.type,
      newProcedureName: `${element.name}_${normalizedType}_logic`
    });
    setCallRefactorDraft(null);
    setResourceRefactorDraft(null);
    setSemanticRefactorPlan(null);
    setPanel('source');
    setMessage(null);
  };

  const previewExtraction = async () => {
    if (!extractDraft) return;
    const newProcedureName = extractDraft.newProcedureName.trim();
    if (!newProcedureName) {
      setMessage('请输入新 Procedure 名称。');
      return;
    }
    setRefactorBusy(true);
    setMessage(null);
    try {
      const plan = await planProcedureRefactor({
        kind: 'extract_node',
        elementId: element.id,
        nodeId: extractDraft.nodeId,
        newProcedureName
      });
      setSemanticRefactorPlan(plan);
      if (!plan) setMessage('无法生成逻辑提取计划。');
    } catch (error) {
      setSemanticRefactorPlan(null);
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setRefactorBusy(false);
    }
  };

  const applyExtraction = async () => {
    if (!extractDraft || !semanticRefactorPlan?.safety.ready) return;
    setRefactorBusy(true);
    setMessage(null);
    try {
      const result = await applyWorkspacePlan(semanticRefactorPlan);
      if (result.status !== 'committed') {
        setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '逻辑提取计划应用失败。');
        return;
      }
      const recovery = result.recoveryPointId ? `，恢复点 ${result.recoveryPointId}` : '';
      setMessage(`已将 ${extractDraft.nodeType} 提取为 ${extractDraft.newProcedureName.trim()}${recovery}。`);
      setExtractDraft(null);
      setSemanticRefactorPlan(null);
      const refreshed = await getProcedureEditor(element.id);
      if (refreshed) setProjection(refreshed);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setRefactorBusy(false);
    }
  };

  const beginCallRefactor = (sourceProcedureId: string | null, sourceName: string) => {
    if (!sourceProcedureId) {
      setMessage('该调用目标没有可用于批量重构的稳定 Procedure 身份。');
      return;
    }
    if (dirty) {
      setMessage('请先保存当前 Procedure 变更，再执行批量调用替换。');
      return;
    }
    const alternative = projection?.symbols.availableProcedures.find((candidate) => candidate.id !== sourceProcedureId);
    if (!alternative) {
      setMessage('工作区中没有其他 Procedure 可作为替换目标。');
      return;
    }
    setCallRefactorDraft({ sourceProcedureId, sourceName, targetProcedureId: alternative.id });
    setExtractDraft(null);
    setResourceRefactorDraft(null);
    setSemanticRefactorPlan(null);
    setPanel('references');
    setMessage(null);
  };

  const previewCallRefactor = async () => {
    if (!callRefactorDraft) return;
    setRefactorBusy(true);
    setMessage(null);
    try {
      const plan = await planProcedureRefactor({
        kind: 'replace_call_target',
        sourceProcedureId: callRefactorDraft.sourceProcedureId,
        targetProcedureId: callRefactorDraft.targetProcedureId
      });
      setSemanticRefactorPlan(plan);
      if (!plan) setMessage('无法生成批量调用替换计划。');
    } catch (error) {
      setSemanticRefactorPlan(null);
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setRefactorBusy(false);
    }
  };

  const applyCallRefactor = async () => {
    if (!callRefactorDraft || !semanticRefactorPlan?.safety.ready) return;
    setRefactorBusy(true);
    setMessage(null);
    try {
      const target = projection?.symbols.availableProcedures.find((candidate) => candidate.id === callRefactorDraft.targetProcedureId);
      const result = await applyWorkspacePlan(semanticRefactorPlan);
      if (result.status !== 'committed') {
        setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '批量调用替换计划应用失败。');
        return;
      }
      const recovery = result.recoveryPointId ? `，恢复点 ${result.recoveryPointId}` : '';
      setMessage(`已批量替换 ${callRefactorDraft.sourceName} → ${target?.name ?? callRefactorDraft.targetProcedureId}${recovery}。`);
      setCallRefactorDraft(null);
      setSemanticRefactorPlan(null);
      const refreshed = await getProcedureEditor(element.id);
      if (refreshed) setProjection(refreshed);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setRefactorBusy(false);
    }
  };

  const beginVariableRefactor = (entryId: string | null, name: string) => {
    if (!entryId) {
      setMessage(`变量 ${name || '(未命名)'} 没有可重构的工作区 Registry 身份。`);
      return;
    }
    if (dirty) {
      setMessage('请先保存当前 Procedure 变更，再启动跨工作区变量重命名。');
      return;
    }
    setRefactorDraft({ entryId, oldName: name, newName: name });
    setExtractDraft(null);
    setCallRefactorDraft(null);
    setResourceRefactorDraft(null);
    setSemanticRefactorPlan(null);
    setRefactorImpact(null);
    setRefactorPlan(null);
    setPanel('references');
    setMessage(null);
  };

  const previewVariableRefactor = async () => {
    if (!refactorDraft) return;
    const newName = refactorDraft.newName.trim();
    if (!newName || newName === refactorDraft.oldName) {
      setMessage('请输入与当前名称不同的新变量名称。');
      return;
    }
    setRefactorBusy(true);
    setMessage(null);
    try {
      const [impact, plan] = await Promise.all([
        previewRegistryRename(refactorDraft.entryId, newName),
        planWorkspaceChanges([{
          operation: 'rename_registry_entry',
          payload: { entryId: refactorDraft.entryId, newName }
        }], true)
      ]);
      setRefactorImpact(impact);
      setRefactorPlan(plan);
      if (!impact || !plan) setMessage('无法生成变量重命名影响预览。');
    } catch (error) {
      setRefactorImpact(null);
      setRefactorPlan(null);
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setRefactorBusy(false);
    }
  };

  const applyVariableRefactor = async () => {
    if (!refactorDraft || !refactorPlan || !refactorPlan.safety.ready) return;
    setRefactorBusy(true);
    setMessage(null);
    try {
      const result = await applyWorkspacePlan(refactorPlan);
      if (result.status !== 'committed') {
        setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : '变量重命名计划应用失败。');
        return;
      }
      const recovery = result.recoveryPointId ? `，恢复点 ${result.recoveryPointId}` : '';
      setMessage(`已安全重命名 ${refactorDraft.oldName} → ${refactorDraft.newName.trim()}${recovery}。`);
      setRefactorDraft(null);
      setRefactorImpact(null);
      setRefactorPlan(null);
      const refreshed = await getProcedureEditor(element.id);
      if (refreshed) setProjection(refreshed);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setRefactorBusy(false);
    }
  };

  const save = async () => {
    const workspace = workspaceRef.current;
    if (!workspace || !projection) return;
    const edits = buildEdits(workspace, projection);
    if (edits.length === 0) {
      setDirty(false);
      setMessage('没有需要保存的变更。');
      return;
    }
    setSaving(true);
    setMessage(null);
    try {
      const result = await updateProcedure(element.id, edits);
      if (result.status !== 'committed') {
        setMessage(result.diagnostics[0] ? t(result.diagnostics[0].message) : 'Procedure 保存失败。');
        return;
      }
      setDirty(false);
      setMessage(`已保存 ${edits.length} 项结构化变更，工作区修订为 r${result.newRevision}。`);
      const refreshed = await getProcedureEditor(element.id);
      if (refreshed) setProjection(refreshed);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setSaving(false);
    }
  };

  const diagnostics: Diagnostic[] = liveDiagnostics
    ?? projection?.diagnostics
    ?? projection?.references.diagnostics
    ?? [];
  const sourcePreview = liveSourcePreview ?? projection?.sourcePreview ?? '//';

  useEffect(() => {
    setGraphSearchIndex(-1);
  }, [graphSearch]);

  const focusPanel = (nextPanel: ProcedurePanel, tab: HTMLButtonElement) => {
    setPanel(nextPanel);
    const tabs = Array.from(tab.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]') ?? []);
    tabs[procedurePanels.indexOf(nextPanel)]?.focus();
  };

  const handlePanelKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    const currentIndex = procedurePanels.indexOf(panel);
    let nextIndex = currentIndex;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % procedurePanels.length;
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + procedurePanels.length) % procedurePanels.length;
    else if (event.key === 'Home') nextIndex = 0;
    else if (event.key === 'End') nextIndex = procedurePanels.length - 1;
    else return;
    event.preventDefault();
    focusPanel(procedurePanels[nextIndex], event.currentTarget);
  };

  const nodeTypeLabel = (type: string) => {
    if (type === 'event_trigger') return '入口触发器';
    const item = projection?.nodeCatalog.find((candidate) => candidate.type === type);
    return item ? catalogLabel(item) : `未知节点 ${type}`;
  };

  const nodeLabel = (node: ProcedureNode) => {
    return nodeTypeLabel(node.type);
  };

  const nodeAccessibleName = (node: ProcedureNode) => {
    const fields = Object.entries(node.fields).map(([name, value]) => `${name} ${String(value)}`);
    const ports = Object.entries(node.inputs).map(([name, target]) => `${name} 连接 ${connectedNodeLabel(target)}`);
    ports.push(node.next ? `下一个连接 ${connectedNodeLabel(node.next)}` : '下一个未连接');
    const output = node.kind === 'value' ? '输出端口' : '语句节点';
    return [nodeLabel(node), node.type, output, ...fields, ...ports].join('，');
  };

  const connectedNodeLabel = (nodeId: string) => {
    const target = projection?.ir.nodes.find((node) => node.id === nodeId);
    return target ? `${nodeLabel(target)} (${target.type})` : `未知节点 ${nodeId}`;
  };

  const selectNode = (nodeId: string) => {
    const workspace = workspaceRef.current;
    const block = workspace?.getBlockById(nodeId);
    if (!workspace || !block) return;
    block.select();
    workspace.centerOnBlock(nodeId);
    setSelectedNodeId(nodeId);
  };

  const navigateGraphSearch = (direction: -1 | 1) => {
    if (graphSearchResults.length === 0) return;
    const nextIndex = direction === 1
      ? (graphSearchIndex + 1 + graphSearchResults.length) % graphSearchResults.length
      : (graphSearchIndex <= 0 ? graphSearchResults.length - 1 : graphSearchIndex - 1);
    setGraphSearchIndex(nextIndex);
    selectNode(graphSearchResults[nextIndex].id);
  };

  return (
    <section className="procedure-workbench" data-testid="procedure-workbench">
      <header className="procedure-toolbar">
        <button className="procedure-icon-button" onClick={onClose} aria-label="返回元素列表" title="返回元素列表">
          <ArrowLeft size={16} />
        </button>
        <div className="procedure-title">
          <strong>{element.displayName}</strong>
          <span>Procedure · {dirty ? '有未保存变更' : `r${projection?.baseRevision ?? '-'}`}</span>
        </div>
        <div className="procedure-toolbar-actions">
          <button className="procedure-icon-button" onClick={() => workspaceRef.current?.undo(false)} aria-label="撤销" title="撤销"><Undo2 size={15} /></button>
          <button className="procedure-icon-button" onClick={() => workspaceRef.current?.undo(true)} aria-label="重做" title="重做"><Redo2 size={15} /></button>
          <button className="procedure-icon-button" onClick={() => workspaceRef.current?.cleanUp()} aria-label="自动布局" title="自动布局"><AlignStartVertical size={15} /></button>
          <button className="btn-primary procedure-save" onClick={() => void save()} disabled={!dirty || saving || projection?.readOnly}>
            <Save size={14} /><span>{saving ? '保存中' : '保存'}</span>
          </button>
        </div>
      </header>

      {message && <div className="procedure-message" role="status">{message}</div>}
      <div className="procedure-body">
        <aside className="procedure-palette" aria-label="Procedure 节点面板">
          <div className="procedure-search">
            <Search size={14} />
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索节点" aria-label="搜索 Procedure 节点" />
          </div>
          <div className="procedure-palette-filter">
            <Filter size={13} aria-hidden="true" />
            <select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="筛选 Procedure 节点分类">
              <option value="all">全部分类</option>
              {categories.map((item) => (
                <option value={item} key={item}>{procedureCategoryLabels[item] ?? item}</option>
              ))}
            </select>
          </div>
          {recentCatalog.length > 0 && (
            <div className="procedure-recent" data-testid="procedure-recent-nodes">
              <div className="procedure-section-label"><Clock3 size={12} aria-hidden="true" />最近使用</div>
              {recentCatalog.map((item) => (
                <button
                  key={`recent-${item.type}`}
                  type="button"
                  className="procedure-recent-button"
                  disabled={item.availability !== 'available' || projection?.readOnly}
                  onClick={() => addBlock(item)}
                >
                  {catalogLabel(item)}
                </button>
              ))}
            </div>
          )}
          <div className="procedure-node-list">
            {filteredCatalog.map((item) => (
              <button
                key={item.type}
                className="procedure-node-button"
                disabled={item.availability !== 'available' || projection?.readOnly}
                onClick={() => addBlock(item)}
                title={item.reasonCode ?? catalogLabel(item)}
              >
                <Braces size={14} />
                <span><strong>{catalogLabel(item)}</strong><small>{item.category} · {item.output}</small></span>
              </button>
            ))}
          </div>
        </aside>

        <div className="procedure-canvas-wrap">
          {loading && <div className="procedure-loading">正在加载 Procedure IR…</div>}
          {!loading && !projection && <div className="procedure-loading"><CircleAlert size={20} />无法加载 Procedure。</div>}
          <div ref={hostRef} className="procedure-canvas" aria-label="Procedure 可视化画布" />
        </div>

        <aside className="procedure-inspector">
          <div className="procedure-tabs" role="tablist" aria-label="Procedure 检查面板">
            <button type="button" id="procedure-tab-source" role="tab" aria-controls="procedure-panel-source" aria-selected={panel === 'source'} tabIndex={panel === 'source' ? 0 : -1} onClick={() => setPanel('source')} onKeyDown={handlePanelKeyDown}><Code2 size={13} aria-hidden="true" />源码</button>
            <button type="button" id="procedure-tab-diagnostics" role="tab" aria-controls="procedure-panel-diagnostics" aria-selected={panel === 'diagnostics'} tabIndex={panel === 'diagnostics' ? 0 : -1} onClick={() => setPanel('diagnostics')} onKeyDown={handlePanelKeyDown}><CircleAlert size={13} aria-hidden="true" />诊断</button>
            <button type="button" id="procedure-tab-references" role="tab" aria-controls="procedure-panel-references" aria-selected={panel === 'references'} tabIndex={panel === 'references' ? 0 : -1} onClick={() => setPanel('references')} onKeyDown={handlePanelKeyDown}><Link2 size={13} aria-hidden="true" />引用</button>
            <button type="button" id="procedure-tab-outline" role="tab" aria-controls="procedure-panel-outline" aria-selected={panel === 'outline'} tabIndex={panel === 'outline' ? 0 : -1} onClick={() => setPanel('outline')} onKeyDown={handlePanelKeyDown}><ListTree size={13} aria-hidden="true" />节点</button>
          </div>
          <div className="procedure-graph-navigation" data-testid="procedure-graph-navigation">
            <div className="procedure-search procedure-graph-search">
              <Search size={14} />
              <input
                value={graphSearch}
                onChange={(event) => setGraphSearch(event.target.value)}
                placeholder="查找当前图"
                aria-label="搜索当前 Procedure 图"
              />
            </div>
            <div className="procedure-search-controls">
              <span aria-live="polite">
                {graphSearch.trim() ? `${graphSearchResults.length} 个匹配` : `${graphNodes.length} 个节点`}
              </span>
              <button type="button" onClick={() => navigateGraphSearch(-1)} disabled={graphSearchResults.length === 0} aria-label="上一个匹配节点"><ChevronLeft size={14} /></button>
              <button type="button" onClick={() => navigateGraphSearch(1)} disabled={graphSearchResults.length === 0} aria-label="下一个匹配节点"><ChevronRight size={14} /></button>
            </div>
            {selectedGraphNode && (
              <button type="button" className="procedure-selected-location" onClick={() => selectNode(selectedGraphNode.id)} data-testid="procedure-selected-location">
                <LocateFixed size={12} aria-hidden="true" />
                <span>{nodeTypeLabel(selectedGraphNode.type)} · x {Math.round(selectedGraphNode.x)}, y {Math.round(selectedGraphNode.y)}</span>
              </button>
            )}
          </div>
          {panel === 'source' && (
            <div id="procedure-panel-source" role="tabpanel" aria-labelledby="procedure-tab-source" className="procedure-panel-content">
              <div className="procedure-panel-meta">
                <span className="badge badge-green">{projection?.sourceOwnership ?? 'generated'}</span>
                <span>{dirty ? (liveCanGenerate === false ? '实时预览 · 存在阻断诊断' : '实时预览') : '只读'}</span>
              </div>
              {selectedIrNode && selectedIrNode.kind === 'statement' && selectedIrNode.type !== 'event_trigger' && !selectedIrNode.unknown && (
                <button
                  type="button"
                  className="procedure-refactor-start procedure-extract-start"
                  data-testid="procedure-extract-start"
                  disabled={projection?.readOnly || dirty || refactorBusy}
                  onClick={beginExtraction}
                  aria-label={`提取节点 ${selectedIrNode.type} 为 Procedure`}
                >提取选中逻辑为 Procedure</button>
              )}
              {extractDraft && (
                <section className="procedure-refactor-card" data-testid="procedure-extract-refactor" aria-label="提取可复用 Procedure">
                  <div className="procedure-section-label">可复用逻辑提取 · {extractDraft.nodeType}</div>
                  <label>
                    <span>新 Procedure 名称</span>
                    <input
                      aria-label="提取后的 Procedure 名称"
                      value={extractDraft.newProcedureName}
                      disabled={refactorBusy}
                      onChange={(event) => {
                        setExtractDraft({ ...extractDraft, newProcedureName: event.target.value });
                        setSemanticRefactorPlan(null);
                      }}
                    />
                  </label>
                  <div className="procedure-refactor-actions">
                    <button type="button" onClick={() => void previewExtraction()} disabled={refactorBusy}>预览提取计划</button>
                    <button type="button" className="btn-primary" onClick={() => void applyExtraction()}
                      disabled={refactorBusy || !semanticRefactorPlan?.safety.ready}>应用提取</button>
                    <button type="button" onClick={() => { setExtractDraft(null); setSemanticRefactorPlan(null); }} disabled={refactorBusy}>取消</button>
                  </div>
                  {semanticRefactorPlan && (
                    <div className={`procedure-refactor-preview ${semanticRefactorPlan.safety.ready ? 'ready' : 'blocked'}`} data-testid="procedure-extract-preview">
                      <strong>{semanticRefactorPlan.operationCount} 步原子计划 · {semanticRefactorPlan.semanticDiff.length} 项语义变更</strong>
                      <span>{semanticRefactorPlan.changedPaths.length} 条持久化路径</span>
                      <span>{semanticRefactorPlan.safety.ready ? '恢复保护可用：应用前将创建 recovery point。' : '恢复保护不可用：禁止应用。'}</span>
                      <code>{semanticRefactorPlan.planId}</code>
                      <WorkspacePlanReview plan={semanticRefactorPlan} testId="procedure-extract-plan-review" />
                    </div>
                  )}
                </section>
              )}
              <pre>{sourcePreview}</pre>
            </div>
          )}
          {panel === 'diagnostics' && (
            <div id="procedure-panel-diagnostics" role="tabpanel" aria-labelledby="procedure-tab-diagnostics" className="procedure-panel-content procedure-list-content">
              {diagnostics.length === 0 ? <p>当前图没有诊断。</p> : diagnostics.map((diagnostic) => (
                <div className={`procedure-diagnostic ${diagnostic.severity}`} key={`${diagnostic.code}-${diagnostic.path}`}>
                  <strong>{diagnostic.code}</strong><span>{t(diagnostic.message)}</span><code>{diagnostic.path}</code>
                  {diagnosticNodeId(diagnostic) && (
                    <button type="button" onClick={() => selectNode(diagnosticNodeId(diagnostic)!)}>
                      <LocateFixed size={12} aria-hidden="true" />定位节点
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
          {panel === 'references' && (
            <div id="procedure-panel-references" role="tabpanel" aria-labelledby="procedure-tab-references" className="procedure-panel-content procedure-list-content">
              <section className="procedure-relationship-overview" data-testid="procedure-relationship-overview" aria-label="Procedure 关系概览">
                <div className="procedure-relationship-summary">
                  <strong>关系概览</strong>
                  <span>{projection?.relationships.stats.inboundCount ?? 0} 入站 · {projection?.relationships.stats.outboundCount ?? 0} 出站</span>
                </div>
                <div className="procedure-relationship-kinds">
                  {Object.entries(projection?.relationships.stats.byKind ?? {}).map(([kind, count]) => (
                    <span key={kind}><code>{kind}</code> {count}</span>
                  ))}
                </div>
                <div className="procedure-relationship-columns">
                  <div>
                    <strong>谁在引用它</strong>
                    {(projection?.relationships.inbound ?? []).length === 0 && <small>没有入站引用</small>}
                    {(projection?.relationships.inbound ?? []).map((edge) => (
                      <div className="procedure-relationship-row" key={`in-${edge.id}`}>
                        <span>{edge.sourceDisplayName || edge.sourceName || edge.sourceId}</span>
                        <code>{edge.sourceType || 'element'} · {edge.kind}</code>
                      </div>
                    ))}
                  </div>
                  <div>
                    <strong>当前 Procedure 引用</strong>
                    {(projection?.relationships.outbound ?? []).length === 0 && <small>没有出站依赖</small>}
                    {(projection?.relationships.outbound ?? []).map((edge) => (
                      <div className="procedure-relationship-row" key={`out-${edge.id}`}>
                        <span>{edge.targetDisplayName || edge.targetName || edge.target}</span>
                        <code>{edge.targetType || edge.kind} · {edge.kind}</code>
                      </div>
                    ))}
                  </div>
                </div>
              </section>
              <div className="procedure-symbol-summary" data-testid="procedure-symbol-summary">
                <strong>图内符号</strong>
                <span>{projection?.symbols?.stats.variableCount ?? 0} 变量 · {projection?.symbols?.stats.resourceCount ?? 0} 资源 · {projection?.symbols?.stats.callCount ?? 0} 调用</span>
              </div>
              {(projection?.symbols?.variables ?? []).map((symbol) => (
                <div className="procedure-symbol-refactor-row" key={`variable-${symbol.nodeId}`}>
                  <button type="button" className="procedure-symbol-row" onClick={() => selectNode(symbol.nodeId)}>
                    <span>变量 · {symbol.access === 'read' ? '读取' : '写入'}</span>
                    <code>{symbol.name || '(未命名)'}</code>
                    {symbol.registryEntryId && <small>{symbol.dataType ?? 'unknown'} · {symbol.scope ?? 'global'}</small>}
                  </button>
                  <button
                    type="button"
                    className="procedure-refactor-start"
                    disabled={!symbol.registryEntryId || projection?.readOnly}
                    onClick={() => beginVariableRefactor(symbol.registryEntryId, symbol.name)}
                    aria-label={`重命名变量 ${symbol.name || '(未命名)'}`}
                  >重命名</button>
                </div>
              ))}
              {refactorDraft && (
                <section className="procedure-refactor-card" data-testid="procedure-variable-refactor" aria-label="变量安全重命名">
                  <div className="procedure-section-label">安全重命名 · {refactorDraft.oldName}</div>
                  <label>
                    <span>新变量名称</span>
                    <input
                      aria-label="新的变量名称"
                      value={refactorDraft.newName}
                      disabled={refactorBusy}
                      onChange={(event) => {
                        setRefactorDraft({ ...refactorDraft, newName: event.target.value });
                        setRefactorImpact(null);
                        setRefactorPlan(null);
                      }}
                    />
                  </label>
                  <div className="procedure-refactor-actions">
                    <button type="button" onClick={() => void previewVariableRefactor()} disabled={refactorBusy}>预览安全重命名</button>
                    <button
                      type="button"
                      className="btn-primary"
                      onClick={() => void applyVariableRefactor()}
                      disabled={refactorBusy || !refactorPlan?.safety.ready || !refactorImpact?.canApply}
                    >应用重构</button>
                    <button type="button" onClick={() => {
                      setRefactorDraft(null);
                      setRefactorImpact(null);
                      setRefactorPlan(null);
                    }} disabled={refactorBusy}>取消</button>
                  </div>
                  {refactorImpact && refactorPlan && (
                    <div className={`procedure-refactor-preview ${refactorPlan.safety.ready ? 'ready' : 'blocked'}`} data-testid="procedure-refactor-preview">
                      <strong>{refactorImpact.impactedElementCount} 个受影响元素 · {refactorPlan.semanticDiff.length} 项语义变更</strong>
                      <span>{refactorPlan.changedPaths.length} 条持久化路径</span>
                      <span>{refactorPlan.safety.ready
                        ? '恢复保护可用：应用前将强制创建 recovery point。'
                        : '恢复保护不可用：该重构被禁止应用。'}</span>
                      <code>{refactorPlan.planId}</code>
                      <WorkspacePlanReview plan={refactorPlan} testId="procedure-variable-plan-review" />
                    </div>
                  )}
                </section>
              )}
              {(projection?.symbols?.resources ?? []).map((symbol) => (
                <div className="procedure-symbol-refactor-row" key={`resource-${symbol.nodeId}`}>
                  <button type="button" className="procedure-symbol-row" onClick={() => selectNode(symbol.nodeId)}>
                    <span>资源 · {symbol.kind}</span><code>{symbol.target || '(未设置)'}</code>
                  </button>
                  <button type="button" className="procedure-refactor-start"
                    disabled={!symbol.target || projection?.readOnly}
                    onClick={() => beginResourceRefactor(symbol.target)}
                    aria-label={`批量替换资源引用 ${symbol.target || '(未设置)'}`}
                  >批量替换</button>
                </div>
              ))}
              {resourceRefactorDraft && (
                <section className="procedure-refactor-card" data-testid="procedure-resource-refactor" aria-label="批量替换 Procedure 资源引用">
                  <div className="procedure-section-label">资源引用批量替换 · {resourceRefactorDraft.sourceResource}</div>
                  <label>
                    <span>替换为资源</span>
                    <input aria-label="新的资源引用" value={resourceRefactorDraft.targetResource} disabled={refactorBusy}
                      onChange={(event) => {
                        setResourceRefactorDraft({ ...resourceRefactorDraft, targetResource: event.target.value });
                        setSemanticRefactorPlan(null);
                      }} />
                  </label>
                  <div className="procedure-refactor-actions">
                    <button type="button" onClick={() => void previewResourceRefactor()} disabled={refactorBusy}>预览资源替换</button>
                    <button type="button" className="btn-primary" onClick={() => void applyResourceRefactor()}
                      disabled={refactorBusy || !semanticRefactorPlan?.safety.ready}>应用资源替换</button>
                    <button type="button" onClick={() => { setResourceRefactorDraft(null); setSemanticRefactorPlan(null); }} disabled={refactorBusy}>取消</button>
                  </div>
                  {semanticRefactorPlan && (
                    <div className={`procedure-refactor-preview ${semanticRefactorPlan.safety.ready ? 'ready' : 'blocked'}`} data-testid="procedure-resource-refactor-preview">
                      <span>{semanticRefactorPlan.safety.ready ? '恢复保护可用：资源替换将作为单个 revision 提交。' : '恢复保护不可用：禁止应用。'}</span>
                      <code>{semanticRefactorPlan.planId}</code>
                      <WorkspacePlanReview plan={semanticRefactorPlan} testId="procedure-resource-plan-review" />
                    </div>
                  )}
                </section>
              )}
              {(projection?.symbols?.calls ?? []).map((symbol) => (
                <div className="procedure-symbol-refactor-row" key={`call-${symbol.nodeId}`}>
                  <button type="button" className="procedure-symbol-row" onClick={() => selectNode(symbol.nodeId)}>
                    <span>Procedure 调用</span><code>{symbol.targetName || symbol.target || '(未设置)'}</code>
                    {symbol.targetId && <small>{symbol.targetId}</small>}
                  </button>
                  <button type="button" className="procedure-refactor-start"
                    disabled={!symbol.targetId || projection?.readOnly || (projection?.symbols.availableProcedures.length ?? 0) < 2}
                    onClick={() => beginCallRefactor(symbol.targetId, symbol.targetName || symbol.target)}
                    aria-label={`批量替换 Procedure 调用 ${symbol.targetName || symbol.target}`}
                  >批量替换</button>
                </div>
              ))}
              {callRefactorDraft && (
                <section className="procedure-refactor-card" data-testid="procedure-call-refactor" aria-label="批量替换 Procedure 调用">
                  <div className="procedure-section-label">批量替换调用 · {callRefactorDraft.sourceName}</div>
                  <label>
                    <span>替换为</span>
                    <select aria-label="新的 Procedure 调用目标" value={callRefactorDraft.targetProcedureId} disabled={refactorBusy}
                      onChange={(event) => {
                        setCallRefactorDraft({ ...callRefactorDraft, targetProcedureId: event.target.value });
                        setSemanticRefactorPlan(null);
                      }}>
                      {(projection?.symbols.availableProcedures ?? []).filter((candidate) => candidate.id !== callRefactorDraft.sourceProcedureId)
                        .map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.name}</option>)}
                    </select>
                  </label>
                  <div className="procedure-refactor-actions">
                    <button type="button" onClick={() => void previewCallRefactor()} disabled={refactorBusy}>预览批量替换</button>
                    <button type="button" className="btn-primary" onClick={() => void applyCallRefactor()}
                      disabled={refactorBusy || !semanticRefactorPlan?.safety.ready}>应用批量替换</button>
                    <button type="button" onClick={() => { setCallRefactorDraft(null); setSemanticRefactorPlan(null); }} disabled={refactorBusy}>取消</button>
                  </div>
                  {semanticRefactorPlan && (
                    <div className={`procedure-refactor-preview ${semanticRefactorPlan.safety.ready ? 'ready' : 'blocked'}`} data-testid="procedure-call-refactor-preview">
                      <strong>{semanticRefactorPlan.operationCount} 个受影响 Procedure · {semanticRefactorPlan.semanticDiff.length} 项语义变更</strong>
                      <span>{semanticRefactorPlan.changedPaths.length} 条持久化路径</span>
                      <span>{semanticRefactorPlan.safety.ready ? '恢复保护可用：批量替换将作为单个 revision 提交。' : '恢复保护不可用：禁止应用。'}</span>
                      <code>{semanticRefactorPlan.planId}</code>
                      <WorkspacePlanReview plan={semanticRefactorPlan} testId="procedure-call-plan-review" />
                    </div>
                  )}
                </section>
              )}
              <p>{projection?.references.stats.edgeCount ?? 0} 条引用 · 增量索引</p>
              {(projection?.references.edges ?? []).map((edge, index) => (
                <div className="procedure-reference" key={String(edge.id ?? index)}><code>{String(edge.sourcePath ?? '')}</code><span>→ {String(edge.target ?? '')}</span></div>
              ))}
            </div>
          )}
          {panel === 'outline' && (
            <div id="procedure-panel-outline" role="tabpanel" aria-labelledby="procedure-tab-outline" className="procedure-panel-content procedure-list-content">
              <ol className="procedure-node-outline" data-testid="procedure-node-outline" aria-label="Procedure 节点与端口">
                {(projection?.ir.nodes ?? []).map((node) => (
                  <li key={node.id}>
                    <button type="button" onClick={() => selectNode(node.id)} aria-label={nodeAccessibleName(node)}>
                      <span><strong>{nodeLabel(node)}</strong><code>{node.type}</code></span>
                      <small>{node.kind === 'value' ? '输出' : '语句'} · {Object.keys(node.inputs).length + (node.next ? 1 : 0)} 个连接</small>
                    </button>
                    <dl>
                      {Object.entries(node.inputs).map(([port, target]) => (
                        <React.Fragment key={port}><dt>{port}</dt><dd>{connectedNodeLabel(target)}</dd></React.Fragment>
                      ))}
                      <dt>下一个</dt><dd>{node.next ? connectedNodeLabel(node.next) : '未连接'}</dd>
                    </dl>
                  </li>
                ))}
              </ol>
            </div>
          )}
        </aside>
      </div>
    </section>
  );
};
