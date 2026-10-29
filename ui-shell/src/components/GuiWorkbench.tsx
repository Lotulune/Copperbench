import { tr } from '../i18n/locale';
import { fieldLabel, valueLabel } from '../i18n/labels';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowLeft,
  Box,
  ChevronDown,
  ChevronUp,
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
import { uiText, uiMessage, renderUiMessage, useUiLocale, type UiMessage } from '../i18n';
import { collectEditorFieldChanges, numericDraftValue } from '../hooks/editorFieldChanges';
import { layoutNumericDrafts } from '../hooks/layoutNumericDrafts';
import { commitSpecializedSession, getSpecializedSession, nextSpecializedReadToken, rebaseEditor,
  specializedEditorKey, updateSpecializedSession, useSpecializedEditorSession } from '../hooks/specializedEditorSessions';
import { EditorRecoveryNotice } from './EditorRecoveryNotice';
import { confirmEditorRead } from '../bridge/editorRecovery';
import { ElementPlate } from './ElementPlate';

interface GuiWorkbenchProps {
  element: ModElementSummary;
  onClose: () => void;
}

function createLabel(index: number): GuiComponentWire {
  return {
    type: 'label',
    data: {
      anchorPoint: null,
      x: Math.floor(WYSIWYG_WIDTH / 2 - 36),
      y: Math.floor(WYSIWYG_HEIGHT / 2 - 5),
      locked: false,
      name: `label_${index + 1}`,
      text: { name: null, fixedValue: 'Label' },
      color: { value: -1, falpha: 0 },
      hasShadow: false,
      displayCondition: null
    }
  };
}

function createImage(index: number, initialTexture: string): GuiComponentWire {
  return {
    type: 'image',
    data: {
      anchorPoint: null,
      x: Math.floor(WYSIWYG_WIDTH / 2 - 16),
      y: Math.floor(WYSIWYG_HEIGHT / 2 - 16),
      locked: false,
      image: initialTexture || `gui_image_${index + 1}.png`,
      use1Xscale: false,
      displayCondition: null
    }
  };
}

function createSlot(type: 'inputslot' | 'outputslot', id: number): GuiComponentWire {
  return {
    type,
    data: {
      anchorPoint: null,
      x: Math.floor(WYSIWYG_WIDTH / 2 - 9),
      y: Math.floor(WYSIWYG_HEIGHT / 2 - 9),
      locked: false,
      color: null,
      id,
      disablePickup: null,
      dropItemsWhenNotBound: true,
      onSlotChanged: null,
      onTakenFromSlot: null,
      onStackTransfer: null,
      ...(type === 'inputslot' ? { inputLimit: 'Blocks.AIR', disablePlacement: null } : {})
    }
  };
}

function labelText(component: GuiComponentWire): string {
  if (component.type !== 'label') return '';
  const text = component.data.text;
  if (!text || typeof text !== 'object' || Array.isArray(text)) return '';
  const value = (text as Record<string, unknown>).fixedValue;
  return typeof value === 'string' ? value : '';
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
    return [{ ...root, type: root.type, data: root.data as Record<string, unknown> }];
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
  const width = numericDraftValue(component.data.width);
  const height = numericDraftValue(component.data.height);
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
    'sprite', 'spritesCount', 'id', 'inputLimit', 'isUndecorated', 'use1Xscale', 'dropItemsWhenNotBound',
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
  if (width <= 0 || height <= 0) issues.push({ index: null, message: tr("GUI 宽度和高度必须大于 0。") });
  if (width > 512 || height > 512) issues.push({ index: null, message: tr("GUI 尺寸超过上游支持的 512 px 上限。") });

  const named = new Map<string, number>();
  components.forEach((component, index) => {
    if ((component.type === 'inputslot' || component.type === 'outputslot') && Number(values['/type'] ?? 0) !== 1) {
      issues.push({ index, message: tr("槽位组件要求界面类型为“含物品槽（With slots）”。") });
    }
    const name = component.data.name;
    if (typeof name === 'string' && name.trim()) {
      const previous = named.get(name);
      if (previous !== undefined) {
        issues.push({ index, message: tr("组件名称“{0}”与第 {1} 个组件重复。", [name, previous + 1]) });
      } else {
        named.set(name, index);
      }
    }
    const x = Number(component.data.x ?? 0);
    const y = Number(component.data.y ?? 0);
    const size = componentSize(component);
    if (!Number.isFinite(x) || !Number.isFinite(y)) {
      issues.push({ index, message: tr("组件坐标不是有效数字。") });
      return;
    }
    if (x < 0 || y < 0 || x + size.width > WYSIWYG_WIDTH || y + size.height > WYSIWYG_HEIGHT) {
      issues.push({ index, message: tr("组件超出 MCreator 427×240 WYSIWYG 画布范围。") });
    }
  });
  return issues;
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

export const GuiWorkbench: React.FC<GuiWorkbenchProps> = props => {
  const { state } = useWorkbench();
  const key = specializedEditorKey(state.workbench?.workspace.id ?? '', props.element.id, 'gui');
  return <GuiEditor key={key} sessionKey={key} {...props} />;
};
const initialGuiValues = () => ({} as Record<string, unknown>);

const GuiEditor: React.FC<GuiWorkbenchProps & { sessionKey: string }> = ({ element, onClose, sessionKey }) => {
  const {
    getModElementEditor,
    previewModElementChange,
    updateModElement,
    listAssets,
    refreshWorkspace,
    state
  } = useWorkbench();
  const current = useSpecializedEditorSession(sessionKey, initialGuiValues);
  const { editor, data: values, saving, error: saveMessage } = current;
  const base = useMemo(() => editor ? editorValues(editor) : {}, [editor]);
  const changeSession = (patch: Partial<typeof current>) => updateSpecializedSession(sessionKey, initialGuiValues, old => ({ ...old, ...patch }));
  const setValues = (change: React.SetStateAction<Record<string, unknown>>) => updateSpecializedSession(sessionKey, initialGuiValues, old => {
    const data = typeof change === 'function' ? change(old.data) : change;
    return { ...old, data, dirty: JSON.stringify(data) !== JSON.stringify(old.editor ? editorValues(old.editor) : {}), error: null };
  });
  const setSaving = (saving: boolean) => changeSession({ saving });
  const setSaveMessage = (error: UiMessage | null) => changeSession({ error });
  const [assets, setAssets] = useState<AssetProjection | null>(null);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [impact, setImpact] = useState<string[]>([]);
  const [newComponentType, setNewComponentType] = useState<'button' | 'label' | 'image' | 'inputslot' | 'outputslot'>('button');
  const requestToken = useRef(0);
  const actions = useRef({ getModElementEditor, listAssets, previewModElementChange, refreshWorkspace });
  actions.current = { getModElementEditor, listAssets, previewModElementChange, refreshWorkspace };
  const locale = useUiLocale();

  useEffect(() => {
    let cancelled = false;
    const existing = getSpecializedSession(sessionKey, initialGuiValues);
    if (existing.dirty || existing.saving || existing.loading || existing.saveUnknown) return;
    const readToken = nextSpecializedReadToken();
    const revision = state.workbench?.workspace.revision ?? 0;
    changeSession({ loading: true, readToken });
    setSelectedIndex(null);
    actions.current.getModElementEditor(element.id)
      .then(projection => {
        if (!projection || projection.element.id !== element.id) throw new Error('GUI editor projection unavailable');
        const next = editorValues(projection);
        updateSpecializedSession(sessionKey, initialGuiValues, old => old.readToken !== readToken ? old
          : { ...old, editor: projection, data: old.dirty ? old.data : clone(next), revision, loading: false, error: null });
        if (cancelled) return;
        if (componentsFrom(next).length > 0) setSelectedIndex(0);
      })
      .catch(() => {
        updateSpecializedSession(sessionKey, initialGuiValues, old => old.readToken !== readToken ? old
          : { ...old, loading: false, error: uiMessage('无法加载 GUI 编辑器。请返回后重试，原草稿已保留。', 'Could not load the GUI editor. Return and try again; your draft is preserved.') });
      });
    return () => {
      cancelled = true;
    };
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
  const issues = useMemo(() => [...layoutIssues(numericDraft.values, components),
    ...numericDraft.invalid.map(path => ({ index: null, message: uiText(`请输入有效数字：${path}`, `Enter a valid number: ${path}`) }))], [numericDraft, components, locale]);
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
      actions.current.previewModElementChange(element.id, changes)
        .then((preview) => {
          if (requestToken.current === token) setImpact(preview?.generationImpact?.affectedDomains ?? []);
        })
        .catch(() => {
          if (requestToken.current === token) setImpact([]);
        });
    }, 160);
    return () => { window.clearTimeout(timer); requestToken.current++; };
  }, [changes, element.id]);

  const setValue = (path: string, value: unknown) => {
    setValues((current) => ({ ...current, [path]: value }));
    setSaveMessage(null);
  };
  const readLatest = async () => {
    if (getSpecializedSession(sessionKey, initialGuiValues).saving) return;
    setSaving(true);
    try {
      const { editor: latest, revision: confirmedRevision } = await confirmEditorRead(state.workbench!.workspace.id, actions.current.refreshWorkspace, () => actions.current.getModElementEditor(element.id));
      if (!latest || latest.element.id !== element.id) throw new Error('GUI editor projection unavailable');
      updateSpecializedSession(sessionKey, initialGuiValues, old => ({ ...old, editor: latest, revision: confirmedRevision,
        dirty: JSON.stringify(old.data) !== JSON.stringify(editorValues(latest)), saveUnknown: false,
        error: uiMessage('已读取最新版本。请核对保留的草稿后再保存。', 'The latest version is loaded. Review your preserved draft before saving again.') }));
    } catch { setSaveMessage(uiMessage('无法读取最新版本，草稿和原诊断已保留。', 'Could not read the latest version. Your draft and original diagnostics are preserved.')); }
    finally { setSaving(false); }
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

  const updateComponentObjectField = (parent: string, key: string, value: unknown) => {
    if (selectedIndex === null) return;
    const next = clone(components);
    const current = next[selectedIndex].data[parent];
    const object = current && typeof current === 'object' && !Array.isArray(current)
      ? current as Record<string, unknown>
      : {};
    next[selectedIndex] = {
      ...next[selectedIndex],
      data: { ...next[selectedIndex].data, [parent]: { ...object, [key]: value } }
    };
    setComponents(next);
  };

  const addComponent = () => {
    const slotIds = components
      .map((component) => typeof component.data.id === 'number' ? component.data.id : -1)
      .filter((id) => id >= 0);
    const nextSlotId = slotIds.length === 0 ? 0 : Math.max(...slotIds) + 1;
    const initialTexture = textureAssets[0]?.relativePath ?? '';
    const component = newComponentType === 'button'
      ? createButton(components.length)
      : newComponentType === 'label'
        ? createLabel(components.length)
        : newComponentType === 'image'
          ? createImage(components.length, initialTexture)
          : createSlot(newComponentType, nextSlotId);
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
    const submitted = getSpecializedSession(sessionKey, initialGuiValues);
    if (issues.length > 0 || submitted.saving || submitted.saveUnknown || !editor || pending.invalid.length > 0) return;
    if (changes.length === 0) { changeSession({ data: numericDraft.values, dirty: false }); return; }
    setSaving(true);
    setSaveMessage(null);
    try {
      const result = await updateModElement(element.id, changes, submitted.revision);
      if (result.status === 'committed') {
        commitSpecializedSession(sessionKey, initialGuiValues, submitted.data, rebaseEditor(editor, changes, result.data?.element ?? editor.element), result.newRevision, result.diagnostics, numericDraft.values);
        setSaveMessage(uiMessage('GUI 更改已保存。', 'GUI changes saved.'));
      } else {
        changeSession({ diagnostics: result.diagnostics });
        setSaveMessage(result.diagnostics[0]?.message ?? uiMessage('保存被 Core 拒绝。草稿未保存。', 'Core rejected the save. Your draft is unsaved.'));
      }
    } catch {
      changeSession({ saveUnknown: true });
      setSaveMessage(uiMessage('未收到保存回执。', 'No save receipt was received.'));
    } finally {
      setSaving(false);
    }
  };

  const guiWidth = numericDraftValue(values['/width']) ?? numericDraftValue(base['/width']) ?? 176;
  const guiHeight = numericDraftValue(values['/height']) ?? numericDraftValue(base['/height']) ?? 166;
  const guiLeft = (WYSIWYG_WIDTH - guiWidth) / 2;
  const guiTop = (WYSIWYG_HEIGHT - guiHeight) / 2;

  if (!editor) {
    return (
      <div data-testid="gui-workbench-loading" style={{ padding: 24, color: 'var(--text-sub)' }}>
        {saveMessage ? renderUiMessage(saveMessage) : uiText('正在加载界面编辑器…', 'Loading the GUI editor…')}</div>
    );
  }

  return (
    <div data-testid="gui-workbench" style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, height: '100%' }}>
      <header className="editor-header" style={{ height: 52, padding: '0 14px', display: 'flex', alignItems: 'center', gap: 10,
        background: 'var(--bg-surface)', borderBottom: '1px solid var(--border-subtle)' }}>
        <button type="button" onClick={onClose} aria-label={tr("返回元素列表")} title={tr("返回元素列表")} className="btn-secondary editor-back">
          <ArrowLeft size={16} />
        </button>
        <ElementPlate type="gui" />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: 'var(--text-main)' }}>{element.displayName}</div>
          <div style={{ fontSize: 10, color: 'var(--text-sub)' }}>{tr("界面（GUI）编辑 · 布局、组件与事件")}</div>
        </div>
        {impact.length > 0 && (
          <span className="badge badge-blue" data-testid="gui-generation-impact">
            {tr("生成影响：")}{impact.join(', ')}
          </span>
        )}
        {saveMessage && <span role="status" style={{ fontSize: 12, color: 'var(--text-sub)' }}>{renderUiMessage(saveMessage)}</span>}
        {current.dirty && <span role="status">{uiText('未保存', 'Unsaved')}</span>}
        <button
          type="button"
          className="btn-primary"
          data-testid="gui-save-btn"
          disabled={saving || current.saveUnknown || !current.dirty || issues.length > 0 || pending.invalid.length > 0}
          onClick={save}
        >
          <Save size={13} /> {saving ? tr("保存中…") : tr("保存 GUI")}
        </button>
      </header>
      <EditorRecoveryNotice editor="gui" saveUnknown={current.saveUnknown} diagnostics={current.diagnostics}
        busy={saving || current.loading} onRead={() => void readLatest()} />

      <div style={{ flex: 1, minHeight: 0, display: 'grid', gridTemplateColumns: '230px minmax(420px, 1fr) 300px' }}>
        <aside style={{ borderRight: '1px solid var(--border-subtle)', background: 'var(--bg-surface)', minHeight: 0,
          display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: 12, borderBottom: '1px solid var(--border-subtle)', display: 'flex', gap: 8,
            alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <div style={{ fontWeight: 700, fontSize: 11, color: 'var(--text-main)' }}>{tr("组件树")}</div>
              <div style={{ fontSize: 9, color: 'var(--text-sub)' }}>{components.length} {tr(" 个组件")}</div>
            </div>
            <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
              <select
                aria-label={tr("新增组件类型")}
                data-testid="gui-add-component-type"
                value={newComponentType}
                onChange={(event) => setNewComponentType(event.target.value as typeof newComponentType)}
                style={{ maxWidth: 112, fontSize: 9, padding: '4px 5px' }}
              >
                <option value="button">{tr("按钮（Button）")}</option>
                <option value="label">{tr("文本标签（Label）")}</option>
                <option value="image">{tr("图片（Image）")}</option>
                <option value="inputslot">{tr("输入槽（Input Slot）")}</option>
                <option value="outputslot">{tr("输出槽（Output Slot）")}</option>
              </select>
              <button type="button" className="btn-secondary" onClick={addComponent} data-testid="gui-add-component-btn" aria-label={tr("添加组件")}>
                <Plus size={12} />
              </button>
            </div>
          </div>
          <div data-testid="gui-component-tree" style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
            {components.length === 0 ? (
              <div style={{ padding: '24px 8px', fontSize: 10, color: 'var(--text-sub)', textAlign: 'center' }}>
                {tr("当前界面没有组件。可添加按钮、文本标签、图片或物品槽。")}</div>
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
                  <div style={{ fontSize: 9, color: 'var(--text-sub)', marginTop: 3 }}>{valueLabel(component.type)}</div>
                </button>
              );
            })}
          </div>
        </aside>

        <main style={{ minWidth: 0, minHeight: 0, display: 'flex', flexDirection: 'column', background: 'var(--bg-base)' }}>
          <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', gap: 12,
            alignItems: 'center', flexWrap: 'wrap' }}>
            <label style={{ fontSize: 10, color: 'var(--text-sub)' }}>
              {tr("类型")}<select
                data-testid="gui-type"
                value={Number(values['/type'] ?? 0)}
                onChange={(event) => setValue('/type', Number(event.target.value))}
                style={{ marginLeft: 5 }}
              >
                <option value={0}>{tr("无物品槽")}</option>
                <option value={1}>{tr("含物品槽")}</option>
              </select>
            </label>
            <label style={{ fontSize: 10, color: 'var(--text-sub)' }}>
              {tr("宽度")}<input data-testid="gui-width" type="text" inputMode="decimal" value={String(values['/width'] ?? '')}
                onChange={(event) => setValue('/width', event.target.value)} style={{ width: 72, marginLeft: 5 }} />
            </label>
            <label style={{ fontSize: 10, color: 'var(--text-sub)' }}>
              {tr("高度")}<input data-testid="gui-height" type="text" inputMode="decimal" value={String(values['/height'] ?? '')}
                onChange={(event) => setValue('/height', event.target.value)} style={{ width: 72, marginLeft: 5 }} />
            </label>
            <label style={{ fontSize: 10, color: 'var(--text-sub)', display: 'flex', alignItems: 'center', gap: 5 }}>
              <input type="checkbox" checked={Boolean(values['/renderBgLayer'])}
                onChange={(event) => setValue('/renderBgLayer', event.target.checked)} /> {tr(" 渲染背景层")}</label>
            <label style={{ fontSize: 10, color: 'var(--text-sub)', display: 'flex', alignItems: 'center', gap: 5 }}>
              <input type="checkbox" checked={Boolean(values['/doesPauseGame'])}
                onChange={(event) => setValue('/doesPauseGame', event.target.checked)} /> {tr(" 暂停游戏")}</label>
          </div>

          <div style={{ flex: 1, minHeight: 0, overflow: 'auto', display: 'grid', placeItems: 'center', padding: 20 }}>
            <div data-testid="gui-layout-preview" style={{ width: WYSIWYG_WIDTH, height: WYSIWYG_HEIGHT, position: 'relative',
              background: 'var(--bg-panel)', border: '1px solid var(--border-active)', boxShadow: 'var(--shadow-md)' }}>
              <div style={{ position: 'absolute', left: guiLeft, top: guiTop, width: guiWidth, height: guiHeight,
                border: '1px dashed var(--accent-copper)', background: Boolean(values['/renderBgLayer']) ? 'var(--bg-surface)' : 'transparent' }} />
              {components.map((component, index) => {
                const x = numericDraftValue(component.data.x) ?? numericDraftValue(componentsFrom(base)[index]?.data.x) ?? 0;
                const y = numericDraftValue(component.data.y) ?? numericDraftValue(componentsFrom(base)[index]?.data.y) ?? 0;
                const size = componentSize(component);
                return (
                  <button
                    type="button"
                    key={`preview-${component.type}-${index}`}
                    data-testid={`gui-preview-component-${index}`}
                    onClick={() => setSelectedIndex(index)}
                    title={`${valueLabel(component.type)} @ ${x},${y}`}
                    style={{ position: 'absolute', left: x, top: y, width: size.width, height: size.height,
                      minWidth: 4, minHeight: 4, overflow: 'hidden', padding: 2, fontSize: 8,
                      border: `1px solid ${selectedIndex === index ? 'var(--accent-copper)' : 'var(--border-focus)'}`,
                      background: selectedIndex === index ? 'var(--accent-copper-dim)' : 'rgba(120,120,120,0.15)',
                      color: 'var(--text-main)' }}
                  >
                    {component.type === 'label'
                      ? labelText(component)
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
                  <AlertTriangle size={10} /> {issue.index === null ? '' : tr("组件 {0}：", [issue.index + 1])}{issue.message}
                </div>
              ))}
            </div>
          )}
        </main>

        <aside style={{ borderLeft: '1px solid var(--border-subtle)', background: 'var(--bg-surface)', minHeight: 0,
          overflowY: 'auto', padding: 12 }}>
          <div style={{ fontWeight: 700, fontSize: 11, color: 'var(--text-main)', marginBottom: 8 }}>{tr("GUI 事件绑定")}</div>
          {(['/onOpen', '/onTick', '/onClosed'] as const).map((path) => (
            <label key={path} style={{ display: 'block', fontSize: 9, color: 'var(--text-sub)', marginBottom: 7 }}>
              {fieldLabel(path.slice(1))}
              <input list="gui-procedure-options" value={String(values[path] ?? '')}
                onChange={(event) => setValue(path, event.target.value || null)} style={{ width: '100%', marginTop: 3 }} />
            </label>
          ))}
          <datalist id="gui-procedure-options">
            {procedures.map((procedure) => <option key={procedure.id} value={procedure.name}>{procedure.displayName}</option>)}
          </datalist>

          <div style={{ height: 1, background: 'var(--border-subtle)', margin: '12px 0' }} />
          {!selected || selectedIndex === null ? (
            <div style={{ fontSize: 10, color: 'var(--text-sub)' }}>{tr("选择组件以编辑属性。")}</div>
          ) : (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 700, fontSize: 11, color: 'var(--text-main)' }}>{componentName(selected, selectedIndex)}</div>
                  <div style={{ fontSize: 9, color: 'var(--text-sub)' }}>{valueLabel(selected.type)}</div>
                </div>
                <button type="button" aria-label={tr("上移组件")} onClick={() => moveSelected(-1)} disabled={selectedIndex === 0} style={{ padding: 4 }}>
                  <ChevronUp size={12} />
                </button>
                <button type="button" aria-label={tr("下移组件")} onClick={() => moveSelected(1)} disabled={selectedIndex === components.length - 1} style={{ padding: 4 }}>
                  <ChevronDown size={12} />
                </button>
                <button type="button" aria-label={tr("删除组件")} onClick={deleteSelected} className="btn-danger" style={{ padding: 4 }}>
                  <Trash2 size={12} />
                </button>
              </div>

              {selected.type === 'label' && (
                <>
                  <label style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                    {tr("固定文本")}<input
                      data-testid="gui-component-field-label-text"
                      value={labelText(selected)}
                      onChange={(event) => updateComponentObjectField('text', 'fixedValue', event.target.value)}
                      style={{ width: '100%', marginTop: 3 }}
                    />
                  </label>
                  <label style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                    {tr("颜色")}<input
                      data-testid="gui-component-field-label-color"
                      type="color"
                      value={argbToHex(selected.data.color)}
                      onChange={(event) => updateComponentField('color', hexToArgb(event.target.value))}
                      style={{ width: '100%', marginTop: 3, minHeight: 28 }}
                    />
                  </label>
                </>
              )}

              {editablePrimitiveEntries(selected).map(([key, value]) => {
                const inputId = `gui-component-field-${key}`;
                if (key === 'anchorPoint') {
                  return (
                    <label key={key} htmlFor={inputId} style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                      {tr("锚点")}<select id={inputId} value={String(value ?? '')} onChange={(event) => updateComponentField(key, event.target.value || null)}
                        style={{ width: '100%', marginTop: 3 }}>
                        {ANCHORS.map((anchor) => <option key={anchor} value={anchor}>{anchor ? valueLabel(anchor) : tr("无")}</option>)}
                      </select>
                    </label>
                  );
                }
                if (typeof value === 'boolean') {
                  return (
                    <label key={key} htmlFor={inputId} style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8,
                      fontSize: 9, color: 'var(--text-sub)' }}>
                      <input id={inputId} type="checkbox" checked={value}
                        onChange={(event) => updateComponentField(key, event.target.checked)} /> {fieldLabel(key)}
                    </label>
                  );
                }
                if (typeof value === 'number' || typeof componentsFrom(base)[selectedIndex ?? -1]?.data[key] === 'number'
                  || ['x', 'y', 'width', 'height', 'id', 'spritesCount'].includes(key)) {
                  return (
                    <label key={key} htmlFor={inputId} style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                      {fieldLabel(key)}
                      <input id={inputId} data-testid={`gui-component-field-${key}`} type="text" inputMode="decimal" value={String(value ?? '')}
                        onChange={(event) => updateComponentField(key, event.target.value)}
                        style={{ width: '100%', marginTop: 3 }} />
                    </label>
                  );
                }
                if (PROCEDURE_FIELDS.has(key)) {
                  return (
                    <label key={key} htmlFor={inputId} style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                      {fieldLabel(key)} {tr(" · 过程（Procedure）")}<input id={inputId} list="gui-procedure-options" value={String(value ?? '')}
                        onChange={(event) => updateComponentField(key, event.target.value || null)} style={{ width: '100%', marginTop: 3 }} />
                    </label>
                  );
                }
                return (
                  <label key={key} htmlFor={inputId} style={{ display: 'block', marginBottom: 8, fontSize: 9, color: 'var(--text-sub)' }}>
                    {fieldLabel(key)}
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
                  <summary style={{ fontSize: 9, color: 'var(--text-sub)', cursor: 'pointer' }}>{tr("复杂字段（只读保留）")}</summary>
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
