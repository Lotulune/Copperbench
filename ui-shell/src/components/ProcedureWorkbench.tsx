import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import * as Blockly from 'blockly/core';
import 'blockly/blocks';
import * as BlocklyEnglish from 'blockly/msg/en';
import {
  AlignStartVertical,
  ArrowLeft,
  Braces,
  CircleAlert,
  Code2,
  Link2,
  ListTree,
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
  ProcedureNodeCatalogItem
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
      args0: [{ type: 'field_input', name: 'procedureId', text: 'procedure-id' }],
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

export const ProcedureWorkbench: React.FC<ProcedureWorkbenchProps> = ({ element, onClose }) => {
  const { getProcedureEditor, updateProcedure } = useWorkbench();
  const hostRef = useRef<HTMLDivElement | null>(null);
  const workspaceRef = useRef<Blockly.WorkspaceSvg | null>(null);
  const [projection, setProjection] = useState<ProcedureEditorProjection | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [search, setSearch] = useState('');
  const [panel, setPanel] = useState<ProcedurePanel>('source');
  const [message, setMessage] = useState<string | null>(null);

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
    const listener = (event: Blockly.Events.Abstract) => {
      if (!event.isUiEvent) setDirty(true);
    };
    workspace.addChangeListener(listener);
    window.setTimeout(() => Blockly.svgResize(workspace), 0);
    return () => {
      workspace.removeChangeListener(listener);
      workspace.dispose();
      workspaceRef.current = null;
    };
  }, [projection]);

  const filteredCatalog = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (projection?.nodeCatalog ?? []).filter((item) =>
      !needle || item.type.toLowerCase().includes(needle) || catalogLabel(item).toLowerCase().includes(needle)
    );
  }, [projection, search]);

  const addBlock = (item: ProcedureNodeCatalogItem) => {
    const workspace = workspaceRef.current;
    if (!workspace || item.availability !== 'available' || projection?.readOnly) return;
    const block = workspace.newBlock(item.type, generateNodeId());
    block.initSvg();
    block.render();
    const offset = workspace.getAllBlocks(false).length * 12;
    block.moveBy(72 + offset, 72 + offset);
    block.select();
    setDirty(true);
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

  const diagnostics: Diagnostic[] = projection?.diagnostics ?? projection?.references.diagnostics ?? [];

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

  const nodeLabel = (node: ProcedureNode) => {
    if (node.type === 'event_trigger') return '入口触发器';
    const item = projection?.nodeCatalog.find((candidate) => candidate.type === node.type);
    return item ? catalogLabel(item) : `未知节点 ${node.type}`;
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
          {panel === 'source' && (
            <div id="procedure-panel-source" role="tabpanel" aria-labelledby="procedure-tab-source" className="procedure-panel-content">
              <div className="procedure-panel-meta"><span className="badge badge-green">{projection?.sourceOwnership ?? 'generated'}</span><span>只读</span></div>
              <pre>{projection?.sourcePreview ?? '//'}</pre>
            </div>
          )}
          {panel === 'diagnostics' && (
            <div id="procedure-panel-diagnostics" role="tabpanel" aria-labelledby="procedure-tab-diagnostics" className="procedure-panel-content procedure-list-content">
              {diagnostics.length === 0 ? <p>当前图没有诊断。</p> : diagnostics.map((diagnostic) => (
                <div className={`procedure-diagnostic ${diagnostic.severity}`} key={`${diagnostic.code}-${diagnostic.path}`}>
                  <strong>{diagnostic.code}</strong><span>{t(diagnostic.message)}</span><code>{diagnostic.path}</code>
                </div>
              ))}
            </div>
          )}
          {panel === 'references' && (
            <div id="procedure-panel-references" role="tabpanel" aria-labelledby="procedure-tab-references" className="procedure-panel-content procedure-list-content">
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
