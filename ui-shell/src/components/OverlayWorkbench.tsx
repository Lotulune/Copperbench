import { tr } from '../i18n/locale';
import { fieldLabel, valueLabel } from '../i18n/labels';
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
  ModElementEditorProjection,
  ModElementSummary
} from '../types/contract';
import { uiText, uiMessage, diagnosticMessages, renderUiMessage, useUiLocale, type UiMessage } from '../i18n';
import { collectEditorFieldChanges, numericDraftValue } from '../hooks/editorFieldChanges';
import { layoutNumericDrafts } from '../hooks/layoutNumericDrafts';
import { commitSpecializedSession, getSpecializedSession, nextSpecializedReadToken, rebaseEditor,
  specializedEditorKey, updateSpecializedSession, useSpecializedEditorSession } from '../hooks/specializedEditorSessions';
import { EditorRecoveryNotice } from './EditorRecoveryNotice';
import { confirmEditorRead } from '../bridge/editorRecovery';

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
    return [{ ...root, type: root.type.toLowerCase(), data: root.data as Record<string, unknown> }];
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

function validateOverlay(values: Record<string, unknown>, components: OverlayComponentWire[]): OverlayIssue[] {
  const issues: OverlayIssue[] = [];
  for (const path of ['/gridSettings/sx', '/gridSettings/sy', '/gridSettings/ox', '/gridSettings/oy']) {
    const value = Number(values[path]);
    if (!Number.isFinite(value) || value < 1 || value > 100) {
      issues.push({ index: null, message: tr("{0} 必须在 1–100 之间。", [path.split('/').pop()]) });
    }
  }
  if (!String(values['/overlayTarget'] ?? '').trim()) issues.push({ index: null, message: tr("Overlay Target 不能为空。") });

  components.forEach((component, index) => {
    if (!LEGAL_COMPONENTS.has(component.type)) {
      issues.push({ index, message: tr("Overlay 不支持 {0}；上游仅允许 Label / Image / Sprite / EntityModel。", [component.type]) });
      return;
    }
    const x = Number(component.data.x ?? 0);
    const y = Number(component.data.y ?? 0);
    if (!Number.isFinite(x) || !Number.isFinite(y)) issues.push({ index, message: tr("组件坐标不是有效数字。") });
    if (component.type === 'image' && !String(component.data.image ?? '').trim()) issues.push({ index, message: tr("Image 必须选择 SCREEN texture。") });
    if (component.type === 'sprite') {
      if (!String(component.data.sprite ?? '').trim()) issues.push({ index, message: tr("Sprite 必须选择 SCREEN texture。") });
      if (Number(component.data.spritesCount ?? 0) < 1) issues.push({ index, message: tr("Sprite spritesCount 必须至少为 1。") });
    }
    if (component.type === 'entitymodel' && !String(component.data.entityModel ?? '').trim()) {
      issues.push({ index, message: tr("EntityModel 必须绑定返回 Entity 的 Procedure。") });
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

export const OverlayWorkbench: React.FC<OverlayWorkbenchProps> = props => {
  const { state } = useWorkbench();
  const key = specializedEditorKey(state.workbench?.workspace.id ?? '', props.element.id, 'overlay');
  return <OverlayEditor key={key} sessionKey={key} {...props} />;
};
const initialOverlayValues = () => ({} as Record<string, unknown>);
const OverlayEditor: React.FC<OverlayWorkbenchProps & { sessionKey: string }> = ({ element, onClose, sessionKey }) => {
  const { getModElementEditor, previewModElementChange, updateModElement, listAssets, refreshWorkspace, state } = useWorkbench();
  const current = useSpecializedEditorSession(sessionKey, initialOverlayValues);
  const { editor, data: values, saving, error: message } = current;
  const base = useMemo(() => editor ? editorValues(editor) : {}, [editor]);
  const changeSession = (patch: Partial<typeof current>) => updateSpecializedSession(sessionKey, initialOverlayValues, old => ({ ...old, ...patch }));
  const setValues = (change: React.SetStateAction<Record<string, unknown>>) => updateSpecializedSession(sessionKey, initialOverlayValues, old => {
    const data = typeof change === 'function' ? change(old.data) : change;
    return { ...old, data, dirty: JSON.stringify(data) !== JSON.stringify(old.editor ? editorValues(old.editor) : {}), error: null };
  });
  const setSaving = (saving: boolean) => changeSession({ saving });
  const setMessage = (error: UiMessage | null) => changeSession({ error });
  const [assets, setAssets] = useState<AssetProjection | null>(null);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [newComponentType, setNewComponentType] = useState<OverlayComponentType>('label');
  const [impact, setImpact] = useState<string[]>([]);
  const previewToken = useRef(0);
  const actions = useRef({ getModElementEditor, listAssets, previewModElementChange, refreshWorkspace });
  actions.current = { getModElementEditor, listAssets, previewModElementChange, refreshWorkspace };
  const locale = useUiLocale();

  useEffect(() => {
    let cancelled = false;
    const existing = getSpecializedSession(sessionKey, initialOverlayValues);
    if (existing.dirty || existing.saving || existing.loading || existing.saveUnknown) return;
    const readToken = nextSpecializedReadToken();
    const revision = state.workbench?.workspace.revision ?? 0;
    changeSession({ loading: true, readToken });
    actions.current.getModElementEditor(element.id)
      .then(projection => {
        if (!projection || projection.element.id !== element.id) throw new Error('Overlay editor projection unavailable');
        const next = editorValues(projection);
        updateSpecializedSession(sessionKey, initialOverlayValues, old => old.readToken !== readToken ? old
          : { ...old, editor: projection, data: old.dirty ? old.data : clone(next), revision, loading: false, error: null });
        if (cancelled) return;
        setSelectedIndex(componentsFrom(next).length > 0 ? 0 : null);
      })
      .catch(() => {
        updateSpecializedSession(sessionKey, initialOverlayValues, old => old.readToken !== readToken ? old
          : { ...old, loading: false, error: uiMessage('无法加载 Overlay 编辑器。请返回后重试，原草稿已保留。', 'Could not load the Overlay editor. Return and try again; your draft is preserved.') });
      });
    return () => { cancelled = true; };
  }, [sessionKey, element.id]);
  useEffect(() => {
    if (!editor) return;
    let cancelled = false;
    void actions.current.listAssets().then(projection => { if (!cancelled) setAssets(projection); }).catch(() => {});
    return () => { cancelled = true; };
  }, [sessionKey, editor?.element.id]);

  const components = useMemo(() => componentsFrom(values), [values]);
  const numericDraft = useMemo(() => layoutNumericDrafts(values, base), [values, base]);
  const pending = useMemo(() => collectEditorFieldChanges(editor, numericDraft.values), [editor, numericDraft.values]);
  const changes = useMemo(() => pending.invalid.length || numericDraft.invalid.length ? [] : pending.changes, [pending, numericDraft]);
  const issues = useMemo(() => [...validateOverlay(numericDraft.values, components),
    ...numericDraft.invalid.map(path => ({ index: null, message: uiText(`请输入有效数字：${path}`, `Enter a valid number: ${path}`) }))], [numericDraft, components, locale]);
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
      actions.current.previewModElementChange(element.id, changes)
        .then((preview) => {
          if (previewToken.current === token) setImpact(preview?.generationImpact?.affectedDomains ?? []);
        })
        .catch(() => {
          if (previewToken.current === token) setImpact([]);
        });
    }, 160);
    return () => { window.clearTimeout(timer); previewToken.current++; };
  }, [changes, element.id]);

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
      setMessage(uiMessage('添加 Image / Sprite 前需要先导入 SCREEN texture。', 'Import a SCREEN texture before adding an Image or Sprite.'));
      return;
    }
    if (newComponentType === 'entitymodel' && !procedure) {
      setMessage(uiMessage('添加 EntityModel 前需要至少一个返回 Entity 的 Procedure。', 'A Procedure that returns Entity is required before adding an EntityModel.'));
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
    const submitted = getSpecializedSession(sessionKey, initialOverlayValues);
    if (submitted.saving || submitted.saveUnknown || issues.length > 0 || pending.invalid.length > 0 || !editor) return;
    if (changes.length === 0) { changeSession({ data: numericDraft.values, dirty: false }); return; }
    setSaving(true);
    setMessage(null);
    try {
      const result = await updateModElement(element.id, changes, submitted.revision);
      if (result.status === 'committed') {
        commitSpecializedSession(sessionKey, initialOverlayValues, submitted.data, rebaseEditor(editor, changes, result.data?.element ?? editor.element), result.newRevision, result.diagnostics, numericDraft.values);
        setMessage(uiMessage('Overlay 更改已保存。', 'Overlay changes saved.'));
      } else {
        changeSession({ diagnostics: result.diagnostics });
        setMessage(diagnosticMessages(result.diagnostics, uiMessage('保存被 Core 拒绝。草稿未保存。', 'Core rejected the save. Your draft is unsaved.')));
      }
    } catch {
      changeSession({ saveUnknown: true });
      setMessage(uiMessage('未收到保存回执。', 'No save receipt was received.'));
    } finally {
      setSaving(false);
    }
  };
  const readLatest = async () => {
    if (getSpecializedSession(sessionKey, initialOverlayValues).saving) return;
    setSaving(true);
    try {
      const { editor: latest, revision: confirmedRevision } = await confirmEditorRead(state.workbench!.workspace.id, actions.current.refreshWorkspace, () => actions.current.getModElementEditor(element.id));
      if (!latest || latest.element.id !== element.id) throw new Error('Overlay editor projection unavailable');
      updateSpecializedSession(sessionKey, initialOverlayValues, old => ({ ...old, editor: latest, revision: confirmedRevision,
        dirty: JSON.stringify(old.data) !== JSON.stringify(editorValues(latest)), saveUnknown: false,
        error: uiMessage('已读取最新版本。请核对保留的草稿后再保存。', 'The latest version is loaded. Review your preserved draft before saving again.') }));
    } catch { setMessage(uiMessage('无法读取最新版本，草稿和原诊断已保留。', 'Could not read the latest version. Your draft and original diagnostics are preserved.')); }
    finally { setSaving(false); }
  };

  if (!editor) {
    return <div data-testid="overlay-workbench-loading" style={{ padding: 24, color: 'var(--text-sub)' }}>{message ? renderUiMessage(message) : uiText('正在加载叠加层编辑器…', 'Loading the Overlay editor…')}</div>;
  }

  return (
    <div data-testid="overlay-workbench" style={{ flex: 1, minWidth: 0, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <header style={{ height: 52, padding: '0 14px', display: 'flex', alignItems: 'center', gap: 10,
        background: 'var(--bg-surface)', borderBottom: '1px solid var(--border-subtle)' }}>
        <button type="button" onClick={onClose} aria-label={tr("返回元素列表")} className="btn-secondary" style={{ padding: 6 }}><ArrowLeft size={14} /></button>
        <Layers3 size={17} color="var(--accent-copper)" />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--text-main)' }}>{element.displayName}</div>
          <div style={{ fontSize: 10, color: 'var(--text-sub)' }}>{tr("叠加层布局")}</div>
        </div>
        {impact.length > 0 && <span className="badge badge-blue" data-testid="overlay-generation-impact">{tr("生成影响：")}{impact.join(', ')}</span>}
        {message && <span role="status" style={{ maxWidth: 280, fontSize: 12, color: 'var(--text-sub)' }}>{renderUiMessage(message)}</span>}
        {current.dirty && <span role="status">{uiText('未保存', 'Unsaved')}</span>}
        <button type="button" className="btn-primary" data-testid="overlay-save-btn"
          disabled={saving || current.saveUnknown || !current.dirty || issues.length > 0 || pending.invalid.length > 0} onClick={save}>
          <Save size={13} /> {saving ? tr("保存中…") : tr("保存 Overlay")}
        </button>
      </header>
      <EditorRecoveryNotice editor="overlay" saveUnknown={current.saveUnknown} diagnostics={current.diagnostics}
        busy={saving || current.loading} onRead={() => void readLatest()} />

      <div style={{ flex: 1, minHeight: 0, display: 'grid', gridTemplateColumns: '230px minmax(420px, 1fr) 300px' }}>
        <aside style={{ minHeight: 0, display: 'flex', flexDirection: 'column', background: 'var(--bg-surface)', borderRight: '1px solid var(--border-subtle)' }}>
          <div style={{ padding: 12, borderBottom: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 11, fontWeight: 700 }}>{tr("组件树")}</div>
              <div style={{ fontSize: 9, color: 'var(--text-sub)' }}>{components.length} {tr(" 个 Overlay 组件")}</div>
            </div>
            <select data-testid="overlay-add-component-type" value={newComponentType}
              onChange={(event) => setNewComponentType(event.target.value as OverlayComponentType)} style={{ fontSize: 9, maxWidth: 105 }}>
              <option value="label">{tr("文本标签（Label）")}</option>
              <option value="image">{tr("图片（Image）")}</option>
              <option value="sprite">{tr("精灵图（Sprite）")}</option>
              <option value="entitymodel">{tr("实体模型（EntityModel）")}</option>
            </select>
            <button type="button" className="btn-secondary" data-testid="overlay-add-component-btn" onClick={addComponent} aria-label={tr("添加 Overlay 组件")}><Plus size={12} /></button>
          </div>
          <div data-testid="overlay-component-tree" style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
            {components.length === 0 ? <div style={{ padding: 18, fontSize: 10, color: 'var(--text-sub)', textAlign: 'center' }}>
              {tr("Overlay 可添加 Label、Image、Sprite、EntityModel。")}</div> : components.map((component, index) => {
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
                <div style={{ marginTop: 2, fontSize: 9, color: 'var(--text-sub)' }}>{valueLabel(component.type)}</div>
              </button>;
            })}
          </div>
        </aside>

        <main style={{ minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column', background: 'var(--bg-base)' }}>
          <div style={{ padding: '9px 12px', display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center', borderBottom: '1px solid var(--border-subtle)' }}>
            <label style={{ fontSize: 9, color: 'var(--text-sub)' }}>{tr("显示目标（Target）")}<input data-testid="overlay-target" list="overlay-targets" value={String(values['/overlayTarget'] ?? 'Ingame')}
                onChange={(event) => setValue('/overlayTarget', event.target.value)} style={{ marginLeft: 4, width: 120 }} />
            </label>
            <datalist id="overlay-targets">{OVERLAY_TARGETS.map((target) => <option key={target} value={target} />)}</datalist>
            <label style={{ fontSize: 9, color: 'var(--text-sub)' }}>{tr("优先级（Priority）")}<select data-testid="overlay-priority" value={String(values['/priority'] ?? 'NORMAL')}
                onChange={(event) => setValue('/priority', event.target.value)} style={{ marginLeft: 4 }}>
                {['LOWEST', 'LOW', 'NORMAL', 'HIGH', 'HIGHEST'].map((priority) => <option key={priority} value={priority}>{priority}</option>)}
              </select>
            </label>
            <label style={{ fontSize: 9, color: 'var(--text-sub)' }}>{tr("基础纹理（Base Texture）")}<input data-testid="overlay-base-texture" list="overlay-textures" value={String(values['/baseTexture'] ?? '')}
                onChange={(event) => setValue('/baseTexture', event.target.value)} style={{ marginLeft: 4, width: 150 }} />
            </label>
            <label style={{ fontSize: 9, color: 'var(--text-sub)' }}>{tr("显示条件（Display Condition）")}<input data-testid="overlay-display-condition" list="overlay-procedures" value={String(values['/displayCondition'] ?? '')}
                onChange={(event) => setValue('/displayCondition', event.target.value || null)} style={{ marginLeft: 4, width: 130 }} />
            </label>
          </div>

          <div style={{ padding: '7px 12px', display: 'flex', gap: 9, alignItems: 'center', borderBottom: '1px solid var(--border-subtle)' }}>
            <Grid3X3 size={13} />
            {(['sx', 'sy', 'ox', 'oy'] as const).map((key) => <label key={key} style={{ fontSize: 9, color: 'var(--text-sub)' }}>{fieldLabel(key)}
              <input data-testid={`overlay-grid-${key}`} type="text" inputMode="decimal"
                value={String(values[`/gridSettings/${key}`] ?? '')}
                onChange={(event) => setValue(`/gridSettings/${key}`, event.target.value)}
                style={{ width: 52, marginLeft: 3 }} />
            </label>)}
            <label style={{ display: 'flex', gap: 4, alignItems: 'center', fontSize: 9, color: 'var(--text-sub)' }}>
              <input data-testid="overlay-grid-snap" type="checkbox" checked={Boolean(values['/gridSettings/snapOnGrid'])}
                onChange={(event) => setValue('/gridSettings/snapOnGrid', event.target.checked)} /> {tr(" 吸附网格")}</label>
          </div>

          <div style={{ flex: 1, minHeight: 0, overflow: 'auto', display: 'grid', placeItems: 'center', padding: 20 }}>
            <div data-testid="overlay-layout-preview" style={{ width: CANVAS_WIDTH, height: CANVAS_HEIGHT, position: 'relative',
              background: 'var(--bg-panel)', border: '1px solid var(--border-active)', boxShadow: 'var(--shadow-md)', overflow: 'hidden' }}>
              {Boolean(values['/gridSettings/snapOnGrid']) && <div style={{ position: 'absolute', inset: 0, opacity: 0.18,
                backgroundImage: `linear-gradient(to right, var(--text-sub) 1px, transparent 1px), linear-gradient(to bottom, var(--text-sub) 1px, transparent 1px)`,
                backgroundSize: `${numericDraftValue(values['/gridSettings/sx']) ?? numericDraftValue(base['/gridSettings/sx']) ?? 18}px ${numericDraftValue(values['/gridSettings/sy']) ?? numericDraftValue(base['/gridSettings/sy']) ?? 18}px`,
                backgroundPosition: `${numericDraftValue(values['/gridSettings/ox']) ?? numericDraftValue(base['/gridSettings/ox']) ?? 11}px ${numericDraftValue(values['/gridSettings/oy']) ?? numericDraftValue(base['/gridSettings/oy']) ?? 15}px` }} />}
              {components.map((component, index) => {
                const x = numericDraftValue(component.data.x) ?? numericDraftValue(componentsFrom(base)[index]?.data.x) ?? 0;
                const y = numericDraftValue(component.data.y) ?? numericDraftValue(componentsFrom(base)[index]?.data.y) ?? 0;
                const size = componentSize(component);
                return <button type="button" key={`preview-${component.type}-${index}`} data-testid={`overlay-preview-component-${index}`}
                  onClick={() => setSelectedIndex(index)} title={`${valueLabel(component.type)} @ ${x},${y}`}
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
              <AlertTriangle size={10} /> {issue.index === null ? '' : tr("组件 {0}：", [issue.index + 1])}{issue.message}
            </div>)}
          </div>}
        </main>

        <aside style={{ minHeight: 0, overflowY: 'auto', padding: 12, background: 'var(--bg-surface)', borderLeft: '1px solid var(--border-subtle)' }}>
          <datalist id="overlay-textures">{textures.map((asset) => <option key={asset.id} value={asset.relativePath}>{asset.relativePath}</option>)}</datalist>
          <datalist id="overlay-procedures">{procedures.map((procedure) => <option key={procedure.id} value={procedure.name}>{procedure.displayName}</option>)}</datalist>
          {!selected || selectedIndex === null ? <div style={{ fontSize: 10, color: 'var(--text-sub)' }}>{tr("选择组件以编辑属性。")}</div> : <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 10 }}>
              <div style={{ flex: 1 }}><div style={{ fontWeight: 700, fontSize: 11 }}>{componentName(selected, selectedIndex)}</div>
                <div style={{ fontSize: 9, color: 'var(--text-sub)' }}>{valueLabel(selected.type)}</div></div>
              <button type="button" aria-label={tr("上移 Overlay 组件")} onClick={() => moveSelected(-1)} disabled={selectedIndex === 0} style={{ padding: 4 }}><ChevronUp size={12} /></button>
              <button type="button" aria-label={tr("下移 Overlay 组件")} onClick={() => moveSelected(1)} disabled={selectedIndex === components.length - 1} style={{ padding: 4 }}><ChevronDown size={12} /></button>
              <button type="button" aria-label={tr("删除 Overlay 组件")} onClick={deleteSelected} className="btn-danger" style={{ padding: 4 }}><Trash2 size={12} /></button>
            </div>

            {(['x', 'y'] as const).map((key) => <label key={key} style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{fieldLabel(key)}
              <input data-testid={`overlay-component-field-${key}`} type="text" inputMode="decimal" value={String(selected.data[key] ?? '')}
                onChange={(event) => updateComponentField(key, event.target.value)} style={{ width: '100%', marginTop: 3 }} />
            </label>)}
            <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("锚点")}<select value={String(selected.data.anchorPoint ?? '')} onChange={(event) => updateComponentField('anchorPoint', event.target.value || null)}
                style={{ width: '100%', marginTop: 3 }}>{ANCHORS.map((anchor) => <option key={anchor} value={anchor}>{anchor ? valueLabel(anchor) : tr("无")}</option>)}</select>
            </label>
            <label style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
              <input type="checkbox" checked={Boolean(selected.data.locked)} onChange={(event) => updateComponentField('locked', event.target.checked)} /> {tr(" 锁定（locked）")}</label>

            {selected.type === 'label' && <>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("名称（name）")}<input value={String(selected.data.name ?? '')} onChange={(event) => updateComponentField('name', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("固定文本")}<input data-testid="overlay-component-field-label-text" value={labelText(selected)}
                  onChange={(event) => updateObjectField('text', 'fixedValue', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("颜色")}<input type="color" value={argbToHex(selected.data.color)} onChange={(event) => updateComponentField('color', hexToArgb(event.target.value))}
                  style={{ width: '100%', marginTop: 3, minHeight: 28 }} />
              </label>
              <label style={{ display: 'flex', gap: 5, marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>
                <input type="checkbox" checked={Boolean(selected.data.hasShadow)} onChange={(event) => updateComponentField('hasShadow', event.target.checked)} /> {tr(" 文字阴影（hasShadow）")}</label>
            </>}

            {selected.type === 'image' && <>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("图片（image）")}<input list="overlay-textures" value={String(selected.data.image ?? '')} onChange={(event) => updateComponentField('image', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'flex', gap: 5, marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>
                <input type="checkbox" checked={Boolean(selected.data.use1Xscale)} onChange={(event) => updateComponentField('use1Xscale', event.target.checked)} /> {tr(" 使用原始比例（use1Xscale）")}</label>
            </>}

            {selected.type === 'sprite' && <>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("精灵图（sprite）")}<input list="overlay-textures" value={String(selected.data.sprite ?? '')} onChange={(event) => updateComponentField('sprite', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("精灵帧数（spritesCount）")}<input data-testid="overlay-component-field-spritesCount" type="text" inputMode="decimal" value={String(selected.data.spritesCount ?? '')}
                  onChange={(event) => updateComponentField('spritesCount', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("固定帧序号")}<input type="text" inputMode="decimal" value={String((selected.data.spriteIndex as Record<string, unknown> | undefined)?.fixedValue ?? '')}
                  onChange={(event) => updateObjectField('spriteIndex', 'fixedValue', event.target.value)} style={{ width: '100%', marginTop: 3 }} />
              </label>
            </>}

            {selected.type === 'entitymodel' && <>
              <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("实体模型来源过程（entityModel）")}<input data-testid="overlay-component-field-entityModel" list="overlay-procedures" value={String(selected.data.entityModel ?? '')}
                  onChange={(event) => updateComponentField('entityModel', event.target.value || null)} style={{ width: '100%', marginTop: 3 }} />
              </label>
              {(['scale', 'rotationX'] as const).map((key) => <label key={key} style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{fieldLabel(key)}
                <input type="text" inputMode="decimal" value={String(selected.data[key] ?? '')} onChange={(event) => updateComponentField(key, event.target.value)}
                  style={{ width: '100%', marginTop: 3 }} />
              </label>)}
              <label style={{ display: 'flex', gap: 5, marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>
                <input type="checkbox" checked={Boolean(selected.data.followMouseMovement)}
                  onChange={(event) => updateComponentField('followMouseMovement', event.target.checked)} /> {tr(" 跟随鼠标（followMouseMovement）")}</label>
            </>}

            <label style={{ display: 'block', marginBottom: 7, fontSize: 9, color: 'var(--text-sub)' }}>{tr("显示条件过程（displayCondition）")}<input list="overlay-procedures" value={String(selected.data.displayCondition ?? '')}
                onChange={(event) => updateComponentField('displayCondition', event.target.value || null)} style={{ width: '100%', marginTop: 3 }} />
            </label>
          </>}
        </aside>
      </div>
    </div>
  );
};
