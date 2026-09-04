import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  Box,
  ChevronDown,
  ChevronUp,
  Grid3X3,
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

interface OverlayWorkbenchProps {
  element: ModElementSummary;
  onClose: () => void;
}

interface OverlayComponentWire {
  type: 'label' | 'image' | 'sprite' | 'entitymodel' | string;
  data: Record<string, unknown>;
}

interface OverlayIssue {
  index: number | null;
  message: string;
}

type OverlayComponentType = 'label' | 'image' | 'sprite' | 'entitymodel';

const CANVAS_WIDTH = 427;
const CANVAS_HEIGHT = 240;
const LEGAL_COMPONENTS = new Set(['label', 'image', 'sprite', 'entitymodel']);
const ANCHORS = [
  '', 'TOP_LEFT', 'TOP_CENTER', 'TOP_RIGHT', 'CENTER_LEFT', 'CENTER', 'CENTER_RIGHT',
  'BOTTOM_LEFT', 'BOTTOM_CENTER', 'BOTTOM_RIGHT'
];
const OVERLAY_TARGETS = [
  'Ingame', 'MainMenu', 'IngameMenu', 'Inventory', 'Creative', 'Chat', 'Advancements', 'Death',
  'Options', 'VideoSettings', 'Controls', 'Crafting', 'Chest', 'Furnace', 'Anvil', 'Merchant',
  'Multiplayer', 'WorldSelection'
];

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function editorValues(editor: ModElementEditorProjection): Record<string, unknown> {
  return Object.fromEntries(
    editor.sections.flatMap((section) => section.fields.map((field) => [field.path, clone(field.value)]))
  );
}

function componentsFrom(values: Record<string, unknown>): OverlayComponentWire[] {
  const raw = values['/components'];
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((entry) => {
    if (!entry || typeof entry !== 'object') return [];
    const root = entry as Record<string, unknown>;
    if (typeof root.type !== 'string' || !root.data || typeof root.data !== 'object' || Array.isArray(root.data)) return [];
    return [{ type: root.type.toLowerCase(), data: root.data as Record<string, unknown> }];
  });
}

function componentName(component: OverlayComponentWire, index: number): string {
  if (typeof component.data.name === 'string' && component.data.name.trim()) return component.data.name;
  const texture = component.data.image ?? component.data.sprite;
  if (typeof texture === 'string' && texture.trim()) return `${component.type}_${texture}`;
  if (component.type === 'entitymodel' && typeof component.data.entityModel === 'string' && component.data.entityModel) {
    return `entity_model_${component.data.entityModel}`;
  }
  return `${component.type}_${index + 1}`;
}

function labelText(component: OverlayComponentWire): string {
  const text = component.data.text;
  if (!text || typeof text !== 'object' || Array.isArray(text)) return '';
  const fixedValue = (text as Record<string, unknown>).fixedValue;
  return typeof fixedValue === 'string' ? fixedValue : '';
}

function argbToHex(value: unknown): string {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return '#ffffff';
  const raw = Number((value as Record<string, unknown>).value ?? -1);
  const rgb = Number.isFinite(raw) ? (raw >>> 0) & 0xffffff : 0xffffff;
  return `#${rgb.toString(16).padStart(6, '0')}`;
}

function hexToArgb(value: string): { value: number; falpha: number } {
  const rgb = Number.parseInt(value.replace('#', ''), 16) & 0xffffff;
  return { value: (0xff000000 | rgb) | 0, falpha: 0 };
}

function componentSize(component: OverlayComponentWire): { width: number; height: number } {
  if (component.type === 'label') return { width: Math.max(36, labelText(component).length * 6), height: 10 };
  if (component.type === 'entitymodel') return { width: 20, height: 20 };
  return { width: 32, height: 32 };
}

function changedFields(base: Record<string, unknown>, values: Record<string, unknown>): FieldChange[] {
  const paths = [
    '/priority', '/baseTexture', '/overlayTarget', '/displayCondition', '/components',
    '/gridSettings/sx', '/gridSettings/sy', '/gridSettings/ox', '/gridSettings/oy', '/gridSettings/snapOnGrid'
  ];
  return paths.flatMap((path) =>
    JSON.stringify(base[path]) === JSON.stringify(values[path]) ? [] : [{ path, value: clone(values[path]) }]
  );
}

function validateOverlay(values: Record<string, unknown>, components: OverlayComponentWire[]): OverlayIssue[] {
  const issues: OverlayIssue[] = [];
  for (const path of ['/gridSettings/sx', '/gridSettings/sy', '/gridSettings/ox', '/gridSettings/oy']) {
    const value = Number(values[path]);
    if (!Number.isFinite(value) || value < 1 || value > 100) {
      issues.push({ index: null, message: `${path.split('/').pop()} 必须在 1–100 之间。` });
    }
  }
  if (!String(values['/overlayTarget'] ?? '').trim()) issues.push({ index: null, message: 'Overlay Target 不能为空。' });

  components.forEach((component, index) => {
    if (!LEGAL_COMPONENTS.has(component.type)) {
      issues.push({ index, message: `Overlay 不支持 ${component.type}；上游仅允许 Label / Image / Sprite / EntityModel。` });
      return;
    }
    const x = Number(component.data.x ?? 0);
    const y = Number(component.data.y ?? 0);
    if (!Number.isFinite(x) || !Number.isFinite(y)) issues.push({ index, message: '组件坐标不是有效数字。' });
    if (component.type === 'image' && !String(component.data.image ?? '').trim()) issues.push({ index, message: 'Image 必须选择 SCREEN texture。' });
    if (component.type === 'sprite') {
      if (!String(component.data.sprite ?? '').trim()) issues.push({ index, message: 'Sprite 必须选择 SCREEN texture。' });
      if (Number(component.data.spritesCount ?? 0) < 1) issues.push({ index, message: 'Sprite spritesCount 必须至少为 1。' });
    }
    if (component.type === 'entitymodel' && !String(component.data.entityModel ?? '').trim()) {
      issues.push({ index, message: 'EntityModel 必须绑定返回 Entity 的 Procedure。' });
    }
  });
  return issues;
}

function createLabel(index: number): OverlayComponentWire {
  return {
    type: 'label',
    data: {
      anchorPoint: null, x: 120, y: 70, locked: false,
      name: `label_${index + 1}`,
      text: { name: null, fixedValue: 'Overlay Label' },
      color: { value: -1, falpha: 0 }, hasShadow: false, displayCondition: null
    }
  };
}

function createImage(texture: string): OverlayComponentWire {
  return {
    type: 'image',
    data: { anchorPoint: null, x: 120, y: 70, locked: false, image: texture, use1Xscale: false, displayCondition: null }
  };
}

function createSprite(texture: string): OverlayComponentWire {
  return {
    type: 'sprite',
    data: {
      anchorPoint: null, x: 120, y: 70, locked: false, sprite: texture, spritesCount: 1,
      displayCondition: null, spriteIndex: { name: null, fixedValue: 0 }
    }
  };
}

function createEntityModel(procedure: string): OverlayComponentWire {
  return {
    type: 'entitymodel',
    data: {
      anchorPoint: null, x: 120, y: 70, locked: false, entityModel: procedure, displayCondition: null,
      scale: 30, rotationX: 0, followMouseMovement: true
    }
  };
}

export const OverlayWorkbench: React.FC<OverlayWorkbenchProps> = ({ element, onClose }) => {
  const { getModElementEditor, previewModElementChange, updateModElement, listAssets, state } = useWorkbench();
  const [editor, setEditor] = useState<ModElementEditorProjection | null>(null);
  const [base, setBase] = useState<Record<string, unknown>>({});
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [assets, setAssets] = useState<AssetProjection | null>(null);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [newComponentType, setNewComponentType] = useState<OverlayComponentType>('label');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [impact, setImpact] = useState<string[]>([]);
  const previewToken = useRef(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all([getModElementEditor(element.id), listAssets().catch(() => null)])
      .then(([projection, assetProjection]) => {
        if (cancelled || !projection) return;
        const next = editorValues(projection);
        setEditor(projection);
        setBase(clone(next));
        setValues(clone(next));
        setAssets(assetProjection);
        setSelectedIndex(componentsFrom(next).length > 0 ? 0 : null);
      })
      .catch(() => {
        if (!cancelled) setMessage('无法加载 Overlay 编辑器投影。');
      });
    return () => { cancelled = true; };
  }, [element.id, getModElementEditor, listAssets]);

  const components = useMemo(() => componentsFrom(values), [values]);
  const changes = useMemo(() => changedFields(base, values), [base, values]);
  const issues = useMemo(() => validateOverlay(values, components), [values, components]);
  const selected = selectedIndex === null ? null : components[selectedIndex] ?? null;
  const procedures = useMemo(() => state.elements.filter((candidate) => candidate.type === 'procedure')
    .sort((left, right) => left.displayName.localeCompare(right.displayName)), [state.elements]);
  const textures = useMemo(() => assets?.assets.filter((asset) => asset.category === 'TEXTURE') ?? [], [assets]);

  useEffect(() => {
    if (changes.length === 0) {
      setImpact([]);
      return;
    }
    const token = ++previewToken.current;
    const timer = window.setTimeout(() => {
      previewModElementChange(element.id, changes)
        .then((preview) => {
          if (previewToken.current === token) setImpact(preview?.generationImpact?.affectedDomains ?? []);
        })
        .catch(() => {
          if (previewToken.current === token) setImpact([]);
        });
    }, 160);
    return () => window.clearTimeout(timer);
  }, [changes, element.id, previewModElementChange]);

  const setValue = (path: string, value: unknown) => {
    setValues((current) => ({ ...current, [path]: value }));
    setMessage(null);
  };

  const setComponents = (next: OverlayComponentWire[]) => setValue('/components', next);

  const updateComponentField = (key: string, value: unknown) => {
    if (selectedIndex === null) return;
    const next = clone(components);
    next[selectedIndex] = { ...next[selectedIndex], data: { ...next[selectedIndex].data, [key]: value } };
    setComponents(next);
  };

  const updateObjectField = (parent: string, key: string, value: unknown) => {
    if (selectedIndex === null) return;
    const next = clone(components);
    const raw = next[selectedIndex].data[parent];
    const object = raw && typeof raw === 'object' && !Array.isArray(raw) ? raw as Record<string, unknown> : {};
    next[selectedIndex] = { ...next[selectedIndex], data: { ...next[selectedIndex].data, [parent]: { ...object, [key]: value } } };
    setComponents(next);
  };

  const addComponent = () => {
    const texture = textures[0]?.relativePath ?? '';
    const procedure = procedures[0]?.name ?? '';
    if ((newComponentType === 'image' || newComponentType === 'sprite') && !texture) {
      setMessage('添加 Image / Sprite 前需要先导入 SCREEN texture。');
      return;
    }
    if (newComponentType === 'entitymodel' && !procedure) {
      setMessage('添加 EntityModel 前需要至少一个返回 Entity 的 Procedure。');
      return;
    }
    const component = newComponentType === 'label' ? createLabel(components.length)
      : newComponentType === 'image' ? createImage(texture)
        : newComponentType === 'sprite' ? createSprite(texture)
          : createEntityModel(procedure);
    const next = [...clone(components), component];
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
    if (saving || changes.length === 0 || issues.length > 0) return;
    setSaving(true);
    setMessage(null);
    try {
      const result = await updateModElement(element.id, changes);
      if (result.status === 'committed') {
        setBase(clone(values));
        setMessage('Overlay 更改已保存。');
      } else {
        setMessage(result.diagnostics.map((diagnostic) => t(diagnostic.message)).join('；') || '保存被 Core 拒绝。');
      }
    } catch {
      setMessage('保存失败，工作区未发生更改。');
    } finally {
      setSaving(false);
    }
  };

  if (!editor) {
    return <div data-testid="overlay-workbench-loading" style={{ padding: 24, color: 'var(--text-sub)' }}>正在加载 Overlay 深度编辑器…</div>;
  }

  return (
    <div data-testid="overlay-workbench" style={{ flex: 1, minWidth: 0, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <header style={{ height: 52, padding: '0 14px', display: 'flex', alignItems: 'center', gap: 10,
        background: 'var(--bg-surface)', borderBottom: '1px solid var(--border-subtle)' }}>
        <button type="button" onClick={onClose} aria-label="返回元素列表" className="btn-secondary" style={{ padding: 6 }}><ArrowLeft size={14} /></button>
        <Layers3 size={17} color="var(--accent-copper)" />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--text-main)' }}>{element.displayName}</div>
          <div style={{ fontSize: 10, color: 'var(--text-sub)' }}>Overlay 深度编辑 · 上游 WYSIWYG Overlay 组件子集</div>
        </div>
        {impact.length > 0 && <span className="badge badge-blue" data-testid="overlay-generation-impact">生成影响：{impact.join(', ')}</span>}
        {message && <span style={{ maxWidth: 280, fontSize: 10, color: 'var(--text-sub)' }}>{message}</span>}
        <button type="button" className="btn-primary" data-testid="overlay-save-btn"
          disabled={saving || changes.length === 0 || issues.length > 0} onClick={save}>
          <Save size={13} /> {saving ? '保存中…' : '保存 Overlay'}
        </button>
      </header>

      <div style={{ flex: 1, minHeight: 0, display: 'grid', gridTemplateColumns: '230px minmax(420px, 1fr) 300px' }}>
        <aside style={{ minHeight: 0, display: 'flex', flexDirection: 'column', background: 'var(--bg-surface)', borderRight: '1px solid var(--border-subtle)' }}>
          <div style={{ padding: 12, borderBottom: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 11, fontWeight: 700 }}>组件树</div>
              <div style={{ fontSize: 9, color: 'var(--text-sub)' }}>{components.length} 个 Overlay 组件</div>
            </div>
            <select data-testid="overlay-add-component-type" value={newComponentType}
              onChange={(event) => setNewComponentType(event.target.value as OverlayComponentType)} style={{ fontSize: 9, maxWidth: 105 }}>
              <option value="label">Label</option>
              <option value="image">Image</option>
              <option value="sprite">Sprite</option>
              <option value="entitymodel">EntityModel</option>
            </select>
            <button type="button" className="btn-secondary" data-testid="overlay-add-component-btn" onClick={addComponent} aria-label="添加 Overlay 组件"><Plus size={12} /></button>
          </div>
          <div data-testid="overlay-component-tree" style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
            {components.length === 0 ? <div style={{ padding: 18, fontSize: 10, color: 'var(--text-sub)', textAlign: 'center' }}>
              Overlay 可添加 Label、Image、Sprite、EntityModel。
            </div> : components.map((component, index) => {
              const invalid = issues.some((issue) => issue.index === index);
              return <button type="button" key={`${component.type}-${index}`} data-testid={`overlay-component-${index}`}
                onClick={() => setSelectedIndex(index)} aria-pressed={selectedIndex === index}
                style={{ width: '100%', textAlign: 'left', padding: '8px 9px', marginBottom: 5,
                  border: `1px solid ${selectedIndex === index ? 'var(--accent-copper)' : 'var(--border-subtle)'}`,
                  borderRadius: 'var(--radius-sm)', background: selectedIndex === index ? 'var(--accent-copper-dim)' : 'var(--bg-panel)' }}>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}><Box size={12} />
                  <span style={{ flex: 1, fontSize: 10, overflow: 'hidden', textOverflow: 'ellipsis' }}>{componentName(component, index)}</span>
                  {invalid && <AlertTriangle size={11} color="var(--badge-amber)" />}
                </div>
                <div style={{ marginTop: 2, fontSize: 9, color: 'var(--text-sub)' }}>{component.type}</div>
              </button>;
            })}
          </div>
        </aside>

        <main style={{ minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column', background: 'var(--bg-base)' }}>
          <div style={{ padding: '9px 12px', display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center', borderBottom: '1px solid var(--border-subtle)' }}>
            <label style={{ fontSize: 9, color: 'var(--text-sub)' }}>Target
              <input data-testid="overlay-target" list="overlay-targets" value={String(values['/overlayTarget'] ?? 'Ingame')}
                onChange={(event) => setValue('/overlayTarget', event.target.value)} style={{ marginLeft: 4, width: 120 }} />
            </label>
            <datalist id="overlay-targets">{OVERLAY_TARGETS.map((target) => <option key={target} value={target} />)}</datalist>
            <label style={{ fontSize: 9, color: 'var(--text-sub)' }}>Priority
              <select data-testid="overlay-priority" value={String(values['/priority'] ?? 'NORMAL')}
                onChange={(event) => setValue('/priority', event.target.value)} style={{ marginLeft: 4 }}>
                {['LOWEST', 'LOW', 'NORMAL', 'HIGH', 'HIGHEST'].map((priority) => <option key={priority} value={priority}>{priority}</option>)}
              </select>
            </label>
            <label style={{ fontSize: 9, color: 'var(--text-sub)' }}>Base texture
              <input data-testid="overlay-base-texture" list="overlay-textures" value={String(values['/baseTexture'] ?? '')}
                onChange={(event) => setValue('/baseTexture', event.target.value)} style={{ marginLeft: 4, width: 150 }} />
            </label>
            <label style={{ fontSize: 9, color: 'var(--text-sub)' }}>Display condition
              <input data-testid="overlay-display-condition" list="overlay-procedures" value={String(values['/displayCondition'] ?? '')}
                onChange={(event) => setValue('/displayCondition', event.target.value || null)} style={{ marginLeft: 4, width: 130 }} />
            </label>
          </div>

          <div style={{ padding: '7px 12px', display: 'flex', gap: 9, alignItems: 'center', borderBottom: '1px solid var(--border-subtle)' }}>
            <Grid3X3 size={13} />
            {(['sx', 'sy', 'ox', 'oy'] as const).map((key) => <label key={key} style={{ fontSize: 9, color: 'var(--text-sub)' }}>{key}
              <input data-testid={`overlay-grid-${key}`} type="number" min={1} max={100}
                value={Number(values[`/gridSettings/${key}`] ?? (key === 'sx' || key === 'sy' ? 18 : key === 'ox' ? 11 : 15))}
                onChange={(event) => setValue(`/gridSettings/${key}`, Number(event.target.value))}
                style={{ width: 52, marginLeft: 3 }} />
            </label>)}
            <label style={{ display: 'flex', gap: 4, alignItems: 'center', fontSize: 9, color: 'var(--text-sub)' }}>
              <input data-testid="overlay-grid-snap" type="checkbox" checked={Boolean(values['/gridSettings/snapOnGrid'])}
                onChange={(event) => setValue('/gridSettings/snapOnGrid', event.target.checked)} /> snap on grid
            </label>
          </div>

          <div style={{ flex: 1, minHeight: 0, overflow: 'auto', display: 'grid', placeItems: 'center', padding: 20 }}>
            <div data-testid="overlay-layout-preview" style={{ width: CANVAS_WIDTH, height: CANVAS_HEIGHT, position: 'relative',
              background: 'var(--bg-panel)', border: '1px solid var(--border-active)', boxShadow: 'var(--shadow-md)', overflow: 'hidden' }}>
              {Boolean(values['/gridSettings/snapOnGrid']) && <div style={{ position: 'absolute', inset: 0, opacity: 0.18,
                backgroundImage: `linear-gradient(to right, var(--text-sub) 1px, transparent 1px), linear-gradient(to bottom, var(--text-sub) 1px, transparent 1px)`,
                backgroundSize: `${Number(values['/gridSettings/sx'] ?? 18)}px ${Number(values['/gridSettings/sy'] ?? 18)}px`,
                backgroundPosition: `${Number(values['/gridSettings/ox'] ?? 11)}px ${Number(values['/gridSettings/oy'] ?? 15)}px` }} />}
              {components.map((component, index) => {
                const x = Number(component.data.x ?? 0);
                const y = Number(component.data.y ?? 0);
                const size = componentSize(component);
                return <button type="button" key={`preview-${component.type}-${index}`} data-testid={`overlay-preview-component-${index}`}
                  onClick={() => setSelectedIndex(index)} title={`${component.type} @ ${x},${y}`}
                  style={{ position: 'absolute', left: x, top: y, width: size.width, height: size.height,
                    minWidth: 4, minHeight: 4, overflow: 'hidden', padding: 2, fontSize: 8,
                    border: `1px solid ${selectedIndex === index ? 'var(--accent-copper)' : 'var(--border-focus)'}`,
                    background: selectedIndex === index ? 'var(--accent-copper-dim)' : 'rgba(120,120,120,0.15)', color: 'var(--text-main)' }}>
                  {component.type === 'label' ? labelText(component) : componentName(component, index)}
                </button>;
              })}
            </div>
          </div>
          {issues.length > 0 && <div data-testid="overlay-layout-diagnostics" style={{ maxHeight: 90, overflowY: 'auto', padding: '8px 12px',
            borderTop: '1px solid var(--border-subtle)', background: 'var(--badge-amber-bg)', fontSize: 9 }}>
            {issues.map((issue, index) => <div key={`${issue.index}-${index}`} style={{ display: 'flex', gap: 5, color: 'var(--badge-amber)', marginBottom: 3 }}>
              <AlertTriangle size={10} /> {issue.index === null ? '' : `组件 ${issue.index + 1}：`}{issue.message}
            </div>)}
          </div>}
        </main>

        <aside style={{ minHeight: 0, overflowY: 'auto', padding: 12, background: 'var(--bg-surface)', borderLeft: '1px solid var(--border-subtle)' }}>
          <datalist id="overlay-textures">{textures.map((asset) => <option key={asset.id} value={asset.relativePath}>{asset.relativePath}</option>)}</datalist>
          <datalist id="overlay-procedures">{procedures.map((procedure) => <option key={procedure.id} value={procedure.name}>{procedure.displayName}</option>)}</datalist>
          {!selected || selectedIndex === null ? <div style={{ fontSize: 10, color: 'var(--text-sub)' }}>选择 Overlay 组件以编辑原生字段。</div> : <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 10 }}>
              <div style={{ flex: 1 }}><div style={{ fontWeight: 700, fontSize: 11 }}>{componentName(selected, selectedIndex)}</div>
                <div style={{ fontSize: 9, color: 'var(--text-sub)' }}>{selected.type}</div></div>
              <button type="button" aria-label="上移 Overlay 组件" onClick={() => moveSelected(-1)} disabled={selectedIndex === 0} style={{ padding: 4 }}><ChevronUp size={12} /></button>
              <button type="button" aria-label="下移 Overlay 组件" onClick={() => moveSelected(1)} disabled={selectedIndex === components.length - 1} style={{ padding: 4 }}><ChevronDown size={12} /></button>
              <button type="button" aria-label="删除 Overlay 组件" onClick={deleteSelected} className="btn-danger" style={{ padding: 4 }}><Trash2 size={12} /></button>
            </div>

            {(['x', 'y'] as const).map((key) => <label key={key} style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{key}
              <input data-testid={`overlay-component-field-${key}`} type="number" value={Number(selected.data[key] ?? 0)}
                onChange={(event) => updateComponentField(key, Number(event.target.value))} style={{ width: '100%', marginTop: 3 }} />
            </label>)}
            <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>anchorPoint
              <select value={String(selected.data.anchorPoint ?? '')} onChange={(event) => updateComponentField('anchorPoint', event.target.value || null)}
                style={{ width: '100%', marginTop: 3 }}>{ANCHORS.map((anchor) => <option key={anchor} value={anchor}>{anchor || '(none)'}</option>)}</select>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
              <input type="checkbox" checked={Boolean(selected.data.locked)} onChange={(event) => updateComponentField('locked', event.target.checked)} /> locked
            </label>

            {selected.type === 'label' && <>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>name
                <input value={String(selected.data.name ?? '')} onChange={(event) => updateComponentField('name', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>text · fixedValue
                <input data-testid="overlay-component-field-label-text" value={labelText(selected)}
                  onChange={(event) => updateObjectField('text', 'fixedValue', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>color
                <input type="color" value={argbToHex(selected.data.color)} onChange={(event) => updateComponentField('color', hexToArgb(event.target.value))}
                  style={{ width: '100%', marginTop: 3, minHeight: 28 }} />
              </label>
              <label style={{ display: 'flex', gap: 5, marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>
                <input type="checkbox" checked={Boolean(selected.data.hasShadow)} onChange={(event) => updateComponentField('hasShadow', event.target.checked)} /> hasShadow
              </label>
            </>}

            {selected.type === 'image' && <>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>image
                <input list="overlay-textures" value={String(selected.data.image ?? '')} onChange={(event) => updateComponentField('image', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'flex', gap: 5, marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>
                <input type="checkbox" checked={Boolean(selected.data.use1Xscale)} onChange={(event) => updateComponentField('use1Xscale', event.target.checked)} /> use1Xscale
              </label>
            </>}

            {selected.type === 'sprite' && <>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>sprite
                <input list="overlay-textures" value={String(selected.data.sprite ?? '')} onChange={(event) => updateComponentField('sprite', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>spritesCount
                <input data-testid="overlay-component-field-spritesCount" type="number" min={1} value={Number(selected.data.spritesCount ?? 1)}
                  onChange={(event) => updateComponentField('spritesCount', Number(event.target.value))} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>spriteIndex · fixedValue
                <input type="number" value={Number((selected.data.spriteIndex as Record<string, unknown> | undefined)?.fixedValue ?? 0)}
                  onChange={(event) => updateObjectField('spriteIndex', 'fixedValue', Number(event.target.value))} style={{ width: '100%', marginTop: 3 }} />
              </label>
            </>}

            {selected.type === 'entitymodel' && <>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>entityModel · Procedure
                <input data-testid="overlay-component-field-entityModel" list="overlay-procedures" value={String(selected.data.entityModel ?? '')}
                  onChange={(event) => updateComponentField('entityModel', event.target.value || null)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              {(['scale', 'rotationX'] as const).map((key) => <label key={key} style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{key}
                <input type="number" value={Number(selected.data[key] ?? 0)} onChange={(event) => updateComponentField(key, Number(event.target.value))}
                  style={{ width: '100%', marginTop: 3 }} />
              </label>)}
              <label style={{ display: 'flex', gap: 5, marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>
                <input type="checkbox" checked={Boolean(selected.data.followMouseMovement)}
                  onChange={(event) => updateComponentField('followMouseMovement', event.target.checked)} /> followMouseMovement
              </label>
            </>}

            <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>displayCondition · Procedure
              <input list="overlay-procedures" value={String(selected.data.displayCondition ?? '')}
                onChange={(event) => updateComponentField('displayCondition', event.target.value || null)} style={{ width: '100%', marginTop: 3 }} />
            </label>
          </>}
        </aside>
      </div>
    </div>
  );
};
