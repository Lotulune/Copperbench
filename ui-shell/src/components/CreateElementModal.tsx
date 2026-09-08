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
  const typeLabels: Partial<Record<ModElementType, string>> = {
    block: '方块', item: '物品', recipe: '配方', procedure: '过程', function: '函数', loottable: '战利品表', achievement: '进度',
    armor: '盔甲', armortrim: '盔甲纹饰', tool: '工具', itemextension: '物品扩展', attribute: '属性', bannerpattern: '旗帜图案',
    command: '命令', damagetype: '伤害类型', enchantment: '附魔', gamerule: '游戏规则', keybind: '按键绑定', painting: '画', particle: '粒子',
    potion: '药水', potioneffect: '药水效果', tab: '创造模式标签页', villagerprofession: '村民职业', villagertrade: '村民交易', biome: '生物群系',
    dimension: '维度', feature: '世界特征', fluid: '流体', plant: '植物', structure: '结构', livingentity: '生物实体', specialentity: '特殊实体',
    projectile: '投射物', gui: '界面', overlay: '覆盖层', code: '代码'
  };
  const typeIcons: Record<string, typeof Box> = { block: Box, item: Compass, recipe: Scroll, procedure: Terminal, function: FileCode2, loottable: Gift, achievement: Trophy };

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
          <button type="button" aria-label="关闭创建元素对话框" onClick={() => setIsCreateModalOpen(false)} style={{ color: 'var(--text-muted)' }}>
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
                {ALL_MOD_ELEMENT_TYPES.map((type) => {
                  const Icon = typeIcons[type] ?? Compass;
                  const item = { type, label: typeLabels[type] ?? type, icon: Icon };
                  const isSel = elementType === item.type;
                  return (
                    <button
                      key={item.type}
                      type="button"
                      data-testid={`create-element-type-${item.type}`}
                      onClick={() => setElementType(item.type as ModElementType)}
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
                      <span style={{ fontSize: '12px' }}>{item.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Name Identifier */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <label htmlFor="create-element-name" style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-muted)' }}>
                标识符（内部 ID）
              </label>
              <input
                id="create-element-name"
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
