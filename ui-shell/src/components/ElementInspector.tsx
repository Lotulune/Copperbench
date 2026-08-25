import React, { useState, useEffect, useRef } from 'react';
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
  EditorField,
  Diagnostic
} from '../types/contract';
import { t } from '../i18n';

interface ElementInspectorProps {
  element: ModElementSummary;
  onClose: () => void;
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
  const { updateModElement, deleteModElement, getModElementEditor, state } = useWorkbench();
  const [editor, setEditor] = useState<ModElementEditorProjection | null>(null);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [localErrors, setLocalErrors] = useState<string[]>([]);

  // Keep the latest query dispatcher without re-running the projection fetch
  // on unrelated bridge state changes.
  const getEditorRef = useRef(getModElementEditor);
  getEditorRef.current = getModElementEditor;

  useEffect(() => {
    let cancelled = false;
    setEditor(null);
    setValues({});
    setLocalErrors([]);
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

    let changes: FieldChange[];
    try {
      changes = editor.sections
        .flatMap((section) => section.fields)
        .filter((field) => !field.readOnly)
        .map((field) => ({
          path: field.path,
          value: field.control === 'json' && typeof values[field.path] === 'string'
            ? JSON.parse(values[field.path] as string)
            : values[field.path]
        }));
    } catch {
      setLocalErrors(['JSON 字段格式无效；请修正括号、引号或逗号后再保存。']);
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
          const projected = Object.fromEntries(
            refreshed.sections.flatMap((section) =>
              section.fields.map((field) => [field.path, field.value])
            )
          );
          setEditor(refreshed);
          setValues((prev) => ({ ...projected, ...prev }));
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
      : elementDiagnostics.filter((d) => d.severity === 'error').map((d) => t(d.message));

  const diagnosticByPath = new Map(
    elementDiagnostics.filter((d) => d.path).map((d) => [d.path as string, d])
  );

  const renderControl = (field: EditorField) => {
    const value = values[field.path];
    const disabled = field.readOnly;
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
            onChange={(e) => setValues((prev) => ({ ...prev, [field.path]: e.target.value }))}
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
      case 'text':
      default:
        return (
          <div style={{ position: 'relative' }}>
            {(field.control === 'resource_reference' || field.control === 'procedure_reference') && (
              <LinkIcon
                size={13}
                style={{ position: 'absolute', left: '10px', top: '9px', color: 'var(--text-sub)' }}
              />
            )}
            <input
              id={controlId}
              type="text"
              value={value === undefined || value === null ? '' : String(value)}
              disabled={disabled}
              readOnly={disabled}
              onChange={(e) => setValues((prev) => ({ ...prev, [field.path]: e.target.value }))}
              style={{
                ...commonStyle,
                paddingLeft:
                  field.control === 'resource_reference' || field.control === 'procedure_reference'
                    ? '30px'
                    : undefined
              }}
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
                const extensionName = loaderExtensionName(field.path);
                const isLoaderExtension = field.readOnly && extensionName !== null;
                const controlId = fieldControlId(field.path);

                const controlBlock = (
                  <>
                    {renderControl(field)}
                    {field.constraints && field.control === 'number' && (
                      <div style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                        范围：{field.constraints.min} - {field.constraints.max}
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
                      opacity: field.readOnly ? 0.8 : 1
                    }}
                  >
                    <label htmlFor={controlId} style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
                      {t(field.label)}
                      {field.required && <span style={{ color: 'var(--badge-red)' }}> *</span>}
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
            disabled={isSaving || !editor}
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
