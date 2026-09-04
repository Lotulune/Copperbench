import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  Box,
  ChevronDown,
  ChevronUp,
  Layers3,
  Plus,
  Save,
  Trash2
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import {
  AssetProjection,
  FieldChange,
  ModElementEditorProjection,
  ModElementSummary
} from '../types/contract';
import { t } from '../i18n';

interface GuiWorkbenchProps {
  element: ModElementSummary;
  onClose: () => void;
}

interface GuiComponentWire {
  type: string;
  data: Record<string, unknown>;
}

interface LayoutIssue {
  index: number | null;
  message: string;
}

const WYSIWYG_WIDTH = 427;
const WYSIWYG_HEIGHT = 240;

const PROCEDURE_FIELDS = new Set([
  'onClick',
  'displayCondition',
  'onSlotChanged',
  'onTakenFromSlot',
  'onStackTransfer',
  'disablePickup',
  'disablePlacement',
  'isCheckedProcedure'
]);

const ANCHORS = [
  '',
  'TOP_LEFT',
  'TOP_CENTER',
  'TOP_RIGHT',
  'CENTER_LEFT',
  'CENTER',
  'CENTER_RIGHT',
  'BOTTOM_LEFT',
  'BOTTOM_CENTER',
  'BOTTOM_RIGHT'
];

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function editorValues(editor: ModElementEditorProjection): Record<string, unknown> {
  return Object.fromEntries(
    editor.sections.flatMap((section) => section.fields.map((field) => [field.path, clone(field.value)]))
  );
}

function componentsFrom(values: Record<string, unknown>): GuiComponentWire[] {
  const raw = values['/components'];
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((entry) => {
    if (!entry || typeof entry !== 'object') return [];
    const root = entry as Record<string, unknown>;
    if (typeof root.type !== 'string' || !root.data || typeof root.data !== 'object' || Array.isArray(root.data)) {
      return [];
    }
    return [{ type: root.type, data: root.data as Record<string, unknown> }];
  });
}

function componentName(component: GuiComponentWire, index: number): string {
  const name = component.data.name;
  if (typeof name === 'string' && name.trim()) return name;
  const id = component.data.id;
  if (typeof id === 'number') return `${component.type}_${id}`;
  const image = component.data.image ?? component.data.sprite;
  if (typeof image === 'string' && image.trim()) return `${component.type}_${image}`;
  return `${component.type}_${index + 1}`;
}

function componentSize(component: GuiComponentWire): { width: number; height: number } {
  const width = typeof component.data.width === 'number' ? component.data.width : undefined;
  const height = typeof component.data.height === 'number' ? component.data.height : undefined;
  if (width !== undefined || height !== undefined) {
    return { width: Math.max(1, width ?? 20), height: Math.max(1, height ?? 20) };
  }
  switch (component.type) {
    case 'inputslot':
    case 'outputslot':
      return { width: 18, height: 18 };
    case 'checkbox':
      return { width: 20, height: 20 };
    case 'label':
      return { width: 72, height: 10 };
    case 'image':
    case 'sprite':
    case 'imagebutton':
      return { width: 32, height: 32 };
    default:
      return { width: 80, height: 20 };
  }
}

function editablePrimitiveEntries(component: GuiComponentWire): Array<[string, unknown]> {
  const priority = ['name', 'x', 'y', 'width', 'height', 'anchorPoint', 'locked', 'text', 'image', 'hoveredImage',
    'sprite', 'spritesCount', 'id', 'isUndecorated', 'use1Xscale', 'dropItemsWhenNotBound',
    ...PROCEDURE_FIELDS];
  const entries = Object.entries(component.data).filter(([, value]) =>
    value === null || ['string', 'number', 'boolean'].includes(typeof value)
  );
  return entries.sort(([left], [right]) => {
    const li = priority.indexOf(left);
    const ri = priority.indexOf(right);
    if (li >= 0 && ri >= 0) return li - ri;
    if (li >= 0) return -1;
    if (ri >= 0) return 1;
    return left.localeCompare(right);
  });
}

function isAssetField(key: string): boolean {
  const normalized = key.toLowerCase();
  return normalized === 'image' || normalized === 'hoveredimage' || normalized === 'sprite'
    || normalized.includes('texture');
}

function layoutIssues(values: Record<string, unknown>, components: GuiComponentWire[]): LayoutIssue[] {
  const issues: LayoutIssue[] = [];
  const width = Number(values['/width'] ?? 176);
  const height = Number(values['/height'] ?? 166);
  if (width <= 0 || height <= 0) issues.push({ index: null, message: 'GUI 宽度和高度必须大于 0。' });
  if (width > 512 || height > 512) issues.push({ index: null, message: 'GUI 尺寸超过上游支持的 512 px 上限。' });

  const named = new Map<string, number>();
  components.forEach((component, index) => {
    const name = component.data.name;
    if (typeof name === 'string' && name.trim()) {
      const previous = named.get(name);
      if (previous !== undefined) {
        issues.push({ index, message: `组件名称“${name}”与第 ${previous + 1} 个组件重复。` });
      } else {
        named.set(name, index);
      }
    }
    const x = Number(component.data.x ?? 0);
    const y = Number(component.data.y ?? 0);
    const size = componentSize(component);
    if (!Number.isFinite(x) || !Number.isFinite(y)) {
      issues.push({ index, message: '组件坐标不是有效数字。' });
      return;
    }
    if (x < 0 || y < 0 || x + size.width > WYSIWYG_WIDTH || y + size.height > WYSIWYG_HEIGHT) {
      issues.push({ index, message: '组件超出 MCreator 427×240 WYSIWYG 画布范围。' });
    }
  });
  return issues;
}

function changedFields(base: Record<string, unknown>, values: Record<string, unknown>): FieldChange[] {
  const editablePaths = ['/type', '/width', '/height', '/inventoryOffsetX', '/inventoryOffsetY', '/renderBgLayer',
    '/doesPauseGame', '/components', '/onOpen', '/onTick', '/onClosed'];
  return editablePaths.flatMap((path) =>
    JSON.stringify(base[path]) === JSON.stringify(values[path]) ? [] : [{ path, value: clone(values[path]) }]
  );
}

function createButton(index: number): GuiComponentWire {
  return {
    type: 'button',
    data: {
      anchorPoint: null,
      x: Math.floor(WYSIWYG_WIDTH / 2 - 40),
      y: Math.floor(WYSIWYG_HEIGHT / 2 - 10),
      locked: false,
      width: 80,
      height: 20,
      name: `button_${index + 1}`,
      text: 'Button',
      isUndecorated: false,
      onClick: null,
      displayCondition: null
    }
  };
}

export const GuiWorkbench: React.FC<GuiWorkbenchProps> = ({ element, onClose }) => {
  const {
    getModElementEditor,
    previewModElementChange,
    updateModElement,
    listAssets,
    state
  } = useWorkbench();
  const [editor, setEditor] = useState<ModElementEditorProjection | null>(null);
  const [base, setBase] = useState<Record<string, unknown>>({});
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [assets, setAssets] = useState<AssetProjection | null>(null);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [impact, setImpact] = useState<string[]>([]);
  const requestToken = useRef(0);

  useEffect(() => {
    let cancelled = false;
    setEditor(null);
    setSelectedIndex(null);
    Promise.all([getModElementEditor(element.id), listAssets().catch(() => null)])
      .then(([projection, assetProjection]) => {
        if (cancelled || !projection) return;
        const next = editorValues(projection);
        setEditor(projection);
        setBase(clone(next));
        setValues(clone(next));
        setAssets(assetProjection);
        if (componentsFrom(next).length > 0) setSelectedIndex(0);
      })
      .catch(() => {
        if (!cancelled) setSaveMessage('无法加载 GUI 编辑器投影。');
      });
    return () => {
      cancelled = true;
    };
  }, [element.id, getModElementEditor, listAssets]);

  const components = useMemo(() => componentsFrom(values), [values]);
  const changes = useMemo(() => changedFields(base, values), [base, values]);
  const issues = useMemo(() => layoutIssues(values, components), [values, components]);
  const selected = selectedIndex !== null ? components[selectedIndex] ?? null : null;
  const procedures = useMemo(() => state.elements
    .filter((candidate) => candidate.type === 'procedure' || candidate.type === 'function')
    .sort((left, right) => left.displayName.localeCompare(right.displayName)), [state.elements]);
  const textureAssets = useMemo(() => assets?.assets.filter((asset) => asset.category === 'TEXTURE') ?? [], [assets]);

  useEffect(() => {
    if (changes.length === 0) {
      setImpact([]);
      return;
    }
    const token = ++requestToken.current;
    const timer = window.setTimeout(() => {
      previewModElementChange(element.id, changes)
        .then((preview) => {
          if (requestToken.current === token) setImpact(preview?.generationImpact?.affectedDomains ?? []);
        })
        .catch(() => {
          if (requestToken.current === token) setImpact([]);
        });
    }, 160);
    return () => window.clearTimeout(timer);
  }, [changes, element.id, previewModElementChange]);

  const setValue = (path: string, value: unknown) => {
    setValues((current) => ({ ...current, [path]: value }));
    setSaveMessage(null);
  };

  const setComponents = (next: GuiComponentWire[]) => setValue('/components', next);

  const updateComponentField = (key: string, value: unknown) => {
    if (selectedIndex === null) return;
    const next = clone(components);
    next[selectedIndex] = {
      ...next[selectedIndex],
      data: { ...next[selectedIndex].data, [key]: value }
    };
    setComponents(next);
  };

  const addButton = () => {
    const next = [...clone(components), createButton(components.length)];
    setComponents(next);
    setSelectedIndex(next.length - 1);
  };

  const deleteSelected = () => {
    if (selectedIndex === null) return;
    const next = clone(components);
    next.splice(selectedIndex, 1);
    setComponents(next);
    setSelectedIndex(next.length === 0 ? null : Math.min(selectedIndex, next.length - 1));
  };

  const moveSelected = (offset: -1 | 1) => {
    if (selectedIndex === null) return;
    const target = selectedIndex + offset;
    if (target < 0 || target >= components.length) return;
    const next = clone(components);
    [next[selectedIndex], next[target]] = [next[target], next[selectedIndex]];
    setComponents(next);
    setSelectedIndex(target);
  };

  const save = async () => {
    if (changes.length === 0 || issues.some((issue) => issue.index === null)) return;
    setSaving(true);
    setSaveMessage(null);
    try {
      const result = await updateModElement(element.id, changes);
      if (result.status === 'committed') {
        setBase(clone(values));
        setSaveMessage('GUI 更改已保存。');
      } else {
        setSaveMessage(result.diagnostics.map((diagnostic) => t(diagnostic.message)).join('；') || '保存被 Core 拒绝。');
      }
    } catch {
      setSaveMessage('保存失败，工作区未发生更改。');
    } finally {
      setSaving(false);
    }
  };

  const guiWidth = Number(values['/width'] ?? 176);
  const guiHeight = Number(values['/height'] ?? 166);
  const guiLeft = (WYSIWYG_WIDTH - guiWidth) / 2;
  const guiTop = (WYSIWYG_HEIGHT - guiHeight) / 2;

  if (!editor) {
    return (
      <div data-testid="gui-workbench-loading" style={{ padding: 24, color: 'var(--text-sub)' }}>
        正在加载 GUI 深度编辑器…
      </div>
    );
  }

  return (
    <div data-testid="gui-workbench" style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, height: '100%' }}>
      <header style={{ height: 52, padding: '0 14px', display: 'flex', alignItems: 'center', gap: 10,
        background: 'var(--bg-surface)', borderBottom: '1px solid var(--border-subtle)' }}>
        <button type="button" onClick={onClose} aria-label="返回元素列表" className="btn-secondary" style={{ padding: 6 }}>
          <ArrowLeft size={14} />
        </button>
        <Layers3 size={17} color="var(--accent-copper)" />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--text-main)' }}>{element.displayName}</div>
          <div style={{ fontSize: 10, color: 'var(--text-sub)' }}>GUI 深度编辑 · 同一 Core /components 语义</div>
        </div>
        {impact.length > 0 && (
          <span className="badge badge-blue" data-testid="gui-generation-impact">
            生成影响：{impact.join(', ')}
          </span>
        )}
        {saveMessage && <span style={{ fontSize: 10, color: 'var(--text-sub)' }}>{saveMessage}</span>}
        <button
          type="button"
          className="btn-primary"
          data-testid="gui-save-btn"
          disabled={saving || changes.length === 0 || issues.some((issue) => issue.index === null)}
          onClick={save}
        >
          <Save size={13} /> {saving ? '保存中…' : '保存 GUI'}
        </button>
      </header>

      <div style={{ flex: 1, minHeight: 0, display: 'grid', gridTemplateColumns: '230px minmax(420px, 1fr) 300px' }}>
        <aside style={{ borderRight: '1px solid var(--border-subtle)', background: 'var(--bg-surface)', minHeight: 0,
          display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: 12, borderBottom: '1px solid var(--border-subtle)', display: 'flex', gap: 8,
            alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <div style={{ fontWeight: 700, fontSize: 11, color: 'var(--text-main)' }}>组件树</div>
              <div style={{ fontSize: 9, color: 'var(--text-sub)' }}>{components.length} 个组件</div>
            </div>
            <button type="button" className="btn-secondary" onClick={addButton} data-testid="gui-add-button-component">
              <Plus size={12} /> 按钮
            </button>
          </div>
          <div data-testid="gui-component-tree" style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
            {components.length === 0 ? (
              <div style={{ padding: '24px 8px', fontSize: 10, color: 'var(--text-sub)', textAlign: 'center' }}>
                当前 GUI 没有组件。可先添加一个上游 Button 组件。
              </div>
            ) : components.map((component, index) => {
              const componentIssues = issues.filter((issue) => issue.index === index);
              return (
                <button
                  type="button"
                  key={`${component.type}-${index}`}
                  data-testid={`gui-component-${index}`}
                  aria-pressed={selectedIndex === index}
                  onClick={() => setSelectedIndex(index)}
                  style={{ width: '100%', textAlign: 'left', padding: '8px 9px', marginBottom: 5,
                    border: `1px solid ${selectedIndex === index ? 'var(--accent-copper)' : 'var(--border-subtle)'}`,
                    borderRadius: 'var(--radius-sm)', background: selectedIndex === index ? 'var(--accent-copper-dim)' : 'var(--bg-panel)' }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Box size={12} />
                    <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', fontSize: 10,
                      fontWeight: 600, color: 'var(--text-main)' }}>{componentName(component, index)}</span>
                    {componentIssues.length > 0 && <AlertTriangle size={11} color="var(--badge-amber)" />}
                  </div>
                  <div style={{ fontSize: 9, color: 'var(--text-sub)', marginTop: 3 }}>{component.type}</div>
                </button>
              );
            })}
          </div>
        </aside>

        <main style={{ minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column', background: 'var(--bg-base)' }}>
          <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', gap: 12,
            alignItems: 'center', flexWrap: 'wrap' }}>
            <label style={{ fontSize: 10, color: 'var(--text-sub)' }}>
              宽度
              <input data-testid="gui-width" type="number" min={0} max={512} value={guiWidth}
                onChange={(event) => setValue('/width', Number(event.target.value))} style={{ width: 72, marginLeft: 5 }} />
            </label>
            <label style={{ fontSize: 10, color: 'var(--text-sub)' }}>
              高度
              <input data-testid="gui-height" type="number" min={0} max={512} value={guiHeight}
                onChange={(event) => setValue('/height', Number(event.target.value))} style={{ width: 72, marginLeft: 5 }} />
            </label>
            <label style={{ fontSize: 10, color: 'var(--text-sub)', display: 'flex', alignItems: 'center', gap: 5 }}>
              <input type="checkbox" checked={Boolean(values['/renderBgLayer'])}
                onChange={(event) => setValue('/renderBgLayer', event.target.checked)} /> 渲染背景层
            </label>
            <label style={{ fontSize: 10, color: 'var(--text-sub)', display: 'flex', alignItems: 'center', gap: 5 }}>
              <input type="checkbox" checked={Boolean(values['/doesPauseGame'])}
                onChange={(event) => setValue('/doesPauseGame', event.target.checked)} /> 暂停游戏
            </label>
          </div>

          <div style={{ flex: 1, minHeight: 0, overflow: 'auto', display: 'grid', placeItems: 'center', padding: 20 }}>
            <div data-testid="gui-layout-preview" style={{ width: WYSIWYG_WIDTH, height: WYSIWYG_HEIGHT, position: 'relative',
              background: 'var(--bg-panel)', border: '1px solid var(--border-active)', boxShadow: 'var(--shadow-md)' }}>
              <div style={{ position: 'absolute', left: guiLeft, top: guiTop, width: guiWidth, height: guiHeight,
                border: '1px dashed var(--accent-copper)', background: Boolean(values['/renderBgLayer']) ? 'var(--bg-surface)' : 'transparent' }} />
              {components.map((component, index) => {
                const x = Number(component.data.x ?? 0);
                const y = Number(component.data.y ?? 0);
                const size = componentSize(component);
                return (
                  <button
                    type="button"
                    key={`preview-${component.type}-${index}`}
                    data-testid={`gui-preview-component-${index}`}
                    onClick={() => setSelectedIndex(index)}
                    title={`${component.type} @ ${x},${y}`}
                    style={{ position: 'absolute', left: x, top: y, width: size.width, height: size.height,
                      minWidth: 4, minHeight: 4, overflow: 'hidden', padding: 2, fontSize: 8,
                      border: `1px solid ${selectedIndex === index ? 'var(--accent-copper)' : 'var(--border-focus)'}`,
                      background: selectedIndex === index ? 'var(--accent-copper-dim)' : 'rgba(120,120,120,0.15)',
                      color: 'var(--text-main)' }}
                  >
                    {component.type === 'label' && typeof component.data.text === 'string'
                      ? component.data.text
                      : componentName(component, index)}
                  </button>
                );
              })}
            </div>
          </div>

          {issues.length > 0 && (
            <div data-testid="gui-layout-diagnostics" style={{ maxHeight: 92, overflowY: 'auto', padding: '8px 12px',
              borderTop: '1px solid var(--border-subtle)', background: 'var(--badge-amber-bg)', fontSize: 9 }}>
              {issues.map((issue, index) => (
                <div key={`${issue.index}-${index}`} style={{ display: 'flex', gap: 5, color: 'var(--badge-amber)', marginBottom: 3 }}>
                  <AlertTriangle size={10} /> {issue.index === null ? '' : `组件 ${issue.index + 1}：`}{issue.message}
                </div>
              ))}
            </div>
          )}
        </main>

        <aside style={{ borderLeft: '1px solid var(--border-subtle)', background: 'var(--bg-surface)', minHeight: 0,
          overflowY: 'auto', padding: 12 }}>
          <div style={{ fontWeight: 700, fontSize: 11, color: 'var(--text-main)', marginBottom: 8 }}>GUI 事件绑定</div>
          {(['/onOpen', '/onTick', '/onClosed'] as const).map((path) => (
            <label key={path} style={{ display: 'block', fontSize: 9, color: 'var(--text-sub)', marginBottom: 7 }}>
              {path.slice(1)}
              <input list="gui-procedure-options" value={String(values[path] ?? '')}
                onChange={(event) => setValue(path, event.target.value || null)} style={{ width: '100%', marginTop: 3 }} />
            </label>
          ))}
          <datalist id="gui-procedure-options">
            {procedures.map((procedure) => <option key={procedure.id} value={procedure.name}>{procedure.displayName}</option>)}
          </datalist>

          <div style={{ height: 1, background: 'var(--border-subtle)', margin: '12px 0' }} />
          {!selected || selectedIndex === null ? (
            <div style={{ fontSize: 10, color: 'var(--text-sub)' }}>选择一个组件后可编辑其上游原始字段。</div>
          ) : (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 700, fontSize: 11, color: 'var(--text-main)' }}>{componentName(selected, selectedIndex)}</div>
                  <div style={{ fontSize: 9, color: 'var(--text-sub)' }}>{selected.type}</div>
                </div>
                <button type="button" aria-label="上移组件" onClick={() => moveSelected(-1)} disabled={selectedIndex === 0} style={{ padding: 4 }}>
                  <ChevronUp size={12} />
                </button>
                <button type="button" aria-label="下移组件" onClick={() => moveSelected(1)} disabled={selectedIndex === components.length - 1} style={{ padding: 4 }}>
                  <ChevronDown size={12} />
                </button>
                <button type="button" aria-label="删除组件" onClick={deleteSelected} className="btn-danger" style={{ padding: 4 }}>
                  <Trash2 size={12} />
                </button>
              </div>

              {editablePrimitiveEntries(selected).map(([key, value]) => {
                const inputId = `gui-component-field-${key}`;
                if (key === 'anchorPoint') {
                  return (
                    <label key={key} htmlFor={inputId} style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                      anchorPoint
                      <select id={inputId} value={String(value ?? '')} onChange={(event) => updateComponentField(key, event.target.value || null)}
                        style={{ width: '100%', marginTop: 3 }}>
                        {ANCHORS.map((anchor) => <option key={anchor} value={anchor}>{anchor || '(none)'}</option>)}
                      </select>
                    </label>
                  );
                }
                if (typeof value === 'boolean') {
                  return (
                    <label key={key} htmlFor={inputId} style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8,
                      fontSize: 9, color: 'var(--text-sub)' }}>
                      <input id={inputId} type="checkbox" checked={value}
                        onChange={(event) => updateComponentField(key, event.target.checked)} /> {key}
                    </label>
                  );
                }
                if (typeof value === 'number') {
                  return (
                    <label key={key} htmlFor={inputId} style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                      {key}
                      <input id={inputId} data-testid={`gui-component-field-${key}`} type="number" value={value}
                        onChange={(event) => updateComponentField(key, Number(event.target.value))}
                        style={{ width: '100%', marginTop: 3 }} />
                    </label>
                  );
                }
                if (PROCEDURE_FIELDS.has(key)) {
                  return (
                    <label key={key} htmlFor={inputId} style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                      {key} · Procedure
                      <input id={inputId} list="gui-procedure-options" value={String(value ?? '')}
                        onChange={(event) => updateComponentField(key, event.target.value || null)} style={{ width: '100%', marginTop: 3 }} />
                    </label>
                  );
                }
                return (
                  <label key={key} htmlFor={inputId} style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                    {key}
                    <input id={inputId} list={isAssetField(key) ? 'gui-texture-assets' : undefined} value={String(value ?? '')}
                      onChange={(event) => updateComponentField(key, event.target.value)} style={{ width: '100%', marginTop: 3 }} />
                  </label>
                );
              })}
              <datalist id="gui-texture-assets">
                {textureAssets.map((asset) => <option key={asset.id} value={asset.relativePath}>{asset.relativePath}</option>)}
              </datalist>

              {Object.entries(selected.data).some(([, value]) => value !== null && typeof value === 'object') && (
                <details style={{ marginTop: 8 }}>
                  <summary style={{ fontSize: 9, color: 'var(--text-sub)', cursor: 'pointer' }}>复杂字段（只读保留）</summary>
                  <pre style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontSize: 8, color: 'var(--text-sub)',
                    background: 'var(--bg-panel)', padding: 7, borderRadius: 4 }}>
                    {JSON.stringify(Object.fromEntries(Object.entries(selected.data)
                      .filter(([, value]) => value !== null && typeof value === 'object')), null, 2)}
                  </pre>
                </details>
              )}
            </>
          )}
        </aside>
      </div>
    </div>
  );
};
