import React, { useState, useEffect, useMemo, useRef } from 'react';
import {
  X,
  Save,
  Trash2,
  AlertTriangle,
  Info,
  Check,
  Box,
  Compass,
  Link as LinkIcon
} from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import {
  ModElementSummary,
  FieldChange,
  ModElementEditorProjection,
  ModElementChangePreview,
  AssetProjection,
  EditorField,
  Diagnostic
} from '../types/contract';
import { t } from '../i18n';

interface ElementInspectorProps {
  element: ModElementSummary;
  onClose: () => void;
}

function fieldValue(field: EditorField, values: Record<string, unknown>): unknown {
  const value = values[field.path];
  if (field.control === 'json' && typeof value === 'string') return JSON.parse(value);
  return value;
}

function comparable(value: unknown): string {
  return JSON.stringify(value) ?? String(value);
}

function conditionTruthy(value: unknown): boolean {
  if (value === null || value === undefined || value === false || value === 0) return false;
  if (typeof value === 'string') return value.trim().length > 0;
  return true;
}

function conditionActive(field: EditorField, values: Record<string, unknown>): boolean {
  if (!field.condition) return true;
  return field.condition.paths.some((path) => conditionTruthy(values[path]));
}

function collectFieldChanges(
  editor: ModElementEditorProjection | null,
  values: Record<string, unknown>
): { changes: FieldChange[]; invalidJson: boolean } {
  if (!editor) return { changes: [], invalidJson: false };
  try {
    const changes = editor.sections
      .flatMap((section) => section.fields)
      .filter((field) => !field.readOnly)
      .flatMap((field) => {
        const next = fieldValue(field, values);
        return comparable(next) === comparable(field.value) ? [] : [{ path: field.path, value: next }];
      });
    return { changes, invalidJson: false };
  } catch {
    return { changes: [], invalidJson: true };
  }
}

function resourceCategoryForField(path: string): AssetProjection['assets'][number]['category'] | null {
  const field = fieldTestSuffix(path).toLowerCase();
  if (field.includes('texture') || field === 'icon') return 'TEXTURE';
  if (field.includes('sound') || field.includes('music')) return 'SOUND';
  if (field.includes('model')) return 'MODEL';
  return null;
}

function looksLikeWorkspaceAssetReference(value: string): boolean {
  return /[\\/]/.test(value) && /\.(png|jpg|jpeg|json|ogg|wav|ttf|otf)$/i.test(value);
}

function generationDomainLabel(domain: string): string {
  switch (domain) {
    case 'client_resources': return '客户端资源';
    case 'entity_behavior': return '实体行为';
    case 'entity_definition': return '实体定义';
    case 'worldgen': return '世界生成';
    case 'ui_layout': return '界面布局';
    default: return '元素生成源码';
  }
}

function fieldTestSuffix(path: string): string {
  return path.split('/').filter(Boolean).pop() ?? 'field';
}

function fieldControlId(path: string): string {
  const suffix = path.split('/').filter(Boolean).join('-').replace(/[^a-z0-9_-]/gi, '-');
  return `element-field-${suffix || 'field'}`;
}

function loaderExtensionName(path: string): string | null {
  const match = /^\/loaderExtensions\/([a-z0-9]+)\//i.exec(path);
  if (!match) return null;
  return match[1].charAt(0).toUpperCase() + match[1].slice(1);
}

export const ElementInspector: React.FC<ElementInspectorProps> = ({ element, onClose }) => {
  const {
    updateModElement,
    deleteModElement,
    getModElementEditor,
    previewModElementChange,
    listAssets,
    state
  } = useWorkbench();
  const [editor, setEditor] = useState<ModElementEditorProjection | null>(null);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [assets, setAssets] = useState<AssetProjection | null>(null);
  const [preview, setPreview] = useState<ModElementChangePreview | null>(null);
  const [isPreviewing, setIsPreviewing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [localErrors, setLocalErrors] = useState<string[]>([]);
  const [referenceDrafts, setReferenceDrafts] = useState<Record<string, string>>({});

  // Keep the latest query dispatcher without re-running the projection fetch
  // on unrelated bridge state changes.
  const getEditorRef = useRef(getModElementEditor);
  getEditorRef.current = getModElementEditor;
  const previewRef = useRef(previewModElementChange);
  previewRef.current = previewModElementChange;
  const listAssetsRef = useRef(listAssets);
  listAssetsRef.current = listAssets;

  useEffect(() => {
    let cancelled = false;
    setEditor(null);
    setValues({});
    setAssets(null);
    setPreview(null);
    setLocalErrors([]);
    setReferenceDrafts({});
    getEditorRef.current(element.id)
      .then((projection) => {
        if (cancelled || !projection) return;
        setEditor(projection);
        setValues(
          Object.fromEntries(
            projection.sections.flatMap((section) =>
              section.fields.map((field) => [field.path, field.value])
            )
          )
        );
      })
      .catch(() => {
        if (!cancelled) setLocalErrors(['无法加载元素编辑器，请重试。']);
      });
    return () => {
      cancelled = true;
    };
  }, [element.id]);

  useEffect(() => {
    if (!editor?.sections.some((section) => section.fields.some((field) => field.control === 'resource_reference'))) {
      setAssets(null);
      return;
    }
    let cancelled = false;
    listAssetsRef.current()
      .then((projection) => {
        if (!cancelled) setAssets(projection);
      })
      .catch(() => {
        if (!cancelled) setAssets(null);
      });
    return () => {
      cancelled = true;
    };
  }, [editor?.element.id]);

  const pending = useMemo(() => collectFieldChanges(editor, values), [editor, values]);

  useEffect(() => {
    if (!editor || pending.invalidJson || pending.changes.length === 0) {
      setPreview(null);
      setIsPreviewing(false);
      return;
    }
    let cancelled = false;
    setIsPreviewing(true);
    const timer = window.setTimeout(() => {
      previewRef.current(element.id, pending.changes)
        .then((result) => {
          if (!cancelled) setPreview(result);
        })
        .catch(() => {
          if (!cancelled) setPreview(null);
        })
        .finally(() => {
          if (!cancelled) setIsPreviewing(false);
        });
    }, 180);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [editor, element.id, pending]);

  const elementDiagnostics: Diagnostic[] = [
    ...(editor?.sections.flatMap((section) => section.fields.flatMap((field) => field.diagnostics)) ??
      []),
    ...state.diagnostics.filter((d) => !d.elementId || d.elementId === element.id)
  ];

  const handleSave = async () => {
    if (!editor) return;
    setIsSaving(true);
    setSaveSuccess(false);
    setLocalErrors([]);

    if (pending.invalidJson) {
      setLocalErrors(['JSON 字段格式无效；请修正括号、引号或逗号后再保存。']);
      setIsSaving(false);
      return;
    }
    const changes = pending.changes;
    if (changes.length === 0) {
      setIsSaving(false);
      return;
    }

    let result;
    try {
      result = await updateModElement(element.id, changes);
    } catch {
      setLocalErrors(['保存失败，工作区未发生更改。']);
      setIsSaving(false);
      return;
    }
    setIsSaving(false);

    if (result.status === 'committed') {
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
      // Refresh the projection, but keep the values the user just committed
      // so the mock (which does not persist field edits) does not visually
      // revert them.
      try {
        const refreshed = await getEditorRef.current(element.id);
        if (refreshed) {
          const committed = { ...values };
          const rebased = {
            ...refreshed,
            sections: refreshed.sections.map((section) => ({
              ...section,
              fields: section.fields.map((field) => ({
                ...field,
                value: Object.prototype.hasOwnProperty.call(committed, field.path)
                  ? fieldValue(field, committed)
                  : field.value
              }))
            }))
          };
          setEditor(rebased);
          setValues(committed);
          setPreview(null);
        }
      } catch {
        setLocalErrors(['元素已保存，但无法刷新编辑器投影。']);
      }
    } else if (result.diagnostics.length > 0) {
      setLocalErrors(result.diagnostics.map((d) => t(d.message)));
    }
  };

  const handleDelete = async () => {
    if (window.confirm(`确定要删除「${element.displayName}」吗？`)) {
      try {
        const result = await deleteModElement(element.id);
        if (result.status === 'committed') onClose();
        else setLocalErrors(result.diagnostics.map((diagnostic) => t(diagnostic.message)));
      } catch {
        setLocalErrors(['删除失败，元素未被移除。']);
      }
    }
  };

  const errorMessagesToDisplay =
    localErrors.length > 0
      ? localErrors
      : pending.invalidJson
        ? ['JSON 字段格式无效；请修正括号、引号或逗号后再保存。']
        : preview && !preview.canApply
          ? preview.diagnostics.filter((d) => d.severity === 'error').map((d) => t(d.message))
      : elementDiagnostics.filter((d) => d.severity === 'error').map((d) => t(d.message));

  const diagnosticByPath = new Map(
    elementDiagnostics.filter((d) => d.path).map((d) => [d.path as string, d])
  );

  const referenceCandidates = (field: EditorField): Array<{ value: string; label: string }> => {
    const byValue = new Map<string, string>();
    field.options.forEach((option) => byValue.set(String(option.value), t(option.label)));
    if (field.control === 'procedure_reference') {
      state.elements
        .filter((candidate) => candidate.type === 'procedure' || candidate.type === 'function')
        .forEach((candidate) => byValue.set(candidate.name, `${candidate.displayName} · ${candidate.type}`));
    } else if (field.control === 'element_reference') {
      state.elements
        .filter((candidate) => candidate.type === 'block' || candidate.type === 'plant' || candidate.type === 'fluid')
        .forEach((candidate) => byValue.set(`CUSTOM:${candidate.name}`, `${candidate.displayName} · ${candidate.type}`));
    } else if (field.control === 'element_reference_list') {
      state.elements
        .filter((candidate) => candidate.type === 'biome')
        .forEach((candidate) => byValue.set(`CUSTOM:${candidate.name}`, `${candidate.displayName} · biome`));
    } else if (field.control === 'resource_reference') {
      const category = resourceCategoryForField(field.path);
      assets?.assets
        .filter((asset) => !category || asset.category === category)
        .forEach((asset) => byValue.set(asset.relativePath, asset.relativePath));
    }
    return [...byValue.entries()].map(([value, label]) => ({ value, label }));
  };

  const referenceIssue = (field: EditorField): string | null => {
    if (!['procedure_reference', 'resource_reference', 'element_reference', 'element_reference_list'].includes(field.control)) return null;
    if (field.control === 'element_reference_list') {
      const current = Array.isArray(values[field.path]) ? (values[field.path] as unknown[]).map(String) : [];
      const candidates = referenceCandidates(field);
      const missing = current.find((entry) => entry.startsWith('CUSTOM:')
        && !candidates.some((candidate) => candidate.value === entry));
      return missing ? `工作区中未找到引用的元素：${missing}` : null;
    }
    const value = String(values[field.path] ?? '').trim();
    if (!value || ['root', 'none', '(none)', 'null'].includes(value.toLowerCase())) return null;
    const candidates = referenceCandidates(field);
    if (field.control === 'procedure_reference' && candidates.length > 0
      && !candidates.some((candidate) => candidate.value === value)) {
      return `未找到引用的 Procedure / Function：${value}`;
    }
    if (field.control === 'element_reference' && value.startsWith('CUSTOM:') && candidates.length > 0
      && !candidates.some((candidate) => candidate.value === value)) {
      return `工作区中未找到引用的元素：${value}`;
    }
    if (field.control === 'resource_reference' && assets && looksLikeWorkspaceAssetReference(value)
      && !assets.assets.some((asset) => asset.relativePath.replace(/\\/g, '/') === value.replace(/\\/g, '/'))) {
      return `工作区中未找到资源：${value}`;
    }
    return null;
  };

  const renderControl = (field: EditorField) => {
    const value = values[field.path];
    const enabledByCondition = conditionActive(field, values);
    const disabled = field.readOnly || !enabledByCondition;
    const commonStyle = disabled ? { background: 'var(--bg-hover)' } : {};
    const controlId = fieldControlId(field.path);

    switch (field.control) {
      case 'number':
        return (
          <input
            id={controlId}
            type="number"
            value={value === undefined || value === null ? '' : String(value)}
            min={field.constraints?.min}
            max={field.constraints?.max}
            step={field.constraints?.step ?? 1}
            disabled={disabled}
            readOnly={disabled}
            onChange={(e) =>
              setValues((prev) => ({ ...prev, [field.path]: parseFloat(e.target.value) || 0 }))
            }
            style={commonStyle}
            data-testid={`field-${fieldTestSuffix(field.path)}`}
          />
        );
      case 'toggle':
        return (
          <label
            htmlFor={controlId}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              fontSize: '12px',
              color: 'var(--text-main)',
              cursor: disabled ? 'not-allowed' : 'pointer'
            }}
          >
            <input
              id={controlId}
              type="checkbox"
              checked={Boolean(value)}
              disabled={disabled}
              onChange={(e) => setValues((prev) => ({ ...prev, [field.path]: e.target.checked }))}
              style={commonStyle}
              data-testid={`field-${fieldTestSuffix(field.path)}`}
            />
            <span>{value ? '启用' : '关闭'}</span>
          </label>
        );
      case 'select':
        return (
          <select
            id={controlId}
            value={value === undefined || value === null ? '' : String(value)}
            disabled={disabled}
            onChange={(e) => setValues((prev) => ({
              ...prev,
              [field.path]: typeof field.value === 'number' ? Number(e.target.value) : e.target.value
            }))}
            style={commonStyle}
            data-testid={`field-${fieldTestSuffix(field.path)}`}
          >
            {field.options.map((option) => (
              <option key={String(option.value)} value={String(option.value)} disabled={option.disabled}>
                {t(option.label)}
                {option.disabled && option.reason ? ` — ${t(option.reason)}` : ''}
              </option>
            ))}
          </select>
        );
      case 'textarea':
      case 'json':
        return (
          <textarea
            id={controlId}
            value={value === undefined || value === null ? ''
              : field.control === 'json' && typeof value !== 'string' ? JSON.stringify(value, null, 2)
              : String(value)}
            disabled={disabled}
            readOnly={disabled}
            rows={field.control === 'json' ? 8 : 5}
            onChange={(e) => setValues((prev) => ({ ...prev, [field.path]: e.target.value }))}
            style={commonStyle}
            data-testid={`field-${fieldTestSuffix(field.path)}`}
          />
        );
      case 'resource_reference':
      case 'procedure_reference':
      case 'element_reference':
        {
          const candidates = referenceCandidates(field);
          const listId = `${controlId}-suggestions`;
          return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <div style={{ position: 'relative' }}>
                <LinkIcon
                  size={13}
                  style={{ position: 'absolute', left: '10px', top: '9px', color: 'var(--text-sub)' }}
                />
                <input
                  id={controlId}
                  type="text"
                  list={candidates.length > 0 ? listId : undefined}
                  value={value === undefined || value === null ? '' : String(value)}
                  disabled={disabled}
                  readOnly={disabled}
                  onChange={(e) => setValues((prev) => ({ ...prev, [field.path]: e.target.value }))}
                  style={{ ...commonStyle, paddingLeft: '30px' }}
                  data-testid={`field-${fieldTestSuffix(field.path)}`}
                />
                {candidates.length > 0 && (
                  <datalist id={listId}>
                    {candidates.map((candidate) => (
                      <option key={candidate.value} value={candidate.value}>{candidate.label}</option>
                    ))}
                  </datalist>
                )}
              </div>
              <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                {field.control === 'resource_reference'
                  ? '资源选择器'
                  : field.control === 'element_reference'
                    ? '方块 / 元素引用选择器'
                    : '元素引用选择器'}
                {candidates.length > 0 ? ` · ${candidates.length} 个候选` : ' · 可输入完整引用'}
              </div>
            </div>
          );
        }
      case 'element_reference_list':
        {
          const candidates = referenceCandidates(field);
          const listId = `${controlId}-suggestions`;
          const current = Array.isArray(value) ? value.map(String) : [];
          const draft = referenceDrafts[field.path] ?? '';
          const addReference = () => {
            const nextValue = draft.trim();
            if (!nextValue || current.includes(nextValue)) return;
            setValues((prev) => ({ ...prev, [field.path]: [...current, nextValue] }));
            setReferenceDrafts((prev) => ({ ...prev, [field.path]: '' }));
          };
          return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {current.length > 0 && (
                <div data-testid={`field-${fieldTestSuffix(field.path)}-values`} style={{ display: 'flex', flexWrap: 'wrap', gap: '5px' }}>
                  {current.map((entry) => (
                    <span key={entry} className="badge badge-copper" style={{ display: 'inline-flex', gap: '4px', alignItems: 'center' }}>
                      {entry}
                      {!disabled && (
                        <button
                          type="button"
                          aria-label={`移除 ${entry}`}
                          onClick={() => setValues((prev) => ({
                            ...prev,
                            [field.path]: current.filter((candidate) => candidate !== entry)
                          }))}
                          style={{ padding: 0, color: 'inherit', lineHeight: 1 }}
                        >
                          ×
                        </button>
                      )}
                    </span>
                  ))}
                </div>
              )}
              <div style={{ display: 'flex', gap: '5px' }}>
                <input
                  id={controlId}
                  type="text"
                  list={candidates.length > 0 ? listId : undefined}
                  value={draft}
                  disabled={disabled}
                  readOnly={disabled}
                  placeholder="选择候选或输入完整 Biome 引用"
                  onChange={(e) => setReferenceDrafts((prev) => ({ ...prev, [field.path]: e.target.value }))}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addReference();
                    }
                  }}
                  style={{ ...commonStyle, flex: 1 }}
                  data-testid={`field-${fieldTestSuffix(field.path)}`}
                />
                <button type="button" className="btn-secondary" disabled={disabled || !draft.trim()} onClick={addReference}>
                  添加
                </button>
                {candidates.length > 0 && (
                  <datalist id={listId}>
                    {candidates.map((candidate) => (
                      <option key={candidate.value} value={candidate.value}>{candidate.label}</option>
                    ))}
                  </datalist>
                )}
              </div>
              <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                Biome 引用列表 · {candidates.length} 个候选 · 支持 CUSTOM:、标签或完整上游引用
              </div>
            </div>
          );
        }
      case 'text':
      default:
        return (
          <div>
            <input
              id={controlId}
              type="text"
              value={value === undefined || value === null ? '' : String(value)}
              disabled={disabled}
              readOnly={disabled}
              onChange={(e) => setValues((prev) => ({ ...prev, [field.path]: e.target.value }))}
              style={commonStyle}
              data-testid={`field-${fieldTestSuffix(field.path)}`}
            />
          </div>
        );
    }
  };

  return (
    <aside
      className="element-inspector animate-fade-in"
      data-testid="element-inspector"
      style={{
        width: '380px',
        background: 'var(--bg-surface)',
        borderLeft: '1px solid var(--border-subtle)',
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        flexShrink: 0,
        boxShadow: 'var(--shadow-md)',
        zIndex: 20
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: '14px 18px',
          borderBottom: '1px solid var(--border-subtle)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'var(--bg-panel)'
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div
            style={{
              width: '28px',
              height: '28px',
              borderRadius: 'var(--radius-sm)',
              background: element.type === 'block' ? 'var(--accent-copper-dim)' : 'var(--badge-blue-bg)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: element.type === 'block' ? 'var(--accent-copper)' : 'var(--badge-blue)'
            }}
          >
            {element.type === 'block' ? <Box size={16} /> : <Compass size={16} />}
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '13px', color: 'var(--text-main)' }}>
              检查器
            </div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
              {element.type.toUpperCase()} · {element.name}
            </div>
          </div>
        </div>

        <button
          type="button"
          aria-label="关闭元素检查器"
          onClick={onClose}
          style={{ padding: '4px', borderRadius: 'var(--radius-xs)', color: 'var(--text-muted)' }}
          title="关闭检查器"
          data-testid="inspector-close-btn"
        >
          <X size={16} />
        </button>
      </div>

      {/* Form Content */}
      <div
        style={{
          padding: '18px',
          overflowY: 'auto',
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          gap: '18px'
        }}
      >
        {/* Validation Errors Notice */}
        {errorMessagesToDisplay.length > 0 && (
          <div
            role="alert"
            data-testid="validation-alert"
            style={{
              background: 'var(--badge-red-bg)',
              border: '1px solid rgba(248, 81, 73, 0.4)',
              borderRadius: 'var(--radius-md)',
              padding: '12px',
              display: 'flex',
              flexDirection: 'column',
              gap: '6px'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--badge-red)', fontWeight: 600, fontSize: '12px' }}>
              <AlertTriangle size={15} />
              <span>校验未通过</span>
            </div>
            {errorMessagesToDisplay.map((msg, idx) => (
              <div key={idx} style={{ fontSize: '11px', color: 'var(--text-main)' }}>
                • {msg}
              </div>
            ))}
          </div>
        )}

        {editor && pending.changes.length > 0 && (
          <div
            data-testid="element-change-preview"
            style={{
              background: 'var(--bg-panel)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-md)',
              padding: '12px',
              display: 'flex',
              flexDirection: 'column',
              gap: '7px'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
              <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-main)' }}>更改影响预览</span>
              <span className="badge badge-blue" style={{ fontSize: '9px' }}>
                {preview?.semanticSummary?.changedFieldCount ?? pending.changes.length} 个字段
              </span>
            </div>
            {isPreviewing ? (
              <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>正在分析语义与生成影响…</div>
            ) : preview ? (
              <>
                <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                  分区：{(preview.semanticSummary?.sections ?? [])
                    .map((id) => editor.sections.find((section) => section.id === id))
                    .filter(Boolean)
                    .map((section) => t(section!.title))
                    .join('、') || '通用属性'}
                </div>
                {preview.generationImpact && (
                  <div style={{ fontSize: '10px', color: 'var(--badge-blue)' }}>
                    保存后需重新生成当前元素 · {preview.generationImpact.affectedDomains.map(generationDomainLabel).join('、')}
                    {preview.generationImpact.generatorId ? ` · ${preview.generationImpact.generatorId}` : ''}
                  </div>
                )}
              </>
            ) : (
              <div style={{ fontSize: '10px', color: 'var(--badge-amber)' }}>暂时无法读取生成影响，保存仍会走 Core 校验。</div>
            )}
          </div>
        )}

        {!editor ? (
          <div
            data-testid="inspector-loading"
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '10px',
              padding: '48px 0',
              color: 'var(--text-sub)',
              fontSize: '12px'
            }}
          >
            正在加载编辑器投影…
          </div>
        ) : (
          editor.sections.map((section) => (
            <div key={section.id} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <div style={{ fontSize: '11px', fontWeight: 700, textTransform: 'uppercase', color: 'var(--text-sub)', letterSpacing: '0.5px' }}>
                {t(section.title)}
              </div>

              {section.fields.map((field) => {
                const fieldDiagnostic = diagnosticByPath.get(field.path);
                const pickerIssue = referenceIssue(field);
                const extensionName = loaderExtensionName(field.path);
                const isLoaderExtension = field.readOnly && extensionName !== null;
                const controlId = fieldControlId(field.path);
                const enabledByCondition = conditionActive(field, values);

                const controlBlock = (
                  <>
                    {renderControl(field)}
                    {field.constraints && field.control === 'number' && (
                      <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                        范围：{field.constraints.min} - {field.constraints.max}
                      </div>
                    )}
                    {field.condition && (
                      <div
                        data-testid={`field-condition-${fieldTestSuffix(field.path)}`}
                        style={{ fontSize: '10px', color: enabledByCondition ? 'var(--badge-blue)' : 'var(--text-sub)' }}
                      >
                        {enabledByCondition ? '条件已启用 · 当前字段必填' : '条件未启用 · 当前字段不会参与生成'}
                      </div>
                    )}
                    {pickerIssue && (
                      <div
                        data-testid={`reference-issue-${fieldTestSuffix(field.path)}`}
                        style={{
                          fontSize: '10px',
                          color: 'var(--badge-amber)',
                          display: 'flex',
                          alignItems: 'flex-start',
                          gap: '4px'
                        }}
                      >
                        <AlertTriangle size={11} style={{ flexShrink: 0, marginTop: '1px' }} />
                        <span>{pickerIssue}</span>
                      </div>
                    )}
                    {fieldDiagnostic && (
                      <div
                        style={{
                          fontSize: '10px',
                          color: fieldDiagnostic.severity === 'error' ? 'var(--badge-red)' : 'var(--badge-amber)',
                          display: 'flex',
                          alignItems: 'flex-start',
                          gap: '4px'
                        }}
                      >
                        <AlertTriangle size={11} style={{ flexShrink: 0, marginTop: '1px' }} />
                        <span>{t(fieldDiagnostic.message)}</span>
                      </div>
                    )}
                  </>
                );

                if (isLoaderExtension) {
                  return (
                    <div
                      key={field.path}
                      data-field-path={field.path}
                      style={{
                        background: 'var(--bg-panel)',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-md)',
                        padding: '12px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '8px'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--badge-blue)' }}>
                          {extensionName} 加载器扩展
                        </span>
                        <span className="badge badge-amber" style={{ fontSize: '9px' }}>
                          只读保留
                        </span>
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <label htmlFor={controlId} style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
                          {t(field.label)}
                        </label>
                        {controlBlock}
                      </div>

                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: '6px', fontSize: '10px', color: 'var(--badge-amber)' }}>
                        <Info size={13} style={{ flexShrink: 0, marginTop: '2px' }} />
                        <span>
                          {field.help
                            ? t(field.help)
                            : '该字段已保留在工作区元数据中，但当前活动生成器下不可用。'}
                        </span>
                      </div>
                    </div>
                  );
                }

                return (
                  <div
                    key={field.path}
                    data-field-path={field.path}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '4px',
                      opacity: field.readOnly || !enabledByCondition ? 0.72 : 1
                    }}
                  >
                    <label htmlFor={controlId} style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
                      {t(field.label)}
                      {(field.required || (field.condition && enabledByCondition)) && <span style={{ color: 'var(--badge-red)' }}> *</span>}
                      {field.readOnly && (
                        <span className="badge badge-amber" style={{ fontSize: '9px', marginLeft: '6px' }}>
                          只读保留
                        </span>
                      )}
                    </label>
                    {controlBlock}
                  </div>
                );
              })}
            </div>
          ))
        )}
      </div>

      {/* Footer Actions */}
      <div
        style={{
          padding: '14px 18px',
          borderTop: '1px solid var(--border-subtle)',
          background: 'var(--bg-panel)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between'
        }}
      >
        <button
          className="btn-danger"
          style={{ fontSize: '11px' }}
          onClick={handleDelete}
          data-testid="inspector-delete-btn"
        >
          <Trash2 size={13} />
          <span>删除</span>
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {saveSuccess && (
            <span style={{ color: 'var(--badge-green)', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Check size={13} /> 已保存
            </span>
          )}
          <button
            className="btn-primary"
            onClick={handleSave}
            disabled={isSaving || !editor || pending.invalidJson || pending.changes.length === 0 || Boolean(preview && !preview.canApply)}
            data-testid="inspector-save-btn"
          >
            <Save size={13} />
            <span>{isSaving ? '保存中…' : '应用更改'}</span>
          </button>
        </div>
      </div>
    </aside>
  );
};
