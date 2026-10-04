import { tr, uiText } from '../i18n/locale';
import { elementLabel, elementShortLabel } from '../i18n/labels';
import React, { useState } from 'react';
import { X, Plus, Box, Compass, Scroll, Terminal, FileCode2, Gift, Trophy } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ALL_MOD_ELEMENT_TYPES, ModElementType } from '../types/contract';
import { useDialogA11y } from '../hooks/useDialogA11y';

export const CreateElementModal: React.FC = () => {
  const { isCreateModalOpen, setIsCreateModalOpen, createModElement } = useWorkbench();
  const [elementType, setElementType] = useState<ModElementType>('block');
  const [name, setName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const typeIcons: Record<string, typeof Box> = { block: Box, item: Compass, recipe: Scroll, procedure: Terminal, function: FileCode2, loottable: Gift, achievement: Trophy };

  const dialogRef = useDialogA11y(isCreateModalOpen, () => setIsCreateModalOpen(false));

  if (!isCreateModalOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError(tr("元素标识符不能为空"));
      return;
    }
    // Validate identifier format
    if (!/^[a-z][a-z0-9_]{0,63}$/.test(name.trim())) {
      setError(tr("标识符须以小写字母开头，仅含小写字母、数字或下划线，长度为 1–64（如 copper_lamp）。"));
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      const res = await createModElement(elementType, name.trim());
      setIsSubmitting(false);
      if (res.status === 'committed') {
        setIsCreateModalOpen(false);
        setName('');
      } else {
        setError(tr("创建元素失败"));
      }
    } catch {
      setIsSubmitting(false);
      setError(tr("创建元素时发生错误"));
    }
  };

  return (
    <div className="modal-overlay" data-testid="create-element-modal">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label={tr("创建模组元素")}
        className="modal-card create-element-card animate-fade-in"
      >
        <div className="modal-header">
          <h2>{tr("创建模组元素")}</h2>
          <button type="button" aria-label={tr("关闭创建元素对话框")} onClick={() => setIsCreateModalOpen(false)} style={{ color: 'var(--text-muted)' }}>
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && (
              <div role="alert" id="create-element-error" style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.3)', padding: '8px 12px', borderRadius: 'var(--radius-sm)', color: 'var(--badge-red)', fontSize: '12px' }}>
                {error}
              </div>
            )}

            {/* Name Identifier */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label htmlFor="create-element-name" style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>
                {uiText("元素标识符", "Element identifier")}</label>
              <input
                id="create-element-name"
                type="text"
                placeholder={tr("例如 copper_lamp、trail_lantern")}
                value={name}
                onChange={(e) => setName(e.target.value)}
                aria-invalid={!!error}
                aria-describedby={error ? "create-element-error create-element-hint" : "create-element-hint"}
                autoComplete="off"
                spellCheck={false}
                autoFocus
                data-testid="create-element-name-input"
              />
              <span id="create-element-hint" style={{ fontSize: '12px', color: 'var(--text-sub)' }}>
                {uiText("小写字母开头，可用数字和下划线，最多 64 个字符。", "Start with a lowercase letter. Use letters, numbers or underscores, up to 64 characters.")}</span>
            </div>

            {/* Element Type Selection */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <label style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-muted)' }}>
                {tr("元素类型")}</label>
              <div className="create-type-grid" role="group" aria-label={tr("元素类型")}>
                {ALL_MOD_ELEMENT_TYPES.map((type) => {
                  const Icon = typeIcons[type] ?? Compass;
                  const item = { type, label: elementLabel(type), icon: Icon };
                  const isSel = elementType === item.type;
                  return (
                    <button
                      key={item.type}
                      type="button"
                      data-testid={`create-element-type-${item.type}`}
                      onClick={() => setElementType(item.type as ModElementType)}
                      aria-label={item.label}
                      title={item.label}
                      aria-pressed={isSel}
                      style={{
                        padding: '10px',
                        background: isSel ? 'var(--accent-copper-dim)' : 'var(--bg-panel)',
                        border: isSel ? '1px solid var(--accent-copper)' : '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-md)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        color: isSel ? 'var(--accent-copper)' : 'var(--text-main)',
                        fontWeight: isSel ? 600 : 500,
                        textAlign: 'left'
                      }}
                    >
                      <Icon size={16} />
                      <span style={{ fontSize: '12px' }}>{elementShortLabel(item.type)}</span>
                    </button>
                  );
                })}
              </div>
            </div>


          </div>

          <div className="modal-footer">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setIsCreateModalOpen(false)}
            >
              {tr("取消")}</button>
            <button
              type="submit"
              className="btn-primary"
              disabled={isSubmitting}
              data-testid="create-element-submit-btn"
            >
              <Plus size={14} />
              <span>{isSubmitting ? tr("创建中…") : tr("创建元素")}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
