import { elementLabel } from '../i18n/labels';
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
  Diagnostic,
  ModElementType
  , WorkspacePlan
} from '../types/contract';
import { t, uiText, uiMessage, renderUiMessage, englishCount, type UiMessage } from '../i18n';
import { BlockbenchTasksPanel } from './BlockbenchTasksPanel';

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

function conditionExpressionActive(expression: string, values: Record<string, unknown>): boolean {
  let condition = expression.trim();
  const negate = condition.startsWith('!');
  if (negate) condition = condition.slice(1).trim();
  let result = false;
  const compare = (operator: string): [string, string] | null => {
    const index = condition.indexOf(operator);
    return index >= 0 ? [condition.slice(0, index).trim(), condition.slice(index + operator.length).trim()] : null;
  };
  const stringEquals = compare('%=');
  const intListEquals = compare('#?=');
  const intEquals = compare('#=');
  if (intListEquals) {
    const actual = Number(values[`/${intListEquals[0]}`]);
    result = Number.isFinite(actual) && intListEquals[1].split(',').some((entry) => Number(entry.trim()) === actual);
  } else if (intEquals) {
    result = Number(values[`/${intEquals[0]}`]) === Number(intEquals[1]);
  } else if (stringEquals) {
    result = String(values[`/${stringEquals[0]}`] ?? '') === stringEquals[1];
  } else {
    result = conditionTruthy(values[`/${condition}`]);
  }
  return negate ? !result : result;
}

function conditionActive(field: EditorField, values: Record<string, unknown>): boolean {
  if (!field.condition) return true;
  if (field.condition.expressions?.length) {
    return field.condition.expressions.some((expression) => conditionExpressionActive(expression, values));
  }
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

function resourceStorageIdentifier(asset: AssetProjection['assets'][number], field: EditorField): string | null {
  if (asset.category !== 'TEXTURE') return null;
  const normalized = asset.relativePath.replace(/\\/g, '/');
  if (field.resourceType && !normalized.toLowerCase().includes(`/textures/${field.resourceType.toLowerCase()}/`)) return null;
  return normalized.split('/').filter(Boolean).pop() ?? null;
}

function generationDomainLabel(domain: string): string {
  switch (domain) {
    case 'client_resources': return uiText("客户端资源", "Client resources");
    case 'entity_behavior': return uiText("实体行为", "Entity behavior");
    case 'entity_definition': return uiText("实体定义", "Entity definition");
    case 'worldgen': return uiText("世界生成", "World generation");
    case 'ui_layout': return uiText("界面布局", "UI layout");
    default: return uiText("元素生成源码", "Generated element sources");
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
    runDiagnosticAction,
    planWorkspaceChanges,
    applyWorkspacePlan,
    state
  } = useWorkbench();
  const [editor, setEditor] = useState<ModElementEditorProjection | null>(null);
  const [configurationPlan, setConfigurationPlan] = useState<WorkspacePlan | null>(null);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [assets, setAssets] = useState<AssetProjection | null>(null);
  const [preview, setPreview] = useState<ModElementChangePreview | null>(null);
  const [isPreviewing, setIsPreviewing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [localErrors, setLocalErrors] = useState<UiMessage[]>([]);
  const [referenceDrafts, setReferenceDrafts] = useState<Record<string, string>>({});
  const [externalChange, setExternalChange] = useState(false);
  const [reloadVersion, setReloadVersion] = useState(0);
  const forceReload = useRef(false);
  const baseRevision = useRef(state.workbench?.workspace.revision ?? 0);
  const revisionRef = useRef(baseRevision.current);
  revisionRef.current = state.workbench?.workspace.revision ?? 0;
  const pending = useMemo(() => collectFieldChanges(editor, values), [editor, values]);
  const draftRef = useRef({ editor, pending, isSaving });
  draftRef.current = { editor, pending, isSaving };

  // Keep the latest query dispatcher without re-running the projection fetch
  // on unrelated bridge state changes.
  const getEditorRef = useRef(getModElementEditor);
  getEditorRef.current = getModElementEditor;
  const previewRef = useRef(previewModElementChange);
  previewRef.current = previewModElementChange;
  const listAssetsRef = useRef(listAssets);
  listAssetsRef.current = listAssets;

  const preparationCompletions = Object.values(state.tasks)
    .filter(task => ['generate', 'build', 'export', 'run_client', 'run_server', 'run_datagen', 'run_gametest'].includes(task.kind)
      && !['queued', 'running'].includes(task.state))
    .map(task => `${task.id}:${task.state}:${task.completedAt ?? ''}`).sort().join('|');
  useEffect(() => {
    if (!preparationCompletions) return;
    let cancelled = false;
    getEditorRef.current(element.id).then(projection => {
      if (!cancelled && projection) setEditor(current => current?.element.id === element.id
        ? { ...current, configuration: projection.configuration } : current);
    }).catch(() => { /* Keep the last observation until the next successful query. */ });
    return () => { cancelled = true; };
  }, [element.id, preparationCompletions]);

  useEffect(() => {
    const draft = draftRef.current;
    if (draft.editor?.element.id === element.id && !forceReload.current) {
      if (draft.isSaving) return;
      if (draft.pending.invalidJson || draft.pending.changes.length > 0) {
        setExternalChange(true);
        return;
      }
    }
    forceReload.current = false;
    let cancelled = false;
    const observedRevision = revisionRef.current;
    setExternalChange(false);
    setEditor(null);
    setConfigurationPlan(null);
    setValues({});
    setAssets(null);
    setPreview(null);
    setLocalErrors([]);
    setReferenceDrafts({});
    getEditorRef.current(element.id)
      .then((projection) => {
        if (cancelled || !projection) return;
        baseRevision.current = observedRevision;
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
        if (!cancelled) setLocalErrors([uiMessage("无法加载元素编辑器，请重试。", "Could not load the element editor. Please try again.")]);
      });
    return () => {
      cancelled = true;
    };
  }, [element.id, element.updatedAt, reloadVersion]);

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
      setLocalErrors([uiMessage("JSON 字段格式无效；请修正括号、引号或逗号后再保存。", "Invalid JSON field. Check brackets, quotes and commas before saving.")]);
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
      result = await updateModElement(element.id, changes, baseRevision.current);
    } catch {
      setLocalErrors([uiMessage("保存失败，工作区未发生更改。", "Save failed. The workspace was not changed.")]);
      setIsSaving(false);
      return;
    }
    setIsSaving(false);

    if (result.status === 'committed') {
      baseRevision.current = result.newRevision;
      setExternalChange(false);
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
        setLocalErrors([uiMessage("元素已保存，但无法刷新编辑器投影。", "The element was saved, but the editor could not be refreshed.")]);
      }
    } else if (result.conflict) {
      setExternalChange(true);
    } else if (result.diagnostics.length > 0) {
      setLocalErrors(result.diagnostics.map((d) => d.message));
    }
  };

  const handleDelete = async () => {
    if (window.confirm(uiText(`确定要删除「${element.displayName}」吗？`, `Delete "${element.displayName}"?`))) {
      try {
        const result = await deleteModElement(element.id);
        if (result.status === 'committed') onClose();
        else setLocalErrors(result.diagnostics.map((diagnostic) => diagnostic.message));
      } catch {
        setLocalErrors([uiMessage("删除失败，元素未被移除。", "Delete failed. The element was not removed.")]);
      }
    }
  };

  const errorMessagesToDisplay =
    localErrors.length > 0
      ? localErrors
      : pending.invalidJson
        ? [uiText("JSON 字段格式无效；请修正括号、引号或逗号后再保存。", "Invalid JSON field. Check brackets, quotes and commas before saving.")]
        : preview && !preview.canApply
          ? preview.diagnostics.filter((d) => d.severity === 'error').map((d) => t(d.message))
      : elementDiagnostics.filter((d) => d.severity === 'error').map((d) => t(d.message));

  const elementPathPrefix = `/elements/${element.id}`;
  const diagnosticByPath = new Map(
    elementDiagnostics.filter((d) => d.path).map((d) => {
      const path = d.path as string;
      return [path.startsWith(`${elementPathPrefix}/`) ? path.slice(elementPathPrefix.length) : path, d];
    })
  );

  const referenceCandidates = (field: EditorField): Array<{ value: string; label: string }> => {
    const byValue = new Map<string, string>();
    const acceptedTypes = field.referenceTypes?.length ? new Set(field.referenceTypes) : null;
    field.options.forEach((option) => byValue.set(String(option.value), t(option.label)));
    if (field.control === 'procedure_reference') {
      state.elements
        .filter((candidate) => candidate.type === 'procedure' || candidate.type === 'function')
        .forEach((candidate) => byValue.set(candidate.name, `${candidate.displayName} · ${candidate.type}`));
    } else if (field.control === 'element_reference') {
      state.elements
        .filter((candidate) => acceptedTypes
          ? acceptedTypes.has(candidate.type as ModElementType)
          : candidate.type === 'block' || candidate.type === 'plant' || candidate.type === 'fluid')
        .forEach((candidate) => byValue.set(`CUSTOM:${candidate.name}`, `${candidate.displayName} · ${candidate.type}`));
    } else if (field.control === 'element_reference_list') {
      state.elements
        .filter((candidate) => acceptedTypes ? acceptedTypes.has(candidate.type as ModElementType) : candidate.type === 'biome')
        .forEach((candidate) => byValue.set(`CUSTOM:${candidate.name}`, `${candidate.displayName} · ${candidate.type}`));
    } else if (field.control === 'resource_reference') {
      const category = resourceCategoryForField(field.path);
      assets?.assets
        .filter((asset) => !category || asset.category === category)
        .forEach((asset) => {
          const identifier = resourceStorageIdentifier(asset, field);
          if (identifier) byValue.set(identifier, `${identifier} · ${asset.relativePath}`);
        });
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
      return missing ? uiText(`工作区中未找到引用的元素：${missing}`, `Referenced element not found in this workspace: ${missing}`) : null;
    }
    const value = String(values[field.path] ?? '').trim();
    if (!value || ['root', 'none', '(none)', 'null'].includes(value.toLowerCase())) return null;
    const candidates = referenceCandidates(field);
    if (field.control === 'procedure_reference' && candidates.length > 0
      && !candidates.some((candidate) => candidate.value === value)) {
      return uiText(`未找到引用的 Procedure / Function：${value}`, `Referenced Procedure / Function not found: ${value}`);
    }
    if (field.control === 'element_reference' && value.startsWith('CUSTOM:') && candidates.length > 0
      && !candidates.some((candidate) => candidate.value === value)) {
      return uiText(`工作区中未找到引用的元素：${value}`, `Referenced element not found in this workspace: ${value}`);
    }
    if (field.control === 'resource_reference' && assets && looksLikeWorkspaceAssetReference(value)
      && !assets.assets.some((asset) => asset.relativePath.replace(/\\/g, '/') === value.replace(/\\/g, '/'))) {
      return uiText(`工作区中未找到资源：${value}`, `Resource not found in this workspace: ${value}`);
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
            <span>{value ? uiText('启用', 'Enabled') : uiText('关闭', 'Disabled')}</span>
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
                  ? uiText("资源选择器", "Resource picker")
                  : field.control === 'element_reference'
                    ? uiText("方块 / 元素引用选择器", "Block / element reference picker")
                    : uiText("元素引用选择器", "Element reference picker")}
                {candidates.length > 0 ? uiText(` · ${candidates.length} 个候选`, ` · Candidates: ${candidates.length}`) : uiText(" · 可输入完整引用", " · Enter a full reference")}
              </div>
            </div>
          );
        }
      case 'structured_list':
        {
          const current = Array.isArray(value)
            ? value.filter((entry): entry is Record<string, unknown> => Boolean(entry) && typeof entry === 'object' && !Array.isArray(entry))
            : [];
          const itemFields = field.itemFields ?? [];
          const updateItem = (index: number, itemPath: string, nextValue: unknown) => {
            const itemName = fieldTestSuffix(itemPath);
            const next = current.map((item, itemIndex) => itemIndex === index ? { ...item, [itemName]: nextValue } : item);
            setValues((prev) => ({ ...prev, [field.path]: next }));
          };
          const renderItemControl = (itemField: EditorField, item: Record<string, unknown>, index: number) => {
            const itemName = fieldTestSuffix(itemField.path);
            const itemValue = item[itemName] ?? itemField.value;
            const itemValuesByPath = Object.fromEntries(itemFields.map((candidate) => [
              candidate.path,
              item[fieldTestSuffix(candidate.path)] ?? candidate.value
            ]));
            const itemDisabled = disabled || itemField.readOnly || !conditionActive(itemField, itemValuesByPath);
            const itemTestId = `field-${fieldTestSuffix(field.path)}-${index}-${itemName}`;
            if (itemField.control === 'number') {
              return <input type="number" value={itemValue === null || itemValue === undefined ? '' : String(itemValue)}
                min={itemField.constraints?.min} max={itemField.constraints?.max} step={itemField.constraints?.step ?? 1}
                disabled={itemDisabled} onChange={(e) => updateItem(index, itemField.path, Number(e.target.value))}
                data-testid={itemTestId} />;
            }
            if (itemField.control === 'toggle') {
              return <input type="checkbox" checked={Boolean(itemValue)} disabled={itemDisabled}
                onChange={(e) => updateItem(index, itemField.path, e.target.checked)} data-testid={itemTestId} />;
            }
            if (itemField.control === 'select') {
              return <select value={itemValue === null || itemValue === undefined ? '' : String(itemValue)} disabled={itemDisabled}
                onChange={(e) => updateItem(index, itemField.path,
                  typeof itemField.value === 'number' ? Number(e.target.value) : e.target.value)} data-testid={itemTestId}>
                {itemField.options.map((option) => <option key={String(option.value)} value={String(option.value)} disabled={option.disabled}>
                  {t(option.label)}
                </option>)}
              </select>;
            }
            if (['resource_reference', 'procedure_reference', 'element_reference'].includes(itemField.control)) {
              const candidates = referenceCandidates(itemField);
              const listId = `${fieldControlId(field.path)}-${index}-${itemName}-suggestions`;
              return <>
                <input type="text" list={candidates.length ? listId : undefined}
                  value={itemValue === null || itemValue === undefined ? '' : String(itemValue)} disabled={itemDisabled}
                  onChange={(e) => updateItem(index, itemField.path, e.target.value)} data-testid={itemTestId} />
                {candidates.length > 0 && <datalist id={listId}>
                  {candidates.map((candidate) => <option key={candidate.value} value={candidate.value}>{candidate.label}</option>)}
                </datalist>}
              </>;
            }
            return <input type="text" value={itemValue === null || itemValue === undefined ? '' : String(itemValue)}
              disabled={itemDisabled} onChange={(e) => updateItem(index, itemField.path, e.target.value)} data-testid={itemTestId} />;
          };
          return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }} data-testid={`field-${fieldTestSuffix(field.path)}-structured-list`}>
              {current.map((item, index) => (
                <div key={index} style={{ border: '1px solid var(--border-main)', borderRadius: '6px', padding: '8px', display: 'grid', gap: '7px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong style={{ fontSize: '11px' }}>#{index + 1}</strong>
                    <button type="button" className="btn-secondary" disabled={disabled}
                      onClick={() => setValues((prev) => ({ ...prev, [field.path]: current.filter((_, itemIndex) => itemIndex !== index) }))}
                      data-testid={`field-${fieldTestSuffix(field.path)}-${index}-remove`}>
                      {t({ key: 'action.remove_list_item', fallback: 'Remove item' })}
                    </button>
                  </div>
                  {itemFields.map((itemField) => (
                    <label key={itemField.path} style={{ display: 'grid', gridTemplateColumns: 'minmax(100px, 0.8fr) minmax(0, 1.4fr)', gap: '8px', alignItems: 'center' }}>
                      <span style={{ fontSize: '11px', color: 'var(--text-sub)' }}>{t(itemField.label)}</span>
                      {renderItemControl(itemField, item, index)}
                    </label>
                  ))}
                </div>
              ))}
              <button type="button" className="btn-secondary" disabled={disabled}
                onClick={() => {
                  const template = JSON.parse(JSON.stringify(field.itemTemplate ?? {})) as Record<string, unknown>;
                  setValues((prev) => ({ ...prev, [field.path]: [...current, template] }));
                }} data-testid={`field-${fieldTestSuffix(field.path)}-add`}>
                {t({ key: 'action.add_list_item', fallback: 'Add item' })}
              </button>
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
                          aria-label={uiText(`移除 ${entry}`, `Remove ${entry}`)}
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
                  placeholder={uiText("选择候选或输入完整 Biome 引用", "Select a candidate or enter a full Biome reference")}
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
                  {uiText("添加", "Add")}</button>
                {candidates.length > 0 && (
                  <datalist id={listId}>
                    {candidates.map((candidate) => (
                      <option key={candidate.value} value={candidate.value}>{candidate.label}</option>
                    ))}
                  </datalist>
                )}
              </div>
              <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                {uiText(`Biome 引用列表 · ${candidates.length} 个候选 · 支持 CUSTOM:、标签或完整上游引用`, `Biome references · Candidates: ${candidates.length} · Supports CUSTOM:, tags and full upstream references`)}
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
              {uiText("检查器", "Inspector")}</div>
            <div style={{ fontSize: '11px', color: 'var(--text-sub)' }}>
              {elementLabel(element.type)} · {element.name}
            </div>
          </div>
        </div>

        <button
          type="button"
          aria-label={uiText("关闭元素检查器", "Close element inspector")}
          onClick={onClose}
          style={{ padding: '4px', borderRadius: 'var(--radius-xs)', color: 'var(--text-muted)' }}
          title={uiText("关闭检查器", "Close inspector")}
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
        {(element.type === 'block' || element.type === 'item') && <BlockbenchTasksPanel key={element.id} element={editor?.element ?? element} />}
        {editor?.configuration?.generationState === 'pending' && <p role="status" data-testid="generation-pending-notice">
          {t({ key: 'editor.generation_pending', fallback: 'Definition saved; source generation is pending. Run Generate or Build to prepare dependencies and update managed sources.' })}
        </p>}
        {editor?.configuration?.status === 'drift' && <section aria-label={uiText("配置差异", "Configuration differences")} style={{ fontSize: '13px' }}>
          <strong>{uiText("配置与生成定义不一致", "Configuration differs from the generated definition")}</strong>
          <p>{uiText("下方字段显示实际定义。请先审查差异，再预览处理方式。", "The fields below show the actual definition. Review the differences before previewing a resolution.")}</p>
          <ul>{editor.configuration.differences?.map(difference => <li key={difference.path}>
            <code>{difference.path}</code>{uiText('：声明 ', ': declared ')}{JSON.stringify(difference.declared)}{uiText('；实际 ', '; actual ')}{JSON.stringify(difference.effective)}
          </li>)}</ul>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {(['adopt_definition', 'reapply_declared'] as const).map(mode => <button key={mode} disabled={isSaving}
              onClick={async () => {
                const operation = editor.configuration?.planOperations?.[mode];
                if (!operation) return;
                setIsSaving(true);
                try {
                  const plan = await planWorkspaceChanges([operation], true, baseRevision.current);
                  setConfigurationPlan(plan);
                  if (!plan) setLocalErrors([uiMessage("无法生成差异处理计划，请查看诊断并重新加载。", "Could not create a resolution plan. Review the diagnostics and reload.")]);
                } catch { setLocalErrors([uiMessage("无法预览配置处理计划。", "Could not preview the configuration resolution plan.")]); }
                finally { setIsSaving(false); }
              }}>{mode === 'adopt_definition' ? uiText("预览：采用当前定义", "Preview: adopt current definition") : uiText("预览：重新应用声明配置", "Preview: reapply declared configuration")}</button>)}
          </div>
          {configurationPlan && <div>
            <p>{uiText(`已生成修订 ${configurationPlan.baseRevision} 的处理计划。应用前将再次核对源文件并创建恢复点。`, `Resolution plan prepared for revision ${configurationPlan.baseRevision}. Source files will be checked again and a recovery point created before applying it.`)}</p>
            <details><summary>{uiText("查看变更详情", "View change details")}</summary><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{JSON.stringify(configurationPlan.semanticDiff, null, 2)}</pre></details>
            <button disabled={isSaving} onClick={async () => {
              setIsSaving(true);
              try {
                const result = await applyWorkspacePlan(configurationPlan);
                if (result.status === 'committed') { forceReload.current = true; setReloadVersion(v => v + 1); }
                else setLocalErrors(result.diagnostics.map(d => d.message));
              } catch { setLocalErrors([uiMessage("配置处理未完成，请重新加载后核对。", "Configuration resolution did not complete. Reload and check the result.")]); }
              finally { setIsSaving(false); setConfigurationPlan(null); }
            }}>{uiText("应用已审查的处理计划", "Apply reviewed resolution plan")}</button>
          </div>}
        </section>}
        {externalChange && (
          <div role="alert" data-testid="inspector-external-change">
            <p>{uiText("此元素或工作区已被其他操作修改。当前草稿已保留，请核对最新内容后再编辑。", "Another operation changed this element or workspace. Your draft is preserved. Review the latest content before continuing.")}</p>
            <button type="button" className="btn-secondary" onClick={() => {
              forceReload.current = true;
              setReloadVersion((version) => version + 1);
            }} data-testid="inspector-reload-latest">
              {uiText("丢弃草稿并加载最新内容", "Discard draft and load latest content")}</button>
          </div>
        )}
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
              <span>{uiText("校验未通过", "Validation failed")}</span>
            </div>
            {errorMessagesToDisplay.map((msg, idx) => (
              <div key={idx} style={{ fontSize: '11px', color: 'var(--text-main)' }}>
                • {renderUiMessage(msg)}
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
              <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text-main)' }}>{uiText("更改影响预览", "Change impact preview")}</span>
              <span className="badge badge-blue" style={{ fontSize: '9px' }}>
                {uiText(`${preview?.semanticSummary?.changedFieldCount ?? pending.changes.length} 个字段`, englishCount(preview?.semanticSummary?.changedFieldCount ?? pending.changes.length, 'field'))}</span>
            </div>
            {isPreviewing ? (
              <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>{uiText("正在分析语义与生成影响…", "Analyzing semantic and generation impact…")}</div>
            ) : preview ? (
              <>
                <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                  {uiText("分区：", "Sections: ")}{(preview.semanticSummary?.sections ?? [])
                    .map((id) => editor.sections.find((section) => section.id === id))
                    .filter(Boolean)
                    .map((section) => t(section!.title))
                    .join(uiText('、', ', ')) || uiText("通用属性", "General attributes")}
                </div>
                {preview.generationImpact && (
                  <div style={{ fontSize: '10px', color: 'var(--badge-blue)' }}>
                    {uiText("保存后需重新生成当前元素 · ", "Regenerate this element after saving · ")}{preview.generationImpact.affectedDomains.map(generationDomainLabel).join(uiText('、', ', '))}
                    {preview.generationImpact.generatorId ? ` · ${preview.generationImpact.generatorId}` : ''}
                  </div>
                )}
              </>
            ) : (
              <div style={{ fontSize: '10px', color: 'var(--badge-amber)' }}>{uiText("暂时无法读取生成影响，保存仍会走 Core 校验。", "Generation impact is unavailable. Saving will still run Core validation.")}</div>
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
            {uiText("正在加载编辑器投影…", "Loading editor…")}</div>
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
                        {uiText("范围：", "Range: ")}{field.constraints.min} - {field.constraints.max}
                      </div>
                    )}
                    {field.condition && (
                      <div
                        data-testid={`field-condition-${fieldTestSuffix(field.path)}`}
                        style={{ fontSize: '10px', color: enabledByCondition ? 'var(--badge-blue)' : 'var(--text-sub)' }}
                      >
                        {enabledByCondition ? uiText("条件已启用 · 当前字段必填", "Condition enabled · This field is required") : uiText("条件未启用 · 当前字段不会参与生成", "Condition disabled · This field will not affect generation")}
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
                          flexDirection: 'column',
                          alignItems: 'flex-start',
                          gap: '6px'
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '4px' }}>
                          <AlertTriangle size={11} style={{ flexShrink: 0, marginTop: '1px' }} />
                          <span>{t(fieldDiagnostic.message)}</span>
                        </div>
                        {fieldDiagnostic.actions.length > 0 && (
                          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', paddingLeft: '15px' }}>
                            {fieldDiagnostic.actions.map((action) => (
                              <button
                                key={action.id}
                                type="button"
                                className="btn-secondary"
                                style={{ fontSize: '10px', padding: '3px 8px' }}
                                onClick={() => runDiagnosticAction(action, fieldDiagnostic)}
                                data-testid={`diag-action-${action.id}`}
                              >
                                {t(action.label)}
                              </button>
                            ))}
                          </div>
                        )}
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
                          {extensionName} {uiText("加载器扩展", "loader extension")}</span>
                        <span className="badge badge-amber" style={{ fontSize: '9px' }}>
                          {uiText("只读保留", "Preserved, read only")}</span>
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
                            : uiText("该字段已保留在工作区元数据中，但当前活动生成器下不可用。", "This field is preserved in workspace metadata but is unavailable for the active generator.")}
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
                          {uiText("只读保留", "Preserved, read only")}</span>
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
          <span>{uiText("删除", "Delete")}</span>
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {saveSuccess && (
            <span style={{ color: 'var(--badge-green)', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Check size={13} /> {uiText("已保存", "Saved")}</span>
          )}
          <button
            className="btn-primary"
            onClick={handleSave}
            disabled={externalChange || isSaving || !editor || pending.invalidJson || pending.changes.length === 0 || Boolean(preview && !preview.canApply)}
            data-testid="inspector-save-btn"
          >
            <Save size={13} />
            <span>{isSaving ? uiText("保存中…", "Saving…") : uiText("应用更改", "Apply changes")}</span>
          </button>
        </div>
      </div>
    </aside>
  );
};
