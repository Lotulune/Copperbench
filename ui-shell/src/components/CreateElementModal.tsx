import React, { useState } from 'react';
import { X, Plus, Box, Compass, Scroll, Terminal } from 'lucide-react';
import { useWorkbench } from '../context/WorkbenchContext';
import { ModElementType } from '../types/contract';
import { useDialogA11y } from '../hooks/useDialogA11y';

export const CreateElementModal: React.FC = () => {
  const { isCreateModalOpen, setIsCreateModalOpen, createModElement } = useWorkbench();
  const [elementType, setElementType] = useState<ModElementType>('block');
  const [name, setName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dialogRef = useDialogA11y(isCreateModalOpen, () => setIsCreateModalOpen(false));

  if (!isCreateModalOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('元素标识符不能为空');
      return;
    }
    // Validate identifier format
    if (!/^[a-z][a-z0-9_]{0,63}$/.test(name.trim())) {
      setError('标识符必须为小写字母、数字或下划线（如 copper_lamp）');
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
        setError('创建元素失败');
      }
    } catch {
      setIsSubmitting(false);
      setError('创建元素时发生错误');
    }
  };

  return (
    <div className="modal-overlay" data-testid="create-element-modal">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label="创建模组元素"
        className="modal-card animate-fade-in"
        style={{ width: '460px' }}
      >
        <div className="modal-header">
          <div style={{ fontWeight: 700, fontSize: '14px', color: 'var(--text-main)' }}>
            创建模组元素
          </div>
          <button onClick={() => setIsCreateModalOpen(false)} style={{ color: 'var(--text-muted)' }}>
            <X size={16} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && (
              <div style={{ background: 'var(--badge-red-bg)', border: '1px solid rgba(248, 81, 73, 0.3)', padding: '8px 12px', borderRadius: 'var(--radius-sm)', color: 'var(--badge-red)', fontSize: '11px' }}>
                {error}
              </div>
            )}

            {/* Element Type Selection */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <label style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
                元素类型
              </label>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '8px' }}>
                {[
                  { type: 'block', label: '方块', icon: Box },
                  { type: 'item', label: '物品', icon: Compass },
                  { type: 'recipe', label: '配方', icon: Scroll },
                  { type: 'procedure', label: '过程', icon: Terminal }
                ].map((item) => {
                  const Icon = item.icon;
                  const isSel = elementType === item.type;
                  return (
                    <button
                      key={item.type}
                      type="button"
                      onClick={() => setElementType(item.type as ModElementType)}
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
                      <span style={{ fontSize: '12px' }}>{item.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Name Identifier */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
                标识符（内部 ID）
              </label>
              <input
                type="text"
                placeholder="例如 copper_lamp、trail_lantern"
                value={name}
                onChange={(e) => setName(e.target.value)}
                autoFocus
                data-testid="create-element-name-input"
              />
              <span style={{ fontSize: '10px', color: 'var(--text-sub)' }}>
                必须为小写字母、数字或下划线，不能包含空格。
              </span>
            </div>
          </div>

          <div className="modal-footer">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setIsCreateModalOpen(false)}
            >
              取消
            </button>
            <button
              type="submit"
              className="btn-primary"
              disabled={isSubmitting}
              data-testid="create-element-submit-btn"
            >
              <Plus size={14} />
              <span>{isSubmitting ? '创建中…' : '创建元素'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
